package com.aurora.client.util;

/**
 * Pure scale and background-opacity bounds for Aurora's Nametag styling.
 *
 * <p>Lives in the Minecraft-free core (ADR 0003).
 */
public final class NametagStyle {
    public static final double MIN_SCALE = 0.5;
    public static final double MAX_SCALE = 2.0;
    public static final double DEFAULT_SCALE = 1.0;
    public static final double DEFAULT_BG_OPACITY = 0.25;
    /** Vanilla's world-space billboard scale factor for nametag text. */
    public static final float VANILLA_BASE_SCALE = 0.025f;

    private NametagStyle() {}

    /** Clamps nametag scale to {@code [0.5, 2.0]}, defaulting to {@code 1.0} on NaN. */
    public static double clampScale(double scale) {
        if (Double.isNaN(scale)) return DEFAULT_SCALE;
        if (scale < MIN_SCALE) return MIN_SCALE;
        if (scale > MAX_SCALE) return MAX_SCALE;
        return scale;
    }

    /** Multiplies a vanilla nametag PoseStack scale component by the clamped multiplier. */
    public static float scaledComponent(float vanillaComponent, double scale) {
        double clamped = clampScale(scale);
        if (Math.abs(clamped - 1.0) < 1e-4) return vanillaComponent;
        return (float) (vanillaComponent * clamped);
    }

    /** Clamps custom nametag background opacity to {@code [0.0, 1.0]}, defaulting to {@code 0.25} on NaN. */
    public static double clampOpacity(double opacity) {
        if (Double.isNaN(opacity)) return DEFAULT_BG_OPACITY;
        if (opacity <= 0.0) return 0.0;
        if (opacity >= 1.0) return 1.0;
        return opacity;
    }
}
