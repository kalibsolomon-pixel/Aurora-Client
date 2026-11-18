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
 * to two decimals ("19.47"), with absorption after it in gold. Its own switch,
 * independent of the health drawn above entities.
 */
public class HealthHudModule extends HudModule {
    public static final String ID = "health";

    private static final Identifier HEART = Identifier.withDefaultNamespace("hud/heart/full");
    private static final int ICON = 9;
    private static final int ICON_GAP = 2;
    private static final int ABSORPTION_COLOR = 0xFFFFAA00;

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

    @Override public String displayName() { return "Health"; }
    @Override public String featureRegistryId() { return "player_health"; }

    private void refresh() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) readout.update(20.0f, 0.0f);
        else readout.update(player.getHealth(), player.getAbsorptionAmount());
    }

    @Override
    public int getWidth() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return 40;
        refresh();
        int w = ICON + ICON_GAP + client.font.width(readout.health());
        if (!readout.absorption().isEmpty()) w += 2 + client.font.width(readout.absorption());
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
        int color = com.aurora.client.theme.HudText.color(AuroraConfig.get().hudColor);
        com.aurora.client.hud.HudTextPainter.draw(ctx, font, readout.health(), tx, ty, color);
        if (!readout.absorption().isEmpty()) {
            com.aurora.client.hud.HudTextPainter.draw(ctx, font, readout.absorption(),
                    tx + font.width(readout.health()) + 2, ty, ABSORPTION_COLOR);
        }
    }
}
