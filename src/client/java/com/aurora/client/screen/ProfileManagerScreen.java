package com.aurora.client.screen;
import com.aurora.client.ui.util.AuroraFontRenderer;

import com.aurora.client.config.profile.ProfileManager;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.Button;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.GlassEditBox;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.component.Toast;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.SmoothScroll;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lists configuration profiles and exposes create / rename / duplicate /
 * delete plus one-click switch to the active profile.
 *
 * <p>Backed by {@link ProfileManager}. Mirrors the visual + interaction
 * conventions of {@link WaypointManagerScreen}: themed overlay background,
 * centered rounded-rect rows, inline {@link EditBox} rename (themed by the
 * mod-wide {@code EditBoxMixin} via {@link ThemedScreen}), a transient toast
 * for confirmation feedback, and a top toolbar of shared themed buttons.
 * Row actions (Duplicate/Delete/Create) are the canonical
 * {@link Button} — Delete in its destructive variant.
 *
 * <p>The active profile is visually marked and cannot be deleted (the
 * manager enforces this; the row simply hides its delete button). The
 * {@code default} profile is likewise non-deletable.
 */
public class ProfileManagerScreen extends Screen implements ThemedScreen {

    private static final int ROW_H = 30;
    private static final int ROW_GAP = 4;
    private static final int LIST_W = 420;
    private static final int LIST_TOP = 56;
    private static final int LIST_BOTTOM_PAD = 50;

    private static final int ACTIVE_BADGE_W = 56;
    private static final int NAME_W = 150;
    private static final int BTN_DUP_W = 72;
    private static final int BTN_DEL_W = 64;
    private static final int CONTROL_GAP = 6;
    private static final int ROW_INSET = 10;

    private final Screen parent;

    /** Toast feedback ("Switched to X", "Created Y", …). */
    private final Toast toast = new Toast();

    /** Inline rename editor state. {@code -1} = not editing. */
    private int editingIndex = -1;
    private EditBox nameField;

    /**
     * Pending create: when the user clicks "New", we show an empty editor
     * at the top of the list rather than mid-list.
     */
    private boolean creatingNew = false;
    private EditBox createField;

    /**
     * List scrolling — new with R2 Part C (approved): this screen and
     * {@link WaypointManagerScreen} previously had hard-clamped direct
     * {@code scrollY} manipulation with no easing and no scrollbar. Now the
     * wheel feeds the shared component's target (τ = 60 ms — the same glide
     * FeatureDetailScreen's plain settings list ships with; wheel step 30
     * unchanged) and a draggable, hover-responsive thumb shares one visual
     * treatment with the Waypoint screen: a 3px {@code ON_OVERLAY} capsule
     * at 0x30 alpha (0x55 hovered/dragged) beside the list.
     */
    private final SmoothScroll scroll = new SmoothScroll(60.0);

    // Shared themed row buttons, keyed by profile NAME — unique and stable,
    // so a rename simply produces a fresh entry instead of a stale capture.
    private final Map<String, Button> dupBtns = new HashMap<>();
    private final Map<String, Button> delBtns = new HashMap<>();
    /** The create-row's confirm button (recreated per create session). */
    private Button createBtn;
    /**
     * Fitted (ellipsis-truncated) profile names, keyed by the raw name —
     * memoizes the per-character {@code font.width()} truncation loop that
     * otherwise runs every row, every frame (the same cost the pack browser
     * memoizes via its text-fit cache). Keyed by content, so renames miss
     * once and repopulate; bounded by distinct names seen this screen-open.
     */
    private final Map<String, String> nameFitCache = new HashMap<>();

    // Which rows' glass drew this frame (glass pass), so the flat row body
    // is drawn for exactly the rows whose glass declined.
    private final Map<String, Boolean> rowGlassDrawn = new HashMap<>();
    private boolean createRowGlassDrawn = false;

    /** Toolbar buttons — kept so the glass pass can paint their surfaces pre-dim. */
    private ButtonWidget newBtn;
    private ButtonWidget doneBtn;

    // Row-control offsets from the row's left edge, shared by the glass pass
    // and the content pass so the two can never disagree on where a button is.
    private static final int DUP_DX = ROW_INSET + ACTIVE_BADGE_W + CONTROL_GAP + NAME_W + CONTROL_GAP;
    private static final int DEL_DX = DUP_DX + BTN_DUP_W + CONTROL_GAP;

    public ProfileManagerScreen(Screen parent) {
        super(Component.literal("Profiles"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int btnY = 16;
        int btnH = 22;

        newBtn = this.addRenderableWidget(new ButtonWidget(
                16, btnY, 120, btnH,
                Component.literal("New Profile"),
                this::beginCreate).glassBackground(true));

        doneBtn = this.addRenderableWidget(new ButtonWidget(
                this.width - 80 - 16, btnY, 80, btnH,
                Component.literal("Done"),
                this::onClose).glassBackground(true));
    }

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------

    /**
     * Glass rollout: with a live world behind the screen, skip vanilla's
     * background sandwich — the glass rows must sample the LIVE world. With
     * no level loaded the renderer declines anyway (its menu-context guard)
     * and the opaque fallback wants the vanilla backdrop as before. (The
     * audit's B3: this override was missing here.)
     */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        if (GlassSurface.liveWorldBackdrop()) return;
        super.renderBackground(g, mouseX, mouseY, delta);
    }

    /**
     * Frame order — the layering contract (AGENTS.md §6), structural here:
     * <ol>
     *   <li><b>Glass pass</b>: EVERY glass surface on the screen — the rows
     *       (this screen's containers: depressed), the create row, the
     *       Duplicate/Create buttons, the toolbar's New Profile/Done, and
     *       the inline rename/create field — paints its surface first.</li>
     *   <li><b>Dim</b>: {@link GlassSurface#overlayDim} veils the glass
     *       exactly as it veils the world, and from here on any glass call
     *       is reported as an ordering violation.</li>
     *   <li><b>Content</b>: title, badges, names, button labels, the caret
     *       — and the flat fallback for any surface whose glass declined.</li>
     * </ol>
     */
    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // Easing step first, so the glass pass and the content pass below
        // both read the same this-frame position.
        scroll.advance(maxScroll());
        int listX = (this.width - LIST_W) / 2;
        List<String> profiles = ProfileManager.getInstance().listProfileNames();
        String active = ProfileManager.getInstance().currentProfile();
        // Row-widget cache hygiene: drop buttons for profiles that no longer
        // exist — before either pass so both see the same instances.
        dupBtns.keySet().retainAll(profiles);
        delBtns.keySet().retainAll(profiles);

        // ---- 1. Glass pass (before the dim) ----
        // Opened explicitly so each surface's rim finish is deferred past
        // the dim (painted by overlayDim); the tracked scissor lets the
        // deferred row rims keep the list clip.
        GlassSurface.beginGlassPass();
        rowGlassDrawn.clear();
        createRowGlassDrawn = false;
        GlassSurface.enableScissor(ctx, listX - 4, listClipTop(), listX + LIST_W + 4, listClipBottom());
        int gy = LIST_TOP - (int) scroll.current();
        if (creatingNew && createField != null) {
            createRowGlassDrawn = renderRowSurface(ctx, listX, gy, LIST_W);
            Button cb = createBtn();
            cb.layout(listX + DUP_DX, gy + 4, BTN_DUP_W, ROW_H - 8);
            cb.renderGlassPass(ctx, listX + DUP_DX, gy + 4, BTN_DUP_W, ROW_H - 8);
            gy += ROW_H + ROW_GAP;
        }
        for (String name : profiles) {
            if (gy + ROW_H > LIST_TOP - ROW_H && gy < listClipBottom()) {
                rowGlassDrawn.put(name, renderRowSurface(ctx, listX, gy, LIST_W));
                // Duplicate is neutral raised glass; Delete is destructive and
                // never glass, so it has nothing to paint here.
                Button db = dupBtn(name);
                db.layout(listX + DUP_DX, gy + 4, BTN_DUP_W, ROW_H - 8);
                db.renderGlassPass(ctx, listX + DUP_DX, gy + 4, BTN_DUP_W, ROW_H - 8);
            }
            gy += ROW_H + ROW_GAP;
        }
        GlassSurface.disableScissor(ctx);
        if (newBtn != null) newBtn.renderGlassPass(ctx);
        if (doneBtn != null) doneBtn.renderGlassPass(ctx);
        EditBox field = layoutEditField(profiles, listX);
        if (field != null) ((GlassEditBox) field).aurora$renderGlassPass(ctx);

        // ---- 2. Dim — end of the glass pass (token-driven; dark in both modes by design) ----
        GlassSurface.overlayDim(ctx, this.width, this.height);

        // ---- 3. Content ----
        ctx.drawString(this.font, this.title, 16, 42, ThemeManager.color(ThemeToken.ON_OVERLAY), false);

        ctx.enableScissor(listX - 4, listClipTop(), listX + LIST_W + 4, listClipBottom());

        if (profiles.isEmpty()) {
            ctx.drawString(this.font,
                    Component.literal("No profiles. Press \"New Profile\" to create one."),
                    listX + 8, LIST_TOP + 10,
                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_OVERLAY), 0x88), false);
        }

        // Inline "new profile" editor row at the top.
        int y = LIST_TOP - (int) scroll.current();
        if (creatingNew && createField != null) {
            renderCreateRow(ctx, listX, y, LIST_W, mouseX, mouseY);
            y += ROW_H + ROW_GAP;
        }

        for (int i = 0; i < profiles.size(); i++) {
            String name = profiles.get(i);
            if (y + ROW_H > LIST_TOP - ROW_H && y < listClipBottom()) {
                renderRow(ctx, listX, y, LIST_W, name, i, active, mouseX, mouseY);
            }
            y += ROW_H + ROW_GAP;
        }

        ctx.disableScissor();

        // The list's scrollbar thumb — after the content scissor (it is
        // content, painted opaque over the dim like every other thumb).
        drawScrollbar(ctx, mouseX, mouseY, listX);

        super.render(ctx, mouseX, mouseY, delta);

        // Inline editors render on top so the caret draws above row fills.
        // Their surface was painted in the glass pass; this is content only.
        EditBox editor = layoutEditField(profiles, listX);
        if (editor != null) editor.render(ctx, mouseX, mouseY, delta);

        // Toast.
        toast.render(ctx, this.font, this.width, this.height, 4);
    }

    /**
     * Ellipsis-truncates a profile name to the name column, memoized per
     * raw name (see {@link #nameFitCache}). The underlying loop calls
     * {@code font.width()} once per removed character; without the cache
     * every long-named profile pays it every frame.
     */
    private String fitName(String raw) {
        String cached = nameFitCache.get(raw);
        if (cached != null) return cached;
        String fitted = AuroraFontRenderer.ellipsize(this.font, raw, NAME_W - 6, 1);
        nameFitCache.put(raw, fitted);
        return fitted;
    }

    private void renderRow(GuiGraphics ctx, int x, int y, int w,
                            String name, int index, String active,
                            int mouseX, int mouseY) {
        boolean isActive = name.equals(active);
        boolean isDefault = "default".equals(name);
        float radius = ThemeManager.current().roundness().radiusSmall();

        // The row surface (shadow + depressed glass) was painted in the
        // glass pass, under the dim — see renderRowSurface. Only the flat
        // fallback is drawn here, for a row whose glass declined: the
        // opacity-tracked surface + hairline border, identical for every
        // row (active included — selection is the Active badge alone).
        if (!rowGlassDrawn.getOrDefault(name, false)) {
            RenderUtil.drawRoundedRectAA(ctx, x, y, w, ROW_H, radius,
                    ThemeManager.surfaceColor(ThemeToken.SURFACE));
            RenderUtil.drawRoundedOutlineAA(ctx, x, y, w, ROW_H, radius, 1.0f,
                    ThemeManager.color(ThemeToken.BORDER));
        }

        int cx = x + ROW_INSET;

        // Active badge — accent chip with the contrast-checked on-accent text.
        if (isActive) {
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
                commitAllEdits();
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
            commitAllEdits();
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
        int fieldX = listX + ROW_INSET + ACTIVE_BADGE_W + CONTROL_GAP;
        if (creatingNew && createField != null) {
            int rowY = LIST_TOP - (int) scroll.current();
            createField.setX(fieldX);
            createField.setY(rowY + (ROW_H - 16) / 2);
            return createField;
        }
        if (nameField != null && editingIndex >= 0 && editingIndex < profiles.size()) {
            int rowY = LIST_TOP - (int) scroll.current()
                    + (creatingNew ? ROW_H + ROW_GAP : 0)
                    + editingIndex * (ROW_H + ROW_GAP);
            if (rowY >= LIST_TOP - ROW_H && rowY < listClipBottom()) {
                nameField.setX(fieldX);
                nameField.setY(rowY + (ROW_H - 16) / 2);
                return nameField;
            }
        }
        return null;
    }

    private void renderCreateRow(GuiGraphics ctx, int x, int y, int w,
                                  int mouseX, int mouseY) {
        float radius = ThemeManager.current().roundness().radiusSmall();
        // The create editor is a neutral card identical to the other rows
        // (the hint text and the Create button signal the open editor; no
        // accent on the container surface in either path). Its surface was
        // painted in the glass pass; only the flat fallback is drawn here.
        if (!createRowGlassDrawn) {
            RenderUtil.drawRoundedRectAA(ctx, x, y, w, ROW_H, radius,
                    ThemeManager.surfaceColor(ThemeToken.SURFACE));
            RenderUtil.drawRoundedOutlineAA(ctx, x, y, w, ROW_H, radius, 1.0f,
                    ThemeManager.color(ThemeToken.BORDER));
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
        float radius = ThemeManager.current().roundness().radiusSmall();
        // Soft glow — structural black.
        for (int i = 1; i <= 6; i++) {
            int shadowAlpha = Math.max(0, 30 - i * 5);
            RenderUtil.drawRoundedOutlineAA(ctx, x - i, y - i, w + i * 2, ROW_H + i * 2, radius + i, 1.0f, (shadowAlpha << 24));
        }
        return GlassSurface.container(ctx, x, y, w, ROW_H, radius);
    }

    /**
     * Vertical band the row list is scissored to at render time (both
     * enableScissor calls in {@link #render}). Hit-testing clamps row
     * geometry to this same band so rows (or row parts) that are clipped
     * away are never clickable — the render clip is the source of truth.
     */
    private int listClipTop() { return LIST_TOP - 2; }
    private int listClipBottom() { return LIST_TOP + (this.height - LIST_TOP - LIST_BOTTOM_PAD); }

    /** Total scrollable overflow of the row list (create row included when open). */
    private double maxScroll() {
        List<String> profiles = ProfileManager.getInstance().listProfileNames();
        int total = (profiles.size() + (creatingNew ? 1 : 0)) * (ROW_H + ROW_GAP);
        return Math.max(0, total - (this.height - LIST_TOP - LIST_BOTTOM_PAD));
    }

    /** The thumb's current top/height in screen coordinates (int-truncated for painting). */
    private int thumbHeightPx(double maxScroll, int trackH) {
        return (int) scroll.thumbHeight(trackH, maxScroll, 24);
    }

    private int thumbYPx(double maxScroll, int trackH, int thumbH) {
        return listClipTop() + (int) ((trackH - thumbH) * scroll.ratio(maxScroll));
    }

    /**
     * The list's scrollbar thumb — the shared-look treatment this screen and
     * {@link WaypointManagerScreen} adopted together (R2 Part C): a 3px
     * capsule ({@code ON_OVERLAY} at 0x30 alpha, 0x55 on hover/drag — the
     * pack browser's hover-responsive idiom in these screens' token
     * vocabulary, since everything here draws over the dim) running down the
     * clip band, six pixels right of the list. Geometry and drag state come
     * from {@link SmoothScroll}; painting stays screen-owned like every
     * other thumb in the mod. Never glass — thumbs are opaque token
     * surfaces by the mod-wide conventions.
     */
    private void drawScrollbar(GuiGraphics ctx, int mouseX, int mouseY, int listX) {
        double maxScroll = maxScroll();
        if (maxScroll <= 0) return;
        int trackTop = listClipTop();
        int trackH = listClipBottom() - trackTop;
        int trackX = listX + LIST_W + 6;
        int thumbH = thumbHeightPx(maxScroll, trackH);
        int thumbY = thumbYPx(maxScroll, trackH, thumbH);
        boolean hover = mouseX >= trackX - 2 && mouseX <= trackX + 5
                && mouseY >= thumbY && mouseY <= thumbY + thumbH;
        int col = (hover || scroll.isDragging())
                ? ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_OVERLAY), 0x55)
                : ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_OVERLAY), 0x30);
        RenderUtil.drawRoundedRectAA(ctx, trackX, thumbY, 3, thumbH, 2, col);
    }

    // ------------------------------------------------------------------
    //  Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent _ev, boolean _doubleClicked) {
        double mouseX = _ev.x();
        double mouseY = _ev.y();
        int button = _ev.button();
        if (button != 0) return super.mouseClicked(_ev, _doubleClicked);

        // Editors first.
        if (createField != null && createField.mouseClicked(_ev, _doubleClicked)) return true;
        if (nameField != null && nameField.mouseClicked(_ev, _doubleClicked)) return true;

        int listX = (this.width - LIST_W) / 2;
        List<String> profiles = ProfileManager.getInstance().listProfileNames();
        String active = ProfileManager.getInstance().currentProfile();

        // "Create" confirm button in the create row (shared Button hit-tests
        // its own per-frame-laid-out bounds).
        if (creatingNew && createBtn != null && createBtn.mouseClicked(mouseX, mouseY, 0)) return true;

        int y = LIST_TOP - (int) scroll.current() + (creatingNew ? ROW_H + ROW_GAP : 0);
        int clipTop = listClipTop();
        int clipBot = listClipBottom();
        for (int i = 0; i < profiles.size(); i++) {
            String name = profiles.get(i);
            int rowTop = y;
            int rowBot = y + ROW_H;

            // Hit-testing agrees with the render scissor: only the VISIBLE
            // part of the row is clickable (rows scrolled above the clip —
            // or below it — are not), and only within the row's horizontal
            // extent. The X bound also fixes the switch-profile body click,
            // which previously had no horizontal check at all — a click at
            // this row's height anywhere on screen would switch profiles.
            if (mouseY >= Math.max(rowTop, clipTop) && mouseY < Math.min(rowBot, clipBot)
                    && mouseX >= listX && mouseX < listX + LIST_W) {
                // Shared row buttons see the click first.
                Button rowBtn;
                if ((rowBtn = dupBtns.get(name)) != null && rowBtn.mouseClicked(mouseX, mouseY, 0)) return true;
                if ((rowBtn = delBtns.get(name)) != null && rowBtn.mouseClicked(mouseX, mouseY, 0)) return true;

                int nameLeft = listX + ROW_INSET + ACTIVE_BADGE_W + CONTROL_GAP;
                if (mouseX >= nameLeft && mouseX < nameLeft + NAME_W) {
                    startEditing(i, name);
                    return true;
                }

                // Click on the row body (not on a control) → switch if not active.
                if (!name.equals(active)) {
                    commitAllEdits();
                    if (ProfileManager.getInstance().switchTo(name)) {
                        flash("Switched to " + name);
                    }
                    return true;
                }
            }
            y += ROW_H + ROW_GAP;
        }

        // Scrollbar thumb grab — checked before the fallthrough commit so
        // grabbing the scroll never cancels an open editor (wheel scrolling
        // doesn't either; the thumb is a list control, not an outside click).
        double maxScroll = maxScroll();
        if (maxScroll > 0) {
            int trackTop = listClipTop();
            int trackH = listClipBottom() - trackTop;
            int trackX = listX + LIST_W + 6;
            int thumbH = thumbHeightPx(maxScroll, trackH);
            int thumbY = thumbYPx(maxScroll, trackH, thumbH);
            if (mouseX >= trackX - 3 && mouseX <= trackX + 6
                    && mouseY >= thumbY - 4 && mouseY <= thumbY + thumbH + 4) {
                // Grab-where-clicked, with the grab offset clamped into the
                // thumb so clicking the generous hit-padding grabs the
                // nearest edge instead of jumping the thumb.
                int grab = (int) Math.max(0, Math.min(thumbH, mouseY - thumbY));
                scroll.beginThumbDrag(grab);
                return true;
            }
        }

        // Click outside any row/field commits/cancels editors.
        commitAllEdits();
        return super.mouseClicked(_ev, _doubleClicked);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent _ev, double dx, double dy) {
        if (scroll.isDragging() && _ev.button() == 0) {
            // Direct manipulation: the position pins to the cursor while
            // dragging and eases again on release.
            scroll.dragThumb(_ev.y(), listClipTop(), listClipBottom() - listClipTop(),
                    maxScroll(), 24, true);
            return true;
        }
        return super.mouseDragged(_ev, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent _ev) {
        if (scroll.isDragging()) {
            scroll.endThumbDrag();
            return true;
        }
        return super.mouseReleased(_ev);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll.wheel(vertical, 30, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent _kev) {
        int key = _kev.key();
        if (createField != null && createField.isFocused()) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                commitCreate();
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                cancelCreate();
                return true;
            }
            if (createField.keyPressed(_kev)) return true;
        }
        if (nameField != null && nameField.isFocused()) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                commitRename();
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                cancelRename();
                return true;
            }
            if (nameField.keyPressed(_kev)) return true;
        }
        return super.keyPressed(_kev);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent _ev) {
        if (createField != null && createField.isFocused() && createField.charTyped(_ev)) return true;
        if (nameField != null && nameField.isFocused() && nameField.charTyped(_ev)) return true;
        return super.charTyped(_ev);
    }

    // ------------------------------------------------------------------
    //  Editor lifecycle
    // ------------------------------------------------------------------

    private void beginCreate() {
        commitAllEdits();
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

    private void startEditing(int index, String currentName) {
        commitAllEdits();
        editingIndex = index;
        nameField = new EditBox(this.font, 0, 0, NAME_W, 16, Component.literal("Name"));
        nameField.setMaxLength(48);
        nameField.setValue(currentName);
        nameField.setFocused(true);
        nameField.moveCursorToEnd(false);
    }

    private void commitRename() {
        if (editingIndex < 0 || nameField == null) { cancelRename(); return; }
        List<String> profiles = ProfileManager.getInstance().listProfileNames();
        if (editingIndex >= profiles.size()) { cancelRename(); return; }
        String oldName = profiles.get(editingIndex);
        String v = nameField.getValue().trim();
        cancelRename();
        if (v.isEmpty() || v.equals(oldName)) return;
        if (ProfileManager.getInstance().rename(oldName, v)) {
            flash("Renamed → " + v);
        } else {
            flash("Could not rename (name taken?)");
        }
    }

    private void cancelRename() {
        editingIndex = -1;
        nameField = null;
    }

    private void commitAllEdits() {
        if (creatingNew) commitCreate();
        if (editingIndex >= 0) commitRename();
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

    private void flash(String text) {
        toast.flash(text, 1500L);
    }

    @Override
    public void onClose() {
        commitAllEdits();
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}