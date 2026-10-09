# Shared settings for the Bash entry points. This file does not modify Vision.
converter_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
converter_vision_jar="${VISION_JAR:-}"
converter_overlay="$converter_root/lib/sta-ei-converter.jar"

converter_require_vision() {
    if [[ -z "$converter_vision_jar" || ! -f "$converter_vision_jar" ]]; then
        echo 'Set VISION_JAR or pass --vision-jar /path/to/Vision.jar.' >&2
        return 2
    fi
}

converter_require_java() {
    if [[ -n "${CONVERTER_JAVA:-}" ]]; then
        converter_java="$CONVERTER_JAVA"
    elif [[ -n "${JAVA_HOME:-}" ]]; then
        converter_java="$JAVA_HOME/bin/java"
    else
        converter_java="$(command -v java || true)"
    fi
    if [[ -z "$converter_java" || ! -x "$converter_java" ]]; then
        echo 'Java was not found. Set JAVA_HOME or CONVERTER_JAVA.' >&2
        return 2
    fi
}

converter_require_jdk() {
    converter_require_java
    if [[ -n "${CONVERTER_JAVAC:-}" ]]; then
        converter_javac="$CONVERTER_JAVAC"
    elif [[ -n "${JAVA_HOME:-}" ]]; then
        converter_javac="$JAVA_HOME/bin/javac"
    else
        converter_javac="$(command -v javac || true)"
    fi
    if [[ -n "${CONVERTER_JAR:-}" ]]; then
        converter_jar="$CONVERTER_JAR"
    elif [[ -n "${JAVA_HOME:-}" ]]; then
        converter_jar="$JAVA_HOME/bin/jar"
    else
        converter_jar="$(command -v jar || true)"
    fi
    if [[ -z "$converter_javac" || ! -x "$converter_javac" || -z "$converter_jar" || ! -x "$converter_jar" ]]; then
        echo 'Building/testing requires a JDK with javac and jar (Java 8 or later).' >&2
        return 2
    fi
}
