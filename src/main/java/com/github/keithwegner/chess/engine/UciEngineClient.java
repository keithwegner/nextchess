package com.github.keithwegner.chess.engine;

import com.github.keithwegner.chess.Move;
import com.github.keithwegner.chess.Position;
import com.github.keithwegner.chess.Side;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class UciEngineClient {
    AnalysisResult analyze(Position position, EngineConfig config) throws IOException {
        String enginePath = config.enginePath();
        if (enginePath == null || enginePath.isBlank()) {
            throw new IOException("No UCI engine path configured.");
        }
        Path path = Path.of(enginePath);
        if (!Files.isRegularFile(path)) {
            throw new IOException("Engine not found: " + enginePath);
        }

        Process process = new ProcessBuilder(enginePath)
                .redirectErrorStream(true)
                .start();
        String engineName = path.getFileName().toString();
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            send(writer, "uci");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("id name ")) {
                    engineName = line.substring("id name ".length()).trim();
                }
                if (line.equals("uciok")) {
                    break;
                }
            }

            send(writer, "setoption name Threads value " + Math.max(1, config.threads()));
            send(writer, "setoption name Hash value " + Math.max(1, config.hashMb()));
            send(writer, "setoption name MultiPV value " + Math.max(1, config.multiPv()));
            send(writer, "isready");
            waitFor(reader, "readyok");

            send(writer, "ucinewgame");
            send(writer, "position fen " + position.toFen());
            send(writer, "isready");
            waitFor(reader, "readyok");

            long moveTimeMs = Math.max(50L, Math.round(config.thinkTimeSeconds() * 1000.0));
            send(writer, "go depth " + Math.max(1, config.depth()) + " movetime " + moveTimeMs);

            Map<Integer, InfoRow> rows = new HashMap<>();
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("info ")) {
                    InfoRow parsed = parseInfoLine(line);
                    if (parsed != null && parsed.pv() != null && !parsed.pv().isEmpty()) {
                        rows.put(parsed.multiPv(), parsed);
                    }
                }
                if (line.startsWith("bestmove ")) {
                    break;
                }
            }

            List<CandidateLine> lines = new ArrayList<>();
            List<InfoRow> ordered = new ArrayList<>(rows.values());
            ordered.sort(Comparator.comparingInt(InfoRow::multiPv));
            for (InfoRow row : ordered) {
                List<Move> pv = new ArrayList<>();
                for (String token : row.pv()) {
                    try {
                        pv.add(position.parseAndNormalizeUci(token));
                    } catch (Exception ignored) {
                        break;
                    }
                }
                if (pv.isEmpty()) {
                    continue;
                }
                Move best = pv.get(0);
                Integer scoreWhite = row.scoreCp();
                Integer mateWhite = row.mate();
                if (position.sideToMove() == Side.BLACK) {
                    if (scoreWhite != null) {
                        scoreWhite = -scoreWhite;
                    }
                    if (mateWhite != null) {
                        mateWhite = -mateWhite;
                    }
                }
                lines.add(new CandidateLine(
                        best,
                        position.moveToSan(best),
                        EngineSupport.formatScore(scoreWhite, mateWhite),
                        scoreWhite,
                        mateWhite,
                        pv,
                        position.movesToSan(pv, 12),
                        row.depth(),
                        row.nodes(),
                        row.nps()));
            }
            lines.sort((a, b) -> Integer.compare(EngineSupport.sortKey(b, position.sideToMove()), EngineSupport.sortKey(a, position.sideToMove())));
            return new AnalysisResult(engineName, EngineConfig.Mode.UCI, lines, "", position.toFen());
        } finally {
            try {
                process.destroy();
            } catch (Exception ignored) {
            }
        }
    }

    private void send(BufferedWriter writer, String line) throws IOException {
        writer.write(line);
        writer.newLine();
        writer.flush();
    }

    private void waitFor(BufferedReader reader, String sentinel) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.equals(sentinel)) {
                return;
            }
        }
        throw new IOException("UCI engine closed before sending " + sentinel);
    }

    private InfoRow parseInfoLine(String line) {
        String[] parts = line.trim().split("\\s+");
        int multiPv = 1;
        int depth = 0;
        Long nodes = null;
        Long nps = null;
        Integer scoreCp = null;
        Integer mate = null;
        List<String> pv = List.of();
        for (int i = 1; i < parts.length; i++) {
            String token = parts[i];
            switch (token) {
                case "multipv" -> {
                    if (i + 1 < parts.length) {
                        multiPv = parseInt(parts[++i], 1);
                    }
                }
                case "depth" -> {
                    if (i + 1 < parts.length) {
                        depth = parseInt(parts[++i], 0);
                    }
                }
                case "nodes" -> {
                    if (i + 1 < parts.length) {
                        nodes = parseLong(parts[++i]);
                    }
                }
                case "nps" -> {
                    if (i + 1 < parts.length) {
                        nps = parseLong(parts[++i]);
                    }
                }
                case "score" -> {
                    if (i + 2 < parts.length) {
                        String scoreType = parts[++i];
                        String scoreValue = parts[++i];
                        if ("cp".equals(scoreType)) {
                            scoreCp = parseInt(scoreValue, 0);
                            mate = null;
                        } else if ("mate".equals(scoreType)) {
                            mate = parseInt(scoreValue, 0);
                            scoreCp = null;
                        }
                    }
                }
                case "pv" -> {
                    List<String> pvMoves = new ArrayList<>();
                    for (int j = i + 1; j < parts.length; j++) {
                        pvMoves.add(parts[j]);
                    }
                    pv = pvMoves;
                    i = parts.length;
                }
                default -> {
                }
            }
        }
        return pv.isEmpty() ? null : new InfoRow(multiPv, depth, scoreCp, mate, nodes, nps, pv);
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record InfoRow(int multiPv, int depth, Integer scoreCp, Integer mate, Long nodes, Long nps, List<String> pv) {
    }
}
