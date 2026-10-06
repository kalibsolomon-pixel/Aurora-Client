package com.aurora.client.util.reflex;

/** Conservative measured pacing: no hardware-name heuristics or user tuning. */
public final class AutomaticReflexPolicy {
    private int invalidSamples;
    private int congestedFrames;
    private boolean failed;

    public boolean acceptGpuSample(long nanos) {
        if (nanos <= 0 || nanos > 250_000_000L) {
            if (++invalidSamples >= 30) failed = true;
            return false;
        }
        invalidSamples = 0;
        return true;
    }

    public void observeQueue(int pending) {
        congestedFrames = pending > 8 ? congestedFrames + 1 : 0;
        if (congestedFrames >= 120) failed = true;
    }

    public boolean failed() { return failed; }

    /** Warm up first; pace only GPU-bound queued work, keeping a 10% safety margin. */
    public static long waitBudget(long cpu, long gpu, int samples, int pending) {
        if (samples < 30 || pending < 2 || cpu <= 0 || gpu <= cpu) return 0;
        return Math.clamp(gpu - cpu - gpu / 10, 0, 2_000_000L);
    }
}
