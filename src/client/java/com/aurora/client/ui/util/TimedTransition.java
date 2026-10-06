package com.aurora.client.ui.util;

/** Finite CSS-style transition. Retarget from the actual visible value;
 * reverse legs shorten in proportion to remaining distance. Monotonic clock
 * is supplied explicitly so interrupted trajectories are deterministic. */
public final class TimedTransition {
    private final long durationNs;
    private final CubicBezier curve;
    private double start, target;
    private long started, legNs;
    private boolean initialized;

    public TimedTransition(long durationMs, CubicBezier curve) {
        this.durationNs = durationMs * 1_000_000;
        this.curve = curve;
    }
    public float update(double next, long now) {
        if (!initialized) { initialized = true; start = target = next; started = now; }
        double current = value(now);
        if (next != target) {
            start = current; target = next; started = now;
            legNs = Math.max(1, Math.round(durationNs * Math.abs(target - start)));
        }
        return (float)value(now);
    }
    public double value(long now) {
        if (legNs == 0 || now - started >= legNs) return target;
        return start + (target-start) * curve.at(Math.max(0, now-started)/(double)legNs);
    }
}
