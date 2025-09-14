# import standard modules
import argparse, os, math
from typing import List, Tuple

# define image geometry defaults
W, H = 640, 480

# define a minimal bit reader supporting MSB- and LSB-first orders
class BitReader:
    # initialize with data, bit order ('msb' or 'lsb'), and absolute start bit position
    def __init__(self, data: bytes, order: str = "msb", start_bit: int = 0):
        # store buffer
        self.data = data
        # store bit order
        self.order = order
        # store current absolute bit position
        self.bp = start_bit

    # read 1 bit; return -1 on EOF
    def read1(self) -> int:
        # compute byte index
        bi = self.bp // 8
        # check EOF
        if bi >= len(self.data):
            # return sentinel
            return -1
        # compute within-byte bit index
        bpos = self.bp % 8
        # fetch byte
        b = self.data[bi]
        # read bit depending on order
        if self.order == "msb":
            # extract bit MSB-first
            bit = (b >> (7 - bpos)) & 1
        else:
            # extract bit LSB-first
            bit = (b >> bpos) & 1
        # advance bit position
        self.bp += 1
        # return bit
        return bit

    # read n bits as integer; return -1 on EOF
    def readn(self, n: int) -> int:
        # initialize value
        v = 0
        # iterate n times
        for i in range(n):
            # read one bit
            b = self.read1()
            # return failure on EOF
            if b < 0:
                # signal EOF
                return -1
            # accumulate depending on order
            if self.order == "msb":
                # shift left then or-in
                v = (v << 1) | b
            else:
                # set bit at position i
                v |= (b << i)
        # return result
        return v

    # snap to next byte boundary
    def align_next_byte(self):
        # compute remainder
        r = self.bp % 8
        # if not aligned, jump forward
        if r != 0:
            # advance to next multiple of 8
            self.bp += (8 - r)

# decode one Rice(k) non-negative codeword (unary zeros, then 1, then k-bit remainder)
def rice_decode(br: BitReader, k: int) -> int:
    # count unary zeros
    q = 0
    # loop until a 1 appears
    while True:
        # read one bit
        b = br.read1()
        # fail on EOF
        if b < 0:
            # return failure
            return -1
        # keep counting on zero
        if b == 0:
            # increment
            q += 1
            # continue
            continue
        # stop at first one
        break
    # read k-bit remainder
    r = br.readn(k) if k > 0 else 0
    # fail on EOF
    if r < 0:
        # return failure
        return -1
    # combine quotient and remainder
    return (q << k) | r

# inverse zigzag (LOCO-I style) mapping from non-negative v to signed e
def inv_zigzag(v: int) -> int:
    # even maps to non-negative
    if (v & 1) == 0:
        # return v//2
        return v // 2
    # odd maps to negative
    return -((v + 1) // 2)

# clamp integer to 16-bit unsigned
def clamp_u16(x: int) -> int:
    # lower bound
    if x < 0: return 0
    # upper bound
    if x > 65535: return 65535
    # inside range
    return x

# compute MED predictor (A=left, B=up, C=up-left)
def predict_med(A: int, B: int, C: int) -> int:
    # apply LOCO-I MED rule
    if C >= max(A, B): return min(A, B)
    if C <= min(A, B): return max(A, B)
    return A + B - C

# pick predictor function by name
def predictor_value(name: str, A: int, B: int, C: int) -> int:
    # choose among left, up, med
    if name == "left":
        # left neighbor
        return A
    if name == "up":
        # upper neighbor
        return B
    # default to med
    return predict_med(A, B, C)

# decode a frame with a given parameter set; returns (ok, img_bytes_le, stats)
def decode_variant(data: bytes,
                   width: int, height: int,
                   order: str, start_align: int,
                   scan: str, predictor: str,
                   sign_rule: str, first_mode: str,
                   k: int, row_realign: bool):
    # create bit reader
    br = BitReader(data, order=order, start_bit=start_align)
    # prepare output buffer
    out = bytearray(width * height * 2)
    # track metrics
    bytes_start = (br.bp // 8)
    # iterate rows
    for y in range(height):
        # optionally align at row start
        if row_realign:
            # snap to byte boundary
            br.align_next_byte()

        # decide row direction for serpentine
        # even rows: left->right, odd rows: right->left
        reverse = (scan == "serp" and (y & 1) == 1)

        # decide starting column and step
        x0, x1, dx = (width-1, -1, -1) if reverse else (0, width, 1)

        # handle first sample of row
        if first_mode == "row_literal16":
            # read a 16-bit literal sample
            p0 = br.readn(16)
            # fail on EOF
            if p0 < 0:
                # return failure
                return False, b"", {"error": f"EOF on literal y={y}"}
            # choose position for first literal based on scan direction
            x = x0
            # store pixel value
            val = p0 & 0xFFFF
            # write little-endian
            idx = (y * width + x) * 2
            out[idx] = val & 0xFF
            out[idx+1] = (val >> 8) & 0xFF
            # set previous value for left predictor (in scan direction)
            prev = val
            # set next x
            x_iter_start = x + dx
        elif first_mode == "row_literal14":
            # read a 14-bit literal, scale to 16-bit by <<2 (common IR behavior)
            p0 = br.readn(14)
            # fail on EOF
            if p0 < 0:
                # return failure
                return False, b"", {"error": f"EOF on literal14 y={y}"}
            # upscale to 16-bit range
            val = (p0 << 2) & 0xFFFF
            # choose position
            x = x0
            # write
            idx = (y * width + x) * 2
            out[idx] = val & 0xFF
            out[idx+1] = (val >> 8) & 0xFF
            # set prev
            prev = val
            # start next x
            x_iter_start = x + dx
        else:
            # unsupported mode placeholder
            return False, b"", {"error": "unknown first_mode"}

        # iterate remaining columns in this row
        for x in range(x_iter_start, x1, dx):
            # fetch neighbors for predictor
            # A is previous pixel in scan direction
            A = prev
            # B is pixel directly above at same column (already decoded previous row)
            # read from output buffer (little-endian) if exists
            if y > 0:
                # compute index of upper sample
                up_idx = ((y - 1) * width + x) * 2
                # reconstruct u16 from LE
                B = out[up_idx] | (out[up_idx + 1] << 8)
                # compute up-left for MED
                if (x - dx) >= 0 and (x - dx) < width:
                    # index for up-left following scan direction
                    ul_idx = ((y - 1) * width + (x - dx)) * 2
                    # reconstruct u16
                    C = out[ul_idx] | (out[ul_idx + 1] << 8)
                else:
                    # no up-left at edges
                    C = 0
            else:
                # top row: no upper neighbors
                B = 0
                C = 0

            # predict value
            P = predictor_value(predictor, A, B, C)

            # decode one Rice codeword
            v = rice_decode(br, k)
            # stop on EOF
            if v < 0:
                # return failure
                return False, b"", {"error": f"EOF at y={y}"}

            # map to signed residual
            e = inv_zigzag(v)

            # apply sign rule
            if sign_rule == "plus":
                # actual = P + e
                cur = (P + e) & 0xFFFF
            else:
                # actual = P - e
                cur = (P - e) & 0xFFFF

            # write LE
            idx = (y * width + x) * 2
            out[idx] = cur & 0xFF
            out[idx + 1] = (cur >> 8) & 0xFF

            # update prev for scan-direction left predictor
            prev = cur

    # compute bytes used
    bytes_used = (br.bp - start_align + 7) // 8
    # return success, bytes, and stats
    return True, bytes(out), {"bytes_used": bytes_used}

# compute image coherence metrics: horizontal and vertical mean|Δ| and row-mean variance
def image_metrics(img_le: bytes, width: int, height: int) -> Tuple[float, float, float]:
    # initialize accumulators
    h_sum = 0
    v_sum = 0
    # initialize counters
    h_cnt = 0
    v_cnt = 0
    # list for per-row means
    row_means: List[float] = []
    # iterate rows
    for y in range(height):
        # compute sum for row mean
        row_sum = 0
        # iterate columns
        for x in range(width):
            # compute index in bytes
            i = (y * width + x) * 2
            # load pixel value in LE
            p = img_le[i] | (img_le[i+1] << 8)
            # accumulate row sum
            row_sum += p
            # if not last col, accumulate horizontal abs diff
            if x + 1 < width:
                # next pixel index
                j = i + 2
                # load next
                q = img_le[j] | (img_le[j+1] << 8)
                # accumulate |Δ|
                h_sum += abs(q - p)
                # increment counter
                h_cnt += 1
            # if not last row, accumulate vertical abs diff
            if y + 1 < height:
                # index below
                k = ((y + 1) * width + x) * 2
                # load below
                r = img_le[k] | (img_le[k+1] << 8)
                # accumulate |Δ|
                v_sum += abs(r - p)
                # increment counter
                v_cnt += 1
        # append row mean
        row_means.append(row_sum / width)
    # compute means safely
    h_grad = h_sum / h_cnt if h_cnt else 1e9
    v_grad = v_sum / v_cnt if v_cnt else 1e9
    # compute row-mean variance
    mean_of_means = sum(row_means) / len(row_means)
    # accumulate squared deviations
    var = sum((m - mean_of_means) ** 2 for m in row_means) / len(row_means)
    # return metrics
    return h_grad, v_grad, var

# scoring function emphasizing vertical coherence, then horizontal, then small row-mean jumps, finally bytes_used
def score_candidate(ok: bool, img_le: bytes, stats: dict, expected_len: int) -> float:
    # reject if decode failed or wrong size
    if not ok or len(img_le) != expected_len:
        # return very low score
        return -1e12
    # compute metrics
    h_grad, v_grad, rowvar = image_metrics(img_le, W, H)
    # get bytes used
    bu = stats.get("bytes_used", 0)
    # lower grads and rowvar are better; higher bytes used is mildly better
    # combine into a score (negative costs + small bytes reward)
    return -3.0 * v_grad - 1.0 * h_grad - 0.01 * rowvar + 0.05 * bu

# main sweep routine
def main():
    # set up CLI
    ap = argparse.ArgumentParser(description="Variant sweeper for IR lossless decode hypotheses (vertical-coherence focused)")
    # add files
    ap.add_argument("files", nargs="+", help="compressed frame files")
    # add width and height
    ap.add_argument("--width", type=int, default=W, help="width (default 640)")
    ap.add_argument("--height", type=int, default=H, help="height (default 480)")
    # parse args
    args = ap.parse_args()

    # expected output bytes
    expected = args.width * args.height * 2

    # define grids (compact but discriminative)
    orders = ["msb", "lsb"]
    aligns = list(range(8))
    scans  = ["row", "serp"]
    preds  = ["left", "med", "up"]
    signs  = ["plus", "minus"]
    firsts = ["row_literal16", "row_literal14"]
    ks     = [4, 5]
    reals  = [False, True]

    # iterate each file
    for path in args.files:
        # read bytes
        data = open(path, "rb").read()
        # collect (score, label, stats) tuples
        ranked = []
        # loop grid
        for order in orders:
            for align in aligns:
                for scan in scans:
                    for pred in preds:
                        for sign in signs:
                            for first in firsts:
                                for kval in ks:
                                    for realign in reals:
                                        # decode this variant
                                        ok, img, stats = decode_variant(
                                            data,
                                            args.width, args.height,
                                            order, align,
                                            scan, pred,
                                            sign, first,
                                            kval, realign
                                        )
                                        # compute score
                                        s = score_candidate(ok, img, stats, expected)
                                        # build label
                                        label = f"ord={order} a={align} scan={scan} pred={pred} sign={sign} first={first} k={kval} realign={int(realign)}"

                                        print(label)

                                        # append to list
                                        ranked.append((s, label, stats, img))

        # sort by score descending
        ranked.sort(key=lambda t: t[0], reverse=True)

        # print top 10
        print(f"\nFile: {os.path.basename(path)}  (top 10 candidates)")
        for i, (s, label, stats, img) in enumerate(ranked[:10], 1):
            # extract metrics
            h, v, rv = image_metrics(img, args.width, args.height)
            # show summary
            print(f"{i:2d}. score={s:10.1f}  bytes_used={stats.get('bytes_used',0):7d}  H|Δ|={h:6.1f}  V|Δ|={v:6.1f}  rowVar={rv:9.1f}  {label}")

        # write the very best image so you can eyeball it
        best_img = ranked[0][3]
        # compose output name
        out_name = os.path.basename(path) + ".best_variant.pgm"
        # write PGM-16 (big-endian pixel order)
        # convert LE->BE
        be = bytearray(len(best_img))
        for i in range(0, len(best_img), 2):
            be[i]   = best_img[i+1]
            be[i+1] = best_img[i]
        # write file
        with open(out_name, "wb") as f:
            # header
            f.write(f"P5\n{args.width} {args.height}\n65535\n".encode("ascii"))
            # data
            f.write(be)
        # print saved file
        print(f"Saved: {out_name}")

# script entry point
if __name__ == "__main__":
    # run the sweeper
    main()

