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
	public List<IrbBlock> blocks;

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
		fileType = IrbFileType.fromString(new String(fileTypeBytes));

		// second file type identifier; gets ignored
		buf.get(fileTypeBytes);
		fileType2 = new String(fileTypeBytes);

		// NOTE: in irbis-file-format, the routines are named readIntBE,
		// although they actually read little endian!
		buf.order(ByteOrder.LITTLE_ENDIAN);
		flag1 = buf.getInt();
		blockOffset = buf.getInt();
		firstBlockCount = buf.getInt();

		// read header blocks
		blocks = new LinkedList<>();
		buf.position(blockOffset);
		for (int i=0; i<firstBlockCount; ++i) {
			IrbBlock headerBlock = new IrbBlock(buf);
			blocks.add(headerBlock);
		}

		// read actual image data
		for (IrbBlock block: blocks) {
			if (block.blockType == IrbBlockType.IMAGE) {
				block.readImage(buf);
				imageCount++;
			}
		}


	}
}
