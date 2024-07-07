package de.labathome.irb;

import java.io.File;
import java.io.IOException;

import aliceinnets.python.jyplot.JyPlot;

public class DemoIrb {
	public static void main(String[] args) throws IOException {

		final String folder = new File(DemoIrb.class.getClassLoader().getResource("de/labathome/irb").getFile()).getAbsolutePath();
		final String filename = folder + "/140203AB/AB020300.irb";

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
