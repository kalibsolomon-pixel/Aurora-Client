package com.aurora.client.screen.setting;

import com.aurora.client.ui.util.AuroraFontRenderer;
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
 * Live samples of the normal and in-reach crosshair shapes and colors.
 * Both samples reuse {@link CrosshairRenderer#drawShape}.
 */
public class CrosshairPreviewSetting extends FeatureSetting {
    private static final int ROW_H = 34, WELL = 30, PAD = 8;
    private static final int SAMPLE_PITCH = 86;
    private static final int MAX_ZOOM = 8, TARGET_EXTENT = 24;

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
        int wellY = y + (ROW_H - WELL) / 2;
        int wellX = x + width - PAD - WELL;
        int labelEnd = wellX - PAD;
        if (cfg.crosshairIndicatorEnabled) {
            int pitch = Math.clamp((width - PAD * 3) / 2, WELL + PAD, SAMPLE_PITCH);
            drawCaption(ctx, font, "In Reach", wellX, wellY, pitch - WELL - PAD);
            drawWell(ctx, wellX, wellY, cfg.crosshairIndicatorStyle, cfg.crosshairIndicatorSize,
                    cfg.crosshairIndicatorThickness, cfg.crosshairIndicatorGap, cfg.crosshairIndicatorColor,
                    cfg.crosshairIndicatorCustomPixels, cfg.crosshairIndicatorCustomWidth, cfg.crosshairIndicatorCustomHeight);
            wellX -= pitch;
            drawCaption(ctx, font, "Normal", wellX, wellY, pitch - WELL - PAD);
            labelEnd = wellX - pitch + WELL;
        }
        int labelX = x + PAD + 4;
        String title = AuroraFontRenderer.ellipsize(font, label, Math.max(0, labelEnd - labelX), 0);
        ctx.text(font, title, labelX, AuroraFontRenderer.textY(font, wellY, WELL), MaterialSurface.color(ThemeToken.ON_BACKGROUND), false);
        drawWell(ctx, wellX, wellY, cfg.crosshairStyle, cfg.crosshairSize, cfg.crosshairThickness,
                cfg.crosshairGap, cfg.crosshairColor, cfg.crosshairCustomPixels, cfg.crosshairCustomWidth, cfg.crosshairCustomHeight);
    }

    private static void drawCaption(GuiGraphicsExtractor ctx, Font font, String text, int wellX, int wellY, int maxWidth) {
        String fitted = AuroraFontRenderer.ellipsize(font, text, maxWidth, 0);
        ctx.text(font, fitted, wellX - PAD - font.width(fitted), AuroraFontRenderer.textY(font, wellY, WELL),
                MaterialSurface.color(ThemeToken.ON_BACKGROUND_SECONDARY), false);
    }

    private static void drawWell(GuiGraphicsExtractor ctx, int x, int y, AuroraConfig.CrosshairStyle style, int size,
                                 int thick, int gap, int color, boolean[] pixels, int w, int h) {
        float r = ThemeManager.current().roundness().radiusSmall();
        if(MaterialSurface.unified()) MaterialSurface.inset(ctx,x,y,WELL,WELL,r,false);
        else {
            RenderUtil.drawRoundedRectAA(ctx, x, y, WELL, WELL, r, ThemeManager.color(ThemeToken.MATERIAL_SUNKEN));
            RenderUtil.drawRoundedOutlineAA(ctx, x, y, WELL, WELL, r, 1.0f, ThemeManager.color(ThemeToken.MATERIAL_BORDER_STRONG));
        }
        ctx.enableScissor(x + 1, y + 1, x + WELL - 1, y + WELL - 1);
        ctx.pose().pushMatrix();
        size = Math.max(1, size);
        thick = Math.max(1, thick);
        gap = Math.max(0, gap);
        float zoom = zoomFor(style, size, thick, gap);
        // A subpixel stroke can vanish on the seam when shrunk. Center it on a device pixel.
        float offset = zoom < 1f ? 0.5f / Minecraft.getInstance().getWindow().getGuiScale() : 0f;
        ctx.pose().translate(x + WELL / 2f + offset, y + WELL / 2f + offset);
        ctx.pose().scale(zoom, zoom);
        // Centre the shape's own bounds on the well, not the HUD's seam: the HUD's half-pixel bias
        // pushed every sample up and left by half a zoomed pixel (owner report 2026-11-15).
        float bias = centreBias(style, thick);
        ctx.pose().translate(-bias, -bias);
        CrosshairRenderer.drawShape(ctx, style, 0, 0, size, thick, gap, color, pixels, w, h);
        ctx.pose().popMatrix();
        ctx.disableScissor();
    }

    /**
     * How far right and down of {@code (cx, cy)} {@link CrosshairRenderer#drawShape} centres a
     * shape when called without the HUD's half-pixel translate: an odd-thickness cross spans
     * {@code cx - t/2 .. cx - t/2 + t}, the device rasters add their own +0.5, and a custom
     * canvas is centred on {@code cx} exactly.
     */
    static float centreBias(AuroraConfig.CrosshairStyle style, int thick) {
        if (style == null) style = AuroraConfig.CrosshairStyle.CROSS;
        return switch (style) {
            case CROSS -> (thick % 2) * 0.5f;
            case DOT, CIRCLE, SQUARE -> 0.5f;
            case CUSTOM -> 0f;
        };
    }

    static float zoomFor(AuroraConfig.CrosshairStyle style, int size, int thick, int gap) {
        float extent = switch (style == null ? AuroraConfig.CrosshairStyle.CROSS : style) {
            case CROSS -> Math.max(thick, 2f * (size + (float) gap) + (thick % 2));
            case DOT -> size + (float) thick + 1f;
            case CIRCLE, SQUARE -> size * 2f + 3f;
            case CUSTOM -> size * 3f;
        };
        float fit = TARGET_EXTENT / extent;
        // Magnify in whole steps for crisp small samples. Shrink large shapes to fit the well.
        return fit < 1f ? fit : Math.min(MAX_ZOOM, (int) fit);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        return false;
    }
}
