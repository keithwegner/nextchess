package com.github.keithwegner.chess.web;

import com.github.keithwegner.chess.Move;
import com.github.keithwegner.chess.Piece;
import com.github.keithwegner.chess.Position;
import com.github.keithwegner.chess.Side;
import com.github.keithwegner.chess.SquareUtil;
import com.github.keithwegner.chess.engine.AnalysisResult;
import com.github.keithwegner.chess.engine.CandidateLine;
import com.github.keithwegner.chess.engine.EngineConfig;
import com.github.keithwegner.chess.engine.EngineSupport;
import com.github.keithwegner.chess.engine.PositionAnalyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WebAppSession {
    private final Position position = new Position();
    private final PositionAnalyzer analyzer = new PositionAnalyzer();
    private final List<HistoryEntry> historyEntries = new ArrayList<>();
    private final List<HistoryEntry> redoEntries = new ArrayList<>();

    private AnalysisResult analysisResult;
    private EngineConfig engineConfig = new EngineConfig();
    private String statusMessage = "Ready";

    public synchronized State snapshot() {
        String fen = position.toFen();
        boolean analysisStale = analysisResult != null && !analysisResult.sourceFen().equals(fen);
        CandidateLine bestLine = analysisResult == null ? null : analysisResult.bestLine();

        List<String> historySan = new ArrayList<>();
        for (HistoryEntry entry : historyEntries) {
            historySan.add(entry.san());
        }

        Move lastMove = historyEntries.isEmpty() ? null : historyEntries.get(historyEntries.size() - 1).move();
        int movingKing = position.findKing(position.sideToMove());
        String checkSquare = movingKing >= 0 && position.isKingInCheck(position.sideToMove())
                ? SquareUtil.name(movingKing)
                : "";

        return new State(
                statusMessage,
                fen,
                position.sideToMove().name(),
                position.isAnalyzable(),
                position.validityMessage(),
                buildBoard(),
                buildLegalMoves(),
                checkSquare,
                lastMove == null ? "" : lastMove.uci(),
                !historyEntries.isEmpty(),
                !redoEntries.isEmpty(),
                List.copyOf(historySan),
                position.historyAsSan(historySan),
                new PositionMetadata(
                        position.sideToMove().name(),
                        position.whiteCastleKing(),
                        position.whiteCastleQueen(),
                        position.blackCastleKing(),
                        position.blackCastleQueen(),
                        position.enPassantSquare() >= 0 ? SquareUtil.name(position.enPassantSquare()) : "-",
                        position.halfmoveClock(),
                        position.fullmoveNumber()),
                new EngineSettings(
                        engineConfig.mode().name(),
                        engineConfig.enginePath(),
                        engineConfig.thinkTimeSeconds(),
                        engineConfig.depth(),
                        engineConfig.multiPv(),
                        engineConfig.threads(),
                        engineConfig.hashMb()),
                analysisResult == null ? null : buildAnalysis(bestLine, analysisStale));
    }

    public synchronized State newGame() {
        position.loadFromFen(Position.startFen());
        resetPositionHistory();
        statusMessage = "New game loaded.";
        return snapshot();
    }

    public synchronized State loadFen(String fen) {
        position.loadFromFen(fen == null ? "" : fen.trim());
        resetPositionHistory();
        if (!position.isAnalyzable()) {
            statusMessage = "Loaded FEN, but the position is not valid for analysis.";
        } else {
            statusMessage = "FEN loaded.";
        }
        return snapshot();
    }

    public synchronized State clearBoard() {
        position.loadFromFen(Position.empty().toFen());
        resetPositionHistory();
        statusMessage = "Board cleared.";
        return snapshot();
    }

    public synchronized State setupPiece(String squareName, String pieceFen) {
        int square = SquareUtil.parse(requiredValue(squareName, "Square"));
        Piece piece = parsePiece(pieceFen);
        position.setPieceAt(square, piece);
        resetPositionHistory();
        statusMessage = piece == Piece.NONE
                ? "Cleared " + SquareUtil.name(square) + "."
                : "Placed " + piece.type().name().toLowerCase(Locale.ROOT) + " on " + SquareUtil.name(square) + ".";
        return snapshot();
    }

    public synchronized State setupMetadata(Map<String, String> values) {
        position.setSideToMove(parseSide(values.get("sideToMove"), position.sideToMove()));
        position.setWhiteCastleKing(parseBoolean(values.get("whiteCastleKing"), position.whiteCastleKing(), "whiteCastleKing"));
        position.setWhiteCastleQueen(parseBoolean(values.get("whiteCastleQueen"), position.whiteCastleQueen(), "whiteCastleQueen"));
        position.setBlackCastleKing(parseBoolean(values.get("blackCastleKing"), position.blackCastleKing(), "blackCastleKing"));
        position.setBlackCastleQueen(parseBoolean(values.get("blackCastleQueen"), position.blackCastleQueen(), "blackCastleQueen"));
        position.setEnPassantSquare(parseEnPassant(values.get("enPassantSquare")));
        position.setHalfmoveClock(parseRequiredInt(values.get("halfmoveClock"), 0, "halfmoveClock"));
        position.setFullmoveNumber(parseRequiredInt(values.get("fullmoveNumber"), 1, "fullmoveNumber"));
        resetPositionHistory();
        statusMessage = "Position metadata updated.";
        return snapshot();
    }

    public synchronized State move(String uci) {
        Move move = position.parseAndNormalizeUci(uci);
        if (!position.isMoveLegal(move)) {
            throw new IllegalArgumentException("Illegal move: " + uci);
        }
        return applyMove(move);
    }

    public synchronized State undo() {
        if (historyEntries.isEmpty()) {
            return snapshot();
        }
        position.undoMove();
        HistoryEntry entry = historyEntries.remove(historyEntries.size() - 1);
        redoEntries.add(entry);
        statusMessage = "Undid " + entry.san() + ".";
        return snapshot();
    }

    public synchronized State redo() {
        if (redoEntries.isEmpty()) {
            return snapshot();
        }
        HistoryEntry entry = redoEntries.remove(redoEntries.size() - 1);
        position.makeMove(entry.move());
        historyEntries.add(entry);
        statusMessage = "Redid " + entry.san() + ".";
        return snapshot();
    }

    public synchronized State analyze(Map<String, String> values) {
        if (!position.isAnalyzable()) {
            throw new IllegalStateException(position.validityMessage());
        }
        engineConfig = parseEngineConfig(values);
        analysisResult = analyzer.analyze(position.copy(), engineConfig);
        statusMessage = "Analysis complete with " + analysisResult.engineName() + ".";
        return snapshot();
    }

    public synchronized State detectEngine() {
        String detected = EngineSupport.detectDefaultEnginePath();
        if (detected.isBlank()) {
            statusMessage = "No Stockfish executable was found in the common locations this app checks.";
            return snapshot();
        }
        engineConfig = copyEngineConfig(engineConfig)
                .setMode(EngineConfig.Mode.UCI)
                .setEnginePath(detected);
        statusMessage = "Detected engine: " + detected;
        return snapshot();
    }

    public synchronized State playBestMove() {
        CandidateLine best = analysisResult == null ? null : analysisResult.bestLine();
        if (best == null) {
            throw new IllegalStateException("No analysis result is available yet.");
        }
        if (!position.toFen().equals(analysisResult.sourceFen())) {
            throw new IllegalStateException("Analysis is stale for the current position.");
        }
        if (!position.isMoveLegal(best.move())) {
            throw new IllegalStateException("Best move is not legal in the current position.");
        }
        return applyMove(best.move());
    }

    private State applyMove(Move move) {
        String san = position.moveToSan(move);
        position.makeMove(move);
        historyEntries.add(new HistoryEntry(move, san));
        redoEntries.clear();
        statusMessage = "Played " + san + ".";
        return snapshot();
    }

    private List<BoardSquare> buildBoard() {
        List<BoardSquare> squares = new ArrayList<>(64);
        for (int rank = 1; rank <= 8; rank++) {
            for (int file = 0; file < 8; file++) {
                int square = SquareUtil.square(file, rank - 1);
                Piece piece = position.pieceAt(square);
                squares.add(new BoardSquare(
                        SquareUtil.name(square),
                        piece == Piece.NONE ? "" : String.valueOf(piece.fenChar()),
                        piece == Piece.NONE ? "" : String.valueOf(piece.unicode()),
                        piece == Piece.NONE ? "" : piece.side().name()));
            }
        }
        return List.copyOf(squares);
    }

    private List<LegalMove> buildLegalMoves() {
        List<LegalMove> moves = new ArrayList<>();
        for (Move move : position.generateLegalMoves()) {
            Move normalized = position.normalizeMove(move);
            moves.add(new LegalMove(
                    normalized.uci(),
                    SquareUtil.name(normalized.from()),
                    SquareUtil.name(normalized.to()),
                    normalized.isPromotion() ? normalized.promotion().type().name() : "",
                    position.moveToSan(normalized)));
        }
        return List.copyOf(moves);
    }

    private AnalysisState buildAnalysis(CandidateLine bestLine, boolean analysisStale) {
        List<AnalysisLine> lines = new ArrayList<>();
        for (CandidateLine line : analysisResult.lines()) {
            lines.add(new AnalysisLine(
                    line.move().uci(),
                    line.sanMove(),
                    line.evalText(),
                    line.pvSan(),
                    line.depth(),
                    line.nodes(),
                    line.nps()));
        }

        return new AnalysisState(
                analysisResult.engineName(),
                analysisResult.modeUsed().name(),
                analysisResult.note(),
                analysisStale,
                analysisResult.sourceFen(),
                bestLine == null ? "" : bestLine.move().uci(),
                bestLine == null ? "" : bestLine.sanMove(),
                bestLine == null ? "" : bestLine.evalText(),
                bestLine == null ? 0.5 : EngineSupport.scoreToBarFraction(bestLine.scoreCpWhite(), bestLine.mateWhite()),
                List.copyOf(lines));
    }

    private EngineConfig parseEngineConfig(Map<String, String> values) {
        EngineConfig next = new EngineConfig()
                .setMode(parseMode(values.get("mode"), engineConfig.mode()))
                .setEnginePath(defaultValue(values.get("enginePath"), engineConfig.enginePath()))
                .setThinkTimeSeconds(parseDouble(values.get("thinkTimeSeconds"), engineConfig.thinkTimeSeconds()))
                .setDepth(parseInt(values.get("depth"), engineConfig.depth()))
                .setMultiPv(parseInt(values.get("multiPv"), engineConfig.multiPv()))
                .setThreads(parseInt(values.get("threads"), engineConfig.threads()))
                .setHashMb(parseInt(values.get("hashMb"), engineConfig.hashMb()));
        return next;
    }

    private EngineConfig copyEngineConfig(EngineConfig source) {
        return new EngineConfig()
                .setMode(source.mode())
                .setEnginePath(source.enginePath())
                .setThinkTimeSeconds(source.thinkTimeSeconds())
                .setDepth(source.depth())
                .setMultiPv(source.multiPv())
                .setThreads(source.threads())
                .setHashMb(source.hashMb());
    }

    private EngineConfig.Mode parseMode(String value, EngineConfig.Mode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return EngineConfig.Mode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private double parseDouble(String value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(value.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String defaultValue(String value, String fallback) {
        return value == null ? fallback : value.trim();
    }

    private Piece parsePiece(String value) {
        if (value == null || value.isBlank() || ".".equals(value.trim())) {
            return Piece.NONE;
        }
        String trimmed = value.trim();
        if (trimmed.length() != 1) {
            throw new IllegalArgumentException("Piece must be a single FEN character.");
        }
        return Piece.fromFenChar(trimmed.charAt(0));
    }

    private Side parseSide(String value, Side fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("W".equals(normalized)) {
            return Side.WHITE;
        }
        if ("B".equals(normalized)) {
            return Side.BLACK;
        }
        try {
            return Side.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid side to move: " + value, ex);
        }
    }

    private boolean parseBoolean(String value, boolean fallback, String fieldName) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> throw new IllegalArgumentException("Invalid boolean for " + fieldName + ": " + value);
        };
    }

    private int parseEnPassant(String value) {
        if (value == null || value.isBlank() || "-".equals(value.trim())) {
            return -1;
        }
        return SquareUtil.parse(value.trim());
    }

    private int parseRequiredInt(String value, int minimum, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < minimum) {
                throw new IllegalArgumentException(fieldName + " must be at least " + minimum + ".");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid number for " + fieldName + ": " + value, ex);
        }
    }

    private String requiredValue(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    private void resetPositionHistory() {
        position.resetHistory();
        historyEntries.clear();
        redoEntries.clear();
    }

    private record HistoryEntry(Move move, String san) {
    }

    public record State(
            String status,
            String fen,
            String sideToMove,
            boolean analyzable,
            String validityMessage,
            List<BoardSquare> board,
            List<LegalMove> legalMoves,
            String checkSquare,
            String lastMoveUci,
            boolean canUndo,
            boolean canRedo,
            List<String> history,
            String historyText,
            PositionMetadata metadata,
            EngineSettings engine,
            AnalysisState analysis) {
    }

    public record BoardSquare(
            String square,
            String pieceFen,
            String pieceUnicode,
            String pieceSide) {
    }

    public record LegalMove(
            String uci,
            String from,
            String to,
            String promotion,
            String san) {
    }

    public record PositionMetadata(
            String sideToMove,
            boolean whiteCastleKing,
            boolean whiteCastleQueen,
            boolean blackCastleKing,
            boolean blackCastleQueen,
            String enPassantSquare,
            int halfmoveClock,
            int fullmoveNumber) {
    }

    public record EngineSettings(
            String mode,
            String enginePath,
            double thinkTimeSeconds,
            int depth,
            int multiPv,
            int threads,
            int hashMb) {
    }

    public record AnalysisState(
            String engineName,
            String modeUsed,
            String note,
            boolean stale,
            String sourceFen,
            String bestMoveUci,
            String bestMoveSan,
            String bestEval,
            double evalFraction,
            List<AnalysisLine> lines) {
    }

    public record AnalysisLine(
            String moveUci,
            String sanMove,
            String evalText,
            String pvSan,
            int depth,
            Long nodes,
            Long nps) {
    }
}
