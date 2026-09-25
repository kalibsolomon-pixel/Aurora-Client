package com.aurora.client.screen;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C-1 source-contract pins (the Phase B inventory-pin pattern —
 * AuroraScreen is not headless-instantiable, so the contract is pinned
 * against source): AuroraScreen's content scissor, render cull, pointer
 * hit-tests, tile hover, and focused-setting keyboard routing all derive
 * from ONE viewport truth, and the pre-C-1 ungated/literal patterns do not
 * silently return. The behavioral primitive itself is pinned by
 * {@code ClipBandTest}; these pins guard the host's adoption of it.
 */
class AuroraScreenViewportCouplingTest {

    private static final Path SCREEN = Path.of(
            "src/client/java/com/aurora/client/screen/AuroraScreen.java");

    private static String screenSource() {
        try {
            return Files.readString(SCREEN);
        } catch (java.io.IOException e) {
            return fail("AuroraScreen.java not readable from the test working dir: " + e);
        }
    }

    @Test
    void contentScissorsDeriveFromTheViewportBand() {
        String src = screenSource();
        // The scissor IS the viewport: both glass-pass scissors and the
        // content scissors must be the band's rectangle, not re-derived
        // literals (the old tree had four inlined copies of
        // boxY()+36/BOX_H-10 that disagreed with the cull band).
        assertTrue(src.contains("GlassSurface.enableScissor(g, vp.x, vp.y, vp.xEnd(), vp.yEnd())"),
                "the glass pass must scissor to the viewport band");
        assertTrue(src.contains("g.enableScissor(vp.x, vp.y, vp.xEnd(), vp.yEnd())"),
                "the content passes must scissor to the viewport band");
    }

    @Test
    void theUngatedScissorLiteralsAreGone() {
        String src = screenSource();
        assertFalse(src.contains("(int) (boxY() + 36)"),
                "the old inline scissor top literal must not return");
        assertFalse(src.contains("(int) (boxY() + BOX_H - 10)"),
                "the old inline scissor bottom literal must not return");
        assertFalse(src.contains("y + rh > my + 36 && y < my + 216"),
                "the old row cull band (12px tighter than the scissor) must not return");
        assertFalse(src.contains("cy + ch < viewTop() || cy > viewBot()"),
                "cardBounds must cull through the viewport, not the legacy band");
    }

    @Test
    void tileCullHoverAndClickUseTheViewport() {
        String src = screenSource();
        assertTrue(src.contains("cardBounds(int i, List<Module> mods, ClipBand vp)"),
                "the tile geometry walk carries the viewport (one cull answer)");
        assertTrue(src.contains("if (!vp.intersects(cx, cy, cw, ch)) return null;"),
                "the cull is the viewport intersection");
        assertTrue(src.contains("boolean hover = vp.contains(mouseX, mouseY)"),
                "tile hover must require the pointer on a visible pixel");
        assertTrue(src.contains("if (vp.contains(mouseX, mouseY)\n"
                        + "                        && mouseX >= b[0]"),
                "tile click must require the pointer on a visible pixel");
    }

    @Test
    void settingsClickWalkIsGatedToTheViewport() {
        String src = screenSource();
        assertTrue(src.contains("} else if (vp.contains(mouseX, mouseY)) {"),
                "the whole Settings-tab walk must be inside the viewport gate");
        assertTrue(src.contains("if (vp.intersects(mx + 4, y, 246, rh)\n"
                        + "                                && s.mouseClicked("),
                "rows skipped by the render walk must be skipped by the click walk");
    }

    @Test
    void focusedSettingKeyboardRoutingIsVisibilityGated() {
        String src = screenSource();
        // All three routers (keys, chars, wheel) must consult the
        // per-frame availability truth — an off-viewport focused setting
        // must not keep receiving action keys or eat the wheel.
        int gated = 0;
        int unguarded = 0;
        for (String router : new String[]{"focused.onScroll(vertical))",
                "focused.onKeyPress(_kev))", "focused.onCharTyped(_ev))"}) {
            int i = src.indexOf(router);
            if (i < 0) { fail("router disappeared: " + router); }
            String before = src.substring(Math.max(0, i - 60), i);
            if (before.contains("focusedSettingVisible")) gated++;
            else unguarded++;
        }
        assertEquals(3, gated, "every registry router must gate on focusedSettingVisible");
        assertEquals(0, unguarded);
    }

    @Test
    void settingsWalkFiresAvailabilityChanges() {
        String src = screenSource();
        assertTrue(src.contains("s.onInteractionAvailabilityChanged(\n"
                        + "                            controls.isEmpty() ? visible : anyControlAvailable);"),
                "the render walk must publish control-rect availability when hosted, "
                        + "and row availability for deferred/non-semantic rows");
    }

    @Test
    void clipBandAdoptionIsExactlyTheC8ConsumerSet() {
        // C-1's blast radius was AuroraScreen only; C-8 (the sanctioned
        // rollout the C-1 record deferred to) added exactly the two named
        // later consumers — the manager list viewport and the pack grid.
        // The pin keeps walking the tree so any FURTHER adopter still shows
        // up here and must be justified in this test.
        List<String> expected = List.of(
                "src/client/java/com/aurora/client/screen/AuroraScreen.java",
                "src/client/java/com/aurora/client/screen/ManagerListScreen.java",
                "src/client/java/com/aurora/client/screen/ResourcePackBrowserScreen.java");
        try (Stream<Path> files = Files.walk(Path.of("src/client/java"))) {
            List<String> adopters = new ArrayList<>();
            files.filter(p -> sourcePath(p).endsWith(".java"))
                    .filter(p -> !sourcePath(p).endsWith("ui/util/ClipBand.java"))
                    .filter(p -> !sourcePath(p).endsWith("DevPilot.java"))
                    .forEach(p -> {
                        try {
                            String s = Files.readString(p);
                            if (s.contains("new ClipBand(") || s.contains("ClipBand vp")) {
                                adopters.add(sourcePath(p));
                            }
                        } catch (java.io.IOException ignored) {
                        }
                    });
            assertTrue(expected.containsAll(adopters) && adopters.containsAll(expected),
                    "ClipBand adopters must be exactly the C-1/C-8 consumer set — found: " + adopters);
        } catch (java.io.IOException e) {
            fail("source walk failed: " + e);
        }
    }

    private static String sourcePath(Path path) {
        return path.toString().replace('\\', '/');
    }
}
