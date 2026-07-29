#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="$ROOT_DIR/out"
LIB_DIR="$ROOT_DIR/lib"
MAIN_CLASS="ui.SushiBattleGUI"

if ! command -v java >/dev/null 2>&1; then
  echo "Missing Java runtime: java" >&2
  echo "Install a JDK or JRE and make sure java is on PATH." >&2
  exit 1
fi

"$ROOT_DIR/build.sh"

CLASSPATH="$OUT_DIR"
if compgen -G "$LIB_DIR/*.jar" >/dev/null; then
  for jar in "$LIB_DIR"/*.jar; do
    CLASSPATH="$CLASSPATH:$jar"
  done
fi

if ! compgen -G "$LIB_DIR/*.jar" >/dev/null; then
  echo "Warning: $LIB_DIR/*.jar not found. MP3 audio requires lib/jlayer-1.0.1.jar" >&2
fi

java -classpath "$CLASSPATH" "$MAIN_CLASS"
