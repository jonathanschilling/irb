/* irb
 * IrbHeaderBlock Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.irb;

import java.nio.ByteBuffer;

public class IrbHeaderBlock {

	public IrbBlockType blockType;
	public int dword2;
	public int frameIndex;
	public int offset;
	public int size;
	public int dword6;
	public int dword7;
	public int dword8;
//	public int headerOffset;
//	public int headerSize;
//	public int imageOffset;
//	public int imageSize;

	public IrbHeaderBlock(ByteBuffer buf) {
		final int initialPosition = buf.position();

		final int blockTypeInt = buf.getInt();
		// 4
		blockType = IrbBlockType.fromInt(blockTypeInt);
//		System.out.printf("found block: %s\n", blockType);

		dword2 = buf.getInt();
		// 8
		frameIndex = buf.getInt();
		// 12

		// starts at 0
		offset = buf.getInt();
		// 16

		size = buf.getInt();
		// 20
//
//		// head has fixed size of 0x6C0
//		// but check against headerSize...
//		headerSize = 0x6C0;
//		if (headerSize > size) {
//			headerSize = size;
//		}
//
//		headerOffset = 0;
//
//		imageOffset = headerSize;
//		imageSize = size - imageOffset;

		dword6 = buf.getInt();
		// 24
		dword7 = buf.getInt();
		// 28
		dword8 = buf.getInt();
		//32

		if (buf.position() - initialPosition != 32) {
			throw new RuntimeException("byte counting error in parsing of IrbHeaderBlock");
		}
	}
}
