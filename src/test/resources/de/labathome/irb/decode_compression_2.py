import numpy as np
import matplotlib.pyplot as plt

import struct

with open("AC032701.irb", "rb") as f:
    all_bytes = f.read()

print("file length:", len(all_bytes)) # 2_852_704

# extracted from IMAGE header block
image_data = all_bytes[9120:9120+355776]

# image header = 60 bytes
# image palette = 1024 bytes
# image meta-data = 644 bytes
# skip all of above: 60 + 1024 + 644 = 1728 bytes
image_img = image_data[1728:]

# last 94 bytes are actually zero - ignore them
# last non-zero bytes are 7C D6
image_img = image_img[:-94]

with open("AC032701_image.bin", "wb") as f:
    f.write(image_img)

# image has w=640, h=480, "bytes_per_pixel=2"

print("image raw data length:", len(image_img)) # 353_954

# make individual bytes accessible as array
e = struct.unpack(f"{len(image_img):d}B", image_img)

print([f"{a:02x}" for a in e[:10]])

# "VARIOCAM_HD" found at positions | distance to previous
#   10346
#  366186 | 355840
#  721770 | 355584
# 1077354 | 355584
# 1432682 | 355328
# 1788010 | 355328
# 2143338 | 355328
# 2498666 | 355328
# -> 8 frames
variocam_pos = np.array([10346, 366186, 721770, 1077354, 1432682, 1788010, 2143338, 2498666])
print(np.diff(variocam_pos))
      
# mystery blocks between frames (64 bytes) -> 1290 bytes before VARIOCAM_HD
# no mystery block before 1st frame - inside regular TEXT_INFO block
# 364896
# 720480
all_mystery_block_pos = variocam_pos[1:] - 1290
print("mystery_block_pos =", all_mystery_block_pos)

for mystery_block_pos in all_mystery_block_pos:
    mystery_block = all_bytes[mystery_block_pos:mystery_block_pos+64]
    mystery_hex = mystery_block.hex()
    # print([mystery_hex[8*i:8*i+8] for i in range(16)])

    # int - always  1 - bit field?
    # int - always 0x65 == 101 - bit field?
    # int - frame counter
    # int - offset in file -> start of IrbImage header (60 bytes) etc.
    # int - size of IrbImage block in file - diff to next block's offset  - 64 (frame header)
    # int - always 0
    # int - always 1
    # int - always 0
    # int - 4, except last 2 frame have 0 - bit field?
    # int - 65, except frame -2 has 0 - bit field?
    # int - frame counter, except frame -2 has 0?
    # int - expected position of next frame?
    # int - mostly 64, except -2 is 0 and last is same as size
    # int - always 0
    # int - always 1, except -2 is 0
    # int - always 0
    mystery_data = struct.unpack("<16i", mystery_block)
    print(' '.join([f"{m:7d}" for m in mystery_data]))
