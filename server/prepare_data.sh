#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
DATA_DIR="$ROOT_DIR/data"
LIB_DIR="$ROOT_DIR/lib"
WORD_ARCHIVE_PATH="$DATA_DIR/english-valid-words.db.gz"
WORD_DB_PATH="$DATA_DIR/english-valid-words.db"
SQLITE_JDBC_JAR="$LIB_DIR/sqlite-jdbc.jar"
TOOLS_OUT="$ROOT_DIR/out-tools"
ASSIGN_LEVELS_SOURCE="$ROOT_DIR/scripts/AssignWordLevels.java"

mkdir -p "$DATA_DIR"


if [[ ! -f "$WORD_DB_PATH" ]]; then
  if [[ ! -f "$WORD_ARCHIVE_PATH" ]]; then
    echo "Missing archive: $WORD_ARCHIVE_PATH" >&2
    exit 1
  fi
  echo "Extracting $(basename "$WORD_ARCHIVE_PATH")"
  gzip -dc "$WORD_ARCHIVE_PATH" > "$WORD_DB_PATH"
fi

if [[ ! -f "$SQLITE_JDBC_JAR" ]]; then
  echo "Missing SQLite JDBC jar: $SQLITE_JDBC_JAR" >&2
  exit 1
fi

if ! command -v javac >/dev/null 2>&1; then
  echo "Missing Java compiler: javac" >&2
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Missing Java runtime: java" >&2
  exit 1
fi

mkdir -p "$TOOLS_OUT"

case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    CP_SEP=";"
    JAVA_TOOLS_OUT="$(cygpath -w "$TOOLS_OUT")"
    JAVA_SQLITE_JDBC_JAR="$(cygpath -w "$SQLITE_JDBC_JAR")"
    JAVA_ASSIGN_LEVELS_SOURCE="$(cygpath -w "$ASSIGN_LEVELS_SOURCE")"
    JAVA_WORD_DB_PATH="$(cygpath -w "$WORD_DB_PATH")"
    ;;
  *)
    CP_SEP=":"
    JAVA_TOOLS_OUT="$TOOLS_OUT"
    JAVA_SQLITE_JDBC_JAR="$SQLITE_JDBC_JAR"
    JAVA_ASSIGN_LEVELS_SOURCE="$ASSIGN_LEVELS_SOURCE"
    JAVA_WORD_DB_PATH="$WORD_DB_PATH"
    ;;
esac

javac -encoding UTF-8 -d "$JAVA_TOOLS_OUT" "$JAVA_ASSIGN_LEVELS_SOURCE"
java -cp "$JAVA_TOOLS_OUT$CP_SEP$JAVA_SQLITE_JDBC_JAR" AssignWordLevels "$JAVA_WORD_DB_PATH"
