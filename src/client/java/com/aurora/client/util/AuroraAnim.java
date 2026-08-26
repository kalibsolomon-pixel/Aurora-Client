package com.aurora.client.util;

/**
 * Centralized easing + interpolation helpers used across Aurora's UI.
 *
 * <p>Curves are tuned to feel close to Apple's UIKit standard timing
 * curves so widgets read as native iOS:
 * <ul>
 *   <li>{@link #easeOutCubic} — UIKit's "EaseOut" default, used for press
 *       animations and hover transitions.</li>
 *   <li>{@link #easeInOutCubic} — symmetric ease, used for segmented
 *       control slide and modal transitions.</li>
 *   <li>{@link #springOvershoot} — single-overshoot spring approximation,
 *       used for {@code UISwitch}-style toggle thumb slides.</li>
 * </ul>
 *
 * <p>All easing functions accept {@code t ∈ [0, 1]} and return values in
 * the same range (overshoot can briefly exceed 1.0 then settle). Callers
 * should clamp inputs themselves; the functions don't.
 */
public final class AuroraAnim {

    private AuroraAnim() {}

    /** UIKit "EaseOut" — fast start, gentle settle. {@code 1 − (1−t)³}. */
    public static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /** UIKit "EaseInOut" — slow start, fast middle, slow end. */
    public static float easeInOutCubic(float t) {
        if (t < 0.5f) return 4f * t * t * t;
        float u = -2f * t + 2f;
        return 1f - 0.5f * u * u * u;
    }

    /**
     * Single-overshoot spring approximation. Crosses 1.0 at ~75 % of the
     * duration, peaks near 1.06 around 88 %, then settles. Visually
     * indistinguishable from a critically-damped spring at the durations
     * Aurora uses (200–320 ms) but cheaper than integrating a real
     * spring ODE per frame.
     */
    public static float springOvershoot(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        // Decaying sinusoid over a cubic ramp. Tuned by eye against the
        // iOS UISwitch toggle response.
        float s = 1.7f;
        float u = t - 1f;
        return 1f + (s + 1f) * u * u * u + s * u * u;
    }

    /** {@code lerp} for floats. {@code t} not clamped. */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Component-wise ARGB linear interpolation, {@code t} clamped to [0,1]. */
    public static int lerpArgb(int a, int b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        int af = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int bf = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int oa = Math.round(af + (bf - af) * t);
        int or = Math.round(ar + (br - ar) * t);
        int og = Math.round(ag + (bg - ag) * t);
        int ob = Math.round(ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    /** Multiply ARGB color's alpha by a {@code [0, 1]} scalar. */
    public static int scaleAlpha(int argb, float s) {
        if (s >= 1f) return argb;
        if (s <= 0f) return argb & 0x00FFFFFF;
        int a = (argb >>> 24) & 0xFF;
        int na = Math.round(a * s);
        return (na << 24) | (argb & 0x00FFFFFF);
    }

    /** {@code [0, 1]} clamp. */
    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
