package com.aurora.client.ui.component;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.SmoothScroll;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shared material and pixel-stable geometry for Aurora scrollbar thumbs. */
public final class ScrollbarChrome {

    private ScrollbarChrome() {}

    /** Logical paint bounds whose two edges are snapped to device pixels. */
    public record Thumb(double y, double height) {}

    /**
     * Quantize the top and bottom edge together. This avoids the historical
     * one-pixel loss caused by independently truncating fractional y/height.
     */
    public static Thumb thumb(double trackY, double trackH, double thumbH, double ratio,
                              double guiScale) {
        double clampedRatio = Math.max(0.0, Math.min(1.0, ratio));
        double top = trackY + Math.max(0.0, trackH - thumbH) * clampedRatio;
        double scale = Math.max(1.0, guiScale);
        double y0 = Math.round(top * scale) / scale;
        double y1 = Math.round((top + thumbH) * scale) / scale;
        return new Thumb(y0, Math.max(1.0 / scale, y1 - y0));
    }

    public static Thumb thumb(SmoothScroll scroll, double trackY, double trackH,
                              double maxScroll, double minThumbH, double guiScale) {
        return thumb(trackY, trackH, scroll.thumbHeight(trackH, maxScroll, minThumbH),
                scroll.ratio(maxScroll), guiScale);
    }

    /**
     * The faint full-length track under a thumb (2026-11-14): a bare thumb read as a stray mark,
     * the track says "this scrolls" and how far.
     */
    public static void track(GuiGraphicsExtractor g, int x, double trackY, double trackH, ThemeToken foreground) {
        RenderUtil.drawRoundedRectAA(g, x, (float) trackY, 3, (float) trackH,
                AuroraTheme.RADIUS, ThemeManager.withAlpha(ThemeManager.color(foreground), 0x1A));
    }

    /** Paint the 3 px scrollbar thumb. */
    public static void draw(GuiGraphicsExtractor g, int x, Thumb thumb, boolean emphasized,
                            ThemeToken foreground) {
        int alpha = emphasized ? 0x80 : 0x50; // readable at rest on the graphite window (2026-11-14; was 0x55/0x30)
        int color = ThemeManager.withAlpha(ThemeManager.color(foreground), alpha);
        RenderUtil.drawRoundedRectAA(g, x, (float) thumb.y(), 3, (float) thumb.height(),
                AuroraTheme.RADIUS, color);
    }
}
