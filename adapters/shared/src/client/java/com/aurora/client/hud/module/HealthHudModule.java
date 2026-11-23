package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.HealthReadout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Health Indicators' HUD readout: vanilla's heart sprite and your own health
 * to two decimals ("19.47"), absorption included ("28.00" after a golden
 * apple). Its own switch, independent of the health drawn above entities.
 */
public class HealthHudModule extends HudModule {
    public static final String ID = "health";

    private static final Identifier HEART = Identifier.withDefaultNamespace("hud/heart/full");
    private static final int ICON = 9;
    private static final int ICON_GAP = 2;

    private final HealthReadout readout = new HealthReadout();

    public HealthHudModule() {
        super(ID);
        // Just above the armor row, at the left end of the vanilla health bar.
        this.anchor = com.aurora.client.hud.HudAnchor.BOTTOM_CENTER;
        this.offsetX = -64;
        this.offsetY = -51;
    }

    @Override
    public boolean isConfigEnabled() {
        return AuroraConfig.get().playerHealthHudEnabled;
    }

    @Override protected AuroraConfig.HudBackground backgroundMode() { return AuroraConfig.get().playerHealthHudBgMode; }
    @Override protected int backgroundColor() { return AuroraConfig.get().playerHealthHudBgColor; }

    @Override public String displayName() { return "Health"; }
    @Override public String featureRegistryId() { return "player_health"; }

    private void refresh() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) readout.update(20.0f, 0.0f);
        else readout.update(player.getHealth(), player.getAbsorptionAmount());
    }

    /** Layout and the background ask for the width several times a frame; measure once per frame. */
    private long widthFrame = Long.MIN_VALUE;
    private int frameWidth;

    @Override
    public int getWidth() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return 40;
        long frame = com.aurora.client.ui.component.GlassSurface.frame();
        if (frame == widthFrame) return frameWidth;
        refresh();
        int w = ICON + ICON_GAP + client.font.width(readout.text());
        widthFrame = frame;
        frameWidth = w;
        return w;
    }

    @Override
    public int getHeight() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return ICON;
        return Math.max(ICON, client.font.lineHeight);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, Minecraft client, int x, int y) {
        Font font = client.font;
        if (font == null) return;
        refresh();
        int h = getHeight();
        ctx.blitSprite(RenderPipelines.GUI_TEXTURED, HEART, x, y + (h - ICON) / 2, ICON, ICON);
        int tx = x + ICON + ICON_GAP;
        int ty = y + (h - font.lineHeight) / 2 + 1;
        int color = com.aurora.client.theme.HudText.color(AuroraConfig.get().playerHealthHudTextColor, AuroraConfig.get().hudColor);
        com.aurora.client.hud.HudTextPainter.draw(ctx, font, readout.text(), tx, ty, color);
    }
}
