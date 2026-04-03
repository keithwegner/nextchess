# Next Chess Desktop — Java + Maven

[![CI](https://github.com/keithwegner/nextchess/actions/workflows/ci.yml/badge.svg)](https://github.com/keithwegner/nextchess/actions/workflows/ci.yml)
[![Packages](https://github.com/keithwegner/nextchess/actions/workflows/release-packages.yml/badge.svg)](https://github.com/keithwegner/nextchess/actions/workflows/release-packages.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

This is a desktop chess analysis application written in Java 17 with a standard Maven project layout.

It keeps the same core workflow as the earlier Python version:
- interactive board with legal move entry
- setup mode with piece palette and editable position metadata
- FEN load / copy / paste
- undo / redo / flip board
- built-in mini engine analysis with candidate lines
- optional external UCI engine support for stronger analysis

## Requirements

- Java 17 or newer
- Maven 3.9 or newer

## Run

### Option 1: run directly with Maven

```bash
mvn package
mvn exec:java
```

### Option 2: build and run the jar

```bash
mvn package
java -jar target/next-chess-desktop-java-1.0.0.jar
```

## Desktop packaging

The project includes `jpackage`-based desktop packaging scripts.

### Build a local app image

macOS / Linux:

```bash
./scripts/package-app.sh
```

Windows PowerShell:

```powershell
./scripts/package-app.ps1
```

The packaged app image is written to `dist/jpackage/`.

On macOS this produces:

```bash
dist/jpackage/NextChess.app
```

### GitHub release artifacts

The `Release Packages` workflow builds `jpackage` app images on macOS, Linux, and Windows, then uploads them as downloadable release assets for version tags like `v1.0.0`.

## macOS

From Terminal:

```bash
cd next_chess_desktop_java
chmod +x run_mac.command
./run_mac.command
```

## External engine

To use Stockfish or another UCI engine:
1. Start the app.
2. In the **Analysis** tab, switch **Mode** to **External UCI Engine**.
3. Browse to the engine executable.
4. Click **Analyze**.

If the external engine fails, the app falls back to the built-in mini engine.

## Project layout

- `src/main/java/com/github/keithwegner/chess` — board state, FEN, move generation, SAN formatting
- `src/main/java/com/github/keithwegner/chess/engine` — built-in mini engine and UCI adapter
- `src/main/java/com/github/keithwegner/chess/ui` — Swing UI
- `scripts` — local build and packaging helpers
- `.github/workflows` — CI and packaging automation

## Notes

- The build is self-contained and does not depend on Python, Tkinter, or any native GUI toolkit.
- The built-in engine is intentionally lightweight. For stronger analysis, use an external UCI engine.
- The jar produced by Maven is directly runnable because the manifest includes the main class.
- The test suite runs under JUnit 5 with JaCoCo coverage checks enforced in Maven CI.

## License

This project is available under the MIT License. See [LICENSE](LICENSE).
