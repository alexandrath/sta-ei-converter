# Test evidence

Test date: 2026-10-09. Host: macOS. Linux uses the same Bash/Java entry points,
but no Linux host was available for this test.

## Downloaded collaborator example

The original `data014` inputs contain 592 STA cells and 592 EI cells. Their
ID order differs; each original order is preserved independently.

| File | Original bytes | Compact bytes | Existing converter elapsed, including JVM startup |
|---|---:|---:|---:|
| STA | 2,873,629,732 | 718,562,660 | 6.86 s |
| EI | 148,208,396 | 74,106,580 | 0.74 s |
| Total | 3,021,838,128 | 792,669,240 | 7.60 s |

The requested `_short` files were first generated using the established compact
Vision JAR under Java 10. The shareable package was then run against the same
inputs using an **ordinary Vision Working JAR** and Java 11, writing temporary
local test outputs. The two package outputs matched the established converter's
outputs **byte-for-byte**. Package conversion took 4.85 s including both JVM
startups; the input had already been read, so caching may improve this timing.

Separate full numerical comparison, using the package with the ordinary Vision
JAR, passed:

- STA: 359,178,240 mean values; all within the per-cell int16 error bound;
  RMSE `8.813194149216977e-6`, maximum absolute error `1.5288591384887695e-5`;
  original cell IDs/order, dimensions and active calibration preserved;
  compact errors zero.
- EI: 18,525,456 mean values compared bit-for-bit; original IDs/order, array
  metadata and spike counts preserved; compact errors zero.

The separate verification script took 5.74 s, including compilation and JVM
startup. This is not included in the conversion timings above.

Original files and companion inode/size/modification-time metadata were
unchanged. No example data is included in this repository.

## Synthetic tests

`test.sh` uses a 128 MB JVM heap for its Java fixture tests, then exercises the
actual launcher. Tests cover:

- Independently written legacy input and golden compact STA bytes, including
  positive/negative rounding boundaries, RGB channels and a zero-valued cell.
- Every raw EI mean bit (including negative zero), spike counts and all IDs,
  including an EI-only ID and an order different from the STA's order.
- Original input preservation and existing-output refusal.
- Truncated EI, duplicate EI IDs, and non-finite STA failures with cleanup.
- Launcher conversion of both file types, reversed argument order and spaces
  in paths.

The prebuilt JAR targets Java 8 bytecode. It was built and tested against an
ordinary Vision JAR; the tests also run with the compact Vision JAR. Specific
older/different Vision releases may need dependency coordination.
