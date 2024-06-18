/* irb
 * IrbFile Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.irb;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
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

	protected final Logger logger = System.getLogger(IrbFile.class.getName());

	/** \ff I R B \0 */
	private static final byte[] MAGIC_ID = { (byte) 0xff, (byte) 0x49, (byte) 0x52, (byte) 0x42, (byte) 0x0 };

	private IrbFileType fileType;

	@SuppressWarnings("unused")
	private int flag1;

	private int blockCount;
	private int blockOffset;

	public List<IrbHeaderBlock> headerBlocks;
	public List<IrbImage> images;

	public IrbFile(ByteBuffer buf) {
		parseHeader(buf);
	}

	private void parseHeader(ByteBuffer buf) {
		// parse magic number ID
		final byte[] magicBytes = new byte[5];
		buf.get(magicBytes);
		logger.log(Level.INFO, "magic bytes: " + Arrays.toString(magicBytes));
		if (!Arrays.equals(magicBytes, MAGIC_ID)) {
			throw new RuntimeException("first 5 magic bytes invalid");
		}

		// read file type
		final byte[] fileTypeBytes = new byte[8];
		buf.get(fileTypeBytes);
		final String fileTypeString = new String(fileTypeBytes);
		logger.log(Level.INFO, "file type bytes: " + Arrays.toString(fileTypeBytes) + "; as String: " + fileTypeString);
		fileType = IrbFileType.fromString(fileTypeString);
		logger.log(Level.INFO, "file type: " + fileType);

		// second file type identifier; gets ignored
		buf.get(fileTypeBytes);
		final String fileTypeString2 = new String(fileTypeBytes);
		logger.log(Level.INFO, "2nd file type bytes: " + Arrays.toString(fileTypeBytes) + "; as String: " + fileTypeString2);

		// NOTE: in irbis-file-format, the routines are named readIntBE,
		// although they actually read little endian!
		buf.order(ByteOrder.LITTLE_ENDIAN);
		flag1 = buf.getInt();
		blockOffset = buf.getInt();
		blockCount = buf.getInt();
		logger.log(Level.INFO, String.format("block offset=%d, block count=%d", blockOffset, blockCount));

		// read header blocks
		headerBlocks = new LinkedList<>();
		buf.position(blockOffset);
		for (int i = 0; i < blockCount; ++i) {
			IrbHeaderBlock headerBlock = new IrbHeaderBlock(buf);
			headerBlocks.add(headerBlock);
		}

		// read actual image data
		images = new LinkedList<>();
		for (IrbHeaderBlock block : headerBlocks) {
			logger.log(Level.INFO, "starting to read block of type " + block.blockType + " at offset " + block.offset + " of size " + block.size);
			if (block.blockType == IrbBlockType.IMAGE) {
				IrbImage image = new IrbImage(buf, block.offset, block.size);
				images.add(image);
			}
		}

		final int size = 0; // seems to not be used ??
		IrbImage image = new IrbImage(buf, 480, size);
		images.add(image);
	}

	public IrbFileType fileType() {
		return fileType;
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
