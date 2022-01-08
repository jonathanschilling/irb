/* irb
 * IrbImage Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

public class IrbImage {

	private static final int FLAGS_OFFSET = 1084;
	public static final float CELSIUS_OFFSET = 273.15F;

	@Expose(serialize = true)
	public int width;

	@Expose(serialize = true)
	public int height;

	@Expose(serialize = true)
	public short bytePerPixel;

	@Expose(serialize = true)
	public short compressed;

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
	 * Read the image data corresponding to this block.
	 *
	 * @param buf    buffer to read image from
	 * @param offset
	 * @param size
	 */
	public IrbImage(ByteBuffer buf, int offset, int size) {

		// save current buffer position
		final int oldPos = buf.position();

		// read image data
		buf.position(offset);

		bytePerPixel = buf.getShort();
		compressed = buf.getShort();
		width = buf.getShort();
		height = buf.getShort();

		// don't know: always 0
		checkIs(0, buf.getInt());

		// don't know: always 0
		checkIs(0, buf.getShort());

		int widthM1 = buf.getShort();
		if (width - 1 != widthM1) {
			System.out.printf("width-1 != widthM1 (%d) ???\n", widthM1);
		}

		// don't know: always 0
		checkIs(0, buf.getShort());

		int heightM1 = buf.getShort();
		if (height - 1 != heightM1) {
			System.out.printf("height-1 != heightM1 (%d) ???\n", heightM1);
		}

		// don't know: always 0
		// TODO: is -32768 for VARIOCAM
		checkIs(0, buf.getShort());

		// don't know: always 0
		checkIs(0, buf.getShort());

		emissivity = buf.getFloat();
		distance = buf.getFloat();
		environmentalTemp = buf.getFloat();

		// don't know: always 0
		checkIs(0, buf.getShort());

		// don't know: always 0
		// TODO: is -32768 for VARIOCAM
		checkIs(0, buf.getShort());

		pathTemperature = buf.getFloat();

		// don't know: always 0x65
		// TODO: is 0 for VARIOCAM
		checkIs(0x65, buf.getShort());

		// don't know: always 0
		// TODO: is 16256 for VARIOCAM
		checkIs(0, buf.getShort());

		centerWavelength = buf.getFloat();

		// don't know: always 0
		checkIs(0, buf.getShort());

		// don't know: always 0x4080
		checkIs(0x4080, buf.getShort());

		// don't know: always 0x9
		checkIs(0x9, buf.getShort());

		// don't know: always 0x101
		checkIs(0x101, buf.getShort());

		if (width > 10000 || height > 10000) {
			System.out.printf("error: width (%d) or height (%d) out-of-range!\n", width, height);
			width = 1;
			height = 1;
			return;
		}

		final int flagsPosition = offset + FLAGS_OFFSET;
		readImageFlags(buf, flagsPosition);

		// TODO: is this IrbHeaderBlock.headerSize ???
		int bindataOffset = 0x6c0;
		int paletteOffset = 60;
		boolean useCompression = (compressed != 0);
		readImageData(buf, offset, bindataOffset, width, height, paletteOffset, useCompression);

		// restore old position
		buf.position(oldPos);
	}

	private void readImageFlags(ByteBuffer buf, int position) {
		// save current buffer position
		final int oldPos = buf.position();

		calibRangeMin = buf.getFloat(position + 92);
		calibRangeMax = buf.getFloat(position + 96);

		device = readNullTerminatedString(buf, position + 142, 12);
		deviceSerial = readNullTerminatedString(buf, position + 450, 16); // was: 186
		optics = readNullTerminatedString(buf, position + 202, 32);
		opticsResolution = readNullTerminatedString(buf, position + 234, 32);
		opticsText = readNullTerminatedString(buf, position + 554, 48);

		shotRangeStartErr = buf.getFloat(position + 532);
		shotRangeSize = buf.getFloat(position + 536);

		timestampRaw = buf.getDouble(position + 540);
		timestampMillisecond = buf.getInt(position + 548);

		timestamp = fromDoubleToDateTime(timestampRaw);

		// restore old position
		buf.position(oldPos);
	}

	private void readImageData(ByteBuffer buf, int offset, int bindataOffset, int width, int height, int paletteOffset, boolean useCompression) {

		int dataSize = width * height;

		int pixelCount = dataSize;
		float[] matrixData = new float[pixelCount];

		int matrixDataPos = 0;

		int v1_pos = bindataOffset;

		// used if data is compressed
		int v2_pos = v1_pos + pixelCount;

		int v1 = 0;
		int v2 = 0;

		palette = readPalette(buf, offset + paletteOffset);

		int v2_count = 0;
		float v = 0.0F;

		float f;

		int v1Min = 1000;
		int v1Max = -1000;

		if (useCompression) {
			// compression active: run-length encoding

			for (int i=pixelCount; i>0; i--) {

				if (v2_count-- < 1) {
					v2_count = buf.get(offset + v2_pos) - 1;
					v2_pos++;
					v2 = buf.get(offset + v2_pos);
					v2_pos++;

					if (v2 < 0) {
						v2 += 256;
					}
				}

				v1 = buf.get(offset + v1_pos);
				v1_pos++;

				if (v1 < 0) {
					v1 += 256;
				}

				v1Min = Math.min(v1Min, v1);
				v1Max = Math.max(v1Max, v1);

				f = v1 / 256.0F;

				// linear interpolation between neighboring palette entries
				v = palette[v2 + 1] * f + palette[v2] * (1.0F - f);
				if (v < 0.0F) {
					v = 0.0F; // or 255 ...
				}

				matrixData[matrixDataPos] = v;
				matrixDataPos++;
			}
		} else {
			// no compression
			for (int i=pixelCount; i>0; i--) {

				v1 = buf.get(offset + v1_pos);
				v1_pos++;
				v2 = buf.get(offset + v1_pos);
				v1_pos++;

				if (v1 < 0) {
					v1 += 256;
				}

				if (v2 < 0) {
					v2 += 256;
				}

				v1Min = Math.min(v1Min, v1);
				v1Max = Math.max(v1Max, v1);

				f = v1 / 256.0F;

				// linear interpolation between neighboring palette entries
				v = palette[v2 + 1] * f + palette[v2] * (1.0F - f);
				if (v < 0.0F) {
					v = 0.0F; // or 255 ...
				}

				matrixData[matrixDataPos] = v;
				matrixDataPos++;
			}
		}

		System.out.println("v1 min " + v1Min);
		System.out.println("v1 max " + v1Max);

		minData = Float.POSITIVE_INFINITY;
		maxData = Float.NEGATIVE_INFINITY;

		data = new float[height][width];
		for (int i=0; i<pixelCount; ++i) {
			final int row = i/width;
			final int col = i%width;
			data[row][col] = matrixData[i];

			minData = Math.min(minData, matrixData[i]);
			maxData = Math.max(maxData, matrixData[i]);
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
		float[][] celsiusData = new float[height][width];
		for (int i=0; i<height; ++i) {
			for (int j=0; j<width; ++j) {
				celsiusData[i][j] = data[i][j] - CELSIUS_OFFSET;
			}
		}
		return celsiusData;
	}

	private float[] readPalette(ByteBuffer buf, int offset) {
		// save current buffer position
		final int oldPos = buf.position();

		float[] palette = new float[256];

		buf.position(offset);
		for (int i=0; i<256; ++i) {
			palette[i] = buf.getFloat();
		}

		// restore old position
		buf.position(oldPos);

		return palette;
	}

	private static void checkIs(int expected, int val) {
		if (expected != val) {
			System.out.printf("expected %d but got %d\n", expected, val);
		}
	}

	private static String readNullTerminatedString(ByteBuffer buf, int offset, int len) {
		byte[] strBytes = new byte[len];
		buf.position(offset);
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
	 * @param filename file to export image data to
	 */
	public void exportImageData(String filename) {
		try(BufferedWriter w = new BufferedWriter(new FileWriter(filename))) {
			float[][] celsiusData = getCelsiusImage();
			int height = celsiusData.length;
			int width = celsiusData[0].length;
			for (int i=height-1; i>=0; i--) {
				for (int j=0; j<width; ++j) {
					w.write(String.format(Locale.ENGLISH, "%8.6f ", celsiusData[i][j]));
				}
				w.write("\n");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
