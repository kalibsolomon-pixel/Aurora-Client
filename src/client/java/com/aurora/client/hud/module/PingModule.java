package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.CachedValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Single-line "Ping: 42 ms" HUD module showing the local player's latency
 * to the current server. Latency comes from the server-supplied playerlist
 * entry; in singleplayer or when the value isn't available it shows "—".
 *
 * <p>Latency only updates every server tick; querying every frame is
 * wasteful, so we cache for 200ms.
 */
public class PingModule extends HudModule {
    public static final String ID = "ping";

    private final CachedValue<Integer> cachedLatency =
            new CachedValue<>(200L, this::queryLatency);

    public PingModule() {
        super(ID);
        this.anchor = com.aurora.client.hud.HudAnchor.TOP_LEFT;
        this.offsetX = 4;
        this.offsetY = 80;
    }

    private int queryLatency() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return -1;
        if (mc.getConnection() == null) return -1;
        PlayerInfo entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return entry == null ? -1 : entry.getLatency();
    }

    private String buildText() {
        int ms = cachedLatency.get();
        if (ms < 0) return "Ping: —";
        return "Ping: " + ms + " ms";
    }

    @Override public boolean isConfigEnabled() { return AuroraConfig.get().pingHudEnabled; }

    @Override
    public int getWidth() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return 60;
        return client.font.width(buildText());
    }

    @Override
    public int getHeight() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return 10;
        return client.font.lineHeight;
    }

    @Override protected AuroraConfig.HudBackground backgroundMode() { return AuroraConfig.HudBackground.NONE; }
    @Override protected int backgroundColor() { return 0x80000000; }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft client, int x, int y) {
        if (!AuroraConfig.get().pingHudEnabled) return;
        Font tr = client.font;
        if (tr == null) return;
        int ms = cachedLatency.get();
        int color = ms < 0 ? AuroraConfig.get().hudColor : pingColor(ms);
        ctx.drawString(tr, buildText(), x, y, color, false);
    }

    /** Same color algorithm as the tab-list ping — green→yellow→red. */
    public static int pingColor(int ms) {
        if (ms < 80)  return 0xFF55FF55;
        if (ms < 150) return 0xFFCCFF55;
        if (ms < 250) return 0xFFFFFF55;
        if (ms < 400) return 0xFFFFAA33;
        return 0xFFFF5555;
    }
    @Override public String featureRegistryId() { return "ping_hud"; }
}