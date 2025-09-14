# import standard modules
import argparse, os
from itertools import product
from concurrent.futures import ProcessPoolExecutor, as_completed
import multiprocessing as mp

# define image geometry defaults
W, H = 640, 480

# define global variables to hold shared state in workers
_DATA = None
_W = None
_H = None

# initialize the worker process with shared data and geometry
def _worker_init(data: bytes, width: int, height: int):
    # store the shared compressed bytes
    global _DATA;  _DATA = data
    # store the geometry
    global _W, _H; _W, _H = width, height

# define a minimal bit reader with both bit orders
class BitReader:
    # initialize with bit order and absolute start bit position
    def __init__(self, order: str = "msb", start_bit: int = 0):
        # store order and start bit
        self.order, self.bp = order, start_bit

    # read 1 bit; return -1 on EOF
    def read1(self) -> int:
        # compute byte index
        bi = self.bp // 8
        # check EOF
        if bi >= len(_DATA):
            # return sentinel
            return -1
        # compute bit position within byte
        bpos = self.bp % 8
        # fetch byte
        b = _DATA[bi]
        # extract MSB- or LSB-first bit
        bit = (b >> (7 - bpos)) & 1 if self.order == "msb" else (b >> bpos) & 1
        # advance bit pointer
        self.bp += 1
        # return bit
        return bit

    # read n bits as integer; return -1 on EOF
    def readn(self, n: int) -> int:
        # initialize value
        v = 0
        # read n bits
        for i in range(n):
            # get next bit
            b = self.read1()
            # return failure on EOF
            if b < 0:
                # indicate EOF
                return -1
            # accumulate bit by order
            if self.order == "msb":
                # shift left then or-in
                v = (v << 1) | b
            else:
                # place bit at position i
                v |= (b << i)
        # return composed value
        return v

    # snap to next byte boundary
    def align_next_byte(self):
        # compute remainder to next boundary
        r = self.bp % 8
        # advance if misaligned
        if r:
            # move to next multiple of 8
            self.bp += (8 - r)

# decode one Rice(k) codeword (unary zeros then 1 then k-bit remainder)
def rice_decode(br: BitReader, k: int) -> int:
    # count zeros
    q = 0
    # loop until a 1 appears
    while True:
        # read one bit
        b = br.read1()
        # return failure on EOF
        if b < 0:
            # indicate failure
            return -1
        # continue counting on zero
        if b == 0:
            # increment count
            q += 1
            # continue loop
            continue
        # break at first one
        break
    # read remainder bits
    r = br.readn(k) if k > 0 else 0
    # return failure on EOF
    if r < 0:
        # indicate failure
        return -1
    # combine quotient and remainder
    return (q << k) | r

# inverse zigzag (LOCO-I style) to signed residual
def inv_zigzag(v: int) -> int:
    # even maps to non-negative
    return v // 2 if (v & 1) == 0 else -((v + 1) // 2)

# compute MED predictor given neighbors A, B, C
def predict_med(A: int, B: int, C: int) -> int:
    # apply LOCO-I MED rule
    if C >= max(A, B):
        # return min of A and B
        return min(A, B)
    # handle opposite extreme
    if C <= min(A, B):
        # return max of A and B
        return max(A, B)
    # return A + B - C in middle case
    return A + B - C

# get predictor value by name
def predictor_value(name: str, A: int, B: int, C: int) -> int:
    # left predictor
    if name == "left": return A
    # up predictor
    if name == "up": return B
    # default to MED
    return predict_med(A, B, C)

# compute coherence metrics over a 16-bit little-endian image buffer
def image_metrics(img_le: bytes, width: int, height: int):
    # initialize accumulators and counters
    h_sum = v_sum = 0
    h_cnt = v_cnt = 0
    # prepare per-row mean buffer
    row_means = [0.0] * height
    # iterate rows
    for y in range(height):
        # compute starting byte index of row
        base = y * width * 2
        # initialize row sum
        rs = 0
        # iterate columns
        for x in range(width):
            # compute pixel byte index
            i = base + 2 * x
            # load pixel value from LE
            p = img_le[i] | (img_le[i + 1] << 8)
            # accumulate row sum
            rs += p
            # accumulate horizontal diff if inside row
            if x + 1 < width:
                # load next pixel
                j = i + 2
                q = img_le[j] | (img_le[j + 1] << 8)
                # accumulate absolute difference
                h_sum += abs(q - p)
                # increment counter
                h_cnt += 1
            # accumulate vertical diff if not last row
            if y + 1 < height:
                # compute index below
                k = ((y + 1) * width + x) * 2
                # load below pixel
                r = img_le[k] | (img_le[k + 1] << 8)
                # accumulate vertical difference
                v_sum += abs(r - p)
                # increment counter
                v_cnt += 1
        # store per-row mean
        row_means[y] = rs / width
    # compute mean gradients
    h_grad = h_sum / h_cnt if h_cnt else 1e9
    v_grad = v_sum / v_cnt if v_cnt else 1e9
    # compute row-mean variance
    mean_of_means = sum(row_means) / height
    row_var = sum((m - mean_of_means) ** 2 for m in row_means) / height
    # return metrics
    return h_grad, v_grad, row_var

# decode a frame under a parameter set and compute score and metrics (worker-side)
def worker_eval(params):
    # unpack parameter tuple
    order, align, scan, predictor, sign_rule, first_mode, kval, realign = params
    # create bit reader with chosen order and alignment
    br = BitReader(order=order, start_bit=align)
    # allocate output buffer (LE u16)
    out = bytearray(_W * _H * 2)

    # iterate rows
    for y in range(_H):
        # align to next byte boundary if requested
        if realign:
            # snap bit pointer to byte boundary
            br.align_next_byte()

        # decide serpentine direction for this row
        rev = (scan == "serp" and (y & 1) == 1)
        # select traversal bounds and step
        x0, x1, dx = (_W - 1, -1, -1) if rev else (0, _W, 1)

        # decode first pixel in row as literal
        if first_mode == "row_literal16":
            # read 16-bit literal
            p0 = br.readn(16)
            # fail fast on EOF
            if p0 < 0: return (-1e12, params, 0.0, 0.0, 0.0, 0)
            # place at starting position
            pos = x0
            # write LE u16
            idx = (y * _W + pos) * 2
            out[idx] = p0 & 0xFF;  out[idx + 1] = (p0 >> 8) & 0xFF
            # set previous in scan direction
            prev = p0 & 0xFFFF
            # choose next x
            start_x = pos + dx
        else:
            # read 14-bit literal and upshift by 2
            p0 = br.readn(14)
            # fail fast on EOF
            if p0 < 0: return (-1e12, params, 0.0, 0.0, 0.0, 0)
            # upscale to 16-bit
            val = (p0 << 2) & 0xFFFF
            # place at starting position
            pos = x0
            # write LE u16
            idx = (y * _W + pos) * 2
            out[idx] = val & 0xFF;  out[idx + 1] = (val >> 8) & 0xFF
            # set prev
            prev = val
            # choose next x
            start_x = pos + dx

        # iterate remaining pixels in row
        for x in range(start_x, x1, dx):
            # fetch neighbors
            A = prev
            if y > 0:
                # load up neighbor from out buffer
                uidx = ((y - 1) * _W + x) * 2
                B = out[uidx] | (out[uidx + 1] << 8)
                # compute up-left in scan direction
                nx = x - dx
                if 0 <= nx < _W:
                    # load up-left
                    ulidx = ((y - 1) * _W + nx) * 2
                    C = out[ulidx] | (out[ulidx + 1] << 8)
                else:
                    # edge case
                    C = 0
            else:
                # top row has no up neighbors
                B = 0; C = 0

            # predict via chosen predictor
            P = predictor_value(predictor, A, B, C)

            # decode Rice(k) symbol
            v = rice_decode(br, kval)
            # abort on EOF
            if v < 0: return (-1e12, params, 0.0, 0.0, 0.0, 0)

            # convert to signed residual
            e = inv_zigzag(v)

            # combine residual with sign rule
            cur = (P + e) & 0xFFFF if sign_rule == "plus" else (P - e) & 0xFFFF

            # store pixel
            idx = (y * _W + x) * 2
            out[idx] = cur & 0xFF;  out[idx + 1] = (cur >> 8) & 0xFF

            # advance prev along the scan
            prev = cur

    # compute bytes used
    bytes_used = (br.bp - align + 7) // 8

    # compute coherence metrics
    h_grad, v_grad, row_var = image_metrics(out, _W, _H)

    # compute score emphasizing vertical first, then horizontal, then row-mean stability, then bytes used
    score = -3.0 * v_grad - 1.0 * h_grad - 0.01 * row_var + 0.05 * bytes_used

    print(params, score)

    # return compact result
    return (score, params, h_grad, v_grad, row_var, bytes_used)

# decode once in the main process to save the best image to PGM-16
def decode_and_save_best(path: str, params, width: int, height: int, out_name: str):
    # unpack parameter tuple
    order, align, scan, predictor, sign_rule, first_mode, kval, realign = params
    # create a fresh reader over file-local data
    data = open(path, "rb").read()
    # create a bit reader in main process
    br = BitReader(order=order, start_bit=align)
    # bind the global data for this local decode
    global _DATA, _W, _H;  _DATA, _W, _H = data, width, height
    # allocate output buffer
    out = bytearray(width * height * 2)

    # iterate rows
    for y in range(height):
        # re-align per request
        if realign:
            # snap to boundary
            br.align_next_byte()

        # choose serpentine direction
        rev = (scan == "serp" and (y & 1) == 1)
        # compute traversal
        x0, x1, dx = (width - 1, -1, -1) if rev else (0, width, 1)

        # read row-first literal
        if first_mode == "row_literal16":
            # read 16-bit literal
            p0 = br.readn(16);  assert p0 >= 0, "EOF on literal16"
            # place
            pos = x0
            # write LE
            idx = (y * width + pos) * 2
            out[idx] = p0 & 0xFF;  out[idx + 1] = (p0 >> 8) & 0xFF
            # set prev
            prev = p0 & 0xFFFF
            # next x
            start_x = pos + dx
        else:
            # read 14-bit literal
            p0 = br.readn(14);  assert p0 >= 0, "EOF on literal14"
            # upscale to 16
            val = (p0 << 2) & 0xFFFF
            # place
            pos = x0
            # write LE
            idx = (y * width + pos) * 2
            out[idx] = val & 0xFF;  out[idx + 1] = (val >> 8) & 0xFF
            # prev
            prev = val
            # next
            start_x = pos + dx

        # decode rest of row
        for x in range(start_x, x1, dx):
            # neighbors
            A = prev
            if y > 0:
                # up
                uidx = ((y - 1) * width + x) * 2
                B = out[uidx] | (out[uidx + 1] << 8)
                # up-left in scan dir
                nx = x - dx
                if 0 <= nx < width:
                    ulidx = ((y - 1) * width + nx) * 2
                    C = out[ulidx] | (out[ulidx + 1] << 8)
                else:
                    C = 0
            else:
                # top row
                B = 0; C = 0

            # predict
            P = predictor_value(predictor, A, B, C)

            # residual
            v = rice_decode(br, kval);  assert v >= 0, "EOF in residual"
            # signed
            e = inv_zigzag(v)
            # combine
            cur = (P + e) & 0xFFFF if sign_rule == "plus" else (P - e) & 0xFFFF

            # write LE
            idx = (y * width + x) * 2
            out[idx] = cur & 0xFF;  out[idx + 1] = (cur >> 8) & 0xFF

            # advance prev
            prev = cur

    # convert LE to BE for PGM
    be = bytearray(len(out))
    for i in range(0, len(out), 2):
        # swap bytes
        be[i] = out[i + 1];  be[i + 1] = out[i]

    # write PGM-16 with header
    with open(out_name, "wb") as f:
        # write header
        f.write(f"P5\n{width} {height}\n65535\n".encode("ascii"))
        # write data
        f.write(be)

# run the parallel sweeper for one file
def run_parallel_sweep(path: str, width: int, height: int, workers: int, topn: int):
    # read compressed data once
    data = open(path, "rb").read()
    # build parameter grid
    orders = ["msb", "lsb"]
    aligns = list(range(8))
    scans  = ["row", "serp"]
    preds  = ["left", "med", "up"]
    signs  = ["plus", "minus"]
    firsts = ["row_literal16", "row_literal14"]
    ks     = [4, 5]
    reals  = [False, True]
    # generate all combinations
    grid = list(product(orders, aligns, scans, preds, signs, firsts, ks, reals))

    # choose number of workers
    maxw = mp.cpu_count()
    # clamp worker count
    n_workers = max(1, min(workers if workers else maxw, maxw))

    # create process pool with initializer to avoid copying data per task
    with ProcessPoolExecutor(max_workers=n_workers, initializer=_worker_init, initargs=(data, width, height)) as ex:
        # submit all tasks
        futures = [ex.submit(worker_eval, params) for params in grid]
        # collect results
        results = []
        # iterate as completed
        for fut in as_completed(futures):
            # get result tuple
            res = fut.result()
            # append to list
            results.append(res)

    # sort results by score descending
    results.sort(key=lambda r: r[0], reverse=True)

    # print header
    print(f"\nFile: {os.path.basename(path)}  (parallel top {topn})")
    # iterate top N
    for i, (score, params, h, v, rv, bu) in enumerate(results[:topn], 1):
        # build label
        order, align, scan, predictor, sign_rule, first_mode, kval, realign = params
        label = f"ord={order} a={align} scan={scan} pred={predictor} sign={sign_rule} first={first_mode} k={kval} realign={int(realign)}"
        # print summary line
        print(f"{i:2d}. score={score:10.1f}  bytes_used={bu:7d}  H|Δ|={h:6.1f}  V|Δ|={v:6.1f}  rowVar={rv:9.1f}  {label}")

    # decode and save the single best candidate image in main process
    best_params = results[0][1]
    # compose output name
    out_name = os.path.basename(path) + ".best_variant_mp.pgm"
    # decode and save
    decode_and_save_best(path, best_params, width, height, out_name)
    # print saved path
    print(f"Saved: {out_name}")

# script entry point
def main():
    # set up CLI
    ap = argparse.ArgumentParser(description="Parallel variant sweeper for IR lossless decode hypotheses")
    # add input files
    ap.add_argument("files", nargs="+", help="compressed frame files")
    # add width and height
    ap.add_argument("--width", type=int, default=W, help="image width (default 640)")
    ap.add_argument("--height", type=int, default=H, help="image height (default 480)")
    # add number of workers
    ap.add_argument("--workers", type=int, default=0, help="number of processes (0 = use all cores)")
    # add how many top results to print
    ap.add_argument("--topn", type=int, default=10, help="how many top candidates to show")
    # parse arguments
    args = ap.parse_args()

    # iterate files sequentially; each gets its own pool to share its data
    for path in args.files:
        # run sweep on this file
        run_parallel_sweep(path, args.width, args.height, args.workers, args.topn)

# standard script guard
if __name__ == "__main__":
    # execute main
    main()

