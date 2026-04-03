package com.github.keithwegner.chess;

public final class SquareUtil {
    private SquareUtil() {
    }

    public static int file(int square) {
        return square & 7;
    }

    public static int rank(int square) {
        return square >> 3;
    }

    public static int square(int file, int rank) {
        return rank * 8 + file;
    }

    public static boolean isOnBoard(int file, int rank) {
        return file >= 0 && file < 8 && rank >= 0 && rank < 8;
    }

    public static String name(int square) {
        if (square < 0 || square >= 64) {
            return "-";
        }
        return String.valueOf((char) ('a' + file(square))) + (char) ('1' + rank(square));
    }

    public static int parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Square is null");
        }
        String trimmed = text.trim().toLowerCase();
        if (trimmed.length() != 2) {
            throw new IllegalArgumentException("Invalid square: " + text);
        }
        int file = trimmed.charAt(0) - 'a';
        int rank = trimmed.charAt(1) - '1';
        if (!isOnBoard(file, rank)) {
            throw new IllegalArgumentException("Invalid square: " + text);
        }
        return square(file, rank);
    }
}
