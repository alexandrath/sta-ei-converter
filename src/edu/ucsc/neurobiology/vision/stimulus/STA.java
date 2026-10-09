package edu.ucsc.neurobiology.vision.stimulus;

import java.io.*;
import java.util.*;

import java.awt.*;

import edu.ucsc.neurobiology.vision.math.*;
import edu.ucsc.neurobiology.vision.math.fitting.*;
import edu.ucsc.neurobiology.vision.plot.*;


/**
 * An implementation of the Spike Trigerred Average (STA). This class does not calculate
 * STAs, it only encapsulates one.
 *
 * @author Dumitru Petrusca, University of California, Santa Cruz
 */
public class STA implements Movie {

    private ArrayList<STAFrame> frames;
    private final int staDepth;
    private final int width;
    private final int height;
    private final double stixelWidth, stixelHeight;
    private final double refreshTime;
    private double[][] timeFilterError;


    public STA(int staDepth, int width, int height, double refreshTime,
               double pixelWidth, double pixelHeight) {

        this.staDepth = staDepth;
        this.width = width;
        this.height = height;
        this.refreshTime = refreshTime;
        this.stixelWidth = pixelWidth;
        this.stixelHeight = pixelHeight;
        this.frames = new ArrayList<STAFrame>(staDepth);
        for (int f = 0; f < staDepth; f++) {
            frames.add(new STAFrame(width, height, stixelWidth, stixelHeight));
        }
    }


    public STA(ArrayList<STAFrame> frames, double refreshTime) {
        this.staDepth = frames.size();
        this.width = ( (ImageFrame) frames.get(0)).getWidth();
        this.height = ( (ImageFrame) frames.get(0)).getHeight();
        this.refreshTime = refreshTime;
        this.stixelWidth = ( (ImageFrame) frames.get(0)).getStixelWidth();
        this.stixelHeight = ( (ImageFrame) frames.get(0)).getStixelHeight();
        this.frames = frames;
    }


    public STA(STAFrame[] f, double refreshTime) {
        this.staDepth = f.length;
        this.width = f[0].getWidth();
        this.height = f[0].getHeight();
        this.refreshTime = refreshTime;
        this.stixelWidth = f[0].getStixelWidth();
        this.stixelHeight = f[0].getStixelHeight();

        this.frames = new ArrayList<STAFrame>();
        for (int i = 0; i < f.length; i++) {
            frames.add(f[i]);
        }
    }


    public String getDescription() {
        return "STA";
    }


    public int getWidth() {
        return width;
    }


    public int getHeight() {
        return height;
    }


    public int size() {
        return staDepth;
    }


    public double getRefreshTime() {
        return this.refreshTime;
    }

    public int getSTADepth() {
        return this.staDepth;
    }


    public double getStixelWidth() {
        return this.stixelWidth;
    }
    
    public double getStixelHeight() {
        return this.stixelHeight;
    }


    public ImageFrame getFrame(int frameIndex) {
        return (ImageFrame) frames.get(frameIndex);
    }


    public STAFrame getSTAFrame(int frameIndex) {
        return (STAFrame) frames.get(frameIndex);
    }


    public void setFrame(int frameIndex, STAFrame frame) {
        frames.set(frameIndex, frame);
    }



    public int[][] getTopoMap(double sigmaPerColor) {
        float[] c = new float[3];
        int[][] topoMap = new int[width][height];

        ImageFrame f = getMainFrame();

        double extremeValue = Math.abs(f.getPixel(0, 0, c)[1]);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double value = Math.abs(f.getPixel(x, y, c)[1]);
                if (value > extremeValue) {
                    extremeValue = value;
                }
            }
        }

        double levelThickness = extremeValue -
                                (extremeValue *
                                 Math.pow(Math.E, -sigmaPerColor * sigmaPerColor / 2));

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double value = Math.abs(f.getPixel(x, y, c)[1]);
                if (value == extremeValue) {
                    topoMap[x][y] = 0;
                } else if (value >= (extremeValue - levelThickness)) {
                    topoMap[x][y] = 1;
                } else if (value >= (extremeValue - 2 * levelThickness)) {
                    topoMap[x][y] = 2;
                } else {
                    topoMap[x][y] = 3;

                }
            }
        }
        return topoMap;
    }


    public double[][] getTimeFilters(double significance) {
        ImageFrame mainFrame = getMainFrame();
        if (mainFrame == null) return null;

        double[][] timeFilter = new double[3][staDepth];
        timeFilterError       = new double[3][staDepth];
        float[] intensity = new float[3];
        float[] error     = new float[3];
        int nSignificantPixels = 0;

        boolean noSignificanceTest = false;
        if (width == 1 && height == 1) noSignificanceTest = true;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (noSignificanceTest || mainFrame.getPixelSignificance(x,y) >= significance) {
                    for (int fIndex = 0; fIndex < staDepth; fIndex++) {
                        ImageFrame f = getFrame(fIndex);
                        for (int cIndex = 0; cIndex < 3; cIndex++) {
                            f.getPixel(x, y, intensity);
                            f.getPixelError(x, y, error);
                            timeFilter[cIndex][fIndex]      += intensity[cIndex];
                            timeFilterError[cIndex][fIndex] += error[cIndex] * error[cIndex];
                        }
                    }
                    nSignificantPixels++;
                }
            }
        }
        if (nSignificantPixels == 0) return null;

        for (int fIndex = 0; fIndex < staDepth; fIndex++) {
            for (int cIndex = 0; cIndex < 3; cIndex++) {
                timeFilter[cIndex][fIndex] /= nSignificantPixels;
                timeFilterError[cIndex][fIndex] = Math.sqrt(timeFilterError[cIndex][fIndex]) / nSignificantPixels;
            }
        }
        
        return timeFilter;
    }


    // Uses this instance of STA to find the sig pixels to get the timecourses
    // for a different sta (or ste)
    public double[][] getComparisonTimeFilters(double significance, STA comparisonSTA) {
        int mainFrameIndex = getMainFrameIndex();
        if (mainFrameIndex == -1) {
            return null;
        }
        ImageFrame mainFrame = getFrame(mainFrameIndex);
        double[][] timeFilter = new double[3][staDepth];
        timeFilterError = new double[3][staDepth];
        float[] intensity = new float[3];
        float[] error = new float[3];
        int nSignificantPixels = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (mainFrame.getPixelSignificance(x, y) >= significance) {
                    for (int fIndex = 0; fIndex < staDepth; fIndex++) {
                        ImageFrame f = comparisonSTA.getFrame(fIndex);
                        for (int cIndex = 0; cIndex < 3; cIndex++) {
                            f.getPixel(x, y, intensity);
                            timeFilter[cIndex][fIndex] += intensity[cIndex];

                            //timeFilterError is probably incorrect
                            f.getPixelError(x, y, error);
                            timeFilterError[cIndex][fIndex] += error[cIndex] * error[cIndex];
                        }
                    }
                    nSignificantPixels++;
                }
            }
        }

        if (nSignificantPixels == 0) {
            return null;
        }

        for (int fIndex = 0; fIndex < staDepth; fIndex++) {
            for (int cIndex = 0; cIndex < 3; cIndex++) {
                timeFilter[cIndex][fIndex] /= nSignificantPixels;
                timeFilterError[cIndex][fIndex] = Math.sqrt(timeFilterError[cIndex][
                    fIndex]) / nSignificantPixels;
            }
        }

        return timeFilter;
    }


    public double[][] getTimeCourse(int x, int y) {
        double[][] timeFilter = new double[3][staDepth];
        float[] intensity = new float[3];

        for (int fIndex = 0; fIndex < staDepth; fIndex++) {
            ImageFrame f = getFrame(fIndex);
            f.getPixel(x, y, intensity);
            for (int cIndex = 0; cIndex < 3; cIndex++)
                timeFilter[cIndex][fIndex] += intensity[cIndex];
        }

        return timeFilter;
    }


    // getTimeFilters must be run first
    public double[][] getTimeFiltersError() {
        return timeFilterError;
    }


    public double[] getTimeFilters(
        double x0, double y0, double s1, double s2, int cIndex) {

        double[] timeFilter = new double[staDepth];
        float[] intensity = new float[3];

        for (int fIndex = 0; fIndex < staDepth; fIndex++) {
            ImageFrame f = getFrame(fIndex);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    f.getPixel(x, y, intensity);
                    double d = Math.sqrt( (x + 0.5 - x0) * (x + 0.5 - x0) +
                                         (y + 0.5 - y0) * (y + 0.5 - y0));
                    if (d >= s1 && d < s2) {
                        timeFilter[fIndex] += intensity[cIndex];
                    }
                }
            }
        }

        return timeFilter;
    }


    /**
     * Finds the frame and the pixel into that frame that has the brightest
     * color.
     *
     * @param f1 int The frame range in which to search for the brightest pixel
     * @param f2 int The frame range in which to search for the brightest pixel
     * @return int[] the params in this order: frame, x, y
     */
    public int[] getMainFrameParams(int f1, int f2) {
        Point p = new Point(0, 0);
        Point maxPoint = new Point(0, 0);
        float maxColor = Float.NEGATIVE_INFINITY, c;
        int maxFrame = -1;

        for (int f = f1; f < f2; f++) {
            ImageFrame frame = getFrame(f);
            c = frame.getMaxAbsColor(p);
            if (c > maxColor) {
                maxFrame = f;
                maxColor = c;
                maxPoint.setLocation(p);
            }
        }

        return new int[] {
            maxFrame, maxPoint.x, maxPoint.y};
    }


    public int[] getMainFrameParams() {
        return getMainFrameParams(0, staDepth);
    }


    public int getMainFrameIndex() {
        return getMainFrameParams()[0];
    }
    
    public ImageFrame getMainFrame() {
        int i = getMainFrameIndex();
        if (i == -1) return null;
        return this.getFrame(i);
    }

    
    public void write(DataOutput output) throws IOException {
        write(output, true, false, false);
    }


    public void write(DataOutput output, boolean includeErrors, boolean useInt16) throws IOException {
        write(output, includeErrors, useInt16, false);
    }


    public void write(DataOutput output, boolean includeErrors, boolean useInt16,
            boolean monochrome) throws IOException {
        if (includeErrors && useInt16) {
            throw new IllegalArgumentException("Int16 STAs cannot include per-pixel errors.");
        }
        if (monochrome && (!useInt16 || includeErrors)) {
            throw new IllegalArgumentException("Monochrome storage requires values-only int16.");
        }

        output.writeDouble(refreshTime);
        output.writeInt(staDepth);

        float scale = 0;
        if (useInt16) {
            scale = getInt16Scale();
            output.writeFloat(scale);
        }

        for (int i = 0; i < frames.size(); i++) {
            if (includeErrors) {
                getSTAFrame(i).write(output);
            } else {
                getSTAFrame(i).writeValuesOnly(output, useInt16, scale, monochrome);
            }
        }
    }


    public static STA read(DataInput input, double stixelWidth, double stixelHeight) throws IOException {
        return read(input, stixelWidth, stixelHeight, true, false, false);
    }


    public static STA read(DataInput input, double stixelWidth, double stixelHeight,
            boolean hasErrors, boolean useInt16) throws IOException {
        return read(input, stixelWidth, stixelHeight, hasErrors, useInt16, false);
    }


    public static STA read(DataInput input, double stixelWidth, double stixelHeight,
            boolean hasErrors, boolean useInt16, boolean monochrome) throws IOException {
        double refreshTime = input.readDouble();
        int staDepth = input.readInt();

        float scale = useInt16 ? input.readFloat() : 0;

        ArrayList<STAFrame> frames = new ArrayList<STAFrame>(staDepth);
        for (int i = 0; i < staDepth; i++) {
            if (hasErrors) {
                frames.add(STAFrame.read(input, stixelWidth, stixelHeight));
            } else {
                frames.add(STAFrame.readValuesOnly(
                        input, stixelWidth, stixelHeight, useInt16, scale, monochrome));
            }
        }

        return new STA(frames, refreshTime);
    }


    private float getInt16Scale() throws IOException {
        float max = 0;
        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            float[] values = getSTAFrame(frameIndex).getBuffer();
            for (int i = 0; i < values.length; i++) {
                float value = values[i];
                if (Float.isNaN(value) || Float.isInfinite(value)) {
                    throw new IOException("Cannot encode a non-finite STA value as int16.");
                }
                max = Math.max(max, Math.abs(value));
            }
        }
        return max == 0 ? 0 : max / 32767.0f;
    }


    public String toString() {
        return "STA w=" + width + ", h=" + height + ", depth=" + staDepth;
    }


    /**
     * makes a contour fit of a sta.
     * @param cv double Cut value.  Values below this are ignored
     * @param color int Color to fit.  Typically 1, for green.
     * @param doubleThreshold boolean  Rectify input?
     * @return Polygon2D
     */
    public Polygon2D getContour(double cv, int color, boolean doubleThreshold) {
        int[] par = getMainFrameParams();
        ImageFrame f = getFrame(par[0]);
        ImageFrame f0 = getFrame(0);
        double cut = 0.39;
        float[] c = new float[3];
        float[] er = new float[3];
        double[][] z = new double[width][height];
        double[][] zer = new double[width][height];
        double rms = 0;
        double sigm = 0;
        double average = 0;
        double average1 = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double value = f.getPixel(x, y, c)[color];
                if (doubleThreshold) {
                    value = Math.abs(value);
                }
                z[x][y] = value;
                zer[x][y] = f.getPixelError(x, y, c)[color];

                f0.getPixel(x, y, c);

                f0.getPixelError(x, y, er);
                rms = rms + c[color] * c[color];
                sigm = sigm + er[color];

            }
        }

        rms = Math.sqrt(rms / (width * height));
        sigm = sigm / (width * height);
        double errcor = rms / sigm;
        int maxpix_sign;

        if (doubleThreshold) {
            maxpix_sign = 1;
        } else {
            double maxpix_intens = f.getPixel(par[1], par[2], c)[color];
            maxpix_sign = (int) (maxpix_intens / Math.abs(maxpix_intens));
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
//
                z[x][y] = maxpix_sign * (z[x][y] / zer[x][y] / errcor);
//
                if (z[x][y] > cv) {
                    average = average + z[x][y];
                }
            }
        }
        average1 = 1.e+10;
        while (Math.abs(average1 / average) - cut > 0.01) {
            average1 = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (z[x][y] > cv) {
                        average1 = average1 + z[x][y];
                    }
                }
            }
            cv = cv + 0.05;
        }
        cv = cv - 0.05;

        Polygon2D polygon = new ContourPlot(2, 2).getOneSigmaContour(z, cv);

        for (int i = 0; i < polygon.xPoints.length; i++) {
            polygon.xPoints[i] = (polygon.xPoints[i] - 0.5);
            polygon.yPoints[i] = (height - polygon.yPoints[i] + 0.5);
        }

        return polygon;
    }


    /**
     *   looks only at green. Returns the reduced chi squared comparison of the two neurons.
     *   anything less than five is usually a duplicate
     */
    public double compareSTAs(STA sta, double significance) throws
        IOException {

        double chiSq = 0;
        int sigPixels = 0;
        int intMainFrameA = getMainFrameIndex();
        int intMainFrameB = sta.getMainFrameIndex();
        ImageFrame mainFrameA = getFrame(intMainFrameA);
        ImageFrame mainFrameB = sta.getFrame(intMainFrameB);
        double[] pixels = new double[width * height];
        ImageFrame firstFrame = getFrame(0);

        //find all the frames within one from the most significant frame
        boolean[] importantFrames = new boolean[staDepth];
        for (int i = 0; i < staDepth; i++) {
            if ( (i >= intMainFrameA - 1 && i <= intMainFrameA + 1) ||
                (i >= intMainFrameB - 1 && i < intMainFrameB + 1)) {
                if (i >= 0 && i < staDepth) {
                    importantFrames[i] = true;
                }
            }
        }
        //calculate sigma from first frame.  This is a good estimate of the error of each pixel
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                pixels[x * height + y] = firstFrame.getPixel(x, y, 1);
            }
        }
        double sigmaA = MathUtil.calculateStatistics(pixels)[1];

        firstFrame = sta.getFrame(0);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                pixels[x * height + y] = firstFrame.getPixel(x, y, 1);
            }
        }
        double sigmaB = MathUtil.calculateStatistics(pixels)[1];
        double totalSigma = sigmaA * sigmaA + sigmaB * sigmaB;

//        int nA =   ((IntegerTag) paramsFile.getCell(neuronA,
//                "nSpikes")).intValue();
//        int nB =   ((IntegerTag) paramsFile.getCell(neuronB,
//                "nSpikes")).intValue();

//error correction factor is sigma_measured / sigma_theory
//this corrects for the fact that the spikes are not poisson distributed
//        double eCFA = sigmaA * sigmaA * nA / .25;
//        double eCFB = sigmaB * sigmaB * nB / .25;

        for (int frame = 0; frame < staDepth; frame++) {
            if (importantFrames[frame]) {
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        if (mainFrameA.getPixelSignificance(x, y) > significance ||
                            mainFrameB.getPixelSignificance(x, y) > significance) {
                            sigPixels++;
                            double dx = (getFrame(frame)).getPixel(x, y, 1) -
                                        (sta.getFrame(frame)).getPixel(x, y, 1);
                            chiSq += dx * dx / (
//                                eCFA*Math.pow((staA.getFrame(frame)).getPixelError(x,y,1), 2) +
//                                 eCFBMath.pow((staB.getFrame(frame)).getPixelError(x,y,1), 2)
                                totalSigma);
                        }
                    }
                }
            }
        }
        if (sigPixels != 0) {
            chiSq /= sigPixels;
        } else {
            chiSq = Double.POSITIVE_INFINITY;
        }
//        System.errSystem.out.println(neuronA + "  " + neuronB +  "   " + chiSq);
        return chiSq;
    }


    public Gaussian2DFunction fit(int color, int binFactor) {
        return getMainFrame().downBin(binFactor).fit(color);
    }

    public Gaussian2DFunction fit(int binFactor) {
        int[] p = getMainFrameParams();
        float[] c = new float[3];
        if (p[0] != -1) {
            getFrame(p[0]).getPixel(p[1], p[2], c);
            c[0] = Math.abs(c[0]);
            c[1] = Math.abs(c[1]);
            c[2] = Math.abs(c[2]);

            return fit(MathUtil.maxIndex(c), binFactor);
        } else {
            return null;
        }
    }

    public Gaussian2DFunction fit() {
        return fit(1);
    }    

    /**
     * Build new STA with given width and height by concatenating the buffers of the given subSTAs.
     * 
     * The subSTA buffers are simply flowed into the new STA in order, so the shape of the new STA is
     * determined only by the given width and height.
     * 
     * In special case of only one subSTA given, this will return a new version of the subSTA reflowed 
     * to the requested shape.  But this is not done as efficiently as it could be for only a single STA.
     * 
     * If no subSTAs given, returns null.
     * 
     * @param subSTAs
     * @param width
     * @param height
     * @return
     * @throws Exception
     */
    public static STA concatenatingBuild(STA[] subSTAs, int width, int height) throws Exception {
        if (subSTAs.length == 0) return null;
        
        // Check subSTAs for compatibility
        Exception argsException = new Exception("Given STAs are not compatible: staDepth, refreshRate, StixelWidth/Height must match.");
        for (int i = 1; i < subSTAs.length; i++) {
            if (subSTAs[i].staDepth     != subSTAs[i-1].staDepth)     throw argsException;
            if (subSTAs[i].refreshTime  != subSTAs[i-1].refreshTime)  throw argsException;
            if (subSTAs[i].stixelWidth  != subSTAs[i-1].stixelWidth)  throw argsException;
            if (subSTAs[i].stixelHeight != subSTAs[i-1].stixelHeight) throw argsException;
        }
        
        
        // Initialize new STA, using given width and height, other params derived from subSTAs
        STA sta = new STA(subSTAs[0].staDepth, width, height, subSTAs[0].refreshTime,
                subSTAs[0].stixelWidth, subSTAs[0].stixelHeight);
        
        // Build new STA from subSTAs frame-by-frame
        for (int f = 0; f < sta.staDepth; f++) {
            
            // Get buffers from each of the subSTAs, keep track of how long the total buffer length gets
            float[][] staSubregionsBuffers = new float[subSTAs.length][];
            float[][] staSubregionsErrorBuffers = new float[subSTAs.length][];
            int bufferTotal = 0;
            for (int i = 0; i < subSTAs.length; i++) {
                staSubregionsBuffers[i] = subSTAs[i].getSTAFrame(f).getBuffer();
                staSubregionsErrorBuffers[i] = subSTAs[i].getSTAFrame(f).getErrorBuffer();
                bufferTotal += staSubregionsBuffers[i].length;
            }
            
            // Make new buffers and load subSTA buffers in
            float[] frameBuffer      = new float[bufferTotal];
            float[] frameErrorBuffer = new float[bufferTotal];
            int frameBufferIndex = 0;
            for (int i = 0; i < subSTAs.length; i++) {
                System.arraycopy(staSubregionsBuffers[i], 0, frameBuffer, frameBufferIndex, staSubregionsBuffers[i].length);
                System.arraycopy(staSubregionsErrorBuffers[i], 0, frameErrorBuffer, frameBufferIndex, staSubregionsErrorBuffers[i].length);	
                frameBufferIndex += staSubregionsBuffers[i].length;
            }
            
            ( (STAFrame) sta.getFrame(f)).set(frameBuffer, frameErrorBuffer);
        } // End iterate through frames
        return sta;
    }
}
