package com.aurora.client.screen.setting;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.component.Slider;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Slider for the theme's panel-background <b>opacity</b> (alpha only — hue
 * and lightness are untouched). Visually identical to the plain
 * {@link SliderSetting} percent row (inherited unchanged: label, cached
 * value readout, track geometry, description tooltip) but with drag-aware
 * commit discipline, because unlike a gameplay slider every applied value
 * triggers a theme re-resolve:
 *
 * <ul>
 *   <li><b>During drag:</b> the pending value is applied through the
 *       standard setter (config write + one {@code ThemeManager.reload()})
 *       at most once per {@link #LIVE_RELOAD_MIN_MS} (see
 *       {@link LiveReloadThrottle}) — live preview without reload spam. A
 *       deferred apply is flushed from {@link #renderOverlay} once the
 *       interval elapses (a single timestamp comparison when idle).</li>
 *   <li><b>On release:</b> the final value is applied only if it still
 *       differs from the live config, then persisted — zero extra reloads
 *       when nothing changed since the last throttled apply.</li>
 * </ul>
 *
 * <p>Because the setter writes the config before reloading, the per-tick
 * {@code ThemeManager.sync()} dirty-check never sees a stale gap: it only
 * reloads on values this slider did not apply itself.
 *
 * <p>Rendering is split for the owning screen's static-layer cache
 * ({@link #renderShapes} = track only, {@link #renderOverlay} = label,
 * readout, live track), and the interaction is owned here rather than
 * delegated to the shared {@code Slider} control: drag writes go through
 * the throttle, never straight to the setter.
 */
public class ThemeOpacitySetting extends SliderSetting {

    private static final double STEP = 0.01;
    /** Minimum spacing between live (mid-drag) reloads. */
    private static final long LIVE_RELOAD_MIN_MS = 100L;

    private final DoubleSupplier getter;
    private final DoubleConsumer setter;

    private final LiveReloadThrottle throttle = new LiveReloadThrottle(LIVE_RELOAD_MIN_MS);

    private boolean dragging = false;
    private double pendingValue;

    public ThemeOpacitySetting(String label, DoubleSupplier getter, DoubleConsumer setter) {
        super(label, getter, setter, 0.0, 1.0, false);
        this.getter = getter;
        this.setter = setter;
        percent();
    }

    /**
     * The renderer's slider reads the pending drag value while dragging
     * (smooth), otherwise the committed config value — the base row's value
     * readout and the fill/knob all read through it.
     */
    @Override
    protected Slider createSlider(DoubleSupplier getter, DoubleConsumer setter, double min, double max) {
        return new Slider(() -> clamp01(dragging ? pendingValue : getter.getAsDouble()), setter, min, max, STEP);
    }

    @Override public ThemeOpacitySetting description(String desc) { super.description(desc); return this; }
    @Override public ThemeOpacitySetting description(java.util.function.Supplier<String> desc) { super.description(desc); return this; }

    /** Historical track inset of this row — 2 px tighter than the generic rows; kept for pixel parity. */
    @Override protected int trackInset() { return 26; }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        renderShapes(ctx, x, y, width, mouseX, mouseY);
        renderOverlay(ctx, x, y, width, mouseX, mouseY);
    }

    @Override
    public void renderShapes(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        // Track only — static given the roundness token, so it lives in the
        // cached static layer (drawn through the shared themed slider).
        drawTrackShapes(ctx, x, y, width);
    }

    @Override
    public void renderOverlay(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        // Flush a deferred live apply once the throttle interval elapsed.
        if (throttle.due()) flushPending();

        drawLabelRow(ctx, x, y, width, mouseX, mouseY);
        drawTrackOverlay(ctx, x, y, width, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (isDisabled() || button != 0) return false;
        if (mouseY < rowY + CONTROL_H - 18 || mouseY > rowY + CONTROL_H - 2) return false;
        if (mouseX < lastTrackX - 4 || mouseX > lastTrackX + lastTrackW + 4) return false;
        dragging = true;
        pendingValue = getter.getAsDouble();
        applyFromMouse(mouseX);
        requestFocus();
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy, int rowX, int rowY, int rowWidth) {
        if (!dragging || button != 0) return false;
        applyFromMouse(mouseX);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            // Final commit: apply whatever is still unapplied, then persist.
            // Focus is intentionally kept (matches the other slider rows) so
            // arrow-key nudging works right after a mouse drag.
            flushPending();
            AuroraConfig.save();
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyPress(int keyCode, int modifiers) {
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean ctrl  = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        double mult = shift ? 10.0 : (ctrl ? 0.1 : 1.0);
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_DOWN) {
            nudge(-STEP * mult);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_UP) {
            nudge(STEP * mult);
            return true;
        }
        return false;
    }

    private void nudge(double delta) {
        double cur = getter.getAsDouble();
        double next = clamp01(Math.round((cur + delta) / STEP) * STEP);
        if (next != cur) {
            setter.accept(next);          // one reload per genuine change
            AuroraConfig.save();
        }
    }

    private void applyFromMouse(double mouseX) {
        double t = (mouseX - lastTrackX) / (double) lastTrackW;
        pendingValue = clamp01(Math.round(t / STEP) * STEP);
        if (Double.compare(pendingValue, getter.getAsDouble()) == 0) {
            return; // no genuine change — must not fire a reload
        }
        throttle.apply(() -> setter.accept(pendingValue));
    }

    /** Apply the pending value if it still differs from the live config. */
    private void flushPending() {
        throttle.flush(() -> {
            if (Double.compare(pendingValue, getter.getAsDouble()) != 0) {
                setter.accept(pendingValue);
            }
        });
    }

    private static double clamp01(double v) {
        return v < 0d ? 0d : (v > 1d ? 1d : v);
    }
}
