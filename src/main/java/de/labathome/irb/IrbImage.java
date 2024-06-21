/* irb
 * IrbImage Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.irb;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;

import aliceinnets.python.jyplot.JyPlot;

public class IrbImage {

	protected final Logger logger = System.getLogger(IrbImage.class.getName());

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
	public String opticsSerial;

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

		// TODO next: remove offset and size from arg list of this constructor...


		// save current buffer position
		final int oldPos = buf.position();

		logger.log(Level.DEBUG, "reading IrbImage starting at offset " + offset);

		// read image data
		buf.position(offset);
		readImageHeader(buf); // 60 bytes fixed
		// at +60 bytes

		int paletteOffset = 60; // ha, a match!
		readPalette(buf, offset + paletteOffset); // 1024 bytes
		// now at +60 + +1024 == +1084 bytes

		final int flagsPosition = offset + FLAGS_OFFSET; // offset + 1084 -> ha, a match!
		readImageFlags(buf, flagsPosition); // 644 bytes
		// now at 60 + 1024 + 644 = 1728 bytes

		// TODO: is this IrbHeaderBlock.headerSize ???
		int bindataOffset = 0x6c0; // 1728 ???
		boolean useCompression = (compressed != 0);
		logger.log(Level.DEBUG, "use compression? " + useCompression);
		readImageData(buf, offset, bindataOffset, width, height, useCompression);

		// restore old position
		buf.position(oldPos);
	}

	/**
	 * Read a fixed-size IrbImage header of length 60 bytes.
	 * @param buf
	 */
	protected void readImageHeader(ByteBuffer buf) {

		final int initialPosition = buf.position();

		bytePerPixel = buf.getShort();
		logger.log(Level.DEBUG, "bytePerPixel: " + bytePerPixel);

		compressed = buf.getShort();
		logger.log(Level.DEBUG, "compressed: " + compressed);

		width = buf.getShort();
		logger.log(Level.DEBUG, "width: " + width);

		height = buf.getShort();
		logger.log(Level.DEBUG, "height: " + height);

		// don't know: always 0
		final int var0 = buf.getInt();
		checkIs(0, var0);

		// don't know: always 0 --> index of first valid pixel column --> ROI ???
		final short var1 = buf.getShort();
		checkIs(0, var1);

		// --> index of last valid pixel column --> ROI ???
		int widthM1 = buf.getShort();
		if (width - 1 != widthM1) {
			System.out.printf("width-1 != widthM1 (%d) ???\n", widthM1);
		}

		// don't know: always 0 -> index of first valid pixel row --> ROI ???
		final short var1a = buf.getShort();
		checkIs(0, var1a);

		// --> index of last valid pixel row --> ROI ???
		int heightM1 = buf.getShort();
		if (height - 1 != heightM1) {
			System.out.printf("height-1 != heightM1 (%d) ???\n", heightM1);
		}

		// don't know: always 0
		// TODO: is -32768 for VARIOCAM, but 0 for oSaveIRB
		final short var2 = buf.getShort();
		logger.log(Level.DEBUG, "var2: " + var2);
		checkIs(0, var2);

		// don't know: always 0
		final short var3 = buf.getShort();
		checkIs(0, var3);

		emissivity = buf.getFloat();
		logger.log(Level.DEBUG, "emissivity: " + emissivity);

		distance = buf.getFloat();
		logger.log(Level.DEBUG, "distance: " + distance);

		environmentalTemp = buf.getFloat();
		logger.log(Level.DEBUG, "environmentalTemp: " + environmentalTemp);

		// don't know: always 0
		final short var4 = buf.getShort();
		checkIs(0, var4);

		// don't know: always 0
		// TODO: is -32768 for VARIOCAM, oSaveIRB (from VARIOCAM_HD)
		// --> 0x8000 as unsigned short ??? bit field ???
		final short var5 = buf.getShort();
		logger.log(Level.DEBUG, "var5: " + var5);
		checkIs(0, var5);

		pathTemperature = buf.getFloat();
		logger.log(Level.DEBUG, "pathTemperature: " + pathTemperature);

		// don't know: always 0x65 --> ASCII "e"
		// TODO: is 0 for VARIOCAM
		final short var6 = buf.getShort();
		checkIs(0x65, var6);

		// don't know: always 0
		// TODO: is 16256 for VARIOCAM ??? bit field ???
		final short var7 = buf.getShort();
		logger.log(Level.DEBUG, "var7: " + var7);
		checkIs(0, var7);

		centerWavelength = buf.getFloat();
		logger.log(Level.DEBUG, "centerWavelength: " + centerWavelength);

		// don't know: always 0
		final short var8 = buf.getShort();
		checkIs(0, var8);

		// don't know: always 0x4080 ???
		// -> could be two separate bytes ??? bit field ???
		final short var9 = buf.getShort();
		checkIs(0x4080, var9);

		// don't know: always 0x9
		final short var10 = buf.getShort();
		checkIs(0x9, var10);

		// don't know: always 0x101
		// -> could be two bytes: 1 and 1 ?
		final short var11 = buf.getShort();
		checkIs(0x101, var11);

		if (width > 10000 || height > 10000) {
			System.out.printf("error: width (%d) or height (%d) out-of-range!\n", width, height);
			width = 1;
			height = 1;
			return;
		}

		int headerLength = buf.position() - initialPosition;
		System.out.printf("header length so far is %d\n", headerLength); // 60 bytes
	}

	private void readImageFlags(ByteBuffer buf, int position) {
		// save current buffer position
		final int oldPos = buf.position();

		calibRangeMin = buf.getFloat(position + 92);
		calibRangeMax = buf.getFloat(position + 96);

		device = readNullTerminatedString(buf, position + 142, 12);

		opticsSerial = readNullTerminatedString(buf, position + 186, 16);
		optics = readNullTerminatedString(buf, position + 202, 32);
		opticsResolution = readNullTerminatedString(buf, position + 234, 32);

		deviceSerial = readNullTerminatedString(buf, position + 450, 16);

		shotRangeStartErr = buf.getFloat(position + 532);
		shotRangeSize = buf.getFloat(position + 536);

		timestampRaw = buf.getDouble(position + 540);
		timestamp = fromDoubleToDateTime(timestampRaw);

		timestampMillisecond = buf.getInt(position + 548);

		opticsText = readNullTerminatedString(buf, position + 554, 48);

		// restore old position
		buf.position(oldPos);
	}

	/**
	 *
	 * @param buf
	 * @param offset - location where full image data block (incl header) starts
	 * @param bindataOffset 0x6c0 == 1728
	 * @param width 640 mostly
	 * @param height 480 mostly
	 * @param paletteOffset fixed at 60
	 * @param useCompression true or false
	 */
	private void readImageData(ByteBuffer buf, int offset, int bindataOffset, int width, int height, boolean useCompression) {

		int dataSize = width * height;

		int pixelCount = dataSize;
		float[] matrixData = new float[pixelCount];

		int matrixDataPos = 0;

		int v1_pos = bindataOffset;

		// used if data is compressed
		int v2_pos = v1_pos + pixelCount;

		int v1 = 0;
		int v2 = 0;

		int v2_count = 0;
		float v = 0.0F;

		float f;

		int v1Min = 1000;
		int v1Max = -1000;

		if (useCompression) {
			// compression active: run-length encoding

			for (int i = pixelCount; i > 0; i--) {

				if (v2_count-- < 1) {
					// happens on first call and then again after v2_count (was read) pixels(?) have passed
					// --> run-length encoding ???

					v2_count = buf.get(offset + v2_pos) - 1;
					v2_pos++;
					v2 = buf.get(offset + v2_pos);
					v2_pos++;

					if (v2 < 0) {
						// handle reading uint8_t
						v2 += 256;
					}
				}

				v1 = buf.get(offset + v1_pos);
				v1_pos++;

				if (v1 < 0) {
					// handle reading uint8_t
					v1 += 256;
				}

				v1Min = Math.min(v1Min, v1);
				v1Max = Math.max(v1Max, v1);

				f = v1 / 256.0F;

				// linear interpolation between neighboring palette entries
				v = palette[v2 + 1] * f + palette[v2] * (1.0F - f);
				if (v < 0.0F) {
					// negative Kelvin temperatures are forbidden?
					v = 0.0F; // or 255 ...
				}

				matrixData[matrixDataPos] = v;
				matrixDataPos++;
			}
		} else {
			logger.log(Level.DEBUG, "start reading image at offset " + (offset + v1_pos));

			// no compression
			for (int i = pixelCount; i > 0; i--) {

				// v1 is used to compute f, which represents a fraction in [0, 1[
				v1 = buf.get(offset + v1_pos);
				v1_pos++;

				// v2 seems to be the index of the current interval in the palette
				// -> for a fixed palette, we thus expect every second byte of an image to be the same value
				v2 = buf.get(offset + v1_pos);
				v1_pos++;

				if (v1 < 0) {
					// handle reading uint8_t -> make v1 be in range 0 ... 255
					v1 += 256;
				}

				if (v2 < 0) {
					// handle reading uint8_t -> make v2 be in range 0 ... 255
					v2 += 256;
				}

				if (i == pixelCount) {
					System.out.println("v2 = " + v2); // 0x72 == 114
				}

				// keep track of min/max occuring values for v1
				// --> seems to actually use full range 0 to 255!
				// --> and then palette entry is used to re-scale to actual temperature range...?
				v1Min = Math.min(v1Min, v1);
				v1Max = Math.max(v1Max, v1);

				// fraction between 0 and 1
				// specifically, v1 can be 0 ... 255
				// --> i / 256 goes from 0 to just below 1
				f = v1 / 256.0F;

				// linear interpolation between neighboring palette entries
				v = palette[v2 + 1] * f + palette[v2] * (1.0F - f);
				if (v < 0.0F) {
					// negative Kelvin temperatures are forbidden?
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
		for (int i = 0; i < pixelCount; ++i) {
			final int row = i / width;
			final int col = i % width;
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
		for (int i = 0; i < height; ++i) {
			for (int j = 0; j < width; ++j) {
				celsiusData[i][j] = data[i][j] - CELSIUS_OFFSET;
			}
		}
		return celsiusData;
	}

	/**
	 * Read a fixed-size palette: 256 `float`s of 4 byte each
	 * --> total length is 1024 bytes
	 * @param buf
	 * @param offset `offset` of image start, + paletteOffset == 60
	 * @return
	 */
	private void readPalette(ByteBuffer buf, int offset) {
		// save current buffer position
		final int oldPos = buf.position();

		palette = new float[256];

		buf.position(offset);
		for (int i = 0; i < 256; ++i) {
			palette[i] = buf.getFloat();
		}

		// restore old position
		buf.position(oldPos);
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
