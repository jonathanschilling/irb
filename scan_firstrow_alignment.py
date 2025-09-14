# import standard modules
import os, sys, argparse
from typing import Tuple, Optional, List

# define a minimal MSB bit reader with start bit alignment
class BitReaderMSB:
    # initialize with data and start bit position
    def __init__(self, data: bytes, start_bitpos: int = 0):
        # store buffer
        self.data = data
        # set bit position
        self.bp = start_bitpos

    # read one MSB-first bit; return -1 on EOF
    def read1(self) -> int:
        # compute byte index
        bi = self.bp // 8
        # if beyond end, return EOF
        if bi >= len(self.data):
            # return sentinel
            return -1
        # within-byte bit index
        bpos = self.bp % 8
        # fetch byte
        b = self.data[bi]
        # extract MSB-first bit
        val = (b >> (7 - bpos)) & 1
        # advance global bit position
        self.bp += 1
        # return bit
        return val

    # read n MSB-first bits; return -1 on EOF
    def readn(self, n: int) -> int:
        # initialize value
        v = 0
        # loop over bits
        for _ in range(n):
            # get bit
            b = self.read1()
            # handle EOF
            if b < 0:
                # return failure
                return -1
            # shift and accumulate
            v = (v << 1) | b
        # return value
        return v

# inverse LOCO-I residual mapping
def inv_loco_map(v: int) -> int:
    # even -> positive
    if (v & 1) == 0:
        # return v//2
        return v // 2
    # odd -> negative
    return -((v + 1) // 2)

# decode a single Rice(k) codeword
def rice_decode(br: BitReaderMSB, k: int) -> int:
    # count zeros in unary prefix
    q = 0
    # loop until a 1 bit
    while True:
        # read bit
        b = br.read1()
        # stop on EOF
        if b < 0:
            # return failure
            return -1
        # accumulate zeros
        if b == 0:
            # increment run
            q += 1
            # continue looping
            continue
        # break on 1
        break
    # read remainder
    r = br.readn(k) if k > 0 else 0
    # return failure if EOF
    if r < 0:
        # indicate failure
        return -1
    # return combined value
    return (q << k) | r

# attempt to decode a single row with left predictor and literal first pixel
def try_first_row(data: bytes, width: int, align: int, k: int) -> Tuple[bool, int, float]:
    # create a bit reader aligned as requested
    br = BitReaderMSB(data, start_bitpos=align)
    # read literal 16-bit first sample
    p0 = br.readn(16)
    # fail on EOF
    if p0 < 0:
        # return failure tuple
        return (False, 0, 0.0)
    # set current sample
    prev = p0
    # residual error count
    failures = 0
    # simple smoothness accumulator
    total_abs = 0
    # decode remaining samples
    for _ in range(1, width):
        # read Rice codeword
        v = rice_decode(br, k)
        # handle failure
        if v < 0:
            # count failure and stop
            failures += 1
            # break out
            break
        # invert LOCO-I mapping
        e = inv_loco_map(v)
        # reconstruct using left predictor
        cur = (prev + e) & 0xFFFF
        # accumulate absolute difference
        total_abs += abs(cur - prev)
        # update prev
        prev = cur
    # compute bits consumed
    bits_used = br.bp - align
    # compute mean abs difference
    mad = total_abs / max(1, (width - 1))
    # success if no failures and we produced full row
    ok = (failures == 0)
    # return tuple
    return (ok, bits_used, mad)

# process multiple files and report best alignment/k per file
def main():
    # parse CLI args
    ap = argparse.ArgumentParser(description="Scan first-row alignment and Rice k across multiple frames")
    # add file list
    ap.add_argument("files", nargs="+", help="compressed frame files (*.bin)")
    # add width
    ap.add_argument("--width", type=int, default=640, help="image width (default 640)")
    # parse
    args = ap.parse_args()

    # iterate files
    for p in args.files:
        # read data
        data = open(p, "rb").read()
        # track best candidate
        best = None
        # scan alignments 0..7 and k=3..5
        for a in range(8):
            for k in (3, 4, 5):
                # try decoding the first row
                ok, bits, mad = try_first_row(data, args.width, a, k)
                # score by ok flag, bits used, and low mad
                score = (1 if ok else 0) * 1_000_000 + bits - 10 * mad
                # update best
                if best is None or score > best[0]:
                    # store record
                    best = (score, a, k, ok, bits, mad)
        # unpack best record
        _, a, k, ok, bits, mad = best
        # print summary
        print(f"{os.path.basename(p)}  best_align={a} k={k} ok={ok} bits_used={bits} mean|d|={mad:.2f}")

# standard guard
if __name__ == "__main__":
    # run the tool
    main()

