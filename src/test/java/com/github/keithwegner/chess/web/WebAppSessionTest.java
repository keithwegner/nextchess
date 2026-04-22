package com.github.keithwegner.chess.web;

import com.github.keithwegner.chess.Position;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WebAppSessionTest {
    @Test
    void analyzeMoveUndoRedoFlowWorks() {
        WebAppSession session = new WebAppSession();

        WebAppSession.State initial = session.snapshot();
        assertEquals(Position.startFen(), initial.fen());
        assertEquals("WHITE", initial.sideToMove());
        assertEquals(20, initial.legalMoves().size());

        WebAppSession.State analyzed = session.analyze(Map.of(
                "mode", "BUILTIN",
                "thinkTimeSeconds", "0.05",
                "depth", "2",
                "multiPv", "2",
                "threads", "1",
                "hashMb", "32"));
        assertNotNull(analyzed.analysis());
        assertFalse(analyzed.analysis().stale());

        WebAppSession.State moved = session.move("e2e4");
        assertEquals("BLACK", moved.sideToMove());
        assertTrue(moved.canUndo());
        assertTrue(moved.analysis().stale());

        WebAppSession.State undone = session.undo();
        assertEquals("WHITE", undone.sideToMove());
        assertTrue(undone.canRedo());

        WebAppSession.State redone = session.redo();
        assertEquals("BLACK", redone.sideToMove());
        assertEquals("e2e4", redone.lastMoveUci());
    }

    @Test
    void playBestMoveUsesCurrentAnalysis() {
        WebAppSession session = new WebAppSession();
        WebAppSession.State analyzed = session.analyze(Map.of(
                "mode", "BUILTIN",
                "thinkTimeSeconds", "0.05",
                "depth", "2"));

        assertNotNull(analyzed.analysis());
        assertFalse(analyzed.analysis().bestMoveUci().isBlank());

        WebAppSession.State afterMove = session.playBestMove();
        assertEquals(1, afterMove.history().size());
        assertEquals("BLACK", afterMove.sideToMove());
    }

    @Test
    void rejectsInvalidFen() {
        WebAppSession session = new WebAppSession();

        assertThrows(IllegalArgumentException.class, () -> session.loadFen("not a fen"));
    }

    @Test
    void clearBoardAndSetupPieceUpdateFenAndResetHistory() {
        WebAppSession session = new WebAppSession();

        session.move("e2e4");
        WebAppSession.State cleared = session.clearBoard();

        assertEquals("8/8/8/8/8/8/8/8 w - - 0 1", cleared.fen());
        assertFalse(cleared.canUndo());
        assertFalse(cleared.analyzable());

        WebAppSession.State withWhiteKing = session.setupPiece("e1", "K");
        WebAppSession.State withBlackKing = session.setupPiece("e8", "k");
        assertEquals("K", square(withWhiteKing, "e1").pieceFen());
        assertEquals("k", square(withBlackKing, "e8").pieceFen());
        assertTrue(withBlackKing.analyzable());

        WebAppSession.State erased = session.setupPiece("e1", "");
        assertEquals("", square(erased, "e1").pieceFen());
        assertFalse(erased.analyzable());
    }

    @Test
    void setupMetadataUpdatesStateAndFen() {
        WebAppSession session = new WebAppSession();

        WebAppSession.State updated = session.setupMetadata(Map.of(
                "sideToMove", "BLACK",
                "whiteCastleKing", "true",
                "whiteCastleQueen", "false",
                "blackCastleKing", "false",
                "blackCastleQueen", "true",
                "enPassantSquare", "e3",
                "halfmoveClock", "7",
                "fullmoveNumber", "42"));

        assertEquals("BLACK", updated.sideToMove());
        assertEquals("BLACK", updated.metadata().sideToMove());
        assertTrue(updated.metadata().whiteCastleKing());
        assertFalse(updated.metadata().whiteCastleQueen());
        assertFalse(updated.metadata().blackCastleKing());
        assertTrue(updated.metadata().blackCastleQueen());
        assertEquals("e3", updated.metadata().enPassantSquare());
        assertEquals(7, updated.metadata().halfmoveClock());
        assertEquals(42, updated.metadata().fullmoveNumber());
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b Kq e3 7 42", updated.fen());
    }

    @Test
    void rejectsInvalidSetupInput() {
        WebAppSession session = new WebAppSession();

        assertThrows(IllegalArgumentException.class, () -> session.setupPiece("z9", "Q"));
        assertThrows(IllegalArgumentException.class, () -> session.setupPiece("e4", "x"));
        assertThrows(IllegalArgumentException.class, () -> session.setupMetadata(Map.of(
                "sideToMove", "BLUE",
                "halfmoveClock", "0",
                "fullmoveNumber", "1")));
        assertThrows(IllegalArgumentException.class, () -> session.setupMetadata(Map.of(
                "sideToMove", "WHITE",
                "enPassantSquare", "z9",
                "halfmoveClock", "0",
                "fullmoveNumber", "1")));
    }

    @Test
    void setupMutationLeavesAnalysisVisibleButStale() {
        WebAppSession session = new WebAppSession();
        WebAppSession.State analyzed = session.analyze(Map.of(
                "mode", "BUILTIN",
                "thinkTimeSeconds", "0.05",
                "depth", "2"));

        assertNotNull(analyzed.analysis());
        assertFalse(analyzed.analysis().stale());

        WebAppSession.State edited = session.setupPiece("e2", "");
        assertNotNull(edited.analysis());
        assertTrue(edited.analysis().stale());
        assertFalse(edited.canUndo());
    }

    private static WebAppSession.BoardSquare square(WebAppSession.State state, String square) {
        return state.board().stream()
                .filter(candidate -> square.equals(candidate.square()))
                .findFirst()
                .orElseThrow();
    }
}
