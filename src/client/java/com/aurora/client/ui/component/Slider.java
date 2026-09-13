package com.aurora.client.ui.component;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.HoverAnim;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Themed slider — flat capsule track, accent fill, circular knob.
 *
 * <p>Consolidates the near-identical int/double/opacity sliders into one
 * control. The track renders in the cacheable shape layer (it depends only
 * on the roundness token); the fill + knob + focus ring render live since
 * they track the value and hover. Colors are token-driven; the knob is pure
 * white (a structural neutral). Drag snaps to {@code step}; arrow keys nudge
 * by one step (Shift ×10, Ctrl ×0.1).
 */
public class Slider extends Widget {

    private static final float TRACK_H = 4f;
    private static final float KNOB_R = 7f;

    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final double min, max, step;

    private boolean disabled = false;
    private boolean dragging = false;
    private final HoverAnim hoverAnim = new HoverAnim(140L);

    public Slider(DoubleSupplier getter, DoubleConsumer setter, double min, double max, double step) {
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public Slider disabled(boolean d) {
        this.disabled = d;
        return this;
    }

    public double value() {
        return getter.getAsDouble();
    }

    /**
     * Applies an exact value programmatically (snapped and clamped to the
     * range, like a drag) — the precise-entry path of
     * {@code SliderSetting.editableValue()}, where typing "1737" must land
     * on 1737, not on wherever a mouse ratio would put it.
     */
    public void setValue(double raw) {
        setter.accept(snap(raw));
    }

    /** Track Y within the widget box (bottom-anchored, matching the row layout). */
    private float trackY() {
        return y + h - 12f;
    }

    @Override
    public void renderShapes(GuiGraphics g, float x, float y, float w, float h) {
        float ty = y + h - 12f;
        RenderUtil.drawRoundedRectAA(g, x, ty, w, TRACK_H, TRACK_H / 2f, trackBase());
    }

    @Override
    public void renderOverlay(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        float ty = y + h - 12f;
        boolean hovered = !disabled && (dragging || inBounds(mouseX, mouseY, x - 4, ty - 6, w + 8, TRACK_H + 12));
        float hT = hoverAnim.update(hovered);

        float t = (float) ((getter.getAsDouble() - min) / (max - min));
        t = Math.max(0f, Math.min(1f, t));
        float filledW = w * t;
        if (filledW > 0) {
            RenderUtil.drawRoundedRectAA(g, x, ty, filledW, TRACK_H, TRACK_H / 2f, fill());
        }

        float knobX = x + filledW;
        float knobY = ty + TRACK_H / 2f;
        float knobD = KNOB_R * 2f;
        RenderUtil.drawRoundedRectAA(g, knobX - KNOB_R, knobY - KNOB_R, knobD, knobD, KNOB_R, knob());

        if (hT > 0f) {
            int onBg = ThemeManager.color(ThemeToken.ON_BACKGROUND) & 0x00FFFFFF;
            int a = Math.round(48 * hT);
            RenderUtil.drawRoundedOutlineAA(g, x - 3f, ty - 3f, w + 6f, TRACK_H + 6f,
                    TRACK_H / 2f + 3f, 1.0f, (a << 24) | onBg);
        }
    }

    private int trackBase() {
        return disabled ? ThemeManager.color(ThemeToken.SURFACE_INSET)
                : ThemeManager.color(ThemeToken.SURFACE_VARIANT);
    }

    private int fill() {
        return disabled ? ThemeManager.color(ThemeToken.BORDER)
                : ThemeManager.color(ThemeToken.ACCENT);
    }

    private int knob() {
        return disabled ? ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED) : 0xFFFFFFFF;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (disabled || button != 0) return false;
        if (!inBounds(mx, my, x - 4, trackY() - 6, w + 8, TRACK_H + 12)) return false;
        dragging = true;
        applyFromMouse(mx);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging || button != 0) return false;
        applyFromMouse(mx);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyPress(int keyCode, int modifiers) {
        if (disabled) return false;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        double mult = shift ? 10.0 : (ctrl ? 0.1 : 1.0);
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_DOWN) {
            applyDelta(-step * mult);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_UP) {
            applyDelta(step * mult);
            return true;
        }
        return false;
    }

    private void applyDelta(double delta) {
        double next = snap(getter.getAsDouble() + delta);
        setter.accept(next);
    }

    private void applyFromMouse(double mx) {
        double t = (mx - x) / (double) w;
        t = Math.max(0, Math.min(1, t));
        setter.accept(snap(min + t * (max - min)));
    }

    private double snap(double raw) {
        double v = Math.round(raw / step) * step;
        if (v < min) v = min;
        if (v > max) v = max;
        return v;
    }
}
