package com.aurora.client.ui.component;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Themed squircle button — the single shared implementation for every
 * button in Aurora's UI (screen actions, row actions, inline controls).
 *
 * <p>Primary: accent-gradient fill + {@code ON_ACCENT} label. Secondary:
 * {@code SURFACE}→{@code SURFACE_VARIANT} fill, {@code BORDER}→
 * {@code BORDER_HOVER} outline, {@code ON_BACKGROUND} label. All colors are
 * token-driven, so the button is correct in Dark and Light mode (the old
 * translucent-white secondary fill was invisible on light surfaces). Hover
 * eases over 140 ms; press scales to 96% with a spring release. Text is
 * always non-shadowed.
 */
public class Button extends Widget {

    private static final long HOVER_MS = 140L;
    private static final long PRESS_DOWN_MS = 90L;
    private static final long PRESS_UP_MS = 180L;

    private final Component label;
    private final Runnable onPress;
    private final boolean primary;

    private boolean disabled = false;
    /** Destructive variant — semantic-error fill/border for delete-style actions. */
    private boolean destructive = false;

    /**
     * Glass pilot material variants, Theme screen only:
     * <ul>
     *   <li>{@code OFF} — opaque token fill + outline (the pre-pilot look;
     *       every button outside the pilot).</li>
     *   <li>{@code NEUTRAL} — raised glass with the WINDOW_FILL tint
     *       (secondary/unselected controls).</li>
     *   <li>{@code STAINED} — raised glass with the accent-stained tint
     *       ({@link ThemeManager#stainedTint()}) — primary/selected controls.
     *       Both stained and neutral are RAISED; selection state reads
     *       through the tint, never through the lighting orientation.</li>
     * </ul>
     */
    public enum GlassStyle { OFF, NEUTRAL, STAINED }

    private GlassStyle glassStyle = GlassStyle.OFF;

    private long hoverStartMs = 0;
    private boolean wasActive = false;
    private float hoverT = 0f;

    private long pressDownStartMs = -1L;

    public Button(String label, Runnable onPress) {
        this(Component.literal(label), onPress, false);
    }

    public Button(String label, Runnable onPress, boolean primary) {
        this(Component.literal(label), onPress, primary);
    }

    public Button(Component label, Runnable onPress) {
        this(label, onPress, false);
    }

    public Button(Component label, Runnable onPress, boolean primary) {
        this.label = label;
        this.onPress = onPress;
        this.primary = primary;
    }

    public Button disabled(boolean d) {
        this.disabled = d;
        return this;
    }

    /**
     * Destructive styling — fill and outline from the {@code SEMANTIC_ERROR}
     * token (resting 0x44 alpha, hover 0x88 — matching the historical delete
     * chip strengths) with a white label, which reads on the fixed-hue error
     * family in both modes.
     */
    public Button destructive(boolean d) {
        this.destructive = d;
        return this;
    }

    /** Glass pilot — see {@link GlassStyle}. Neutral glass (secondary look). */
    public Button glassBackground(boolean g) {
        return glassStyle(g ? GlassStyle.NEUTRAL : GlassStyle.OFF);
    }

    /** Glass pilot — full variant selector (neutral vs accent-stained). */
    public Button glassStyle(GlassStyle s) {
        this.glassStyle = s;
        return this;
    }

    public Component label() {
        return label;
    }

    @Override
    public void renderOverlay(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;

        boolean active = !disabled && inBounds(mouseX, mouseY, x, y, w, h);
        advanceHover(active);
        float scale = currentScale();

        float cx = x + w / 2f;
        float cy = y + h / 2f;
        g.pose().pushMatrix();
        if (scale != 1.0f) {
            g.pose().translate(cx, cy);
            g.pose().scale(scale, scale);
            g.pose().translate(-cx, -cy);
        }

        float radius = ThemeManager.current().roundness().radiusSmall();

        int bg, border, text;
        if (disabled) {
            bg = ThemeManager.color(ThemeToken.SURFACE_INSET);
            border = ThemeManager.color(ThemeToken.BORDER);
            text = ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED);
        } else if (destructive) {
            int err = ThemeManager.color(ThemeToken.SEMANTIC_ERROR);
            bg = AuroraAnim.lerpArgb(
                    ThemeManager.withAlpha(err, 0x44),
                    ThemeManager.withAlpha(err, 0x88), hoverT);
            border = err;
            text = 0xFFFFFFFF;
        } else if (primary) {
            bg = AuroraAnim.lerpArgb(
                    ThemeManager.color(ThemeToken.ACCENT_GRAD_BOT),
                    ThemeManager.color(ThemeToken.ACCENT_GRAD_TOP), hoverT);
            border = AuroraAnim.lerpArgb(0x22FFFFFF, 0x44FFFFFF, hoverT);
            text = ThemeManager.color(ThemeToken.ON_ACCENT);
        } else {
            bg = AuroraAnim.lerpArgb(
                    ThemeManager.color(ThemeToken.SURFACE),
                    ThemeManager.color(ThemeToken.SURFACE_VARIANT), hoverT);
            border = AuroraAnim.lerpArgb(
                    ThemeManager.color(ThemeToken.BORDER),
                    ThemeManager.color(ThemeToken.BORDER_HOVER), hoverT);
            text = ThemeManager.color(ThemeToken.ON_BACKGROUND);
        }

        if (glassStyle != GlassStyle.OFF && scale == 1.0f && !disabled && !destructive) {
            // Glass pilot: blur + raised lighting replace the fill + outline
            // (the glass rim replaces the border — no double outline). The
            // tint keeps the hierarchy rule: NEUTRAL (secondary) uses
            // WINDOW_FILL, whose alpha carries the theme's Background
            // Opacity — single application point; STAINED (primary) uses
            // ThemeManager.stainedTint() (accent RGB, same opacity
            // discipline). While a press animation runs (scale != 1) or the
            // look is not the standard surface, the plain token fill is
            // drawn instead: renderPanel places its quad from raw GUI
            // coordinates, which would not follow the pose's press scaling.
            if (BlurPanelRenderer.renderPanel(g, x, y, w, h, radius,
                    BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX,
                    BlurPanelRenderer.Lighting.raised())) {
                int tint = glassStyle == GlassStyle.STAINED
                        ? ThemeManager.stainedTint()
                        : ThemeManager.color(ThemeToken.WINDOW_FILL);
                RenderUtil.drawRoundedRectAA(g, x, y, w, h, radius, tint);
            } else {
                RenderUtil.drawRoundedRectAA(g, x, y, w, h, radius, bg);
                RenderUtil.drawRoundedOutlineAA(g, x, y, w, h, radius, 1.0f, border);
            }
        } else {
            RenderUtil.drawRoundedRectAA(g, x, y, w, h, radius, bg);
            RenderUtil.drawRoundedOutlineAA(g, x, y, w, h, radius, 1.0f, border);
        }

        int textX = Math.round(x + w / 2f);
        int textY = Math.round(y + (h - tr.lineHeight) / 2f) + 1;
        AuroraFontRenderer.drawCentered(g, tr, label, textX, textY, text);

        g.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (disabled || button != 0 || !inBounds(mx, my, x, y, w, h)) return false;
        pressDownStartMs = System.currentTimeMillis();
        if (onPress != null) onPress.run();
        return true;
    }

    private void advanceHover(boolean active) {
        if (active != wasActive) {
            hoverStartMs = System.currentTimeMillis() - (long) ((1f - hoverT) * HOVER_MS);
            wasActive = active;
        }
        float target = active ? 1f : 0f;
        if (hoverT != target) {
            long elapsed = System.currentTimeMillis() - hoverStartMs;
            float raw = Math.min(1f, Math.max(0f, elapsed / (float) HOVER_MS));
            float t = active ? raw : (1f - raw);
            hoverT = AuroraAnim.easeOutCubic(t);
            if (raw >= 1f) hoverT = target;
        }
    }

    private float currentScale() {
        if (pressDownStartMs > 0L) {
            long elapsed = System.currentTimeMillis() - pressDownStartMs;
            if (elapsed < PRESS_DOWN_MS) {
                float raw = Math.min(1f, Math.max(0f, elapsed / (float) PRESS_DOWN_MS));
                return AuroraAnim.lerp(1.0f, 0.96f, AuroraAnim.easeOutCubic(raw));
            } else if (elapsed < PRESS_DOWN_MS + PRESS_UP_MS) {
                float raw = Math.min(1f, Math.max(0f, (elapsed - PRESS_DOWN_MS) / (float) PRESS_UP_MS));
                return AuroraAnim.lerp(0.96f, 1.0f, AuroraAnim.springOvershoot(raw));
            } else {
                pressDownStartMs = -1L;
            }
        }
        return 1.0f;
    }
}
