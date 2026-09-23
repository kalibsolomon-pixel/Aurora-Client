package com.aurora.client.theme;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static com.aurora.client.theme.ContrastFixtures.ACCENTS;
import static com.aurora.client.theme.ContrastFixtures.OPACITIES;
import static org.junit.jupiter.api.Assertions.*;

/** Resolve-time plumbing, determinism, and stored-preference invariants. */
class ResolvedThemeContrastTest {

    private static final Gson GSON = new Gson();

    @Test
    void everyStressDefinitionResolvesDeterministicallyWithoutMutation() {
        for (int accent : ACCENTS.values()) {
            for (ThemeMode mode : ThemeMode.values()) {
                for (double opacity : OPACITIES) {
                    ThemeDefinition definition = definition(accent, mode, opacity);
                    String serializedBefore = GSON.toJson(definition);
                    int accentBefore = definition.accent;

                    ResolvedTheme first = ThemeResolver.resolve(definition, true);
                    ResolvedTheme second = ThemeResolver.resolve(definition, true);

                    assertEquals(accentBefore, definition.accent, "stored accent unchanged");
                    assertEquals(serializedBefore, GSON.toJson(definition),
                            "resolution does not mutate any serialized preference");
                    assertEquals(accentBefore, first.accent(), "resolved source remembers input");
                    assertThemeEquals(first, second);
                    assertDerivationsEqual(first.contrastDerivations(), second.contrastDerivations());
                }
            }
        }
    }

    @Test
    void bothFactoryAndDerivedPathsCarryDerivations() {
        ThemeDefinition definition = definition(
                ContrastFixtures.DEFAULT_RED, ThemeMode.LIGHT, 0.35);
        ResolvedTheme derived = ThemeResolver.resolve(definition, true);
        ResolvedTheme factory = ThemeResolver.resolve(definition, false);
        assertNotNull(derived.contrastDerivations());
        assertNotNull(factory.contrastDerivations());
        assertNotNull(derived.contrastDerivations().focusRing());
        assertNotNull(derived.contrastDerivations().stainedTextBacking());
        assertTrue(Double.isFinite(derived.contrastDerivations().selectionSeparationRatio()));
    }

    @Test
    void resolvingAConfigOwnedDefinitionDoesNotChangeItsSerializedForm() {
        // AuroraConfig's static path is Fabric-loader-owned and is not
        // available in the plain JVM test process. This envelope models the
        // same serialized ownership boundary without initializing Fabric;
        // the live DevPilot oracle covers the real AuroraConfig instance.
        ConfigEnvelope config = new ConfigEnvelope();
        config.theme = definition(ContrastFixtures.SATURATED_BLUE,
                ThemeMode.LIGHT, 0.35);
        String before = GSON.toJson(config);
        ThemeResolver.resolve(config.theme, config.themeEnabled);
        ThemeResolver.resolve(config.theme, config.themeEnabled);
        assertEquals(before, GSON.toJson(config),
                "resolution performs no config/schema/persistence mutation");
    }

    private static final class ConfigEnvelope {
        boolean themeEnabled = true;
        ThemeDefinition theme;
        int unrelatedSetting = 17;
    }

    @Test
    void carriedFocusRingRatioIsTheActualWorstOfItsDocumentedAdjacencies() {
        for (int accent : ACCENTS.values()) {
            for (ThemeMode mode : ThemeMode.values()) {
                ResolvedTheme theme = ThemeResolver.resolve(definition(accent, mode, 0.35), true);
                ContrastDerivations.FocusRing ring = theme.contrastDerivations().focusRing();
                int windowAlpha = (theme.color(ThemeToken.WINDOW_FILL) >>> 24) & 0xFF;
                int surface = (windowAlpha << 24)
                        | (theme.color(ThemeToken.SURFACE) & 0x00FFFFFF);
                double actualWorst = Double.MAX_VALUE;
                for (int base : ContrastDerivations.WORST_BASES) {
                    int adjacent = PaletteEngine.composite(surface, base);
                    int rendered = PaletteEngine.composite(ring.colorArgb(), adjacent);
                    actualWorst = Math.min(actualWorst,
                            PaletteEngine.contrastRatio(rendered, adjacent));
                }
                assertEquals(actualWorst, ring.achievedRatio(), 0.0);
                assertEquals(actualWorst >= ContrastDerivations.NON_TEXT_INDICATOR_RATIO,
                        ring.met());
            }
        }
    }

    @Test
    void carriedSelectionSeparationMatchesTheCompositedPolicy() {
        for (ThemeMode mode : ThemeMode.values()) {
            ResolvedTheme theme = ThemeResolver.resolve(
                    definition(ContrastFixtures.NEAR_WHITE, mode, 0.10), true);
            int alpha = Math.max(
                    (theme.color(ThemeToken.WINDOW_FILL) >>> 24) & 0xFF,
                    ContrastDerivations.STAINED_VISIBILITY_FLOOR_ALPHA);
            int stain = (alpha << 24)
                    | (theme.color(ThemeToken.ACCENT) & 0x00FFFFFF);
            double expected = Double.MAX_VALUE;
            for (int base : ContrastDerivations.WORST_BASES) {
                expected = Math.min(expected, ContrastDerivations.separationRatio(
                        stain, theme.color(ThemeToken.WINDOW_FILL), base));
            }
            assertEquals(expected,
                    theme.contrastDerivations().selectionSeparationRatio(), 0.0);
        }
    }

    private static ThemeDefinition definition(int accent, ThemeMode mode, double opacity) {
        ThemeDefinition definition = new ThemeDefinition();
        definition.accent = accent;
        definition.mode = mode;
        definition.backgroundOpacity = opacity;
        definition.roundness = ThemeRoundness.SQUARE;
        definition.glassStyle = GlassStyle.TRANSPARENT;
        return definition;
    }

    private static void assertThemeEquals(ResolvedTheme a, ResolvedTheme b) {
        assertEquals(a.accent(), b.accent());
        assertEquals(a.mode(), b.mode());
        assertEquals(a.backgroundOpacity(), b.backgroundOpacity(), 0.0);
        for (ThemeToken token : ThemeToken.values()) {
            assertEquals(a.color(token), b.color(token), "token " + token);
        }
    }

    private static void assertDerivationsEqual(
            ContrastDerivations a, ContrastDerivations b) {
        assertEquals(a.focusRing(), b.focusRing());
        assertEquals(a.stainedTextBacking(), b.stainedTextBacking());
        assertEquals(a.selectionSeparationRatio(), b.selectionSeparationRatio(), 0.0);
    }
}
