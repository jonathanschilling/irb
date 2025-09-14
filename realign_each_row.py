# import required modules
import argparse, os

# define geometry defaults
W, H = 640, 480

# define MSB-first bit reader
class BitReader:
    # initialize with data and starting bit position
    def __init__(self, data: bytes, start_bit=0):
        # store input and bit position
        self.data, self.bp = data, start_bit

    # read one MSB-first bit, return -1 on EOF
    def read1(self) -> int:
        # compute byte index
        bi = self.bp // 8
        # handle EOF
        if bi >= len(self.data):
            # return sentinel
            return -1
        # compute position inside byte
        bpos = self.bp % 8
        # fetch current byte
        b = self.data[bi]
        # extract bit
        bit = (b >> (7 - bpos)) & 1
        # advance position
        self.bp += 1
        # return bit
        return bit

    # read n bits, return -1 on EOF
    def readn(self, n: int) -> int:
        # initialize accumulator
        v = 0
        # loop n times
        for _ in range(n):
            # read one bit
            b = self.read1()
            # check EOF
            if b < 0:
                # return failure
                return -1
            # shift and accumulate
            v = (v << 1) | b
        # return value
        return v

    # snap to the next byte boundary (do nothing if already aligned)
    def align_next_byte(self):
        # compute remainder bits in current byte
        r = self.bp % 8
        # if not at boundary, advance to next multiple of 8
        if r != 0:
            # add the pad
            self.bp += (8 - r)

# decode one Rice(k) codeword
def rice_decode(br: BitReader, k: int) -> int:
    # count unary zeros
    q = 0
    # loop until 1
    while True:
        # read one bit
        b = br.read1()
        # handle EOF
        if b < 0:
            # return failure
            return -1
        # continue on zero
        if b == 0:
            # increment count
            q += 1
            # loop
            continue
        # stop at one
        break
    # read remainder bits
    r = br.readn(k) if k > 0 else 0
    # handle EOF
    if r < 0:
        # failure
        return -1
    # combine
    return (q << k) | r

# inverse LOCO-I map
def inv_loco_map(v: int) -> int:
    # even -> positive
    if (v & 1) == 0:
        # return v//2
        return v // 2
    # odd -> negative
    return -((v + 1) // 2)

# clamp u16
def clamp_u16(x: int) -> int:
    # lower clamp
    if x < 0:
        # zero
        return 0
    # upper clamp
    if x > 65535:
        # max
        return 65535
    # return value
    return x

# write PGM-16 for viewing
def write_pgm16(path: str, width: int, height: int, data_u16_le: bytes):
    # expected size
    expected = width * height * 2
    # verify length
    if len(data_u16_le) != expected:
        # error
        raise ValueError("bad size")
    # swap to big-endian
    be = bytearray(expected)
    # swap bytes
    for i in range(0, expected, 2):
        # swap
        be[i] = data_u16_le[i+1]
        be[i+1] = data_u16_le[i]
    # write file
    with open(path, "wb") as f:
        # header
        f.write(f"P5\n{width} {height}\n65535\n".encode("ascii"))
        # data
        f.write(be)

# decode with per-row realignment to next byte boundary
def decode_row_realign(data: bytes, width=640, height=480, start_align_bits=0, k=5):
    # create bitreader at provided start
    br = BitReader(data, start_align_bits)
    # prepare output buffer
    out = bytearray(width * height * 2)
    # iterate rows
    for y in range(height):
        # force align to next byte boundary before row start
        br.align_next_byte()
        # read 16-bit literal for first pixel
        p0 = br.readn(16)
        # abort on failure
        if p0 < 0:
            # return None on failure
            return None, br.bp
        # store first pixel
        # compute index
        idx = (y * width + 0) * 2
        # write LE
        out[idx] = p0 & 0xFF
        out[idx+1] = (p0 >> 8) & 0xFF
        # set prev for left predictor
        prev = p0 & 0xFFFF
        # decode remaining pixels
        for x in range(1, width):
            # decode Rice
            v = rice_decode(br, k)
            # on failure, abort
            if v < 0:
                # return None
                return None, br.bp
            # inverse map
            e = inv_loco_map(v)
            # reconstruct
            cur = (prev + e) & 0xFFFF
            # store
            idx = (y * width + x) * 2
            out[idx] = cur & 0xFF
            out[idx+1] = (cur >> 8) & 0xFF
            # advance predictor
            prev = cur
    # return output and final bit position
    return bytes(out), br.bp

# main function
def main():
    # parse CLI args
    ap = argparse.ArgumentParser(description="Decode with forced per-row byte re-alignment")
    # add files
    ap.add_argument("files", nargs="+", help="compressed frame files")
    # add width and height
    ap.add_argument("--width", type=int, default=W, help="image width")
    # add height
    ap.add_argument("--height", type=int, default=H, help="image height")
    # add start alignment
    ap.add_argument("--align", type=int, default=0, help="file start bit alignment (0..7)")
    # parse
    args = ap.parse_args()

    # process each file
    for path in args.files:
        # read bytes
        data = open(path, "rb").read()
        # decode with per-row realignment
        img, end_bp = decode_row_realign(data, args.width, args.height, args.align, k=5)
        # check result
        if img is None:
            # print failure
            print(f"{os.path.basename(path)}  decode_failed  end_bitpos={end_bp}")
            # continue to next
            continue
        # construct output filename
        out_pgm = os.path.basename(path) + ".rowk5_left_realign.pgm"
        # write pgm
        write_pgm16(out_pgm, args.width, args.height, img)
        # print success line
        print(f"{os.path.basename(path)}  decode_ok  end_bytes={(end_bp+7)//8}  wrote {out_pgm}")

# standard guard
if __name__ == "__main__":
    # run main
    main()

