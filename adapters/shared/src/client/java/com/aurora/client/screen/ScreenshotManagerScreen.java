package com.aurora.client.screen;

import com.aurora.client.adapter.NativeUi;
import com.aurora.client.screenshot.ScreenshotIO;
import com.aurora.client.screenshot.ScreenshotIndex;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.Button;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.component.ScrollbarChrome;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.component.Toast;
import com.aurora.client.ui.interaction.ArmedAction;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.FieldMessage;
import com.aurora.client.ui.util.MaterialIconRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.ui.util.UiDensity;
import com.aurora.client.util.AuroraTheme;
import com.aurora.client.util.SmoothScroll;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * In-game Screenshot Manager: browse {@code screenshots/} with async-decoded
 * thumbnails, search, inspect full previews, rename, delete (with 3 s
 * press-again confirmation), copy image to system clipboard, and perform
 * non-destructive drag-to-crop saves.
 */
public class ScreenshotManagerScreen extends Screen implements ThemedScreen {
    @Override public boolean parentGlassOnly() { return true; }

    private static final int CARD_GAP = 8;
    private static final int CARD_MIN_W = 132;
    private static final int CARD_THUMB_H = 74;
    private static final int CARD_FOOTER_H = 26;
    private static final int CARD_H = CARD_THUMB_H + CARD_FOOTER_H;

    private final Screen parent;
    private final SmoothScroll scroll = new SmoothScroll(60.0).boundedMotion();
    private final Toast toast = new Toast();
    private final ArmedAction deleteConfirm = new ArmedAction();

    private Path screenshotsDir;
    private List<ScreenshotIndex.Entry> allEntries = List.of();
    private List<ScreenshotIndex.Entry> filteredEntries = List.of();
    private String lastFilterQuery = null;

    // Detail mode state (null = grid view)
    private ScreenshotIndex.Entry selected;
    private boolean cropMode;
    private boolean draggingCrop;
    private float cropX0, cropY0, cropX1, cropY1;
    private int previewX, previewY, previewW, previewH;

    // Frame geometry
    private int panelX, panelY, panelW, panelH;
    private int bodyX, bodyY, bodyW, bodyH;

    // Widgets
    private EditBox searchField;
    private EditBox renameField;
    private String renameError;
    private ButtonWidget deleteBtn;
    private boolean deleteBtnArmed;
    private ButtonWidget cropBtn;
    private ButtonWidget saveCropBtn;

    public ScreenshotManagerScreen(Screen parent) {
        super(Component.literal("Screenshots"));
        this.parent = parent;
    }

    private Path dir() {
        if (screenshotsDir == null && this.minecraft != null) {
            screenshotsDir = this.minecraft.gameDirectory.toPath().resolve("screenshots");
        }
        return screenshotsDir;
    }

    @Override
    protected void init() {
        var frame = UiDensity.window(this.width, this.height);
        panelX = frame.x();
        panelY = frame.y();
        panelW = frame.width();
        panelH = frame.height();

        int pad = UiDensity.TITLE_X;
        bodyX = panelX + pad;
        bodyW = panelW - pad * 2;

        if (allEntries.isEmpty() && lastFilterQuery == null) {
            rescanAsync(null);
        }

        int bh = UiDensity.HEADER_BUTTON_H;
        int doneW = UiDensity.HEADER_BUTTON_W;
        int doneX = panelX + panelW - UiDensity.FRAME_EDGE - doneW;
        int headerBtnY = panelY + UiDensity.HEADER_TOP;

        if (selected == null) {
            buildGridWidgets(headerBtnY, bh, doneX, doneW);
        } else {
            buildDetailWidgets(headerBtnY, bh, doneX, doneW);
        }
    }

    private void buildGridWidgets(int headerBtnY, int bh, int doneX, int doneW) {
        int folderW = Math.max(76, this.font.width("Open folder") + 22);
        this.addRenderableWidget(ButtonWidget.semantic(doneX - folderW - 6, headerBtnY, folderW, bh,
                Component.literal("Open folder"),
                "Opens Minecraft's screenshots folder in your file manager.",
                this::openScreenshotsFolder).glassBackground(true));
        this.addRenderableWidget(ButtonWidget.semantic(doneX, headerBtnY, doneW, bh,
                Component.literal("Done"), "Returns to the previous screen.", this::onClose)
                .glassStyle(Button.GlassStyle.STAINED).primary(true));

        int toolsY = panelY + UiDensity.HEADER + 4;
        String prevQuery = searchField != null ? searchField.getValue() : "";
        searchField = new EditBox(this.font, bodyX, toolsY, bodyW, bh, Component.literal("Search screenshots"));
        searchField.setMaxLength(80);
        searchField.setHint(Component.literal("Search screenshots"));
        searchField.setValue(prevQuery);
        searchField.setResponder(q -> {
            lastFilterQuery = null;
            scroll.set(0);
            refreshFiltered();
        });
        this.addRenderableWidget(searchField);

        bodyY = toolsY + bh + 8;
        bodyH = Math.max(40, panelY + panelH - UiDensity.FRAME_EDGE - bodyY);
        renameField = null;
        deleteBtn = null;
        cropBtn = null;
        saveCropBtn = null;
    }

    private void buildDetailWidgets(int headerBtnY, int bh, int doneX, int doneW) {
        int backW = Math.max(60, this.font.width("Back") + 22);
        this.addRenderableWidget(ButtonWidget.semantic(panelX + UiDensity.TITLE_X, headerBtnY, backW, bh,
                Component.literal("Back"), "Returns to the screenshots grid.", this::leaveDetail)
                .glassBackground(true));
        this.addRenderableWidget(ButtonWidget.semantic(doneX, headerBtnY, doneW, bh,
                Component.literal("Done"), "Returns to the previous screen.", this::onClose)
                .glassStyle(Button.GlassStyle.STAINED).primary(true));

        int renameY = panelY + UiDensity.HEADER + 4;
        int applyW = 64;
        int fieldW = Math.max(80, bodyW - applyW - 6);
        renameField = new EditBox(this.font, bodyX, renameY, fieldW, bh, Component.literal("Rename screenshot"));
        renameField.setMaxLength(120);
        renameField.setValue(selected.displayName());
        renameField.setResponder(v -> renameError = ScreenshotIndex.validateRename(v, dir(), selected.path()));
        this.addRenderableWidget(renameField);
        this.addRenderableWidget(ButtonWidget.semantic(bodyX + fieldW + 6, renameY, applyW, bh,
                Component.literal("Rename"), "Renames this screenshot file.", this::commitRename)
                .glassBackground(true));

        int actionY = panelY + panelH - UiDensity.FRAME_EDGE - bh;
        int gap = 6;
        int count = cropMode ? 5 : 4;
        int btnW = (bodyW - gap * (count - 1)) / count;
        int bx = bodyX;

        this.addRenderableWidget(ButtonWidget.semantic(bx, actionY, btnW, bh,
                Component.literal("Copy"), "Copies this screenshot image to your system clipboard.", this::copySelected)
                .glassBackground(true));
        bx += btnW + gap;

        cropBtn = this.addRenderableWidget(ButtonWidget.semantic(bx, actionY, btnW, bh,
                Component.literal(cropMode ? "Cancel crop" : "Crop"),
                "Drag on the preview image to select a region and save a cropped copy.",
                this::toggleCropMode).glassBackground(true));
        bx += btnW + gap;

        saveCropBtn = null;
        if (cropMode) {
            saveCropBtn = this.addRenderableWidget(ButtonWidget.semantic(bx, actionY, btnW, bh,
                    Component.literal("Save crop"),
                    "Saves the selected region as a new PNG file alongside the original.",
                    this::saveCrop).glassStyle(Button.GlassStyle.STAINED).primary(true));
            bx += btnW + gap;
        }

        this.addRenderableWidget(ButtonWidget.semantic(bx, actionY, btnW, bh,
                Component.literal("Open"), "Opens this screenshot in your system image viewer.",
                () -> { if (selected != null) NativeUi.openPath(selected.path()); })
                .glassBackground(true));
        bx += btnW + gap;

        deleteBtn = this.addRenderableWidget(ButtonWidget.semantic(bx, actionY, bodyX + bodyW - bx, bh,
                Component.literal(deleteConfirm.isArmed() ? "Confirm delete" : "Delete"),
                "Deletes this screenshot permanently. Press again to confirm.",
                this::deleteSelected).destructive(true));
        deleteBtnArmed = deleteConfirm.isArmed();

        bodyY = renameY + bh + 12;
        bodyH = Math.max(40, actionY - 8 - bodyY);
        searchField = null;
    }

    private void rescanAsync(Path selectAfter) {
        Path folder = dir();
        CompletableFuture.supplyAsync(() -> ScreenshotIndex.scan(folder)).thenAccept(list -> {
            if (this.minecraft == null) return;
            this.minecraft.execute(() -> {
                allEntries = list;
                lastFilterQuery = null;
                refreshFiltered();
                if (selectAfter != null) {
                    for (ScreenshotIndex.Entry e : allEntries) {
                        if (e.path().equals(selectAfter)) {
                            selected = e;
                            rebuildWidgets();
                            return;
                        }
                    }
                }
            });
        });
    }

    private void refreshFiltered() {
        String q = searchField != null ? searchField.getValue() : "";
        if (q.equals(lastFilterQuery)) return;
        lastFilterQuery = q;
        filteredEntries = ScreenshotIndex.filter(allEntries, q);
    }

    private void openDetail(ScreenshotIndex.Entry entry) {
        selected = entry;
        cropMode = false;
        draggingCrop = false;
        renameError = null;
        deleteConfirm.disarm();
        rebuildWidgets();
    }

    private void leaveDetail() {
        selected = null;
        cropMode = false;
        draggingCrop = false;
        renameError = null;
        deleteConfirm.disarm();
        rebuildWidgets();
    }

    private void openScreenshotsFolder() {
        Path folder = dir();
        if (folder == null) return;
        try { Files.createDirectories(folder); } catch (Exception ignored) {}
        NativeUi.openPath(folder);
    }

    private void commitRename() {
        if (selected == null || renameField == null) return;
        String raw = renameField.getValue();
        String problem = ScreenshotIndex.validateRename(raw, dir(), selected.path());
        if (problem != null) {
            renameError = problem;
            return;
        }
        try {
            Path renamed = ScreenshotIndex.rename(selected.path(), raw);
            renameError = null;
            toast.flash("Renamed → " + renamed.getFileName(), 1800L);
            rescanAsync(renamed);
        } catch (Exception e) {
            renameError = e.getMessage() != null ? e.getMessage() : "Could not rename";
        }
    }

    private void copySelected() {
        if (selected == null) return;
        ScreenshotIO.copyImageToClipboardAsync(selected.path()).thenAccept(msg -> {
            if (this.minecraft != null) this.minecraft.execute(() -> toast.flash(msg, 1800L));
        });
    }

    private void toggleCropMode() {
        cropMode = !cropMode;
        draggingCrop = false;
        if (cropMode) {
            cropX0 = 0.15f; cropY0 = 0.15f;
            cropX1 = 0.85f; cropY1 = 0.85f;
        }
        rebuildWidgets();
    }

    private void saveCrop() {
        if (selected == null || !cropMode) return;
        Path src = selected.path();
        float x0 = cropX0, y0 = cropY0, x1 = cropX1, y1 = cropY1;
        ScreenshotIO.cropAsync(src, x0, y0, x1, y1).whenComplete((out, err) -> {
            if (this.minecraft == null) return;
            this.minecraft.execute(() -> {
                if (err != null) {
                    toast.flash("Crop failed: " + err.getMessage(), 2200L);
                } else {
                    cropMode = false;
                    draggingCrop = false;
                    toast.flash("Saved " + out.getFileName(), 2000L);
                    rescanAsync(out);
                }
            });
        });
    }

    private void deleteSelected() {
        if (selected == null) return;
        if (deleteConfirm.press()) {
            Path victim = selected.path();
            try {
                if (ScreenshotIndex.delete(dir(), victim)) {
                    toast.flash("Deleted " + victim.getFileName(), 1800L);
                    leaveDetail();
                    rescanAsync(null);
                }
            } catch (Exception e) {
                toast.flash("Delete failed", 2000L);
            }
        }
        syncDeleteButton();
    }

    private void syncDeleteButton() {
        if (deleteBtn == null) return;
        boolean armed = deleteConfirm.isArmed();
        if (armed == deleteBtnArmed) return;
        deleteBtnArmed = armed;
        deleteBtn.setMessage(Component.literal(armed ? "Confirm delete" : "Delete"));
    }

    private int columns() {
        return Math.max(1, (bodyW + CARD_GAP) / (CARD_MIN_W + CARD_GAP));
    }

    private int cardWidth() {
        int cols = columns();
        return (bodyW - CARD_GAP * (cols - 1)) / cols;
    }

    private int totalGridHeight() {
        if (filteredEntries.isEmpty()) return 0;
        int cols = columns();
        int rows = (filteredEntries.size() + cols - 1) / cols;
        return rows * CARD_H + Math.max(0, rows - 1) * CARD_GAP;
    }

    @Override
    public void onClose() {
        if (selected != null) {
            leaveDetail();
            return;
        }
        NativeUi.setScreen(this.minecraft, parent);
    }

    @Override
    public void removed() {
        super.removed();
        ScreenshotIO.retireAll();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (renameField != null && renameField.isFocused()
                && (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER)) {
            commitRename();
            return true;
        }
        if (deleteConfirm.isArmed() && key == InputConstants.KEY_ESCAPE) {
            deleteConfirm.disarm();
            syncDeleteButton();
            return true;
        }
        if (cropMode && key == InputConstants.KEY_ESCAPE) {
            toggleCropMode();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (selected == null && mouseX >= bodyX && mouseX <= bodyX + bodyW && mouseY >= bodyY && mouseY <= bodyY + bodyH) {
            int max = Math.max(0, totalGridHeight() - bodyH);
            scroll.wheel(vertical, 30.0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (deleteConfirm.isArmed() && (deleteBtn == null || !deleteBtn.isMouseOver(event.x(), event.y()))) {
            deleteConfirm.disarm();
            syncDeleteButton();
        }
        if (super.mouseClicked(event, doubleClick)) return true;

        if (selected == null) {
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                    && event.x() >= bodyX && event.x() < bodyX + bodyW
                    && event.y() >= bodyY && event.y() < bodyY + bodyH) {
                int cols = columns();
                int cw = cardWidth();
                int scrollOff = (int) Math.round(scroll.current());
                for (int i = 0; i < filteredEntries.size(); i++) {
                    int r = i / cols, c = i % cols;
                    int cx = bodyX + c * (cw + CARD_GAP);
                    int cy = bodyY + r * (CARD_H + CARD_GAP) - scrollOff;
                    if (event.x() >= cx && event.x() < cx + cw && event.y() >= cy && event.y() < cy + CARD_H) {
                        openDetail(filteredEntries.get(i));
                        return true;
                    }
                }
            }
        } else if (cropMode && event.button() == InputConstants.MOUSE_BUTTON_LEFT && previewW > 0 && previewH > 0) {
            if (event.x() >= previewX && event.x() <= previewX + previewW
                    && event.y() >= previewY && event.y() <= previewY + previewH) {
                cropX0 = (float) ((event.x() - previewX) / previewW);
                cropY0 = (float) ((event.y() - previewY) / previewH);
                cropX1 = cropX0;
                cropY1 = cropY0;
                draggingCrop = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingCrop && previewW > 0 && previewH > 0) {
            cropX1 = Math.max(0f, Math.min(1f, (float) ((event.x() - previewX) / previewW)));
            cropY1 = Math.max(0f, Math.min(1f, (float) ((event.y() - previewY) / previewH)));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingCrop) {
            draggingCrop = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (GlassSurface.liveWorldBackdrop()) return;
        GlassSurface.renderMenuPanorama(this, g, delta);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ScreenshotIO.pumpUploads();
        syncDeleteButton();

        float radius = AuroraTheme.RADIUS_LARGE;
        GlassSurface.beginGlassPass();
        GlassSurface.container(ctx, panelX, panelY, panelW, panelH, radius);
        GlassSurface.overlayDim(ctx, this.width, this.height);
        MaterialSurface.parent(ctx, panelX, panelY, panelW, panelH, radius, MaterialSurface.frameInset(radius));

        int onBg = MaterialSurface.color(ThemeToken.ON_BACKGROUND);
        int secondary = ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY);
        int muted = ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED);

        if (selected == null) {
            String title = "Screenshots (" + allEntries.size() + ")";
            ctx.text(this.font, AuroraFontRenderer.role(title, AuroraFontRenderer.Role.TITLE),
                    panelX + UiDensity.TITLE_X,
                    AuroraFontRenderer.roleTextY(this.font, panelY + UiDensity.HEADER_TOP, UiDensity.TOOL, AuroraFontRenderer.Role.TITLE),
                    onBg, false);
            renderGrid(ctx, mouseX, mouseY, onBg, muted);
        } else {
            String title = AuroraFontRenderer.ellipsizeRole(this.font, selected.displayName(),
                    Math.max(40, panelW - 220), AuroraFontRenderer.Role.TITLE);
            ctx.text(this.font, AuroraFontRenderer.role(title, AuroraFontRenderer.Role.TITLE),
                    panelX + UiDensity.TITLE_X + 76,
                    AuroraFontRenderer.roleTextY(this.font, panelY + UiDensity.HEADER_TOP, UiDensity.TOOL, AuroraFontRenderer.Role.TITLE),
                    onBg, false);
            renderDetailPreview(ctx, secondary, muted);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        if (selected != null && renameError != null && renameField != null) {
            FieldMessage.draw(ctx, this.font, renameError, renameField.getX(),
                    renameField.getY() + renameField.getHeight(), renameField.getWidth());
        }
        toast.render(ctx, this.font, this.width, this.height, Math.round(AuroraTheme.RADIUS_SMALL));
    }

    private void renderGrid(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int onBg, int muted) {
        refreshFiltered();
        int maxScroll = Math.max(0, totalGridHeight() - bodyH);
        scroll.advance(maxScroll);
        int scrollOff = (int) Math.round(scroll.current());

        if (filteredEntries.isEmpty()) {
            String msg = allEntries.isEmpty()
                    ? "No screenshots yet — press F2 in-game to capture one."
                    : "No screenshots match \"" + (searchField != null ? searchField.getValue().trim() : "") + "\"";
            AuroraFontRenderer.drawCentered(ctx, this.font, msg, bodyX + bodyW / 2, bodyY + bodyH / 2 - 4, muted);
            return;
        }

        int cols = columns();
        int cw = cardWidth();
        float small = AuroraTheme.RADIUS_SMALL;
        ctx.enableScissor(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH);
        for (int i = 0; i < filteredEntries.size(); i++) {
            int r = i / cols, c = i % cols;
            int cx = bodyX + c * (cw + CARD_GAP);
            int cy = bodyY + r * (CARD_H + CARD_GAP) - scrollOff;
            if (cy + CARD_H < bodyY || cy > bodyY + bodyH) continue;

            ScreenshotIndex.Entry entry = filteredEntries.get(i);
            boolean hot = mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + CARD_H
                    && mouseY >= bodyY && mouseY < bodyY + bodyH;
            MaterialSurface.selection(ctx, cx, cy, cw, CARD_H, small, false, hot ? 1f : 0f);

            int tx = cx + 4, ty = cy + 4, tw = cw - 8, th = CARD_THUMB_H - 6;
            ScreenshotIO.LoadedTexture thumb = ScreenshotIO.thumbnail(entry);
            if (thumb != null) {
                drawAspectTexture(ctx, thumb, tx, ty, tw, th);
            } else {
                RenderUtil.drawRoundedRectAA(ctx, tx, ty, tw, th, small, ThemeManager.color(ThemeToken.SURFACE_INSET));
                MaterialIconRenderer.drawIcon(ctx, this.font, IconCatalog.glyph("image"),
                        tx + tw / 2f, ty + th / 2f, 16f, muted);
            }

            String label = AuroraFontRenderer.ellipsize(this.font, entry.displayName(), cw - 12, 0);
            ctx.text(this.font, label, cx + 6, cy + CARD_THUMB_H + 2, onBg, false);
            String meta = ScreenshotIndex.formatSize(entry.sizeBytes());
            ctx.text(this.font, meta, cx + 6, cy + CARD_THUMB_H + 13, muted, false);
        }
        ctx.disableScissor();

        if (maxScroll > 0 && this.minecraft != null) {
            int sx = bodyX + bodyW + 3;
            double scale = this.minecraft.getWindow().getGuiScale();
            ScrollbarChrome.track(ctx, sx, bodyY, bodyH, ThemeToken.ON_OVERLAY);
            ScrollbarChrome.draw(ctx, sx, ScrollbarChrome.thumb(scroll, bodyY, bodyH, maxScroll, 24.0, scale),
                    false, ThemeToken.ON_OVERLAY);
        }
    }

    private void renderDetailPreview(GuiGraphicsExtractor ctx, int secondary, int muted) {
        float small = AuroraTheme.RADIUS_SMALL;
        RenderUtil.drawRoundedRectAA(ctx, bodyX, bodyY, bodyW, bodyH, small, ThemeManager.color(ThemeToken.SURFACE_INSET));

        ScreenshotIO.LoadedTexture tex = ScreenshotIO.preview(selected);
        if (tex == null) {
            previewW = 0; previewH = 0;
            AuroraFontRenderer.drawCentered(ctx, this.font, "Loading preview…",
                    bodyX + bodyW / 2, bodyY + bodyH / 2 - 4, muted);
            return;
        }

        int pad = 6;
        int availW = Math.max(1, bodyW - pad * 2);
        int availH = Math.max(1, bodyH - pad * 2 - 14);
        double scale = Math.min((double) availW / tex.width(), (double) availH / tex.height());
        previewW = Math.max(1, (int) Math.round(tex.width() * scale));
        previewH = Math.max(1, (int) Math.round(tex.height() * scale));
        previewX = bodyX + (bodyW - previewW) / 2;
        previewY = bodyY + pad + (availH - previewH) / 2;

        ctx.blit(RenderPipelines.GUI_TEXTURED, tex.id(),
                previewX, previewY, 0f, 0f, previewW, previewH, previewW, previewH, previewW, previewH, -1);

        if (cropMode) {
            int cx0 = previewX + Math.round(Math.min(cropX0, cropX1) * previewW);
            int cy0 = previewY + Math.round(Math.min(cropY0, cropY1) * previewH);
            int cx1 = previewX + Math.round(Math.max(cropX0, cropX1) * previewW);
            int cy1 = previewY + Math.round(Math.max(cropY0, cropY1) * previewH);
            int dim = 0x88000000;
            ctx.fill(previewX, previewY, previewX + previewW, cy0, dim);
            ctx.fill(previewX, cy1, previewX + previewW, previewY + previewH, dim);
            ctx.fill(previewX, cy0, cx0, cy1, dim);
            ctx.fill(cx1, cy0, previewX + previewW, cy1, dim);
            int accent = ThemeManager.color(ThemeToken.ACCENT);
            ctx.fill(cx0, cy0, cx1, cy0 + 1, accent);
            ctx.fill(cx0, Math.max(cy0, cy1 - 1), cx1, cy1, accent);
            ctx.fill(cx0, cy0, cx0 + 1, cy1, accent);
            ctx.fill(Math.max(cx0, cx1 - 1), cy0, cx1, cy1, accent);
        }

        String info = tex.sourceWidth() + "×" + tex.sourceHeight() + "  •  "
                + ScreenshotIndex.formatSize(selected.sizeBytes())
                + (cropMode ? "  •  Drag on image to adjust crop" : "");
        AuroraFontRenderer.drawCentered(ctx, this.font, info,
                bodyX + bodyW / 2, bodyY + bodyH - 13, secondary);
    }

    private static void drawAspectTexture(GuiGraphicsExtractor ctx, ScreenshotIO.LoadedTexture tex,
                                          int boxX, int boxY, int boxW, int boxH) {
        double scale = Math.min((double) boxW / tex.width(), (double) boxH / tex.height());
        int dw = Math.max(1, (int) Math.round(tex.width() * scale));
        int dh = Math.max(1, (int) Math.round(tex.height() * scale));
        int dx = boxX + (boxW - dw) / 2;
        int dy = boxY + (boxH - dh) / 2;
        ctx.blit(RenderPipelines.GUI_TEXTURED, tex.id(),
                dx, dy, 0f, 0f, dw, dh, dw, dh, dw, dh, -1);
    }
}
