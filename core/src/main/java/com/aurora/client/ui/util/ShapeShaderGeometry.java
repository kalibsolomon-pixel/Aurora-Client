package com.aurora.client.ui.util;

/** Bounds policy for a one-quad shape with antialiasing in the final framebuffer. */
public final class ShapeShaderGeometry {
    private ShapeShaderGeometry() {}

    /**
     * Logical-space padding that remains at least one framebuffer pixel after
     * the GUI scale and an arbitrary nonsingular 2D pose. Returns zero when the
     * transform is too small or invalid for a bounded shader quad.
     */
    public static float padding(float guiScale, float m00, float m01, float m10, float m11) {
        if (!(guiScale > 0) || !Float.isFinite(guiScale)) return 0;
        double a = m00, b = m01, c = m10, d = m11;
        double col0 = a * a + b * b, col1 = c * c + d * d, dot = a * c + b * d;
        double largest = Math.sqrt((col0 + col1 + Math.hypot(col0 - col1, 2 * dot)) * .5);
        double smallest = Math.abs(a * d - b * c) / largest;
        if (!Double.isFinite(smallest) || smallest < 1.0 / 64.0) return 0;
        double pad = 1.0 / (guiScale * Math.min(1.0, smallest));
        return Double.isFinite(pad) && pad <= Float.MAX_VALUE ? (float) pad : 0;
    }
}
