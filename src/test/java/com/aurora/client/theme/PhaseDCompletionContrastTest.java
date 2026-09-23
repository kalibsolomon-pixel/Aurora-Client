package com.aurora.client.theme;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PhaseDCompletionContrastTest {
    private static ResolvedTheme resolve(int accent, ThemeMode mode, double opacity) {
        ThemeDefinition def = new ThemeDefinition();
        def.accent = accent;
        def.mode = mode;
        def.backgroundOpacity = opacity;
        return ThemeResolver.resolve(def, true);
    }

    @Test
    void lowOpacityEssentialPlatesAndControlsPassControlledBackdrops() {
        int[] representative = {0xFF080808, 0xFF444444, 0xFFBBBBBB, 0xFFF8F8F8};
        for (int accent : ContrastFixtures.ACCENTS.values()) {
            for (ThemeMode mode : ThemeMode.values()) {
                ResolvedTheme theme = resolve(accent, mode, 0.10);
                SemanticContrastTreatment s = theme.semanticContrast();
                int fg = theme.color(ThemeToken.ON_BACKGROUND);
                int window = theme.color(ThemeToken.WINDOW_FILL);
                for (int world : representative) {
                    int windowVisible = PaletteEngine.composite(window, world);
                    int rowBacking = PaletteEngine.composite(s.essentialPlate(), windowVisible);
                    assertRatio(fg, rowBacking, ContrastDerivations.ESSENTIAL_TEXT_RATIO,
                            "row " + mode + "/" + Integer.toHexString(accent));
                    int control = PaletteEngine.composite(s.controlTint(), world);
                    assertRatio(fg, control, ContrastDerivations.ESSENTIAL_TEXT_RATIO,
                            "control " + mode + "/" + Integer.toHexString(accent));
                }
            }
        }
    }

    @Test
    void fieldPlaceholderDisabledTooltipAndStatusRolesMeetTheirContracts() {
        for (int accent : ContrastFixtures.ACCENTS.values()) {
            for (ThemeMode mode : ThemeMode.values()) {
                ResolvedTheme theme = resolve(accent, mode, 0.10);
                SemanticContrastTreatment s = theme.semanticContrast();
                for (int world : ContrastFixtures.BACKDROPS) {
                    int field = PaletteEngine.composite(s.fieldTint(), world);
                    int placeholder = PaletteEngine.composite(s.placeholderText(), field);
                    assertRatio(placeholder, field, ContrastDerivations.SUPPLEMENTAL_MUTED_RATIO,
                            "placeholder " + mode);
                }
                assertRatio(s.disabledText(), s.disabledFill(),
                        ContrastDerivations.ESSENTIAL_TEXT_RATIO, "disabled " + mode);
                assertRatio(s.tooltipForeground(), s.tooltipBackground(),
                        ContrastDerivations.ESSENTIAL_TEXT_RATIO, "tooltip " + mode);
                int surface = theme.color(ThemeToken.SURFACE);
                assertRatio(s.errorIndicator(), surface,
                        ContrastDerivations.NON_TEXT_INDICATOR_RATIO, "error " + mode);
                assertRatio(s.warningIndicator(), surface,
                        ContrastDerivations.NON_TEXT_INDICATOR_RATIO, "warning " + mode);
                assertRatio(s.secondaryIndicator(), surface,
                        ContrastDerivations.NON_TEXT_INDICATOR_RATIO, "secondary " + mode);
            }
        }
    }

    @Test
    void focusAndMechanicalIndicatorsPassTheirIntendedBackings() {
        for (int accent : ContrastFixtures.ACCENTS.values()) {
            for (ThemeMode mode : ThemeMode.values()) {
                ResolvedTheme theme = resolve(accent, mode, 0.10);
                SemanticContrastTreatment s = theme.semanticContrast();
                for (int world : ContrastDerivations.WORST_BASES) {
                    int control = PaletteEngine.composite(s.controlTint(), world);
                    assertRatio(s.focusNeutral(), control,
                            ContrastDerivations.NON_TEXT_INDICATOR_RATIO, "neutral focus");
                }
                assertRatio(s.focusOnAccent(), theme.adaptiveOnAccent().selectedSegment(),
                        ContrastDerivations.NON_TEXT_INDICATOR_RATIO, "accent focus");
                assertRatio(s.mechanicalOn(), theme.color(ThemeToken.ACCENT),
                        ContrastDerivations.NON_TEXT_INDICATOR_RATIO, "on mechanical");
                assertRatio(s.mechanicalOff(), theme.color(ThemeToken.SURFACE_VARIANT),
                        ContrastDerivations.NON_TEXT_INDICATOR_RATIO, "off mechanical");
            }
        }
    }

    @Test
    void fieldPainterDistinguishesDisabledFromCoveredAndRestoresSelection() throws Exception {
        String source = Files.readString(Path.of(
                "src/client/java/com/aurora/client/mixin/EditBoxMixin.java"));
        assertTrue(source.contains("@Shadow private boolean isEditable"));
        assertTrue(source.contains("editable && GlassSurface.field"));
        assertTrue(source.contains("highlightPos != cursorPos"));
        assertFalse(source.contains("self.active"),
                "active=false remains modal containment, not semantic disability");
    }

    @Test
    void noProductionOnAccentOrLegacyFocusBypassRemains() throws Exception {
        Path root = Path.of("src/client/java/com/aurora/client");
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("DevPilot.java")).toList()) {
                String source = Files.readString(path);
                assertFalse(source.contains("ThemeManager.color(ThemeToken.ON_ACCENT)"), path.toString());
                assertFalse(source.contains("withAlpha(ThemeManager.color(ThemeToken.ACCENT), 0x99)"),
                        path.toString());
            }
        }
    }

    private static void assertRatio(int foreground, int backing, double minimum, String message) {
        assertTrue(PaletteEngine.contrastRatio(foreground, backing) >= minimum,
                message + " ratio=" + PaletteEngine.contrastRatio(foreground, backing));
    }
}
