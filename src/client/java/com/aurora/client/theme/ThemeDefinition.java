package com.aurora.client.theme;

/** Persisted accent, mode, corner family and background density.
 * UnifiedPalette derives the material/action/selection roles; neutral Dark chrome
 * is independent of accent. ThemeMigrator retires legacy glassStyle values in
 * configs and profiles without changing the remaining preferences.
 */
public class ThemeDefinition {

    /** Factory default accent (formerly {@code themePrimaryColor}). OnePlus Red. */
    public static final int DEFAULT_ACCENT = 0xFFEB0029;

    /** Factory default panel-background density (maximum diffuse material). */
    public static final double DEFAULT_BACKGROUND_OPACITY = 1.0;

    /** Legacy persistence member; supported rendering and migrated preferences are DARK. */
    public ThemeMode mode = ThemeMode.DARK;

    /**
     * Corner-roundness family the UI renders with (Stage 3's non-color
     * token). Resolved to concrete radii by {@link ThemeRoundness#radius()}
     * et al. and projected onto {@code AuroraTheme.RADIUS*}.
     */
    public ThemeRoundness roundness = ThemeRoundness.ROUND;

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

    /**
     * Null-safe copy: a null input yields factory defaults, and every
     * nullable/fragile field is normalized (null mode →
     * {@link ThemeMode#DARK}, null roundness → {@link ThemeRoundness#ROUND},
     * opacity → {@link #normalizedOpacity}), so callers can never observe
     * a half-invalid definition.
     */
    public static ThemeDefinition copyOf(ThemeDefinition other) {
        ThemeDefinition copy = new ThemeDefinition();
        if (other != null) {
            copy.mode = ThemeMode.DARK; // legacy LIGHT preferences resolve to the supported appearance
            copy.accent = other.accent;
            copy.roundness = other.roundness != null ? other.roundness : ThemeRoundness.ROUND;
            copy.backgroundOpacity = normalizedOpacity(other.backgroundOpacity);
        }
        return copy;
    }

    /** Instance copy helper. */
    public ThemeDefinition copy() {
        return copyOf(this);
    }
}
