/* irb
 * IrbHeaderBlock Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.irb;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;

public class IrbHeaderBlock {

	protected final Logger logger = System.getLogger(IrbHeaderBlock.class.getName());

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
		readIrbHeader(buf);
	}

	protected void readIrbHeader(ByteBuffer buf) {

		final int blockTypeInt = buf.getInt();
		logger.log(Level.DEBUG, String.format("block type: %d", blockTypeInt));
		blockType = IrbBlockType.fromInt(blockTypeInt);
		System.out.printf("found block: %s\n", blockType);

		// always 100 ???
		dword2 = buf.getInt();
		logger.log(Level.DEBUG, String.format("dword2: %d", dword2));

		frameIndex = buf.getInt();
		logger.log(Level.DEBUG, String.format("frame index: %d", frameIndex));

		// starts at 0
		offset = buf.getInt();
		logger.log(Level.DEBUG, String.format("offset: %d", offset));

		size = buf.getInt();
		logger.log(Level.DEBUG, String.format("  size: %d", size));

		// head has fixed size of 0x6C0
		// but check against headerSize...
		headerSize = 0x6C0;
		if (headerSize > size) {
			headerSize = size;
		}
		logger.log(Level.DEBUG, String.format("headerSize: %d", headerSize));

		headerOffset = 0;

		imageOffset = headerSize;
		imageSize = size - imageOffset;
		logger.log(Level.DEBUG, String.format("imageOffset: %d", imageOffset));
		logger.log(Level.DEBUG, String.format("imageSize  : %d", imageSize));

		dword6 = buf.getInt();
		logger.log(Level.DEBUG, String.format("dword6: %d", dword6));

		dword7 = buf.getInt();
		logger.log(Level.DEBUG, String.format("dword7: %d", dword7));

		dword8 = buf.getInt();
		logger.log(Level.DEBUG, String.format("dword8: %d", dword8));
	}
}
