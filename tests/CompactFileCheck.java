import edu.ucsc.neurobiology.vision.io.PhysiologicalImagingFile;
import edu.ucsc.neurobiology.vision.io.STAFile;
import edu.ucsc.neurobiology.vision.stimulus.STA;
import edu.ucsc.neurobiology.vision.stimulus.STAFrame;

import java.io.File;
import java.util.Arrays;

/**
 * Standalone, read-only validation helper for compact Vision STA/EI files.
 *
 * Compile against the compact Vision.jar; this class is intentionally outside
 * the Vision build so it can be used as an operational safety check.
 */
public final class CompactFileCheck {

    private CompactFileCheck() {
    }


    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("ei")) {
            PhysiologicalImagingFile original = new PhysiologicalImagingFile(args[1], true);
            int[] ids;
            try {
                ids = original.getIDList();
            } finally {
                original.close();
            }
            validateEI(new File(args[1]), new File(args[2]), ids);
            return;
        }

        if (args.length == 3 && args[0].equals("sta")) {
            STAResult result = validateSTA(new File(args[1]), new File(args[2]));
            printSTAResult(result);
            return;
        }

        if (args.length == 4 && args[0].equals("pair")) {
            File originalDirectory = new File(args[1]);
            File compactDirectory = new File(args[2]);
            String prefix = args[3];

            File originalSTA = new File(originalDirectory, prefix + ".sta");
            File compactSTA = new File(compactDirectory, prefix + ".sta");
            STAResult staResult = validateSTA(originalSTA, compactSTA);
            printSTAResult(staResult);

            validateEI(
                    new File(originalDirectory, prefix + ".ei"),
                    new File(compactDirectory, prefix + ".ei"),
                    staResult.ids);
            return;
        }

        if (args.length >= 2 && args[0].equals("inspect-sta")) {
            for (int i = 1; i < args.length; i++) inspectSTA(new File(args[i]));
            return;
        }

        if (args.length >= 2 && args[0].equals("inspect-ei")) {
            for (int i = 1; i < args.length; i++) inspectEI(new File(args[i]));
            return;
        }

        System.err.println("Usage:");
        System.err.println("  CompactFileCheck sta <original.sta> <compact.sta>");
        System.err.println("  CompactFileCheck ei <original.ei> <compact.ei>");
        System.err.println("  CompactFileCheck pair <original-directory> <compact-directory> <prefix>");
        System.err.println("  CompactFileCheck inspect-sta <file.sta> [...]");
        System.err.println("  CompactFileCheck inspect-ei <file.ei> [...]");
        System.exit(2);
    }


    private static STAResult validateSTA(File originalPath, File compactPath) throws Exception {
        STAFile original = new STAFile(originalPath, true);
        STAFile compact = new STAFile(compactPath, true);
        try {
            if (original.getVersion() != STAFile.LEGACY_VERSION) {
                throw new AssertionError("Expected legacy STA version 32, found "
                        + original.getVersion());
            }
            if (compact.getVersion() != STAFile.INT16_NO_ERROR_VERSION
                    && compact.getVersion() != STAFile.INT16_MONOCHROME_NO_ERROR_VERSION) {
                throw new AssertionError("Expected compact STA version 34 or 35, found "
                        + compact.getVersion());
            }

            if (original.getHeaderCapacity() != compact.getHeaderCapacity()
                    || original.getWidth() != compact.getWidth()
                    || original.getHeight() != compact.getHeight()
                    || original.getSTADepth() != compact.getSTADepth()
                    || original.getSTAOffset() != compact.getSTAOffset()
                    || Double.compare(original.getStixelWidth(), compact.getStixelWidth()) != 0
                    || Double.compare(original.getStixelHeight(), compact.getStixelHeight()) != 0
                    || Double.compare(original.getRefreshTime(), compact.getRefreshTime()) != 0) {
                throw new AssertionError("STA metadata differs");
            }

            int[] ids = original.getIDList();
            if (!Arrays.equals(ids, compact.getIDList())) {
                throw new AssertionError("STA cell ID lists differ");
            }

            double squaredError = 0;
            double maxError = 0;
            long valueCount = 0;
            boolean sourceMonochrome = true;

            for (int id : ids) {
                STA a = original.getSTA(id);
                STA b = compact.getSTA(id);
                if (a.getSTADepth() != b.getSTADepth()) {
                    throw new AssertionError("STA depth differs for cell " + id);
                }

                float cellMaxAbs = 0;
                double cellMaxError = 0;
                for (int frame = 0; frame < a.getSTADepth(); frame++) {
                    STAFrame af = a.getSTAFrame(frame);
                    STAFrame bf = b.getSTAFrame(frame);
                    float[] av = af.getBuffer();
                    float[] bv = bf.getBuffer();
                    float[] be = bf.getErrorBuffer();
                    if (av.length != bv.length || bv.length != be.length) {
                        throw new AssertionError("STA frame dimensions differ for cell " + id);
                    }

                    for (int pixel = 0; pixel < av.length; pixel += 3) {
                        if (av[pixel] != av[pixel + 1] || av[pixel] != av[pixel + 2]) {
                            sourceMonochrome = false;
                        }
                    }

                    for (int i = 0; i < av.length; i++) {
                        if (!Float.isFinite(av[i]) || !Float.isFinite(bv[i])) {
                            throw new AssertionError("Non-finite STA value for cell " + id);
                        }
                        if (be[i] != 0) {
                            throw new AssertionError("Compact STA error is nonzero for cell " + id);
                        }

                        cellMaxAbs = Math.max(cellMaxAbs, Math.abs(av[i]));
                        double error = bv[i] - av[i];
                        double absoluteError = Math.abs(error);
                        squaredError += error * error;
                        maxError = Math.max(maxError, absoluteError);
                        cellMaxError = Math.max(cellMaxError, absoluteError);
                        valueCount++;
                    }
                }

                float scale = cellMaxAbs == 0 ? 0 : cellMaxAbs / 32767.0f;
                double allowedError = Math.abs(scale) * 0.5001
                        + Math.max(1e-12, Math.ulp(cellMaxAbs) * 2.0);
                if (cellMaxError > allowedError) {
                    throw new AssertionError("STA quantization error exceeds the int16 bound for cell "
                            + id + ": " + cellMaxError + " > " + allowedError);
                }
            }

            if (compact.isMonochrome() && !sourceMonochrome) {
                throw new AssertionError("Monochrome compact STA has non-monochrome source values");
            }

            return new STAResult(
                    ids, compact.getVersion(), compact.isMonochrome(), sourceMonochrome,
                    valueCount, Math.sqrt(squaredError / valueCount), maxError);
        } finally {
            compact.close();
            original.close();
        }
    }


    private static void validateEI(File originalPath, File compactPath, int[] expectedIDs)
            throws Exception {
        PhysiologicalImagingFile original = new PhysiologicalImagingFile(
                originalPath.getPath(), true);
        PhysiologicalImagingFile compact = new PhysiologicalImagingFile(
                compactPath.getPath(), true);
        try {
            if (original.getFormat() != PhysiologicalImagingFile.LEGACY_FORMAT) {
                throw new AssertionError("Expected a legacy EI source");
            }
            if (compact.getFormat() != PhysiologicalImagingFile.FLOAT32_NO_ERROR_FORMAT) {
                throw new AssertionError("Expected compact values-only EI format");
            }
            if (original.nlPoints != compact.nlPoints
                    || original.nrPoints != compact.nrPoints
                    || original.arrayID != compact.arrayID) {
                throw new AssertionError("EI metadata differs");
            }
            if (!Arrays.equals(expectedIDs, compact.getIDList())) {
                throw new AssertionError("Compact EI IDs differ from the expected ID order");
            }

            long valueCount = 0;
            for (int id : expectedIDs) {
                if (original.getIndex(id) == -1) {
                    throw new AssertionError("Original EI lacks STA cell ID " + id);
                }
                if (original.getNSpikes(id) != compact.getNSpikes(id)) {
                    throw new AssertionError("EI spike count differs for cell " + id);
                }

                float[][][] a = original.getImage(id);
                float[][][] b = compact.getImage(id);
                if (a[0].length != b[0].length) {
                    throw new AssertionError("EI electrode count differs for cell " + id);
                }
                for (int electrode = 0; electrode < a[0].length; electrode++) {
                    if (a[0][electrode].length != b[0][electrode].length) {
                        throw new AssertionError("EI sample count differs for cell " + id);
                    }
                    for (int sample = 0; sample < a[0][electrode].length; sample++) {
                        if (Float.floatToIntBits(a[0][electrode][sample])
                                != Float.floatToIntBits(b[0][electrode][sample])) {
                            throw new AssertionError("EI value differs for cell " + id
                                    + ", electrode " + electrode + ", sample " + sample);
                        }
                        if (b[1][electrode][sample] != 0) {
                            throw new AssertionError("Compact EI error is nonzero for cell " + id);
                        }
                        valueCount++;
                    }
                }
            }

            System.out.println("EI PASS: cells=" + expectedIDs.length
                    + ", values=" + valueCount
                    + ", valuesBitExact=true, errorsZero=true");
        } finally {
            compact.close();
            original.close();
        }
    }


    private static void printSTAResult(STAResult result) {
        System.out.println("STA PASS: cells=" + result.ids.length
                + ", values=" + result.valueCount
                + ", version=" + result.version
                + ", monochrome=" + result.monochrome
                + ", sourceMonochrome=" + result.sourceMonochrome
                + ", errorsZero=true"
                + ", RMSE=" + result.rmse
                + ", maxError=" + result.maxError);
    }


    private static void inspectSTA(File path) throws Exception {
        STAFile sta = new STAFile(path, true);
        try {
            System.out.println(path.getPath()
                    + "\tbytes=" + path.length()
                    + "\tversion=" + sta.getVersion()
                    + "\tmonochrome=" + sta.isMonochrome()
                    + "\tcells=" + sta.getIDList().length
                    + "\t" + sta.getWidth() + "x" + sta.getHeight()
                    + "x" + sta.getSTADepth());
        } finally {
            sta.close();
        }
    }


    private static void inspectEI(File path) throws Exception {
        PhysiologicalImagingFile ei = new PhysiologicalImagingFile(path.getPath(), true);
        try {
            System.out.println(path.getPath()
                    + "\tbytes=" + path.length()
                    + "\tformat=" + ei.getFormat()
                    + "\thasErrors=" + ei.hasErrors()
                    + "\tcells=" + ei.getIDList().length
                    + "\tarray=" + ei.arrayID
                    + "\tpoints=" + ei.nlPoints + "+" + ei.nrPoints + "+1");
        } finally {
            ei.close();
        }
    }


    private static final class STAResult {
        final int[] ids;
        final int version;
        final boolean monochrome;
        final boolean sourceMonochrome;
        final long valueCount;
        final double rmse;
        final double maxError;

        STAResult(int[] ids, int version, boolean monochrome, boolean sourceMonochrome,
                long valueCount, double rmse, double maxError) {
            this.ids = ids;
            this.version = version;
            this.monochrome = monochrome;
            this.sourceMonochrome = sourceMonochrome;
            this.valueCount = valueCount;
            this.rmse = rmse;
            this.maxError = maxError;
        }
    }
}
