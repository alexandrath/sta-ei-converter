# STA/EI converter

Convert existing legacy Vision STA and EI files into smaller files for transfer.
This is a command-line package for macOS and Linux. It uses your existing
`Vision.jar`; it does not install or modify Vision, and does not open a GUI.

## Requirements

- Java 8 or later. Java 10 and 11 have been tested.
- Your existing `Vision.jar`, including its usual Java libraries. The ordinary
  Vision JAR is sufficient; a Vision Compact installation is not needed.
- For STA conversion, the corresponding `.globals` file beside the `.sta`.
  The globals file supplies the physical stixel calibration used by Vision.
- Enough free disk space for the output: approximately 25% of the original
  RGB STA size and 50% of the original EI size. Originals remain in place.

The compiled `lib/sta-ei-converter.jar` is included. You do **not** need `javac`,
Ant, MATLAB, Python, a GPU library, or the Vision GUI to convert files.

## Convert

Clone this repository or unpack its ZIP, then run from this folder:

```bash
chmod +x convert.sh build.sh test.sh verify.sh
./convert.sh --vision-jar '/path/to/Vision.jar' \
  '/path/to/data014.sta' '/path/to/data014.ei'
```

This creates, alongside the inputs:

```text
data014.sta_short
data014.ei_short
```

Each output name is the complete input filename with `_short` appended.
The `chmod` command above makes the launchers executable after a browser download.
The input files are opened read-only. Existing outputs are refused, including
an existing output for a later argument: path checks run before any conversion.
The corresponding `data014.globals` must remain beside `data014.sta`.
Other companions such as `.neurons`, `.params`, and classifications are not
needed for conversion and are not changed.

Either file can also be converted on its own:

```bash
./convert.sh --vision-jar '/path/to/Vision.jar' '/path/to/data014.ei'
```

Alternatively, configure the library path once:

```bash
export VISION_JAR='/path/to/Vision.jar'
./convert.sh '/path/to/data014.sta' '/path/to/data014.ei'
```

On macOS, the JAR may be inside the Vision app, for example
`/Applications/Vision.app/Contents/Resources/Java/Vision.jar`. Use the actual
path for your installation. Quote paths containing spaces.

Java is selected from `CONVERTER_JAVA`, then `$JAVA_HOME/bin/java`, then `java`
on your PATH. For example:

```bash
CONVERTER_JAVA='/path/to/jdk/bin/java' ./convert.sh \
  --vision-jar '/path/to/Vision.jar' '/path/to/data014.sta'
```

The default Java heap limit is 512 MB. Files are processed one cell at a time,
so a 100 GB file is not loaded into memory. A particularly large individual
STA may require a larger heap: set `CONVERTER_HEAP=1g` if needed.

## What is stored

| Input | Output | Typical output size |
|---|---|---:|
| Legacy STA v32: RGB float32 means and errors | STA v34: RGB int16 means, per-cell scale, no errors | 25% |
| Legacy EI: float32 means and errors | EIC1 v1: float32 means, no errors | 50% |

STA means are quantized using the existing Vision Compact encoding:
per-cell `scale = max(abs(mean)) / 32767`, followed by Java `Math.round` and
int16 clipping. All RGB channels are retained. EI mean values retain float32
precision, and spike counts are retained. Each file keeps its own complete cell
ID list and ID order; the EI is not filtered or reordered to match the STA.

Error matrices are omitted in both formats. These are transfer copies for
use with a compatible reader; an ordinary Vision installation need not open
them on the sending computer. Compact readers return zero errors. Keep the
originals for workflows that need the original error estimates.

The launcher uses RGB STA v34 for every STA, including monochrome inputs.
It does not silently choose the more compact monochrome format.

The converter reports elapsed time and output bytes. Routine conversion does
not perform a second full numerical comparison or checksum pass. Basic checks
reject already compact input, malformed lengths, duplicate IDs, non-finite STA
means, and inconsistent STA frame dimensions. Failed outputs are removed when
the process can clean up normally. If interrupted or killed, a partial `_short`
file can remain; inspect/remove that exact partial output before retrying.
If a later file fails, an earlier successful output remains available.

## Optional checks and development

Numerical comparison is separate from conversion and requires a JDK:

```bash
./verify.sh --vision-jar '/path/to/Vision.jar' \
  '/path/to/data014.sta' '/path/to/data014.ei'
```

For a transfer checksum, run `shasum -a 256 data014.sta_short data014.ei_short`
on macOS or `sha256sum data014.sta_short data014.ei_short` on Linux. Compare
with the checksums after downloading; checksum generation reads the outputs.

Rebuild the included JAR or run the synthetic integration tests with a JDK:

```bash
./build.sh --vision-jar '/path/to/Vision.jar'
./test.sh --vision-jar '/path/to/Vision.jar'
```

You can set `CONVERTER_JAVAC` and `CONVERTER_JAR` for explicit compiler and
archive-tool paths. The scripts also respect `JAVA_HOME`.

See [test results](docs/TESTED.md) and [source provenance](docs/PROVENANCE.md).
