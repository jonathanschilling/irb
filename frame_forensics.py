#!/usr/bin/env python

# import required modules
import sys, math, collections, itertools

# import numpy for vectorized stats
import numpy as np

# compute Shannon entropy (bits/byte)
def entropy(bs: bytes) -> float:
    # build histogram of byte values
    counts = collections.Counter(bs)
    # total count
    n = float(len(bs))
    # compute sum p*log2(p)
    s = 0.0
    for c in counts.values():
        # probability of the symbol
        p = c / n
        # accumulate entropy contribution
        s += -p * math.log2(p)
    # return entropy
    return s

# compute longest common prefix length across frames
def longest_common_prefix(frames: list[bytes]) -> int:
    # get minimum length to bound the search
    m = min(len(b) for b in frames)
    # iterate over positions until mismatch
    for i in range(m):
        # get the byte in the first frame
        b0 = frames[0][i]
        # check all frames at this position
        if any(fr[i] != b0 for fr in frames[1:]):
            # return index of first mismatch
            return i
    # all bytes match to min length
    return m

# compute longest common suffix length across frames
def longest_common_suffix(frames: list[bytes]) -> int:
    # get minimum length
    m = min(len(b) for b in frames)
    # iterate from the end
    for k in range(1, m + 1):
        # reference byte from the first frame
        b0 = frames[0][-k]
        # compare across frames
        if any(fr[-k] != b0 for fr in frames[1:]):
            # return length matched so far
            return k - 1
    # whole min length matches
    return m

# compute per-position variability (how often the same byte repeats across frames)
def per_position_variability(frames: list[bytes]) -> np.ndarray:
    # ensure equal lengths (use shortest)
    L = min(len(b) for b in frames)
    # stack into 2D array (n_frames × L)
    arr = np.stack([np.frombuffer(b[:L], dtype=np.uint8) for b in frames], axis=0)
    # compute number of unique values per column
    uniq = np.apply_along_axis(lambda col: len(np.unique(col)), 0, arr)
    # return unique counts (1 means constant field)
    return uniq

# rank candidate marker bytes by how often they are followed by runs of the same value
def rank_marker_candidates(frames: list[bytes], max_check: int = 512) -> list[tuple[int, float, int]]:
    # keep score per marker
    scores = collections.Counter()
    # iterate frames
    for bs in frames:
        # scan up to max_check random-like positions (or full if small)
        limit = min(len(bs) - 3, max_check)
        # iterate positions
        for i in range(limit):
            # read potential marker
            m = bs[i]
            # read next two bytes as a candidate (count,value) pattern
            c = bs[i+1]
            v = bs[i+2]
            # test whether the following bytes are mostly v
            run = 0
            # iterate over the run length window
            for j in range(min(c, 32)):
                # break if not v
                if i+3+j >= len(bs) or bs[i+3+j] != v:
                    # stop counting run
                    break
                # increment run
                run += 1
            # accumulate score weighed by run
            if run >= 4:
                # award points for likely RLE packet
                scores[m] += run
    # prepare ranked list (marker, score, rank)
    ranked = [(m, float(s), idx) for idx, (m, s) in enumerate(scores.most_common())]
    # return ranked markers
    return ranked

# try simple transforms on a byte string
def simple_transforms(bs: bytes) -> dict[str, bytes]:
    # precompute bit-reverse for 0..255
    rev = bytes(int(f"{b:08b}"[::-1], 2) for b in range(256))
    # build nibble-swap table
    nib = bytes(((b & 0x0F) << 4) | ((b & 0xF0) >> 4) for b in range(256))
    # identity transform
    out = {"id": bs}
    # bit-reverse transform
    out["bitrev"] = bytes(rev[b] for b in bs)
    # nibble-swap transform
    out["nibbleswap"] = bytes(nib[b] for b in bs)
    # xor with common patterns
    for k in (0x00, 0x55, 0xAA, 0xFF):
        # label for this XOR
        out[f"xor_{k:02X}"] = bytes(b ^ k for b in bs)
    # return dictionary of transformed streams
    return out

# compute between-frame similarity after a transform (Jaccard on k-grams and bytewise repeat rate)
def similarity_report(frames: list[bytes], k: int = 3) -> dict[str, float]:
    # guard for empty case
    if len(frames) < 2:
        # default metrics
        return {"jaccard_k3": 0.0, "byte_match_rate": 0.0}
    # take two consecutive frames
    a, b = frames[0], frames[1]
    # build sets of k-grams
    A = {a[i:i+k] for i in range(0, max(len(a)-k+1, 0))}
    B = {b[i:i+k] for i in range(0, max(len(b)-k+1, 0))}
    # compute jaccard index
    j = (len(A & B) / len(A | B)) if (A or B) else 0.0
    # compute bytewise match rate on the shortest length
    L = min(len(a), len(b))
    # count equal positions
    eq = sum(1 for i in range(L) if a[i] == b[i])
    # compute rate
    r = eq / L if L else 0.0
    # return metrics
    return {"jaccard_k3": j, "byte_match_rate": r}

# main entry point
def main():
    # require at least two frames
    if len(sys.argv) < 2:
        # print usage
        print(f"Usage: {sys.argv[0]} frame1.bin [frame2.bin ...]")
        sys.exit(1)

    # load frames
    frames = []
    # iterate file paths
    for p in sys.argv[1:]:
        # read file as bytes
        with open(p, "rb") as f:
            # append to list
            frames.append(f.read())

    # report basic sizes
    print("# frames:", len(frames))
    print("sizes:", [len(b) for b in frames])

    # compute entropy per frame
    ents = [entropy(b) for b in frames]
    print("entropy(bits/byte):", [f"{e:.3f}" for e in ents])

    # compute common prefix/suffix lengths
    lcp = longest_common_prefix(frames)
    lcs = longest_common_suffix(frames)
    print("longest_common_prefix:", lcp)
    print("longest_common_suffix:", lcs)

    # compute per-position variability on a trimmed slice (exclude stable prefix/suffix)
    Lmin = min(len(b) for b in frames)
    start = lcp
    end = Lmin - lcs if lcs > 0 else Lmin
    core = [b[start:end] for b in frames]
    print("core_region:", start, "to", end, "(len =", end - start, ")")

    # build variability array (unique count per position)
    var = per_position_variability(core)
    # write variability to CSV for inspection
    with open("per_position_variability.csv", "w") as f:
        # write header
        f.write("pos,unique_values\n")
        # iterate positions
        for i, u in enumerate(var.tolist()):
            # write row
            f.write(f"{i},{u}\n")
    print("wrote per_position_variability.csv")

    # rank marker candidates on the core region
    ranked = rank_marker_candidates(core, max_check=min(100000, end - start - 3))
    # write top markers to CSV
    with open("marker_candidates.csv", "w") as f:
        # write header
        f.write("rank,marker_hex,score\n")
        # iterate top 32 markers
        for rank, (m, score, _) in enumerate(ranked[:32], 1):
            # write row
            f.write(f"{rank},0x{m:02X},{score:.1f}\n")
    print("wrote marker_candidates.csv")

    # try simple transforms on the core region of the first two frames and compare similarity
    reports = {}
    # build transform sets for frame 0 and 1
    t0 = simple_transforms(core[0])
    t1 = {name: simple_transforms(core[1])[name] for name in t0.keys()}
    # compute similarity per transform name
    for name in t0.keys():
        # build two-frame list for similarity
        rep = similarity_report([t0[name], t1[name]], k=3)
        # store metrics
        reports[name] = rep

    # print similarity results (higher = closer; look for a transform that increases similarity)
    print("transform_similarity:")
    for name, rep in reports.items():
        # print jaccard and bytewise match rate
        print(f"  {name:12s}  jaccard_k3={rep['jaccard_k3']:.3f}  byte_match={rep['byte_match_rate']:.3f}")

    # bonus: dump a quick byte histogram of the core region of frame 0
    counts = collections.Counter(core[0])
    with open("byte_histogram_core.csv", "w") as f:
        # write header
        f.write("byte_hex,count\n")
        # iterate bytes 0..255
        for b in range(256):
            # count occurrences
            # f.write(f"{b},{counts.get(b,0)}\n")
            f.write(f"0x{b:02X},{counts.get(b,0)}\n")
    print("wrote byte_histogram_core.csv")

# standard guard
if __name__ == "__main__":
    # run main
    main()

