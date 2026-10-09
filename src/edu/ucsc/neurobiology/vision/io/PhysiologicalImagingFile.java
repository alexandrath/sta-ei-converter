package edu.ucsc.neurobiology.vision.io;

import edu.ucsc.neurobiology.vision.electrodemap.ElectrodeMapFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedHashMap;


/**
 * This class is used to create and read Physiological Image files (EI files).
 * Each EI is associated with a neuron ID and is represented by a float[][][].
 * float[0][][] is the EI.
 * float[1][][] is the error of the EI.
 *
 * @author Dumitru Petrusca, University of California, Santa Cruz
 * 
 * FIXME: There's a lot of stuff in here that should be moved to a specific EI data structure class
 */
public class PhysiologicalImagingFile {
    public static final int LEGACY_FORMAT = 0;
    public static final int FLOAT32_NO_ERROR_FORMAT = 1;
    private static final int COMPACT_MAGIC = 0x45494331; // "EIC1"

    public final int nElectrodes;
    public final int headerSize, imageSize, nSamples;
    private LinkedHashMap<Integer, Integer> idIndexMap;
    private RandomAccessFile dis;
    private byte[] buffer;
    public final int nlPoints, nrPoints, arrayID;
    private final int format;

    public PhysiologicalImagingFile(String fileName) throws IOException {
        this(fileName, false);
    }


    public PhysiologicalImagingFile(String fileName, boolean readOnly) throws IOException {
        dis = new RandomAccessFile(fileName, readOnly ? "r" : "rw");

        int first = dis.readInt();
        if (first == COMPACT_MAGIC) {
            format = dis.readInt();
            if (format != FLOAT32_NO_ERROR_FORMAT) {
                throw new IOException("Unsupported compact EI format: " + format);
            }
            nlPoints = dis.readInt();
            nrPoints = dis.readInt();
            arrayID = dis.readInt();
        } else {
            format = LEGACY_FORMAT;
            nlPoints = first;
            nrPoints = dis.readInt();
            arrayID = dis.readInt();
        }
        nElectrodes = ElectrodeMapFactory.getElectrodeMap(arrayID).
                getNumberOfElectrodes();

        headerSize = (int) dis.getFilePointer();
        nSamples = nlPoints + nrPoints + 1;
        imageSize = bytesPerValue() * nElectrodes * nSamples;

        idIndexMap = new LinkedHashMap<Integer, Integer>();
        for (int i = 0; ; i++) {
            long seek = headerSize + (long) i * ((long) imageSize + 4 + 4);
            if (seek < dis.length()) {
                dis.seek(seek);
                idIndexMap.put(dis.readInt(), i);
            } else break;
        }

        buffer = new byte[imageSize];
    }

    @Override
    public String toString() {
        String nl = System.getProperty("line.separator");
        String ret = "Physiological Imaging File:" + nl;
        ret += "  Left points: " + nlPoints + nl;
        ret += "  Right points: " + nrPoints + nl;
        ret += "  Number of IDs: " + idIndexMap.size() + nl;

        // FIXME: This should be abstracted
        try {
            int max = 0;
            double mean = 0;
            for (int id : idIndexMap.keySet()) {
                int nSpikes = getNSpikes(id);
                max = Math.max(max, nSpikes);
                mean += nSpikes;
            }
            mean /= idIndexMap.size();

            ret += "  Max, mean number of spikes: " + max + ", " + mean + nl;
        } catch (IOException e) {
        }

        return ret;
    }

    public PhysiologicalImagingFile(String fileName, final int nlPoints, final int nrPoints, final int arrayID) throws
            IOException {

        this(fileName, nlPoints, nrPoints, arrayID, LEGACY_FORMAT);
    }


    public PhysiologicalImagingFile(String fileName, final int nlPoints, final int nrPoints,
            final int arrayID, final int format) throws IOException {

        if (format != LEGACY_FORMAT && format != FLOAT32_NO_ERROR_FORMAT) {
            throw new IOException("Unsupported EI format: " + format);
        }

        this.nlPoints = nlPoints;
        this.nrPoints = nrPoints;
        this.arrayID = arrayID;
        this.nSamples = nlPoints + nrPoints + 1;
        this.format = format;

        dis = new RandomAccessFile(fileName, "rw");

        if (format == LEGACY_FORMAT) {
            dis.writeInt(nlPoints);
            dis.writeInt(nrPoints);
            dis.writeInt(arrayID);
        } else {
            dis.writeInt(COMPACT_MAGIC);
            dis.writeInt(format);
            dis.writeInt(nlPoints);
            dis.writeInt(nrPoints);
            dis.writeInt(arrayID);
        }

        this.nElectrodes = ElectrodeMapFactory.getElectrodeMap(arrayID).
                getNumberOfElectrodes();

//		System.out.println("nlPoints = " + nlPoints);
//		System.out.println("nELectrodes = " + nElectrodes);
//		System.out.println("nrPoints = " + nrPoints);

        headerSize = (int) dis.getFilePointer();
        imageSize = bytesPerValue() * nElectrodes * nSamples;

        idIndexMap = new LinkedHashMap<Integer, Integer>();
        buffer = new byte[imageSize];
    }

    synchronized public int[] getIDList() {
        int[] arr = new int[idIndexMap.size()];
        int i = 0;
        for (int id : idIndexMap.keySet()) arr[i++] = id;
        return arr;
    }


    synchronized public int getArrayID() {
        return arrayID;
    }


    /**
     * Writes new EI to end of file
     *
     * @param id
     * @param nSpikes
     * @param image
     */
    synchronized public void appendImage(final int id, final int nSpikes, float[][][] image) throws IOException {
        int index = idIndexMap.size();
        writeImageToIndex(index, id, nSpikes, image);
    }

    /**
     * Attempts to write EI to existing index, or else writes to end of file.
     *
     * @param id
     * @param nSpikes
     * @param image
     * @throws IOException
     */
    synchronized public void overwriteOrAppendImage(final int id, final int nSpikes, float[][][] image) throws IOException {
        int index = idIndexMap.size();
        if (idIndexMap.containsKey(id)) index = idIndexMap.get(id);
        writeImageToIndex(index, id, nSpikes, image);
    }

    /**
     * Writes new EI to the specified index.  Must be careful that the EI is the right size otherwise the
     * file will be corrupted.
     *
     * @param index
     * @param id
     * @param nSpikes
     * @param image
     * @throws IOException
     */
    synchronized private void writeImageToIndex(final int index, final int id, final int nSpikes, float[][][] image) throws IOException {
        for (int e = 0; e < nElectrodes; e++) {
            if (image[0][e].length != nSamples ||
                    (hasErrors() && image[1][e].length != nSamples)) {
                throw new Error("Wrong Image Length");
            }
        }

        idIndexMap.put(id, index);
        dis.seek((long) headerSize + (long) index * ((long) imageSize + 4 + 4));
        dis.writeInt(id); // write ID
        dis.writeInt(nSpikes); // write nSpikes

        int bits;
        for (int e = 0, byteIndex = 0; e < nElectrodes; e++) {
            for (int i = 0; i < nSamples; i++) {
                // write the value
                bits = Float.floatToIntBits((float) image[0][e][i]);
                buffer[byteIndex++] = (byte) ((bits >>> 24) & 0xFF);
                buffer[byteIndex++] = (byte) ((bits >>> 16) & 0xFF);
                buffer[byteIndex++] = (byte) ((bits >>> 8) & 0xFF);
                buffer[byteIndex++] = (byte) ((bits >>> 0) & 0xFF);
                if (hasErrors()) {
                    // write the error
                    bits = Float.floatToIntBits((float) image[1][e][i]);
                    buffer[byteIndex++] = (byte) ((bits >>> 24) & 0xFF);
                    buffer[byteIndex++] = (byte) ((bits >>> 16) & 0xFF);
                    buffer[byteIndex++] = (byte) ((bits >>> 8) & 0xFF);
                    buffer[byteIndex++] = (byte) ((bits >>> 0) & 0xFF);
                }
            }
        }

        // write the image
        dis.write(buffer);
    }


    public int getIndex(int id) {
        if (!idIndexMap.containsKey(id)) return -1;
        return idIndexMap.get(id);
    }


    public final static int STANDARD_DEVIATION = 0;
    public final static int VARIANCE_OF_MEAN = 1;

    synchronized public float[][][] getImage(int id) throws IOException {
        return getImage(id, STANDARD_DEVIATION);
    }


    private boolean numSpikesErrorShown = false;

    /**
     * @param id
     * @param errorMode either PhysilogicalImaging.STANDARD_DEVIATION or PhysiologicalImaging.VARIANCE_OF_MEAN
     * @return dimension 3 array array containing the image.
     * [average(0)/error(1)][electrode][time index]
     */
    synchronized public float[][][] getImage(int id, int errorMode) throws IOException {
        int index = getIndex(id);
        if (index == -1) return null;

        dis.seek((long) headerSize + (long) index * ((long) imageSize + 4 + 4));
        dis.readInt(); // read ID
        // read nSpikes, in physiological imaging, slow, this was set to zero for a long time.
        // must always check to see if it is zero, and dump an error if so.
        int numSpikes = dis.readInt();

        // read the image
        dis.readFully(buffer);

        float[][][] image = new float[2][nElectrodes][nSamples];
        int ch1, ch2, ch3, ch4;
        for (int e = 0, bIndex = 0; e < nElectrodes; e++) {
            for (int i = 0; i < nSamples; i++) {
                ch1 = buffer[bIndex++] & 0xff;
                ch2 = buffer[bIndex++] & 0xff;
                ch3 = buffer[bIndex++] & 0xff;
                ch4 = buffer[bIndex++] & 0xff;
                float amplitude = Float.intBitsToFloat(
                        (ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
                image[0][e][i] = amplitude;

                if (hasErrors()) {
                    ch1 = buffer[bIndex++] & 0xff;
                    ch2 = buffer[bIndex++] & 0xff;
                    ch3 = buffer[bIndex++] & 0xff;
                    ch4 = buffer[bIndex++] & 0xff;
                    float sigma = Float.intBitsToFloat(
                            (ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
                    if (errorMode == VARIANCE_OF_MEAN) {
                        if (numSpikes == 0 && !numSpikesErrorShown) {
                            System.err.println("Number of spikes in EI is zero for neuron " + id + ".");
                            System.err.println("It is likely that the EI's need to be recalculated.");
                            numSpikesErrorShown = true;
                        }
                        sigma /= Math.sqrt(numSpikes);
                    }
                    image[1][e][i] = sigma;
                }

            }
        }

        return image;
    }

    /**
     * @param id
     * @return ei with zeros filled in for missing data.  Use when joining divided data.
     */
    synchronized public float[][][] getExpandedImage(int id) throws IOException {
        float[][][] subregionImage = getImage(id);
        int fullArrayID = arrayID & 0xFFFF;
        int part = (arrayID >> 16) & 0xFF;
        int nParts = (arrayID >> 24);

        int[] parentElectrodeNumbers = ElectrodeMapFactory.getElectrodeMap(arrayID).getParentElectrodeNumbers();

        int nFullElectrodes = ElectrodeMapFactory.getElectrodeMap(fullArrayID).getNumberOfElectrodes();

        float[][][] fullImage = new float[2][nFullElectrodes][nSamples];

        for (int i = 0; i < nSamples; i++) {
            //TTL
            fullImage[0][0][i] = subregionImage[0][0][i];
            fullImage[1][0][i] = subregionImage[1][0][i];
            for (int e = 1; e < nElectrodes; e++) {

                fullImage[0][parentElectrodeNumbers[e - 1]][i] = subregionImage[0][e][i];
                fullImage[1][parentElectrodeNumbers[e - 1]][i] = subregionImage[1][e][i];
            }
        }

        return fullImage;
    }

    /**
     * @param id
     * @return number of spikes used in physiological image.
     */
    synchronized public int getNSpikes(int id) throws IOException {
        int index = getIndex(id);
        if (index == -1) return -1;
        dis.seek((long) headerSize + (long) index * ((long) imageSize + 4 + 4));
        dis.readInt(); // read ID
        return dis.readInt(); // read nSpikes
    }


    public boolean hasErrors() {
        return format == LEGACY_FORMAT;
    }


    public int getFormat() {
        return format;
    }


    private int bytesPerValue() {
        return hasErrors() ? 8 : 4;
    }


    /**
     * This should be designed so that it is safe to run it more than once.  dis.close() implements
     * Closeable, which is specified to have no effect if run more than once.
     *
     * @throws IOException
     */
    synchronized public void close() throws IOException {
        dis.close();
    }


    /**
     * Returns the reduced chi squared comparison of two neurons, based on EIs.
     * <p>
     * FIXME: Business logic should be moved to specific EI class
     */
    public double compareEIs(int referenceID, int comparisonID, double significance) throws IOException {
        double chiSq = 0;
        double sigPoints = 0;
        float[][] reference = getImage(referenceID, STANDARD_DEVIATION)[0]; //[average(0)/error(1)][electrode][time index]
        float[][] comparison = getImage(comparisonID, STANDARD_DEVIATION)[0];
        float[][] referenceSD = getImage(referenceID, STANDARD_DEVIATION)[1]; //[average(0)/error(1)][electrode][time index]
        float[][] comparisonSD = getImage(comparisonID, STANDARD_DEVIATION)[1];
        float[][] referenceVM = getImage(referenceID, VARIANCE_OF_MEAN)[1];
        float[][] comparisonVM = getImage(referenceID, VARIANCE_OF_MEAN)[1];
        //skip ttl electrode (0)
        for (int electrode = 1; electrode < reference.length; electrode++) {
            for (int time = 0; time < reference[0].length; time++) {
                if (reference[electrode][time] / referenceVM[electrode][time] > significance ||
                        comparison[electrode][time] / comparisonVM[electrode][time] > significance) {
                    sigPoints++;
                    double dx = reference[electrode][time] - comparison[electrode][time];
                    double er1 = referenceSD[electrode][time];
                    double er2 = comparisonSD[electrode][time];

                    //		if(er1!=0 && er2!=0) {
                    chiSq += dx * dx / (er1 * er1 + er2 * er2);
                    sigPoints++;

                    //		}

//						System.out.println(dx*dx/(er1*er1 + er2*er2));
//						System.out.println(dx*dx);
//						System.out.println(er1*er1);
//						System.out.println(er2*er2);
//						System.out.println("electrode: " + electrode);
//						System.out.println("time: " + time);


                }
            }
        }
        //	System.out.println(sigPoints);
        if (sigPoints != 0) {
            chiSq /= sigPoints;
        } else {

            chiSq = Double.POSITIVE_INFINITY;
        }
        return chiSq;

    }


    /**
     * Finds the electrode with the largest amplitude spike.  Assumes (correctly) that
     * the ei has already had its means subtracted.
     *
     * @param ei
     * @return maxElectrode
     * <p>
     * FIXME: Should be moved to specific EI class
     */
    public static int getMaxElectrode(float[][][] ei) {
        double max = 0.0;
        int maxElectrode = -1;
        for (int electrode = 1; electrode < ei[0].length; electrode++) {
            for (int time = 0; time < ei[0][0].length; time++) {
                if (Math.abs(ei[0][electrode][time]) > max) {
                    max = Math.abs(ei[0][electrode][time]);
                    maxElectrode = electrode;
                }
            }
        }
        return maxElectrode;
    }

    public int getMaxElectrode(int id) throws IOException {
        return getMaxElectrode(getImage(id));
    }

    @Override
    /**
     * This is a backup safeguard, mostly here because people seemed to forget to close STAFiles and 
     * EI files appropriately from Matlab.  Do not rely on this to close files; you should always run close
     * explicitly.
     */
    protected void finalize() throws Throwable {
        try {
            close();
        } catch (Exception e) {
        } finally {
            super.finalize();
        }
    }

    /**
     * main() method for testing EI file functionality
     */
    public static void main(String args[]) throws Exception {
        String fileName = "/Volumes/Analysis/2008-08-27-0/spectra-output/el.2/qt0.030_s3.0/data000/data000.ei-raw";
        PhysiologicalImagingFile eiFile = new PhysiologicalImagingFile(fileName);
        System.out.println(eiFile.toString());
    }

}
