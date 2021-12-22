package de.labathome;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * A reading class for the *.irb file format by InfraTec,
 * inspired by https://github.com/tomsoftware/Irbis-File-Format .
 *
 * This is a pure hobby project. No copyright infringements or similar is intended.
 * Please inform the author about possible legal issues before turning to a lawyer.
 *
 * @author Jonathan Schilling (jonathan.schilling@mail.de)
 */
public class IrbFileReader implements AutoCloseable {

	/** \ff I R B \0 */
	private static final byte[] MAGIC_ID = {
			(byte) 0xff,
			(byte) 0x49,
			(byte) 0x52,
			(byte) 0x42,
			(byte) 0x0
	};

	public static enum FileType {
		/** single image; identified by "IRBACS\0\0" */
		IMAGE("IRBACS\0\0"),

		/** sequence of images; idenfitied by "IRBIS 3\0" */
		SEQUENCE("IRBIS 3\0"),

		/** specific camera model; identified by "VARIOCAM" */
		VARIOCAM("VARIOCAM");

		private FileType(String content) {
			this.content = content;
		}

		private String content;
		public String getContent() { return content; }
	}

	private static Logger logger;
	static {
		logger = Logger.getLogger(IrbFileReader.class.getName());
	}

	public IrbFileReader(String filename) {
		logger.info("reading '"+filename+"'");

		if (! (new File(filename).exists())) {
			throw new RuntimeException("File '"+filename+"' does not exists!");
		}

		try (RandomAccessFile memoryFile = new RandomAccessFile(filename, "r")) {
			MappedByteBuffer buf = memoryFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, memoryFile.length());

			final byte[] magic = new byte[5];
			buf.get(magic);

			// parse magic number ID
			if (!Arrays.equals(magic, MAGIC_ID)) {
				throw new RuntimeException("first 5 magic bytes invalid");
			}

			// read file type
			final byte[] fileType = new byte[8];
			buf.get(fileType);
			String fileTypeStr = new String(fileType);
			if (fileTypeStr.equals(FileType.IMAGE.getContent())) {
				logger.info("image");

			} else if (fileTypeStr.equals(FileType.SEQUENCE.getContent())) {
				logger.info("sequence");

			} else if (fileTypeStr.equals(FileType.VARIOCAM.getContent())) {
				logger.info("VARIOCAM");

			} else {
				throw new RuntimeException("file type bytes invalid");
			}







		} catch (Exception e) {
			e.printStackTrace();
		}


	}

	public void close() throws Exception {
		logger.info("close");
	}



	public static void main(String[] args) {
		if (args != null && args.length>0) {

			String filename = args[0];
			try (IrbFileReader reader = new IrbFileReader(filename)) {

				// do something nice

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
