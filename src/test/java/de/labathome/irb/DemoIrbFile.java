package de.labathome.irb;

import aliceinnets.python.jyplot.JyPlot;

public class DemoIrbFile {

	public static void main(String[] args) throws Exception {
		run();
	}

	public static void run() throws Exception {
//		String filename = "/data/jonathan/Projekte/Programmierung/irbis/perma_0005.irb";
		String filename = "/home/jons/00_privat/irb_debugging/perma_0005.irb";

		IrbFile irbFile = IrbFile.fromFile(filename);

//		int imageIndex = 0;
//		IrbImage image = irbFile.images.get(imageIndex);

//		JyPlot plt = new JyPlot();
//
//        plt.figure();
//        plt.imshow(image.getCelsiusImage(), "cmap=plt.get_cmap('jet')");
//        // plt.imshow(image.getCelsiusImage(), "cmap=plt.get_cmap('gist_ncar')");
//        // plt.imshow(image.getCelsiusImage(), "cmap=plt.get_cmap('nipy_spectral')");
//        plt.colorbar();
//        plt.title(String.format("image %d", imageIndex));
//
//        plt.show();
//        plt.exec();
	}
}
