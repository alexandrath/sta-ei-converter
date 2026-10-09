#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/scripts/common.sh"
if [[ "${1:-}" == --vision-jar && $# -eq 2 ]]; then
    converter_vision_jar="$2"
elif [[ $# -ne 0 ]]; then
    echo 'Usage: ./test.sh [--vision-jar /path/to/Vision.jar]' >&2
    exit 2
fi
converter_require_vision
converter_require_jdk
converter_test=$(mktemp -d "${TMPDIR:-/tmp}/sta-ei-test.XXXXXX")
trap 'rm -rf "$converter_test"' EXIT
converter_cp="$converter_overlay:$converter_vision_jar"
"$converter_javac" -source 8 -target 8 -Xlint:-options -cp "$converter_cp" \
    -d "$converter_test/classes" "$converter_root/tests/ConverterTest.java"
"$converter_java" -Djava.awt.headless=true -Xmx128m -cp "$converter_test/classes:$converter_cp" \
    ConverterTest "$converter_test/fixture with spaces"

# Exercise the actual launcher, with spaces in paths and reversed argument order.
"$converter_root/convert.sh" --vision-jar "$converter_vision_jar" \
    "$converter_test/fixture with spaces/launcher.ei" \
    "$converter_test/fixture with spaces/launcher.sta"
if "$converter_root/convert.sh" --vision-jar "$converter_vision_jar" \
    "$converter_test/fixture with spaces/launcher.sta" > "$converter_test/refusal.log" 2>&1; then
    echo 'FAIL: launcher overwrote an existing output.' >&2
    exit 1
fi
echo 'ALL TESTS PASS (synthetic files only).'
