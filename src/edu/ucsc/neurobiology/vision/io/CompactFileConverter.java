package edu.ucsc.neurobiology.vision.io;

import edu.ucsc.neurobiology.vision.stimulus.STA;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;


/** Converts legacy EI and STA files to the compact formats understood by Vision. */
public final class CompactFileConverter {

    private CompactFileConverter() {
    }


    public static void main(String[] args) throws Exception {
        boolean matchSTA = args.length > 0 && args[0].equals("ei-no-errors-for-sta");
        if ((!matchSTA && args.length != 3) || (matchSTA && args.length != 4)) {
            System.err.println("Usage: CompactFileConverter <ei-no-errors|sta-no-errors|sta-int16|sta-int16-mono> <input> <output>");
            System.err.println("   or: CompactFileConverter ei-no-errors-for-sta <input.ei> <input.sta> <output.ei>");
            System.exit(2);
        }

        File input = new File(args[1]);
        File output = new File(args[matchSTA ? 3 : 2]);
        validatePaths(input, output);

        long start = System.currentTimeMillis();
        boolean complete = false;
        try {
            if (matchSTA) {
                convertEIMatchingSTA(input, new File(args[2]), output);
            } else if (args[0].equals("ei-no-errors")) {
                convertEI(input, output, null);
            } else if (args[0].equals("sta-no-errors")) {
                convertSTA(input, output, STAFile.FLOAT32_NO_ERROR_VERSION);
            } else if (args[0].equals("sta-int16")) {
                convertSTA(input, output, STAFile.INT16_NO_ERROR_VERSION);
            } else if (args[0].equals("sta-int16-mono")) {
                convertSTA(input, output, STAFile.INT16_MONOCHROME_NO_ERROR_VERSION);
            } else {
                throw new IllegalArgumentException("Unknown conversion mode: " + args[0]);
            }
            complete = true;
        } finally {
            if (!complete && output.exists() && !output.delete()) {
                System.err.println("Could not remove incomplete output: " + output);
            }
        }

        double seconds = (System.currentTimeMillis() - start) / 1000.0;
        System.out.println("Wrote " + output.length() + " bytes in " + seconds + " seconds.");
    }


    private static void validatePaths(File input, File output) throws IOException {
        if (!input.isFile()) {
            throw new IOException("Input is not a file: " + input);
        }
        if (output.exists()) {
            throw new IOException("Output already exists: " + output);
        }
        if (input.getCanonicalFile().equals(output.getCanonicalFile())) {
            throw new IOException("Input and output must be different files.");
        }
        File parent = output.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create output directory: " + parent);
        }
    }


    private static void convertEIMatchingSTA(File inputFile, File staFile, File outputFile) throws IOException {
        if (!staFile.isFile()) {
            throw new IOException("STA input is not a file: " + staFile);
        }

        STAFile sta = null;
        try {
            sta = new STAFile(staFile, true);
            convertEI(inputFile, outputFile, sta.getIDList());
        } finally {
            if (sta != null) sta.close();
        }
    }


    private static void convertEI(File inputFile, File outputFile, int[] selectedIDs) throws IOException {
        PhysiologicalImagingFile input = null;
        PhysiologicalImagingFile output = null;
        try {
            input = new PhysiologicalImagingFile(inputFile.getPath(), true);
            if (input.getFormat() != PhysiologicalImagingFile.LEGACY_FORMAT) {
                throw new IOException("Expected a legacy EI input; it is already compact.");
            }
            long expectedInputSize = (long) input.headerSize
                    + (long) input.getIDList().length * ((long) input.imageSize + 8L);
            if (inputFile.length() != expectedInputSize) {
                throw new IOException("EI has a partial record or duplicate cell IDs.");
            }
            output = new PhysiologicalImagingFile(
                    outputFile.getPath(), input.nlPoints, input.nrPoints, input.arrayID,
                    PhysiologicalImagingFile.FLOAT32_NO_ERROR_FORMAT);

            int[] ids = selectedIDs == null ? input.getIDList() : selectedIDs;
            for (int i = 0; i < ids.length; i++) {
                int id = ids[i];
                if (input.getIndex(id) == -1) {
                    throw new IOException("EI does not contain STA cell ID " + id);
                }
                output.appendImage(id, input.getNSpikes(id), input.getImage(id));
                printProgress("EI", i + 1, ids.length);
            }
        } finally {
            if (output != null) output.close();
            if (input != null) input.close();
        }
    }


    private static void convertSTA(File inputFile, File outputFile, int outputVersion) throws IOException {
        STAFile input = null;
        STAFile output = null;
        try {
            input = new STAFile(inputFile, true);
            if (input.getVersion() != STAFile.LEGACY_VERSION) {
                throw new IOException("Expected a legacy STA input (version 32).");
            }
            output = new STAFile(
                    outputFile.getPath(), input.getHeaderCapacity(), input.getWidth(),
                    input.getHeight(), input.getSTADepth(), input.getSTAOffset(),
                    input.getStixelWidth(), input.getStixelHeight(), input.getRefreshTime(),
                    outputVersion);

            int[] ids = input.getIDList();
            HashSet<Integer> seen = new HashSet<Integer>();
            for (int i = 0; i < ids.length; i++) {
                if (!seen.add(ids[i])) {
                    throw new IOException("Duplicate STA cell ID " + ids[i]);
                }
                STA sta = input.getSTA(ids[i]);
                for (int frame = 0; frame < sta.getSTADepth(); frame++) {
                    if (sta.getSTAFrame(frame).getWidth() != input.getWidth()
                            || sta.getSTAFrame(frame).getHeight() != input.getHeight()) {
                        throw new IOException("STA frame dimensions differ from the header for cell " + ids[i]);
                    }
                }
                output.addSTA(ids[i], sta);
                printProgress("STA", i + 1, ids.length);
            }
        } finally {
            if (output != null) output.close();
            if (input != null) input.close();
        }
    }


    private static void printProgress(String kind, int complete, int total) {
        if (complete == total || complete % 50 == 0) {
            System.out.println(kind + ": " + complete + "/" + total);
        }
    }
}
