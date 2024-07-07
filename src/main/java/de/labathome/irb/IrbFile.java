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

	public IrbFile(ByteBuffer buf) {
		final int initialPosition = buf.position();

		// NOTE: in irbis-file-format, the routines are named readIntBE,
		// although they actually read little endian!
		buf.order(ByteOrder.LITTLE_ENDIAN);

		header = new IrbFileHeader(buf);

		// read header blocks
		headerBlocks = new LinkedList<>();
		buf.position(initialPosition + header.blockOffset);
		for (int i = 0; i < header.blockCount; ++i) {
			IrbHeaderBlock headerBlock = new IrbHeaderBlock(buf);
			headerBlocks.add(headerBlock);
		}

		// read actual image data
		images = new LinkedList<>();
		previews = new LinkedList<>();
		textInfos = new LinkedList<>();
		for (IrbHeaderBlock block : headerBlocks) {
			switch (block.blockType) {
			case IMAGE:
				IrbImage image = new IrbImage(buf, block.offset, block.size);
				images.add(image);
				break;
			case PREVIEW:
				IrbPreview preview = new IrbPreview(buf, block.offset, block.size);
				previews.add(preview);
				break;
			case TEXT_INFO:
				IrbTextInfo textInfo = new IrbTextInfo(buf, block.offset, block.size);
				textInfos.add(textInfo);
				break;
			case EMPTY:
				if (block.offset != 0 || block.size != 0) {
					throw new RuntimeException("non-empty EMPTY block? offset=" + block.offset + " size=" + block.size);
				}
				// ignore
				break;
			default:
				throw new RuntimeException("block not implemented yet:" + block.blockType);
			}
		}
	}

	public IrbFileType fileType() {
		return header.fileType;
	}

	public static IrbFile fromFile(String filename) throws IOException {
		if (!(new File(filename).exists())) {
			throw new RuntimeException("File '" + filename + "' does not exists!");
		}

		RandomAccessFile memoryFile = new RandomAccessFile(filename, "r");
		MappedByteBuffer buf = memoryFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, memoryFile.length());

		IrbFile file = new IrbFile(buf);

		memoryFile.close();

		return file;
	}
}
