package de.labathome.irb;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestIrb {

	@Test
	void testIrbFileHeader() throws IOException {
		final String folder = new File(DemoIrb.class.getClassLoader().getResource("de/labathome/irb").getFile()).getAbsolutePath() + "/";
		final String filename = folder + "140114AA/AA011400.irb";

		try (RandomAccessFile memoryFile = new RandomAccessFile(filename, "r")) {
			MappedByteBuffer buf = memoryFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, memoryFile.length());
			final int initialPosition = buf.position();
			Assertions.assertEquals(0, initialPosition);

			// NOTE: in irbis-file-format, the routines are named readIntBE,
			// although they actually read little endian!
			buf.order(ByteOrder.LITTLE_ENDIAN);

			IrbFileHeader header = new IrbFileHeader(buf);
			Assertions.assertEquals(IrbFileType.VARIOCAM, header.fileType);
			Assertions.assertEquals(64, header.blockOffset);
			Assertions.assertEquals(10, header.blockCount);
			// IrbFileHeader has a size of 64 bytes
			Assertions.assertEquals(64, buf.position() - initialPosition);

			IrbHeaderBlock[] headerBlocks = new IrbHeaderBlock[header.blockCount];

			for (int headerBlockIdx = 0; headerBlockIdx < header.blockCount; ++headerBlockIdx) {
				final int posBeforeHeaderBlock = buf.position();
				IrbHeaderBlock headerBlock = new IrbHeaderBlock(buf);
				Assertions.assertEquals(32, buf.position() - posBeforeHeaderBlock);

				// store for later
				headerBlocks[headerBlockIdx] = headerBlock;

				if (headerBlockIdx == 0) {
					Assertions.assertEquals(IrbBlockType.IMAGE, headerBlock.blockType);
					Assertions.assertEquals(5216, headerBlock.offset);
					Assertions.assertEquals(616128, headerBlock.size);
				} else if (headerBlockIdx == 1) {
					Assertions.assertEquals(IrbBlockType.PREVIEW, headerBlock.blockType);
					Assertions.assertEquals(384, headerBlock.offset);
					Assertions.assertEquals(4832, headerBlock.size);
				} else if (headerBlockIdx == 2) {
					Assertions.assertEquals(IrbBlockType.TEXT_INFO, headerBlock.blockType);
					Assertions.assertEquals(621344, headerBlock.offset);
					Assertions.assertEquals(22, headerBlock.size);
				} else {
					Assertions.assertEquals(IrbBlockType.EMPTY, headerBlock.blockType);
					Assertions.assertEquals(0, headerBlock.offset);
					Assertions.assertEquals(0, headerBlock.size);
				}
			}

			IrbImage image = new IrbImage(buf, headerBlocks[0].offset, headerBlocks[0].size);

			// image header
			Assertions.assertEquals(2, image.bytesPerPixel);
			Assertions.assertEquals(0, image.compressed);
			Assertions.assertEquals(640, image.width);
			Assertions.assertEquals(480, image.height);
			Assertions.assertEquals(1.0F, image.emissivity);
			Assertions.assertEquals(0.80496436F, image.distance);
			Assertions.assertEquals(298.15F, image.environmentalTemp);
			Assertions.assertEquals(298.15F, image.pathTemperature);
			Assertions.assertEquals(9.5F, image.centerWavelength);

			// image metadata
			Assertions.assertEquals(233.0F, image.calibRangeMin);
			Assertions.assertEquals(393.0F, image.calibRangeMax);
			Assertions.assertEquals("VARIOCAM", image.device);
			Assertions.assertEquals("", image.deviceSerial);
			Assertions.assertEquals("VarioCAM II standard optics", image.optics);
			Assertions.assertEquals("IR 1.0/30 LW", image.opticsResolution);
			Assertions.assertEquals("4001_275213", image.opticsSerial);
			Assertions.assertEquals(323.15F, image.shotRangeStartErr);
			Assertions.assertEquals(80.0F, image.shotRangeSize);
			Assertions.assertEquals("Tue Jan 14 18:33:45 CET 2014", image.timestamp.toString());
			Assertions.assertEquals(0, image.timestampMillisecond);
			Assertions.assertEquals("JENOPTIK Laser, Optik, Systeme GmbH : VC HiRes", image.opticsText);

			// actual image data
			Assertions.assertEquals(480, image.data.length);
			Assertions.assertEquals(640, image.data[0].length);
			Assertions.assertEquals(296.5852F, image.minData);
			Assertions.assertEquals(311.3182F, image.maxData);

		}
	}

}
