# import modules from the standard library
import sys, argparse, math, os
from typing import Optional, Tuple, List

# define image geometry defaults
W_DEFAULT = 640
H_DEFAULT = 480

# define a tiny PGM-16 writer (big-endian as per Netpbm)
def write_pgm16(path: str, width: int, height: int, data_u16_le: bytes):
    # compute expected size in bytes
    expected = width * height * 2
    # verify data length
    if len(data_u16_le) != expected:
        # raise an exception if size mismatches
        raise ValueError(f"PGM writer: got {len(data_u16_le)} bytes, expected {expected}")
    # convert little-endian to big-endian for PGM
    # swap bytes for each 16-bit sample
    be = bytearray(expected)
    # iterate over pixels
    for i in range(0, expected, 2):
        # swap low/high
        be[i] = data_u16_le[i+1]
        be[i+1] = data_u16_le[i]
    # open file for binary write
    with open(path, "wb") as f:
        # write PGM header
        f.write(f"P5\n{width} {height}\n65535\n".encode("ascii"))
        # write big-endian pixel data
        f.write(be)

# define a bit reader for MSB-first with arbitrary start bit offset
class BitReaderMSB:
    # initialize with data and starting bit position (byte_offset*8 + bit_align)
    def __init__(self, data: bytes, start_bitpos: int = 0):
        # store data buffer
        self.data = data
        # store absolute bit position
        self.bp = start_bitpos

    # read a single bit (MSB-first inside each byte), return -1 on EOF
    def read1(self) -> int:
        # compute byte index
        bi = self.bp // 8
        # stop at end of buffer
        if bi >= len(self.data):
            # return EOF sentinel
            return -1
        # compute bit position within the byte
        bpos = self.bp % 8
        # fetch the byte
        b = self.data[bi]
        # extract MSB-first bit
        bit = (b >> (7 - bpos)) & 1
        # advance global bit position
        self.bp += 1
        # return bit value
        return bit

    # read n bits as an integer (MSB-first), return -1 on EOF
    def readn(self, n: int) -> int:
        # initialize accumulator
        v = 0
        # loop over n bits
        for _ in range(n):
            # read one bit
            b = self.read1()
            # handle EOF
            if b < 0:
                # return failure
                return -1
            # shift in the bit
            v = (v << 1) | b
        # return composed value
        return v

# decode a single Rice(k) codeword (unary quotient + k-bit remainder)
def rice_decode(br: BitReaderMSB, k: int) -> int:
    # count unary zeros until a one appears
    q = 0
    # loop over unary prefix
    while True:
        # read next bit
        b = br.read1()
        # fail if out of bits
        if b < 0:
            # signal error
            return -1
        # continue counting zeros
        if b == 0:
            # increment count
            q += 1
            # continue scanning
            continue
        # stop at the first one
        break
    # read k-bit remainder (0 if k==0)
    r = br.readn(k) if k > 0 else 0
    # fail if we could not read remainder
    if r < 0:
        # signal error
        return -1
    # combine quotient and remainder
    return (q << k) | r

# inverse LOCO-I mapping: from non-negative v to signed residual e
def inv_loco_map(v: int) -> int:
    # if v is even, residual is v//2
    if (v & 1) == 0:
        # return positive residual
        return v // 2
    # if v is odd, residual is -(v+1)//2
    return -((v + 1) // 2)

# clamp an integer to 16-bit unsigned range
def clamp_u16(x: int) -> int:
    # bound to [0, 65535]
    if x < 0:
        # lower clamp
        return 0
    if x > 65535:
        # upper clamp
        return 65535
    # return as-is if already in range
    return x

# fetch neighbor pixels safely
def get_A(row: List[int], x: int) -> int:
    # left neighbor
    return row[x - 1] if x > 0 else 0

def get_B(prev_row: Optional[List[int]], x: int) -> int:
    # above neighbor
    return prev_row[x] if prev_row is not None else 0

def get_C(prev_row: Optional[List[int]], x: int) -> int:
    # up-left neighbor
    return prev_row[x - 1] if (prev_row is not None and x > 0) else 0

# LOCO-I MED predictor
def predict_med(A: int, B: int, C: int) -> int:
    # compute gradient predictor as in JPEG-LS
    if C >= max(A, B):
        # min(A, B)
        return min(A, B)
    elif C <= min(A, B):
        # max(A, B)
        return max(A, B)
    else:
        # A + B - C
        return A + B - C

# simple left predictor
def predict_left(A: int, B: int, C: int) -> int:
    # return left neighbor
    return A

# decode a full frame under one hypothesis; returns (ok, out_bytes_le, bits_used, stats)
def decode_frame_loco_rice(
    data: bytes,
    width: int,
    height: int,
    align_bits: int,
    k: int,
    predictor: str,
    firstpix_mode: str
) -> Tuple[bool, bytes, int, dict]:
    # create MSB-first bit reader starting at chosen bit alignment
    br = BitReaderMSB(data, start_bitpos=align_bits)
    # choose predictor function
    pred_fn = predict_med if predictor == "med" else predict_left
    # prepare output buffer as a list of rows of ints
    rows: List[List[int]] = []
    # residual decode failures counter
    failures = 0
    # iterate over image rows
    for y in range(height):
        # get previous row if any
        prev = rows[y - 1] if y > 0 else None
        # initialize current row with zeros
        row = [0] * width
        # handle first pixel of row according to mode
        if firstpix_mode == "row_literal16" or (firstpix_mode == "frame_literal16" and y == 0):
            # read a 16-bit literal sample for the first pixel
            p0 = br.readn(16)
            # fail on EOF
            if p0 < 0:
                # return unsuccessful decode
                return (False, b"", br.bp, {"error": f"EOF on literal y={y}"})
            # clamp to 16-bit
            row[0] = clamp_u16(p0)
            # set start column for residuals
            start_x = 1
        else:
            # otherwise, predict first pixel from neighbors and read residual
            # compute neighbors
            A = get_A(row, 0)
            B = get_B(prev, 0)
            C = get_C(prev, 0)
            # compute prediction
            P = pred_fn(A, B, C)
            # decode one Rice codeword
            val = rice_decode(br, k)
            # fail on EOF
            if val < 0:
                # return unsuccessful decode
                return (False, b"", br.bp, {"error": f"EOF on first residual y={y}"})
            # invert LOCO-I mapping
            e = inv_loco_map(val)
            # reconstruct pixel
            row[0] = clamp_u16(P + e)
            # set start column
            start_x = 1
        # decode remaining pixels in the row
        for x in range(start_x, width):
            # compute neighbors
            A = get_A(row, x)
            B = get_B(prev, x)
            C = get_C(prev, x)
            # predict using selected predictor
            P = pred_fn(A, B, C)
            # decode one Rice codeword
            val = rice_decode(br, k)
            # check for EOF
            if val < 0:
                # count a failure and return
                failures += 1
                return (False, b"", br.bp, {"error": f"EOF at y={y} x={x}", "failures": failures})
            # map to signed residual
            e = inv_loco_map(val)
            # reconstruct pixel
            row[x] = clamp_u16(P + e)
        # append completed row
        rows.append(row)

    # flatten rows to bytes in little-endian 16-bit
    out = bytearray(width * height * 2)
    # write each pixel value
    idx = 0
    for y in range(height):
        # iterate columns
        for x in range(width):
            # fetch value
            v = rows[y][x]
            # write little-endian order
            out[idx] = v & 0xFF
            out[idx + 1] = (v >> 8) & 0xFF
            # advance pointer
            idx += 2

    # compute gradients for a quick plausibility metric
    # count adjacent differences horizontally
    diffs = 0
    # accumulate absolute differences
    total_abs = 0
    # iterate rows
    for y in range(height):
        # iterate columns except the last
        for x in range(width - 1):
            # compute absolute difference
            d = abs(rows[y][x + 1] - rows[y][x])
            # accumulate
            total_abs += d
            # increment counter
            diffs += 1
    # compute mean absolute gradient
    mean_abs_grad = (total_abs / diffs) if diffs > 0 else 0.0

    # build stats dictionary
    stats = {
        "bits_used": br.bp - align_bits,
        "bytes_used": (br.bp - align_bits + 7) // 8,
        "failures": failures,
        "mean_abs_grad": mean_abs_grad
    }
    # return success, output bytes, bits used, and stats
    return (True, bytes(out), br.bp, stats)

# score a decode result: prefer full size, high bit consumption, and smooth output
def score_result(ok: bool, out_len: int, expected_len: int, stats: dict) -> float:
    # if not ok or size mismatch, penalize heavily
    if not ok or out_len != expected_len:
        # return a very low score
        return -1e9
    # get bytes used
    bu = stats.get("bytes_used", 0)
    # get mean gradient (lower is smoother)
    mag = stats.get("mean_abs_grad", 1e9)
    # compute score as bytes_used minus a small penalty for roughness
    # the constants are heuristic; we only need relative ordering
    return bu - 0.05 * mag

# run the sweep over a compact grid of hypotheses
def run_sweep(path: str, width: int, height: int, outdir: str):
    # read the compressed file
    data = open(path, "rb").read()
    # compute expected output size in bytes
    expected = width * height * 2
    # prepare result records
    results = []
    # ensure output directory exists
    os.makedirs(outdir, exist_ok=True)

    # define parameter grids
    aligns = list(range(8))                     # 0..7
    ks = [3, 4, 5]                              # plausible Rice parameters
    predictors = ["left", "med"]                # left vs LOCO-I MED
    firstmodes = ["frame_literal16", "row_literal16"]  # literal modes

    # iterate over combinations
    for a in aligns:
        for k in ks:
            for pred in predictors:
                for fm in firstmodes:
                    # decode with this hypothesis
                    ok, out_bytes, bits_used, stats = decode_frame_loco_rice(
                        data, width, height, a, k, pred, fm
                    )
                    # compute score
                    score = score_result(ok, len(out_bytes), expected, stats)
                    # build a short label
                    label = f"a{a}_k{k}_{pred}_{fm}"
                    # store record
                    results.append((score, ok, label, out_bytes, stats))

    # sort results by descending score
    results.sort(key=lambda r: r[0], reverse=True)

    # print top-8 summaries and write artifacts
    print(f"File: {path}")
    print("Top candidates:")
    topN = min(8, len(results))
    for i in range(topN):
        # unpack record
        score, ok, label, out_bytes, stats = results[i]
        # compute size status
        size_ok = (len(out_bytes) == expected)
        # print a summary line
        print(f"  {i+1:2d}. {label:28s} ok={ok} size_ok={size_ok} bytes_used={stats.get('bytes_used',0)} mean|d|={stats.get('mean_abs_grad',0):.2f} score={score:.1f}")
        # if successful, write outputs
        if ok and size_ok:
            # compose base filename
            base = os.path.join(outdir, os.path.basename(path) + f".{label}")
            # write raw 16-bit little-endian
            with open(base + ".raw16le", "wb") as w:
                # write bytes
                w.write(out_bytes)
            # write PGM-16 view
            write_pgm16(base + ".pgm", width, height, out_bytes)

# main entry point
def main():
    # set up CLI
    ap = argparse.ArgumentParser(description="Sweep LOCO-I/Rice hypotheses over a compressed IR frame")
    # add positional file argument(s)
    ap.add_argument("files", nargs="+", help="compressed frame files (*.bin)")
    # add width argument
    ap.add_argument("--width", type=int, default=W_DEFAULT, help="image width (default 640)")
    # add height argument
    ap.add_argument("--height", type=int, default=H_DEFAULT, help="image height (default 480)")
    # add output directory
    ap.add_argument("--outdir", type=str, default="decoded_candidates", help="directory for outputs")
    # parse arguments
    args = ap.parse_args()

    # run sweep for each file
    for p in args.files:
        # run the sweep on the file
        run_sweep(p, args.width, args.height, args.outdir)

# standard script guard
if __name__ == "__main__":
    # invoke main
    main()

