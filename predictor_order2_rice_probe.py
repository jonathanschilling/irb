# Put source code comments before the code they refer to and not at the end of the line they refer to.
# This script tests two vertical order-2 predictors with Rice residuals:
#   1) UP2:          P(y,x) = V(y-2,x)
#   2) V2 (2nd ord): P(y,x) = clamp16( 2*V(y-1,x) - V(y-2,x) )
# It bootstraps the first two rows via baseline modes and tries k in {4,5,6}, plus optional output left-shifts.
# Usage: python predictor_order2_rice_probe.py image_*.bin

import sys, os, math, struct
from itertools import product
from multiprocessing import Pool, cpu_count

# Define geometry
W, H = 640, 480

# Implement an MSB-first bit reader
class BitReaderMSB:
    # Initialize with bytes
    def __init__(self, data):
        self.data = data
        self.bitpos = 0
        self.nbits = len(data)*8

    # Read n bits; -1 on EOF
    def readn(self, n):
        if n <= 0:
            return 0
        if self.bitpos + n > self.nbits:
            return -1
        v = 0
        for _ in range(n):
            bidx = self.bitpos >> 3
            bit  = 7 - (self.bitpos & 7)
            self.bitpos += 1
            v = (v<<1) | ((self.data[bidx]>>bit)&1)
        return v

    # Return current bit position
    def tell_bits(self):
        return self.bitpos

# Decode one Rice(k) signed value using "plus" mapping (even->+, odd->-)
def rice_read_signed_plus(br, k):
    q = 0
    while True:
        b = br.readn(1)
        if b < 0: return None, False
        if b == 0:
            q += 1; continue
        break
    r = 0
    if k > 0:
        r = br.readn(k)
        if r < 0: return None, False
    u = (q<<k) | r
    if (u & 1) == 0:
        s = u >> 1
    else:
        s = -((u >> 1) + 1)
    return s, True

# Apply predictor by mode
def predict(mode, img, y, x):
    if y >= 2:
        up1 = img[y-1][x]
        up2 = img[y-2][x]
        if mode == "UP2":
            return up2
        # V2 predictor = 2*up1 - up2
        p = (2*up1 - up2) & 0xFFFF
        return p
    elif y == 1:
        # Only one row available; fall back to simple up if exists else 0
        return img[y-1][x]
    else:
        # Top row baseline = 0
        return 0

# Reconstruct full frame with given parameters
def reconstruct(data, k, mode, bootstrap, out_shift_bits):
    br = BitReaderMSB(data)
    img = [[0]*W for _ in range(H)]

    # Handle the first two rows based on bootstrap policy
    # bootstrap choices:
    #   "zero":     first two rows predicted from 0 with Rice residuals
    #   "row0_lit": send row 0 as 14-bit literals (<<2), row 1 from 0 via Rice
    #   "row01_lit": both row 0 and row 1 as 14-bit literals (<<2)
    def read_row_literal14(y):
        for x in range(W):
            v14 = br.readn(14)
            if v14 < 0: return False
            img[y][x] = (v14 << 2) & 0xFFFF
        return True

    if bootstrap == "row01_lit":
        if not read_row_literal14(0): return None, False, br.tell_bits()
        if not read_row_literal14(1): return None, False, br.tell_bits()
        start_y = 2
    elif bootstrap == "row0_lit":
        if not read_row_literal14(0): return None, False, br.tell_bits()
        start_y = 1
    else:
        start_y = 0

    # Decode remaining rows via chosen predictor
    for y in range(start_y, H):
        for x in range(W):
            P = predict(mode, img, y, x)
            d, ok = rice_read_signed_plus(br, k)
            if not ok:
                return None, False, br.tell_bits()
            img[y][x] = (P + d) & 0xFFFF

    # Optional output left shift to account for 12/14-bit true depth
    if out_shift_bits in (2,4):
        mask = (0xFFFF >> out_shift_bits)  # for detecting clipping if needed
        for y in range(H):
            for x in range(W):
                img[y][x] = (img[y][x] << out_shift_bits) & 0xFFFF

    return img, True, br.tell_bits()

# Compute simple image metrics for ranking
def score(img):
    if img is None: return 1e12, 1e12, 1e12, 0.0
    hsum = vsum = 0
    hcnt = vcnt = 0
    for y in range(H):
        for x in range(W):
            v = img[y][x]
            if x+1 < W:
                hsum += abs(v - img[y][x+1]); hcnt += 1
            if y+1 < H:
                vsum += abs(v - img[y+1][x]); vcnt += 1
    Hgrad = hsum / max(1,hcnt)
    Vgrad = vsum / max(1,vcnt)
    imb = abs(Hgrad - Vgrad) / max(1.0, max(Hgrad, Vgrad))
    S = 2.5*max(Hgrad, Vgrad) + 1.0*imb
    return S, Hgrad, Vgrad, imb

# Save PGM16 (big-endian)
def save_pgm(fn, img):
    with open(fn,"wb") as f:
        f.write(f"P5\n{W} {H}\n65535\n".encode("ascii"))
        for y in range(H):
            for x in range(W):
                f.write(struct.pack(">H", img[y][x] & 0xFFFF))

# Worker wrapper
def worker(args):
    data, k, mode, bootstrap, shift = args
    img, ok, bits = reconstruct(data, k, mode, bootstrap, shift)
    if not ok:
        return (1e12, k, mode, bootstrap, shift, bits, None)
    S, Hg, Vg, imb = score(img)
    return (S, k, mode, bootstrap, shift, bits, img)

def main(files):
    payloads = [(fn, open(fn,"rb").read()) for fn in files]

    ks = [4,5,6]
    modes = ["UP2", "V2"]
    boots = ["zero", "row0_lit", "row01_lit"]
    shifts = [0,2,4]

    grid = list(product(ks, modes, boots, shifts))

    from multiprocessing import Pool, cpu_count
    for fn, data in payloads:
        tasks = [(data, k, m, b, s) for (k,m,b,s) in grid]
        with Pool(processes=max(1,cpu_count()-1)) as pool:
            results = pool.map(worker, tasks)

        results.sort(key=lambda t: t[0])
        print(f"\nFile: {os.path.basename(fn)} (best 8)")
        for i,(S,k,m,b,sh,bits,img) in enumerate(results[:8],1):
            print(f" {i}. S={S:9.1f}  k={k}  pred={m:>3}  boot={b:>8}  shift={sh}  bits≈{bits}  Bpp≈{bits/(W*H):.2f}")

        # Save the top image
        S,k,m,b,sh,bits,img = results[0]
        if img is not None:
            out = os.path.splitext(os.path.basename(fn))[0] + f".order2_{m}_k{k}_{b}_sh{sh}.pgm"
            save_pgm(out, img)
            print(f" wrote {out}")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python predictor_order2_rice_probe.py image_*.bin")
        sys.exit(1)
    main(sys.argv[1:])


