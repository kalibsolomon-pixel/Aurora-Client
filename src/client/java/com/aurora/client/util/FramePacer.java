package com.aurora.client.util;

import com.aurora.client.config.AuroraConfig;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.locks.LockSupport;

/**
 * Frame pacing implementations. Each {@code waitUntil} method blocks until
 * GLFW time reaches {@code target} (in seconds), using a different strategy.
 *
 * <p>Background: vanilla MC uses {@code Thread.sleep} for frame pacing,
 * which on Windows is locked to the OS scheduler quantum (~15.625 ms by
 * default) unless {@code timeBeginPeriod(1)} has been called. That makes
 * any FPS cap higher than ~64 jittery in the millisecond range.
 *
 * <p>The strategies here progressively trade CPU for precision:
 * <ul>
 *   <li>{@link #waitUntilSpin} — sub-microsecond precision, ~1 full core.</li>
 *   <li>{@link #waitUntilPark} — ~1-2 ms precision, near-zero CPU.</li>
 *   <li>{@link #waitUntilHybrid} — park while far, spin at the end.</li>
 * </ul>
 *
 * <p>There is no YIELD strategy (removed 2026-09-13): its two implementations
 * were measured under the efficiency audit's DevPilot {@code perf} harness and
 * neither earned the menu slot. The original shipped form was byte-identical
 * to {@link #waitUntilSpin} (both loops called {@code Thread.onSpinWait},
 * which is a pipeline hint, not a deschedule). A genuine cooperative form —
 * {@link Thread#yield()} between polls — was then implemented and re-measured
 * at 60 and 120 fps: it still burned a full core (render-thread CPU 95.3% vs
 * SPIN's 95.6% at 60, 92.2% vs 92.6% at 120; per-frame CSV showed 7.73 ms CPU
 * per 8.33 ms frame with not one genuinely-descheduled frame in 360) because
 * {@code yield} returns immediately when no other thread is runnable, and its
 * precision was within run-to-run noise of SPIN's — a duplicate of SPIN, not
 * a middle ground. (The other candidate implementation, short
 * {@code parkNanos} calls, is PARK's own mechanism and would have duplicated
 * PARK instead.) Configs that persisted "YIELD" load as null and fall back
 * through {@link #waitUntil} to the default strategy.
 */
public final class FramePacer {

    private FramePacer() {}

    /** Pure busy-spin. Max precision, full CPU core spin. */
    public static void waitUntilSpin(double target) {
        while (GLFW.glfwGetTime() < target) {
            Thread.onSpinWait();
        }
    }

    /**
     * Sleep most of the wait via {@link LockSupport#parkNanos}, then spin
     * the last {@code spinTailMicros} for precision. Low CPU usage.
     */
    public static void waitUntilPark(double target, int spinTailMicros) {
        double now = GLFW.glfwGetTime();
        double remaining = target - now;
        if (remaining <= 0) return;
        // Leave the spin tail unaccounted, park the rest
        long parkNanos = (long) ((remaining - spinTailMicros / 1_000_000.0) * 1_000_000_000.0);
        if (parkNanos > 0) {
            LockSupport.parkNanos(parkNanos);
        }
        while (GLFW.glfwGetTime() < target) {
            Thread.onSpinWait();
        }
    }

    /**
     * Two-stage wait: park while far away, spin once within
     * {@code spinThresholdMicros} of the target. The audit measured no
     * precision gain over {@link #waitUntilPark} (whose shorter spin tail
     * already covers the last half-millisecond) for the longer spin window's
     * extra CPU.
     */
    public static void waitUntilHybrid(double target, int parkThresholdMicros, int spinThresholdMicros) {
        // Stage 1 — park while still > parkThreshold away.
        double now = GLFW.glfwGetTime();
        double parkUntil = target - parkThresholdMicros / 1_000_000.0;
        if (parkUntil > now) {
            long parkNanos = (long) ((parkUntil - now) * 1_000_000_000.0);
            if (parkNanos > 0) LockSupport.parkNanos(parkNanos);
        }

        // Stage 2 — spin to target.
        while (GLFW.glfwGetTime() < target) {
            Thread.onSpinWait();
        }
    }

    /** Dispatch to the configured strategy. */
    public static void waitUntil(double target, AuroraConfig cfg) {
        AuroraConfig.PacingStrategy strat = cfg.framePacingStrategy;
        if (strat == null) strat = AuroraConfig.PacingStrategy.HYBRID;

        int spinTail = Math.max(50, cfg.framePacerSpinThresholdMicros);
        int parkTail = Math.max(spinTail + 100, cfg.framePacerParkThresholdMicros);

        // Low CPU mode pushes the park threshold further out, so we park
        // longer and spin/yield less. Costs ~1-2ms precision but keeps the
        // CPU cool — useful on laptops.
        if (cfg.framePacerLowCpuMode) {
            parkTail = Math.max(parkTail, 4000);
        }

        switch (strat) {
            case VANILLA -> { /* unreachable; callers gate on this */ }
            case PARK -> waitUntilPark(target, spinTail);
            case HYBRID -> waitUntilHybrid(target, parkTail, spinTail);
            case SPIN -> waitUntilSpin(target);
        }
    }
}