package com.aurora.client.screen.setting;
import com.aurora.client.ui.component.Widget;
import com.aurora.client.ui.util.AuroraFontRenderer;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.theme.PaletteEngine;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemePresets;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.interaction.MinecraftSemanticFeedback;
import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.interaction.SemanticSound;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.AuroraTheme;
import com.aurora.client.util.HoverAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * COSMIC-style accent picker (Stage 3): a grid of the nine
 * {@link ThemePresets} swatches plus a distinct "Custom" slot — the Phase B
 * peer-selection family (2026-09-18). Peers represent VALUES, so selection
 * is never stored: the literal accent IS the source of truth
 * ({@link ThemePresets#matching}; Custom is selected exactly when no preset
 * matches). The five channels never alias:
 * <ul>
 *   <li><b>selected</b> — the persistent 1.5 px label-colored ring at 2 px
 *       out, drawn unconditionally for the matching peer (and Custom while
 *       the accent is custom) — visible at hover 0, never driven by hover
 *       progress. The pre-pilot code drew hover through an {@code else if}
 *       on !selected, which erased hover feedback on the selected peer and
 *       gave active Custom none at all; both compose now.</li>
 *   <li><b>hover</b> — per-peer {@link HoverAnim#symmetric}(140)
 *       (pointer-only target, rejected while disabled) animating the
 *       1 px WINDOW_OUTLINE ring's alpha. The baseline rings were
 *       immediate; endpoints unchanged, only the path animates.</li>
 *   <li><b>focused</b> — one {@link SemanticActionControl} per peer,
 *       materialized only when a host asks ({@link #interactionControls()}
 *       — FeatureDetailScreen and, since Phase C-2, AuroraScreen's inline
 *       Settings tab both do). Focus paints the
 *       Button-family 1 px accent hairline ON the cell — weight/position
 *       distinct from the selection ring's 2 px-out stroke.</li>
 *   <li><b>disabled</b> — the authoritative gate: no hover target, no
 *       ring, no pointer/keyboard activation, no save, no sound (the
 *       baseline painted the hover ring on disabled cells — fixed). The
 *       selected ring stays readable and every color interior stays
 *       literal: the baseline alpha-halved the active Custom preview's
 *       fill while disabled, modifying represented DATA
 *       to say "disabled" — removed per the ColorSwatch principle
 *       (interaction state belongs to chrome, never the sample).</li>
 *   <li><b>custom-active</b> — derived ({@code matching() == null}), not
 *       stored; a picker edit landing exactly on a preset value selects
 *       that preset by construction.</li>
 * </ul>
 *
 * <p>Sound ownership: the peer actions carry {@link SemanticSound#NONE} and
 * play exactly one {@link SemanticSound#ACTIVATION} through
 * {@link MinecraftSemanticFeedback} inside the behavior, only when the
 * activation had an effect — a preset that changes the accent (or closes
 * the open editor) clicks; the already-selected no-op (editor closed) is
 * silent. Opening/resetting Custom always clicks (it always acts). On the
 * AuroraScreen inline host the direct pointer path stays silent (the enum
 * precedent — sound arrives with the host that owns semantic routing).
 * NO press animation by decision: the immediate selection-ring transfer is
 * the feedback. Persistence is unchanged in production: the wired setter
 * already persists ({@code persistThemeChange}); the pilot removes the
 * redundant SECOND direct config save the grid-click path used to
 * issue right after it — one intended persistence action per change.
 *
 * <p>Opening the Custom slot embeds the mod's existing {@link ColorSetting}
 * picker — <b>reused literally, unmodified in style/layout</b> — exactly as
 * the shield damage-tint feature edits its colors; only the wiring differs
 * (accent setter + throttled reload).
 *
 * <p>Every applied value goes through the single standard path: write the
 * definition field, one {@code ThemeManager.reload()}, then persistence at
 * the setter's commit points. Picker drags apply live but throttled to
 * {@link #LIVE_RELOAD_MIN_MS} (the final value commits on release).
 */
public class AccentSetting extends FeatureSetting {

    private static final int LABEL_ROW_H = 28;
    private static final int SWATCH_H = 24;
    private static final int SWATCH_GAP = 6;
    private static final int GRID_PAD_X = 12;
    private static final int GRID_GAP_Y = 6;
    /** 9 presets + the Custom slot = 10 cells, 5 per row. */
    private static final int PER_ROW = 5;
    private static final int EDITOR_TOP_GAP = 8;

    /** §8.3 canonical hover duration — every peer cell alike (Phase B). */
    private static final long HOVER_MS = 140L;

    /** Minimum spacing between live (mid-drag) reloads — same budget as the opacity slider. */
    private static final long LIVE_RELOAD_MIN_MS = 100L;

    private final IntSupplier getter;
    private final IntConsumer setter;

    private boolean editorOpen;

    /**
     * The embedded damage-tint-style picker. A plain, unmodified
     * {@link ColorSetting} instance — recreated fresh each time the Custom
     * slot is opened so it always starts collapsed exactly as it does in
     * its original feature rows.
     */
    private ColorSetting customPicker;

    /** Deferred live-apply value while the throttle window rides out. */
    private int pendingRgb;
    private final LiveReloadThrottle throttle = new LiveReloadThrottle(LIVE_RELOAD_MIN_MS);

    /**
     * Per-peer canonical hover animators — one per cell (9 presets + the
     * Custom slot), each owning its own transient state so no two peers can
     * appear hovered together. Keyed by grid index, long-lived for the
     * setting's lifetime (settings are long-lived singletons).
     */
    private final HoverAnim[] cellHover = new HoverAnim[ThemePresets.ALL.size() + 1];

    /**
     * Per-peer semantic controls (focus/keyboard/narration), lazily
     * materialized as a complete set the first time a host asks
     * {@link #interactionControls()} — the EnumSetting host-dependency
     * discipline. Null until then; bounds + pointer sync from the render
     * walk each frame.
     */
    private final SemanticActionControl[] peerControls = new SemanticActionControl[ThemePresets.ALL.size() + 1];
    /** Cached list view of {@link #peerControls} (the sweep walks it per frame). */
    private java.util.List<SemanticActionControl> peerControlList;
    /**
     * C-3 roving ring over the peers, declared HERE so it travels with
     * {@link #interactionControls()} to whichever host materializes them —
     * the Theme detail screen and AuroraScreen's Settings tab get the
     * identical grid semantics with zero host-side wiring. Grid of
     * {@code PER_ROW}: Left/Right step one peer, Up/Down one row; arrows
     * move focus silently, never select.
     */
    private final com.aurora.client.ui.interaction.SemanticControlGroup peerGroup =
            com.aurora.client.ui.interaction.SemanticControlGroup.grid(PER_ROW);

    private int lastX, lastY, lastW;

    public AccentSetting(String label, IntSupplier getter, IntConsumer setter) {
        super(label);
        this.getter = getter;
        this.setter = setter;
        // Auto-open the editor when the saved accent is already custom, so
        // the picker is right there on revisit.
        this.editorOpen = ThemePresets.matching(getter.getAsInt()) == null;
        this.customPicker = createCustomPicker();
    }

    /**
     * A fresh, unmodified {@link ColorSetting} wired to the accent through
     * the standard throttled apply path (its own commit/save behavior is
     * left exactly as-is).
     */
    private ColorSetting createCustomPicker() {
        return new ColorSetting("Custom Accent",
                this::currentAccent,
                this::applyAccentLive);
    }

    @Override public AccentSetting description(String desc) { super.description(desc); return this; }
    @Override public AccentSetting description(java.util.function.Supplier<String> desc) { super.description(desc); return this; }

    // ------------------------------------------------------------------
    //  Layout
    // ------------------------------------------------------------------

    /** Row top of the swatch grid, relative to the row top. */
    private static int gridTopRel() { return LABEL_ROW_H; }

    /** Row top of the custom editor (the embedded picker), relative to the row top. */
    private static int editorTopRel() { return LABEL_ROW_H + 2 * SWATCH_H + GRID_GAP_Y + EDITOR_TOP_GAP; }

    @Override public int baseHeight() {
        int h = LABEL_ROW_H + 2 * SWATCH_H + GRID_GAP_Y;
        if (editorOpen) {
            h += EDITOR_TOP_GAP + customPicker.height() + 2;
        }
        return h;
    }

    @Override public int height() { return baseHeight(); }

    private int swatchW(int rowWidth) {
        return (rowWidth - GRID_PAD_X * 2 - SWATCH_GAP * (PER_ROW - 1)) / PER_ROW;
    }

    private int cellX(int index, int rowWidth) {
        return lastX + GRID_PAD_X + (index % PER_ROW) * (swatchW(rowWidth) + SWATCH_GAP);
    }

    private int cellY(int index) {
        return lastY + gridTopRel() + (index / PER_ROW) * (SWATCH_H + GRID_GAP_Y);
    }

    // ------------------------------------------------------------------
    //  Apply paths — all funnel through the single setter (one reload per
    //  genuine change); save happens only at commit points. The throttle
    //  logic lives here so the shared picker stays persistence-agnostic.
    // ------------------------------------------------------------------

    private int currentAccent() {
        return getter.getAsInt();
    }

    /** Apply a full RGB now if the throttle allows; otherwise defer to the render flush. */
    private void applyAccentLive(int rgb) {
        int v = rgb & 0x00FFFFFF;
        if (v == (currentAccent() & 0x00FFFFFF)) {
            return; // no genuine change — must not fire a reload
        }
        pendingRgb = v;
        throttle.apply(() -> setter.accept(0xFF000000 | pendingRgb));
    }

    /** Flush a deferred live apply if it still differs from the live config. */
    private void flushPendingLive() {
        throttle.flush(() -> {
            int v = 0xFF000000 | (pendingRgb & 0x00FFFFFF);
            if (v != (currentAccent() | 0xFF000000)) {
                setter.accept(v);
            }
        });
    }

    // ------------------------------------------------------------------
    //  Peer semantics — selection derives from the literal accent; hover is
    //  pointer-only; one semantic action per peer; disabled is the gate.
    // ------------------------------------------------------------------

    /** The cell count: 9 presets + the Custom slot. */
    private static int peerCount() {
        return ThemePresets.ALL.size() + 1;
    }

    /**
     * Whether peer {@code i} is the persistent selected value — derived LIVE
     * from the accent (presets by literal RGB equality, Custom when nothing
     * matches). There is deliberately no selected-index field: nothing can
     * disagree with the stored accent.
     */
    private boolean isPeerSelected(int i) {
        if (i >= ThemePresets.ALL.size()) return ThemePresets.matching(currentAccent()) == null;
        return ThemePresets.matching(currentAccent()) == ThemePresets.ALL.get(i);
    }

    /** The peer's canonical hover animator (lazily created, long-lived). */
    private HoverAnim hoverOf(int i) {
        HoverAnim a = cellHover[i];
        if (a == null) {
            a = HoverAnim.symmetric(HOVER_MS);
            cellHover[i] = a;
        }
        return a;
    }

    /**
     * The peer hover-anim target: the POINTER only, rejected while disabled.
     * Never consults selection or focus — selected peers keep animating
     * hover in and out, and a disabled peer's hover eases back to rest.
     * Kept as a method because the invariant is testable headless.
     */
    boolean peerHoverTarget(boolean pointerOverCell, boolean disabled) {
        return pointerOverCell && !disabled;
    }

    /**
     * Activates preset peer {@code e} — the one value path for pointer and
     * keyboard. Returns whether the activation HAD AN EFFECT (the accent
     * changed, or the open editor closed); the already-selected no-op with
     * the editor closed changes nothing and persists nothing. Persistence
     * rides the wired setter (the production setter's persistThemeChange);
     * the redundant second config save this method used to issue was
     * removed — one intended persistence action per change.
     */
    private boolean applyPresetSelection(ThemePresets.Entry e) {
        boolean changed = (e.argb | 0xFF000000) != (currentAccent() | 0xFF000000);
        if (changed) {
            setter.accept(e.argb); // one reload + one persist per genuine change
        }
        boolean closedEditor = editorOpen;
        editorOpen = false;
        return changed || closedEditor;
    }

    /**
     * Activates the Custom peer: opens (or resets — a fresh collapsed
     * picker, exactly the baseline behavior) the embedded editor. Never a
     * value change by itself ("opening the editor is not a change").
     */
    private boolean openCustomEditor() {
        editorOpen = true;
        customPicker = createCustomPicker();
        return true;
    }

    /**
     * The peer's semantic control (focus traversal, Enter/Space activation,
     * narration with Selected/Not-selected state, the conditional selection
     * click). The enabled gate is the authoritative row disabled state — a
     * disabled peer drops out of traversal and narrates unavailable, which
     * is the genuine contract here (unlike a selected tab, "selected" is
     * not an exclusion worth traversing around).
     */
    private SemanticActionControl peerControl(int i) {
        SemanticActionControl control = peerControls[i];
        if (control != null) return control;
        boolean custom = i >= ThemePresets.ALL.size();
        String name = custom ? "Custom" : ThemePresets.ALL.get(i).name;
        control = new SemanticActionControl(new SemanticAction(
                Component.literal(name),
                () -> Component.literal(custom
                        ? "Opens the accent color editor."
                        : "Sets the theme accent to " + name + "."),
                () -> Component.literal(isPeerSelected(i) ? "Selected" : "Not selected"),
                () -> !isDisabled(),
                () -> {
                    boolean effect = custom
                            ? openCustomEditor()
                            : applyPresetSelection(ThemePresets.ALL.get(i));
                    if (effect) MinecraftSemanticFeedback.INSTANCE.play(SemanticSound.ACTIVATION);
                },
                SemanticSound.NONE, true, true),
                MinecraftSemanticFeedback.INSTANCE,
                null, // no press animation — the ring transfer is the feedback
                SemanticActionControl.PointerRouting.MANUAL);
        peerControls[i] = control;
        peerGroup.attach(control); // C-3: attach order = row-major visual order
        return control;
    }

    /**
     * The complete peer set, materialized on first ask. Hosts that ask
     * (FeatureDetailScreen: registration, per-frame availability sweep,
     * post-activation refocus) get the full peer-selection contract; hosts
     * that never ask keep the component-level states with the silent direct
     * pointer path. AuroraScreen asks during init as of Phase C-2.
     */
    @Override
    public java.util.List<SemanticActionControl> interactionControls() {
        if (peerControlList == null) {
            for (int i = 0; i < peerCount(); i++) peerControl(i);
            peerControlList = java.util.Arrays.asList(peerControls);
        }
        return peerControlList;
    }

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        renderShapes(ctx, x, y, width, mouseX, mouseY);
        renderOverlay(ctx, x, y, width, mouseX, mouseY);
    }

    @Override
    public void renderShapes(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastX = x;
        lastY = y;
        lastW = width;
        // The nine preset swatch fills are constant → cacheable static layer.
        float swR = Math.min(SWATCH_H / 2.0f, AuroraTheme.RADIUS_SMALL);
        for (int i = 0; i < ThemePresets.ALL.size(); i++) {
            ThemePresets.Entry e = ThemePresets.ALL.get(i);
            RenderUtil.drawRoundedRectAA(ctx, cellX(i, width), cellY(i), swatchW(width), SWATCH_H, swR, e.argb);
        }
    }

    @Override
    public void renderOverlay(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastX = x;
        lastY = y;
        lastW = width;

        Font tr = Minecraft.getInstance().font;
        boolean disabled = isDisabled();

        // Flush a deferred live apply once the throttle interval elapsed.
        if (throttle.due()) {
            flushPendingLive();
        }

        renderLabelWithTooltip(ctx, label, x + GRID_PAD_X, y + (LABEL_ROW_H - tr.lineHeight) / 2,
                AuroraTheme.IOS_LABEL, mouseX, mouseY, disabled);

        int accent = currentAccent();
        ThemePresets.Entry preset = ThemePresets.matching(accent);

        float swR = Math.min(SWATCH_H / 2.0f, AuroraTheme.RADIUS_SMALL);

        // Peer rings — EXPLICIT COMPOSITION, not else-if: the persistent
        // selection ring (1.5 px, label color, 2 px out) draws whenever the
        // peer IS the accent value, independent of hover; the transient
        // hover ring (1 px, WINDOW_OUTLINE, 1 px out) draws for EVERY
        // enabled peer with alpha = the canonical 140 ms animator —
        // including the selected peer and active Custom (both previously
        // suppressed). Hover never paints on a disabled peer (the baseline
        // rendered the affordance while rejecting clicks). The preset
        // interiors in renderShapes and the Custom fill below stay literal
        // in every state — represented color is data.
        for (int i = 0; i < peerCount(); i++) {
            boolean isCustom = i >= ThemePresets.ALL.size();
            int sx = cellX(i, width);
            int sy = cellY(i);
            int sw = swatchW(width);
            boolean selected = isCustom ? preset == null : preset == ThemePresets.ALL.get(i);
            float hT = hoverOf(i).update(peerHoverTarget(hoverAt(mouseX, mouseY, sx, sy, sw), disabled));

            // Semantic sync (the EnumSetting discipline): bounds + pointer
            // mirror the painted cell each frame; the hairline reads the
            // control's vanilla focus. Only when a host materialized the
            // controls — null otherwise keeps the silent legacy path.
            SemanticActionControl control = peerControls[i];
            if (control != null) {
                control.setBounds(sx, sy, sw, SWATCH_H);
                control.updatePointer(mouseX, mouseY);
            }

            if (selected) {
                RenderUtil.drawRoundedOutlineAA(ctx, sx - 2, sy - 2, sw + 4, SWATCH_H + 4,
                        swR + 2, 1.5f, AuroraTheme.IOS_LABEL);
            }
            if (!disabled && hT > 0f) {
                RenderUtil.drawRoundedOutlineAA(ctx, sx - 1, sy - 1, sw + 2, SWATCH_H + 2,
                        swR + 1, 1.0f, AuroraAnim.scaleAlpha(AuroraTheme.WINDOW_OUTLINE, hT));
            }
            if (control != null && control.isFocused()) {
                RenderUtil.drawRoundedOutlineAA(ctx, sx, sy, sw, SWATCH_H, swR, 1.0f,
                        ThemeManager.semanticContrast().focusNeutral());
            }
        }

        // Custom slot — fill is the live accent value while active (data:
        // literal in every state, disabled included — the baseline's
        // alpha-halving dim is gone; disabled communicates through the
        // label + absent hover affordance), or the neutral chrome fill
        // while inactive.
        int ci = ThemePresets.ALL.size();
        int cx = cellX(ci, width);
        int cy = cellY(ci);
        int cw = swatchW(width);
        boolean customActive = preset == null;
        int customFill = customActive ? accent : AuroraTheme.IOS_TERTIARY_BG;
        RenderUtil.drawRoundedRectAA(ctx, cx, cy, cw, SWATCH_H, swR, customFill | 0xFF000000);
        if (!customActive) {
            RenderUtil.drawRoundedOutlineAA(ctx, cx, cy, cw, SWATCH_H, swR, 1.0f, AuroraTheme.BORDER_OFF);
        }
        int customText = customActive
                ? PaletteEngine.pickOnColor(accent, 0f)
                : AuroraTheme.IOS_SECONDARY_LABEL;
        AuroraFontRenderer.drawCentered(ctx, tr, "Custom", cx + cw / 2,
                cy + (SWATCH_H - tr.lineHeight) / 2, disabled ? AuroraTheme.TEXT_DIM : customText);

        if (!editorOpen) return;
        // The embedded, unmodified damage-tint-style picker — rendered as a
        // normal settings row, exactly as it appears in its own feature.
        customPicker.render(ctx, x, y + editorTopRel(), width, mouseX, mouseY);
    }

    private static boolean hoverAt(double mouseX, double mouseY, int x, int y, int w) {
        return Widget.inBounds(mouseX, mouseY, x, y, w, SWATCH_H);
    }

    // ------------------------------------------------------------------
    //  Interaction — embedded picker first (when open), then the swatch grid
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int rowX, int rowY, int rowWidth) {
        if (button != 0) return false;
        boolean disabled = isDisabled();

        // 1) The embedded, unmodified picker (label row / pad / strips).
        if (editorOpen && !disabled) {
            if (customPicker.mouseClicked(mouseX, mouseY, button,
                    lastX, lastY + editorTopRel(), lastW)) {
                return true;
            }
        }

        // 2) Swatch grid (presets + Custom slot). Every cell hit routes
        // through the peer's semantic action where one exists (the host
        // asked — exactly-once activation, the conditional selection click,
        // the disabled gate); hosts that never asked keep the direct value
        // path, silent (the enum precedent). The consumed return is the
        // same either way — a rejected/disabled hit inside the grid never
        // falls through to the row's focus-drop below.
        if (!disabled && mouseY >= lastY + gridTopRel() && mouseY < cellY(PER_ROW) + SWATCH_H) {
            int sw = swatchW(lastW);
            for (int i = 0; i < peerCount(); i++) {
                int sx = cellX(i, lastW);
                int sy = cellY(i);
                if (Widget.inBounds(mouseX, mouseY, sx, sy, sw, SWATCH_H)) {
                    SemanticActionControl control = peerControls[i];
                    if (control != null && control.isAvailable()) {
                        control.activateFromPointer(mouseX, mouseY, button);
                    } else if (i >= ThemePresets.ALL.size()) {
                        openCustomEditor();
                    } else {
                        applyPresetSelection(ThemePresets.ALL.get(i));
                    }
                    return true;
                }
            }
        }

        // Click inside our row but outside any control: drop focus.
        if (Widget.inBounds(mouseX, mouseY, lastX, lastY, lastW, height())) {
            releaseFocus();
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy,
                                int rowX, int rowY, int rowWidth) {
        if (!editorOpen) return false;
        return customPicker.mouseDragged(mouseX, mouseY, button, dx, dy, rowX, rowY, rowWidth);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!editorOpen) return false;
        boolean handled = customPicker.mouseReleased(mouseX, mouseY, button);
        if (handled) {
            // The picker persists on its own release; flush anything the
            // throttle deferred so the saved value is the final one.
            flushPendingLive();
            AuroraConfig.save();
        }
        return handled;
    }

    @Override
    public void onDetailScreenClose() {
        // Commit any dangling throttle deferral so nothing is lost on
        // navigation. Note FeatureDetailScreen saves the config BEFORE this
        // hook runs, so anything committed here must save on its own.
        flushPendingLive();
        AuroraConfig.save();
    }

    // Package-private test visibility (Phase B pilot).
    boolean peerSelectedState(int i) { return isPeerSelected(i); }
    HoverAnim peerHoverAnimator(int i) { return hoverOf(i); }
    boolean editorOpenState() { return editorOpen; }
}
