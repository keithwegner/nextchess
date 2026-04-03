package com.github.keithwegner.chess.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AnalysisResult {
    private final String engineName;
    private final EngineConfig.Mode modeUsed;
    private final List<CandidateLine> lines;
    private final String note;
    private final String sourceFen;

    public AnalysisResult(String engineName,
                          EngineConfig.Mode modeUsed,
                          List<CandidateLine> lines,
                          String note,
                          String sourceFen) {
        this.engineName = engineName;
        this.modeUsed = modeUsed;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        this.note = note == null ? "" : note;
        this.sourceFen = sourceFen == null ? "" : sourceFen;
    }

    public String engineName() {
        return engineName;
    }

    public EngineConfig.Mode modeUsed() {
        return modeUsed;
    }

    public List<CandidateLine> lines() {
        return lines;
    }

    public String note() {
        return note;
    }

    public String sourceFen() {
        return sourceFen;
    }

    public CandidateLine bestLine() {
        return lines.isEmpty() ? null : lines.get(0);
    }
}
