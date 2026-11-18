package com.aurora.client.theme;

/** Persisted accent, mode, corner radius, blur radius and background density.
 * UnifiedPalette derives the material/action/selection roles; neutral Dark chrome
 * is independent of accent. ThemeMigrator retires legacy glassStyle values in
 * configs and profiles without changing the remaining preferences.
 */
public class ThemeDefinition {

    /** Factory default accent (formerly {@code themePrimaryColor}). OnePlus Red. */
    public static final int DEFAULT_ACCENT = 0xFFEB0029;

    /** Factory window tint: the unified Dark panel graphite, so existing users see no change. */
    public static final int DEFAULT_BACKGROUND = ThemePresets.BACKGROUND_GRAPHITE;

    /** Factory default panel-background density (maximum diffuse material). */
    public static final double DEFAULT_BACKGROUND_OPACITY = 1.0;

    /** Supported glass blur radius range, in GUI pixels (0 = no blur). */
    public static final int MIN_BLUR_RADIUS = 0, MAX_BLUR_RADIUS = 32;

    /**
     * Initializer blur radius, in GUI pixels. Before the Blur Radius slider
     * glass blurred at 28 GUI pixels scaled by Background Opacity, so 28
     * matches the initializer's full opacity; the release defaults pair 0.33
     * opacity with 9.
     */
    public static final int DEFAULT_BLUR_RADIUS = 28;

    /** Legacy persistence member; supported rendering and migrated preferences are DARK. */
    public ThemeMode mode = ThemeMode.DARK;

    /**
     * Corner radius in pixels ({@link ThemeRoundness#MIN_RADIUS}..
     * {@link ThemeRoundness#MAX_RADIUS}), the Corner Radius slider. Every
     * UI radius derives from it through {@link ThemeRoundness}. Replaced the
     * {@code roundness} family name, which {@link ThemeMigrator} converts.
     */
    public int cornerRadius = ThemeRoundness.DEFAULT_RADIUS;

    /**
     * Blur radius of glass surfaces in GUI pixels
     * ({@link #MIN_BLUR_RADIUS}..{@link #MAX_BLUR_RADIUS}), the Blur Radius
     * slider. Independent of {@link #backgroundOpacity}; {@link ThemeMigrator}
     * seeds it from the opacity for themes saved before it existed.
     */
    public int blurRadius = DEFAULT_BLUR_RADIUS;

    /**
     * Density (0..1) of the theme's panel/window background fill. Applied by
     * {@link ThemeResolver} to the {@code WINDOW_FILL} token's alpha channel
     * only, with the approved 68% maximum — this slider adjusts
     * translucency, not color.
     */
    public double backgroundOpacity = DEFAULT_BACKGROUND_OPACITY;

    /**
     * The single identity accent driving the whole theme — buttons,
     * switches, fills and HUD accents. Neutral Dark chrome and informational
     * text use separate roles. Either a {@link ThemePresets} value or a custom RGB.
     */
    public int accent = DEFAULT_ACCENT;

    /**
     * Window tint (RGB; alpha ignored) — the colour of every Aurora window and
     * its neutral material family. {@link #backgroundOpacity} still owns the
     * translucency. {@link #DEFAULT_BACKGROUND} reproduces the stock palette.
     */
    public int background = DEFAULT_BACKGROUND;

    public ThemeDefinition() {}

    /** A fresh definition carrying factory defaults. */
    public static ThemeDefinition defaults() {
        return new ThemeDefinition();
    }

    /**
     * Clamp/repair a persisted background opacity. Out-of-range values are
     * clamped to 0..1; NaN (hand-edited garbage) falls back to the factory
     * default. Total — never throws.
     */
    public static double normalizedOpacity(double v) {
        if (Double.isNaN(v)) return DEFAULT_BACKGROUND_OPACITY;
        if (v < 0d) return 0d;
        if (v > 1d) return 1d;
        return v;
    }

    /** Clamp a persisted blur radius into the supported range. Total — never throws. */
    public static int normalizedBlurRadius(int v) {
        return Math.max(MIN_BLUR_RADIUS, Math.min(MAX_BLUR_RADIUS, v));
    }

    /**
     * Null-safe copy: a null input yields factory defaults, and every
     * fragile field is normalized (mode → {@link ThemeMode#DARK}, radii
     * clamped, opacity → {@link #normalizedOpacity}), so callers can never
     * observe a half-invalid definition.
     */
    public static ThemeDefinition copyOf(ThemeDefinition other) {
        ThemeDefinition copy = new ThemeDefinition();
        if (other != null) {
            copy.mode = ThemeMode.DARK; // legacy LIGHT preferences resolve to the supported appearance
            copy.accent = other.accent;
            copy.background = 0xFF000000 | other.background;
            copy.cornerRadius = ThemeRoundness.clamp(other.cornerRadius);
            copy.blurRadius = normalizedBlurRadius(other.blurRadius);
            copy.backgroundOpacity = normalizedOpacity(other.backgroundOpacity);
        }
        return copy;
    }

    /** Instance copy helper. */
    public ThemeDefinition copy() {
        return copyOf(this);
    }
}
