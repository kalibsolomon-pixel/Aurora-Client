package com.aurora.client.util;

import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.Map;

/**
 * Rounded rectangle drawing with cached corner masks. The mask for a given
 * (radius) is computed once and reused for all subsequent fills/outlines,
 * which avoids the O(rÂ²) circle math being recomputed every frame for every
 * widget. Profiling showed this was the single biggest GUI cost in Aurora's
 * settings screen.
 */
public final class RoundedRect {

    private RoundedRect() {}

    /**
     * Cached corner mask for a given radius. {@code mask[y][x]} is true if the
     * pixel at that offset (relative to the rounded corner's outer cell) lies
     * INSIDE the rounded shape.
     */
    private static final Map<Integer, float[][]> CORNER_FILL_CACHE = new HashMap<>();
    private static final Map<Integer, int[][]> CORNER_OUTLINE_CACHE = new HashMap<>();

    public static void fill(GuiGraphics ctx, int x1, int y1, int x2, int y2, int radius, int color) {
        if (radius <= 0) {
            ctx.fill(x1, y1, x2, y2, color);
            return;
        }
        int r = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));

        // Body cross
        ctx.fill(x1 + r, y1,     x2 - r, y2,     color);
        ctx.fill(x1,     y1 + r, x1 + r, y2 - r, color);
        ctx.fill(x2 - r, y1 + r, x2,     y2 - r, color);

        float[][] mask = getCornerMask(r);

        // Top-left
        applyCornerFill(ctx, x1, y1, r, color, mask, false, false);
        // Top-right
        applyCornerFill(ctx, x2 - r, y1, r, color, mask, true, false);
        // Bottom-left
        applyCornerFill(ctx, x1, y2 - r, r, color, mask, false, true);
        // Bottom-right
        applyCornerFill(ctx, x2 - r, y2 - r, r, color, mask, true, true);
    }

    public static void outline(GuiGraphics ctx, int x1, int y1, int x2, int y2, int radius, int color) {
        int r = Math.max(0, Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2)));

        ctx.fill(x1 + r, y1,         x2 - r, y1 + 1,     color);
        ctx.fill(x1 + r, y2 - 1,     x2 - r, y2,         color);
        ctx.fill(x1,     y1 + r,     x1 + 1, y2 - r,     color);
        ctx.fill(x2 - 1, y1 + r,     x2,     y2 - r,     color);

        if (r > 0) {
            int[][] points = getOutlinePoints(r);
            for (int[] p : points) {
                int px = p[0], py = p[1];
                // Apply to all 4 corners using symmetry
                ctx.fill(x1 + r - px,     y1 + r - py,     x1 + r - px + 1, y1 + r - py + 1, color); // TL
                ctx.fill(x2 - r - 1 + px, y1 + r - py,     x2 - r + px,     y1 + r - py + 1, color); // TR
                ctx.fill(x1 + r - px,     y2 - r - 1 + py, x1 + r - px + 1, y2 - r + py,     color); // BL
                ctx.fill(x2 - r - 1 + px, y2 - r - 1 + py, x2 - r + px,     y2 - r + py,     color); // BR
            }
        }
    }

    private static float[][] getCornerMask(int r) {
        float[][] cached = CORNER_FILL_CACHE.get(r);
        if (cached != null) return cached;
        float[][] mask = new float[r][r];
        double centerX = r;
        double centerY = r;
        for (int y = 0; y < r; y++) {
            for (int x = 0; x < r; x++) {
                double px = x + 0.5;
                double py = y + 0.5;
                double dx = centerX - px;
                double dy = centerY - py;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d < r - 0.5) {
                    mask[y][x] = 1.0f;
                } else if (d > r + 0.5) {
                    mask[y][x] = 0.0f;
                } else {
                    mask[y][x] = (float) (r + 0.5 - d);
                }
            }
        }
        CORNER_FILL_CACHE.put(r, mask);
        return mask;
    }

    /**
     * Render one corner cell of the rounded rect. Rather than emitting one
     * {@code ctx.fill} per pixel (the previous approach: {@code r²} fills per
     * corner, {@code 4r²} per rect), we walk each row of the corner and emit
     * <b>one</b> contiguous horizontal span for the fully-covered run, plus
     * at most two boundary pixels whose alpha is scaled by their fractional
     * coverage. This reduces the per-corner fill count from {@code r²} to
     * {@code ~3r}, which is critical because {@code RoundedRect.fill} is
     * called every frame by {@code HudBackgrounds.SOLID} for every HUD module.
     *
     * <p>The mask is the cached antialiasing coverage table; {@code flipX}
     * and {@code flipY} mirror it for the other three corners.
     */
    private static void applyCornerFill(GuiGraphics ctx, int cellX, int cellY, int r, int color,
                                        float[][] mask, boolean flipX, boolean flipY) {
        // alpha of the body (the mask's max value, used to detect full-opacity runs).
        for (int y = 0; y < r; y++) {
            int my = flipY ? (r - 1 - y) : y;
            int rowY = cellY + y;
            int runStart = -1;
            for (int x = 0; x < r; x++) {
                int mx = flipX ? (r - 1 - x) : x;
                float alpha = mask[my][mx];
                boolean full = alpha >= 0.995f;
                if (full) {
                    // Start or extend a contiguous full-opacity run.
                    if (runStart < 0) runStart = x;
                } else {
                    // Flush any pending full run as a single span.
                    if (runStart >= 0) {
                        ctx.fill(cellX + runStart, rowY, cellX + x, rowY + 1, color);
                        runStart = -1;
                    }
                    // Boundary / antialiased pixel — emit individually.
                    if (alpha > 0.01f) {
                        ctx.fill(cellX + x, rowY, cellX + x + 1, rowY + 1, scaleAlpha(color, alpha));
                    }
                }
            }
            // Flush a trailing run reaching the cell's right edge.
            if (runStart >= 0) {
                ctx.fill(cellX + runStart, rowY, cellX + r, rowY + 1, color);
            }
        }
    }

    private static int scaleAlpha(int argb, float scale) {
        float s = Math.max(0f, Math.min(1f, scale));
        int a = (argb >>> 24) & 0xFF;
        int newA = Math.round(a * s);
        return (newA << 24) | (argb & 0x00FFFFFF);
    }

    /** Returns sorted list of (xOffset, yOffset) pixel positions on the top-left arc, suitable for symmetric reflection. */
    private static int[][] getOutlinePoints(int r) {
        int[][] cached = CORNER_OUTLINE_CACHE.get(r);
        if (cached != null) return cached;

        // Generate via midpoint algorithm
        java.util.List<int[]> list = new java.util.ArrayList<>(r * 2);
        int x = r;
        int y = 0;
        int err = 1 - x;
        while (x >= y) {
            list.add(new int[] { x, y });
            list.add(new int[] { y, x });
            y++;
            if (err < 0) err += 2 * y + 1;
            else { x--; err += 2 * (y - x) + 1; }
        }
        int[][] arr = list.toArray(new int[0][]);
        CORNER_OUTLINE_CACHE.put(r, arr);
        return arr;
    }
}