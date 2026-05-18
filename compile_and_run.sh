#!/usr/bin/env bash
# compile_and_run.sh
# Compiles all Java source files in src/ and launches PhysicsDemo.
# Usage: ./compile_and_run.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="${SCRIPT_DIR}/src"
OUT_DIR="${SCRIPT_DIR}/out"

echo "=== Physics Demo Build Script ==="
echo "Source  : ${SRC_DIR}"
echo "Classes : ${OUT_DIR}"
echo ""

# Create output directory if it doesn't exist
mkdir -p "${OUT_DIR}"

# Compile all .java files
echo "[1/2] Compiling Java sources..."
javac -d "${OUT_DIR}" \
      -source 8 \
      -target 8 \
      -Xlint:none \
      "${SRC_DIR}/Vector2D.java" \
      "${SRC_DIR}/Body.java" \
      "${SRC_DIR}/PhysicsDemo.java"

echo "      Compilation successful."
echo ""

# Run
echo "[2/2] Launching PhysicsDemo..."
java -cp "${OUT_DIR}" \
     -Dsun.java2d.opengl=true \
     -Dawt.useSystemAAFontSettings=on \
     -Dswing.aatext=true \
     PhysicsDemo
