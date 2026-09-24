package com.aurora.client.ui.component;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.interaction.MinecraftSemanticFeedback;
import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.interaction.SemanticControlGroup;
import com.aurora.client.ui.interaction.SemanticSound;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.HoverAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Segmented control — an iOS-style split track where the selected segment is
 * filled with the accent. Used for the Theme screen's Mode and Corner Style
 * rows, and the main screen's tab strip.
 *
 * <p>The track renders in the cacheable shape layer; the selected/hover
 * segment fills and labels render live. The selected fill is {@code ACCENT};
 * text uses {@code ON_ACCENT} (selected) and {@code ON_BACKGROUND_SECONDARY}
 * / {@code ON_BACKGROUND} (hover). Track corner radius follows the roundness
 * token. Labels are centered and non-shadowed.
 *
 * <p>Phase C-5 (2026-09-20): the canonical peer-selection contract — the
 * AccentSetting family. Hover is pointer-only {@code HoverAnim.symmetric(140)}
 * per segment and is NEVER gated on selection (the baseline suppressed hover
 * on the selected segment — the exact conflation Phase B banned); the
 * transient channel is a translucent ON_BACKGROUND wash that composes over
 * stained/neutral glass AND flat fills alike (the module-card idiom), so
 * {@code selected+hover} is representable everywhere. Focus is the
 * Button-family 1 px accent hairline, independent of both. One
 * {@link SemanticActionControl} per segment materializes on the first host
 * ask ({@link #interactionControls(String)}), attached to a component-owned
 * horizontal {@link SemanticControlGroup} — the C-3 roving ring travels with
 * the controls to every host. Arrows move focus silently; Enter/Space/click
 * select through the action, which plays exactly one ACTIVATION on a genuine
 * change and treats the already-selected peer as a silent consumed no-op.
 */
public class SegmentedControl extends Widget {

    private static final float SEG_GAP = 2f;
    /** §8.3 canonical hover duration — every segment (C-5). */
    private static final long HOVER_MS = 140L;

    private final String[] options;
    private final IntSupplier selectedIndex;
    private final IntConsumer onSelect;

    private boolean disabled = false;
    private float trackH = 20f;

    /** Canonical hover animators — pointer-only, one per segment, long-lived (C-5). */
    private final HoverAnim[] segmentHovers;

    /** C-5: one semantic control per segment — materialized only when a host asks. */
    private SemanticActionControl[] segmentControls;
    /** Cached list view of {@link #segmentControls} (the host sweeps it per frame). */
    private java.util.List<SemanticActionControl> segmentControlList;
    /**
     * C-5 roving ring: horizontal, attach order = visual order. Declared here
     * so it travels with {@link #interactionControls(String)} to whichever
     * host materializes them — hosts need only the existing C-3 rove seam.
     */
    private final SemanticControlGroup segmentGroup = SemanticControlGroup.horizontal();

    /**
     * Glass pilot (Theme screen only): each segment becomes its own RAISED
     * glass panel — neutral tint (WINDOW_FILL) for unselected, accent-stained
     * tint ({@link ThemeManager#stainedTint()}) for selected. The tint is the
     * primary selection cue and a thin semantic edge reinforces it; both states share the raised orientation (the
     * hierarchy rule: controls float above the recessed window). The flat
     * track base is suppressed from the cached shape layer while this is on;
     * whenever the glass renderer declines (screenshot suppression, failure)
     * the overlay redraws the full flat track + fills as the fallback.
     */
    private boolean glassEnabled = false;

    public SegmentedControl(String[] options, IntSupplier selectedIndex, IntConsumer onSelect) {
        this.options = options;
        this.selectedIndex = selectedIndex;
        this.onSelect = onSelect;
        this.segmentHovers = new HoverAnim[options.length];
        for (int i = 0; i < options.length; i++) {
            this.segmentHovers[i] = HoverAnim.symmetric(HOVER_MS);
        }
    }

    public SegmentedControl trackHeight(float h) {
        this.trackH = h;
        return this;
    }

    /**
     * Glass-pass bookkeeping (the {@code Button} scheme, §6 convention 6):
     * the frame in which {@link #renderGlassPass} painted the segments
     * pre-dim, and whether every one of them drew. In that frame
     * {@link #renderOverlay} paints labels only (or the complete flat track
     * if any segment declined); otherwise it paints the surfaces in place —
     * the legacy order, pixel-identical.
     */
    private long glassPassFrame = -1L;
    private boolean passDrewSegments = false;

    public SegmentedControl disabled(boolean d) {
        this.disabled = d;
        return this;
    }

    /** Glass pilot — see {@link #glassEnabled}. */
    public SegmentedControl glassEnabled(boolean g) {
        this.glassEnabled = g;
        return this;
    }

    @Override
    public void renderShapes(GuiGraphics g, float x, float y, float w, float h) {
        if (glassEnabled) return; // segments are live glass; track base suppressed
        float radius = Math.min(trackH / 2f, ThemeManager.current().roundness().radiusSmall());
        RenderUtil.drawRoundedRectAA(g, x, y, w, trackH, radius,
                ThemeManager.color(ThemeToken.SURFACE_VARIANT));
    }

    /**
     * Pre-dim surface (the split's surface half): every segment's raised
     * glass, tint carrying the selection state. The selection index is read
     * here so the tint can never disagree with the label pass.
     */
    @Override
    public void renderGlassPass(GuiGraphics g, float x, float y, float w, float h) {
        glassPassFrame = GlassSurface.frame();
        passDrewSegments = false;
        if (!glassEnabled) return;
        float radius = Math.min(trackH / 2f, ThemeManager.current().roundness().radiusSmall());
        int selected = Math.max(0, Math.min(options.length - 1, selectedIndex.getAsInt()));
        passDrewSegments = true;
        for (int i = 0; i < options.length; i++) {
            int sx = segStart(i, x, w);
            int sw = segWidth(i, x, w);
            boolean drew = i == selected
                    ? GlassSurface.adaptiveOnAccentControl(g, sx, y, sw, trackH, radius)
                    : GlassSurface.control(g, sx, y, sw, trackH, radius);
            if (!drew) {
                passDrewSegments = false;
                break;
            }
        }
    }

    @Override
    public void renderOverlay(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;

        float radius = Math.min(trackH / 2f, ThemeManager.current().roundness().radiusSmall());
        int selected = Math.max(0, Math.min(options.length - 1, selectedIndex.getAsInt()));

        // Glass pilot path: every segment is a live raised-glass panel with
        // the tint carrying the selection state. Hover feedback stays in the
        // LABEL color only — a flat hover fill would occlude the glass. On
        // decline, fall back to the complete flat look (track base + fills).
        // If the screen ran this control's glass pass this frame the
        // surfaces are already on screen UNDER the dim and only the combined
        // result matters here; otherwise (legacy frame order) they are
        // painted in place now through the shared GlassSurface helper
        // (identical calls).
        boolean glassOk = false;
        if (glassEnabled) {
            if (glassPassFrame == GlassSurface.frame()) {
                glassOk = passDrewSegments;
            } else {
                glassOk = true;
                for (int i = 0; i < options.length; i++) {
                    int sx = segStart(i, x, w);
                    int sw = segWidth(i, x, w);
                    boolean isSelected = i == selected;
                    boolean drew = isSelected
                            ? GlassSurface.adaptiveOnAccentControl(g, sx, y, sw, trackH, radius)
                            : GlassSurface.control(g, sx, y, sw, trackH, radius);
                    if (!drew) {
                        glassOk = false;
                        break;
                    }
                }
            }
            if (!glassOk) {
                // Flat fallback: track base first (it was suppressed from the
                // cached layer), then the flat segment fills.
                RenderUtil.drawRoundedRectAA(g, x, y, w, trackH, radius,
                        ThemeManager.color(ThemeToken.SURFACE_VARIANT));
            }
        }

        for (int i = 0; i < options.length; i++) {
            int sx = segStart(i, x, w);
            int sw = segWidth(i, x, w);
            boolean isSelected = i == selected;

            // Canonical hover (C-5): POINTER-only, symmetric 140 ms, never
            // gated on selection — the selected segment stays hoverable (the
            // baseline suppressed it, the Phase-C conflation). The transient
            // channel is a translucent ON_BACKGROUND wash that composes over
            // stained/neutral glass and flat fills alike — never a second
            // selection read.
            float hoverT = segmentHovers[i].update(
                    !disabled && inBounds(mouseX, mouseY, sx, y, sw, trackH));

            // Semantic sync (the EnumSetting discipline): bounds + pointer
            // mirror the painted segment each frame; the hairline reads the
            // control's vanilla focus. Only when a host materialized the
            // controls — null otherwise keeps the silent legacy path.
            SemanticActionControl control = segmentControls == null ? null : segmentControls[i];
            if (control != null) {
                control.setBounds(sx, (int) y, sw, (int) trackH);
                control.updatePointer(mouseX, mouseY);
            }

            if (!glassOk && isSelected) {
                RenderUtil.drawRoundedRectAA(g, sx, y, sw, trackH, radius,
                        ThemeManager.adaptiveOnAccent().selectedSegment());
            }
            boolean focused = control != null && control.isFocused();
            if (isSelected && !focused) {
                RenderUtil.drawRoundedOutlineAA(g, sx, y, sw, trackH, radius,
                        RenderUtil.devicePixelStroke(),
                        ThemeManager.adaptiveOnAccent().selectionIndicator());
            }

            if (!disabled && hoverT > 0f) {
                RenderUtil.drawRoundedRectAA(g, sx, y, sw, trackH, radius,
                        alpha(ThemeToken.ON_BACKGROUND, (0x1A / 255f) * hoverT));
            }

            // Keyboard focus: the Button-family 1 px accent hairline — an
            // independent channel from both the selection fill and hover wash.
            if (focused) {
                RenderUtil.drawRoundedOutlineAA(g, sx, y, sw, trackH, radius, 1.0f,
                        isSelected ? ThemeManager.semanticContrast().focusOnAccent()
                                : ThemeManager.semanticContrast().focusNeutral());
            }

            int color = disabled ? ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED)
                    : isSelected ? ThemeManager.adaptiveOnAccent().foreground()
                    : AuroraAnim.lerpArgb(
                            ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY),
                            ThemeManager.color(ThemeToken.ON_BACKGROUND), hoverT);

            AuroraFontRenderer.drawCentered(g, tr, options[i], Math.round(sx + sw / 2f),
                    Math.round(y + (trackH - tr.lineHeight) / 2f), color);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (disabled || button != 0 || !inBounds(mx, my, x, y, w, trackH)) return false;
        for (int i = 0; i < options.length; i++) {
            int sx = segStart(i, x, w);
            int sw = segWidth(i, x, w);
            if (mx >= sx && mx < sx + sw) {
                // Pointer converges on the same semantic value-change path as
                // the keyboard when a host materialized the controls
                // (exactly-one activation click; the no-op lives in the
                // action with the sound). Hosts that never asked keep the
                // direct silent path — the EnumSetting precedent.
                if (segmentControls != null && segmentControls[i].isAvailable()) {
                    segmentControls[i].activateFromPointer(mx, my, button);
                } else if (i != selectedIndex.getAsInt()) {
                    onSelect.accept(i);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * The complete peer set, materialized on first ask (the AccentSetting
     * discipline): one {@link SemanticActionControl} per segment in visual
     * order, all attached to the component's horizontal roving ring. Hosts
     * that ask (FeatureDetailScreen, AuroraScreen's Settings tab) get the
     * full peer-selection contract; hosts that never ask keep the silent
     * direct pointer path. {@code settingLabel} names the owning row for
     * narration/description and is captured once at materialization.
     */
    public java.util.List<SemanticActionControl> interactionControls(String settingLabel) {
        if (segmentControlList == null) {
            for (int i = 0; i < options.length; i++) segmentControl(i, settingLabel);
            segmentControlList = Arrays.asList(segmentControls);
        }
        return segmentControlList;
    }

    private SemanticActionControl segmentControl(int i, String settingLabel) {
        if (segmentControls == null) segmentControls = new SemanticActionControl[options.length];
        SemanticActionControl control = segmentControls[i];
        if (control != null) return control;
        final int index = i;
        control = new SemanticActionControl(new SemanticAction(
                Component.literal(options[i]),
                () -> Component.literal("Sets " + settingLabel + " to " + options[index] + "."),
                () -> Component.literal(isSelected(index) ? "Selected" : "Not selected"),
                () -> !disabled,
                () -> {
                    if (index == clampedSelected()) return; // silent consumed no-op
                    MinecraftSemanticFeedback.INSTANCE.play(SemanticSound.ACTIVATION);
                    onSelect.accept(index);
                },
                SemanticSound.NONE, true, true),
                MinecraftSemanticFeedback.INSTANCE,
                null, // no press animation — the selection transfer is the feedback
                SemanticActionControl.PointerRouting.MANUAL);
        segmentControls[i] = control;
        segmentGroup.attach(control); // C-5 ring; idempotent across re-materialization
        return control;
    }

    private int clampedSelected() {
        return Math.max(0, Math.min(options.length - 1, selectedIndex.getAsInt()));
    }

    private boolean isSelected(int i) {
        return i == clampedSelected();
    }

    private static int alpha(ThemeToken t, float a) {
        return (Math.round(a * 255f) << 24) | (ThemeManager.color(t) & 0x00FFFFFF);
    }

    private int segStart(int i, float x, float w) {
        return Math.round(x + i * (segWidth() + SEG_GAP));
    }

    private float segWidth() {
        return (w - SEG_GAP * (options.length - 1)) / options.length;
    }

    /** Width of segment {@code i} — the last absorbs integer-division slack. */
    private int segWidth(int i, float x, float w) {
        return i == options.length - 1
                ? Math.round(x + w) - segStart(i, x, w)
                : Math.round(segWidth());
    }
}
