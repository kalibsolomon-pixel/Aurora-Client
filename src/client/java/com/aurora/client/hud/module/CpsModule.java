package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.ClickTrackerFeature;
import com.aurora.client.util.CachedValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * "L: 5 CPS   R: 2 CPS" single-line display.
 *
 * <p>Component is rebuilt at most every 100 ms â€” CPS values are integers and
 * change at most ~10 Hz for a fast clicker, so the cap is imperceptible
 * and saves 4 string concatenations on every render frame that would
 * otherwise occur in both getWidth() and renderContent().
 */
public class CpsModule extends HudModule {
    public static final String ID = "cps";

    private final CachedValue<String> textCache = new CachedValue<>(100L, this::buildText);

    public CpsModule() {
        super(ID);
        this.anchor = com.aurora.client.hud.HudAnchor.TOP_LEFT;
        this.offsetX = 4;
        this.offsetY = 60;
    }

    private String buildText() {
        return "L: " + ClickTrackerFeature.LEFT.get() + " CPS   "
                + "R: " + ClickTrackerFeature.RIGHT.get() + " CPS";
    }

    @Override protected AuroraConfig.HudBackground backgroundMode() { return AuroraConfig.get().cpsBgMode; }
    @Override protected int backgroundColor() { return AuroraConfig.get().cpsBgColor; }

    @Override
    public int getWidth() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return 120;
        return client.font.width(textCache.get());
    }
    @Override
    public boolean isConfigEnabled() {
        return AuroraConfig.get().cpsEnabled;
    }
    @Override
    public int getHeight() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return 10;
        return client.font.lineHeight;
    }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft client, int x, int y) {
        if (!AuroraConfig.get().cpsEnabled) return;
        Font tr = client.font;
        if (tr == null) return;
        ctx.drawString(tr, textCache.get(), x, y,
                com.aurora.client.theme.HudText.color(AuroraConfig.get().hudColor), false);
    }
}