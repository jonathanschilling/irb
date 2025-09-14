# import required modules
import argparse, os

# define image geometry defaults
W, H = 640, 480

# define a simple MSB-first bit reader with an absolute bit position
class BitReader:
    # initialize with data and a starting bit position
    def __init__(self, data: bytes, start_bit=0):
        # store buffer and bit position
        self.data, self.bp = data, start_bit

    # read 1 MSB-first bit; return -1 on EOF
    def read1(self) -> int:
        # compute byte index
        bi = self.bp // 8
        # check EOF
        if bi >= len(self.data):
            # return sentinel
            return -1
        # compute bit position within the byte
        bpos = self.bp % 8
        # fetch the byte
        b = self.data[bi]
        # extract MSB-first bit
        bit = (b >> (7 - bpos)) & 1
        # advance the bit position
        self.bp += 1
        # return bit value
        return bit

    # read n MSB-first bits as integer; return -1 on EOF
    def readn(self, n: int) -> int:
        # initialize value
        v = 0
        # read n bits
        for _ in range(n):
            # read one bit
            b = self.read1()
            # handle EOF
            if b < 0:
                # return failure
                return -1
            # shift and or-in the bit
            v = (v << 1) | b
        # return composed value
        return v

# decode a single Rice(k) non-negative codeword (unary quotient + k-bit remainder)
def rice_decode(br: BitReader, k: int) -> int:
    # count unary zeros until 1
    q = 0
    # loop until we find the terminating one
    while True:
        # read one bit
        b = br.read1()
        # handle EOF
        if b < 0:
            # signal failure
            return -1
        # if zero, keep counting
        if b == 0:
            # increment zero count
            q += 1
            # continue
            continue
        # break on the terminating one
        break
    # read k-bit remainder
    r = br.readn(k) if k > 0 else 0
    # handle EOF on remainder
    if r < 0:
        # failure
        return -1
    # combine quotient and remainder
    return (q << k) | r

# inverse LOCO-I mapping (non-negative v -> signed residual e)
def inv_loco_map(v: int) -> int:
    # even v maps to positive e
    if (v & 1) == 0:
        # return v//2
        return v // 2
    # odd v maps to negative e
    return -((v + 1) // 2)

# clamp to 16-bit unsigned range
def clamp_u16(x: int) -> int:
    # lower clamp
    if x < 0:
        # return zero
        return 0
    # upper clamp
    if x > 65535:
        # return max
        return 65535
    # ok as-is
    return x

# write a 16-bit PGM (big-endian pixel order per Netpbm)
def write_pgm16(path: str, width: int, height: int, data_u16_le: bytes):
    # compute expected byte size
    expected = width * height * 2
    # verify length
    if len(data_u16_le) != expected:
        # raise error if mismatch
        raise ValueError(f"PGM writer: got {len(data_u16_le)} bytes, expected {expected}")
    # convert LE to BE for PGM
    be = bytearray(expected)
    # swap every 16-bit sample
    for i in range(0, expected, 2):
        # swap low/high
        be[i] = data_u16_le[i+1]
        be[i+1] = data_u16_le[i]
    # open for writing
    with open(path, "wb") as f:
        # write header
        f.write(f"P5\n{width} {height}\n65535\n".encode("ascii"))
        # write data
        f.write(be)

# decode one frame with left+DPCM, Rice(k=5), row-literal16; record row bit usage
def decode_and_audit(data: bytes, width=640, height=480, start_align_bits=0, k=5):
    # create bit reader at requested start alignment
    br = BitReader(data, start_align_bits)
    # prepare output buffer (little-endian u16)
    out = bytearray(width * height * 2)
    # store per-row bitpos snapshots
    row_bits = []
    # iterate rows
    for y in range(height):
        # record row start bit position
        row_start = br.bp
        # read first pixel as 16-bit literal
        p0 = br.readn(16)
        # bail out on EOF
        if p0 < 0:
            # return failure with partial stats
            return False, None, row_bits, br.bp
        # write first pixel (little endian)
        # compute output index
        idx = (y * width + 0) * 2
        # store low byte
        out[idx] = p0 & 0xFF
        # store high byte
        out[idx+1] = (p0 >> 8) & 0xFF
        # keep previous sample for left predictor
        prev = p0 & 0xFFFF
        # decode rest of the row
        for x in range(1, width):
            # decode Rice(k) value
            v = rice_decode(br, k)
            # handle EOF
            if v < 0:
                # return failure with partial stats
                return False, None, row_bits, br.bp
            # invert LOCO-I map
            e = inv_loco_map(v)
            # reconstruct current sample
            cur = (prev + e) & 0xFFFF
            # write sample to output
            # compute index
            idx = (y * width + x) * 2
            # write low byte
            out[idx] = cur & 0xFF
            # write high byte
            out[idx+1] = (cur >> 8) & 0xFF
            # update left neighbor
            prev = cur
        # record row end info
        row_end = br.bp
        # append (row, bits_used_in_row, end_alignment_mod8)
        row_bits.append((y, row_end - row_start, row_end % 8))
    # compute total bits and bytes used
    total_bits = br.bp - start_align_bits
    total_bytes = (total_bits + 7) // 8
    # return success, decoded image bytes, row stats, and final bitpos
    return True, bytes(out), row_bits, br.bp

# main entry point
def main():
    # set up CLI
    ap = argparse.ArgumentParser(description="Audit row boundaries and trailer for line-Rice coded IR frames")
    # add positional files
    ap.add_argument("files", nargs="+", help="compressed frame files")
    # add width and height
    ap.add_argument("--width", type=int, default=W, help="image width")
    ap.add_argument("--height", type=int, default=H, help="image height")
    # add start alignment
    ap.add_argument("--align", type=int, default=0, help="start bit alignment (0..7)")
    # add output folder
    ap.add_argument("--outdir", type=str, default="audit_outputs", help="where to write decoded PGM")
    # parse args
    args = ap.parse_args()

    # ensure output dir
    os.makedirs(args.outdir, exist_ok=True)

    # iterate files
    for path in args.files:
        # read file bytes
        data = open(path, "rb").read()
        # run decoder/auditor
        ok, img, row_bits, end_bp = decode_and_audit(data, args.width, args.height, args.align, k=5)
        # compute used bytes and trailer
        used_bytes = (end_bp - args.align + 7) // 8
        trailer = len(data) - used_bytes
        # print header
        print(f"\n{os.path.basename(path)}")
        # print status
        print(f"  decode_ok={ok}   bytes_used={used_bytes}   file_size={len(data)}   trailer_bytes={trailer}")
        # summarize row alignment statistics
        mod8_counts = {}
        # iterate rows
        for _, _, m in row_bits:
            # count end mod
            mod8_counts[m] = mod8_counts.get(m, 0) + 1
        # print mod8 histogram
        print(f"  row_end_bitpos_mod8: {sorted(mod8_counts.items())}")
        # print min/max/mean bits used per row
        if row_bits:
            # extract bit lengths per row
            lens = [b for (_, b, _) in row_bits]
            # compute stats
            mn, mx, avg = min(lens), max(lens), sum(lens) / len(lens)
            # print stats
            print(f"  bits_per_row: min={mn}  max={mx}  mean≈{avg:.1f}")
        # write PGM if successful
        if ok and img is not None:
            # compose path
            outpgm = os.path.join(args.outdir, os.path.basename(path) + ".rowk5_left.pgm")
            # write file
            write_pgm16(outpgm, args.width, args.height, img)
            # report path
            print(f"  wrote {outpgm}")

# standard guard
if __name__ == "__main__":
    # run main
    main()

