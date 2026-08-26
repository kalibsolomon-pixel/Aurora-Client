package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.ClickTrackerFeature;
import com.aurora.client.util.CachedValue;
import com.aurora.client.util.WorldScope;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** FPS / XYZ / time. The original Aurora HUD, now as a movable module. */
public class InfoModule extends HudModule {
    public static final String ID = "info";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Refresh dynamic strings ~10 times per second instead of every frame.
    // Imperceptible to the user, free 90%+ of the per-frame cost.
    private final CachedValue<List<String>> linesCache = new CachedValue<>(100L, this::computeLines);

    public InfoModule() {
        super(ID);
    }

    private List<String> computeLines() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return List.of();
        AuroraConfig cfg = AuroraConfig.get();
        LocalPlayer player = client.player;
        ClientLevel level = client.level;

        List<String> lines = new ArrayList<>(8);
        if (cfg.showFps) lines.add("FPS: " + client.getFps());

        if (cfg.showCoords && player != null) {
            BlockPos p = player.blockPosition();
            lines.add("XYZ: " + p.getX() + " / " + p.getY() + " / " + p.getZ());
        }

        // Survival QoL rows — every line guarded on the toggle AND on
        // having a live player + level, so the module never NPEs while
        // the player is on the title screen / mid-dimension-change.
        if (cfg.infoShowFacing && player != null) {
            lines.add(formatFacing(player.getYRot(), player.getDirection()));
        }
        if (cfg.infoShowChunkLocal && player != null) {
            BlockPos p = player.blockPosition();
            // Mojang's chunk-local arithmetic: bottom 4 bits of world coord.
            // Works for both positive and negative world coords because it's
            // a bitmask, not a modulo.
            lines.add("In-chunk: " + (p.getX() & 15) + ", " + (p.getZ() & 15));
        }
        if (cfg.infoShowChunkCoords && player != null) {
            ChunkPos cp = player.chunkPosition();
            lines.add("Chunk: " + cp.x + ", " + cp.z);
        }
        if (cfg.infoShowYRelSea && player != null && level != null) {
            int y = player.blockPosition().getY();
            int sea = level.getSeaLevel();
            int delta = y - sea;
            String sign = delta >= 0 ? "+" : "";
            lines.add("Y: " + y + " (" + sign + delta + ")");
        }
        if (cfg.infoShowBiome && player != null && level != null) {
            Holder<Biome> biome = level.getBiome(player.blockPosition());
            String name = biome.unwrapKey()
                    .map(k -> shortId(k.identifier()))
                    .orElse("?");
            lines.add("Biome: " + name);
        }
        if (cfg.infoShowDimension && level != null) {
            lines.add("Dim: " + shortId(level.dimension().identifier()));
        }
        if (cfg.infoShowLightAtFeet && player != null && level != null) {
            BlockPos p = player.blockPosition();
            int sky   = level.getBrightness(LightLayer.SKY,   p);
            int block = level.getBrightness(LightLayer.BLOCK, p);
            int max   = Math.max(sky, block);
            lines.add("Light: " + max + " (sky " + sky + " / block " + block + ")");
        }

        // Performance & input telemetry.
        if (cfg.infoShowFrameTime) {
            int fps = client.getFps();
            double ms = fps > 0 ? 1000.0 / fps : 0.0;
            lines.add(String.format(java.util.Locale.ROOT, "Frame: %.1f ms", ms));
        }
        if (cfg.infoShowMemory) {
            Runtime rt = Runtime.getRuntime();
            long max = rt.maxMemory();
            long used = rt.totalMemory() - rt.freeMemory();
            long pct = max > 0 ? used * 100L / max : 0L;
            lines.add("Mem: " + pct + "% (" + (used >> 20) + "/" + (max >> 20) + " MB)");
        }
        if (cfg.infoShowPing && player != null && client.getConnection() != null) {
            net.minecraft.client.multiplayer.PlayerInfo entry =
                    client.getConnection().getPlayerInfo(player.getUUID());
            if (entry != null) lines.add("Ping: " + entry.getLatency() + " ms");
        }
        if (cfg.infoShowCps) {
            lines.add("CPS: L" + ClickTrackerFeature.LEFT.get()
                    + " R" + ClickTrackerFeature.RIGHT.get());
        }

        if (cfg.showTime) lines.add("Time: " + LocalTime.now().format(TIME_FMT));

        if (cfg.infoShowPlaytime) {
            long ms;
            if (cfg.infoPlaytimePerWorld) {
                String scope = WorldScope.current();
                ms = "none".equals(scope) ? 0L : cfg.playtimePerWorld.getOrDefault(scope, 0L);
            } else {
                ms = cfg.playtimeTotalMs;
            }
            lines.add("Played: " + formatDuration(ms));
        }

        return lines;
    }

    /**
     * Compact human-readable duration. Tiers:
     * {@code <1m → "12s"}, {@code <1h → "5m 12s"},
     * {@code <1d → "2h 5m"}, otherwise {@code "3d 2h"}.
     * Always exactly two tiers so the column width stays bounded.
     */
    private static String formatDuration(long ms) {
        if (ms < 0L) ms = 0L;
        long s = ms / 1000L;
        long m = s / 60L; s %= 60L;
        long h = m / 60L; m %= 60L;
        long d = h / 24L; h %= 24L;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    /**
     * Cardinal direction + raw yaw. Yaw is normalized to (-180, 180] so the
     * sign cleanly indicates left/right of due south (the vanilla zero
     * heading), which lines up with the F3 readout players are used to.
     */
    private static String formatFacing(float yawDeg, Direction facing) {
        float y = yawDeg % 360f;
        if (y > 180f) y -= 360f;
        else if (y < -180f) y += 360f;
        String cardinal = switch (facing) {
            case NORTH -> "N";
            case SOUTH -> "S";
            case EAST  -> "E";
            case WEST  -> "W";
            default    -> "?"; // UP/DOWN never returned by getDirection() for a player
        };
        return "Facing: " + cardinal + " (" + Math.round(y) + "\u00B0)";
    }

    /** "minecraft:plains" → "plains"; foreign namespaces keep the prefix so it's still unambiguous. */
    private static String shortId(Identifier id) {
        if (id == null) return "?";
        return "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    @Override protected AuroraConfig.HudBackground backgroundMode() { return AuroraConfig.get().infoBgMode; }
    @Override protected int backgroundColor() { return AuroraConfig.get().infoBgColor; }

    @Override
    public boolean isConfigEnabled() {
        return AuroraConfig.get().infoEnabled;
    }

    @Override
    public int getWidth() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return 80;
        Font tr = client.font;
        int max = 0;
        List<String> lines = linesCache.get();
        for (int i = 0; i < lines.size(); i++) {
            int w = tr.width(lines.get(i));
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
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.infoEnabled) return;
        Font tr = client.font;
        if (tr == null) return;
        int color = cfg.hudColor;
        int lh = tr.lineHeight + 1;
        List<String> lines = linesCache.get();
        for (int i = 0; i < lines.size(); i++) {
            ctx.drawString(tr, lines.get(i), x, y + i * lh, color, false);
        }
    }
    @Override public String featureRegistryId() { return "info_module"; }
}