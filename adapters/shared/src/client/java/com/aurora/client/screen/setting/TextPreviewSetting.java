package com.aurora.client.screen.setting;

import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.MaterialSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The Text section's live sample (2026-11-12): one line in the current font,
 * weight, modifiers and case, drawn the way HUD text is (with its shadow),
 * so a change shows before the player leaves the menu. Read-only.
 */
public class TextPreviewSetting extends FeatureSetting {
    private static final String SAMPLE = "The quick brown fox jumps over the lazy dog 0123";
    private static final int ROW_H = 26;
    private static final int PAD_X = 12;

    private int fittedWidth = -1;
    private int fittedStyle;
    private String fitted = SAMPLE;

    public TextPreviewSetting() {
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
        int available = Math.max(0, width - PAD_X * 2);
        int style = com.aurora.client.ui.util.AuroraFontRenderer.styleKey();
        if (available != fittedWidth || style != fittedStyle) {
            fittedWidth = available;
            fittedStyle = style;
            fitted = com.aurora.client.ui.util.AuroraFontRenderer.ellipsize(font, SAMPLE, available, 1);
        }
        com.aurora.client.hud.HudTextPainter.draw(ctx, font, fitted, x + PAD_X, y + (ROW_H - font.lineHeight) / 2 + 1,
                MaterialSurface.color(ThemeToken.ON_BACKGROUND));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        return false;
    }
}
