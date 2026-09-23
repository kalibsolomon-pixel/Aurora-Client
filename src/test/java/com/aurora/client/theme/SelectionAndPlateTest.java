package com.aurora.client.theme;

import org.junit.jupiter.api.Test;

import static com.aurora.client.theme.ContrastFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Selection separation (§24) and the essential-readability plate contract
 * (§25) — the deterministic math D-3 and D-4 will consume. Production-neutral
 * in D-1: no selected control and no production row reads these helpers.
 */
class SelectionAndPlateTest {

    // ------------------------------------------------------------------
    //  Selection separation
    // ------------------------------------------------------------------

    @Test
    void separationBasics() {
        assertEquals(1.0, ContrastDerivations.separationRatio(0xFF808080, 0xFF808080), 0.0,
                "identical colors do not separate");
        assertEquals(21.0, ContrastDerivations.separationRatio(0xFFFFFFFF, 0xFF000000), 1e-9);
        // Composited overload: same layers over any common base must never
        // report MORE separation than the layers' own channel distance —
        // and equal layers over equal base never separate.
        assertEquals(1.0, ContrastDerivations.separationRatio(
                0xFFFF0000, 0xFFFF0000, NEAR_WHITE_BACKDROP), 0.0);
    }

    @Test
    void d0SelectionSeparationCollapseReproduces() {
        // CURRENT-FAILURE CHARACTERIZATION (update at D-3): D-0 measured
        // stained-chip-vs-window at 1.00 for a near-white accent in LIGHT
        // mode — same tint-vs-tint, no separation rule exists yet.
        int[] p = PaletteEngine.derive(NEAR_WHITE, ThemeMode.LIGHT);
        int wa = (p[ThemeToken.WINDOW_FILL.ordinal()] >>> 24) & 0xFF;
        int stain = (Math.max(wa, 140) << 24) | (p[ThemeToken.ACCENT.ordinal()] & 0x00FFFFFF);
        int window = p[ThemeToken.WINDOW_FILL.ordinal()];
        double worst = Math.min(
                ContrastDerivations.separationRatio(stain, window, ContrastDerivations.WORST_BASE_BLACK),
                ContrastDerivations.separationRatio(stain, window, ContrastDerivations.WORST_BASE_WHITE));
        assertEquals(1.00, worst, LOOSE_PIN_TOLERANCE,
                "D-0's 1.00 stained-chip-vs-window collapse (near-white accent, LIGHT)");
        assertTrue(worst < ContrastDerivations.NON_TEXT_INDICATOR_RATIO,
                "below the 3.0 indicator threshold — D-3's problem statement");
    }

    @Test
    void separationSufficiencyIsACallableQuestion() {
        // The D-3 form: "does the selected backing differ sufficiently from
        // its container?" — answerable over the worst-case bases for any
        // accent, both modes, with the composited rule.
        int separated = 0;
        int total = 0;
        for (int accent : ACCENTS.values()) {
            for (ThemeMode mode : ThemeMode.values()) {
                int[] p = PaletteEngine.derive(accent, mode);
                int wa = (p[ThemeToken.WINDOW_FILL.ordinal()] >>> 24) & 0xFF;
                int stain = (Math.max(wa, 140) << 24) | (p[ThemeToken.ACCENT.ordinal()] & 0x00FFFFFF);
                int window = p[ThemeToken.WINDOW_FILL.ordinal()];
                double worst = Math.min(
                        ContrastDerivations.separationRatio(stain, window, ContrastDerivations.WORST_BASE_BLACK),
                        ContrastDerivations.separationRatio(stain, window, ContrastDerivations.WORST_BASE_WHITE));
                total++;
                if (worst >= ContrastDerivations.NON_TEXT_INDICATOR_RATIO) {
                    separated++;
                }
            }
        }
        assertTrue(total == ACCENTS.size() * 2);
        // Some accents separate and some do not (D-0 found 1.00 collapses AND
        // ordinary cases) — the helper answers both directions.
        assertTrue(separated > 0, "some catalog accents do separate from the window");
        assertTrue(separated < total, "some catalog accents collapse — the D-3 problem exists");
    }

    // ------------------------------------------------------------------
    //  Essential-readability plate (§25)
    // ------------------------------------------------------------------

    @Test
    void primaryTextAtLowWindowOpacityOverNearWhiteIsInadequate() {
        // CURRENT-FAILURE CHARACTERIZATION (update at D-4): primary text over
        // a 0.10 window over a near-white world is ~1.21 — far below 4.5.
        ThemeDefinition def = new ThemeDefinition();
        def.accent = DEFAULT_RED;
        def.mode = ThemeMode.DARK;
        def.backgroundOpacity = 0.10;
        ResolvedTheme th = ThemeResolver.resolve(def, true);
        int backing = PaletteEngine.composite(th.color(ThemeToken.WINDOW_FILL), NEAR_WHITE_BACKDROP);
        double ratio = PaletteEngine.contrastRatio(th.color(ThemeToken.ON_BACKGROUND), backing);
        assertEquals(1.21, ratio, LOOSE_PIN_TOLERANCE, "D-0's 1.21 measurement");
        assertTrue(ratio < ContrastDerivations.ESSENTIAL_TEXT_RATIO,
                "0.10 window opacity cannot carry essential text over a bright world");
    }

    @Test
    void minimumBackingPinsTheD0OpacityThreshold() {
        // D-0: 4.5:1 over a near-white world requires window opacity ~0.58
        // (0.57 measured 4.49; 0.58 crossed it). The helper returns the
        // first 1/255 step that meets the target — ~0.58.
        ThemeDefinition def = new ThemeDefinition();
        def.accent = DEFAULT_RED;
        def.mode = ThemeMode.DARK;
        def.backgroundOpacity = 0.10;
        ResolvedTheme th = ThemeResolver.resolve(def, true);
        double alpha = ContrastDerivations.minimumReadableBacking(
                th.color(ThemeToken.ON_BACKGROUND),
                th.color(ThemeToken.WINDOW_FILL) & 0x00FFFFFF,
                NEAR_WHITE_BACKDROP,
                ContrastDerivations.ESSENTIAL_TEXT_RATIO);
        assertEquals(0.58, alpha, PIN_TOLERANCE + 0.005,
                "the measured minimum-effective-backing threshold");
        assertTrue(alpha > 0.10, "0.10 is genuinely insufficient");
        assertTrue(alpha < 1.0, "fully opaque is not required");
    }

    @Test
    void plateHelperHandlesDarkWorldsAndMutedForegrounds() {
        // Over a near-black world the same chain passes with no plate at
        // all (alpha 0): dark worlds were never the failing case.
        ThemeDefinition def = new ThemeDefinition();
        def.accent = DEFAULT_RED;
        def.mode = ThemeMode.DARK;
        def.backgroundOpacity = 0.10;
        ResolvedTheme th = ThemeResolver.resolve(def, true);
        double alpha = ContrastDerivations.minimumReadableBacking(
                th.color(ThemeToken.ON_BACKGROUND),
                th.color(ThemeToken.WINDOW_FILL) & 0x00FFFFFF,
                NEAR_BLACK_BACKDROP,
                ContrastDerivations.ESSENTIAL_TEXT_RATIO);
        assertTrue(alpha < 0.35, "dark worlds need little or no plate");

        // A translucent (muted-tier) foreground composites over the plate
        // result, so the helper can state ITS minimum backing too — the
        // D-4 placeholder-policy input.
        double mutedAlpha = ContrastDerivations.minimumReadableBacking(
                th.color(ThemeToken.ON_BACKGROUND_MUTED),
                th.color(ThemeToken.WINDOW_FILL) & 0x00FFFFFF,
                NEAR_WHITE_BACKDROP,
                ContrastDerivations.SUPPLEMENTAL_MUTED_RATIO);
        assertTrue(mutedAlpha >= 0, "muted text over its own plate family is reachable");
    }

    @Test
    void unreachablePlateReportsMinusOne() {
        // Foreground == plate family == base: no alpha can separate them.
        assertEquals(-1.0, ContrastDerivations.minimumReadableBacking(
                0xFF808080, 0x808080, 0xFF808080, 4.5), 0.0);
    }

    @Test
    void plateHelperIsDeterministic() {
        for (int i = 0; i < 3; i++) {
            assertEquals(
                    ContrastDerivations.minimumReadableBacking(0xFFF8F7F7, 0x160E0F, NEAR_WHITE_BACKDROP, 4.5),
                    ContrastDerivations.minimumReadableBacking(0xFFF8F7F7, 0x160E0F, NEAR_WHITE_BACKDROP, 4.5),
                    0.0);
        }
    }
}
