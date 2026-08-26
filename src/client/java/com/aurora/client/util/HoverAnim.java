package com.aurora.client.util;

/**
 * Reusable 0→1 transition with smoothstep easing and an animation-completion
 * short-circuit. Construct with a duration; call {@link #update(boolean)}
 * once per render frame with the desired target. Returns the eased current
 * T value.
 *
 * <p>Locks at 0 or 1 once stable so idle frames skip the time math
 * entirely — matches the perf pattern used elsewhere in Aurora.
 *
 * <p>If the target reverses mid-animation, the start time is recomputed so
 * the transition reverses seamlessly from its current position rather than
 * snapping back to 0 or 1.
 */
public final class HoverAnim {

    private final long durationMs;

    private long startMs = 0;
    private float currentT = 0f;
    private boolean lastTarget = false;

    public HoverAnim(long durationMs) {
        this.durationMs = durationMs;
    }

    public float update(boolean target) {
        if (target != lastTarget) {
            startMs = System.currentTimeMillis() - (long) ((1f - currentT) * durationMs);
            lastTarget = target;
        }
        float targetT = target ? 1f : 0f;
        if (currentT == targetT) return currentT;

        long elapsed = System.currentTimeMillis() - startMs;
        float raw = Math.min(1f, Math.max(0f, elapsed / (float) durationMs));
        float t = target ? raw : (1f - raw);
        currentT = t * t * (3f - 2f * t);
        if (raw >= 1f) currentT = targetT;
        return currentT;
    }

    public float current() { return currentT; }
}