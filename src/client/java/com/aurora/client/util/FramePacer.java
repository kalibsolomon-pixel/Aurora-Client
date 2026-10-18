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

    // Process-only developer opt-in. Never persisted or changed during a session.
    static final boolean EXPERIMENTAL_PARK_REPARK = Boolean.getBoolean("aurora.experimentalParkRepark");
    static final long EXPERIMENTAL_RETRY_NANOS = 1_000_000L;

    /** Minimal boundary for deterministic tests of the isolated experiment. */
    interface ExperimentalWait {
        double time();
        long nanoTime();
        void park(long nanos);
        void spin();
        boolean isInterrupted();
    }

    private static final ExperimentalWait EXPERIMENTAL_WAIT = new ExperimentalWait() {
        public double time() { return GLFW.glfwGetTime(); }
        public long nanoTime() { return System.nanoTime(); }
        public void park(long nanos) { LockSupport.parkNanos(nanos); }
        public void spin() { Thread.onSpinWait(); }
        public boolean isInterrupted() { return Thread.currentThread().isInterrupted(); }
    };

    private FramePacer() {}

    /** Pure busy-spin. Max precision, full CPU core spin. */
    public static void waitUntilSpin(double target) {
        while (GLFW.glfwGetTime() < target) {
            Thread.onSpinWait();
        }
    }

    /** Time eligibility only; interruption vetoes the single retry separately. */
    static boolean experimentalRetryEligible(double remaining, int spinTailMicros) {
        return remaining >= spinTailMicros / 1_000_000.0 + 0.001;
    }

    /**
     * Isolated C-PERF.2A path. The first request matches reference PARK exactly.
     * One fixed 1ms retry requires the full configured reserve plus 1ms remaining.
     * A second immediate return MUST go to the deadline spin, never a retry loop.
     * The nullable observer changes collection only, never scheduling decisions.
     * No tasks/events are serviced here; a retry can consume a Minecraft unpark
     * permit and can oversleep. Neither the reserve nor responsiveness is guaranteed.
     */
    static void waitUntilParkExperimental(double target, int spinTailMicros,
                                          ExperimentalWait wait, ParkDiagnostics observer) {
        double now = wait.time();
        double remaining = target - now;
        if (remaining <= 0) {
            if (observer != null) observer.recordExperimental(now, target, now, now,
                    0, 0, false, now, 0, false, false, false);
            return;
        }
        long parkNanos = (long) ((remaining - spinTailMicros / 1_000_000.0) * 1_000_000_000.0);
        boolean interruptedAtEntry = wait.isInterrupted();
        long actualNanos = 0;
        if (parkNanos > 0) {
            long before = observer != null ? wait.nanoTime() : 0;
            wait.park(parkNanos);
            if (observer != null) actualNanos = wait.nanoTime() - before;
        }
        double firstReturn = wait.time();
        double spinStart = firstReturn;
        boolean eligible = parkNanos > 0 && experimentalRetryEligible(target - firstReturn, spinTailMicros);
        boolean retry = false;
        boolean interrupted = false;
        long retryActualNanos = 0;
        if (eligible) {
            interrupted = wait.isInterrupted() || interruptedAtEntry;
            // A final non-clearing check bounds the race without clearing/restoring
            // the flag. An interrupt after this check may still wake the retry.
            if (!interrupted) interrupted = wait.isInterrupted();
            if (!interrupted) {
                retry = true;
                long before = observer != null ? wait.nanoTime() : 0;
                wait.park(EXPERIMENTAL_RETRY_NANOS);
                if (observer != null) retryActualNanos = wait.nanoTime() - before;
                spinStart = wait.time();
            }
        }
        double exit = spinStart;
        boolean entered = spinStart < target;
        if (entered) {
            do {
                wait.spin();
            } while ((exit = wait.time()) < target);
        }
        if (observer != null) observer.recordExperimental(now, target, spinStart, exit,
                Math.max(0, parkNanos), actualNanos, entered, firstReturn,
                retryActualNanos, eligible, retry, interrupted);
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

    /** Same one-park/one-spin decisions; enabled diagnostics only. */
    private static void waitUntilParkMeasured(double target, int spinTailMicros) {
        double now = GLFW.glfwGetTime();
        double remaining = target - now;
        if (remaining <= 0) {
            ParkDiagnostics.INSTANCE.record(now, target, now, now, 0, 0, false);
            return;
        }
        long parkNanos = (long) ((remaining - spinTailMicros / 1_000_000.0) * 1_000_000_000.0);
        long actualNanos = 0;
        if (parkNanos > 0) {
            long before = System.nanoTime();
            LockSupport.parkNanos(parkNanos);
            actualNanos = System.nanoTime() - before;
        }
        double spinStart = GLFW.glfwGetTime();
        double exit = spinStart;
        boolean entered = spinStart < target;
        if (entered) {
            do {
                Thread.onSpinWait();
            } while ((exit = GLFW.glfwGetTime()) < target);
        }
        ParkDiagnostics.INSTANCE.record(now, target, spinStart, exit,
                Math.max(0, parkNanos), actualNanos, entered);
    }

    /**
     * Two-stage wait: park while far away, spin once within
     * {@code spinWindowMicros} of the target. The audit measured no
     * precision gain over {@link #waitUntilPark} (whose shorter spin tail
     * already covers the last half-millisecond) for the longer spin window's
     * extra CPU.
     */
    public static void waitUntilHybrid(double target, int spinWindowMicros) {
        // Stage 1 — park until the planned spin window begins.
        double now = GLFW.glfwGetTime();
        double parkUntil = target - spinWindowMicros / 1_000_000.0;
        if (parkUntil > now) {
            long parkNanos = (long) ((parkUntil - now) * 1_000_000_000.0);
            if (parkNanos > 0) LockSupport.parkNanos(parkNanos);
        }

        // Stage 2 — spin to target.
        while (GLFW.glfwGetTime() < target) {
            Thread.onSpinWait();
        }
    }

    /**
     * HYBRID alone uses the Low CPU modifier. It parks until the configured
     * shorter spin tail instead of increasing its active-spinning window.
     * Values are normalized for dispatch only; saved thresholds are untouched.
     */
    static int hybridSpinWindowMicros(int spinThresholdMicros, int parkThresholdMicros, boolean lowCpu) {
        int spinTail = Math.max(50, spinThresholdMicros);
        int parkTail = Math.max(spinTail + 100, parkThresholdMicros);
        return lowCpu ? Math.min(parkTail, spinTail) : parkTail;
    }

    /** Dispatch to the configured strategy (null falls back to PARK, the factory default). */
    public static void waitUntil(double target, AuroraConfig cfg) {
        AuroraConfig.PacingStrategy strat = cfg.framePacingStrategy;
        if (strat == null) strat = AuroraConfig.PacingStrategy.PARK;

        int spinTail = Math.max(50, cfg.framePacerSpinThresholdMicros);
        switch (strat) {
            case VANILLA -> { /* unreachable; callers gate on this */ }
            case PARK -> {
                if (EXPERIMENTAL_PARK_REPARK) {
                    waitUntilParkExperimental(target, spinTail, EXPERIMENTAL_WAIT,
                            ParkDiagnostics.INSTANCE.isEnabled() ? ParkDiagnostics.INSTANCE : null);
                } else {
                    if (ParkDiagnostics.INSTANCE.isEnabled()) waitUntilParkMeasured(target, spinTail);
                    else waitUntilPark(target, spinTail);
                }
            }
            case HYBRID -> waitUntilHybrid(target, hybridSpinWindowMicros(
                    cfg.framePacerSpinThresholdMicros, cfg.framePacerParkThresholdMicros, cfg.framePacerLowCpuMode));
            case SPIN -> waitUntilSpin(target);
        }
    }
}
