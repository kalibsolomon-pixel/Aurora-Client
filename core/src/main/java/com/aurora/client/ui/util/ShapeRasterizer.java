package com.aurora.client.ui.util;

import com.aurora.client.port.DrawSurface;

/** Existing physical-pixel coverage math, independent of the native GUI pipeline. */
public final class ShapeRasterizer {
    private ShapeRasterizer() {}

    private static void fillRect(DrawSurface sink, float x1, float y1, float x2, float y2, int color) {
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
    private static void fillAARow(DrawSurface sink, float leftEdge, float rowY, float rightEdge, int color) {
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
        int fullStart = leftFrac > 0.001f ? leftInt + 1 : leftInt;
        int fullEnd = rightInt;
        if (fullEnd > fullStart) {
            sink.rect(fullStart, y0, fullEnd, y1, color);
        }
        // Right boundary pixel â€” covered from rightInt to rightEdge.
        if (rightFrac > 0.001f) {
            sink.rect(rightInt, y0, rightInt + 1, y1, scaleAlphaChannel(color, rightFrac));
        }
    }

    /** Filled triangle using the same physical-pixel coverage spans as rounded shapes.
     * No mesh, shader or texture allocation; useful for continuously turning indicators. */
    public static void drawTriangleAA(DrawSurface sink, float scale, float ax, float ay, float bx, float by,
                                      float cx, float cy, int color) {

        ax *= scale; ay *= scale; bx *= scale; by *= scale; cx *= scale; cy *= scale;
        float minY = Math.min(ay, Math.min(by, cy)), maxY = Math.max(ay, Math.max(by, cy));
        if (!Float.isFinite(minY) || !Float.isFinite(maxY) || maxY <= minY) return;


        for (int y = (int) Math.floor(minY); y < Math.ceil(maxY); y++) {
            float top = Math.max(y, minY), bottom = Math.min(y + 1, maxY), mid = (top + bottom) * .5f;
            float ab = edgeX(ax, ay, bx, by, mid), bc = edgeX(bx, by, cx, cy, mid), ca = edgeX(cx, cy, ax, ay, mid);
            float left = Float.POSITIVE_INFINITY, right = Float.NEGATIVE_INFINITY;
            if (Float.isFinite(ab)) { left = Math.min(left, ab); right = Math.max(right, ab); }
            if (Float.isFinite(bc)) { left = Math.min(left, bc); right = Math.max(right, bc); }
            if (Float.isFinite(ca)) { left = Math.min(left, ca); right = Math.max(right, ca); }
            if (right > left) fillAARow(sink, left, y, right, scaleAlphaChannel(color, bottom - top));
        }

    }

    private static float edgeX(float ax, float ay, float bx, float by, float y) {
        return ay != by && y >= Math.min(ay, by) && y < Math.max(ay, by)
                ? ax + (bx - ax) * (y - ay) / (by - ay) : Float.NaN;
    }

    /**
     * Antialiased filled rounded rectangle — the canonical rounded-fill
     * rasterizer. Exact sub-pixel coverage of each edge pixel is folded into
     * its alpha channel via {@link #fillAARow}, producing a smooth curve
     * instead of a jagged integer staircase.
     */
    public static void drawRoundedRectAA(DrawSurface sink, float scale, float x, float y, float width, float height, float radius, int color) {
        if (width <= 0 || height <= 0) return;



        float px = x * scale;
        float py = y * scale;
        float pWidth = width * scale;
        float pHeight = height * scale;
        float pRadius = Math.min(radius * scale, Math.min(pWidth, pHeight) / 2.0f);

        if (pRadius <= 0) {
            fillRect(sink, px, py, px + pWidth, py + pHeight, color);

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
    public static void drawCircleAA(DrawSurface sink, float scale, float x, float y, float width, float height, int color) {
        if (width <= 0 || height <= 0) return;



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

    }

    /**
     * Antialiased rounded-rect outline. Same analytical geometry as
     * {@link #drawRoundedOutline} but emits {@link #fillAARow} for the
     * corner bands so both the outer and inner arc edges fade smoothly
     * into the surface â€” no faceted staircase on small radii. Recommended
     * for the search bar focus ring and any thin-stroked control.
     */
    public static void drawRoundedOutlineAA(DrawSurface sink, float scale, float x, float y, float width, float height, float radius, float thickness, int color) {
        if (width <= 0 || height <= 0) return;



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

    }

    /**
     * Draws a filled squircle (superellipse with exponent 5.0) with physical-pixel resolution matching.
     * Uses a single-pass row-by-row horizontal span render to prevent overlap artifacts and double-blending lines.
     */
    public static void drawSquircle(DrawSurface sink, float scale, float x, float y, float width, float height, float radius, int color) {
        if (width <= 0 || height <= 0) return;



        float px = x * scale;
        float py = y * scale;
        float pWidth = width * scale;
        float pHeight = height * scale;
        float pRadius = Math.min(radius * scale, Math.min(pWidth, pHeight) / 2.0f);

        if (pRadius <= 0) {
            fillRect(sink, px, py, px + pWidth, py + pHeight, color);

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

    }

    /**
     * Draws an outlined squircle (superellipse with exponent 5.0) with physical-pixel resolution matching.
     */
    public static void drawSquircleOutline(DrawSurface sink, float scale, float x, float y, float width, float height, float radius, float thickness, int color) {
        if (width <= 0 || height <= 0) return;



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

    }

}
