package com.aurora.client.theme;

/** Resolves the approved Launcher-derived material roles for every UI consumer.
 * Dark neutrals are independent of accent; Light retains the Client companion.
 * Legacy tokens project the same roles through AuroraTheme. Saved identity and
 * customization remain intact, including while the fixed factory theme is active.
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
        UnifiedPalette.populate(c, accent, def.mode);
        UnifiedPalette.tintNeutrals(c, def.background);
        applyUnifiedRoles(c);
        applyBackgroundOpacity(c, def.backgroundOpacity);
        return new ResolvedTheme(def, true, c);
    }

    /**
     * The fixed factory palette used when the user's custom colors are
     * disabled. The legacy seed table preserves fixed identity colors;
     * unified roles then project the same neutral Red/Dark chrome as the
     * enabled theme without changing the user's stored definition.
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

        UnifiedPalette.populate(c, ThemeDefinition.DEFAULT_ACCENT, ThemeMode.DARK);
        applyUnifiedRoles(c);
        applyBackgroundOpacity(c, source.backgroundOpacity);

        return new ResolvedTheme(source, false, c);
    }

    /** Project the unified roles through every existing token/facade reader. */
    private static void applyUnifiedRoles(int[] c) {
        c[ThemeToken.SECONDARY_ACCENT.ordinal()] = c[ThemeToken.ACCENT_MARKER.ordinal()];
        c[ThemeToken.BORDER_ACCENT_HOVER.ordinal()] = c[ThemeToken.ACCENT_MARKER.ordinal()];
        c[ThemeToken.BACKGROUND.ordinal()] = c[ThemeToken.MATERIAL_CANVAS.ordinal()];
        c[ThemeToken.SURFACE.ordinal()] = c[ThemeToken.MATERIAL_PANEL.ordinal()];
        c[ThemeToken.WINDOW_FILL.ordinal()] = c[ThemeToken.MATERIAL_PANEL.ordinal()];
        c[ThemeToken.SURFACE_VARIANT.ordinal()] = c[ThemeToken.MATERIAL_RAISED.ordinal()];
        c[ThemeToken.SURFACE_INSET.ordinal()] = c[ThemeToken.MATERIAL_SUNKEN.ordinal()];
        c[ThemeToken.BORDER.ordinal()] = c[ThemeToken.MATERIAL_BORDER.ordinal()];
        c[ThemeToken.WINDOW_OUTLINE.ordinal()] = c[ThemeToken.MATERIAL_BORDER.ordinal()];
        c[ThemeToken.BORDER_HOVER.ordinal()] = c[ThemeToken.MATERIAL_BORDER_STRONG.ordinal()];
        c[ThemeToken.WINDOW_HIGHLIGHT.ordinal()] = c[ThemeToken.MATERIAL_BORDER_STRONG.ordinal()];
        c[ThemeToken.ON_BACKGROUND.ordinal()] = c[ThemeToken.MATERIAL_TEXT.ordinal()];
        c[ThemeToken.ON_SURFACE.ordinal()] = c[ThemeToken.MATERIAL_TEXT.ordinal()];
        c[ThemeToken.ON_BACKGROUND_SECONDARY.ordinal()] = c[ThemeToken.MATERIAL_TEXT_SECONDARY.ordinal()];
        c[ThemeToken.ON_BACKGROUND_MUTED.ordinal()] = c[ThemeToken.MATERIAL_TEXT_MUTED.ordinal()];
        c[ThemeToken.ON_BACKGROUND_FAINT.ordinal()] = c[ThemeToken.MATERIAL_TEXT_MUTED.ordinal()];
        c[ThemeToken.ON_ACCENT.ordinal()] = c[ThemeToken.ON_ACTION.ordinal()];
        c[ThemeToken.ACCENT_HOVER.ordinal()] = c[ThemeToken.ACTION_HOVER.ordinal()];
        c[ThemeToken.ACCENT_PRESSED.ordinal()] = c[ThemeToken.ACTION_PRESSED.ordinal()];
        c[ThemeToken.ACCENT_GRAD_TOP.ordinal()] = c[ThemeToken.ACTION_STRONG.ordinal()];
        c[ThemeToken.ACCENT_GRAD_TOP_HOVER.ordinal()] = c[ThemeToken.ACTION_HOVER.ordinal()];
        c[ThemeToken.ACCENT_GRAD_BOT.ordinal()] = c[ThemeToken.ACTION_STRONG.ordinal()];
        c[ThemeToken.TILE_FILL.ordinal()] = c[ThemeToken.MATERIAL_RAISED.ordinal()];
        c[ThemeToken.TILE_FILL_HOVER.ordinal()] = c[ThemeToken.MATERIAL_HOVER.ordinal()];
        c[ThemeToken.TILE_GRAD_TOP.ordinal()] = c[ThemeToken.MATERIAL_RAISED.ordinal()];
        c[ThemeToken.TILE_GRAD_BOT.ordinal()] = c[ThemeToken.MATERIAL_RAISED.ordinal()];
        c[ThemeToken.TILE_OUTLINE.ordinal()] = c[ThemeToken.MATERIAL_BORDER.ordinal()];
        c[ThemeToken.TILE_OUTLINE_STRONG.ordinal()] = c[ThemeToken.MATERIAL_BORDER_STRONG.ordinal()];
        // World-overlay backing remains dark in both UI modes so fixed HUD
        // status hues and explicit player-selected text colors retain their context.
        c[ThemeToken.HUD_BACKDROP_TOP.ordinal()] = 0xff16171d;
        c[ThemeToken.HUD_BACKDROP_BOT.ordinal()] = 0xff1d1f27;
        c[ThemeToken.BACKDROP_TOP_DARK.ordinal()] = c[ThemeToken.MATERIAL_CANVAS.ordinal()];
        c[ThemeToken.BACKDROP_TOP_LIGHT.ordinal()] = c[ThemeToken.MATERIAL_PANEL.ordinal()];
        c[ThemeToken.BACKDROP_BOT_DARK.ordinal()] = c[ThemeToken.MATERIAL_SUNKEN.ordinal()];
        c[ThemeToken.BACKDROP_BOT_LIGHT.ordinal()] = c[ThemeToken.MATERIAL_CANVAS.ordinal()];
    }

    /** Single window-density mapping; saved preferences remain unchanged. */
    public static int backgroundAlpha(double opacity) {
        return Math.round((float)ThemeDefinition.normalizedOpacity(opacity) * 255f * .68f);
    }

    private static void applyBackgroundOpacity(int[] colors, double opacity) {
        int alpha = backgroundAlpha(opacity);
        int fill = colors[ThemeToken.WINDOW_FILL.ordinal()];
        colors[ThemeToken.WINDOW_FILL.ordinal()] = (alpha << 24) | (fill & 0x00FFFFFF);
    }
}
