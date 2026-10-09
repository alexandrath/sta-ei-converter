#!/usr/bin/env bash
# Optional full numerical comparison; convert.sh does not call this.
set -euo pipefail
source "$(dirname "$0")/scripts/common.sh"
if [[ "${1:-}" == --vision-jar ]]; then
    [[ $# -ge 2 ]] || { echo 'Missing Vision.jar path.' >&2; exit 2; }
    converter_vision_jar="$2"
    shift 2
fi
[[ $# -gt 0 ]] || { echo 'Usage: ./verify.sh [--vision-jar JAR] FILE.sta [FILE.ei ...]' >&2; exit 2; }
converter_require_vision
converter_require_jdk
converter_check=$(mktemp -d "${TMPDIR:-/tmp}/sta-ei-check.XXXXXX")
trap 'rm -rf "$converter_check"' EXIT
converter_cp="$converter_overlay:$converter_vision_jar"
"$converter_javac" -source 8 -target 8 -Xlint:-options -cp "$converter_cp" \
    -d "$converter_check" "$converter_root/tests/CompactFileCheck.java"
for converter_input in "$@"; do
    case "$converter_input" in
        *.sta) converter_mode=sta ;;
        *.ei) converter_mode=ei ;;
        *) echo "Expected a .sta or .ei input: $converter_input" >&2; exit 2 ;;
    esac
    "$converter_java" -Djava.awt.headless=true "-Xmx${CONVERTER_HEAP:-512m}" \
        -cp "$converter_check:$converter_cp" CompactFileCheck \
        "$converter_mode" "$converter_input" "${converter_input}_short"
done
