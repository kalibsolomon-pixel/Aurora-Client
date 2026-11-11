package com.aurora.client.diagnostics;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * The short benchmark inside the diagnostics report (ADR 0004): frame times
 * over a few seconds of whatever is on screen.
 *
 * <p>{@link #onFrame} is called once per rendered frame from the frame-head
 * mixin. While no benchmark is running it reads one volatile flag and
 * returns. A run discards a warm-up period, records frame intervals into a
 * fixed buffer, and hands the {@link Stats} to its callback on the render
 * thread. No Minecraft imports.
 */
public final class FrameBenchmark {
    private FrameBenchmark() {}

    /** Enough for the full run at 2000 fps; a faster run simply ends early. */
    private static final int CAPACITY = 16_384;

    /** What the run measured. Times are milliseconds. */
    public record Stats(int frames, double seconds, double averageFps, double onePercentLowFps,
                        double medianMs, double p95Ms, double p99Ms, double maxMs, double fillsPerFrame) {

        /** Summarize frame intervals (nanoseconds). Null when there is nothing to summarize. */
        public static Stats of(long[] intervalsNanos, int count, long fills) {
            if (intervalsNanos == null || count <= 0) return null;
            long[] sorted = Arrays.copyOf(intervalsNanos, count);
            Arrays.sort(sorted);
            long total = 0;
            for (long v : sorted) total += v;
            if (total <= 0) return null;
            // 1% low: the average frame rate over the slowest 1% of frames (at least one).
            int slowCount = Math.max(1, count / 100);
            long slowTotal = 0;
            for (int i = count - slowCount; i < count; i++) slowTotal += sorted[i];
            double seconds = total / 1e9;
            return new Stats(count, seconds, count / seconds, slowCount / (slowTotal / 1e9),
                    percentile(sorted, 0.50) / 1e6, percentile(sorted, 0.95) / 1e6,
                    percentile(sorted, 0.99) / 1e6, sorted[count - 1] / 1e6, (double) fills / count);
        }

        private static double percentile(long[] sorted, double p) {
            int index = (int) Math.ceil(p * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
        }
    }

    private static volatile boolean active;
    private static final long[] INTERVALS = new long[CAPACITY];
    private static int count;
    private static long warmupUntil, endAt, lastFrame, fillsAtStart;
    private static java.util.function.LongSupplier fillCounter;
    private static Consumer<Stats> callback;

    /** True while a run is in progress. */
    public static boolean running() { return active; }

    /**
     * Start a run. Call from the render thread.
     *
     * @param warmupSeconds  frames in this period are ignored (a screen is closing)
     * @param measureSeconds how long to record after the warm-up
     * @param fills          running total of UI fills submitted, for a per-frame average
     * @param onDone         receives the result on the render thread; null if no frame was seen
     * @return false if a run is already in progress
     */
    public static boolean start(double warmupSeconds, double measureSeconds,
                                java.util.function.LongSupplier fills, Consumer<Stats> onDone) {
        if (active) return false;
        long now = System.nanoTime();
        count = 0;
        lastFrame = 0;
        warmupUntil = now + (long) (warmupSeconds * 1e9);
        endAt = warmupUntil + (long) (measureSeconds * 1e9);
        fillCounter = fills;
        callback = onDone;
        active = true;
        return true;
    }

    /** Once per rendered frame, on the render thread. */
    public static void onFrame() {
        if (!active) return;
        long now = System.nanoTime();
        if (now < warmupUntil) return;
        if (lastFrame == 0) {
            lastFrame = now;
            fillsAtStart = fillCounter == null ? 0 : fillCounter.getAsLong();
            return;
        }
        INTERVALS[count++] = now - lastFrame;
        lastFrame = now;
        if (now >= endAt || count == CAPACITY) finish();
    }

    /**
     * End a run that produced no frames: call from the client tick, so a
     * session without the frame hook still gets its report.
     */
    public static void tick() {
        if (active && System.nanoTime() > endAt + 2_000_000_000L) finish();
    }

    private static void finish() {
        active = false;
        long fills = fillCounter == null ? 0 : fillCounter.getAsLong() - fillsAtStart;
        Stats stats = Stats.of(INTERVALS, count, fills);
        Consumer<Stats> done = callback;
        callback = null;
        fillCounter = null;
        if (done != null) done.accept(stats);
    }
}
