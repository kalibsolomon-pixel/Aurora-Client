package com.aurora.client.theme;

import com.aurora.client.util.ColorEntryHelper;

/**
 * The subtle two-stop gradient accent fills wear (owner request
 * 2026-11-12): switch tracks and slider fills run from {@link #start}
 * on the left to {@link #end} on the right. Both stops sit a few degrees of
 * hue either side of the accent, the start a touch lighter, so the fill
 * reads as the accent with a little depth rather than as two colors.
 * Derived automatically — there is nothing to configure.
 *
 * <p>Greys (no saturation) have no hue to sweep; they only get the
 * lightness step. Pure color math, no Minecraft imports (ADR 0003).
 */
public final class AccentGradient {
    private AccentGradient() {}

    /** Hue offset of each stop from the accent, as a fraction of the color wheel (≈ 14°). */
    static final float HUE_STEP = 0.04f;
    /** Lightness added to the start stop (half of it is taken from the end stop). */
    static final float LIGHT_STEP = 0.06f;

    /**
     * One-entry memo: painters ask for the same accent every frame, so the
     * HSL round trip (and its small array) runs once per accent change, not
     * per control per frame. Render-thread only, like every painter.
     */
    private static int memoIn = 0, memoStart = 0, memoEnd = 0;
    private static boolean memoValid;

    /** Left stop: hue turned back, slightly lighter. Alpha is the accent's. */
    public static int start(int accent) {
        memo(accent);
        return memoStart;
    }

    /** Right stop: hue turned forward, slightly darker. Alpha is the accent's. */
    public static int end(int accent) {
        memo(accent);
        return memoEnd;
    }

    private static void memo(int accent) {
        if (memoValid && memoIn == accent) return;
        memoStart = shift(accent, -HUE_STEP, LIGHT_STEP);
        memoEnd = shift(accent, HUE_STEP, -LIGHT_STEP * 0.5f);
        memoIn = accent;
        memoValid = true;
    }

    private static int shift(int argb, float hue, float light) {
        float[] hsla = ColorEntryHelper.argbToHsla(argb);
        float h = hsla[1] < 0.02f ? hsla[0] : hsla[0] + hue;
        h -= (float) Math.floor(h);
        float l = Math.max(0f, Math.min(1f, hsla[2] + light));
        int rgb = ColorEntryHelper.hslaToArgb(h, hsla[1], l, 1f) & 0x00FFFFFF;
        return (argb & 0xFF000000) | rgb;
    }
}
