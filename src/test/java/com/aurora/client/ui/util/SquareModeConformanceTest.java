package com.aurora.client.ui.util;

import com.aurora.client.SourceTree;
import com.aurora.client.util.AuroraTheme;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/** Conformance checks for fixed square UI corners. */
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
    void everyUiRadiusIsZero() {
        assertEquals(0, AuroraTheme.RADIUS);
        assertEquals(0, AuroraTheme.RADIUS_SMALL);
        assertEquals(0, AuroraTheme.RADIUS_LARGE);
    }

    // ------------------------------------------------------------------
    // AuroraScreen: raised-glass chrome + embedded/glass tile paths.
    // ------------------------------------------------------------------

    @Test
    void auroraScreenChromeResolvesThroughRadiusSmallInBothPaths() {
        String src = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/AuroraScreen.java");
        // Isolated raised-glass path (paintGlassPass) — sidebar tabs,
        // Profiles, and the layout pair all share the resolved token.
        assertTrue(src.contains("chipGlass[i] = GlassSurface.card(g, catX, catY, navW(), TAB_H,"),
                "chip glass uses the resolved control radius");
        assertTrue(src.contains("profGlass = GlassSurface.card(g, profX, profY, navW(), TAB_H, ctrlRadius)"),
                "Profiles glass uses the resolved control radius");
        assertTrue(src.contains("layoutGlass[index] = GlassSurface.card(g, box.x(), box.top(), box.width(), box.height(), ctrlRadius"),
                "layout-button glass uses the resolved control radius");
        // Tiles intentionally split by state: neutral is embedded; enabled
        // is isolated stained glass. Both consume the same resolved radius.
        assertTrue(src.contains("MaterialSurface.embeddedControl(g, bounds.x(), bounds.y(layoutScroll()), bounds.width(), bounds.height(), ctrlRadius"),
                "neutral tile material uses the resolved control radius");
        assertTrue(src.contains("bounds.x(), bounds.y(layoutScroll()), bounds.width(), bounds.height(), ctrlRadius, true,"),
                "highlighted tile glass uses the resolved control radius");
        assertTrue(src.contains("MaterialSurface.module(g,cx,cy,cw,ch,tileRadius,on,hoverT)"),
                "opaque depth templates share the resolved radius in the unified path");
        // Content overlays — the same derivations, not independent literals.
        assertTrue(src.contains("size, size, ctrlRadius"), "flat layout fills use the same radius");
        assertTrue(src.contains("cw, ch, tileRadius"), "flat tile fills use the same radius");
        // One hoisted derivation per render pass (paintGlassPass,
        // renderLive, drawLayoutButton, renderModulesLive) — no scattered
        // per-surface lookups, no literal survivors.
        assertEquals(4, countOf(src, "AuroraTheme.RADIUS_SMALL"),
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
    void scrollbarThumbsUseSquareCorners() {
        String src = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/ui/component/ScrollbarChrome.java");
        assertTrue(src.contains("AuroraTheme.RADIUS, color"),
                "the shared scrollbar thumb follows the theme");
        String browser = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/ResourcePackBrowserScreen.java");
        assertTrue(browser.contains("AuroraTheme.RADIUS, col);"));
        assertTrue(browser.contains("AuroraTheme.RADIUS, thumbCol);"));
        String host = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/AuroraScreen.java");
        assertFalse(host.contains("MECHANICAL"), "the retired exemption is not documented as current");
    }

    // ------------------------------------------------------------------
    // Manager toast + pack-browser toast agree on the token.
    // ------------------------------------------------------------------

    @Test
    void managerToastUsesSquareCorners() {
        String src = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/ManagerListScreen.java");
        assertTrue(src.contains("toast.render(ctx, this.font, this.width, this.height,\n"
                        + "                AuroraTheme.RADIUS_SMALL);"),
                "the manager toast is ordinary feedback chrome — tokenized");
        assertFalse(src.contains("this.height, 4)"),
                "the toast literal 4 must not return");
        // The pack browser's toast already passed the token — both screens
        // now share one radius policy.
        String rpbs = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/ResourcePackBrowserScreen.java");
        assertTrue(rpbs.contains("AuroraTheme.RADIUS_SMALL"),
                "the pack browser toast keeps its tokenized radius");
    }

    // ------------------------------------------------------------------
    // Color picker preview, pad and strips.
    // ------------------------------------------------------------------

    @Test
    void colorPickerPreviewAndStripsUseSquareCorners() {
        String src = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/ColorPickerScreen.java");
        assertTrue(src.contains("Math.min(previewH / 2f, AuroraTheme.RADIUS_SMALL)"),
                "the preview sample uses the ColorSwatch roundness pattern");
        assertFalse(src.contains("previewW, previewH, 4,"),
                "the preview literal 4 must not return");
        assertEquals(0, countOf(src, ", 6, 1.0f, 0xFFFFFFFF);"), "no literal frame radius remains");
        assertEquals(3, countOf(src, ", radius, 1.0f, 0xFFFFFFFF);"), "pad/hue/alpha frames");
        assertEquals(3, countOf(src, "Cache.clipRounded(0, 0, "), "pad/hue/alpha clips");
        assertTrue(src.contains("return AuroraTheme.RADIUS_SMALL;"));
    }

    @Test
    void hudPanelsKeysAndSquareMinimapUseSquareCorners() {
        String hud = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/util/HudBackgrounds.java");
        assertTrue(hud.contains("case SOLID -> ctx.fill("));
        String keys = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/hud/module/KeystrokesModule.java");
        assertTrue(keys.contains("keycaps.draw(ctx, x, y, w, h, 0f, t,"));
        String minimap = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/hud/module/MinimapModule.java");
        assertTrue(minimap.contains("cfg.minimapCircular ? size * .5f\n                : 0f"));
        assertFalse(minimap.contains("ThemeCorners"));
    }

    @Test
    void hudCornerFieldsAndSettingsAreRetired() {
        for (var field : com.aurora.client.config.AuroraConfig.class.getFields()) {
            assertFalse(field.getName().endsWith("ThemeCorners"), field.getName());
        }
        String registry = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/FeatureRegistry.java");
        assertFalse(registry.contains("Theme Corner Radius"));
    }

    @Test
    void toggleAndSliderShapesUseSquareCorners() {
        String toggle = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/ui/component/ToggleSwitch.java");
        String slider = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/ui/component/Slider.java");
        assertFalse(toggle.contains("h / 2f;") || toggle.contains("h/2,"), "no fixed capsule radius on the switch");
        assertFalse(toggle.contains("drawCircleAA"), "the switch thumb uses square corners");
        assertTrue(countOf(toggle, "AuroraTheme.RADIUS") >= 4, "track, thumb and focus ring in both painters");
        assertFalse(slider.contains("TRACK_H / 2f,") || slider.contains("TRACK_H / 2f + 3f"),
                "no fixed capsule radius on the slider track or its rings");
        assertTrue(slider.contains("return AuroraTheme.RADIUS"), "the track follows the theme");
        assertTrue(slider.contains("AuroraTheme.RADIUS, knob()"), "the knob follows the theme");
    }

    @Test
    void hudEditorChromeUsesSquareCorners() {
        String src = read(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/HudEditorScreen.java");
        assertTrue(src.contains("ctx.fill("));
        assertFalse(src.contains("drawRoundedRectAA"),
                "HUD editor chrome uses square edges");
    }
}
