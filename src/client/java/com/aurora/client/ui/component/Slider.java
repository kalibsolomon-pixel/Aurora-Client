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
 *
 * <p><b>Phase B pilot (2026-09-16) — opt-in canonical state mode</b>
 * ({@link #canonicalStates()}; 62 slider rows share this component, so the
 * pilot must not migrate them silently). The default construction keeps the
 * legacy behavior byte-for-byte. Canonical mode adds overlapping state
 * channels:
 * <ul>
 *   <li><b>hover</b> — {@link HoverAnim#symmetric}(140): no snap from rest,
 *       reversible, continuous (the two prior pilots' proven mode). The
 *       settled endpoint (the 48-alpha track outline) is unchanged; only
 *       the path animates. Dragging keeps the hover channel alive when the
 *       pointer leaves the bounds — that clause is pre-existing and
 *       deliberate (§7: manipulation must not depend on continued
 *       hover).</li>
 *   <li><b>active dragging</b> — the knob expands one pixel in radius
 *       (7→8) the instant a drag is accepted and returns instantly on
 *       release: a mechanical "grip" signal, distinct from hover, with no
 *       staged animation (direct response outranks decoration on a
 *       continuous control) and a center that never moves — the knob's
 *       center stays exactly at the value coordinate through the
 *       deformation.</li>
 *   <li><b>focused</b> — opt-in {@link #focusedVisual(boolean)}: a 1 px
 *       accent capsule hairline 3 px outside the track, the same
 *       geometry-following family as the toggle/button pilots. Visible
 *       without hover, hue-distinct from the white hover halo, never
 *       touching the value pixels.</li>
 * </ul>
 *
 * <p>Cache note: the track's cached color depends on the disabled flag, so
 * {@link #shapeFingerprint()} carries a disabled bit — without it a
 * disabled↔enabled flip would leave a stale track color in the owning
 * screen's row cache (the latent staleness this pilot's audit found; no
 * production slider flips disabled today). Hover/drag/focus visuals all
 * render in the LIVE layer, so intermediate states cannot invalidate any
 * cache (the Button-safe category, not the toggle-hole category).
 */
public class Slider extends Widget {

    private static final float TRACK_H = 4f;
    private static final float KNOB_R = 7f;
    /** Canonical-mode drag grip: knob radius while an accepted drag is active. */
    private static final float KNOB_R_DRAG = KNOB_R + 1f;

    private final DoubleSupplier getter;
    private final DoubleConsumer setter;
    private final double min, max, step;

    private boolean disabled = false;
    private boolean dragging = false;
    /** Phase B opt-in: canonical hover/manipulation/focus channels (see class doc). */
    private boolean canonical = false;
    /** Opt-in focus paint (the row owns the focus lifecycle); never set => zero new pixels. */
    private boolean focusedVisual = false;
    private HoverAnim hoverAnim = new HoverAnim(140L);

    public Slider(DoubleSupplier getter, DoubleConsumer setter, double min, double max, double step) {
        this.getter = getter;
        this.setter = setter;
        this.min = min;
        this.max = max;
        this.step = step;
    }

    /**
     * Opts this slider into the Phase B canonical state channels (symmetric
     * 140 ms hover, drag-grip knob, focus hairline). Must be called before
     * first render (construction time) — it swaps the hover animator.
     */
    public Slider canonicalStates() {
        this.canonical = true;
        this.hoverAnim = HoverAnim.symmetric(140L);
        return this;
    }

    /** True when this slider runs the Phase B canonical state channels. */
    public boolean canonical() {
        return canonical;
    }

    public Slider disabled(boolean d) {
        this.disabled = d;
        return this;
    }

    /** Focus paint opt-in — the host row mirrors its keyboard focus here each frame. */
    public Slider focusedVisual(boolean focused) {
        this.focusedVisual = focused;
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
    public int shapeFingerprint() {
        // The cached layer (track) depends on the disabled color — carry the
        // bit so a flip invalidates the owning screen's row cache.
        return disabled ? 1 : 0;
    }

    @Override
    public void renderShapes(GuiGraphics g, float x, float y, float w, float h) {
        float ty = y + h - 12f;
        RenderUtil.drawRoundedRectAA(g, x, ty, w, TRACK_H, TRACK_H / 2f, trackBase());
    }

    @Override
    public void renderOverlay(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        float ty = y + h - 12f;
        // Dragging keeps the hover channel alive outside the bounds — the
        // pointer owning an active manipulation must not lose feedback
        // because it crossed a geometry edge (pre-existing, deliberate).
        boolean hovered = !disabled && (dragging || inBounds(mouseX, mouseY, x - 4, ty - 6, w + 8, TRACK_H + 12));
        float hT = hoverAnim.update(hovered);

        float t = (float) ((getter.getAsDouble() - min) / (max - min));
        t = Math.max(0f, Math.min(1f, t));
        float filledW = w * t;
        if (filledW > 0) {
            RenderUtil.drawRoundedRectAA(g, x, ty, filledW, TRACK_H, TRACK_H / 2f, fill());
        }

        // Knob — center stays exactly at the value coordinate; the canonical
        // drag grip grows the RADIUS around that center (no center shift).
        float knobR = canonical && dragging ? KNOB_R_DRAG : KNOB_R;
        float knobX = x + filledW;
        float knobY = ty + TRACK_H / 2f;
        RenderUtil.drawRoundedRectAA(g, knobX - knobR, knobY - knobR, knobR * 2f, knobR * 2f, knobR, knob(t));

        if (hT > 0f) {
            int onBg = ThemeManager.color(ThemeToken.ON_BACKGROUND) & 0x00FFFFFF;
            int a = Math.round(48 * hT);
            RenderUtil.drawRoundedOutlineAA(g, x - 3f, ty - 3f, w + 6f, TRACK_H + 6f,
                    TRACK_H / 2f + 3f, 1.0f, (a << 24) | onBg);
        }

        // Canonical focus treatment: accent capsule hairline 3 px outside the
        // track — the geometry-following family (toggle/button pilots),
        // hue-distinct from the white hover halo and from the accent FILL
        // (which stays inside the track at the value).
        if (canonical && focusedVisual) {
            RenderUtil.drawRoundedOutlineAA(g, x - 3f, ty - 3f, w + 6f, TRACK_H + 6f,
                    TRACK_H / 2f + 3f, 1.0f,
                    ThemeManager.semanticContrast().focusNeutral());
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

    private int knob(float valueT) {
        return disabled ? ThemeManager.semanticContrast().disabledText()
                : valueT > 0f ? ThemeManager.semanticContrast().mechanicalOn()
                        : ThemeManager.semanticContrast().mechanicalOff();
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

    // Package-private test visibility (Phase B pilot).
    HoverAnim hoverAnimator() { return hoverAnim; }
    boolean isDragging() { return dragging; }
    float knobRadiusPx() { return canonical && dragging ? KNOB_R_DRAG : KNOB_R; }
}
