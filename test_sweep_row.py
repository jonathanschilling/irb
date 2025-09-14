# import numpy for stats
import numpy as np

# define a bit reader for MSB-first bits starting at a byte offset and bit alignment
class BitReader:
    # initialize with data, start byte offset, and bit alignment inside the first byte (0..7)
    def __init__(self, data: bytes, byte_off: int = 0, align_bits: int = 0):
        # store data buffer
        self.data = data
        # set absolute bit position
        self.bp = byte_off * 8 + align_bits

    # read one MSB-first bit, return -1 on EOF
    def read1(self) -> int:
        # compute byte index
        bi = self.bp // 8
        # stop at EOF
        if bi >= len(self.data):
            # return sentinel
            return -1
        # compute within-byte position
        bpos = self.bp % 8
        # fetch byte
        b = self.data[bi]
        # extract MSB-first bit
        bit = (b >> (7 - bpos)) & 1
        # advance bit position
        self.bp += 1
        # return bit value
        return bit

    # read n MSB-first bits as integer, return -1 on EOF
    def readn(self, n: int) -> int:
        # initialize value
        v = 0
        # loop n times
        for _ in range(n):
            # read one bit
            b = self.read1()
            # return -1 if out of data
            if b < 0:
                return -1
            # shift and or
            v = (v << 1) | b
        # return composed integer
        return v

# decode one Rice(k) non-negative integer (unary quotient + k-bit remainder), return -1 on failure
def rice_decode(br: BitReader, k: int) -> int:
    # count unary zeros until a one
    q = 0
    # loop over unary prefix
    while True:
        # read a bit
        b = br.read1()
        # fail at EOF
        if b < 0:
            return -1
        # continue counting zeros
        if b == 0:
            q += 1
            continue
        # stop at the first one
        break
    # read remainder
    r = br.readn(k) if k > 0 else 0
    # fail on EOF
    if r < 0:
        return -1
    # return combined value
    return (q << k) | r

# inverse of LOCO-I mapped residuals (maps non-negative v to signed e)
def inv_loco_map(v: int) -> int:
    # if even, e = v//2; if odd, e = -(v+1)//2
    return (v // 2) if (v & 1) == 0 else (-(v + 1) // 2)

# try to reconstruct the first row with Rice(k) and left predictor
def try_first_row(data: bytes, width: int = 640, align_bits: int = 0, k: int = 0):
    # create MSB-first bit reader at given alignment
    br = BitReader(data, 0, align_bits)
    # prepare output row
    row = np.zeros(width, dtype=np.int32)
    # decode first pixel as a raw 16-bit literal (common variant) — we’ll try both: literal16 or residual
    # start with a variant that treats first pixel as a literal16
    # read 16 bits for pixel 0
    p0 = br.readn(16)
    # if literal failed, abort this variant
    if p0 < 0:
        return None, br.bp, "EOF on p0"
    # clamp to 16-bit unsigned
    row[0] = p0 & 0xFFFF
    # decode the rest as Rice-mapped residuals relative to left neighbor
    for x in range(1, width):
        # read one Rice(k) value
        v = rice_decode(br, k)
        # abort on failure
        if v < 0:
            return None, br.bp, f"EOF at x={x}"
        # invert LOCO-I mapping
        e = inv_loco_map(v)
        # reconstruct pixel with left predictor
        row[x] = (row[x-1] + e) & 0xFFFF
    # compute simple smoothness metrics
    gx = np.abs(np.diff(row)).mean()
    # return row, bits consumed, and a small report string
    return row.astype(np.uint16), br.bp, f"mean|Δ|={gx:.2f}"

# sweep alignments and k for a quick plausibility check
def sweep_row_probe(path: str):
    # read bytes
    data = open(path, "rb").read()
    # prepare candidates
    best = None
    # iterate alignments 0..7 and k=0..4
    for a in range(8):
        for k in range(5):
            # attempt reconstruction
            row, bits, info = try_first_row(data, 640, a, k)
            # skip on failure
            if row is None:
                continue
            # compute neighbor correlation
            r = np.corrcoef(row[:-1].astype(np.float64), row[1:].astype(np.float64))[0,1]
            # score: high correlation & low mean difference preferred
            score = r
            # keep best by score
            if (best is None) or (score > best[0]):
                best = (score, a, k, bits, info)
    # print best candidate if any
    if best:
        # unpack best tuple
        score, a, k, bits, info = best
        print(f"best_row_probe: align={a} k={k} corr={score:.3f} bits_used={bits} {info}")
    else:
        # print failure message
        print("no plausible row reconstruction with simple Rice+left predictor")

# example usage (change the filename to a representative frame)
sweep_row_probe("image_10848.bin")

