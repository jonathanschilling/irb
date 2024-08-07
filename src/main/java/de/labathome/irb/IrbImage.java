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
	public static IrbImage controlledRead(ByteBuffer buf, int offset, int size, boolean isVideoFrameFirstRead) {
		buf.position(offset);

		final int initialPosition = buf.position();

		IrbImage image = new IrbImage(buf, isVideoFrameFirstRead);

		final int expectedSize;
		if (isVideoFrameFirstRead) {
			// only IrbImage header in first occurence
			// -->  IrbImage header (60 bytes)
			//      IrbImage palette (1024 bytes)
			//      IrbImage metadata (644 bytes)
			expectedSize = 60 + 1024 + 644;
		} else {
			expectedSize = size;
		}

		if (buf.position() - initialPosition != expectedSize) {
			throw new RuntimeException("byte counting error in reading of IrbImage; expected " + expectedSize + " but read " + (buf.position() - initialPosition));
		}

		return image;
	}

	/**
	 * Read the IMAGE data corresponding to this block.
	 * @param buf buffer to read image from
	 * @param isVideoFrame
	 */
	public IrbImage(ByteBuffer buf, boolean isVideoFrameFirstRead) {
		readImageHeader(buf);
		// 60

		readPalette(buf);
		// 60 + 1024 = 1084

		readImageMetadata(buf);
		// 1084 + 644 == 1728

		if (isVideoFrameFirstRead) {
			return;
		}

		if (compressed == 0) {
			readImageDataUncompressed(buf);
		} else {
			readImageDataCompressed(buf);
		}
	}

	private void readImageHeader(ByteBuffer buf) {
		final int initialPosition = buf.position();

		bytesPerPixel = buf.getShort();
		compressed = buf.getShort();
		width = buf.getShort();
		height = buf.getShort();

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

		minData = Float.POSITIVE_INFINITY;
		maxData = Float.NEGATIVE_INFINITY;

		data = new float[height][width];
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

				minData = Math.min(minData, v);
				maxData = Math.max(maxData, v);

				data[y][x] = v;
			}
		}

//		System.out.println("data min: " + minData);
//		System.out.println("data max: " + maxData);

		if (buf.position() - initialPosition != (height * width * 2)) {
			throw new RuntimeException("byte counting error in parsing of IrbImage pixel data");
		}
	}

	private void readImageDataCompressed(ByteBuffer buf) {
		// FIXME: likely broken after code cleanup due to lack of test data

		// starting from beginning of IrbImage...
		int offset = 0;

		int dataSize = width * height;

		int pixelCount = dataSize;
		float[] matrixData = new float[pixelCount];

		int matrixDataPos = 0;

		int v1_pos = 1728;

		// used if data is compressed
		int v2_pos = v1_pos + pixelCount;

		int v1 = 0;
		int v2 = 0;

		int v2_count = 0;
		float v = 0.0F;

		float f;

		int v1Min = 1000;
		int v1Max = -1000;

		// compression active: run-length encoding

		for (int i = pixelCount; i > 0; i--) {

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

	private static void checkIs(int expected, int val) {
		// FIXME: figure out the logic behing these values....
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
