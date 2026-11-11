package com.aurora.client.theme;

/** Launcher-derived roles, resolved once alongside the compatibility palette.
 * No saved preference is rewritten. Light deliberately retains Client's existing
 * palette; Launcher supplies no authoritative Light companion. */
public final class UnifiedPalette {
    private UnifiedPalette() {}

    public record AccentRoles(int base, int strong, int hover, int pressed,
                              int onAction, int soft, int marker, int outline) {}

    /** Port of appearance.rs custom HSL policy, with bounded Client adaptations:
     * dark bases remain valid, and EVERY action state is checked after rounding.
     * Client stores RGB, not Launcher preset ids, so all RGBs take this one path. */
    public static AccentRoles accent(int stored, ThemeMode mode) {
        int base = PaletteEngine.normalizeAccent(stored);
        double[] hsl = toHsl(base);
        double light = clamp(hsl[2], .34, .62);
        int strong = rgb(hsl[0], hsl[1], light);
        int foreground = contrast(strong, 0xffffffff) >= 4.5 ? 0xffffffff : 0xff101116;
        while (contrast(strong, foreground) < 4.5 && light > .10) {
            light = Math.max(.10, light - .05);
            strong = rgb(hsl[0], hsl[1], light);
            foreground = 0xffffffff;
        }
        if(strong==base) {
            light += foreground == -1 ? -.02 : .02;
            strong=readable(hsl[0],hsl[1],light,foreground);
        }
        int hover = readable(hsl[0], hsl[1], light + (foreground == -1 ? -.03 : .05), foreground);
        int pressed = readable(hsl[0], hsl[1], light + (foreground == -1 ? -.07 : .02), foreground);
        // A base already in the action band may equal strong. Preserve the base,
        // but keep the primary role visibly distinct with a small HSL step.
        int neutral = mode == ThemeMode.LIGHT ? PaletteEngine.derive(base,mode)[ThemeToken.SURFACE_VARIANT.ordinal()] : 0xff1d1f27;
        double markerLight = hsl[2];
        int marker = base;
        for (int i = 0; contrast(marker, neutral) < 3 && i < 100; i++) {
            markerLight = clamp(markerLight + (mode == ThemeMode.LIGHT ? -.01 : .01), .02, .98);
            marker = rgb(hsl[0], hsl[1], markerLight);
        }
        return new AccentRoles(base, strong, hover, pressed, foreground,
                (base & 0xffffff) | 0x24000000, marker, (marker & 0xffffff) | 0x8c000000);
    }

    static void populate(int[] c, int accent, ThemeMode mode) {
        boolean dark = mode != ThemeMode.LIGHT;
        put(c, ThemeToken.MATERIAL_CANVAS, dark ? 0xff101116 : c[ThemeToken.BACKGROUND.ordinal()]);
        put(c, ThemeToken.MATERIAL_PANEL, dark ? 0xff16171d : c[ThemeToken.SURFACE.ordinal()]);
        put(c, ThemeToken.MATERIAL_RAISED, dark ? 0xff1d1f27 : c[ThemeToken.SURFACE_VARIANT.ordinal()]);
        put(c, ThemeToken.MATERIAL_HOVER, dark ? 0xff242631 : neutralHover(c[ThemeToken.SURFACE_VARIANT.ordinal()],c[ThemeToken.ON_BACKGROUND.ordinal()]));
        put(c, ThemeToken.MATERIAL_PRESSED, dark ? 0xff1a1c24 : c[ThemeToken.SURFACE_INSET.ordinal()]);
        put(c, ThemeToken.MATERIAL_SUNKEN, dark ? 0xff0d0e13 : c[ThemeToken.SURFACE_INSET.ordinal()]);
        put(c, ThemeToken.MATERIAL_BORDER, dark ? 0xff24262f : c[ThemeToken.BORDER.ordinal()]);
        put(c, ThemeToken.MATERIAL_BORDER_STRONG, dark ? 0xff343743 : c[ThemeToken.BORDER_HOVER.ordinal()]);
        put(c, ThemeToken.MATERIAL_TEXT, dark ? 0xffedf1fb : c[ThemeToken.ON_BACKGROUND.ordinal()]);
        put(c, ThemeToken.MATERIAL_TEXT_SECONDARY, dark ? 0xffa7b0c5 : c[ThemeToken.ON_BACKGROUND_SECONDARY.ordinal()]);
        put(c, ThemeToken.MATERIAL_TEXT_MUTED, dark ? 0xff76819a : c[ThemeToken.ON_BACKGROUND_MUTED.ordinal()]);
        AccentRoles a = accent(accent, mode);
        put(c, ThemeToken.ACTION_STRONG, a.strong); put(c, ThemeToken.ACTION_HOVER, a.hover);
        put(c, ThemeToken.ACTION_PRESSED, a.pressed); put(c, ThemeToken.ON_ACTION, a.onAction);
        put(c, ThemeToken.SELECTION_SOFT, a.soft); put(c, ThemeToken.ACCENT_MARKER, a.marker);
        put(c, ThemeToken.ACCENT_OUTLINE, a.outline);
        put(c, ThemeToken.STATUS_SUCCESS, 0xff7ee2b8); put(c, ThemeToken.STATUS_WARNING, 0xffecc36b);
        put(c, ThemeToken.STATUS_ERROR, 0xffff9a9a); put(c, ThemeToken.STATUS_WORKING, 0xff8fb7ff);
    }

    /** The neutral material roles a Background Color re-tints (text and status roles stay fixed). */
    private static final ThemeToken[] TINTED_NEUTRALS = {
            ThemeToken.MATERIAL_CANVAS, ThemeToken.MATERIAL_PANEL, ThemeToken.MATERIAL_RAISED,
            ThemeToken.MATERIAL_HOVER, ThemeToken.MATERIAL_PRESSED, ThemeToken.MATERIAL_SUNKEN,
            ThemeToken.MATERIAL_BORDER, ThemeToken.MATERIAL_BORDER_STRONG};
    /** Panel lightness ceiling: brighter picks are deepened so primary text stays readable. */
    static final double MAX_PANEL_LIGHTNESS = .28;
    /** Primary text against a tinted panel (before window translucency). */
    static final double MIN_TEXT_CONTRAST = 7;

    /**
     * Re-tints the neutral material family toward {@code background}: the
     * panel (the window fill) takes the chosen hue, saturation and lightness
     * (lightness capped at {@link #MAX_PANEL_LIGHTNESS}), and every other
     * neutral keeps its lightness step from the stock panel, so raised,
     * pressed, sunken and border roles stay distinguishable on any tint. The
     * stock graphite is an exact no-op.
     */
    public static void tintNeutrals(int[] c, int background) {
        int panel = c[ThemeToken.MATERIAL_PANEL.ordinal()];
        if (((background ^ panel) & 0xFFFFFF) == 0) return;
        double[] tint = toHsl(background);
        double target = Math.min(tint[2], MAX_PANEL_LIGHTNESS);
        // Bright hues (yellow, cyan) are luminous even at low lightness: deepen
        // until primary text keeps the 7:1 the stock panel and presets have.
        int text = c[ThemeToken.MATERIAL_TEXT.ordinal()];
        while (target > .04 && contrast(rgb(tint[0], tint[1], target), text) < MIN_TEXT_CONTRAST) target -= .01;
        double stock = toHsl(panel)[2];
        for (ThemeToken token : TINTED_NEUTRALS) {
            int i = token.ordinal();
            double lightness = clamp(toHsl(c[i])[2] - stock + target, .02, .90);
            c[i] = (c[i] & 0xFF000000) | (rgb(tint[0], tint[1], lightness) & 0xFFFFFF);
        }
    }

    private static int neutralHover(int surface,int text) {
        int result=0xff000000;
        for(int shift:new int[]{16,8,0}) {
            int a=surface>>>shift&255,b=text>>>shift&255;
            result |= Math.round(a+(b-a)*.08f)<<shift;
        }
        return result;
    }
    private static void put(int[] c, ThemeToken t, int v) { c[t.ordinal()] = v; }
    private static int readable(double h, double s, double l, int foreground) {
        l = clamp(l, .05, .95);
        int color = rgb(h, s, l);
        for (int i = 0; contrast(color, foreground) < 4.5 && i < 100; i++) {
            l = clamp(l + (foreground == -1 ? -.005 : .005), .02, .98);
            color = rgb(h, s, l);
        }
        return color;
    }
    public static double contrast(int a, int b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + .05) / (Math.min(x, y) + .05);
    }
    private static double luminance(int c) {
        return .2126 * channel(c >> 16) + .7152 * channel(c >> 8) + .0722 * channel(c);
    }
    private static double channel(int c) {
        double v = (c & 255) / 255d;
        return v <= .03928 ? v / 12.92 : Math.pow((v + .055) / 1.055, 2.4);
    }
    private static double[] toHsl(int c) {
        double r = (c >> 16 & 255) / 255d, g = (c >> 8 & 255) / 255d, b = (c & 255) / 255d;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        double d = max - min, l = (max + min) / 2, h = 0, s = 0;
        if (d != 0) {
            s = d / (1 - Math.abs(2 * l - 1));
            h = (max == r ? (g - b) / d + (g < b ? 6 : 0) : max == g ? (b - r) / d + 2 : (r - g) / d + 4) / 6;
        }
        return new double[]{h, s, l};
    }
    private static int rgb(double h, double s, double l) {
        double q = l < .5 ? l * (1 + s) : l + s - l * s, p = 2 * l - q;
        return 0xff000000 | (int)Math.round(hue(p, q, h + 1d/3) * 255) << 16
                | (int)Math.round(hue(p, q, h) * 255) << 8 | (int)Math.round(hue(p, q, h - 1d/3) * 255);
    }
    private static double hue(double p, double q, double t) {
        t = (t + 1) % 1;
        if (t < 1d/6) return p + (q-p)*6*t;
        if (t < .5) return q;
        if (t < 2d/3) return p + (q-p)*(2d/3-t)*6;
        return p;
    }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
}
