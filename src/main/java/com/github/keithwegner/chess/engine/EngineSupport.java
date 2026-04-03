package com.github.keithwegner.chess.engine;

import com.github.keithwegner.chess.Side;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class EngineSupport {
    private EngineSupport() {
    }

    public static String formatScore(Integer scoreCpWhite, Integer mateWhite) {
        if (mateWhite != null) {
            return mateWhite > 0 ? "#" + mateWhite : "#" + mateWhite;
        }
        int cp = scoreCpWhite == null ? 0 : scoreCpWhite;
        return String.format("%+.2f", cp / 100.0);
    }

    public static double scoreToBarFraction(Integer scoreCpWhite, Integer mateWhite) {
        if (mateWhite != null) {
            return mateWhite > 0 ? 1.0 : 0.0;
        }
        int cp = scoreCpWhite == null ? 0 : scoreCpWhite;
        double normalized = Math.tanh(cp / 400.0);
        return (normalized + 1.0) / 2.0;
    }

    public static int sortKey(CandidateLine line, Side sideToMove) {
        if (line.mateWhite() != null) {
            if (sideToMove == Side.WHITE) {
                return line.mateWhite() > 0 ? 200_000 : -200_000;
            }
            return line.mateWhite() < 0 ? 200_000 : -200_000;
        }
        int score = line.scoreCpWhite() == null ? 0 : line.scoreCpWhite();
        return sideToMove == Side.WHITE ? score : -score;
    }

    public static String detectDefaultEnginePath() {
        List<Path> candidates = new ArrayList<>();
        String[] names = {"stockfish", "stockfish.exe"};
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(System.getProperty("path.separator"))) {
                for (String name : names) {
                    candidates.add(Path.of(dir, name));
                }
            }
        }
        Path home = Path.of(System.getProperty("user.home", "."));
        candidates.add(home.resolve("Applications/Stockfish.app/Contents/MacOS/stockfish"));
        candidates.add(Path.of("/Applications/Stockfish.app/Contents/MacOS/stockfish"));
        candidates.add(home.resolve("Downloads/stockfish/stockfish-macos-m1-apple-silicon"));
        candidates.add(home.resolve("Downloads/stockfish/stockfish-macos-x86-64"));
        candidates.add(home.resolve("Downloads/stockfish/stockfish-ubuntu-x86-64-avx2"));
        candidates.add(home.resolve("Downloads/stockfish/stockfish-windows-x86-64-avx2.exe"));
        candidates.add(Path.of("C:/Program Files/Stockfish/stockfish.exe"));
        candidates.add(Path.of("C:/Program Files (x86)/Stockfish/stockfish.exe"));
        for (Path candidate : candidates) {
            try {
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return candidate.toString();
                }
            } catch (Exception ignored) {
            }
        }
        return "";
    }
}
