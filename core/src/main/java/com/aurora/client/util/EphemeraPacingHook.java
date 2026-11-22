package com.aurora.client.util;

/**
 * Pure-Java boundary (ADR 0003) allowing Aurora's core frame pacer and Reflex scheduler to
 * cooperate with Ephemera's {@code ephemera:frame_pacer} ObjectShare bridge when Ephemera is
 * installed, with zero compile-time or runtime dependency on Fabric or Ephemera.
 */
public final class EphemeraPacingHook {
    public interface Bridge {
        void reportAurora(boolean auroraOwnsWait, int effectiveTargetFps, long lastWaitNanos);
        void onBeforeWait(long plannedWaitNanos);
        void onAfterPark(long requestedNanos, long actualNanos);
        boolean isVulkanAsyncPresentActive();
        int recommendedOvershootMicros();
        default boolean ownsFramePacing() { return false; }
    }

    private static final Bridge NOOP = new Bridge() {
        @Override public void reportAurora(boolean auroraOwnsWait, int effectiveTargetFps, long lastWaitNanos) {}
        @Override public void onBeforeWait(long plannedWaitNanos) {}
        @Override public void onAfterPark(long requestedNanos, long actualNanos) {}
        @Override public boolean isVulkanAsyncPresentActive() { return false; }
        @Override public int recommendedOvershootMicros() { return 0; }
    };

    private static volatile Bridge bridge = NOOP;

    private EphemeraPacingHook() {}

    public static void install(Bridge installed) {
        bridge = installed != null ? installed : NOOP;
    }

    public static boolean isConnected() {
        return bridge != NOOP;
    }

    public static boolean ownsFramePacing() {
        return bridge.ownsFramePacing();
    }

    public static void reportAurora(boolean auroraOwnsWait, int effectiveTargetFps, long lastWaitNanos) {
        bridge.reportAurora(auroraOwnsWait, effectiveTargetFps, lastWaitNanos);
    }

    public static void onBeforeWait(long plannedWaitNanos) {
        bridge.onBeforeWait(plannedWaitNanos);
    }

    public static void onAfterPark(long requestedNanos, long actualNanos) {
        bridge.onAfterPark(requestedNanos, actualNanos);
    }

    public static boolean isVulkanAsyncPresentActive() {
        return bridge.isVulkanAsyncPresentActive();
    }

    public static int recommendedOvershootMicros() {
        return bridge.recommendedOvershootMicros();
    }
}
