package com.aurora.client.ui.interaction;

import java.util.function.LongSupplier;

/**
 * Press-again confirmation for a destructive control (design language §8.7).
 *
 * <p>The two-press pattern, as one pure state machine: the first
 * {@link #press()} arms (the caller relabels to "Confirm …"), a second press
 * within {@link #TIMEOUT_MS} fires (returns {@code true} — run the action),
 * and anything else — the timeout elapsing, an explicit {@link #disarm()}
 * (another control clicked, focus lost, Escape pressed) — returns to idle.
 * Time is injected so the machine is unit-testable; screens pass
 * {@link System#currentTimeMillis} and poll {@link #isArmed()} each frame,
 * which expires a stale arming lazily so the resting label comes back on the
 * timeout without a timer.
 *
 * <p>Core module (ADR 0003): no Minecraft types here. The armed look itself
 * is the caller's business — the shared rule is that state is carried by the
 * label and the existing destructive styling, never by a new tint (AGENTS.md
 * §6 convention 9).
 */
public final class ArmedAction {

    /** The confirmation window: 3 s from the first press. */
    public static final long TIMEOUT_MS = 3000L;

    private final LongSupplier clock;
    private final long timeoutMs;

    private boolean armed;
    private long armedAtMs;

    /** The production clock ({@link System#currentTimeMillis}) and {@link #TIMEOUT_MS}. */
    public ArmedAction() {
        this(System::currentTimeMillis, TIMEOUT_MS);
    }

    /** Testable construction: inject the time source and the window length. */
    public ArmedAction(LongSupplier clock, long timeoutMs) {
        this.clock = clock;
        this.timeoutMs = timeoutMs;
    }

    /**
     * One press of the control. The first press arms; a second press while
     * still armed fires — disarms and returns {@code true}, the caller's cue
     * to run the destructive action. A press after the window elapsed (or a
     * first press) arms afresh and returns {@code false}.
     */
    public boolean press() {
        long now = clock.getAsLong();
        if (armed(now)) {
            disarm();
            return true;
        }
        armed = true;
        armedAtMs = now;
        return false;
    }

    /**
     * Whether the confirmation is currently armed. Lazily expires an arming
     * whose window has elapsed, so polling this each frame is also the
     * timeout's label-restore path.
     */
    public boolean isArmed() {
        if (!armed) return false;
        if (!armed(clock.getAsLong())) {
            armed = false;
            return false;
        }
        return true;
    }

    /** Explicit disarm: another control was clicked, focus was lost, Escape was pressed. */
    public void disarm() {
        armed = false;
    }

    private boolean armed(long now) {
        return armed && now - armedAtMs >= 0 && now - armedAtMs < timeoutMs;
    }
}
