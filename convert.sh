#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/scripts/common.sh"

usage() {
    cat <<'EOF'
Usage: ./convert.sh [--vision-jar /path/to/Vision.jar] FILE.sta [FILE.ei ...]
       ./convert.sh [--vision-jar /path/to/Vision.jar] FILE.ei

Each input gets a new file named INPUT_short in the same folder.
STA: RGB int16 v34, errors omitted. EI: float32 EIC1, errors omitted.
Originals and cell ID order are preserved; existing outputs are refused.
STA inputs need their corresponding .globals file beside them.
Requires Java 8+ and an existing Vision.jar. No compilation is needed.
EOF
}

if [[ "${1:-}" == --help || "${1:-}" == -h ]]; then
    usage
    exit 0
fi
if [[ "${1:-}" == --vision-jar ]]; then
    [[ $# -ge 2 ]] || { usage >&2; exit 2; }
    converter_vision_jar="$2"
    shift 2
fi
[[ $# -gt 0 ]] || { usage >&2; exit 2; }
converter_require_vision
converter_require_java
[[ -f "$converter_overlay" ]] || { echo 'Missing lib/sta-ei-converter.jar; run build.sh.' >&2; exit 2; }

# Check every requested path before starting any conversion.
converter_inputs=()
for converter_input in "$@"; do
    [[ -f "$converter_input" ]] || { echo "Input is not a file: $converter_input" >&2; exit 2; }
    converter_input="$(cd "$(dirname "$converter_input")" && pwd)/$(basename "$converter_input")"
    case "$converter_input" in
        *.sta)
            [[ -f "${converter_input%.sta}.globals" ]] || {
                echo "STA needs its globals file: ${converter_input%.sta}.globals" >&2
                exit 2
            }
            ;;
        *.ei) ;;
        *) echo "Expected a .sta or .ei input: $converter_input" >&2; exit 2 ;;
    esac
    converter_output="${converter_input}_short"
    if [[ -e "$converter_output" || -L "$converter_output" ]]; then
        echo "Output already exists; refusing to overwrite: $converter_output" >&2
        exit 2
    fi
    for converter_previous in "${converter_inputs[@]+${converter_inputs[@]}}"; do
        [[ "$converter_previous" != "$converter_input" ]] || {
            echo "Input listed twice: $converter_input" >&2
            exit 2
        }
    done
    converter_inputs+=("$converter_input")
done

converter_start=$SECONDS
for converter_input in "${converter_inputs[@]}"; do
    case "$converter_input" in
        *.sta) converter_mode=sta-int16 ;;
        *.ei) converter_mode=ei-no-errors ;;
    esac
    echo "Converting: $converter_input"
    "$converter_java" -Djava.awt.headless=true "-Xmx${CONVERTER_HEAP:-512m}" \
        -cp "$converter_overlay:$converter_vision_jar" \
        edu.ucsc.neurobiology.vision.io.CompactFileConverter \
        "$converter_mode" "$converter_input" "${converter_input}_short"
    echo "Output: ${converter_input}_short"
done
echo "Total elapsed (including Java startup): $((SECONDS - converter_start)) seconds."
