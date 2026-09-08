package com.aurora.client.screen.setting;

/**
 * Paces live (mid-drag) theme reloads. Every value the theme widgets apply
 * triggers a full {@code ThemeManager.reload()}, so while the user drags,
 * applies pass through at most once per {@code minMs}; values arriving
 * inside the window are deferred and flushed from the widget's render (a
 * single timestamp compare while idle) or at commit points (release,
 * screen close). Callers keep their own notion of "genuine change" and
 * only offer values that actually differ from the live config — this class
 * never fires a reload on its own.
 *
 * <p>Shared by {@link ThemeOpacitySetting} (drag) and {@link AccentSetting}
 * (embedded picker drags), which previously carried identical copies of
 * this state machine.
 */
final class LiveReloadThrottle {

    private final long minMs;
    private long lastApplyMs;
    private long nextMs;
    private boolean pending;

    LiveReloadThrottle(long minMs) {
        this.minMs = minMs;
    }

    /** True when a deferred apply is due — the cheap idle check for render. */
    boolean due() {
        return pending && System.currentTimeMillis() >= nextMs;
    }

    /** Apply now if the window allows; otherwise defer to a later {@link #flush}. */
    void apply(Runnable apply) {
        long now = System.currentTimeMillis();
        if (now - lastApplyMs >= minMs) {
            apply.run();
            lastApplyMs = now;
            pending = false;
        } else {
            pending = true;
            nextMs = lastApplyMs + minMs;
        }
    }

    /**
     * Run a deferred apply if one is pending — the runnable re-checks
     * "still differs from the live config" itself. The throttle clock is
     * always stamped so a commit point starts a fresh window, exactly as
     * the opacity slider's original release path did.
     */
    void flush(Runnable flush) {
        if (pending) flush.run();
        lastApplyMs = System.currentTimeMillis();
        pending = false;
    }
}
