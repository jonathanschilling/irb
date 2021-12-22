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

/**
 * A reading class for the *.irb file format by InfraTec, inspired by
 * https://github.com/tomsoftware/Irbis-File-Format .
 *
 * This is a pure hobby project. No copyright infringements or similar is
 * intended. Please inform the author about possible legal issues before turning
 * to a lawyer.
 *
 * @author Jonathan Schilling (jonathan.schilling@mail.de)
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
			try {
				IrbFile irbFile = IrbFile.fromFile(filename);

				System.out.println("number of images: "+irbFile.images.size());

				JyPlot plt = new JyPlot();

				int i=0;
				for (IrbImage image: irbFile.images) {

					plt.figure();
					plt.imshow(image.getCelsiusImage(), "cmap=plt.get_cmap('jet')");
					plt.colorbar();
					plt.title(String.format("image %d", i));

					System.out.println("\n\nimage " + i);
					System.out.println(" env temp: "+image.environmentalTemp);
					System.out.println("path temp: "+image.pathTemperature);

					i++;
				}

				plt.show();
				plt.exec();

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
