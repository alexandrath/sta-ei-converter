# Source provenance

Version: 0.1.0 (2026-10-09).

The five classes under `src/edu/ucsc/neurobiology/vision/` were extracted from
the local `compact-ei-sta` Vision fork at commit
`b9ae2a593a0ffb94849e54f1a88bbf3dad9ffcf2`:

- `io/CompactFileConverter.java`
- `io/STAFile.java`
- `io/PhysiologicalImagingFile.java`
- `stimulus/STA.java`
- `stimulus/STAFrame.java`

Original author documentation is retained in these classes. The encoding
implementation is the existing Vision Compact implementation, including 64-bit
EI offsets. This package adds structural guards to `CompactFileConverter` and
the Bash launcher; it does not implement a different quantization algorithm.

The included JAR contains only these converter/format classes. It is placed
first on the Java classpath; the user's ordinary Vision JAR supplies the other
Vision classes. No complete Vision JAR, native library, GUI application,
experimental data, credentials, or machine-specific configuration is bundled.
This does not modify the user's Vision JAR or app.

`tests/CompactFileCheck.java` is derived from the fork's read-only validator.
An additional EI-only mode compares against the original EI's ID order,
independently of any STA ID order. `tests/ConverterTest.java` creates independent
raw-byte STA/EI fixtures and checks golden output bytes and failure behavior.

The initial prebuilt JAR was compiled with Corretto JDK 11 using Java 8 source
and bytecode targets, against an ordinary Vision JAR from the local Vision
Working installation (SHA-256 recorded in `SHA256SUMS` as a comment).
Running conversion requires a Java runtime; a JDK is only needed for rebuilding
or running the optional verification/tests.
