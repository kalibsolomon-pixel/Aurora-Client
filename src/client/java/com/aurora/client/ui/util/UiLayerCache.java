package com.aurora.client.ui.util;

import com.aurora.client.AuroraClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.concurrent.atomic.AtomicLong;
import java.nio.IntBuffer;
import java.util.Arrays;
import org.lwjgl.system.MemoryUtil;

/**
 * Off-screen static-layer cache for pixel-heavy Aurora screens. Mirrors the
 * "resolve once, cache, blit" discipline already used by the World Map
 * ({@code RegionCache}) and minimap: a screen's <em>unchanging</em> chrome
 * (window glow, panel fills, static swatches/tracks/cards) is rasterized
 * into a {@link NativeImage} <b>once</b> via the exact same
 * {@link RenderUtil} AA math (through {@link RenderUtil#beginCapture}), then
 * uploaded as a {@link DynamicTexture} and drawn each frame as a single
 * textured blit. Only genuinely-dirty frames re-rasterize.
 *
 * <p><b>Identical quality:</b> the capture sink runs the same physical-pixel
 * row/coverage math and the same source-over compositing the live path uses,
 * and the final blit is 1:1 at native resolution — no resampling, no
 * quality loss.
 *
 * <p><b>Lifecycle:</b> one instance per screen; {@link #dispose()} on close.
 * {@link #ensureSize} re-allocates on window/GUI-scale resize.
 */
public final class UiLayerCache implements RenderUtil.RectSink {

    private static final AtomicLong SEQ = new AtomicLong();

    private NativeImage image;
    private DynamicTexture tex;
    private Identifier texId;
    private int w = -1;
    private int h = -1;
    private long version = Long.MIN_VALUE;
    private int[] scaledRow;
    private int[] maskRuns;
    private int maskW, maskH;
    private float maskRadius;
    private boolean maskCircle;

    /** {@code true} when a texture for the current version exists and is current. */
    public boolean isCurrent(long v) {
        return tex != null && version == v;
    }

    /** The sink to hand to {@link RenderUtil#beginCapture} for a capture pass. */
    public RenderUtil.RectSink sink() {
        return this;
    }

    /** (Re)allocate the backing image/texture when the framebuffer size changes. */
    public void ensureSize(int w, int h) {
        if (this.w == w && this.h == h && image != null) return;
        releaseTexture();
        if (image != null) {
            image.close();
        }
        image = new NativeImage(NativeImage.Format.RGBA, w, h, false); // zero-init = transparent
        this.w = w;
        this.h = h;
    }

    /** Clear the buffer to transparent — call before a capture pass. */
    public void clear() {
        if (image != null) {
            image.fillRect(0, 0, w, h, 0);
        }
    }

    /** Upscale cached data, without resampling world columns or allocating per update.
     * Expanded rows are copied in bulk; repeated source rows use native memcpy. */
    public void writeScaledAbgr(int[] source, int sourceW, int sourceH) {
        if (image == null || sourceW <= 0 || sourceH <= 0 || source.length != sourceW * sourceH)
            throw new IllegalArgumentException("Valid source and allocated destination required");
        if (scaledRow == null || scaledRow.length != w) scaledRow = new int[w];
        long pointer = image.getPointer();
        IntBuffer pixels = MemoryUtil.memIntBuffer(pointer, w * h);
        int previousY = -1;
        for (int y = 0; y < h; y++) {
            int sourceY = y * sourceH / h;
            if (sourceY == previousY) MemoryUtil.memCopy(pointer + (long) (y - 1) * w * 4, pointer + (long) y * w * 4, (long) w * 4);
            else {
                int row = sourceY * sourceW;
                for (int x = 0; x < w; x++) scaledRow[x] = source[row + x * sourceW / w];
                pixels.position(y * w); pixels.put(scaledRow);
                previousY = sourceY;
            }
        }
    }

    /**
     * Nearest-cell affine resample of an {@code n×n} ABGR grid: device pixel
     * {@code (x, y)} reads cell {@code (u0 + x·duX + y·duY, v0 + x·dvX + y·dvY)}.
     * Pixels landing outside the grid take {@code fallback}; every pixel gets
     * {@code alpha}. Two adds and two casts per pixel, no allocation.
     */
    public void writeAffineAbgr(int[] grid, int n, double u0, double v0, double duX, double dvX,
                                double duY, double dvY, int alpha, int fallback) {
        if (image == null || n <= 0 || grid.length < n * n)
            throw new IllegalArgumentException("Valid grid and allocated destination required");
        if (scaledRow == null || scaledRow.length != w) scaledRow = new int[w];
        IntBuffer pixels = MemoryUtil.memIntBuffer(image.getPointer(), w * h);
        int a = alpha << 24, outside = a | (fallback & 0xFFFFFF);
        for (int y = 0; y < h; y++) {
            double u = u0 + y * duY, v = v0 + y * dvY;
            for (int x = 0; x < w; x++, u += duX, v += dvX) {
                // Cells in view sit well inside the grid; negative values are outside anyway.
                int i = (int) u, j = (int) v;
                scaledRow[x] = u < 0 || v < 0 || i >= n || j >= n ? outside : a | (grid[j * n + i] & 0xFFFFFF);
            }
            pixels.position(y * w); pixels.put(scaledRow);
        }
    }

    /** Clip cached data with RenderUtil's exact coverage, rasterized only on geometry changes.
     * Opaque interior spans need no work; transparent runs clear in bulk. */
    public void clipShape(GuiGraphics g, boolean circle, float radiusPixels) {
        if (image == null) return;
        if (maskRuns == null || maskW != w || maskH != h || maskRadius != radiusPixels || maskCircle != circle) {
            byte[] coverage = new byte[w * h];
            RenderUtil.RectSink previous = RenderUtil.beginCapture((x1, y1, x2, y2, argb) -> {
                int left = Math.max(0, x1), right = Math.min(w, x2);
                if (right > left) for (int y = Math.max(0, y1); y < Math.min(h, y2); y++)
                    Arrays.fill(coverage, y * w + left, y * w + right, (byte) (argb >>> 24));
            });
            float scale = Minecraft.getInstance().getWindow().getGuiScale();
            try {
                if (circle) RenderUtil.drawCircleAA(g, 0, 0, w / scale, h / scale, -1);
                else RenderUtil.drawRoundedRectAA(g, 0, 0, w / scale, h / scale, radiusPixels / scale, -1);
            } finally { RenderUtil.endCapture(previous); }
            var runs = new it.unimi.dsi.fastutil.ints.IntArrayList(h * 12);
            for (int start = 0; start < coverage.length;) {
                int alpha = coverage[start] & 255, end = start + 1;
                while (end < coverage.length && coverage[end] == coverage[start]) end++;
                if (alpha != 255) { runs.add(start); runs.add(end - start); runs.add(alpha); }
                start = end;
            }
            maskRuns = runs.toIntArray();
            maskW = w; maskH = h; maskRadius = radiusPixels; maskCircle = circle;
        }
        long pointer = image.getPointer();
        IntBuffer pixels = MemoryUtil.memIntBuffer(pointer, w * h);
        for (int i = 0; i < maskRuns.length; i += 3) {
            int start = maskRuns[i], length = maskRuns[i + 1], coverage = maskRuns[i + 2];
            if (coverage == 0) MemoryUtil.memSet(pointer + (long) start * 4, 0, (long) length * 4);
            else for (int p = start; p < start + length; p++) {
                int color = pixels.get(p);
                pixels.put(p, (Math.round((color >>> 24) * coverage / 255f) << 24) | (color & 0xffffff));
            }
        }
    }

    /** Upload the captured pixels and stamp the version they represent. */
    public void clipRounded(float x, float y, float width, float height, float radius) {
        if (image == null) return;
        float r = Math.min(radius, Math.min(width,height)/2);
        for (int py=0;py<h;py++) for(int px=0;px<w;px++) {
            double qx=Math.abs(px+.5-x-width/2)-(width/2-r);
            double qy=Math.abs(py+.5-y-height/2)-(height/2-r);
            double distance=Math.hypot(Math.max(qx,0),Math.max(qy,0))+Math.min(Math.max(qx,qy),0)-r;
            int coverage=(int)Math.round(Math.max(0,Math.min(1,.5-distance))*255);
            int color=image.getPixel(px,py);
            image.setPixel(px,py,(Math.min(color>>>24,coverage)<<24)|(color&0xffffff));
        }
    }

    public void commit(long v) {
        if (image == null) return;
        ensureGpu();
        tex.upload();
        version = v;
    }

    private void ensureGpu() {
        if (tex != null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getTextureManager() == null) return;
        long id = SEQ.incrementAndGet();
        texId = Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "ui_layer/" + id);
        tex = new DynamicTexture(() -> "ui_layer_" + id, image);
        mc.getTextureManager().register(texId, tex);
    }

    /** One textured draw covering the logical screen at native resolution. */
    public void blit(GuiGraphics g, int logicalW, int logicalH) {
        blitAt(g, 0, 0, logicalW, logicalH);
    }

    /**
     * Positional variant of {@link #blit}: one textured draw of the captured
     * buffer's full content at {@code (x, y)}, sized {@code logicalW × logicalH}
     * (the buffer's device resolution divided by the GUI scale). Used by the
     * row/card <em>template</em> pattern — a small buffer rasterized once at
     * template-local coordinates, blitted per row at each row's position, so
     * scrolling never re-rasterizes.
     */
    public void blitAt(GuiGraphics g, int x, int y, int logicalW, int logicalH) {
        blitAt(g,x,y,logicalW,logicalH,-1);
    }

    /** Alpha-modulated reuse of a stable template (floating material fades). */
    public void blitAt(GuiGraphics g,int x,int y,int logicalW,int logicalH,int tint) {
        if (tex == null || w <= 0 || h <= 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, texId,
                x, y, 0f, 0f, logicalW, logicalH, w, h, w, h, tint);
    }

    /**
     * Rasterizer sink: source-over blend of an integer-pixel rect (physical
     * space) into the buffer. Matches the GPU's sequential source-over on an
     * opaque backdrop (premultiplied math) so the blit is visually identical
     * to drawing the same rects live.
     */
    @Override
    public void rect(int x1, int y1, int x2, int y2, int argb) {
        if (image == null) return;
        int x0 = Math.max(0, x1);
        int y0 = Math.max(0, y1);
        int x3 = Math.min(w, x2);
        int y3 = Math.min(h, y2);
        if (x3 <= x0 || y3 <= y0) return;

        int srcA = (argb >>> 24) & 0xFF;
        if (srcA == 0) return;
        int srcR = (argb >>> 16) & 0xFF;
        int srcG = (argb >>> 8) & 0xFF;
        int srcB = argb & 0xFF;
        int invA = 255 - srcA;

        int srcPr = srcR * srcA;
        int srcPg = srcG * srcA;
        int srcPb = srcB * srcA;

        for (int y = y0; y < y3; y++) {
            for (int x = x0; x < x3; x++) {
                int d = image.getPixel(x, y); // ARGB
                int dstA = (d >>> 24) & 0xFF;
                int dstR = (d >>> 16) & 0xFF;
                int dstG = (d >>> 8) & 0xFF;
                int dstB = d & 0xFF;

                int outA = srcA + Math.round(dstA * invA / 255f);
                if (outA == 0) {
                    image.setPixel(x, y, 0);
                    continue;
                }
                int r = Math.min(255, Math.round((srcPr + Math.round(dstR * dstA * invA / 255f)) / (float) outA));
                int g = Math.min(255, Math.round((srcPg + Math.round(dstG * dstA * invA / 255f)) / (float) outA));
                int b = Math.min(255, Math.round((srcPb + Math.round(dstB * dstA * invA / 255f)) / (float) outA));
                image.setPixel(x, y, (outA << 24) | (r << 16) | (g << 8) | b);
            }
        }
    }

    /** Release GPU + CPU resources. Idempotent. */
    public void dispose() {
        releaseTexture();
        if (image != null) {
            image.close();
            image = null;
        }
        w = -1;
        h = -1;
        version = Long.MIN_VALUE;
        scaledRow = null; maskRuns = null;
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
}
