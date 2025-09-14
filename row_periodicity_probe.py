# Put source code comments before the code they refer to and not at the end of the line they refer to.
# This script decodes rows with a simple up+Rice(k) model (no resets) and records:
#   - bits consumed per row
#   - mean vertical/horizontal gradients per row
# It then runs an autocorrelation to find periodicity (tile/reset cadence).
# Usage: python row_periodicity_probe.py image_10848.bin

import sys, math

W, H = 640, 480

class BitReaderMSB:
    # Initialize bit reader for MSB-first reads
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
            bit = 7 - (self.bitpos & 7)
            self.bitpos += 1
            v = (v<<1) | ((self.data[bidx]>>bit)&1)
        return v
    # Current bit position
    def tell_bits(self):
        return self.bitpos

def rice_read_signed_plus(br, k):
    # Read unary quotient
    q = 0
    while True:
        b = br.readn(1)
        if b < 0:
            return None, False
        if b == 0:
            q += 1
            continue
        break
    # Read remainder
    r = 0
    if k>0:
        r = br.readn(k)
        if r < 0:
            return None, False
    u = (q<<k) | r
    # "plus" signed map
    if (u & 1) == 0:
        s = u >> 1
    else:
        s = -((u >> 1) + 1)
    return s, True

def decode_rows_return_stats(data, k):
    br = BitReaderMSB(data)
    prev_row = [0]*W
    row_bits = []
    row_hgrad = []
    row_vgrad = []
    ok_all = True

    for y in range(H):
        start = br.tell_bits()
        row = [0]*W
        # Decode row with 'up' predictor
        for x in range(W):
            up = prev_row[x]
            d, ok = rice_read_signed_plus(br, k)
            if not ok:
                ok_all = False
                break
            row[x] = (up + d) & 0xFFFF
        used = br.tell_bits() - start
        row_bits.append(used)

        # Horizontal gradient mean
        hsum = 0
        for x in range(1, W):
            hsum += abs(row[x]-row[x-1])
        row_hgrad.append(hsum/max(1,W-1))

        # Vertical gradient mean
        vsum = 0
        for x in range(W):
            vsum += abs(row[x]-prev_row[x])
        row_vgrad.append(vsum/max(1,W))

        prev_row = row
        if not ok_all:
            break

    return ok_all, row_bits, row_hgrad, row_vgrad

def autocorr_period(seq, maxp=128):
    # Compute simple auto-correlation and return best period > 1
    best_p, best_r = 1, -1e9
    n = len(seq)
    mean = sum(seq)/max(1,n)
    var = sum((x-mean)**2 for x in seq)/max(1,n)
    if var <= 1e-9:
        return 1, 0.0
    for p in range(2, min(maxp, n//3)):
        num = 0.0
        den = 0.0
        for i in range(n-p):
            a = seq[i]-mean
            b = seq[i+p]-mean
            num += a*b
        r = num / (var*(n-p))
        if r > best_r:
            best_r, best_p = r, p
    return best_p, best_r

def main(fn):
    data = open(fn,"rb").read()
    for k in (4,5,6):
        ok, row_bits, row_h, row_v = decode_rows_return_stats(data, k)
        print(f"\n{k=}: ok={ok} rows={len(row_bits)}")
        if len(row_bits) >= 32:
            p_bits, r_bits = autocorr_period(row_bits, 128)
            p_v, r_v = autocorr_period(row_v, 128)
            p_h, r_h = autocorr_period(row_h, 128)
            print(f"  period(bits/row) ~ {p_bits}  corr={r_bits:.3f}")
            print(f"  period(V|Δ|)     ~ {p_v}  corr={r_v:.3f}")
            print(f"  period(H|Δ|)     ~ {p_h}  corr={r_h:.3f}")
        # Print quick ranges
        if row_bits:
            print(f"  bits/row: min={min(row_bits)} max={max(row_bits)} mean≈{sum(row_bits)/len(row_bits):.1f}")
        if row_v:
            print(f"  V|Δ| mean across rows ≈ {sum(row_v)/len(row_v):.1f}")
        if row_h:
            print(f"  H|Δ| mean across rows ≈ {sum(row_h)/len(row_h):.1f}")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python row_periodicity_probe.py image_10848.bin")
        sys.exit(1)
    main(sys.argv[1])

