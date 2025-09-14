# import struct for big-endian packing
import struct

from pathlib import Path

# define a function to create a minimal JPEG-LS wrapper around a raw entropy payload
def build_jpegls(payload: bytes, width: int, height: int, bits_per_sample: int = 16) -> bytes:
    # start with SOI marker 0xFFD8
    out = bytearray(b"\xFF\xD8")

    # build SOF55 (Start of Frame for JPEG-LS, marker 0xFFF7)
    # fields: Lf, P, Y, X, Nf, [ID, H/V, Tq] for each component (Nf=1 here; Tq unused in JPEG-LS but must be present)
    # pack length: 8 + 3*Nf => 8 + 3 = 11
    out += b"\xFF\xF7"                      # SOF55
    out += struct.pack(">H", 11)            # Lf = 11 bytes
    out += struct.pack(">B", bits_per_sample)   # P = sample precision
    out += struct.pack(">H", height)        # Y = number of lines
    out += struct.pack(">H", width)         # X = number of samples per line
    out += struct.pack(">B", 1)             # Nf = number of components
    out += struct.pack(">B", 1)             # C1 = component ID
    out += struct.pack(">B", 0x11)          # H1/V1 sampling factors
    out += struct.pack(">B", 0)             # Tq1 = 0 (not used by JPEG-LS)

    # build SOS (Start of Scan) marker 0xFFDA
    # fields: Ls, Ns, [Cs, NEAR] per comp is *not* the JPEG-LS layout; JPEG-LS uses a JPEG-style SOS but adds parameters:
    # We use the common minimal layout used by JPEG-LS implementations:
    # Ls = 8 + 2*(Ns) ; here Ns=1 => Ls=10
    out += b"\xFF\xDA"
    out += struct.pack(">H", 10)            # Ls
    out += struct.pack(">B", 1)             # Ns = 1 component in scan
    out += struct.pack(">B", 1)             # Cs1 = component 1
    out += struct.pack(">B", 0x00)          # mapping table selectors (not used for JPEG-LS)
    out += struct.pack(">B", 0x00)          # NEAR=0 (lossless), ILV=0 (no interleave)
    out += struct.pack(">B", 0x00)          # point transform (0)
    out += struct.pack(">B", 0x00)          # reserved / not used (kept 0)

    # append the entropy-coded segment (your payload)
    out += payload

    # end with EOI
    out += b"\xFF\xD9"

    # return as bytes
    return bytes(out)

# write a test file for one frame
raw = Path("image_10848.bin").read_bytes()
jls = build_jpegls(raw, width=640, height=480, bits_per_sample=16)
Path("image_10848_wrapped.jls").write_bytes(jls)
print("wrote image_10848_wrapped.jls")

