#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APP_NAME="NextChess"
ARTIFACT_PREFIX="next-chess-desktop-java-"
MAIN_CLASS="com.github.keithwegner.chess.Main"
DEST_DIR="${2:-$ROOT_DIR/dist/jpackage}"
PACKAGE_TYPE="${1:-app-image}"

cd "$ROOT_DIR"

echo "Building application JAR..."
mvn -B -DskipTests package

JAR_PATH="$(find "$ROOT_DIR/target" -maxdepth 1 -type f -name "${ARTIFACT_PREFIX}*.jar" | sort | head -n 1)"
if [ -z "$JAR_PATH" ]; then
  echo "Could not find packaged application JAR under target/." >&2
  exit 1
fi

MAIN_JAR="$(basename "$JAR_PATH")"
APP_VERSION="${MAIN_JAR#${ARTIFACT_PREFIX}}"
APP_VERSION="${APP_VERSION%.jar}"

rm -rf "$DEST_DIR"
mkdir -p "$DEST_DIR"

JPACKAGE_ARGS=(
  --type "$PACKAGE_TYPE"
  --input "$ROOT_DIR/target"
  --dest "$DEST_DIR"
  --name "$APP_NAME"
  --main-jar "$MAIN_JAR"
  --main-class "$MAIN_CLASS"
  --app-version "$APP_VERSION"
  --vendor "Keith Wegner"
  --description "Interactive desktop chess analysis app in Java 17 + Maven."
)

if [[ "$OSTYPE" == darwin* ]]; then
  JPACKAGE_ARGS+=(
    --java-options "-Dapple.awt.application.appearance=system"
    --mac-package-identifier "com.github.keithwegner.chess"
  )
fi

echo "Running jpackage (${PACKAGE_TYPE})..."
jpackage "${JPACKAGE_ARGS[@]}"

echo "Package created in $DEST_DIR"
