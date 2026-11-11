package com.aurora.client.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Aurora's per-frame overhead budget (ADR 0005), set 2026-11-11 from the
 * baseline in {@code docs/OVERHEAD_BUDGET.md}: about 1.5 times what factory
 * defaults cost in a world, with no screen open, on the development machine.
 * A release that exceeds it there does not ship.
 *
 * <p>The CPU figures are what {@link AuroraCost} counts. A profiler run found
 * that it covers about four fifths of Aurora's real per-frame work; the rest is
 * small mixin hooks inside vanilla methods.
 */
public final class OverheadBudget {
    private OverheadBudget() {}

    public static final double CPU_MEAN_MS = 0.25;
    public static final double CPU_P95_MS = 0.40;
    /** The default HUD has no glass; a capture in a world with no screen open is a defect. */
    public static final double GLASS_PER_FRAME = 0.0;
    /** Far below the roughly 2,000 fills per frame where submitting them starts to cost frame rate. */
    public static final double FILLS_PER_FRAME = 100.0;

    /** What exceeded the budget, one phrase each; empty when the run is within it. */
    public static List<String> exceeded(FrameBenchmark.Stats stats) {
        List<String> out = new ArrayList<>();
        if (stats.auroraMeanMs() > CPU_MEAN_MS) out.add(over("CPU mean", stats.auroraMeanMs(), CPU_MEAN_MS, " ms"));
        if (stats.auroraP95Ms() > CPU_P95_MS) out.add(over("CPU p95", stats.auroraP95Ms(), CPU_P95_MS, " ms"));
        if (stats.glassPerFrame() > GLASS_PER_FRAME) out.add(over("glass captures", stats.glassPerFrame(), GLASS_PER_FRAME, ""));
        if (stats.fillsPerFrame() > FILLS_PER_FRAME) out.add(over("UI fills", stats.fillsPerFrame(), FILLS_PER_FRAME, ""));
        return out;
    }

    private static String over(String what, double value, double limit, String unit) {
        return String.format(Locale.ROOT, "%s %.3f%s > %.3f%s", what, value, unit, limit, unit);
    }
}
