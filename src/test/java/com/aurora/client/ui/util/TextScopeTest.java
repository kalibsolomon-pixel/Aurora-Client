package com.aurora.client.ui.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The truth table behind {@code TextRendererMode#AURORA_ONLY} (owner ruling
 * 2026-11-17): windows style, the HUD never does — not even the editor
 * previews drawn inside a window — and a broken scope mixin degrades to the
 * coarser open-screen check rather than silently styling nothing.
 */
class TextScopeTest {

    @Test void windowScopeStylesOnlyInsideTheWindow() {
        assertTrue(TextScope.auroraOnly(false, true, false, false));
        assertTrue(TextScope.auroraOnly(false, true, false, true));
    }

    @Test void textOutsideAWindowStaysVanilla() {
        assertFalse(TextScope.auroraOnly(false, false, false, true),
                "an Aurora screen being open must not style the rest of the frame");
        assertFalse(TextScope.auroraOnly(false, false, false, false));
    }

    @Test void hudPassesAreNeverStyled() {
        assertFalse(TextScope.auroraOnly(true, false, false, false));
        assertFalse(TextScope.auroraOnly(true, false, false, true));
    }

    @Test void hudPassesInsideAWindowScopeStayVanilla() {
        // The HUD editor's module previews draw within the editor's window
        // scope; they must look the way they will in game.
        assertFalse(TextScope.auroraOnly(true, true, false, true));
        assertFalse(TextScope.auroraOnly(true, true, true, true));
    }

    @Test void failSoftFallsBackToTheOpenScreenCheck() {
        assertTrue(TextScope.auroraOnly(false, false, true, true),
                "without the scope mixin the mode still does something, coarser");
        assertFalse(TextScope.auroraOnly(false, false, true, false));
        // The fallback never fakes a window that is not open, but it also
        // ignores the (always empty) scope: the open screen decides.
        assertFalse(TextScope.auroraOnly(false, true, true, false));
    }
}
