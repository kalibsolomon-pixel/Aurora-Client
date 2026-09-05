package com.aurora.client.ui.component;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.Animation;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraAnim;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * iOS UISwitch — pill track + circular thumb, spring slide.
 *
 * <p>The track crossfades between the mode-aware neutral
 * ({@code SURFACE_VARIANT}) and the {@code ACCENT} token on the same curve
 * as the thumb position. The thumb is pure white (a structural neutral —
 * correct in both modes). All curves render through the high-res AA engine.
 *
 * <p>Cache discipline: the settled toggle is the cacheable shape layer,
 * keyed on on/off state + slide-in-progress via {@link #shapeFingerprint()};
 * while the thumb slides it renders live in {@link #renderOverlay}.
 */
public class ToggleSwitch extends Widget {

    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private boolean disabled = false;

    private boolean slideInProgress = false;
    private Animation slideAnim;

    public ToggleSwitch(BooleanSupplier getter, Consumer<Boolean> setter) {
        this.getter = getter;
        this.setter = setter;
    }

    public ToggleSwitch disabled(boolean d) {
        this.disabled = d;
        return this;
    }

    @Override
    public int shapeFingerprint() {
        return (getter.getAsBoolean() ? 1 : 0) ^ (slideInProgress ? 2 : 0);
    }

    @Override
    public void renderShapes(GuiGraphics g, float x, float y, float w, float h) {
        if (slideInProgress) return;
        draw(g, x, y, w, h, getter.getAsBoolean() ? 1f : 0f);
    }

    @Override
    public void renderOverlay(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        boolean on = getter.getAsBoolean();
        boolean animatingThisFrame = slideInProgress;
        float t = advance(on); // advances once per frame; clears slideInProgress on settle
        if (animatingThisFrame) {
            draw(g, x, y, w, h, t);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (disabled || button != 0 || !inBounds(mx, my, x, y, w, h)) return false;
        toggle();
        return true;
    }

    /** Flip the switch (setter + start the slide animation). Hosts with a broader hit-test call this directly. */
    public void toggle() {
        setter.accept(!getter.getAsBoolean());
        slideInProgress = true; // recapture empties the cached toggle; the slide renders live
    }

    private void draw(GuiGraphics g, float x, float y, float w, float h, float t) {
        int activeCol = ThemeManager.color(ThemeToken.ACCENT);
        int inactiveCol = ThemeManager.color(ThemeToken.SURFACE_VARIANT);
        int pill = AuroraAnim.lerpArgb(inactiveCol, activeCol, t);

        // The switch is ALWAYS a full capsule, deliberately decoupled from the
        // theme's Corner Style token — matching the project convention that
        // knobs/thumbs stay circular in every roundness mode (see
        // ResolvedTheme.project()'s note on KNOB_RADIUS). Reading
        // roundness().radiusSmall() here previously made the track collapse
        // to a rectangle at Corner Style = Square.
        float radius = h / 2f;

        RenderUtil.drawRoundedRectAA(g, x, y, w, h, radius, pill);
        // Hairline separator token for the pill edge (mode-aware, replaces the
        // old hardcoded translucent white which vanished in light mode).
        RenderUtil.drawRoundedOutlineAA(g, x, y, w, h, radius, 1.0f, ThemeManager.color(ThemeToken.BORDER));

        float thumbD = h - 5f;               // 2.5 px inset per side (10 px on a 15 px track)
        float minX = x + 2.5f;
        float maxX = x + w - thumbD - 2.5f;
        float knobX = minX + (maxX - minX) * t;
        float knobY = y + (h - thumbD) / 2f;
        // Perfect circle, single continuous AA row loop — no partitions, no
        // squircle exponent, no corner-style inheritance: seamless and round
        // at every roundness setting and in both toggle states.
        RenderUtil.drawCircleAA(g, knobX, knobY, thumbD, thumbD, 0xFFFFFFFF);
    }

    /** Wall-clock stamp of the previous advance() call; drives real-dt animation. */
    private long lastAdvanceNs = 0L;

    private float advance(boolean on) {
        if (slideAnim == null) {
            slideAnim = new Animation(on ? 1f : 0f, 12.0f);
            slideAnim.setCurrentValue(on ? 1f : 0f);
        }
        // Real per-frame delta, not a hardcoded 60fps assumption — the old
        // 0.016f constant made the slide run ~2x fast at 120fps and ~2x slow
        // at 30. Animation.update clamps the step internally, so a hitch or
        // an idle gap cannot produce a jump.
        long nowNs = System.nanoTime();
        float dt = lastAdvanceNs == 0L ? 0.016f : (nowNs - lastAdvanceNs) / 1_000_000_000f;
        lastAdvanceNs = nowNs;
        slideAnim.setTargetValue(on ? 1f : 0f);
        slideAnim.update(dt);
        if (slideAnim.getCurrentValue() == slideAnim.getTargetValue()) {
            slideInProgress = false;
        }
        return slideAnim.getCurrentValue();
    }
}
