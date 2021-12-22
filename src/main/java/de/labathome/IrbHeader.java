package de.labathome;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class IrbHeader {

	/** \ff I R B \0 */
	private static final byte[] MAGIC_ID = {
			(byte) 0xff,
			(byte) 0x49,
			(byte) 0x52,
			(byte) 0x42,
			(byte) 0x0
	};

	private String magic;
	private IrbFileType fileType;
	private String fileType2;
	private int flag1;

	private int firstBlockCount;

	/** starts at 0 */
	private int blockOffset;
	public List<IrbHeaderBlock> blocks;

	private int imageCount;

	public IrbHeader(ByteBuffer buf) {

		// parse magic number ID
		final byte[] magicBytes = new byte[5];
		buf.get(magicBytes);
		if (!Arrays.equals(magicBytes, MAGIC_ID)) {
			throw new RuntimeException("first 5 magic bytes invalid");
		}
		magic = new String(magicBytes);

		// read file type
		final byte[] fileTypeBytes = new byte[8];
		buf.get(fileTypeBytes);
		fileType = IrbFileType.valueOf(new String(fileTypeBytes));

		// second file type identifier; gets ignored
		buf.get(fileTypeBytes);
		fileType2 = new String(fileTypeBytes);

		// NOTE: in irbis-file-format, the routines are named readIntBE,
		// although they actually read little endian!
		buf.order(ByteOrder.LITTLE_ENDIAN);
		flag1 = buf.getInt();
		blockOffset = buf.getInt();
		firstBlockCount = buf.getInt();

		// read first header block
		IrbHeaderBlock firstHeaderBlock = new IrbHeaderBlock(buf, blockOffset, firstBlockCount);
		if (firstHeaderBlock.blockType == IrbBlockType.IMAGE) {
			imageCount++;
		}
		blocks = new LinkedList<>();
		blocks.add(firstHeaderBlock);




	}
}
