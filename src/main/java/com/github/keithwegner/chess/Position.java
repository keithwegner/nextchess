package com.github.keithwegner.chess;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public final class Position {
    private final Piece[] board = new Piece[64];
    private Side sideToMove = Side.WHITE;
    private boolean whiteCastleKing = true;
    private boolean whiteCastleQueen = true;
    private boolean blackCastleKing = true;
    private boolean blackCastleQueen = true;
    private int enPassantSquare = -1;
    private int halfmoveClock = 0;
    private int fullmoveNumber = 1;

    private final Deque<StateSnapshot> stateHistory = new ArrayDeque<>();
    private final Deque<Move> moveHistory = new ArrayDeque<>();

    public Position() {
        loadFromFen(startFen());
    }

    public static Position empty() {
        Position position = new Position(false);
        position.clearBoard();
        position.sideToMove = Side.WHITE;
        position.whiteCastleKing = false;
        position.whiteCastleQueen = false;
        position.blackCastleKing = false;
        position.blackCastleQueen = false;
        position.enPassantSquare = -1;
        position.halfmoveClock = 0;
        position.fullmoveNumber = 1;
        return position;
    }

    public static String startFen() {
        return "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    }

    private Position(boolean initialize) {
        Arrays.fill(board, Piece.NONE);
        if (initialize) {
            loadFromFen(startFen());
        }
    }

    public Position copy() {
        Position copy = new Position(false);
        System.arraycopy(this.board, 0, copy.board, 0, this.board.length);
        copy.sideToMove = this.sideToMove;
        copy.whiteCastleKing = this.whiteCastleKing;
        copy.whiteCastleQueen = this.whiteCastleQueen;
        copy.blackCastleKing = this.blackCastleKing;
        copy.blackCastleQueen = this.blackCastleQueen;
        copy.enPassantSquare = this.enPassantSquare;
        copy.halfmoveClock = this.halfmoveClock;
        copy.fullmoveNumber = this.fullmoveNumber;
        return copy;
    }

    public void clearBoard() {
        Arrays.fill(board, Piece.NONE);
        stateHistory.clear();
        moveHistory.clear();
    }

    public Piece pieceAt(int square) {
        return board[square];
    }

    public void setPieceAt(int square, Piece piece) {
        board[square] = piece == null ? Piece.NONE : piece;
    }

    public Side sideToMove() {
        return sideToMove;
    }

    public void setSideToMove(Side sideToMove) {
        this.sideToMove = sideToMove;
    }

    public boolean whiteCastleKing() {
        return whiteCastleKing;
    }

    public void setWhiteCastleKing(boolean value) {
        this.whiteCastleKing = value;
    }

    public boolean whiteCastleQueen() {
        return whiteCastleQueen;
    }

    public void setWhiteCastleQueen(boolean value) {
        this.whiteCastleQueen = value;
    }

    public boolean blackCastleKing() {
        return blackCastleKing;
    }

    public void setBlackCastleKing(boolean value) {
        this.blackCastleKing = value;
    }

    public boolean blackCastleQueen() {
        return blackCastleQueen;
    }

    public void setBlackCastleQueen(boolean value) {
        this.blackCastleQueen = value;
    }

    public int enPassantSquare() {
        return enPassantSquare;
    }

    public void setEnPassantSquare(int enPassantSquare) {
        this.enPassantSquare = enPassantSquare;
    }

    public int halfmoveClock() {
        return halfmoveClock;
    }

    public void setHalfmoveClock(int value) {
        this.halfmoveClock = Math.max(0, value);
    }

    public int fullmoveNumber() {
        return fullmoveNumber;
    }

    public void setFullmoveNumber(int value) {
        this.fullmoveNumber = Math.max(1, value);
    }

    public int moveCount() {
        return moveHistory.size();
    }

    public void resetHistory() {
        stateHistory.clear();
        moveHistory.clear();
    }

    public List<Move> generateLegalMoves() {
        List<Move> pseudo = generatePseudoLegalMoves(sideToMove);
        List<Move> legal = new ArrayList<>(pseudo.size());
        Side mover = sideToMove;
        for (Move move : pseudo) {
            makeMove(move);
            boolean ok = !isKingInCheck(mover);
            undoMove();
            if (ok) {
                legal.add(move);
            }
        }
        return legal;
    }

    public List<Move> generateLegalMovesFrom(int fromSquare) {
        List<Move> result = new ArrayList<>();
        for (Move move : generateLegalMoves()) {
            if (move.from() == fromSquare) {
                result.add(move);
            }
        }
        return result;
    }

    public boolean isMoveLegal(Move move) {
        Move normalized = normalizeMove(move);
        return generateLegalMoves().contains(normalized);
    }

    public Move parseAndNormalizeUci(String uci) {
        return normalizeMove(Move.fromUci(uci));
    }

    public Move normalizeMove(Move move) {
        if (move == null) {
            return null;
        }
        if (!move.isPromotion()) {
            return move;
        }
        return move.withPromotionForSide(sideToMove);
    }

    public void makeMove(Move inputMove) {
        Move move = normalizeMove(inputMove);
        StateSnapshot snapshot = snapshot();
        stateHistory.push(snapshot);
        moveHistory.push(move);

        Piece moving = board[move.from()];
        Piece target = board[move.to()];
        if (moving == Piece.NONE) {
            throw new IllegalArgumentException("No piece on " + SquareUtil.name(move.from()));
        }

        boolean pawnMove = moving.type() == PieceType.PAWN;
        boolean isCapture = target != Piece.NONE;
        boolean enPassantCapture = false;

        board[move.from()] = Piece.NONE;

        if (moving.type() == PieceType.KING && Math.abs(SquareUtil.file(move.to()) - SquareUtil.file(move.from())) == 2) {
            if (SquareUtil.file(move.to()) > SquareUtil.file(move.from())) {
                int rookFrom = sideToMove == Side.WHITE ? SquareUtil.parse("h1") : SquareUtil.parse("h8");
                int rookTo = sideToMove == Side.WHITE ? SquareUtil.parse("f1") : SquareUtil.parse("f8");
                board[rookTo] = board[rookFrom];
                board[rookFrom] = Piece.NONE;
            } else {
                int rookFrom = sideToMove == Side.WHITE ? SquareUtil.parse("a1") : SquareUtil.parse("a8");
                int rookTo = sideToMove == Side.WHITE ? SquareUtil.parse("d1") : SquareUtil.parse("d8");
                board[rookTo] = board[rookFrom];
                board[rookFrom] = Piece.NONE;
            }
        }

        if (pawnMove && move.to() == enPassantSquare && target == Piece.NONE
                && SquareUtil.file(move.from()) != SquareUtil.file(move.to())) {
            int capturedSquare = move.to() + (sideToMove == Side.WHITE ? -8 : 8);
            target = board[capturedSquare];
            board[capturedSquare] = Piece.NONE;
            isCapture = true;
            enPassantCapture = true;
        }

        Piece placed = move.isPromotion() ? Piece.of(sideToMove, move.promotion().type()) : moving;
        board[move.to()] = placed;

        updateCastlingRightsOnMove(moving, move.from(), move.to(), target, enPassantCapture);

        if (pawnMove && Math.abs(SquareUtil.rank(move.to()) - SquareUtil.rank(move.from())) == 2) {
            enPassantSquare = move.from() + (sideToMove == Side.WHITE ? 8 : -8);
        } else {
            enPassantSquare = -1;
        }

        if (pawnMove || isCapture) {
            halfmoveClock = 0;
        } else {
            halfmoveClock++;
        }

        if (sideToMove == Side.BLACK) {
            fullmoveNumber++;
        }

        sideToMove = sideToMove.opposite();
    }

    public Move undoMove() {
        if (stateHistory.isEmpty()) {
            return null;
        }
        StateSnapshot snapshot = stateHistory.pop();
        Move move = moveHistory.pop();
        restore(snapshot);
        return move;
    }

    public boolean isKingInCheck(Side side) {
        int kingSquare = findKing(side);
        if (kingSquare < 0) {
            return true;
        }
        return isSquareAttacked(kingSquare, side.opposite());
    }

    public int findKing(Side side) {
        Piece target = Piece.of(side, PieceType.KING);
        for (int i = 0; i < 64; i++) {
            if (board[i] == target) {
                return i;
            }
        }
        return -1;
    }

    public boolean isSquareAttacked(int square, Side bySide) {
        int file = SquareUtil.file(square);
        int rank = SquareUtil.rank(square);

        int pawnRankOffset = bySide == Side.WHITE ? -1 : 1;
        int pawnSourceRank = rank + pawnRankOffset;
        if (pawnSourceRank >= 0 && pawnSourceRank < 8) {
            int leftFile = file - 1;
            if (leftFile >= 0) {
                Piece piece = board[SquareUtil.square(leftFile, pawnSourceRank)];
                if (piece.side() == bySide && piece.type() == PieceType.PAWN) {
                    return true;
                }
            }
            int rightFile = file + 1;
            if (rightFile < 8) {
                Piece piece = board[SquareUtil.square(rightFile, pawnSourceRank)];
                if (piece.side() == bySide && piece.type() == PieceType.PAWN) {
                    return true;
                }
            }
        }

        int[][] knightOffsets = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
        for (int[] offset : knightOffsets) {
            int nf = file + offset[0];
            int nr = rank + offset[1];
            if (SquareUtil.isOnBoard(nf, nr)) {
                Piece piece = board[SquareUtil.square(nf, nr)];
                if (piece.side() == bySide && piece.type() == PieceType.KNIGHT) {
                    return true;
                }
            }
        }

        int[][] bishopDirs = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] dir : bishopDirs) {
            int nf = file + dir[0];
            int nr = rank + dir[1];
            while (SquareUtil.isOnBoard(nf, nr)) {
                Piece piece = board[SquareUtil.square(nf, nr)];
                if (piece != Piece.NONE) {
                    if (piece.side() == bySide && (piece.type() == PieceType.BISHOP || piece.type() == PieceType.QUEEN)) {
                        return true;
                    }
                    break;
                }
                nf += dir[0];
                nr += dir[1];
            }
        }

        int[][] rookDirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] dir : rookDirs) {
            int nf = file + dir[0];
            int nr = rank + dir[1];
            while (SquareUtil.isOnBoard(nf, nr)) {
                Piece piece = board[SquareUtil.square(nf, nr)];
                if (piece != Piece.NONE) {
                    if (piece.side() == bySide && (piece.type() == PieceType.ROOK || piece.type() == PieceType.QUEEN)) {
                        return true;
                    }
                    break;
                }
                nf += dir[0];
                nr += dir[1];
            }
        }

        for (int df = -1; df <= 1; df++) {
            for (int dr = -1; dr <= 1; dr++) {
                if (df == 0 && dr == 0) {
                    continue;
                }
                int nf = file + df;
                int nr = rank + dr;
                if (SquareUtil.isOnBoard(nf, nr)) {
                    Piece piece = board[SquareUtil.square(nf, nr)];
                    if (piece.side() == bySide && piece.type() == PieceType.KING) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean isCheckmate() {
        return isKingInCheck(sideToMove) && generateLegalMoves().isEmpty();
    }

    public boolean isStalemate() {
        return !isKingInCheck(sideToMove) && generateLegalMoves().isEmpty();
    }

    public boolean isDrawishByRule() {
        return isStalemate() || halfmoveClock >= 100 || isInsufficientMaterial();
    }

    public boolean isInsufficientMaterial() {
        int whiteMinor = 0;
        int blackMinor = 0;
        int whiteOther = 0;
        int blackOther = 0;
        int whiteBishops = 0;
        int blackBishops = 0;

        for (Piece piece : board) {
            if (piece == Piece.NONE || piece.type() == PieceType.KING) {
                continue;
            }
            boolean white = piece.side() == Side.WHITE;
            switch (piece.type()) {
                case PAWN, ROOK, QUEEN -> {
                    if (white) {
                        whiteOther++;
                    } else {
                        blackOther++;
                    }
                }
                case KNIGHT -> {
                    if (white) {
                        whiteMinor++;
                    } else {
                        blackMinor++;
                    }
                }
                case BISHOP -> {
                    if (white) {
                        whiteMinor++;
                        whiteBishops++;
                    } else {
                        blackMinor++;
                        blackBishops++;
                    }
                }
                default -> {
                }
            }
        }

        if (whiteOther > 0 || blackOther > 0) {
            return false;
        }
        if (whiteMinor == 0 && blackMinor == 0) {
            return true;
        }
        if (whiteMinor == 1 && blackMinor == 0) {
            return true;
        }
        if (whiteMinor == 0 && blackMinor == 1) {
            return true;
        }
        return whiteMinor == 1 && blackMinor == 1 && whiteBishops == 1 && blackBishops == 1;
    }

    public boolean isAnalyzable() {
        int whiteKings = 0;
        int blackKings = 0;
        for (Piece piece : board) {
            if (piece == Piece.WHITE_KING) {
                whiteKings++;
            } else if (piece == Piece.BLACK_KING) {
                blackKings++;
            }
        }
        if (whiteKings != 1 || blackKings != 1) {
            return false;
        }
        int whiteKing = findKing(Side.WHITE);
        int blackKing = findKing(Side.BLACK);
        if (whiteKing < 0 || blackKing < 0) {
            return false;
        }
        return !kingsAdjacent(whiteKing, blackKing);
    }

    public String validityMessage() {
        if (!isAnalyzable()) {
            return "Position must contain exactly one king for each side, and the kings cannot be adjacent.";
        }
        return "";
    }

    public String toFen() {
        StringBuilder sb = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                Piece piece = board[SquareUtil.square(file, rank)];
                if (piece == Piece.NONE) {
                    empty++;
                } else {
                    if (empty > 0) {
                        sb.append(empty);
                        empty = 0;
                    }
                    sb.append(piece.fenChar());
                }
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (rank > 0) {
                sb.append('/');
            }
        }
        sb.append(' ').append(sideToMove.fenToken()).append(' ');
        StringBuilder rights = new StringBuilder();
        if (whiteCastleKing) rights.append('K');
        if (whiteCastleQueen) rights.append('Q');
        if (blackCastleKing) rights.append('k');
        if (blackCastleQueen) rights.append('q');
        sb.append(rights.length() == 0 ? "-" : rights.toString());
        sb.append(' ').append(enPassantSquare >= 0 ? SquareUtil.name(enPassantSquare) : "-");
        sb.append(' ').append(halfmoveClock);
        sb.append(' ').append(fullmoveNumber);
        return sb.toString();
    }

    public void loadFromFen(String fen) {
        if (fen == null || fen.isBlank()) {
            throw new IllegalArgumentException("FEN is empty");
        }
        String[] parts = fen.trim().split("\\s+");
        if (parts.length < 4) {
            throw new IllegalArgumentException("FEN must have at least 4 fields");
        }

        Arrays.fill(board, Piece.NONE);
        String[] ranks = parts[0].split("/");
        if (ranks.length != 8) {
            throw new IllegalArgumentException("FEN board field must have 8 ranks");
        }
        for (int fenRank = 0; fenRank < 8; fenRank++) {
            String rankText = ranks[fenRank];
            int file = 0;
            int boardRank = 7 - fenRank;
            for (int i = 0; i < rankText.length(); i++) {
                char ch = rankText.charAt(i);
                if (Character.isDigit(ch)) {
                    file += ch - '0';
                } else {
                    if (file >= 8) {
                        throw new IllegalArgumentException("Too many files in rank: " + rankText);
                    }
                    board[SquareUtil.square(file, boardRank)] = Piece.fromFenChar(ch);
                    file++;
                }
            }
            if (file != 8) {
                throw new IllegalArgumentException("Rank does not sum to 8: " + rankText);
            }
        }

        sideToMove = switch (parts[1].toLowerCase(Locale.ROOT)) {
            case "w" -> Side.WHITE;
            case "b" -> Side.BLACK;
            default -> throw new IllegalArgumentException("Invalid side-to-move token: " + parts[1]);
        };

        String rights = parts[2];
        whiteCastleKing = rights.contains("K");
        whiteCastleQueen = rights.contains("Q");
        blackCastleKing = rights.contains("k");
        blackCastleQueen = rights.contains("q");

        enPassantSquare = parts[3].equals("-") ? -1 : SquareUtil.parse(parts[3]);
        halfmoveClock = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        fullmoveNumber = parts.length > 5 ? Math.max(1, Integer.parseInt(parts[5])) : 1;

        stateHistory.clear();
        moveHistory.clear();
    }

    public String moveToSan(Move move) {
        Move normalized = normalizeMove(move);
        List<Move> legalMoves = generateLegalMoves();
        if (!legalMoves.contains(normalized)) {
            return normalized == null ? "—" : normalized.uci();
        }

        Piece moving = board[normalized.from()];
        boolean castle = moving.type() == PieceType.KING
                && Math.abs(SquareUtil.file(normalized.to()) - SquareUtil.file(normalized.from())) == 2;
        if (castle) {
            String san = SquareUtil.file(normalized.to()) > SquareUtil.file(normalized.from()) ? "O-O" : "O-O-O";
            makeMove(normalized);
            san += checkSuffixAfterMove();
            undoMove();
            return san;
        }

        boolean enPassant = moving.type() == PieceType.PAWN
                && normalized.to() == enPassantSquare
                && board[normalized.to()] == Piece.NONE
                && SquareUtil.file(normalized.from()) != SquareUtil.file(normalized.to());
        boolean capture = board[normalized.to()] != Piece.NONE || enPassant;

        StringBuilder sb = new StringBuilder();
        if (moving.type() != PieceType.PAWN) {
            sb.append(moving.type().sanSymbol());
            appendDisambiguation(sb, normalized, legalMoves, moving.type());
        } else if (capture) {
            sb.append((char) ('a' + SquareUtil.file(normalized.from())));
        }
        if (capture) {
            sb.append('x');
        }
        sb.append(SquareUtil.name(normalized.to()));
        if (normalized.isPromotion()) {
            sb.append('=');
            sb.append(normalized.promotion().type().sanSymbol());
        }
        makeMove(normalized);
        sb.append(checkSuffixAfterMove());
        undoMove();
        return sb.toString();
    }

    public String movesToSan(List<Move> moves, int maxPlies) {
        Position temp = copy();
        StringBuilder sb = new StringBuilder();
        int plies = 0;
        for (Move move : moves) {
            if (plies >= maxPlies) {
                break;
            }
            Move normalized = temp.normalizeMove(move);
            if (!temp.isMoveLegal(normalized)) {
                break;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            if (temp.sideToMove == Side.WHITE) {
                sb.append(temp.fullmoveNumber).append('.').append(' ');
            } else {
                sb.append(temp.fullmoveNumber).append("...").append(' ');
            }
            sb.append(temp.moveToSan(normalized));
            temp.makeMove(normalized);
            plies++;
        }
        return sb.toString();
    }

    public String historyAsSan(List<String> sanHistory) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sanHistory.size(); i++) {
            if (i % 2 == 0) {
                sb.append((i / 2) + 1).append('.').append(' ');
            }
            sb.append(sanHistory.get(i)).append(' ');
        }
        return sb.toString().trim();
    }

    public List<Piece> promotionPiecesForSide(Side side) {
        return List.of(
                Piece.of(side, PieceType.QUEEN),
                Piece.of(side, PieceType.ROOK),
                Piece.of(side, PieceType.BISHOP),
                Piece.of(side, PieceType.KNIGHT));
    }

    private void appendDisambiguation(StringBuilder sb, Move targetMove, List<Move> legalMoves, PieceType type) {
        boolean sameFile = false;
        boolean sameRank = false;
        boolean hasAlternative = false;
        for (Move move : legalMoves) {
            if (move.equals(targetMove)) {
                continue;
            }
            if (move.to() != targetMove.to()) {
                continue;
            }
            Piece other = board[move.from()];
            if (other.type() != type || other.side() != sideToMove) {
                continue;
            }
            hasAlternative = true;
            if (SquareUtil.file(move.from()) == SquareUtil.file(targetMove.from())) {
                sameFile = true;
            }
            if (SquareUtil.rank(move.from()) == SquareUtil.rank(targetMove.from())) {
                sameRank = true;
            }
        }
        if (!hasAlternative) {
            return;
        }
        if (!sameFile) {
            sb.append((char) ('a' + SquareUtil.file(targetMove.from())));
        } else if (!sameRank) {
            sb.append((char) ('1' + SquareUtil.rank(targetMove.from())));
        } else {
            sb.append((char) ('a' + SquareUtil.file(targetMove.from())));
            sb.append((char) ('1' + SquareUtil.rank(targetMove.from())));
        }
    }

    private String checkSuffixAfterMove() {
        if (isCheckmate()) {
            return "#";
        }
        if (isKingInCheck(sideToMove)) {
            return "+";
        }
        return "";
    }

    private void updateCastlingRightsOnMove(Piece moving, int from, int to, Piece captured, boolean enPassantCapture) {
        if (moving.type() == PieceType.KING) {
            if (moving.side() == Side.WHITE) {
                whiteCastleKing = false;
                whiteCastleQueen = false;
            } else {
                blackCastleKing = false;
                blackCastleQueen = false;
            }
        }
        if (moving.type() == PieceType.ROOK) {
            if (from == SquareUtil.parse("a1")) whiteCastleQueen = false;
            if (from == SquareUtil.parse("h1")) whiteCastleKing = false;
            if (from == SquareUtil.parse("a8")) blackCastleQueen = false;
            if (from == SquareUtil.parse("h8")) blackCastleKing = false;
        }
        if (!enPassantCapture && captured != Piece.NONE && captured.type() == PieceType.ROOK) {
            if (to == SquareUtil.parse("a1")) whiteCastleQueen = false;
            if (to == SquareUtil.parse("h1")) whiteCastleKing = false;
            if (to == SquareUtil.parse("a8")) blackCastleQueen = false;
            if (to == SquareUtil.parse("h8")) blackCastleKing = false;
        }
    }

    private List<Move> generatePseudoLegalMoves(Side side) {
        List<Move> moves = new ArrayList<>(64);
        for (int square = 0; square < 64; square++) {
            Piece piece = board[square];
            if (piece == Piece.NONE || piece.side() != side) {
                continue;
            }
            switch (piece.type()) {
                case PAWN -> generatePawnMoves(square, side, moves);
                case KNIGHT -> generateLeaperMoves(square, side, moves,
                        new int[][]{{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}},
                        false);
                case BISHOP -> generateSliderMoves(square, side, moves,
                        new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}});
                case ROOK -> generateSliderMoves(square, side, moves,
                        new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}});
                case QUEEN -> generateSliderMoves(square, side, moves,
                        new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}});
                case KING -> {
                    generateLeaperMoves(square, side, moves,
                            new int[][]{{1, 1}, {1, 0}, {1, -1}, {0, 1}, {0, -1}, {-1, 1}, {-1, 0}, {-1, -1}},
                            true);
                    generateCastlingMoves(square, side, moves);
                }
            }
        }
        return moves;
    }

    private void generatePawnMoves(int square, Side side, List<Move> moves) {
        int file = SquareUtil.file(square);
        int rank = SquareUtil.rank(square);
        int forward = side == Side.WHITE ? 1 : -1;
        int startRank = side == Side.WHITE ? 1 : 6;
        int promotionRank = side == Side.WHITE ? 6 : 1;

        int oneRank = rank + forward;
        if (SquareUtil.isOnBoard(file, oneRank)) {
            int oneStep = SquareUtil.square(file, oneRank);
            if (board[oneStep] == Piece.NONE) {
                if (rank == promotionRank) {
                    addPromotionMoves(square, oneStep, side, moves);
                } else {
                    moves.add(new Move(square, oneStep));
                    int twoRank = rank + forward * 2;
                    if (rank == startRank && SquareUtil.isOnBoard(file, twoRank)) {
                        int twoStep = SquareUtil.square(file, twoRank);
                        if (board[twoStep] == Piece.NONE) {
                            moves.add(new Move(square, twoStep));
                        }
                    }
                }
            }
        }

        int[] captureFiles = {file - 1, file + 1};
        for (int targetFile : captureFiles) {
            int targetRank = rank + forward;
            if (!SquareUtil.isOnBoard(targetFile, targetRank)) {
                continue;
            }
            int targetSquare = SquareUtil.square(targetFile, targetRank);
            Piece target = board[targetSquare];
            boolean capture = target != Piece.NONE && target.side() != side;
            boolean enPassant = targetSquare == enPassantSquare;
            if (capture || enPassant) {
                if (rank == promotionRank) {
                    addPromotionMoves(square, targetSquare, side, moves);
                } else {
                    moves.add(new Move(square, targetSquare));
                }
            }
        }
    }

    private void addPromotionMoves(int from, int to, Side side, List<Move> moves) {
        moves.add(new Move(from, to, Piece.of(side, PieceType.QUEEN)));
        moves.add(new Move(from, to, Piece.of(side, PieceType.ROOK)));
        moves.add(new Move(from, to, Piece.of(side, PieceType.BISHOP)));
        moves.add(new Move(from, to, Piece.of(side, PieceType.KNIGHT)));
    }

    private void generateLeaperMoves(int square, Side side, List<Move> moves, int[][] offsets, boolean king) {
        int file = SquareUtil.file(square);
        int rank = SquareUtil.rank(square);
        for (int[] offset : offsets) {
            int nf = file + offset[0];
            int nr = rank + offset[1];
            if (!SquareUtil.isOnBoard(nf, nr)) {
                continue;
            }
            int targetSquare = SquareUtil.square(nf, nr);
            Piece target = board[targetSquare];
            if (target == Piece.NONE || target.side() != side) {
                moves.add(new Move(square, targetSquare));
            }
        }
    }

    private void generateSliderMoves(int square, Side side, List<Move> moves, int[][] directions) {
        int file = SquareUtil.file(square);
        int rank = SquareUtil.rank(square);
        for (int[] dir : directions) {
            int nf = file + dir[0];
            int nr = rank + dir[1];
            while (SquareUtil.isOnBoard(nf, nr)) {
                int targetSquare = SquareUtil.square(nf, nr);
                Piece target = board[targetSquare];
                if (target == Piece.NONE) {
                    moves.add(new Move(square, targetSquare));
                } else {
                    if (target.side() != side) {
                        moves.add(new Move(square, targetSquare));
                    }
                    break;
                }
                nf += dir[0];
                nr += dir[1];
            }
        }
    }

    private void generateCastlingMoves(int square, Side side, List<Move> moves) {
        int homeSquare = side == Side.WHITE ? SquareUtil.parse("e1") : SquareUtil.parse("e8");
        if (square != homeSquare) {
            return;
        }
        if (isKingInCheck(side)) {
            return;
        }
        if (side == Side.WHITE) {
            if (whiteCastleKing
                    && board[SquareUtil.parse("f1")] == Piece.NONE
                    && board[SquareUtil.parse("g1")] == Piece.NONE
                    && board[SquareUtil.parse("h1")] == Piece.WHITE_ROOK
                    && !isSquareAttacked(SquareUtil.parse("f1"), Side.BLACK)
                    && !isSquareAttacked(SquareUtil.parse("g1"), Side.BLACK)) {
                moves.add(new Move(square, SquareUtil.parse("g1")));
            }
            if (whiteCastleQueen
                    && board[SquareUtil.parse("d1")] == Piece.NONE
                    && board[SquareUtil.parse("c1")] == Piece.NONE
                    && board[SquareUtil.parse("b1")] == Piece.NONE
                    && board[SquareUtil.parse("a1")] == Piece.WHITE_ROOK
                    && !isSquareAttacked(SquareUtil.parse("d1"), Side.BLACK)
                    && !isSquareAttacked(SquareUtil.parse("c1"), Side.BLACK)) {
                moves.add(new Move(square, SquareUtil.parse("c1")));
            }
        } else {
            if (blackCastleKing
                    && board[SquareUtil.parse("f8")] == Piece.NONE
                    && board[SquareUtil.parse("g8")] == Piece.NONE
                    && board[SquareUtil.parse("h8")] == Piece.BLACK_ROOK
                    && !isSquareAttacked(SquareUtil.parse("f8"), Side.WHITE)
                    && !isSquareAttacked(SquareUtil.parse("g8"), Side.WHITE)) {
                moves.add(new Move(square, SquareUtil.parse("g8")));
            }
            if (blackCastleQueen
                    && board[SquareUtil.parse("d8")] == Piece.NONE
                    && board[SquareUtil.parse("c8")] == Piece.NONE
                    && board[SquareUtil.parse("b8")] == Piece.NONE
                    && board[SquareUtil.parse("a8")] == Piece.BLACK_ROOK
                    && !isSquareAttacked(SquareUtil.parse("d8"), Side.WHITE)
                    && !isSquareAttacked(SquareUtil.parse("c8"), Side.WHITE)) {
                moves.add(new Move(square, SquareUtil.parse("c8")));
            }
        }
    }

    private boolean kingsAdjacent(int whiteKing, int blackKing) {
        return Math.abs(SquareUtil.file(whiteKing) - SquareUtil.file(blackKing)) <= 1
                && Math.abs(SquareUtil.rank(whiteKing) - SquareUtil.rank(blackKing)) <= 1;
    }

    private StateSnapshot snapshot() {
        return new StateSnapshot(board.clone(), sideToMove, whiteCastleKing, whiteCastleQueen,
                blackCastleKing, blackCastleQueen, enPassantSquare, halfmoveClock, fullmoveNumber);
    }

    private void restore(StateSnapshot snapshot) {
        System.arraycopy(snapshot.board, 0, board, 0, 64);
        this.sideToMove = snapshot.sideToMove;
        this.whiteCastleKing = snapshot.whiteCastleKing;
        this.whiteCastleQueen = snapshot.whiteCastleQueen;
        this.blackCastleKing = snapshot.blackCastleKing;
        this.blackCastleQueen = snapshot.blackCastleQueen;
        this.enPassantSquare = snapshot.enPassantSquare;
        this.halfmoveClock = snapshot.halfmoveClock;
        this.fullmoveNumber = snapshot.fullmoveNumber;
    }

    private record StateSnapshot(
            Piece[] board,
            Side sideToMove,
            boolean whiteCastleKing,
            boolean whiteCastleQueen,
            boolean blackCastleKing,
            boolean blackCastleQueen,
            int enPassantSquare,
            int halfmoveClock,
            int fullmoveNumber) {
    }

}
