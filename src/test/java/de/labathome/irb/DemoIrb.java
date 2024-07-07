package de.labathome.irb;

import java.io.File;
import java.io.IOException;

import aliceinnets.python.jyplot.JyPlot;

public class DemoIrb {
	public static void main(String[] args) throws IOException {

		final String folder = new File(DemoIrb.class.getClassLoader().getResource("de/labathome/irb").getFile()).getAbsolutePath();

		// uncompressed
//		final String filename = folder + "/140114AA/AA011400.irb";
//		final String filename = folder + "/140114AA/AA011401.irb";

//		final String filename = folder + "/140115AA/AA011500.irb";
//		final String filename = folder + "/140115AA/AA011501.irb";
//		final String filename = folder + "/140115AA/AA011502.irb";
//		final String filename = folder + "/140115AA/AA011503.irb";

//		final String filename = folder + "/140202AA/AA020200.irb";

//		final String filename = folder + "/140203AA/AA020300.irb";
//		final String filename = folder + "/140203AA/AA020301.irb";
//		final String filename = folder + "/140203AA/AA020302.irb";
//		final String filename = folder + "/140203AA/AA020303.irb";
//		final String filename = folder + "/140203AA/AA020304.irb";

//		final String filename = folder + "/140203AB/AB020300.irb";
//		final String filename = folder + "/140203AB/AB020301.irb";
		final String filename = folder + "/140203AB/AB020302.irb";

		IrbFile irbFile = IrbFile.fromFile(filename);

		final int imageIndex = 0;
		IrbImage image = irbFile.images.get(imageIndex);

		JyPlot plt = new JyPlot();

        plt.figure();
        plt.imshow(image.getCelsiusImage(), "cmap=plt.get_cmap('jet')");
        // plt.imshow(image.getCelsiusImage(), "cmap=plt.get_cmap('gist_ncar')");
        // plt.imshow(image.getCelsiusImage(), "cmap=plt.get_cmap('nipy_spectral')");
        plt.colorbar();
        plt.title(String.format("image %d", imageIndex));

        plt.show();
        plt.exec();
	}
}
