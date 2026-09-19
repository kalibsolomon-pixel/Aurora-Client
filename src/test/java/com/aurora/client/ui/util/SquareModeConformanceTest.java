package com.aurora.client.ui.util;

import com.aurora.client.theme.ThemeRoundness;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C-7 Square-mode conformance pins. Two layers: the TOKEN table
 * (behavioral — the values every migrated site inherits) and the Wave-1
 * source contracts (migrated sites resolve through the token in BOTH glass
 * and flat paths; the audited literal radii do not silently return; the
 * mechanical/data exemptions stay explicit instead of being re-discovered
 * as "bugs" by future agents).
 */
class SquareModeConformanceTest {

    private static String read(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (java.io.IOException e) {
            return fail("source not readable: " + path);
        }
    }

    // ------------------------------------------------------------------
    // The token table every migrated site resolves through.
    // ------------------------------------------------------------------

    @Test
    void squareModeResolvesEveryRadiusTokenToZero() {
        // The shipped token table (ROUND / SLIGHTLY_ROUND / SQUARE).
        assertEquals(10, ThemeRoundness.ROUND.radius());
        assertEquals(6, ThemeRoundness.SLIGHTLY_ROUND.radius());
        assertEquals(0, ThemeRoundness.SQUARE.radius());
        assertEquals(14, ThemeRoundness.ROUND.radiusLarge());
        assertEquals(8, ThemeRoundness.SLIGHTLY_ROUND.radiusLarge());
        assertEquals(0, ThemeRoundness.SQUARE.radiusLarge());
        assertEquals(6, ThemeRoundness.ROUND.radiusSmall());
        assertEquals(3, ThemeRoundness.SLIGHTLY_ROUND.radiusSmall());
        assertEquals(0, ThemeRoundness.SQUARE.radiusSmall());
    }

    @Test
    void radiusSmallRoundEqualsTheHistoricalTileLiteral() {
        // The module tiles' former literal 6 equals radiusSmall's ROUND
        // value — the tile migration is ROUND-pixel-identical by construction.
        assertEquals(6, ThemeRoundness.ROUND.radiusSmall());
        assertEquals(3, ThemeRoundness.SLIGHTLY_ROUND.radiusSmall());
    }

    // ------------------------------------------------------------------
    // AuroraScreen: chips / layout buttons / tiles — glass AND flat paths.
    // ------------------------------------------------------------------

    @Test
    void auroraScreenChromeResolvesThroughRadiusSmallInBothPaths() {
        String src = read("src/client/java/com/aurora/client/screen/AuroraScreen.java");
        // Glass path (paintGlassPass) — chips, profiles chip, layout pair, tiles.
        assertTrue(src.contains("GlassSurface.control(g, bx + 8, catY, 64, TAB_H, ctrlRadius"),
                "chip glass surface uses the resolved control radius");
        assertTrue(src.contains("GlassSurface.control(g, bx + 8, profY, 64, TAB_H, ctrlRadius"),
                "profiles chip glass surface uses the resolved control radius");
        assertTrue(src.contains("GlassSurface.control(g, mx, my, 20, 20, ctrlRadius"),
                "layout-button glass surface uses the resolved control radius");
        assertTrue(src.contains("GlassSurface.control(g, b[0], b[1], b[2], b[3], ctrlRadius"),
                "tile glass surface uses the resolved control radius");
        // Flat/content path — the same derivations, not independent literals.
        assertTrue(src.contains("64, 22, ctrlRadius"), "flat chip fills use the same radius");
        assertTrue(src.contains("size, size, ctrlRadius"), "flat layout fills use the same radius");
        assertTrue(src.contains("cw, ch, tileRadius"), "flat tile fills use the same radius");
        // One hoisted derivation per render pass (paintGlassPass,
        // renderLive, drawLayoutButton, renderModulesLive) — no scattered
        // per-surface lookups, no literal survivors.
        assertEquals(4, countOf(src, "roundness().radiusSmall()"),
                "exactly four radiusSmall derivations — one per render pass");
    }

    private static int countOf(String src, String needle) {
        int count = 0;
        for (int i = src.indexOf(needle); i >= 0; i = src.indexOf(needle, i + 1)) count++;
        return count;
    }

    @Test
    void auroraScreenChromeLiteralsAreGone() {
        String src = read("src/client/java/com/aurora/client/screen/AuroraScreen.java");
        assertFalse(src.contains("64, TAB_H, 5"), "the chip literal 5 must not return");
        assertFalse(src.contains("64, 22, 5,"), "the flat chip literal 5 must not return");
        assertFalse(src.contains("20, 20, 4,"), "the layout-button literal 4 must not return");
        assertFalse(src.contains("size, size, 4,"), "the flat layout literal 4 must not return");
        assertFalse(src.contains("b[3], 6,"), "the tile glass literal 6 must not return");
        assertFalse(src.contains("cw, ch, 6,"), "the flat tile literal 6 must not return");
    }

    @Test
    void auroraScreenScrollbarThumbStaysAMechanicalCapsule() {
        String src = read("src/client/java/com/aurora/client/screen/AuroraScreen.java");
        // MECHANICAL exemption (C-7 ruling): the 3px thumb's capsule radius 2
        // is direct-manipulation chrome (the toggle/slider track family) —
        // pinned so it is not re-filed as a Square-mode bug.
        assertTrue(src.contains("thumbY, 3, thumbH, 2,"),
                "the scrollbar thumb keeps its mechanical capsule radius");
        assertTrue(src.contains("MECHANICAL"),
                "the exemption stays documented at the site");
    }

    // ------------------------------------------------------------------
    // Manager toast + pack-browser toast agree on the token.
    // ------------------------------------------------------------------

    @Test
    void managerToastRoutesThroughThemeRoundness() {
        String src = read("src/client/java/com/aurora/client/screen/ManagerListScreen.java");
        assertTrue(src.contains("toast.render(ctx, this.font, this.width, this.height,\n"
                        + "                ThemeManager.current().roundness().radiusSmall());"),
                "the manager toast is ordinary feedback chrome — tokenized");
        assertFalse(src.contains("this.height, 4)"),
                "the toast literal 4 must not return");
        // The pack browser's toast already passed the token — both screens
        // now share one radius policy.
        String rpbs = read("src/client/java/com/aurora/client/screen/ResourcePackBrowserScreen.java");
        assertTrue(rpbs.contains("AuroraTheme.RADIUS_SMALL") || rpbs.contains("radiusSmall()"),
                "the pack browser toast keeps its tokenized radius");
    }

    // ------------------------------------------------------------------
    // ColorPicker: preview tokenized (a color SAMPLE), strips exempt (data).
    // ------------------------------------------------------------------

    @Test
    void colorPickerPreviewFollowsThemeRoundnessStripsStayDataDriven() {
        String src = read("src/client/java/com/aurora/client/screen/ColorPickerScreen.java");
        assertTrue(src.contains("Math.min(previewH / 2f, ThemeManager.current().roundness().radiusSmall())"),
                "the preview sample uses the ColorSwatch roundness pattern");
        assertFalse(src.contains("previewW, previewH, 4,"),
                "the preview literal 4 must not return");
        // The three gradient-surface frames keep their data radius 6 — the
        // exemption is deliberate and documented at each site.
        assertEquals(3, countOf(src, ", 6, 1.0f, 0xFFFFFFFF);"),
                "pad/hue/alpha frames keep the data-driven radius 6");
        assertTrue(countOf(src.toLowerCase(), "data-driven exemption") >= 3,
                "each exempt frame documents the ruling");
    }

    // ------------------------------------------------------------------
    // Explicit exemptions: Keystrokes keycaps, HudBackgrounds panels.
    // ------------------------------------------------------------------

    @Test
    void keystrokesKeycapRadiusIsAnExplicitRepresentationalExemption() {
        String src = read("src/client/java/com/aurora/client/hud/module/KeystrokesModule.java");
        assertTrue(src.contains("private static final int KEY_RADIUS = 3;"),
                "the keycap radius stays the representational constant");
        assertTrue(src.contains("Square-mode-exempt"),
                "the exemption stays documented at the constant");
    }

    @Test
    void hudBackgroundPanelsKeepTheirSanctionedBoxyDeviation() {
        String src = read("src/client/java/com/aurora/client/util/HudBackgrounds.java");
        assertTrue(src.contains("private static final int RADIUS = 2;"),
                "the HUD panel radius stays the sanctioned constant");
        assertTrue(src.contains("Square-mode-exempt"),
                "the sanction stays documented at the constant");
    }

    // ------------------------------------------------------------------
    // Mechanical/circular exemption sweep — Square mode must not destroy
    // mechanical affordances (representative pins).
    // ------------------------------------------------------------------

    @Test
    void toggleAndSliderMechanicalShapesAreNotSquared() {
        String toggle = read("src/client/java/com/aurora/client/ui/component/ToggleSwitch.java");
        String slider = read("src/client/java/com/aurora/client/ui/component/Slider.java");
        // The capsule tracks and circular knobs are the recorded mechanical
        // exemptions (AGENTS.md §5: "Knobs/thumbs stay circular in every
        // mode"); ToggleSwitch's own comment records why radiusSmall once
        // collapsed the track. These pins keep them that way.
        assertTrue(toggle.contains("float radius = h / 2f;"),
                "the toggle track stays a capsule");
        assertTrue(toggle.contains("made the track collapse"),
                "the historical rationale stays documented");
        assertTrue(slider.contains("TRACK_H / 2f") && slider.contains("KNOB_R"),
                "the slider track stays a capsule and the knob circular");
    }

    @Test
    void hudEditorChromeIsAlreadySquareConformantInBothModes() {
        String src = read("src/client/java/com/aurora/client/screen/HudEditorScreen.java");
        // C-7 ruling: the editor's status-overlay chrome (outlines, badges,
        // label plates) is plain fill rectangles — square in BOTH modes, so
        // Square conformance is satisfied by construction. ROUND-mode
        // rounding for it is deliberately NOT added in Wave 1 (no
        // design-language requirement; a later geometry task if ever ruled).
        assertTrue(src.contains("ctx.fill("));
        assertFalse(src.contains("drawRoundedRectAA"),
                "Wave 1 adds no rounding to the HUD editor chrome");
    }
}
