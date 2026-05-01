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

# ---------------------------------------------------------------------------
# Runtime environment — mirrors Run-Library.ps1 (Windows) so Phase 3 features
# 2.7 (LLM summary) and 3.9 (Google Books download) behave the same on macOS.
# Each variable is only set if not already exported, so a user can override
# any of these by exporting them before invoking this script.
# ---------------------------------------------------------------------------
: "${GOOGLE_BOOKS_API_KEY:=AIzaSyBKMNFbGxR0Zj7ihJWsPqbj4SwCH0LprWk}"
: "${INFERENCE_BASE_URL:=http://127.0.0.1:1234/v1}"
: "${INFERENCE_API_KEY:=}"
: "${INFERENCE_MODEL:=local-model}"
export GOOGLE_BOOKS_API_KEY INFERENCE_BASE_URL INFERENCE_API_KEY INFERENCE_MODEL

echo "Google Books API key: ${GOOGLE_BOOKS_API_KEY:+set}${GOOGLE_BOOKS_API_KEY:-(empty)}"
echo "Inference base URL:   $INFERENCE_BASE_URL"
echo "Inference model:      $INFERENCE_MODEL"

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
    echo "Cannot find $tool. Set JAVA_HOME or add it to PATH." >&2
    exit 1
}

JAVA=$(resolve_tool java)
JAVAC=$(resolve_tool javac)

echo "Using java:  $JAVA"
echo "Using javac: $JAVAC"

# ---------------------------------------------------------------------------
# Change to src/ (parent of Library/) as the working directory
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
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
    "$JAVAC" -encoding UTF-8 -d "$TEST_OUTPUT" @"$TEST_SOURCES"

    if ! $COMPILE_ONLY; then
        echo "Running integration tests..."
        "$JAVA" -cp "$TEST_OUTPUT" Library.Test.LibraryIntegrationTest
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
    "$JAVAC" -encoding UTF-8 -d "$FX_OUTPUT" @"$FX_SOURCES"

    if ! $COMPILE_ONLY; then
        if $MODE_SMOKE; then
            echo "Running web UI smoke test..."
            "$JAVA" -cp "$FX_OUTPUT" Library.Ui.LibraryManagementApp --smoke-test
        else
            echo "Starting web UI on http://localhost:8080 ..."
            "$JAVA" -cp "$FX_OUTPUT" Library.Ui.LibraryManagementApp
        fi
    fi
fi

echo "Done."
