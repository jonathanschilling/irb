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
		this(buf, false);
	}

	public IrbFile(ByteBuffer buf, boolean forceReadOneImageAfterHeaderBlocks) {
		parseHeader(buf, forceReadOneImageAfterHeaderBlocks);

		// read actual image data
		images = new LinkedList<>();
		for (IrbHeaderBlock block : headerBlocks) {
			if (block.blockType != IrbBlockType.EMPTY) {
				System.out.println("reading contents of block of type "
						+ block.blockType + " at offset " + block.offset + " of size " + block.size);

				if (block.blockType == IrbBlockType.IMAGE) {
					IrbImage image = new IrbImage(buf, block.offset, block.size);
					images.add(image);
				} else {
					// just read past the whatever block...
					byte[] dummy = new byte[block.size];
					buf.get(dummy);

					System.out.printf("read dummy %d bytes; now at position %d\n", block.size, buf.position());
				}
			} else {
				System.out.println("ignore EMPTY header block");
			}
		}

		if (fileType == IrbFileType.O_SAVE_IRB) {
			// expect a single "front matter"/"preview"/"thumbnail" image to be present,
			// without it being mentioned/announced with a corresponding header block

			final int dummySize = 0; //not actually needed...
			IrbImage image = new IrbImage(buf, buf.position(), dummySize);
			images.add(image);
		}

//		if (forceReadOneImageAfterHeaderBlocks) {
//			final int dummySize = 0; //not actually needed...
//			//IrbImage image = new IrbImage(buf, buf.position() + 192, dummySize);
//
//			buf.position(616608 + 192);
//
//			System.out.println("start reading 2nd actual image data at " + buf.position());
//
//			IrbImage image = new IrbImage(buf, buf.position(), dummySize);
//			images.add(image);
//		}
	}

	/**
	 * fixed 64 bytes of global file header,
	 * then 32 bytes for each of the `IrbHeaderBlock`s immediately after the global header
	 * @param buf
	 * @param forceReadOneImageAfterHeaderBlocks
	 */
	private void parseHeader(ByteBuffer buf, boolean forceReadOneImageAfterHeaderBlocks) {

		final int initialPos = buf.position();

		// parse magic number ID
		final byte[] magicBytes = new byte[5];
		buf.get(magicBytes);
		logger.log(Level.DEBUG, "magic bytes: " + Arrays.toString(magicBytes));
		if (!Arrays.equals(magicBytes, MAGIC_ID)) {
			throw new RuntimeException("first 5 magic bytes invalid");
		}

		// read file type
		final byte[] fileTypeBytes = new byte[8];
		buf.get(fileTypeBytes);
		final String fileTypeString = new String(fileTypeBytes);
		logger.log(Level.DEBUG, "file type bytes: " + Arrays.toString(fileTypeBytes) + "; as String: " + fileTypeString);
		fileType = IrbFileType.fromString(fileTypeString);
		logger.log(Level.DEBUG, "file type: " + fileType);

		// second file type identifier; gets ignored
		buf.get(fileTypeBytes);
		final String fileTypeString2 = new String(fileTypeBytes);
		logger.log(Level.DEBUG, "2nd file type bytes: " + Arrays.toString(fileTypeBytes) + "; as String: " + fileTypeString2);

		// NOTE: in irbis-file-format, the routines are named readIntBE,
		// although they actually read little endian!
		buf.order(ByteOrder.LITTLE_ENDIAN);
		flag1 = buf.getInt();
		blockOffset = buf.getInt();
		blockCount = buf.getInt();
		System.out.printf("block offset=%d, block count=%d\n", blockOffset, blockCount);

//		System.out.println("size of header, fixed part: " + (buf.position() - initialPos));
		if (buf.position() - initialPos != 33) {
			System.out.println("warning: fixed part of IRB file header was not read correctly?");
		}

		System.out.println("header blocks expected to start at " + blockOffset);

		// read header blocks
		headerBlocks = new LinkedList<>();
		buf.position(initialPos + blockOffset);
		int totalAppendedDataSize = 0;
		for (int i = 0; i < blockCount; ++i) {
			System.out.printf("starting to read block %d at position %d\n", i, buf.position());
			IrbHeaderBlock headerBlock = new IrbHeaderBlock(buf);
			headerBlocks.add(headerBlock);
			System.out.println("  offset parameter in header block is " + headerBlock.offset);
			System.out.println("  size   parameter in header block is " + headerBlock.size);
			totalAppendedDataSize += headerBlock.size;
		}

		System.out.println("end of header blocks at pos = " + buf.position());
		System.out.println("total size of appended data: " + totalAppendedDataSize);
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

//		if (file.fileType == IrbFileType.O_SAVE_IRB) {
//			buf.position(616608);
//
//			System.out.println("starting to read 2nd IRB file at " + buf.position());
//			IrbFile file2 = new IrbFile(buf, true);
//		}

		memoryFile.close();

		return file;
	}
}
