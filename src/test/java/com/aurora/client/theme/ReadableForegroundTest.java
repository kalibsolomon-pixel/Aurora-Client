package com.aurora.client.theme;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.aurora.client.theme.ContrastFixtures.ACCENTS;
import static com.aurora.client.theme.ContrastFixtures.BACKDROPS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The readable-foreground primitive (§17–18): the generalized
 * {@code pickOnColor} deciding against the ACTUAL deterministic rendered
 * backing, with both candidates' ratios exposed and neither-passes
 * detection. Permanent invariants — the primitive's contract, not current
 * production behavior.
 */
class ReadableForegroundTest {

    private static List<Integer> deterministicBackings() {
        List<Integer> backings = new ArrayList<>();
        for (int accent : ACCENTS.values()) {
            for (ThemeMode mode : ThemeMode.values()) {
                int[] p = PaletteEngine.derive(accent, mode);
                backings.add(p[ThemeToken.BACKGROUND.ordinal()]);
                backings.add(p[ThemeToken.SURFACE.ordinal()]);
                backings.add(p[ThemeToken.WINDOW_FILL.ordinal()]);
                backings.add(p[ThemeToken.ACCENT.ordinal()]);
                backings.add(p[ThemeToken.ACCENT_HOVER.ordinal()]);
                // The compositing contexts production actually renders:
                // window fill at low opacity over a fixture backdrop.
                for (int b : BACKDROPS) {
                    backings.add(PaletteEngine.composite(p[ThemeToken.WINDOW_FILL.ordinal()], b));
                }
            }
        }
        return backings;
    }

    @Test
    void chosenIsAlwaysTheHigherContrastCandidate() {
        // §18: for each backing, chosen == max(contrast(light), contrast(dark)).
        for (int accent : ACCENTS.values()) {
            float hue = PaletteEngine.rgbToHsl(accent & 0x00FFFFFF)[0];
            for (int backing : deterministicBackings()) {
                ContrastDerivations.ForegroundChoice c =
                        ContrastDerivations.chooseForeground(backing, hue);
                assertEquals(c.lightCandidate() == c.chosen() ? c.lightContrast() : c.darkContrast(),
                        Math.max(c.lightContrast(), c.darkContrast()), 0.0,
                        "chosen carries the max ratio");
                assertTrue(c.chosen() == c.lightCandidate() || c.chosen() == c.darkCandidate(),
                        "chosen is one of the two finite candidates");
            }
        }
    }

    @Test
    void choiceIsDeterministicAndRepetitionStable() {
        int backing = 0xFF808080;
        for (int accent : ACCENTS.values()) {
            float hue = PaletteEngine.rgbToHsl(accent & 0x00FFFFFF)[0];
            ContrastDerivations.ForegroundChoice a = ContrastDerivations.chooseForeground(backing, hue);
            for (int i = 0; i < 5; i++) {
                assertEquals(a, ContrastDerivations.chooseForeground(backing, hue),
                        "pure function — no per-call state");
            }
        }
    }

    @Test
    void tiePreferenceIsLightAndRampNeverContradictsTheComparison() {
        // Documented tie behavior: exact ties prefer the LIGHT candidate
        // (pickOnColor's historical >= — byte-identical delegation). Over the
        // whole 8-bit gray ramp the chosen side always agrees with the
        // sign of (lightRatio - darkRatio).
        float hue = 0f;
        boolean sawNearTie = false;
        for (int v = 0; v <= 255; v++) {
            int gray = 0xFF000000 | (v << 16) | (v << 8) | v;
            ContrastDerivations.ForegroundChoice c = ContrastDerivations.chooseForeground(gray, hue);
            if (c.lightContrast() > c.darkContrast()) {
                assertEquals(c.lightCandidate(), c.chosen(), "light wins at v=" + v);
            } else if (c.darkContrast() > c.lightContrast()) {
                assertEquals(c.darkCandidate(), c.chosen(), "dark wins at v=" + v);
            } else {
                assertEquals(c.lightCandidate(), c.chosen(), "exact tie prefers light at v=" + v);
                assertTrue(c.tie());
            }
            if (Math.abs(c.lightContrast() - c.darkContrast()) < 0.05) {
                sawNearTie = true;
            }
        }
        assertTrue(sawNearTie, "the ramp passes the flip zone (the ~4.17 binary floor)");
    }

    @Test
    void neitherCandidatePassingIsDetectableNotHidden() {
        // §18: where neither candidate clears the normative threshold the
        // API must permit DETECTING that fact — a gray near the binary
        // picker's crossover cannot give either candidate 4.5:1.
        float hue = 0f;
        ContrastDerivations.ForegroundChoice c =
                ContrastDerivations.chooseForeground(0xFF757575, hue);
        assertFalse(c.meets(ContrastDerivations.ESSENTIAL_TEXT_RATIO),
                "crossover gray: neither candidate reaches 4.5");
        assertTrue(c.bestContrast() > 1.0, "the best achievable ratio is still exposed");
        // And against a near-white backing the same primitive reports
        // success — detection, not pessimism.
        assertTrue(ContrastDerivations.chooseForeground(0xFFFAFAFA, hue)
                .meets(ContrastDerivations.ESSENTIAL_TEXT_RATIO));
    }

    @Test
    void candidatesAreTheFiniteSemanticPairForEveryHue() {
        for (float hue : new float[]{0f, 60f, 120f, 180f, 240f, 300f, 359f}) {
            ContrastDerivations.ForegroundChoice c =
                    ContrastDerivations.chooseForeground(0xFF000000, hue);
            assertEquals(PaletteEngine.hsl(hue, 0.05f, 0.97f), c.lightCandidate());
            assertEquals(PaletteEngine.hsl(hue, 0.10f, 0.08f), c.darkCandidate());
        }
    }

    @Test
    void pickOnColorDelegatesByteIdentically() {
        // pickOnColor IS chooseForeground().chosen() — the generalization
        // changed no output (AccentSetting's Custom-swatch label and every
        // ON_* derivation read the same values as before D-1).
        for (int accent : ACCENTS.values()) {
            for (int b : BACKDROPS) {
                float hue = PaletteEngine.rgbToHsl(accent & 0x00FFFFFF)[0];
                assertEquals(PaletteEngine.pickOnColor(b, hue),
                        ContrastDerivations.chooseForeground(b, hue).chosen(),
                        "delegation identity over " + Integer.toHexString(b));
            }
        }
    }

    @Test
    void choiceDoesNotMutateInputsAndHasNoState() {
        // Inputs are values; the primitive cannot rewrite an accent even by
        // accident (the stored-preference invariant's leaf level).
        int accent = ACCENTS.get("DEFAULT_RED");
        int before = accent;
        PaletteEngine.rgbToHsl(accent & 0x00FFFFFF);
        ContrastDerivations.chooseForeground(accent, 42f);
        assertEquals(before, accent);
    }
}
