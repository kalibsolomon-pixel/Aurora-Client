package com.aurora.client.ui.interaction;

import org.junit.jupiter.api.Test;

import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The pure state machine behind press-again destructive confirmation
 * (design language §8.7): press 1 arms, press 2 within the window fires,
 * the timeout or an explicit disarm returns to idle — with an injected
 * clock so every boundary is exact.
 */
class ArmedActionTest {

    /** A mutable clock: tests advance time explicitly, no sleeping. */
    private static final class ManualClock implements LongSupplier {
        long now;
        @Override public long getAsLong() { return now; }
    }

    private static ArmedAction armed(ManualClock clock) {
        return new ArmedAction(clock, ArmedAction.TIMEOUT_MS);
    }

    @Test
    void firstPressArmsWithoutFiring() {
        ManualClock clock = new ManualClock();
        ArmedAction a = armed(clock);
        assertFalse(a.isArmed(), "starts idle");
        assertFalse(a.press(), "the first press arms, it does not fire");
        assertTrue(a.isArmed());
    }

    @Test
    void secondPressWithinTheWindowFiresAndDisarms() {
        ManualClock clock = new ManualClock();
        ArmedAction a = armed(clock);
        assertFalse(a.press());
        clock.now = ArmedAction.TIMEOUT_MS - 1;
        assertTrue(a.press(), "the second press inside the window fires");
        assertFalse(a.isArmed(), "firing disarms");
    }

    @Test
    void pressAfterTheWindowReArmsInsteadOfFiring() {
        ManualClock clock = new ManualClock();
        ArmedAction a = armed(clock);
        assertFalse(a.press());
        clock.now = ArmedAction.TIMEOUT_MS; // the window is half-open: at exactly the timeout it has lapsed
        assertFalse(a.press(), "a press after the window re-arms rather than fires");
        assertTrue(a.isArmed());
    }

    @Test
    void isArmedExpiresLazilyAtTheTimeout() {
        ManualClock clock = new ManualClock();
        ArmedAction a = armed(clock);
        assertFalse(a.press());
        clock.now = ArmedAction.TIMEOUT_MS - 1;
        assertTrue(a.isArmed(), "still armed one tick before the window closes");
        clock.now = ArmedAction.TIMEOUT_MS;
        assertFalse(a.isArmed(), "polling after the window expires the arming (the label's restore path)");
    }

    @Test
    void explicitDisarmCancelsAndTheNextPressArmsAgain() {
        ManualClock clock = new ManualClock();
        ArmedAction a = armed(clock);
        assertFalse(a.press());
        a.disarm();
        assertFalse(a.isArmed());
        clock.now = 10;
        assertFalse(a.press(), "after a disarm the next press is a fresh first press");
        assertTrue(a.isArmed());
    }

    @Test
    void firingThenPressingAgainStartsANewCycle() {
        ManualClock clock = new ManualClock();
        ArmedAction a = armed(clock);
        assertFalse(a.press());
        clock.now = 500;
        assertTrue(a.press());
        clock.now = 600;
        assertFalse(a.press(), "the press after a fire arms a new cycle");
        clock.now = 700;
        assertTrue(a.press());
    }

    @Test
    void aClockThatStepsBackwardsLapsesTheArming() {
        ManualClock clock = new ManualClock();
        ArmedAction a = armed(clock);
        assertFalse(a.press());
        clock.now = -1;
        assertFalse(a.isArmed(), "a clock reset to before the arming lapses it instead of arming forever");
    }

    @Test
    void aCustomWindowIsHonoured() {
        ManualClock clock = new ManualClock();
        ArmedAction a = new ArmedAction(clock, 100);
        assertFalse(a.press());
        clock.now = 99;
        assertTrue(a.press(), "the injected 100 ms window fires at 99 ms");
    }
}
