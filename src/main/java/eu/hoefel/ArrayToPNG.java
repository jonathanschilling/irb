package eu.hoefel;

import java.awt.image.BufferedImage;
import java.io.FileOutputStream;

import javax.imageio.ImageIO;

import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

/**
 * Convert a 2d array into a PNG image.
 * @author Udo Hoefel
 */
public class ArrayToPNG {

	/**
	 * Dumps the array as a png file'
	 *
	 * @param a The array
	 * @param filename The name of the image file.
	 */
	public static void dumpAsPng(float[][] a, String filename) {
		dumpAsPng(a, null, null, filename, false);
	}

	/**
	 * Dumps the array as a png file, using a specified (optional) colormap and (optional) transparency alpha.
	 * The corresponding colors to the values in the array will be linearly interpolated
	 * according to the colormap. If the alpha array is given, the corresponding alpha value for the pixel i,j will
	 * be set to alpha[i][j], where alpha must be [0,1].
	 *
	 * @param a The array
	 * @param colormap A 3xm array, of [0,1] values. colormap[0][i] is red, colormap[1][i] green, colormap[2][i] blue.
	 * @param alpha An array with the same dimensions as a containing transparency values. If null transparency will be 0.
	 */
	public static void dumpAsPng(float[][] a, double[][] colormap, double[][] alpha, String filename, boolean  interpolatedColors) {
		BufferedImage image = array2Image(a, colormap, alpha, interpolatedColors);

		try(FileOutputStream file = new FileOutputStream(filename)) {
			ImageIO.write(image, "png", file);
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * Creates an image from an array using a specified (optional) colormap and (optional) transparency alpha.
	 * The corresponding colors to the values in the array will be linearly interpolated
	 * according to the colormap. If the alpha array is given, the corresponding alpha value for the pixel i,j will
	 * be set to alpha[i][j], where alpha must be [0,1].
	 *
	 * @param a The array
	 * @param colormap [3][m] values in the range [0,1]. colormap[0][i] is red, colormap[1][i] green, colormap[2][i] blue. Can be null (default; jet-like).
	 * @param alpha An array with the same dimensions as a containing transparency values. If null transparency will be 0.
	 * @param interpolatedColors true: interpolate colormap linearly; false: find nearest color; NaN is mapped to first entry in colormap
	 * @return A BufferedImage object that can be later saved with for example ImageIO.write(image, "PNG", new File("my.png"));
	 */
	public static BufferedImage array2Image(float[][] a, double[][] colormap, double[][] alpha, boolean interpolatedColors) {
		double maxval = a[0][0];
		double minval = a[0][0];
		for (int i=0; i<a.length; ++i) {
			for (int j=0; j<a[i].length; ++i) {
				if (a[i][j] > maxval) { maxval = a[i][j]; }
				if (a[i][j] < maxval) { minval = a[i][j]; }
			}
		}

		if (colormap == null) colormap = COLORMAP1;

		final double[] rescaledRangeR = new double[colormap[0].length];
		final double[] rescaledRangeG = new double[colormap[1].length];
		final double[] rescaledRangeB = new double[colormap[2].length];

		double deltaValueR = (maxval-minval)/(colormap[0].length - 1.0);
		double deltaValueG = (maxval-minval)/(colormap[1].length - 1.0);
		double deltaValueB = (maxval-minval)/(colormap[2].length - 1.0);

		for (int i=0; i<colormap[0].length; ++i) {
			rescaledRangeR[i] = minval + i*deltaValueR;
		}
		for (int i=0; i<colormap[1].length; ++i) {
			rescaledRangeG[i] = minval + i*deltaValueG;
		}
		for (int i=0; i<colormap[2].length; ++i) {
			rescaledRangeB[i] = minval + i*deltaValueB;
		}

		LinearInterpolator linearInterpolator = new LinearInterpolator();
        PolynomialSplineFunction rmap = linearInterpolator.interpolate(rescaledRangeR, colormap[0]);
        PolynomialSplineFunction gmap = linearInterpolator.interpolate(rescaledRangeR, colormap[1]);
        PolynomialSplineFunction bmap = linearInterpolator.interpolate(rescaledRangeR, colormap[2]);

		BufferedImage image = new BufferedImage(a[0].length, a.length, BufferedImage.TYPE_INT_ARGB/*BufferedImage.TYPE_INT_RGB*/);

		int MAXINT = 255;
		for(int i=0;i<a.length;++i) {
			for(int j=0;j<a[0].length;++j) {
				boolean isNaN = Double.isNaN(a[i][j]);
				double val = isNaN ? 0 : a[i][j];
				int r=0, g=0,b=0;
				if (interpolatedColors) {
					r = (int) (rmap.value(val)*MAXINT);
					g = (int) (gmap.value(val)*MAXINT);
					b = (int) (bmap.value(val)*MAXINT);
				} else {
					int indexR = 0;
					int indexG = 0;
					int indexB = 0;
					if (isNaN) {
						indexR = 0;
						indexG = 0;
						indexB = 0;
					} else {
						indexR = (int) Math.round(Math.abs((val-minval)/(maxval-minval)*(colormap[0].length-1)));
						indexG = (int) Math.round(Math.abs((val-minval)/(maxval-minval)*(colormap[1].length-1)));
						indexB = (int) Math.round(Math.abs((val-minval)/(maxval-minval)*(colormap[2].length-1)));
					}
					r = (int) (colormap[0][indexR]*MAXINT);
					g = (int) (colormap[1][indexG]*MAXINT);
					b = (int) (colormap[2][indexB]*MAXINT);
				}
				int al = (int) ((alpha == null ? MAXINT : alpha[i][j]*MAXINT));
				int color = (al << 24) | (r << 16) | (g << 8) | b;
				//int color = (r << 16) | (g << 8) | b;
				image.setRGB(j, i, color);
			}
		}

		return image;
	}

	/**
	 * Default jet-like colormap for use with array2Image and dumpAsPng.
	 * size: [R,G,B][nPoints]
	 */
	public static double[][] COLORMAP1 = {
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0625, 0.125, 0.1875, 0.25,
					0.3125, 0.375, 0.4375, 0.5, 0.5625, 0.625, 0.6875, 0.75, 0.8125, 0.875, 0.9375, 1, 1, 1, 1, 1, 1, 1,
					1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0.9375, 0.875, 0.8125, 0.75, 0.6875, 0.625, 0.5625, 0.5 },
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0.0625, 0.125, 0.1875, 0.25, 0.3125, 0.375, 0.4375, 0.5, 0.5625, 0.625, 0.6875,
					0.75, 0.8125, 0.875, 0.9375, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0.9375, 0.875,
					0.8125, 0.75, 0.6875, 0.625, 0.5625, 0.5, 0.4375, 0.375, 0.3125, 0.25, 0.1875, 0.125, 0.0625, 0, 0,
					0, 0, 0, 0, 0, 0, 0 },
			{ 0.5625, 0.625, 0.6875, 0.75, 0.8125, 0.875, 0.9375, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
					0.9375, 0.875, 0.8125, 0.75, 0.6875, 0.625, 0.5625, 0.5, 0.4375, 0.375, 0.3125, 0.25, 0.1875, 0.125,
					0.0625, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 } };


}
