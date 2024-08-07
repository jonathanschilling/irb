/* irb
 * IrbCli Class
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.cli;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

@Command(name = "irb", version = "irb 1.1.0", description = "Process *.irb files")
public class IrbCli implements Callable<Integer> {

	@Parameters(index = "0", description = "The *.irb file to read.")
	private String filename;

	@Option(names = {"--headless"}, description = "Skip GUI plot using JyPlot and just dump image data to disk.")
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

            	// parallelize over frames to speed up export
            	final int numThreads = Runtime.getRuntime().availableProcessors();
            	ExecutorService service = Executors.newFixedThreadPool(numThreads);

            	for (int frameIdx = 0; frameIdx < irbFile.frames.size(); ++frameIdx) {
    				IrbFile frame = irbFile.frames.get(frameIdx);
    				if (frame.images == null) {
    					System.out.println("skipping frame " + frameIdx + ", since no image was present");
    					continue;
    				}

    				for (int imageIdx = 0; imageIdx < frame.images.size(); ++imageIdx) {
    					final IrbImage image = frame.images.get(imageIdx);

    					final int finalFrameIdx = frameIdx;
    					final int finalImageIdx = imageIdx;

        				service.execute(() -> {
        					try {
	        					System.out.printf("exporting frame %4d/%4d...\n", finalFrameIdx+1, irbFile.frames.size());

	        					image.exportImageData(String.format(filename + ".img_%04d_%04d.dat", finalFrameIdx, finalImageIdx));
		                        image.exportMetaData(String.format(filename + ".meta_%04d_%04d.json", finalFrameIdx, finalImageIdx));
		                        ArrayToPNG.dumpAsPng(image.getCelsiusImage(), filename + String.format(".img_%04d_%04d.png", finalFrameIdx, finalImageIdx));

		                        if (!runHeadless) {
		                        	JyPlot plt = new JyPlot();

		                            plt.figure();
		                            plt.imshow(image.getCelsiusImage(), "cmap=plt.get_cmap('jet')");
		                            // plt.imshow(image.getCelsiusImage(), "cmap=plt.get_cmap('gist_ncar')");
		                            // plt.imshow(image.getCelsiusImage(), "cmap=plt.get_cmap('nipy_spectral')");
		                            plt.colorbar();
		                            plt.title(String.format("frame %d, image %d", finalFrameIdx, finalImageIdx));
		                            plt.savefig(filename + String.format(".plot_%04d_%04d.png", finalFrameIdx, finalImageIdx));

		                            plt.exec();
		                        }
        					} catch (Exception e) {
        						// no chance to see if something within threads goes wrong, if not explicitly caught here...
        						e.printStackTrace();
        					}
        				});
    				}
            	}

            	service.shutdown();
            	service.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
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
