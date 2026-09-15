package com.aurora.client.screen;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.AuroraConfig.Waypoint;
import com.aurora.client.feature.impl.WaypointFeature;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.Button;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.ColorSwatch;
import com.aurora.client.ui.component.GlassEditBox;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.interaction.MinecraftSemanticFeedback;
import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.util.WorldScope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lists the current world's waypoints. Each row exposes: color swatch,
 * editable name, integer coordinates, color-edit button, delete button.
 * Top toolbar provides "Drop at current position" and "Done".
 *
 * <p>On the shared component framework, atop the {@link ManagerListScreen}
 * foundation (audit R3): the frame order, scroll + scrollbar, toast and
 * inline rename lifecycle come from the base; this class owns the row
 * content and actions — row swatches are the shared {@link ColorSwatch},
 * row actions are the canonical themed {@link Button} (delete uses its
 * destructive variant), row chrome resolves through theme tokens, and the
 * inline rename field is themed by the mod-wide {@code EditBoxMixin} via
 * the {@link com.aurora.client.ui.component.ThemedScreen} marker.
 *
 * <p>Glass rollout: each row is its own RAISED glass panel — controls/rows
 * float above the world (there is no containing window panel on this
 * screen, so nothing takes the depressed treatment here; the Profile
 * screen's rows do — a deliberate per-screen §6 ruling kept by the base).
 * Row action buttons are neutral raised glass via the shared Button; the
 * Drop button — the screen's single primary action — uses accent-STAINED
 * glass. The color swatch and the inline rename field stay opaque: the
 * swatch displays user color content at full fidelity (the picker-pad
 * rule), and text-entry fields are never glass. On renderer decline (no
 * world, screenshot suppression) the flat surface fills + borders return
 * unchanged.
 */
public class WaypointManagerScreen extends ManagerListScreen<Waypoint> {

    private static final int ROW_H        = 28;
    private static final int LIST_W       = 460;

    private static final int SWATCH_W     = 16;
    private static final int NAME_W       = 130;
    private static final int COORDS_W     = 96;
    private static final int BTN_COPY_W   = 48;
    private static final int BTN_COLOR_W  = 48;
    private static final int BTN_DEL_W    = 56;
    private static final int CONTROL_GAP  = 6;
    private static final int ROW_INSET    = 8;

    // Shared per-row widgets, keyed by the waypoint INSTANCE — the same
    // identity WaypointFeature.remove() matches, and the objects in the
    // live list are stable across frames (edits mutate in place). Keying
    // by position (the old scheme) let a remove-then-add between frames
    // reuse a stale entry for a different waypoint; keying by identity and
    // reconciling against the live list every frame (see
    // reconcileRowCaches) can never point an action at the wrong object.
    // Waypoint does not override equals/hashCode, so HashMap compares by
    // identity — exactly right.
    private final Map<Waypoint, Button> rowCopyBtns = new HashMap<>();
    private final Map<Waypoint, Button> rowColorBtns = new HashMap<>();
    private final Map<Waypoint, Button> rowDeleteBtns = new HashMap<>();
    private final Map<Waypoint, ColorSwatch> rowSwatches = new HashMap<>();

    /**
     * The semantic controls behind the row buttons (Phase A full rollout),
     * keyed in lockstep with their buttons — created together, and dropped
     * together in {@link #reconcileRowCaches} (removal also unregisters the
     * control from the screen, which clears focus if it held it). The shared
     * Button remains the sole pixel and press-animation owner; the control
     * owns the enabled gate, the activation convergence, narration, and the
     * semantic click. Color/Delete joined Copy in the full rollout; Delete's
     * accessible name carries the target waypoint.
     */
    private final Map<Waypoint, SemanticActionControl> rowCopyControls = new HashMap<>();
    private final Map<Waypoint, SemanticActionControl> rowColorControls = new HashMap<>();
    private final Map<Waypoint, SemanticActionControl> rowDeleteControls = new HashMap<>();

    // Row-control offsets from the row's left edge, shared by the glass pass
    // and the content pass so the two can never disagree on where a button is.
    private static final int COPY_DX  = ROW_INSET + SWATCH_W + CONTROL_GAP + NAME_W + CONTROL_GAP + COORDS_W + CONTROL_GAP;
    private static final int COLOR_DX = COPY_DX + BTN_COPY_W + CONTROL_GAP;
    private static final int DEL_DX   = COLOR_DX + BTN_COLOR_W + CONTROL_GAP;

    public WaypointManagerScreen(Screen parent) {
        super(Component.literal("Waypoints — " + WorldScope.current()), parent);
    }

    // ------------------------------------------------------------------
    //  Foundation hooks
    // ------------------------------------------------------------------

    @Override protected int listWidth() { return LIST_W; }
    @Override protected int rowHeight() { return ROW_H; }
    @Override protected int nameColumnWidth() { return NAME_W; }
    @Override protected int editorFieldX(int listX) {
        return listX + ROW_INSET + SWATCH_W + CONTROL_GAP;
    }

    @Override protected List<Waypoint> currentRows() {
        WaypointFeature feat = WaypointFeature.get();
        if (feat == null) return java.util.Collections.emptyList();
        return feat.currentWorldWaypoints();
    }

    @Override protected Component emptyMessage() {
        return Component.literal("No waypoints in this world. Press Drop to add one.");
    }

    @Override protected ButtonWidget createToolbarActionBtn() {
        // Drop is the screen's single primary action → accent-STAINED glass
        // (Done, from the base, is a plain action → neutral).
        return ButtonWidget.semantic(
                16, 16, 160, 22,
                Component.literal("Drop at Player Position"),
                "Drops a new waypoint at your current position.",
                () -> {
                    WaypointFeature feat = WaypointFeature.get();
                    if (feat == null) return;
                    feat.dropAtPlayer("Waypoint", 0xFFFFAA00, false);
                    rebuildNameField();
                }).glassStyle(Button.GlassStyle.STAINED).primary(true);
    }

    @Override
    protected void reconcileRowCaches(List<Waypoint> all) {
        // Row-widget cache hygiene: reconcile against the LIVE list every
        // frame — entries for removed waypoints drop immediately, new
        // waypoints lazily create theirs. The old count-only guard missed
        // remove-then-add sequences that kept the count identical.
        //
        // Reconciled against a SET: retainAll(List) costs O(rows²) per map
        // (a List.contains scan per key) and FOUR maps ran it every frame —
        // with a large waypoint list that alone dominated the frame (~1.5 ms
        // at 2000 rows; fixed in 0a0caaa). Same semantics: Waypoint does not
        // override equals, so set membership and List.contains agree (both
        // identity). The name-fit cache clears with the row caches when
        // membership changes, to keep the map bounded.
        Set<Waypoint> live = new HashSet<>(all);
        boolean membershipChanged = rowCopyBtns.keySet().retainAll(live);
        membershipChanged |= rowColorBtns.keySet().retainAll(live);
        membershipChanged |= rowDeleteBtns.keySet().retainAll(live);
        membershipChanged |= rowSwatches.keySet().retainAll(live);
        // The row buttons' semantic controls drop with their buttons —
        // removeWidget takes the control out of the vanilla child/narratable
        // lists and clears screen focus if the removed control held it, so a
        // deleted waypoint can never leave a stale focusable region behind.
        reconcileSemanticControls(rowCopyControls, live);
        reconcileSemanticControls(rowColorControls, live);
        reconcileSemanticControls(rowDeleteControls, live);
        if (membershipChanged) nameFitCache.clear();
    }

    /** Unregisters and drops the row controls whose waypoint is no longer live. */
    private void reconcileSemanticControls(Map<Waypoint, SemanticActionControl> controls,
                                           Set<Waypoint> live) {
        for (var it = controls.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (!live.contains(entry.getKey())) {
                unregisterSemanticControl(entry.getValue());
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------
    //  Row passes
    // ------------------------------------------------------------------

    /**
     * Glass pass for one row: the row's own RAISED glass surface (its own
     * correctly-cropped backdrop slice — per-element capture, never a
     * squished parent rect; neutral WINDOW_FILL tint, whose alpha is the
     * Background Opacity — single application point; the glass rim replaces
     * the hairline border) plus the surfaces of its Copy/Color buttons.
     * The glow rings are the row's drop shadow, i.e. part of the surface,
     * so they belong here too. Delete is the destructive variant and never
     * glass — its pass is a no-op by eligibility, called for uniformity.
     */
    @Override
    protected void paintRowGlassPass(GuiGraphics ctx, int x, int y, int w, Waypoint wp) {
        // Static shapes (rings + row tint) come from the shared row template
        // (see ManagerListScreen); the row panel still renders live below, and
        // the Copy/Color/Delete buttons supply their own surfaces.
        paintTemplatedRowSurface(ctx, x, y,
                () -> {
                    paintRowShadow(ctx, x, y, w);
                    rowGlassDrawn.put(wp, GlassSurface.control(ctx, x, y, w, ROW_H,
                            ThemeManager.current().roundness().radiusSmall(), BlurPanelRenderer.Priority.ROW));
                },
                () -> {
                    int by = y + 4;
                    int bh = ROW_H - 8;
                    Button b = copyBtn(wp);
                    b.layout(x + COPY_DX, by, BTN_COPY_W, bh);
                    // Same clip-band rule the content pass and the base hit
                    // test use (row rect vs listClipTop/Bottom), applied at
                    // the earliest point the geometry is known.
                    SemanticActionControl copyControl = copyControl(wp);
                    copyControl.setBounds(x + COPY_DX, by, BTN_COPY_W, bh);
                    copyControl.setAvailable(y + ROW_H > listClipTop() && y < listClipBottom());
                    b.renderGlassPass(ctx, x + COPY_DX, by, BTN_COPY_W, bh);
                    b = colorBtn(wp);
                    b.layout(x + COLOR_DX, by, BTN_COLOR_W, bh);
                    SemanticActionControl colorControl = colorControl(wp);
                    colorControl.setBounds(x + COLOR_DX, by, BTN_COLOR_W, bh);
                    colorControl.setAvailable(y + ROW_H > listClipTop() && y < listClipBottom());
                    b.renderGlassPass(ctx, x + COLOR_DX, by, BTN_COLOR_W, bh);
                    b = deleteBtn(wp);
                    b.layout(x + DEL_DX, by, BTN_DEL_W, bh);
                    SemanticActionControl deleteControl = deleteControl(wp);
                    deleteControl.setBounds(x + DEL_DX, by, BTN_DEL_W, bh);
                    deleteControl.setAvailable(y + ROW_H > listClipTop() && y < listClipBottom());
                    b.renderGlassPass(ctx, x + DEL_DX, by, BTN_DEL_W, bh);
                },
                () -> rowGlassDrawn.getOrDefault(wp, false));
    }

    @Override
    protected void paintRow(GuiGraphics ctx, int x, int y, int w, Waypoint wp, int index,
                            int mouseX, int mouseY) {
        // The row surface (shadow + glass) was painted in the glass pass,
        // under the dim — see paintRowGlassPass. Only the flat fallback
        // (surface fill + border) is drawn here, for a row whose glass
        // declined; it returns unchanged from the pre-glass look.
        if (!rowGlassDrawn.getOrDefault(wp, false)) {
            paintFlatRow(ctx, x, y, w);
        }

        int cx = x + ROW_INSET;
        int cy = y + (ROW_H - SWATCH_W) / 2;

        // Color swatch — the shared ColorSwatch component (checkerboard so
        // alpha-carrying waypoint colors read correctly). Not clickable; the
        // Color button beside it owns the edit action. Stays opaque: it
        // displays user color content at full fidelity.
        ColorSwatch swatch = rowSwatches.computeIfAbsent(wp,
                k -> new ColorSwatch(() -> wp.color, null).checkerboard(true));
        swatch.layout(cx, cy, SWATCH_W, SWATCH_W);
        swatch.render(ctx, cx, cy, SWATCH_W, SWATCH_W, mouseX, mouseY);
        cx += SWATCH_W + CONTROL_GAP;

        // Name (or inline editor — when editing, the field is drawn from
        // the tail pass so the row body skips drawing the name text
        // for that index).
        if (index != editingIndex) {
            String name = fitName(wp.name == null ? "" : wp.name);
            ctx.drawString(this.font, name,
                    cx, y + (ROW_H - this.font.lineHeight) / 2,
                    ThemeManager.color(ThemeToken.ON_OVERLAY), false);
        }
        cx += NAME_W + CONTROL_GAP;

        // Coords (clickable: click to copy).
        String coords = wp.x + ", " + wp.y + ", " + wp.z;
        ctx.drawString(this.font, coords,
                cx, y + (ROW_H - this.font.lineHeight) / 2,
                ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_OVERLAY), 0x88), false);
        cx += COORDS_W + CONTROL_GAP;

        // Row buttons — surfaces already painted in the glass pass; these
        // renders draw labels (or the flat fallback where glass declined).
        // Each button's semantic control re-syncs here (same rect the glass
        // pass computed): pointer-hover state for narration, availability
        // from the same clip band the base hit test clamps to, and the
        // provisional focus ring on the shared Button.
        Button copyBtn = copyBtn(wp);
        copyBtn.layout(x + COPY_DX, y + 4, BTN_COPY_W, ROW_H - 8);
        SemanticActionControl copyControl = copyControl(wp);
        syncRowControl(copyControl, x + COPY_DX, BTN_COPY_W, y, mouseX, mouseY);
        copyBtn.focused(copyControl.isFocused());
        copyBtn.render(ctx, x + COPY_DX, y + 4, BTN_COPY_W, ROW_H - 8, mouseX, mouseY);

        Button colorBtn = colorBtn(wp);
        colorBtn.layout(x + COLOR_DX, y + 4, BTN_COLOR_W, ROW_H - 8);
        SemanticActionControl colorControl = colorControl(wp);
        syncRowControl(colorControl, x + COLOR_DX, BTN_COLOR_W, y, mouseX, mouseY);
        colorBtn.focused(colorControl.isFocused());
        colorBtn.render(ctx, x + COLOR_DX, y + 4, BTN_COLOR_W, ROW_H - 8, mouseX, mouseY);

        Button delBtn = deleteBtn(wp);
        delBtn.layout(x + DEL_DX, y + 4, BTN_DEL_W, ROW_H - 8);
        SemanticActionControl delControl = deleteControl(wp);
        syncRowControl(delControl, x + DEL_DX, BTN_DEL_W, y, mouseX, mouseY);
        delBtn.focused(delControl.isFocused());
        delBtn.render(ctx, x + DEL_DX, y + 4, BTN_DEL_W, ROW_H - 8, mouseX, mouseY);
    }

    /** Shared per-frame row-control sync: bounds + clip-band availability + pointer hover for narration. */
    private void syncRowControl(SemanticActionControl control, int btnX, int btnW, int rowY,
                                int mouseX, int mouseY) {
        control.setBounds(btnX, rowY + 4, btnW, ROW_H - 8);
        control.setAvailable(rowY + ROW_H > listClipTop() && rowY < listClipBottom());
        control.updatePointer(mouseX, mouseY);
    }

    /** Copy — the shared themed Button in neutral raised glass, with its Phase A semantic control. */
    private Button copyBtn(Waypoint wp) {
        return rowCopyBtns.computeIfAbsent(wp, k -> {
            Button b = new Button("Copy", () -> {}).glassBackground(true)
                    .priority(BlurPanelRenderer.Priority.DETAIL);
            // The action owns the real behavior exactly once; the Button keeps
            // the pixels, hover, press animation (via triggerPressAnimation),
            // and per-frame bounds. Always enabled — the manager enforces no
            // availability rule for copy; visibility gating comes from the
            // control's per-frame availability instead.
            SemanticActionControl control = new SemanticActionControl(SemanticAction.button(
                    Component.literal("Copy " + where(k)),
                    () -> Component.literal("Copies this waypoint's coordinates to the clipboard."),
                    () -> Component.literal("Coordinates: " + k.x + " " + k.y + " " + k.z),
                    () -> true,
                    () -> {
                        commitEditors();
                        copyToClipboard(k.x + " " + k.y + " " + k.z);
                        flash("Copied: " + k.x + " " + k.y + " " + k.z);
                    }),
                    MinecraftSemanticFeedback.INSTANCE,
                    b::triggerPressAnimation,
                    SemanticActionControl.PointerRouting.MANUAL);
            rowCopyControls.put(k, control);
            registerSemanticControl(control);
            return b;
        });
    }

    /** The semantic control behind a Copy button (creating the button pair on demand). */
    private SemanticActionControl copyControl(Waypoint wp) {
        copyBtn(wp);
        return rowCopyControls.get(wp);
    }

    /** Color — shared themed Button in neutral raised glass, with its Phase A semantic control; opens the shared color picker. */
    private Button colorBtn(Waypoint wp) {
        return rowColorBtns.computeIfAbsent(wp, k -> {
            Button b = new Button("Color", () -> {}).glassBackground(true)
                    .priority(BlurPanelRenderer.Priority.DETAIL);
            String where = where(k);
            SemanticActionControl control = new SemanticActionControl(SemanticAction.button(
                    Component.literal("Color " + where),
                    () -> Component.literal("Opens the color picker for this waypoint."),
                    () -> Component.empty(),
                    () -> true,
                    () -> {
                        commitEditors();
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(new ColorPickerScreen(WaypointManagerScreen.this,
                                    "Waypoint Color", k.color, argb -> {
                                        // k IS the waypoint instance (identity-keyed), so
                                        // resolve it in the live list by identity — never
                                        // by position, which could target a different
                                        // waypoint after any add/remove.
                                        List<Waypoint> live = currentRows();
                                        if (live.contains(k)) {
                                            k.color = argb;
                                            WaypointFeature feat = WaypointFeature.get();
                                            if (feat != null) feat.touch();
                                        }
                                    }));
                        }
                    }),
                    MinecraftSemanticFeedback.INSTANCE,
                    b::triggerPressAnimation,
                    SemanticActionControl.PointerRouting.MANUAL);
            rowColorControls.put(k, control);
            registerSemanticControl(control);
            return b;
        });
    }

    /** The semantic control behind a Color button (creating the button pair on demand). */
    private SemanticActionControl colorControl(Waypoint wp) {
        colorBtn(wp);
        return rowColorControls.get(wp);
    }

    /**
     * Delete — shared Button in its destructive (semantic-error) variant,
     * with its Phase A semantic control. The accessible name carries the
     * target waypoint so a narrator announces what would be removed.
     * Destructive keeps the flat look by design (the shared Button skips
     * glass for destructive) — error semantics must not read as glass chrome.
     */
    private Button deleteBtn(Waypoint wp) {
        return rowDeleteBtns.computeIfAbsent(wp, k -> {
            Button b = new Button("Delete", () -> {}).destructive(true);
            SemanticActionControl control = new SemanticActionControl(SemanticAction.button(
                    Component.literal("Delete " + where(k)),
                    () -> Component.literal("Removes this waypoint from the current world."),
                    () -> Component.empty(),
                    () -> true,
                    () -> {
                        commitEditors();
                        WaypointFeature feat = WaypointFeature.get();
                        if (feat != null) feat.remove(k);
                        rebuildNameField();
                    }),
                    MinecraftSemanticFeedback.INSTANCE,
                    b::triggerPressAnimation,
                    SemanticActionControl.PointerRouting.MANUAL);
            rowDeleteControls.put(k, control);
            registerSemanticControl(control);
            return b;
        });
    }

    /** The semantic control behind a Delete button (creating the button pair on demand). */
    private SemanticActionControl deleteControl(Waypoint wp) {
        deleteBtn(wp);
        return rowDeleteControls.get(wp);
    }

    /** The accessible-name form of a waypoint: its name, or its coordinates when unnamed. */
    private static String where(Waypoint wp) {
        return (wp.name == null || wp.name.isBlank())
                ? "waypoint " + wp.x + " " + wp.y + " " + wp.z
                : wp.name;
    }

    // ------------------------------------------------------------------
    //  Input
    // ------------------------------------------------------------------

    @Override
    protected boolean editorClickFirst(MouseButtonEvent _ev, boolean _doubleClicked) {
        return nameField != null && nameField.mouseClicked(_ev, _doubleClicked);
    }

    @Override
    protected boolean rowClicked(double mouseX, double mouseY, int listX, Waypoint wp, int index) {
        // Shared row buttons see the click first — each routes through its
        // semantic control (enabled gate + exactly-once activation + sound +
        // focus participation). A rejected click (missed bounds) falls
        // through to the next handler, as before.
        SemanticActionControl copyControl = rowCopyControls.get(wp);
        if (copyControl != null && copyControl.activateFromPointer(mouseX, mouseY, 0)) {
            this.setFocused(copyControl);
            return true;
        }
        SemanticActionControl colorControl = rowColorControls.get(wp);
        if (colorControl != null && colorControl.activateFromPointer(mouseX, mouseY, 0)) {
            this.setFocused(colorControl);
            return true;
        }
        SemanticActionControl delControl = rowDeleteControls.get(wp);
        if (delControl != null && delControl.activateFromPointer(mouseX, mouseY, 0)) {
            // NO re-focus: the action just removed this row, and the control
            // is unregistered by the next reconcile — refocusing would point
            // keyboard focus at a control about to leave the screen (the
            // stale-focus-after-removal rule). The click rule above already
            // dropped focus.
            return true;
        }

        // Name area → enter inline edit mode for this row.
        int nameLeft = editorFieldX(listX);
        if (mouseX >= nameLeft && mouseX < nameLeft + NAME_W) {
            startEditing(index, wp.name == null ? "" : wp.name);
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    //  Rename apply + editor upkeep
    // ------------------------------------------------------------------

    @Override
    protected void applyRename(Waypoint wp, String newValue) {
        wp.name = newValue;
        WaypointFeature feat = WaypointFeature.get();
        if (feat != null) feat.touch();
    }

    @Override
    protected void commitEditors() {
        commitEdit();
    }

    private void rebuildNameField() {
        if (editingIndex >= currentRows().size()) {
            cancelEdit();
        }
    }

    // ------------------------------------------------------------------
    //  Frame tails (screen-owned order)
    // ------------------------------------------------------------------

    @Override
    protected void paintEditorGlassPass(GuiGraphics ctx, List<Waypoint> rows, int listX) {
        if (layoutRenameField(rows, listX) != null) {
            ((GlassEditBox) nameField).aurora$renderGlassPass(ctx);
        }
    }

    @Override
    protected void paintTail(GuiGraphics ctx, int mouseX, int mouseY, float delta,
                             List<Waypoint> rows, int listX) {
        // Transient toast for clipboard copies. Fades over the last 250ms
        // of its lifetime so the visual confirmation isn't jarring.
        renderToast(ctx);

        // Inline name editor sits on top of its row — rendered after the
        // toast so its caret and selection draw above everything (this
        // screen's historical order; the Profile screen draws its editor
        // first). Its surface was painted in the glass pass; this draws
        // content only.
        if (layoutRenameField(rows, listX) != null) {
            nameField.render(ctx, mouseX, mouseY, delta);
        }
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private void copyToClipboard(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.keyboardHandler == null) return;
        mc.keyboardHandler.setClipboard(text);
    }

    @Override
    protected void saveOnClose() {
        AuroraConfig.save();
    }
}
