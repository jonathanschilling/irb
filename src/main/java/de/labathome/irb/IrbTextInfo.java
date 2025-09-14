/* irb
 * IrbTextInfo Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.irb;

import java.nio.ByteBuffer;

public class IrbTextInfo {

	public String textInfo;

	private IrbTextInfo() { }

	/**
	 * Read a TEXT_INFO block.
	 *
	 * @param buf    buffer to read image from
	 * @param offset
	 * @param size
	 */
	public static IrbTextInfo fromBuffer(ByteBuffer buf, int offset, int size) {
		IrbTextInfo textInfo = new IrbTextInfo();

		buf.position(offset);

		final int initialPosition = buf.position();

		// TODO: implement parsing additional meta-data at end of TEXT_INFO block
		byte[] textInfoBytes = new byte[size];
		buf.get(textInfoBytes);
		textInfo.textInfo = new String(textInfoBytes);

//		System.out.println("#### TEXT_INFO start ####");
//		System.out.println(textInfo);
//		System.out.println("#### TEXT_INFO end ####");

		if (buf.position() - initialPosition != size) {
			throw new RuntimeException("byte counting error in reading of IrbTextInfo; expected " + size + " but read " + (buf.position() - initialPosition));
		}

		return textInfo;
	}
}
