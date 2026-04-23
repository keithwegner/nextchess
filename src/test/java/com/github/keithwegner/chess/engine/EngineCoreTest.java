package com.github.keithwegner.chess.engine;

import com.github.keithwegner.chess.Move;
import com.github.keithwegner.chess.Piece;
import com.github.keithwegner.chess.PieceType;
import com.github.keithwegner.chess.Position;
import com.github.keithwegner.chess.Side;
import com.github.keithwegner.chess.SquareUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineCoreTest {
    @Test
    void engineConfigClampsValuesAndDataObjectsAreImmutable() {
        EngineConfig config = new EngineConfig()
                .setMode(null)
                .setEnginePath("  /tmp/engine  ")
                .setThinkTimeSeconds(0.0)
                .setDepth(0)
                .setMultiPv(99)
                .setThreads(0)
                .setHashMb(0);

        assertEquals(EngineConfig.Mode.BUILTIN, config.mode());
        assertEquals("/tmp/engine", config.enginePath());
        assertEquals(0.05, config.thinkTimeSeconds());
        assertEquals(1, config.depth());
        assertEquals(5, config.multiPv());
        assertEquals(1, config.threads());
        assertEquals(1, config.hashMb());
        assertEquals(EngineConfig.Mode.UCI, config.setMode(EngineConfig.Mode.UCI).mode());
        assertEquals("", config.setEnginePath(null).enginePath());

        List<Move> pv = new ArrayList<>(List.of(move("e2e4")));
        CandidateLine line = new CandidateLine(
                move("e2e4"),
                "e4",
                "+0.20",
                20,
                null,
                pv,
                "1. e4",
                6,
                100L,
                200L);
        pv.add(move("e7e5"));
        assertEquals(1, line.pv().size());
        assertEquals(6, line.depth());
        assertEquals(100L, line.nodes());
        assertEquals(200L, line.nps());
        assertThrows(UnsupportedOperationException.class, () -> line.pv().add(move("g1f3")));

        AnalysisResult result = new AnalysisResult("mini", EngineConfig.Mode.BUILTIN, List.of(line), null, null);
        assertEquals("", result.note());
        assertEquals("", result.sourceFen());
        assertEquals(line, result.bestLine());
        assertThrows(UnsupportedOperationException.class, () -> result.lines().add(line));

        AnalysisResult empty = new AnalysisResult("mini", EngineConfig.Mode.BUILTIN, List.of(), "", "");
        assertNull(empty.bestLine());
    }

    @Test
    void engineSupportFormatsScoresSortsLinesAndDetectsEnginePath(@TempDir Path tempDir) throws IOException {
        assertEquals("#3", EngineSupport.formatScore(null, 3));
        assertEquals("#-2", EngineSupport.formatScore(null, -2));
        assertEquals("+0.00", EngineSupport.formatScore(null, null));
        assertEquals("-1.50", EngineSupport.formatScore(-150, null));

        assertEquals(1.0, EngineSupport.scoreToBarFraction(null, 2));
        assertEquals(0.0, EngineSupport.scoreToBarFraction(null, -2));
        assertEquals(0.5, EngineSupport.scoreToBarFraction(null, null));
        assertTrue(EngineSupport.scoreToBarFraction(250, null) > 0.5);
        assertTrue(EngineSupport.scoreToBarFraction(-250, null) < 0.5);

        CandidateLine cpLine = new CandidateLine(move("e2e4"), "e4", "+0.25", 25, null, List.of(move("e2e4")), "1. e4", 1, null, null);
        CandidateLine mateForWhite = new CandidateLine(move("e2e4"), "e4", "#2", null, 2, List.of(move("e2e4")), "1. e4", 1, null, null);
        CandidateLine mateForBlack = new CandidateLine(move("e2e4"), "e4", "#-2", null, -2, List.of(move("e2e4")), "1. e4", 1, null, null);
        assertTrue(EngineSupport.sortKey(mateForWhite, Side.WHITE) > EngineSupport.sortKey(cpLine, Side.WHITE));
        assertTrue(EngineSupport.sortKey(mateForBlack, Side.BLACK) > EngineSupport.sortKey(cpLine, Side.BLACK));

        String originalHome = System.getProperty("user.home");
        try {
            Path fakeHome = tempDir.resolve("home");
            Path engine = fakeHome.resolve("Downloads/stockfish/stockfish-macos-m1-apple-silicon");
            System.setProperty("user.home", fakeHome.toString());
            assertEquals("", EngineSupport.detectDefaultEnginePath());

            Files.createDirectories(engine.getParent());
            Files.writeString(engine, "#!/bin/sh\nexit 0\n");
            assertTrue(engine.toFile().setExecutable(true));
            assertEquals(engine.toString(), EngineSupport.detectDefaultEnginePath());
        } finally {
            if (originalHome == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", originalHome);
            }
        }
    }

    @Test
    void miniEngineHandlesEdgeCasesAndBasicSearch() {
        MiniEngine engine = new MiniEngine();

        Position invalid = Position.empty();
        invalid.setPieceAt(sq("e1"), Piece.WHITE_KING);
        MiniEngine.MiniEngineResult invalidResult = engine.analyze(invalid, 4, 3, 0.1);
        assertEquals(0, invalidResult.depth());
        assertTrue(invalidResult.lines().isEmpty());

        Position stalemate = fen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");
        MiniEngine.MiniEngineResult stalemateResult = engine.analyze(stalemate, 4, 3, 0.1);
        assertEquals(0, stalemateResult.depth());
        assertTrue(stalemateResult.lines().isEmpty());

        Position start = new Position();
        MiniEngine.MiniEngineResult startResult = engine.analyze(start, 8, 9, 0.2);
        assertEquals("Built-in Mini Engine", startResult.engineName());
        assertTrue(startResult.depth() >= 1);
        assertTrue(startResult.depth() <= 6);
        assertEquals(5, startResult.lines().size());
        assertFalse(startResult.lines().get(0).pv().isEmpty());
        assertTrue(startResult.lines().get(0).rootScoreStm() >= startResult.lines().get(1).rootScoreStm());

        Position kingsOnly = fen("4k3/8/8/8/8/8/8/4K3 w - - 0 1");
        MiniEngine.MiniEngineResult drawn = engine.analyze(kingsOnly, 2, 2, 0.1);
        assertFalse(drawn.lines().isEmpty());
        assertTrue(drawn.lines().stream().allMatch(line -> line.whiteScoreCp() != null && line.whiteScoreCp() == 0));

        Position mateInOne = fen("7k/5Q2/6K1/8/8/8/8/8 w - - 0 1");
        MiniEngine.MiniEngineResult mate = engine.analyze(mateInOne, 3, 1, 0.2);
        assertTrue(List.of(move("f7f8"), move("f7e8")).contains(mate.lines().get(0).move()));
        assertNotNull(mate.lines().get(0).mateWhite());
        assertTrue(mate.lines().get(0).mateWhite() > 0);
    }

    @Test
    void miniEngineReportsExactScoresForMultipleRootCandidates() {
        MiniEngine.MiniEngineResult result = new MiniEngine().analyze(new Position(), 2, 5, 0.5);

        assertEquals(2, result.depth());
        assertEquals(5, result.lines().size());
        assertTrue(result.lines().stream()
                .map(MiniEngine.MiniEngineLine::rootScoreStm)
                .distinct()
                .count() >= 3);
    }

    @Test
    void positionAnalyzerUsesBuiltInAndFallsBackFromBrokenUci() {
        PositionAnalyzer analyzer = new PositionAnalyzer();
        Position position = new Position();
        String originalFen = position.toFen();

        EngineConfig builtin = new EngineConfig()
                .setDepth(2)
                .setMultiPv(2)
                .setThinkTimeSeconds(0.1);
        AnalysisResult builtinResult = analyzer.analyze(position, builtin);
        assertEquals(EngineConfig.Mode.BUILTIN, builtinResult.modeUsed());
        assertEquals(originalFen, builtinResult.sourceFen());
        assertEquals(originalFen, position.toFen());
        assertFalse(builtinResult.lines().isEmpty());

        EngineConfig brokenUci = new EngineConfig()
                .setMode(EngineConfig.Mode.UCI)
                .setEnginePath("/definitely/missing/engine")
                .setDepth(1)
                .setMultiPv(1)
                .setThinkTimeSeconds(0.1);
        AnalysisResult fallback = analyzer.analyze(position, brokenUci);
        assertEquals(EngineConfig.Mode.BUILTIN, fallback.modeUsed());
        assertTrue(fallback.note().contains("External engine failed"));
        assertEquals(originalFen, fallback.sourceFen());
    }

    @Test
    void positionAnalyzerUsesUciWhenAWorkingEngineIsConfigured(@TempDir Path tempDir) throws IOException {
        Path script = tempDir.resolve("ok-engine.sh");
        Files.writeString(script, """
                #!/bin/sh
                while IFS= read -r line; do
                  case "$line" in
                    uci)
                      echo "id name MiniFish"
                      echo "uciok"
                      ;;
                    isready)
                      echo "readyok"
                      ;;
                    ucinewgame)
                      ;;
                    "position fen "*)
                      ;;
                    "go depth "*)
                      echo "info depth 8 multipv 1 score cp 42 nodes 900 nps 450 pv e2e4 e7e5"
                      echo "bestmove e2e4"
                      ;;
                  esac
                done
                """);
        assertTrue(script.toFile().setExecutable(true));

        Position position = new Position();
        AnalysisResult result = new PositionAnalyzer().analyze(position, new EngineConfig()
                .setMode(EngineConfig.Mode.UCI)
                .setEnginePath(script.toString())
                .setDepth(2)
                .setMultiPv(1)
                .setThinkTimeSeconds(0.1));

        assertEquals(EngineConfig.Mode.UCI, result.modeUsed());
        assertEquals("MiniFish", result.engineName());
        assertEquals(position.toFen(), result.sourceFen());
        assertEquals(move("e2e4"), result.bestLine().move());
    }

    private static Position fen(String fen) {
        Position position = Position.empty();
        position.loadFromFen(fen);
        return position;
    }

    private static Move move(String uci) {
        return Move.fromUci(uci);
    }

    private static int sq(String square) {
        return SquareUtil.parse(square);
    }
}
