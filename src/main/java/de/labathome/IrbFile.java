/* irb
 * IrbFile Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import aliceinnets.python.jyplot.JyPlot;
import eu.hoefel.ArrayToPNG;

/**
 * A reading class for the *.irb file format by InfraTec, inspired by
 * https://github.com/tomsoftware/Irbis-File-Format .
 *
 * This is a pure hobby project. No copyright infringements or similar is
 * intended. Please inform the author about possible legal issues before turning
 * to a lawyer.
 *
 * @author Jonathan Schilling (jonathan.schilling@mail.de)
 * @author Benjamin Schilling (benjamin.schilling33@gmail.com)
 */
public class IrbFile {

	/** \ff I R B \0 */
	private static final byte[] MAGIC_ID = { (byte) 0xff, (byte) 0x49, (byte) 0x52, (byte) 0x42, (byte) 0x0 };

	private IrbFileType fileType;

	@SuppressWarnings("unused")
	private int flag1;

	private int blockCount;
	private int blockOffset;

	public List<IrbHeaderBlock> headerBlocks;
	public List<IrbImage> images;

	public IrbFile(ByteBuffer buf) {
		parseHeader(buf);
	}

	private void parseHeader(ByteBuffer buf) {
		// parse magic number ID
		final byte[] magicBytes = new byte[5];
		buf.get(magicBytes);
		if (!Arrays.equals(magicBytes, MAGIC_ID)) {
			throw new RuntimeException("first 5 magic bytes invalid");
		}

		// read file type
		final byte[] fileTypeBytes = new byte[8];
		buf.get(fileTypeBytes);
		fileType = IrbFileType.fromString(new String(fileTypeBytes));

		// second file type identifier; gets ignored
		buf.get(fileTypeBytes);

		// NOTE: in irbis-file-format, the routines are named readIntBE,
		// although they actually read little endian!
		buf.order(ByteOrder.LITTLE_ENDIAN);
		flag1 = buf.getInt();
		blockOffset = buf.getInt();
		blockCount = buf.getInt();

		// read header blocks
		headerBlocks = new LinkedList<>();
		buf.position(blockOffset);
		for (int i = 0; i < blockCount; ++i) {
			IrbHeaderBlock headerBlock = new IrbHeaderBlock(buf);
			headerBlocks.add(headerBlock);
		}

		// read actual image data
		images = new LinkedList<>();
		for (IrbHeaderBlock block : headerBlocks) {
			if (block.blockType == IrbBlockType.IMAGE) {
				IrbImage image = new IrbImage(buf, block.offset, block.size);
				images.add(image);
			}
		}
	}

	public IrbFileType fileType() {
		return fileType;
	}

	public static IrbFile fromFile(String filename) throws IOException {
		if (!(new File(filename).exists())) {
			throw new RuntimeException("File '" + filename + "' does not exists!");
		}

		RandomAccessFile memoryFile = new RandomAccessFile(filename, "r");
		MappedByteBuffer buf = memoryFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, memoryFile.length());

		IrbFile file = new IrbFile(buf);

		memoryFile.close();

		return file;
	}

	public static void main(String[] args) {
		if (args != null && args.length > 0) {

			String filename = args[0];
			boolean runHeadless = false;
			if(args.length > 1){
				String headless = args[1];
				if(headless.equals("--headless"))
				{
					runHeadless = true;
				}
			}
			
			try {
				System.out.println("Processing file: " + filename);
				IrbFile irbFile = IrbFile.fromFile(filename);

				System.out.println("number of images: "+irbFile.images.size());

				int imageIndex=0;
				for (IrbImage image: irbFile.images) {

					System.out.println("\n\nimage " + imageIndex);
					System.out.printf("            env temp: %g °C\n", image.environmentalTemp - IrbImage.CELSIUS_OFFSET);
					System.out.printf("           path temp: %g °C\n", image.pathTemperature - IrbImage.CELSIUS_OFFSET);
					System.out.printf("     calib range min: %g °C\n", image.calibRangeMin - IrbImage.CELSIUS_OFFSET);
					System.out.printf("     calib range max: %g °C\n", image.calibRangeMax - IrbImage.CELSIUS_OFFSET);
					System.out.printf("shot range start err: %g °C\n", image.shotRangeStartErr - IrbImage.CELSIUS_OFFSET);
					System.out.printf("     shot range size: %g  K\n", image.shotRangeSize);

					// try to export data
					try {
						System.out.print("starting to export to text files... ");
						image.exportImageData(String.format(filename+".img_%d.dat", imageIndex));
						image.exportMetaData(String.format(filename+".meta_%d.json", imageIndex));
						System.out.println("done");
					} catch (Exception e) {
						e.printStackTrace();
					}

					try {
						System.out.print("starting to dump image as PNG... ");
						ArrayToPNG.dumpAsPng(image.getCelsiusImage(), String.format(filename+".img_%d.png", imageIndex));
						System.out.println("done");
					} catch (Exception e) {
						e.printStackTrace();
					}
					if(!runHeadless){
						// try to plot using JyPlot
						try {
							System.out.print("plot using JyPlot... ");
							JyPlot plt = new JyPlot();

							plt.figure();
							plt.imshow(image.getCelsiusImage(), "cmap=plt.get_cmap('jet')");
		//					plt.imshow(image.getCelsiusImage(), "cmap=plt.get_cmap('gist_ncar')");
		//					plt.imshow(image.getCelsiusImage(), "cmap=plt.get_cmap('nipy_spectral')");
							plt.colorbar();
							plt.title(String.format("image %d", imageIndex));

							plt.show();
							plt.exec();							
							
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					System.out.println("done");
					imageIndex++;
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			System.out.println("usage: java -jar irb.jar /path/to/image.irb (options)");
			System.out.println("options:");
			System.out.println("	--headless: save the image to disk instead of displaying it. ");
		}
	}

}
