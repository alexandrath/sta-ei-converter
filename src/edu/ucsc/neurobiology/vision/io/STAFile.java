package edu.ucsc.neurobiology.vision.io;

import java.io.*;
import java.util.*;

import edu.ucsc.neurobiology.vision.stimulus.*;
import edu.ucsc.neurobiology.vision.util.*;
import edu.ucsc.neurobiology.vision.io.chunk.*;


/**
 * Used to create and read STA files. STA files contain an association between a neuron
 * ID and an STA. All STA parameters can also be queried from this class. This class
 * accepts and returns instances of the STA class. STA files are created by STACalculation.
 *
 * @see STA
 * @see STACalculation
 * @author Dumitru Petrusca, University of California, Santa Cruz
 */
public class STAFile implements STACollection {
    private static final boolean DEBUG = false;
    public static final int LEGACY_VERSION = 32;
    public static final int FLOAT32_NO_ERROR_VERSION = 33;
    public static final int INT16_NO_ERROR_VERSION = 34;
    public static final int INT16_MONOCHROME_NO_ERROR_VERSION = 35;
    private static final int HEADER_LENGTH = 164;
    private static final int SLOT_LENGTH = 4 + 8;
    private static final int UNUSED_SLOT = Integer.MIN_VALUE;
    
    private RandomAccessFile file;
    private final int version;
    private final int headerCapacity;
    private final int[] neuronIDList;
    private final long[] startLocationList;
    private final int width, height, staDepth; 
    private final int staOffset; //zero means that spike occurs in movie frame after last frame of sta. 
                                 //-1 means that spike occurs in last movie frame of sta.
    private final double stixelWidth, stixelHeight, refreshTime;
    private byte[] buffer;
    public final int staSize;
    
    private HashMap<Integer,STA> staMap;
    
    
    public STAFile(
        String fileName, int headerCapacity, int width, int height,
        int staDepth, int staOffset, double stixelWidth, double stixelHeight, double refreshTime) throws IOException {

        this(fileName, headerCapacity, width, height, staDepth, staOffset,
                stixelWidth, stixelHeight, refreshTime, LEGACY_VERSION);
    }


    public STAFile(
        String fileName, int headerCapacity, int width, int height,
        int staDepth, int staOffset, double stixelWidth, double stixelHeight,
        double refreshTime, int version) throws IOException {

        validateVersion(version);

        this.headerCapacity = headerCapacity;
        this.width = width;
        this.height = height;
        this.staDepth = staDepth;
        this.stixelWidth = stixelWidth;
        this.stixelHeight = stixelHeight;
        this.refreshTime = refreshTime;
        this.staOffset = staOffset;
        this.version = version;

        File f = new File(fileName);
        if (f.exists()) {
            f.delete();
            //throw new IOException("The sta file already exits");
        }
        file = new RandomAccessFile(fileName, "rw");
        file.writeInt(version);
        file.writeInt(headerCapacity);
        file.writeInt(width);
        file.writeInt(height);
        file.writeInt(staDepth);
        file.writeDouble((stixelWidth + stixelHeight)/2);  // DEPRECATED PARAMETER.  USE GLOBALS FILE.
        file.writeDouble(refreshTime);
        file.writeInt(staOffset);  
        for (int i = 0; i < 124; i++) {
            file.writeByte(0);
        }

        neuronIDList = new int[headerCapacity];
        Arrays.fill(neuronIDList, UNUSED_SLOT);
        startLocationList = new long[headerCapacity];
        flushAll();

        staSize = calculateSTASize(version, width, height, staDepth);
        this.buffer = new byte[staSize];
    }


    public STAFile(String fileName) throws IOException {
        this(new File(fileName));
    }


    public STAFile(File f) throws IOException {
        this(f, false);
    }


    public STAFile(File f, boolean readOnly) throws IOException {
        if (!f.exists() || f.isDirectory()) {
            throw new IOException("Cannot read file: " + f.getAbsolutePath());
        }
        GlobalsFile gfile = new GlobalsFile(StringUtil.removeExtension(f.getPath()) + ".globals", ChunkFile.READ);
        GlobalsFile.RunTimeMovieParams runTimeParams = gfile.getRunTimeMovieParams();
 
        file = new RandomAccessFile(f, readOnly ? "r" : "rw");

        this.version = file.readInt();
        validateVersion(version);
        if (DEBUG) {
            System.out.println("version : " + version);
        }
        this.headerCapacity = file.readInt();
        if (DEBUG) {
            System.out.println("headerCapacity : " + headerCapacity);
        }
        this.width = file.readInt();
        if (DEBUG) {
            System.out.println("width : " + width);
        }
        this.height = file.readInt();
        if (DEBUG) {
            System.out.println("height : " + height);
        }
        this.staDepth = file.readInt();
        if (DEBUG) {
            System.out.println("staDepth : " + staDepth);
        }
        double deprecatedPixelSize = file.readDouble();  //getting information from globals file instead.
        this.stixelWidth = runTimeParams.micronsPerStixelX;
        this.stixelHeight = runTimeParams.micronsPerStixelY;
        
        this.refreshTime = file.readDouble();
     
        this.staOffset = file.readInt();
        if (DEBUG) {
            System.out.println("staOffset : " + staOffset);
        }
        file.readFully(new byte[124]);
        
        //Reading as a block here significantly improves performance
        //in OS 10.5, Java 1.5, when reading over NFS.  OS 10.5 with
        //NFS appears to not have sufficient buffering.
        
        byte[] buffer = new byte[12*headerCapacity];
        file.readFully(buffer);
        
        DataInputStream dis  = new DataInputStream(new ByteArrayInputStream(buffer));
   
        staSize = calculateSTASize(version, width, height, staDepth);
        neuronIDList = new int[headerCapacity];
        startLocationList = new long[headerCapacity];
        for (int i = 0; i < headerCapacity; i++) {
            neuronIDList[i] = dis.readInt();
            startLocationList[i] = dis.readLong();
        }

        // check the length of the file
        if (!isSizeValid()) {
            long expectedlength = (long) HEADER_LENGTH +
                                  (long) headerCapacity * (long) SLOT_LENGTH +
                                  (long) getIDList().length * (long) staSize;
            throw new IOException(
                "Corrupt STA file " + f.getAbsolutePath() +
                ". Expected size: " + expectedlength +
                ", actual size: " + file.length() +
                ". Size difference: " + (expectedlength - file.length())
                );
        }

        this.buffer = new byte[staSize];
    }


    public boolean isSizeValid() throws IOException {
        long expectedlength = (long) HEADER_LENGTH +
                              (long) headerCapacity * (long) SLOT_LENGTH +
                              (long) getIDList().length * (long) staSize;
        return (expectedlength == file.length());
    }


    /**
     * Do not change this method.
     * @throws IOException
     */
    synchronized public void bufferAllSTAs() throws IOException {
        HashMap<Integer,STA> _staMap = new HashMap<Integer,STA>();

        int[] idList = getIDList();
        System.out.println("sta's " + idList.length);

        for (int i = 0; i < idList.length; i++) {
            System.out.println("loading " + i);
            _staMap.put(new Integer(idList[i]), getSTA(idList[i]));
        }

        staMap = _staMap;
    }


    private void flush(int i) throws IOException {
        file.seek(HEADER_LENGTH + i * SLOT_LENGTH);
        file.writeInt(neuronIDList[i]);
        file.writeLong(startLocationList[i]);
    }


    private void flushAll() throws IOException {
        file.seek(HEADER_LENGTH);

        for (int i = 0; i < headerCapacity; i++) {
            file.writeInt(neuronIDList[i]);
            file.writeLong(startLocationList[i]);
        }
    }


    synchronized public int getSTACount() {
        int n = 0;
        for (int i = 0; i < headerCapacity; i++)
            if (!isEmpty(i)) n++;
        return n;
    }


    synchronized private boolean isEmpty(int slotIndex) {
        if (neuronIDList[slotIndex] < 0) {
            return true;
        } else {
            return false;
        }
    }


    synchronized public int getHeaderCapacity() {
        return headerCapacity;
    }


    synchronized private int getSomeEmptySlot() {
        for (int i = 0; i < headerCapacity; i++)
            if (isEmpty(i)) return i;
        return -1;
    }


    synchronized private int getIndexForID(int id) {
        for (int i = 0; i < headerCapacity; i++) {
            if (isEmpty(i)) continue;
            if (neuronIDList[i] == id) return i;
        }
        return -1;
    }

    
    // Implements, so @Override will only work with Java 6
    synchronized public int[] getIDList() {
        IntegerList list = new IntegerList();
        for (int i = 0; i < headerCapacity; i++)
            if (!isEmpty(i)) list.add(neuronIDList[i]);
        return list.toArray();
    }


    synchronized public void addSTA(int neuronID, STA sta) throws IOException {
        if (sta == null) {
            throw new NullPointerException("STA cannot be null.");
        }

        int firstFreeCellIndex = getSomeEmptySlot();
        if (firstFreeCellIndex == -1) {
            throw new IOException("The header is full.");
        }
        
        if (sta.getWidth() != width) {
            throw new IOException("STA to be added has the wrong width.");
        }
        
        if (sta.getHeight() != height) {
            throw new IOException("STA to be added has the wrong height.");
        }
        
        if (sta.getSTADepth() != staDepth) {
            throw new IOException("STA to be added has the wrong depth.");
        }

        neuronIDList[firstFreeCellIndex] = neuronID;
        startLocationList[firstFreeCellIndex] = file.length();

        // write the STA
        file.seek(startLocationList[firstFreeCellIndex]);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        sta.write(dos, hasErrors(), usesInt16(), isMonochrome());
        dos.close();
        bos.close();
        file.write(bos.toByteArray());

        firstFreeCellIndex++;
        flush(firstFreeCellIndex - 1);
    }


 

    // Implements, so @Override will only work with Java 6
    synchronized public STA getSTA(final int neuronID) throws IOException {
        if (staMap != null) {
            return (STA) staMap.get(new Integer(neuronID));
        } else {
            int index = getIndexForID(neuronID);

            if (index == -1) {
                return null;
            } else {
                file.seek(startLocationList[index]);
                file.readFully(buffer);
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(
                    buffer));
                return STA.read(dis, stixelWidth, stixelHeight,
                        hasErrors(), usesInt16(), isMonochrome());
            }
        }
    }


    public int getVersion() {
        return version;
    }


    public boolean hasErrors() {
        return version == LEGACY_VERSION;
    }


    public boolean usesInt16() {
        return version == INT16_NO_ERROR_VERSION ||
                version == INT16_MONOCHROME_NO_ERROR_VERSION;
    }


    public boolean isMonochrome() {
        return version == INT16_MONOCHROME_NO_ERROR_VERSION;
    }


    private static void validateVersion(int version) throws IOException {
        if (version != LEGACY_VERSION &&
                version != FLOAT32_NO_ERROR_VERSION &&
                version != INT16_NO_ERROR_VERSION &&
                version != INT16_MONOCHROME_NO_ERROR_VERSION) {
            throw new IOException("Unsupported STA file version: " + version);
        }
    }


    private static int calculateSTASize(int version, int width, int height, int staDepth) {
        int bytesPerValue = version == LEGACY_VERSION ? 8 :
                (version == FLOAT32_NO_ERROR_VERSION ? 4 : 2);
        int scaleBytes = usesInt16Version(version) ? 4 : 0;
        int channelCount = version == INT16_MONOCHROME_NO_ERROR_VERSION ? 1 : 3;
        return 8 + 4 + scaleBytes +
                staDepth * (4 + 4 + 8 + width * height * channelCount * bytesPerValue);
    }


    private static boolean usesInt16Version(int version) {
        return version == INT16_NO_ERROR_VERSION ||
                version == INT16_MONOCHROME_NO_ERROR_VERSION;
    }


    /**
     * This method is an implementation side effect. Do not call.
     */
    public Object getObject(int objectID) throws IOException {
        return getSTA(objectID);
    }


    // Implements, so @Override will only work with Java 6
    public double getStixelWidth() {
        return stixelWidth;
    }
    
    // Implements, so @Override will only work with Java 6
    public double getStixelHeight() {
        return stixelHeight;
    }


    // Implements, so @Override will only work with Java 6
    public int getWidth() {
        return width;
    }

    
    // Implements, so @Override will only work with Java 6
    public int getHeight() {
        return height;
    }


    // Implements, so @Override will only work with Java 6
    public int getSTADepth() {
        return staDepth;
    }
    
    // Implements, so @Override will only work with Java 6
    public int getSTAOffset() {
        return staOffset;
    }


    // Implements, so @Override will only work with Java 6
    public double getRefreshTime() {
        return refreshTime;
    }

    public boolean isConsistentWith(STACollection other) {
        if (getWidth() 		  != other.getWidth())        return false;
        if (getHeight()		  != other.getHeight())       return false;
        if (getSTADepth()     != other.getSTADepth())     return false;
        if (getSTAOffset()    != other.getSTAOffset())    return false;
        if (getStixelWidth()  != other.getStixelWidth())  return false;
        if (getStixelHeight() != other.getStixelHeight()) return false;
        return true;
    }

    /**
     * Implements, so @Override will only work with Java 6
     *     
     * This should be designed so that it is safe to run it more than once.  file.close() implements
     * Closeable, which is specified to have no effect if run more than once.
     */
    synchronized public void close() throws IOException {
        file.close();
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
        } catch (Exception e) {} finally {
            super.finalize();
        }
    }

}
