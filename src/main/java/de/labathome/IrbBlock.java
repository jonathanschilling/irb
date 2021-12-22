package de.labathome;

import java.nio.ByteBuffer;

public class IrbBlock {

	// header data
	public IrbBlockType blockType;
	public int dword2;
	public int frameIndex;
	public int offset;
	public int size;
	public int dword6;
	public int dword7;
	public int dword8;
	public int headerOffset;
	public int headerSize;
	public int imageOffset;
	public int imageSize;

	public byte[] imageData;

	public IrbBlock(ByteBuffer buf) {

		// read header

		blockType = IrbBlockType.fromInt(buf.getInt());
		System.out.printf("found block: %s\n", blockType);

		dword2 = buf.getInt();
		frameIndex = buf.getInt();

		// starts at 0
		offset = buf.getInt();

		size = buf.getInt();

		// head has fixed size of 0x6C0
		// but check against headerSize...
		headerSize = 0x6C0;
		if (headerSize > size) {
			headerSize = size;
		}

		headerOffset = 0;

		imageOffset = headerSize;
		imageSize = size - imageOffset;

		dword6 = buf.getInt();
		dword7 = buf.getInt();
		dword8 = buf.getInt();
	}

	/**
	 * Read the image data corresponding to this block.
	 *
	 * @param buf buffer to read image from
	 */
	public void readImage(ByteBuffer buf) {
		// save current buffer position
		int oldPos = buf.position();

		// read image data
		imageData = new byte[size];
		buf.position(offset);
		buf.get(imageData);

		// restore old position
		buf.position(oldPos);
	}
}
