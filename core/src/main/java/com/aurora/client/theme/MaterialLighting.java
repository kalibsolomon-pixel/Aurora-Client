package com.aurora.client.theme;

/** Pure captured-pixel lighting. Deltas are absolute HSL lightness points (0..100). */
public final class MaterialLighting {
    private MaterialLighting() {}

    public static float verticalDelta(int row, int height, float top, float bottom) {
        return height <= 1 ? (top + bottom) * .5f : top + (bottom - top) * row / (height - 1f);
    }

    /** Preserve hue, HSL saturation and alpha; only lightness changes, with endpoint clamping. */
    public static int lightness(int argb, float points) {
        if ((argb >>> 24) == 0 || points == 0) return argb;
        double r = ((argb >>> 16) & 255) / 255d;
        double g = ((argb >>> 8) & 255) / 255d;
        double b = (argb & 255) / 255d;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double light = (max + min) * .5, chroma = max - min;
        double next = Math.max(0, Math.min(1, light + points / 100d));
        // Scaling chroma around the new lightness preserves H and S without hue conversion.
        double ratio = chroma == 0 ? 0 : (1 - Math.abs(2 * next - 1)) / (1 - Math.abs(2 * light - 1));
        return (argb & 0xff000000) | channel(next + (r - light) * ratio) << 16
                | channel(next + (g - light) * ratio) << 8 | channel(next + (b - light) * ratio);
    }

    /** Source-atop overlay: edge ink changes RGB inside coverage, never enlarges the silhouette. */
    public static int edge(int argb, int overlay) {
        if ((argb >>> 24) == 0) return argb;
        double a = (overlay >>> 24) / 255d;
        int r = (int) Math.round(((argb >>> 16) & 255) * (1 - a) + ((overlay >>> 16) & 255) * a);
        int g = (int) Math.round(((argb >>> 8) & 255) * (1 - a) + ((overlay >>> 8) & 255) * a);
        int b = (int) Math.round((argb & 255) * (1 - a) + (overlay & 255) * a);
        return (argb & 0xff000000) | r << 16 | g << 8 | b;
    }

    /** A black shadow behind a straight-alpha pixel, preserving its already-lit colour. */
    public static int shadowBehind(int argb, float shadowAlpha) {
        double a = (argb >>> 24) / 255d;
        double out = a + Math.max(0, Math.min(1, shadowAlpha)) * (1 - a);
        if (out == 0) return 0;
        double ratio = a / out;
        return (int) Math.round(out * 255) << 24
                | (int) Math.round(((argb >>> 16) & 255) * ratio) << 16
                | (int) Math.round(((argb >>> 8) & 255) * ratio) << 8
                | (int) Math.round((argb & 255) * ratio);
    }

    private static int channel(double value) { return (int) Math.round(Math.max(0, Math.min(1, value)) * 255); }
}
