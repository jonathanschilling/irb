package de.labathome;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Date;

public class IrbImage {

	private static final int FLAGS_OFFSET = 1084;

	private int width;
	private int height;
	private short bytePerPixel;
	private short compressed;
	private float emissivity;
	private float distance;
	private float environmentalTemp;
	private float pathTemperature;
	private float centerWavelength;

	private float calibRangeMin;
	private float calibRangeMax;
	private String device;
	private String deviceSerial;

	private String optics;

	private String opticsResolution;

	private String opticsText;

	private float shotRangeStartErr;

	private float shotRangeSize;

	private double timestampRaw;

	private int timestampMillisecond;

	private Date timestamp;


	private static void checkIs(int expected, int val) {
		if (expected != val) {
			System.out.printf("expected %d but got %d\n", expected, val);
		}
	}

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
		checkIs(0, buf.getShort());

		// don't know: always 0
		checkIs(0, buf.getShort());

		emissivity = buf.getFloat();
		distance = buf.getFloat();
		environmentalTemp = buf.getFloat();

		// don't know: always 0
		checkIs(0, buf.getShort());

		// don't know: always 0
		checkIs(0, buf.getShort());

		pathTemperature = buf.getFloat();

		// don't know: always 0x65
		checkIs(0x65, buf.getShort());

		// don't know: always 0
		checkIs(0, buf.getShort());

		centerWavelength = buf.getFloat();

		// don't know: always 0
		checkIs(0, buf.getShort());

		// don't know: always 0xh4080
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

	private static String readNullTerminatedString(ByteBuffer buf, int offset, int len) {
		byte[] strBytes = new byte[len];
		buf.position(offset);
		buf.get(strBytes);
		return new String(strBytes).trim();
	}

	public static Date fromDoubleToDateTime(double OADate)
	{
	    long num = (long) ((OADate * 86400000.0) + ((OADate >= 0.0) ? 0.5 : -0.5));
	    if (num < 0L) {
	        num -= (num % 0x5265c00L) * 2L;
	    }
	    num += 0x3680b5e1fc00L;
	    num -=  62135596800000L;

	    return new Date(num);
	}

}
