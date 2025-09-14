# import modules for CLI and number crunching
import sys, argparse, math
from collections import Counter

# define a helper to read a file into bytes
def read_bytes(path):
    # open the file in binary mode
    with open(path, "rb") as f:
        # return contents
        return f.read()

# define a helper to compute shannon entropy (bits/byte)
def shannon_entropy(bs: bytes) -> float:
    # construct a histogram of byte values
    c = Counter(bs)
    # compute total count as float
    n = float(len(bs))
    # handle empty input
    if n == 0:
        # return zero
        return 0.0
    # accumulate -p*log2(p) across symbols
    return -sum((v/n) * math.log2(v/n) for v in c.values())

# define a function to extract a bit at (byte_index, bit_index) for a given order
def get_bit(byte_val: int, bitpos: int, order: str) -> int:
    # compute which bit to test based on order
    if order == "msb":
        # for MSB-first, bit 0 is the 0x80 position
        shift = 7 - bitpos
    else:
        # for LSB-first, bit 0 is the 0x01 position
        shift = bitpos
    # extract the bit and return
    return (byte_val >> shift) & 1

# define a generator that yields bits from a byte buffer at a given bit alignment and order
def bit_stream(buf: bytes, align_bits: int, order: str):
    # initialize bit index including alignment offset
    bit_index = align_bits
    # compute total number of bits available
    total_bits = len(buf) * 8
    # iterate until we exhaust the data
    while bit_index < total_bits:
        # compute the byte index and bit position within the byte
        bidx = bit_index // 8
        # compute within-byte bit index (0..7)
        bpos = bit_index % 8
        # fetch the current byte value
        b = buf[bidx]
        # yield the requested bit
        yield get_bit(b, bpos, order)
        # advance to the next bit
        bit_index += 1

# define a probe that scans the stream as if it were Rice-coded with parameter k
def probe_rice(buf: bytes, k: int, order: str, align_bits: int, max_symbols: int = 200000):
    # create a bit iterator with chosen alignment and bit order
    bits = bit_stream(buf, align_bits, order)
    # initialize counters for stats
    symbols = 0
    # count of unary zeros per symbol
    unary_counts = Counter()
    # count of remainder values
    remainder_counts = Counter()
    # track how many times we managed to read a full (unary + remainder)
    full_pairs = 0
    # track how many bits we consumed if we were Rice-decoding
    consumed_bits = 0
    # loop over symbols up to a limit
    while symbols < max_symbols:
        # count unary prefix: number of 0s until a 1
        z = 0
        # loop over zeros
        while True:
            # get next bit or stop on exhaustion
            try:
                # read one bit
                b = next(bits)
            except StopIteration:
                # end of data: stop gracefully
                symbols = max_symbols
                # break out of unary scan
                break
            # consume a bit
            consumed_bits += 1
            # if bit is one, unary terminates
            if b == 1:
                # record unary length and break
                unary_counts[z] += 1
                # break the unary loop
                break
            # otherwise increment zero run
            z += 1
            # protect against absurdly long runs (likely not Rice)
            if z > 1024:
                # treat as failure to parse
                symbols = max_symbols
                # break out
                break
        # check if we exhausted the stream
        if symbols >= max_symbols:
            # stop top-level loop
            break
        # read k-bit remainder (if k>0)
        rem = 0
        # iterate remainder bits
        for i in range(k):
            # try to get the next bit
            try:
                # fetch bit
                b = next(bits)
            except StopIteration:
                # out of bits: stop counting
                symbols = max_symbols
                # break remainder loop
                break
            # accumulate remainder for msb/lsb order
            if order == "msb":
                # shift left and or-in bit
                rem = (rem << 1) | b
            else:
                # place bit at position i
                rem |= (b << i)
            # count bit in consumed stats
            consumed_bits += 1
        # record a complete pair only if we read all remainder bits
        if symbols < max_symbols:
            # increment full pair count
            full_pairs += 1
            # record remainder value
            remainder_counts[rem] += 1
        # increment processed symbols
        symbols += 1
    # compute simple quality metrics
    # fraction of complete (unary + k) pairs we could parse before limit/EOF
    completeness = 0.0 if symbols == 0 else full_pairs / symbols
    # estimate geometric parameter p for unary distribution (mean run length m ⇒ p ≈ 1/(m+1))
    # compute weighted mean of zero runs
    total_unary = sum(v for v in unary_counts.values())
    # avoid division by zero
    mean_unary = sum(z * c for z, c in unary_counts.items()) / total_unary if total_unary else 0.0
    # estimate parameter p from mean
    p_est = 1.0 / (mean_unary + 1.0) if mean_unary > 0 else 0.0
    # measure how close remainders are to uniform over [0, 2^k)
    # compute expected uniform count
    if k > 0 and total_unary > 0:
        # ideal uniform probability for each remainder
        ideal = 1.0 / (1 << k)
        # compute chi-square error normalized
        chi = 0.0
        # loop over all possible remainders
        for r in range(1 << k):
            # observed frequency
            obs = remainder_counts.get(r, 0) / total_unary
            # accumulate squared error
            chi += (obs - ideal) ** 2 / max(ideal, 1e-12)
        # convert to a bounded score (smaller better → map to 0..1)
        remainder_uniformity = 1.0 / (1.0 + chi)
    else:
        # if no remainder bits, treat as perfect uniform (trivial)
        remainder_uniformity = 1.0
    # return a dict with metrics
    return {
        "k": k,
        "order": order,
        "align_bits": align_bits,
        "symbols_examined": symbols,
        "pairs_parsed": full_pairs,
        "completeness": completeness,
        "mean_unary_zeros": mean_unary,
        "p_est": p_est,
        "remainder_uniformity": remainder_uniformity,
        "unary_top": unary_counts.most_common(8),
        "remainder_top": remainder_counts.most_common(8),
        "consumed_bits_if_rice": consumed_bits
    }

# define a heuristic that labels the stream as Rice-like or not based on probe results
def label_from_probes(results):
    # score each probe by completeness + remainder uniformity
    scored = []
    # iterate over probe outputs
    for r in results:
        # compute a composite score with mild weight on completeness
        score = 0.6 * r["completeness"] + 0.4 * r["remainder_uniformity"]
        # collect tuple for sorting
        scored.append((score, r))
    # sort by descending score
    scored.sort(reverse=True, key=lambda x: x[0])
    # select the best candidate
    best_score, best = scored[0]
    # decide if Rice-like: require decent completeness and reasonable uniformity
    rice_like = (best["completeness"] > 0.85) and (best["remainder_uniformity"] > 0.6)
    # return decision and best probe
    return rice_like, best_score, best

# define a lightweight huffman-ness heuristic from byte-level symptoms
def huffman_signatures(buf: bytes):
    # compute byte histogram
    c = Counter(buf)
    # gather counts for typical bit-pack residues
    hi_bias = sum(c.get(x, 0) for x in (0x80, 0xC0, 0xE0, 0xF0, 0x60, 0x40, 0x20, 0x10, 0x00))
    # compute top 10 bytes
    top10 = c.most_common(10)
    # approximate bias score as fraction of those residues among all bytes
    bias_score = hi_bias / max(1, len(buf))
    # return a dict with simple indicators
    return {
        "entropy_bits_per_byte": shannon_entropy(buf),
        "bias_power_of_two_fraction": bias_score,
        "top10": top10
    }

# define the command-line interface
def main():
    # set up argument parser
    ap = argparse.ArgumentParser(description="Entropy-coder probe: distinguish Rice/Golomb from Huffman-like bit-packing")
    # add file path argument
    ap.add_argument("file", help="compressed bytestream to analyze")
    # add max symbols for Rice probing
    ap.add_argument("--max-symbols", type=int, default=200000, help="cap number of symbols to scan (default 200k)")
    # parse arguments
    args = ap.parse_args()

    # read the input file
    buf = read_bytes(args.file)
    # print basic info
    print(f"input_size={len(buf)} bytes")

    # compute and print huffman-like signatures
    sig = huffman_signatures(buf)
    print(f"entropy(bits/byte) = {sig['entropy_bits_per_byte']:.3f}")
    print(f"bias_power_of_two_fraction = {sig['bias_power_of_two_fraction']:.4f}")
    print(f"top10_bytes = {[(hex(v), n) for (v, n) in sig['top10']]}")

    # prepare Rice probe parameter grid
    orders = ["msb", "lsb"]
    k_values = [0, 1, 2, 3, 4, 5]
    alignments = list(range(8))

    # collect all probe results
    results = []
    # iterate over parameter combinations
    for order in orders:
        # loop over possible k
        for k in k_values:
            # loop over bit alignments
            for a in alignments:
                # run a probe
                r = probe_rice(buf, k=k, order=order, align_bits=a, max_symbols=args.max_symbols)
                # append result
                results.append(r)

    # decide based on probe results
    rice_like, best_score, best = label_from_probes(results)

    # report best Rice candidate
    print("\nbest_rice_probe:")
    print(f"  order={best['order']} k={best['k']} align_bits={best['align_bits']}")
    print(f"  completeness={best['completeness']:.3f} remainder_uniformity={best['remainder_uniformity']:.3f}")
    print(f"  mean_unary_zeros={best['mean_unary_zeros']:.2f}  p_est≈{best['p_est']:.3f}")
    print(f"  pairs_parsed={best['pairs_parsed']} symbols_examined={best['symbols_examined']}")
    print(f"  unary_top={best['unary_top'][:5]} remainder_top={best['remainder_top'][:5]}")
    print(f"  consumed_bits_if_rice={best['consumed_bits_if_rice']} (~{best['consumed_bits_if_rice']/8:.0f} bytes)")

    # print a simple verdict
    print("\nverdict:")
    if rice_like:
        # if Rice-like probe looks good, say so
        print("  Stream LOOKS LIKE Rice/Golomb (unary + k-bit). Try building a real decoder with these parameters.")
    else:
        # otherwise lean toward Huffman-like bit-packed coding
        print("  Stream does NOT behave like Rice/Golomb under any alignment/k. Likely Huffman-style bit-packed entropy coding.")

# run main if executed as a script
if __name__ == "__main__":
    # call main function
    main()

