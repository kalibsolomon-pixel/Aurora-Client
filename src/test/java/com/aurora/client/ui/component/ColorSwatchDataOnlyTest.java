package com.aurora.client.ui.component;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B E-class cleanup: the Waypoint manager's color chips are
 * DISPLAY-ONLY data samples. They opt out of the interactive vocabulary
 * through {@link ColorSwatch#dataOnly()} — no hover response (the spurious
 * legacy snap-in halo), no selection/focus paint, no press, no click
 * consumption, no callback — while the literal represented color keeps
 * rendering at rest, byte-identical to an interactive swatch's rest.
 * Interactive ColorSwatches (the canonical ColorSetting rows) are untouched.
 */
class ColorSwatchDataOnlyTest {

    private static final String SWATCH_SRC =
            "src/client/java/com/aurora/client/ui/component/ColorSwatch.java";
    private static final String WAYPOINTS_SRC =
            "src/client/java/com/aurora/client/screen/WaypointManagerScreen.java";

    private static String read(String path) throws Exception {
        return java.nio.file.Files.readString(java.nio.file.Path.of(path));
    }

    private static ColorSwatch swatch(int color, Runnable onClick) {
        return new ColorSwatch(() -> color, onClick);
    }

    @Test
    void dataOnlySwatchRejectsPointerAndNeverRunsCallback() {
        AtomicInteger clicks = new AtomicInteger();
        ColorSwatch s = swatch(0xFF3B82F6, clicks::incrementAndGet).dataOnly();
        s.layout(10, 10, 16, 16);
        assertTrue(s.isDataOnly());
        // In-bounds left-click on a DATA sample: not consumed, no callback.
        assertFalse(s.mouseClicked(15, 15, 0));
        assertEquals(0, clicks.get());
        // The interactive default is unchanged: same click consumes + runs.
        ColorSwatch live = swatch(0xFF3B82F6, clicks::incrementAndGet);
        live.layout(10, 10, 16, 16);
        assertFalse(live.isDataOnly());
        assertTrue(live.mouseClicked(15, 15, 0));
        assertEquals(1, clicks.get());
    }

    @Test
    void dataOnlyRenderNeverComputesInteractionState() throws Exception {
        // Source-pinned (render needs GL headless-impossibly): the data-only
        // branch returns straight after the rest template blit — before the
        // hover computation, the halo, the selection ring, and the focus
        // hairline. mouseX/mouseY are unused.
        String src = read(SWATCH_SRC);
        int i = src.indexOf("if (dataOnly) {");
        assertTrue(i >= 0, "data-only render branch not found");
        String branch = src.substring(i, src.indexOf("return;\n        }", i));
        assertTrue(branch.contains("blitSwatchTemplate"),
                "data-only renders the shared rest template (one blit, no live chrome)");
        int hoverCompute = src.indexOf("boolean hover = !disabled && inBounds(");
        assertTrue(hoverCompute > i, "the hover computation must come after (never reach) the data-only branch");
        assertFalse(branch.contains("hoverAnim.update"), "no animator advance");
        assertFalse(branch.contains("drawRoundedOutlineAA"), "no halo/ring/hairline chrome");
    }

    @Test
    void dataOnlyModeCannotLeakBetweenInstances() throws Exception {
        // Instance-scoped flag; no static/shared interaction state exists.
        ColorSwatch a = swatch(1, null).dataOnly();
        ColorSwatch b = swatch(2, null);
        ColorSwatch c = swatch(3, null).canonicalStates();
        assertTrue(a.isDataOnly());
        assertFalse(b.isDataOnly());
        assertFalse(c.isDataOnly());
        assertFalse(b.canonical());
        assertFalse(a.canonical());
        assertTrue(c.canonical());
    }

    @Test
    void literalSampleStaysLiteralInEveryMode() {
        // The pilot's literal-color invariant: the sample ARGB is the stored
        // color verbatim for data-only, canonical rest/hover, and legacy
        // rest; only the LEGACY disabled branch ever modified it (pinned by
        // ColorSwatchPilotTest as unreachable from production).
        ColorSwatch data = swatch(0x80FF8000, null).dataOnly();
        ColorSwatch canon = swatch(0x80FF8000, null).canonicalStates();
        ColorSwatch legacy = swatch(0x80FF8000, null);
        for (ColorSwatch s : new ColorSwatch[]{data, canon, legacy}) {
            assertEquals(0xFFFF8000, s.sampleArgb(0x80FF8000, false));
        }
    }

    @Test
    void waypointChipsAdoptDataOnlyAndRemainPureData() throws Exception {
        String src = read(WAYPOINTS_SRC);
        assertTrue(src.contains("new ColorSwatch(() -> wp.color, null).checkerboard(true).dataOnly()"),
                "the manager's chips construct through the data-only path");
        // The chip's entire call surface is layout + render: no clicks, no
        // focus paint, no selection. (The manager's SemanticActionControls
        // belong to the row action BUTTONS — Copy/Color/Delete — which own
        // the edit action the chip deliberately does not.)
        assertFalse(src.contains("swatch.mouseClicked"), "the chip owns no clicks");
        assertFalse(src.contains("swatch.focusedVisual"), "the chip owns no focus paint");
        assertFalse(src.contains("swatch.selected("), "selection is meaningless for data");
        long chipCalls = java.util.regex.Pattern.compile("\\bswatch\\.")
                .matcher(src).results().count();
        assertEquals(2, chipCalls, "only swatch.layout(...) and swatch.render(...) are called");
    }

    @Test
    void interactiveColorSettingRemainsCanonical() throws Exception {
        String src = read("src/client/java/com/aurora/client/screen/setting/ColorSetting.java");
        assertTrue(src.contains("new ColorSetting(\"Custom Accent\",")
                        || src.contains("this.swatch = new ColorSwatch(getter, null).canonicalStates();"),
                "sanity anchor");
        assertTrue(src.contains("new ColorSwatch(getter, null).canonicalStates()"),
                "ColorSetting's swatch stays canonical — data-only cannot leak in");
        assertFalse(src.contains("dataOnly"), "no data-only adoption in interactive rows");
    }

    @Test
    void postCleanupLegacyHoverAnimInventory() throws Exception {
        // Production `new HoverAnim(` after the cleanup: exactly the
        // documented three — ColorSwatch's legacy default field (kept as the
        // component's default interactive behavior; production now reaches
        // it never — ColorSetting is canonical, the chips are data-only),
        // Slider's default (ThemePreview mock only, untouched), and
        // HoverAnim.symmetric's internal factory construction.
        String swatch = read(SWATCH_SRC);
        String slider = read("src/client/java/com/aurora/client/ui/component/Slider.java");
        String hoverAnim = read("src/client/java/com/aurora/client/util/HoverAnim.java");
        assertEquals(1, swatch.split("new HoverAnim\\(", -1).length - 1);
        assertEquals(1, slider.split("new HoverAnim\\(", -1).length - 1);
        assertEquals(1, hoverAnim.split("new HoverAnim\\(", -1).length - 1);
        // ...and no other production file constructs one.
        java.nio.file.Path root = java.nio.file.Path.of("src/client/java/com/aurora/client");
        long others = java.util.stream.Stream.concat(
                        java.nio.file.Files.walk(root),
                        java.util.stream.Stream.empty())
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.toString().contains("DevPilot")
                        && !p.toString().contains(".mimosa")
                        && !p.toString().endsWith("ColorSwatch.java")
                        && !p.toString().endsWith("Slider.java")
                        && !p.toString().endsWith("HoverAnim.java"))
                .filter(p -> {
                    try {
                        return read(p.toString()).contains("new HoverAnim(");
                    } catch (Exception e) {
                        return false;
                    }
                }).count();
        assertEquals(0, others, "no other production construction site may exist");
    }
}
