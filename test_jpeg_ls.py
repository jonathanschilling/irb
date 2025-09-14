# import Path for convenience
from pathlib import Path

# define a function to measure JPEG/JLS byte-stuffing signature
def jpeg_stuffing_signature(data: bytes):
    # count total occurrences of 0xFF
    total_ff = sum(1 for i in range(len(data)) if data[i] == 0xFF)
    # count how many 0xFF are followed by 0x00
    ff00 = sum(1 for i in range(len(data)-1) if data[i] == 0xFF and data[i+1] == 0x00)
    # count potential restart markers 0xFFD0..0xFFD7
    restarts = sum(1 for i in range(len(data)-1) if data[i] == 0xFF and (data[i+1] & 0xF8) == 0xD0)
    # compute fraction safely
    frac = ff00 / total_ff if total_ff else 0.0
    # return a compact dict
    return {"total_ff": total_ff, "ff00": ff00, "ff00_fraction": frac, "restart_markers": restarts}

# loop over your files and print the signature
for p in sorted(Path(".").glob("image_*.bin")):
    # read file bytes
    b = p.read_bytes()
    # compute signature
    sig = jpeg_stuffing_signature(b)
    # print a single-line summary
    print(p.name, sig)

