package com.github.keithwegner.chess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChessBasicsTest {
    @Test
    void sidePieceSquareAndMoveHelpersWork() {
        assertEquals(Side.BLACK, Side.WHITE.opposite());
        assertEquals(Side.WHITE, Side.BLACK.opposite());
        assertEquals("w", Side.WHITE.fenToken());
        assertEquals("b", Side.BLACK.fenToken());

        assertEquals("", PieceType.PAWN.sanSymbol());
        assertEquals('Q', PieceType.QUEEN.fenUpper());

        assertTrue(Piece.NONE.isNone());
        assertTrue(Piece.WHITE_KING.isWhite());
        assertTrue(Piece.BLACK_KING.isBlack());
        assertFalse(Piece.BLACK_KING.isWhite());
        assertFalse(Piece.WHITE_KING.isBlack());
        assertEquals(Piece.WHITE_QUEEN, Piece.fromFenChar('Q'));
        assertEquals(Piece.BLACK_BISHOP, Piece.fromFenChar('b'));
        assertEquals(Piece.BLACK_ROOK, Piece.of(Side.BLACK, PieceType.ROOK));
        assertEquals(Piece.WHITE_KNIGHT, Piece.of(Side.WHITE, PieceType.KNIGHT));

        int e4 = SquareUtil.square(4, 3);
        assertEquals(4, SquareUtil.file(e4));
        assertEquals(3, SquareUtil.rank(e4));
        assertTrue(SquareUtil.isOnBoard(0, 0));
        assertTrue(SquareUtil.isOnBoard(7, 7));
        assertFalse(SquareUtil.isOnBoard(-1, 0));
        assertFalse(SquareUtil.isOnBoard(0, 8));
        assertEquals("e4", SquareUtil.name(e4));
        assertEquals("-", SquareUtil.name(-1));
        assertEquals("-", SquareUtil.name(64));
        assertEquals(e4, SquareUtil.parse(" e4 "));

        Move move = new Move(SquareUtil.parse("e7"), SquareUtil.parse("e8"), Piece.WHITE_QUEEN);
        Move parsed = Move.fromUci("e7e8Q");
        Move rookPromotion = Move.fromUci("e7e8r");
        Move bishopPromotion = Move.fromUci("e7e8b");
        Move knightPromotion = Move.fromUci("e7e8n");
        Move quietMove = new Move(SquareUtil.parse("a2"), SquareUtil.parse("a3"));
        assertTrue(move.isPromotion());
        assertEquals("e7e8q", move.uci());
        assertEquals(move, parsed);
        assertEquals(move.hashCode(), parsed.hashCode());
        assertEquals("e7e8q", parsed.toString());
        assertEquals(PieceType.ROOK, rookPromotion.promotion().type());
        assertEquals(PieceType.BISHOP, bishopPromotion.promotion().type());
        assertEquals(PieceType.KNIGHT, knightPromotion.promotion().type());
        assertEquals(quietMove, quietMove.withPromotionForSide(Side.BLACK));
        assertTrue(quietMove.equals(quietMove));
        assertFalse(quietMove.equals("not a move"));
        assertEquals(Piece.BLACK_QUEEN, parsed.withPromotionForSide(Side.BLACK).promotion());
        assertEquals(new Move(SquareUtil.parse("a2"), SquareUtil.parse("a4")),
                new Move(SquareUtil.parse("a2"), SquareUtil.parse("a4"), null));
    }

    @Test
    void moveAndSquareParsingRejectInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> SquareUtil.parse(null));
        assertThrows(IllegalArgumentException.class, () -> SquareUtil.parse("a"));
        assertThrows(IllegalArgumentException.class, () -> SquareUtil.parse("z9"));
        assertThrows(IllegalArgumentException.class, () -> Move.fromUci(null));
        assertThrows(IllegalArgumentException.class, () -> Move.fromUci("e2"));
        assertThrows(IllegalArgumentException.class, () -> Move.fromUci("e7e8x"));
        assertThrows(IllegalArgumentException.class, () -> Piece.fromFenChar('x'));
    }
}
