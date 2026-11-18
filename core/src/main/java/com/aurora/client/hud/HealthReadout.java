package com.aurora.client.hud;

import java.util.Locale;

/**
 * The text of Health Indicators' HUD readout: health to two decimals
 * ("19.47", "20.00"), with absorption after it ("+4.00") when there is any.
 * Remembers the last value so an unchanged health formats nothing per frame.
 */
public final class HealthReadout {
    private float lastHealth = Float.NaN;
    private float lastAbsorption = Float.NaN;
    private String health = "";
    private String absorption = "";

    public static String format(float hp) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0f, hp));
    }

    public void update(float health, float absorption) {
        if (Float.compare(health, lastHealth) != 0) {
            lastHealth = health;
            this.health = format(health);
        }
        if (Float.compare(absorption, lastAbsorption) != 0) {
            lastAbsorption = absorption;
            this.absorption = absorption > 0.0f ? "+" + format(absorption) : "";
        }
    }

    public String health() {
        return health;
    }

    /** Empty without absorption. */
    public String absorption() {
        return absorption;
    }
}
