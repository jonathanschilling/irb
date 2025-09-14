/* irb
 * IrbPreview Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.irb;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * <pre>
 * 4832 bytes
 * * 32 bytes header
 *   * 4 magic bytes: x l a \ff
 *   * unknown 32-bit int - example has 1 in there - ID for palette?
 *   * width 32-bit int: example has 80
 *   * height 32-bit int: example has 60
 *   * four more unknown 32-bit integers ???
 * * 4800 bytes image data
 *   * 60 pixels high (slow dimension)
 *   * 80 pixels wide (fast dimension) --> row-major
 *   * 1 byte per pixel, unsigned char - fixed colormap?
 * </pre>
 */
public class IrbPreview {

	/** x l a \ff */
	private static final byte[] MAGIC_ID = { 'x', 'l', 'a', (byte) 0xff };

	/** unknown bit field at offset 4 */
	public int bitfield_0;

	/** width of preview image; typically 80 */
	public int width;

	/** height of preview image; typically 60 */
	public int height;

	/** unknown bit field at offset 16 */
	public int bitfield_1;

	/** unknown bit field at offset 20 */
	public int bitfield_2;

	/** unknown bit field at offset 24 */
	public int bitfield_3;

	/** unknown bit field at offset 28 */
	public int bitfield_4;

	/** [height * width] preview image: typically unsigned 8-bit values (0 .. 255)
	 * row-major: width is fast dimension */
	public byte[] image;

	private IrbPreview() { }

	/**
	 * Read the PREVIEW data blob.
	 *
	 * @param buf    buffer to read image from
	 * @param offset
	 * @param size
	 */
	public static IrbPreview fromBuffer(ByteBuffer buf, int offset, int size) {
		IrbPreview preview = new IrbPreview();

		buf.position(offset);

		final int initialPosition = buf.position();
		// 0

		// 32 byte header

		// parse magic number ID
		final byte[] magicBytes = new byte[4];
		buf.get(magicBytes);
		// 4
		if (!Arrays.equals(magicBytes, MAGIC_ID)) {
			throw new RuntimeException("first 4 magic bytes of PREVIEW block invalid");
		}

		preview.bitfield_0 = buf.getInt();
		// 8

		preview.width = buf.getInt();
		// 12

		preview.height = buf.getInt();
		// 16

		preview.bitfield_1 = buf.getInt();
		// 20

		preview.bitfield_2 = buf.getInt();
		// 24

		preview.bitfield_3 = buf.getInt();
		// 28

		preview.bitfield_4 = buf.getInt();
		// 32

		// (typically) 4800 byte preview image: 80 x 60 pixels, 1 byte per pixel
		preview.image = new byte[preview.height * preview.width];
		for (int y = 0; y < preview.height; ++y) {
			for (int x = 0; x < preview.width; ++x) {
				preview.image[y * preview.width + x] = buf.get();
			}
		}

		if (buf.position() - initialPosition != size) {
			throw new RuntimeException("byte counting error in reading of IrbPreview; expected " + size + " but read " + (buf.position() - initialPosition));
		}

		return preview;
	}

	public short[][] getPreviewImage() {
		short[][] preview_2d = new short[height][width];
		for (int y = 0; y < height; ++y) {
			for (int x = 0; x < width; ++x) {
				short pixel_value = image[y * width + x];
				if (pixel_value < 0) {
					pixel_value += 256;
				}
				preview_2d[y][x] = pixel_value;
			}
		}
		return preview_2d;
	}
}
