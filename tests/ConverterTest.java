import edu.ucsc.neurobiology.vision.io.CompactFileConverter;
import edu.ucsc.neurobiology.vision.io.chunk.ChunkFile;
import edu.ucsc.neurobiology.vision.io.chunk.GlobalsFile;

import java.io.*;
import java.nio.file.*;
import java.util.Arrays;

/** Independent raw-byte fixtures: no converter reader/writer builds the STA/EI inputs. */
public final class ConverterTest {
    private static final float[][] VALUES = {
        {-32767f, 32767f, 0f, 0.49f, 0.5f, 1.5f},
        {-1.5f, -2.5f, -0.5f, 2.49f, 2.5f, -32767f}
    };
    private static final short[][] QUANTIZED = {
        {-32767, 32767, 0, 0, 1, 2}, {-1, -2, 0, 2, 3, -32767}
    };
    private static final int[] EI_IDS = {7, 99, 42}; // Extra EI-only ID, different STA order.

    public static void main(String[] args) throws Exception {
        Path dir = Paths.get(args[0]);
        Files.createDirectories(dir);
        Path sta = dir.resolve("test.sta"), ei = dir.resolve("test.ei");
        globals(dir.resolve("test.globals"));
        writeSTA(sta, false);
        writeEI(ei, false);
        byte[] staBefore = Files.readAllBytes(sta), eiBefore = Files.readAllBytes(ei);
        convert("sta-int16", sta);
        convert("ei-no-errors", ei);
        checkSTA(Paths.get(sta + "_short"));
        checkEI(ei, Paths.get(ei + "_short"));
        require(Arrays.equals(staBefore, Files.readAllBytes(sta)), "STA source changed");
        require(Arrays.equals(eiBefore, Files.readAllBytes(ei)), "EI source changed");

        byte[] compactBefore = Files.readAllBytes(Paths.get(sta + "_short"));
        expectFailure("sta-int16", sta);
        require(Arrays.equals(compactBefore, Files.readAllBytes(Paths.get(sta + "_short"))),
                "Existing compact output changed");

        Path truncated = dir.resolve("truncated.ei");
        Files.copy(ei, truncated);
        try (RandomAccessFile f = new RandomAccessFile(truncated.toFile(), "rw")) {
            f.setLength(f.length() - 1);
        }
        expectFailure("ei-no-errors", truncated);
        require(!Files.exists(Paths.get(truncated + "_short")), "Partial EI output survived failure");

        Path duplicate = dir.resolve("duplicate.ei");
        writeEI(duplicate, true);
        expectFailure("ei-no-errors", duplicate);
        require(!Files.exists(Paths.get(duplicate + "_short")), "Duplicate-ID EI output survived failure");

        Path nonfinite = dir.resolve("nonfinite.sta");
        globals(dir.resolve("nonfinite.globals"));
        writeSTA(nonfinite, true);
        expectFailure("sta-int16", nonfinite);
        require(!Files.exists(Paths.get(nonfinite + "_short")), "Non-finite STA output survived failure");

        globals(dir.resolve("launcher.globals"));
        writeSTA(dir.resolve("launcher.sta"), false);
        writeEI(dir.resolve("launcher.ei"), false);
        System.out.println("PASS: golden STA bytes, EI raw bits/IDs/spike counts, original preservation, refusal and failure cleanup.");
    }

    private static void globals(Path p) throws IOException {
        GlobalsFile g = new GlobalsFile(p.toString(), ChunkFile.WRITE);
        try {
            g.setRunTimeMovieParams(1, 1, 2, 1, 10, 10, 0, 0, 1, 60, 100, 1000.0 / 60, 2, new int[0]);
        } finally {
            g.close();
        }
    }

    private static void writeSTA(Path p, boolean nonfinite) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(p.toFile()))) {
            out.writeInt(32); out.writeInt(4); out.writeInt(2); out.writeInt(1); out.writeInt(2);
            out.writeDouble(10); out.writeDouble(1.0 / 60); out.writeInt(-1); out.write(new byte[124]);
            out.writeInt(42); out.writeLong(212);
            out.writeInt(7); out.writeLong(352);
            for (int i = 0; i < 2; i++) { out.writeInt(Integer.MIN_VALUE); out.writeLong(0); }
            for (int cell = 0; cell < 2; cell++) {
                out.writeDouble(1.0 / 60); out.writeInt(2);
                for (int frame = 0; frame < 2; frame++) {
                    out.writeInt(2); out.writeInt(1); out.writeDouble(10);
                    for (int value = 0; value < 6; value++) {
                        float v = cell == 0 ? VALUES[frame][value] : 0;
                        if (nonfinite && cell == 0 && frame == 0 && value == 0) v = Float.NaN;
                        out.writeFloat(v); out.writeFloat(0.125f);
                    }
                }
            }
        }
    }

    private static void writeEI(Path p, boolean duplicate) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(p.toFile()))) {
            out.writeInt(1); out.writeInt(1); out.writeInt(504);
            for (int cell = 0; cell < 3; cell++) {
                out.writeInt(duplicate && cell == 2 ? EI_IDS[0] : EI_IDS[cell]);
                out.writeInt(100 + cell);
                for (int electrode = 0; electrode < 513; electrode++) {
                    for (int sample = 0; sample < 3; sample++) {
                        float value = (cell + 1) * (electrode % 3 - 1) * (sample + 0.25f);
                        if (electrode == 0 && sample == 0) value = -0.0f;
                        out.writeFloat(value); out.writeFloat(0.25f);
                    }
                }
            }
        }
    }

    private static void checkSTA(Path p) throws IOException {
        require(Files.size(p) == 356, "Compact STA size");
        try (RandomAccessFile in = new RandomAccessFile(p.toFile(), "r")) {
            require(in.readInt() == 34, "STA version");
            require(in.readInt() == 4 && in.readInt() == 2 && in.readInt() == 1 && in.readInt() == 2, "STA header");
            in.seek(164);
            require(in.readInt() == 42 && in.readLong() == 212, "First STA ID/offset");
            require(in.readInt() == 7 && in.readLong() == 284, "Second STA ID/offset");
            in.seek(212);
            for (int cell = 0; cell < 2; cell++) {
                require(in.readDouble() == 1.0 / 60 && in.readInt() == 2, "STA refresh/depth");
                require(in.readFloat() == (cell == 0 ? 1f : 0f), "Per-cell STA scale");
                for (int frame = 0; frame < 2; frame++) {
                    require(in.readInt() == 2 && in.readInt() == 1 && in.readDouble() == 10, "STA frame metadata");
                    for (int value = 0; value < 6; value++) {
                        require(in.readShort() == (cell == 0 ? QUANTIZED[frame][value] : 0), "Golden int16 sample");
                    }
                }
            }
            require(in.getFilePointer() == in.length(), "STA trailing bytes");
        }
    }

    private static void checkEI(Path original, Path compact) throws IOException {
        try (DataInputStream a = new DataInputStream(new FileInputStream(original.toFile()));
             DataInputStream b = new DataInputStream(new FileInputStream(compact.toFile()))) {
            require(b.readInt() == 0x45494331 && b.readInt() == 1, "EIC1 header");
            for (int i = 0; i < 3; i++) require(a.readInt() == b.readInt(), "EI header metadata");
            for (int cell = 0; cell < 3; cell++) {
                require(a.readInt() == b.readInt(), "EI ID order");
                require(a.readInt() == b.readInt(), "EI spike counts");
                for (int i = 0; i < 513 * 3; i++) {
                    require(a.readInt() == b.readInt(), "EI raw float32 bits");
                    a.readInt(); // Original errors are intentionally omitted.
                }
            }
            require(a.read() == -1 && b.read() == -1, "EI trailing bytes");
        }
    }

    private static void convert(String mode, Path p) throws Exception {
        CompactFileConverter.main(new String[]{mode, p.toString(), p + "_short"});
    }

    private static void expectFailure(String mode, Path p) throws Exception {
        try {
            convert(mode, p);
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError("Expected conversion to fail for " + p);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
