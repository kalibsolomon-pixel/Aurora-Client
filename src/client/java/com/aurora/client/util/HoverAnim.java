package com.aurora.client.util;

/**
 * Reusable 0→1 transition with an animation-completion short-circuit.
 * Construct with a duration (and optionally an easing curve — smoothstep
 * by default); call {@link #update(boolean)} once per render frame with
 * the desired target. Returns the eased current T value.
 *
 * <p>Locks at 0 or 1 once stable so idle frames skip the time math
 * entirely — matches the perf pattern used elsewhere in Aurora.
 *
 * <p>If the target reverses mid-animation, the start time is recomputed so
 * the transition reverses seamlessly from its current position rather than
 * snapping back to 0 or 1. Note the corollary at rest: a first
 * {@code update(true)} from the locked-0 state back-dates the start by the
 * full duration and therefore settles instantly (hover-in snaps, hover-out
 * eases). Every existing consumer ships with that behavior; adopting this
 * class preserves it exactly, so migrate hand-rolled copies with their
 * curve — do not silently swap in a different one.
 */
public final class HoverAnim {

    /** Easing applied to the raw linear fraction on every advancing frame. */
    @FunctionalInterface
    public interface Easing {
        float ease(float t);
    }

    /** Symmetric smoothstep — the default curve. */
    public static final Easing SMOOTHSTEP = t -> t * t * (3f - 2f * t);

    /** {@link AuroraAnim#easeOutCubic} — fast start, gentle settle (Button's hover). */
    public static final Easing EASE_OUT_CUBIC = AuroraAnim::easeOutCubic;

    private final long durationMs;
    private final Easing ease;

    private long startMs = 0;
    private float currentT = 0f;
    private boolean lastTarget = false;

    public HoverAnim(long durationMs) {
        this(durationMs, SMOOTHSTEP);
    }

    public HoverAnim(long durationMs, Easing ease) {
        this.durationMs = durationMs;
        this.ease = ease;
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
        currentT = ease.ease(t);
        if (raw >= 1f) currentT = targetT;
        return currentT;
    }

    public float current() { return currentT; }
}
