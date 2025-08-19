/* irb
 * IrbHeader Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.irb;

import java.nio.ByteBuffer;

public class IrbHeader {

	private byte[] dummy;

	private IrbHeader() { }

	/**
	 * Read a HEADER data blob.
	 *
	 * @param buf    buffer to read image from
	 * @param offset
	 * @param size
	 */
	public static IrbHeader fromBuffer(ByteBuffer buf, int offset, int size) {
		IrbHeader header = new IrbHeader();

		buf.position(offset);
		final int initialPosition = buf.position();

		// TODO: implement parsing HEADER data
		header.dummy = new byte[size];
		buf.get(header.dummy);

		if (buf.position() - initialPosition != size) {
			throw new RuntimeException("byte counting error in reading of IrbHeader; expected " + size + " but read " + (buf.position() - initialPosition));
		}

		return header;
	}

}
