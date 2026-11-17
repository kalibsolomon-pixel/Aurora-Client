package com.aurora.client.ui.util;

/** Minecraft-free SDF text math shared by generation, shaders and tests. */
public final class SdfTextMath {
    /** Strike multiplier relative to Aurora's raster provider at the current GUI scale. */
    public static final float STRIKE_MULTIPLIER = 4.0f;
    /** FreeType's SDF spread, in strike pixels; also its documented default. */
    public static final int SPREAD = 8;
    /** Extra far-outside texels around FreeType's own spread, on every side. */
    public static final int PADDING = 1;
    /** FreeType maps the exact edge to byte 128: 128 * (distance / spread + 1). */
    public static final int EDGE_BYTE = 128;
    private static final float MIN_GRADIENT = 0.0001f;

    private SdfTextMath() {}

    /** Decodes an unsigned SDF byte to signed strike pixels; positive is inside. */
    public static float signedDistance(int encodedByte, int spread) {
        int encoded = Math.max(0, Math.min(255, encodedByte));
        return (encoded - EDGE_BYTE) * (spread / (float) EDGE_BYTE);
    }

    /** Screen-space coverage for a signed distance and its derivative width. */
    public static float coverage(float distance, float derivativeWidth) {
        float width = Math.max(Math.abs(derivativeWidth), MIN_GRADIENT);
        return Math.max(0.0f, Math.min(1.0f, 0.5f + distance / width));
    }

    /** A dimension after adding transparent padding on both sides. */
    public static int paddedSize(int bitmapSize, int padding) {
        return bitmapSize + 2 * padding;
    }

    /** Left bearing after padding extends the quad to the left. Advances remain untouched. */
    public static float paddedLeftBearing(int bitmapLeft, int padding, float oversample) {
        return (bitmapLeft - padding) / oversample;
    }

    /** Top bearing is measured upward, so padding above the bitmap increases it. */
    public static float paddedTopBearing(int bitmapTop, int padding, float oversample) {
        return (bitmapTop + padding) / oversample;
    }
}
