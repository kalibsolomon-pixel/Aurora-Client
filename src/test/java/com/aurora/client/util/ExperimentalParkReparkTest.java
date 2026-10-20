package com.aurora.client.util;

import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentalParkReparkTest {
    /** All clock scripts fail on exhaustion rather than hanging on a frozen deadline. */
    static final class Script implements FramePacer.ExperimentalWait {
        final double[] times;
        final long[] elapsed;
        final List<Long> requests = new ArrayList<>();
        int reads, spins, nanoReads, interruptReads;
        int interruptOnRead = Integer.MAX_VALUE;
        boolean interruptInFirstPark, interruptInRetry;
        long nanos;

        Script(double[] times, long... elapsed) { this.times = times; this.elapsed = elapsed; }
        public double time() {
            assertTrue(reads < times.length, "Script exhausted: wait did not exit at the absolute deadline");
            return times[reads++];
        }
        public long nanoTime() { nanoReads++; return nanos; }
        public void park(long n) {
            assertTrue(n > 0);
            assertTrue(requests.size() < 2, "Third park forbidden");
            requests.add(n);
            nanos += elapsed[requests.size() - 1];
            if ((requests.size() == 1 && interruptInFirstPark) || (requests.size() == 2 && interruptInRetry)) {
                Thread.currentThread().interrupt();
            }
        }
        public void spin() { assertTrue(++spins < 100); }
        public boolean isInterrupted() {
            if (++interruptReads == interruptOnRead) Thread.currentThread().interrupt();
            return Thread.currentThread().isInterrupted();
        }
    }

    static ParkDiagnostics collector(int spin) {
        var d = new ParkDiagnostics();
        d.start("test", 0);
        d.observe(ParkDiagnostics.Owner.FRAME_HEAD_GLOBAL, "PARK_REPARK", 60,
                60, 60, spin, 2000, false, true, 0);
        return d;
    }

    static ParkDiagnostics.HistogramSnapshot metric(ParkDiagnostics.Snapshot s, ParkDiagnostics.Metric m) {
        return s.distributions().stream().filter(d -> d.metric() == m).findFirst().orElseThrow().values();
    }

    static void populations(ParkDiagnostics.Snapshot s) {
        var c = s.reparkCounters();
        assertEquals(c.totalParkInvocations(), c.zeroParkInvocations() + c.oneParkInvocations() + c.twoParkInvocations());
        assertEquals(c.firstParkCalls(), c.oneParkInvocations() + c.twoParkInvocations());
        assertEquals(c.totalParkCalls(), c.firstParkCalls() + c.retriesTaken());
        assertEquals(c.twoParkInvocations(), c.retriesTaken());
        assertEquals(c.eligibleRetries(), c.retriesTaken() + c.retriesSkippedInterruption());
        assertTrue(c.retryLimitExhaustions() <= c.retriesTaken());
        assertEquals(s.counters().invocations(), c.totalParkInvocations());
        assertEquals(s.counters().parkCalls(), c.firstParkCalls());
        for (var distribution : s.distributions()) {
            long expected = switch (distribution.metric()) {
                case FIRST_REQUESTED_PARK, FIRST_ACTUAL_PARK, FIRST_WAKE_ERROR,
                     FINAL_PARK_WAKE_ERROR, REMAINING_AFTER_FIRST_PARK -> c.firstParkCalls();
                default -> c.totalParkInvocations();
            };
            assertEquals(expected, distribution.values().count(), distribution.metric().name());
            assertEquals(expected, distribution.values().buckets().stream().mapToLong(Long::longValue).sum());
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void substantiallyEarlyReturnRetriesExactlyOnceWithOrWithoutDiagnostics(boolean measured) {
        var w = new Script(new double[]{0, .002, .002, .005, .010}, 100, 0);
        var d = collector(500);
        FramePacer.waitUntilParkExperimental(.010, 500, w, measured ? d : null);
        assertEquals(List.of(9_500_000L, 1_000_000L), w.requests);
        assertEquals(2, w.spins);
        assertEquals(measured ? 4 : 0, w.nanoReads);
        if (measured) {
            var s = d.snapshot(.010);
            populations(s);
            assertEquals(1, s.reparkCounters().retryLimitExhaustions());
            assertEquals(100, metric(s, ParkDiagnostics.Metric.TOTAL_PARK_ELAPSED).minNanos());
            assertEquals(-1_000_000, metric(s, ParkDiagnostics.Metric.FINAL_PARK_WAKE_ERROR).minNanos());
            assertEquals(8_000_000, metric(s, ParkDiagnostics.Metric.REMAINING_AFTER_FIRST_PARK).minNanos());
            assertEquals(8_000_000, metric(s, ParkDiagnostics.Metric.REMAINING_AT_FINAL_SPIN_CHECK).minNanos());
        } else assertEquals(0, d.snapshot(.010).counters().invocations());
    }

    @ParameterizedTest @ValueSource(doubles = {0, -.001})
    void expiredEntryPreservesTheExistingImmediateReturn(double target) {
        var d = collector(500);
        var w = new Script(new double[]{0});
        FramePacer.waitUntilParkExperimental(target, 500, w, d);
        assertEquals(0, w.requests.size());
        assertEquals(0, w.spins);
        assertEquals(0, w.interruptReads);
        populations(d.snapshot(1));
        assertEquals(1, d.snapshot(1).counters().lateEntries());
    }

    @ParameterizedTest @ValueSource(doubles = {1e-12, .0002, .0005})
    void tinyOrShortWaitNeverRetriesWithoutAFirstPark(double target) {
        var w = new Script(new double[]{0, target / 2, target});
        var d = collector(500);
        FramePacer.waitUntilParkExperimental(target, 500, w, d);
        assertTrue(w.requests.isEmpty());
        assertEquals(1, w.spins);
        assertEquals(1, d.snapshot(1).reparkCounters().zeroParkInvocations());
        populations(d.snapshot(1));
    }

    @ParameterizedTest @ValueSource(doubles = {.0099, .0095, .009, .010, .011})
    void nearInsideOrPastTheSpinWindowNeverRetries(double firstReturn) {
        var w = new Script(new double[]{0, firstReturn, .010}, 9_000_000);
        var d = collector(500);
        FramePacer.waitUntilParkExperimental(.010, 500, w, d);
        assertEquals(1, w.requests.size());
        assertEquals(0, d.snapshot(1).reparkCounters().eligibleRetries());
        populations(d.snapshot(1));
        assertEquals(Math.round((.010 - firstReturn) * 1e9),
                metric(d.snapshot(1), ParkDiagnostics.Metric.REMAINING_AFTER_FIRST_PARK).minNanos());
    }

    @ParameterizedTest @ValueSource(ints = {50, 250, 500, 1500, Integer.MAX_VALUE})
    void exactEligibilityBoundaryAndNeighboringDoubles(int spin) {
        double boundary = spin / 1_000_000.0 + .001;
        assertFalse(FramePacer.experimentalRetryEligible(Math.nextDown(boundary), spin));
        assertTrue(FramePacer.experimentalRetryEligible(boundary, spin));
        assertTrue(FramePacer.experimentalRetryEligible(Math.nextUp(boundary), spin));
        assertFalse(FramePacer.experimentalRetryEligible(0, spin));
        assertFalse(FramePacer.experimentalRetryEligible(-1, spin));
    }

    @ParameterizedTest @ValueSource(doubles = {.004, .0095, .010, .012})
    void finalRetryReturnAlwaysRechecksTheSameDeadline(double retryReturn) {
        var w = new Script(new double[]{0, .002, retryReturn, .010}, 2_000_000, 1_800_000);
        var d = collector(500);
        FramePacer.waitUntilParkExperimental(.010, 500, w, d);
        assertEquals(2, w.requests.size());
        assertEquals(retryReturn < .010 ? 1 : 0, w.spins);
        var s = d.snapshot(1);
        populations(s);
        assertEquals(3_800_000, metric(s, ParkDiagnostics.Metric.TOTAL_PARK_ELAPSED).minNanos());
        assertEquals(800_000, metric(s, ParkDiagnostics.Metric.FINAL_PARK_WAKE_ERROR).minNanos());
        assertEquals(retryReturn <= .0085 ? 1 : 0, s.reparkCounters().retryLimitExhaustions());
        assertEquals(Math.round((Math.max(.010, retryReturn) - .010) * 1e9),
                metric(s, ParkDiagnostics.Metric.DEADLINE_ERROR).minNanos());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4})
    void interruptionBeforeDuringFirstParkBeforeRetryOrRacingRetryIsPreserved(int point) {
        assertFalse(Thread.currentThread().isInterrupted());
        var w = new Script(new double[]{0, .002, .010}, 0, 0);
        var d = collector(500);
        try {
            if (point == 0) Thread.currentThread().interrupt();
            if (point == 1) w.interruptInFirstPark = true;
            if (point == 2) w.interruptOnRead = 2;
            if (point == 3) w.interruptOnRead = 3;
            if (point == 4) w.interruptInRetry = true;
            FramePacer.waitUntilParkExperimental(.010, 500, w, d);
            assertTrue(Thread.currentThread().isInterrupted(), "Wait must never clear the interrupt bit");
            assertEquals(point == 4 ? 2 : 1, w.requests.size());
            var s = d.snapshot(1);
            populations(s);
            assertEquals(point == 4 ? 0 : 1, s.reparkCounters().retriesSkippedInterruption());
        } finally {
            // Test teardown only: production never clears/restores interruption.
            Thread.interrupted();
        }
    }

    @ParameterizedTest @ValueSource(doubles = {Double.MAX_VALUE, 9_223_372_036.0, 9_223_372_037.0})
    void largeFiniteDeadlinesAndNanosecondConversionDoNotOverflow(double target) {
        var w = new Script(new double[]{0, target, target}, Long.MAX_VALUE);
        var d = collector(500);
        FramePacer.waitUntilParkExperimental(target, 500, w, d);
        long expected = (long) ((target - .0005) * 1e9);
        assertEquals(List.of(expected), w.requests);
        assertTrue(expected > 0);
        populations(d.snapshot(target));
    }

    @Test void parkedElapsedSumSaturatesInsteadOfWrapping() {
        var w = new Script(new double[]{0, 0, .010}, Long.MAX_VALUE - 5, 100);
        var d = collector(500);
        FramePacer.waitUntilParkExperimental(.010, 500, w, d);
        populations(d.snapshot(1));
        assertEquals(Long.MAX_VALUE, metric(d.snapshot(1), ParkDiagnostics.Metric.TOTAL_PARK_ELAPSED).minNanos());
    }

    @Test void experimentalExportIdentityPopulationsAndReferenceSegregation() {
        var d = collector(500);
        FramePacer.waitUntilParkExperimental(.010, 500, new Script(new double[]{0, .002, .003, .010}, 100, 200), d);
        FramePacer.waitUntilParkExperimental(.010, 500, new Script(new double[]{.010}), d);
        FramePacer.waitUntilParkExperimental(.010, 500, new Script(new double[]{0, .010}, 100), d);
        var s = d.snapshot(1);
        populations(s);
        assertEquals(1, s.reparkCounters().zeroParkInvocations());
        assertEquals(1, s.reparkCounters().oneParkInvocations());
        assertEquals(1, s.reparkCounters().twoParkInvocations());
        assertEquals(2, s.schemaVersion());
        assertEquals(10, s.distributions().size());
        assertEquals("EXPERIMENTAL_BOUNDED_REPARK", s.parkPath().path());
        assertEquals(2, s.parkPath().maximumParkCalls());
        assertEquals(1_000_000, s.parkPath().retryRequestNanos());
        assertTrue(s.parkPath().retryEligibility().contains("R >= normalized S + 1ms"));
        String json = new GsonBuilder().serializeNulls().create().toJson(s);
        assertTrue(json.contains("FIRST_REQUESTED_PARK"));
        assertTrue(json.contains("EXPERIMENTAL_BOUNDED_REPARK"));
        d.observe(ParkDiagnostics.Owner.FRAME_HEAD_GLOBAL, "PARK", 60, 60, 60, 500, 2000, false, true, 2);
        d.record(2, 2.002, 2.0015, 2.002, 1_500_000, 1_400_000, true);
        var reference = d.snapshot(3);
        assertEquals(1, reference.schemaVersion());
        assertEquals(6, reference.distributions().size());
        assertEquals(3, reference.transitions().discardedInvocations());
        assertEquals(1, reference.counters().invocations());
        assertNull(reference.reparkCounters());
        assertEquals(-100_000, metric(reference, ParkDiagnostics.Metric.WAKE_ERROR).minNanos());
        d.observe(ParkDiagnostics.Owner.FRAME_HEAD_GLOBAL, "PARK_REPARK", 60, 60, 60, 500, 2000, false, true, 4);
        assertEquals(4, d.snapshot(4).transitions().discardedInvocations());
        populations(d.snapshot(4));
        assertEquals(0, d.snapshot(4).reparkCounters().retriesTaken());
    }

    @Test void bothReferenceMethodBodiesRemainPinnedAndDispatchAndNormalizationStayIsolated() throws Exception {
        String s = Files.readString(Path.of("src/client/java/com/aurora/client/util/FramePacer.java")).replace("\r\n", "\n");
        assertEquals("682728fccccd05cb3dd25e03b6aa5cb10672ba3e57e499953f497937ddbfa1a3",
                hashMethod(s, "    public static void waitUntilPark("));
        assertEquals("d9827dccdb59d1be90081af299b17482a346327a50b6766e32ce76a0afd02824",
                hashMethod(s, "    private static void waitUntilParkMeasured("));
        assertTrue(s.contains("int spinTail = Math.max(50, cfg.framePacerSpinThresholdMicros)"));
        assertTrue(s.contains("case PARK -> {\n                if (EXPERIMENTAL_PARK_REPARK)"));
        assertTrue(s.contains("ParkDiagnostics.INSTANCE.isEnabled() ? ParkDiagnostics.INSTANCE : null"));
        String experiment = s.substring(s.indexOf("    static void waitUntilParkExperimental"), s.indexOf("    /**\n     * Sleep most"));
        assertFalse(experiment.contains("Thread.interrupted()"));
        assertFalse(experiment.contains("new "));
        assertFalse(experiment.contains("synchronized"));
        assertFalse(experiment.contains("execute("));
        assertFalse(experiment.contains("pollEvents"));
        assertEquals(2, experiment.split("wait.park\\(", -1).length - 1);
    }

    private static String hashMethod(String s, String prefix) throws Exception {
        int start = s.indexOf(prefix);
        int end = s.indexOf("\n    }", start) + 6;
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(s.substring(start, end).getBytes(StandardCharsets.UTF_8)));
    }

    @ParameterizedTest @ValueSource(strings = {"absent", "false", "true"})
    void startupPropertyIsIndependentAndReadOnceInFreshJvms(String value) throws Exception {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin",
                java.io.File.separatorChar == '\\' ? "java.exe" : "java").toString());
        if (!value.equals("absent")) command.add("-Daurora.experimentalParkRepark=" + value);
        command.add("-cp");
        command.add(Path.of(FramePacer.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                + java.io.File.pathSeparator + Path.of(FlagProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        command.add(FlagProbe.class.getName());
        command.add(String.valueOf(value.equals("true")));
        var p = new ProcessBuilder(command).redirectErrorStream(true).start();
        assertTrue(p.waitFor(10, TimeUnit.SECONDS), "Property probe timed out");
        assertEquals(0, p.exitValue(), new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    public static class FlagProbe {
        public static void main(String[] args) {
            boolean expected = Boolean.parseBoolean(args[0]);
            if (FramePacer.EXPERIMENTAL_PARK_REPARK != expected) throw new AssertionError("Startup flag");
            System.setProperty("aurora.experimentalParkRepark", String.valueOf(!expected));
            System.setProperty("aurora.parkDiagnostics", "true");
            if (FramePacer.EXPERIMENTAL_PARK_REPARK != expected) throw new AssertionError("Flag changed at runtime");
        }
    }
}
