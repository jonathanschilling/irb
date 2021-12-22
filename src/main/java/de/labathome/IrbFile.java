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

	private String magic;
	private IrbFileType fileType;
	private String fileType2;
	private int flag1;

	private int firstBlockCount;

	/** starts at 0 */
	private int blockOffset;
	public List<IrbBlock> blocks;

	private int imageCount;

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
		magic = new String(magicBytes);

		// read file type
		final byte[] fileTypeBytes = new byte[8];
		buf.get(fileTypeBytes);
		fileType = IrbFileType.fromString(new String(fileTypeBytes));

		// second file type identifier; gets ignored
		buf.get(fileTypeBytes);
		fileType2 = new String(fileTypeBytes);

		// NOTE: in irbis-file-format, the routines are named readIntBE,
		// although they actually read little endian!
		buf.order(ByteOrder.LITTLE_ENDIAN);
		flag1 = buf.getInt();
		blockOffset = buf.getInt();
		firstBlockCount = buf.getInt();

		// read header blocks
		blocks = new LinkedList<>();
		buf.position(blockOffset);
		for (int i = 0; i < firstBlockCount; ++i) {
			IrbBlock headerBlock = new IrbBlock(buf);
			blocks.add(headerBlock);
		}

		// read actual image data
		for (IrbBlock block : blocks) {
			if (block.blockType == IrbBlockType.IMAGE) {
				block.readImage(buf);
				imageCount++;
			}
		}
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

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
