package com.aurora.client.util;

/**
 * FPS-limit precedence and the single deadline shared by the two pacing hooks.
 * Minecraft supplies an effective tracker limit, never the raw option. All time
 * inputs are GLFW seconds; this class does not wait or inspect window state.
 */
public final class FramePacing {
    public enum Owner { NONE, NATIVE, FRAME_HEAD, POST_SWAP }
    public record Decision(Owner owner, int fps) {}
    public record PostSwap(boolean cancelNative, double waitTarget) {}

    private static final Decision UNLIMITED = new Decision(Owner.NONE, 0);
    public static final FramePacing INSTANCE = new FramePacing();
    private Decision head = UNLIMITED;
    private Decision scheduled = UNLIMITED;
    private double nextTarget;
    private boolean headWaited;

    /**
     * Native 260 (or greater) skips limitDisplayFPS in Minecraft 1.21.11.
     * Aurora global 0 is Off; GUI 1000 is its Unlimited choice. Retain the
     * existing GUI exemption from adaptive sleeping and the historical GUI
     * limiter applicability (only when Minecraft would call its limiter).
     */
    public static Decision resolve(int nativeFps, int globalFps, int guiFps,
                                   boolean auroraGui, boolean customPacer, boolean adaptive) {
        boolean nativeActive = nativeFps > 0 && nativeFps < 260;
        int fps = nativeActive ? nativeFps : 0;
        if (auroraGui && nativeActive && guiFps > 0 && guiFps < 1000) {
            fps = Math.min(fps, guiFps);
        }
        if (globalFps > 0) {
            fps = fps > 0 ? Math.min(fps, globalFps) : globalFps;
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
                return new PostSwap(headWaited, 0.0);
            }
            reset(current);
            return new PostSwap(false, 0.0);
        }
        if (current.owner() != Owner.POST_SWAP) {
            reset(current);
            return new PostSwap(false, 0.0);
        }
        double target = advance(current, now);
        return new PostSwap(target != 0.0, target);
    }

    private void reset(Decision decision) {
        scheduled = decision;
        nextTarget = 0.0;
    }

    // Preserve the existing deadline calculation, park/spin dispatch point,
    // and late-frame reanchoring; only ownership/target changes reset it.
    private double advance(Decision decision, double now) {
        double frameTime = 1.0 / decision.fps();
        if (!scheduled.equals(decision)) reset(decision);
        if (nextTarget == 0.0 || now > nextTarget + frameTime * 0.5) {
            nextTarget = now + frameTime;
            return 0.0;
        }
        double target = nextTarget;
        nextTarget += frameTime;
        return target;
    }
}
