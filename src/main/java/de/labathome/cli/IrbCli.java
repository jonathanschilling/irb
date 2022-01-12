/* irb
 * CLI MainClass
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.cli;

// External includes
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

import aliceinnets.python.jyplot.JyPlot;

// Our packages
import de.labathome.irb.IrbFile;
import de.labathome.irb.IrbImage;

//3rd Party packages that are part of this repo
import eu.hoefel.ArrayToPNG;

@Command(name = "irb", version = "irb 1.0.2", description = "Process *.irb files")
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
                System.out.printf("            env temp: %g °C\n",
                        image.environmentalTemp - IrbImage.CELSIUS_OFFSET);
                System.out.printf("           path temp: %g °C\n", image.pathTemperature - IrbImage.CELSIUS_OFFSET);
                System.out.printf("     calib range min: %g °C\n", image.calibRangeMin - IrbImage.CELSIUS_OFFSET);
                System.out.printf("     calib range max: %g °C\n", image.calibRangeMax - IrbImage.CELSIUS_OFFSET);
                System.out.printf("shot range start err: %g °C\n",
                        image.shotRangeStartErr - IrbImage.CELSIUS_OFFSET);
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
