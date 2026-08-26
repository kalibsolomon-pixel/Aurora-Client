package com.aurora.client.screen;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.AuroraConfig.Waypoint;
import com.aurora.client.feature.impl.WaypointFeature;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.Button;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.ColorSwatch;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.ui.util.AuroraFontRenderer;
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
    private String flashText = null;
    private long   flashUntilMs = 0L;

    private final Screen parent;
    private double scrollY = 0;

    private int editingIndex = -1;
    private EditBox nameField;

    // Shared per-row widgets, keyed by waypoint index. Invalidated whenever
    // list membership changes — any add/remove changes the count, so a count
    // guard is sufficient (indices are otherwise stable; color edits mutate
    // the captured Waypoint object in place, keeping the getters live).
    private final Map<Integer, Button> rowCopyBtns = new HashMap<>();
    private final Map<Integer, Button> rowColorBtns = new HashMap<>();
    private final Map<Integer, Button> rowDeleteBtns = new HashMap<>();
    private final Map<Integer, ColorSwatch> rowSwatches = new HashMap<>();
    private int lastRowCount = -1;

    public WaypointManagerScreen(Screen parent) {
        super(Component.literal("Waypoints — " + WorldScope.current()));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Top toolbar: Drop / Done.
        int btnY = 16;
        int btnH = 22;
        this.addRenderableWidget(new ButtonWidget(
                16, btnY, 160, btnH,
                Component.literal("Drop at Player Position"),
                () -> {
                    WaypointFeature feat = WaypointFeature.get();
                    if (feat == null) return;
                    feat.dropAtPlayer("Waypoint", 0xFFFFAA00, false);
                    rebuildNameField();
                },
                true));

        this.addRenderableWidget(new ButtonWidget(
                this.width - 80 - 16, btnY, 80, btnH,
                Component.literal("Done"),
                this::onClose));
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // Themed overlay dim (token-driven; dark in both modes by design).
        ctx.fill(0, 0, this.width, this.height, ThemeManager.color(ThemeToken.OVERLAY_DIM));

        // Title.
        ctx.drawString(this.font, this.title, 16, 42, ThemeManager.color(ThemeToken.ON_OVERLAY), false);

        // List region.
        int listX = (this.width - LIST_W) / 2;
        int listH = this.height - LIST_TOP - LIST_BOTTOM_PAD;
        int listClipBottom = LIST_TOP + listH;

        ctx.enableScissor(listX - 4, LIST_TOP - 2, listX + LIST_W + 4, listClipBottom);

        List<Waypoint> all = currentList();
        // Row-widget cache hygiene: membership changed (add/remove) → rebuild.
        if (all.size() != lastRowCount) {
            rowCopyBtns.clear();
            rowColorBtns.clear();
            rowDeleteBtns.clear();
            rowSwatches.clear();
            lastRowCount = all.size();
        }
        if (all.isEmpty()) {
            ctx.drawString(this.font,
                    Component.literal("No waypoints in this world. Press Drop to add one."),
                    listX + 8, LIST_TOP + 10,
                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_OVERLAY), 0x88), false);
        }

        int y = LIST_TOP - (int) scrollY;
        for (int i = 0; i < all.size(); i++) {
            Waypoint w = all.get(i);
            if (y + ROW_H > LIST_TOP - ROW_H && y < listClipBottom) {
                renderRow(ctx, listX, y, LIST_W, w, i, mouseX, mouseY);
            }
            y += ROW_H + ROW_GAP;
        }

        ctx.disableScissor();

        super.render(ctx, mouseX, mouseY, delta);

        // Transient toast for clipboard copies. Fades over the last 250ms
        // of its lifetime so the visual confirmation isn't jarring.
        if (flashText != null && System.currentTimeMillis() < flashUntilMs) {
            long remaining = flashUntilMs - System.currentTimeMillis();
            int alpha = (int) Math.min(255, remaining > 250 ? 220 : remaining * 220 / 250);
            int textColor = (alpha << 24) | (ThemeManager.color(ThemeToken.ON_OVERLAY) & 0x00FFFFFF);
            int bgColor   = ThemeManager.withAlpha(ThemeManager.color(ThemeToken.OVERLAY_DIM), alpha * 160 / 255);
            int tw = this.font.width(flashText) + 16;
            int tx = (this.width - tw) / 2;
            int ty = this.height - 32;
            RenderUtil.drawRoundedRectAA(ctx, tx, ty, tw, 18, 4, bgColor);
            AuroraFontRenderer.drawCentered(ctx, this.font, Component.literal(flashText),
                    this.width / 2, ty + 5, textColor);
        }

        // Inline name editor sits on top of its row — render after super
        // so its caret and selection draw above the row swatches.
        if (nameField != null && editingIndex >= 0 && editingIndex < all.size()) {
            int rowY = LIST_TOP - (int) scrollY + editingIndex * (ROW_H + ROW_GAP);
            if (rowY >= LIST_TOP - ROW_H && rowY < listClipBottom) {
                int fieldX = listX + ROW_INSET + SWATCH_W + CONTROL_GAP;
                int fieldY = rowY + (ROW_H - 16) / 2;
                nameField.setX(fieldX);
                nameField.setY(fieldY);
                nameField.render(ctx, mouseX, mouseY, delta);
            }
        }
    }

    private void renderRow(GuiGraphics ctx, int x, int y, int w,
                            Waypoint wp, int index, int mouseX, int mouseY) {
        // Glow (hollow shadow so center stays translucent) — structural black.
        float radius = ThemeManager.current().roundness().radiusSmall();
        for (int i = 1; i <= 6; i++) {
            int shadowAlpha = Math.max(0, 30 - i * 5);
            RenderUtil.drawRoundedOutlineAA(ctx, x - i, y - i, w + i * 2, ROW_H + i * 2, radius + i, 1.0f, (shadowAlpha << 24) | 0x000000);
        }

        // Row surface — opacity-tracked themed surface + hairline border token.
        RenderUtil.drawRoundedRectAA(ctx, x, y, w, ROW_H, radius, ThemeManager.surfaceColor(ThemeToken.SURFACE));
        RenderUtil.drawRoundedOutlineAA(ctx, x, y, w, ROW_H, radius, 1.0f, ThemeManager.color(ThemeToken.BORDER));

        int cx = x + ROW_INSET;
        int cy = y + (ROW_H - SWATCH_W) / 2;

        // Color swatch — the shared ColorSwatch component (checkerboard so
        // alpha-carrying waypoint colors read correctly). Not clickable; the
        // Color button beside it owns the edit action.
        ColorSwatch swatch = rowSwatches.computeIfAbsent(index,
                k -> new ColorSwatch(() -> wp.color, null).checkerboard(true));
        swatch.layout(cx, cy, SWATCH_W, SWATCH_W);
        swatch.render(ctx, cx, cy, SWATCH_W, SWATCH_W, mouseX, mouseY);
        cx += SWATCH_W + CONTROL_GAP;

        // Name (or inline editor — when editing, the field is drawn from
        // the parent render() so the row body skips drawing the name text
        // for that index).
        if (index != editingIndex) {
            String name = wp.name == null ? "" : wp.name;
            if (this.font.width(name) > NAME_W - 6) {
                while (name.length() > 1 && this.font.width(name + "…") > NAME_W - 6) {
                    name = name.substring(0, name.length() - 1);
                }
                name = name + "…";
            }
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

        // Copy — the shared themed Button (single canonical implementation).
        Button copyBtn = rowCopyBtns.computeIfAbsent(index, k -> new Button("Copy", () -> {
            commitNameEdit();
            copyToClipboard(wp.x + " " + wp.y + " " + wp.z);
            flash("Copied: " + wp.x + " " + wp.y + " " + wp.z);
        }));
        copyBtn.layout(cx, y + 4, BTN_COPY_W, ROW_H - 8);
        copyBtn.render(ctx, cx, y + 4, BTN_COPY_W, ROW_H - 8, mouseX, mouseY);
        cx += BTN_COPY_W + CONTROL_GAP;

        // Color — shared themed Button; opens the shared color picker.
        Button colorBtn = rowColorBtns.computeIfAbsent(index, k -> new Button("Color", () -> {
            commitNameEdit();
            if (this.minecraft != null) {
                this.minecraft.setScreen(new ColorPickerScreen(WaypointManagerScreen.this,
                        "Waypoint Color", wp.color, argb -> {
                            List<Waypoint> live = currentList();
                            if (k < live.size()) {
                                live.get(k).color = argb;
                                WaypointFeature feat = WaypointFeature.get();
                                if (feat != null) feat.touch();
                            }
                        }));
            }
        }));
        colorBtn.layout(cx, y + 4, BTN_COLOR_W, ROW_H - 8);
        colorBtn.render(ctx, cx, y + 4, BTN_COLOR_W, ROW_H - 8, mouseX, mouseY);
        cx += BTN_COLOR_W + CONTROL_GAP;

        // Delete — shared Button in its destructive (semantic-error) variant.
        Button delBtn = rowDeleteBtns.computeIfAbsent(index, k -> new Button("Delete", () -> {
            commitNameEdit();
            WaypointFeature feat = WaypointFeature.get();
            if (feat != null) feat.remove(wp);
            rebuildNameField();
        }).destructive(true));
        delBtn.layout(cx, y + 4, BTN_DEL_W, ROW_H - 8);
        delBtn.render(ctx, cx, y + 4, BTN_DEL_W, ROW_H - 8, mouseX, mouseY);
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
        int y = LIST_TOP - (int) scrollY;
        for (int i = 0; i < all.size(); i++) {
            Waypoint wp = all.get(i);
            int rowTop = y;
            int rowBot = y + ROW_H;

            if (mouseY >= rowTop && mouseY < rowBot) {
                // Shared row buttons see the click first — each hit-tests its
                // own (per-frame-laid-out) bounds and runs its own action.
                Button rowBtn;
                if ((rowBtn = rowCopyBtns.get(i)) != null && rowBtn.mouseClicked(mouseX, mouseY, 0)) return true;
                if ((rowBtn = rowColorBtns.get(i)) != null && rowBtn.mouseClicked(mouseX, mouseY, 0)) return true;
                if ((rowBtn = rowDeleteBtns.get(i)) != null && rowBtn.mouseClicked(mouseX, mouseY, 0)) return true;

                // Name area → enter inline edit mode for this row.
                int nameLeft = listX + ROW_INSET + SWATCH_W + CONTROL_GAP;
                if (mouseX >= nameLeft && mouseX < nameLeft + NAME_W) {
                    startEditing(i, wp);
                    return true;
                }
            }
            y += ROW_H + ROW_GAP;
        }

        // Click outside any row commits / cancels editing.
        commitNameEdit();
        return super.mouseClicked(_ev, _doubleClicked);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        List<Waypoint> all = currentList();
        int totalH = all.size() * (ROW_H + ROW_GAP);
        double maxScroll = Math.max(0, totalH - (this.height - LIST_TOP - LIST_BOTTOM_PAD));
        scrollY -= vertical * 30;
        if (scrollY < 0) scrollY = 0;
        if (scrollY > maxScroll) scrollY = maxScroll;
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

    private void copyToClipboard(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.keyboardHandler == null) return;
        mc.keyboardHandler.setClipboard(text);
    }

    private void flash(String text) {
        flashText = text;
        flashUntilMs = System.currentTimeMillis() + 1500L;
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
