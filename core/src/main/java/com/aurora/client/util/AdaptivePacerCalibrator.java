package com.aurora.client.util;

/**
 * Fast-attack / slow-decay OS {@code LockSupport.parkNanos} wake-overshoot estimator.
 *
 * <p>On Windows, timer granularity can shift between 0.5 ms and 15.6 ms depending on active
 * background processes and power profiles. When a park call wakes late, widening the spin reserve
 * immediately prevents consecutive oversleep frame drops; when wakeup precision settles, decaying
 * slowly back toward the configured floor recovers CPU headroom without oscillating.
 */
public final class AdaptivePacerCalibrator {
    private static final double MIN_OVERSHOOT_NS = 100_000.0;   // 100 us floor
    private static final double MAX_OVERSHOOT_NS = 3_000_000.0; // 3.0 ms ceiling

    private double overshootNs = 300_000.0;

    public static final AdaptivePacerCalibrator INSTANCE = new AdaptivePacerCalibrator();

    public void observePark(long requestedNanos, long actualNanos) {
        if (requestedNanos <= 0L || actualNanos < 0L) return;
        long overshoot = actualNanos - requestedNanos;
        if (overshoot > 0L) {
            double clamped = Math.min((double) overshoot, MAX_OVERSHOOT_NS);
            if (clamped > overshootNs) {
                overshootNs = overshootNs * 0.65 + clamped * 0.35;
            } else {
                overshootNs = overshootNs * 0.94 + clamped * 0.06;
            }
        } else {
            overshootNs = Math.max(MIN_OVERSHOOT_NS, overshootNs * 0.97);
        }
    }

    /**
     * Effective spin window in microseconds: never smaller than {@code baseMicros}, widened when
     * measured OS park overshoot (from Aurora or Ephemera's shared estimator) exceeds it.
     */
    public int effectiveSpinWindowMicros(int baseMicros) {
        int localAdaptive = (int) Math.round((overshootNs * 1.15 + 150_000.0) / 1_000.0);
        int sharedOvershoot = EphemeraPacingHook.recommendedOvershootMicros();
        int sharedAdaptive = sharedOvershoot > 0 ? (int) Math.round(sharedOvershoot * 1.15) + 150 : 0;
        int adaptive = Math.max(localAdaptive, sharedAdaptive);
        return Math.min(3500, Math.max(baseMicros, adaptive));
    }

    public int estimatedOvershootMicros() {
        return (int) Math.round(overshootNs / 1_000.0);
    }

    public void reset() {
        overshootNs = 300_000.0;
    }
}
