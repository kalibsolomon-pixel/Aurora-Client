package com.aurora.client.theme;

/**
 * The single home of the theme's color derivation. Stage 1 kept a verbatim
 * port of the legacy {@code AuroraTheme.updateColors()} here; Stage 2
 * replaces that with the real COSMIC-style engine in {@link PaletteEngine} —
 * a pure function (accent + mode in, full token set out) that derives the
 * entire palette from a single accent color.
 *
 * <p>Two resolution paths exist:
 * <ul>
 *   <li><b>Accents enabled</b> (user theme on): every token is derived by
 *       {@link PaletteEngine#derive} from the definition's accent + mode.</li>
 *   <li><b>Accents disabled</b>: a fixed factory palette is used (unchanged
 *       from Stage 1, so the "theme off" look is stable).</li>
 * </ul>
 */
public final class ThemeResolver {

    private ThemeResolver() {}

    /**
     * Resolve a definition into concrete token colors.
     *
     * @param definition     the persisted theme definition; {@code null} is
     *                       treated as factory defaults
     * @param accentsEnabled whether the user's custom colors apply at all
     *                       ({@code false} = fixed factory palette)
     */
    public static ResolvedTheme resolve(ThemeDefinition definition, boolean accentsEnabled) {
        ThemeDefinition def = ThemeDefinition.copyOf(definition); // null-safe, fully normalized
        if (!accentsEnabled) {
            return factoryPalette(def);
        }

        // Single source of truth: one accent + one mode → the whole palette.
        int accent = PaletteEngine.normalizeAccent(def.accent);
        int[] c = PaletteEngine.derive(accent, def.mode);
        applyBackgroundOpacity(c, def.backgroundOpacity);
        return new ResolvedTheme(def, true, c);
    }

    /**
     * The fixed factory palette used when the user's custom colors are
     * disabled. Verbatim copy of the legacy "restore defaults" table
     * (including the former {@code HudBackgrounds} HUD constants) — see
     * the class javadoc for why this is intentionally not the derived
     * form of the default seeds.
     */
    public static ResolvedTheme factoryPalette() {
        return factoryPalette(ThemeDefinition.defaults());
    }

    /**
     * The fixed factory palette, remembering {@code source} as the
     * definition it was resolved from. The no-arg overload uses factory
     * defaults; {@link #resolve} passes the live definition so
     * {@link ThemeManager#sync()}'s dirty-check does not see a phantom
     * accent/mode change (and infinitely reload) when the theme is disabled
     * with a non-default accent.
     */
    private static ResolvedTheme factoryPalette(ThemeDefinition source) {
        int[] c = new int[ThemeToken.values().length];

        c[ThemeToken.ACCENT.ordinal()]                 = 0xFFEB0029;
        c[ThemeToken.ACCENT_HOVER.ordinal()]           = 0xFFFF2447;
        c[ThemeToken.ACCENT_PRESSED.ordinal()]         = 0xFF9E0016;
        c[ThemeToken.ACCENT_GRAD_TOP.ordinal()]        = 0xFFFF3B58;
        c[ThemeToken.ACCENT_GRAD_TOP_HOVER.ordinal()]  = 0xFFFF5C74;
        c[ThemeToken.ACCENT_GRAD_BOT.ordinal()]        = 0xFFEB0029;

        c[ThemeToken.SECONDARY_ACCENT.ordinal()]       = 0xFFEB0029;
        c[ThemeToken.BORDER_ACCENT_HOVER.ordinal()]    = 0xFFFF2447;

        c[ThemeToken.BACKGROUND.ordinal()]             = 0xFF000000;
        c[ThemeToken.SURFACE.ordinal()]                = 0xFF1A1A1A;
        c[ThemeToken.SURFACE_VARIANT.ordinal()]        = 0xFF262626;
        c[ThemeToken.SURFACE_INSET.ordinal()]          = 0xFF121212;
        c[ThemeToken.WINDOW_FILL.ordinal()]            = 0xFF0D0D0D;

        c[ThemeToken.TILE_FILL.ordinal()]              = 0x22FFFFFF;
        c[ThemeToken.TILE_FILL_HOVER.ordinal()]        = 0x18FFFFFF;
        c[ThemeToken.TILE_GRAD_TOP.ordinal()]          = 0xFF2A2A2A;
        c[ThemeToken.TILE_GRAD_BOT.ordinal()]          = 0xFF2A2A2A;
        c[ThemeToken.TILE_OUTLINE.ordinal()]           = 0x55FFFFFF;
        c[ThemeToken.TILE_OUTLINE_STRONG.ordinal()]    = 0xAAFFFFFF;

        c[ThemeToken.WINDOW_OUTLINE.ordinal()]         = 0x66FFFFFF;
        c[ThemeToken.WINDOW_HIGHLIGHT.ordinal()]       = 0x48FFFFFF;

        c[ThemeToken.BACKDROP_TOP_DARK.ordinal()]      = 0xFF030610;
        c[ThemeToken.BACKDROP_TOP_LIGHT.ordinal()]     = 0xFF0C1A36;
        c[ThemeToken.BACKDROP_BOT_DARK.ordinal()]      = 0xFF000000;
        c[ThemeToken.BACKDROP_BOT_LIGHT.ordinal()]     = 0xFF050912;

        c[ThemeToken.ON_BACKGROUND.ordinal()]          = 0xFFFFFFFF;
        c[ThemeToken.ON_BACKGROUND_SECONDARY.ordinal()] = 0x99EBEBF5;
        c[ThemeToken.ON_BACKGROUND_MUTED.ordinal()]    = 0x4DEBEBF5;
        c[ThemeToken.ON_BACKGROUND_FAINT.ordinal()]    = 0x2EEBEBF5;
        c[ThemeToken.ON_SURFACE.ordinal()]             = 0xFFFFFFFF;
        c[ThemeToken.ON_ACCENT.ordinal()]              = 0xFFFFFFFF;

        c[ThemeToken.BORDER.ordinal()]                 = 0xFF38383A;
        c[ThemeToken.BORDER_HOVER.ordinal()]           = 0xFF3E3E3E;

        // Semantic status (fixed hues, dark-mode reference values)
        c[ThemeToken.SEMANTIC_ERROR.ordinal()]         = 0xFFFF453A;
        c[ThemeToken.SEMANTIC_SUCCESS.ordinal()]       = 0xFF30D158;
        c[ThemeToken.SEMANTIC_WARNING.ordinal()]       = 0xFFFFC107;

        c[ThemeToken.OVERLAY_DIM.ordinal()]            = 0x990A0B0D;
        c[ThemeToken.ON_OVERLAY.ordinal()]             = 0xFFFFFFFF;

        c[ThemeToken.HUD_BACKDROP_TOP.ordinal()]       = 0xFF050810;
        c[ThemeToken.HUD_BACKDROP_BOT.ordinal()]       = 0xFF0A4DBF;

        applyBackgroundOpacity(c, source.backgroundOpacity);

        return new ResolvedTheme(source, false, c);
    }

    /**
     * Re-stamp the panel/window fill's <b>alpha channel only</b> to the
     * user's background opacity — hue and lightness stay untouched, so the
     * opacity slider adjusts translucency, never color. Opacity is expected
     * to arrive already normalized ({@link ThemeDefinition#copyOf} does
     * that); this method clamps defensively anyway.
     */
    private static void applyBackgroundOpacity(int[] colors, double opacity) {
        if (Double.isNaN(opacity)) opacity = ThemeDefinition.DEFAULT_BACKGROUND_OPACITY;
        int alpha = Math.round((float) Math.max(0d, Math.min(1d, opacity)) * 255f);
        int fill = colors[ThemeToken.WINDOW_FILL.ordinal()];
        colors[ThemeToken.WINDOW_FILL.ordinal()] = (alpha << 24) | (fill & 0x00FFFFFF);
    }
}
