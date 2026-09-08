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
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.component.Toast;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.util.SmoothScroll;
import com.aurora.client.util.WorldScope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lists the current world's waypoints. Each row exposes: color swatch,
 * editable name, integer coordinates, color-edit button, delete button.
 * Top toolbar provides "Drop at current position" and "Done".
 *
 * <p>On the shared component framework: toolbar and row actions are the
 * canonical themed {@link Button} (delete uses its destructive variant), row
 * swatches are the shared {@link ColorSwatch}, row chrome resolves through
 * theme tokens, and the inline rename field is themed by the mod-wide
 * {@code EditBoxMixin} via the {@link ThemedScreen} marker.
 *
 * <p>Glass rollout: each row is its own RAISED glass panel — controls/rows
 * float above the world (there is no containing window panel on this
 * screen, so nothing takes the depressed treatment here). Row action
 * buttons are neutral raised glass via the shared Button; the Drop button —
 * the screen's single primary action — uses accent-STAINED glass. The color
 * swatch and the inline rename field stay opaque: the swatch displays user
 * color content at full fidelity (the picker-pad rule), and text-entry
 * fields are never glass. On renderer decline (no world, screenshot
 * suppression) the flat surface fills + borders return unchanged.
 */
public class WaypointManagerScreen extends Screen implements ThemedScreen {

    private static final int ROW_H        = 28;
    private static final int ROW_GAP      = 4;
    private static final int LIST_W       = 460;
    private static final int LIST_TOP     = 56;
    private static final int LIST_BOTTOM_PAD = 50;

    private static final int SWATCH_W     = 16;
    private static final int NAME_W       = 130;
    private static final int COORDS_W     = 96;
    private static final int BTN_COPY_W   = 48;
    private static final int BTN_COLOR_W  = 48;
    private static final int BTN_DEL_W    = 56;
    private static final int CONTROL_GAP  = 6;
    private static final int ROW_INSET    = 8;

    /** Toast-style transient feedback for clipboard copy. */
    private final Toast toast = new Toast();

    private final Screen parent;
    /**
     * List scrolling — new with R2 Part C (approved): this screen and
     * {@link ProfileManagerScreen} previously had hard-clamped direct
     * {@code scrollY} manipulation with no easing and no scrollbar. Now the
     * wheel feeds the shared component's target (τ = 60 ms, wheel step 30
     * unchanged) and a draggable, hover-responsive thumb shares one visual
     * treatment with the Profile screen: a 3px {@code ON_OVERLAY} capsule
     * at 0x30 alpha (0x55 hovered/dragged) beside the list.
     */
    private final SmoothScroll scroll = new SmoothScroll(60.0);

    private int editingIndex = -1;
    private EditBox nameField;

    // Shared per-row widgets, keyed by the waypoint INSTANCE — the same
    // identity WaypointFeature.remove() matches, and the objects in the
    // live list are stable across frames (edits mutate in place). Keying
    // by position (the old scheme) let a remove-then-add between frames
    // reuse a stale entry for a different waypoint; keying by identity and
    // reconciling against the live list every frame (see render) can never
    // point an action at the wrong object. Waypoint does not override
    // equals/hashCode, so HashMap compares by identity — exactly right.
    private final Map<Waypoint, Button> rowCopyBtns = new HashMap<>();
    private final Map<Waypoint, Button> rowColorBtns = new HashMap<>();
    private final Map<Waypoint, Button> rowDeleteBtns = new HashMap<>();
    private final Map<Waypoint, ColorSwatch> rowSwatches = new HashMap<>();
    /**
     * Fitted (ellipsis-truncated) row names, keyed by the raw name. The
     * truncation loop runs {@code font.width()} once per removed character
     * per row per frame otherwise — the same per-frame text-fit cost the
     * pack browser memoizes. Names are the cache key, so an in-place rename
     * simply misses once and repopulates; cleared with the row caches when
     * membership changes to keep the map bounded.
     */
    private final Map<String, String> nameFitCache = new HashMap<>();

    /** Toolbar buttons — kept so the glass pass can paint their surfaces pre-dim. */
    private ButtonWidget dropBtn;
    private ButtonWidget doneBtn;
    /** Which rows' glass drew this frame (glass pass) — consumed by renderRow's flat fallback. */
    private final Map<Waypoint, Boolean> rowGlassDrawn = new HashMap<>();

    // Row-control offsets from the row's left edge, shared by the glass pass
    // and the content pass so the two can never disagree on where a button is.
    private static final int COPY_DX  = ROW_INSET + SWATCH_W + CONTROL_GAP + NAME_W + CONTROL_GAP + COORDS_W + CONTROL_GAP;
    private static final int COLOR_DX = COPY_DX + BTN_COPY_W + CONTROL_GAP;
    private static final int DEL_DX   = COLOR_DX + BTN_COLOR_W + CONTROL_GAP;

    public WaypointManagerScreen(Screen parent) {
        super(Component.literal("Waypoints — " + WorldScope.current()));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Top toolbar: Drop / Done. Drop is the screen's single primary
        // action → accent-STAINED glass; Done is a plain action → neutral.
        int btnY = 16;
        int btnH = 22;
        dropBtn = this.addRenderableWidget(new ButtonWidget(
                16, btnY, 160, btnH,
                Component.literal("Drop at Player Position"),
                () -> {
                    WaypointFeature feat = WaypointFeature.get();
                    if (feat == null) return;
                    feat.dropAtPlayer("Waypoint", 0xFFFFAA00, false);
                    rebuildNameField();
                },
                true).glassStyle(Button.GlassStyle.STAINED));

        doneBtn = this.addRenderableWidget(new ButtonWidget(
                this.width - 80 - 16, btnY, 80, btnH,
                Component.literal("Done"),
                this::onClose).glassBackground(true));
    }

    /**
     * Glass rollout: with a live world behind the screen, skip vanilla's
     * background sandwich — the glass rows must sample the LIVE world. With
     * no level loaded the renderer declines anyway (its menu-context guard)
     * and the opaque fallback wants the vanilla backdrop as before.
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
     *       (raised controls; there is no containing window), their
     *       Copy/Color buttons, the toolbar's Drop/Done, and the inline
     *       rename field — paints its surface first.</li>
     *   <li><b>Dim</b>: {@link GlassSurface#overlayDim} veils the glass
     *       exactly as it veils the world, and from here on any glass call
     *       is reported as an ordering violation.</li>
     *   <li><b>Content</b>: title, row text/swatches, button labels, the
     *       caret — and the flat fallback for any surface whose glass
     *       declined.</li>
     * </ol>
     */
    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // Easing step first, so the glass pass and the content pass below
        // both read the same this-frame position.
        scroll.advance(maxScroll());
        int listX = (this.width - LIST_W) / 2;
        List<Waypoint> all = currentList();

        // Row-widget cache hygiene: reconcile against the LIVE list every
        // frame (the ProfileManagerScreen pattern) — entries for removed
        // waypoints drop immediately, new waypoints lazily create theirs.
        // The old count-only guard missed remove-then-add sequences that
        // kept the count identical. Runs before either pass so both see
        // the same button/swatch instances.
        boolean membershipChanged = rowCopyBtns.keySet().retainAll(all);
        membershipChanged |= rowColorBtns.keySet().retainAll(all);
        membershipChanged |= rowDeleteBtns.keySet().retainAll(all);
        membershipChanged |= rowSwatches.keySet().retainAll(all);
        if (membershipChanged) nameFitCache.clear();

        // ---- 1. Glass pass (before the dim) ----
        // Opened explicitly so each surface's rim finish is deferred past
        // the dim (painted by overlayDim); the tracked scissor lets the
        // deferred row rims keep the list clip.
        GlassSurface.beginGlassPass();
        rowGlassDrawn.clear();
        GlassSurface.enableScissor(ctx, listX - 4, listClipTop(), listX + LIST_W + 4, listClipBottom());
        int gy = LIST_TOP - (int) scroll.current();
        for (Waypoint w : all) {
            if (gy + ROW_H > LIST_TOP - ROW_H && gy < listClipBottom()) {
                renderRowGlassPass(ctx, listX, gy, LIST_W, w);
            }
            gy += ROW_H + ROW_GAP;
        }
        GlassSurface.disableScissor(ctx);
        if (dropBtn != null) dropBtn.renderGlassPass(ctx);
        if (doneBtn != null) doneBtn.renderGlassPass(ctx);
        if (layoutNameField(all, listX)) ((GlassEditBox) nameField).aurora$renderGlassPass(ctx);

        // ---- 2. Dim — end of the glass pass (token-driven; dark in both modes by design) ----
        GlassSurface.overlayDim(ctx, this.width, this.height);

        // ---- 3. Content ----
        ctx.drawString(this.font, this.title, 16, 42, ThemeManager.color(ThemeToken.ON_OVERLAY), false);

        ctx.enableScissor(listX - 4, listClipTop(), listX + LIST_W + 4, listClipBottom());
        if (all.isEmpty()) {
            ctx.drawString(this.font,
                    Component.literal("No waypoints in this world. Press Drop to add one."),
                    listX + 8, LIST_TOP + 10,
                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_OVERLAY), 0x88), false);
        }

        int y = LIST_TOP - (int) scroll.current();
        for (int i = 0; i < all.size(); i++) {
            Waypoint w = all.get(i);
            if (y + ROW_H > LIST_TOP - ROW_H && y < listClipBottom()) {
                renderRow(ctx, listX, y, LIST_W, w, i, mouseX, mouseY);
            }
            y += ROW_H + ROW_GAP;
        }

        ctx.disableScissor();

        // The list's scrollbar thumb — after the content scissor (it is
        // content, painted opaque over the dim like every other thumb).
        drawScrollbar(ctx, mouseX, mouseY, listX);

        super.render(ctx, mouseX, mouseY, delta);

        // Transient toast for clipboard copies. Fades over the last 250ms
        // of its lifetime so the visual confirmation isn't jarring.
        toast.render(ctx, this.font, this.width, this.height, 4);

        // Inline name editor sits on top of its row — render after super
        // so its caret and selection draw above the row swatches. Its
        // surface was painted in the glass pass; this draws content only.
        if (layoutNameField(all, listX)) {
            nameField.render(ctx, mouseX, mouseY, delta);
        }
    }

    /**
     * Positions the inline rename field over its row for this frame and
     * reports whether it is visible at all. Shared by the glass pass (which
     * paints the field's surface at that position) and the content pass
     * (which renders it), so the two can never disagree.
     */
    private boolean layoutNameField(List<Waypoint> all, int listX) {
        if (nameField == null || editingIndex < 0 || editingIndex >= all.size()) return false;
        int rowY = LIST_TOP - (int) scroll.current() + editingIndex * (ROW_H + ROW_GAP);
        if (rowY < LIST_TOP - ROW_H || rowY >= listClipBottom()) return false;
        nameField.setX(listX + ROW_INSET + SWATCH_W + CONTROL_GAP);
        nameField.setY(rowY + (ROW_H - 16) / 2);
        return true;
    }

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
    private void renderRowGlassPass(GuiGraphics ctx, int x, int y, int w, Waypoint wp) {
        float radius = ThemeManager.current().roundness().radiusSmall();
        // Glow (hollow shadow so center stays translucent) — structural black.
        for (int i = 1; i <= 6; i++) {
            int shadowAlpha = Math.max(0, 30 - i * 5);
            RenderUtil.drawRoundedOutlineAA(ctx, x - i, y - i, w + i * 2, ROW_H + i * 2, radius + i, 1.0f, (shadowAlpha << 24) | 0x000000);
        }
        rowGlassDrawn.put(wp, GlassSurface.control(ctx, x, y, w, ROW_H, radius));

        int by = y + 4;
        int bh = ROW_H - 8;
        Button b = copyBtn(wp);
        b.layout(x + COPY_DX, by, BTN_COPY_W, bh);
        b.renderGlassPass(ctx, x + COPY_DX, by, BTN_COPY_W, bh);
        b = colorBtn(wp);
        b.layout(x + COLOR_DX, by, BTN_COLOR_W, bh);
        b.renderGlassPass(ctx, x + COLOR_DX, by, BTN_COLOR_W, bh);
        b = deleteBtn(wp);
        b.layout(x + DEL_DX, by, BTN_DEL_W, bh);
        b.renderGlassPass(ctx, x + DEL_DX, by, BTN_DEL_W, bh);
    }

    private void renderRow(GuiGraphics ctx, int x, int y, int w,
                            Waypoint wp, int index, int mouseX, int mouseY) {
        float radius = ThemeManager.current().roundness().radiusSmall();

        // The row surface (shadow + glass) was painted in the glass pass,
        // under the dim — see renderRowGlassPass. Only the flat fallback
        // (surface fill + border) is drawn here, for a row whose glass
        // declined; it returns unchanged from the pre-glass look.
        if (!rowGlassDrawn.getOrDefault(wp, false)) {
            RenderUtil.drawRoundedRectAA(ctx, x, y, w, ROW_H, radius, ThemeManager.surfaceColor(ThemeToken.SURFACE));
            RenderUtil.drawRoundedOutlineAA(ctx, x, y, w, ROW_H, radius, 1.0f, ThemeManager.color(ThemeToken.BORDER));
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
        // the parent render() so the row body skips drawing the name text
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
        Button copyBtn = copyBtn(wp);
        copyBtn.layout(x + COPY_DX, y + 4, BTN_COPY_W, ROW_H - 8);
        copyBtn.render(ctx, x + COPY_DX, y + 4, BTN_COPY_W, ROW_H - 8, mouseX, mouseY);

        Button colorBtn = colorBtn(wp);
        colorBtn.layout(x + COLOR_DX, y + 4, BTN_COLOR_W, ROW_H - 8);
        colorBtn.render(ctx, x + COLOR_DX, y + 4, BTN_COLOR_W, ROW_H - 8, mouseX, mouseY);

        Button delBtn = deleteBtn(wp);
        delBtn.layout(x + DEL_DX, y + 4, BTN_DEL_W, ROW_H - 8);
        delBtn.render(ctx, x + DEL_DX, y + 4, BTN_DEL_W, ROW_H - 8, mouseX, mouseY);
    }

    /** Copy — the shared themed Button in neutral raised glass. */
    private Button copyBtn(Waypoint wp) {
        return rowCopyBtns.computeIfAbsent(wp, k -> new Button("Copy", () -> {
            commitNameEdit();
            copyToClipboard(k.x + " " + k.y + " " + k.z);
            flash("Copied: " + k.x + " " + k.y + " " + k.z);
        }).glassBackground(true));
    }

    /** Color — shared themed Button in neutral raised glass; opens the shared color picker. */
    private Button colorBtn(Waypoint wp) {
        return rowColorBtns.computeIfAbsent(wp, k -> new Button("Color", () -> {
            commitNameEdit();
            if (this.minecraft != null) {
                this.minecraft.setScreen(new ColorPickerScreen(WaypointManagerScreen.this,
                        "Waypoint Color", k.color, argb -> {
                            // k IS the waypoint instance (identity-keyed), so
                            // resolve it in the live list by identity — never
                            // by position, which could target a different
                            // waypoint after any add/remove.
                            List<Waypoint> live = currentList();
                            if (live.contains(k)) {
                                k.color = argb;
                                WaypointFeature feat = WaypointFeature.get();
                                if (feat != null) feat.touch();
                            }
                        }));
            }
        }).glassBackground(true));
    }

    /**
     * Delete — shared Button in its destructive (semantic-error) variant.
     * Destructive keeps the flat look by design (the shared Button skips
     * glass for destructive) — error semantics must not read as glass chrome.
     */
    private Button deleteBtn(Waypoint wp) {
        return rowDeleteBtns.computeIfAbsent(wp, k -> new Button("Delete", () -> {
            commitNameEdit();
            WaypointFeature feat = WaypointFeature.get();
            if (feat != null) feat.remove(k);
            rebuildNameField();
        }).destructive(true));
    }

    /**
     * Vertical band the row list is scissored to at render time (the
     * enableScissor call in {@link #render}). Hit-testing clamps row
     * geometry to this same band so rows (or row parts) that are clipped
     * away are never clickable — the render clip is the source of truth.
     */
    private int listClipTop() { return LIST_TOP - 2; }
    private int listClipBottom() { return LIST_TOP + (this.height - LIST_TOP - LIST_BOTTOM_PAD); }

    /** Total scrollable overflow of the row list. */
    private double maxScroll() {
        int totalH = currentList().size() * (ROW_H + ROW_GAP);
        return Math.max(0, totalH - (this.height - LIST_TOP - LIST_BOTTOM_PAD));
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
     * {@link ProfileManagerScreen} adopted together (R2 Part C): a 3px
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

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent _ev, boolean _doubleClicked) {
        double mouseX = _ev.x();
        double mouseY = _ev.y();
        int button = _ev.button();
        if (button != 0) return super.mouseClicked(_ev, _doubleClicked);

        // Let the inline name editor see the click first.
        if (nameField != null && nameField.mouseClicked(_ev, _doubleClicked)) return true;

        // Iterate visible rows.
        int listX = (this.width - LIST_W) / 2;
        List<Waypoint> all = currentList();
        int y = LIST_TOP - (int) scroll.current();
        int clipTop = listClipTop();
        int clipBot = listClipBottom();
        for (int i = 0; i < all.size(); i++) {
            Waypoint wp = all.get(i);
            int rowTop = y;
            int rowBot = y + ROW_H;

            // Hit-testing agrees with the render scissor: only the VISIBLE
            // part of the row is clickable, and only within the row's
            // horizontal extent (the name click below keeps its own tighter
            // bounds; the row buttons self-bound).
            if (mouseY >= Math.max(rowTop, clipTop) && mouseY < Math.min(rowBot, clipBot)
                    && mouseX >= listX && mouseX < listX + LIST_W) {
                // Shared row buttons see the click first — each hit-tests its
                // own (per-frame-laid-out) bounds and runs its own action.
                Button rowBtn;
                if ((rowBtn = rowCopyBtns.get(wp)) != null && rowBtn.mouseClicked(mouseX, mouseY, 0)) return true;
                if ((rowBtn = rowColorBtns.get(wp)) != null && rowBtn.mouseClicked(mouseX, mouseY, 0)) return true;
                if ((rowBtn = rowDeleteBtns.get(wp)) != null && rowBtn.mouseClicked(mouseX, mouseY, 0)) return true;

                // Name area → enter inline edit mode for this row.
                int nameLeft = listX + ROW_INSET + SWATCH_W + CONTROL_GAP;
                if (mouseX >= nameLeft && mouseX < nameLeft + NAME_W) {
                    startEditing(i, wp);
                    return true;
                }
            }
            y += ROW_H + ROW_GAP;
        }

        // Scrollbar thumb grab — checked before the fallthrough commit so
        // grabbing the scroll never cancels an open rename (wheel scrolling
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

        // Click outside any row commits / cancels editing.
        commitNameEdit();
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
        if (nameField != null && nameField.isFocused()) {
            int key = _kev.key();
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                commitNameEdit();
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                editingIndex = -1;
                nameField = null;
                return true;
            }
            if (nameField.keyPressed(_kev)) return true;
        }
        return super.keyPressed(_kev);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent _ev) {
        if (nameField != null && nameField.isFocused()) {
            if (nameField.charTyped(_ev)) return true;
        }
        return super.charTyped(_ev);
    }

    private void startEditing(int index, Waypoint wp) {
        commitNameEdit();
        editingIndex = index;
        nameField = new EditBox(this.font, 0, 0, NAME_W, 16, Component.literal("Name"));
        nameField.setMaxLength(48);
        nameField.setValue(wp.name == null ? "" : wp.name);
        nameField.setFocused(true);
        nameField.moveCursorToEnd(false);
    }

    private void commitNameEdit() {
        if (editingIndex < 0 || nameField == null) return;
        List<Waypoint> all = currentList();
        if (editingIndex < all.size()) {
            String v = nameField.getValue().trim();
            if (!v.isEmpty()) {
                all.get(editingIndex).name = v;
                WaypointFeature feat = WaypointFeature.get();
                if (feat != null) feat.touch();
            }
        }
        editingIndex = -1;
        nameField = null;
    }

    private void rebuildNameField() {
        if (editingIndex >= currentList().size()) {
            editingIndex = -1;
            nameField = null;
        }
    }

    private static List<Waypoint> currentList() {
        WaypointFeature feat = WaypointFeature.get();
        if (feat == null) return java.util.Collections.emptyList();
        return feat.currentWorldWaypoints();
    }

    /**
     * Ellipsis-truncates a row name to the name column, memoized per raw
     * name (see {@link #nameFitCache}). The underlying loop calls
     * {@code font.width()} once per removed character; without the cache a
     * list of long-named waypoints pays it every row, every frame.
     */
    private String fitName(String raw) {
        String cached = nameFitCache.get(raw);
        if (cached != null) return cached;
        String fitted = AuroraFontRenderer.ellipsize(this.font, raw, NAME_W - 6, 1);
        nameFitCache.put(raw, fitted);
        return fitted;
    }

    private void copyToClipboard(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.keyboardHandler == null) return;
        mc.keyboardHandler.setClipboard(text);
    }

    private void flash(String text) {
        toast.flash(text, 1500L);
    }

    @Override
    public void onClose() {
        commitNameEdit();
        AuroraConfig.save();
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
