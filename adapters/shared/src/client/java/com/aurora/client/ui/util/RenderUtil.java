package com.aurora.client.ui.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

public class RenderUtil {

    /**
     * A destination for rasterized integer-pixel rectangles. The mod's
     * high-res AA engine rasterizes per <b>physical device pixel</b>; this
     * interface lets the same row/coverage math write either into a live
     * {@link GuiGraphicsExtractor} or an off-screen {@code UiLayerCache}.
     * The shader uses different edge coverage for live shapes; capture
     * continues to use this rasterizer.
     */
    public interface RectSink extends com.aurora.client.port.DrawSurface {}

    /**
     * Live-GuiGraphicsExtractor sink. Folds into the pose's {@code 1/guiScale} frame
     * the AA draw methods set up, so physical ints land on exact device
     * pixels exactly as before this was factored out.
     */
    private static final class GuiRectSink implements RectSink {
        private final GuiGraphicsExtractor graphics;
        GuiRectSink(GuiGraphicsExtractor graphics) { this.graphics = graphics; }
        @Override public void rect(int x1, int y1, int x2, int y2, int argb) {
            FILLS_SUBMITTED.incrementAndGet();
            graphics.fill(x1, y1, x2, y2, argb);
        }
    }

    private static RectSink guiSink(GuiGraphicsExtractor graphics) {
        RectSink capture = CAPTURE_SINK.get();
        return capture != null ? capture : new GuiRectSink(graphics);
    }

    /**
     * Redirects all {@code RenderUtil} draw calls to {@code sink} for the
     * duration of the current render-thread scope, instead of issuing live
     * GUI fill submissions. Used by the static-layer cache to rasterize a
     * screen's unchanged chrome into an off-screen buffer once, then blit.
     *
     * <p>Thread-local and strictly scoped (try/finally) â€” the GUI thread
     * only, single-threaded; no persistent shared state. Restore with the
     * value returned here.
     */
    private static final ThreadLocal<RectSink> CAPTURE_SINK = new ThreadLocal<>();

    /** Begin redirecting {@code RenderUtil} draws to {@code sink}; returns the prior sink to restore. */
    public static RectSink beginCapture(RectSink sink) {
        RectSink prev = CAPTURE_SINK.get();
        CAPTURE_SINK.set(sink);
        return prev;
    }

    /** Restore the sink captured by {@link #beginCapture}. */
    public static void endCapture(RectSink previous) {
        if (previous != null) CAPTURE_SINK.set(previous);
        else CAPTURE_SINK.remove();
    }

    /**
     * Sink that discards every submission. Paired with a cached template
     * blit: the screen runs its ordinary paint path inside
     * {@code beginCapture(DISCARD_SINK)} so the static shape fills are
     * suppressed from the live batch while the template (rasterized from
     * the identical calls) supplies their pixels. Unlike the live sink this
     * does not count toward {@link #fillsSubmitted()} — those submissions
     * are the ones being eliminated.
     */
    public static final RectSink DISCARD_SINK = new RectSink() {
        @Override public void rect(int x1, int y1, int x2, int y2, int argb) {}
    };

    /**
     * Capture-aware plain logical-pixel fill for hard-edged integer grids
     * (checkerboards, gradient cells): during a capture pass the rect goes
     * to the active sink in physical pixels ({@code ·guiScale}; integer
     * input, so device alignment is exact), live it is an ordinary
     * {@link GuiGraphicsExtractor#fill}. Output is identical through both
     * destinations — a GUI fill at integer logical coordinates and a
     * sink-rect at the scaled integers cover the same device pixels, and a
     * 1:1 blit of the captured buffer lands them back on those pixels.
     */
    public static void fillLogical(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int argb) {
        RectSink capture = CAPTURE_SINK.get();
        if (capture == null) {
            g.fill(x1, y1, x2, y2, argb);
        } else if (capture != DISCARD_SINK) {
            float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
            capture.rect(Math.round(x1 * scale), Math.round(y1 * scale),
                    Math.round(x2 * scale), Math.round(y2 * scale), argb);
        }
        // DISCARD_SINK: swallowed — same suppression semantics as the AA paths.
    }

    /**
     * Instrumentation counter â€” GUI fill submissions issued through this
     * class per process lifetime. Lets a live session quantify the frame
     * cost of pixel-heavy screens (and verify the static-layer cache is
     * eliminating them). Cheap to maintain; read via {@link #fillsSubmitted()}.
     */
    private static final java.util.concurrent.atomic.AtomicLong FILLS_SUBMITTED =
            new java.util.concurrent.atomic.AtomicLong();

    /** Number of GUI fill submissions issued so far (monotonic). */
    public static long fillsSubmitted() { return FILLS_SUBMITTED.get(); }

    public static void drawTriangleAA(GuiGraphicsExtractor g, float ax, float ay, float bx, float by, float cx, float cy, int color) {
        float scale = Minecraft.getInstance().getWindow().getGuiScale();
        g.pose().pushMatrix();
        g.pose().scale(1 / scale, 1 / scale);
        try {
            ShapeRasterizer.drawTriangleAA(guiSink(g), scale, ax, ay, bx, by, cx, cy, color);
        } finally {
            g.pose().popMatrix();
        }
    }

    public static void drawRoundedRectAA(GuiGraphicsExtractor graphics, float x, float y, float width, float height, float radius, int color) {
        float scale = Minecraft.getInstance().getWindow().getGuiScale();
        if (CAPTURE_SINK.get() == null && NativeShapeRenderer.draw(graphics, scale, x, y, width, height, radius, 0, color, false)) return;
        graphics.pose().pushMatrix();
        graphics.pose().scale(1 / scale, 1 / scale);
        try {
            ShapeRasterizer.drawRoundedRectAA(guiSink(graphics), scale, x, y, width, height, radius, color);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /**
     * A rounded rect filled with a linear two-stop gradient ({@code from} →
     * {@code to}, left to right when {@code horizontal}, else top to
     * bottom), drawn as one shader quad. Where the shader cannot run (a
     * template capture, the CPU fallback) it fills with the midpoint color,
     * so the shape and its overall tone are always right. A square corner
     * is passed as a sub-pixel radius, which the shader accepts and nobody
     * can see.
     */
    public static void drawRoundedRectGradientAA(GuiGraphicsExtractor graphics, float x, float y, float width,
                                                 float height, float radius, int from, int to, boolean horizontal) {
        float scale = Minecraft.getInstance().getWindow().getGuiScale();
        if (from == to) { drawRoundedRectAA(graphics, x, y, width, height, radius, from); return; }
        float shaderRadius = Math.max(radius, 0.125f / Math.max(1f, scale));
        if (CAPTURE_SINK.get() == null && NativeShapeRenderer.drawGradient(graphics, scale, x, y, width, height,
                shaderRadius, from, to, horizontal)) return;
        drawRoundedRectAA(graphics, x, y, width, height, radius, com.aurora.client.util.AuroraAnim.lerpArgb(from, to, 0.5f));
    }

    public static void drawCircleAA(GuiGraphicsExtractor graphics, float x, float y, float width, float height, int color) {
        float scale = Minecraft.getInstance().getWindow().getGuiScale();
        float diameter = Math.min(width, height);
        if (CAPTURE_SINK.get() == null && NativeShapeRenderer.draw(graphics, scale,
                x + (width - diameter) / 2, y + (height - diameter) / 2,
                diameter, diameter, diameter / 2, 0, color, false)) return;
        graphics.pose().pushMatrix();
        graphics.pose().scale(1 / scale, 1 / scale);
        try {
            ShapeRasterizer.drawCircleAA(guiSink(graphics), scale, x, y, width, height, color);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public static void drawRoundedOutlineAA(GuiGraphicsExtractor graphics, float x, float y, float width, float height, float radius, float thickness, int color) {
        float scale = Minecraft.getInstance().getWindow().getGuiScale();
        if (thickness > 0 && CAPTURE_SINK.get() == null && NativeShapeRenderer.draw(graphics, scale, x, y, width, height, radius, thickness, color, false)) return;
        graphics.pose().pushMatrix();
        graphics.pose().scale(1 / scale, 1 / scale);
        try {
            ShapeRasterizer.drawRoundedOutlineAA(guiSink(graphics), scale, x, y, width, height, radius, thickness, color);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public static void drawSquircle(GuiGraphicsExtractor graphics, float x, float y, float width, float height, float radius, int color) {
        float scale = Minecraft.getInstance().getWindow().getGuiScale();
        if (CAPTURE_SINK.get() == null && NativeShapeRenderer.draw(graphics, scale, x, y, width, height, radius, 0, color, true)) return;
        graphics.pose().pushMatrix();
        graphics.pose().scale(1 / scale, 1 / scale);
        try {
            ShapeRasterizer.drawSquircle(guiSink(graphics), scale, x, y, width, height, radius, color);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    public static void drawSquircleOutline(GuiGraphicsExtractor graphics, float x, float y, float width, float height, float radius, float thickness, int color) {
        float scale = Minecraft.getInstance().getWindow().getGuiScale();
        if (thickness > 0 && CAPTURE_SINK.get() == null && NativeShapeRenderer.draw(graphics, scale, x, y, width, height, radius, thickness, color, true)) return;
        graphics.pose().pushMatrix();
        graphics.pose().scale(1 / scale, 1 / scale);
        try {
            ShapeRasterizer.drawSquircleOutline(guiSink(graphics), scale, x, y, width, height, radius, thickness, color);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /**
     * Helper to render word-wrapped string text within a specified bounding width.
     */
    public static void drawWordWrap(Font font, GuiGraphicsExtractor graphics, String text, float x, float y, float maxWidth, int color) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), (int) maxWidth);
        float currentY = y;
        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, (int) x, (int) currentY, color, false);
            currentY += font.lineHeight + 2;
        }
    }

    /**
     * Helper to render word-wrapped string text with a capped maximum line count.
     */
    public static void drawWordWrapMaxLines(Font font, GuiGraphicsExtractor graphics, String text, float x, float y, float maxWidth, int maxLines, int color) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), (int) maxWidth);
        float currentY = y;
        int count = 0;
        for (FormattedCharSequence line : lines) {
            if (count >= maxLines) break;
            graphics.text(font, line, (int) x, (int) currentY, color, false);
            currentY += font.lineHeight + 2;
            count++;
        }
    }

    /** Centered measured text, with breaks only between whole words. */
    public static void drawCenteredLines(Font font, GuiGraphicsExtractor graphics, java.util.List<String> lines,
                                          float x, float y, float width, int color) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            graphics.text(font, line, Math.round(x + (width - font.width(line)) / 2),
                    Math.round(y + i * (font.lineHeight + 2)), color, false);
        }
    }
}
