package com.aurora.client.ui.util;

public final class OverlayMotion {
    public static final int OPEN_MS = 120, CLOSE_MS = 90;
    private static final float REST_SCALE = 1f;
    private static final float START_SCALE = .96f;
    private boolean open;
    private float from;
    private long started;

    public void setOpen(boolean next, long now) {
        if (next == open) return;
        from = progress(now, false);
        open = next;
        started = now;
    }

    public void reset() {
        open = false;
        from = 0;
        started = 0;
    }

    public float progress(long now, boolean reduced) {
        float target = open ? 1 : 0;
        if (reduced || from == target) return target;
        double duration = (open ? OPEN_MS : CLOSE_MS) * 1_000_000.0 * Math.abs(target - from);
        double elapsed = (now - started) / duration;
        if (elapsed >= 1) return target;
        return (float) (from + (target - from) * CubicBezier.EASE_OUT.at(elapsed));
    }

    public static float scale(float progress) { return progress >= 1 ? REST_SCALE : START_SCALE + .04f * progress; }
    public static double unscale(double position, int pivot, float progress) { return pivot + (position - pivot) / scale(progress); }
}
