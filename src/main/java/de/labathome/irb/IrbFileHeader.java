package de.labathome.irb;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class IrbFileHeader {
	/** \ff I R B \0 */
	private static final byte[] MAGIC_ID = { (byte) 0xff, (byte) 0x49, (byte) 0x52, (byte) 0x42, (byte) 0x0 };

	private IrbFileType fileType;
	private int flag1;
	private int blockOffset;
	private int blockCount;

	public IrbFileType fileType() {
		return fileType;
	}

	public int flag1() {
		return flag1;
	}

	public int blockOffset() {
		return blockOffset;
	}

	public int blockCount() {
		return blockCount;
	}

	/**
	 * fixed 64 bytes of global file header,
	 * then 32 bytes for each of the `IrbHeaderBlock`s immediately after the global header
	 * @param buf
	 * @param forceReadOneImageAfterHeaderBlocks
	 */
	public static IrbFileHeader parse(ByteBuffer buf) {
		IrbFileHeader header = new IrbFileHeader();

		final int initialPos = buf.position();

		// parse magic number ID
		final byte[] magicBytes = new byte[5];
		buf.get(magicBytes);
		if (!Arrays.equals(magicBytes, MAGIC_ID)) {
			throw new RuntimeException("first 5 magic bytes invalid");
		}

		// read file type
		final byte[] fileTypeBytes = new byte[8];
		buf.get(fileTypeBytes);
		final String fileTypeString = new String(fileTypeBytes);
		header.fileType = IrbFileType.fromString(fileTypeString);

		// second file type identifier; gets ignored
		buf.get(fileTypeBytes);

		// NOTE: in irbis-file-format, the routines are named readIntBE,
		// although they actually read little endian!
		buf.order(ByteOrder.LITTLE_ENDIAN);
		header.flag1 = buf.getInt();
		header.blockOffset = buf.getInt();
		header.blockCount = buf.getInt();
		System.out.printf( "block offset=%d, block count=%d\n", header.blockOffset, header.blockCount);
		System.out.println("header blocks expected to start at " + header.blockOffset);

		// make sure to always read up to full 64 bytes
		final byte[] dummy = new byte[64 - 33];
		buf.get(dummy);
		if (buf.position() - initialPos != 64) {
			System.out.println("warning: IRB file header was not read correctly?");
		}

		return header;
	}

	/**
	 * read header blocks;
	 * assumes buffer is at starting position for header blocks
	 * @param buf
	 * @return
	 */
	public static List<IrbHeaderBlock> readHeaderBlocks(ByteBuffer buf, int blockCount) {
		List<IrbHeaderBlock> headerBlocks = new LinkedList<>();
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

		return headerBlocks;
	}
}
