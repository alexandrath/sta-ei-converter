package edu.ucsc.neurobiology.vision.stimulus;

import java.io.*;
import java.util.*;


/**
 * This class is a single frame of a RGB checkerboard usied by the STA class.
 * Each element is an arbitrary color.
 *
 * @author Charles A. Loomis, University of California, Santa Cruz<br>
 *         Dumitru Petrusca, University of California, Santa Cruz
 *
 */
public class STAFrame extends ImageFrame {
    
    private float[] color;
    private float[] error;

    public STAFrame(int width, int height, double stixelWidth, double stixelHeight) {
        super(width, height, stixelWidth, stixelHeight);

        this.color = new float[width * height * 3];
        this.error = new float[width * height * 3];
    }

    public ImageFrame downBin(int binFactor) {
        if (binFactor == 1) return this;
        if (binFactor < 1) throw new Error();
        
        STAFrame f = new STAFrame(width/binFactor, height/binFactor, stixelWidth*binFactor, stixelHeight*binFactor);

        int wRemainder = width  - f.width*binFactor;
        int hRemainder = height - f.height*binFactor;
        int xStart = wRemainder / 2;
        int yStart = hRemainder / 2;
        
        for (int x = xStart; x < f.width; x++) {
            for (int y = yStart; y < f.height; y++) {
                float[] binnedPixel = binPixels(x*binFactor, y*binFactor, binFactor);
                f.setPixel(x, y, binnedPixel);
                
                float[] binnedError = binErrors(x*binFactor, y*binFactor, binFactor);
                f.setPixelError(x, y, binnedError);
            }
        }
        
        return f;
    }
    
    public float[] binPixels(int x0, int y0, int binFactor) {
        float[] curPix = new float[3];
        float[] binned = new float[3];
        for (int x = x0; x < x0+binFactor; x++) {
            for (int y = y0; y < y0+binFactor; y++) {
                this.getPixel(x, y, curPix);
                binned[0] += curPix[0];
                binned[1] += curPix[1];
                binned[2] += curPix[2];
            }
        }
        return binned;
    }
    
    public float[] binErrors(int x0, int y0, int binFactor) {
        float[] curErr = new float[3];
        float[] binned = new float[3];
        for (int x = x0; x < x0+binFactor; x++) {
            for (int y = y0; y < y0+binFactor; y++) {
                this.getPixelError(x, y, curErr);
                binned[0] += curErr[0];
                binned[1] += curErr[1];
                binned[2] += curErr[2];
            }
        }
        return binned;
    }
    
    public STAFrame(int width, int height, double stixelWidth, double stixelHeight, float[] color) {
        super(width, height, stixelWidth, stixelHeight);

        this.color = color;
        this.error = new float[width * height * 3];
    }


    public STAFrame(int width, int height, double stixelWidth, double stixelHeight, float[] color, float[] error) {
        super(width, height, stixelWidth, stixelHeight);

        this.color = color;
        this.error = error;
    }
    
    
    public void set(float[] c, float[] e) {
        System.arraycopy(c, 0, color, 0, color.length);
        System.arraycopy(e, 0, error, 0, color.length);
    }


    public void reset() {
        Arrays.fill(color, 0);
        Arrays.fill(error, 0);
    }


    public void setBuffer(float[] color) {
        this.color = color;
    }


    public void setErrorBuffer(float[] error) {
        this.error = error;
    }
    

    /**
     * Must behave reasonably when read from multiple threads concurrently.  
     * FIXME: Should get further thread-safing.
     */
    public float[] getPixel(int x, int y, float[] pixel) {
        System.arraycopy(color, 3 * (y * width + x), pixel, 0, 3);
        return pixel;
    }


    public float getPixel(int x, int y, int c) {
        return color[3 * (y * width + x) + c];
    }


    public void setPixel(int x, int y, int c, float val) {
        int index = 3 * (y * width + x) + c;
        this.color[index] = val;
    }


    public void addToPixel(int x, int y, int c, float val) {
        int index = 3 * (y * width + x) + c;
        this.color[index] += val;
    }


    public void setPixelError(int x, int y, int c, float val) {
        int index = 3 * (y * width + x) + c;
        this.error[index] = val;
    }


    public void setPixel(int x, int y, int c, float val, float err) {
        int index = 3 * (y * width + x) + c;
//        float[] color = this.getBuffer();
        this.color[index] = val;
        this.error[index] = err;
    }


    public void setPixel(int x, int y, float[] pixel) {
        System.arraycopy(pixel, 0, color, 3 * (y * width + x), 3);
    }


    public float[] getPixelError(int x, int y, float[] pixel) {
        System.arraycopy(error, 3 * (y * width + x), pixel, 0, 3);
        return pixel;
    }


    public float getPixelError(int x, int y, int c) {
        return error[3 * (y * width + x) + c];
    }


    public void setPixelError(int x, int y, float[] pixel) {
        System.arraycopy(pixel, 0, error, 3 * (y * width + x), 3);
    }
    
    
    public float[] getBuffer() {
        return color;
    }

    public float[] getErrorBuffer() {
        return error;
    }

    public String toString() {
        return "STAFrame";
    }
  
    
    public void write(DataOutput output) throws IOException {
        output.writeInt(width);
        output.writeInt(height);
        output.writeDouble((stixelWidth + stixelHeight)/2); //DEPRECATED PARAMETER.  USE GLOBALS FILE.

        for (int i = 0; i < color.length; i++) {
            output.writeFloat(color[i]);
            output.writeFloat(error[i]);
        }    	
    }


    void writeValuesOnly(DataOutput output, boolean useInt16, float scale) throws IOException {
        writeValuesOnly(output, useInt16, scale, false);
    }


    void writeValuesOnly(DataOutput output, boolean useInt16, float scale,
            boolean monochrome) throws IOException {
        output.writeInt(width);
        output.writeInt(height);
        output.writeDouble((stixelWidth + stixelHeight)/2); //DEPRECATED PARAMETER.  USE GLOBALS FILE.

        if (monochrome) {
            if (!useInt16) throw new IOException("Monochrome STA storage requires int16.");
            for (int i = 0; i < color.length; i += 3) {
                if (color[i] != color[i + 1] || color[i] != color[i + 2]) {
                    throw new IOException("STA is not monochrome at pixel " + (i / 3) + ".");
                }
                output.writeShort(quantizeInt16(color[i], scale));
            }
            return;
        }

        for (int i = 0; i < color.length; i++) {
            if (useInt16) {
                output.writeShort(quantizeInt16(color[i], scale));
            } else {
                output.writeFloat(color[i]);
            }
        }
    }


    private static int quantizeInt16(float value, float scale) {
        int quantized = scale == 0 ? 0 : Math.round(value / scale);
        return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, quantized));
    }


    public static STAFrame read(DataInput input, double stixelWidth, double stixelHeight) throws IOException {
        int w = input.readInt();
        int h = input.readInt();
        double pixSize = input.readDouble();  //DEPRECATED PARAMETER.  USE GLOBALS FILE.
        STAFrame f = new STAFrame(w, h, stixelWidth, stixelHeight);

        byte[] buffer = new byte[w * h * 3 * 2 * 4];
        input.readFully(buffer);
        int ch1, ch2, ch3, ch4;

        for (int i = 0, byteIndex = 0; i < f.color.length; i++) {
            ch1 = buffer[byteIndex++] & 0xff;
            ch2 = buffer[byteIndex++] & 0xff;
            ch3 = buffer[byteIndex++] & 0xff;
            ch4 = buffer[byteIndex++] & 0xff;
            f.color[i] = Float.intBitsToFloat(
                (ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));

            ch1 = buffer[byteIndex++] & 0xff;
            ch2 = buffer[byteIndex++] & 0xff;
            ch3 = buffer[byteIndex++] & 0xff;
            ch4 = buffer[byteIndex++] & 0xff;
            f.error[i] = Float.intBitsToFloat(
                (ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
        }

        return f;
    }


    static STAFrame readValuesOnly(DataInput input, double stixelWidth, double stixelHeight,
            boolean useInt16, float scale) throws IOException {
        return readValuesOnly(input, stixelWidth, stixelHeight, useInt16, scale, false);
    }


    static STAFrame readValuesOnly(DataInput input, double stixelWidth, double stixelHeight,
            boolean useInt16, float scale, boolean monochrome) throws IOException {
        int w = input.readInt();
        int h = input.readInt();
        double pixSize = input.readDouble();  //DEPRECATED PARAMETER.  USE GLOBALS FILE.
        STAFrame f = new STAFrame(w, h, stixelWidth, stixelHeight);

        if (monochrome) {
            if (!useInt16) throw new IOException("Monochrome STA storage requires int16.");
            for (int i = 0; i < f.color.length; i += 3) {
                float value = input.readShort() * scale;
                f.color[i] = value;
                f.color[i + 1] = value;
                f.color[i + 2] = value;
            }
            return f;
        }

        for (int i = 0; i < f.color.length; i++) {
            f.color[i] = useInt16 ? input.readShort() * scale : input.readFloat();
        }

        return f;
    }


    public boolean equals(STAFrame f) {
        if (f.width != width || f.height != height || f.stixelWidth != stixelWidth
                || f.stixelHeight != stixelHeight) {
            return false;
        }

        for (int i = 0; i < color.length; i++) {
            if (f.color[i] != color[i]) {
                return false;
            }
        }

        return true;
    }
}
