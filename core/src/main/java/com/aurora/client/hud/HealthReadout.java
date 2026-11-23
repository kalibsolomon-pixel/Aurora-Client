package com.aurora.client.hud;

import java.util.Locale;

/**
 * The text of Health Indicators' HUD readout: one number to two decimals,
 * health plus absorption ("19.47", "20.00", "28.00" with a golden apple's
 * four absorption hearts). Remembers the last value so an unchanged total
 * formats nothing per frame.
 */
public final class HealthReadout {
    private float lastTotal = Float.NaN;
    private String text = "";

    public static String format(float hp) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0f, hp));
    }

    public void update(float health, float absorption) {
        float total = Math.max(0.0f, health) + Math.max(0.0f, absorption);
        if (Float.compare(total, lastTotal) != 0) {
            lastTotal = total;
            this.text = format(total);
        }
    }

    public String text() {
        return text;
    }
}
