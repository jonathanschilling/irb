# import standard modules
import argparse, os
from concurrent.futures import ProcessPoolExecutor, as_completed
import multiprocessing as mp

# define geometry defaults
W, H = 640, 480
OUT_LEN = W * H * 2

# define global buffer for worker
_DATA = None

# initialize workers with shared data
def _init_worker(data: bytes):
    # bind shared data
    global _DATA; _DATA = data

# compute quick image coherence metrics on LE u16 buffer
def img_metrics(buf: bytearray, w: int, h: int):
    # initialize accumulators
    hsum = vsum = 0
    hcnt = vcnt = 0
    # iterate rows
    for y in range(h):
        # row base
        base = y * w * 2
        # iterate cols
        for x in range(w):
            # index
            i = base + 2 * x
            # value
            p = buf[i] | (buf[i+1] << 8)
            # horizontal neighbor
            if x + 1 < w:
                j = i + 2
                q = buf[j] | (buf[j+1] << 8)
                hsum += abs(q - p); hcnt += 1
            # vertical neighbor
            if y + 1 < h:
                k = ((y + 1) * w + x) * 2
                r = buf[k] | (buf[k+1] << 8)
                vsum += abs(r - p); vcnt += 1
    # averages
    hgrad = hsum / hcnt if hcnt else 1e9
    vgrad = vsum / vcnt if vcnt else 1e9
    # return metrics
    return hgrad, vgrad

# decode one frame given parameterization
def lzss_decode(params):
    # unpack parameters
    ctrl_msb, one_means_literal, fmt, len_base, row_reset = params
    # match fmt selection
    # fmt = ("12/4",) etc.
    # set bit widths
    if fmt == "12/4":
        off_bits, len_bits = 12, 4
    elif fmt == "11/5":
        off_bits, len_bits = 11, 5
    elif fmt == "10/6":
        off_bits, len_bits = 10, 6
    else:
        return None

    # decode across whole file
    src = memoryview(_DATA)
    si = 0
    out = bytearray(OUT_LEN)
    oi = 0

    # helper to read control byte
    def next_ctrl():
        nonlocal si
        if si >= len(src): return None
        b = src[si]
        si += 1
        return b

    # helper to pull one literal (2 bytes)
    def put_lit():
        nonlocal si, oi
        if si + 2 > len(src): return False
        if oi + 2 > OUT_LEN:  return False
        out[oi]   = src[si]
        out[oi+1] = src[si+1]
        oi += 2; si += 2
        return True

    # helper to copy match
    def do_match(off, ln):
        nonlocal oi
        if off == 0: return False
        if oi - 2*off < 0: return False
        total = ln * 2
        if oi + total > OUT_LEN: return False
        srcpos = oi - 2*off
        # copy bytes
        for _ in range(total):
            out[oi] = out[srcpos]
            oi += 1; srcpos += 1
        return True

    # per-row window reset support
    row_bytes = W * 2
    next_row_end = row_bytes

    # iterate control groups
    while oi < OUT_LEN:
        # optionally reset window at row boundary
        if row_reset and oi >= next_row_end:
            # cannot really clear match history, but we can enforce no backrefs across rows
            # by rejecting matches that cross row boundary in do_match
            next_row_end += row_bytes

        # fetch control byte
        c = next_ctrl()
        if c is None: break

        # iterate 8 flags
        for bit in range(8):
            # determine flag bit
            if ctrl_msb:
                flag = (c >> (7 - bit)) & 1
            else:
                flag = (c >> bit) & 1

            # if we reached output size, stop
            if oi >= OUT_LEN:
                break

            # interpret flag
            is_literal = (flag == 1) if one_means_literal else (flag == 0)
            if is_literal:
                # write one 16-bit literal
                if not put_lit():
                    return None
            else:
                # need two bytes for match token
                if si + 2 > len(src):
                    return None
                b0 = src[si]; b1 = src[si+1]; si += 2
                # compose 16-bit token
                tok = (b0 << 8) | b1
                # extract fields (MSB-packed)
                off = tok >> len_bits
                ln  = (tok & ((1 << len_bits) - 1)) + len_base
                # enforce per-row no-cross-copy if requested
                if row_reset:
                    # compute bytes to copy
                    total = ln * 2
                    # source start
                    src_start = oi - 2*off
                    # if source before row start, reject
                    row_start = ((oi // row_bytes) * row_bytes)
                    if src_start < row_start:
                        return None
                # perform match copy
                if not do_match(off, ln):
                    return None

        # continue until out filled or src exhausted

    # success only if exact size filled
    if oi != OUT_LEN:
        return None

    # compute metrics
    h, v = img_metrics(out, W, H)
    # balanced score: prefer small and balanced gradients
    imb = abs(h - v) / max(1.0, max(h, v))
    score = - (2.5 * max(h, v) + 1.0 * imb)
    # return tuple
    return (score, (ctrl_msb, one_means_literal, fmt, len_base, row_reset), h, v, len(_DATA), OUT_LEN)

# worker wrapper
def worker_eval(params):
    # run decode
    res = lzss_decode(params)
    # propagate failure distinctly
    if res is None:
        return (-1e12, params, 1e9, 1e9, 0, 0)
    # unpack
    score, p, h, v, sz, outsz = res
    return (score, p, h, v, sz, outsz)

# main
def main():
    # parse args
    ap = argparse.ArgumentParser(description="LZSS control-byte sweeper for IR frames")
    ap.add_argument("files", nargs="+")
    ap.add_argument("--workers", type=int, default=0)
    ap.add_argument("--topn", type=int, default=10)
    args = ap.parse_args()

    # parameter grid
    ctrl_msb_opts = [True, False]
    one_literal_opts = [True, False]
    fmts = ["12/4", "11/5", "10/6"]
    len_bases = [2, 3]
    row_resets = [False, True]

    grid = []
    for cm in ctrl_msb_opts:
        for ol in one_literal_opts:
            for f in fmts:
                for lb in len_bases:
                    for rr in row_resets:
                        grid.append((cm, ol, f, lb, rr))

    # process each file
    for path in args.files:
        # read data
        data = open(path, "rb").read()

        # choose workers
        import multiprocessing as mp
        maxw = mp.cpu_count()
        n_workers = max(1, min(args.workers if args.workers else maxw, maxw))

        # run pool
        results = []
        with ProcessPoolExecutor(max_workers=n_workers, initializer=_init_worker, initargs=(data,)) as ex:
            futs = [ex.submit(worker_eval, p) for p in grid]
            for fu in as_completed(futs):
                results.append(fu.result())

        # sort best first
        results.sort(key=lambda t: t[0], reverse=True)

        # print top N
        print(f"\nLZSS sweep: {os.path.basename(path)}  (top {args.topn})")
        for i, (s, p, h, v, sz, outsz) in enumerate(results[:args.topn], 1):
            cm, ol, f, lb, rr = p
            print(f"{i:2d}. S={s:9.1f}  H|Δ|={h:7.1f} V|Δ|={v:7.1f}  ctrl_msb={int(cm)}  one=lit?={int(ol)}  fmt={f} len_base={lb} row_reset={int(rr)}")

if __name__ == "__main__":
    main()

