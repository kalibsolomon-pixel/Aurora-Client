package com.aurora.client.screen.setting;

import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraShapes;
import com.aurora.client.util.AuroraTheme;
import com.aurora.client.util.HoverAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Pixel-canvas editor row. Renders a grid where users can left-click
 * to set pixels, right-click to clear them, and drag with either button
 * to paint a stroke. Mirrors the crosshair's grid topology exactly.
 */
public class PixelCanvasSetting extends FeatureSetting {

    private static final int[] SIZES = {5, 11, 25, 33};
    private static final int CANVAS_PAD = 4;

    private static final int CLEAR_BTN_W = 56;
    private static final int CLEAR_BTN_H = 16;

    private static final int CHECKER_LIGHT = 0xFF2A3346;
    private static final int CHECKER_DARK  = 0xFF1F2638;
    private static final int CELL_ON_COLOR = 0xFFFFFFFF;
    private static final int CELL_BORDER   = 0x40000000;

    private static final int LABEL_H = 22;  // top label area (incl. clear button)

    private final Supplier<boolean[]> getter;
    private final Consumer<boolean[]> setter;
    private int lastWidth = 240;

    // Drag state
    private boolean dragging = false;
    private int dragButton = -1;
    private int lastCellX = -1, lastCellY = -1;

    private int canvasX, canvasY;
    private int clearBtnX, clearBtnY;
    
    // Tab bounds
    private int[] tabX = new int[4];
    private int[] tabW = new int[4];
    private int tabsY;
    private int tabsH = 16;

    private final HoverAnim clearHoverAnim = new HoverAnim(140L);
    private final HoverAnim[] tabHoverAnims = new HoverAnim[4];

    public PixelCanvasSetting(String label, Supplier<boolean[]> getter, Consumer<boolean[]> setter) {
        super(label);
        this.getter = getter;
        this.setter = setter;
        for (int i = 0; i < 4; i++) {
            tabHoverAnims[i] = new HoverAnim(140L);
        }
    }

    private int getCurrentSizeIndex() {
        boolean[] pixels = getter.get();
        if (pixels != null) {
            int len = pixels.length;
            if (len == 25) return 0;
            if (len == 625) return 2;
            if (len == 1089) return 3;
        }
        return 1; // 11x11 default
    }

    private int getGridW() { return SIZES[getCurrentSizeIndex()]; }
    private int getGridH() { return SIZES[getCurrentSizeIndex()]; }
    private int getCellPx() {
        int w = getGridW();
        if (w == 5) return 20;
        if (w == 11) return 13;
        if (w == 25) return 6;
        return 5; // 33x33
    }
    
    private int getCanvasW() { return getGridW() * getCellPx() + CANVAS_PAD * 2; }
    private int getCanvasH() { return getGridH() * getCellPx() + CANVAS_PAD * 2; }
    private int getControlH() { return LABEL_H + 24 + getCanvasH() + 6; } // +24 for tabs

    @Override public int baseHeight() { return getControlH(); }
    @Override public int height()    { return getControlH() + descriptionHeight(lastWidth); }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;
        boolean disabled = isDisabled();

        // Label
        renderLabelWithTooltip(ctx, label, x + 12, y + (LABEL_H - tr.lineHeight) / 2,
                AuroraTheme.TEXT_PRIMARY, mouseX, mouseY, disabled);

        // Clear button
        clearBtnX = x + width - CLEAR_BTN_W - 14;
        clearBtnY = y + (LABEL_H - CLEAR_BTN_H) / 2;
        boolean clearHover = !disabled && mouseX >= clearBtnX && mouseX < clearBtnX + CLEAR_BTN_W
                && mouseY >= clearBtnY && mouseY < clearBtnY + CLEAR_BTN_H;
        float chT = clearHoverAnim.update(clearHover);
        
        int clearFill   = disabled ? 0x22FFFFFF : lerpColor(AuroraTheme.PANEL_OFF, AuroraTheme.PANEL_OFF_HOVER, chT);
        int clearBorder = disabled ? 0x33FFFFFF : lerpColor(AuroraTheme.BORDER_OFF, AuroraTheme.BORDER_OFF_HOVER, chT);
        int clearText   = disabled ? AuroraTheme.TEXT_DIM : lerpColor(AuroraTheme.TEXT_SECONDARY, AuroraTheme.TEXT_PRIMARY, chT);
        
        RenderUtil.drawSquircle(ctx, clearBtnX, clearBtnY, CLEAR_BTN_W, CLEAR_BTN_H, AuroraTheme.RADIUS_SMALL, clearFill);
        RenderUtil.drawSquircleOutline(ctx, clearBtnX, clearBtnY, CLEAR_BTN_W, CLEAR_BTN_H, AuroraTheme.RADIUS_SMALL, 1.0f, clearBorder);
        String clearLabel = "Clear";
        int clearLabelW = tr.width(clearLabel);
        ctx.drawString(tr, clearLabel,
                clearBtnX + (CLEAR_BTN_W - clearLabelW) / 2,
                clearBtnY + (CLEAR_BTN_H - tr.lineHeight) / 2 + 1,
                clearText, false);

        // Tabs
        tabsY = y + LABEL_H;
        int currentIdx = getCurrentSizeIndex();
        int totalTabsW = 0;
        String[] tabLabels = {"5x5", "11x11", "25x25", "33x33"};
        int[] lblWidths = new int[4];
        for (int i = 0; i < 4; i++) {
            lblWidths[i] = tr.width(tabLabels[i]);
            totalTabsW += lblWidths[i] + 16;
            if (i < 3) totalTabsW += 4; // gap
        }
        
        int currentX = x + (width - totalTabsW) / 2;
        for (int i = 0; i < 4; i++) {
            tabX[i] = currentX;
            tabW[i] = lblWidths[i] + 16;
            
            boolean hover = !disabled && mouseX >= tabX[i] && mouseX < tabX[i] + tabW[i]
                    && mouseY >= tabsY && mouseY < tabsY + tabsH;
            float tT = tabHoverAnims[i].update(hover);
            
            boolean active = (i == currentIdx);
            
            int fill;
            int border;
            int textCol;
            
            if (disabled) {
                fill = active ? 0x44FFFFFF : 0x22FFFFFF;
                border = 0x33FFFFFF;
                textCol = AuroraTheme.TEXT_DIM;
            } else if (active) {
                fill = AuroraTheme.IOS_BLUE;
                border = AuroraTheme.BORDER_ON;
                textCol = AuroraTheme.IOS_LABEL;
            } else {
                fill = lerpColor(AuroraTheme.PANEL_OFF, AuroraTheme.PANEL_OFF_HOVER, tT);
                border = lerpColor(AuroraTheme.BORDER_OFF, AuroraTheme.BORDER_OFF_HOVER, tT);
                textCol = lerpColor(AuroraTheme.TEXT_SECONDARY, AuroraTheme.TEXT_PRIMARY, tT);
            }
            
            RenderUtil.drawSquircle(ctx, tabX[i], tabsY, tabW[i], tabsH, AuroraTheme.RADIUS_SMALL, fill);
            RenderUtil.drawSquircleOutline(ctx, tabX[i], tabsY, tabW[i], tabsH, AuroraTheme.RADIUS_SMALL, 1.0f, border);
            
            ctx.drawString(tr, tabLabels[i],
                    tabX[i] + 8,
                    tabsY + (tabsH - tr.lineHeight) / 2 + 1,
                    textCol, false);
                    
            currentX += tabW[i] + 4;
        }

        // Canvas
        canvasX = x + (width - getCanvasW()) / 2;
        canvasY = tabsY + tabsH + 8;

        // Canvas backdrop panel.
        AuroraShapes.panel(ctx, canvasX, canvasY, getCanvasW(), getCanvasH(), AuroraTheme.PANEL_INSET, 0);
        AuroraShapes.outline(ctx, canvasX, canvasY, getCanvasW(), getCanvasH(), disabled ? 0x33FFFFFF : AuroraTheme.BORDER_OFF, 0);

        boolean[] pixels = getter.get();
        int gridW = getGridW();
        int gridH = getGridH();
        int cellPx = getCellPx();
        
        if (pixels == null || pixels.length != gridW * gridH) {
            renderDescription(ctx, x, y + getControlH(), width);
            return;
        }

        // Cells.
        int gridLeft = canvasX + CANVAS_PAD;
        int gridTop  = canvasY + CANVAS_PAD;
        for (int gy = 0; gy < gridH; gy++) {
            for (int gx = 0; gx < gridW; gx++) {
                int cellLeft = gridLeft + gx * cellPx;
                int cellTop  = gridTop  + gy * cellPx;
                boolean lightCell = ((gx + gy) & 1) == 0;
                int bg = lightCell ? CHECKER_LIGHT : CHECKER_DARK;
                if (disabled) bg = lerpColor(bg, 0x00000000, 0.5f); // darken
                ctx.fill(cellLeft, cellTop, cellLeft + cellPx, cellTop + cellPx, bg);

                if (pixels[gy * gridW + gx]) {
                    ctx.fill(cellLeft + 1, cellTop + 1,
                            cellLeft + cellPx - 1, cellTop + cellPx - 1,
                            disabled ? 0x66FFFFFF : CELL_ON_COLOR);
                }

                ctx.fill(cellLeft + cellPx - 1, cellTop, cellLeft + cellPx, cellTop + cellPx, disabled ? 0x22000000 : CELL_BORDER);
                ctx.fill(cellLeft, cellTop + cellPx - 1, cellLeft + cellPx, cellTop + cellPx, disabled ? 0x22000000 : CELL_BORDER);
            }
        }

        renderDescription(ctx, x, y + getControlH(), width);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (isDisabled()) return false;
        
        // Tab clicks
        if (button == 0 && mouseY >= tabsY && mouseY < tabsY + tabsH) {
            for (int i = 0; i < 4; i++) {
                if (mouseX >= tabX[i] && mouseX < tabX[i] + tabW[i]) {
                    int newSize = SIZES[i];
                    int oldSize = getGridW();
                    if (newSize != oldSize) {
                        boolean[] newPixels = new boolean[newSize * newSize];
                        boolean[] oldPixels = getter.get();
                        
                        if (oldPixels != null && oldPixels.length == oldSize * oldSize) {
                            int offsetNew = newSize / 2;
                            int offsetOld = oldSize / 2;
                            for (int py = 0; py < oldSize; py++) {
                                for (int px = 0; px < oldSize; px++) {
                                    if (oldPixels[py * oldSize + px]) {
                                        int nx = px - offsetOld + offsetNew;
                                        int ny = py - offsetOld + offsetNew;
                                        if (nx >= 0 && nx < newSize && ny >= 0 && ny < newSize) {
                                            newPixels[ny * newSize + nx] = true;
                                        }
                                    }
                                }
                            }
                        } else {
                            newPixels[newSize / 2 * newSize + newSize / 2] = true;
                        }
                        
                        setter.accept(newPixels);
                        com.aurora.client.config.AuroraConfig.save();
                    }
                    return true;
                }
            }
        }
        
        // Clear button.
        if (button == 0
                && mouseX >= clearBtnX && mouseX < clearBtnX + CLEAR_BTN_W
                && mouseY >= clearBtnY && mouseY < clearBtnY + CLEAR_BTN_H) {
            boolean[] pixels = getter.get();
            if (pixels != null) {
                for (int i = 0; i < pixels.length; i++) pixels[i] = false;
                com.aurora.client.config.AuroraConfig.save();
            }
            return true;
        }

        // Canvas hits.
        int[] cell = cellAt(mouseX, mouseY);
        if (cell == null) return false;
        if (button != 0 && button != 1) return false;

        applyCell(cell[0], cell[1], button == 0);
        dragging = true;
        dragButton = button;
        lastCellX = cell[0];
        lastCellY = cell[1];
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy,
                                int rowX, int rowY, int rowWidth) {
        if (!dragging || button != dragButton || isDisabled()) return false;

        int[] cell = cellAt(mouseX, mouseY);
        if (cell == null) {
            return true;
        }
        if (cell[0] == lastCellX && cell[1] == lastCellY) return true;

        fillLine(lastCellX, lastCellY, cell[0], cell[1], button == 0);
        lastCellX = cell[0];
        lastCellY = cell[1];
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == dragButton) {
            dragging = false;
            dragButton = -1;
            lastCellX = -1;
            lastCellY = -1;
            com.aurora.client.config.AuroraConfig.save();
            return true;
        }
        return false;
    }

    private int[] cellAt(double mouseX, double mouseY) {
        int gridLeft = canvasX + CANVAS_PAD;
        int gridTop  = canvasY + CANVAS_PAD;
        int relX = (int) Math.floor(mouseX - gridLeft);
        int relY = (int) Math.floor(mouseY - gridTop);
        if (relX < 0 || relY < 0) return null;
        int cellPx = getCellPx();
        int gx = relX / cellPx;
        int gy = relY / cellPx;
        int gridW = getGridW();
        int gridH = getGridH();
        if (gx < 0 || gx >= gridW) return null;
        if (gy < 0 || gy >= gridH) return null;
        return new int[] { gx, gy };
    }

    private void applyCell(int gx, int gy, boolean setOn) {
        boolean[] pixels = getter.get();
        if (pixels == null) return;
        int gridW = getGridW();
        int idx = gy * gridW + gx;
        if (idx < 0 || idx >= pixels.length) return;
        pixels[idx] = setOn;
    }

    private void fillLine(int x0, int y0, int x1, int y1, boolean setOn) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int cx = x0;
        int cy = y0;
        while (true) {
            applyCell(cx, cy, setOn);
            if (cx == x1 && cy == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; cx += sx; }
            if (e2 < dx)  { err += dx; cy += sy; }
        }
    }

    private static int lerpColor(int from, int to, float t) {
        if (t <= 0f) return from;
        if (t >= 1f) return to;
        int af = (from >>> 24) & 0xFF, ar = (from >>> 16) & 0xFF, ag = (from >>> 8) & 0xFF, ab = from & 0xFF;
        int bf = (to   >>> 24) & 0xFF, br = (to   >>> 16) & 0xFF, bg = (to   >>> 8) & 0xFF, bb = to   & 0xFF;
        int a = Math.round(af + (bf - af) * t);
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int b = Math.round(ab + (bb - ab) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}