package com.aurora.client.theme;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.aurora.client.theme.ContrastFixtures.ACCENTS;
import static com.aurora.client.theme.ContrastFixtures.BACKDROPS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Permanent mathematical invariants of the canonical sRGB / luminance /
 * contrast / composition math (Phase D-1, DESIGN_LANGUAGE §3.6). These are
 * NOT current-failure characterizations (see {@code D0CharacterizationTest})
 * and NOT future normative conformance (D-2+): they pin the math itself,
 * which must hold forever.
 */
class SrgbColorMathTest {

    // ------------------------------------------------------------------
    //  sRGB linearization — exact boundary values (§8)
    // ------------------------------------------------------------------

    @Test
    void srgbLinearizationBoundaryValues() {
        assertEquals(0.0, PaletteEngine.srgbToLinear(0), 0.0, "black channel");
        assertEquals(1.0, PaletteEngine.srgbToLinear(255), 1e-12, "white channel");
        // The WCAG knee: 10/255 = 0.03922 <= 0.03928 (linear segment);
        // 11/255 = 0.04314 > knee (power segment). Exact at both.
        assertEquals((10 / 255.0) / 12.92, PaletteEngine.srgbToLinear(10), 0.0);
        assertEquals(Math.pow((11 / 255.0 + 0.055) / 1.055, 2.4), PaletteEngine.srgbToLinear(11), 0.0);
        // Truncation/alpha bits in the channel input are ignored.
        assertEquals(PaletteEngine.srgbToLinear(128), PaletteEngine.srgbToLinear(128 | 0xFFFFFF00));
    }

    @Test
    void srgbLinearizationIsMonotonic() {
        double prev = -1;
        for (int c = 0; c <= 255; c++) {
            double v = PaletteEngine.srgbToLinear(c);
            assertTrue(v >= prev, "monotonic at " + c);
            assertTrue(v >= 0.0 && v <= 1.0, "bounded at " + c);
            prev = v;
        }
    }

    // ------------------------------------------------------------------
    //  Relative luminance (§9) — reference values
    // ------------------------------------------------------------------

    @Test
    void relativeLuminanceReferenceValues() {
        assertEquals(0.0, PaletteEngine.relativeLuminance(0xFF000000), 1e-12, "black");
        assertEquals(1.0, PaletteEngine.relativeLuminance(0xFFFFFFFF), 1e-9,
                "white (weights sum to 1)");
        // Mid-gray #808080: the standard WCAG reference is 0.2158.
        assertEquals(0.2158, PaletteEngine.relativeLuminance(0xFF808080), 1e-3, "mid-gray");
        // Default Aurora red #EB0029 — hand-derived from the channel function.
        double expected = 0.2126 * Math.pow((235 / 255.0 + 0.055) / 1.055, 2.4)
                + 0.0722 * Math.pow((41 / 255.0 + 0.055) / 1.055, 2.4);
        assertEquals(expected, PaletteEngine.relativeLuminance(0xFFEB0029), 1e-12, "default red");
    }

    @Test
    void relativeLuminanceIgnoresAlphaAndStressAccentsAreBounded() {
        for (int accent : ACCENTS.values()) {
            double l = PaletteEngine.relativeLuminance(accent);
            assertTrue(l >= 0.0 && l <= 1.0, "bounded: " + Integer.toHexString(accent));
            assertEquals(l, PaletteEngine.relativeLuminance((accent & 0x00FFFFFF)), 0.0,
                    "alpha ignored");
        }
    }

    // ------------------------------------------------------------------
    //  Contrast ratio (§10) — canonical properties
    // ------------------------------------------------------------------

    @Test
    void contrastRatioCanonicalProperties() {
        assertEquals(21.0, PaletteEngine.contrastRatio(0xFFFFFFFF, 0xFF000000), 1e-9,
                "white vs black == 21");
        assertEquals(21.0, PaletteEngine.contrastRatio(0xFF000000, 0xFFFFFFFF), 1e-9, "symmetric");
        for (int accent : ACCENTS.values()) {
            assertEquals(1.0, PaletteEngine.contrastRatio(accent, accent), 0.0, "x/x == 1");
        }
    }

    @Test
    void contrastRatioIsSymmetricAndBoundedOverTheStressGrid() {
        List<Integer> colors = new ArrayList<>(ACCENTS.values());
        for (int b : BACKDROPS) {
            colors.add(b);
        }
        for (int a : colors) {
            for (int b : colors) {
                double ab = PaletteEngine.contrastRatio(a, b);
                double ba = PaletteEngine.contrastRatio(b, a);
                assertEquals(ab, ba, 1e-12, "symmetry");
                assertTrue(ab >= 1.0 && ab <= 21.0, "ratio in [1,21]");
            }
        }
    }

    // ------------------------------------------------------------------
    //  Alpha composition (§11–12) — the canonical composite()
    // ------------------------------------------------------------------

    @Test
    void compositeAlphaZeroReturnsBackgroundVerbatim() {
        int bg = 0x4D3C3C3C; // translucent background keeps its own alpha
        assertEquals(bg, PaletteEngine.composite(0x00FFFFFF, bg));
        assertEquals(bg, PaletteEngine.composite(0x00000000, bg));
    }

    @Test
    void compositeOpaqueForegroundIsIdentity() {
        for (int accent : ACCENTS.values()) {
            assertEquals(accent, PaletteEngine.composite(accent, 0xFF123456));
            assertEquals(accent, PaletteEngine.composite(accent, 0x80123456));
        }
    }

    @Test
    void compositeHalfAlphaOverOpaqueBackgroundHandComputed() {
        // 0x80 alpha = 128/255 foreground weight, 127/255 background weight;
        // output alpha must be 255 over an opaque background.
        int out = PaletteEngine.composite(0x80FF0000, 0xFF0000FF);
        assertEquals(0xFF, (out >>> 24) & 0xFF, "opaque background -> opaque result");
        assertEquals(128, (out >> 16) & 0xFF, "red = round(255*128/255)");
        assertEquals(0, (out >> 8) & 0xFF);
        assertEquals(127, out & 0xFF, "blue = round(255*127/255)");
    }

    @Test
    void compositeTranslucentForegroundOverTranslucentBackgroundHandComputed() {
        // Straight alpha, red=2@alpha1 over red=1@alpha1: exact output alpha
        // is just under 2/255 and rounds to 2. The visible red channel rounds
        // to 2 when normalized by that exact alpha. Normalizing by rounded
        // 2/255 instead incorrectly quantizes the red channel to 1.
        assertEquals(0x02020000,
                PaletteEngine.composite(0x01020000, 0x01010000));
    }

    @Test
    void compositeOpaqueResultInvariant() {
        // Any translucent fg over an opaque bg yields an opaque result whose
        // channels lie between the two inputs' channels (to rounding).
        int bg = 0xFFF0E0D0;
        for (int a = 0; a <= 255; a += 17) {
            int fg = (a << 24) | 0x102030;
            int out = PaletteEngine.composite(fg, bg);
            assertEquals(0xFF, (out >>> 24) & 0xFF, "alpha at " + a);
            for (int shift = 0; shift <= 16; shift += 8) {
                int c = (out >> shift) & 0xFF;
                int fgc = (fg >> shift) & 0xFF;
                int bgc = (bg >> shift) & 0xFF;
                int lo = Math.min(fgc, bgc), hi = Math.max(fgc, bgc);
                assertTrue(c >= lo - 1 && c <= hi + 1,
                        "channel between inputs at shift " + shift + " alpha " + a);
            }
        }
    }

    @Test
    void compositeNestedCompositionAssociatesWithinRounding() {
        // fg over (mid over bg) == (fg over mid) over bg, to 1/255/channel.
        int[][] layers = {
                {0x80FF0000, 0x800000FF, 0xFF3C3C3C},
                {0x4DFFFFFF, 0x99EBEBF5, 0xFF0D0D0D},
                {0x2E101010, 0x66FF8844, 0x33002233},
        };
        for (int[] l : layers) {
            int left = PaletteEngine.composite(l[0], PaletteEngine.composite(l[1], l[2]));
            int right = PaletteEngine.composite(PaletteEngine.composite(l[0], l[1]), l[2]);
            for (int shift = 0; shift <= 24; shift += 8) {
                int a = (left >>> shift) & 0xFF;
                int b = (right >>> shift) & 0xFF;
                assertTrue(Math.abs(a - b) <= 1, "channel within 1/255 at shift " + shift
                        + " (" + a + " vs " + b + ")");
            }
        }
    }

    @Test
    void compositeChannelRoundingMatchesNearestInteger() {
        // 0xAB (171) at 0x80 alpha over black: the weight is 128/255, so the
        // channel is round(171 * 128/255) = round(85.83) = 86 — nearest
        // integer, never truncated to 85.
        int out = PaletteEngine.composite(0x80ABABAB, 0xFF000000);
        assertEquals(86, (out >> 16) & 0xFF);
        assertEquals(86, (out >> 8) & 0xFF);
        assertEquals(86, out & 0xFF);
    }

    // ------------------------------------------------------------------
    //  Threshold edge cases (§32) — epsilon policy
    // ------------------------------------------------------------------

    @Test
    void thresholdsAreReachableWithinEightBitQuantization() {
        // For each normative threshold, the gray ramp crosses it between two
        // adjacent 8-bit values — i.e. the thresholds live inside real,
        // producible colors, and the jump at the crossing is one channel
        // step's worth of ratio (< 0.15 near these magnitudes). Conformance
        // comparisons themselves use plain >= with NO epsilon (documented in
        // ContrastFixtures).
        for (double t : new double[]{4.5, 3.0, 2.2}) {
            boolean crossed = false;
            for (int v = 0; v < 255 && !crossed; v++) {
                double lo = PaletteEngine.contrastRatio(0xFF000000, 0xFF000000 | (v << 16) | (v << 8) | v);
                double hi = PaletteEngine.contrastRatio(0xFF000000, 0xFF000000 | ((v + 1) << 16) | ((v + 1) << 8) | (v + 1));
                if (lo < t && hi >= t) {
                    assertTrue(hi - lo < 0.15, "one channel step cannot jump the threshold band");
                    crossed = true;
                }
            }
            assertTrue(crossed, "threshold " + t + " is crossed by the 8-bit gray ramp");
        }
    }
}
