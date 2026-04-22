package com.github.keithwegner.chess.engine;

import com.github.keithwegner.chess.Move;
import com.github.keithwegner.chess.Piece;
import com.github.keithwegner.chess.PieceType;
import com.github.keithwegner.chess.Position;
import com.github.keithwegner.chess.Side;
import com.github.keithwegner.chess.SquareUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MiniEngine {
    private static final int MATE_SCORE = 100_000;
    private static final int INFINITY = 1_000_000;

    private static final Map<PieceType, Integer> PIECE_VALUES = new HashMap<>();

    static {
        PIECE_VALUES.put(PieceType.PAWN, 100);
        PIECE_VALUES.put(PieceType.KNIGHT, 320);
        PIECE_VALUES.put(PieceType.BISHOP, 335);
        PIECE_VALUES.put(PieceType.ROOK, 500);
        PIECE_VALUES.put(PieceType.QUEEN, 900);
        PIECE_VALUES.put(PieceType.KING, 0);
    }

    private long startNanos;
    private long timeLimitNanos;
    private long nodes;

    public MiniEngineResult analyze(Position position, int depth, int multiPv, double timeLimitSeconds) {
        if (!position.isAnalyzable()) {
            return new MiniEngineResult("Built-in Mini Engine", 0, 0L, List.of());
        }
        this.startNanos = System.nanoTime();
        this.timeLimitNanos = Math.max(50_000_000L, (long) (timeLimitSeconds * 1_000_000_000L));
        this.nodes = 0L;

        List<Move> legalMoves = position.generateLegalMoves();
        if (legalMoves.isEmpty()) {
            return new MiniEngineResult("Built-in Mini Engine", 0, 0L, List.of());
        }

        int cappedDepth = Math.max(1, Math.min(depth, 6));
        int cappedMultiPv = Math.max(1, Math.min(multiPv, 5));
        int completedDepth = 0;
        long completedNodes = 0L;
        List<MiniEngineLine> latestLines = List.of();

        for (int currentDepth = 1; currentDepth <= cappedDepth; currentDepth++) {
            try {
                List<MiniEngineLine> lines = searchRoot(position, currentDepth, cappedMultiPv);
                if (!lines.isEmpty()) {
                    latestLines = lines;
                    completedDepth = currentDepth;
                    completedNodes = nodes;
                }
            } catch (SearchTimeout timeout) {
                break;
            }
            if (System.nanoTime() - startNanos >= timeLimitNanos) {
                break;
            }
        }

        if (latestLines.isEmpty()) {
            List<MiniEngineLine> fallback = new ArrayList<>();
            Side mover = position.sideToMove();
            for (Move move : orderedMoves(position, legalMoves)) {
                position.makeMove(move);
                int scoreStm;
                try {
                    scoreStm = -evaluateStm(position);
                } finally {
                    position.undoMove();
                }
                int whiteScore = mover == Side.WHITE ? scoreStm : -scoreStm;
                fallback.add(new MiniEngineLine(
                        move,
                        scoreStm,
                        whiteScore,
                        null,
                        List.of(move),
                        1,
                        nodes));
            }
            fallback.sort(Comparator.comparingInt(MiniEngineLine::rootScoreStm).reversed());
            latestLines = fallback.stream().limit(cappedMultiPv).toList();
            completedDepth = 1;
            completedNodes = nodes;
        }

        return new MiniEngineResult("Built-in Mini Engine", completedDepth, completedNodes, latestLines);
    }

    private List<MiniEngineLine> searchRoot(Position position, int depth, int multiPv) throws SearchTimeout {
        Side rootSide = position.sideToMove();
        List<MiniEngineLine> lines = new ArrayList<>();
        int alpha = -INFINITY;
        int beta = INFINITY;

        for (Move move : orderedMoves(position, position.generateLegalMoves())) {
            checkTime();
            position.makeMove(move);
            SearchResult child;
            try {
                child = negamax(position, depth - 1, -beta, -alpha, 1);
            } finally {
                position.undoMove();
            }

            int score = -child.score();
            int whiteScore = rootSide == Side.WHITE ? score : -score;
            Integer mateWhite = scoreToMateWhite(whiteScore);
            Integer whiteCp = mateWhite == null ? whiteScore : null;
            List<Move> pv = new ArrayList<>();
            pv.add(move);
            pv.addAll(child.pv());
            lines.add(new MiniEngineLine(move, score, whiteCp, mateWhite, pv, depth, nodes));
            alpha = Math.max(alpha, score);
        }
        lines.sort(Comparator.comparingInt(MiniEngineLine::rootScoreStm).reversed());
        return lines.size() > multiPv ? new ArrayList<>(lines.subList(0, multiPv)) : lines;
    }

    private SearchResult negamax(Position position, int depth, int alpha, int beta, int ply) throws SearchTimeout {
        checkTime();
        nodes++;

        List<Move> legalMoves = position.generateLegalMoves();
        if (legalMoves.isEmpty()) {
            if (position.isKingInCheck(position.sideToMove())) {
                return new SearchResult(-MATE_SCORE + ply, List.of());
            }
            return new SearchResult(0, List.of());
        }
        if (position.halfmoveClock() >= 100 || position.isInsufficientMaterial()) {
            return new SearchResult(0, List.of());
        }
        if (depth <= 0) {
            return quiescence(position, alpha, beta, ply);
        }

        int bestScore = -INFINITY;
        List<Move> bestPv = List.of();
        for (Move move : orderedMoves(position, legalMoves)) {
            position.makeMove(move);
            SearchResult child;
            try {
                child = negamax(position, depth - 1, -beta, -alpha, ply + 1);
            } finally {
                position.undoMove();
            }
            int score = -child.score();
            if (score > bestScore) {
                bestScore = score;
                List<Move> pv = new ArrayList<>();
                pv.add(move);
                pv.addAll(child.pv());
                bestPv = pv;
            }
            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) {
                break;
            }
        }
        return new SearchResult(bestScore, bestPv);
    }

    private SearchResult quiescence(Position position, int alpha, int beta, int ply) throws SearchTimeout {
        checkTime();
        nodes++;

        List<Move> legalMoves = position.generateLegalMoves();
        if (legalMoves.isEmpty()) {
            if (position.isKingInCheck(position.sideToMove())) {
                return new SearchResult(-MATE_SCORE + ply, List.of());
            }
            return new SearchResult(0, List.of());
        }
        if (position.halfmoveClock() >= 100 || position.isInsufficientMaterial()) {
            return new SearchResult(0, List.of());
        }

        if (position.isKingInCheck(position.sideToMove())) {
            int bestScore = -INFINITY;
            List<Move> bestPv = List.of();
            for (Move move : orderedMoves(position, legalMoves)) {
                position.makeMove(move);
                SearchResult child;
                try {
                    child = quiescence(position, -beta, -alpha, ply + 1);
                } finally {
                    position.undoMove();
                }
                int score = -child.score();
                if (score > bestScore) {
                    bestScore = score;
                    List<Move> pv = new ArrayList<>();
                    pv.add(move);
                    pv.addAll(child.pv());
                    bestPv = pv;
                }
                if (score > alpha) {
                    alpha = score;
                }
                if (alpha >= beta) {
                    break;
                }
            }
            return new SearchResult(bestScore, bestPv);
        }

        int standPat = evaluateStm(position);
        if (standPat >= beta) {
            return new SearchResult(beta, List.of());
        }
        if (standPat > alpha) {
            alpha = standPat;
        }

        int bestScore = standPat;
        List<Move> bestPv = List.of();
        for (Move move : orderedQuiescenceMoves(position, legalMoves)) {
            position.makeMove(move);
            SearchResult child;
            try {
                child = quiescence(position, -beta, -alpha, ply + 1);
            } finally {
                position.undoMove();
            }
            int score = -child.score();
            if (score > bestScore) {
                bestScore = score;
                List<Move> pv = new ArrayList<>();
                pv.add(move);
                pv.addAll(child.pv());
                bestPv = pv;
            }
            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) {
                break;
            }
        }
        return new SearchResult(bestScore, bestPv);
    }

    private List<Move> orderedMoves(Position position, List<Move> moves) {
        List<Move> ordered = new ArrayList<>(moves);
        ordered.sort(Comparator.comparingInt((Move move) -> moveOrderScore(position, move)).reversed());
        return ordered;
    }

    private List<Move> orderedQuiescenceMoves(Position position, List<Move> legalMoves) {
        List<Move> forcing = new ArrayList<>();
        for (Move move : legalMoves) {
            Piece moving = position.pieceAt(move.from());
            Piece target = position.pieceAt(move.to());
            boolean enPassant = moving.type() == PieceType.PAWN
                    && move.to() == position.enPassantSquare()
                    && target == Piece.NONE
                    && SquareUtil.file(move.from()) != SquareUtil.file(move.to());
            if (target != Piece.NONE || move.isPromotion() || enPassant) {
                forcing.add(move);
            }
        }
        return orderedMoves(position, forcing);
    }

    private int moveOrderScore(Position position, Move move) {
        Piece moving = position.pieceAt(move.from());
        Piece target = position.pieceAt(move.to());
        int score = 0;
        if (move.isPromotion()) {
            score += 20_000 + PIECE_VALUES.getOrDefault(move.promotion().type(), 0);
        }
        boolean enPassant = moving.type() == PieceType.PAWN
                && move.to() == position.enPassantSquare()
                && target == Piece.NONE
                && SquareUtil.file(move.from()) != SquareUtil.file(move.to());
        if (target != Piece.NONE || enPassant) {
            int victimValue = enPassant ? PIECE_VALUES.get(PieceType.PAWN) : PIECE_VALUES.getOrDefault(target.type(), 0);
            int attackerValue = PIECE_VALUES.getOrDefault(moving.type(), 0);
            score += 15_000 + victimValue * 16 - attackerValue;
        }
        if (moving.type() == PieceType.KING && Math.abs(SquareUtil.file(move.to()) - SquareUtil.file(move.from())) == 2) {
            score += 1_000;
        }
        score += squareBonus(moving.type(), moving.side(), move.to(), position, 0.5);
        return score;
    }

    private void checkTime() throws SearchTimeout {
        if (System.nanoTime() - startNanos > timeLimitNanos) {
            throw new SearchTimeout();
        }
    }

    private int evaluateStm(Position position) {
        int whiteEval = evaluateWhite(position);
        return position.sideToMove() == Side.WHITE ? whiteEval : -whiteEval;
    }

    private int evaluateWhite(Position position) {
        List<Move> legalMoves = position.generateLegalMoves();
        if (legalMoves.isEmpty()) {
            if (position.isKingInCheck(position.sideToMove())) {
                return position.sideToMove() == Side.WHITE ? -MATE_SCORE : MATE_SCORE;
            }
            return 0;
        }
        if (position.halfmoveClock() >= 100 || position.isInsufficientMaterial()) {
            return 0;
        }

        int nonPawnMaterial = 0;
        for (int square = 0; square < 64; square++) {
            Piece piece = position.pieceAt(square);
            if (piece == Piece.NONE || piece.type() == PieceType.PAWN || piece.type() == PieceType.KING) {
                continue;
            }
            nonPawnMaterial += PIECE_VALUES.getOrDefault(piece.type(), 0);
        }
        double phase = Math.min(1.0, nonPawnMaterial / 6400.0);

        int score = 0;
        for (int square = 0; square < 64; square++) {
            Piece piece = position.pieceAt(square);
            if (piece == Piece.NONE) {
                continue;
            }
            int sign = piece.side() == Side.WHITE ? 1 : -1;
            score += sign * PIECE_VALUES.getOrDefault(piece.type(), 0);
            score += sign * squareBonus(piece.type(), piece.side(), square, position, phase);
        }

        score += pawnStructure(position, Side.WHITE);
        score -= pawnStructure(position, Side.BLACK);
        score += bishopPairBonus(position, Side.WHITE);
        score -= bishopPairBonus(position, Side.BLACK);
        score += rookFileBonus(position, Side.WHITE);
        score -= rookFileBonus(position, Side.BLACK);
        score += kingSafety(position, Side.WHITE, phase);
        score -= kingSafety(position, Side.BLACK, phase);
        score += mobilityBonus(position, Side.WHITE);
        score -= mobilityBonus(position, Side.BLACK);
        score += position.sideToMove() == Side.WHITE ? 12 : -12;
        return score;
    }

    private int squareBonus(PieceType type, Side side, int square, Position position, double phase) {
        int file = SquareUtil.file(square);
        int rank = SquareUtil.rank(square);
        int rankFromSide = side == Side.WHITE ? rank : 7 - rank;
        double centerDistance = Math.abs(file - 3.5) + Math.abs(rankFromSide - 3.5);

        return switch (type) {
            case PAWN -> {
                int bonus = rankFromSide * 11 - (int) (Math.abs(file - 3.5) * 4);
                if ((file >= 2 && file <= 5) && (rankFromSide == 3 || rankFromSide == 4)) {
                    bonus += 10;
                }
                yield bonus;
            }
            case KNIGHT -> (int) (42 - centerDistance * 12) - rankFromSide * 2;
            case BISHOP -> (int) (28 - centerDistance * 7) + rankFromSide * 2;
            case ROOK -> rankFromSide * 4 + (rankFromSide == 6 ? 14 : 0);
            case QUEEN -> (int) (18 - centerDistance * 4);
            case KING -> {
                if (phase >= 0.5) {
                    int castledBonus = ((file == 1 || file == 2 || file == 5 || file == 6) && rankFromSide <= 1) ? 24 : 0;
                    yield castledBonus - (int) (centerDistance * 16);
                }
                yield (int) (36 - centerDistance * 10);
            }
        };
    }

    private int pawnStructure(Position position, Side side) {
        List<Integer> pawns = pieceSquares(position, Piece.of(side, PieceType.PAWN));
        if (pawns.isEmpty()) {
            return 0;
        }
        int[] fileCounts = new int[8];
        for (int pawn : pawns) {
            fileCounts[SquareUtil.file(pawn)]++;
        }

        int score = 0;
        for (int pawn : pawns) {
            int file = SquareUtil.file(pawn);
            int rank = SquareUtil.rank(pawn);
            int rankFromSide = side == Side.WHITE ? rank : 7 - rank;

            if (fileCounts[file] > 1) {
                score -= 12 * (fileCounts[file] - 1);
            }

            boolean hasNeighbor = (file > 0 && fileCounts[file - 1] > 0) || (file < 7 && fileCounts[file + 1] > 0);
            if (!hasNeighbor) {
                score -= 14;
            }
            if (isPassedPawn(position, pawn, side)) {
                score += 18 + rankFromSide * 12;
            }
            if (isConnectedPawn(position, pawn, side)) {
                score += 8;
            }
        }
        return score;
    }

    private boolean isPassedPawn(Position position, int pawnSquare, Side side) {
        int file = SquareUtil.file(pawnSquare);
        int rank = SquareUtil.rank(pawnSquare);
        for (int enemyPawn : pieceSquares(position, Piece.of(side.opposite(), PieceType.PAWN))) {
            int ef = SquareUtil.file(enemyPawn);
            int er = SquareUtil.rank(enemyPawn);
            if (Math.abs(ef - file) > 1) {
                continue;
            }
            if (side == Side.WHITE && er > rank) {
                return false;
            }
            if (side == Side.BLACK && er < rank) {
                return false;
            }
        }
        return true;
    }

    private boolean isConnectedPawn(Position position, int pawnSquare, Side side) {
        int file = SquareUtil.file(pawnSquare);
        int rank = SquareUtil.rank(pawnSquare);
        for (int df = -1; df <= 1; df += 2) {
            int nf = file + df;
            if (nf < 0 || nf > 7) {
                continue;
            }
            for (int dr = -1; dr <= 1; dr++) {
                int nr = rank + dr;
                if (!SquareUtil.isOnBoard(nf, nr)) {
                    continue;
                }
                Piece neighbor = position.pieceAt(SquareUtil.square(nf, nr));
                if (neighbor == Piece.of(side, PieceType.PAWN)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int bishopPairBonus(Position position, Side side) {
        return pieceSquares(position, Piece.of(side, PieceType.BISHOP)).size() >= 2 ? 32 : 0;
    }

    private int rookFileBonus(Position position, Side side) {
        int score = 0;
        List<Integer> ownPawns = pieceSquares(position, Piece.of(side, PieceType.PAWN));
        List<Integer> enemyPawns = pieceSquares(position, Piece.of(side.opposite(), PieceType.PAWN));
        for (int rookSquare : pieceSquares(position, Piece.of(side, PieceType.ROOK))) {
            int file = SquareUtil.file(rookSquare);
            boolean ownOnFile = ownPawns.stream().anyMatch(p -> SquareUtil.file(p) == file);
            boolean enemyOnFile = enemyPawns.stream().anyMatch(p -> SquareUtil.file(p) == file);
            if (!ownOnFile && !enemyOnFile) {
                score += 20;
            } else if (!ownOnFile) {
                score += 10;
            }
        }
        return score;
    }

    private int kingSafety(Position position, Side side, double phase) {
        int kingSquare = position.findKing(side);
        if (kingSquare < 0) {
            return -5000;
        }
        int file = SquareUtil.file(kingSquare);
        int rank = SquareUtil.rank(kingSquare);
        int rankFromSide = side == Side.WHITE ? rank : 7 - rank;
        if (phase < 0.5) {
            double centerDistance = Math.abs(file - 3.5) + Math.abs(rankFromSide - 3.5);
            return (int) (30 - centerDistance * 10);
        }
        int score = 0;
        if ((file == 1 || file == 2 || file == 5 || file == 6) && rankFromSide <= 1) {
            score += 28;
        }
        if (rankFromSide > 1) {
            score -= 18;
        }
        int forward = side == Side.WHITE ? 1 : -1;
        for (int df = -1; df <= 1; df++) {
            int shieldFile = file + df;
            int shieldRank = rank + forward;
            if (SquareUtil.isOnBoard(shieldFile, shieldRank)) {
                Piece piece = position.pieceAt(SquareUtil.square(shieldFile, shieldRank));
                if (piece == Piece.of(side, PieceType.PAWN)) {
                    score += 10;
                }
            }
        }
        return score;
    }

    private int mobilityBonus(Position position, Side side) {
        int score = 0;
        for (int square = 0; square < 64; square++) {
            Piece piece = position.pieceAt(square);
            if (piece == Piece.NONE || piece.side() != side) {
                continue;
            }
            switch (piece.type()) {
                case KNIGHT -> score += countKnightTargets(position, square, side);
                case BISHOP -> score += countSlidingTargets(position, square, side, new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}});
                case ROOK -> score += countSlidingTargets(position, square, side, new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}});
                case QUEEN -> score += countSlidingTargets(position, square, side,
                        new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}});
                default -> {
                }
            }
        }
        return score * 2;
    }

    private int countKnightTargets(Position position, int square, Side side) {
        int[][] offsets = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
        int file = SquareUtil.file(square);
        int rank = SquareUtil.rank(square);
        int count = 0;
        for (int[] offset : offsets) {
            int nf = file + offset[0];
            int nr = rank + offset[1];
            if (!SquareUtil.isOnBoard(nf, nr)) {
                continue;
            }
            Piece target = position.pieceAt(SquareUtil.square(nf, nr));
            if (target == Piece.NONE || target.side() != side) {
                count++;
            }
        }
        return count;
    }

    private int countSlidingTargets(Position position, int square, Side side, int[][] directions) {
        int file = SquareUtil.file(square);
        int rank = SquareUtil.rank(square);
        int count = 0;
        for (int[] dir : directions) {
            int nf = file + dir[0];
            int nr = rank + dir[1];
            while (SquareUtil.isOnBoard(nf, nr)) {
                Piece target = position.pieceAt(SquareUtil.square(nf, nr));
                if (target == Piece.NONE) {
                    count++;
                } else {
                    if (target.side() != side) {
                        count++;
                    }
                    break;
                }
                nf += dir[0];
                nr += dir[1];
            }
        }
        return count;
    }

    private List<Integer> pieceSquares(Position position, Piece piece) {
        List<Integer> squares = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            if (position.pieceAt(i) == piece) {
                squares.add(i);
            }
        }
        return squares;
    }

    private Integer scoreToMateWhite(int whiteScore) {
        if (Math.abs(whiteScore) < MATE_SCORE - 512) {
            return null;
        }
        int plies = Math.max(1, MATE_SCORE - Math.abs(whiteScore));
        int moves = Math.max(1, (plies + 1) / 2);
        return whiteScore > 0 ? moves : -moves;
    }

    private record SearchResult(int score, List<Move> pv) {
    }

    public record MiniEngineLine(
            Move move,
            int rootScoreStm,
            Integer whiteScoreCp,
            Integer mateWhite,
            List<Move> pv,
            int depth,
            long nodes) {
    }

    public record MiniEngineResult(String engineName, int depth, long nodes, List<MiniEngineLine> lines) {
    }

    public static final class SearchTimeout extends Exception {
    }
}
