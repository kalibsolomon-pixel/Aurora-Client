package com.aurora.client.diagnostics;

/**
 * Running total of the time the render thread spends inside Aurora's own
 * entry points: its HUD, world-render and client-tick callbacks (ADR 0005).
 *
 * <p>Each entry point brackets its body with {@link #begin} and {@link #end};
 * {@link FrameBenchmark} reads the total once per frame. Waiting is not cost:
 * frame pacing and Reflex waits are outside every bracket. Mixin hooks inside
 * vanilla methods are not bracketed either (a timer per hook would cost more
 * than most hooks do); a profiler run measures how much that leaves out.
 *
 * <p>Render thread only, so a plain field. Always on: one clock read per
 * bracket edge, a few dozen per frame.
 */
public final class AuroraCost {
    private AuroraCost() {}

    private static long totalNanos;
    private static int depth;

    /** Start of an Aurora entry point. Pass the result to {@link #end}. */
    public static long begin() {
        return depth++ == 0 ? System.nanoTime() : 0L;
    }

    /** End of the entry point started by {@code start}. Nested brackets count once. */
    public static void end(long start) {
        if (--depth == 0) totalNanos += System.nanoTime() - start;
    }

    /** Nanoseconds spent inside entry points since the game started. */
    public static long totalNanos() {
        return totalNanos;
    }
}
