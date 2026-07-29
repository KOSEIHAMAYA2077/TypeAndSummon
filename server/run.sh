#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="$ROOT_DIR/out"
LIB_DIR="$ROOT_DIR/lib"
SQLITE_JDBC_JAR="$LIB_DIR/sqlite-jdbc.jar"

"$ROOT_DIR/prepare_data.sh"
"$ROOT_DIR/build.sh"

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    CP_SEP=";"
    JAVA_OUT_DIR="$(cygpath -w "$OUT_DIR")"
    JAVA_SQLITE_JDBC_JAR="$(cygpath -w "$SQLITE_JDBC_JAR")"
    ;;
  *)
    CP_SEP=":"
    JAVA_OUT_DIR="$OUT_DIR"
    JAVA_SQLITE_JDBC_JAR="$SQLITE_JDBC_JAR"
    ;;
esac

java -cp "$JAVA_OUT_DIR$CP_SEP$JAVA_SQLITE_JDBC_JAR" Main
