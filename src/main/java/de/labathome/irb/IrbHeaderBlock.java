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

	private IrbHeaderBlock() { }

	public static IrbHeaderBlock fromBuffer(ByteBuffer buf) {
		IrbHeaderBlock headerBlock = new IrbHeaderBlock();

		final int initialPosition = buf.position();

		final int blockTypeInt = buf.getInt();
		// 4
		headerBlock.blockType = IrbBlockType.fromInt(blockTypeInt);

		headerBlock.dword2 = buf.getInt();
		// 8
		headerBlock.frameIndex = buf.getInt();
		// 12

		// starts at 0
		headerBlock.offset = buf.getInt();
		// 16

		headerBlock.size = buf.getInt();
		// 20

		headerBlock.dword6 = buf.getInt();
		// 24
		headerBlock.dword7 = buf.getInt();
		// 28
		headerBlock.dword8 = buf.getInt();
		//32

//		System.out.printf("# IrbHeaderBlock blockType=%s [%d] frameIndex=%d offset=%d size=%d\n",
//				headerBlock.blockType.toString(), headerBlock.blockType.value(),
//				headerBlock.frameIndex, headerBlock.offset, headerBlock.size);

		if (buf.position() - initialPosition != 32) {
			throw new RuntimeException("byte counting error in parsing of IrbHeaderBlock");
		}

		return headerBlock;
	}
}
