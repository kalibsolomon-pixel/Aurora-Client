package com.aurora.client.theme;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.aurora.client.theme.ContrastFixtures.ACCENTS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The FOCUS_RING derivation foundation (§22–23): a resolve-time, bounded,
 * identity-preferring ring color targeting 3:1 against ONE documented
 * deterministic adjacent backing — with the context limitation pinned
 * explicitly (a single ring cannot solve same-accent stained backings).
 */
class FocusRingDerivationTest {

    /** The flat control surface over a worst-case base — the documented adjacent. */
    private static List<Integer> adjacents(int accent, ThemeMode mode) {
        int[] p = PaletteEngine.derive(accent, mode);
        List<Integer> out = new ArrayList<>();
        int wa = (p[ThemeToken.WINDOW_FILL.ordinal()] >>> 24) & 0xFF;
        int surface = (wa << 24) | (p[ThemeToken.SURFACE.ordinal()] & 0x00FFFFFF);
        for (int base : ContrastDerivations.WORST_BASES) {
            out.add(PaletteEngine.composite(surface, base));
        }
        out.add(p[ThemeToken.SURFACE.ordinal()]); // opaque window interior
        return out;
    }

    @Test
    void ringMeetsThreeToOneOrExposesFailure() {
        for (int accent : ACCENTS.values()) {
            for (ThemeMode mode : ThemeMode.values()) {
                for (int adjacent : adjacents(accent, mode)) {
                    ContrastDerivations.FocusRing ring = ContrastDerivations.focusRing(
                            accent, adjacent, ContrastDerivations.NON_TEXT_INDICATOR_RATIO);
                    if (ring.met()) {
                        // Re-verify the achieved ratio against the adjacent.
                        int rendered = PaletteEngine.composite(ring.colorArgb(), adjacent);
                        assertTrue(PaletteEngine.contrastRatio(rendered, adjacent)
                                        >= ContrastDerivations.NON_TEXT_INDICATOR_RATIO,
                                "met ring re-verifies");
                    } else {
                        // Failure is exposed with the bounded best effort.
                        assertTrue(ring.policy().startsWith("unmet"), ring.policy());
                        assertTrue(ring.achievedRatio()
                                < ContrastDerivations.NON_TEXT_INDICATOR_RATIO);
                    }
                }
            }
        }
    }

    @Test
    void identityIsPreferredWhenTheAccentItselfSeparates() {
        // Against a near-white adjacent, a dark accent is its own best ring:
        // the derivation returns the accent verbatim with identity=true.
        ContrastDerivations.FocusRing ring = ContrastDerivations.focusRing(
                0xFF000000, 0xFFFAFAFA, ContrastDerivations.NON_TEXT_INDICATOR_RATIO);
        assertTrue(ring.met());
        assertTrue(ring.accentIdentity());
        assertEquals(0xFF000000, ring.colorArgb());
        assertEquals("accent", ring.policy());
    }

    @Test
    void accentRelatedFallbackCarriesTheAccentHue() {
        // When the accent cannot separate (near-white accent on a near-white
        // adjacent), the fallback candidates are tinted with the ACCENT's
        // hue — accent-related, never an arbitrary foreign color.
        ContrastDerivations.FocusRing ring = ContrastDerivations.focusRing(
                0xFFF5F5F5, 0xFFF4F4F4, ContrastDerivations.NON_TEXT_INDICATOR_RATIO);
        assertTrue(ring.met());
        assertFalse(ring.accentIdentity());
        float[] hue = PaletteEngine.rgbToHsl(ring.colorArgb() & 0x00FFFFFF);
        // Near-white/near-black candidates carry ~0 saturation for a
        // near-white accent hue (0 degrees): verify the candidate IS one of
        // the semantic pair for that hue.
        ContrastDerivations.ForegroundChoice pair =
                ContrastDerivations.chooseForeground(0xFFF4F4F4, 0f);
        assertTrue(ring.colorArgb() == pair.lightCandidate()
                || ring.colorArgb() == pair.darkCandidate());
        assertEquals(0f, hue[0], 1.5f);
    }

    @Test
    void unreachableTargetIsExposedNotFaked() {
        // At a deliberately impossible target (4.5 for an indicator) the
        // bounded policy fails loudly against a mid-luminance adjacent and
        // returns the best candidate with policy "unmet:*".
        int adjacent = 0xFF808080;
        for (int accent : new int[]{0xFFEB0029, 0xFF5E5EFF, 0xFFF5F5F5, 0xFF000000}) {
            ContrastDerivations.FocusRing ring = ContrastDerivations.focusRing(accent, adjacent, 4.5);
            // The tinted pair tops out near the ~4.2 binary floor, so 4.5 is
            // out of reach for at least some; every case still answers.
            assertNotNull(ring.policy());
            assertTrue(ring.achievedRatio() > 1.0);
        }
        ContrastDerivations.FocusRing mustFail = ContrastDerivations.focusRing(0xFF757575, 0xFF757575, 4.5);
        assertFalse(mustFail.met(), "crossover gray cannot reach 4.5 under the bounded policy");
    }

    @Test
    void ringIsDeterministic() {
        for (int i = 0; i < 5; i++) {
            assertEquals(
                    ContrastDerivations.focusRing(0xFFEB0029, 0xFF1A1A1A, 3.0),
                    ContrastDerivations.focusRing(0xFFEB0029, 0xFF1A1A1A, 3.0));
        }
    }

    @Test
    void multiBackingResultUsesOneColorAndReportsItsWorstCase() {
        int[] adjacents = {0xFF171717, 0xFFEFEFEF};
        ContrastDerivations.FocusRing ring = ContrastDerivations.focusRing(
                0xFFEB0029, adjacents, ContrastDerivations.NON_TEXT_INDICATOR_RATIO);
        double actualWorst = Double.MAX_VALUE;
        for (int adjacent : adjacents) {
            int rendered = PaletteEngine.composite(ring.colorArgb(), adjacent);
            actualWorst = Math.min(actualWorst,
                    PaletteEngine.contrastRatio(rendered, adjacent));
        }
        assertEquals(actualWorst, ring.achievedRatio(), 0.0);
        assertEquals(actualWorst >= ContrastDerivations.NON_TEXT_INDICATOR_RATIO, ring.met());
    }

    // ------------------------------------------------------------------
    //  The §23 context limitation — pinned, not papered over
    // ------------------------------------------------------------------

    @Test
    void aSingleRingDoesNotSolveSameAccentStainedBackings() {
        // The base ring is derived against the flat control surface. Against
        // a same-accent STAINED backing (the ring's hue family IS the
        // backing), the derived color can collapse below 3:1 — the D-0
        // focus/stain failure is structural, and D-3/D-6 own the contextual
        // treatment. This test pins that the limitation is real so no later
        // phase quietly assumes one token solved every context.
        int collapsed = 0;
        for (int accent : ACCENTS.values()) {
            int[] p = PaletteEngine.derive(accent, ThemeMode.DARK);
            int wa = (p[ThemeToken.WINDOW_FILL.ordinal()] >>> 24) & 0xFF;
            int surface = (wa << 24) | (p[ThemeToken.SURFACE.ordinal()] & 0x00FFFFFF);
            for (int base : ContrastDerivations.WORST_BASES) {
                int adjacent = PaletteEngine.composite(surface, base);
                ContrastDerivations.FocusRing ring = ContrastDerivations.focusRing(
                        accent, adjacent, ContrastDerivations.NON_TEXT_INDICATOR_RATIO);
                int stain = (Math.max(wa, 140) << 24) | (accent & 0x00FFFFFF);
                int stainedBacking = PaletteEngine.composite(stain, base);
                int rendered = PaletteEngine.composite(ring.colorArgb(), stainedBacking);
                if (PaletteEngine.contrastRatio(rendered, stainedBacking)
                        < ContrastDerivations.NON_TEXT_INDICATOR_RATIO) {
                    collapsed++;
                }
            }
        }
        assertTrue(collapsed > 0,
                "the same-accent stained context collapses for some catalog entries — "
                        + "the documented limitation (D-3/D-6 scope), not a solvable bug here");
    }
}
