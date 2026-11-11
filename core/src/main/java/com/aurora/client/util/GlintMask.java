package com.aurora.client.util;

/** Texture conversion for the additive shimmer and the alternative coverage mask. */
public final class GlintMask {
    private GlintMask() {}

    /** Original additive recolor: the source value ramp drives selected RGB, alpha stays intact. */
    public static int additive(int source, int selected, int intensityPercent) {
        int value = Math.max((source >>> 16) & 255, Math.max((source >>> 8) & 255, source & 255));
        double strength = value / 255.0 * (selected >>> 24) / 255.0
                * Math.max(0, Math.min(100, intensityPercent)) / 100.0;
        return (source & 0xff000000)
                | ((int)Math.round(((selected >>> 16) & 255) * strength) << 16)
                | ((int)Math.round(((selected >>> 8) & 255) * strength) << 8)
                | (int)Math.round((selected & 255) * strength);
    }

    public static int colorize(int source, int selected, int intensityPercent) {
        return colorize(source, selected, intensityPercent, 0, 255);
    }

    /** Remove a non-black base when a pack stores the whole pattern above zero. */
    public static int colorize(int source, int selected, int intensityPercent, int base, int peak) {
        int sourceAlpha = source >>> 24;
        int value = Math.max((source >>> 16) & 255, Math.max((source >>> 8) & 255, source & 255));
        double coverage = peak > base ? Math.max(0, (value - base) / (double) (peak - base)) : value > 0 ? 1 : 0;
        double strength = Math.max(0, Math.min(100, intensityPercent)) / 100.0;
        int alpha = (int) Math.round(sourceAlpha * coverage * (selected >>> 24) / 255.0 * strength);
        // Constant RGB even in transparent texels prevents dark fringes under filtering.
        return (alpha << 24) | (selected & 0x00ffffff);
    }
}
