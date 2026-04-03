package com.github.keithwegner.chess;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionTest {
    @Test
    void startPositionCopyAndMetadataHelpersWork() {
        Position position = new Position();
        assertEquals(Position.startFen(), position.toFen());
        assertEquals(20, position.generateLegalMoves().size());
        assertEquals(List.of(move("e2e3"), move("e2e4")), position.generateLegalMovesFrom(sq("e2")));
        assertTrue(position.isMoveLegal(move("g1f3")));
        assertFalse(position.isMoveLegal(move("e2e5")));

        Position copy = position.copy();
        position.makeMove(move("e2e4"));
        assertNotEquals(copy.toFen(), position.toFen());
        assertEquals(Position.startFen(), copy.toFen());
        assertEquals(1, position.moveCount());
        position.resetHistory();
        assertEquals(0, position.moveCount());

        Position empty = Position.empty();
        assertEquals(Piece.NONE, empty.pieceAt(sq("a1")));
        assertFalse(empty.whiteCastleKing());
        assertFalse(empty.whiteCastleQueen());
        assertFalse(empty.blackCastleKing());
        assertFalse(empty.blackCastleQueen());
        empty.setPieceAt(sq("a1"), null);
        empty.setSideToMove(Side.BLACK);
        empty.setWhiteCastleKing(true);
        empty.setWhiteCastleQueen(true);
        empty.setBlackCastleKing(true);
        empty.setBlackCastleQueen(true);
        empty.setEnPassantSquare(sq("a3"));
        empty.setHalfmoveClock(-5);
        empty.setFullmoveNumber(0);
        assertEquals(Side.BLACK, empty.sideToMove());
        assertTrue(empty.whiteCastleKing());
        assertTrue(empty.whiteCastleQueen());
        assertTrue(empty.blackCastleKing());
        assertTrue(empty.blackCastleQueen());
        assertEquals(sq("a3"), empty.enPassantSquare());
        assertEquals(0, empty.halfmoveClock());
        assertEquals(1, empty.fullmoveNumber());

        empty.clearBoard();
        assertEquals(Piece.NONE, empty.pieceAt(sq("a1")));
        assertNull(empty.undoMove());
    }

    @Test
    void moveNormalizationAndStateUpdatesWork() {
        Position start = new Position();
        start.makeMove(move("e2e4"));
        assertEquals(Piece.NONE, start.pieceAt(sq("e2")));
        assertEquals(Piece.WHITE_PAWN, start.pieceAt(sq("e4")));
        assertEquals(Side.BLACK, start.sideToMove());
        assertEquals(sq("e3"), start.enPassantSquare());
        assertEquals(0, start.halfmoveClock());
        assertEquals(1, start.fullmoveNumber());

        start.makeMove(move("c7c5"));
        assertEquals(2, start.fullmoveNumber());
        assertEquals(sq("c6"), start.enPassantSquare());
        assertEquals(move("c7c5"), start.undoMove());
        assertEquals(Side.BLACK, start.sideToMove());
        assertEquals(move("e2e4"), start.undoMove());
        assertEquals(Position.startFen(), start.toFen());

        Position promotionPosition = fen("4k3/8/8/8/8/8/p7/4K3 b - - 0 1");
        Move normalized = promotionPosition.parseAndNormalizeUci("a2a1q");
        assertEquals(Piece.BLACK_QUEEN, normalized.promotion());
        assertEquals(normalized, promotionPosition.normalizeMove(Move.fromUci("a2a1q")));
        assertNull(promotionPosition.normalizeMove(null));

        Position empty = Position.empty();
        assertThrows(IllegalArgumentException.class, () -> empty.makeMove(move("a1a2")));
    }

    @Test
    void specialMovesAndCastlingRightsWork() {
        Position enPassant = fen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
        enPassant.makeMove(move("e5d6"));
        assertEquals(Piece.WHITE_PAWN, enPassant.pieceAt(sq("d6")));
        assertEquals(Piece.NONE, enPassant.pieceAt(sq("d5")));
        assertEquals(Piece.NONE, enPassant.pieceAt(sq("e5")));
        assertEquals(move("e5d6"), enPassant.undoMove());
        assertEquals("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1", enPassant.toFen());

        Position castling = fen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        assertTrue(castling.isMoveLegal(move("e1g1")));
        assertTrue(castling.isMoveLegal(move("e1c1")));
        castling.makeMove(move("e1g1"));
        assertEquals(Piece.WHITE_KING, castling.pieceAt(sq("g1")));
        assertEquals(Piece.WHITE_ROOK, castling.pieceAt(sq("f1")));
        assertFalse(castling.whiteCastleKing());
        assertFalse(castling.whiteCastleQueen());
        castling.undoMove();
        assertEquals("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", castling.toFen());

        castling.makeMove(move("a1a8"));
        assertFalse(castling.blackCastleQueen());

        Position promotion = fen("6k1/4P3/8/8/8/8/8/4K3 w - - 0 1");
        promotion.makeMove(move("e7e8q"));
        assertEquals(Piece.WHITE_QUEEN, promotion.pieceAt(sq("e8")));
        assertEquals(Piece.NONE, promotion.pieceAt(sq("e7")));
    }

    @Test
    void attackDetectionAndGameStateHelpersWork() {
        Position attacks = Position.empty();
        attacks.setPieceAt(sq("e1"), Piece.WHITE_KING);
        attacks.setPieceAt(sq("e8"), Piece.BLACK_KING);
        attacks.setPieceAt(sq("d5"), Piece.BLACK_PAWN);
        attacks.setPieceAt(sq("f6"), Piece.BLACK_KNIGHT);
        attacks.setPieceAt(sq("h7"), Piece.BLACK_BISHOP);
        attacks.setPieceAt(sq("e7"), Piece.BLACK_ROOK);
        attacks.setPieceAt(sq("d2"), Piece.BLACK_QUEEN);
        attacks.setPieceAt(sq("f2"), Piece.BLACK_KING);
        assertTrue(attacks.isSquareAttacked(sq("e4"), Side.BLACK));
        assertTrue(attacks.isSquareAttacked(sq("e1"), Side.BLACK));
        assertFalse(attacks.isSquareAttacked(sq("a1"), Side.BLACK));
        assertEquals(sq("e1"), attacks.findKing(Side.WHITE));
        assertEquals(-1, Position.empty().findKing(Side.WHITE));

        Position foolsMate = new Position();
        foolsMate.makeMove(move("f2f3"));
        foolsMate.makeMove(move("e7e5"));
        foolsMate.makeMove(move("g2g4"));
        assertEquals("Qh4#", foolsMate.moveToSan(move("d8h4")));
        foolsMate.makeMove(move("d8h4"));
        assertTrue(foolsMate.isKingInCheck(Side.WHITE));
        assertTrue(foolsMate.isCheckmate());
        assertFalse(foolsMate.isStalemate());

        Position stalemate = fen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");
        assertTrue(stalemate.isStalemate());
        assertTrue(stalemate.isDrawishByRule());

        Position kingsOnly = fen("4k3/8/8/8/8/8/8/4K3 w - - 0 1");
        assertTrue(kingsOnly.isInsufficientMaterial());
        assertTrue(kingsOnly.isDrawishByRule());

        Position bishopsOnly = fen("4k3/8/8/8/8/8/1b6/4K1B1 w - - 0 1");
        assertTrue(bishopsOnly.isInsufficientMaterial());

        Position notInsufficient = fen("4k3/8/8/8/8/8/8/R3K3 w - - 100 1");
        assertFalse(notInsufficient.isInsufficientMaterial());
        assertTrue(notInsufficient.isDrawishByRule());

        Position invalid = fen("8/8/8/8/8/8/4Kk2/8 w - - 0 1");
        assertFalse(invalid.isAnalyzable());
        assertEquals("Position must contain exactly one king for each side, and the kings cannot be adjacent.", invalid.validityMessage());

        Position missingKing = Position.empty();
        missingKing.setPieceAt(sq("e1"), Piece.WHITE_KING);
        assertFalse(missingKing.isAnalyzable());
        assertTrue(missingKing.isKingInCheck(Side.BLACK));

        Position valid = new Position();
        assertEquals("", valid.validityMessage());
    }

    @Test
    void fenParsingFormattingAndSanHelpersWork() {
        Position roundTrip = new Position();
        roundTrip.loadFromFen("r3k2r/8/8/8/8/8/8/R3K2R b KQkq e3 12 34");
        assertEquals("r3k2r/8/8/8/8/8/8/R3K2R b KQkq e3 12 34", roundTrip.toFen());

        Position fenTarget = new Position();
        assertThrows(IllegalArgumentException.class, () -> fenTarget.loadFromFen(null));
        assertThrows(IllegalArgumentException.class, () -> fenTarget.loadFromFen(" "));
        assertThrows(IllegalArgumentException.class, () -> fenTarget.loadFromFen("8/8/8/8/8/8/8/8 w -"));
        assertThrows(IllegalArgumentException.class, () -> fenTarget.loadFromFen("8/8/8/8/8/8/8 w - -"));
        assertThrows(IllegalArgumentException.class, () -> fenTarget.loadFromFen("8p/8/8/8/8/8/8/4K3 w - - 0 1"));
        assertThrows(IllegalArgumentException.class, () -> fenTarget.loadFromFen("7/8/8/8/8/8/8/4K3 w - - 0 1"));
        assertThrows(IllegalArgumentException.class, () -> fenTarget.loadFromFen("8/8/8/8/8/8/8/4K3 x - - 0 1"));

        Position illegal = new Position();
        assertEquals("—", illegal.moveToSan(null));
        assertEquals("e2e5", illegal.moveToSan(move("e2e5")));

        Position castle = fen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        assertEquals("O-O", castle.moveToSan(move("e1g1")));
        assertEquals("O-O-O", castle.moveToSan(move("e1c1")));

        Position enPassant = fen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
        assertEquals("exd6", enPassant.moveToSan(move("e5d6")));

        Position fileDisambiguation = fen("4k3/8/8/8/8/2Q1Q3/8/4K3 w - - 0 1");
        assertEquals("Qcd4+", fileDisambiguation.moveToSan(move("c3d4")));

        Position rankDisambiguation = fen("4k3/2Q5/8/8/8/2Q5/8/4K3 w - - 0 1");
        assertEquals("Q3e5+", rankDisambiguation.moveToSan(move("c3e5")));

        Position fullDisambiguation = fen("4k3/2Q5/8/8/8/Q1Q5/8/4K3 w - - 0 1");
        assertEquals("Qc3c5", fullDisambiguation.moveToSan(move("c3c5")));

        Position check = fen("4k3/8/8/8/8/8/4Q3/4K3 w - - 0 1");
        assertEquals("Qe7+", check.moveToSan(move("e2e7")));

        Position promotion = fen("6k1/4P3/8/8/8/8/8/4K3 w - - 0 1");
        assertEquals("e8=Q+", promotion.moveToSan(move("e7e8q")));

        Position capturePromotion = fen("1r4k1/P7/8/8/8/8/8/4K3 w - - 0 1");
        assertTrue(capturePromotion.isMoveLegal(move("a7b8q")));

        Position blackCastling = fen("r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1");
        assertTrue(blackCastling.isMoveLegal(move("e8g8")));

        Position line = new Position();
        assertEquals("1. e4 1... e5 2. Nf3", line.movesToSan(
                List.of(move("e2e4"), move("e7e5"), move("g1f3"), move("e2e5")), 4));
        assertEquals("1. e4", line.movesToSan(List.of(move("e2e4"), move("e7e5")), 1));
        assertEquals("1. e4 e5 2. Nf3", line.historyAsSan(List.of("e4", "e5", "Nf3")));
        assertEquals(List.of(Piece.WHITE_QUEEN, Piece.WHITE_ROOK, Piece.WHITE_BISHOP, Piece.WHITE_KNIGHT),
                line.promotionPiecesForSide(Side.WHITE));
        assertEquals(List.of(Piece.BLACK_QUEEN, Piece.BLACK_ROOK, Piece.BLACK_BISHOP, Piece.BLACK_KNIGHT),
                line.promotionPiecesForSide(Side.BLACK));
    }

    private static Position fen(String fen) {
        Position position = Position.empty();
        position.loadFromFen(fen);
        return position;
    }

    private static int sq(String square) {
        return SquareUtil.parse(square);
    }

    private static Move move(String uci) {
        return Move.fromUci(uci);
    }
}
