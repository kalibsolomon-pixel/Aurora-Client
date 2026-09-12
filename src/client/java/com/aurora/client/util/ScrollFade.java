package com.aurora.client.util;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Top-edge scroll fade (DESIGN_LANGUAGE §8): scrollable content fades into
 * the surface it sits on as it approaches the top clip boundary, instead of
 * hard-cutting (or, on unscissored lists, sliding over the title band).
 *
 * <p>Mechanism: a short vertical gradient painted AFTER the scrollable
 * content — never inside a {@code UiLayerCache} capture pass — starting at
 * the boundary in the surface color VERBATIM (alpha channel included) and
 * ramping to fully transparent over {@code fadeH} pixels. Because the top of
 * the gradient is the color the surface already has at that position, the
 * fade reads as content dissolving into the surface over glass and
 * flat/fallback containers alike, with no hardcoded color: scissored
 * viewports inside a window pass {@code WINDOW_FILL}, screens whose rows
 * float over the veiled world pass {@code OVERLAY_DIM}.
 *
 * <p>The fade ENGAGES with scroll: the top alpha scales with
 * {@code min(1, scrollPos / fadeH)}, so a list at rest (nothing above the
 * boundary) is pixel-untouched and the fade ramps in over the first
 * {@code fadeH} pixels of scrolling. This is also why the engagement takes
 * the raw scroll position, not a scroll/overflow ratio — the fade must be
 * fully engaged exactly when one fade-height of content has passed the
 * boundary, regardless of how long the list is.
 *
 * <p>Two shapes: {@link #drawTop} for scissored viewports (content cannot
 * paint above the boundary, so a plain gradient suffices) and
 * {@link #drawTopCapped} for unscissored lists, where a solid cap of the
 * same color covers content that still paints above the boundary.
 *
 * <p>Bottom edge deliberately has no counterpart — lists end in the screen's
 * own padding/footer, which never produced the collision the top edge does.
 */
public final class ScrollFade {

    /** The rule's fade distance in GUI pixels (DESIGN_LANGUAGE §8). */
    public static final int FADE_PX = 16;

    private ScrollFade() {}

    /**
     * 0..1 — how engaged the fade is: full once one fade-height of content
     * has scrolled past the boundary, ramping from zero at rest.
     */
    public static double engagement(double scrollPos, int fadeH) {
        if (fadeH <= 0) return 0.0;
        return Math.max(0.0, Math.min(1.0, scrollPos / fadeH));
    }

    /**
     * Gradient-only top fade for scissored viewports: content cannot paint
     * above {@code boundaryY}, so nothing needs capping above it.
     */
    public static void drawTop(GuiGraphics g, int x, int w, int boundaryY, int fadeH,
                               double scrollPos, int surfaceArgb) {
        draw(g, x, w, boundaryY, Integer.MIN_VALUE, fadeH, scrollPos, surfaceArgb);
    }

    /**
     * Capped top fade for unscissored lists: a solid fill of the surface
     * color from {@code capTopY} down to {@code boundaryY} hides content
     * that still paints above the boundary, and the gradient continues below
     * it. The cap uses the same engagement-scaled top alpha.
     */
    public static void drawTopCapped(GuiGraphics g, int x, int w, int capTopY, int boundaryY,
                                     int fadeH, double scrollPos, int surfaceArgb) {
        draw(g, x, w, boundaryY, capTopY, fadeH, scrollPos, surfaceArgb);
    }

    private static void draw(GuiGraphics g, int x, int w, int boundaryY, int capTopY,
                             int fadeH, double scrollPos, int surfaceArgb) {
        double k = engagement(scrollPos, fadeH);
        if (k <= 0.0 || fadeH <= 0 || w <= 0) return;
        int rgb = surfaceArgb & 0x00FFFFFF;
        int surfaceA = (surfaceArgb >>> 24) & 0xFF;
        int topA = (int) (surfaceA * k);
        if (topA <= 0) return;
        int top = (topA << 24) | rgb;
        int bot = rgb; // fully transparent, same hue
        if (capTopY != Integer.MIN_VALUE && boundaryY > capTopY) {
            g.fill(x, capTopY, x + w, boundaryY, top);
        }
        g.fillGradient(x, boundaryY, x + w, boundaryY + fadeH, top, bot);
    }
}
