package com.github.keithwegner.chess.engine;

import com.github.keithwegner.chess.Move;
import com.github.keithwegner.chess.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UciEngineClientTest {
    @TempDir
    Path tempDir;

    @Test
    void analyzeRejectsBlankAndMissingPaths() {
        UciEngineClient client = new UciEngineClient();
        Position position = new Position();

        IOException blank = assertThrows(IOException.class, () -> client.analyze(position,
                new EngineConfig().setMode(EngineConfig.Mode.UCI).setEnginePath("")));
        assertEquals("No UCI engine path configured.", blank.getMessage());

        IOException missing = assertThrows(IOException.class, () -> client.analyze(position,
                new EngineConfig().setMode(EngineConfig.Mode.UCI).setEnginePath(tempDir.resolve("missing-engine").toString())));
        assertTrue(missing.getMessage().contains("Engine not found"));
    }

    @Test
    void analyzeParsesUciOutputAndFlipsScoresForBlack() throws IOException {
        UciEngineClient client = new UciEngineClient();
        Path engine = createEngineScript("fake-engine.sh", """
                #!/bin/sh
                fen=""
                while IFS= read -r line; do
                  case "$line" in
                    uci)
                      echo "id name FakeFish"
                      echo "uciok"
                      ;;
                    isready)
                      echo "readyok"
                      ;;
                    ucinewgame)
                      ;;
                    "position fen "*)
                      fen="${line#position fen }"
                      ;;
                    "go depth "*)
                      case "$fen" in
                        *" b "*)
                          echo "info depth 8 multipv 2 score cp 34 nodes 2000 nps 1000 pv d2d4 d7d5"
                          echo "info depth 9 multipv 1 score mate 2 nodes 4000 nps 2000 pv e2e4 e7e5"
                          ;;
                        *)
                          echo "info depth nope multipv 3 score cp nope nodes bad nps nope pv junk"
                          echo "info depth 8 multipv 2 score cp 34 nodes 2000 nps 1000 pv d2d4 d7d5"
                          echo "info depth 9 multipv 1 score mate 2 nodes 4000 nps 2000 pv e2e4 e7e5"
                          ;;
                      esac
                      echo "bestmove e2e4"
                      ;;
                  esac
                done
                """);

        EngineConfig config = new EngineConfig()
                .setMode(EngineConfig.Mode.UCI)
                .setEnginePath(engine.toString())
                .setDepth(4)
                .setMultiPv(3)
                .setThreads(0)
                .setHashMb(0)
                .setThinkTimeSeconds(0.1);

        AnalysisResult whiteResult = client.analyze(new Position(), config);
        assertEquals("FakeFish", whiteResult.engineName());
        assertEquals(EngineConfig.Mode.UCI, whiteResult.modeUsed());
        assertEquals(2, whiteResult.lines().size());
        assertEquals(move("e2e4"), whiteResult.bestLine().move());
        assertEquals(Integer.valueOf(2), whiteResult.bestLine().mateWhite());
        assertTrue(whiteResult.bestLine().pvSan().contains("e4"));
        assertFalse(whiteResult.lines().get(1).pv().isEmpty());

        Position blackToMove = new Position();
        blackToMove.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1");
        AnalysisResult blackResult = client.analyze(blackToMove, config);
        assertEquals(Integer.valueOf(-2), blackResult.bestLine().mateWhite());
        assertEquals(Integer.valueOf(-34), blackResult.lines().get(1).scoreCpWhite());
        assertEquals("#-2", blackResult.bestLine().evalText());
    }

    @Test
    void analyzeFailsWhenEngineClosesBeforeReady() throws IOException {
        UciEngineClient client = new UciEngineClient();
        Path engine = createEngineScript("broken-engine.sh", """
                #!/bin/sh
                while IFS= read -r line; do
                  case "$line" in
                    uci)
                      echo "id name BrokenFish"
                      echo "uciok"
                      exit 0
                      ;;
                  esac
                done
                """);

        EngineConfig config = new EngineConfig()
                .setMode(EngineConfig.Mode.UCI)
                .setEnginePath(engine.toString());
        assertThrows(IOException.class, () -> client.analyze(new Position(), config));
    }

    private Path createEngineScript(String name, String body) throws IOException {
        Path script = tempDir.resolve(name);
        Files.writeString(script, body);
        assertTrue(script.toFile().setExecutable(true));
        return script;
    }

    private static Move move(String uci) {
        return Move.fromUci(uci);
    }
}
