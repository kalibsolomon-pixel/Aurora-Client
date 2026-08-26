package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.TotemPopFeature;
import com.aurora.client.util.CachedValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Multi-line totem-pop readout. Top line is always the local player's
 * count. When {@link AuroraConfig#totemShowOthers} is on, up to
 * {@link AuroraConfig#totemTopOthers} additional lines list the highest
 * pop counts among nearby players, sorted descending.
 */
public class TotemPopModule extends HudModule {
    public static final String ID = "totem_pop";

    private final CachedValue<List<String>> linesCache = new CachedValue<>(150L, this::computeLines);

    public TotemPopModule() {
        super(ID);
        this.anchor = com.aurora.client.hud.HudAnchor.TOP_LEFT;
        this.offsetX = 4;
        this.offsetY = 104;
    }

    @Override public boolean isConfigEnabled() { return AuroraConfig.get().totemPopEnabled; }
    @Override protected AuroraConfig.HudBackground backgroundMode() { return AuroraConfig.get().totemPopBgMode; }
    @Override protected int backgroundColor() { return AuroraConfig.get().totemPopBgColor; }

    private List<String> computeLines() {
        AuroraConfig cfg = AuroraConfig.get();
        TotemPopFeature feat = TotemPopFeature.get();
        if (feat == null) return List.of("Pops: 0");

        List<String> out = new ArrayList<>(1 + cfg.totemTopOthers);
        out.add("Pops: " + feat.selfPops());

        if (cfg.totemShowOthers) {
            // Sort by pop count descending; ties broken by insertion
            // order via a stable sort.
            List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(feat.otherPops().entrySet());
            sorted.sort(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed());
            int limit = Math.min(cfg.totemTopOthers, sorted.size());
            for (int i = 0; i < limit; i++) {
                Map.Entry<UUID, Integer> e = sorted.get(i);
                out.add(feat.nameOf(e.getKey()) + ": " + e.getValue());
            }
        }
        return out;
    }

    @Override
    public int getWidth() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return 60;
        Font tr = client.font;
        int max = 0;
        for (String s : linesCache.get()) {
            int w = tr.width(s);
            if (w > max) max = w;
        }
        return Math.max(max, 40);
    }

    @Override
    public int getHeight() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return 10;
        int n = linesCache.get().size();
        return Math.max(1, n) * (client.font.lineHeight + 1);
    }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft client, int x, int y) {
        if (!AuroraConfig.get().totemPopEnabled) return;
        Font tr = client.font;
        if (tr == null) return;
        int color = AuroraConfig.get().hudColor;
        int lh = tr.lineHeight + 1;
        List<String> lines = linesCache.get();
        for (int i = 0; i < lines.size(); i++) {
            ctx.drawString(tr, lines.get(i), x, y + i * lh, color, false);
        }
    }

    @Override public String featureRegistryId() { return "totem_pop"; }
}
