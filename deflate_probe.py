# import modules for CLI parsing and decompression
import argparse, sys, zlib, os

# define a helper that tries to decompress with a chosen deflate "mode"
def try_decompress(data: bytes, mode: str):
    # select wbits based on mode: zlib(+15), raw(-15), gzip(+31)
    if mode == "zlib":
        # use standard zlib header
        wbits = zlib.MAX_WBITS
    elif mode == "raw":
        # use raw deflate stream (no wrapper)
        wbits = -zlib.MAX_WBITS
    elif mode == "gzip":
        # use gzip wrapper (zlib supports via MAX_WBITS | 16)
        wbits = zlib.MAX_WBITS | 16
    else:
        # reject unknown mode
        raise ValueError(f"unknown mode: {mode}")

    # create a decompress object
    d = zlib.decompressobj(wbits)
    try:
        # feed all data starting at the chosen offset (handled by caller)
        out = d.decompress(data)
        # flush any remaining buffered output
        out += d.flush()
        # compute how many input bytes were consumed (unused_data is what's left after a full stream)
        consumed = len(data) - len(d.unused_data)
        # return output, consumed count, and no error
        return out, consumed, None
    except Exception as e:
        # return failure with error message
        return b"", 0, str(e)

# define a function that attempts all modes at a given offset and reports matches
def try_all_modes_at_offset(data: bytes, offset: int, expected: int):
    # slice the data at the specified offset
    view = data[offset:]
    # define candidate modes to try
    modes = ("zlib", "raw", "gzip")
    # prepare results list
    results = []
    # iterate over each mode
    for m in modes:
        # attempt decompression
        out, consumed, err = try_decompress(view, m)
        # compute flags for matching the expected size and full consumption
        size_ok = (len(out) == expected)
        consumed_ok = (consumed > 0)
        # append a result record
        results.append({
            "mode": m,
            "offset": offset,
            "ok_size": size_ok,
            "out_len": len(out),
            "consumed": consumed,
            "error": err,
            "data": out if size_ok else b""
        })
    # return the list of results
    return results

# define the main program logic
def main():
    # set up command-line options
    p = argparse.ArgumentParser(description="Brute-force Deflate (zlib/raw/gzip) test harness for 8-bit frames")
    # add positional file arguments (one or more)
    p.add_argument("files", nargs="+", help="input frame files (compressed)")
    # add expected output size option
    p.add_argument("--expected", type=int, default=640*480, help="expected decompressed size (default: 640*480=307200)")
    # add scan toggle
    p.add_argument("--scan", action="store_true", help="scan offsets to find embedded deflate streams")
    # add scan step size in bytes
    p.add_argument("--scan-step", type=int, default=1024, help="scan offset step in bytes (default: 1024)")
    # add maximum scan offset
    p.add_argument("--scan-max", type=int, default=256*1024, help="max scan offset (default: 262144)")
    # add flag to write successful outputs
    p.add_argument("--write", action="store_true", help="write outputs that match expected size")
    # parse arguments
    args = p.parse_args()

    # iterate over each provided file
    for path in args.files:
        # open and read the file as bytes
        with open(path, "rb") as f:
            # load entire file into memory
            blob = f.read()

        # print basic info
        print(f"\n=== {path} ===")
        print(f"input_size={len(blob)} expected_decoded={args.expected}")

        # define a helper to write outputs if requested
        def maybe_write(mode: str, offset: int, payload: bytes):
            # skip if writing not requested
            if not args.write:
                # nothing to do
                return
            # build output filename with mode and offset
            base = os.path.basename(path)
            # construct out path
            out_path = f"{base}.decoded.{mode}.off{offset}.bin"
            # write bytes to disk
            with open(out_path, "wb") as w:
                w.write(payload)
            # report path
            print(f"  wrote: {out_path}")

        # first, try from offset 0 in all three modes
        print("offset=0 (no scan):")
        # run attempts for offset 0
        results0 = try_all_modes_at_offset(blob, 0, args.expected)
        # print a compact summary for each mode
        for r in results0:
            # compute a status string
            status = "MATCH" if r["ok_size"] else "no"
            # print summary line
            print(f"  mode={r['mode']:5s} consumed={r['consumed']:7d} out_len={r['out_len']:7d} ok={status} err={r['error']}")
            # write output if matches
            if r["ok_size"]:
                # call writer
                maybe_write(r["mode"], 0, r["data"])

        # if scan is not requested, continue to next file
        if not args.scan:
            # skip scanning
            continue

        # perform offset scanning if requested
        print(f"scan: step={args.scan_step} max_offset={args.scan_max}")
        # initialize counters for matches
        matches_found = 0
        # iterate offsets from 0 to min(file_size-1, scan_max) inclusive
        limit = min(len(blob) - 1, args.scan_max)
        # loop over offsets in configured steps
        for off in range(0, max(1, limit + 1), max(1, args.scan_step)):
            # attempt all modes at this offset
            res = try_all_modes_at_offset(blob, off, args.expected)
            # iterate results to report any matches
            for r in res:
                # check if decompressed size matches expected
                if r["ok_size"]:
                    # increment match count
                    matches_found += 1
                    # print a hit line
                    print(f"  HIT mode={r['mode']} offset={off} consumed={r['consumed']} out_len={r['out_len']}")
                    # write the output if requested
                    maybe_write(r["mode"], off, r["data"])

        # print scan summary
        print(f"scan_done: matches={matches_found}")

# run main when invoked as a script
if __name__ == "__main__":
    # call the main function
    main()

