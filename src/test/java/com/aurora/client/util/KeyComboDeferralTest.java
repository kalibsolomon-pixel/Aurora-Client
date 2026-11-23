package com.aurora.client.util;

import com.aurora.client.config.KeybindMigrator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.aurora.client.util.KeyCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/** A modifier that may start a combo waits for its release (owner request 2026-11-22). */
class KeyComboDeferralTest {
    private static final int LCTRL_STORED = KeybindMigrator.fromGlfw(341);
    private static final int RCTRL_STORED = KeybindMigrator.fromGlfw(345);
    private static final int LSHIFT_STORED = KeybindMigrator.fromGlfw(340);
    private static final int RIGHT = 79, P = 19;

    /** Sprint on Left Ctrl, Next Track on Ctrl + Right, sneak on Left Shift with no Shift combo. */
    @BeforeEach void bindings() {
        Registry.begin();
        Registry.add(LCTRL_STORED);
        Registry.add(of(RIGHT, CTRL));
        Registry.add(LSHIFT_STORED);
        Registry.add(P);
        Registry.commit();
        Deferral.reset();
        Deferral.setEnabled(true);
    }

    @AfterEach void clear() {
        Deferral.setEnabled(false);
        Registry.begin();
        Registry.commit();
    }

    @Test void onlyModifiersSomeComboUsesWait() {
        assertEquals(CTRL, Registry.prefixes());
        assertTrue(Deferral.defers(LCTRL_STORED));
        assertTrue(Deferral.defers(RCTRL_STORED), "either side: a combo's Ctrl is either Ctrl");
        assertFalse(Deferral.defers(LSHIFT_STORED), "no Shift combo: sneak behaves as before");
        assertFalse(Deferral.defers(P));
        Deferral.setEnabled(false);
        assertFalse(Deferral.defers(LCTRL_STORED), "without the combo mixin's key events nothing waits");
    }

    @Test void aModifierLetGoAloneTapsOnce() {
        assertTrue(Deferral.press(LCTRL_STORED, 1_000), "Ctrl down: sprint waits");
        assertTrue(Deferral.waiting(LCTRL_STORED));
        assertTrue(Deferral.press(LCTRL_STORED, 1_050), "a repeat still waits");
        assertTrue(Deferral.release(LCTRL_STORED, true), "let go alone: it taps");
        assertTrue(Deferral.tapped(LCTRL_STORED));
        Deferral.endTick();
        assertFalse(Deferral.tapped(LCTRL_STORED), "for one tick");
    }

    @Test void aCompletedComboSwallowsTheModifier() {
        Deferral.press(LCTRL_STORED, 1_000);
        assertEquals(CTRL, comboBits(RIGHT, CTRL), "Right with Ctrl held completes Ctrl + Right");
        Deferral.consume(comboBits(RIGHT, CTRL));
        assertEquals(0, Deferral.promoteDue(5_000), "a used modifier never becomes a hold");
        assertFalse(Deferral.release(LCTRL_STORED, true), "sprint never fired on the way to the arrow");
        assertEquals(0, comboBits(P, CTRL), "P has no Ctrl combo: pressing it uses nothing");
    }

    @Test void holdingPastTheThresholdBecomesAHold() {
        Deferral.press(LCTRL_STORED, 1_000);
        assertEquals(0, Deferral.promoteDue(1_000 + Deferral.HOLD_MS - 1));
        int promoted = Deferral.promoteDue(1_000 + Deferral.HOLD_MS);
        assertNotEquals(0, promoted);
        assertTrue(Deferral.held(LCTRL_STORED));
        assertFalse(Deferral.waiting(LCTRL_STORED));
        assertFalse(Deferral.press(LCTRL_STORED, 2_000), "a held modifier's repeats pass through");
        assertFalse(Deferral.release(LCTRL_STORED, true), "after a hold, letting go taps nothing");
        assertFalse(Deferral.held(LCTRL_STORED));
    }

    @Test void aScreenTakesTheTap() {
        Deferral.press(LCTRL_STORED, 1_000);
        assertFalse(Deferral.release(LCTRL_STORED, false), "let go while a screen is open: no tap");
        Deferral.press(LCTRL_STORED, 2_000);
        Deferral.cancelHeld();
        assertFalse(Deferral.release(LCTRL_STORED, true), "held across a screen: no tap");
    }

    @Test void bothSidesAreTrackedApart() {
        Deferral.press(LCTRL_STORED, 1_000);
        Deferral.press(RCTRL_STORED, 1_000);
        assertTrue(Deferral.release(RCTRL_STORED, true));
        assertTrue(Deferral.waiting(LCTRL_STORED), "Left Ctrl is still down and waiting");
    }
}
