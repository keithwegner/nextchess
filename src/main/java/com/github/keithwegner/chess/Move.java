package com.github.keithwegner.chess;

import java.util.Objects;

public final class Move {
    private final int from;
    private final int to;
    private final Piece promotion;

    public Move(int from, int to) {
        this(from, to, Piece.NONE);
    }

    public Move(int from, int to, Piece promotion) {
        this.from = from;
        this.to = to;
        this.promotion = promotion == null ? Piece.NONE : promotion;
    }

    public int from() {
        return from;
    }

    public int to() {
        return to;
    }

    public Piece promotion() {
        return promotion;
    }

    public boolean isPromotion() {
        return promotion != Piece.NONE;
    }

    public String uci() {
        StringBuilder sb = new StringBuilder();
        sb.append(SquareUtil.name(from));
        sb.append(SquareUtil.name(to));
        if (isPromotion()) {
            sb.append(Character.toLowerCase(promotion.type().fenUpper()));
        }
        return sb.toString();
    }

    public static Move fromUci(String uci) {
        if (uci == null) {
            throw new IllegalArgumentException("Move text is null");
        }
        String trimmed = uci.trim();
        if (trimmed.length() < 4) {
            throw new IllegalArgumentException("Invalid UCI move: " + uci);
        }
        int from = SquareUtil.parse(trimmed.substring(0, 2));
        int to = SquareUtil.parse(trimmed.substring(2, 4));
        Piece promotion = Piece.NONE;
        if (trimmed.length() >= 5) {
            char promo = trimmed.charAt(4);
            PieceType type = switch (Character.toLowerCase(promo)) {
                case 'q' -> PieceType.QUEEN;
                case 'r' -> PieceType.ROOK;
                case 'b' -> PieceType.BISHOP;
                case 'n' -> PieceType.KNIGHT;
                default -> throw new IllegalArgumentException("Unsupported promotion: " + promo);
            };
            // Side will be inferred by the position when needed; default to white here.
            promotion = Piece.of(Side.WHITE, type);
        }
        return new Move(from, to, promotion);
    }

    public Move withPromotionForSide(Side side) {
        if (!isPromotion()) {
            return this;
        }
        return new Move(from, to, Piece.of(side, promotion.type()));
    }

    @Override
    public String toString() {
        return uci();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Move move)) {
            return false;
        }
        return from == move.from && to == move.to && promotion.type() == move.promotion.type();
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, promotion.type());
    }
}
