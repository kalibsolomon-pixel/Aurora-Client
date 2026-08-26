package com.aurora.client.theme;

import com.aurora.client.util.AuroraTheme;

/**
 * An immutable, fully resolved set of token colors — the cached result of
 * {@link ThemeResolver#resolve}. Colors are computed once per theme load or
 * change and stored in an ordinal-indexed array, so {@link #color} is a
 * single array access with zero per-frame cost.
 *
 * <p>It also remembers the (normalized) {@link ThemeDefinition} it was
 * resolved from, so {@link ThemeManager#sync()} can dirty-check the live
 * config against the last-applied source without re-resolving.
 *
 * <p><b>Facade projection.</b> {@link #project()} publishes these values
 * onto the legacy mutable {@link AuroraTheme} statics. That keeps the ~24
 * screens still reading {@code AuroraTheme.X} rendering pixel-identically
 * while the token system is the single source of truth underneath — the
 * projection is written only here, never recomputed by consumers. Stages
 * 2-4 migrate readers off the legacy names onto {@link ThemeManager#color}
 * directly.
 */
public final class ResolvedTheme {

    private final ThemeDefinition source;
    private final boolean accentsEnabled;
    private final int[] colors;

    ResolvedTheme(ThemeDefinition source, boolean accentsEnabled, int[] colors) {
        this.source = source;
        this.accentsEnabled = accentsEnabled;
        this.colors = colors;
    }

    /** The resolved color for a token (ARGB). Never recomputed — cached array read. */
    public int color(ThemeToken token) {
        return colors[token.ordinal()];
    }

    /** The normalized definition this theme was resolved from. */
    public ThemeMode mode() { return source.mode; }

    /** Whether the user's custom colors were applied (vs. factory palette). */
    public boolean accentsEnabled() { return accentsEnabled; }

    /** Source accent seed. */
    public int accent() { return source.accent; }

    /** Corner-roundness family this theme renders with (never null — normalized). */
    public ThemeRoundness roundness() { return source.roundness; }

    /** Panel/window background alpha, 0..1 (always normalized). */
    public double backgroundOpacity() { return source.backgroundOpacity; }

    /**
     * Publish the resolved values onto the legacy {@link AuroraTheme}
     * statics — the facade that existing render code reads. This is the
     * only writer of those statics.
     */
    public void project() {
        // Accent family
        AuroraTheme.IOS_BLUE                = color(ThemeToken.ACCENT);
        AuroraTheme.IOS_BLUE_HOVER          = color(ThemeToken.ACCENT_HOVER);
        AuroraTheme.IOS_BLUE_PRESSED        = color(ThemeToken.ACCENT_PRESSED);
        AuroraTheme.IOS_BLUE_GRAD_TOP       = color(ThemeToken.ACCENT_GRAD_TOP);
        AuroraTheme.IOS_BLUE_GRAD_TOP_HOVER = color(ThemeToken.ACCENT_GRAD_TOP_HOVER);
        AuroraTheme.IOS_BLUE_GRAD_BOT       = color(ThemeToken.ACCENT_GRAD_BOT);
        AuroraTheme.IOS_BLUE_GRAD_BOT_HOVER = color(ThemeToken.ACCENT_HOVER);
        AuroraTheme.PANEL_ON                = color(ThemeToken.ACCENT);
        AuroraTheme.PANEL_ON_HOVER          = color(ThemeToken.ACCENT_HOVER);
        AuroraTheme.GEM_ON_TOP              = color(ThemeToken.ACCENT);
        AuroraTheme.GEM_ON_BOT              = color(ThemeToken.ACCENT_PRESSED);

        // Neutral chrome (derived from the neutral seed)
        AuroraTheme.MODULE_ACCENT_ON        = color(ThemeToken.SECONDARY_ACCENT);
        AuroraTheme.BORDER_ON               = color(ThemeToken.SECONDARY_ACCENT);
        AuroraTheme.BORDER_ON_HOVER         = color(ThemeToken.BORDER_ACCENT_HOVER);
        AuroraTheme.ACCENT_BLUE             = color(ThemeToken.SECONDARY_ACCENT);
        AuroraTheme.ACCENT_GREEN            = color(ThemeToken.SECONDARY_ACCENT);
        AuroraTheme.ACCENT_YELLOW           = color(ThemeToken.SECONDARY_ACCENT);
        AuroraTheme.TEXT_ACCENT             = color(ThemeToken.SECONDARY_ACCENT);

        // Surfaces
        AuroraTheme.IOS_SYSTEM_BG           = color(ThemeToken.BACKGROUND);
        AuroraTheme.IOS_SECONDARY_BG        = color(ThemeToken.SURFACE);
        AuroraTheme.IOS_TERTIARY_BG         = color(ThemeToken.SURFACE_VARIANT);
        AuroraTheme.PANEL_OFF               = color(ThemeToken.SURFACE);
        AuroraTheme.PANEL_OFF_HOVER         = color(ThemeToken.SURFACE_VARIANT);
        AuroraTheme.PANEL_INSET             = color(ThemeToken.SURFACE_INSET);
        AuroraTheme.GEM_OFF_TOP             = color(ThemeToken.SURFACE);
        AuroraTheme.GEM_OFF_BOT             = color(ThemeToken.SURFACE);
        AuroraTheme.WINDOW_FILL             = color(ThemeToken.WINDOW_FILL);
        AuroraTheme.WINDOW_OUTLINE          = color(ThemeToken.WINDOW_OUTLINE);
        AuroraTheme.WINDOW_HIGHLIGHT        = color(ThemeToken.WINDOW_HIGHLIGHT);

        // Tiles
        AuroraTheme.TILE_FILL               = color(ThemeToken.TILE_FILL);
        AuroraTheme.TILE_FILL_HOVER         = color(ThemeToken.TILE_FILL_HOVER);
        AuroraTheme.TILE_GRAD_TOP           = color(ThemeToken.TILE_GRAD_TOP);
        AuroraTheme.TILE_GRAD_BOT           = color(ThemeToken.TILE_GRAD_BOT);
        AuroraTheme.TILE_OUTLINE_OFF        = color(ThemeToken.TILE_OUTLINE);
        AuroraTheme.TILE_OUTLINE_ON         = color(ThemeToken.TILE_OUTLINE_STRONG);

        // Backdrop
        AuroraTheme.BACKDROP_TOP_DARK       = color(ThemeToken.BACKDROP_TOP_DARK);
        AuroraTheme.BACKDROP_TOP_LIGHT      = color(ThemeToken.BACKDROP_TOP_LIGHT);
        AuroraTheme.BACKDROP_BOT_DARK       = color(ThemeToken.BACKDROP_BOT_DARK);
        AuroraTheme.BACKDROP_BOT_LIGHT      = color(ThemeToken.BACKDROP_BOT_LIGHT);

        // Text / foreground
        AuroraTheme.IOS_LABEL               = color(ThemeToken.ON_BACKGROUND);
        AuroraTheme.IOS_SECONDARY_LABEL     = color(ThemeToken.ON_BACKGROUND_SECONDARY);
        AuroraTheme.IOS_TERTIARY_LABEL      = color(ThemeToken.ON_BACKGROUND_MUTED);
        AuroraTheme.IOS_QUATERNARY_LABEL    = color(ThemeToken.ON_BACKGROUND_FAINT);
        AuroraTheme.TEXT_PRIMARY            = color(ThemeToken.ON_BACKGROUND);
        AuroraTheme.TEXT_SECONDARY          = color(ThemeToken.ON_BACKGROUND_SECONDARY);
        AuroraTheme.TEXT_DIM                = color(ThemeToken.ON_BACKGROUND_MUTED);
        AuroraTheme.ON_SURFACE              = color(ThemeToken.ON_SURFACE);
        AuroraTheme.ON_ACCENT               = color(ThemeToken.ON_ACCENT);

        // Neutral borders
        AuroraTheme.BORDER_OFF              = color(ThemeToken.BORDER);
        AuroraTheme.BORDER_OFF_HOVER        = color(ThemeToken.BORDER_HOVER);

        // Semantic status + overlay dim
        AuroraTheme.SEMANTIC_ERROR          = color(ThemeToken.SEMANTIC_ERROR);
        AuroraTheme.SEMANTIC_SUCCESS        = color(ThemeToken.SEMANTIC_SUCCESS);
        AuroraTheme.SEMANTIC_WARNING        = color(ThemeToken.SEMANTIC_WARNING);
        AuroraTheme.OVERLAY_DIM             = color(ThemeToken.OVERLAY_DIM);
        AuroraTheme.ON_OVERLAY              = color(ThemeToken.ON_OVERLAY);

        // Sizes (Stage 3): corner radii project from the roundness token the
        // same way the colors above project from the color tokens — this is
        // the only writer of these statics. KNOB_RADIUS is deliberately not
        // projected: knobs/thumbs stay circular in every roundness mode.
        AuroraTheme.RADIUS                  = source.roundness.radius();
        AuroraTheme.RADIUS_LARGE            = source.roundness.radiusLarge();
        AuroraTheme.RADIUS_SMALL            = source.roundness.radiusSmall();
    }
}
