package com.aurora.client.theme;

/**
 * The semantic color tokens of Aurora's UI — named by <b>role</b>, not by
 * value, following COSMIC's token model. Stage 1's set is the minimum that
 * covers every color the legacy {@code AuroraTheme.updateColors()} engine
 * computed; Stages 2-4 extend it as the derivation engine and component
 * framework grow.
 *
 * <p>Token lookup is a single array access on a cached {@link ResolvedTheme}
 * (see {@link ThemeManager#color}) — nothing is recomputed per frame.
 *
 * <p>Naming follows the Material/COSMIC "on-" convention: {@code ON_*}
 * tokens are the foreground/text color appropriate on top of the surface
 * token they derive their name from.
 */
public enum ThemeToken {

    // ---- Accent (identity color family) ----

    /** The identity accent. Solid fills: switch-ON, slider fill, active chip. */
    ACCENT,
    /** Accent with hover feedback (accent lifted ~15% toward white). */
    ACCENT_HOVER,
    /** Accent with pressed feedback (accent deepened ~30% toward black). */
    ACCENT_PRESSED,
    /** Top stop of the accent's vertical gradient (lit-from-above). */
    ACCENT_GRAD_TOP,
    /** Hover-state top stop of the accent gradient. */
    ACCENT_GRAD_TOP_HOVER,
    /** Bottom stop of the accent's vertical gradient (canonical accent). */
    ACCENT_GRAD_BOT,

    // ---- Neutral chrome (derived from the neutral seed) ----

    /** Secondary accent — borders/icons/text-tint family, from the neutral seed. */
    SECONDARY_ACCENT,
    /** Hover variant of the accent border. */
    BORDER_ACCENT_HOVER,

    // ---- Surfaces ----

    /** Page-level background. */
    BACKGROUND,
    /** Card / grouped-row surface sitting on the background. */
    SURFACE,
    /** Raised / hover variant of a surface. */
    SURFACE_VARIANT,
    /** Inset (recessed) surface inside a surface. */
    SURFACE_INSET,
    /** Opaque fill of the main content window. */
    WINDOW_FILL,
    /** Translucent outline around the content window. */
    WINDOW_OUTLINE,
    /** Subtle top-edge highlight on dark chrome. */
    WINDOW_HIGHLIGHT,

    // ---- Tiles (feature cards inside the window) ----

    /** Translucent tile fill over the window. */
    TILE_FILL,
    /** Hover overlay on top of {@link #TILE_FILL}. */
    TILE_FILL_HOVER,
    /** Tile gradient — top stop. */
    TILE_GRAD_TOP,
    /** Tile gradient — bottom stop. */
    TILE_GRAD_BOT,
    /** Translucent tile outline, resting state. */
    TILE_OUTLINE,
    /** Translucent tile outline, active state. */
    TILE_OUTLINE_STRONG,

    // ---- Backdrop (page gradient behind everything) ----

    /** Backdrop top color, dark breathing phase. */
    BACKDROP_TOP_DARK,
    /** Backdrop top color, light breathing phase. */
    BACKDROP_TOP_LIGHT,
    /** Backdrop bottom color, dark breathing phase. */
    BACKDROP_BOT_DARK,
    /** Backdrop bottom color, light breathing phase. */
    BACKDROP_BOT_LIGHT,

    // ---- Text / foreground ----

    /** Primary text on {@link #BACKGROUND} / {@link #SURFACE}. */
    ON_BACKGROUND,
    /** Secondary text — descriptions, captions. */
    ON_BACKGROUND_SECONDARY,
    /** Muted text — placeholders, inactive labels. */
    ON_BACKGROUND_MUTED,
    /** Faint text — barely-there hints. */
    ON_BACKGROUND_FAINT,

    /** Primary text on {@link #SURFACE} / cards. Contrast-checked against {@link #SURFACE}. */
    ON_SURFACE,

    /** Text drawn on top of the {@link #ACCENT} fill (filled buttons, toggles). */
    ON_ACCENT,

    // ---- Neutral borders ----

    /** Hairline separator / resting border on surfaces. */
    BORDER,
    /** Hover variant of {@link #BORDER}. */
    BORDER_HOVER,

    // ---- Semantic status (fixed hues — deliberately NOT accent-derived) ----

    /**
     * Destructive / error — delete affordances, failure text, disabled-state
     * outlines. Fixed red hue; lightness is mode-locked for legibility.
     */
    SEMANTIC_ERROR,
    /** Success / confirmation — additions, enabled-state indicators. Fixed green hue. */
    SEMANTIC_SUCCESS,
    /** Caution / attention — locked states, drag feedback, warnings. Fixed yellow hue. */
    SEMANTIC_WARNING,

    // ---- Overlays ----

    /**
     * Full-screen dim behind modal/list screens. Deliberately dark in BOTH
     * modes — its job is to dim the world behind the screen so the themed
     * surfaces on top stay readable. Alpha-scaled variants (the subtler
     * dims some screens use) derive from this RGB via
     * {@link ThemeManager#withAlpha}.
     */
    OVERLAY_DIM,

    /**
     * Text on top of {@link #OVERLAY_DIM}. The overlay is deliberately dark in
     * both modes, so this token is deliberately LIGHT in both modes — unlike
     * {@link #ON_BACKGROUND}, which flips with the mode. Alpha-scaled secondary
     * variants derive via {@link ThemeManager#withAlpha}.
     */
    ON_OVERLAY,

    // ---- HUD module panels (AURORA background mode) ----

    /** HUD panel gradient — top stop. */
    HUD_BACKDROP_TOP,
    /** HUD panel gradient — bottom stop. */
    HUD_BACKDROP_BOT
}
