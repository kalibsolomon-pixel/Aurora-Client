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
 *
 * <p><b>Symmetric mode</b> ({@link #symmetric}): design language §8.3's
 * canonical hover — 140 ms enter AND 140 ms exit, no snap from rest. The
 * first {@code update(true)} from locked-0 starts the transition at zero
 * progress instead of back-dating, and mid-flight reversals continue from
 * the raw (un-eased) progress fraction, so interruption is continuous in
 * both directions. Phase B pilots this mode; the legacy constructors keep
 * the historical snap-in behavior byte-for-byte until the rollout re-trains
 * each consumer deliberately.
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
    /** Legacy: back-date from rest (snap-in). Symmetric mode: start at zero progress. */
    private final boolean backDateFromRest;

    private long startMs = 0;
    private float currentT = 0f;
    /** Raw (un-eased) progress of the active leg; 1 = the last leg completed. */
    private float lastRaw = 1f;
    private boolean lastTarget = false;

    /** Legacy constructor — hover-in snaps from rest (every pre-Phase-B consumer). */
    public HoverAnim(long durationMs) {
        this(durationMs, SMOOTHSTEP);
    }

    /** Legacy constructor — hover-in snaps from rest (every pre-Phase-B consumer). */
    public HoverAnim(long durationMs, Easing ease) {
        this(durationMs, ease, true);
    }

    private HoverAnim(long durationMs, Easing ease, boolean backDateFromRest) {
        this.durationMs = durationMs;
        this.ease = ease;
        this.backDateFromRest = backDateFromRest;
    }

    /**
     * The design language §8.3 canonical hover animator: symmetric,
     * reversible, continuous — entering from rest animates over the full
     * duration instead of snapping, and a mid-flight reversal mirrors the
     * raw progress ({@code newRaw₀ = 1 − lastRaw}), which continues the
     * EASED value exactly for any easing without needing its inverse.
     */
    public static HoverAnim symmetric(long durationMs) {
        return new HoverAnim(durationMs, SMOOTHSTEP, false);
    }

    public float update(boolean target) {
        if (target != lastTarget) {
            if (backDateFromRest) {
                // Shipped legacy behavior: back-compute from the eased value.
                // Enters from locked rest fully back-dated — the snap-in.
                startMs = System.currentTimeMillis() - (long) ((1f - currentT) * durationMs);
            } else {
                // §8.3 symmetric: the new leg starts mirrored (raw 1−lastRaw),
                // so from locked rest (lastRaw 1) it begins at zero progress.
                startMs = System.currentTimeMillis() - (long) ((1f - lastRaw) * durationMs);
            }
            lastTarget = target;
        }
        float targetT = target ? 1f : 0f;
        if (currentT == targetT) return currentT;

        long elapsed = System.currentTimeMillis() - startMs;
        float raw = Math.min(1f, Math.max(0f, elapsed / (float) durationMs));
        lastRaw = raw;
        float t = target ? raw : (1f - raw);
        currentT = ease.ease(t);
        if (raw >= 1f) currentT = targetT;
        return currentT;
    }

    /** True while a transition is in flight (settled endpoints skip the time math). */
    public boolean isAnimating() {
        return currentT > 0f && currentT < 1f;
    }

    public float current() { return currentT; }
}
