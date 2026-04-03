package com.github.keithwegner.chess;

public enum Piece {
    NONE('.', ' ', null, null),

    WHITE_PAWN('P', '\u2659', Side.WHITE, PieceType.PAWN),
    WHITE_KNIGHT('N', '\u2658', Side.WHITE, PieceType.KNIGHT),
    WHITE_BISHOP('B', '\u2657', Side.WHITE, PieceType.BISHOP),
    WHITE_ROOK('R', '\u2656', Side.WHITE, PieceType.ROOK),
    WHITE_QUEEN('Q', '\u2655', Side.WHITE, PieceType.QUEEN),
    WHITE_KING('K', '\u2654', Side.WHITE, PieceType.KING),

    BLACK_PAWN('p', '\u265F', Side.BLACK, PieceType.PAWN),
    BLACK_KNIGHT('n', '\u265E', Side.BLACK, PieceType.KNIGHT),
    BLACK_BISHOP('b', '\u265D', Side.BLACK, PieceType.BISHOP),
    BLACK_ROOK('r', '\u265C', Side.BLACK, PieceType.ROOK),
    BLACK_QUEEN('q', '\u265B', Side.BLACK, PieceType.QUEEN),
    BLACK_KING('k', '\u265A', Side.BLACK, PieceType.KING);

    private final char fenChar;
    private final char unicode;
    private final Side side;
    private final PieceType type;

    Piece(char fenChar, char unicode, Side side, PieceType type) {
        this.fenChar = fenChar;
        this.unicode = unicode;
        this.side = side;
        this.type = type;
    }

    public char fenChar() {
        return fenChar;
    }

    public char unicode() {
        return unicode;
    }

    public Side side() {
        return side;
    }

    public PieceType type() {
        return type;
    }

    public boolean isNone() {
        return this == NONE;
    }

    public boolean isWhite() {
        return side == Side.WHITE;
    }

    public boolean isBlack() {
        return side == Side.BLACK;
    }

    public static Piece fromFenChar(char ch) {
        for (Piece piece : values()) {
            if (piece.fenChar == ch) {
                return piece;
            }
        }
        throw new IllegalArgumentException("Unknown FEN piece: " + ch);
    }

    public static Piece of(Side side, PieceType type) {
        return switch (side) {
            case WHITE -> switch (type) {
                case PAWN -> WHITE_PAWN;
                case KNIGHT -> WHITE_KNIGHT;
                case BISHOP -> WHITE_BISHOP;
                case ROOK -> WHITE_ROOK;
                case QUEEN -> WHITE_QUEEN;
                case KING -> WHITE_KING;
            };
            case BLACK -> switch (type) {
                case PAWN -> BLACK_PAWN;
                case KNIGHT -> BLACK_KNIGHT;
                case BISHOP -> BLACK_BISHOP;
                case ROOK -> BLACK_ROOK;
                case QUEEN -> BLACK_QUEEN;
                case KING -> BLACK_KING;
            };
        };
    }
}
