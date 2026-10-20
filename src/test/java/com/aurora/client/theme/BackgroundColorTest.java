package com.aurora.client.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BackgroundColorTest {
    private static ResolvedTheme resolve(int background) {
        ThemeDefinition def = ThemeDefinition.defaults();
        def.background = background;
        def.backgroundOpacity = .5;
        return ThemeResolver.resolve(def, true);
    }

    private static double lightness(int c) {
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        return (Math.max(r, Math.max(g, b)) + Math.min(r, Math.min(g, b))) / 510d;
    }

    @Test void defaultBackgroundIsTheStockGraphiteAndChangesNothing() {
        assertEquals(ThemePresets.BACKGROUND_GRAPHITE, ThemeDefinition.defaults().background);
        ResolvedTheme stock = resolve(ThemeDefinition.DEFAULT_BACKGROUND);
        assertEquals(ThemeDefinition.DEFAULT_BACKGROUND & 0xFFFFFF, stock.color(ThemeToken.WINDOW_FILL) & 0xFFFFFF,
                "the default tint is exactly the existing window fill");
        int[] before = new int[ThemeToken.values().length];
        int accent = PaletteEngine.normalizeAccent(ThemeDefinition.DEFAULT_ACCENT);
        int[] c = PaletteEngine.derive(accent, ThemeMode.DARK);
        UnifiedPalette.populate(c, accent, ThemeMode.DARK);
        System.arraycopy(c, 0, before, 0, c.length);
        UnifiedPalette.tintNeutrals(c, ThemeDefinition.DEFAULT_BACKGROUND);
        assertArrayEquals(before, c, "graphite is an exact no-op");
    }

    @Test void presetTintsTheWindowAndKeepsOpacityAndNeutralSteps() {
        int blue = ThemePresets.BACKGROUNDS.get(1).argb;
        ResolvedTheme tinted = resolve(blue);
        ResolvedTheme stock = resolve(ThemeDefinition.DEFAULT_BACKGROUND);
        int fill = tinted.color(ThemeToken.WINDOW_FILL);
        assertEquals(blue & 0xFFFFFF, fill & 0xFFFFFF, "the window takes the chosen tone");
        assertEquals(stock.color(ThemeToken.WINDOW_FILL) >>> 24, fill >>> 24, "Background Opacity still owns alpha");
        assertTrue(lightness(tinted.color(ThemeToken.SURFACE_VARIANT)) > lightness(fill), "raised stays above the panel");
        assertTrue(lightness(tinted.color(ThemeToken.SURFACE_INSET)) < lightness(fill), "sunken stays below the panel");
        assertEquals(stock.color(ThemeToken.ON_BACKGROUND), tinted.color(ThemeToken.ON_BACKGROUND), "text roles are untouched");
        assertEquals(stock.color(ThemeToken.HUD_BACKDROP_TOP), tinted.color(ThemeToken.HUD_BACKDROP_TOP), "HUD backing stays neutral");
    }

    @Test void lightCustomColoursAreDeepenedForReadableText() {
        int fill = resolve(0xFFFFF59D).color(ThemeToken.WINDOW_FILL);
        assertTrue(lightness(fill) <= UnifiedPalette.MAX_PANEL_LIGHTNESS + .01, "panel lightness capped");
        assertTrue(UnifiedPalette.contrast(fill | 0xFF000000, 0xFFEDF1FB) >= 6.9, "primary text stays readable");
        int cyan = resolve(0xFF00FFFF).color(ThemeToken.WINDOW_FILL);
        assertTrue(UnifiedPalette.contrast(cyan | 0xFF000000, 0xFFEDF1FB) >= 6.9, "luminous hues are deepened too");
    }

    @Test void everyBackgroundPresetKeepsPrimaryTextReadableAndIsDistinct() {
        assertEquals(9, ThemePresets.BACKGROUNDS.size(), "nine presets + Custom fill the two-row peer grid");
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (var e : ThemePresets.BACKGROUNDS) {
            assertTrue(seen.add(e.argb & 0xFFFFFF), e.name);
            int fill = resolve(e.argb).color(ThemeToken.WINDOW_FILL) | 0xFF000000;
            assertTrue(UnifiedPalette.contrast(fill, 0xFFEDF1FB) >= 7, e.name);
        }
    }

    @Test void copiesCarryTheBackgroundAsOpaqueRgb() {
        ThemeDefinition def = ThemeDefinition.defaults();
        def.background = 0x00123456;
        assertEquals(0xFF123456, ThemeDefinition.copyOf(def).background);
    }
}
