package com.aurora.client.screen;
import com.aurora.client.ui.util.AuroraFontRenderer;

import com.aurora.client.config.profile.ProfileManager;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.Button;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.util.RenderUtil;
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
    private String flashText = null;
    private long flashUntilMs = 0L;

    /** Inline rename editor state. {@code -1} = not editing. */
    private int editingIndex = -1;
    private EditBox nameField;

    /**
     * Pending create: when the user clicks "New", we show an empty editor
     * at the top of the list rather than mid-list.
     */
    private boolean creatingNew = false;
    private EditBox createField;

    private double scrollY = 0;

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

    // Glass pilot: which rows' glass drew this frame (pre-dim pass), so the
    // flat row body can be skipped for exactly those rows.
    private final Map<String, Boolean> rowGlassDrawn = new HashMap<>();
    private boolean createRowGlassDrawn = false;

    public ProfileManagerScreen(Screen parent) {
        super(Component.literal("Profiles"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int btnY = 16;
        int btnH = 22;

        this.addRenderableWidget(new ButtonWidget(
                16, btnY, 120, btnH,
                Component.literal("New Profile"),
                this::beginCreate).glassBackground(true));

        this.addRenderableWidget(new ButtonWidget(
                this.width - 80 - 16, btnY, 80, btnH,
                Component.literal("Done"),
                this::onClose).glassBackground(true));
    }

    // ------------------------------------------------------------------
    //  Render
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        int listX = (this.width - LIST_W) / 2;
        int listH = this.height - LIST_TOP - LIST_BOTTOM_PAD;
        int listClipBottom = LIST_TOP + listH;

        // ---- Glass pilot: row glass BEFORE the dim ----
        // Same layering contract as every glass surface so far: the deferred
        // OVERLAY_DIM fill must veil the glass like it veils the world —
        // otherwise the blurred backdrop inside a row would read brighter
        // than the same world outside it. The per-row results are remembered
        // so renderRow/renderCreateRow skip their flat fills for exactly the
        // rows whose glass drew this frame (declines fall back per row).
        rowGlassDrawn.clear();
        createRowGlassDrawn = false;
        if (liveWorldBackdrop()) {
            List<String> profiles = ProfileManager.getInstance().listProfileNames();
            ctx.enableScissor(listX - 4, LIST_TOP - 2, listX + LIST_W + 4, listClipBottom);
            int gy = LIST_TOP - (int) scrollY;
            if (creatingNew && createField != null) {
                createRowGlassDrawn = drawRowGlass(ctx, listX, gy, LIST_W);
                gy += ROW_H + ROW_GAP;
            }
            for (String name : profiles) {
                rowGlassDrawn.put(name, drawRowGlass(ctx, listX, gy, LIST_W));
                gy += ROW_H + ROW_GAP;
            }
            ctx.disableScissor();
        }

        // Themed overlay dim (token-driven; dark in both modes by design).
        ctx.fill(0, 0, this.width, this.height, ThemeManager.color(ThemeToken.OVERLAY_DIM));

        // Title.
        ctx.drawString(this.font, this.title, 16, 42, ThemeManager.color(ThemeToken.ON_OVERLAY), false);

        ctx.enableScissor(listX - 4, LIST_TOP - 2, listX + LIST_W + 4, listClipBottom);

        List<String> profiles = ProfileManager.getInstance().listProfileNames();
        String active = ProfileManager.getInstance().currentProfile();
        // Row-widget cache hygiene: drop buttons for profiles that no longer exist.
        dupBtns.keySet().retainAll(profiles);
        delBtns.keySet().retainAll(profiles);

        if (profiles.isEmpty()) {
            ctx.drawString(this.font,
                    Component.literal("No profiles. Press \"New Profile\" to create one."),
                    listX + 8, LIST_TOP + 10,
                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_OVERLAY), 0x88), false);
        }

        // Inline "new profile" editor row at the top.
        int y = LIST_TOP - (int) scrollY;
        if (creatingNew && createField != null) {
            renderCreateRow(ctx, listX, y, LIST_W, mouseX, mouseY);
            y += ROW_H + ROW_GAP;
        }

        for (int i = 0; i < profiles.size(); i++) {
            String name = profiles.get(i);
            if (y + ROW_H > LIST_TOP - ROW_H && y < listClipBottom) {
                renderRow(ctx, listX, y, LIST_W, name, i, active, mouseX, mouseY);
            }
            y += ROW_H + ROW_GAP;
        }

        ctx.disableScissor();

        super.render(ctx, mouseX, mouseY, delta);

        // Inline editors render on top so the caret draws above row fills.
        if (creatingNew && createField != null) {
            int rowY = LIST_TOP - (int) scrollY;
            int fieldX = listX + ROW_INSET + ACTIVE_BADGE_W + CONTROL_GAP;
            int fieldY = rowY + (ROW_H - 16) / 2;
            createField.setX(fieldX);
            createField.setY(fieldY);
            createField.render(ctx, mouseX, mouseY, delta);
        } else if (nameField != null && editingIndex >= 0 && editingIndex < profiles.size()) {
            int rowY = LIST_TOP - (int) scrollY
                    + (creatingNew ? ROW_H + ROW_GAP : 0)
                    + editingIndex * (ROW_H + ROW_GAP);
            if (rowY >= LIST_TOP - ROW_H && rowY < listClipBottom) {
                int fieldX = listX + ROW_INSET + ACTIVE_BADGE_W + CONTROL_GAP;
                int fieldY = rowY + (ROW_H - 16) / 2;
                nameField.setX(fieldX);
                nameField.setY(fieldY);
                nameField.render(ctx, mouseX, mouseY, delta);
            }
        }

        // Toast.
        if (flashText != null && System.currentTimeMillis() < flashUntilMs) {
            long remaining = flashUntilMs - System.currentTimeMillis();
            int alpha = (int) Math.min(255, remaining > 250 ? 220 : remaining * 220 / 250);
            int textColor = (alpha << 24) | (ThemeManager.color(ThemeToken.ON_OVERLAY) & 0x00FFFFFF);
            int bgColor = ThemeManager.withAlpha(ThemeManager.color(ThemeToken.OVERLAY_DIM), alpha * 160 / 255);
            int tw = this.font.width(flashText) + 16;
            int tx = (this.width - tw) / 2;
            int ty = this.height - 32;
            RenderUtil.drawRoundedRectAA(ctx, tx, ty, tw, 18, 4, bgColor);
            AuroraFontRenderer.drawCentered(ctx, this.font, Component.literal(flashText),
                    this.width / 2, ty + 5, textColor);
        }
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
        String fitted = raw;
        if (this.font.width(fitted) > NAME_W - 6) {
            String ell = "…";
            while (fitted.length() > 1 && this.font.width(fitted + ell) > NAME_W - 6) {
                fitted = fitted.substring(0, fitted.length() - 1);
            }
            fitted = fitted + ell;
        }
        nameFitCache.put(raw, fitted);
        return fitted;
    }

    private void renderRow(GuiGraphics ctx, int x, int y, int w,
                            String name, int index, String active,
                            int mouseX, int mouseY) {
        boolean isActive = name.equals(active);
        boolean isDefault = "default".equals(name);
        float radius = ThemeManager.current().roundness().radiusSmall();

        // Soft glow — structural black.
        for (int i = 1; i <= 6; i++) {
            int shadowAlpha = Math.max(0, 30 - i * 5);
            RenderUtil.drawRoundedOutlineAA(ctx, x - i, y - i, w + i * 2, ROW_H + i * 2, radius + i, 1.0f, (shadowAlpha << 24));
        }

        // Glass pilot: each profile row is this screen's CONTAINER, so it
        // takes the same treatment as every other screen's window — DEPRESSED
        // neutral glass tinted only by WINDOW_FILL. Accent never tints the
        // row surface: a whole-row stain read as an accent-tinted container
        // (flagged twice), so selection is carried solely by the compact
        // accent Active badge below. The glass itself drew in the pre-dim
        // pass (see render); this applies the one neutral tint, or the
        // complete flat row when that row's glass declined.
        boolean rowGlass = rowGlassDrawn.getOrDefault(name, false);
        if (rowGlass) {
            RenderUtil.drawRoundedRectAA(ctx, x, y, w, ROW_H, radius,
                    ThemeManager.color(ThemeToken.WINDOW_FILL));
            BlurPanelRenderer.drawRimFinish(ctx, x, y, w, ROW_H, radius);
        } else {
            // Row body — identical for every row (active included): the
            // opacity-tracked surface + hairline border.
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

        // Duplicate — the shared themed Button (glass pilot: neutral raised
        // glass — a plain action, never a selected state).
        Button dupBtn = dupBtns.computeIfAbsent(name, k -> new Button("Duplicate", () -> {
            commitAllEdits();
            String dupName = uniqueName(k + " Copy");
            if (ProfileManager.getInstance().duplicate(k, dupName)) {
                flash("Duplicated → " + dupName);
            }
        }).glassBackground(true));
        dupBtn.layout(cx, y + 4, BTN_DUP_W, ROW_H - 8);
        dupBtn.render(ctx, cx, y + 4, BTN_DUP_W, ROW_H - 8, mouseX, mouseY);
        cx += BTN_DUP_W + CONTROL_GAP;

        // Delete — shared Button, destructive variant; hidden for the
        // default + active profiles (non-deletable).
        if (!isDefault && !isActive) {
            Button dBtn = delBtns.computeIfAbsent(name, k -> new Button("Delete", () -> {
                commitAllEdits();
                if (ProfileManager.getInstance().delete(k)) {
                    flash("Deleted " + k);
                }
            }).destructive(true));
            dBtn.layout(cx, y + 4, BTN_DEL_W, ROW_H - 8);
            dBtn.render(ctx, cx, y + 4, BTN_DEL_W, ROW_H - 8, mouseX, mouseY);
        }
    }

    private void renderCreateRow(GuiGraphics ctx, int x, int y, int w,
                                  int mouseX, int mouseY) {
        float radius = ThemeManager.current().roundness().radiusSmall();
        // Glass pilot: the create editor is a neutral card, identical to the
        // other rows (the hint text and the Create button signal the open
        // editor; no accent on the container surface in either path).
        if (createRowGlassDrawn) {
            RenderUtil.drawRoundedRectAA(ctx, x, y, w, ROW_H, radius,
                    ThemeManager.color(ThemeToken.WINDOW_FILL));
        } else {
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

        // "Create" confirm button where Duplicate usually sits — neutral
        // raised glass like every other action button on this screen.
        int bx = x + ROW_INSET + ACTIVE_BADGE_W + CONTROL_GAP + NAME_W + CONTROL_GAP;
        if (createBtn == null) {
            createBtn = new Button("Create", this::commitCreate).glassBackground(true);
        }
        createBtn.layout(bx, y + 4, BTN_DUP_W, ROW_H - 8);
        createBtn.render(ctx, bx, y + 4, BTN_DUP_W, ROW_H - 8, mouseX, mouseY);
    }

    /**
     * Glass pilot — one DEPRESSED-glass row surface, drawn in the pre-dim
     * pass. Rows are this screen's containers, so they take the window
     * treatment (depressed lighting, WINDOW_FILL tint) rather than the
     * raised control treatment; selection is never carried here at all —
     * the accent Active badge in {@link #renderRow} marks it. Returns
     * whether the glass drew (false = caller's flat fallback).
     */
    private boolean drawRowGlass(GuiGraphics ctx, int x, int y, int w) {
        float radius = ThemeManager.current().roundness().radiusSmall();
        return BlurPanelRenderer.renderPanel(ctx, x, y, w, ROW_H, radius,
                BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX,
                BlurPanelRenderer.Lighting.depressed());
    }

    /**
     * True when the main render target holds a live world — i.e. the glass
     * capture source is valid. Mirrors {@code BlurPanelRenderer}'s own
     * menu-context guard so this screen never asks for glass in a context
     * where it cannot engage (the flat rows render unchanged there).
     */
    private boolean liveWorldBackdrop() {
        return this.minecraft != null && this.minecraft.level != null;
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

        int y = LIST_TOP - (int) scrollY + (creatingNew ? ROW_H + ROW_GAP : 0);
        for (int i = 0; i < profiles.size(); i++) {
            String name = profiles.get(i);
            int rowTop = y;
            int rowBot = y + ROW_H;

            if (mouseY >= rowTop && mouseY < rowBot) {
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

        // Click outside any row/field commits/cancels editors.
        commitAllEdits();
        return super.mouseClicked(_ev, _doubleClicked);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        List<String> profiles = ProfileManager.getInstance().listProfileNames();
        int total = (profiles.size() + (creatingNew ? 1 : 0)) * (ROW_H + ROW_GAP);
        double maxScroll = Math.max(0, total - (this.height - LIST_TOP - LIST_BOTTOM_PAD));
        scrollY -= vertical * 30;
        if (scrollY < 0) scrollY = 0;
        if (scrollY > maxScroll) scrollY = maxScroll;
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
        scrollY = 0;
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
        flashText = text;
        flashUntilMs = System.currentTimeMillis() + 1500L;
    }

    @Override
    public void onClose() {
        commitAllEdits();
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}