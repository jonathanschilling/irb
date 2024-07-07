/* irb
 * IrbFile Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.irb;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.System.Logger;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
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
 * @author Benjamin Schilling (benjamin.schilling33@gmail.com)
 */
public class IrbFile {

	protected final Logger logger = System.getLogger(IrbFile.class.getName());

	public IrbFileHeader header;
	public List<IrbHeaderBlock> headerBlocks;
	public List<IrbImage> images;

	public IrbFile(ByteBuffer buf) {
		header = IrbFileHeader.parse(buf);

		// read header blocks
		headerBlocks = IrbFileHeader.readHeaderBlocks(buf, header.blockCount());

		// read actual image data
		images = new LinkedList<>();
		for (IrbHeaderBlock block : headerBlocks) {
			if (block.blockType != IrbBlockType.EMPTY) {
				System.out.println("reading contents of block of type "
						+ block.blockType + " at offset " + block.offset + " of size " + block.size);

				if (block.blockType == IrbBlockType.IMAGE) {
					IrbImage image = IrbImage.readImage(buf);
					images.add(image);
				} else {
					// just read past the whatever block...
					byte[] dummy = new byte[block.size];
					buf.get(dummy);

					System.out.printf("read dummy %d bytes; now at position %d\n", block.size, buf.position());
				}
			} else {
				System.out.println("ignore EMPTY header block");
			}
		}

		if (header.fileType() == IrbFileType.O_SAVE_IRB) {
			// expect a single "front matter"/"preview"/"thumbnail" image to be present,
			// without it being mentioned/announced with a corresponding header block

			IrbImage image = IrbImage.readImage(buf);
			images.add(image);
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
}
