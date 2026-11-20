package com.aurora.client.hud;

import java.util.Locale;

/**
 * Health plus absorption, formatted once per change to two decimal places.
 */
public final class HealthReadout {
    private float lastTotal = Float.NaN;
    private String total = "";

    public static String format(float hp) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0f, hp));
    }

    public void update(float health, float absorption) {
        float combined = Math.max(0.0f, health) + Math.max(0.0f, absorption);
        if (Float.compare(combined, lastTotal) != 0) {
            lastTotal = combined;
            total = format(combined);
        }
    }

    public String total() {
        return total;
    }
}
