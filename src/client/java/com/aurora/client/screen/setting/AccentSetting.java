package com.aurora.client.screen.setting;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A theme colour row: the shared {@link ColorSetting} picker with ONE
 * throttle on top. Owner ruling 2026-10-30 removed the preset-swatch peer
 * grid and the "Custom" slot — the row is now exactly one small square
 * swatch that opens the color picker; the picker's alpha strip is not
 * offered (theme colours are opaque; window translucency has its own
 * Background Opacity slider).
 *
 * <p>The only behavior this subclass adds over the plain picker row is the
 * established live-apply budget ({@link #LIVE_RELOAD_MIN_MS}, the same
 * window as the opacity slider's {@code LiveReloadThrottle}): mid-drag
 * commits coalesce to at most one theme apply per window, the LATEST value
 * always wins, and the final value flushes on release (the picker's own
 * release-save path) and on the next render tick, whichever comes first.
 */
public class AccentSetting extends ColorSetting {

    /** Minimum spacing between live (mid-drag) theme applies. */
    private static final long LIVE_RELOAD_MIN_MS = 100L;

    private final IntConsumer target;
    private int pendingRgb;
    private boolean hasPending;
    private long lastApplyMs = Long.MIN_VALUE;

    public AccentSetting(String label, IntSupplier getter, IntConsumer setter) {
        super(label, getter, AccentSetting::noop);
        this.target = setter;
        opaqueOnly();
    }

    private static void noop(int v) {}

    /** The picker's commit seam: coalesced live apply, latest value wins. */
    @Override
    void applyValue(int argb) {
        pendingRgb = argb;
        hasPending = true;
        if (lastApplyMs == Long.MIN_VALUE || System.currentTimeMillis() - lastApplyMs >= LIVE_RELOAD_MIN_MS) {
            flushPending();
        }
    }

    private void flushPending() {
        if (!hasPending) return;
        hasPending = false;
        lastApplyMs = System.currentTimeMillis();
        target.accept(pendingRgb);
    }

    /** A deferred live apply flushes once the window elapses. */
    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        if (hasPending && System.currentTimeMillis() - lastApplyMs >= LIVE_RELOAD_MIN_MS) flushPending();
        super.render(ctx, x, y, width, mouseX, mouseY);
    }

    /** Release = the drag is over: the final value applies immediately
     *  (before the picker's own save). */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean consumed = super.mouseReleased(mouseX, mouseY, button);
        flushPending();
        return consumed;
    }
}
