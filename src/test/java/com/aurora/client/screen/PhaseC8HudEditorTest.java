package com.aurora.client.screen;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C-8, part 3 — the HUD editor's correctness items: the phantom
 * X-key hint (reworded to the real affordance — no key invented), the
 * disable action's persistence (immediate save, matching the grid path),
 * the resize on-screen clamp, and the resize-halo/X precedence ruling
 * (documented, pixel-exact, pinned here).
 */
class PhaseC8HudEditorTest {

    private static final Path SCREEN = Path.of(
            "src/client/java/com/aurora/client/screen/HudEditorScreen.java");

    private static String source() {
        try {
            return Files.readString(SCREEN);
        } catch (java.io.IOException e) {
            return fail("HudEditorScreen.java not readable from the test working dir: " + e);
        }
    }

    @Test
    void hintDescribesTheRealAffordanceAndNoPhantomKeyExists() {
        String src = source();
        assertTrue(src.contains("X badge: disable"),
                "the hint must describe the badge click (the truthful fix)");
        assertFalse(src.contains("|  X: disable"),
                "the phantom X-KEY claim must not return");
        // No hidden X-key binding was introduced: the screen routes no key
        // events of its own (Escape stays vanilla screen close).
        assertFalse(src.contains("GLFW_KEY_X"),
                "no X key binding may exist");
        assertFalse(src.contains("public boolean keyPressed"),
                "the screen must not grow a key handler to satisfy the old hint text");
    }

    @Test
    void disableViaRegistryPersistsLikeTheGridPath() {
        String src = source();
        int body = src.indexOf("private void disableViaRegistry");
        assertTrue(body >= 0, "disableViaRegistry exists");
        String method = src.substring(body, src.indexOf('}', src.indexOf("m.enabled = false;", body)) + 1);
        int setEnabled = method.indexOf("fm.setEnabled(false);");
        int save = method.indexOf("AuroraConfig.save();");
        assertTrue(setEnabled >= 0 && save > setEnabled,
                "the registry disable must save the config immediately (Module.setEnabled parity)");
    }

    @Test
    void resizeStaysOnScreen() {
        String src = source();
        assertTrue(src.contains("Math.max(0, Math.min(this.width - sw, newTopLeftX))"),
                "the resize result clamps horizontally into the screen");
        assertTrue(src.contains("Math.max(0, Math.min(this.height - sh, newTopLeftY))"),
                "the resize result clamps vertically into the screen");
    }

    @Test
    void xBadgePixelsAndCornerHandlePixelsAreDisjointWithXFirst() {
        // The painted affordances (constants mirrored from the screen):
        // X badge [w-11, w-2) x [2, 11); TR handle [w-2, w+2) x [-2, 2).
        int xLeft = -11, xRight = -2, xTop = 2, xBottom = 11;   // relative to (w, 0)
        int hLeft = -2, hRight = 2, hTop = -2, hBottom = 2;
        boolean overlap = xLeft < hRight && hLeft < xRight && xTop < hBottom && hTop < xBottom;
        assertFalse(overlap, "the X badge and the TR corner handle paint disjoint pixels");

        // And the click walk tests the X badge BEFORE the corner zones, so
        // X pixels disable and handle-adjacent pixels resize — explicit
        // precedence, not event-order luck.
        String src = source();
        int click = src.indexOf("public boolean mouseClicked");
        String body = src.substring(click);
        int xBranch = body.indexOf("X icon (only present on enabled modules)");
        int cornerBranch = body.indexOf("Corner-handle hit detection.");
        assertTrue(xBranch >= 0 && cornerBranch > xBranch,
                "the X badge hit test must precede the corner-handle zones");
    }
}
