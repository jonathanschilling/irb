package de.labathome.irb;

import java.nio.ByteBuffer;

public class IrbHeader {

	/**
	 * Read a HEADER data blob.
	 *
	 * @param buf    buffer to read image from
	 * @param offset
	 * @param size
	 */
	public static IrbHeader controlledRead(ByteBuffer buf, int offset, int size) {
		buf.position(offset);

		final int initialPosition = buf.position();

		IrbHeader header = new IrbHeader(buf, size);

		if (buf.position() - initialPosition != size) {
			throw new RuntimeException("byte counting error in reading of IrbHeader; expected " + size + " but read " + (buf.position() - initialPosition));
		}

		return header;
	}

	public IrbHeader(ByteBuffer buf, int size) {
		// TODO: implement parsing HEADER data
		byte[] dummy = new byte[size];
		buf.get(dummy);
	}
}
