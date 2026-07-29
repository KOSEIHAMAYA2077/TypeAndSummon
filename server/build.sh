#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="$ROOT_DIR/out"

mkdir -p "$OUT_DIR"

javac -encoding UTF-8 -d "$OUT_DIR" $(find "$ROOT_DIR/src" -name "*.java")

echo "Compiled Java sources into $OUT_DIR"
