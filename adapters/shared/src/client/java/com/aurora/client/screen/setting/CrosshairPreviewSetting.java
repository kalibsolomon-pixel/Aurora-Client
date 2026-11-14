package com.aurora.client.screen.setting;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.CrosshairRenderer;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The Crosshair page's live sample (UI polish 2026-11-14): the crosshair as the HUD draws it,
 * on a dark well at the control edge, and beside it the in-reach indicator while that is on.
 * Style, size, thickness, gap and colour changes show before the player leaves the menu.
 * Read-only; it reuses {@link CrosshairRenderer#drawShape}, so the sample cannot drift from
 * the real crosshair.
 */
public class CrosshairPreviewSetting extends FeatureSetting {
    private static final int ROW_H = 44, WELL = 38, GAP = 6, PAD = 8;
    private static final int MIN_ZOOM = 3, MAX_ZOOM = 6, TARGET_EXTENT = 24;

    public CrosshairPreviewSetting() {
        super("Preview");
    }

    @Override public int baseHeight() { return ROW_H; }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY) {
        renderOverlay(ctx, x, y, width, mouseX, mouseY);
    }

    @Override
    public void renderOverlay(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        if (font == null) return;
        AuroraConfig cfg = AuroraConfig.get();
        ctx.text(font, label, x + PAD + 4, y + (ROW_H - font.lineHeight) / 2 + 1, MaterialSurface.color(ThemeToken.ON_BACKGROUND), false);
        int wellY = y + (ROW_H - WELL) / 2;
        int right = x + width - PAD;
        if (cfg.crosshairIndicatorEnabled) {
            drawWell(ctx, right - WELL, wellY, cfg.crosshairIndicatorStyle, cfg.crosshairIndicatorSize,
                    cfg.crosshairIndicatorThickness, cfg.crosshairIndicatorGap, cfg.crosshairIndicatorColor,
                    cfg.crosshairIndicatorCustomPixels, cfg.crosshairIndicatorCustomWidth, cfg.crosshairIndicatorCustomHeight);
            right -= WELL + GAP;
        }
        drawWell(ctx, right - WELL, wellY, cfg.crosshairStyle, cfg.crosshairSize, cfg.crosshairThickness,
                cfg.crosshairGap, cfg.crosshairColor, cfg.crosshairCustomPixels, cfg.crosshairCustomWidth, cfg.crosshairCustomHeight);
    }

    private static void drawWell(GuiGraphicsExtractor ctx, int x, int y, AuroraConfig.CrosshairStyle style, int size,
                                 int thick, int gap, int color, boolean[] pixels, int w, int h) {
        float r = ThemeManager.current().roundness().radiusSmall();
        RenderUtil.drawRoundedRectAA(ctx, x, y, WELL, WELL, r, ThemeManager.color(ThemeToken.MATERIAL_SUNKEN));
        RenderUtil.drawRoundedOutlineAA(ctx, x, y, WELL, WELL, r, 1.0f, ThemeManager.color(ThemeToken.MATERIAL_BORDER_STRONG));
        ctx.enableScissor(x + 1, y + 1, x + WELL - 1, y + WELL - 1);
        ctx.pose().pushMatrix();
        // Drawn at a whole-number zoom about the well's centre: small shapes step up to fill about
        // two thirds of the well (a 2 px dot at 3x still read as a speck, council review
        // 2026-11-14), larger ones stay at 3x; shape, proportions and colour are the HUD's own.
        float zoom = zoomFor(size, gap);
        ctx.pose().translate(x + WELL / 2f, y + WELL / 2f);
        ctx.pose().scale(zoom, zoom);
        ctx.pose().translate(-0.5f, -0.5f); // the HUD path's seam-straddling centre
        CrosshairRenderer.drawShape(ctx, style, 0, 0, Math.max(1, size), Math.max(1, thick),
                Math.max(0, gap), color, pixels, w, h);
        ctx.pose().popMatrix();
        ctx.disableScissor();
    }

    static int zoomFor(int size, int gap) {
        int extent = Math.max(1, size * 2 + Math.max(0, gap));
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, TARGET_EXTENT / extent));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        return false;
    }
}
