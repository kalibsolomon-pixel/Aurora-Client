package com.aurora.client.screen.setting;

import com.aurora.client.ui.component.Button;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Single-action button row. Used for things like a "Preview" sound button
 * sitting next to a sound selector — the button does its work and returns
 * (no state change tracked here).
 */
public class ButtonSetting extends FeatureSetting {
    private static final int CONTROL_H = 28;
    private static final int BTN_W = 90;
    private static final int BTN_H = 18;

    private final Button button;

    private int lastWidth = 240;

    public ButtonSetting(String label, Runnable onPress) {
        this(label, "Preview", onPress);
    }

    /** Variant with an explicit button caption (the default reads "Preview"). */
    public ButtonSetting(String label, String buttonText, Runnable onPress) {
        super(label);
        this.button = new Button(buttonText, onPress).glassBackground(true);
    }

    @Override public ButtonSetting description(String desc) { super.description(desc); return this; }
    @Override public ButtonSetting description(java.util.function.Supplier<String> desc) { super.description(desc); return this; }

    @Override public int baseHeight() { return CONTROL_H; }
    @Override public int height() { return CONTROL_H + descriptionHeight(lastWidth); }

    /**
     * Pre-dim surface (§6 convention 6): the embedded shared Button already
     * carries the split — this drives its glass pass at the row's live
     * geometry (the same rect render computes), so on a screen running the
     * structural pass the button's surface paints pre-dim and its
     * renderOverlay paints the label only. The Button itself stamps the
     * frame; when no pass ran, it keeps painting in place (legacy order).
     */
    @Override
    public void renderGlassPass(GuiGraphics ctx, int x, int y, int width) {
        if (!com.aurora.client.ui.component.GlassSurface.passOpen()) return; // legacy frame order
        int btnX = x + width - BTN_W - 14;
        int btnY = y + (CONTROL_H - BTN_H) / 2;
        button.layout(btnX, btnY, BTN_W, BTN_H);
        button.renderGlassPass(ctx, btnX, btnY, BTN_W, BTN_H);
    }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;

        // Left side of the row is the label; the button sits on the right.
        renderLabelWithTooltip(ctx, label, x + 12, y + (CONTROL_H - tr.lineHeight) / 2,
                AuroraTheme.TEXT_PRIMARY, mouseX, mouseY);

        int btnX = x + width - BTN_W - 14;
        int btnY = y + (CONTROL_H - BTN_H) / 2;

        button.layout(btnX, btnY, BTN_W, BTN_H);
        button.render(ctx, btnX, btnY, BTN_W, BTN_H, mouseX, mouseY);

        renderDescription(ctx, x, y + CONTROL_H, width);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (button != 0) return false;
        return this.button.mouseClicked(mouseX, mouseY, button);
    }
}
