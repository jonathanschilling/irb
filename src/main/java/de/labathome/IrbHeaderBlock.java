package de.labathome;

import java.nio.ByteBuffer;

public class IrbHeaderBlock {

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

	public IrbHeaderBlock(ByteBuffer buf) {

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
}
