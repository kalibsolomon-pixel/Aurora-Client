package com.aurora.client.theme;

/**
 * Phase D's canonical resolve-time treatment for every text-bearing ON_ACCENT family:
 * flat primary buttons, selected segmented-control peers, and the Keybind
 * listening pill. Components consume these already-resolved colors; no
 * luminance or contrast work occurs while rendering.
 *
 * <p>The adaptation order is deliberate: choose one stable semantic
 * foreground, use D-1's alpha/lightness-bounded stained backing, then (only
 * when the bounded result cannot cover every real pilot state) composite the
 * minimum black/white readability scrim over the backing. The scrim is a
 * separate semantic layer, not a mutation of the stored accent and not an
 * excuse to exceed the {@code |delta L| <= 0.08} identity bound.
 */
public final class AdaptiveOnAccentTreatment {

    private static final int PATH_STEPS = 255;
    private static final int HOVER_WASH_MAX_ALPHA = 0x1A;

    private final int foreground;
    private final int supplementalForeground;
    private final int buttonRest;
    private final int buttonHover;
    private final int selectedSegment;
    private final int listeningPill;
    private final int stainedTint;
    private final int selectionIndicator;
    private final int scrimArgb;
    private final float lightnessShift;
    private final double stainedAlpha;
    private final double worstRatio;
    private final boolean boundedBackingSufficient;
    private final String policy;

    private AdaptiveOnAccentTreatment(int foreground, int supplementalForeground,
                                   int buttonRest, int buttonHover,
                                   int selectedSegment, int listeningPill, int stainedTint,
                                   int selectionIndicator, int scrimArgb, float lightnessShift, double stainedAlpha,
                                   double worstRatio, boolean boundedBackingSufficient,
                                   String policy) {
        this.foreground = foreground;
        this.supplementalForeground = supplementalForeground;
        this.buttonRest = buttonRest;
        this.buttonHover = buttonHover;
        this.selectedSegment = selectedSegment;
        this.listeningPill = listeningPill;
        this.stainedTint = stainedTint;
        this.selectionIndicator = selectionIndicator;
        this.scrimArgb = scrimArgb;
        this.lightnessShift = lightnessShift;
        this.stainedAlpha = stainedAlpha;
        this.worstRatio = worstRatio;
        this.boundedBackingSufficient = boundedBackingSufficient;
        this.policy = policy;
    }

    static AdaptiveOnAccentTreatment fromColors(int[] colors) {
        // D-1 already made the semantic light/dark foreground choice. D-2
        // consumes that result rather than silently replacing it when the
        // carried bounded-backing result reports insufficient; insufficiency
        // must select the explicit scrim fallback.
        return derive(colors, colors[ThemeToken.ON_ACCENT.ordinal()]).treatment;
    }

    private static Candidate derive(int[] colors, int foreground) {
        int windowAlpha = (colors[ThemeToken.WINDOW_FILL.ordinal()] >>> 24) & 0xFF;
        double floor = Math.max(windowAlpha,
                ContrastDerivations.STAINED_VISIBILITY_FLOOR_ALPHA) / 255d;
        int[] family = {
                colors[ThemeToken.ACCENT.ordinal()],
                colors[ThemeToken.ACCENT_HOVER.ordinal()],
                colors[ThemeToken.ACCENT_PRESSED.ordinal()],
                colors[ThemeToken.ACCENT_GRAD_BOT.ordinal()],
                colors[ThemeToken.ACCENT_GRAD_TOP.ordinal()],
                colors[ThemeToken.ACCENT_GRAD_TOP_HOVER.ordinal()]
        };
        ContrastDerivations.StainedBacking bounded =
                ContrastDerivations.readableStainedBacking(family, foreground, floor,
                        ContrastDerivations.ESSENTIAL_TEXT_RATIO);

        float shift = bounded.lightnessShift();
        int accent = shiftLightness(colors[ThemeToken.ACCENT.ordinal()], shift);
        int pressed = shiftLightness(colors[ThemeToken.ACCENT_PRESSED.ordinal()], shift);
        int rest = shiftLightness(colors[ThemeToken.ACCENT_GRAD_BOT.ordinal()], shift);
        int hover = shiftLightness(colors[ThemeToken.ACCENT_GRAD_TOP.ordinal()], shift);
        int stainAlpha = (int) Math.round(bounded.requiredAlpha() * 255d);
        int stain = (stainAlpha << 24) | (accent & 0x00FFFFFF);
        int hoverWashRgb = colors[ThemeToken.ON_BACKGROUND.ordinal()] & 0x00FFFFFF;

        boolean lightForeground = PaletteEngine.relativeLuminance(foreground) >= 0.5;
        int scrimRgb = lightForeground ? 0x000000 : 0xFFFFFF;
        int scrimAlpha = bounded.sufficient() ? 0 : 1;
        for (; scrimAlpha <= 255; scrimAlpha++) {
            int scrim = (scrimAlpha << 24) | scrimRgb;
            if (passesAllStates(foreground, rest, hover, accent, pressed, stain,
                    hoverWashRgb, scrim)) {
                break;
            }
        }
        if (scrimAlpha > 255) {
            throw new IllegalStateException("ON_ACCENT adaptive ON_ACCENT fallback failed");
        }

        int scrim = (scrimAlpha << 24) | scrimRgb;
        int finalRest = PaletteEngine.composite(scrim, rest);
        int finalHover = PaletteEngine.composite(scrim, hover);
        int finalSelected = PaletteEngine.composite(scrim, accent);
        int finalListening = PaletteEngine.composite(scrim, pressed);
        int finalStain = PaletteEngine.composite(scrim, stain);
        double worst = worstRatio(foreground, finalRest, finalHover, finalSelected,
                finalListening, finalStain, hoverWashRgb);
        String policy;
        if (scrimAlpha > 0) {
            policy = "separate-readability-scrim";
        } else if (shift != 0f || stainAlpha > Math.round(floor * 255d)) {
            policy = "bounded-accent-backing";
        } else {
            policy = "foreground-only";
        }

        int supplemental = supplementalForeground(foreground, finalSelected, finalStain);
        AdaptiveOnAccentTreatment treatment = new AdaptiveOnAccentTreatment(
                foreground, supplemental, finalRest, finalHover, finalSelected, finalListening,
                finalStain, colors[ThemeToken.ON_BACKGROUND.ordinal()], scrim,
                shift, stainAlpha / 255d, worst,
                bounded.sufficient(), policy);
        return new Candidate(treatment, scrimAlpha);
    }

    private static boolean passesAllStates(int fg, int rest, int hover, int selected,
                                           int listening, int stain, int hoverWashRgb,
                                           int scrim) {
        for (int i = 0; i <= PATH_STEPS; i++) {
            float t = i / (float) PATH_STEPS;
            int button = PaletteEngine.composite(scrim, lerpArgb(rest, hover, t));
            if (!passes(fg, button)) return false;

            int segment = PaletteEngine.composite(scrim, selected);
            int wash = (Math.round(HOVER_WASH_MAX_ALPHA * t) << 24) | hoverWashRgb;
            if (!passes(fg, PaletteEngine.composite(wash, segment))) return false;
        }
        if (!passes(fg, PaletteEngine.composite(scrim, listening))) return false;

        int combinedStain = PaletteEngine.composite(scrim, stain);
        for (int base : ContrastDerivations.WORST_BASES) {
            int visible = PaletteEngine.composite(combinedStain, base);
            if (!passes(fg, visible)) return false;
            for (int i = 0; i <= PATH_STEPS; i++) {
                float t = i / (float) PATH_STEPS;
                int wash = (Math.round(HOVER_WASH_MAX_ALPHA * t) << 24) | hoverWashRgb;
                if (!passes(fg, PaletteEngine.composite(wash, visible))) return false;
            }
        }
        return true;
    }

    private static double worstRatio(int fg, int rest, int hover, int selected,
                                     int listening, int stain, int hoverWashRgb) {
        double worst = Double.MAX_VALUE;
        for (int i = 0; i <= PATH_STEPS; i++) {
            float t = i / (float) PATH_STEPS;
            worst = Math.min(worst, PaletteEngine.contrastRatio(fg, lerpArgb(rest, hover, t)));
            int wash = (Math.round(HOVER_WASH_MAX_ALPHA * t) << 24) | hoverWashRgb;
            worst = Math.min(worst,
                    PaletteEngine.contrastRatio(fg, PaletteEngine.composite(wash, selected)));
        }
        worst = Math.min(worst, PaletteEngine.contrastRatio(fg, listening));
        for (int base : ContrastDerivations.WORST_BASES) {
            int visible = PaletteEngine.composite(stain, base);
            worst = Math.min(worst, PaletteEngine.contrastRatio(fg, visible));
            int wash = (HOVER_WASH_MAX_ALPHA << 24) | hoverWashRgb;
            worst = Math.min(worst,
                    PaletteEngine.contrastRatio(fg, PaletteEngine.composite(wash, visible)));
        }
        return worst;
    }

    private static boolean passes(int fg, int backing) {
        return PaletteEngine.contrastRatio(fg, backing)
                >= ContrastDerivations.ESSENTIAL_TEXT_RATIO;
    }

    private static int supplementalForeground(int foreground, int selected, int stain) {
        int rgb = foreground & 0x00FFFFFF;
        for (int a = 0x4D; a <= 255; a++) {
            int candidate = (a << 24) | rgb;
            int visible = PaletteEngine.composite(candidate, selected);
            if (PaletteEngine.contrastRatio(visible, selected)
                    < ContrastDerivations.SUPPLEMENTAL_MUTED_RATIO) continue;
            boolean pass = true;
            for (int base : ContrastDerivations.WORST_BASES) {
                int backing = PaletteEngine.composite(stain, base);
                visible = PaletteEngine.composite(candidate, backing);
                if (PaletteEngine.contrastRatio(visible, backing)
                        < ContrastDerivations.SUPPLEMENTAL_MUTED_RATIO) {
                    pass = false;
                    break;
                }
            }
            if (pass) return candidate;
        }
        return foreground;
    }

    private static int shiftLightness(int color, float shift) {
        if (shift == 0f) return color;
        float[] hsl = PaletteEngine.rgbToHsl(color & 0x00FFFFFF);
        return PaletteEngine.hsl(hsl[0], hsl[1],
                Math.max(0f, Math.min(1f, hsl[2] + shift)));
    }

    private static int lerpArgb(int a, int b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24)
                | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }

    private record Candidate(AdaptiveOnAccentTreatment treatment, int scrimAlpha) {}

    public int foreground() { return foreground; }
    public int supplementalForeground() { return supplementalForeground; }
    public int buttonRest() { return buttonRest; }
    public int buttonHover() { return buttonHover; }
    public int selectedSegment() { return selectedSegment; }
    public int listeningPill() { return listeningPill; }
    public int stainedTint() { return stainedTint; }
    /** Opaque compact boundary used when tint alone cannot guarantee 3:1 selected/container separation. */
    public int selectionIndicator() { return selectionIndicator; }
    public int scrimArgb() { return scrimArgb; }
    public float lightnessShift() { return lightnessShift; }
    public double stainedAlpha() { return stainedAlpha; }
    public double worstRatio() { return worstRatio; }
    public boolean boundedBackingSufficient() { return boundedBackingSufficient; }
    public String policy() { return policy; }
}
