package com.aurora.client.util;

/**
 * Pure formatting and color math for Aurora's Tab List polish.
 *
 * <p>Lives in the Minecraft-free core (ADR 0003) so ping slot sizing,
 * latency formatting, background opacity scaling, and self-row highlighting
 * are unit-tested without a client.
 */
public final class TabListStyle {
    /** Vanilla's reserved width for the 5-bar ping icon. */
    public static final int VANILLA_PING_WIDTH = 13;
    /** Reserved width for numeric ping values (fits 3-digit values like "250"). */
    public static final int NUMERIC_PING_WIDTH = 22;
    /** Reserved width for numeric ping values with "ms" suffix (fits "250ms"). */
    public static final int NUMERIC_PING_MS_WIDTH = 34;

    public static final double DEFAULT_BG_OPACITY = 0.5;

    private TabListStyle() {}

    /** Reserved column width for the ping slot in {@code PlayerTabOverlay}. */
    public static int pingColumnWidth(boolean numericPingEnabled, boolean showMs) {
        if (!numericPingEnabled) return VANILLA_PING_WIDTH;
        return showMs ? NUMERIC_PING_MS_WIDTH : NUMERIC_PING_WIDTH;
    }

    /** Formats a player's latency in milliseconds, or {@code null} when unknown/negative. */
    public static String formatPing(int latencyMs, boolean showMs) {
        if (latencyMs < 0) return null;
        return showMs ? latencyMs + "ms" : Integer.toString(latencyMs);
    }

    /** Clamps background opacity to {@code [0.0, 1.0]}, falling back to {@link #DEFAULT_BG_OPACITY} on NaN. */
    public static double clampOpacity(double opacity) {
        if (Double.isNaN(opacity)) return DEFAULT_BG_OPACITY;
        if (opacity <= 0.0) return 0.0;
        if (opacity >= 1.0) return 1.0;
        return opacity;
    }

    /** Outer tab-list container fill color (black with alpha scaled from {@code opacity}). */
    public static int panelFillColor(double opacity) {
        int alpha = (int) Math.round(clampOpacity(opacity) * 255.0);
        return (alpha & 0xFF) << 24;
    }

    /**
     * Per-row backing fill color. At {@code 0.5} opacity this matches vanilla's
     * {@code 0x20FFFFFF}; at {@code 0.0} it becomes fully transparent so rows
     * float cleanly without grey bars.
     */
    public static int rowFillColor(double opacity) {
        int alpha = (int) Math.round(clampOpacity(opacity) * 64.0);
        return ((alpha & 0xFF) << 24) | 0x00FFFFFF;
    }

    /** Translucent accent wash drawn over the local player's own row when highlighted. */
    public static int selfRowWashColor(int accentArgb) {
        return 0x38000000 | (accentArgb & 0x00FFFFFF);
    }

    /** Crisp 1px left edge marker for the local player's own row. */
    public static int selfRowEdgeColor(int accentArgb) {
        return 0xFF000000 | (accentArgb & 0x00FFFFFF);
    }
}
