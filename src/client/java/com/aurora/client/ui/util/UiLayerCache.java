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

    /** Upload the captured pixels and stamp the version they represent. */
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
        if (tex == null || w <= 0 || h <= 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, texId,
                x, y, 0f, 0f, logicalW, logicalH, w, h, w, h, -1);
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