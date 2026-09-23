package com.aurora.client.theme;

import org.junit.jupiter.api.Test;

import static com.aurora.client.theme.ContrastFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproducible Phase D-0 failure evidence. These assertions intentionally
 * describe the pre-fix rendering that D-2 through D-6 will change; they are
 * kept separate from D-1's permanent math and derivation contracts.
 */
class D0CharacterizationTest {

    @Test
    void defaultRedOnAccentIsAboutFourPointThree() {
        int[] p = PaletteEngine.derive(DEFAULT_RED, ThemeMode.DARK);
        double ratio = PaletteEngine.contrastRatio(
                p[ThemeToken.ON_ACCENT.ordinal()], p[ThemeToken.ACCENT.ordinal()]);
        assertEquals(4.30, ratio, PIN_TOLERANCE);
        assertTrue(ratio < ContrastDerivations.ESSENTIAL_TEXT_RATIO);
    }

    @Test
    void saturatedRedPressedVariantFallsToAboutTwoPointSixSeven() {
        int[] p = PaletteEngine.derive(SATURATED_RED, ThemeMode.DARK);
        double ratio = PaletteEngine.contrastRatio(
                p[ThemeToken.ON_ACCENT.ordinal()], p[ThemeToken.ACCENT_PRESSED.ordinal()]);
        assertEquals(2.67, ratio, PIN_TOLERANCE);
        assertTrue(ratio < ContrastDerivations.ESSENTIAL_TEXT_RATIO);
    }

    @Test
    void saturatedRedStainOverNearBlackFallsToAboutOnePointNineFive() {
        int[] p = PaletteEngine.derive(SATURATED_RED, ThemeMode.DARK);
        int stain = (ContrastDerivations.STAINED_VISIBILITY_FLOOR_ALPHA << 24)
                | (p[ThemeToken.ACCENT.ordinal()] & 0x00FFFFFF);
        int backing = PaletteEngine.composite(stain, NEAR_BLACK_BACKDROP);
        double ratio = PaletteEngine.contrastRatio(
                p[ThemeToken.ON_ACCENT.ordinal()], backing);
        assertEquals(1.95, ratio, LOOSE_PIN_TOLERANCE);
        assertTrue(ratio < ContrastDerivations.ESSENTIAL_TEXT_RATIO);
    }

    @Test
    void primaryTextAtTenPercentOverNearWhiteFallsToAboutOnePointTwoOne() {
        ResolvedTheme theme = resolved(DEFAULT_RED, ThemeMode.DARK, 0.10);
        int backing = PaletteEngine.composite(
                theme.color(ThemeToken.WINDOW_FILL), NEAR_WHITE_BACKDROP);
        double ratio = PaletteEngine.contrastRatio(
                theme.color(ThemeToken.ON_BACKGROUND), backing);
        assertEquals(1.21, ratio, LOOSE_PIN_TOLERANCE);
        assertTrue(ratio < ContrastDerivations.ESSENTIAL_TEXT_RATIO);
    }

    @Test
    void lightMutedTextOnOpaqueSurfaceIsAboutOnePointNineSix() {
        ResolvedTheme theme = resolved(DEFAULT_RED, ThemeMode.LIGHT, 1.0);
        int backing = theme.color(ThemeToken.SURFACE);
        int visibleText = PaletteEngine.composite(
                theme.color(ThemeToken.ON_BACKGROUND_MUTED), backing);
        double ratio = PaletteEngine.contrastRatio(visibleText, backing);
        assertEquals(1.96, ratio, LOOSE_PIN_TOLERANCE);
        assertTrue(ratio < ContrastDerivations.SUPPLEMENTAL_MUTED_RATIO);
    }

    @Test
    void lightWarningIndicatorIsAboutTwoPointZeroTwo() {
        ResolvedTheme theme = resolved(DEFAULT_RED, ThemeMode.LIGHT, 1.0);
        double ratio = PaletteEngine.contrastRatio(
                theme.color(ThemeToken.SEMANTIC_WARNING),
                theme.color(ThemeToken.SURFACE));
        assertEquals(2.02, ratio, LOOSE_PIN_TOLERANCE);
        assertTrue(ratio < ContrastDerivations.NON_TEXT_INDICATOR_RATIO);
    }

    @Test
    void sameAccentFocusHairlineCanCollapseToOneToOne() {
        int[] p = PaletteEngine.derive(NEAR_WHITE, ThemeMode.LIGHT);
        int stain = (ContrastDerivations.STAINED_VISIBILITY_FLOOR_ALPHA << 24)
                | (p[ThemeToken.ACCENT.ordinal()] & 0x00FFFFFF);
        int backing = PaletteEngine.composite(stain, NEAR_WHITE_BACKDROP);
        int hairline = (0x99 << 24) | (p[ThemeToken.ACCENT.ordinal()] & 0x00FFFFFF);
        int rendered = PaletteEngine.composite(hairline, backing);
        double ratio = PaletteEngine.contrastRatio(rendered, backing);
        assertEquals(1.0, ratio, LOOSE_PIN_TOLERANCE);
        assertTrue(ratio < ContrastDerivations.NON_TEXT_INDICATOR_RATIO);
    }

    private static ResolvedTheme resolved(int accent, ThemeMode mode, double opacity) {
        ThemeDefinition definition = new ThemeDefinition();
        definition.accent = accent;
        definition.mode = mode;
        definition.backgroundOpacity = opacity;
        return ThemeResolver.resolve(definition, true);
    }
}
