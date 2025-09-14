# Put source code comments before the code they refer to and not at the end of the line they refer to.
# This script tests parity-dependent k values (k_even, k_odd) for a fixed vertical order-2 predictor:
#   predictor = "UP2" or "V2"
# It bootstraps the first two rows from zero and tries k_even, k_odd in {4,5,6}.
# Usage: python parity_k_probe.py --pred UP2 image_*.bin

import sys, os, math, struct
from itertools import product

W, H = 640, 480

class BitReaderMSB:
    def __init__(self, data):
        self.data = data
        self.bitpos = 0
        self.nbits = len(data)*8
    def readn(self, n):
        if n <= 0: return 0
        if self.bitpos + n > self.nbits: return -1
        v = 0
        for _ in range(n):
            bidx = self.bitpos >> 3
            bit  = 7 - (self.bitpos & 7)
            self.bitpos += 1
            v = (v<<1) | ((self.data[bidx]>>bit)&1)
        return v

def rice_signed_plus(br, k):
    q = 0
    while True:
        b = br.readn(1)
        if b < 0: return None, False
        if b == 0: q += 1; continue
        break
    r = 0
    if k>0:
        r = br.readn(k)
        if r < 0: return None, False
    u = (q<<k) | r
    if (u & 1) == 0: s = u >> 1
    else:            s = -((u >> 1) + 1)
    return s, True

def reconstruct(data, pred_mode, k_even, k_odd):
    br = BitReaderMSB(data)
    img = [[0]*W for _ in range(H)]

    def P(y,x):
        if y >= 2:
            up1 = img[y-1][x]
            up2 = img[y-2][x]
            if pred_mode == "UP2":
                return up2
            else:
                return (2*up1 - up2) & 0xFFFF
        elif y == 1:
            return img[y-1][x]
        else:
            return 0

    for y in range(H):
        k = k_even if (y & 1) == 0 else k_odd
        for x in range(W):
            p = P(y,x)
            d, ok = rice_signed_plus(br, k)
            if not ok: return None, False
            img[y][x] = (p + d) & 0xFFFF

    return img, True

def score(img):
    if img is None: return 1e12,0,0,0
    hsum=vsum=hcnt=vcnt=0
    for y in range(H):
        for x in range(W):
            v = img[y][x]
            if x+1<W:
                hsum += abs(v - img[y][x+1]); hcnt+=1
            if y+1<H:
                vsum += abs(v - img[y+1][x]); vcnt+=1
    Hgrad = hsum/max(1,hcnt)
    Vgrad = vsum/max(1,vcnt)
    imb = abs(Hgrad - Vgrad)/max(1.0,max(Hgrad,Vgrad))
    S = 2.5*max(Hgrad,Vgrad) + 1.0*imb
    return S,Hgrad,Vgrad,imb

def main():
    if len(sys.argv) < 3 or sys.argv[1] != "--pred":
        print("Usage: python parity_k_probe.py --pred {UP2|V2} image_*.bin")
        sys.exit(1)
    pred_mode = sys.argv[2]
    files = sys.argv[3:]
    ks = [4,5,6]
    grid = list(product(ks, ks))

    for fn in files:
        data = open(fn,"rb").read()
        best=[]
        for ke,ko in grid:
            img, ok = reconstruct(data, pred_mode, ke, ko)
            if not ok:
                continue
            S,Hg,Vg,imb = score(img)
            best.append((S,ke,ko,Hg,Vg,imb))
        best.sort(key=lambda t:t[0])
        print(f"\n{os.path.basename(fn)}  predictor={pred_mode}")
        for i,(S,ke,ko,Hg,Vg,imb) in enumerate(best[:8],1):
            print(f" {i}. S={S:9.1f}  k_even={ke} k_odd={ko}  H|Δ|={Hg:7.1f} V|Δ|={Vg:7.1f} imb={imb:5.3f}")

if __name__=="__main__":
    main()

