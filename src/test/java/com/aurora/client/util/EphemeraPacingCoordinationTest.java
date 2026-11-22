package com.aurora.client.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.aurora.client.util.FramePacing.Owner.FRAME_HEAD;
import static org.junit.jupiter.api.Assertions.*;

class EphemeraPacingCoordinationTest {
    @AfterEach
    void restoreHook() {
        EphemeraPacingHook.install(null);
        AdaptivePacerCalibrator.INSTANCE.reset();
    }

    @Test
    void adaptiveCalibratorWidensSpinWindowQuicklyOnLateOsWakesAndRelaxesSlowly() {
        var cal = new AdaptivePacerCalibrator();
        int initial = cal.effectiveSpinWindowMicros(500);
        for (int i = 0; i < 6; i++) {
            cal.observePark(5_000_000L, 6_800_000L); // 1.8 ms late wakes
        }
        int widened = cal.effectiveSpinWindowMicros(500);
        assertTrue(widened > initial + 900, "Calibrator must widen reserve rapidly: " + widened);
        assertTrue(widened <= 3500);

        for (int i = 0; i < 40; i++) {
            cal.observePark(5_000_000L, 4_950_000L); // clean early wakes
        }
        assertTrue(cal.effectiveSpinWindowMicros(500) < widened, "Reserve should gently relax once wakes settle");
    }

    @Test
    void mildlyLateFrameAppliesSoftPhaseRecoveryWhileKeepingOwnership() {
        var pacing = new FramePacing();
        var head100 = new FramePacing.Decision(FRAME_HEAD, 100);
        assertEquals(0.0, pacing.beginFrame(head100, 1.0));
        assertFalse(pacing.didHeadWaitThisFrame());

        // Arrive 3 ms late (within 0.5 * 10 ms period): returns past target (nonzero -> keeps ownership)
        // and blends half the lateness (+1.5 ms) into the next deadline to avoid sawtooth jitter.
        double t1 = pacing.beginFrame(head100, 1.013);
        assertEquals(1.010, t1, 1e-10);
        assertTrue(pacing.didHeadWaitThisFrame());
        assertTrue(pacing.postSwap(head100, 1.014).cancelNative());

        double t2 = pacing.beginFrame(head100, 1.015);
        assertEquals(1.0215, t2, 1e-10);
    }

    @Test
    void ephemeraHookReceivesReportsAndFeedsSharedOvershootEstimate() {
        var owns = new AtomicBoolean();
        var fps = new AtomicInteger();
        var wait = new AtomicLong();
        var vkAsync = new AtomicBoolean(true);

        EphemeraPacingHook.install(new EphemeraPacingHook.Bridge() {
            @Override public void reportAurora(boolean auroraOwnsWait, int effectiveTargetFps, long lastWaitNanos) {
                owns.set(auroraOwnsWait);
                fps.set(effectiveTargetFps);
                wait.set(lastWaitNanos);
            }
            @Override public void onBeforeWait(long plannedWaitNanos) {}
            @Override public void onAfterPark(long requestedNanos, long actualNanos) {}
            @Override public boolean isVulkanAsyncPresentActive() { return vkAsync.get(); }
            @Override public int recommendedOvershootMicros() { return 1400; }
        });

        assertTrue(EphemeraPacingHook.isConnected());
        assertTrue(EphemeraPacingHook.isVulkanAsyncPresentActive());
        EphemeraPacingHook.reportAurora(true, 165, 2_500_000L);
        assertTrue(owns.get());
        assertEquals(165, fps.get());
        assertEquals(2_500_000L, wait.get());

        // Shared overshoot from Ephemera should raise Aurora's effective spin reserve automatically.
        assertTrue(AdaptivePacerCalibrator.INSTANCE.effectiveSpinWindowMicros(500) >= 1700);
    }
}
