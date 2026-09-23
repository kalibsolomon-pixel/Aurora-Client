package com.aurora.client.ui.component;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.ui.util.UiLayerCache;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.HoverAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

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
    private boolean primary;

    private boolean disabled = false;
    /** Opt-in semantic focus ring; false preserves every legacy button pixel. */
    private boolean focused = false;
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

    /**
     * Hover crossfade — the §8.3 canonical symmetric mode (Phase B pilot,
     * 2026-09-15): 140 ms enter AND 140 ms exit, no snap from locked rest,
     * mid-flight reversal mirrors the raw progress fraction (the same
     * semantics the ToggleSwitch pilot proved). Curve is the canonical
     * smoothstep (the former easeOutCubic copy moves to the shared
     * vocabulary); rest and settled-hover pixels are unchanged — only the
     * path between them.
     */
    private final HoverAnim hoverAnim = HoverAnim.symmetric(HOVER_MS);

    private long pressDownStartMs = -1L;

    /**
     * Glass-pass bookkeeping: the frame in which {@link #renderGlassPass}
     * last painted this button's surface, and whether the glass drew. When
     * {@link #renderOverlay} runs in that same frame it paints content
     * only (label, or the flat fallback if the glass declined); otherwise —
     * a screen not yet on the pre-dim discipline — it paints the surface in
     * place as it always did.
     */
    private long glassPassFrame = -1L;
    private boolean glassPassDrew = false;

    /**
     * Degradation priority of this button's glass under output-pool
     * pressure (see {@link BlurPanelRenderer.Priority}). CONTROL for a
     * standalone button; per-row / per-card buttons set DETAIL so they are
     * the first surfaces to go flat on an over-subscribed frame.
     */
    private BlurPanelRenderer.Priority priority = BlurPanelRenderer.Priority.CONTROL;

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

    public Button focused(boolean focused) {
        this.focused = focused;
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

    /** Variant selector for the flat fallback fill (see {@link #primary}). */
    public Button primary(boolean p) {
        this.primary = p;
        return this;
    }

    /** Glass pilot — see {@link GlassStyle}. Neutral glass (secondary look). */
    public Button glassBackground(boolean g) {
        return glassStyle(g ? GlassStyle.NEUTRAL : GlassStyle.OFF);
    }

    /** Degradation priority under output-pool pressure — see {@link #priority}. */
    public Button priority(BlurPanelRenderer.Priority p) {
        this.priority = p != null ? p : BlurPanelRenderer.Priority.CONTROL;
        return this;
    }

    /** Glass pilot — full variant selector (neutral vs accent-stained). */
    public Button glassStyle(GlassStyle s) {
        this.glassStyle = s;
        return this;
    }

    public Component label() {
        return label;
    }

    /**
     * Glass is only the standard raised surface: never while a press
     * animation runs (renderPanel places its quad from raw GUI coordinates
     * and could not follow the pose's press scaling), never disabled, never
     * destructive (error semantics must not read as glass chrome).
     */
    private boolean glassEligible(float scale) {
        return glassStyle != GlassStyle.OFF && scale == 1.0f && !disabled && !destructive;
    }

    /**
     * Pre-dim surface: NEUTRAL → raised {@code WINDOW_FILL} glass, STAINED
     * → raised accent-stained glass (both raised — selection/primacy reads
     * through the tint, never the lighting orientation). See
     * {@link Widget#renderGlassPass}.
     */
    // ---- Resting-surface templates (audit P2 residue) ----
    //
    // A button's SURFACE at rest is one small rounded fill (+ outline on the
    // flat variants) — but a grid/list full of buttons re-submitting that
    // per frame was the surviving fill volume after the row templates landed
    // (per-card Install tints on the pack browser, per-row Delete flats on
    // the manager lists). At rest the surface is fully determined by
    // (size, variant, theme, GUI scale), so each distinct combination
    // rasterizes once into a shared session cache and blits per button —
    // the same discipline as the row/card templates, one level down.
    // Session-static and bounded by distinct (size × variant) pairs, like
    // the renderer's rim-mask cache; re-raster keyed off the theme
    // generation stamp (in-place re-upload, no mid-frame texture release).
    private static final int KIND_FLAT_SECONDARY = 0;
    private static final int KIND_FLAT_PRIMARY = 1;
    private static final int KIND_FLAT_DESTRUCTIVE = 2;
    private static final int KIND_GLASS_TINT = 3;
    private static final int KIND_GLASS_STAINED = 4;
    private static final int KIND_GLASS_STAINED_PILOT = 5;
    private static final Map<Long, UiLayerCache> SURFACE_TEMPLATES = new HashMap<>();

    private static long templateKey(float w, float h, int kind) {
        int scale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
        return ThemeManager.generation() * 1_000_003L
                ^ (long) scale * 65537L
                ^ (long) Math.round(w) * 7919L
                ^ (long) Math.round(h) * 104729L
                ^ (long) kind * 31L;
    }

    /**
     * Blits the resting surface for {@code (w, h, kind)}, rasterizing it
     * first if needed. The raster draws the same calls the live paths draw
     * (keep the color expressions below in lockstep with
     * {@link #renderOverlay}'s flat branch and {@code GlassSurface.paint}'s
     * tints).
     */
    private static void blitSurfaceTemplate(GuiGraphics g, float x, float y, float w, float h,
                                            float radius, int kind) {
        long key = templateKey(w, h, kind);
        UiLayerCache cache = SURFACE_TEMPLATES.get(key);
        if (cache == null) {
            cache = new UiLayerCache();
            SURFACE_TEMPLATES.put(key, cache);
        }
        if (!cache.isCurrent(key)) {
            int scale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
            int iw = Math.round(w) + 2, ih = Math.round(h) + 2; // +1 logical AA margin
            cache.ensureSize(iw * scale, ih * scale);
            cache.clear();
            RenderUtil.RectSink prev = RenderUtil.beginCapture(cache.sink());
            try {
                switch (kind) {
                    case KIND_FLAT_SECONDARY -> {
                        RenderUtil.drawRoundedRectAA(g, 1, 1, w, h, radius, secondaryFlatFill(0f));
                        RenderUtil.drawRoundedOutlineAA(g, 1, 1, w, h, radius, 1.0f,
                                secondaryFlatBorder(0f));
                    }
                    case KIND_FLAT_PRIMARY -> {
                        RenderUtil.drawRoundedRectAA(g, 1, 1, w, h, radius,
                                ThemeManager.adaptiveOnAccent().buttonRest());
                        RenderUtil.drawRoundedOutlineAA(g, 1, 1, w, h, radius, 1.0f, 0x22FFFFFF);
                    }
                    case KIND_FLAT_DESTRUCTIVE -> {
                        int err = ThemeManager.color(ThemeToken.SEMANTIC_ERROR);
                        RenderUtil.drawRoundedRectAA(g, 1, 1, w, h, radius,
                                ThemeManager.withAlpha(err, 0x44));
                        RenderUtil.drawRoundedOutlineAA(g, 1, 1, w, h, radius, 1.0f, err);
                    }
                    default -> RenderUtil.drawRoundedRectAA(g, 1, 1, w, h, radius,
                            kind == KIND_GLASS_STAINED_PILOT
                                    ? ThemeManager.adaptiveOnAccent().stainedTint()
                                    : kind == KIND_GLASS_STAINED
                                            ? ThemeManager.stainedTint()
                                            : ThemeManager.color(ThemeToken.WINDOW_FILL));
                }
            } finally {
                RenderUtil.endCapture(prev);
            }
            cache.commit(key);
        }
        cache.blitAt(g, Math.round(x) - 1, Math.round(y) - 1, Math.round(w) + 2, Math.round(h) + 2);
    }

    @Override
    public void renderGlassPass(GuiGraphics g, float x, float y, float w, float h) {
        glassPassFrame = GlassSurface.frame();
        float radius = ThemeManager.current().roundness().radiusSmall();
        boolean eligible = glassEligible(currentScale());
        if (eligible) {
            // The tint fill comes from the resting-surface template (one blit)
            // while the panel and the deferred rim stay live — so the discard
            // wrap suppresses only GlassSurface's live tint submission.
            RenderUtil.RectSink prev = RenderUtil.beginCapture(RenderUtil.DISCARD_SINK);
            try {
                glassPassDrew = glassStyle == GlassStyle.STAINED && primary
                        ? GlassSurface.adaptiveOnAccentControl(g, x, y, w, h, radius, priority)
                        : GlassSurface.control(g, x, y, w, h, radius,
                                glassStyle == GlassStyle.STAINED, priority);
            } finally {
                RenderUtil.endCapture(prev);
            }
            if (glassPassDrew) {
                blitSurfaceTemplate(g, x, y, w, h, radius,
                        glassStyle == GlassStyle.STAINED
                                ? primary ? KIND_GLASS_STAINED_PILOT : KIND_GLASS_STAINED
                                : KIND_GLASS_TINT);
            }
        } else {
            glassPassDrew = false;
        }
    }

    @Override
    public void renderOverlay(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;

        boolean active = !disabled && inBounds(mouseX, mouseY, x, y, w, h);
        float hoverT = hoverAnim.update(active);
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
                    ThemeManager.adaptiveOnAccent().buttonRest(),
                    ThemeManager.adaptiveOnAccent().buttonHover(), hoverT);
            border = AuroraAnim.lerpArgb(0x22FFFFFF, 0x44FFFFFF, hoverT);
            text = ThemeManager.adaptiveOnAccent().foreground();
        } else {
            // C-6: the flat-secondary ramps are shared with the vanilla-gated
            // mixin painter — the single source (secondaryFlatFill/Border).
            bg = secondaryFlatFill(hoverT);
            border = secondaryFlatBorder(hoverT);
            text = ThemeManager.color(ThemeToken.ON_BACKGROUND);
        }

        // Surface. Glass (blur + raised lighting + tint + rim) replaces the
        // fill + outline — the glass rim replaces the border, no double
        // outline. If the screen ran this button's glass pass this frame
        // the surface is already on screen UNDER the dim and only its
        // result matters here; otherwise (legacy screens) it is painted in
        // place now. Either way a decline means the complete flat button.
        // At rest the surface comes from the shared resting-surface template
        // (see the template block above); hover/press/disabled paint live.
        boolean glassDrew;
        if (glassPassFrame == GlassSurface.frame()) {
            glassDrew = glassPassDrew;
        } else if (glassEligible(scale)) {
            RenderUtil.RectSink prev = RenderUtil.beginCapture(RenderUtil.DISCARD_SINK);
            try {
                glassDrew = glassStyle == GlassStyle.STAINED && primary
                        ? GlassSurface.adaptiveOnAccentControl(g, x, y, w, h, radius, priority)
                        : GlassSurface.control(g, x, y, w, h, radius,
                                glassStyle == GlassStyle.STAINED, priority);
            } finally {
                RenderUtil.endCapture(prev);
            }
            if (glassDrew) {
                blitSurfaceTemplate(g, x, y, w, h, radius,
                        glassStyle == GlassStyle.STAINED
                                ? primary ? KIND_GLASS_STAINED_PILOT : KIND_GLASS_STAINED
                                : KIND_GLASS_TINT);
            }
        } else {
            glassDrew = false;
        }
        if (!glassDrew) {
            if (scale == 1.0f && hoverT == 0f && !disabled) {
                blitSurfaceTemplate(g, x, y, w, h, radius, destructive ? KIND_FLAT_DESTRUCTIVE
                        : primary ? KIND_FLAT_PRIMARY : KIND_FLAT_SECONDARY);
            } else {
                RenderUtil.drawRoundedRectAA(g, x, y, w, h, radius, bg);
                RenderUtil.drawRoundedOutlineAA(g, x, y, w, h, radius, 1.0f, border);
            }
        }

        if (focused) {
            RenderUtil.drawRoundedOutlineAA(g, x, y, w, h, radius, 1.0f,
                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ACCENT), 0x99));
        }

        int textX = Math.round(x + w / 2f);
        int textY = Math.round(y + (h - tr.lineHeight) / 2f) + 1;
        AuroraFontRenderer.drawCentered(g, tr, label, textX, textY, text);

        g.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (disabled || button != 0 || !inBounds(mx, my, x, y, w, h)) return false;
        triggerPressAnimation();
        if (onPress != null) onPress.run();
        return true;
    }

    /** Starts the existing mouse-down press animation for a semantic activation. */
    public void triggerPressAnimation() {
        pressDownStartMs = System.currentTimeMillis();
    }

    /** The hover animator — package-private test visibility for the Phase B motion pilot. */
    HoverAnim hoverAnimator() {
        return hoverAnim;
    }

    // ---- Shared flat-secondary painter + press timeline (Phase C-6) ----
    //
    // The vanilla-gated AbstractButtonMixin paints Aurora-styled vanilla
    // buttons on the selection screens. To avoid a second independently
    // maintained copy of the ordinary secondary look, the color derivations
    // and the press-scale timeline live HERE as the single source:
    // renderOverlay's flat-secondary branch consumes the color helpers, and
    // the mixin consumes paintFlatSecondary/pressScaleAt. The painter is
    // PURE VISUALS — bounds, label, hoverT, press scale, focus, active —
    // and owns no activation, sound, narration, or traversal semantics
    // (vanilla keeps those on the mixin path; this class keeps its own on
    // the Aurora path).

    /** The flat-secondary fill ramp (rest → settled hover). */
    static int secondaryFlatFill(float hoverT) {
        return AuroraAnim.lerpArgb(
                ThemeManager.color(ThemeToken.SURFACE),
                ThemeManager.color(ThemeToken.SURFACE_VARIANT), hoverT);
    }

    /** The flat-secondary outline ramp (rest → settled hover). */
    static int secondaryFlatBorder(float hoverT) {
        return AuroraAnim.lerpArgb(
                ThemeManager.color(ThemeToken.BORDER),
                ThemeManager.color(ThemeToken.BORDER_HOVER), hoverT);
    }

    /**
     * The shared press-scale timeline — 90 ms toward 0.96 (ease-out), then
     * ~180 ms spring recovery — as a pure function of the press start
     * stamp. One implementation for Aurora's own buttons and the
     * vanilla-gated mixin buttons.
     */
    public static float pressScaleAt(long pressDownStartMs) {
        if (pressDownStartMs > 0L) {
            long elapsed = System.currentTimeMillis() - pressDownStartMs;
            if (elapsed < PRESS_DOWN_MS) {
                float raw = Math.min(1f, Math.max(0f, elapsed / (float) PRESS_DOWN_MS));
                return AuroraAnim.lerp(1.0f, 0.96f, AuroraAnim.easeOutCubic(raw));
            } else if (elapsed < PRESS_DOWN_MS + PRESS_UP_MS) {
                float raw = Math.min(1f, Math.max(0f, (elapsed - PRESS_DOWN_MS) / (float) PRESS_UP_MS));
                return AuroraAnim.lerp(0.96f, 1.0f, AuroraAnim.springOvershoot(raw));
            }
        }
        return 1.0f;
    }

    /**
     * Pure visual painter for one flat-secondary button: press-scale pose
     * wrap, token radius, disabled/rest/hover colors, 1 px outline, the
     * Button-family focus hairline (an independent channel — never a hover
     * substitute), and the centered non-shadowed label. Consumers own all
     * interaction state; this method activates nothing.
     */
    public static void paintFlatSecondary(GuiGraphics g, Font tr, Component label,
                                           int x, int y, int w, int h,
                                           float hoverT, float scale,
                                           boolean focused, boolean active) {
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        g.pose().pushMatrix();
        if (scale != 1.0f) {
            g.pose().translate(cx, cy);
            g.pose().scale(scale, scale);
            g.pose().translate(-cx, -cy);
        }

        float radius = ThemeManager.current().roundness().radiusSmall();
        int bg = active ? secondaryFlatFill(hoverT)
                : ThemeManager.color(ThemeToken.SURFACE_INSET);
        int border = active ? secondaryFlatBorder(hoverT)
                : ThemeManager.color(ThemeToken.BORDER);
        int text = active ? ThemeManager.color(ThemeToken.ON_BACKGROUND)
                : ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED);

        RenderUtil.drawRoundedRectAA(g, x, y, w, h, radius, bg);
        RenderUtil.drawRoundedOutlineAA(g, x, y, w, h, radius, 1.0f, border);

        if (focused) {
            RenderUtil.drawRoundedOutlineAA(g, x, y, w, h, radius, 1.0f,
                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ACCENT), 0x99));
        }

        AuroraFontRenderer.drawCentered(g, tr, label, Math.round(x + w / 2f),
                Math.round(y + (h - tr.lineHeight) / 2f) + 1, text);

        g.pose().popMatrix();
    }

    private float currentScale() {
        return pressScaleAt(pressDownStartMs);
    }
}
