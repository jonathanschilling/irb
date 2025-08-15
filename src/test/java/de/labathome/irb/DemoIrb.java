/* irb
 * DemoIrb Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.irb;

import java.io.File;
import java.io.IOException;

import aliceinnets.python.jyplot.JyPlot;

public class DemoIrb {
	public static void main(String[] args) throws IOException {

		final String folder;
		if (args.length > 0) {
			folder = args[0];
		} else {
			folder = new File(DemoIrb.class.getClassLoader().getResource("de/labathome/irb").getFile()).getAbsolutePath() + "/";
		}

		// video file
//		final String filename = folder + "perma_0005.irb";

		// snapshots
//		final String filename = folder + "140114AA/AA011400.irb";
//		final String filename = folder + "140114AA/AA011401.irb";

//		final String filename = folder + "140115AA/AA011500.irb";
//		final String filename = folder + "140115AA/AA011501.irb";
//		final String filename = folder + "140115AA/AA011502.irb";
//		final String filename = folder + "140115AA/AA011503.irb";

//		final String filename = folder + "140202AA/AA020200.irb";

//		final String filename = folder + "140203AA/AA020300.irb";
//		final String filename = folder + "140203AA/AA020301.irb";
//		final String filename = folder + "140203AA/AA020302.irb";
//		final String filename = folder + "140203AA/AA020303.irb";
//		final String filename = folder + "140203AA/AA020304.irb";

		final String filename = folder + "140203AB/AB020300.irb";
//		final String filename = folder + "140203AB/AB020301.irb";
//		final String filename = folder + "140203AB/AB020302.irb";

		// InfraTec samples coming with IRBIS 3
//		final String filename = folder + "Building inspection/Building01.irb";
//		final String filename = folder + "Building inspection/Inner Wall01.irb"; // ERROR !!!
//		final String filename = folder + "Building inspection/Inner Wall02.irb"; // ERROR !!!
//		final String filename = folder + "Building inspection/Old Building01.irb"; // ERROR !!!

//		final String filename = folder + "Inspection/Elt 01.irb"; // ERROR !!!
//		final String filename = folder + "Inspection/Elt 02.irb"; // ERROR !!!
//		final String filename = folder + "Inspection/Elt 03.irb"; // ERROR !!!

//		final String filename = folder + "Inspection/Equipment01.irb";
//		final String filename = folder + "Inspection/Equipment02.irb"; // ERROR !!!
//		final String filename = folder + "Inspection/Equipment03.irb"; // ERROR !!!
//		final String filename = folder + "Inspection/Equipment04.irb"; // ERROR !!!
//		final String filename = folder + "Inspection/Motor01.irb";
//		final String filename = folder + "Inspection/Motor02.irb"; // ERROR !!!
//		final String filename = folder + "Inspection/Motor03.irb"; // ERROR !!!
//		final String filename = folder + "Inspection/Pump01.irb";

//		final String filename = folder + "Pixel-wise correction/Direct1/Reference_50.irb"; // ERROR !!!
//		final String filename = folder + "Pixel-wise correction/Direct1/Correction.irb"; // ERROR !!!

//		final String filename = folder + "Resolution/160x120.IRB";
//		final String filename = folder + "Resolution/320x240.IRB"; // ERROR !!!
//		final String filename = folder + "Resolution/384x288.IRB"; // ERROR !!!
//		final String filename = folder + "Resolution/640x480.IRB"; // ERROR !!!
//		final String filename = folder + "Resolution/1280x960.IRB"; // ERROR !!!

//		final String filename = folder + "Sequences/Packet file/Sequencefile.irb";

//		final String filename = folder + "Sequences/Single file/lp01.irb"; // ERROR !!!
//		final String filename = folder + "Sequences/Single file/lp02.irb"; // ERROR !!!
//		final String filename = folder + "Sequences/Single file/lp03.irb"; // ERROR !!!
//		final String filename = folder + "Sequences/Single file/lp04.irb"; // ERROR !!!
//		final String filename = folder + "Sequences/Single file/lp05.irb"; // ERROR !!!
//		final String filename = folder + "Sequences/Single file/lp06.irb"; // ERROR !!!
//		final String filename = folder + "Sequences/Single file/lp07.irb"; // ERROR !!!
//		final String filename = folder + "Sequences/Single file/lp08.irb"; // ERROR !!!
//		final String filename = folder + "Sequences/Single file/lp09.irb"; // ERROR !!!
//		final String filename = folder + "Sequences/Single file/lp10.irb"; // ERROR !!!
//		final String filename = folder + "Sequences/Single file/lp11.irb"; // ERROR !!!
//		final String filename = folder + "Sequences/Single file/lp12.irb"; // ERROR !!!
//		final String filename = folder + "Sequences/Single file/lp13.irb"; // ERROR !!!
//		final String filename = folder + "Sequences/Single file/lp14.irb"; // ERROR !!!
//		final String filename = folder + "Sequences/Single file/lp15.irb"; // ERROR !!!

		// Provided by David Jessop
//		final String filename = folder + "AD071802.irb"; // single image; has non-empty EMPTY block
//		final String filename = folder + "AC032701.irb"; // video?
//		final String filename = folder + "AA022000.irb"; // single shot, old format, includes mystery block


		IrbFile irbFile = IrbFile.fromFile(filename);

		for (IrbTextInfo textInfo: irbFile.textInfos) {
			System.out.println("### TEXT_INFO start ###");
			System.out.println(textInfo.textInfo);
			System.out.println("### TEXT_INFO end ###");
		}

		JyPlot plt = new JyPlot();

		int imageIndex = 0;
		for (IrbImage image: irbFile.images) {
	        plt.figure();
	        plt.imshow(image.getCelsiusImage(), "cmap='jet'");
//	        plt.imshow(image.getCelsiusImage(), "cmap='gist_ncar'");
//	        plt.imshow(image.getCelsiusImage(), "cmap='nipy_spectral'");
	        plt.colorbar();
	        plt.title(String.format("image %d", imageIndex));

	        imageIndex++;
		}

		if (irbFile.frames != null) {
			// is video file
			for (int frameIdx = 0; frameIdx < 10; ++frameIdx) {
				IrbFile frame = irbFile.frames.get(frameIdx);

				int imageInFrame = 0;
				for (IrbImage image: frame.images) {
			        plt.figure();
			        plt.imshow(image.getCelsiusImage(), "cmap='jet'");
//			        plt.imshow(image.getCelsiusImage(), "cmap='gist_ncar'");
//			        plt.imshow(image.getCelsiusImage(), "cmap='nipy_spectral'");
			        plt.colorbar();
			        plt.title(String.format("frame %d, image %d", frameIdx, imageInFrame));

			        imageInFrame++;
				}
			}
		}

        plt.show();
        plt.exec();
	}
}
