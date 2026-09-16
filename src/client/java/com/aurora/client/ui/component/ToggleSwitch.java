package com.aurora.client.ui.component;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.Animation;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.HoverAnim;
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
 * <p><b>State-complete primitive (Design Language v3 Phase B pilot).</b>
 * The states OVERLAP — they are independent channels, not one enum, because
 * e.g. ON+hover or focused+disabled are real simultaneous states:
 * <ul>
 *   <li><b>hover</b> — symmetric 140 ms enter / 140 ms exit
 *       ({@link HoverAnim#symmetric}, the §8.3 canonical hover: no snap
 *       from rest, mid-flight reversal is continuous, wall-clock driven).
 *       One restrained channel: the track lerps 10% toward the mode's
 *       {@code ON_BACKGROUND} (lifts in dark mode, deepens in light) —
 *       "under the pointer" without competing with the ON/OFF fill.</li>
 *   <li><b>pressed</b> — a one-shot mechanical pulse on the ACCEPTED
 *       activation (fired from {@link #toggle()}): the thumb compresses to
 *       92% over 90 ms and recovers over 180 ms — track/thumb geometry
 *       responding, not a whole-control scale, and never a second toggle
 *       event. A disabled toggle is never toggled, so it never pulses.</li>
 *   <li><b>focused</b> — an opt-in visual (hosts with a keyboard path set
 *       {@link #focusedVisual}): a 1 px accent hairline capsule 1.5 px
 *       OUTSIDE the track — visible without hover, distinct from the ON
 *       fill (outside vs. inside the pill), correct in both modes. The
 *       provisional-treatment family (same token/alpha as the button
 *       hairline); the final Aurora focus visual is still a Phase B
 *       decision.</li>
 *   <li><b>disabled</b> — clearly inert while still communicating the
 *       stored value: OFF rests on {@code SURFACE_INSET}, ON keeps a 35%
 *       accent residue over it, the thumb goes {@code ON_BACKGROUND_MUTED}.
 *       No hover/press feedback on any disabled channel.</li>
 * </ul>
 *
 * <p>Cache discipline: the toggle renders FULLY LIVE and does not
 * participate in the shape cache ({@link #shapeFingerprint()} is a constant
 * {@code 0}). A cached-settled-toggle design was piloted first and
 * rejected: its fingerprint could only flip on a settled-state change, so
 * the moment a hover EXIT began the cache re-rastered with the toggle
 * absent (the animator was mid-flight and the shape pass early-returned)
 * and the settle-back to the original fingerprint never refilled that hole
 * — the track vanished under the live thumb. Going fully live also deletes
 * the old design's full-frame row-cache re-raster on every on/off flip (a
 * state change invalidated the WHOLE screen's row layer for one 28×15
 * widget). Cost: ~35 AA fills per toggle per frame — a settings screen
 * carries 1–6 toggles (~200 fills), far under the ~2k imperceptibility
 * line (AGENTS.md §10), and the removed per-flip full-cache re-rasters
 * dwarf it on every screen with a list of booleans.
 */
public class ToggleSwitch extends Widget {

    /** Canonical §8.3 hover duration (enter and exit). */
    private static final long HOVER_MS = 140L;
    /** Press-pulse timings: ~90 ms compression, ~180 ms recovery (§9 press baseline). */
    private static final long PRESS_DOWN_MS = 90L;
    private static final long PRESS_TOTAL_MS = PRESS_DOWN_MS + 180L;
    /** Peak thumb compression at the pulse's deepest point. */
    static final float PRESS_COMPRESSION = 0.08f;
    /** Peak hover wash: fraction of the lerp toward ON_BACKGROUND. */
    private static final float HOVER_WASH = 0.10f;
    /** Accent residue kept on a disabled ON track (value legibility while inert). */
    private static final float DISABLED_ON_RESIDUE = 0.35f;

    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;
    private boolean disabled = false;

    private Animation slideAnim;

    /** §8.3 symmetric hover — 140 ms enter, 140 ms exit, continuous reversal. */
    private final HoverAnim hoverAnim = HoverAnim.symmetric(HOVER_MS);
    /** One-shot accepted-activation pulse start (-1 = idle), like Button's press stamp. */
    private long pressStartMs = -1L;
    /** Opt-in focus paint (hosts with a keyboard path); never set => zero new pixels. */
    private boolean focusedVisual = false;

    public ToggleSwitch(BooleanSupplier getter, Consumer<Boolean> setter) {
        this.getter = getter;
        this.setter = setter;
    }

    public ToggleSwitch disabled(boolean d) {
        this.disabled = d;
        return this;
    }

    /**
     * Opts this toggle into painting the keyboard-focus hairline (the
     * provisional Phase B treatment). Purely visual — the host owns the
     * focus lifecycle; unset hosts (the theme-preview mock) render exactly
     * as before.
     */
    public ToggleSwitch focusedVisual(boolean focused) {
        this.focusedVisual = focused;
        return this;
    }

    @Override
    public int shapeFingerprint() {
        // Fully live — no cacheable shape layer (see the class javadoc's
        // cache-discipline note for why this is deliberate).
        return 0;
    }

    @Override
    public void renderShapes(GuiGraphics g, float x, float y, float w, float h) {
        // Fully live — everything renders in renderOverlay every frame.
    }

    @Override
    public void renderOverlay(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        boolean on = getter.getAsBoolean();
        float t = advance(on); // advances once per frame; wall-clock real-dt
        boolean hovered = !disabled && inBounds(mouseX, mouseY, x, y, w, h);
        float hoverT = hoverAnim.update(hovered);

        // Track (fill carries the ON/OFF state, the hover wash, and the
        // disabled treatment) + hairline outline.
        drawTrack(g, x, y, w, h, t, hoverT);

        // Thumb: position rides the slide, diameter rides the press pulse,
        // color carries the disabled treatment.
        float pulse = pressCompression(System.currentTimeMillis() - pressStartMs);
        float thumbD = (h - 5f) * (1f - pulse);   // 2.5 px inset per side (10 px on a 15 px track)
        float minX = x + 2.5f;
        float maxX = x + w - (h - 5f) - 2.5f;
        float knobX = minX + (maxX - minX) * t;
        float knobY = y + (h - thumbD) / 2f;
        int thumbCol = disabled
                ? ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED)
                : 0xFFFFFFFF;
        RenderUtil.drawCircleAA(g, knobX, knobY, thumbD, thumbD, thumbCol);

        // Provisional focus treatment: geometry-following accent hairline
        // OUTSIDE the capsule — visible without hover, distinct from the ON
        // fill, both modes. Drawn last, additive, over both OFF and ON.
        if (focusedVisual) {
            float radius = h / 2f;
            RenderUtil.drawRoundedOutlineAA(g, x - 1.5f, y - 1.5f, w + 3f, h + 3f,
                    radius + 1.5f, 1.0f,
                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ACCENT), 0x99));
        }
    }

    /**
     * The track fill for state {@code t} (0 = OFF, 1 = ON) with the hover
     * wash {@code hoverT} applied. Disabled replaces the interactive colors:
     * OFF rests on the inset surface, ON keeps an accent residue over it.
     */
    private void drawTrack(GuiGraphics g, float x, float y, float w, float h, float t, float hoverT) {
        int base;
        if (disabled) {
            base = AuroraAnim.lerpArgb(
                    ThemeManager.color(ThemeToken.SURFACE_INSET),
                    ThemeManager.color(ThemeToken.ACCENT), DISABLED_ON_RESIDUE * t);
        } else {
            int activeCol = ThemeManager.color(ThemeToken.ACCENT);
            int inactiveCol = ThemeManager.color(ThemeToken.SURFACE_VARIANT);
            base = AuroraAnim.lerpArgb(inactiveCol, activeCol, t);
        }
        // One restrained hover channel: toward the mode's primary foreground
        // (lifts the track in dark mode, deepens it in light) — reads as
        // "under the pointer" without competing with the ON/OFF state.
        int washTarget = ThemeManager.color(ThemeToken.ON_BACKGROUND);
        int pill = AuroraAnim.lerpArgb(base, washTarget, HOVER_WASH * hoverT);

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
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (disabled || button != 0 || !inBounds(mx, my, x, y, w, h)) return false;
        toggle();
        return true;
    }

    /** Flip the switch (setter + press pulse + slide animation). Hosts with a broader hit-test call this directly. */
    public void toggle() {
        setter.accept(!getter.getAsBoolean());
        pressStartMs = System.currentTimeMillis(); // accepted-activation pulse — feedback, not a second event
    }

    /**
     * Thumb compression fraction for a press pulse {@code elapsedMs} ago:
     * 0 → {@link #PRESS_COMPRESSION} over 90 ms (ease-out), back to 0 over
     * 180 ms (ease-in-out, no overshoot). One-shot — resolves on its own,
     * so hosts without a release event still get a complete gesture.
     * Package-private static for unit tests.
     */
    static float pressCompression(long elapsedMs) {
        if (elapsedMs < 0 || elapsedMs >= PRESS_TOTAL_MS) return 0f;
        if (elapsedMs < PRESS_DOWN_MS) {
            return PRESS_COMPRESSION * AuroraAnim.easeOutCubic(elapsedMs / (float) PRESS_DOWN_MS);
        }
        float back = (elapsedMs - PRESS_DOWN_MS) / (float) (PRESS_TOTAL_MS - PRESS_DOWN_MS);
        return PRESS_COMPRESSION * (1f - AuroraAnim.easeInOutCubic(back));
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
        return slideAnim.getCurrentValue();
    }
}
