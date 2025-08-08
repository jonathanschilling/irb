/* irb
 * IrbFileHeader Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.irb;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class IrbFileHeader {

	/** \ff I R B \0 */
	private static final byte[] MAGIC_ID = { (byte) 0xff, (byte) 0x49, (byte) 0x52, (byte) 0x42, (byte) 0x0 };

	public IrbFileType fileType;

	@SuppressWarnings("unused")
	private int flag1;

	public int blockCount;
	public int blockOffset;

	public IrbFileHeader(ByteBuffer buf) {
		parseHeader(buf);
	}

	private void parseHeader(ByteBuffer buf) {
		final int initialPosition = buf.position();

		// parse magic number ID
		final byte[] magicBytes = new byte[5];
		buf.get(magicBytes);
		// 5
		if (!Arrays.equals(magicBytes, MAGIC_ID)) {
			throw new RuntimeException("first 5 magic bytes invalid");
		}

		// read file type
		final byte[] fileTypeBytes = new byte[8];
		buf.get(fileTypeBytes);
		// 13
		fileType = IrbFileType.fromString(new String(fileTypeBytes));

		// second file type identifier; gets ignored
		buf.get(fileTypeBytes);
		// 21
		flag1 = buf.getInt();
		// 25
		blockOffset = buf.getInt();
		// 29
		blockCount = buf.getInt();
		// 33
		byte[] dummy = new byte[31];
		buf.get(dummy);
		// 64

//		System.out.printf("# IrbFileHeader: fileType=%s blockOffset=%d blockCount=%d\n", fileType.toString(), blockOffset, blockCount);

		if (buf.position() - initialPosition != 64) {
			throw new RuntimeException("byte counting error in parsing of IrbFileHeader");
		}
	}
}
