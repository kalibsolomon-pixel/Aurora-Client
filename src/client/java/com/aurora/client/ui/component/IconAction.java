package com.aurora.client.ui.component;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.interaction.MinecraftSemanticFeedback;
import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.util.MaterialIconRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.HoverAnim;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * One compact button-like ICON action (Phase C-4): a Material Symbols glyph
 * carrying the canonical interaction contract — pointer-only
 * {@code HoverAnim.symmetric(140)} hover, the Button-family 1 px accent
 * focus hairline, Tab/Enter/Space through a {@link SemanticActionControl},
 * exactly one ACTIVATION per accepted activation, and the action's enabled
 * gate as the authoritative disabled state (no hover target, no activation,
 * glyph painted in the muted token).
 *
 * <p><b>The glyph is presentation only.</b> Accessible meaning lives on the
 * {@link SemanticAction}'s textual label ("Remove item", "Add effect") —
 * never on the icon or its Unicode name. The glyph draws through
 * {@link MaterialIconRenderer} (crisp native-resolution rasterization — the
 * shared atlas is point-sampled), centered in the caller's box with an
 * explicit em rule: {@code min(NATURAL_EM_GUI, min(w, h) − 2)} — per-instance
 * geometry, never one fixed size, but always finite.
 *
 * <p><b>Ownership:</b> the primitive owns interaction semantics and channel
 * state; the host owns layout and the domain action (which arrives through
 * the {@code SemanticAction}). It knows nothing about specific settings or
 * screens. The SURFACE — pill, panel, badge — stays the host's: {@link #paint}
 * renders the glyph + hairline channels only, and {@link #syncChannels}
 * supplies the channels alone for hosts whose painter supplies its own
 * pixels (HudEditor's raster badge, the status-overlay family). Chrome
 * radius resolves through the theme ({@code min(min(w,h)/2, radiusSmall())},
 * the segmented-segment rule) so SQUARE squares rectangular chrome;
 * intrinsically circular glyph content is never squared.
 *
 * <p><b>Lifecycle:</b> one long-lived instance per action site, created by
 * the owning component (identity-keyed for dynamic rows —
 * {@code computeIfAbsent}, never per frame). The control materializes
 * lazily on the first {@link #interactionControl()} ask and is never
 * rebuilt; hosts that never ask keep their silent direct pointer path (the
 * EnumSetting discipline — callers keep a legacy fallback beside
 * {@link #clicked}). A DISCLOSURE action (expand/collapse) is the one
 * stateful form: the glyph supplier flips with the Expanded/Collapsed
 * state (§6 semantic disclosure contract — the state lives in the action's
 * narration metadata), while the instance — and with it the control and
 * keyboard focus — survives the toggle.
 */
public final class IconAction {

    /** §8.3 canonical hover duration — every icon action (C-4). */
    public static final long HOVER_MS = 140L;

    private final SemanticAction action;
    /**
     * Material Symbols codepoint — presentation only, never semantics. A
     * supplier (not a fixed value) so a DISCLOSURE action can flip its glyph
     * with its Expanded/Collapsed state while staying ONE long-lived
     * instance: the control (and with it keyboard focus) must survive the
     * toggle, so the state cannot be modeled as two actions. The fixed-glyph
     * constructor wraps its constant — no per-frame allocation either way.
     */
    private final java.util.function.Supplier<String> glyph;
    private final java.util.function.IntSupplier restColor;
    private final java.util.function.IntSupplier hoverColor;
    private final HoverAnim hover = HoverAnim.symmetric(HOVER_MS);
    private SemanticActionControl control;

    /** Colors are suppliers so theme-derived values stay live across reloads. */
    public IconAction(String glyph, SemanticAction action,
                      java.util.function.IntSupplier restColor,
                      java.util.function.IntSupplier hoverColor) {
        this(() -> glyph, action, restColor, hoverColor);
    }

    /** Stateful-glyph form — the disclosure contract (Expanded/Collapsed). */
    public IconAction(java.util.function.Supplier<String> glyph, SemanticAction action,
                      java.util.function.IntSupplier restColor,
                      java.util.function.IntSupplier hoverColor) {
        this.glyph = glyph;
        this.action = action;
        this.restColor = restColor;
        this.hoverColor = hoverColor;
    }

    /** The glyph for the action's CURRENT state — presentation only. */
    public String currentGlyph() {
        return glyph.get();
    }

    /** The lazy semantic control — hosts register it once (the EnumSetting discipline). */
    public SemanticActionControl interactionControl() {
        if (control == null) {
            control = new SemanticActionControl(action,
                    MinecraftSemanticFeedback.INSTANCE,
                    null, // no press animation — the action's effect is the feedback
                    SemanticActionControl.PointerRouting.MANUAL);
        }
        return control;
    }

    public boolean materialized() {
        return control != null;
    }

    /** Current hover progress — for hosts painting their own glyph/badge pixels. */
    public float hoverT() {
        return hover.current();
    }

    public boolean isFocused() {
        return control != null && control.isFocused();
    }

    /**
     * Full canonical paint at the box: hover animation, control bounds/pointer
     * sync, the glyph (animated rest→hover color; muted while disabled), and
     * the focus hairline. The surface underneath stays the host's.
     */
    public void paint(GuiGraphics g, Font font, int x, int y, int w, int h,
                      int mouseX, int mouseY) {
        syncChannels(x, y, w, h, mouseX, mouseY);
        paintGlyph(g, font, x, y, w, h);
    }

    /**
     * Glyph + hairline WITHOUT the channel sync — for callers that already
     * called {@link #syncChannels} and read {@link #hoverT()} to interleave
     * their own surface between the sync and the glyph (syncing twice would
     * double-step the animator).
     */
    public void paintGlyph(GuiGraphics g, Font font, int x, int y, int w, int h) {
        int color = !action.enabled()
                ? ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED)
                : AuroraAnim.lerpArgb(restColor.getAsInt(), hoverColor.getAsInt(), hover.current());
        MaterialIconRenderer.drawIcon(g, font, glyph.get(), x + w / 2f, y + h / 2f,
                emFor(w, h), color);
        paintHairline(g, x, y, w, h);
    }

    /**
     * Channel sync WITHOUT the glyph — the hover animation and the control's
     * bounds/pointer/state mirror. For hosts whose painter supplies its own
     * pixels; read {@link #hoverT()} and call {@link #paintHairline}.
     */
    public void syncChannels(int x, int y, int w, int h, double mouseX, double mouseY) {
        boolean target = action.enabled() && Widget.inBounds(mouseX, mouseY, x, y, w, h);
        hover.update(target);
        if (control != null) {
            control.setBounds(x, y, w, h);
            control.updatePointer(mouseX, mouseY);
        }
    }

    /** The Button-family 1 px accent hairline (0x99) — an independent focus channel. */
    public void paintHairline(GuiGraphics g, int x, int y, int w, int h) {
        if (control == null || !control.isFocused()) return;
        float radius = Math.min(Math.min(w, h) / 2f,
                ThemeManager.current().roundness().radiusSmall());
        RenderUtil.drawRoundedOutlineAA(g, x, y, w, h, radius, 1.0f,
                ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ACCENT), 0x99));
    }

    /**
     * Pointer route through the semantic action (exactly-once activation +
     * the one click). False when unhosted, unavailable, out of bounds, or
     * disabled — callers keep their legacy direct path for that case.
     */
    public boolean clicked(double mouseX, double mouseY, int button) {
        return control != null && control.activateFromPointer(mouseX, mouseY, button);
    }

    /** Explicit, finite glyph em for a box — never one fixed size for all actions. */
    private static float emFor(int w, int h) {
        return Math.min(MaterialIconRenderer.NATURAL_EM_GUI, Math.min(w, h) - 2);
    }
}
