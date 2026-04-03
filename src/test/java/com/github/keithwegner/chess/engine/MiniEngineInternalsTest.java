package com.github.keithwegner.chess.engine;

import com.github.keithwegner.chess.Move;
import com.github.keithwegner.chess.Piece;
import com.github.keithwegner.chess.PieceType;
import com.github.keithwegner.chess.Position;
import com.github.keithwegner.chess.Side;
import com.github.keithwegner.chess.SquareUtil;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniEngineInternalsTest {
    @Test
    void privateOrderingAndEvaluationHelpersCoverEdgeBranches() throws Exception {
        MiniEngine engine = new MiniEngine();
        setSearchWindow(engine);

        Position promotion = fen("6k1/4P3/8/8/8/8/8/4K3 w - - 0 1");
        Position castling = fen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        Position connected = fen("4k3/8/8/8/8/8/PP6/4K3 w - - 0 1");
        Position isolated = fen("4k3/8/8/8/8/8/P7/4K3 w - - 0 1");
        Position openFile = fen("4k3/8/8/8/8/8/8/R3K3 w - - 0 1");
        Position noBlackKing = Position.empty();
        noBlackKing.setPieceAt(sq("e1"), Piece.WHITE_KING);
        Position advancedWhiteKing = fen("4k3/8/8/8/8/4K3/8/8 w - - 0 1");
        Position checkmate = fen("7k/6Q1/6K1/8/8/8/8/8 b - - 0 1");
        Position stalemate = fen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");
        Position drawn = fen("4k3/8/8/8/8/8/8/4K3 w - - 100 1");
        Position blackStart = new Position();
        blackStart.loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1");

        assertTrue((int) invoke(engine, "moveOrderScore",
                new Class[]{Position.class, Move.class}, promotion, move("e7e8q")) > 20_000);
        int castleScore = (int) invoke(engine, "moveOrderScore",
                new Class[]{Position.class, Move.class}, castling, move("e1g1"));
        int kingStepScore = (int) invoke(engine, "moveOrderScore",
                new Class[]{Position.class, Move.class}, castling, move("e1f1"));
        assertTrue(castleScore > kingStepScore);

        assertTrue((boolean) invoke(engine, "isConnectedPawn",
                new Class[]{Position.class, int.class, Side.class}, connected, sq("a2"), Side.WHITE));
        assertFalse((boolean) invoke(engine, "isConnectedPawn",
                new Class[]{Position.class, int.class, Side.class}, isolated, sq("a2"), Side.WHITE));

        assertEquals(20, invoke(engine, "rookFileBonus",
                new Class[]{Position.class, Side.class}, openFile, Side.WHITE));
        assertEquals(-5000, invoke(engine, "kingSafety",
                new Class[]{Position.class, Side.class, double.class}, noBlackKing, Side.BLACK, 1.0));
        assertTrue((int) invoke(engine, "kingSafety",
                new Class[]{Position.class, Side.class, double.class}, advancedWhiteKing, Side.WHITE, 1.0) < 0);

        assertEquals(100_000, invoke(engine, "evaluateWhite",
                new Class[]{Position.class}, checkmate));
        assertEquals(0, invoke(engine, "evaluateWhite",
                new Class[]{Position.class}, stalemate));
        assertEquals(0, invoke(engine, "evaluateWhite",
                new Class[]{Position.class}, drawn));

        Object mateResult = invoke(engine, "quiescence",
                new Class[]{Position.class, int.class, int.class, int.class}, checkmate, -1_000_000, 1_000_000, 1);
        Object staleResult = invoke(engine, "quiescence",
                new Class[]{Position.class, int.class, int.class, int.class}, stalemate, -1_000_000, 1_000_000, 1);
        assertEquals(-99_999, recordInt(mateResult, "score"));
        assertEquals(0, recordInt(staleResult, "score"));

        assertNull(invoke(engine, "scoreToMateWhite", new Class[]{int.class}, 500));
        assertEquals(Integer.valueOf(1), invoke(engine, "scoreToMateWhite", new Class[]{int.class}, 99_999));

        MiniEngine.MiniEngineResult blackResult = engine.analyze(blackStart, 2, 1, 0.1);
        assertFalse(blackResult.lines().isEmpty());
    }

    private static void setSearchWindow(MiniEngine engine) throws Exception {
        Field startNanos = MiniEngine.class.getDeclaredField("startNanos");
        Field timeLimitNanos = MiniEngine.class.getDeclaredField("timeLimitNanos");
        startNanos.setAccessible(true);
        timeLimitNanos.setAccessible(true);
        startNanos.setLong(engine, System.nanoTime());
        timeLimitNanos.setLong(engine, Long.MAX_VALUE / 4);
    }

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static int recordInt(Object record, String accessor) throws Exception {
        Method method = record.getClass().getDeclaredMethod(accessor);
        method.setAccessible(true);
        return (int) method.invoke(record);
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
