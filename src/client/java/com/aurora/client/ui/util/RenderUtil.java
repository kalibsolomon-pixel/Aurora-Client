package com.aurora.client.ui.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

public class RenderUtil {

    /**
     * One physical device pixel expressed in logical GUI units. Use this for
     * understated semantic edges that must not grow with the GUI scale.
     */
    public static float devicePixelStroke() {
        return 1.0f / Math.max(1.0f,
                (float) Minecraft.getInstance().getWindow().getGuiScale());
    }

    /**
     * A destination for rasterized integer-pixel rectangles. The mod's
     * high-res AA engine rasterizes per <b>physical device pixel</b>; this
     * interface lets the exact same row/coverage math write either into a
     * live {@link GuiGraphics} (screen path) or into an off-screen cache
     * buffer ({@code UiLayerCache}) â€” one rasterizer, two destinations, no
     * quality difference.
     */
    public interface RectSink {
        /** Emits an opaque-or-translucent rectangle in <b>physical pixel</b> space. */
        void rect(int x1, int y1, int x2, int y2, int argb);
    }

    /**
     * Live-GuiGraphics sink. Folds into the pose's {@code 1/guiScale} frame
     * the AA draw methods set up, so physical ints land on exact device
     * pixels exactly as before this was factored out.
     */
    private static final class GuiRectSink implements RectSink {
        private final GuiGraphics graphics;
        GuiRectSink(GuiGraphics graphics) { this.graphics = graphics; }
        @Override public void rect(int x1, int y1, int x2, int y2, int argb) {
            FILLS_SUBMITTED.incrementAndGet();
            graphics.fill(x1, y1, x2, y2, argb);
        }
    }

    private static RectSink guiSink(GuiGraphics graphics) {
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
     * {@link GuiGraphics#fill}. Output is identical through both
     * destinations — a GUI fill at integer logical coordinates and a
     * sink-rect at the scaled integers cover the same device pixels, and a
     * 1:1 blit of the captured buffer lands them back on those pixels.
     */
    public static void fillLogical(GuiGraphics g, int x1, int y1, int x2, int y2, int argb) {
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

    private static void fillRect(RectSink sink, float x1, float y1, float x2, float y2, int color) {
        sink.rect(Math.round(x1), Math.round(y1), Math.round(x2), Math.round(y2), color);
    }

    /**
     * Scales the alpha channel of {@code color} by {@code scale} (clamped
     * 0..1) and returns the new ARGB int. Used to fold sub-pixel coverage
     * into the boundary pixels of antialiased shapes.
     */
    private static int scaleAlphaChannel(int color, float scale) {
        float s = Math.max(0f, Math.min(1f, scale));
        int a = (color >>> 24) & 0xFF;
        int newA = Math.round(a * s);
        return (newA << 24) | (color & 0x00FFFFFF);
    }

    /**
     * Emits a single 1-physical-pixel-tall horizontal span from
     * {@code leftEdge} to {@code rightEdge} (float coords, scaled space)
     * with sub-pixel antialiasing on the two boundary pixels. The
     * fractional coverage of each edge pixel is folded into its alpha so
     * the silhouette edge reads smooth instead of a jagged integer
     * staircase.
     *
     * <p>Used by both the antialiased fill and the antialiased outline:
     * an outline band is just a narrow span {@code [outerEdge, innerEdge]},
     * so the same AA logic applies to its outer and inner boundaries.
     */
    private static void fillAARow(RectSink sink, float leftEdge, float rowY, float rightEdge, int color) {
        int y0 = Math.round(rowY);
        int y1 = y0 + 1;

        int leftInt = (int) Math.floor(leftEdge);
        float leftFrac = leftEdge - leftInt;
        int rightInt = (int) Math.floor(rightEdge);
        float rightFrac = rightEdge - rightInt;

        if (leftInt == rightInt) {
            // Whole span falls within a single pixel.
            float cov = Math.max(0f, Math.min(1f, rightEdge - leftEdge));
            int a = Math.round(cov * ((color >>> 24) & 0xFF));
            if (a > 0) sink.rect(leftInt, y0, leftInt + 1, y1, (a << 24) | (color & 0x00FFFFFF));
            return;
        }

        // Left boundary pixel â€” covered from leftEdge to leftInt+1.
        if (leftFrac > 0.001f) {
            sink.rect(leftInt, y0, leftInt + 1, y1, scaleAlphaChannel(color, 1f - leftFrac));
        }
        // Fully-covered interior pixels.
        int fullStart = leftInt + 1;
        int fullEnd = rightInt;
        if (fullEnd > fullStart) {
            sink.rect(fullStart, y0, fullEnd, y1, color);
        }
        // Right boundary pixel â€” covered from rightInt to rightEdge.
        if (rightFrac > 0.001f) {
            sink.rect(rightInt, y0, rightInt + 1, y1, scaleAlphaChannel(color, rightFrac));
        }
    }

    /**
     * Antialiased filled rounded rectangle — the canonical rounded-fill
     * rasterizer. Exact sub-pixel coverage of each edge pixel is folded into
     * its alpha channel via {@link #fillAARow}, producing a smooth curve
     * instead of a jagged integer staircase.
     */
    public static void drawRoundedRectAA(GuiGraphics graphics, float x, float y, float width, float height, float radius, int color) {
        if (width <= 0 || height <= 0) return;

                float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        RectSink sink = guiSink(graphics);

        graphics.pose().pushMatrix();
        graphics.pose().scale(1.0f / scale, 1.0f / scale);

        float px = x * scale;
        float py = y * scale;
        float pWidth = width * scale;
        float pHeight = height * scale;
        float pRadius = Math.min(radius * scale, Math.min(pWidth, pHeight) / 2.0f);

        if (pRadius <= 0) {
            fillRect(sink, px, py, px + pWidth, py + pHeight, color);
            graphics.pose().popMatrix();
            return;
        }

        float yCenterTop = py + pRadius;
        float yCenterBottom = py + pHeight - pRadius;
        int rows = (int) Math.ceil(pHeight);

        // Top corners â€” AA rows.
        int topRows = (int) Math.ceil(pRadius);
        for (int i = 0; i < topRows; i++) {
            float rowY = py + i;
            float dy = yCenterTop - (rowY + 0.5f);
            float dx = (dy > 0 && dy < pRadius) ? (float) Math.sqrt(pRadius * pRadius - dy * dy) : pRadius;
            float leftX = px + pRadius - dx;
            float rightX = px + pWidth - pRadius + dx;
            fillAARow(sink, leftX, rowY, rightX, color);
        }

        // Body.
        if (yCenterBottom > yCenterTop) {
            fillRect(sink, px, yCenterTop, px + pWidth, yCenterBottom, color);
        }

        // Bottom corners â€” AA rows.
        int bottomStart = Math.max((int) Math.ceil(pRadius), (int) Math.floor(pHeight - pRadius));
        for (int i = bottomStart; i < rows; i++) {
            float rowY = py + i;
            float dy = (rowY + 0.5f) - yCenterBottom;
            float dx = (dy > 0 && dy < pRadius) ? (float) Math.sqrt(pRadius * pRadius - dy * dy) : pRadius;
            float leftX = px + pRadius - dx;
            float rightX = px + pWidth - pRadius + dx;
            fillAARow(sink, leftX, rowY, rightX, color);
        }

        graphics.pose().popMatrix();
    }

    /**
     * Antialiased filled circle â€” a single continuous row loop around one
     * center with exact circle math ({@code dx = sqrt(r^2 - dy^2)}) and
     * {@link #fillAARow} sub-pixel coverage.
     *
     * <p>Unlike {@link #drawRoundedRectAA} (top/body/bottom partitions) and
     * {@link #drawSquircle} (superellipse exponent 5 â€” deliberately boxier
     * than a circle), this has no partitions to seam and no squircle
     * geometry, so it renders a perfect, seamless circle at any size. Use it
     * for toggle knobs and other shapes that must stay fully round
     * regardless of the theme's corner-roundness setting.
     */
    public static void drawCircleAA(GuiGraphics graphics, float x, float y, float width, float height, int color) {
        if (width <= 0 || height <= 0) return;

        float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        RectSink sink = guiSink(graphics);

        graphics.pose().pushMatrix();
        graphics.pose().scale(1.0f / scale, 1.0f / scale);

        float pcx = (x + width / 2f) * scale;
        float pcy = (y + height / 2f) * scale;
        float pr = Math.min(width, height) * scale / 2f;

        float startY = (float) Math.floor(pcy - pr);
        float endY = (float) Math.ceil(pcy + pr);
        int rows = (int) (endY - startY);

        for (int i = 0; i < rows; i++) {
            float rowY = startY + i;
            float dy = (rowY + 0.5f) - pcy;
            if (Math.abs(dy) >= pr) continue; // outside the circle â€” zero coverage
            float dx = (float) Math.sqrt(pr * pr - dy * dy);
            fillAARow(sink, pcx - dx, rowY, pcx + dx, color);
        }

        graphics.pose().popMatrix();
    }

    /**
     * Antialiased rounded-rect outline. Same analytical geometry as
     * {@link #drawRoundedOutline} but emits {@link #fillAARow} for the
     * corner bands so both the outer and inner arc edges fade smoothly
     * into the surface â€” no faceted staircase on small radii. Recommended
     * for the search bar focus ring and any thin-stroked control.
     */
    public static void drawRoundedOutlineAA(GuiGraphics graphics, float x, float y, float width, float height, float radius, float thickness, int color) {
        if (width <= 0 || height <= 0) return;

                float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        RectSink sink = guiSink(graphics);

        graphics.pose().pushMatrix();
        graphics.pose().scale(1.0f / scale, 1.0f / scale);

        float px = x * scale;
        float py = y * scale;
        float pWidth = width * scale;
        float pHeight = height * scale;
        float pRadius = Math.min(radius * scale, Math.min(pWidth, pHeight) / 2.0f);
        float t = Math.max(1.0f, thickness * scale);

        if (pRadius <= 0) {
            fillRect(sink, px, py, px + pWidth, py + t, color); // Top
            fillRect(sink, px, py + pHeight - t, px + pWidth, py + pHeight, color); // Bottom
            fillRect(sink, px, py + t, px + t, py + pHeight - t, color); // Left
            fillRect(sink, px + pWidth - t, py + t, px + pWidth, py + pHeight - t, color); // Right
            graphics.pose().popMatrix();
            return;
        }

        float yCenterTop = py + pRadius;
        float yCenterBottom = py + pHeight - pRadius;
        int rows = (int) Math.ceil(pHeight);
        float innerR = pRadius - t;

        // Top corners â€” AA bands.
        int topRows = (int) Math.ceil(pRadius);
        for (int i = 0; i < topRows; i++) {
            float rowY = py + i;
            float dy = yCenterTop - (rowY + 0.5f);
            float dxOuter = (dy > 0 && dy < pRadius) ? (float) Math.sqrt(pRadius * pRadius - dy * dy) : pRadius;
            float dxInner = (dy >= 0 && dy < innerR) ? (float) Math.sqrt(innerR * innerR - dy * dy) : 0;

            float leftXOuter = px + pRadius - dxOuter;
            float leftXInner = px + pRadius - dxInner;
            float rightXInner = px + pWidth - pRadius + dxInner;
            float rightXOuter = px + pWidth - pRadius + dxOuter;

            if (rowY < py + t) {
                fillAARow(sink, leftXOuter, rowY, rightXOuter, color);
            } else {
                fillAARow(sink, leftXOuter, rowY, leftXInner, color);
                fillAARow(sink, rightXInner, rowY, rightXOuter, color);
            }
        }

        // Body side bars.
        if (yCenterBottom > yCenterTop) {
            fillRect(sink, px, yCenterTop, px + t, yCenterBottom, color);
            fillRect(sink, px + pWidth - t, yCenterTop, px + pWidth, yCenterBottom, color);
        }

        // Bottom corners â€” AA bands.
        int bottomStart = Math.max((int) Math.ceil(pRadius), (int) Math.floor(pHeight - pRadius));
        for (int i = bottomStart; i < rows; i++) {
            float rowY = py + i;
            float dy = (rowY + 0.5f) - yCenterBottom;
            float dxOuter = (dy > 0 && dy < pRadius) ? (float) Math.sqrt(pRadius * pRadius - dy * dy) : pRadius;
            float dxInner = (dy >= 0 && dy < innerR) ? (float) Math.sqrt(innerR * innerR - dy * dy) : 0;

            float leftXOuter = px + pRadius - dxOuter;
            float leftXInner = px + pRadius - dxInner;
            float rightXInner = px + pWidth - pRadius + dxInner;
            float rightXOuter = px + pWidth - pRadius + dxOuter;

            if (rowY >= py + pHeight - t) {
                fillAARow(sink, leftXOuter, rowY, rightXOuter, color);
            } else {
                fillAARow(sink, leftXOuter, rowY, leftXInner, color);
                fillAARow(sink, rightXInner, rowY, rightXOuter, color);
            }
        }

        graphics.pose().popMatrix();
    }

    /**
     * Draws a filled squircle (superellipse with exponent 5.0) with physical-pixel resolution matching.
     * Uses a single-pass row-by-row horizontal span render to prevent overlap artifacts and double-blending lines.
     */
    public static void drawSquircle(GuiGraphics graphics, float x, float y, float width, float height, float radius, int color) {
        if (width <= 0 || height <= 0) return;

                float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        RectSink sink = guiSink(graphics);

        graphics.pose().pushMatrix();
        graphics.pose().scale(1.0f / scale, 1.0f / scale);

        float px = x * scale;
        float py = y * scale;
        float pWidth = width * scale;
        float pHeight = height * scale;
        float pRadius = Math.min(radius * scale, Math.min(pWidth, pHeight) / 2.0f);

        if (pRadius <= 0) {
            fillRect(sink, px, py, px + pWidth, py + pHeight, color);
            graphics.pose().popMatrix();
            return;
        }

        float yCenterTop = py + pRadius;
        float yCenterBottom = py + pHeight - pRadius;
        int rows = (int) Math.ceil(pHeight);

        // Draw top corners row-by-row
        int topRows = (int) Math.ceil(pRadius);
        for (int i = 0; i < topRows; i++) {
            float rowY = py + i;
            float dy = yCenterTop - (rowY + 0.5f);
            float dx = (dy > 0 && dy < pRadius) ? pRadius * (float) Math.pow(1.0 - Math.pow(dy / pRadius, 5.0), 0.2) : pRadius;
            float leftX = px + pRadius - dx;
            float rightX = px + pWidth - pRadius + dx;
            fillRect(sink, leftX, rowY, rightX, rowY + 1, color);
        }

        // Draw the flat center body as a single giant GPU-filled rectangle
        if (yCenterBottom > yCenterTop) {
            fillRect(sink, px, yCenterTop, px + pWidth, yCenterBottom, color);
        }

        // Draw bottom corners row-by-row
        int bottomStart = Math.max((int) Math.ceil(pRadius), (int) Math.floor(pHeight - pRadius));
        for (int i = bottomStart; i < rows; i++) {
            float rowY = py + i;
            float dy = (rowY + 0.5f) - yCenterBottom;
            float dx = (dy > 0 && dy < pRadius) ? pRadius * (float) Math.pow(1.0 - Math.pow(dy / pRadius, 5.0), 0.2) : pRadius;
            float leftX = px + pRadius - dx;
            float rightX = px + pWidth - pRadius + dx;
            fillRect(sink, leftX, rowY, rightX, rowY + 1, color);
        }

        graphics.pose().popMatrix();
    }

    /**
     * Draws an outlined squircle (superellipse with exponent 5.0) with physical-pixel resolution matching.
     */
    public static void drawSquircleOutline(GuiGraphics graphics, float x, float y, float width, float height, float radius, float thickness, int color) {
        if (width <= 0 || height <= 0) return;

                float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        RectSink sink = guiSink(graphics);

        graphics.pose().pushMatrix();
        graphics.pose().scale(1.0f / scale, 1.0f / scale);

        float px = x * scale;
        float py = y * scale;
        float pWidth = width * scale;
        float pHeight = height * scale;
        float pRadius = Math.min(radius * scale, Math.min(pWidth, pHeight) / 2.0f);
        float t = Math.max(1.0f, thickness * scale);

        if (pRadius <= 0) {
            fillRect(sink, px, py, px + pWidth, py + t, color); // Top
            fillRect(sink, px, py + pHeight - t, px + pWidth, py + pHeight, color); // Bottom
            fillRect(sink, px, py + t, px + t, py + pHeight - t, color); // Left
            fillRect(sink, px + pWidth - t, py + t, px + pWidth, py + pHeight - t, color); // Right
            graphics.pose().popMatrix();
            return;
        }

        float yCenterTop = py + pRadius;
        float yCenterBottom = py + pHeight - pRadius;
        int rows = (int) Math.ceil(pHeight);

        // Draw top corners row-by-row
        int topRows = (int) Math.ceil(pRadius);
        for (int i = 0; i < topRows; i++) {
            float rowY = py + i;
            float dy = yCenterTop - (rowY + 0.5f);
            float dxOuter = (dy > 0 && dy < pRadius) ? pRadius * (float) Math.pow(1.0 - Math.pow(dy / pRadius, 5.0), 0.2) : pRadius;
            float innerR = pRadius - t;
            float dxInner = (dy >= 0 && dy < innerR) ? innerR * (float) Math.pow(1.0 - Math.pow(dy / innerR, 5.0), 0.2) : 0;

            float leftXOuter = px + pRadius - dxOuter;
            float leftXInner = px + pRadius - dxInner;
            float rightXInner = px + pWidth - pRadius + dxInner;
            float rightXOuter = px + pWidth - pRadius + dxOuter;

            if (rowY < py + t) {
                fillRect(sink, leftXOuter, rowY, rightXOuter, rowY + 1, color);
            } else {
                fillRect(sink, leftXOuter, rowY, leftXInner, rowY + 1, color);
                fillRect(sink, rightXInner, rowY, rightXOuter, rowY + 1, color);
            }
        }

        // Draw the flat center body side bars as single tall rectangles
        if (yCenterBottom > yCenterTop) {
            fillRect(sink, px, yCenterTop, px + t, yCenterBottom, color); // Left bar
            fillRect(sink, px + pWidth - t, yCenterTop, px + pWidth, yCenterBottom, color); // Right bar
        }

        // Draw bottom corners row-by-row
        int bottomStart = Math.max((int) Math.ceil(pRadius), (int) Math.floor(pHeight - pRadius));
        for (int i = bottomStart; i < rows; i++) {
            float rowY = py + i;
            float dy = (rowY + 0.5f) - yCenterBottom;
            float dxOuter = (dy > 0 && dy < pRadius) ? pRadius * (float) Math.pow(1.0 - Math.pow(dy / pRadius, 5.0), 0.2) : pRadius;
            float innerR = pRadius - t;
            float dxInner = (dy >= 0 && dy < innerR) ? innerR * (float) Math.pow(1.0 - Math.pow(dy / innerR, 5.0), 0.2) : 0;

            float leftXOuter = px + pRadius - dxOuter;
            float leftXInner = px + pRadius - dxInner;
            float rightXInner = px + pWidth - pRadius + dxInner;
            float rightXOuter = px + pWidth - pRadius + dxOuter;

            if (rowY >= py + pHeight - t) {
                fillRect(sink, leftXOuter, rowY, rightXOuter, rowY + 1, color);
            } else {
                fillRect(sink, leftXOuter, rowY, leftXInner, rowY + 1, color);
                fillRect(sink, rightXInner, rowY, rightXOuter, rowY + 1, color);
            }
        }

        graphics.pose().popMatrix();
    }

    /**
     * Helper to render word-wrapped string text within a specified bounding width.
     */
    public static void drawWordWrap(Font font, GuiGraphics graphics, String text, float x, float y, float maxWidth, int color) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), (int) maxWidth);
        float currentY = y;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, (int) x, (int) currentY, color, false);
            currentY += font.lineHeight + 2;
        }
    }

    /**
     * Helper to render word-wrapped string text with a capped maximum line count.
     */
    public static void drawWordWrapMaxLines(Font font, GuiGraphics graphics, String text, float x, float y, float maxWidth, int maxLines, int color) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), (int) maxWidth);
        float currentY = y;
        int count = 0;
        for (FormattedCharSequence line : lines) {
            if (count >= maxLines) break;
            graphics.drawString(font, line, (int) x, (int) currentY, color, false);
            currentY += font.lineHeight + 2;
            count++;
        }
    }
}
