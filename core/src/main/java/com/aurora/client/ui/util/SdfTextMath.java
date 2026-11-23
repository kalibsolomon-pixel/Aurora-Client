package com.aurora.client.ui.util;

/** Minecraft-free SDF text math shared by generation, shaders and tests. */
public final class SdfTextMath {
    public static final int STRIKE_SIZE = 64;
    /** FreeType's SDF spread, in strike pixels; also its documented default. */
    public static final int SPREAD = 8;
    /** Extra far-outside texels around FreeType's own spread, on every side. */
    public static final int PADDING = 1;
    /** FreeType maps the exact edge to byte 128: 128 * (distance / spread + 1). */
    public static final int EDGE_BYTE = 128;
    public static final float EDGE_BIAS = 0.30f;
    private static final float MIN_GRADIENT = 0.0001f;

    private SdfTextMath() {}

    /** Decodes an unsigned SDF byte to signed strike pixels; positive is inside. */
    public static float signedDistance(int encodedByte, int spread) {
        int encoded = Math.max(0, Math.min(255, encodedByte));
        return (encoded - EDGE_BYTE) * (spread / (float) EDGE_BYTE);
    }

    /**
     * Screen-space coverage for a signed distance and its derivative width: texels per
     * screen pixel, which the shader takes from the interpolated UVs rather than from the
     * sampled distance (whose derivative block can reach past the glyph's cell).
     */
    public static float coverage(float distance, float derivativeWidth) {
        float width = Math.max(Math.abs(derivativeWidth), MIN_GRADIENT);
        return Math.max(0.0f, Math.min(1.0f, 0.5f + (distance - EDGE_BIAS) / width));
    }

    /** A dimension after adding transparent padding on both sides. */
    public static int paddedSize(int bitmapSize, int padding) {
        return bitmapSize + 2 * padding;
    }

    public static float quadStart(float rasterEdge, int padding, float logicalPerTexel) {
        return rasterEdge - padding * logicalPerTexel;
    }

    public static float insetUv(int edge, boolean upper, int pageSize) {
        float center = (edge + (upper ? -0.5f : 0.5f)) / pageSize;
        return Math.clamp(center, 0f, 1f);
    }
}
