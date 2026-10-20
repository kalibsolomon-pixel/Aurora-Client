package com.aurora.client.screen;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.config.profile.ProfileManager;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.Button;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.GlassEditBox;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.interaction.MinecraftSemanticFeedback;
import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.interaction.SemanticSound;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lists configuration profiles and exposes create / rename / duplicate /
 * delete plus one-click switch to the active profile.
 *
 * <p>Backed by {@link ProfileManager}, on the shared
 * {@link ManagerListScreen} foundation (audit R3): the frame order, scroll +
 * scrollbar, toast, inline rename lifecycle and toolbar row come from the
 * base; this class owns the row content and actions — the accent Active
 * badge, the Duplicate/Delete buttons (the canonical {@link Button}; Delete
 * in its destructive variant) — plus this screen's extra inline editor: the
 * "New Profile" create row pinned above the list.
 *
 * <p>The active profile is visually marked and cannot be deleted (the
 * manager enforces this; the row simply hides its delete button). The
 * {@code default} profile is likewise non-deletable.
 */
public class ProfileManagerScreen extends ManagerListScreen<String> {

    // ---- Card grid (owner request 2026-10-19: tiles, like the launcher's instances) ----
    //
    // Each profile is one card: name on top, the Active pill (or a hover
    // hint) under it, Duplicate and Delete stacked at the bottom so every
    // card in a row keeps its actions on the same lines. Columns follow the
    // available width (at least one); the active card wears the accent
    // selection outline the enabled feature cards use.
    private static final int CARD_H = 92;
    private static final int CARD_MIN_W = 132;
    private static final int CARD_GAP = 8;
    private static final int LIST_MAX_W = 444;
    private static final int INSET = 8;
    private static final int BTN_H = 18;
    private static final int NAME_Y = 9;
    private static final int PILL_Y = 24;
    private static final int PILL_H = 14;
    /** Upper action line (Duplicate / Create) and lower line (Delete), from the card top. */
    private static final int UPPER_BTN_Y = CARD_H - INSET - BTN_H - 4 - BTN_H;
    private static final int LOWER_BTN_Y = CARD_H - INSET - BTN_H;

    /**
     * Pending create: when the user clicks "New", we show an empty editor
     * at the top of the list rather than mid-list.
     */
    private boolean creatingNew = false;
    private EditBox createField;

    // Shared themed row buttons, keyed by profile NAME — unique and stable,
    // so a rename simply produces a fresh entry instead of a stale capture.
    private final Map<String, Button> dupBtns = new HashMap<>();
    private final Map<String, Button> delBtns = new HashMap<>();
    /**
     * The semantic controls behind the Duplicate/Delete row buttons (Phase A
     * full rollout), keyed in lockstep with their buttons — created together
     * on the row's first paint, dropped together in {@link #reconcileRowCaches}
     * (removal unregisters the control, clearing focus if it held it). The
     * shared Button remains the sole pixel and press-animation owner; the
     * control owns the activation convergence, narration, and the semantic
     * click. Delete's accessible name carries the target profile name.
     */
    private final Map<String, SemanticActionControl> dupControls = new HashMap<>();
    private final Map<String, SemanticActionControl> delControls = new HashMap<>();
    /** The create-row's confirm button (recreated per create session). */
    private Button createBtn;

    /**
     * The Create confirm's Phase A semantic control (rollout): long-lived
     * (one per screen), with a DYNAMIC enabled gate — enabled exactly while
     * the create field holds a non-empty name. Keyboard routing stays
     * deliberately with the field's Enter (focusable/keyboard-eligible off
     * on the control): making the button a selection target while its own
     * field is being typed into would let a Space keypress commit the form
     * mid-typing — the field already commits on Enter, which is the form's
     * established keyboard path. Pointer activation routes through the
     * control's gate (clicks on the disabled button no longer consume,
     * animate, or fall through into a create attempt).
     */
    private final SemanticActionControl createControl;

    private boolean createRowGlassDrawn = false;

    /** Per-card hover ease (canonical 140 ms), keyed and reconciled like the buttons. */
    private final Map<String, com.aurora.client.util.HoverAnim> cardHover = new HashMap<>();

    /** Card action width — shared by the glass and content passes so they never disagree. */
    private int btnW() { return cellWidth() - INSET * 2; }

    public ProfileManagerScreen(Screen parent) {
        super(Component.literal("Profiles"), parent);
        // Constructed directly (not via SemanticAction.button): this form's
        // keyboard commit belongs to the field's Enter, so the button is
        // focusable=false / keyboard-eligible=false — pointer-activated,
        // narratable, traversal-invisible (see the field javadoc).
        this.createControl = new SemanticActionControl(new SemanticAction(
                Component.literal("Create"),
                () -> Component.literal("Creates a new profile with the entered name."),
                null,
                this::createEnabled,
                this::commitCreate,
                SemanticSound.ACTIVATION,
                false, false),
                MinecraftSemanticFeedback.INSTANCE,
                () -> {
                    if (createBtn != null) createBtn.triggerPressAnimation();
                },
                SemanticActionControl.PointerRouting.MANUAL);
        registerSemanticControl(this.createControl);
    }

    /** The create gate: enabled exactly while a non-empty name is entered. */
    private boolean createEnabled() {
        return creatingNew && createField != null && !createField.getValue().trim().isEmpty();
    }

    // ------------------------------------------------------------------
    //  Foundation hooks
    // ------------------------------------------------------------------

    @Override protected int listWidth() { return Math.max(CARD_MIN_W, Math.min(LIST_MAX_W, this.width - 40)); }
    @Override protected int columns() { return Math.max(1, (listWidth() + CARD_GAP) / (CARD_MIN_W + CARD_GAP)); }
    @Override protected int columnGap() { return CARD_GAP; }
    @Override protected int rowGap() { return CARD_GAP; }
    @Override protected int rowHeight() { return CARD_H; }
    @Override protected int nameColumnWidth() { return cellWidth() - INSET * 2; }
    @Override protected int editorFieldX(int cellX) { return cellX + INSET; }
    @Override protected int editorFieldYOffset() { return NAME_Y - 4; }

    @Override protected List<String> currentRows() {
        return ProfileManager.getInstance().listProfileNames();
    }

    @Override protected Component emptyMessage() {
        return Component.literal("No profiles. Press \"New Profile\" to create one.");
    }

    /** Profile count from the last frame's single list fetch (−1 before the first frame). */
    private int knownProfiles = -1;

    /** The window hugs its cards (up to three rows, then the grid scrolls) — no empty well. */
    @Override protected int windowMaxHeight() {
        int items = (knownProfiles >= 0 ? knownProfiles : currentRows().size()) + leadingRowCount();
        int rows = Math.max(1, Math.min(3, (items + columns() - 1) / columns()));
        return HEADER_H + 2 + rows * (CARD_H + CARD_GAP) - CARD_GAP + 10;
    }

    @Override protected int leadingRowCount() {
        return creatingNew && createField != null ? 1 : 0;
    }

    @Override protected ButtonWidget createToolbarActionBtn() {
        // "New Profile" — a plain action, so neutral raised glass.
        return ButtonWidget.semantic(16, 16, 120, 22,
                Component.literal("New Profile"),
                "Opens the new-profile editor.",
                this::beginCreate).glassBackground(true);
    }

    @Override
    protected void reconcileRowCaches(List<String> profiles) {
        // Row-widget cache hygiene: drop buttons for profiles that no longer
        // exist. Reconciled against a SET: retainAll(List) costs O(rows²) per
        // map (a List.contains scan per key) and ran every frame — with a
        // large profile list that alone dominated the frame (~5 ms at 2000
        // rows). (fixed in 0a0caaa; the rule is now standing — AGENTS.md §10)
        knownProfiles = profiles.size();
        Set<String> live = new HashSet<>(profiles);
        boolean membershipChanged = dupBtns.keySet().retainAll(live);
        membershipChanged |= delBtns.keySet().retainAll(live);
        cardHover.keySet().retainAll(live);
        // The row buttons' semantic controls drop with their buttons —
        // removeWidget takes the control out of the vanilla child/narratable
        // lists and clears screen focus if the removed control held it, so a
        // deleted profile can never leave a stale focusable region behind
        // (the Waypoint-Copy lifecycle rule, applied to both row actions).
        reconcileSemanticControls(dupControls, live);
        reconcileSemanticControls(delControls, live);
        if (membershipChanged) nameFitCache.clear();
    }

    /** Unregisters and drops the row controls whose profile is no longer live. */
    private void reconcileSemanticControls(Map<String, SemanticActionControl> controls,
                                           Set<String> live) {
        for (var it = controls.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (!live.contains(entry.getKey())) {
                unregisterSemanticControl(entry.getValue());
                it.remove();
            }
        }
    }

    @Override
    protected void onBeginRowGlassPass() {
        createRowGlassDrawn = false;
    }

    // ------------------------------------------------------------------
    //  Row passes
    // ------------------------------------------------------------------

    @Override
    protected void paintLeadingRowGlassPass(GuiGraphics ctx, int x, int y, int w, int leadIndex) {
        // Same template treatment as normal rows (the create row shares the
        // row geometry; its Create button sits at Duplicate's offset).
        // Availability follows the same clip-band rule as the row loop so a
        // create row scrolled out of the clip can neither be keyed nor
        // clicked while invisible.
        createControl.setAvailable(creatingNew && y + CARD_H > listClipTop() && y < listClipBottom());
        paintTemplatedRowSurface(ctx, x, y,
                () -> createRowGlassDrawn = renderRowSurface(ctx, x, y, w),
                () -> {
                    Button cb = createBtn();
                    int bx = x + INSET, by = y + LOWER_BTN_Y;
                    cb.layout(bx, by, btnW(), BTN_H);
                    createControl.setBounds(bx, by, btnW(), BTN_H);
                    boolean enabled = createEnabled();
                    cb.disabled(!enabled);
                    cb.focused(createControl.isFocused() && enabled);
                    cb.renderGlassPass(ctx, bx, by, btnW(), BTN_H);
                },
                () -> createRowGlassDrawn);
    }

    @Override
    protected void paintLeadingRow(GuiGraphics ctx, int x, int y, int w, int leadIndex,
                                   int mouseX, int mouseY) {
        renderCreateRow(ctx, x, y, w, mouseX, mouseY);
    }

    @Override
    protected void paintRowGlassPass(GuiGraphics ctx, int x, int y, int w, String name) {
        // Static shapes (rings + row tint) come from the shared row template
        // (see ManagerListScreen); the row panel renders live below, and the
        // Duplicate button supplies its own surface. Delete is destructive
        // (flat) and paints in the content pass via the same shared Button.
        paintTemplatedRowSurface(ctx, x, y,
                () -> rowGlassDrawn.put(name, renderRowSurface(ctx, x, y, w)),
                () -> {
                    Button db = dupBtn(name);
                    db.layout(x + INSET, y + UPPER_BTN_Y, btnW(), BTN_H);
                    syncDupControl(name, x + INSET, y + UPPER_BTN_Y, y);
                    db.renderGlassPass(ctx, x + INSET, y + UPPER_BTN_Y, btnW(), BTN_H);
                },
                () -> rowGlassDrawn.getOrDefault(name, false));
    }

    /** Per-frame Duplicate-control sync: bounds follow the card's live geometry, availability the clip band. Lazily creates the pair (the glass pass runs before the content pass on a card's first frame). */
    private void syncDupControl(String name, int btnX, int btnY, int rowY) {
        SemanticActionControl control = dupControl(name);
        control.setBounds(btnX, btnY, btnW(), BTN_H);
        control.setAvailable(rowY + CARD_H > listClipTop() && rowY < listClipBottom());
    }

    @Override
    protected void paintRow(GuiGraphics ctx, int x, int y, int w, String name, int index,
                            int mouseX, int mouseY) {
        renderRow(ctx, x, y, w, name, index, ProfileManager.getInstance().currentProfile(), mouseX, mouseY);
    }

    private void renderRow(GuiGraphics ctx, int x, int y, int w,
                            String name, int index, String active,
                            int mouseX, int mouseY) {
        boolean isActive = name.equals(active);
        boolean isDefault = "default".equals(name);
        float radius = ThemeManager.current().roundness().radiusSmall();
        boolean pointerIn = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + CARD_H
                && mouseY >= listClipTop() && mouseY < listClipBottom();
        float hover = cardHover.computeIfAbsent(name, k -> com.aurora.client.util.HoverAnim.symmetric(140L))
                .update(pointerIn && !isActive);

        // The card surface was painted in the glass pass, under the dim — see
        // paintRowGlassPass. Here: the selection role (accent outline + soft
        // stain on the active card, a hover lift on the others), or the flat
        // fallback for a card whose glass declined.
        if (MaterialSurface.unified()) {
            MaterialSurface.selection(ctx, x, y, w, CARD_H, radius, isActive, hover);
        } else if (!rowGlassDrawn.getOrDefault(name, false)) {
            if (isActive) MaterialSurface.selection(ctx, x, y, w, CARD_H, radius, true, 0);
            else paintFlatRow(ctx, x, y, w);
        }

        // Name (or the inline editor over it).
        if (index != editingIndex) {
            AuroraFontRenderer.drawCentered(ctx, this.font, fitName(name), x + w / 2, y + NAME_Y,
                    ThemeManager.color(ThemeToken.ON_BACKGROUND));
        }

        // Active pill — the selection-soft chip under the name. Other cards
        // fade in what a click does, only while hovered.
        if (isActive) {
            int pillW = this.font.width("Active") + 14;
            RenderUtil.drawRoundedRectAA(ctx, x + (w - pillW) / 2f, y + PILL_Y, pillW, PILL_H, radius,
                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.SELECTION_SOFT), 0x55));
            AuroraFontRenderer.drawCentered(ctx, this.font, Component.literal("Active"),
                    x + w / 2, y + PILL_Y + (PILL_H - this.font.lineHeight) / 2 + 1,
                    ThemeManager.color(ThemeToken.ON_BACKGROUND));
        } else if (hover > 0.01f) {
            int muted = ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED);
            AuroraFontRenderer.drawCentered(ctx, this.font, Component.literal("Click to switch"),
                    x + w / 2, y + PILL_Y + (PILL_H - this.font.lineHeight) / 2 + 1,
                    ThemeManager.withAlpha(muted, Math.round((muted >>> 24) * hover)));
        }

        // Duplicate — surface already painted in the glass pass; this draws
        // the label (or the flat fallback where glass declined). The semantic
        // control re-syncs here (same rect the glass pass computed): pointer
        // hover for narration and the provisional focus ring on the Button.
        Button dupBtn = dupBtn(name);
        dupBtn.layout(x + INSET, y + UPPER_BTN_Y, btnW(), BTN_H);
        syncDupControl(name, x + INSET, y + UPPER_BTN_Y, y);
        SemanticActionControl dupControl = dupControls.get(name);
        if (dupControl != null) {
            dupControl.updatePointer(mouseX, mouseY);
            dupBtn.focused(dupControl.isFocused());
        }
        dupBtn.render(ctx, x + INSET, y + UPPER_BTN_Y, btnW(), BTN_H, mouseX, mouseY);

        // Delete — shared Button, destructive variant (never glass); hidden
        // for the default + active profiles (non-deletable). Semantic control
        // keyed in lockstep; the accessible name carries the target profile.
        if (!isDefault && !isActive) {
            Button dBtn = delBtn(name);
            dBtn.layout(x + INSET, y + LOWER_BTN_Y, btnW(), BTN_H);
            SemanticActionControl delControl = delControls.get(name);
            if (delControl != null) {
                delControl.setBounds(x + INSET, y + LOWER_BTN_Y, btnW(), BTN_H);
                delControl.setAvailable(y + CARD_H > listClipTop() && y < listClipBottom());
                delControl.updatePointer(mouseX, mouseY);
                dBtn.focused(delControl.isFocused());
            }
            dBtn.render(ctx, x + INSET, y + LOWER_BTN_Y, btnW(), BTN_H, mouseX, mouseY);
        }
    }

    /** Duplicate — the shared themed Button in neutral raised glass, with its Phase A semantic control. */
    private Button dupBtn(String name) {
        return dupBtns.computeIfAbsent(name, k -> {
            Button b = new Button("Duplicate", () -> {}).glassBackground(true)
                    .priority(BlurPanelRenderer.Priority.DETAIL);
            // The action owns the real behavior exactly once; the Button keeps
            // the pixels, hover, press animation (via triggerPressAnimation),
            // and per-frame bounds. Always enabled — deletion of a duplicate
            // is always possible; visibility gating comes from the control's
            // per-frame availability instead.
            SemanticActionControl control = new SemanticActionControl(SemanticAction.button(
                    Component.literal("Duplicate " + k),
                    () -> Component.literal("Creates a copy of this profile, including all of its settings."),
                    () -> Component.empty(),
                    () -> true,
                    () -> {
                        commitEditors();
                        String dupName = uniqueName(k + " Copy");
                        if (ProfileManager.getInstance().duplicate(k, dupName)) {
                            flash("Duplicated → " + dupName);
                        }
                    }),
                    MinecraftSemanticFeedback.INSTANCE,
                    b::triggerPressAnimation,
                    SemanticActionControl.PointerRouting.MANUAL);
            dupControls.put(k, control);
            registerSemanticControl(control);
            return b;
        });
    }

    /** The semantic control behind a Duplicate button (creating the button pair on demand). */
    private SemanticActionControl dupControl(String name) {
        dupBtn(name);
        return dupControls.get(name);
    }

    /** Delete — shared Button, destructive variant, with its Phase A semantic control. */
    private Button delBtn(String name) {
        return delBtns.computeIfAbsent(name, k -> {
            Button b = new Button("Delete", () -> {}).destructive(true);
            SemanticActionControl control = new SemanticActionControl(SemanticAction.button(
                    Component.literal("Delete " + k),
                    () -> Component.literal("Deletes this profile. Its file is removed; the active profile cannot be deleted."),
                    () -> Component.empty(),
                    () -> true,
                    () -> {
                        commitEditors();
                        if (ProfileManager.getInstance().delete(k)) {
                            flash("Deleted " + k);
                        }
                    }),
                    MinecraftSemanticFeedback.INSTANCE,
                    b::triggerPressAnimation,
                    SemanticActionControl.PointerRouting.MANUAL);
            delControls.put(k, control);
            registerSemanticControl(control);
            return b;
        });
    }

    /** The create-row's confirm button — neutral raised glass like every other action button here. */
    private Button createBtn() {
        if (createBtn == null) {
            // No-op onPress: the behavior fires exactly once through the
            // createControl's semantic gate (this button only paints).
            createBtn = new Button("Create", () -> {}).glassBackground(true)
                    .priority(BlurPanelRenderer.Priority.DETAIL);
        }
        return createBtn;
    }

    /**
     * Per-frame sync of the create pair: the control's bounds follow the
     * row's live geometry and its availability tracks the clip band; the
     * Button mirrors the dynamic enabled gate (existing disabled treatment —
     * the rollout's one new visual state) and the control's focus.
     */
    private void syncCreateControl(int x, int y, int mouseX, int mouseY) {
        Button cb = createBtn();
        int btnX = x + INSET;
        int btnY = y + LOWER_BTN_Y;
        cb.layout(btnX, btnY, btnW(), BTN_H);
        createControl.setBounds(btnX, btnY, btnW(), BTN_H);
        createControl.setAvailable(y + CARD_H > listClipTop() && y < listClipBottom());
        createControl.updatePointer(mouseX, mouseY);
        boolean enabled = createEnabled();
        cb.disabled(!enabled);
        cb.focused(createControl.isFocused() && enabled);
    }

    /**
     * Positions the inline editor (the create field, else the rename
     * field) over its row for this frame and returns it, or {@code null}
     * when none is open. Shared by the glass pass (which paints the
     * field's surface at that position) and the content pass (which
     * renders it), so the two can never disagree.
     *
     * <p>C-8: like the base's rename contract, the create field's position
     * is written EVERY frame while the create editor is open — even when
     * its leading row has scrolled out of the clip band — so it can never
     * keep stale hit bounds from its last visible frame. Paint and input
     * gate on {@link #editFieldVisible} plus the base's band gates.
     */
    private EditBox layoutEditField(List<String> profiles, int listX) {
        if (creatingNew && createField != null) {
            createField.setX(editorFieldX(cellX(listX, 0)));
            createField.setY(cellY(0) + editorFieldYOffset());
            return createField;
        }
        return layoutRenameField(profiles, listX);
    }

    /**
     * Whether this screen's active inline editor (create or rename) shows
     * at least one pixel in the clip band this frame — the C-8 render-truth
     * gate the paint passes and keyboard routing consult.
     */
    private boolean editFieldVisible(List<String> profiles) {
        if (creatingNew && createField != null) {
            int fieldY = cellY(0) + editorFieldYOffset();
            return fieldY + 16 > listClipTop() && fieldY < listClipBottom();
        }
        return renameEditorVisible();
    }

    private void renderCreateRow(GuiGraphics ctx, int x, int y, int w,
                                  int mouseX, int mouseY) {
        // The create editor is a neutral card identical to the other rows
        // (the hint text and the Create button signal the open editor; no
        // accent on the container surface in either path). Its surface was
        // painted in the glass pass; only the flat fallback is drawn here.
        if (MaterialSurface.unified()) {
            MaterialSurface.selection(ctx, x, y, w, CARD_H, ThemeManager.current().roundness().radiusSmall(), false, 0);
        } else if (!createRowGlassDrawn) {
            paintFlatRow(ctx, x, y, w);
        }
        AuroraFontRenderer.drawCentered(ctx, this.font, Component.literal("Enter to create · Esc to cancel"),
                x + w / 2, y + PILL_Y + 8, ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED));

        // "Create" confirm button on the card's bottom action line — surface
        // painted in the glass pass; this draws the label.
        syncCreateControl(x, y, mouseX, mouseY);
        Button cb = createBtn();
        cb.render(ctx, x + INSET, y + LOWER_BTN_Y, btnW(), BTN_H, mouseX, mouseY);
    }

    /**
     * Glass pass for one row surface: the soft glow (the row's drop shadow,
     * so part of the surface) plus one DEPRESSED glass panel. Rows are this
     * screen's containers, so they take the window treatment (depressed
     * lighting, WINDOW_FILL tint) rather than the raised control treatment;
     * selection is never carried here at all — the accent Active badge in
     * {@link #renderRow} marks it (a whole-row stain read as an
     * accent-tinted container, flagged twice). Returns whether the glass
     * drew (false = the content pass draws the flat row).
     */
    private boolean renderRowSurface(GuiGraphics ctx, int x, int y, int w) {
        if (MaterialSurface.unified()) return GlassSurface.card(ctx,x,y,w,CARD_H,
                ThemeManager.current().roundness().radiusSmall());
        paintRowShadow(ctx, x, y, w);
        return GlassSurface.container(ctx, x, y, w, CARD_H,
                ThemeManager.current().roundness().radiusSmall(), BlurPanelRenderer.Priority.ROW);
    }

    // ------------------------------------------------------------------
    //  Input
    // ------------------------------------------------------------------

    @Override
    protected boolean editorClickFirst(MouseButtonEvent _ev, boolean _doubleClicked) {
        if (createField != null && createField.mouseClicked(_ev, _doubleClicked)) return true;
        if (nameField != null && nameField.mouseClicked(_ev, _doubleClicked)) return true;
        return false;
    }

    @Override
    protected boolean rowAreaClickFirst(double mouseX, double mouseY) {
        // "Create" confirm button in the create row — routed through its
        // semantic control. C-8: the hit test uses the create row's LIVE
        // geometry (leading row 0 at the current scroll), never the
        // control's possibly-stale bounds — the render walk only re-syncs
        // them while the row is visible — and availability is the control's
        // own clip-band gate, so a create row scrolled out of the band can
        // neither create nor fall through onto anything. Clicks within the
        // button's rect are CONSUMED even when the gate rejects them (empty
        // name, scrolled-away row): the create row sits directly above
        // profile row 0 in the same geometry column, and an unconsumed
        // click would fall through onto that row's Duplicate button. A
        // rejected click is inert — no animation, no sound, no create, no
        // fall-through.
        if (!creatingNew) return false;
        int listX = (this.width - listWidth()) / 2;
        int btnX = cellX(listX, 0) + INSET;
        int btnY = cellY(0) + LOWER_BTN_Y;
        boolean inButton = mouseX >= btnX && mouseX < btnX + btnW()
                && mouseY >= btnY && mouseY < btnY + BTN_H;
        if (!inButton) return false;
        if (createControl.isAvailable()) {
            createControl.activateFromPointer(mouseX, mouseY, 0);
        }
        return true;
    }

    @Override
    protected boolean rowClicked(double mouseX, double mouseY, int listX, String name, int index) {
        // Shared row buttons see the click first — Duplicate routes through
        // its semantic control (enabled gate + exactly-once activation +
        // sound + focus participation), Delete likewise. A rejected click
        // (missed bounds) falls through to the next handler, as before.
        SemanticActionControl dupControl = dupControls.get(name);
        if (dupControl != null && dupControl.activateFromPointer(mouseX, mouseY, 0)) {
            this.setFocused(dupControl);
            return true;
        }
        SemanticActionControl delControl = delControls.get(name);
        if (delControl != null && delControl.activateFromPointer(mouseX, mouseY, 0)) {
            // NO re-focus: the action just deleted this profile's row and the
            // control is unregistered by the next reconcile — refocusing
            // would point keyboard focus at a control about to leave the
            // screen (the stale-focus-after-removal rule).
            return true;
        }

        // The name line renames; everywhere else on the card switches.
        int nameLeft = editorFieldX(listX);
        int nameTop = cellY(leadingRowCount() + index) + editorFieldYOffset();
        if (mouseX >= nameLeft && mouseX < nameLeft + nameColumnWidth()
                && mouseY >= nameTop && mouseY < nameTop + 16) {
            startEditing(index, name);
            return true;
        }

        // Click on the row body (not on a control) → switch if not active.
        // (The base's band + X-extent test is what gives the body click its
        // horizontal bound — it previously had no horizontal check at all.)
        if (!name.equals(ProfileManager.getInstance().currentProfile())) {
            commitEditors();
            if (ProfileManager.getInstance().switchTo(name)) {
                flash("Switched to " + name);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent _kev) {
        int key = _kev.key();
        // C-8: the create editor follows the base rename contract — a field
        // scrolled out of the clip band is SUSPENDED (no keys routed), not
        // cancelled; scrolling back restores typing with focus and value.
        if (createField != null && createField.isFocused() && editFieldVisible(currentRows())) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                commitCreate();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                cancelCreate();
                return true;
            }
            if (createField.keyPressed(_kev)) return true;
        }
        // The base handles the rename field's ENTER/ESCAPE/forwarding.
        return super.keyPressed(_kev);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent _ev) {
        if (createField != null && createField.isFocused() && editFieldVisible(currentRows())
                && createField.charTyped(_ev)) return true;
        return super.charTyped(_ev);
    }

    // ------------------------------------------------------------------
    //  Editor lifecycle (create editor + rename apply)
    // ------------------------------------------------------------------

    private void beginCreate() {
        commitEditors();
        creatingNew = true;
        createField = new EditBox(this.font, 0, 0, nameColumnWidth(), 16, Component.literal("Name"));
        createField.setMaxLength(48);
        createField.setHint(Component.literal("New profile name…")); // the field's own placeholder, above its fill
        createField.setFocused(true);
        scroll.set(0);
    }

    private void commitCreate() {
        if (createField == null) return;
        String v = createField.getValue().trim();
        cancelCreate();
        if (v.isEmpty()) return;
        if (ProfileManager.getInstance().create(v)) {
            flash("Created " + v);
        } else {
            flash("Could not create (name taken?)");
        }
    }

    private void cancelCreate() {
        creatingNew = false;
        createField = null;
        createBtn = null;
    }

    @Override
    protected void applyRename(String oldName, String newValue) {
        // Renaming to the same name is a no-op (no manager call, no toast).
        if (newValue.equals(oldName)) return;
        if (ProfileManager.getInstance().rename(oldName, newValue)) {
            flash("Renamed → " + newValue);
        } else {
            flash("Could not rename (name taken?)");
        }
    }

    @Override
    protected void commitEditors() {
        if (creatingNew) commitCreate();
        if (editingIndex >= 0) commitEdit();
    }

    // ------------------------------------------------------------------
    //  Frame tails (screen-owned order)
    // ------------------------------------------------------------------

    @Override
    protected void paintEditorGlassPass(GuiGraphics ctx, List<String> rows, int listX) {
        // Position ALWAYS (fresh bounds every frame — a hidden editor must
        // never keep a stale rect its last visible frame could leave in the
        // band), then paint only when a pixel is visible. The base scissors
        // this call to the list clip (C-8).
        EditBox field = layoutEditField(rows, listX);
        if (field == null || !editFieldVisible(rows)) return;
        ((GlassEditBox) field).aurora$renderGlassPass(ctx);
    }

    @Override
    protected void paintTail(GuiGraphics ctx, int mouseX, int mouseY, float delta,
                             List<String> rows, int listX) {
        // Inline editors render on top so the caret draws above row fills,
        // and UNDER THE LIST CLIP (C-8 — renderEditorClipped): a partially
        // visible editor paints only its visible pixels, a fully hidden one
        // nothing (bounds still refreshed above via the glass-pass layout).
        // Their surface was painted in the glass pass; this is content only.
        EditBox editor = layoutEditField(rows, listX);
        if (editor != null && editFieldVisible(rows)) {
            renderEditorClipped(ctx, editor, listX, mouseX, mouseY, delta);
        }

        // Toast (above the editor here — this screen's historical order;
        // the Waypoint screen draws it the other way around).
        renderToast(ctx);
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    /** Produce a non-colliding name derived from {@code base}. */
    private String uniqueName(String base) {
        List<String> existing = ProfileManager.getInstance().listProfileNames();
        if (!existing.contains(base)) return base;
        for (int n = 2; n < 1000; n++) {
            String candidate = base + " " + n;
            if (!existing.contains(candidate)) return candidate;
        }
        return base + " " + System.currentTimeMillis();
    }
}
