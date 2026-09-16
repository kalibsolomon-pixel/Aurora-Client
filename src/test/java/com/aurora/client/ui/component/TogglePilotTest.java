package com.aurora.client.ui.component;

import com.aurora.client.util.HoverAnim;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B ToggleSwitch pilot: the §8.3 symmetric hover animator and the
 * press-pulse shape. Render-side states (wash/ring/disabled pixels) are
 * runtime-verified by the DevPilot harness; these tests pin the time
 * mechanics the visuals ride on, plus the legacy HoverAnim compatibility
 * contract every existing consumer depends on.
 */
class TogglePilotTest {

    // ------------------------------------------------------------------
    // HoverAnim.symmetric — the §8.3 canonical hover mechanics
    // ------------------------------------------------------------------

    @Test
    void symmetricHoverDoesNotSnapFromRest() {
        HoverAnim hover = HoverAnim.symmetric(140L);
        // First frame after entering from locked rest: elapsed ~0 → the
        // eased value must still be near zero (the legacy animator returns
        // exactly 1 here — that snap is what this mode exists to remove).
        float first = hover.update(true);
        assertTrue(first < 0.5f, "enter from rest must animate, got " + first);
        float second = hover.update(true);
        assertTrue(second >= first, "progress must not go backwards");
        assertFalse(hover.isAnimating() && first == 0f && second == 0f,
                "animator must be in flight after enter");
    }

    @Test
    void symmetricHoverReversesContinuouslyFromMidFlight() throws Exception {
        HoverAnim hover = HoverAnim.symmetric(140L);
        hover.update(true);
        Thread.sleep(60); // ~mid-flight (raw ≈ 0.4)
        float mid = hover.update(true);
        assertTrue(mid > 0.05f && mid < 0.95f, "expected mid-flight, got " + mid);
        // Immediate reversal: continues from the current position, never
        // restarting from an endpoint.
        float reversed = hover.update(false);
        assertTrue(reversed <= mid + 0.05f, "reversal must not jump up");
        assertTrue(reversed >= mid - 0.35f,
                "reversal must continue from current progress, got " + reversed
                        + " from " + mid);
    }

    @Test
    void symmetricHoverSettlesAndLocks() throws Exception {
        HoverAnim hover = HoverAnim.symmetric(140L);
        hover.update(true);
        Thread.sleep(180);
        assertEquals(1f, hover.update(true), 0f, "enter must settle at exactly 1");
        assertFalse(hover.isAnimating());
        hover.update(false);
        Thread.sleep(180);
        assertEquals(0f, hover.update(false), 0f, "exit must settle at exactly 0");
        assertFalse(hover.isAnimating());
    }

    // ------------------------------------------------------------------
    // Legacy HoverAnim compatibility — existing consumers keep snap-in
    // ------------------------------------------------------------------

    @Test
    void legacyHoverAnimStillSnapsFromRest() {
        HoverAnim hover = new HoverAnim(140L);
        float first = hover.update(true);
        assertEquals(1f, first, 0f,
                "the legacy from-rest back-dating (snap-in) is shipped behavior — do not change silently");
    }

    // ------------------------------------------------------------------
    // ToggleSwitch press pulse — mechanical, one-shot, resolves itself
    // ------------------------------------------------------------------

    @Test
    void pressPulseRisesAndRecoversWithinItsWindow() {
        assertEquals(0f, ToggleSwitch.pressCompression(-1L), 0f, "no pulse before start");
        assertEquals(0f, ToggleSwitch.pressCompression(0L), 0f, "zero compression at the instant of press");
        assertTrue(ToggleSwitch.pressCompression(45L) > 0f
                        && ToggleSwitch.pressCompression(45L) < ToggleSwitch.PRESS_COMPRESSION,
                "compression rises during the 90ms press-down");
        assertEquals(ToggleSwitch.PRESS_COMPRESSION, ToggleSwitch.pressCompression(90L), 1e-4f,
                "peak compression at the end of the press-down");
        float late = ToggleSwitch.pressCompression(200L);
        assertTrue(late > 0f && late < ToggleSwitch.PRESS_COMPRESSION,
                "recovery eases back without overshoot");
        assertEquals(0f, ToggleSwitch.pressCompression(270L), 0f,
                "the pulse is one-shot — fully resolved after 270ms");
        assertEquals(0f, ToggleSwitch.pressCompression(10_000L), 0f,
                "no residual compression long after");
    }

    // ------------------------------------------------------------------
    // Toggle state plumbing — the overlapping-state model
    // ------------------------------------------------------------------

    @Test
    void toggleCallbackFiresWithTheFlippedValueExactlyOnce() {
        AtomicBoolean state = new AtomicBoolean(false);
        AtomicInteger calls = new AtomicInteger();
        ToggleSwitch toggle = new ToggleSwitch(state::get, v -> {
            calls.incrementAndGet();
            state.set(v);
        });
        // The pulse is feedback inside toggle(); the setter is the single
        // state mutation — one call per toggle, flipped value.
        toggle.toggle();
        assertEquals(1, calls.get());
        assertTrue(state.get());
        toggle.toggle();
        assertEquals(2, calls.get());
        assertFalse(state.get());
    }

}
