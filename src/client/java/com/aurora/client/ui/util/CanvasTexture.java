package com.aurora.client.ui.util;

import com.aurora.client.AuroraClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Rasterized pixel-canvas for the crosshair custom-canvas editor — the
 * "resolve once, cache, blit" fix for the per-cell fill cost.
 *
 * <p><b>Why this exists (measured 2026-08-29):</b> the editor used to
 * submit every grid cell as individual {@code GuiGraphics.fill()} calls
 * (checker background + on-overlay + two 1-px border strips = 4 fills per
 * cell, every frame). At the 33×33 preset that is 4,356 fill submissions
 * per frame, and each submission allocates a fresh
 * {@code ColoredRectangleRenderState} plus a {@code Matrix3x2f} copy of
 * the pose — ~8,700 allocations and ~12.8 ms of CPU submission time per
 * frame on the profile machine, with GC-driven spikes to 26.5 ms,
 * re-incurred every frame although the pixels only change while the user
 * is actually drawing.
 *
 * <p><b>Fix:</b> the whole grid (checker, lit cells, cell borders,
 * disabled tint) is rasterized into a {@link NativeImage} and uploaded as
 * a {@link DynamicTexture}; each frame is ONE textured blit with zero
 * per-cell work and zero allocation. The raster is regenerated only when
 * something actually changed (pixels, dimensions, cell scale, disabled
 * state). The raster math below replicates the old fill sequence exactly
 * — same colors, same order, same source-over compositing (the same
 * integer blend {@link UiLayerCache} uses to stay pixel-identical with
 * the GPU's live fills) — so a clean cache blit is visually identical to
 * the old per-cell path at every size the old path supported.
 *
 * <p><b>Cost model after the fix:</b> per-frame cost is O(1); the
 * size-dependent cost moved into {@link #regenerate}, which runs once per
 * edit event (cell crossed during a drag), not per frame.
 * {@link #measureRasterMs} times exactly that regen work on the current
 * machine — the measured input the resolution-warning flow is driven by
 * (never by hardware-name heuristics).
 *
 * <p>Threading: {@link #rasterInto} and {@link #measureRasterMs} touch
 * only the {@link NativeImage} backing array (no GL) and are safe
 * off-thread; {@link #regenerate}'s upload and {@link #blit} must run on
 * the render thread, like every other texture in the mod.
 */
public final class CanvasTexture {

    private static final AtomicLong SEQ = new AtomicLong();

    // Checker + cell colors, copied verbatim from the old per-cell path.
    private static final int CHECKER_LIGHT = 0xFF2A3346;
    private static final int CHECKER_DARK  = 0xFF1F2638;
    private static final int CELL_ON_COLOR = 0xFFFFFFFF;
    private static final int CELL_BORDER   = 0x40000000;
    private static final int CELL_ON_DISABLED  = 0x66FFFFFF;
    private static final int CELL_BORDER_DISABLED = 0x22000000;

    /** Legacy square presets keep their historical cell scale so existing
     *  configs look pixel-for-pixel like they did before the raster path. */
    private static int legacyCellPx(int side) {
        return switch (side) {
            case 5 -> 20;
            case 11 -> 13;
            case 25 -> 6;
            case 33 -> 5;
            default -> -1;
        };
    }

    /**
     * Cell scale (logical px per grid cell) for a grid of {@code w×h}.
     * Legacy presets keep their exact historical sizes; everything else
     * shrinks to keep the canvas within ~168 logical px on its larger
     * axis (a 34-wide grid gets 4-px cells, a 128-wide grid 1-px cells),
     * clamped to 1..20.
     */
    public static int cellPxFor(int w, int h) {
        if (w == h) {
            int legacy = legacyCellPx(w);
            if (legacy > 0) return legacy;
        }
        int maxDim = Math.max(w, h);
        return Math.max(1, Math.min(20, 168 / Math.max(1, maxDim)));
    }

    /** {@code true} when cells are large enough for the 1-px grid lines
     *  to read (below 4 px they would cover most of the checker — the old
     *  path never ran at those scales, so this is not a visual change). */
    public static boolean bordersVisible(int cellPx) {
        return cellPx >= 4;
    }

    private NativeImage image;
    private DynamicTexture tex;
    private Identifier texId;
    private long version = Long.MIN_VALUE;

    /** {@code true} when the texture holds pixels for {@code v}. */
    public boolean isCurrent(long v) {
        return tex != null && version == v;
    }

    /**
     * Rasterizes the grid into the cached image and uploads it.
     * Render thread only (GL upload); cheap enough to run per edit.
     *
     * @param v caller's dirty key — regenerate only when it changes
     *          (dims × cellPx × disabled × edit-generation).
     */
    public void regenerate(long v, boolean[] pixels, int gridW, int gridH,
                           int cellPx, boolean disabled) {
        int texW = gridW * cellPx;
        int texH = gridH * cellPx;
        if (image == null || image.getWidth() != texW || image.getHeight() != texH) {
            if (image != null) image.close();
            image = new NativeImage(NativeImage.Format.RGBA, texW, texH, false);
            if (tex != null) { // size changed: force re-register below
                releaseTexture();
            }
        }
        rasterInto(image, pixels, gridW, gridH, cellPx, disabled);
        ensureGpu();
        tex.upload();
        version = v;
    }

    /** One textured draw of the cached grid at logical position (x, y). */
    public void blit(GuiGraphics g, int x, int y, int gridW, int gridH, int cellPx) {
        if (tex == null) return;
        int w = gridW * cellPx;
        int h = gridH * cellPx;
        g.blit(RenderPipelines.GUI_TEXTURED, texId,
                x, y, 0f, 0f, w, h, w, h, w, h, -1);
    }

    /** Release GPU + CPU resources. Idempotent. Render thread. */
    public void dispose() {
        releaseTexture();
        if (image != null) {
            image.close();
            image = null;
        }
        version = Long.MIN_VALUE;
    }

    private void ensureGpu() {
        if (tex != null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getTextureManager() == null) return;
        long id = SEQ.incrementAndGet();
        texId = Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "pixel_canvas/" + id);
        tex = new DynamicTexture(() -> "pixel_canvas_" + id, image);
        mc.getTextureManager().register(texId, tex);
    }

    private void releaseTexture() {
        if (tex != null) {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.getTextureManager() != null && texId != null) {
                    mc.getTextureManager().release(texId);
                }
            } catch (Throwable ignored) {
            }
            tex = null;
            texId = null;
        }
    }

    // ==== Rasterization ====

    /**
     * Paints the grid into {@code img} (sized {@code gridW*cellPx ×
     * gridH*cellPx}), reproducing the old per-cell fill sequence exactly:
     * checker background, then the 1-px-inset on-overlay for lit cells,
     * then the right/bottom 1-px border strips — with the same
     * source-over blend the GPU applied, so the result is pixel-identical
     * to the pre-raster fills. No GL; safe off-thread.
     */
    public static void rasterInto(NativeImage img, boolean[] pixels, int gridW, int gridH,
                                  int cellPx, boolean disabled) {
        int texW = gridW * cellPx;
        int texH = gridH * cellPx;
        boolean borders = bordersVisible(cellPx);
        int border = disabled ? CELL_BORDER_DISABLED : CELL_BORDER;
        int onColor = disabled ? CELL_ON_DISABLED : CELL_ON_COLOR;

        for (int gy = 0; gy < gridH; gy++) {
            for (int gx = 0; gx < gridW; gx++) {
                int x0 = gx * cellPx;
                int y0 = gy * cellPx;

                // 1. checker background (opaque)
                int bg = ((gx + gy) & 1) == 0 ? CHECKER_LIGHT : CHECKER_DARK;
                if (disabled) bg = lerpHalfToBlack(bg);
                fillRect(img, texW, texH, x0, y0, x0 + cellPx, y0 + cellPx, bg);

                // 2. lit overlay, inset by 1 (skipped when the inset is empty —
                //    the old fill() call degenerated to a no-op there too)
                if (pixels[gy * gridW + gx] && cellPx > 2) {
                    fillRect(img, texW, texH, x0 + 1, y0 + 1, x0 + cellPx - 1, y0 + cellPx - 1, onColor);
                } else if (pixels[gy * gridW + gx] && cellPx == 1) {
                    // legacy quirk preserved: cellPx==1 lit cell filled the
                    // whole cell (fill() swapped the inverted coords)
                    fillRect(img, texW, texH, x0, y0, x0 + 1, y0 + 1, onColor);
                }

                // 3. right + bottom 1-px border strips
                if (borders) {
                    fillRect(img, texW, texH, x0 + cellPx - 1, y0, x0 + cellPx, y0 + cellPx, border);
                    fillRect(img, texW, texH, x0, y0 + cellPx - 1, x0 + cellPx, y0 + cellPx, border);
                }
            }
        }
    }

    /** Source-over blend of one rect, matching UiLayerCache's sink math. */
    private static void fillRect(NativeImage img, int texW, int texH,
                                 int x1, int y1, int x2, int y2, int argb) {
        int x0 = Math.max(0, x1);
        int y0 = Math.max(0, y1);
        int x3 = Math.min(texW, x2);
        int y3 = Math.min(texH, y2);
        if (x3 <= x0 || y3 <= y0) return;

        int srcA = (argb >>> 24) & 0xFF;
        if (srcA == 0) return;
        if (srcA == 255) {
            for (int y = y0; y < y3; y++) {
                for (int x = x0; x < x3; x++) {
                    img.setPixel(x, y, argb);
                }
            }
            return;
        }
        int srcR = (argb >>> 16) & 0xFF;
        int srcG = (argb >>> 8) & 0xFF;
        int srcB = argb & 0xFF;
        int invA = 255 - srcA;
        int srcPr = srcR * srcA;
        int srcPg = srcG * srcA;
        int srcPb = srcB * srcA;
        for (int y = y0; y < y3; y++) {
            for (int x = x0; x < x3; x++) {
                int d = img.getPixel(x, y); // ARGB
                int dstA = (d >>> 24) & 0xFF;
                int dstR = (d >>> 16) & 0xFF;
                int dstG = (d >>> 8) & 0xFF;
                int dstB = d & 0xFF;

                int outA = srcA + Math.round(dstA * invA / 255f);
                if (outA == 0) {
                    img.setPixel(x, y, 0);
                    continue;
                }
                int r = Math.min(255, Math.round((srcPr + Math.round(dstR * dstA * invA / 255f)) / (float) outA));
                int g = Math.min(255, Math.round((srcPg + Math.round(dstG * dstA * invA / 255f)) / (float) outA));
                int b = Math.min(255, Math.round((srcPb + Math.round(dstB * dstA * invA / 255f)) / (float) outA));
                img.setPixel(x, y, (outA << 24) | (r << 16) | (g << 8) | b);
            }
        }
    }

    private static int lerpHalfToBlack(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        r = Math.round(r + (0 - r) * 0.5f);
        g = Math.round(g + (0 - g) * 0.5f);
        b = Math.round(b + (0 - b) * 0.5f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ==== Measured-cost benchmark ====

    /**
     * Times {@code iterations} full-grid rasters at the requested size on
     * the <em>current machine</em> and returns the mean milliseconds per
     * raster. This is the exact CPU work {@link #regenerate} performs per
     * edit event — the only size-dependent editor cost left after the
     * caching fix — measured, not estimated from hardware names.
     * Thread-safe (no GL); intended to run on a worker thread while the
     * UI keeps rendering.
     */
    public static double measureRasterMs(int gridW, int gridH, int cellPx, int iterations) {
        int texW = Math.max(1, gridW * cellPx);
        int texH = Math.max(1, gridH * cellPx);
        // Raster cost is independent of the lit pattern (every cell is
        // painted regardless); an empty grid is the honest baseline.
        boolean[] px = new boolean[gridW * gridH];
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, texW, texH, false);
        try {
            // one warm-up outside the measurement (JIT + page faults)
            rasterInto(img, px, gridW, gridH, cellPx, false);
            long t0 = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                rasterInto(img, px, gridW, gridH, cellPx, false);
            }
            long dt = System.nanoTime() - t0;
            return dt / 1_000_000.0 / iterations;
        } finally {
            img.close();
        }
    }

    /**
     * Measures the per-frame CPU cost of the <em>in-game</em> custom
     * crosshair at this resolution: the run-length scan over a
     * worst-case (fully lit) grid. Returns {@code [scanMs, runs]} where
     * {@code runs} is the number of horizontal runs a dense pattern
     * produces — the number of {@code fill()} submissions the HUD path
     * makes per frame. The caller combines {@code runs} with a
     * machine-measured per-fill cost (see PixelCanvasSetting's probe) for
     * a fully measured composite. Off-thread safe.
     */
    public static double[] measureHudScan(int gridW, int gridH, int iterations) {
        boolean[] dense = new boolean[gridW * gridH];
        java.util.Arrays.fill(dense, true);
        long t0 = System.nanoTime();
        long runs = 0;
        for (int i = 0; i < iterations; i++) {
            runs = 0;
            for (int gy = 0; gy < gridH; gy++) {
                int rowBase = gy * gridW;
                int gx = 0;
                while (gx < gridW) {
                    if (!dense[rowBase + gx]) {
                        gx++;
                        continue;
                    }
                    int start = gx;
                    while (gx < gridW && dense[rowBase + gx]) gx++;
                    if (gx > start) runs++;
                }
            }
        }
        double scanMs = (System.nanoTime() - t0) / 1_000_000.0 / iterations;
        return new double[]{scanMs, runs};
    }
}
