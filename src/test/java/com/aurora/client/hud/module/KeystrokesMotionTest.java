package com.aurora.client.hud.module;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeystrokesMotionTest {
    @Test void pressEasesOutAndSettlesExactly() {
        long leg = KeystrokesModule.PRESS_NS;
        assertEquals(0f, KeystrokesModule.pressValue(0f, true, 0, leg));
        float quarter = KeystrokesModule.pressValue(0f, true, leg / 4, leg);
        assertTrue(quarter > .5f, "ease-out covers most of the travel early: " + quarter);
        assertEquals(1f, KeystrokesModule.pressValue(0f, true, leg, leg));
        assertEquals(1f, KeystrokesModule.pressValue(0f, true, leg * 5, leg));
        assertTrue(KeystrokesModule.PRESS_NS < KeystrokesModule.RELEASE_NS, "the press lands faster than it recovers");
        assertTrue(KeystrokesModule.RELEASE_NS <= 200_000_000L, "release stays inside the recovery budget");
    }

    @Test void interruptedReleaseContinuesFromTheVisibleValue() {
        float midway = KeystrokesModule.pressValue(0f, true, KeystrokesModule.PRESS_NS / 3, KeystrokesModule.PRESS_NS);
        // Reversal starts where the cap is, never snapping to either end.
        assertEquals(midway, KeystrokesModule.pressValue(midway, false, 0, KeystrokesModule.RELEASE_NS), 1e-6);
        float later = KeystrokesModule.pressValue(midway, false, KeystrokesModule.RELEASE_NS / 4, KeystrokesModule.RELEASE_NS);
        assertTrue(later < midway && later > 0f);
        assertEquals(0f, KeystrokesModule.pressValue(midway, false, KeystrokesModule.RELEASE_NS, KeystrokesModule.RELEASE_NS));
    }

    @Test void legendsReadLikeAKeyboard() {
        assertEquals("Shift", KeystrokesModule.shortKeyName("Left Shift"));
        assertEquals("Ctrl", KeystrokesModule.shortKeyName("Left Control"));
        assertEquals("Alt", KeystrokesModule.shortKeyName("Right Alt"));
        assertEquals("Space", KeystrokesModule.shortKeyName("Space"));
        assertEquals("W", KeystrokesModule.shortKeyName(" W "));
        assertEquals("Scroll L", KeystrokesModule.shortKeyName("Scroll Lock"));
    }
}
