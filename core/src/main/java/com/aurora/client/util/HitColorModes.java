package com.aurora.client.util;

/**
 * Pure math behind the Hit Color flash modes. No Minecraft or native types:
 * everything is ARGB ints in and out, so this lives in the core module.
 *
 * <p>Rows 0-7 of the hurt overlay texture each hold one color; the flash a
 * given entity sees is the row selected at render time. FLAT/PULSE/RAINBOW
 * write the same color to all eight rows (time-driven ones refresh the rows
 * per frame), HEALTH_GRADIENT buckets the entity's health fraction across
 * the rows.
 */
public final class HitColorModes {
    /** Full hue cycle length of the rainbow mode. */
    public static final long RAINBOW_PERIOD_MS = 4000;
    /** Bounds for the pulse rate setting in cycles per second. */
    public static final double PULSE_RATE_MIN = 0.5, PULSE_RATE_MAX = 4.0;

    private HitColorModes() {}

    /** The configured color itself: the default and flat modes use it verbatim. */
    public static int flat(int color) { return color; }

    /**
     * Alpha oscillation between ~25% and the configured alpha, one cosine
     * period per pulse cycle. Deterministic in time, so every hurt entity
     * pulses in sync.
     */
    public static int pulse(int color, long nowMillis, double cyclesPerSecond) {
        double rate = clamp(cyclesPerSecond, PULSE_RATE_MIN, PULSE_RATE_MAX);
        long period = Math.max(1, Math.round(1000.0 / rate));
        double phase = ((nowMillis % period) + period) % period / (double) period;
        float shape = (float) (0.5 - 0.5 * Math.cos(phase * 2.0 * Math.PI)); // 0..1..0
        int alpha = (color >>> 24) & 0xFF;
        float floor = 0.25f;
        int a = Math.round(alpha * (floor + (1.0f - floor) * shape));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** Hue cycles over {@link #RAINBOW_PERIOD_MS}; alpha stays the configured one. */
    public static int rainbow(long nowMillis, int color) {
        float hue = (float) (((nowMillis % RAINBOW_PERIOD_MS) + RAINBOW_PERIOD_MS)
                % RAINBOW_PERIOD_MS / (double) RAINBOW_PERIOD_MS);
        int rgb = hsvToRgb(hue, 1.0f, 1.0f);
        return ((color >>> 24) & 0xFF) << 24 | rgb;
    }

    /**
     * Health fraction of a living entity, 0..1, with absorption counted.
     * Overheal saturates at 1.
     */
    public static float healthFraction(float health, float maxHealth, float absorption) {
        float max = Math.max(maxHealth, 1.0f);
        return clamp((health + Math.max(absorption, 0.0f)) / max, 0.0f, 1.0f);
    }

    /** Row of the eight gradient rows that best matches a health fraction. */
    public static int bucketRow(float healthFraction) {
        return Math.round(clamp(healthFraction, 0.0f, 1.0f) * 7.0f);
    }

    /** Health fraction a gradient row represents (its bucket center). */
    public static float bucketFraction(int row) {
        return Math.round(clamp(row, 0, 7)) / 7.0f;
    }

    /**
     * The gradient palette color for a health fraction, alpha taken from the
     * configured Hit Color. The curve mirrors the Player Health label colors
     * (red at low health through yellow to green) so the flash reads the
     * same way the rest of the UI does. This is the information-advantage
     * mode: callers must gate it behind {@code hitColorHealthGradient}.
     */
    public static int gradientColor(float healthFraction, int color) {
        float f = clamp(healthFraction, 0.0f, 1.0f);
        int rgb = hsvToRgb(clamp(f, 0.02f, 0.98f) / 3.0f, 1.0f, 1.0f);
        return ((color >>> 24) & 0xFF) << 24 | rgb;
    }

    /**
     * ARGB color a gradient row should hold (row 0 = 0% health, row 7 = 100%).
     */
    public static int gradientRowColor(int row, int color) {
        return gradientColor(bucketFraction(row), color);
    }

    /** hsvToRgb with h, s, v each in 0..1; returns packed RGB (opaque). */
    public static int hsvToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float f = h * 6.0f;
        int i = (int) Math.floor(f);
        float p = v * (1.0f - s);
        float q = v * (1.0f - s * (f - i));
        float t = v * (1.0f - s * (1.0f - (f - i)));
        return switch (Math.floorMod(i, 6)) {
            case 0 -> rgb(v, t, p);
            case 1 -> rgb(q, v, p);
            case 2 -> rgb(p, v, t);
            case 3 -> rgb(p, q, v);
            case 4 -> rgb(t, p, v);
            default -> rgb(v, p, q);
        };
    }

    private static int rgb(float r, float g, float b) {
        return Math.round(clamp(r, 0.0f, 1.0f) * 255.0f) << 16
                | Math.round(clamp(g, 0.0f, 1.0f) * 255.0f) << 8
                | Math.round(clamp(b, 0.0f, 1.0f) * 255.0f);
    }

    private static float clamp(float v, float min, float max) { return Math.min(Math.max(v, min), max); }
    private static double clamp(double v, double min, double max) { return Math.min(Math.max(v, min), max); }
}
