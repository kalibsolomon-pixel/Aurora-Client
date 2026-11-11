package com.aurora.client.diagnostics;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * The short benchmark inside the diagnostics report (ADR 0004): frame times
 * over a few seconds of whatever is on screen, and Aurora's own share of each
 * frame against the overhead budget (ADR 0005).
 *
 * <p>{@link #onFrame} is called once per rendered frame from the frame-head
 * mixin. While no benchmark is running it reads one volatile flag and
 * returns. A run discards a warm-up period, records frame intervals and
 * Aurora's time per frame ({@link AuroraCost}) into fixed buffers, and hands
 * the {@link Stats} to its callback on the render thread. No Minecraft imports.
 */
public final class FrameBenchmark {
    private FrameBenchmark() {}

    /** Enough for the full run at 2000 fps; a faster run simply ends early. */
    private static final int CAPACITY = 16_384;

    /**
     * What the run measured. Times are milliseconds. The Aurora figures are the
     * time inside Aurora's entry points per frame; glass and fills are averages.
     */
    public record Stats(int frames, double seconds, double averageFps, double onePercentLowFps,
                        double medianMs, double p95Ms, double p99Ms, double maxMs, double fillsPerFrame,
                        double auroraMeanMs, double auroraMedianMs, double auroraP95Ms,
                        double glassPerFrame) {

        /**
         * Summarize frame intervals and Aurora's time per frame (nanoseconds,
         * one entry per interval). Null when there is nothing to summarize.
         */
        public static Stats of(long[] intervalsNanos, long[] auroraNanos, int count, long fills, long glass) {
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
            long[] aurora = auroraNanos == null ? new long[count] : Arrays.copyOf(auroraNanos, count);
            Arrays.sort(aurora);
            long auroraTotal = 0;
            for (long v : aurora) auroraTotal += v;
            double seconds = total / 1e9;
            return new Stats(count, seconds, count / seconds, slowCount / (slowTotal / 1e9),
                    percentile(sorted, 0.50) / 1e6, percentile(sorted, 0.95) / 1e6,
                    percentile(sorted, 0.99) / 1e6, sorted[count - 1] / 1e6, (double) fills / count,
                    auroraTotal / 1e6 / count, percentile(aurora, 0.50) / 1e6, percentile(aurora, 0.95) / 1e6,
                    (double) glass / count);
        }

        private static double percentile(long[] sorted, double p) {
            int index = (int) Math.ceil(p * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
        }
    }

    private static volatile boolean active;
    private static final long[] INTERVALS = new long[CAPACITY];
    private static final long[] AURORA = new long[CAPACITY];
    private static int count;
    private static long warmupUntil, endAt, lastFrame, lastAurora, fillsAtStart, glassAtStart;
    private static LongSupplier fillCounter, glassCounter;
    private static Consumer<Stats> callback;

    /** True while a run is in progress. */
    public static boolean running() { return active; }

    /**
     * Start a run. Call from the render thread.
     *
     * @param warmupSeconds  frames in this period are ignored (a screen is closing)
     * @param measureSeconds how long to record after the warm-up
     * @param fills          running total of UI fills submitted, for a per-frame average
     * @param glass          running total of glass panels captured, for a per-frame average
     * @param onDone         receives the result on the render thread; null if no frame was seen
     * @return false if a run is already in progress
     */
    public static boolean start(double warmupSeconds, double measureSeconds,
                                LongSupplier fills, LongSupplier glass, Consumer<Stats> onDone) {
        if (active) return false;
        long now = System.nanoTime();
        count = 0;
        lastFrame = 0;
        warmupUntil = now + (long) (warmupSeconds * 1e9);
        endAt = warmupUntil + (long) (measureSeconds * 1e9);
        fillCounter = fills;
        glassCounter = glass;
        callback = onDone;
        active = true;
        return true;
    }

    /** Once per rendered frame, on the render thread. */
    public static void onFrame() {
        if (!active) return;
        long now = System.nanoTime();
        if (now < warmupUntil) return;
        long aurora = AuroraCost.totalNanos();
        if (lastFrame == 0) {
            lastFrame = now;
            lastAurora = aurora;
            fillsAtStart = read(fillCounter);
            glassAtStart = read(glassCounter);
            return;
        }
        INTERVALS[count] = now - lastFrame;
        AURORA[count++] = aurora - lastAurora;
        lastFrame = now;
        lastAurora = aurora;
        if (now >= endAt || count == CAPACITY) finish();
    }

    /**
     * End a run that produced no frames: call from the client tick, so a
     * session without the frame hook still gets its report.
     */
    public static void tick() {
        if (active && System.nanoTime() > endAt + 2_000_000_000L) finish();
    }

    private static long read(LongSupplier counter) {
        return counter == null ? 0 : counter.getAsLong();
    }

    private static void finish() {
        active = false;
        Stats stats = Stats.of(INTERVALS, AURORA, count, read(fillCounter) - fillsAtStart,
                read(glassCounter) - glassAtStart);
        Consumer<Stats> done = callback;
        callback = null;
        fillCounter = null;
        glassCounter = null;
        if (done != null) done.accept(stats);
    }
}
