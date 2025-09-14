# Put source code comments before the code they refer to and not at the end of the line they refer to.
# Demultiplex a single Rice stream into two lanes (even/odd rows) and reconstruct with lane-local UP prediction.
# It tests several split strategies and scores the result. Writes the best image for visual inspection.
# Usage: python demux_even_odd_rice.py image_*.bin

import sys, os, struct

# Define geometry
W, H = 640, 480
N_PIX = W * H

# Implement an MSB-first bit reader
class BitReaderMSB:
    # Initialize with bytes
    def __init__(self, data):
        self.data = data
        self.bitpos = 0
        self.nbits = len(data) * 8

    # Read unary quotient (zeros then one); return q or -1 on EOF
    def read_unary_q(self):
        q = 0
        while True:
            if self.bitpos >= self.nbits:
                return -1
            byte = self.data[self.bitpos >> 3]
            bit  = 7 - (self.bitpos & 7)
            self.bitpos += 1
            if ((byte >> bit) & 1) == 0:
                q += 1
            else:
                return q

    # Read n bits; return -1 on EOF
    def readn(self, n):
        if n == 0:
            return 0
        if self.bitpos + n > self.nbits:
            return -1
        v = 0
        for _ in range(n):
            bidx = self.bitpos >> 3
            bit  = 7 - (self.bitpos & 7)
            self.bitpos += 1
            v = (v << 1) | ((self.data[bidx] >> bit) & 1)
        return v

# Decode next Rice(k) signed value with "plus" signed mapping; return (val, ok)
def rice_signed_plus(br, k):
    q = br.read_unary_q()
    if q < 0:
        return 0, False
    r = br.readn(k) if k > 0 else 0
    if r < 0:
        return 0, False
    u = (q << k) | r
    # even -> non-negative, odd -> negative
    if (u & 1) == 0:
        s = u >> 1
    else:
        s = -((u >> 1) + 1)
    return s, True

# Decode N symbols from a single stream; return list of signed residuals or None on failure
def decode_rice_stream(data, k, N):
    br = BitReaderMSB(data)
    out = [0] * N
    for i in range(N):
        d, ok = rice_signed_plus(br, k)
        if not ok:
            return None
        out[i] = d
    return out

# Reconstruct image from two residual lanes assigned to even/odd rows; UP predictor within each lane
def reconstruct_from_lanes(res_even, res_odd):
    img = [[0] * W for _ in range(H)]
    # Fill even rows lane
    i = 0
    for y in range(0, H, 2):
        prev_row = img[y-2] if y >= 2 else None
        for x in range(W):
            up = prev_row[x] if prev_row is not None else 0
            v  = (up + res_even[i]) & 0xFFFF
            img[y][x] = v
            i += 1
    # Fill odd rows lane
    i = 0
    for y in range(1, H, 2):
        prev_row = img[y-2] if y >= 2 else None
        for x in range(W):
            up = prev_row[x] if prev_row is not None else 0
            v  = (up + res_odd[i]) & 0xFFFF
            img[y][x] = v
            i += 1
    return img

# Compute simple gradients for scoring
def score(img):
    hsum=vsum=hcnt=vcnt=0
    for y in range(H):
        row = img[y]
        for x in range(W-1):
            hsum += abs(row[x+1] - row[x]); hcnt += 1
        if y+1 < H:
            nrow = img[y+1]
            for x in range(W):
                vsum += abs(nrow[x] - row[x]); vcnt += 1
    Hgrad = hsum / max(1, hcnt)
    Vgrad = vsum / max(1, vcnt)
    imb   = abs(Hgrad - Vgrad) / max(1.0, max(Hgrad, Vgrad))
    S = 2.5 * max(Hgrad, Vgrad) + 1.0 * imb
    return S, Hgrad, Vgrad, imb

# Save PGM-16 BE
def save_pgm(fn, img):
    with open(fn, "wb") as f:
        f.write(f"P5\n{W} {H}\n65535\n".encode("ascii"))
        for y in range(H):
            for x in range(W):
                f.write(((img[y][x] & 0xFFFF).to_bytes(2, "big")))

def try_demux(path, k):
    data = open(path, "rb").read()
    total = W * H
    # Decode exactly N residuals; if that fails, bail
    res = decode_rice_stream(data, k, total)
    if res is None:
        print("Rice stream decode failed")
        return

    # Build candidates of (even_res, odd_res) as different splits
    # Strategy 1: even-first exact half split
    mid = total // 2
    cand = []
    cand.append(("even_first_half", res[:mid], res[mid:]))

    # Strategy 2: odd-first exact half split
    cand.append(("odd_first_half", res[mid:], res[:mid]))

    # Strategy 3: sweep boundary around mid (±5% of total), step by 256 pixels
    delta = int(0.05 * total)
    for s in range(mid - delta, mid + delta + 1, 256):
        if 0 < s < total:
            cand.append((f"even_first_s{s}", res[:s], res[s:]))
            cand.append((f"odd_first_s{s}",  res[s:], res[:s]))

    # Filter only pairs with exact sizes
    E = (H + 1)//2 * W  # number of even-row pixels
    O = H//2 * W        # number of odd-row pixels
    keep = []
    for tag, ev, od in cand:
        if len(ev) == E and len(od) == O:
            keep.append((tag, ev, od))

    best = []
    for tag, ev, od in keep:
        img = reconstruct_from_lanes(ev, od)
        S,Hg,Vg,imb = score(img)
        best.append((S, tag, Hg, Vg, imb, img))

    if not best:
        print("No candidate had exact lane sizes; widen sweep or adjust E/O math.")
        return

    best.sort(key=lambda t: t[0])
    print(f"\n{os.path.basename(path)}  k={k}  best 8 demux hypotheses:")
    for i,(S, tag, Hg, Vg, imb, _img) in enumerate(best[:8],1):
        print(f" {i}. S={S:9.1f}  tag={tag:>18}  H|Δ|={Hg:7.1f} V|Δ|={Vg:7.1f} imb={imb:5.3f}")

    # Save the winner
    S, tag, Hg, Vg, imb, img = best[0]
    out = os.path.splitext(os.path.basename(path))[0] + f".demux_{tag}_k{k}.pgm"
    save_pgm(out, img)
    print(f" wrote {out}")

def main():
    if len(sys.argv) < 2:
        print("Usage: python demux_even_odd_rice.py image_*.bin")
        sys.exit(1)
    paths = sys.argv[1:]
    for p in paths:
        for k in (4,5):
            try_demux(p, k)

if __name__ == "__main__":
    main()

