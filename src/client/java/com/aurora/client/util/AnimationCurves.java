package com.aurora.client.util;

import com.aurora.client.config.AuroraConfig;

/**
 * Animation curve helpers. Vanilla Minecraft passes a linear 0..1 tickProgress
 * to per-frame interpolators. By remapping that value through a curve here,
 * we change the timing profile of the underlying animation.
 *
 * <p>Two families:
 * <ul>
 *   <li><b>Ease-in-out</b> (sine/smoothstep/quartic/quintic) — slow at both ends,
 *       fast in the middle. Pendulum feel. Right for view bob (wave).</li>
 *   <li><b>Ease-out</b> (cubic_out, quartic_out) — fast at start, slow at end.
 *       Punchy, weighty feel. Right for swings (windup→follow-through).</li>
 * </ul>
 *
 * <p>Higher-order ease-in-out curves (quartic, quintic) hold the endpoints
 * flatter for longer. Beyond quintic, the difference is not visually
 * distinguishable at typical animation durations (~300ms).
 */
public final class AnimationCurves {

    private AnimationCurves() {}

    /** Apply the configured swing curve. */
    public static float applySwing(float t) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.smoothAnimationsEnabled) return t;
        return apply(cfg.swingAnimationCurve, t);
    }

    /** Apply the configured view-bob curve. */
    public static float applyBob(float t) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.smoothAnimationsEnabled) return t;
        return apply(cfg.viewBobCurve, t);
    }

    private static float apply(AuroraConfig.AnimationCurve c, float t) {
        if (c == null) return t;
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return switch (c) {
            case LINEAR -> t;
            case SINE -> sineInOut(t);
            case SMOOTHSTEP -> smoothstep(t);
            case QUARTIC -> quarticInOut(t);
            case QUINTIC, CUBIC_HERMITE -> smootherstep(t);
            case CUBIC_OUT -> cubicOut(t);
            case QUARTIC_OUT -> quarticOut(t);
        };
    }

    /** -(cos(πt)-1)/2. Gentlest ease-in-out. */
    private static float sineInOut(float t) {
        return (float) (-(Math.cos(Math.PI * t) - 1.0) / 2.0);
    }

    /** 3t² − 2t³. Classic cubic ease-in-out (Perlin's smoothstep). */
    private static float smoothstep(float t) {
        return t * t * (3f - 2f * t);
    }

    /** Quartic ease-in-out — steeper middle than cubic, flatter ends. */
    private static float quarticInOut(float t) {
        if (t < 0.5f) return 8f * t * t * t * t;
        float u = -2f * t + 2f;
        return 1f - (u * u * u * u) / 2f;
    }

    /** 6t⁵ − 15t⁴ + 10t³. Perlin's smootherstep (quintic ease-in-out). */
    private static float smootherstep(float t) {
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    /** 1 − (1−t)³. Cubic ease-out — punchy, fast windup. */
    private static float cubicOut(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /** 1 − (1−t)⁴. Quartic ease-out — very snappy. */
    private static float quarticOut(float t) {
        float u = 1f - t;
        return 1f - u * u * u * u;
    }
}