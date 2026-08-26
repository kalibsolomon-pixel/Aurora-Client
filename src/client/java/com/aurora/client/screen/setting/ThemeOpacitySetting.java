package com.aurora.client.screen.setting;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.component.Slider;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Slider for the theme's panel-background <b>opacity</b> (alpha only — hue
 * and lightness are untouched). Visually identical to
 * {@link DoubleSliderSetting} (percent readout, thin track, circular knob)
 * but with drag-aware commit discipline, because unlike a gameplay slider
 * every applied value triggers a theme re-resolve:
 *
 * <ul>
 *   <li><b>During drag:</b> the pending value is applied through the
 *       standard setter (config write + one {@code ThemeManager.reload()})
 *       at most once per {@link #LIVE_RELOAD_MIN_MS} — live preview without
 *       reload spam. A deferred apply is flushed from {@link #render} once
 *       the interval elapses (a single timestamp comparison when idle).</li>
 *   <li><b>On release:</b> the final value is applied only if it still
 *       differs from the live config, then persisted — zero extra reloads
 *       when nothing changed since the last throttled apply.</li>
 * </ul>
 *
 * <p>Because the setter writes the config before reloading, the per-tick
 * {@code ThemeManager.sync()} dirty-check never sees a stale gap: it only
 * reloads on values this slider did not apply itself.
 */
public class ThemeOpacitySetting extends FeatureSetting {

    private static final int CONTROL_H = 36;
    /** iOS UISlider track height — matches the other slider rows. */
    private static final int TRACK_H = 4;
    private static final int KNOB_R = 7;
    private static final double STEP = 0.01;
    /** Minimum spacing between live (mid-drag) reloads. */
    private static final long LIVE_RELOAD_MIN_MS = 100L;

    private final DoubleSupplier getter;
    private final DoubleConsumer setter;

    /** Shared themed slider — owns the track/fill/knob drawing (value read through the display lambda). */
    private final Slider slider;

    private boolean dragging = false;
    private double pendingValue;
    private boolean liveApplyPending = false;
    private long lastLiveReloadMs = 0L;
    private long nextLiveReloadMs = 0L;

    private int lastTrackX, lastTrackW;
    private int lastWidth = 240;

    private long cachedValueKey = Long.MIN_VALUE;
    private String cachedValueStr = "";
    private int cachedValueStrW = 0;

    public ThemeOpacitySetting(String label, DoubleSupplier getter, DoubleConsumer setter) {
        super(label);
        this.getter = getter;
        this.setter = setter;
        // Display value = the pending drag value while dragging (smooth),
        // otherwise the committed config value.
        this.slider = new Slider(() -> dragging ? pendingValue : getter.getAsDouble(), setter, 0, 1, STEP);
    }

    @Override public ThemeOpacitySetting description(String desc) { super.description(desc); return this; }
    @Override public ThemeOpacitySetting description(java.util.function.Supplier<String> desc) { super.description(desc); return this; }

    @Override public int baseHeight() { return CONTROL_H; }
    @Override public int height() { return CONTROL_H + descriptionHeight(lastWidth); }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        renderShapes(ctx, x, y, width, mouseX, mouseY);
        renderOverlay(ctx, x, y, width, mouseX, mouseY);
    }

    @Override
    public void renderShapes(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        // Track only — static given the roundness token, so it lives in the
        // cached static layer (drawn through the shared themed slider).
        int trackX = x + 12;
        int trackW = width - 26;
        lastTrackX = trackX;
        lastTrackW = trackW;
        slider.layout(trackX, y, trackW, CONTROL_H);
        slider.renderShapes(ctx, trackX, y, trackW, CONTROL_H);
    }

    @Override
    public void renderOverlay(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        Font tr = Minecraft.getInstance().font;
        boolean disabled = isDisabled();

        // Flush a deferred live apply once the throttle interval elapsed.
        if (liveApplyPending && System.currentTimeMillis() >= nextLiveReloadMs) {
            flushPending();
        }

        double value = clamp01(dragging ? pendingValue : getter.getAsDouble());

        renderLabelWithTooltip(ctx, label, x + 12, y + 6, AuroraTheme.IOS_LABEL, mouseX, mouseY, disabled);

        long key = Math.round(value * 100.0);
        if (key != cachedValueKey) {
            cachedValueStr = Math.round(value * 100.0) + "%";
            cachedValueStrW = tr.width(cachedValueStr);
            cachedValueKey = key;
        }

        boolean focused = FeatureSetting.getFocused() == this;
        int valueColor = focused ? AuroraTheme.IOS_BLUE : AuroraTheme.IOS_SECONDARY_LABEL;
        ctx.drawString(tr, cachedValueStr, x + width - cachedValueStrW - 14, y + 6, valueColor, false);

        int trackX = x + 12;
        int trackW = width - 26;
        lastTrackX = trackX;
        lastTrackW = trackW;

        slider.disabled(disabled);
        slider.layout(trackX, y, trackW, CONTROL_H);
        slider.renderOverlay(ctx, trackX, y, trackW, CONTROL_H, mouseX, mouseY);
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
        long now = System.currentTimeMillis();
        if (now - lastLiveReloadMs >= LIVE_RELOAD_MIN_MS) {
            setter.accept(pendingValue);
            lastLiveReloadMs = now;
            liveApplyPending = false;
        } else {
            liveApplyPending = true;
            nextLiveReloadMs = lastLiveReloadMs + LIVE_RELOAD_MIN_MS;
        }
    }

    /** Apply the pending value if it still differs from the live config. */
    private void flushPending() {
        if (Double.compare(pendingValue, getter.getAsDouble()) != 0) {
            setter.accept(pendingValue);
        }
        lastLiveReloadMs = System.currentTimeMillis();
        liveApplyPending = false;
    }

    private static double clamp01(double v) {
        return v < 0d ? 0d : (v > 1d ? 1d : v);
    }
}