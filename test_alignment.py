# import Counter for stats
from collections import Counter

# define a minimal unary-only probe (k=0) that scans N symbols from a given bit alignment and bit order
def unary_probe_bits(buf: bytes, order: str, align_bits: int, max_symbols: int = 500000):
    # initialize counters
    symbols, zeros, ones = 0, 0, 0
    # compute bit positions
    bitpos = align_bits
    # total bits available
    total_bits = len(buf) * 8

    # helper to read one bit at global bitpos
    def get_bit(bp):
        # compute byte index
        bi = bp // 8
        # stop at EOF
        if bi >= len(buf):
            # return sentinel and stop
            return -1
        # compute position in byte
        bpos = bp % 8
        # fetch byte
        b = buf[bi]
        # MSB-first bit select
        if order == "msb":
            # select MSB-first bit
            return (b >> (7 - bpos)) & 1
        else:
            # select LSB-first bit
            return (b >> bpos) & 1

    # loop over symbols
    while symbols < max_symbols and bitpos < total_bits:
        # read unary zeros terminated by one
        z = 0
        # consume zeros
        while True:
            # get next bit
            bit = get_bit(bitpos)
            # break on EOF
            if bit < 0:
                # exit outer loop
                symbols = max_symbols
                # break inner loop
                break
            # advance bitpos
            bitpos += 1
            # count zeros / stop on one
            if bit == 0:
                # increment zero run
                z += 1
                # continue
                continue
            # record we saw the terminating one
            ones += 1
            # break inner loop
            break
        # add zeros to counter
        zeros += z
        # increment symbol count
        symbols += 1

    # compute bits consumed and average unary length
    consumed_bits = bitpos - align_bits
    mean_unary = zeros / max(1, symbols)
    # return a simple report
    return {"order": order, "align": align_bits, "symbols": symbols, "consumed_bits": consumed_bits, "mean_unary": mean_unary}

# run the probe on a representative file
buf = open("image_10848.bin","rb").read()
reports = []
for order in ("msb","lsb"):
    # try all 8 alignments
    for a in range(8):
        # run probe
        reports.append(unary_probe_bits(buf, order, a, max_symbols=200000))
# sort by consumed bits descending
reports.sort(key=lambda r: r["consumed_bits"], reverse=True)
# print the top few
for r in reports[:6]:
    # print alignment summary
    print(r)

