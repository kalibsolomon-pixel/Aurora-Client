package com.aurora.client.ui.component;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B Button-hover pilot: the shared Button opts into the §8.3
 * canonical symmetric hover. These tests prove the COMPONENT's opt-in and
 * channel independence — the symmetric math itself (reversal mirroring,
 * settle locking, legacy snap pinning) is already covered by
 * {@link TogglePilotTest} and is deliberately not duplicated here.
 */
class ButtonHoverPilotTest {

    private static Button button() {
        return new Button("Test", () -> {});
    }

    @Test
    void buttonHoverDoesNotSnapFromRest() {
        Button b = button();
        // The legacy constructor's first update(true) from locked rest
        // returns exactly 1 (the snap-in this migration removes); the
        // canonical mode must still be mid-flight.
        float first = b.hoverAnimator().update(true);
        assertTrue(first < 0.5f, "Button hover must animate from rest, got " + first);
        float second = b.hoverAnimator().update(true);
        assertTrue(second >= first, "hover progress must not go backwards");
    }

    @Test
    void buttonHoverSettlesWithinItsCanonicalWindow() throws Exception {
        Button b = button();
        b.hoverAnimator().update(true);
        Thread.sleep(200);
        assertEquals(1f, b.hoverAnimator().update(true), 0f, "enter settles at exactly 1");
        b.hoverAnimator().update(false);
        Thread.sleep(200);
        assertEquals(0f, b.hoverAnimator().update(false), 0f, "exit settles at exactly 0");
    }

    @Test
    void pressChannelIsIndependentOfHover() throws Exception {
        Button b = button();
        b.hoverAnimator().update(true);
        Thread.sleep(60);
        float midHover = b.hoverAnimator().update(true);
        assertTrue(midHover > 0f && midHover < 1f, "expected mid-flight hover, got " + midHover);

        // Press while hover is mid-flight: the press stamp appears and the
        // hover channel is untouched (two independent channels composing).
        long before = pressStart(b);
        b.triggerPressAnimation();
        long after = pressStart(b);
        assertTrue(after >= before, "press stamp advances");
        assertEquals(midHover, b.hoverAnimator().current(), 0f,
                "pressing must not disturb hover progress");

        // And the converse: advancing hover must not restart or clear the press.
        float progressed = b.hoverAnimator().update(true);
        assertEquals(after, pressStart(b), "hover updates must not touch the press stamp");
        assertTrue(progressed >= midHover);
    }

    @Test
    void activationStillFiresExactlyOnceWhileHoverIsPartial() {
        // Hover is visual feedback, never activation gating (§11): a
        // partially-hovered button clicks normally.
        AtomicInteger clicks = new AtomicInteger();
        Button b = new Button("Test", clicks::incrementAndGet);
        b.layout(10, 20, 90, 18);
        b.hoverAnimator().update(true); // ~0 progress — nowhere near settled
        assertTrue(b.mouseClicked(15, 25, 0));
        assertEquals(1, clicks.get());
    }

    private static long pressStart(Button b) {
        try {
            var f = Button.class.getDeclaredField("pressDownStartMs");
            f.setAccessible(true);
            return f.getLong(b);
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }
}
