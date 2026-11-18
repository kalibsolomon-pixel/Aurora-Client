package com.aurora.client.ui.util;

import com.aurora.client.AuroraClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * <p>The capture sink runs the physical-pixel row/coverage math and source-over
 * compositing used by the CPU live path; the final blit is 1:1 at native
 * resolution. Live shader shapes use different edge coverage.
 *
 * <p><b>Lifecycle:</b> screens queue {@link #retire()} on close so deferred
 * blits finish before disposal. Region bounds track position and raster size;
 * {@link #ensureSize} is the local-coordinate form used by row templates.
 */
public final class UiLayerCache implements RenderUtil.RectSink {

    private static final AtomicLong SEQ = new AtomicLong();
    private static final java.util.List<UiLayerCache> RETIRED = new java.util.ArrayList<>();

    /** Keep deferred GUI blits alive until the next extraction frame starts. */
    public void retire() { RETIRED.add(this); }

    public static void beginFrame() {
        for (var cache : RETIRED) cache.dispose();
        RETIRED.clear();
    }

    private NativeImage image;
    private DynamicTexture tex;
    private Identifier texId;
    private int w = -1;
    private int h = -1;
    private int captureX, captureY;
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

    /** Allocate a template whose draw coordinates are local to the image. */
    public void ensureSize(int w, int h) {
        ensureRegion(0, 0, w, h);
    }

    /**
     * Capture only this rectangle in physical pixels. Draw calls keep their
     * original screen coordinates; the sink translates and clips them into
     * the image. Call before isCurrent so moving or resizing invalidates the
     * old pixels. The final blit must use the corresponding logical bounds.
     */
    public void ensureRegion(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) throw new IllegalArgumentException("Positive capture dimensions required");
        if (captureX != x || captureY != y) version = Long.MIN_VALUE;
        captureX = x;
        captureY = y;
        if (this.w != w || this.h != h || image == null) {
            releaseTexture();
            if (image != null) image.close();
            image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
            this.w = w;
            this.h = h;
            version = Long.MIN_VALUE;
        }
    }

    /** Clear the buffer to transparent — call before a capture pass. */
    public void clear() {
        if (image != null) {
            image.fillRect(0, 0, w, h, 0);
        }
    }

    /**
     * Direct per-device-pixel writer: fills the WHOLE buffer with ARGB colors
     * from {@code argbAt(x, y)} (device-pixel coordinates, top-left origin,
     * one invocation per pixel). The color picker's gradient surfaces use this
     * instead of integer GUI-cell fills — every physical pixel gets its exact
     * sampled color, so the pad/strips render with true per-pixel gradients
     * (no visible banding at any GUI scale). Bulk row writes; no allocation.
     */
    public void writeArgb(java.util.function.IntBinaryOperator argbAt) {
        if (image == null) return;
        if (scaledRow == null || scaledRow.length != w) scaledRow = new int[w];
        IntBuffer pixels = MemoryUtil.memIntBuffer(image.getPointer(), w * h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                scaledRow[x] = argbToAbgr(argbAt.applyAsInt(x, y));
            }
            pixels.position(y * w);
            pixels.put(scaledRow);
        }
    }

    /** ARGB to the buffer's raw ABGR int layout. */
    private static int argbToAbgr(int c) {
        return ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >>> 16) & 0xFF) | (c & 0xFF000000);
    }

    /** Compatibility shading for existing themed cards/popups only; depth recipes use HSL lightVertical. */
    public void shadeVertical(int x, int y, int width, int height, int amount) {
        if (image == null || height < 2 || amount <= 0) return;
        IntBuffer pixels = MemoryUtil.memIntBuffer(image.getPointer(), w * h);
        int left = Math.max(0, x), right = Math.min(w, x + width);
        for (int py = Math.max(0, y); py < Math.min(h, y + height); py++) {
            int delta = Math.round(amount * (1f - 2f * (py - y) / (height - 1)));
            for (int px = left; px < right; px++) {
                int index = py * w + px, color = pixels.get(index);
                if ((color >>> 24) == 0) continue;
                int red = Math.clamp((color & 255) + delta, 0, 255);
                int green = Math.clamp(((color >>> 8) & 255) + delta, 0, 255);
                int blue = Math.clamp(((color >>> 16) & 255) + delta, 0, 255);
                pixels.put(index, (color & 0xff000000) | (blue << 16) | (green << 8) | red);
            }
        }
    }

    /** Light only captured face pixels; coordinates and deltas are device pixels / HSL points. */
    public void lightVertical(int x, int y, int width, int height, float top, float bottom) {
        if (image == null || height <= 0 || top == 0 && bottom == 0) return;
        IntBuffer pixels = MemoryUtil.memIntBuffer(image.getPointer(), w * h);
        var bake = new com.aurora.client.theme.MaterialLighting.FaceBake(width,height,0,1,top,bottom,
                com.aurora.client.theme.MaterialLighting.Profile.FLAT);
        for (int py = Math.max(0, y); py < Math.min(h, y + height); py++) {
            for (int px = Math.max(0, x); px < Math.min(w, x + width); px++) {
                int index=py*w+px, argb=argbToAbgr(pixels.get(index));
                pixels.put(index,argbToAbgr(bake.shade(argb,px-x,py-y)));
            }
        }
    }

    /** Per-pixel SDF height-field lighting, baked before selection ink/shadows. Coverage is immutable.
     * Geometry is the face's own device-space rectangle, including fractional knob sizes. */
    public void lightFace(int x, int y, float width, float height, float radius, int scale,
                          float top, float bottom, com.aurora.client.theme.MaterialLighting.Profile profile) {
        if (image == null || width <= 0 || height <= 0) return;
        IntBuffer pixels = MemoryUtil.memIntBuffer(image.getPointer(), w * h);
        var bake = new com.aurora.client.theme.MaterialLighting.FaceBake(width,height,radius,scale,top,bottom,profile);
        for (int py = Math.max(0, y); py < Math.min(h, y + (int)Math.ceil(height)); py++)
            for (int px = Math.max(0, x); px < Math.min(w, x + (int)Math.ceil(width)); px++) {
                int index=py*w+px, argb=argbToAbgr(pixels.get(index));
                pixels.put(index,argbToAbgr(bake.shade(argb,px-x,py-y)));
            }
    }

    /** Baked black shadow behind the lit face. Radius/offset/softness are device pixels.
     * Rounded SDF coverage keeps SQUARE square; no GUI fills or temporary image allocation. */
    public void softShadow(float x, float y, float width, float height, float radius,
                           float elevation, float softness, float alpha) {
        if (image == null) return;
        double offsetX = elevation * com.aurora.client.theme.MaterialLighting.SHADOW_X;
        double offsetY = elevation * com.aurora.client.theme.MaterialLighting.SHADOW_Y;
        double soft=Math.max(1,softness), r=Math.clamp(radius,0,Math.min(width,height)*.5);
        int left=Math.max(0,(int)Math.floor(x+offsetX-soft*.5));
        int right=Math.min(w,(int)Math.ceil(x+offsetX+width+soft*.5));
        int top=Math.max(0,(int)Math.floor(y+offsetY-soft*.5));
        int bottom=Math.min(h,(int)Math.ceil(y+offsetY+height+soft*.5));
        // X terms are separable; compute once, preserving the old expression order.
        double[] qx=new double[Math.max(0,right-left)];
        for(int px=left;px<right;px++) qx[px-left]=Math.abs(px+.5-x-offsetX-width*.5)-(width*.5-r);
        IntBuffer pixels = MemoryUtil.memIntBuffer(image.getPointer(), w * h);
        for (int py = 0; py < h; py++) {
            double qy=Math.abs(py+.5-y-offsetY-height*.5)-(height*.5-r);
            for (int px = 0; px < w; px++) {
                int index=py*w+px, raw=pixels.get(index);
                if((raw>>>24)==255) continue; // Black behind an opaque pixel cannot affect it.
                if(px<left || px>=right || py<top || py>=bottom) {
                    // The legacy whole-image pass also canonicalized hidden RGB to transparent black.
                    if((raw>>>24)==0 && raw!=0) pixels.put(index,0);
                    continue;
                }
                double q=qx[px-left];
                double distance=Math.hypot(Math.max(q,0),Math.max(qy,0))+Math.min(Math.max(q,qy),0)-r;
                float coverage = (float) Math.max(0, Math.min(1, .5 - distance / soft));
                pixels.put(index,argbToAbgr(com.aurora.client.theme.MaterialLighting.shadowBehind(argbToAbgr(raw), alpha * coverage)));
            }
        }
    }

    public void windowFace(com.aurora.client.theme.WindowBevel.Raster raster, int fill, int pad, int logicalW, int logicalH) {
        IntBuffer pixels = MemoryUtil.memIntBuffer(image.getPointer(), w * h);
        double stepX = (double) logicalW / w, stepY = (double) logicalH / h;
        if (scaledRow == null || scaledRow.length < w) scaledRow = new int[w];
        java.util.Arrays.fill(scaledRow, 0, w, argbToAbgr(fill));
        for (int row = 0; row < h; row++) {
            double sampleY = (row + .5) * stepY - pad;
            int leftEnd = Math.min((w + 1) / 2, (int) Math.ceil((raster.plateauInset(sampleY) + pad) / stepX));
            int rightStart = Math.max(leftEnd, w - leftEnd);
            int start = row * w;
            pixels.put(start + leftEnd, scaledRow, leftEnd, rightStart - leftEnd);
            for (int column = 0; column < leftEnd; column++)
                pixels.put(start + column, argbToAbgr(raster.pixel((column + .5) * stepX - pad, sampleY, fill)));
            for (int column = rightStart; column < w; column++)
                pixels.put(start + column, argbToAbgr(raster.pixel((column + .5) * stepX - pad, sampleY, fill)));
        }
    }

    /**
     * The cache discipline for one editing surface: if the buffer is not
     * current for {@code version}, size it to {@code wLogical × hLogical}
     * (scaled to device pixels), clear it, run {@code raster} (draw at LOCAL
     * logical coordinates — the capture sink maps fills to the buffer's
     * physical pixels, and {@link #writeArgb} writes device pixels directly;
     * both compose in draw order) and commit; then one positional blit at
     * {@code (x, y)}. Shared by the color-picker surfaces.
     */
    public static void drawSurface(GuiGraphicsExtractor g, UiLayerCache cache, long version,
                                   int x, int y, int wLogical, int hLogical, Runnable raster) {
        drawSurface(g, cache, version, x, y, wLogical, hLogical, -1, raster);
    }

    public static void drawSurface(GuiGraphicsExtractor g, UiLayerCache cache, long version,
                                   int x, int y, int wLogical, int hLogical, int tint, Runnable raster) {
        if (!cache.isCurrent(version)) {
            int scale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
            cache.ensureSize(wLogical * scale, hLogical * scale);
            cache.clear();
            RenderUtil.RectSink prev = RenderUtil.beginCapture(cache.sink());
            try {
                raster.run();
            } finally {
                RenderUtil.endCapture(prev);
            }
            cache.commit(version);
        }
        cache.blitAt(g, x, y, wLogical, hLogical, tint);
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
    public void clipShape(GuiGraphicsExtractor g, boolean circle, float radiusPixels) {
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
    public void blit(GuiGraphicsExtractor g, int logicalW, int logicalH) {
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
    public void blitAt(GuiGraphicsExtractor g, int x, int y, int logicalW, int logicalH) {
        blitAt(g,x,y,logicalW,logicalH,-1);
    }

    /** Alpha-modulated reuse of a stable template (floating material fades). */
    public void blitAt(GuiGraphicsExtractor g,int x,int y,int logicalW,int logicalH,int tint) {
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
        int x0 = Math.max(0, x1 - captureX);
        int y0 = Math.max(0, y1 - captureY);
        int x3 = Math.min(w, x2 - captureX);
        int y3 = Math.min(h, y2 - captureY);
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
        captureX = captureY = 0;
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
