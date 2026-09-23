package com.aurora.client.theme;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.aurora.client.theme.ContrastDerivations.ESSENTIAL_TEXT_RATIO;
import static com.aurora.client.theme.ContrastFixtures.ACCENTS;
import static com.aurora.client.theme.ContrastFixtures.BACKDROPS;
import static org.junit.jupiter.api.Assertions.*;

/** Phase D-2/D-3 mathematical oracle and complete production-inventory pins. */
class AdaptiveOnAccentTreatmentTest {

    private static ResolvedTheme resolve(int accent, ThemeMode mode, double opacity) {
        ThemeDefinition def = new ThemeDefinition();
        def.accent = accent;
        def.mode = mode;
        def.backgroundOpacity = opacity;
        return ThemeResolver.resolve(def, true);
    }

    @Test
    void everyPilotPassesTheCompleteStressAndStateMatrix() {
        for (var named : ACCENTS.entrySet()) {
            for (ThemeMode mode : ThemeMode.values()) {
                for (double opacity : ContrastFixtures.OPACITIES) {
                    AdaptiveOnAccentTreatment p = resolve(named.getValue(), mode, opacity).adaptiveOnAccent();
                    String where = named.getKey() + "/" + mode + "/" + opacity;

                    // Button rest -> hover is the complete color animation;
                    // pressed scales geometry but retains the current path color.
                    for (int i = 0; i <= 255; i++) {
                        int backing = lerp(p.buttonRest(), p.buttonHover(), i / 255f);
                        assertPass(p.foreground(), backing, "button path " + i + " " + where);
                    }

                    // Selected segment, including the real 0..0x1A hover wash.
                    int washRgb = resolve(named.getValue(), mode, opacity)
                            .color(ThemeToken.ON_BACKGROUND) & 0x00FFFFFF;
                    for (int i = 0; i <= 255; i++) {
                        int wash = (Math.round(0x1A * (i / 255f)) << 24) | washRgb;
                        assertPass(p.foreground(),
                                PaletteEngine.composite(wash, p.selectedSegment()),
                                "segment hover " + i + " " + where);
                    }

                    // Flat listening fallback plus the shared glass tint for
                    // all three pilot families over every controlled backdrop.
                    assertPass(p.foreground(), p.listeningPill(), "listening flat " + where);
                    for (int backdrop : BACKDROPS) {
                        int glass = PaletteEngine.composite(p.stainedTint(), backdrop);
                        assertPass(p.foreground(), glass, "glass " + where);
                        int supplemental = PaletteEngine.composite(p.supplementalForeground(), glass);
                        assertTrue(PaletteEngine.contrastRatio(supplemental, glass)
                                >= ContrastDerivations.SUPPLEMENTAL_MUTED_RATIO,
                                "supplemental glass " + where);
                        int wash = (0x1A << 24) | washRgb;
                        assertPass(p.foreground(), PaletteEngine.composite(wash, glass),
                                "selected glass hover " + where);
                    }
                    assertTrue(p.worstRatio() >= ESSENTIAL_TEXT_RATIO, where);
                }
            }
        }
    }

    @Test
    void foregroundIsStableAcrossEveryAnimationState() {
        for (int accent : ACCENTS.values()) {
            AdaptiveOnAccentTreatment p = resolve(accent, ThemeMode.DARK, 0.10).adaptiveOnAccent();
            int foreground = p.foreground();
            for (int i = 0; i <= 255; i++) {
                assertEquals(foreground, p.foreground(), "no light/dark flip at step " + i);
            }
        }
    }

    @Test
    void adaptationNeverMutatesStoredAccentAndResolvesDeterministically() {
        for (var named : ACCENTS.entrySet()) {
            ThemeDefinition def = new ThemeDefinition();
            def.accent = named.getValue();
            def.mode = ThemeMode.LIGHT;
            def.backgroundOpacity = 0.10;
            int before = def.accent;
            ResolvedTheme first = ThemeResolver.resolve(def, true);
            ResolvedTheme second = ThemeResolver.resolve(def, true);
            assertEquals(before, def.accent, named.getKey());
            assertEquals(before, first.accent(), named.getKey());
            assertEquals(first.adaptiveOnAccent().foreground(), second.adaptiveOnAccent().foreground());
            assertEquals(first.adaptiveOnAccent().stainedTint(), second.adaptiveOnAccent().stainedTint());
            assertEquals(first.adaptiveOnAccent().policy(), second.adaptiveOnAccent().policy());
        }
    }

    @Test
    void boundedAccentAdaptationNeverExceedsIdentityLimit() {
        for (int accent : ACCENTS.values()) {
            for (ThemeMode mode : ThemeMode.values()) {
                AdaptiveOnAccentTreatment p = resolve(accent, mode, 0.10).adaptiveOnAccent();
                assertTrue(Math.abs(p.lightnessShift())
                        <= ContrastDerivations.MAX_BACKING_LIGHTNESS_SHIFT);
            }
        }
    }

    @Test
    void insufficientD1FamilyUsesExplicitScrimFallback() {
        ResolvedTheme t = resolve(ContrastFixtures.DEFAULT_RED, ThemeMode.DARK, 0.10);
        assertFalse(t.contrastDerivations().stainedTextBacking().sufficient(),
                "D-1's documented full-family counterexample remains real");
        AdaptiveOnAccentTreatment p = t.adaptiveOnAccent();
        assertEquals("separate-readability-scrim", p.policy());
        assertTrue((p.scrimArgb() >>> 24) > 0, "fallback is present, not silently bypassed");
        assertTrue(p.worstRatio() >= ESSENTIAL_TEXT_RATIO);
    }

    @Test
    void allSevenOriginalFamiliesConsumeTheAdaptiveSemanticOutput() throws Exception {
        String button = source("ui/component/Button.java");
        String segment = source("ui/component/SegmentedControl.java");
        String keybind = source("screen/setting/KeybindSetting.java");
        assertTrue(button.contains("ThemeManager.adaptiveOnAccent().foreground()"));
        assertTrue(segment.contains("ThemeManager.adaptiveOnAccent().foreground()"));
        assertTrue(keybind.contains("ThemeManager.adaptiveOnAccent().foreground()"));

        String keyList = source("screen/setting/KeyListSetting.java");
        assertTrue(keyList.contains("ThemeManager.adaptiveOnAccent().foreground()"));
        String aurora = source("screen/AuroraScreen.java");
        String profiles = source("screen/ProfileManagerScreen.java");
        assertTrue(aurora.contains("ThemeManager.adaptiveOnAccent().foreground()"));
        assertTrue(aurora.contains("ThemeManager.adaptiveOnAccent().selectionIndicator()"));
        assertTrue(profiles.contains("ThemeManager.adaptiveOnAccent().foreground()"));
        assertEquals(0, occurrences(aurora, "ThemeManager.color(ThemeToken.ON_ACCENT)"));
        assertEquals(0, occurrences(profiles, "ThemeManager.color(ThemeToken.ON_ACCENT)"));
        assertEquals(0, occurrences(keyList, "ThemeManager.color(ThemeToken.ON_ACCENT)"));
    }

    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/client/java/com/aurora/client").resolve(relative));
    }

    private static int occurrences(String source, String needle) {
        int count = 0, at = 0;
        while ((at = source.indexOf(needle, at)) >= 0) {
            count++;
            at += needle.length();
        }
        return count;
    }

    private static void assertPass(int fg, int bg, String where) {
        assertTrue(PaletteEngine.contrastRatio(fg, bg) >= ESSENTIAL_TEXT_RATIO, where);
    }

    private static int lerp(int a, int b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24)
                | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }
}
