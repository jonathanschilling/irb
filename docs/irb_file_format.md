IRB file structure:

* little endian

* IRB file header: 64 bytes, starts at 0, ends at 63
  * 5 magic bytes: \ff I R B \00
  * file type: 8 bytes, string
  * file sub-type: 8 bytes, string
  * flag1: 32-bit int
  * blockOffset: 32-bit int
    denotes at which absolute position in the file the header blocks start
  * blockCount: 32-bit int
    denotes how many header blocks are expected
  * dummy: 31 bytes
* header blocks: start at blockOffset (from IRB file header; typically 64),
                 number is given by blockCount (also from IRB file header),
                 each one is 32 bytes long
  * header block type: 32-bit int
  * dword2: 32-bit int
  * frameIndex: 32-bit int
  * offset: 32-bit int
  * size: 32-bit int
  * dword6: 32-bit int
  * dword7: 32-bit int
  * dword8: 32-bit int
* now comes the data that belongs to each of the header blocks
* each data block is located by offset and size in the corresponding
  header block
* actual data begins at 64 bytes + blockCount * 32 bytes
  -> 4 header blocks: 64 + 4 * 32 = 192

* IMAGE data block:
  * header: 60 bytes
  * palette: 1024 bytes
  * meta-data: 644 bytes
  * actual image data (maybe compressed): rest of IMAGE data block
