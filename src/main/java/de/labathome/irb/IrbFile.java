/* irb
 * IrbFile Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.irb;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedList;
import java.util.List;

/**
 * A reading class for the *.irb file format by InfraTec, inspired by
 * https://github.com/tomsoftware/Irbis-File-Format .
 *
 * This is a pure hobby project. No copyright infringements or similar is
 * intended. Please inform the author about possible legal issues before turning
 * to a lawyer.
 *
 * @author Jonathan Schilling (jonathan.schilling@mail.de)
 * @author Benjamin Schilling (benjamin.schilling33@gmail.com)
 */
public class IrbFile {

	public IrbFileHeader header;

	public List<IrbHeaderBlock> headerBlocks;

	public List<IrbImage> images;
	public List<IrbPreview> previews;
	public List<IrbTextInfo> textInfos;
	public List<IrbFrameHeader> headers;

	// video data
	public List<IrbFile> frames;

	public static IrbFile fromFile(String filename) throws IOException {
		if (!(new File(filename).exists())) {
			throw new RuntimeException("File '" + filename + "' does not exists!");
		}

		RandomAccessFile memoryFile = new RandomAccessFile(filename, "r");

		long file_size = memoryFile.length();
		System.out.println("file size: " + file_size);

		MappedByteBuffer buf = memoryFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, file_size);

		IrbFile irb = IrbFile.read(buf, false);

		if (irb.header.fileType == IrbFileType.O_SAVE_IRB) {

			// preview image
			IrbImage frontMatter = new IrbImage(buf, 0, 0, false);
			irb.images.add(frontMatter);

			// now read frames, appended one after another
			irb.frames = new LinkedList<>();
			int frameCount = 0;
			while (buf.remaining() > 0) {
				if (frameCount > 0 && frameCount % 100 == 0) {
					System.out.printf("already read %4d frames...\n", frameCount);
				}
				IrbFile frame = IrbFile.read(buf, true);
				irb.frames.add(frame);

				frameCount++;
			}
		}

		memoryFile.close();

		return irb;
	}

	public static IrbFile read(ByteBuffer buf, boolean isVideoFrameFirstRead) {
		final int initialPosition = buf.position();

		// NOTE: in irbis-file-format, the routines are named readIntBE,
		// although they actually read little endian!
		buf.order(ByteOrder.LITTLE_ENDIAN);

		IrbFile irb = new IrbFile();

		irb.header = IrbFileHeader.fromBuffer(buf);

		// read header blocks
		irb.headerBlocks = new LinkedList<>();
		buf.position(initialPosition + irb.header.blockOffset);
		for (int i = 0; i < irb.header.blockCount; ++i) {
			IrbHeaderBlock headerBlock = IrbHeaderBlock.fromBuffer(buf);
			irb.headerBlocks.add(headerBlock);
		}

		// sort header blocks by appearance in the file
		irb.headerBlocks.sort((IrbHeaderBlock a, IrbHeaderBlock b) -> { return a.offset - b.offset; });

		// print in order of appearance in the file
		// and check if header block data is continuous and spans the whole buffer size
		int lastDataStart = irb.header.blockOffset + irb.header.blockCount * 32;
		for (IrbHeaderBlock headerBlock: irb.headerBlocks) {
			System.out.printf("# IrbHeaderBlock blockType=%s [%d] frameIndex=%d offset=%d size=%d\n",
					headerBlock.blockType.toString(), headerBlock.blockType.value(),
					headerBlock.frameIndex, headerBlock.offset, headerBlock.size);

			// EMPTY header block is expected to have offset=0, size=0 -> ignore that case
			if (headerBlock.offset != lastDataStart &&
					!(headerBlock.blockType == IrbBlockType.EMPTY && headerBlock.offset == 0 && headerBlock.size == 0)) {
				System.out.printf("  WARNING: block data does not line up: expected offset=%d, but read pointer is at %d\n",
						headerBlock.offset, lastDataStart);
			}

			// move current file position marker by size of data block denoted in corresponding header block
			lastDataStart += headerBlock.size;
		}
		if (lastDataStart != buf.capacity()) {
			System.out.printf("  WARNING: mismatch between declared blocks and file size: declared end at %d, actual file size %d\n",
					lastDataStart, buf.capacity());
		}

		// read actual image data
		irb.images = new LinkedList<>();
		irb.previews = new LinkedList<>();
		irb.textInfos = new LinkedList<>();
		irb.headers = new LinkedList<>();
		for (IrbHeaderBlock block : irb.headerBlocks) {
			switch (block.blockType) {
			case EMPTY: // 0
				if (block.offset != 0 || block.size != 0) {
					System.out.println("non-empty EMPTY block? offset=" + block.offset + " size=" + block.size);
				}
				// ignore
				break;
			case IMAGE: // 1
				IrbImage image = IrbImage.fromBuffer(buf, initialPosition + block.offset, block.size, isVideoFrameFirstRead);
				if (!isVideoFrameFirstRead) {
					irb.images.add(image);
				}
				break;
			case PREVIEW: // 2
				IrbPreview preview = IrbPreview.fromBuffer(buf, initialPosition + block.offset, block.size);
				irb.previews.add(preview);
				break;
			case TEXT_INFO: // 3
				IrbTextInfo textInfo = IrbTextInfo.fromBuffer(buf, initialPosition + block.offset, block.size);
				irb.textInfos.add(textInfo);
				break;
			case FRAME_HEADER: // 4
				IrbFrameHeader header = IrbFrameHeader.fromBuffer(buf, initialPosition + block.offset, block.size);
				irb.headers.add(header);
				break;
			case TODO_MYSTERY_5: // 5
				System.out.println("TODO: mystery block 5 - ignored for now");
				buf.position(initialPosition + block.offset + block.size);
				break;
			case TODO_MYSTERY_6: // 6
				System.out.println("TODO: mystery block 6 - ignored for now");
				buf.position(initialPosition + block.offset + block.size);
				break;
			case AUDIO: // 7
				System.out.println("TODO: AUDIO block - ignored for now");
				buf.position(initialPosition + block.offset + block.size);
				break;
			default:
				throw new RuntimeException("block not implemented yet: " + block.blockType);
			}
		}

		if (buf.remaining() > 0) {
			if (isVideoFrameFirstRead) {
				// expect an IMAGE header block
				IrbHeaderBlock imageHeaderBlock = IrbHeaderBlock.fromBuffer(buf);
				if (imageHeaderBlock.blockType != IrbBlockType.IMAGE) {
					throw new RuntimeException("expecting IMAGE header block, but got " + imageHeaderBlock.blockType);
				}

	//			System.out.println("  frame index: " + imageHeaderBlock.frameIndex);

				// expect a HEADER header block
				IrbHeaderBlock headerHeaderBlock = IrbHeaderBlock.fromBuffer(buf);
				if (headerHeaderBlock.blockType != IrbBlockType.FRAME_HEADER) {
					throw new RuntimeException("expecting HEADER header block, but got " + headerHeaderBlock.blockType);
				}

				// now comes the actual frame image
				IrbImage image = new IrbImage(buf, 0, 0, /*isVideoFrameFirstRead=*/false);
				irb.images.add(image);
			} else if (irb.header.fileType == IrbFileType.VARIOCAM) {
				System.out.println("trying to interpret as VARIOCAM video...");

				IrbFrameHeader last_frame_header = irb.headers.get(irb.headers.size() - 1);
				while (last_frame_header.expected_next_offset != last_frame_header.offset) {
					// read current frame
					IrbImage next_frame = IrbImage.fromBuffer(buf, last_frame_header.offset, last_frame_header.size, false);
					irb.images.add(next_frame);

					// expect another frame header after current frame
					last_frame_header = IrbFrameHeader.fromBuffer(buf, buf.position(), 64);
					irb.headers.add(last_frame_header);
				}
			}
		}

		return irb;
	}

	public IrbFileType fileType() {
		return header.fileType;
	}
}
