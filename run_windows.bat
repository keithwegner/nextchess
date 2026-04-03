@echo off
cd /d %~dp0
if exist target\next-chess-desktop-java-1.0.0.jar (
  java -jar target\next-chess-desktop-java-1.0.0.jar
  goto :eof
)
mvn -DskipTests package
java -jar target\next-chess-desktop-java-1.0.0.jar
