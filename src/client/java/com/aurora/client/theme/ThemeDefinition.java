package com.aurora.client.theme;

/**
 * A persisted, user-owned theme definition — the single structured source
 * of truth for Aurora's colors, modeled on COSMIC's theme files.
 *
 * <p>Stage 2 makes this intentionally hold <b>a single identity seed</b>:
 * the {@link #accent} color. Everything else (backgrounds, surfaces,
 * neutral chrome, text-on-X colors) is <b>derived algorithmically</b> from
 * that one value by {@link PaletteEngine}, so a user never hand-picks more
 * than one color to get a coherent, legible palette in either brightness
 * mode. The former {@code neutralSeed} (Stage 1's secondary seed) is gone —
 * neutrals are now tinted from the accent hue, which is what keeps the
 * whole palette looking like one family.
 *
 * <p>Migration: {@code themePrimaryColor} became {@link #accent}
 * (see {@link ThemeMigrator}); the legacy {@code themeSecondaryColor} is
 * dropped because it has no independent role anymore. Serialized by the
 * mod's existing GSON config — no new format or library.
 */
public class ThemeDefinition {

    /** Factory default accent (formerly {@code themePrimaryColor}). OnePlus Red. */
    public static final int DEFAULT_ACCENT = 0xFFEB0029;

    /** Factory default panel-background alpha (fully opaque). */
    public static final double DEFAULT_BACKGROUND_OPACITY = 1.0;

    /** Brightness mode the theme renders in. */
    public ThemeMode mode = ThemeMode.DARK;

    /**
     * Corner-roundness family the UI renders with (Stage 3's non-color
     * token). Resolved to concrete radii by {@link ThemeRoundness#radius()}
     * et al. and projected onto {@code AuroraTheme.RADIUS*}.
     */
    public ThemeRoundness roundness = ThemeRoundness.ROUND;

    /**
     * Alpha (0..1) of the theme's panel/window background fill. Applied by
     * {@link ThemeResolver} to the {@code WINDOW_FILL} token's alpha channel
     * only — hue and lightness stay derived, so this slider adjusts
     * translucency, not color.
     */
    public double backgroundOpacity = DEFAULT_BACKGROUND_OPACITY;

    /**
     * The single identity accent driving the whole theme — buttons,
     * switches, fills, HUD glow, and (via derivation) every background and
     * text color. Either a {@link ThemePresets} value or a custom RGB.
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
            copy.mode = other.mode != null ? other.mode : ThemeMode.DARK;
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
