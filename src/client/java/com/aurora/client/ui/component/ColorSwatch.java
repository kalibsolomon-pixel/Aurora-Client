package com.aurora.client.ui.component;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.HoverAnim;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.IntSupplier;

/**
 * Themed color swatch — a rounded swatch showing a color (optionally over a
 * checkerboard so alpha reads correctly), with hover + selection feedback.
 *
 * <p>Consolidates the swatch rendering previously duplicated across
 * {@code ColorSetting} (checkerboard + chamfered ring + halo) and
 * {@code AccentSetting} (preset grid + selection ring). All curves render
 * through the high-res AA engine; the base ring uses the {@code BORDER}
 * token, the hover halo uses {@code ON_BACKGROUND} (mode-aware), and the
 * selection ring uses {@code ACCENT}.
 */
public class ColorSwatch extends Widget {

    private final IntSupplier getter;
    private final Runnable onClick;

    private boolean checkerboard = true;
    private boolean selected = false;
    private boolean disabled = false;
    private final HoverAnim hoverAnim = new HoverAnim(140L);

    public ColorSwatch(IntSupplier getter, Runnable onClick) {
        this.getter = getter;
        this.onClick = onClick;
    }

    public ColorSwatch checkerboard(boolean c) {
        this.checkerboard = c;
        return this;
    }

    public ColorSwatch selected(boolean s) {
        this.selected = s;
        return this;
    }

    public ColorSwatch disabled(boolean d) {
        this.disabled = d;
        return this;
    }

    @Override
    public void renderOverlay(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY) {
        int color = getter.getAsInt();
        float radius = Math.min(h / 2f, ThemeManager.current().roundness().radiusSmall());

        if (checkerboard) {
            drawCheckerboard(g, x, y, w, h);
        }

        int drawColor = disabled ? (color & 0x55FFFFFF) : (color | 0xFF000000);
        RenderUtil.drawRoundedRectAA(g, x, y, w, h, radius, drawColor);

        boolean hover = !disabled && inBounds(mouseX, mouseY, x, y, w, h);
        float hT = hoverAnim.update(hover);

        if (selected) {
            RenderUtil.drawRoundedOutlineAA(g, x - 2, y - 2, w + 4, h + 4, radius + 2, 1.5f,
                    ThemeManager.color(ThemeToken.ACCENT));
        } else {
            RenderUtil.drawRoundedOutlineAA(g, x, y, w, h, radius, 1.0f,
                    ThemeManager.color(ThemeToken.BORDER));
            if (hT > 0f) {
                int haloA = Math.round(90 * hT);
                int onBg = ThemeManager.color(ThemeToken.ON_BACKGROUND) & 0x00FFFFFF;
                RenderUtil.drawRoundedOutlineAA(g, x - 1, y - 1, w + 2, h + 2, radius + 1, 1.0f,
                        (haloA << 24) | onBg);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (disabled || button != 0 || !inBounds(mx, my, x, y, w, h)) return false;
        if (onClick != null) onClick.run();
        return true;
    }

    private static void drawCheckerboard(GuiGraphics g, float x, float y, float w, float h) {
        int cell = 4;
        for (int dy = 0; dy < h; dy += cell) {
            for (int dx = 0; dx < w; dx += cell) {
                boolean dark = ((dx / cell) + (dy / cell)) % 2 == 0;
                int col = dark ? 0xFF555555 : 0xFFAAAAAA;
                g.fill(Math.round(x + dx), Math.round(y + dy),
                        Math.round(Math.min(x + dx + cell, x + w)),
                        Math.round(Math.min(y + dy + cell, y + h)),
                        col);
            }
        }
    }
}
