package com.aurora.client.screen;
import com.aurora.client.config.profile.ProfileManager;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.Button;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.GlassEditBox;
import com.aurora.client.ui.component.GlassSurface;
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

    private static final int ROW_H = 30;
    private static final int LIST_W = 420;

    private static final int ACTIVE_BADGE_W = 56;
    private static final int NAME_W = 150;
    private static final int BTN_DUP_W = 72;
    private static final int BTN_DEL_W = 64;
    private static final int CONTROL_GAP = 6;
    private static final int ROW_INSET = 10;

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
    /** The create-row's confirm button (recreated per create session). */
    private Button createBtn;

    private boolean createRowGlassDrawn = false;

    // Row-control offsets from the row's left edge, shared by the glass pass
    // and the content pass so the two can never disagree on where a button is.
    private static final int DUP_DX = ROW_INSET + ACTIVE_BADGE_W + CONTROL_GAP + NAME_W + CONTROL_GAP;
    private static final int DEL_DX = DUP_DX + BTN_DUP_W + CONTROL_GAP;

    public ProfileManagerScreen(Screen parent) {
        super(Component.literal("Profiles"), parent);
    }

    // ------------------------------------------------------------------
    //  Foundation hooks
    // ------------------------------------------------------------------

    @Override protected int listWidth() { return LIST_W; }
    @Override protected int rowHeight() { return ROW_H; }
    @Override protected int nameColumnWidth() { return NAME_W; }
    @Override protected int editorFieldX(int listX) {
        return listX + ROW_INSET + ACTIVE_BADGE_W + CONTROL_GAP;
    }

    @Override protected List<String> currentRows() {
        return ProfileManager.getInstance().listProfileNames();
    }

    @Override protected Component emptyMessage() {
        return Component.literal("No profiles. Press \"New Profile\" to create one.");
    }

    @Override protected int leadingRowCount() {
        return creatingNew && createField != null ? 1 : 0;
    }

    @Override protected ButtonWidget createToolbarActionBtn() {
        // "New Profile" — a plain action, so neutral raised glass.
        return new ButtonWidget(16, 16, 120, 22,
                Component.literal("New Profile"),
                this::beginCreate).glassBackground(true);
    }

    @Override
    protected void reconcileRowCaches(List<String> profiles) {
        // Row-widget cache hygiene: drop buttons for profiles that no longer
        // exist. Reconciled against a SET: retainAll(List) costs O(rows²) per
        // map (a List.contains scan per key) and ran every frame — with a
        // large profile list that alone dominated the frame (~5 ms at 2000
        // rows). (fixed in 0a0caaa; the rule is now standing — AGENTS.md §10)
        Set<String> live = new HashSet<>(profiles);
        dupBtns.keySet().retainAll(live);
        delBtns.keySet().retainAll(live);
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
        createRowGlassDrawn = renderRowSurface(ctx, x, y, w);
        Button cb = createBtn();
        cb.layout(x + DUP_DX, y + 4, BTN_DUP_W, ROW_H - 8);
        cb.renderGlassPass(ctx, x + DUP_DX, y + 4, BTN_DUP_W, ROW_H - 8);
    }

    @Override
    protected void paintLeadingRow(GuiGraphics ctx, int x, int y, int w, int leadIndex,
                                   int mouseX, int mouseY) {
        renderCreateRow(ctx, x, y, w, mouseX, mouseY);
    }

    @Override
    protected void paintRowGlassPass(GuiGraphics ctx, int x, int y, int w, String name) {
        rowGlassDrawn.put(name, renderRowSurface(ctx, x, y, w));
        // Duplicate is neutral raised glass; Delete is destructive and
        // never glass, so it has nothing to paint here.
        Button db = dupBtn(name);
        db.layout(x + DUP_DX, y + 4, BTN_DUP_W, ROW_H - 8);
        db.renderGlassPass(ctx, x + DUP_DX, y + 4, BTN_DUP_W, ROW_H - 8);
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

        // The row surface (shadow + depressed glass) was painted in the
        // glass pass, under the dim — see paintRowGlassPass. Only the flat
        // fallback is drawn here, for a row whose glass declined: the
        // opacity-tracked surface + hairline border, identical for every
        // row (active included — selection is the Active badge alone).
        if (!rowGlassDrawn.getOrDefault(name, false)) {
            paintFlatRow(ctx, x, y, w);
        }

        int cx = x + ROW_INSET;

        // Active badge — accent chip with the contrast-checked on-accent text.
        if (isActive) {
            float radius = ThemeManager.current().roundness().radiusSmall();
            RenderUtil.drawRoundedRectAA(ctx, cx, y + (ROW_H - 16) / 2, ACTIVE_BADGE_W, 16, radius,
                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ACCENT), 0x55));
            AuroraFontRenderer.drawCentered(ctx, this.font, Component.literal("Active"),
                    cx + ACTIVE_BADGE_W / 2, y + (ROW_H - this.font.lineHeight) / 2,
                    ThemeManager.color(ThemeToken.ON_ACCENT));
        }
        cx += ACTIVE_BADGE_W + CONTROL_GAP;

        // Name (or inline editor).
        if (index != editingIndex) {
            String display = fitName(name);
            ctx.drawString(this.font, display,
                    cx, y + (ROW_H - this.font.lineHeight) / 2,
                    ThemeManager.color(ThemeToken.ON_OVERLAY), false);
        }
        cx += NAME_W + CONTROL_GAP;

        // Duplicate — surface already painted in the glass pass; this draws
        // the label (or the flat fallback where glass declined).
        Button dupBtn = dupBtn(name);
        dupBtn.layout(x + DUP_DX, y + 4, BTN_DUP_W, ROW_H - 8);
        dupBtn.render(ctx, x + DUP_DX, y + 4, BTN_DUP_W, ROW_H - 8, mouseX, mouseY);

        // Delete — shared Button, destructive variant (never glass); hidden
        // for the default + active profiles (non-deletable).
        if (!isDefault && !isActive) {
            Button dBtn = delBtns.computeIfAbsent(name, k -> new Button("Delete", () -> {
                commitEditors();
                if (ProfileManager.getInstance().delete(k)) {
                    flash("Deleted " + k);
                }
            }).destructive(true));
            dBtn.layout(x + DEL_DX, y + 4, BTN_DEL_W, ROW_H - 8);
            dBtn.render(ctx, x + DEL_DX, y + 4, BTN_DEL_W, ROW_H - 8, mouseX, mouseY);
        }
    }

    /** Duplicate — the shared themed Button in neutral raised glass (a plain action, never a selected state). */
    private Button dupBtn(String name) {
        return dupBtns.computeIfAbsent(name, k -> new Button("Duplicate", () -> {
            commitEditors();
            String dupName = uniqueName(k + " Copy");
            if (ProfileManager.getInstance().duplicate(k, dupName)) {
                flash("Duplicated → " + dupName);
            }
        }).glassBackground(true));
    }

    /** The create-row's confirm button — neutral raised glass like every other action button here. */
    private Button createBtn() {
        if (createBtn == null) {
            createBtn = new Button("Create", this::commitCreate).glassBackground(true);
        }
        return createBtn;
    }

    /**
     * Positions the inline editor (the create field, else the rename
     * field) over its row for this frame and returns it, or {@code null}
     * when none is visible. Shared by the glass pass (which paints the
     * field's surface at that position) and the content pass (which
     * renders it), so the two can never disagree.
     */
    private EditBox layoutEditField(List<String> profiles, int listX) {
        if (creatingNew && createField != null) {
            int rowY = listTop() - (int) scroll.current();
            createField.setX(editorFieldX(listX));
            createField.setY(rowY + (ROW_H - 16) / 2);
            return createField;
        }
        return layoutRenameField(profiles, listX);
    }

    private void renderCreateRow(GuiGraphics ctx, int x, int y, int w,
                                  int mouseX, int mouseY) {
        // The create editor is a neutral card identical to the other rows
        // (the hint text and the Create button signal the open editor; no
        // accent on the container surface in either path). Its surface was
        // painted in the glass pass; only the flat fallback is drawn here.
        if (!createRowGlassDrawn) {
            paintFlatRow(ctx, x, y, w);
        }
        // Hint label in the name area (field draws over it).
        int cx = x + ROW_INSET + ACTIVE_BADGE_W + CONTROL_GAP;
        ctx.drawString(this.font, Component.literal("New profile name…"),
                cx + 4, y + (ROW_H - this.font.lineHeight) / 2,
                ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_OVERLAY), 0x66), false);

        // "Create" confirm button where Duplicate usually sits — surface
        // painted in the glass pass; this draws the label.
        Button cb = createBtn();
        cb.layout(x + DUP_DX, y + 4, BTN_DUP_W, ROW_H - 8);
        cb.render(ctx, x + DUP_DX, y + 4, BTN_DUP_W, ROW_H - 8, mouseX, mouseY);
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
        paintRowShadow(ctx, x, y, w);
        return GlassSurface.container(ctx, x, y, w, ROW_H,
                ThemeManager.current().roundness().radiusSmall());
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
        // "Create" confirm button in the create row (shared Button hit-tests
        // its own per-frame-laid-out bounds).
        return creatingNew && createBtn != null && createBtn.mouseClicked(mouseX, mouseY, 0);
    }

    @Override
    protected boolean rowClicked(double mouseX, double mouseY, int listX, String name, int index) {
        // Shared row buttons see the click first.
        Button rowBtn;
        if ((rowBtn = dupBtns.get(name)) != null && rowBtn.mouseClicked(mouseX, mouseY, 0)) return true;
        if ((rowBtn = delBtns.get(name)) != null && rowBtn.mouseClicked(mouseX, mouseY, 0)) return true;

        int nameLeft = editorFieldX(listX);
        if (mouseX >= nameLeft && mouseX < nameLeft + NAME_W) {
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
        if (createField != null && createField.isFocused()) {
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
        if (createField != null && createField.isFocused() && createField.charTyped(_ev)) return true;
        return super.charTyped(_ev);
    }

    // ------------------------------------------------------------------
    //  Editor lifecycle (create editor + rename apply)
    // ------------------------------------------------------------------

    private void beginCreate() {
        commitEditors();
        creatingNew = true;
        createField = new EditBox(this.font, 0, 0, NAME_W, 16, Component.literal("Name"));
        createField.setMaxLength(48);
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
        EditBox field = layoutEditField(rows, listX);
        if (field != null) ((GlassEditBox) field).aurora$renderGlassPass(ctx);
    }

    @Override
    protected void paintTail(GuiGraphics ctx, int mouseX, int mouseY, float delta,
                             List<String> rows, int listX) {
        // Inline editors render on top so the caret draws above row fills.
        // Their surface was painted in the glass pass; this is content only.
        EditBox editor = layoutEditField(rows, listX);
        if (editor != null) editor.render(ctx, mouseX, mouseY, delta);

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
