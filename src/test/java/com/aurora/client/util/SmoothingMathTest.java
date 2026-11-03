package com.aurora.client.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the 2026-11-03 responsiveness-audit fix for Smooth Camera: the blend
 * must (a) reproduce the legacy constant per-frame lerp factor at 60 FPS
 * (strength calibration — existing settings keep their feel) and (b) be
 * time-invariant, so 4 frames at 240 FPS compose to the same total blend as
 * 1 frame at 60 FPS (the pre-fix code settled 4x faster at 240 FPS).
 */
class SmoothingMathTest {

    private static final double EPS = 1e-9;
    private static final double COMPOSITION_EPS = 1e-9;

    @Test
    void sixtyFpsFrameReproducesLegacyStrength() {
        for (double s = 0.05; s < 0.999; s += 0.0123) {
            assertEquals(s, SmoothingMath.alpha(s, 1.0 / 60.0), EPS,
                    "strength " + s + " must equal the legacy 60 FPS factor");
        }
    }

    @Test
    void frameRateInvariant_composition() {
        // Four 240 FPS frames must compose to one 60 FPS frame's blend.
        for (double s = 0.05; s < 0.999; s += 0.037) {
            double oneFrame = SmoothingMath.alpha(s, 1.0 / 60.0);
            double remain = 1.0;
            for (int i = 0; i < 4; i++) {
                remain *= 1.0 - SmoothingMath.alpha(s, 1.0 / 240.0);
            }
            assertEquals(oneFrame, 1.0 - remain, COMPOSITION_EPS,
                    "strength " + s + " must be frame-rate independent");
        }
    }

    @Test
    void alphaBoundsAndMonotonicity() {
        assertEquals(0.0, SmoothingMath.alpha(0.5, 0.0), EPS);
        assertEquals(1.0, SmoothingMath.alpha(1.0, 0.001), EPS);
        assertEquals(1.0, SmoothingMath.alpha(2.0, 0.001), EPS); // >= 1 clamps instant
        double prev = -1;
        for (double dt = 0.0001; dt <= 0.05; dt += 0.001) {
            double a = SmoothingMath.alpha(0.5, dt);
            assertTrue(a >= 0.0 && a <= 1.0);
            assertTrue(a > prev, "alpha must increase with dt at " + dt);
            prev = a;
        }
        // Lower strength = smaller alpha (smoother) at the same dt.
        assertTrue(SmoothingMath.alpha(0.1, 1.0 / 120.0) < SmoothingMath.alpha(0.9, 1.0 / 120.0));
    }

    @Test
    void tauCalibrationMatchesLegacyEquation() {
        for (double s = 0.05; s < 0.999; s += 0.05) {
            double tau = SmoothingMath.tauForStrength(s);
            assertEquals(s, 1.0 - Math.exp(-(1.0 / 60.0) / tau), EPS);
        }
    }

    @Test
    void defaultStrengthSettleProfile() {
        // Documentation anchor: at the 0.5 default, tau is ~24.06 ms — i.e.
        // the legacy "~3-frame settle at 60 FPS" feel (95% in ~3 tau).
        double tau = SmoothingMath.tauForStrength(0.5);
        assertEquals(0.02406, tau, 0.0001);
        // After 3 frames at 60 FPS the remaining error is (1-0.5)^3 = 12.5%.
        double remain = Math.pow(1.0 - SmoothingMath.alpha(0.5, 1.0 / 60.0), 3);
        assertEquals(0.125, remain, 1e-6);
    }
}
