package com.aurora.client.util;

/**
 * FPS-limit precedence and the single deadline shared by the two pacing hooks.
 * Minecraft supplies an effective tracker limit, never the raw option. All time
 * inputs are SDL seconds; this class does not wait or inspect window state.
 */
public final class FramePacing {
    public enum Owner { NONE, NATIVE, FRAME_HEAD, POST_SWAP }
    public record Decision(Owner owner, int fps) {}
    public record PostSwap(boolean cancelNative, double waitTarget) {}

    private static final Decision UNLIMITED = new Decision(Owner.NONE, 0);
    private static final PostSwap NO_CANCEL = new PostSwap(false, 0.0);
    private static final PostSwap CANCEL_NO_WAIT = new PostSwap(true, 0.0);
    public static final FramePacing INSTANCE = new FramePacing();
    private Decision head = UNLIMITED;
    private Decision scheduled = UNLIMITED;
    private double nextTarget;
    private boolean headWaited;

    /** True when the most recent frame executed a frame-head pacing wait. */
    public boolean didHeadWaitThisFrame() {
        return headWaited;
    }

    /**
     * Native 260 (or greater) skips limitDisplayFPS in Minecraft 1.21.11.
     * Aurora global 0 is Off; GUI 1000 is its Unlimited choice. Retain the
     * existing GUI exemption from adaptive sleeping and the historical GUI
     * limiter applicability (only when Minecraft would call its limiter).
     */
    public static Decision resolve(int nativeFps, int globalFps, int guiFps,
                                   boolean auroraGui, boolean customPacer, boolean adaptive) {
        boolean nativeActive = nativeFps > 0 && nativeFps < 260;
        int fps = effectiveFps(nativeFps, globalFps, guiFps, auroraGui);
        if (globalFps > 0) {
            return new Decision(Owner.FRAME_HEAD, fps);
        }
        if (!nativeActive) return UNLIMITED;
        if (!auroraGui && customPacer && adaptive) {
            return new Decision(Owner.FRAME_HEAD, fps);
        }
        if (auroraGui ? guiFps > 0 : customPacer) {
            return new Decision(Owner.POST_SWAP, fps);
        }
        return new Decision(Owner.NATIVE, fps);
    }

    /** Applies the same cap precedence when another engine owns the wait. */
    public static int effectiveFps(int nativeFps, int globalFps, int guiFps, boolean auroraGui) {
        int fps = nativeFps > 0 && nativeFps < 260 ? nativeFps : 0;
        if (auroraGui && fps > 0 && guiFps > 0 && guiFps < 1000) {
            fps = Math.min(fps, guiFps);
        }
        if (globalFps > 0) {
            fps = fps > 0 ? Math.min(fps, globalFps) : globalFps;
        }
        return fps;
    }

    public void relinquish() {
        head = UNLIMITED;
        headWaited = false;
        reset(UNLIMITED);
    }

    /** Returns a deadline to wait for, or zero when arming/inactive/late. */
    public double beginFrame(Decision decision, double now) {
        head = decision;
        headWaited = false;
        if (!scheduled.equals(decision)) reset(decision);
        if (decision.owner() != Owner.FRAME_HEAD) return 0.0;
        double target = advance(decision, now);
        headWaited = target != 0.0;
        return target;
    }

    /**
     * Recheck after swap/event pumping. Cancel only a wait that Aurora has
     * actually owned. A newly tighter native restriction runs natively this
     * frame and is picked up at the next frame head. Initialization/hitches
     * also leave the native wait available rather than cancelling an unpaced
     * frame. A disabled/Unlimited interval clears any prior deadline.
     */
    public PostSwap postSwap(Decision current, double now) {
        if (head.owner() == Owner.FRAME_HEAD) {
            if (current.fps() > 0 && head.fps() <= current.fps()) {
                return headWaited ? CANCEL_NO_WAIT : NO_CANCEL;
            }
            reset(current);
            return NO_CANCEL;
        }
        if (current.owner() != Owner.POST_SWAP) {
            reset(current);
            return NO_CANCEL;
        }
        double target = advance(current, now);
        return target != 0.0 ? new PostSwap(true, target) : NO_CANCEL;
    }

    private void reset(Decision decision) {
        scheduled = decision;
        nextTarget = 0.0;
    }

    // Preserve the existing deadline calculation, park/spin dispatch point,
    // and late-frame reanchoring; gently damp mildly late frames (0 < lateness <= 0.5 * period)
    // so minor blips do not trigger a short-frame sawtooth oscillation.
    private double advance(Decision decision, double now) {
        double frameTime = 1.0 / decision.fps();
        if (!scheduled.equals(decision)) reset(decision);
        if (nextTarget == 0.0 || now > nextTarget + frameTime * 0.5) {
            if (ParkDiagnostics.INSTANCE.isEnabled()) ParkDiagnostics.INSTANCE.anchor(nextTarget == 0.0);
            nextTarget = now + frameTime;
            return 0.0;
        }
        double target = nextTarget;
        if (now > nextTarget) {
            // Mildly late: still return the past target (immediate exit, keeps ownership) and
            // blend half the lateness into the next deadline to avoid long-short stutter.
            nextTarget = nextTarget + frameTime + (now - nextTarget) * 0.5;
        } else {
            nextTarget += frameTime;
        }
        return target;
    }
}
