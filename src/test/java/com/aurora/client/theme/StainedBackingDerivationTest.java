package com.aurora.client.theme;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.aurora.client.theme.ContrastFixtures.ACCENTS;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The readable-stained-backing derivation (§19–21, §33): the bounded,
 * mode-deterministic, identity-preserving adaptation D-2 will consume for
 * text-bearing stained surfaces. Foundation tests — the derivation is not
 * consumed by production yet, and no test here may push the identity bound
 * to manufacture a pass (§20).
 */
class StainedBackingDerivationTest {

    private static final double STAIN_FLOOR = 140 / 255.0;

    private static int[] accentFamily(int accent) {
        int[] p = PaletteEngine.derive(accent, ThemeMode.DARK);
        return new int[]{
                p[ThemeToken.ACCENT.ordinal()],
                p[ThemeToken.ACCENT_HOVER.ordinal()],
                p[ThemeToken.ACCENT_PRESSED.ordinal()],
                p[ThemeToken.ACCENT_GRAD_TOP.ordinal()],
                p[ThemeToken.ACCENT_GRAD_TOP_HOVER.ordinal()],
        };
    }

    // ------------------------------------------------------------------
    //  Identity bound (§20)
    // ------------------------------------------------------------------

    @Test
    void lightnessShiftNeverExceedsTheHardBound() {
        assertEquals(0.08f, ContrastDerivations.MAX_BACKING_LIGHTNESS_SHIFT, 0f,
                "the D-0 identity bound itself is normative");
        // HSL round-trip quantization is <= ~0.002 in lightness for one
        // 8-bit channel step; the bound is otherwise exact.
        for (int accent : ACCENTS.values()) {
            for (ThemeMode mode : ThemeMode.values()) {
                int onAccent = PaletteEngine.derive(accent, mode)[ThemeToken.ON_ACCENT.ordinal()];
                for (double target : new double[]{2.2, 3.0, 4.5, 6.0}) {
                    ContrastDerivations.StainedBacking sb = ContrastDerivations.readableStainedBacking(
                            accent, onAccent, STAIN_FLOOR, target);
                    assertTrue(Math.abs(sb.lightnessShift()) <= 0.08f,
                            "shift bound at target " + target);
                    float[] original = PaletteEngine.rgbToHsl(accent & 0x00FFFFFF);
                    float[] shifted = PaletteEngine.rgbToHsl(sb.stainArgb() & 0x00FFFFFF);
                    float dl = Math.abs(shifted[2] - original[2]);
                    // Quantization: rgb->hsl->rgb->hsl can wobble by a channel step.
                    assertTrue(dl <= ContrastDerivations.MAX_BACKING_LIGHTNESS_SHIFT + 0.005f,
                            "realized ΔL within bound + quantization, got " + dl);
                }
            }
        }
    }

    @Test
    void adaptationPreservesHueAndSaturation() {
        // Identity = hue + saturation; the shift touches lightness only.
        // 8-bit quantization allows ~1.5 degrees of hue wobble on the round
        // trip and small saturation drift near the ramps' knees.
        for (int accent : ACCENTS.values()) {
            int onAccent = PaletteEngine.derive(accent, ThemeMode.DARK)[ThemeToken.ON_ACCENT.ordinal()];
            ContrastDerivations.StainedBacking sb = ContrastDerivations.readableStainedBacking(
                    accent, onAccent, STAIN_FLOOR, ContrastDerivations.ESSENTIAL_TEXT_RATIO);
            float[] o = PaletteEngine.rgbToHsl(accent & 0x00FFFFFF);
            float[] s = PaletteEngine.rgbToHsl(sb.stainArgb() & 0x00FFFFFF);
            float hueDelta = Math.abs(s[0] - o[0]);
            if (hueDelta > 180f) {
                hueDelta = 360f - hueDelta;
            }
            assertTrue(hueDelta <= 1.5f, "hue preserved, got Δ" + hueDelta);
            assertTrue(Math.abs(s[1] - o[1]) <= 0.02f, "saturation preserved, got Δ"
                    + Math.abs(s[1] - o[1]));
        }
    }

    @Test
    void adaptationNeverMutatesTheAccent() {
        int accent = ACCENTS.get("DEFAULT_RED");
        int onAccent = PaletteEngine.derive(accent, ThemeMode.DARK)[ThemeToken.ON_ACCENT.ordinal()];
        int before = accent;
        ContrastDerivations.readableStainedBacking(accent, onAccent, STAIN_FLOOR, 4.5);
        assertEquals(before, accent, "the stored accent is an input, never a target");
        // The returned stain differs only when adaptation was needed.
        ContrastDerivations.StainedBacking identity =
                ContrastDerivations.readableStainedBacking(accent, onAccent, 1.0, 1.0);
        assertEquals(accent, identity.stainArgb(), "trivial target -> identity stain");
        assertEquals(0f, identity.lightnessShift(), 0f);
    }

    // ------------------------------------------------------------------
    //  Failure exposure (§20) — never fake a pass
    // ------------------------------------------------------------------

    @Test
    void unreachableTargetIsExposedNotFaked() {
        // An impossible target (e.g. the foreground IS the accent) must
        // report failure without violating the bound.
        int gray = ACCENTS.get("MID_GRAY");
        ContrastDerivations.StainedBacking impossible =
                ContrastDerivations.readableStainedBacking(gray, gray, STAIN_FLOOR, 4.5);
        assertFalse(impossible.sufficient(), "fg == stain: no alpha or bounded shift can help");
        assertTrue(impossible.achievedRatio() < 4.5);
        assertTrue(Math.abs(impossible.lightnessShift()) <= ContrastDerivations.MAX_BACKING_LIGHTNESS_SHIFT,
                "the bound held even at failure");
        // At the returned fully opaque best effort, world pixels no longer
        // affect the backing, so coversAllWorlds may be true even though the
        // bounded lightness shift still cannot reach the target.
        assertTrue(impossible.coversAllWorlds());
    }

    @Test
    void familyOverloadReportsTheD0_02HoverFailureHonestly() {
        // The full production accent FAMILY includes hover/gradient variants
        // that drift lighter than the pick reference (D0-02). For the
        // default accent at the 4.5 essential-text target the bounded policy
        // cannot cover the whole family — the derivation says so instead of
        // exceeding the bound. This is the number D-2 will design against.
        int accent = ACCENTS.get("DEFAULT_RED");
        int onAccent = PaletteEngine.derive(accent, ThemeMode.DARK)[ThemeToken.ON_ACCENT.ordinal()];
        ContrastDerivations.StainedBacking family =
                ContrastDerivations.readableStainedBacking(accentFamily(accent), onAccent,
                        STAIN_FLOOR, ContrastDerivations.ESSENTIAL_TEXT_RATIO);
        assertFalse(family.sufficient(), "the hover-variant family cannot reach 4.5 within ΔL 0.08");
        assertTrue(Math.abs(family.lightnessShift()) <= ContrastDerivations.MAX_BACKING_LIGHTNESS_SHIFT);

        // The SINGLE-accent derivation (a stained surface that does not
        // recolor on hover) DOES succeed — bounded shift, raised alpha.
        ContrastDerivations.StainedBacking single =
                ContrastDerivations.readableStainedBacking(accent, onAccent,
                        STAIN_FLOOR, ContrastDerivations.ESSENTIAL_TEXT_RATIO);
        assertTrue(single.sufficient(), "single-stain default red is solvable within the bound");
        assertTrue(single.requiredAlpha() > STAIN_FLOOR, "alpha had to rise");
        assertTrue(single.achievedRatio() >= ContrastDerivations.ESSENTIAL_TEXT_RATIO);
    }

    // ------------------------------------------------------------------
    //  Candidate policy semantics (§18–19)
    // ------------------------------------------------------------------

    @Test
    void sufficientResultsActuallyVerifyOverTheWorstCase() {
        for (int accent : ACCENTS.values()) {
            int onAccent = PaletteEngine.derive(accent, ThemeMode.DARK)[ThemeToken.ON_ACCENT.ordinal()];
            ContrastDerivations.StainedBacking sb = ContrastDerivations.readableStainedBacking(
                    accent, onAccent, STAIN_FLOOR, ContrastDerivations.ESSENTIAL_TEXT_RATIO);
            if (!sb.sufficient()) {
                continue;
            }
            int a = (int) Math.round(sb.requiredAlpha() * 255);
            for (int base : ContrastDerivations.WORST_BASES) {
                int stain = (a << 24) | (sb.stainArgb() & 0x00FFFFFF);
                double ratio = PaletteEngine.contrastRatio(onAccent,
                        PaletteEngine.composite(stain, base));
                assertTrue(ratio >= ContrastDerivations.ESSENTIAL_TEXT_RATIO,
                        "re-verified worst case over base " + Integer.toHexString(base));
            }
        }
    }

    // ------------------------------------------------------------------
    //  Mode-deterministic backing assumption (§21)
    // ------------------------------------------------------------------

    @Test
    void derivationIsModeIndependentBecauseTheWorldIs() {
        // The formalized assumption: the world behind glass may be ANY
        // luminance in BOTH modes, so the derivation evaluates both neutral
        // extremes and never reads the mode. Identical inputs -> identical
        // results across DARK and LIGHT.
        for (int accent : ACCENTS.values()) {
            int onAccentDark = PaletteEngine.derive(accent, ThemeMode.DARK)[ThemeToken.ON_ACCENT.ordinal()];
            int onAccentLight = PaletteEngine.derive(accent, ThemeMode.LIGHT)[ThemeToken.ON_ACCENT.ordinal()];
            assertEquals(onAccentDark, onAccentLight, "ON_ACCENT itself is mode-independent");
            ContrastDerivations.StainedBacking dark = ContrastDerivations.readableStainedBacking(
                    accent, onAccentDark, STAIN_FLOOR, ContrastDerivations.ESSENTIAL_TEXT_RATIO);
            ContrastDerivations.StainedBacking light = ContrastDerivations.readableStainedBacking(
                    accent, onAccentLight, STAIN_FLOOR, ContrastDerivations.ESSENTIAL_TEXT_RATIO);
            assertEquals(dark, light, "the stained-backing derivation has no mode input");
        }
    }

    @Test
    void repeatedDerivationIsByteIdentical() {
        int accent = ACCENTS.get("SATURATED_BLUE");
        int onAccent = PaletteEngine.derive(accent, ThemeMode.DARK)[ThemeToken.ON_ACCENT.ordinal()];
        ContrastDerivations.StainedBacking first =
                ContrastDerivations.readableStainedBacking(accent, onAccent, STAIN_FLOOR, 4.5);
        for (int i = 0; i < 5; i++) {
            assertEquals(first, ContrastDerivations.readableStainedBacking(
                    accent, onAccent, STAIN_FLOOR, 4.5));
        }
    }

    // ------------------------------------------------------------------
    //  Animation-stability preparation (§33)
    // ------------------------------------------------------------------

    @Test
    void worstCaseEndpointsCoverAnimatedIntermediates() {
        // A sufficient derivation must hold along every INTERPOLATED backing
        // between family endpoints, not just at the endpoints: derivations
        // are computed once per resolve, so hover/gradient animation frames
        // between endpoints inherit the guarantee. (Structurally sound
        // because the foreground's luminance lies outside the family's
        // composited band whenever coversAllWorlds holds.)
        List<Integer> solvable = new ArrayList<>();
        for (int accent : ACCENTS.values()) {
            int onAccent = PaletteEngine.derive(accent, ThemeMode.DARK)[ThemeToken.ON_ACCENT.ordinal()];
            ContrastDerivations.StainedBacking sb = ContrastDerivations.readableStainedBacking(
                    accentFamily(accent), onAccent, STAIN_FLOOR, 3.0);
            if (sb.sufficient()) {
                solvable.add(accent);
            }
        }
        assertTrue(solvable.size() >= 10, "most of the catalog is solvable at the 3.0 indicator target");
        for (int accent : solvable) {
            int onAccent = PaletteEngine.derive(accent, ThemeMode.DARK)[ThemeToken.ON_ACCENT.ordinal()];
            ContrastDerivations.StainedBacking sb = ContrastDerivations.readableStainedBacking(
                    accentFamily(accent), onAccent, STAIN_FLOOR, 3.0);
            int a = (int) Math.round(sb.requiredAlpha() * 255);
            int[] family = accentFamily(accent);
            for (int i = 0; i < family.length; i++) {
                for (int j = i + 1; j < family.length; j++) {
                    for (double t = 0.25; t < 1.0; t += 0.25) {
                        int left = applyLightnessShift(family[i], sb.lightnessShift());
                        int right = applyLightnessShift(family[j], sb.lightnessShift());
                        int mid = PaletteEngine.composite( // opaque blend family endpoint
                                (int) Math.round(0xFF * t) << 24 | (left & 0x00FFFFFF),
                                right);
                        int stain = (a << 24) | (mid & 0x00FFFFFF);
                        for (int base : ContrastDerivations.WORST_BASES) {
                            double ratio = PaletteEngine.contrastRatio(onAccent,
                                    PaletteEngine.composite(stain, base));
                            assertTrue(ratio >= 3.0,
                                    "intermediate t=" + t + " between family members " + i + "," + j);
                        }
                    }
                }
            }
        }
    }

    private static int applyLightnessShift(int color, float shift) {
        if (shift == 0f) {
            return color;
        }
        float[] hsl = PaletteEngine.rgbToHsl(color & 0x00FFFFFF);
        return PaletteEngine.hsl(hsl[0], hsl[1],
                Math.max(0f, Math.min(1f, hsl[2] + shift)));
    }
}
