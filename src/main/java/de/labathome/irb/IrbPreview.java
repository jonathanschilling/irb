/* irb
 * IrbPreview Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.irb;

import java.nio.ByteBuffer;

public class IrbPreview {

	/**
	 * Read the PREVIEW data blob.
	 *
	 * @param buf    buffer to read image from
	 * @param offset
	 * @param size
	 */
	public IrbPreview(ByteBuffer buf, int offset, int size) {
		buf.position(offset);

		final int initialPosition = buf.position();

		// TODO: implement parsing preview image
		byte[] dummy = new byte[size];
		buf.get(dummy);

		if (buf.position() - initialPosition != size) {
			throw new RuntimeException("byte counting error in reading of IrbPreview; expected " + size + " but read " + (buf.position() - initialPosition));
		}
	}
}
