package com.aurora.client.screen.setting;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.component.Slider;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Slider for a theme value (Background Opacity, Blur Radius, Corner
 * Radius). Visually identical to the plain {@link SliderSetting} row
 * (inherited unchanged: label, cached value readout, track geometry,
 * description tooltip) but with drag-aware commit discipline, because unlike
 * a gameplay slider every applied value triggers a theme re-resolve:
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
 * the throttle, never straight to the setter. Values snap to {@code step}.
 */
public class ThemeSliderSetting extends SliderSetting {

    /** Minimum spacing between live (mid-drag) reloads. */
    private static final long LIVE_RELOAD_MIN_MS = 100L;

    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final double min, max, step;

    private final LiveReloadThrottle throttle = new LiveReloadThrottle(LIVE_RELOAD_MIN_MS);

    private boolean dragging = false;
    private double pendingValue;

    /**
     * @param intMode whole-number readout (pixel values); {@code step} should then be 1
     */
    public ThemeSliderSetting(String label, DoubleSupplier getter, DoubleConsumer setter,
                              double min, double max, double step, boolean intMode) {
        super(label, getter, setter, min, max, intMode);
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
        this.step = step;
    }

    /**
     * The renderer's slider reads the pending drag value while dragging
     * (smooth), otherwise the committed config value — the base row's value
     * readout and the fill/knob all read through it. Runs inside the super
     * constructor, so it uses its arguments, never this class's fields; the
     * slider's own step is unused because this row owns its input.
     */
    @Override
    protected Slider createSlider(DoubleSupplier getter, DoubleConsumer setter, double min, double max) {
        return new Slider(() -> clamp(dragging ? pendingValue : getter.getAsDouble(), min, max),
                setter, min, max, (max - min) / 100.0);
    }

    @Override public ThemeSliderSetting description(String desc) { super.description(desc); return this; }
    @Override public ThemeSliderSetting description(java.util.function.Supplier<String> desc) { super.description(desc); return this; }

    /** Historical track inset of the theme rows — 2 px tighter than the generic rows; kept for pixel parity. */
    @Override protected int trackInset() { return 26; }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY) {
        renderShapes(ctx, x, y, width, mouseX, mouseY);
        renderOverlay(ctx, x, y, width, mouseX, mouseY);
    }

    @Override
    public void renderShapes(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY) {
        // Track only — static given the corner-radius token, so it lives in
        // the cached static layer (drawn through the shared themed slider).
        drawTrackShapes(ctx, x, y, width);
    }

    @Override
    public void renderOverlay(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY) {
        // Flush a deferred live apply once the throttle interval elapsed.
        if (throttle.due()) flushPending();

        drawLabelRow(ctx, x, y, width, mouseX, mouseY);
        drawTrackOverlay(ctx, x, y, width, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (isDisabled() || button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;
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
        if (!dragging || button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;
        applyFromMouse(mouseX);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && dragging) {
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
        boolean shift = (modifiers & InputConstants.MOD_SHIFT) != 0;
        boolean ctrl  = (modifiers & InputConstants.MOD_CONTROL) != 0;
        // Whole-number rows have no finer step than one.
        double mult = shift ? 10.0 : (ctrl && step < 1 ? 0.1 : 1.0);
        if (keyCode == InputConstants.KEY_LEFT || keyCode == InputConstants.KEY_DOWN) {
            nudge(-step * mult);
            return true;
        }
        if (keyCode == InputConstants.KEY_RIGHT || keyCode == InputConstants.KEY_UP) {
            nudge(step * mult);
            return true;
        }
        return false;
    }

    private void nudge(double delta) {
        double cur = getter.getAsDouble();
        double next = snap(cur + delta);
        if (next != cur) {
            setter.accept(next);          // one reload per genuine change
            AuroraConfig.save();
        }
    }

    private void applyFromMouse(double mouseX) {
        double t = (mouseX - lastTrackX) / (double) lastTrackW;
        pendingValue = snap(min + t * (max - min));
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

    /** Round to the nearest step from {@code min}, clamped to the range. */
    private double snap(double v) {
        return clamp(min + Math.round((v - min) / step) * step, min, max);
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
