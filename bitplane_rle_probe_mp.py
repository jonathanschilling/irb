# import modules
import argparse, os
from concurrent.futures import ProcessPoolExecutor, as_completed
import multiprocessing as mp

# geometry
W, H = 640, 480
OUT_LEN = W * H * 2

# shared data
_DATA = None

# init worker
def _init_worker(data: bytes):
    global _DATA; _DATA = data

# bit reader
class BitReader:
    def __init__(self, order="msb", start_bit=0):
        self.order = order
        self.bp = start_bit
    def read1(self):
        bi = self.bp // 8
        if bi >= len(_DATA): return -1
        bpos = self.bp % 8
        b = _DATA[bi]
        bit = (b >> (7 - bpos)) & 1 if self.order == "msb" else (b >> bpos) & 1
        self.bp += 1
        return bit
    def readn(self, n):
        v = 0
        for i in range(n):
            b = self.read1()
            if b < 0: return -1
            v = (v << 1) | b if self.order == "msb" else (v | (b << i))
        return v

# read Exp-Golomb(k=0) codeword (aka unsigned Exp-Golomb)
def read_exp_golomb(br: BitReader):
    zeros = 0
    while True:
        b = br.read1()
        if b < 0: return -1
        if b == 0:
            zeros += 1
            continue
        break
    if zeros == 0:
        return 0
    suffix = br.readn(zeros)
    if suffix < 0: return -1
    return (1 << zeros) - 1 + suffix

# compute gradients for scoring
def img_metrics(buf: bytearray, w: int, h: int):
    hsum = vsum = 0
    hcnt = vcnt = 0
    for y in range(h):
        base = y * w * 2
        for x in range(w):
            i = base + 2 * x
            p = buf[i] | (buf[i+1] << 8)
            if x + 1 < w:
                j = i + 2
                q = buf[j] | (buf[j+1] << 8)
                hsum += abs(q - p); hcnt += 1
            if y + 1 < h:
                k = ((y + 1) * w + x) * 2
                r = buf[k] | (buf[k+1] << 8)
                vsum += abs(r - p); vcnt += 1
    hgrad = hsum / hcnt if hcnt else 1e9
    vgrad = vsum / vcnt if vcnt else 1e9
    return hgrad, vgrad

# reconstruct one row given bitplane RLE settings
def decode_row_planes(br: BitReader, plane_order_msb_first: bool, start_bit_value: int):
    row = [0] * W
    planes = range(15, -1, -1) if plane_order_msb_first else range(0, 16)
    for bitpos in planes:
        cur_bit = start_bit_value
        filled = 0
        while filled < W:
            rl = read_exp_golomb(br)
            if rl < 0: return None
            run = rl + 1
            take = min(run, W - filled)
            if cur_bit:
                for x in range(filled, filled + take):
                    row[x] |= (1 << bitpos)
            filled += take
            cur_bit ^= 1
    return row

# decode whole frame under a parameter set
def decode_frame(order, plane_msb_first, start_bit0):
    br = BitReader(order=order, start_bit=0)
    out = bytearray(OUT_LEN)
    oi = 0
    for y in range(H):
        row = decode_row_planes(br, plane_msb_first, start_bit0)
        if row is None:
            return None
        for x in range(W):
            val = row[x] & 0xFFFF
            out[oi] = val & 0xFF
            out[oi+1] = (val >> 8) & 0xFF
            oi += 2
    return out

# worker
def worker_eval(params):
    order, plane_msb_first, start_bit0 = params
    img = decode_frame(order, plane_msb_first, start_bit0)
    if img is None:
        return (-1e12, params, 1e9, 1e9)
    h, v = img_metrics(img, W, H)
    imb = abs(h - v) / max(1.0, max(h, v))
    score = - (2.0 * max(h, v) + 1.0 * imb)
    return (score, params, h, v)

# main
def main():
    ap = argparse.ArgumentParser(description="Bitplane RLE (Exp-Golomb) probe")
    ap.add_argument("files", nargs="+")
    ap.add_argument("--workers", type=int, default=0)
    ap.add_argument("--topn", type=int, default=6)
    args = ap.parse_args()

    orders = ["msb", "lsb"]
    plane_orders = [True, False]   # True = MSB->LSB, False = LSB->MSB
    starts = [0, 1]                # starting bit value

    grid = []
    for o in orders:
        for pm in plane_orders:
            for st in starts:
                grid.append((o, pm, st))

    for path in args.files:
        data = open(path, "rb").read()
        import multiprocessing as mp
        maxw = mp.cpu_count()
        n_workers = max(1, min(args.workers if args.workers else maxw, maxw))

        results = []
        with ProcessPoolExecutor(max_workers=n_workers, initializer=_init_worker, initargs=(data,)) as ex:
            futs = [ex.submit(worker_eval, p) for p in grid]
            for fu in as_completed(futs):
                results.append(fu.result())

        results.sort(key=lambda t: t[0], reverse=True)
        print(f"\nBitplane RLE probe: {os.path.basename(path)} (top {args.topn})")
        for i, (s, p, h, v) in enumerate(results[:args.topn], 1):
            o, pm, st = p
            print(f"{i:2d}. S={s:9.1f}  H|Δ|={h:7.1f} V|Δ|={v:7.1f}  order={o}  plane_MSBfirst={int(pm)}  startbit={st}")

if __name__ == "__main__":
    main()

