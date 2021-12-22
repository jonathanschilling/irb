package de.labathome;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * A reading class for the *.irb file format by InfraTec,
 * inspired by https://github.com/tomsoftware/Irbis-File-Format .
 *
 * This is a pure hobby project. No copyright infringements or similar is intended.
 * Please inform the author about possible legal issues before turning to a lawyer.
 *
 * @author Jonathan Schilling (jonathan.schilling@mail.de)
 */
public class IrbFile {

	IrbHeader header;

	public static IrbFile fromFile(String filename) throws IOException {
		if (! (new File(filename).exists())) {
			throw new RuntimeException("File '"+filename+"' does not exists!");
		}

		RandomAccessFile memoryFile = new RandomAccessFile(filename, "r");
		MappedByteBuffer buf = memoryFile.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, memoryFile.length());

		IrbFile file = new IrbFile(buf);

		memoryFile.close();

		return file;
	}

	public IrbFile(ByteBuffer buf) {

		// parse header and header blocks
		header = new IrbHeader(buf);




	}

	public static void main(String[] args) {
		if (args != null && args.length>0) {

			String filename = args[0];
			try {
				IrbFile irbFile = IrbFile.fromFile(filename);








			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
