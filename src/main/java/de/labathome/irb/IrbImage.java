/* irb
 * IrbImage Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.irb;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

public class IrbImage {

	public static final float CELSIUS_OFFSET = 273.15F;

	@Expose(serialize = true)
	public int width;

	@Expose(serialize = true)
	public int height;

	@Expose(serialize = true)
	public short bytesPerPixel;

	@Expose(serialize = true)
	public short compression_type;

	@Expose(serialize = true)
	public float emissivity;

	@Expose(serialize = true)
	public float distance;

	@Expose(serialize = true)
	public float environmentalTemp;

	@Expose(serialize = true)
	public float pathTemperature;

	@Expose(serialize = true)
	public float centerWavelength;

	@Expose(serialize = true)
	public float calibRangeMin;

	@Expose(serialize = true)
	public float calibRangeMax;

	@Expose(serialize = true)
	public String device;

	@Expose(serialize = true)
	public String deviceSerial;

	@Expose(serialize = true)
	public String optics;

	@Expose(serialize = true)
	public String opticsResolution;

	@Expose(serialize = true)
	public String opticsSerial;

	@Expose(serialize = true)
	public String opticsText;

	@Expose(serialize = true)
	public float shotRangeStartErr;

	@Expose(serialize = true)
	public float shotRangeSize;

	@Expose(serialize = true)
	public double timestampRaw;

	@Expose(serialize = true)
	public int timestampMillisecond;

	@Expose(serialize = true)
	public Date timestamp;

	@Expose(serialize = true)
	public float[] palette;

	@Expose(serialize = true)
	public float minData;

	@Expose(serialize = true)
	public float maxData;

	@Expose(serialize = false)
	public float[][] data;

	/**
	 * Read the IMAGE data corresponding to this block.
	 *
	 * @param buf    buffer to read image from
	 * @param offset
	 * @param size
	 */
	public static IrbImage fromBuffer(ByteBuffer buf, int offset, int size, boolean isVideoFrameFirstRead) {
		buf.position(offset);

		final int initialPosition = buf.position();

		IrbImage image = new IrbImage(buf, offset, size, isVideoFrameFirstRead);

		final int expectedSize;
		if (isVideoFrameFirstRead) {
			// only IrbImage without actual raw image data in first occurrence
			// -->  IrbImage header   (  60 bytes)
			//      IrbImage palette  (1024 bytes)
			//      IrbImage metadata ( 644 bytes)
			expectedSize = 60 + 1024 + 644; // == 1728 bytes
		} else {
			expectedSize = size;
		}

		if (buf.position() - initialPosition != expectedSize) {
			System.out.println("WARNING: byte counting error in reading of IrbImage; expected " + expectedSize +
					" but read " + (buf.position() - initialPosition));
		}

		return image;
	}

	/**
	 * Read the IMAGE data corresponding to this block.
	 * @param buf buffer to read image from
	 * @param isVideoFrame
	 */
	public IrbImage(ByteBuffer buf, int offset, int size, boolean isVideoFrameFirstRead) {
		readImageHeader(buf);
		// 60

		readPalette(buf);
		// 60 + 1024 = 1084

		readImageMetadata(buf);
		// 1084 + 644 == 1728

		if (isVideoFrameFirstRead) {
			return;
		}

		data = new float[height][width];

		switch (compression_type) {
		case 0:
			readImageDataUncompressed(buf);
			break;
		case 1:
			readImageDataCompressed1(buf);
			break;
		case 2:
			readImageDataCompressed2(buf, offset + 1728, size - 1728);
			break;
		default:
			throw new RuntimeException("unknown compression type: " + compression_type);
		}

		updateDataRange();
	}

	private void readImageHeader(ByteBuffer buf) {
		final int initialPosition = buf.position();

		bytesPerPixel = buf.getShort();
		compression_type = buf.getShort();
		width = buf.getShort();
		height = buf.getShort();

		System.out.printf("# IMAGE: bytesPerPixel=%d compression_type=%d width=%d height=%d\n",
				bytesPerPixel, compression_type, width, height);

		// don't know: always 0
		int var1 = buf.getInt();
		checkIs(0, var1);

		// don't know: always 0
		// could be start of ROI
		short var2 = buf.getShort();
		checkIs(0, var2);

		// could be end of ROI
		int widthM1 = buf.getShort();
		if (width - 1 != widthM1) {
			System.out.printf("width-1 != widthM1 (%d) ???\n", widthM1);
		}

		// don't know: always 0
		// could be start of ROI
		short var3 = buf.getShort();
		checkIs(0, var3);

		// could be end of ROI
		int heightM1 = buf.getShort();
		if (height - 1 != heightM1) {
			System.out.printf("height-1 != heightM1 (%d) ???\n", heightM1);
		}

		// don't know: always 0
		// TODO: is -32768 for VARIOCAM
		short var4 = buf.getShort();
		checkIs(0, var4);

		// don't know: always 0
		short var5 = buf.getShort();
		checkIs(0, var5);

		emissivity = buf.getFloat();
		distance = buf.getFloat();
		environmentalTemp = buf.getFloat();

		// don't know: always 0
		short var6 = buf.getShort();
		checkIs(0, var6);

		// don't know: always 0
		// TODO: is -32768 for VARIOCAM
		short var7 = buf.getShort();
		checkIs(0, var7);

		pathTemperature = buf.getFloat();

		// don't know: always 0x65
		// TODO: is 0 for VARIOCAM
		short var8 = buf.getShort();
		checkIs(0x65, var8);

		// don't know: always 0
		// TODO: is 16256 for VARIOCAM
		short var9 = buf.getShort();
		checkIs(0, var9);

		centerWavelength = buf.getFloat();

		// don't know: always 0
		short var10 = buf.getShort();
		checkIs(0, var10);

		// don't know: always 0x4080
		short var11 = buf.getShort();
		checkIs(0x4080, var11);

		// don't know: always 0x9
		short var12 = buf.getShort();
		checkIs(0x9, var12);

		// don't know: always 0x101
		short var13 = buf.getShort();
		checkIs(0x101, var13);

		if (width > 10000 || height > 10000) {
			System.out.printf("error: width (%d) or height (%d) out-of-range!\n", width, height);
			width = 1;
			height = 1;
			return;
		}

		if (buf.position() - initialPosition != 60) {
			throw new RuntimeException("byte counting error in parsing of IrbImage header");
		}
	}

	/**
	 * Read palette from image buffer.
	 * 1024 bytes, 256 floats (32-bit each = 4 bytes each), little-endian;
	 * maps 8-bit unsigned integer palette entries (which are stored in raw image data)
	 * to temperature
	 * @param buf buffer to read palette from
	 */
	private void readPalette(ByteBuffer buf) {
		palette = new float[256];
		for (int i = 0; i < 256; ++i) {
			palette[i] = buf.getFloat();
		}
	}

	private void readImageMetadata(ByteBuffer buf) {
		final int initialPosition = buf.position();
		// 0

		byte[] dummy1 = new byte[92];
		buf.get(dummy1);
		// 92

		calibRangeMin = buf.getFloat();
		// 96

		calibRangeMax = buf.getFloat();
		// 100

		byte[] dummy2 = new byte[42];
		buf.get(dummy2);
		// 142

		device = readNullTerminatedString(buf, 12);
		// 154

		byte[] dummy3 = new byte[10];
		buf.get(dummy3);
		// 164

		deviceSerial = readNullTerminatedString(buf, 16);
		// 180

		byte[] dummy3a = new byte[22];
		buf.get(dummy3a);
		// 202

		optics = readNullTerminatedString(buf, 32);
		// 234

		opticsResolution = readNullTerminatedString(buf, 32);
		// 266

		byte[] dummy4 = new byte[184];
		buf.get(dummy4);
		// 450

		opticsSerial = readNullTerminatedString(buf, 16);
		// 466

		byte[] dummy5 = new byte[66];
		buf.get(dummy5);
		// 532

		shotRangeStartErr = buf.getFloat();
		// 536

		shotRangeSize = buf.getFloat();
		// 540

		timestampRaw = buf.getDouble();
		timestamp = fromDoubleToDateTime(timestampRaw);
		// 548

		timestampMillisecond = buf.getInt();
		// 552

		buf.getShort();
		// 554

		opticsText = readNullTerminatedString(buf, 48);
		// 602

		byte[] dummy6 = new byte[42];
		buf.get(dummy6);
		// 644

		if (buf.position() - initialPosition != 644) {
			throw new RuntimeException("byte counting error in parsing of IrbImage metadata");
		}
	}

	private void readImageDataUncompressed(ByteBuffer buf) {
		final int initialPosition = buf.position();

		for (int y = 0; y < height; ++y) {
			for (int x = 0; x < width; ++x) {

				short v1 = buf.get();
				if (v1 < 0) {
					v1 += 256;
				}

				float f = v1 / 256.0F;

				short v2 = buf.get();
				if (v2 < 0) {
					v2 += 256;
				}

				// linear interpolation between neighboring palette entries
				float v = palette[v2 + 1] * f + palette[v2] * (1.0F - f);
				if (v < 0.0F) {
					v = 0.0F; // or 255 ...
				}

				data[y][x] = v;
			}
		}

		if (buf.position() - initialPosition != (height * width * 2)) {
			throw new RuntimeException("byte counting error in parsing of IrbImage pixel data");
		}
	}

	private void readImageDataCompressed1(ByteBuffer buf) {
		int offset = buf.position();

		int dataSize = width * height;

		int pixelCount = dataSize;

		int v1_pos = 0;

		// used if data is compressed
		int v2_pos = v1_pos + pixelCount;

		int v2_count = 0;
		int v2 = 0;

		// compression active: run-length encoding
		for (int y = 0; y < height; ++y) {
			for (int x = 0; x < width; ++x) {
				if (v2_count == 0) {
					v2_count = buf.get(offset + v2_pos);
					if (v2_count < 0) {
						v2_count += 256;
					}

					v2_pos++;

					// ----------

					v2 = buf.get(offset + v2_pos);
					if (v2 < 0) {
						v2 += 256;
					}

					v2_pos++;
				}

				int v1 = buf.get(offset + v1_pos);
				if (v1 < 0) {
					v1 += 256;
				}

				v1_pos++;

				float f = v1 / 256.0F;

				// linear interpolation between neighboring palette entries
				float v = palette[v2 + 1] * f + palette[v2] * (1.0F - f);
				if (v < 0.0F) {
					v = 0.0F; // or 255 ...
				}

				data[y][x] = v;

				v2_count--;
			}
		}

		// TODO: position buffer at end to make santiy checks happy
		// or better: fix sanity check...
	}

	// Put a simple container around the bit-buffer logic so the core stays readable.
    // This reader refills from 16-bit little-endian words and exposes MSB-first bits.
    private static final class BitReaderLE16MSB {
        // Underlying byte array (compressed stream)
        private final byte[] data;
        // Current byte position in 'data' (advanced by 2 on each 16-bit refill)
        private int pos;
        // 32-bit shift register used as a left-shifting buffer
        private int buf;
        // Number of valid bits currently in 'buf'
        private int bits;

        // Construct a reader that starts at 'offset' bytes into 'data'
        BitReaderLE16MSB(byte[] data, int offset) {
            this.data = data;
            this.pos = offset;
            this.buf = 0;
            this.bits = 0;
        }

        // Refill 'buf' with one 16-bit little-endian word if fewer than 16 bits are present.
        // The new 16 bits are aligned to the high side of 'buf' so that future MSB-first reads work.
        private void refill16() {
            if (bits < 16 && pos + 1 < data.length) {
                int lo = data[pos] & 0xFF;
                int hi = data[pos + 1] & 0xFF;
                int w = lo | (hi << 8);
                pos += 2;
                // Align the just-read 16 bits to the high side of the 32-bit buffer
                buf = ((w << (16 - bits)) & 0xFFFFFFFF) | buf;
                bits += 16;
            }
        }

        // Read exactly 8 bits as an unsigned byte, MSB-first from 'buf'.
        int read8() {
            if (bits < 16) {
                refill16();
            }
            if (bits < 8) {
                throw new IllegalArgumentException("Unexpected end of stream while reading 8 bits");
            }
            int out = (buf >>> 24) & 0xFF;
            buf = (buf << 8);
            bits -= 8;
            return out;
        }

        // Read 'n' bits (1..24) as an unsigned integer, MSB-first from 'buf'.
        int readBits(int n) {
            if (n <= 0 || n > 24) {
                throw new IllegalArgumentException("readBits width out of range: " + n);
            }
            while (bits < n) {
                refill16();
                if (bits < n && pos + 1 >= data.length) {
                    throw new IllegalArgumentException("Unexpected end of stream while reading " + n + " bits");
                }
            }
            int out = (buf >>> (32 - n)) & ((1 << n) - 1);
            buf = (buf << n);
            bits -= n;
            return out;
        }
    }

	private void readImageDataCompressed2(ByteBuffer buf, int offset, int size) {
		buf.position(offset);

		byte[] compressed = new byte[size];
		buf.get(compressed);

		// Compute total number of pixels to decode
        final int n = width * height;

        // Read the first pixel as a 16-bit little-endian literal
        final int p0 = ((compressed[1] & 0xFF) << 8) | (compressed[0] & 0xFF);

        // Store the first pixel at (0,0)
        int pixel_value = (short) (p0 & 0xFFFF);
		if (pixel_value < 0) {
        	pixel_value += 65536;
        }
		// TODO: figure out if this scaling is correct - looks somewhat reasonable for an example though
        data[0][0] = pixel_value / 100.0F;

        // Prepare to decode the remaining (n - 1) deltas
        final BitReaderLE16MSB br = new BitReaderLE16MSB(compressed, 2);

        // Set up the prefix threshold and the extended-code parameters
        // prefix values 0..191 are "short" deltas
        final int THRESH = 192;
        // extended payload width
        final int EXT_BITS = 11;
        final int EXT_MAX = (1 << EXT_BITS) - 1;
        // subtraction bias applied to extended codes
        final int BIAS = THRESH * EXT_MAX;

        // Track previous sample to reconstruct absolute values from deltas
        int prev = p0;

        // Decode in raster order, filling the 2D array row by row
        int x = 1;
        int y = 0;

        // Iterate over all remaining pixels
        for (int i = 1; i < n; i++) {
            // Read the 8-bit prefix
            int prefix = br.read8();

            // Compute the unsigned delta token 'u' (short or extended)
            int u;
            if (prefix < THRESH) {
                u = prefix;
            } else {
                // Read the extended payload, then unbias
                int ext = br.readBits(EXT_BITS);
                u = ((prefix << EXT_BITS) | ext) - BIAS;
            }

            // Convert unsigned token to signed delta using the codec's even/odd rule
            int d = u >>> 1;
            if ((u & 1) != 0) {
                d = -d;
            }

            // Accumulate delta with wrap to 16-bit
            prev = (prev + d) & 0xFFFF;

            // Write the reconstructed 16-bit sample into the output raster
            pixel_value = (short) (prev & 0xFFFF);;
            if (pixel_value < 0) {
            	pixel_value += 65536;
            }
            data[y][x] = pixel_value / 100.0F;

            // Advance raster position
            x++;
            if (x == width) {
                x = 0;
                y++;
            }
        }

		if (buf.position() - offset != size) {
			throw new RuntimeException("byte counting error in parsing of IrbImage pixel data");
		}
	}

	private void updateDataRange() {
		minData = Float.POSITIVE_INFINITY;
		maxData = Float.NEGATIVE_INFINITY;

		for (int y = 0; y < height; ++y) {
			for (int x = 0; x < width; ++x) {
				minData = Math.min(minData, data[y][x]);
				maxData = Math.max(maxData, data[y][x]);
			}
		}

		System.out.println("data min: " + minData);
		System.out.println("data max: " + maxData);
	}

	/**
	 * Get image in deg. Celsius
	 *
	 * @return [height][width] image data
	 */
	public float[][] getCelsiusImage() {
		float[][] celsiusData = new float[data.length][data[0].length];
		for (int i = 0; i < data.length; ++i) {
			for (int j = 0; j < data[0].length; ++j) {
				celsiusData[i][j] = data[i][j] - CELSIUS_OFFSET;
			}
		}
		return celsiusData;
	}

	private static void checkIs(int expected, int val) {
		// FIXME: figure out the logic behind these values....
//		if (expected != val) {
//			System.out.printf("expected %d but got %d\n", expected, val);
//		}
	}

	private static String readNullTerminatedString(ByteBuffer buf, int len) {
		byte[] strBytes = new byte[len];
		buf.get(strBytes);
		return new String(strBytes).trim();
	}

	/** from https://stackoverflow.com/a/23673012 */
	private static Date fromDoubleToDateTime(double OADate) {
		long num = (long) ((OADate * 86400000.0) + ((OADate >= 0.0) ? 0.5 : -0.5));
		if (num < 0L) {
			num -= (num % 0x5265c00L) * 2L;
		}
		num += 0x3680b5e1fc00L;
		num -= 62135596800000L;

		return new Date(num);
	}

	/**
	 * Export all meta-data (except the actual image data) to a JSON file.
	 *
	 * @param filename file to export metadata to
	 */
	public void exportMetaData(String filename) {
		try (BufferedWriter w = new BufferedWriter(new FileWriter(filename))) {
			Gson gson = new GsonBuilder()
					.setPrettyPrinting()
					.excludeFieldsWithoutExposeAnnotation()
					.create();
			String json = gson.toJson(this);
			w.write(json);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Export image data as 2d text file.
	 *
	 * @param filename file to export image data to
	 */
	public void exportImageData(String filename) {
		try (BufferedWriter w = new BufferedWriter(new FileWriter(filename))) {
			float[][] celsiusData = getCelsiusImage();
			int height = celsiusData.length;
			int width = celsiusData[0].length;
			for (int i = height - 1; i >= 0; i--) {
				for (int j = 0; j < width; ++j) {
					w.write(String.format(Locale.ENGLISH, "%8.6f ", celsiusData[i][j]));
				}
				w.write("\n");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
