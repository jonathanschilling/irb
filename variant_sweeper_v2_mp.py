# import standard modules
import argparse, os, math
from itertools import product
from concurrent.futures import ProcessPoolExecutor, as_completed
import multiprocessing as mp

# image geometry defaults
W, H = 640, 480

# globals shared by workers
_DATA = None
_W = None
_H = None

# initialize worker with shared compressed bytes and geometry
def _worker_init(data: bytes, width: int, height: int):
    # store shared buffers and geometry
    global _DATA, _W, _H
    _DATA, _W, _H = data, width, height

# simple bit reader supporting MSB- and LSB-first
class BitReader:
    # construct with bit order and absolute start bit
    def __init__(self, order: str = "msb", start_bit: int = 0):
        # store order and start bit
        self.order = order
        self.bp = start_bit

    # read one bit; return -1 on EOF
    def read1(self) -> int:
        # compute byte index
        bi = self.bp // 8
        # check EOF
        if bi >= len(_DATA):
            return -1
        # compute bit position within the byte
        bpos = self.bp % 8
        # fetch byte
        b = _DATA[bi]
        # extract bit according to order
        if self.order == "msb":
            bit = (b >> (7 - bpos)) & 1
        else:
            bit = (b >> bpos) & 1
        # advance bit pointer
        self.bp += 1
        # return bit
        return bit

    # read n bits as int; return -1 on EOF
    def readn(self, n: int) -> int:
        # accumulator
        v = 0
        # read n bits
        for i in range(n):
            # get next bit
            b = self.read1()
            # stop on EOF
            if b < 0:
                return -1
            # accumulate by order
            if self.order == "msb":
                v = (v << 1) | b
            else:
                v |= (b << i)
        # return value
        return v

    # snap to next byte boundary
    def align_next_byte(self):
        # compute remainder
        r = self.bp % 8
        # advance if misaligned
        if r:
            self.bp += (8 - r)

# decode one Rice(k) symbol; return -1 on EOF
def rice_decode(br: BitReader, k: int) -> int:
    # count unary zeros
    q = 0
    # scan until first one
    while True:
        # read a bit
        b = br.read1()
        # handle EOF
        if b < 0:
            return -1
        # count zero
        if b == 0:
            q += 1
            continue
        # stop at one
        break
    # read remainder
    r = br.readn(k) if k > 0 else 0
    # handle EOF
    if r < 0:
        return -1
    # combine quotient and remainder
    return (q << k) | r

# inverse zigzag to signed residual
def inv_zigzag(v: int) -> int:
    # even -> non-negative
    if (v & 1) == 0:
        return v // 2
    # odd -> negative
    return -((v + 1) // 2)

# MED predictor
def predict_med(A: int, B: int, C: int) -> int:
    # apply LOCO-I MED rule
    if C >= max(A, B):
        return min(A, B)
    if C <= min(A, B):
        return max(A, B)
    return A + B - C

# choose predictor value
def predictor_value(name: str, A: int, B: int, C: int) -> int:
    # left predictor
    if name == "left":
        return A
    # up predictor
    if name == "up":
        return B
    # default to MED
    return predict_med(A, B, C)

# compute coherence + distribution metrics and a balanced score
def score_image(img_le: bytearray, width: int, height: int,
                remainder_counts, k: int, bytes_used: int):
    # initialize stats
    h_sum = v_sum = 0
    h_cnt = v_cnt = 0
    row_means = [0.0] * height
    hist_bins = [0] * 256

    # accumulate gradients and histogram (on high byte quick-proxy)
    for y in range(height):
        # row base
        base = y * width * 2
        # row sum
        rs = 0
        # iterate columns
        for x in range(width):
            # compute index
            i = base + 2 * x
            # load LE pixel
            p = img_le[i] | (img_le[i + 1] << 8)
            # accumulate row sum
            rs += p
            # accumulate high-byte histogram
            hist_bins[(p >> 8) & 0xFF] += 1
            # horizontal neighbor
            if x + 1 < width:
                j = i + 2
                q = img_le[j] | (img_le[j + 1] << 8)
                h_sum += abs(q - p)
                h_cnt += 1
            # vertical neighbor
            if y + 1 < height:
                kidx = ((y + 1) * width + x) * 2
                r = img_le[kidx] | (img_le[kidx + 1] << 8)
                v_sum += abs(r - p)
                v_cnt += 1
        # store row mean
        row_means[y] = rs / width

    # compute gradient means
    h_grad = h_sum / h_cnt if h_cnt else 1e9
    v_grad = v_sum / v_cnt if v_cnt else 1e9
    # compute row mean variance
    mean_rows = sum(row_means) / height
    row_var = sum((m - mean_rows) ** 2 for m in row_means) / height

    # compute value stddev and saturation penalties
    total = width * height
    mean = sum((i * 257) * hist_bins[i] for i in range(256)) / total
    var = sum(((i * 257) - mean) ** 2 * hist_bins[i] for i in range(256)) / total
    std = math.sqrt(max(1e-9, var))

    # cumulative counts for saturation tails
    cdf = [0]
    for i in range(256):
        cdf.append(cdf[-1] + hist_bins[i])
    p_low = cdf[5] / total
    p_high = (total - cdf[251]) / total
    sat_penalty = max(0.0, p_low - 0.05) + max(0.0, p_high - 0.05)

    # chi-square test for remainder uniformity
    if k > 0 and remainder_counts:
        m = 1 << k
        N = sum(remainder_counts)
        exp = N / m if m else 0.0
        chi2 = sum((c - exp) ** 2 / exp for c in remainder_counts) if exp > 0 else 1e9
    else:
        chi2 = 1e9

    # imbalance between H and V gradients
    imb = abs(h_grad - v_grad) / max(1.0, max(h_grad, v_grad))

    # aggregate cost
    cost = (3.0 * max(h_grad, v_grad)
            + 1.5 * imb * max(h_grad, v_grad)
            + 0.02 * row_var
            + 200.0 * sat_penalty
            + 0.02 * chi2
            + 0.05 * max(0.0, 500.0 - std))

    # final score
    score = -cost + 0.03 * bytes_used
    # return score and metrics
    return score, h_grad, v_grad, row_var, std, sat_penalty, chi2

# decode with params, track remainder histogram, and compute score (worker)
def worker_eval(params):
    # unpack parameters
    order, align, scan, predictor, sign_rule, first_mode, kval, realign = params
    # create bit reader
    br = BitReader(order=order, start_bit=align)
    # allocate output buffer
    out = bytearray(_W * _H * 2)
    # remainder histogram for Rice remainders
    rem_counts = [0] * (1 << kval) if kval > 0 else []

    # iterate rows
    for y in range(_H):
        # re-align to byte per row if requested
        if realign:
            br.align_next_byte()

        # serpentine direction flag
        rev = (scan == "serp" and (y & 1) == 1)
        # traversal bounds
        x0, x1, dx = (_W - 1, -1, -1) if rev else (0, _W, 1)

        # handle the first sample per row according to mode
        if first_mode == "row_literal16":
            # read 16-bit literal
            p0 = br.readn(16)
            # bail on EOF
            if p0 < 0:
                return (-1e12, params, 0, 0, 0, 0, 0, 0, 0)
            # place pixel at row start end
            pos = x0
            # write little-endian
            idx = (y * _W + pos) * 2
            out[idx] = p0 & 0xFF
            out[idx + 1] = (p0 >> 8) & 0xFF
            # set predictor seed
            prev = p0 & 0xFFFF
            # set first x
            xs = pos + dx

        elif first_mode == "row_literal14":
            # read 14-bit literal
            p0 = br.readn(14)
            # bail on EOF
            if p0 < 0:
                return (-1e12, params, 0, 0, 0, 0, 0, 0, 0)
            # upscale to 16-bit domain
            val = (p0 << 2) & 0xFFFF
            # place pixel
            pos = x0
            # write LE
            idx = (y * _W + pos) * 2
            out[idx] = val & 0xFF
            out[idx + 1] = (val >> 8) & 0xFF
            # seed predictor
            prev = val
            # next x
            xs = pos + dx

        elif first_mode == "frame_literal16":
            # special-case only the very first pixel of the image as literal
            if y == 0 and x0 == 0:
                # read 16-bit literal
                p0 = br.readn(16)
                # bail on EOF
                if p0 < 0:
                    return (-1e12, params, 0, 0, 0, 0, 0, 0, 0)
                # write first pixel at (0,0)
                out[0] = p0 & 0xFF
                out[1] = (p0 >> 8) & 0xFF
                # seed predictor
                prev = p0 & 0xFFFF
                # next x for top row
                xs = 1
            else:
                # predict and code first pixel of row
                A = prev
                if y > 0:
                    uidx = ((y - 1) * _W + x0) * 2
                    B = out[uidx] | (out[uidx + 1] << 8)
                    C = B
                else:
                    B = 0
                    C = 0
                # compute prediction
                P = predictor_value(predictor, A, B, C)
                # decode residual
                v = rice_decode(br, kval)
                # bail on EOF
                if v < 0:
                    return (-1e12, params, 0, 0, 0, 0, 0, 0, 0)
                # accumulate remainder histogram
                if kval > 0:
                    rem_counts[v & ((1 << kval) - 1)] += 1
                # invert zigzag
                e = inv_zigzag(v)
                # combine with sign rule
                cur = (P + e) & 0xFFFF if sign_rule == "plus" else (P - e) & 0xFFFF
                # write pixel
                idx0 = (y * _W + x0) * 2
                out[idx0] = cur & 0xFF
                out[idx0 + 1] = (cur >> 8) & 0xFF
                # update prev and next x
                prev = cur
                xs = x0 + dx

        elif first_mode == "no_row_literal_up":
            # code first sample of row as residual (UP-based seeding)
            A = 0 if x0 == 0 else prev
            if y > 0:
                uidx = ((y - 1) * _W + x0) * 2
                B = out[uidx] | (out[uidx + 1] << 8)
                C = B
            else:
                B = 0
                C = 0
            # compute prediction
            P = predictor_value(predictor, A, B, C)
            # decode residual
            v = rice_decode(br, kval)
            # bail on EOF
            if v < 0:
                return (-1e12, params, 0, 0, 0, 0, 0, 0, 0)
            # accumulate remainder histogram
            if kval > 0:
                rem_counts[v & ((1 << kval) - 1)] += 1
            # invert zigzag
            e = inv_zigzag(v)
            # combine with sign rule
            cur = (P + e) & 0xFFFF if sign_rule == "plus" else (P - e) & 0xFFFF
            # write pixel
            idx0 = (y * _W + x0) * 2
            out[idx0] = cur & 0xFF
            out[idx0 + 1] = (cur >> 8) & 0xFF
            # update prev and next x
            prev = cur
            xs = x0 + dx

        else:
            # unknown mode
            return (-1e12, params, 0, 0, 0, 0, 0, 0, 0)

        # decode remaining pixels in the row
        for x in range(xs, x1, dx):
            # neighbors in scan direction
            A = prev
            if y > 0:
                uidx = ((y - 1) * _W + x) * 2
                B = out[uidx] | (out[uidx + 1] << 8)
                nx = x - dx
                if 0 <= nx < _W:
                    ulidx = ((y - 1) * _W + nx) * 2
                    C = out[ulidx] | (out[ulidx + 1] << 8)
                else:
                    C = 0
            else:
                B = 0
                C = 0
            # predict
            P = predictor_value(predictor, A, B, C)
            # decode residual
            v = rice_decode(br, kval)
            # bail on EOF
            if v < 0:
                return (-1e12, params, 0, 0, 0, 0, 0, 0, 0)
            # accumulate remainder histogram
            if kval > 0:
                rem_counts[v & ((1 << kval) - 1)] += 1
            # signed residual
            e = inv_zigzag(v)
            # combine by sign rule
            cur = (P + e) & 0xFFFF if sign_rule == "plus" else (P - e) & 0xFFFF
            # write out
            idx = (y * _W + x) * 2
            out[idx] = cur & 0xFF
            out[idx + 1] = (cur >> 8) & 0xFF
            # advance prev
            prev = cur

    # compute bytes consumed
    bytes_used = (br.bp - align + 7) // 8
    # compute composite score and metrics
    score, h, v, rowvar, std, satp, chi2 = score_image(out, _W, _H, rem_counts, kval, bytes_used)

    print (params, score)

    # return result tuple
    return (score, params, h, v, rowvar, std, satp, chi2, bytes_used)

# decode best params once in main and write PGM-16
def save_best_image(path: str, params, width: int, height: int, out_name: str):
    # unpack params
    order, align, scan, predictor, sign_rule, first_mode, kval, realign = params
    # read data
    data = open(path, "rb").read()
    # bind globals for local decode
    global _DATA, _W, _H
    _DATA, _W, _H = data, width, height
    # create reader
    br = BitReader(order=order, start_bit=align)
    # allocate output buffer
    out = bytearray(width * height * 2)

    # decode rows
    for y in range(height):
        # per-row realignment
        if realign:
            br.align_next_byte()

        # serpentine direction
        rev = (scan == "serp" and (y & 1) == 1)
        # traversal bounds
        x0, x1, dx = (width - 1, -1, -1) if rev else (0, width, 1)

        # first pixel handling
        if first_mode == "row_literal16":
            # read literal
            p0 = br.readn(16)
            # assert availability
            assert p0 >= 0
            # place
            pos = x0
            # write LE
            idx = (y * width + pos) * 2
            out[idx] = p0 & 0xFF
            out[idx + 1] = (p0 >> 8) & 0xFF
            # seed prev
            prev = p0 & 0xFFFF
            # next x
            xs = pos + dx

        elif first_mode == "row_literal14":
            # read 14-bit literal
            p0 = br.readn(14)
            # assert availability
            assert p0 >= 0
            # upscale
            val = (p0 << 2) & 0xFFFF
            # place
            pos = x0
            # write LE
            idx = (y * width + pos) * 2
            out[idx] = val & 0xFF
            out[idx + 1] = (val >> 8) & 0xFF
            # seed prev
            prev = val
            # next x
            xs = pos + dx

        elif first_mode == "frame_literal16":
            # only first pixel of image is literal
            if y == 0 and x0 == 0:
                # read 16b literal
                p0 = br.readn(16)
                # assert availability
                assert p0 >= 0
                # write (0,0)
                out[0] = p0 & 0xFF
                out[1] = (p0 >> 8) & 0xFF
                # seed prev
                prev = p0 & 0xFFFF
                # next x
                xs = 1
            else:
                # predict and code
                A = prev
                if y > 0:
                    uidx = ((y - 1) * width + x0) * 2
                    B = out[uidx] | (out[uidx + 1] << 8)
                    C = B
                else:
                    B = 0
                    C = 0
                # predictor
                P = predictor_value(predictor, A, B, C)
                # residual
                v = rice_decode(br, kval)
                # assert availability
                assert v >= 0
                # invert zigzag
                e = inv_zigzag(v)
                # combine
                cur = (P + e) & 0xFFFF if sign_rule == "plus" else (P - e) & 0xFFFF
                # write
                idx0 = (y * width + x0) * 2
                out[idx0] = cur & 0xFF
                out[idx0 + 1] = (cur >> 8) & 0xFF
                # update
                prev = cur
                xs = x0 + dx

        elif first_mode == "no_row_literal_up":
            # code first pixel as residual
            A = 0 if x0 == 0 else prev
            if y > 0:
                uidx = ((y - 1) * width + x0) * 2
                B = out[uidx] | (out[uidx + 1] << 8)
                C = B
            else:
                B = 0
                C = 0
            # predictor
            P = predictor_value(predictor, A, B, C)
            # residual
            v = rice_decode(br, kval)
            # assert availability
            assert v >= 0
            # invert zigzag
            e = inv_zigzag(v)
            # combine
            cur = (P + e) & 0xFFFF if sign_rule == "plus" else (P - e) & 0xFFFF
            # write
            idx0 = (y * width + x0) * 2
            out[idx0] = cur & 0xFF
            out[idx0 + 1] = (cur >> 8) & 0xFF
            # update
            prev = cur
            xs = x0 + dx

        else:
            # invalid mode
            raise ValueError("bad first_mode")

        # decode remainder of row
        for x in range(xs, x1, dx):
            # neighbors
            A = prev
            if y > 0:
                uidx = ((y - 1) * width + x) * 2
                B = out[uidx] | (out[uidx + 1] << 8)
                nx = x - dx
                if 0 <= nx < width:
                    ulidx = ((y - 1) * width + nx) * 2
                    C = out[ulidx] | (out[ulidx + 1] << 8)
                else:
                    C = 0
            else:
                B = 0
                C = 0
            # predictor
            P = predictor_value(predictor, A, B, C)
            # residual
            v = rice_decode(br, kval)
            # assert availability
            assert v >= 0
            # invert zigzag
            e = inv_zigzag(v)
            # combine
            cur = (P + e) & 0xFFFF if sign_rule == "plus" else (P - e) & 0xFFFF
            # write pixel
            idx = (y * width + x) * 2
            out[idx] = cur & 0xFF
            out[idx + 1] = (cur >> 8) & 0xFF
            # advance
            prev = cur

    # convert to PGM-16 big-endian
    be = bytearray(len(out))
    for i in range(0, len(out), 2):
        be[i] = out[i + 1]
        be[i + 1] = out[i]
    # write file
    with open(out_name, "wb") as f:
        f.write(f"P5\n{width} {height}\n65535\n".encode("ascii"))
        f.write(be)

# run parallel sweep and save best
def run_parallel_sweep(path: str, width: int, height: int, workers: int, topn: int):
    # read compressed data
    data = open(path, "rb").read()

    # parameter grids
    orders = ["msb", "lsb"]
    aligns = list(range(8))
    scans  = ["row", "serp"]
    preds  = ["left", "med", "up"]
    signs  = ["plus", "minus"]
    firsts = ["row_literal16", "row_literal14", "frame_literal16", "no_row_literal_up"]
    ks     = [4, 5]
    reals  = [False, True]

    # enumerate combinations
    grid = list(product(orders, aligns, scans, preds, signs, firsts, ks, reals))

    # choose worker count
    maxw = mp.cpu_count()
    n_workers = max(1, min(workers if workers else maxw, maxw))

    # create pool with initializer sharing data
    with ProcessPoolExecutor(max_workers=n_workers, initializer=_worker_init, initargs=(data, width, height)) as ex:
        # submit all jobs
        futures = [ex.submit(worker_eval, p) for p in grid]
        # gather results
        results = []
        for fu in as_completed(futures):
            results.append(fu.result())

    # sort by score
    results.sort(key=lambda r: r[0], reverse=True)

    # print top candidates
    print(f"\nFile: {os.path.basename(path)}  (top {topn})")
    for i, (score, params, h, v, rowvar, std, satp, chi2, bu) in enumerate(results[:topn], 1):
        o, a, s, p, sg, fi, k, rl = params
        print(f"{i:2d}. S={score:9.1f} bu={bu:7d} H|Δ|={h:6.1f} V|Δ|={v:6.1f} rowVar={rowvar:9.1f} "
              f"std={std:6.1f} sat={satp:4.2f} chi2={chi2:8.1f}  "
              f"ord={o} a={a} scan={s} pred={p} sign={sg} first={fi} k={k} realign={int(rl)}")

    # pick best params and render once
    best_params = results[0][1]
    out_name = os.path.basename(path) + ".best_v2.pgm"
    save_best_image(path, best_params, width, height, out_name)
    print(f"Saved: {out_name}")

# CLI
def main():
    # build CLI
    ap = argparse.ArgumentParser(description="Parallel IR variant sweeper v2 (balanced scoring + Rice remainder uniformity)")
    ap.add_argument("files", nargs="+", help="compressed frames")
    ap.add_argument("--width", type=int, default=W)
    ap.add_argument("--height", type=int, default=H)
    ap.add_argument("--workers", type=int, default=0)
    ap.add_argument("--topn", type=int, default=10)
    args = ap.parse_args()

    # run for each file
    for path in args.files:
        run_parallel_sweep(path, args.width, args.height, args.workers, args.topn)

# script guard
if __name__ == "__main__":
    main()
