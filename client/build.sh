#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$ROOT_DIR/src"
OUT_DIR="$ROOT_DIR/out"
LIB_DIR="$ROOT_DIR/lib"

if ! command -v javac >/dev/null 2>&1; then
  echo "Missing Java compiler: javac" >&2
  echo "Install a JDK and make sure javac is on PATH." >&2
  exit 1
fi

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

CLASSPATH="$OUT_DIR"
if compgen -G "$LIB_DIR/*.jar" >/dev/null; then
  for jar in "$LIB_DIR"/*.jar; do
    CLASSPATH="$CLASSPATH:$jar"
  done
fi

javac -encoding UTF-8 -classpath "$CLASSPATH" -d "$OUT_DIR" $(find "$SRC_DIR" -name "*.java" | sort)

echo "Compiled Java sources into $OUT_DIR"
