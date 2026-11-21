package com.aurora.client.util;

/**
 * Frame-rate-independent landing ease and sticky settle latch for dropped
 * item physics ({@code ItemPhysicsPose}).
 *
 * <p>Asymmetric on purpose:
 * <ul>
 *   <li><b>Landing ({@code grounded == true}):</b> eases {@code groundT} from
 *       0 to 1 over {@link #LANDING_DURATION_NANOS} (~120 ms, matching ~7
 *       frames at 60 FPS) regardless of frame rate.</li>
 *   <li><b>Liftoff ({@code grounded == false}):</b> snaps {@code groundT}
 *       immediately to 0 so falling items never visibly tilt upward mid-air.</li>
 *   <li><b>Settle latch:</b> once {@code groundT} reaches 1 (or the item is
 *       first observed already resting on the ground), {@code settled} stays
 *       true for the lifetime of that entity so settled items that later fall
 *       off a broken block stay flat instead of snapping back into a tumble.</li>
 * </ul>
 */
public final class ItemPhysicsSmoothing {
    /** Duration for an airborne item to ease into its flat-on-ground pose. */
    public static final long LANDING_DURATION_NANOS = 120_000_000L;
    /** Cap elapsed time per update so a pause or GC stall does not jump. */
    public static final long MAX_DT_NANOS = 100_000_000L;

    public record Step(float groundT, boolean settled) {}

    private ItemPhysicsSmoothing() {}

    public static Step advance(boolean hasPrevious, float prevGroundT, boolean prevSettled,
                               boolean grounded, long elapsedNanos) {
        if (!hasPrevious) {
            float initial = grounded ? 1f : 0f;
            return new Step(initial, grounded);
        }
        if (!grounded) {
            return new Step(0f, prevSettled);
        }
        if (prevGroundT >= 1f) {
            return new Step(1f, true);
        }
        long dt = Math.max(0L, Math.min(elapsedNanos, MAX_DT_NANOS));
        float groundT = Math.max(0f, prevGroundT) + (float) ((double) dt / (double) LANDING_DURATION_NANOS);
        if (groundT >= 1f - 1e-4f) groundT = 1f;
        boolean settled = prevSettled || groundT >= 1f;
        return new Step(groundT, settled);
    }
}
