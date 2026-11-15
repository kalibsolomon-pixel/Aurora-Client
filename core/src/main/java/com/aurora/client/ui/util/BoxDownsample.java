package com.aurora.client.ui.util;

/** Keeps a two-texel low-pass footprint when rounded half sizes are not exactly 2:1. */
public final class BoxDownsample {
    private BoxDownsample() {}

    /** Exact 2:1 bilinear sampling already averages two texels; other phases need two taps. */
    public static float halfTexel(int sourceSpan, int targetSpan, int textureSpan) {
        return sourceSpan > targetSpan && sourceSpan != targetSpan * 2 ? .5f / textureSpan : 0f;
    }
}
