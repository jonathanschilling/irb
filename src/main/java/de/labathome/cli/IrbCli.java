/* irb
 * IrbCli Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.cli;

import java.io.File;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import aliceinnets.python.jyplot.JyPlot;
// Our packages
import de.labathome.irb.IrbFile;
import de.labathome.irb.IrbImage;
//3rd Party packages that are part of this repo
import eu.hoefel.ArrayToPNG;
// External includes
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "irb", version = "irb 1.0.3", description = "Process *.irb files")
public class IrbCli implements Callable<Integer> {

	@Parameters(index = "0", description = "The *.irb file to read.")
	private String filename;

	@Option(names = {"-h", "--headless"}, description = "Save the image to disk instead of displaying it.")
	private boolean runHeadless;

	public Integer call() throws Exception {
		try {
            System.out.println("Processing file: " + filename);
            IrbFile irbFile = IrbFile.fromFile(filename);

            System.out.println("number of images: " + irbFile.images.size());

            int imageIndex = 0;
            for (IrbImage image : irbFile.images) {

                System.out.println("\n\nimage " + imageIndex);
                System.out.printf("            env temp: %g °C\n", image.environmentalTemp - IrbImage.CELSIUS_OFFSET);
                System.out.printf("           path temp: %g °C\n", image.pathTemperature   - IrbImage.CELSIUS_OFFSET);
                System.out.printf("     calib range min: %g °C\n", image.calibRangeMin     - IrbImage.CELSIUS_OFFSET);
                System.out.printf("     calib range max: %g °C\n", image.calibRangeMax     - IrbImage.CELSIUS_OFFSET);
                System.out.printf("shot range start err: %g °C\n", image.shotRangeStartErr - IrbImage.CELSIUS_OFFSET);
                System.out.printf("     shot range size: %g  K\n", image.shotRangeSize);

                // try to export data
                try {
                    System.out.print("starting to export to text files... ");
                    image.exportImageData(String.format(filename + ".img_%d.dat", imageIndex));
                    image.exportMetaData(String.format(filename + ".meta_%d.json", imageIndex));
                    System.out.println("done");
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    System.out.print("starting to dump image as PNG... ");
                    ArrayToPNG.dumpAsPng(image.getCelsiusImage(),
                            String.format(filename + ".img_%d.png", imageIndex));
                    System.out.println("done");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (!runHeadless) {
                    // try to plot using JyPlot
                    try {
                        System.out.print("plot using JyPlot... ");
                        JyPlot plt = new JyPlot();

                        plt.figure();
                        plt.imshow(image.getCelsiusImage(), "cmap=plt.get_cmap('jet')");
                        // plt.imshow(image.getCelsiusImage(), "cmap=plt.get_cmap('gist_ncar')");
                        // plt.imshow(image.getCelsiusImage(), "cmap=plt.get_cmap('nipy_spectral')");
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

            if (irbFile.frames != null) {
            	// have video frames -> dump them now

            	for (int frameIdx = 0; frameIdx < irbFile.frames.size(); ++frameIdx) {
    				IrbFile frame = irbFile.frames.get(frameIdx);
    				System.out.printf("exporting frame %4d/%4d...\n", frameIdx+1, irbFile.frames.size());
    				if (frame.images == null) {
    					System.out.println("  skipping frame, since no image was present");
    					continue;
    				}

    				for (int imageIdx = 0; imageIdx < frame.images.size(); ++imageIdx) {
    					IrbImage image = frame.images.get(imageIdx);


    					String frameFilename;
    					if (filename.toLowerCase().endsWith(".irb")) {
    						frameFilename = filename.substring(0, filename.length() - 4);
    					} else {
    						frameFilename = filename;
    					}
    					frameFilename += String.format("_%04d_%04d", frameIdx, imageIdx);

    					image.exportImageData(frameFilename + "_img.dat");
    					image.exportMetaData(frameFilename + "_meta.json");
                        ArrayToPNG.dumpAsPng(image.getCelsiusImage(), frameFilename + ".png");
    				}
            	}
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
		return 0;
	}

    public static void main(String[] args) {
    	int exitCode = new CommandLine(new IrbCli()).execute(args);
        System.exit(exitCode);
    }
}
