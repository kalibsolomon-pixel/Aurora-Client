package com.aurora.client.util;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Bounded, single-render-thread PARK collector. All lifecycle/record/snapshot calls
 * belong to that writer; only immutable snapshots may cross threads. No clock,
 * Minecraft, logging, I/O, synchronization or allocation in the sample path.
 */
public final class ParkDiagnostics {
    public enum Owner { NONE, NATIVE, FRAME_HEAD_GLOBAL, FRAME_HEAD_ADAPTIVE, POST_SWAP }
    public enum Metric { REQUESTED_PARK, ACTUAL_PARK, WAKE_ERROR, SPIN_WALL, TOTAL_WAIT_WALL, DEADLINE_ERROR,
        TOTAL_PARK_ELAPSED, FINAL_PARK_WAKE_ERROR, REMAINING_AFTER_FIRST_PARK, REMAINING_AT_FINAL_SPIN_CHECK,
        FIRST_REQUESTED_PARK, FIRST_ACTUAL_PARK, FIRST_WAKE_ERROR }
    // The final three names are schema-2 export aliases, not additional storage.
    private static final Metric[] METRICS = Arrays.copyOf(Metric.values(), 10);
    public static final ParkDiagnostics INSTANCE = new ParkDiagnostics();
    private final Histogram[] histograms = new Histogram[METRICS.length];
    private boolean enabled, hasContext;
    private String sessionId = "none", build = "unknown", strategy = "NONE";
    private Owner owner = Owner.NONE;
    private int fps, globalCap, guiCap, spinMicros, parkMicros;
    private boolean lowCpu, focused;
    private double started, stopped, segmentStarted;
    private long segment, discardedInvocations, fpsChanges, ownerChanges, focusChanges, conditionChanges;
    private long invocations, activeWaits, lateEntries, tooShortToPark, parkCalls, spinEntered, spinOnly;
    private long earlyParkReturns, lateParkReturns, initialAnchors, lateReanchors;
    private long eligibleRetries, retriesTaken, interruptedRetries, retryLimitExhaustions;

    public ParkDiagnostics() {
        for (int i = 0; i < histograms.length; i++) histograms[i] = new Histogram();
    }

    public boolean isEnabled() { return enabled; }

    /** Explicit start/reset begins a fresh session, including all transition totals. */
    public void start(String clientBuild, double now) {
        enabled = true;
        hasContext = false;
        sessionId = UUID.randomUUID().toString();
        build = clientBuild;
        started = segmentStarted = now;
        stopped = 0;
        segment = discardedInvocations = fpsChanges = ownerChanges = focusChanges = conditionChanges = 0;
        owner = Owner.NONE;
        strategy = "NONE";
        fps = globalCap = guiCap = spinMicros = parkMicros = 0;
        lowCpu = focused = false;
        clearMeasurements();
    }

    public void stop(double now) {
        if (enabled) { stopped = now; enabled = false; }
    }

    /**
     * Called from the existing two hooks using their existing GLFW timestamp.
     * Only the newest homogeneous segment survives. Transition totals are
     * session-wide; FPS refers to the effective target, not the vanilla option.
     */
    public void observe(Owner nextOwner, String nextStrategy, int nextFps,
                        int nextGlobalCap, int nextGuiCap, int nextSpinMicros,
                        int nextParkMicros, boolean nextLowCpu, boolean nextFocused, double now) {
        if (!enabled) return;
        boolean changed = !hasContext || owner != nextOwner || !strategy.equals(nextStrategy)
                || fps != nextFps || globalCap != nextGlobalCap || guiCap != nextGuiCap
                || spinMicros != nextSpinMicros || parkMicros != nextParkMicros
                || lowCpu != nextLowCpu || focused != nextFocused;
        if (!changed) return;
        if (hasContext) {
            if (fps != nextFps) fpsChanges = increment(fpsChanges);
            if (owner != nextOwner) ownerChanges = increment(ownerChanges);
            if (focused != nextFocused) focusChanges = increment(focusChanges);
            conditionChanges = increment(conditionChanges);
            discardedInvocations = add(discardedInvocations, invocations);
        }
        clearMeasurements();
        segment = increment(segment);
        segmentStarted = now;
        owner = nextOwner;
        strategy = nextStrategy;
        fps = nextFps;
        globalCap = nextGlobalCap;
        guiCap = nextGuiCap;
        spinMicros = nextSpinMicros;
        parkMicros = nextParkMicros;
        lowCpu = nextLowCpu;
        focused = nextFocused;
        hasContext = true;
    }

    /** Arming is separate from recovery; neither is a PARK invocation. */
    public void anchor(boolean initial) {
        if (!enabled || !(strategy.equals("PARK") || strategy.equals("PARK_REPARK"))) return;
        if (initial) initialAnchors = increment(initialAnchors);
        else lateReanchors = increment(lateReanchors);
    }

    /**
     * GLFW seconds: entry, deadline, spin start and exit. Java nanoseconds:
     * actual park elapsed ONLY (self-contained subtraction at the call site).
     * requestedNanos is the exact long supplied to parkNanos, or zero if skipped.
     */
    public void record(double entry, double target, double spinStart, double exit,
                       long requestedNanos, long actualNanos, boolean enteredSpin) {
        if (!enabled || invocations == Long.MAX_VALUE) return;
        invocations++;
        boolean late = target <= entry;
        if (late) lateEntries++;
        else {
            if (requestedNanos > 0 || enteredSpin) activeWaits++;
            if (requestedNanos == 0) {
                tooShortToPark++;
                if (enteredSpin) spinOnly++;
            }
        }
        if (enteredSpin) spinEntered++;
        if (requestedNanos > 0) {
            parkCalls++;
            long error = actualNanos - requestedNanos;
            if (error < 0) earlyParkReturns++;
            if (error > 0) lateParkReturns++;
            histograms[Metric.REQUESTED_PARK.ordinal()].record(requestedNanos);
            histograms[Metric.ACTUAL_PARK.ordinal()].record(actualNanos);
            histograms[Metric.WAKE_ERROR.ordinal()].record(error);
        }
        histograms[Metric.SPIN_WALL.ordinal()].record(enteredSpin ? nanos(exit - spinStart) : 0);
        histograms[Metric.TOTAL_WAIT_WALL.ordinal()].record(nanos(exit - entry));
        histograms[Metric.DEADLINE_ERROR.ordinal()].record(nanos(exit - target));
    }

    /** Schema 2 only. Existing park counters/histograms keep FIRST-call semantics. */
    public void recordExperimental(double entry, double target, double spinStart, double exit,
                                   long firstRequestedNanos, long firstActualNanos, boolean enteredSpin,
                                   double firstReturn, long retryActualNanos,
                                   boolean eligible, boolean retry, boolean interrupted) {
        if (!enabled || !strategy.equals("PARK_REPARK") || invocations == Long.MAX_VALUE) return;
        record(entry, target, spinStart, exit, firstRequestedNanos, firstActualNanos, enteredSpin);
        if (eligible) eligibleRetries++;
        if (interrupted) interruptedRetries++;
        if (retry) {
            retriesTaken++;
            if (FramePacer.experimentalRetryEligible(target - spinStart, Math.max(50, spinMicros))) {
                retryLimitExhaustions++;
            }
        }
        histograms[Metric.TOTAL_PARK_ELAPSED.ordinal()].record(add(firstActualNanos, retryActualNanos));
        if (firstRequestedNanos > 0) {
            histograms[Metric.FINAL_PARK_WAKE_ERROR.ordinal()].record(retry
                    ? retryActualNanos - FramePacer.EXPERIMENTAL_RETRY_NANOS : firstActualNanos - firstRequestedNanos);
            histograms[Metric.REMAINING_AFTER_FIRST_PARK.ordinal()].record(nanos(target - firstReturn));
        }
        histograms[Metric.REMAINING_AT_FINAL_SPIN_CHECK.ordinal()].record(nanos(target - spinStart));
    }

    private void clearMeasurements() {
        invocations = activeWaits = lateEntries = tooShortToPark = parkCalls = spinEntered = spinOnly = 0;
        earlyParkReturns = lateParkReturns = initialAnchors = lateReanchors = 0;
        eligibleRetries = retriesTaken = interruptedRetries = retryLimitExhaustions = 0;
        for (Histogram h : histograms) h.clear();
    }

    static long nanos(double seconds) { return Math.round(seconds * 1_000_000_000.0); }
    static long increment(long n) { return n == Long.MAX_VALUE ? n : n + 1; }
    static long add(long a, long b) { return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b; }

    /** Explicit, bounded writer-thread copy outside wait operations; no automatic exports. */
    public Snapshot snapshot(double now) {
        double end = enabled ? now : stopped;
        boolean experimental = strategy.equals("PARK_REPARK");
        var distributions = new java.util.ArrayList<Distribution>(histograms.length);
        for (Metric m : METRICS) {
            if (!experimental && m.ordinal() >= 6) break;
            Metric label = experimental ? switch (m) {
                case REQUESTED_PARK -> Metric.FIRST_REQUESTED_PARK;
                case ACTUAL_PARK -> Metric.FIRST_ACTUAL_PARK;
                case WAKE_ERROR -> Metric.FIRST_WAKE_ERROR;
                default -> m;
            } : m;
            distributions.add(new Distribution(label, histograms[m.ordinal()].snapshot()));
        }
        return new Snapshot(experimental ? 2 : 1, build, sessionId, enabled, segment,
                nanos(end - started), nanos(end - segmentStarted),
                new Conditions(owner, strategy, fps, globalCap, guiCap, spinMicros,
                        Math.max(50, spinMicros), parkMicros, lowCpu, focused),
                new Counters(invocations, activeWaits, lateEntries, tooShortToPark, parkCalls,
                        spinEntered, spinOnly, earlyParkReturns, lateParkReturns, initialAnchors, lateReanchors),
                new Transitions(fpsChanges, ownerChanges, focusChanges, conditionChanges, discardedInvocations),
                Histogram.UPPER_EXCLUSIVE, List.copyOf(distributions),
                new ParkPath(experimental ? "EXPERIMENTAL_BOUNDED_REPARK" : "REFERENCE",
                        FramePacer.EXPERIMENTAL_PARK_REPARK, experimental ? 2 : 1,
                        experimental ? FramePacer.EXPERIMENTAL_RETRY_NANOS : 0,
                        experimental ? "First park occurred; R >= normalized S + 1ms; no interruption observed at entry or before retry; at most one retry."
                                : "No retry."),
                experimental ? new ReparkCounters(invocations, invocations - parkCalls,
                        parkCalls - retriesTaken, retriesTaken, parkCalls, eligibleRetries, retriesTaken,
                        interruptedRetries, retryLimitExhaustions, add(parkCalls, retriesTaken),
                        retriesTaken == 0 ? 0 : FramePacer.EXPERIMENTAL_RETRY_NANOS,
                        retriesTaken == 0 ? 0 : FramePacer.EXPERIMENTAL_RETRY_NANOS) : null,
                "Current segment only; prior segments discarded. Durations/errors in ns; wall time, not CPU. "
                        + "GLFW deadlines/spin; System.nanoTime park elapsed, never mixed. "
                        + "Buckets [lower,upper), null endpoints unbounded; percentile rank buckets approximate. "
                        + "PARK does not use the configured park threshold (HYBRID only). "
                        + "Native/Reflex/VSync/GPU/frame work excluded; invocations are not frame IDs. "
                        + "No added per-iteration clocks. Exit uses final GLFW condition read, before aggregation. "
                        + "Clock/read/preemption overhead included; sub-clock differences unreliable. "
                        + "Counters saturate at Long.MAX_VALUE; wait samples freeze at that invocation count. "
                        + (experimental ? "Schema 2: counters.parkCalls/earlyParkReturns/lateParkReturns are FIRST calls only. "
                        + "TOTAL_PARK_ELAPSED and REMAINING_AT_FINAL_SPIN_CHECK: all invocations (expired entry included). "
                        + "FINAL_PARK_WAKE_ERROR and REMAINING_AFTER_FIRST_PARK: invocations with a first park. "
                        + "Total parked elapsed sums call brackets only; retry-limit exhaustion means R >= S + 1ms after retry. "
                        + "eligibleRetries = retriesTaken + retriesSkippedInterruption; totalParkCalls saturates. "
                        + "Retry can consume task-scheduler permits or oversleep; task responsiveness is not guaranteed equivalent."
                        : "Schema 1 metrics and populations unchanged; parkPath is additive identity metadata."));
    }

    public record Conditions(Owner owner, String strategy, int effectiveFps, int configuredGlobalFps,
                             int configuredGuiFps, int configuredSpinMicros, int effectiveSpinMicros,
                             int configuredParkMicros, boolean hybridLowCpu, boolean focused) {}
    public record Counters(long invocations, long activeWaits, long lateEntries, long tooShortToPark,
                           long parkCalls, long spinEntered, long spinOnly, long earlyParkReturns,
                           long lateParkReturns, long initialAnchors, long lateReanchors) {}
    public record Transitions(long fpsChanges, long ownerChanges, long focusChanges,
                              long conditionChanges, long discardedInvocations) {}
    public record Distribution(Metric metric, HistogramSnapshot values) {}
    public record Bucket(Long lowerInclusiveNanos, Long upperExclusiveNanos) {}
    public record HistogramSnapshot(long count, Long minNanos, Long maxNanos, Long meanNanos,
                                    Bucket approximateP50, Bucket approximateP95, Bucket approximateP99,
                                    List<Long> buckets) {}
    public record ParkPath(String path, boolean experimentalFlag, int maximumParkCalls,
                           long retryRequestNanos, String retryEligibility) {}
    public record ReparkCounters(long totalParkInvocations, long zeroParkInvocations,
                                 long oneParkInvocations, long twoParkInvocations, long firstParkCalls,
                                 long eligibleRetries, long retriesTaken, long retriesSkippedInterruption,
                                 long retryLimitExhaustions, long totalParkCalls,
                                 long retryRequestMinNanos, long retryRequestMaxNanos) {}
    public record Snapshot(int schemaVersion, String clientBuild, String sessionId, boolean active,
                           long segment, long sessionDurationNanos, long segmentDurationNanos,
                           Conditions conditions, Counters counters, Transitions transitions,
                           List<Long> bucketUpperExclusiveNanos, List<Distribution> distributions,
                           ParkPath parkPath, ReparkCounters reparkCounters,
                           String limitations) {}

    /** 25us central buckets, exponential tails and explicit under/overflow. */
    static final class Histogram {
        static final List<Long> UPPER_EXCLUSIVE;
        static {
            var bounds = new java.util.ArrayList<Long>();
            for (long n : new long[]{-1_000_000_000, -256_000_000, -128_000_000, -64_000_000,
                    -32_000_000, -16_000_000, -8_000_000, -4_000_000}) bounds.add(n);
            for (long n = -2_000_000; n <= 2_000_000; n += 25_000) bounds.add(n);
            for (long n : new long[]{4_000_000, 8_000_000, 16_000_000, 32_000_000, 64_000_000,
                    128_000_000, 256_000_000, 1_000_000_000}) bounds.add(n);
            UPPER_EXCLUSIVE = List.copyOf(bounds);
        }
        // Primitive boundaries avoid boxing in the writer path.
        private static final long[] BOUNDS = UPPER_EXCLUSIVE.stream().mapToLong(Long::longValue).toArray();
        private final long[] buckets = new long[BOUNDS.length + 1];
        private long count, min = Long.MAX_VALUE, max = Long.MIN_VALUE;
        private double sum; // double avoids duration-sum long overflow; mean is approximate

        static int bucket(long value) {
            int low = 0, high = BOUNDS.length;
            while (low < high) {
                int mid = (low + high) >>> 1;
                if (value >= BOUNDS[mid]) low = mid + 1;
                else high = mid;
            }
            return low;
        }
        void record(long value) {
            if (count == Long.MAX_VALUE) return;
            count++;
            buckets[bucket(value)]++;
            min = Math.min(min, value);
            max = Math.max(max, value);
            sum += value;
        }
        void clear() {
            Arrays.fill(buckets, 0);
            count = 0;
            min = Long.MAX_VALUE;
            max = Long.MIN_VALUE;
            sum = 0;
        }
        Bucket percentile(int percent) {
            if (count == 0) return null;
            // ceil(count * percent / 100) without overflowing long
            long rank = (count / 100) * percent + ((count % 100) * percent + 99) / 100;
            long seen = 0;
            for (int i = 0; i < buckets.length; i++) {
                seen += buckets[i];
                if (seen >= rank) return new Bucket(i == 0 ? null : BOUNDS[i - 1],
                        i == BOUNDS.length ? null : BOUNDS[i]);
            }
            throw new IllegalStateException("Inconsistent histogram");
        }
        HistogramSnapshot snapshot() {
            return new HistogramSnapshot(count, count == 0 ? null : min, count == 0 ? null : max,
                    count == 0 ? null : Math.round(sum / count), percentile(50), percentile(95), percentile(99),
                    Arrays.stream(buckets).boxed().toList());
        }
    }
}
