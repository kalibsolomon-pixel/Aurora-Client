package com.aurora.client.util;

/**
 * Pure frame-rate-independent exponential smoothing math (no Minecraft
 * imports, unit-testable).
 *
 * <p>{@link #alpha} converts a "strength" knob plus a frame delta into the
 * per-frame exponential blend factor {@code 1 - exp(-dt / tau)}, where tau is
 * calibrated so a strength value reproduces the legacy constant per-frame
 * lerp factor at 60 FPS: {@code s == 1 - exp(-(1/60) / tau)}, i.e.
 * {@code tau = -(1/60) / ln(1 - s)}. The result is time-invariant — n frames
 * of any duration compose to the same total blend as one frame of the summed
 * duration — so a smoothing setting feels identical at 60, 120 or 240 FPS.
 *
 * <p>Extracted from {@code CameraSmoothFeature} during the 2026-11-03
 * responsiveness audit, whose finding was that the camera smoother applied a
 * constant per-frame factor (4x faster settle at 240 FPS than at 60) while
 * its javadoc claimed exactly this compensation.
 */
public final class SmoothingMath {

    /** Legacy calibration reference frame rate (frames per second). */
    public static final double REFERENCE_FPS = 60.0;

    private SmoothingMath() {}

    /**
     * Time constant (seconds) for a strength value, calibrated so the blend
     * at one 60 FPS frame equals the legacy constant per-frame factor.
     *
     * @param strength 0..1 exclusive (0 = infinitely slow, 1 = instant);
     *                 callers clamp before calling
     */
    public static double tauForStrength(double strength) {
        return -(1.0 / REFERENCE_FPS) / Math.log(1.0 - strength);
    }

    /**
     * Frame-rate-independent exponential blend factor for one frame.
     *
     * @param strength 0..1; values {@code >= 1} mean "instant" and yield 1.0
     * @param dtSec    frame delta in seconds; clamped to {@code >= 0}
     * @return blend factor in [0, 1]
     */
    public static double alpha(double strength, double dtSec) {
        if (strength >= 1.0) return 1.0;
        if (dtSec <= 0.0) return 0.0;
        double a = 1.0 - Math.exp(-dtSec / tauForStrength(strength));
        return Math.max(0.0, Math.min(1.0, a));
    }
}
