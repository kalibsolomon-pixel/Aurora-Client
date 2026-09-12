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
 * <p>One shape: the gradient-only top fade, for scissored viewports —
 * content must not be able to paint above the boundary, and a GL scissor
 * (not a painted cover) is what enforces that. The pilot originally
 * shipped a capped variant for the then-unscissored detail screens — a
 * solid fill of the surface color above the boundary — and it failed in
 * the field (2026-09-12): its hiding power was WINDOW_FILL's alpha, i.e.
 * the user-tunable Background Opacity, so at low opacity rows slid right
 * through the "cover" over the title band (reproduced on Minimap and
 * Better Hitreg at opacity 0.1). The screen gained a real scissor and
 * the capped variant was deleted; any future scrollable surface gets the
 * scissor, never a painted cap.
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
     * The §8 gradient: painted AFTER the scissored content, from the
     * surface color VERBATIM at {@code boundaryY} down to fully
     * transparent over {@code fadeH}. Softens the scissor boundary; it is
     * not what hides content (see the class javadoc for why a painted
     * cover cannot do that).
     */
    public static void drawTop(GuiGraphics g, int x, int w, int boundaryY, int fadeH,
                               double scrollPos, int surfaceArgb) {
        double k = engagement(scrollPos, fadeH);
        if (k <= 0.0 || fadeH <= 0 || w <= 0) return;
        int rgb = surfaceArgb & 0x00FFFFFF;
        int surfaceA = (surfaceArgb >>> 24) & 0xFF;
        int topA = (int) (surfaceA * k);
        if (topA <= 0) return;
        g.fillGradient(x, boundaryY, x + w, boundaryY + fadeH,
                (topA << 24) | rgb, rgb);
    }
}
