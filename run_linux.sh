#!/bin/bash
set -e
cd "$(dirname "$0")"
if [ -f target/next-chess-desktop-java-1.0.0.jar ]; then
  exec java -jar target/next-chess-desktop-java-1.0.0.jar
fi
mvn -DskipTests package
exec java -jar target/next-chess-desktop-java-1.0.0.jar
