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
 *   <li>{@link #waitUntilYield} — ~10 µs precision, ~1 core but cooperative
 *       (Thread.onSpinWait gives the CPU hyperthread a hint).</li>
 *   <li>{@link #waitUntilPark} — ~1-2 ms precision, near-zero CPU.</li>
 *   <li>{@link #waitUntilHybrid} — park while far, yield while near, spin
 *       at the very end. Best balance.</li>
 * </ul>
 */
public final class FramePacer {

    private FramePacer() {}

    /** Pure busy-spin. Max precision, full CPU core spin. */
    public static void waitUntilSpin(double target) {
        while (GLFW.glfwGetTime() < target) {
            Thread.onSpinWait();
        }
    }

    /** Cooperative yield via {@link Thread#onSpinWait}. ~10µs precision. */
    public static void waitUntilYield(double target) {
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
     * Three-stage wait: park while far away, yield while close, spin at the
     * end. Combines low CPU (park) with high precision (spin). This is the
     * recommended default.
     */
    public static void waitUntilHybrid(double target, int parkThresholdMicros, int spinThresholdMicros) {
        // Stage 1 — park while still > parkThreshold away.
        double now = GLFW.glfwGetTime();
        double parkUntil = target - parkThresholdMicros / 1_000_000.0;
        if (parkUntil > now) {
            long parkNanos = (long) ((parkUntil - now) * 1_000_000_000.0);
            if (parkNanos > 0) LockSupport.parkNanos(parkNanos);
        }

        // Stage 2 — yield while > spinThreshold away.
        double yieldUntil = target - spinThresholdMicros / 1_000_000.0;
        while (GLFW.glfwGetTime() < yieldUntil) {
            Thread.onSpinWait();
        }

        // Stage 3 — pure spin to target.
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
            case YIELD -> waitUntilYield(target);
            case PARK -> waitUntilPark(target, spinTail);
            case HYBRID -> waitUntilHybrid(target, parkTail, spinTail);
            case SPIN -> waitUntilSpin(target);
        }
    }
}