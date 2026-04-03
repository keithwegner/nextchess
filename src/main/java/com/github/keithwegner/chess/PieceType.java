package com.github.keithwegner.chess;

public enum PieceType {
    PAWN("", 'P'),
    KNIGHT("N", 'N'),
    BISHOP("B", 'B'),
    ROOK("R", 'R'),
    QUEEN("Q", 'Q'),
    KING("K", 'K');

    private final String sanSymbol;
    private final char fenUpper;

    PieceType(String sanSymbol, char fenUpper) {
        this.sanSymbol = sanSymbol;
        this.fenUpper = fenUpper;
    }

    public String sanSymbol() {
        return sanSymbol;
    }

    public char fenUpper() {
        return fenUpper;
    }
}
