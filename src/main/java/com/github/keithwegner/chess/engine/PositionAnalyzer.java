package com.github.keithwegner.chess.engine;

import com.github.keithwegner.chess.Move;
import com.github.keithwegner.chess.Position;

import java.util.ArrayList;
import java.util.List;

public final class PositionAnalyzer {
    private final MiniEngine miniEngine = new MiniEngine();
    private final UciEngineClient uciEngineClient = new UciEngineClient();

    public AnalysisResult analyze(Position position, EngineConfig config) {
        Position snapshot = position.copy();
        if (config.mode() == EngineConfig.Mode.UCI && !config.enginePath().isBlank()) {
            try {
                return uciEngineClient.analyze(snapshot, config);
            } catch (Exception ex) {
                AnalysisResult fallback = analyzeBuiltin(snapshot, config);
                return new AnalysisResult(
                        fallback.engineName(),
                        fallback.modeUsed(),
                        fallback.lines(),
                        "External engine failed (" + ex.getMessage() + "); used built-in mini engine instead.",
                        fallback.sourceFen());
            }
        }
        return analyzeBuiltin(snapshot, config);
    }

    private AnalysisResult analyzeBuiltin(Position position, EngineConfig config) {
        String sourceFen = position.toFen();
        MiniEngine.MiniEngineResult result = miniEngine.analyze(position, config.depth(), config.multiPv(), config.thinkTimeSeconds());
        List<CandidateLine> lines = new ArrayList<>();
        for (MiniEngine.MiniEngineLine line : result.lines()) {
            List<Move> pv = line.pv();
            lines.add(new CandidateLine(
                    line.move(),
                    position.moveToSan(line.move()),
                    EngineSupport.formatScore(line.whiteScoreCp(), line.mateWhite()),
                    line.whiteScoreCp(),
                    line.mateWhite(),
                    pv,
                    position.movesToSan(pv, 12),
                    line.depth(),
                    line.nodes(),
                    null));
        }
        return new AnalysisResult(result.engineName(), EngineConfig.Mode.BUILTIN, lines, "", sourceFen);
    }
}
