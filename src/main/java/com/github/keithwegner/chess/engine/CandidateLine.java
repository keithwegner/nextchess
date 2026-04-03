package com.github.keithwegner.chess.engine;

import com.github.keithwegner.chess.Move;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CandidateLine {
    private final Move move;
    private final String sanMove;
    private final String evalText;
    private final Integer scoreCpWhite;
    private final Integer mateWhite;
    private final List<Move> pv;
    private final String pvSan;
    private final int depth;
    private final Long nodes;
    private final Long nps;

    public CandidateLine(Move move,
                         String sanMove,
                         String evalText,
                         Integer scoreCpWhite,
                         Integer mateWhite,
                         List<Move> pv,
                         String pvSan,
                         int depth,
                         Long nodes,
                         Long nps) {
        this.move = move;
        this.sanMove = sanMove;
        this.evalText = evalText;
        this.scoreCpWhite = scoreCpWhite;
        this.mateWhite = mateWhite;
        this.pv = Collections.unmodifiableList(new ArrayList<>(pv));
        this.pvSan = pvSan;
        this.depth = depth;
        this.nodes = nodes;
        this.nps = nps;
    }

    public Move move() {
        return move;
    }

    public String sanMove() {
        return sanMove;
    }

    public String evalText() {
        return evalText;
    }

    public Integer scoreCpWhite() {
        return scoreCpWhite;
    }

    public Integer mateWhite() {
        return mateWhite;
    }

    public List<Move> pv() {
        return pv;
    }

    public String pvSan() {
        return pvSan;
    }

    public int depth() {
        return depth;
    }

    public Long nodes() {
        return nodes;
    }

    public Long nps() {
        return nps;
    }
}
