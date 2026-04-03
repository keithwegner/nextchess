package com.github.keithwegner.chess.engine;

public final class EngineConfig {
    public enum Mode {
        BUILTIN,
        UCI
    }

    private Mode mode = Mode.BUILTIN;
    private String enginePath = "";
    private double thinkTimeSeconds = 2.0;
    private int depth = 4;
    private int multiPv = 3;
    private int threads = 2;
    private int hashMb = 128;

    public Mode mode() {
        return mode;
    }

    public EngineConfig setMode(Mode mode) {
        this.mode = mode == null ? Mode.BUILTIN : mode;
        return this;
    }

    public String enginePath() {
        return enginePath;
    }

    public EngineConfig setEnginePath(String enginePath) {
        this.enginePath = enginePath == null ? "" : enginePath.trim();
        return this;
    }

    public double thinkTimeSeconds() {
        return thinkTimeSeconds;
    }

    public EngineConfig setThinkTimeSeconds(double thinkTimeSeconds) {
        this.thinkTimeSeconds = Math.max(0.05, thinkTimeSeconds);
        return this;
    }

    public int depth() {
        return depth;
    }

    public EngineConfig setDepth(int depth) {
        this.depth = Math.max(1, depth);
        return this;
    }

    public int multiPv() {
        return multiPv;
    }

    public EngineConfig setMultiPv(int multiPv) {
        this.multiPv = Math.max(1, Math.min(5, multiPv));
        return this;
    }

    public int threads() {
        return threads;
    }

    public EngineConfig setThreads(int threads) {
        this.threads = Math.max(1, threads);
        return this;
    }

    public int hashMb() {
        return hashMb;
    }

    public EngineConfig setHashMb(int hashMb) {
        this.hashMb = Math.max(1, hashMb);
        return this;
    }
}
