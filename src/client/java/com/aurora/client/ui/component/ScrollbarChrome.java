package com.aurora.client.ui.component;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.SmoothScroll;
import net.minecraft.client.gui.GuiGraphics;

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

    /** Paint the canonical 3 px mechanical capsule. */
    public static void draw(GuiGraphics g, int x, Thumb thumb, boolean emphasized,
                            ThemeToken foreground) {
        int alpha = emphasized ? 0x55 : 0x30;
        int color = ThemeManager.withAlpha(ThemeManager.color(foreground), alpha);
        RenderUtil.drawRoundedRectAA(g, x, (float) thumb.y(), 3, (float) thumb.height(), 2, color);
    }
}
