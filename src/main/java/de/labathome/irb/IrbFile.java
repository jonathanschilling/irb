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
	public List<IrbHeader> headers;

	// video data
	public List<IrbFile> frames;

	public static IrbFile fromFile(String filename) throws IOException {
		if (!(new File(filename).exists())) {
			throw new RuntimeException("File '" + filename + "' does not exists!");
		}

		RandomAccessFile memoryFile = new RandomAccessFile(filename, "r");
		MappedByteBuffer buf = memoryFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, memoryFile.length());

		IrbFile irb = IrbFile.read(buf, false);

		if (irb.header.fileType == IrbFileType.O_SAVE_IRB) {

			// preview image
			IrbImage frontMatter = new IrbImage(buf, false);
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

	public static IrbFile read(ByteBuffer buf, boolean isVideoFrame) {
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
				IrbImage image = IrbImage.fromBuffer(buf, initialPosition + block.offset, block.size, /*isVideoFrameFirstRead=*/isVideoFrame);
				if (!isVideoFrame) {
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
			case HEADER: // 4
				IrbHeader header = IrbHeader.fromBuffer(buf, initialPosition + block.offset, block.size);
				irb.headers.add(header);
				break;
			case TODO_MYSTERY_5: // 5
				System.out.println("TODO: mystery block 5 - ignored for now");
				break;
			case TODO_MYSTERY_6: // 6
				System.out.println("TODO: mystery block 6 - ignored for now");
				break;
			case AUDIO: // 7
				System.out.println("TODO: AUDIO block - ignored for now");
				break;
			default:
				throw new RuntimeException("block not implemented yet: " + block.blockType);
			}
		}

		if (isVideoFrame && buf.remaining() > 0) {
			// expect an IMAGE header block
			IrbHeaderBlock imageHeaderBlock = IrbHeaderBlock.fromBuffer(buf);
			if (imageHeaderBlock.blockType != IrbBlockType.IMAGE) {
				throw new RuntimeException("expecting IMAGE header block, but got " + imageHeaderBlock.blockType);
			}

//			System.out.println("  frame index: " + imageHeaderBlock.frameIndex);

			// expect a HEADER header block
			IrbHeaderBlock headerHeaderBlock = IrbHeaderBlock.fromBuffer(buf);
			if (headerHeaderBlock.blockType != IrbBlockType.HEADER) {
				throw new RuntimeException("expecting HEADER header block, but got " + headerHeaderBlock.blockType);
			}

			// now comes the actual frame image
			IrbImage image = new IrbImage(buf, /*isVideoFrameFirstRead=*/false);
			irb.images.add(image);
		}

		return irb;
	}

	public IrbFileType fileType() {
		return header.fileType;
	}
}
