package com.aurora.client.diagnostics;

import com.aurora.client.health.HealthTestAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The diagnostics report says what broke and how the game ran, and names no person (ADR 0004). */
class DiagnosticsTextTest {
    private static final DiagnosticsText.Environment ENV = new DiagnosticsText.Environment(
            "3.2.0", "1.21.11", "0.19.2", "OpenGL", "Some GPU (4.6)", "2560x1440, GUI scale 3",
            "in a singleplayer world", true, List.of("fps limit 260", "vsync off"));

    @BeforeEach @AfterEach void clean() {
        HealthTestAccess.reset(null);
        RecentErrors.clearForTests();
    }

    @Test void statsSummarizeFrameIntervals() {
        // 99 frames at 4 ms and one at 40 ms.
        long[] intervals = new long[128];
        for (int i = 0; i < 99; i++) intervals[i] = 4_000_000L;
        intervals[99] = 40_000_000L;
        FrameBenchmark.Stats stats = FrameBenchmark.Stats.of(intervals, null, 100, 1200, 0);

        assertEquals(100, stats.frames());
        assertEquals(0.436, stats.seconds(), 1e-9);
        assertEquals(100 / 0.436, stats.averageFps(), 1e-6);
        assertEquals(25.0, stats.onePercentLowFps(), 1e-9, "the slowest 1% is the single 40 ms frame");
        assertEquals(4.0, stats.medianMs(), 1e-9);
        assertEquals(4.0, stats.p99Ms(), 1e-9);
        assertEquals(40.0, stats.maxMs(), 1e-9);
        assertEquals(12.0, stats.fillsPerFrame(), 1e-9);
    }

    @Test void statsSummarizeAuroraTimeAndGlassPerFrame() {
        long[] intervals = {4_000_000L, 4_000_000L, 4_000_000L, 4_000_000L};
        long[] aurora = {100_000L, 200_000L, 300_000L, 1_000_000L};
        FrameBenchmark.Stats stats = FrameBenchmark.Stats.of(intervals, aurora, 4, 0, 6);
        assertEquals(0.4, stats.auroraMeanMs(), 1e-9);
        assertEquals(0.2, stats.auroraMedianMs(), 1e-9);
        assertEquals(1.0, stats.auroraP95Ms(), 1e-9);
        assertEquals(1.5, stats.glassPerFrame(), 1e-9);
    }

    @Test void nestedAuroraBracketsCountOnce() throws Exception {
        long before = AuroraCost.totalNanos();
        long outer = AuroraCost.begin();
        long inner = AuroraCost.begin();
        Thread.sleep(2);
        AuroraCost.end(inner);
        long afterInner = AuroraCost.totalNanos();
        AuroraCost.end(outer);
        long spent = AuroraCost.totalNanos() - before;
        assertEquals(before, afterInner, "an inner bracket adds nothing on its own");
        assertTrue(spent >= 2_000_000L && spent < 1_000_000_000L, "counted once: " + spent);
    }

    @Test void noFramesMeansNoStats() {
        assertNull(FrameBenchmark.Stats.of(new long[4], null, 0, 0, 0));
        assertNull(FrameBenchmark.Stats.of(null, null, 3, 0, 0));
    }

    @Test void aRunDiscardsWarmupAndReportsOnce() throws Exception {
        FrameBenchmark.Stats[] result = new FrameBenchmark.Stats[1];
        int[] calls = {0};
        long[] fills = {0};
        assertTrue(FrameBenchmark.start(0, 0.05, () -> fills[0], () -> 0, stats -> { result[0] = stats; calls[0]++; }));
        assertFalse(FrameBenchmark.start(0, 0.05, () -> 0, () -> 0, stats -> {}), "one run at a time");
        long end = System.nanoTime() + 2_000_000_000L;
        while (FrameBenchmark.running() && System.nanoTime() < end) {
            FrameBenchmark.onFrame();
            fills[0] += 3;
            Thread.sleep(1);
        }
        assertFalse(FrameBenchmark.running());
        assertEquals(1, calls[0]);
        assertTrue(result[0].frames() > 3);
        assertEquals(3.0, result[0].fillsPerFrame(), 0.5);
        FrameBenchmark.onFrame(); // inert afterwards
        assertEquals(1, calls[0]);
    }

    @Test void aHealthyReportListsEnvironmentBenchmarkAndMods() {
        long[] intervals = {4_000_000L, 4_000_000L, 4_000_000L, 4_000_000L};
        String report = DiagnosticsText.format(ENV, List.of("aurora 3.2.0", "sodium 0.6.0"),
                FrameBenchmark.Stats.of(intervals, null, 4, 40, 0), List.of());

        assertTrue(report.startsWith("Aurora Client diagnostics\nAurora 3.2.0 | Minecraft 1.21.11 | Fabric Loader 0.19.2\n"), report);
        assertTrue(report.contains("Renderer: OpenGL | Some GPU (4.6)\n"));
        assertTrue(report.contains("No Aurora mixin has failed so far"), "the audit has not run in a unit test");
        assertTrue(report.contains("Benchmark (0.0 s, in a singleplayer world)\n- 4 frames, 250.0 fps average, 250.0 fps 1% low\n"), report);
        assertTrue(report.contains("- frame time: 4.00 ms median, 4.00 ms p95, 4.00 ms p99, 4.00 ms worst\n"));
        assertTrue(report.contains("- Aurora CPU per frame: 0.000 ms mean, 0.000 ms median, 0.000 ms p95\n"), report);
        assertTrue(report.contains("- Aurora UI fills per frame: 10.0, glass captures per frame: 0.0\n"), report);
        assertTrue(report.contains("- settings: fps limit 260, vsync off\n"));
        assertTrue(report.contains("Recent Aurora warnings and errors (0)\n- none\n"));
        assertTrue(report.contains("Mods (2)\n- aurora 3.2.0\n- sodium 0.6.0\n"));
        assertTrue(report.endsWith("This report contains no account, world or server names.\n"));
    }

    @Test void aBrokenSessionSaysWhichFeaturesAndMixins() {
        HealthTestAccess.fail("GameRendererMixin");
        HealthTestAccess.fail("ScreenshotMixin");
        String report = DiagnosticsText.format(ENV, List.of(), null, List.of("12:00:00 ERROR Aurora: something"));

        assertTrue(report.contains("Features not fully working (1)\n- Zoom: Not working in this session: zooming the view."), report);
        assertTrue(report.contains("- Aurora in general: blurred glass"));
        assertTrue(report.contains("Mixins not working (2 of 72)\n- GameRendererMixin could not be applied - injection point not found\n"), report);
        assertTrue(report.contains("Benchmark\n- not available: no frames were measured\n"));
        assertTrue(report.contains("Recent Aurora warnings and errors (1)\n- 12:00:00 ERROR Aurora: something\n"));
    }

    @Test void theOperatingSystemUserIsRemoved() {
        String scrubbed = DiagnosticsText.scrub(
                "failed to read C:\\Users\\alexandra\\cfg and C:/Users/alexandra/x by alexandra",
                "C:\\Users\\alexandra", "alexandra");
        assertEquals("failed to read ~\\cfg and ~/x by <user>", scrubbed);
        assertEquals("unchanged", DiagnosticsText.scrub("unchanged", null, null));
    }

    @Test void onlyAurorasOwnLogLinesAreKept() {
        assertTrue(RecentErrors.isAurora("Aurora", "x"));
        assertTrue(RecentErrors.isAurora("aurora/health", "x"));
        assertTrue(RecentErrors.isAurora("FabricLoader/Mixin", "Mixin apply for mod aurora failed aurora.mixins.json:X"));
        assertFalse(RecentErrors.isAurora("Minecraft", "Failed to fetch user properties"));
        assertEquals("first", RecentErrors.firstLine("first\nsecond"));

        for (int i = 0; i < 30; i++) RecentErrors.add("line " + i);
        List<String> kept = RecentErrors.lines();
        assertEquals(20, kept.size(), "bounded");
        assertEquals("line 10", kept.get(0));
        assertEquals("line 29", kept.get(19));
    }

    @Test void theOverheadBudgetIsJudgedOnlyInAWorldWithNoScreen() {
        long[] intervals = {4_000_000L, 4_000_000L, 4_000_000L, 4_000_000L};
        long[] cheap = {100_000L, 100_000L, 100_000L, 100_000L};
        long[] costly = {300_000L, 300_000L, 300_000L, 900_000L};
        String within = DiagnosticsText.format(ENV, List.of(), FrameBenchmark.Stats.of(intervals, cheap, 4, 0, 0), List.of());
        assertTrue(within.contains("- overhead budget: within (CPU mean 0.25 ms, p95 0.40 ms, no glass, 100 fills)\n"), within);

        String over = DiagnosticsText.format(ENV, List.of(), FrameBenchmark.Stats.of(intervals, costly, 4, 0, 4), List.of());
        assertTrue(over.contains("- overhead budget: OVER: CPU mean 0.450 ms > 0.250 ms, CPU p95 0.900 ms > 0.400 ms, "
                + "glass captures 1.000 > 0.000\n"), over);

        DiagnosticsText.Environment menu = new DiagnosticsText.Environment("3.2.0", "1.21.11", "0.19.2", "OpenGL",
                "Some GPU (4.6)", "2560x1440, GUI scale 3", "in a menu", false, List.of());
        String notJudged = DiagnosticsText.format(menu, List.of(), FrameBenchmark.Stats.of(intervals, costly, 4, 0, 4), List.of());
        assertTrue(notJudged.contains("- overhead budget: not judged"), notJudged);
    }
}
