package de.labathome.irb;

import java.nio.ByteBuffer;

public class IrbTextInfo {

	public String textInfo;

	/**
	 * Read a TEXT_INFO block.
	 *
	 * @param buf    buffer to read image from
	 * @param offset
	 * @param size
	 */
	public IrbTextInfo(ByteBuffer buf, int offset, int size) {
		buf.position(offset);

		final int initialPosition = buf.position();

		// TODO: implement parsing preview image
		byte[] textInfoBytes = new byte[size];
		buf.get(textInfoBytes);
		textInfo = new String(textInfoBytes);

//		System.out.println("#### TEXT_INFO start ####");
//		System.out.println(textInfo);
//		System.out.println("#### TEXT_INFO end ####");

		if (buf.position() - initialPosition != size) {
			throw new RuntimeException("byte counting error in reading of IrbTextInfo; expected " + size + " but read " + (buf.position() - initialPosition));
		}
	}
}
