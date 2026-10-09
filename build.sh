#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/scripts/common.sh"
if [[ "${1:-}" == --vision-jar && $# -eq 2 ]]; then
    converter_vision_jar="$2"
elif [[ $# -ne 0 ]]; then
    echo 'Usage: ./build.sh [--vision-jar /path/to/Vision.jar]' >&2
    exit 2
fi
converter_require_vision
converter_require_jdk
converter_build=$(mktemp -d "${TMPDIR:-/tmp}/sta-ei-build.XXXXXX")
trap 'rm -rf "$converter_build"' EXIT
mkdir -p "$converter_build/classes" "$converter_root/lib"
"$converter_javac" -source 8 -target 8 -Xlint:-options \
    -cp "$converter_vision_jar" -d "$converter_build/classes" \
    "$converter_root"/src/edu/ucsc/neurobiology/vision/io/*.java \
    "$converter_root"/src/edu/ucsc/neurobiology/vision/stimulus/*.java
"$converter_jar" cf "$converter_build/sta-ei-converter.jar" -C "$converter_build/classes" .
mv "$converter_build/sta-ei-converter.jar" "$converter_overlay"
echo "Built $converter_overlay (Java 8 bytecode)."
