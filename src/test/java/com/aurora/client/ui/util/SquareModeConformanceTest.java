package com.aurora.client.ui.util;

import com.aurora.client.SourceTree;
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
            return Files.readString(SourceTree.resolve(path));
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
    // AuroraScreen: raised-glass chrome + embedded/glass tile paths.
    // ------------------------------------------------------------------

    @Test
    void auroraScreenChromeResolvesThroughRadiusSmallInBothPaths() {
        String src = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/AuroraScreen.java");
        // Isolated raised-glass path (paintGlassPass) — sidebar tabs,
        // Profiles, and the layout pair all share the resolved token.
        assertTrue(src.contains("chipGlass[i] = GlassSurface.card(g, catX, catY, TAB_W, TAB_H,"),
                "chip glass uses the resolved control radius");
        assertTrue(src.contains("profGlass = GlassSurface.card(g, profX, profY, TAB_W, TAB_H, ctrlRadius)"),
                "Profiles glass uses the resolved control radius");
        assertTrue(src.contains("layoutGlass[0] = GlassSurface.card(g, toolbarX(), my, TOOL, TOOL, ctrlRadius"),
                "layout-button glass uses the resolved control radius");
        // Tiles intentionally split by state: neutral is embedded; enabled
        // is isolated stained glass. Both consume the same resolved radius.
        assertTrue(src.contains("MaterialSurface.embeddedControl(g, b[0], b[1], b[2], b[3], ctrlRadius"),
                "neutral tile material uses the resolved control radius");
        assertTrue(src.contains("b[0], b[1], b[2], b[3], ctrlRadius, true,"),
                "highlighted tile glass uses the resolved control radius");
        // Content overlays — the same derivations, not independent literals.
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
        String src = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/AuroraScreen.java");
        assertFalse(src.contains("64, TAB_H, 5"), "the chip literal 5 must not return");
        assertFalse(src.contains("64, 22, 5,"), "the flat chip literal 5 must not return");
        assertFalse(src.contains("20, 20, 4,"), "the layout-button literal 4 must not return");
        assertFalse(src.contains("size, size, 4,"), "the flat layout literal 4 must not return");
        assertFalse(src.contains("b[3], 6,"), "the tile glass literal 6 must not return");
        assertFalse(src.contains("cw, ch, 6,"), "the flat tile literal 6 must not return");
    }

    @Test
    void scrollbarThumbsFollowTheCornerRadius() {
        // Owner ruling 2026-11-12 retired the C-7 MECHANICAL exemption: the
        // Corner Radius slider applies to every shape, scrollbar thumbs
        // included, through the capsule rule (full at the default, square at 0).
        String src = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/ui/component/ScrollbarChrome.java");
        assertTrue(src.contains("ThemeManager.current().roundness().capsule(3f)"),
                "the shared scrollbar thumb follows the theme");
        String browser = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/ResourcePackBrowserScreen.java");
        assertEquals(2, countOf(browser, "ThemeManager.current().roundness().capsule("),
                "both pack-browser thumbs follow the theme");
        String host = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/AuroraScreen.java");
        assertFalse(host.contains("MECHANICAL"), "the retired exemption is not documented as current");
    }

    // ------------------------------------------------------------------
    // Manager toast + pack-browser toast agree on the token.
    // ------------------------------------------------------------------

    @Test
    void managerToastRoutesThroughThemeRoundness() {
        String src = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/ManagerListScreen.java");
        assertTrue(src.contains("toast.render(ctx, this.font, this.width, this.height,\n"
                        + "                ThemeManager.current().roundness().radiusSmall());"),
                "the manager toast is ordinary feedback chrome — tokenized");
        assertFalse(src.contains("this.height, 4)"),
                "the toast literal 4 must not return");
        // The pack browser's toast already passed the token — both screens
        // now share one radius policy.
        String rpbs = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/ResourcePackBrowserScreen.java");
        assertTrue(rpbs.contains("AuroraTheme.RADIUS_SMALL") || rpbs.contains("radiusSmall()"),
                "the pack browser toast keeps its tokenized radius");
    }

    // ------------------------------------------------------------------
    // ColorPicker: preview tokenized (a color SAMPLE), strips exempt (data).
    // ------------------------------------------------------------------

    @Test
    void colorPickerPreviewAndStripsFollowThemeRoundness() {
        String src = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/ColorPickerScreen.java");
        assertTrue(src.contains("Math.min(previewH / 2f, ThemeManager.current().roundness().radiusSmall())"),
                "the preview sample uses the ColorSwatch roundness pattern");
        assertFalse(src.contains("previewW, previewH, 4,"),
                "the preview literal 4 must not return");
        // Owner ruling 2026-11-12: the pad/hue/alpha frames left the
        // data-driven exemption and use the theme's small radius (6 at the
        // default, so the factory look is unchanged).
        assertEquals(0, countOf(src, ", 6, 1.0f, 0xFFFFFFFF);"), "no literal frame radius remains");
        assertEquals(3, countOf(src, ", radius, 1.0f, 0xFFFFFFFF);"), "pad/hue/alpha frames");
        assertEquals(3, countOf(src, "Cache.clipRounded(0, 0, "), "pad/hue/alpha clips");
        assertTrue(src.contains("return ThemeManager.current().roundness().radiusSmall();"));
    }

    // ------------------------------------------------------------------
    // Explicit exemptions: Keystrokes keycaps, HudBackgrounds panels.
    // ------------------------------------------------------------------

    @Test
    void keystrokesKeycapRadiusIsAnExplicitRepresentationalExemption() {
        String src = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/hud/module/KeystrokesModule.java");
        assertTrue(src.contains("private static final int KEY_RADIUS = 3;"),
                "the keycap radius stays the representational constant");
        assertTrue(src.contains("Square-mode-exempt"),
                "the exemption stays documented at the constant");
    }

    @Test
    void hudBackgroundPanelsKeepTheirSanctionedBoxyDeviation() {
        String src = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/util/HudBackgrounds.java");
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
    void toggleAndSliderShapesFollowTheCornerRadius() {
        String toggle = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/ui/component/ToggleSwitch.java");
        String slider = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/ui/component/Slider.java");
        // Owner ruling 2026-11-12 (supersedes "knobs/thumbs stay circular"):
        // tracks and knobs follow the Corner Radius through the capsule rule
        // — unchanged at the default radius, square at 0.
        assertFalse(toggle.contains("h / 2f;") || toggle.contains("h/2,"), "no fixed capsule radius on the switch");
        assertFalse(toggle.contains("drawCircleAA"), "the switch thumb is a theme-radius shape, not a fixed circle");
        assertTrue(countOf(toggle, ".capsule(") >= 4, "track, thumb and focus ring in both painters");
        assertFalse(slider.contains("TRACK_H / 2f,") || slider.contains("TRACK_H / 2f + 3f"),
                "no fixed capsule radius on the slider track or its rings");
        assertTrue(slider.contains("roundness().capsule(TRACK_H)"), "the track follows the theme");
        assertTrue(slider.contains("roundness().capsule(knobR * 2f)"), "the knob follows the theme");
    }

    @Test
    void hudEditorChromeIsAlreadySquareConformantInBothModes() {
        String src = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/HudEditorScreen.java");
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
