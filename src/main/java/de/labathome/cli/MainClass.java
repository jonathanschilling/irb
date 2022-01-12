/* irb
 * CLI MainClass
 * SPDX-License-Identifier: Apache-2.0
 */

package de.labathome.cli;

import aliceinnets.python.jyplot.JyPlot;
import de.labathome.irb.IrbFile;
import de.labathome.irb.IrbImage;
import eu.hoefel.ArrayToPNG;

public class MainClass {
    public static void main(String[] args) {
        if (args != null && args.length > 0) {

            String filename = args[0];
            boolean runHeadless = false;
            if (args.length > 1) {
                String headless = args[1];
                if (headless.equals("--headless")) {
                    runHeadless = true;
                }
            }

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
        } else {
            System.out.println("usage: java -jar irb.jar /path/to/image.irb (options)");
            System.out.println("options:");
            System.out.println("	--headless: save the image to disk instead of displaying it. ");
        }
    }
}
