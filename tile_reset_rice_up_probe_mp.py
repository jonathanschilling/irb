# Put source code comments before the code they refer to and not at the end of the line they refer to.
# This script brute-forces tile/segment resets for a Rice-coded residual stream with an "up" predictor.
# It sweeps tile heights and widths, k values, and first-row baseline modes, then scores reconstruction.
# Usage: python tile_reset_rice_up_probe_mp.py image_*.bin

import sys, os, math, struct
from multiprocessing import Pool, cpu_count

# Define image dimensions (change if needed)
W, H = 640, 480

# Implement a simple MSB-first bit reader for Python bytes
class BitReaderMSB:
    # Initialize reader with a bytes-like object
    def __init__(self, data):
        self.data = data
        self.bitpos = 0
        self.nbits = len(data) * 8

    # Read n bits as unsigned integer; return -1 on EOF
    def readn(self, n):
        if n <= 0:
            return 0
        if self.bitpos + n > self.nbits:
            return -1
        v = 0
        for _ in range(n):
            byte_index = self.bitpos >> 3
            bit_in_byte = 7 - (self.bitpos & 7)
            self.bitpos += 1
            v = (v << 1) | ((self.data[byte_index] >> bit_in_byte) & 1)
        return v

    # Return current bit position
    def tell_bits(self):
        return self.bitpos

# Decode a single Rice-coded signed residual using "plus" mapping (non-folded).
# Quotient: unary (zeros then a 1), remainder: k fixed bits, value = (q << k) | r
# Signed map: even -> +u/2, odd -> -(u//2 + 1)
def rice_read_signed_plus(br, k):
    # Read unary quotient q (count zeros until we see a 1)
    q = 0
    while True:
        b = br.readn(1)
        if b < 0:
            return None, False
        if b == 0:
            q += 1
            continue
        break
    # Read k-bit remainder
    r = 0
    if k > 0:
        r = br.readn(k)
        if r < 0:
            return None, False
    # Compose unsigned u
    u = (q << k) | r
    # Map to signed using "plus" convention
    if (u & 1) == 0:
        s = u >> 1
    else:
        s = -((u >> 1) + 1)
    return s, True

# Reconstruct an image assuming Rice-coded residuals with "up" predictor, under a tiling/reset policy.
# tile_w, tile_h: dimensions of a reset region; at top of each tile, the "up" reference is reset per baseline_mode
# baseline_mode: "zero" uses 0 for the entire top row of the tile; "seed" tries to decode a DC seed per tile; "none" uses the previous row even across tiles (i.e., no reset)
def reconstruct_rice_up(data, k, tile_w, tile_h, baseline_mode="zero"):
    br = BitReaderMSB(data)
    img = [[0]*W for _ in range(H)]

    # Iterate tiles
    for ty in range(0, H, tile_h):
        for tx in range(0, W, tile_w):
            # Optionally read a per-tile DC seed (Rice-coded), applied as baseline for the first tile row
            seed = 0
            if baseline_mode == "seed":
                val, ok = rice_read_signed_plus(br, k)
                if not ok:
                    return None, False, br.tell_bits()
                seed = val

            # For each row in the tile
            for y in range(ty, min(ty+tile_h, H)):
                # Determine baseline row above; for the first row of the tile, choose per baseline_mode
                for x in range(tx, min(tx+tile_w, W)):
                    # Compute predictor 'up'
                    if y == ty:
                        # Inside first row of tile: choose baseline rule
                        up = 0 if baseline_mode in ("zero","seed") else (img[y-1][x] if y>0 else 0)
                    else:
                        up = img[y-1][x]

                    # Read residual
                    d, ok = rice_read_signed_plus(br, k)
                    if not ok:
                        return None, False, br.tell_bits()

                    # Reconstruct sample (16-bit unsigned container)
                    v = (up + d) & 0xFFFF
                    img[y][x] = v

    return img, True, br.tell_bits()

# Compute quick scores to judge “image-likeness”
def score_image(img):
    if img is None:
        return 1e12, 1e12, 1e12, 1e12
    H_ = len(img)
    W_ = len(img[0])
    # Horizontal absolute gradient mean
    hsum = 0
    cnt = 0
    for y in range(H_):
        row = img[y]
        for x in range(1, W_):
            hsum += abs(row[x] - row[x-1])
            cnt += 1
    Hgrad = hsum / max(1, cnt)

    # Vertical absolute gradient mean
    vsum = 0
    cnt = 0
    for y in range(1, H_):
        r0, r1 = img[y-1], img[y]
        for x in range(W_):
            vsum += abs(r1[x] - r0[x])
            cnt += 1
    Vgrad = vsum / max(1, cnt)

    # Row variance mean
    import statistics as stats
    rvars = []
    for y in range(H_):
        rvars.append(stats.pvariance(img[y]) if W_ > 1 else 0.0)
    row_var_mean = sum(rvars)/len(rvars)

    # A composite “goodness” (lower is better): prefer lower vertical gradients and moderate horizontal structure
    S = Vgrad*4 + Hgrad*1 + (row_var_mean**0.5)*0.02
    return S, Hgrad, Vgrad, row_var_mean

# Save PGM (16-bit big endian) for visual inspection
def save_pgm16(path, img):
    H_ = len(img); W_ = len(img[0])
    with open(path, "wb") as f:
        header = f"P5\n{W_} {H_}\n65535\n".encode("ascii")
        f.write(header)
        for y in range(H_):
            for x in range(W_):
                v = img[y][x] & 0xFFFF
                f.write(struct.pack(">H", v))

# Worker function to try a parameter combo
def worker(args):
    fname, data, k, tile_w, tile_h, baseline = args
    img, ok, bits = reconstruct_rice_up(data, k, tile_w, tile_h, baseline)
    if not ok:
        return (1e12, fname, k, tile_w, tile_h, baseline, bits, None)
    S, Hg, Vg, rv = score_image(img)
    return (S, fname, k, tile_w, tile_h, baseline, bits, img)

def main(files):
    # Read input files into memory once
    payloads = []
    for fn in files:
        with open(fn, "rb") as f:
            payloads.append((fn, f.read()))

    # Parameter grid to explore
    ks = [4,5,6]
    tile_ws = [W, 128, 64, 32]
    tile_hs = [8, 12, 16, 24, 32]
    baselines = ["zero", "seed", "none"]

    # Build tasks
    tasks = []
    for fn, data in payloads:
        for k in ks:
            for tw in tile_ws:
                for th in tile_hs:
                    for bl in baselines:
                        tasks.append((fn, data, k, tw, th, bl))

    # Run in parallel
    with Pool(processes=max(1, cpu_count()-1)) as pool:
        results = pool.map(worker, tasks)

    # Group and report per file
    from collections import defaultdict
    buckets = defaultdict(list)
    for (S, fn, k, tw, th, bl, bits, img) in results:
        buckets[fn].append((S, k, tw, th, bl, bits, img))

    for fn in files:
        best = sorted(buckets[fn], key=lambda t: t[0])[:6]
        print(f"\nFile: {fn} (best 6 by composite score)")
        for i,(S,k,tw,th,bl,bits,img) in enumerate(best,1):
            print(f" {i}. S={S:9.1f}  k={k}  tile={tw}x{th}  baseline={bl:>4}  bits_used≈{bits}  "
                  f"Bpp≈{bits/(W*H):.2f}")
            # Save a preview for the single best
        S,k,tw,th,bl,bits,img = best[0]
        if img is not None:
            out = os.path.splitext(os.path.basename(fn))[0] + f".tile_{tw}x{th}_k{k}_{bl}.pgm"
            save_pgm16(out, img)
            print(f" wrote {out}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python tile_reset_rice_up_probe_mp.py image_*.bin")
        sys.exit(1)
    main(sys.argv[1:])

