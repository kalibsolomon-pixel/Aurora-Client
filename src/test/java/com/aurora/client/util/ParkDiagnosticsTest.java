package com.aurora.client.util;

import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static com.aurora.client.util.ParkDiagnostics.Metric.*;
import static com.aurora.client.util.ParkDiagnostics.Owner.*;
import static org.junit.jupiter.api.Assertions.*;

class ParkDiagnosticsTest {
    private final ParkDiagnostics d = new ParkDiagnostics();

    @AfterEach void stopSingleton() { ParkDiagnostics.INSTANCE.stop(100); }

    private void begin() {
        d.start("test-build", 1);
        observe(POST_SWAP, "PARK", 60, 500, 2000, true, 1);
    }
    private void observe(ParkDiagnostics.Owner owner, String strategy, int fps, int spin, int park,
                         boolean focused, double now) {
        d.observe(owner, strategy, fps, 0, 60, spin, park, false, focused, now);
    }
    private ParkDiagnostics.HistogramSnapshot metric(ParkDiagnostics.Snapshot s, ParkDiagnostics.Metric m) {
        return s.distributions().get(m.ordinal()).values();
    }

    @Test void disabledByDefaultAndIgnoresAllWriterEvents() {
        assertFalse(ParkDiagnostics.INSTANCE.isEnabled());
        assertFalse(d.isEnabled());
        observe(POST_SWAP, "PARK", 60, 500, 2000, true, 1);
        d.anchor(false);
        d.record(1, 2, 1.9, 2, 1_000_000, 1_100_000, true);
        var s = d.snapshot(3);
        assertEquals("none", s.sessionId());
        assertEquals(0, s.segment());
        assertEquals(0, s.sessionDurationNanos());
        assertEquals(0, s.counters().invocations());
    }

    @ParameterizedTest
    @CsvSource({"900000,-100000", "1100000,100000", "1000000,0"})
    void requestedActualAndSignedWakeError(long actual, long error) {
        begin();
        d.record(1, 1.002, 1.0015, 1.002001, 1_000_000, actual, true);
        var s = d.snapshot(2);
        assertEquals(1_000_000L, metric(s, REQUESTED_PARK).minNanos());
        assertEquals(actual, metric(s, ACTUAL_PARK).minNanos());
        assertEquals(error, metric(s, WAKE_ERROR).minNanos());
        assertEquals(error < 0 ? 1 : 0, s.counters().earlyParkReturns());
        assertEquals(error > 0 ? 1 : 0, s.counters().lateParkReturns());
        assertEquals(1, s.counters().parkCalls());
        assertEquals(1, s.counters().activeWaits());
        assertEquals(501_000L, metric(s, SPIN_WALL).minNanos());
        assertEquals(2_001_000L, metric(s, TOTAL_WAIT_WALL).minNanos());
        assertEquals(1000L, metric(s, DEADLINE_ERROR).minNanos());
    }

    @ParameterizedTest
    @CsvSource({"1.999999,-1000", "2.000001,1000", "2.0,0"})
    void deadlineErrorsStaySignedIndependentlyOfLateParkReturn(double exit, long error) {
        begin();
        d.record(1.999, 2, 1.9999, exit, 500_000, 550_000, true);
        var s = d.snapshot(3);
        assertEquals(50_000L, metric(s, WAKE_ERROR).minNanos());
        assertEquals(error, metric(s, DEADLINE_ERROR).minNanos());
        assertEquals(0, s.counters().lateEntries());
    }

    @ParameterizedTest
    @CsvSource({"2.0,0", "2.001,1000000"})
    void lateEntryHasZeroWaitAndNoParkOrSpin(double entry, long error) {
        begin();
        d.record(entry, 2, entry, entry, 0, 0, false);
        var s = d.snapshot(3);
        assertEquals(1, s.counters().invocations());
        assertEquals(1, s.counters().lateEntries());
        assertEquals(0, s.counters().activeWaits());
        assertEquals(0, s.counters().tooShortToPark());
        assertEquals(0, s.counters().spinOnly());
        assertEquals(0, s.counters().spinEntered());
        assertEquals(0, metric(s, ACTUAL_PARK).count());
        assertEquals(0L, metric(s, TOTAL_WAIT_WALL).minNanos());
        assertEquals(0L, metric(s, SPIN_WALL).minNanos());
        assertEquals(error, metric(s, DEADLINE_ERROR).minNanos());
    }

    @Test void shortRemainingIsSpinOnlyAndParkOversleepMaySkipTheSpin() {
        begin();
        d.record(1, 1.0002, 1, 1.0002, 0, 0, true);
        d.record(2, 2.002, 2.003, 2.003, 1_500_000, 3_000_000, false);
        var s = d.snapshot(3);
        assertEquals(2, s.counters().activeWaits());
        assertEquals(1, s.counters().tooShortToPark());
        assertEquals(1, s.counters().spinOnly());
        assertEquals(1, s.counters().spinEntered());
        assertEquals(1, s.counters().parkCalls());
        assertEquals(2, metric(s, SPIN_WALL).count());
        assertEquals(0L, metric(s, SPIN_WALL).minNanos());
        assertEquals(200_000L, metric(s, SPIN_WALL).maxNanos());
    }

    @Test void positiveRemainingConsumedBeforeSpinIsNotAnActiveWait() {
        begin();
        d.record(1, 1.000000001, 1.000001, 1.000001, 0, 0, false);
        var c = d.snapshot(2).counters();
        assertEquals(1, c.invocations());
        assertEquals(1, c.tooShortToPark());
        assertEquals(0, c.activeWaits());
        assertEquals(0, c.spinOnly());
        assertEquals(0, c.lateEntries());
    }

    @Test void signedBucketEdgesAreLowerInclusiveAndUpperExclusive() {
        var bounds = ParkDiagnostics.Histogram.UPPER_EXCLUSIVE;
        for (int i = 0; i < bounds.size(); i++) {
            long edge = bounds.get(i);
            assertEquals(i, ParkDiagnostics.Histogram.bucket(edge - 1));
            assertEquals(i + 1, ParkDiagnostics.Histogram.bucket(edge));
        }
        assertEquals(0, ParkDiagnostics.Histogram.bucket(Long.MIN_VALUE));
        assertEquals(bounds.size(), ParkDiagnostics.Histogram.bucket(Long.MAX_VALUE));
        assertEquals(25_000, bounds.get(bounds.indexOf(0L) + 1));
    }

    @Test void percentileRanksReturnApproximateSignedAndUnboundedBuckets() {
        var h = new ParkDiagnostics.Histogram();
        assertNull(h.snapshot().approximateP50());
        for (int i = 0; i < 50; i++) h.record(-1);
        for (int i = 0; i < 45; i++) h.record(25_000);
        for (int i = 0; i < 4; i++) h.record(250_000);
        h.record(Long.MAX_VALUE);
        var s = h.snapshot();
        assertEquals(new ParkDiagnostics.Bucket(-25_000L, 0L), s.approximateP50());
        assertEquals(new ParkDiagnostics.Bucket(25_000L, 50_000L), s.approximateP95());
        assertEquals(new ParkDiagnostics.Bucket(250_000L, 275_000L), s.approximateP99());
        var tails = new ParkDiagnostics.Histogram();
        tails.record(Long.MIN_VALUE);
        assertNull(tails.snapshot().approximateP50().lowerInclusiveNanos());
        tails.clear();
        tails.record(Long.MAX_VALUE);
        assertNull(tails.snapshot().approximateP99().upperExclusiveNanos());
    }

    @Test void histogramMeanMinMaxCountsAndResetRemainConsistent() {
        var h = new ParkDiagnostics.Histogram();
        h.record(-10);
        h.record(20);
        h.record(50);
        var s = h.snapshot();
        assertEquals(3, s.count());
        assertEquals(-10L, s.minNanos());
        assertEquals(50L, s.maxNanos());
        assertEquals(20L, s.meanNanos());
        assertEquals(s.count(), s.buckets().stream().mapToLong(Long::longValue).sum());
        h.clear();
        assertEquals(0, h.snapshot().count());
        assertNull(h.snapshot().minNanos());
        assertNull(h.snapshot().meanNanos());
    }

    @Test void stopFreezesDurationAndSamplesWhileRestartResetsEverything() {
        begin();
        d.record(1, 1, 1, 1, 0, 0, false);
        String id = d.snapshot(2).sessionId();
        d.stop(2);
        d.stop(5);
        d.record(5, 6, 5, 6, 0, 0, true);
        var stopped = d.snapshot(9);
        assertFalse(stopped.active());
        assertEquals(1_000_000_000, stopped.sessionDurationNanos());
        assertEquals(1, stopped.counters().invocations());
        d.start("new", 10);
        var fresh = d.snapshot(10);
        assertNotEquals(id, fresh.sessionId());
        assertEquals(0, fresh.counters().invocations());
        assertEquals(0, fresh.transitions().conditionChanges());
        assertEquals(0, fresh.segment());
        assertEquals(0, fresh.sessionDurationNanos());
        assertEquals("new", fresh.clientBuild());
    }

    @Test void unchangedContextKeepsSamplesButTargetAndOwnerChangesDiscardThemExplicitly() {
        begin();
        d.record(1, 1, 1, 1, 0, 0, false);
        observe(POST_SWAP, "PARK", 60, 500, 2000, true, 2);
        assertEquals(1, d.snapshot(2).segment());
        observe(FRAME_HEAD_GLOBAL, "PARK", 120, 500, 2000, true, 3);
        var s = d.snapshot(4);
        assertEquals(2, s.segment());
        assertEquals(1, s.transitions().fpsChanges());
        assertEquals(1, s.transitions().ownerChanges());
        assertEquals(1, s.transitions().discardedInvocations());
        assertEquals(0, s.counters().invocations());
        assertEquals(1_000_000_000, s.segmentDurationNanos());
    }

    @Test void thresholdsStrategyFocusAndSameEffectiveFpsCapChangesCannotMixData() {
        begin();
        observe(POST_SWAP, "PARK", 60, 600, 2000, true, 2);
        observe(POST_SWAP, "PARK", 60, 600, 2100, true, 3);
        observe(POST_SWAP, "SPIN", 60, 600, 2100, true, 4);
        observe(POST_SWAP, "SPIN", 60, 600, 2100, false, 5);
        d.observe(POST_SWAP, "SPIN", 60, 144, 60, 600, 2100, false, false, 6);
        d.observe(POST_SWAP, "SPIN", 60, 144, 60, 600, 2100, true, false, 7);
        var s = d.snapshot(8);
        assertEquals(7, s.segment());
        assertEquals(6, s.transitions().conditionChanges());
        assertEquals(1, s.transitions().focusChanges());
        assertEquals(0, s.transitions().fpsChanges());
        assertEquals(0, s.counters().invocations());
    }

    @Test void snapshotIsImmutableSelfConsistentAndSafeForAsynchronousInspection() throws Exception {
        begin();
        d.record(1, 1.001, 1, 1.001, 0, 0, true);
        var s = d.snapshot(2);
        d.start("other", 3);
        assertThrows(UnsupportedOperationException.class, () -> s.distributions().clear());
        assertThrows(UnsupportedOperationException.class, () -> s.bucketUpperExclusiveNanos().clear());
        assertThrows(UnsupportedOperationException.class, () -> metric(s, SPIN_WALL).buckets().clear());
        assertEquals(1, CompletableFuture.supplyAsync(() -> s.counters().invocations()).get());
        String json = new GsonBuilder().serializeNulls().create().toJson(s);
        var parsed = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        assertEquals(1, parsed.get("schemaVersion").getAsInt());
        assertEquals("POST_SWAP", parsed.getAsJsonObject("conditions").get("owner").getAsString());
        assertEquals(s.sessionId(), parsed.get("sessionId").getAsString());
        assertEquals(6, parsed.getAsJsonArray("distributions").size());
        assertTrue(json.contains("approximateP99"));
        assertEquals(1, metric(s, TOTAL_WAIT_WALL).buckets().stream().mapToLong(Long::longValue).sum());
    }

    @Test void schedulerAnchorsRemainDistinctFromInvocationsAndOnlyCountPark() {
        var collector = ParkDiagnostics.INSTANCE;
        collector.start("test", 1);
        collector.observe(FRAME_HEAD_ADAPTIVE, "PARK", 60, 0, 60, 500, 2000, false, true, 1);
        var pacing = new FramePacing();
        var decision = new FramePacing.Decision(FramePacing.Owner.FRAME_HEAD, 60);
        assertEquals(0, pacing.beginFrame(decision, 1));
        assertTrue(pacing.beginFrame(decision, 1.001) > 0);
        assertEquals(0, pacing.beginFrame(decision, 2));
        var s = collector.snapshot(2);
        assertEquals(1, s.counters().initialAnchors());
        assertEquals(1, s.counters().lateReanchors());
        assertEquals(0, s.counters().invocations());
        collector.observe(POST_SWAP, "HYBRID", 60, 0, 60, 500, 2000, false, true, 2);
        collector.anchor(false);
        assertEquals(0, collector.snapshot(3).counters().lateReanchors());
    }

    @Test void saturationDoesNotWrap() {
        assertEquals(Long.MAX_VALUE, ParkDiagnostics.increment(Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, ParkDiagnostics.add(Long.MAX_VALUE - 1, 5));
        assertEquals(15, ParkDiagnostics.add(10, 5));
    }

    @Test void hookContextUsesActualOwnershipAndNeverLabelsFallbackAsPark() {
        var c = ParkDiagnostics.INSTANCE;
        var cfg = new com.aurora.client.config.AuroraConfig();
        c.start("test", 1);
        cfg.frameCapFps = 60;
        ParkDiagnosticsControl.observe(new FramePacing.Decision(FramePacing.Owner.FRAME_HEAD, 30), cfg, true, true, 1);
        assertEquals(FRAME_HEAD_GLOBAL, c.snapshot(1).conditions().owner());
        assertEquals(30, c.snapshot(1).conditions().effectiveFps());
        assertEquals("PARK", c.snapshot(1).conditions().strategy());
        cfg.frameCapFps = 0;
        ParkDiagnosticsControl.observe(new FramePacing.Decision(FramePacing.Owner.FRAME_HEAD, 30), cfg, true, true, 2);
        assertEquals(FRAME_HEAD_ADAPTIVE, c.snapshot(2).conditions().owner());
        ParkDiagnosticsControl.observe(new FramePacing.Decision(FramePacing.Owner.POST_SWAP, 30), cfg, false, true, 3);
        assertEquals(POST_SWAP, c.snapshot(3).conditions().owner());
        assertEquals("HYBRID_FALLBACK", c.snapshot(3).conditions().strategy());
        ParkDiagnosticsControl.observe(new FramePacing.Decision(FramePacing.Owner.NATIVE, 30), cfg, false, true, 4);
        assertEquals(NATIVE, c.snapshot(4).conditions().owner());
        assertEquals("NONE", c.snapshot(4).conditions().strategy());
        cfg.framePacingStrategy = null;
        ParkDiagnosticsControl.observe(new FramePacing.Decision(FramePacing.Owner.POST_SWAP, 60), cfg, true, true, 5);
        assertEquals("PARK", c.snapshot(5).conditions().strategy());
    }

    @Test void instrumentationKeepsTheClockAndAllocationBoundaryAndDeveloperGate() throws Exception {
        Path root = Path.of("src/client/java/com/aurora/client");
        String pacer = Files.readString(root.resolve("util/FramePacer.java"));
        String measured = pacer.substring(pacer.indexOf("private static void waitUntilParkMeasured"),
                pacer.indexOf("public static void waitUntilHybrid"));
        assertEquals(2, measured.split("System.nanoTime\\(\\)", -1).length - 1);
        assertEquals(1, measured.split("LockSupport.parkNanos\\(parkNanos\\)", -1).length - 1);
        assertFalse(measured.contains("new "));
        assertFalse(measured.contains("synchronized"));
        assertTrue(measured.contains("actualNanos = System.nanoTime() - before"));
        assertTrue(measured.contains("while ((exit = GLFW.glfwGetTime()) < target)"));
        String controls = Files.readString(root.resolve("util/ParkDiagnosticsControl.java"));
        assertTrue(controls.contains("if (!Boolean.getBoolean(\"aurora.parkDiagnostics\")) return;"));
        assertTrue(controls.indexOf("CompletableFuture.runAsync") < controls.indexOf("Files.createDirectories"));
        assertTrue(controls.contains("EXPORTING.compareAndSet(false, true)"));
        assertFalse(Files.readString(root.resolve("config/AuroraConfig.java")).contains("parkDiagnostics"));
    }
}
