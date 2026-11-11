package com.aurora.client.util;

/**
 * Grid dimensions for the crosshair custom canvas.
 *
 * <p>The canvas is stored in the config as a flat {@code boolean[]} whose
 * length is {@code width * height}. Historically the only shapes were
 * square presets and every consumer inferred the side length via
 * {@code sqrt(len)}; free-form width/height (see PixelCanvasSetting)
 * made the dimensions explicit config fields. Resolution rule, shared by
 * the editor, the HUD renderer and the config migration:
 *
 * <ol>
 *   <li>{@code cfgW * cfgH == len} — trust the explicit dims;</li>
 *   <li>else if {@code len} is a perfect square — legacy square layout
 *       (old configs and profile snapshots predate the dim fields);</li>
 *   <li>else — 11×11 sentinel. Callers must treat a mismatching array as
 *       unusable and fall back to their defensive path (the editor shows
 *       nothing until a valid resize; the HUD draws nothing).</li>
 * </ol>
 */
public final class GridDims {

    /** Factory default canvas size (matches {@code defaultCustomPixels}). */
    public static final int DEFAULT = 11;

    public final int w;
    public final int h;

    private GridDims(int w, int h) {
        this.w = w;
        this.h = h;
    }

    public static GridDims of(int w, int h) {
        return new GridDims(w, h);
    }

    /**
     * Resolves the effective dimensions for a pixel array of
     * {@code len} entries given the explicitly-configured dims (may be
     * stale/zero on legacy configs).
     */
    public static GridDims resolve(int len, int cfgW, int cfgH) {
        if (len > 0 && cfgW > 0 && cfgH > 0 && (long) cfgW * cfgH == len) {
            return new GridDims(cfgW, cfgH);
        }
        if (len > 0) {
            int side = (int) Math.round(Math.sqrt(len));
            if ((long) side * side == len) return new GridDims(side, side);
        }
        return new GridDims(DEFAULT, DEFAULT);
    }

    /** {@code true} when the array matches these dims and can be drawn. */
    public boolean matches(boolean[] pixels) {
        return pixels != null && pixels.length == w * h;
    }
}
