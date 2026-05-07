#!/usr/bin/env bash
# run-library.sh — macOS/Linux equivalent of Run-Library.ps1
#
# Usage:
#   chmod +x run-library.sh   # make executable once
#   ./run-library.sh              # defaults to --web
#   ./run-library.sh --tests
#   ./run-library.sh --smoke-test
#   ./run-library.sh --compile-only
#   ./run-library.sh --compile-only --tests

set -euo pipefail

# ---------------------------------------------------------------------------
# Parse flags
# ---------------------------------------------------------------------------
MODE_TESTS=false
MODE_WEB=false
MODE_SMOKE=false
COMPILE_ONLY=false

for arg in "$@"; do
    case "$arg" in
        --tests)        MODE_TESTS=true ;;
        --web)          MODE_WEB=true ;;
        --smoke-test)   MODE_SMOKE=true ;;
        --compile-only) COMPILE_ONLY=true ;;
        *)
            echo "Unknown option: $arg" >&2
            echo "Usage: $0 [--tests|--web|--smoke-test] [--compile-only]" >&2
            exit 1
            ;;
    esac
done

# Default to --web when no mode is given
if ! $MODE_TESTS && ! $MODE_WEB && ! $MODE_SMOKE; then
    echo "No mode provided. Defaulting to --web."
    MODE_WEB=true
fi

# Resolve script directory for default model path
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ---------------------------------------------------------------------------
# Runtime environment — mirrors Run-Library.ps1 so Phase 3 features
# 2.7 (LLM summary) and 3.9 (Google Books download) behave the same on macOS/Linux.
# ---------------------------------------------------------------------------
if [[ -z "${JAVA_HOME:-}" ]]; then
    if [[ -d "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home" ]]; then
        export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
    elif [[ -d "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" ]]; then
        export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
    elif [[ -d "/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" ]]; then
        export JAVA_HOME="/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
    fi
fi

export GOOGLE_BOOKS_API_KEY="AIzaSyBKMNFbGxR0Zj7ihJWsPqbj4SwCH0LprWk"
export GGUF_MODEL_PATH="$(cd "$SCRIPT_DIR" && pwd)/Meta-Llama-3.1-8B-Instruct-Q4_K_S.gguf"
LLAMA_JAR="$(cd "$SCRIPT_DIR/third-party/llama" && pwd)/llama-4.1.0.jar"
export LLAMA_JAR

if [[ ! -f "$GGUF_MODEL_PATH" ]]; then
    echo "ERROR: Meta-Llama model not found at: $GGUF_MODEL_PATH" >&2
    exit 1
fi

JAVAFX_LIB="/Library/Java/JavaVirtualMachines/javafx-sdk-21.0.10/lib"
JAVAFX_MODULES="javafx.controls,javafx.fxml"

echo "JAVA_HOME set to: ${JAVA_HOME:-}"
echo "Google Books API key set."
echo "GGUF model path (ABSOLUTE): $GGUF_MODEL_PATH"
echo "JavaFX path: $JAVAFX_LIB"
echo "Llama jar: $LLAMA_JAR"

# ---------------------------------------------------------------------------
# Resolve java / javac
# ---------------------------------------------------------------------------
resolve_tool() {
    local tool="$1"
    if [[ -n "${JAVA_HOME:-}" ]]; then
        local candidate="$JAVA_HOME/bin/$tool"
        if [[ -x "$candidate" ]]; then
            echo "$candidate"
            return
        fi
    fi
    if command -v "$tool" &>/dev/null; then
        echo "$tool"
        return
    fi
    echo "Cannot find $tool." >&2
    exit 1
}

JAVA=$(resolve_tool java)
JAVAC=$(resolve_tool javac)

echo "Using java:  $JAVA"
echo "Using javac: $JAVAC"

# ---------------------------------------------------------------------------
# Change to src/ (parent of Library/) as the working directory
# ---------------------------------------------------------------------------
SOURCE_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$SOURCE_ROOT"
echo "Working directory: $SOURCE_ROOT"

TEST_SOURCES=""
FX_SOURCES=""
cleanup_temp_files() {
    rm -f "${TEST_SOURCES:-}" "${FX_SOURCES:-}"
}
trap cleanup_temp_files EXIT

# ---------------------------------------------------------------------------
# Helper: collect .java files from a list of directories into a temp file.
# Uses mktemp so we never overwrite any version-controlled sources-*.txt file.
# ---------------------------------------------------------------------------
make_source_list() {
    local tmp_file
    tmp_file="$(mktemp)"
    # Remaining args are directories to search
    find "$@" -name "*.java" | sort > "$tmp_file"
    echo "$tmp_file"
}

# ---------------------------------------------------------------------------
# Tests mode
# ---------------------------------------------------------------------------
if $MODE_TESTS; then
    TEST_OUTPUT="$SOURCE_ROOT/out-tests"

    echo "Preparing test source list..."
    TEST_SOURCES="$(make_source_list \
        Library/Exception \
        Library/Model \
        Library/Repository \
        Library/Security \
        Library/Service \
        Library/Test)"

    mkdir -p "$TEST_OUTPUT"

    echo "Compiling integration tests..."
    "$JAVAC" -encoding UTF-8 -cp "$LLAMA_JAR" -d "$TEST_OUTPUT" @"$TEST_SOURCES"

    if ! $COMPILE_ONLY; then
        echo "Running integration tests..."
        "$JAVA" -cp "$TEST_OUTPUT:$LLAMA_JAR" Library.Test.LibraryIntegrationTest
    fi
fi

# ---------------------------------------------------------------------------
# Web / smoke-test mode
# ---------------------------------------------------------------------------
if $MODE_WEB || $MODE_SMOKE; then
    FX_OUTPUT="$SOURCE_ROOT/out-fx"

    echo "Preparing full source list..."
    FX_SOURCES="$(make_source_list Library)"

    mkdir -p "$FX_OUTPUT"

    echo "Compiling application (web UI + services)..."
    "$JAVAC" -encoding UTF-8 -cp "$LLAMA_JAR" -d "$FX_OUTPUT" @"$FX_SOURCES"

    if ! $COMPILE_ONLY; then
        if $MODE_SMOKE; then
            echo "Running web UI smoke test..."
            "$JAVA" -cp "$FX_OUTPUT:$LLAMA_JAR" Library.Ui.LibraryManagementApp --smoke-test
        else
            echo "Starting web UI on http://localhost:8080 ..."
            "$JAVA" -cp "$FX_OUTPUT:$LLAMA_JAR" Library.Ui.LibraryManagementApp
        fi
    fi
fi

echo "Done."
