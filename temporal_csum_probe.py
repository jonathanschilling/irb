# Put source code comments before the code they refer to and not at the end of the line they refer to.
# Test temporal prediction: decode each frame as pure Rice residuals (no spatial predictor)
# and cumulatively add them across frames. If the codec is frame-delta based, the scene will emerge.
# Usage: python temporal_csum_probe.py image_*.bin

import sys, os, struct

W, H = 640, 480
N = W * H

class BitReaderMSB:
    # Initialize MSB-first bit reader
    def __init__(self, data):
        self.data = data
        self.bitpos = 0
        self.nbits = len(data)*8
    # Read unary q
    def read_unary_q(self):
        q = 0
        while True:
            if self.bitpos >= self.nbits: return -1
            b = self.data[self.bitpos>>3]; bit=7-(self.bitpos&7); self.bitpos+=1
            if ((b>>bit)&1)==0: q+=1
            else: return q
    # Read n bits
    def readn(self,n):
        if n==0: return 0
        if self.bitpos + n > self.nbits: return -1
        v=0
        for _ in range(n):
            b = self.data[self.bitpos>>3]; bit=7-(self.bitpos&7); self.bitpos+=1
            v = (v<<1) | ((b>>bit)&1)
        return v

def rice_signed_plus(br,k):
    # Read unary q and k-bit remainder; map to signed
    q = br.read_unary_q()
    if q<0: return 0, False
    r = br.readn(k) if k>0 else 0
    if r<0: return 0, False
    u = (q<<k)|r
    if (u&1)==0: s=u>>1
    else:        s=-((u>>1)+1)
    return s, True

def decode_residuals(path, k):
    data = open(path,"rb").read()
    br = BitReaderMSB(data)
    out = [0]*N
    for i in range(N):
        d, ok = rice_signed_plus(br,k)
        if not ok:
            return None
        out[i]=d
    return out

def save_pgm(fn, arr):
    with open(fn,"wb") as f:
        f.write(f"P5\n{W} {H}\n65535\n".encode("ascii"))
        for v in arr:
            f.write(((v & 0xFFFF).to_bytes(2,"big")))

def main():
    if len(sys.argv)<2:
        print("Usage: python temporal_csum_probe.py image_*.bin")
        sys.exit(1)
    files = sorted(sys.argv[1:])
    # Try both k=4 and k=5
    for k in (4,5):
        acc = [0]*N
        for idx,fn in enumerate(files,1):
            res = decode_residuals(fn, k)
            if res is None:
                print(f"{fn}: decode failed (k={k})")
                break
            # Save raw residuals (biased for visibility by +32768)
            vis = [((r + 32768) & 0xFFFF) for r in res]
            out_res = os.path.splitext(os.path.basename(fn))[0] + f".residual_k{k}.pgm"
            save_pgm(out_res, vis)
            print(f" wrote {out_res}")
            # Accumulate temporally
            for i in range(N):
                acc[i] = (acc[i] + res[i]) & 0xFFFF
            if idx in (2,3,5,10,len(files)):
                out_acc = f"temporal_csum_{idx}_frames_k{k}.pgm"
                save_pgm(out_acc, acc)
                print(f" wrote {out_acc}")

if __name__=="__main__":
    main()

