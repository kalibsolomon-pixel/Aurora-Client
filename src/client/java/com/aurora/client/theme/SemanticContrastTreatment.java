package com.aurora.client.theme;

/**
 * Resolve-time Phase D treatment for non-ON_ACCENT contrast roles. Painters
 * consume these immutable colors directly; all searches are finite and run
 * only when a theme is resolved.
 */
public final class SemanticContrastTreatment {
    private final int essentialPlate;
    private final int controlTint;
    private final int fieldTint;
    private final int disabledFill;
    private final int disabledText;
    private final int placeholderText;
    private final int focusNeutral;
    private final int focusOnAccent;
    private final int mechanicalOn;
    private final int mechanicalOff;
    private final int errorForeground;
    private final int errorIndicator;
    private final int warningIndicator;
    private final int secondaryIndicator;
    private final int tooltipBackground;
    private final int tooltipForeground;

    private SemanticContrastTreatment(int essentialPlate, int controlTint, int fieldTint,
                                      int disabledFill, int disabledText, int placeholderText,
                                      int focusNeutral, int focusOnAccent,
                                      int mechanicalOn, int mechanicalOff,
                                      int errorForeground, int errorIndicator,
                                      int warningIndicator, int secondaryIndicator,
                                      int tooltipBackground, int tooltipForeground) {
        this.essentialPlate = essentialPlate;
        this.controlTint = controlTint;
        this.fieldTint = fieldTint;
        this.disabledFill = disabledFill;
        this.disabledText = disabledText;
        this.placeholderText = placeholderText;
        this.focusNeutral = focusNeutral;
        this.focusOnAccent = focusOnAccent;
        this.mechanicalOn = mechanicalOn;
        this.mechanicalOff = mechanicalOff;
        this.errorForeground = errorForeground;
        this.errorIndicator = errorIndicator;
        this.warningIndicator = warningIndicator;
        this.secondaryIndicator = secondaryIndicator;
        this.tooltipBackground = tooltipBackground;
        this.tooltipForeground = tooltipForeground;
    }

    static SemanticContrastTreatment fromColors(int[] colors,
                                                 AdaptiveOnAccentTreatment onAccent,
                                                 ContrastDerivations derivations) {
        int window = colors[ThemeToken.WINDOW_FILL.ordinal()];
        int surface = colors[ThemeToken.SURFACE.ordinal()];
        int inset = colors[ThemeToken.SURFACE_INSET.ordinal()];
        int primary = colors[ThemeToken.ON_BACKGROUND.ordinal()];
        int muted = colors[ThemeToken.ON_BACKGROUND_MUTED.ordinal()];
        int accent = colors[ThemeToken.ACCENT.ordinal()];
        float hue = PaletteEngine.rgbToHsl(accent & 0x00FFFFFF)[0];

        int plateRgb = surface & 0x00FFFFFF;
        int plateAlpha = minimumOverlayAlpha(primary, plateRgb, window,
                ContrastDerivations.ESSENTIAL_TEXT_RATIO);
        int plate = (plateAlpha << 24) | plateRgb;

        int controlAlpha = Math.max((window >>> 24) & 0xFF,
                minimumOverlayAlpha(primary, plateRgb, 0x00000000,
                        ContrastDerivations.ESSENTIAL_TEXT_RATIO));
        int control = (controlAlpha << 24) | plateRgb;
        int fieldAlpha = Math.max(controlAlpha,
                minimumOverlayAlpha(muted, inset & 0x00FFFFFF, 0x00000000,
                        ContrastDerivations.SUPPLEMENTAL_MUTED_RATIO));
        int field = (fieldAlpha << 24) | (inset & 0x00FFFFFF);

        int disabledFill = 0xFF000000 | (inset & 0x00FFFFFF);
        int disabledText = ContrastDerivations.chooseForeground(disabledFill, hue).chosen();
        int placeholder = readableForegroundAlpha(
                colors[ThemeToken.ON_BACKGROUND_MUTED.ordinal()], field,
                ContrastDerivations.SUPPLEMENTAL_MUTED_RATIO);

        int[] visibleControls = new int[ContrastDerivations.WORST_BASES.size()];
        for (int i = 0; i < visibleControls.length; i++) {
            visibleControls[i] = PaletteEngine.composite(control,
                    ContrastDerivations.WORST_BASES.get(i));
        }
        int focusNeutral = bestBinaryAcross(hue, visibleControls);
        int focusAccent = ContrastDerivations.chooseForeground(
                onAccent.selectedSegment(), hue).chosen();

        int mechanicalOn = ContrastDerivations.chooseForeground(accent, hue).chosen();
        int mechanicalOff = ContrastDerivations.chooseForeground(
                colors[ThemeToken.SURFACE_VARIANT.ordinal()], hue).chosen();
        int error = ContrastDerivations.chooseForeground(
                colors[ThemeToken.SEMANTIC_ERROR.ordinal()], hue).chosen();
        int errorIndicator = readableHueIndicator(colors[ThemeToken.SEMANTIC_ERROR.ordinal()],
                surface, ContrastDerivations.NON_TEXT_INDICATOR_RATIO);
        int warning = readableHueIndicator(colors[ThemeToken.SEMANTIC_WARNING.ordinal()],
                surface, ContrastDerivations.NON_TEXT_INDICATOR_RATIO);
        int secondary = readableHueIndicator(colors[ThemeToken.SECONDARY_ACCENT.ordinal()],
                surface, ContrastDerivations.NON_TEXT_INDICATOR_RATIO);

        int tooltipBg = 0xFF000000 | (surface & 0x00FFFFFF);
        int tooltipFg = ContrastDerivations.chooseForeground(tooltipBg, hue).chosen();
        return new SemanticContrastTreatment(plate, control, field, disabledFill, disabledText,
                placeholder, focusNeutral, focusAccent, mechanicalOn, mechanicalOff,
                error, errorIndicator,
                warning, secondary,
                tooltipBg, tooltipFg);
    }

    private static int minimumOverlayAlpha(int fg, int plateRgb, int window) {
        return minimumOverlayAlpha(fg, plateRgb, window,
                ContrastDerivations.ESSENTIAL_TEXT_RATIO);
    }

    private static int minimumOverlayAlpha(int fg, int plateRgb, int window, double target) {
        for (int a = 0; a <= 255; a++) {
            int overlay = (a << 24) | (plateRgb & 0x00FFFFFF);
            boolean pass = true;
            for (int world : ContrastDerivations.WORST_BASES) {
                int visibleWindow = PaletteEngine.composite(window, world);
                int backing = PaletteEngine.composite(overlay, visibleWindow);
                int visibleFg = PaletteEngine.composite(fg, backing);
                if (PaletteEngine.contrastRatio(visibleFg, backing) < target) {
                    pass = false;
                    break;
                }
            }
            if (pass) return a;
        }
        return 255;
    }

    private static int bestBinaryAcross(float hue, int[] backings) {
        int light = PaletteEngine.hsl(hue, 0.05f, 0.97f);
        int dark = PaletteEngine.hsl(hue, 0.10f, 0.08f);
        double lightWorst = Double.MAX_VALUE;
        double darkWorst = Double.MAX_VALUE;
        for (int backing : backings) {
            lightWorst = Math.min(lightWorst, PaletteEngine.contrastRatio(light, backing));
            darkWorst = Math.min(darkWorst, PaletteEngine.contrastRatio(dark, backing));
        }
        return lightWorst >= darkWorst ? light : dark;
    }

    private static int readableForegroundAlpha(int foreground, int backingLayer, double target) {
        int rgb = foreground & 0x00FFFFFF;
        int floor = (foreground >>> 24) & 0xFF;
        for (int a = floor; a <= 255; a++) {
            int candidate = (a << 24) | rgb;
            boolean pass = true;
            for (int world : ContrastDerivations.WORST_BASES) {
                int backing = PaletteEngine.composite(backingLayer, world);
                int visible = PaletteEngine.composite(candidate, backing);
                if (PaletteEngine.contrastRatio(visible, backing) < target) {
                    pass = false;
                    break;
                }
            }
            if (pass) return candidate;
        }
        return 0xFF000000 | rgb;
    }

    private static int readableHueIndicator(int color, int backing, double target) {
        int opaque = 0xFF000000 | color;
        if (PaletteEngine.contrastRatio(opaque, backing) >= target) return opaque;
        float[] hsl = PaletteEngine.rgbToHsl(opaque & 0x00FFFFFF);
        for (int step = 1; step <= 255; step++) {
            float delta = step / 255f;
            float darker = Math.max(0f, hsl[2] - delta);
            int dark = PaletteEngine.hsl(hsl[0], hsl[1], darker);
            if (PaletteEngine.contrastRatio(dark, backing) >= target) return dark;
            float lighter = Math.min(1f, hsl[2] + delta);
            int light = PaletteEngine.hsl(hsl[0], hsl[1], lighter);
            if (PaletteEngine.contrastRatio(light, backing) >= target) return light;
        }
        return ContrastDerivations.chooseForeground(backing, hsl[0]).chosen();
    }

    public int essentialPlate() { return essentialPlate; }
    public int controlTint() { return controlTint; }
    public int fieldTint() { return fieldTint; }
    public int disabledFill() { return disabledFill; }
    public int disabledText() { return disabledText; }
    public int placeholderText() { return placeholderText; }
    public int focusNeutral() { return focusNeutral; }
    public int focusOnAccent() { return focusOnAccent; }
    public int mechanicalOn() { return mechanicalOn; }
    public int mechanicalOff() { return mechanicalOff; }
    public int errorForeground() { return errorForeground; }
    public int errorIndicator() { return errorIndicator; }
    public int warningIndicator() { return warningIndicator; }
    public int secondaryIndicator() { return secondaryIndicator; }
    public int tooltipBackground() { return tooltipBackground; }
    public int tooltipForeground() { return tooltipForeground; }
}
