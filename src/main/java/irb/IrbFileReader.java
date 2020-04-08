package irb;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.logging.Logger;

/**
 * A reading class for the *.irb file format by InfraTec
 * inspired by https://github.com/tomsoftware/Irbis-File-Format.
 * 
 * This is a pure hobby project. No copyright infringements or similar is intended.
 * 
 * @author Jonathan Schilling (jonathan.schilling@mail.de)
 */
public class IrbFileReader implements AutoCloseable {
	
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
			
			System.out.print("magic: '");
			for (byte b: magic) {
				System.out.printf("%s", (char)b);
			}
			System.out.printf("'\n");
			
			
			
			
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
