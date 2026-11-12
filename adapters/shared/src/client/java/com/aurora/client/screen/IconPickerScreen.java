package com.aurora.client.screen;

import com.aurora.client.screen.setting.FeatureSetting;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.Button;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.component.ScrollbarChrome;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.MaterialIconRenderer;
import com.aurora.client.ui.util.UiDensity;
import com.aurora.client.util.SmoothScroll;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Searchable browser over every bundled Material Symbols icon (Profiles
 * refresh, 2026-11-12). Returns the chosen icon's name; the
 * {@link ColorPickerScreen} shape: {@code (parent, title, initial, onApply)}.
 *
 * <p>Thousands of icons, so the grid is virtual: only the rows inside the
 * viewport draw, and new glyphs rasterize under
 * {@link MaterialIconRenderer#drawIconBudgeted}'s per-frame budget (a small
 * placeholder shows until a glyph is ready). Search results are memoized by
 * {@link IconCatalog#search}.
 */
public class IconPickerScreen extends Screen implements ThemedScreen {
    /** The window owns the one backdrop capture; its buttons use cached faces. */
    @Override public boolean parentGlassOnly() { return true; }

    private static final int CELL = 26;
    private static final int GAP = 2;
    private static final int STEP = CELL + GAP;
    private static final float ICON_EM = 15f;

    private final Screen parent;
    private final Consumer<String> onApply;
    private String selected;

    private int panelX, panelY, panelW, panelH;
    private int gridX, gridY, gridW, gridH, cols;
    private int footerY;
    private EditBox searchField;
    private final SmoothScroll scroll = new SmoothScroll(60.0).boundedMotion();
    private String lastQuery = "";
    private int titleCount = -1, titleWidth = -1;
    private String titleText = "";

    public IconPickerScreen(Screen parent, String title, String initialIcon, Consumer<String> onApply) {
        super(Component.literal(title));
        this.parent = parent;
        this.onApply = onApply;
        this.selected = initialIcon == null ? "" : initialIcon;
    }

    @Override
    protected void init() {
        var frame = UiDensity.window(this.width, this.height);
        panelX = frame.x(); panelY = frame.y(); panelW = frame.width(); panelH = frame.height();
        int bh = UiDensity.HEADER_BUTTON_H;
        footerY = panelY + panelH - 8 - bh;
        gridY = panelY + UiDensity.HEADER + 6;
        gridH = Math.max(STEP, footerY - 8 - gridY);
        int usable = panelW - 24 - 8; // 8 for the scrollbar lane
        cols = Math.max(1, (usable + GAP) / STEP);
        gridW = cols * STEP - GAP;
        gridX = panelX + (panelW - 8 - gridW) / 2;

        String query = searchField != null ? searchField.getValue() : "";
        int searchW = Math.min(160, Math.max(80, panelW / 3));
        searchField = new EditBox(this.font, panelX + panelW - 12 - searchW,
                panelY + (UiDensity.HEADER - bh) / 2 + 1, searchW, bh, Component.literal("Search icons"));
        searchField.setMaxLength(48);
        searchField.setHint(Component.literal("Search icons…"));
        searchField.setValue(query);
        searchField.setFocused(true);
        this.addRenderableWidget(searchField);
        this.setFocused(searchField);

        int btnW = 64;
        int applyX = panelX + panelW - 12 - btnW;
        this.addRenderableWidget(ButtonWidget.semantic(applyX - 6 - btnW, footerY, btnW, bh,
                Component.literal("Cancel"), "Discards the choice and returns.",
                () -> com.aurora.client.adapter.NativeUi.setScreen(this.minecraft, parent)).glassBackground(true));
        this.addRenderableWidget(ButtonWidget.semantic(applyX, footerY, btnW, bh,
                Component.literal("Apply"), "Uses the selected icon and returns.", this::apply)
                .glassStyle(Button.GlassStyle.STAINED).primary(true));
        if (!selected.isEmpty()) scrollToSelected();
    }

    private List<IconCatalog.Icon> results() {
        return IconCatalog.search(searchField == null ? "" : searchField.getValue());
    }

    private double maxScroll(int count) {
        int rows = (count + cols - 1) / cols;
        return Math.max(0, rows * STEP - GAP - gridH);
    }

    private void scrollToSelected() {
        List<IconCatalog.Icon> icons = results();
        for (int i = 0; i < icons.size(); i++) {
            if (icons.get(i).name().equals(selected)) {
                double top = (i / cols) * STEP - (gridH - CELL) / 2.0;
                scroll.set(Math.max(0, Math.min(maxScroll(icons.size()), top)));
                return;
            }
        }
    }

    private void apply() {
        onApply.accept(selected);
        com.aurora.client.adapter.NativeUi.setScreen(this.minecraft, parent);
    }

    @Override
    public void onClose() {
        com.aurora.client.adapter.NativeUi.setScreen(this.minecraft, parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (GlassSurface.liveWorldBackdrop()) return;
        GlassSurface.renderMenuPanorama(this, g, delta);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        float radius = ThemeManager.current().roundness().radiusLarge();
        GlassSurface.beginGlassPass();
        GlassSurface.container(ctx, panelX, panelY, panelW, panelH, radius);
        GlassSurface.overlayDim(ctx, this.width, this.height);
        MaterialSurface.parent(ctx, panelX, panelY, panelW, panelH, radius);

        String query = searchField.getValue();
        if (!query.equals(lastQuery)) {
            lastQuery = query;
            scroll.set(0);
        }
        List<IconCatalog.Icon> icons = results();
        double maxScroll = maxScroll(icons.size());
        scroll.advance(maxScroll);

        int onBackground = MaterialSurface.color(ThemeToken.ON_BACKGROUND);
        int muted = ThemeManager.color(ThemeToken.ON_BACKGROUND_MUTED);
        ctx.text(this.font, title(icons.size()), panelX + 12, panelY + (UiDensity.HEADER - this.font.lineHeight) / 2 + 1,
                onBackground, false);

        // The grid: only the rows the viewport shows.
        float small = ThemeManager.current().roundness().radiusSmall();
        double offset = scroll.current();
        int firstRow = (int) Math.floor(offset / STEP);
        int lastRow = (int) Math.floor((offset + gridH) / STEP);
        String hovered = null;
        int hoverX = 0, hoverY = 0;
        ctx.enableScissor(gridX - 2, gridY, gridX + gridW + 2, gridY + gridH);
        for (int row = Math.max(0, firstRow); row <= lastRow; row++) {
            int cy = gridY + (int) Math.round(row * STEP - offset);
            for (int col = 0; col < cols; col++) {
                int i = row * cols + col;
                if (i >= icons.size()) break;
                IconCatalog.Icon icon = icons.get(i);
                int cx = gridX + col * STEP;
                boolean isSelected = icon.name().equals(selected);
                boolean hot = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL
                        && mouseY >= gridY && mouseY < gridY + gridH;
                if (hot) {
                    hovered = icon.label();
                    hoverX = cx;
                    hoverY = cy;
                }
                if (isSelected || hot) {
                    MaterialSurface.selection(ctx, cx, cy, CELL, CELL, small, isSelected, hot ? 1f : 0f);
                }
                int ink = isSelected ? ThemeManager.color(ThemeToken.ACCENT) : onBackground;
                if (!MaterialIconRenderer.drawIconBudgeted(ctx, this.font, icon.glyph(),
                        cx + CELL / 2f, cy + CELL / 2f, ICON_EM, ink)) {
                    ctx.fill(cx + CELL / 2 - 1, cy + CELL / 2 - 1, cx + CELL / 2 + 1, cy + CELL / 2 + 1, muted);
                }
            }
        }
        ctx.disableScissor();
        if (icons.isEmpty()) {
            AuroraFontRenderer.drawCentered(ctx, this.font, Component.literal("No icons match"),
                    gridX + gridW / 2, gridY + gridH / 2 - this.font.lineHeight / 2,
                    ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY));
        }
        if (maxScroll > 0) {
            int scale = Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
            ScrollbarChrome.draw(ctx, gridX + gridW + 4,
                    ScrollbarChrome.thumb(scroll, gridY, gridH, maxScroll, 16, scale), false, ThemeToken.ON_BACKGROUND);
        }

        // Footer: the selected icon and its name.
        String glyph = IconCatalog.glyph(selected);
        int footerText = footerY + (UiDensity.HEADER_BUTTON_H - this.font.lineHeight) / 2 + 1;
        int labelX = panelX + 12;
        if (!glyph.isEmpty()) {
            MaterialIconRenderer.drawIcon(ctx, this.font, glyph, labelX + 7, footerY + UiDensity.HEADER_BUTTON_H / 2f, 14f,
                    ThemeManager.color(ThemeToken.ACCENT));
            labelX += 18;
        }
        int labelW = Math.max(0, panelX + panelW - 12 - 64 - 6 - 64 - 8 - labelX);
        String label = glyph.isEmpty() ? "Default icon" : selected.replace('_', ' ');
        ctx.text(this.font, AuroraFontRenderer.ellipsize(this.font, label, labelW, 0), labelX, footerText,
                ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY), false);

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        String tip = hovered;
        FeatureSetting.hoverTooltip("icon-picker", () -> tip, hovered == null ? -1000 : hoverX, hoverY, CELL, CELL,
                mouseX, mouseY);
        FeatureSetting.drawPendingTooltip(ctx, this.width, this.height);
    }

    /** The header title with the result count, rebuilt only when the count or width changes. */
    private String title(int count) {
        int width = Math.max(0, searchField.getX() - 12 - (panelX + 12));
        if (count != titleCount || width != titleWidth) {
            titleCount = count;
            titleWidth = width;
            String counted = count == 1 ? "1 icon" : String.format("%,d icons", count);
            titleText = AuroraFontRenderer.ellipsize(this.font, this.title.getString() + "  ·  " + counted, width, 0);
        }
        return titleText;
    }

    /** The icon under the pointer, or null. */
    private IconCatalog.Icon iconAt(double mouseX, double mouseY) {
        if (mouseX < gridX || mouseX >= gridX + gridW || mouseY < gridY || mouseY >= gridY + gridH) return null;
        int col = (int) ((mouseX - gridX) / STEP);
        double local = mouseY - gridY + scroll.current();
        int row = (int) (local / STEP);
        if (col >= cols || mouseX - gridX - col * STEP >= CELL || local - row * STEP >= CELL) return null;
        List<IconCatalog.Icon> icons = results();
        int i = row * cols + col;
        return i < icons.size() ? icons.get(i) : null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            IconCatalog.Icon icon = iconAt(event.x(), event.y());
            if (icon != null) {
                boolean again = icon.name().equals(selected);
                selected = icon.name();
                if (doubleClick && again) apply();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll.wheel(vertical, STEP * 2, maxScroll(results().size()));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && !selected.isEmpty()) {
            apply();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        super.removed();
        MaterialSurface.disposeTemplates();
    }
}
