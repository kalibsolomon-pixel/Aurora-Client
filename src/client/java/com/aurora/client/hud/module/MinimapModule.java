package com.aurora.client.hud.module;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.AuroraConfig.Waypoint;
import com.aurora.client.feature.impl.WaypointFeature;
import com.aurora.client.hud.HudAnchor;
import com.aurora.client.theme.HudText;
import com.aurora.client.theme.HudStatus;
import com.aurora.client.worldmap.MapTransform;
import com.aurora.client.worldmap.WorldMapClient;
import com.aurora.client.worldmap.MapMarkers;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Cached presentation of the shared map engine. No world-column reads in rendering.
 * Terrain raster/upload is capped at 20 Hz during motion and stops when stationary
 * and unchanged. Shape/background/border share the same masked texture (one blit). */
public final class MinimapModule extends HudModule {
    public static final String ID = "minimap";
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "minimap_terrain");
    private static final String[] CARDINALS = {"N", "E", "S", "W"};
    private DynamicTexture texture;
    private NativeImage image;
    private int textureSize = -1, previousZoom, previousBorder, previousAlpha;
    private boolean previousCircle, previousTerrain, previousRotate;
    private double previousX = Double.NaN, previousZ;
    private float previousYaw;
    private long previousRevision = -1, lastPaint;
    private Object previousLevel;
    private List<Entity> entities = List.of();
    private long lastEntities;
    private Object entityLevel;

    public MinimapModule() {
        super(ID);
        resetLayoutToDefaults();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> release());
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> release());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!AuroraConfig.get().minimapEnabled || mc.level == null) release();
        });
    }

    private static int size() { return MapTransform.bounded(AuroraConfig.get().minimapSize, 64, 256); }
    private static int zoom() { return MapTransform.bounded(AuroraConfig.get().minimapZoom, 16, 512); }
    @Override public boolean isConfigEnabled() { return AuroraConfig.get().minimapEnabled; }
    @Override protected AuroraConfig.HudBackground backgroundMode() { return AuroraConfig.HudBackground.NONE; }
    @Override public String featureRegistryId() { return ID; }
    @Override public int getWidth() { return size() + 2; }
    @Override public int getHeight() {
        var cfg = AuroraConfig.get();
        return size() + 2 + ((cfg.minimapShowCoords ? 1 : 0) + (cfg.minimapShowBiome ? 1 : 0))
                * (Minecraft.getInstance().font.lineHeight + 2);
    }

    @Override protected void renderContent(GuiGraphics g, Minecraft mc, int x, int y) {
        if (mc.level == null || mc.player == null) return;
        var cfg = AuroraConfig.get();
        int size = size(), zoom = zoom();
        int border = HudText.color(cfg.minimapBorderColor);
        int alpha = (int) (MapTransform.bounded(cfg.minimapTerrainAlpha, 0, 1) * 255);
        double px = mc.player.getX(), pz = mc.player.getZ();
        float yaw = mc.player.getYRot();
        double rotation = MapTransform.rotation(yaw, cfg.minimapRotateWithPlayer);
        double cos = Math.cos(rotation), sin = Math.sin(rotation);
        WorldMapClient engine = WorldMapClient.get();
        long revision = engine == null ? -1 : engine.terrainRevision();
        long now = System.nanoTime();
        boolean changed = previousLevel != mc.level || textureSize != size || previousZoom != zoom
                || previousCircle != cfg.minimapCircular || previousRotate != cfg.minimapRotateWithPlayer
                || previousTerrain != cfg.minimapShowTerrain || previousBorder != border || previousAlpha != alpha;
        boolean motion = Math.abs(px - previousX) + Math.abs(pz - previousZ) > zoom / (double) size * .25
                || (cfg.minimapRotateWithPlayer && Math.abs(yaw - previousYaw) > .2);
        if (changed || ((motion || previousRevision != revision) && now - lastPaint >= 50_000_000L)) {
            ensureTexture(size);
            if (engine != null && cfg.minimapShowTerrain) engine.requestMinimap(px, pz, zoom);
            paint(engine, px, pz, size, zoom, cos, sin, cfg, border, alpha);
            texture.upload();
            previousX = px; previousZ = pz; previousYaw = yaw;
            previousLevel = mc.level; previousZoom = zoom;
            previousCircle = cfg.minimapCircular; previousRotate = cfg.minimapRotateWithPlayer;
            previousTerrain = cfg.minimapShowTerrain; previousBorder = border; previousAlpha = alpha;
            previousRevision = revision; lastPaint = now;
        }
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x + 1, y + 1, 0, 0, size, size, size, size);
        double centerX = x + 1 + size * .5, centerY = y + 1 + size * .5;
        double scale = size / (double) zoom;
        double limit = size * .5 - 6;
        WaypointFeature feature = WaypointFeature.get();
        if (cfg.minimapShowWaypoints && feature != null) {
            int labels = 0;
            for (Waypoint w : feature.nearbyWaypoints()) {
                double dx = w.x + .5 - px, dz = w.z + .5 - pz;
                double sx = MapTransform.screenX(dx, dz, cos, sin) * scale;
                double sy = MapTransform.screenZ(dx, dz, cos, sin) * scale;
                double clamp = MapTransform.clampScale(sx, sy, limit, cfg.minimapCircular);
                int markerX = (int) Math.round(centerX + sx * clamp);
                int markerY = (int) Math.round(centerY + sy * clamp);
                MapMarkers.waypoint(g, markerX, markerY, w, w.id.equals(feature.navigationId()));
                if (clamp < 1 && cfg.minimapShowWaypointDistance && labels++ < 4) {
                    String text = Math.round(Math.hypot(dx, dz)) + "m";
                    int width = mc.font.width(text);
                    int tx = (int) MapTransform.bounded(markerX - width * .5,
                            x + 8, x + size - width - 8);
                    // Keep labels towards the map interior, inside a circular mask as well.
                    int ty = markerY < centerY ? markerY + 7 : markerY - 13;
                    if (!cfg.minimapCircular || Math.hypot(tx + width * .5 - centerX, ty + 4 - centerY) < limit - width * .5)
                        g.drawString(mc.font, text, tx, ty, 0xFFFFFFFF, true);
                }
            }
        }
        if (cfg.minimapShowEntities && (cfg.minimapShowPlayers || cfg.minimapShowHostiles || cfg.minimapShowPassives)) {
            if (now - lastEntities > 100_000_000L || entityLevel != mc.level) {
                double radius = zoom * .75;
                entities = mc.level.getEntities(mc.player, new AABB(px - radius, mc.player.getY() - 64,
                        pz - radius, px + radius, mc.player.getY() + 64, pz + radius));
                lastEntities = now;
                entityLevel = mc.level;
            }
            int drawn = 0;
            for (Entity entity : entities) {
                if (drawn >= 128) break;
                if (!(entity instanceof LivingEntity) || entity.isInvisible() || entity.isRemoved()) continue;
                int color;
                if (entity instanceof Player) { if (!cfg.minimapShowPlayers) continue; color = 0xFFFFFFFF; }
                else if (entity instanceof Monster) { if (!cfg.minimapShowHostiles) continue; color = HudStatus.DOT_HOSTILE; }
                else { if (!cfg.minimapShowPassives) continue; color = entity instanceof Animal ? HudStatus.DOT_PASSIVE : HudStatus.DOT_NEUTRAL; }
                double dx = entity.getX() - px, dz = entity.getZ() - pz;
                double sx = MapTransform.screenX(dx, dz, cos, sin) * scale;
                double sy = MapTransform.screenZ(dx, dz, cos, sin) * scale;
                if (MapTransform.clampScale(sx, sy, limit, cfg.minimapCircular) < 1) continue;
                int ex = (int) Math.round(centerX + sx), ey = (int) Math.round(centerY + sy);
                g.fill(ex - 1, ey - 1, ex + 2, ey + 2, color); drawn++;
            }
        }
        if (cfg.minimapShowSelfArrow) {
            double heading = Math.toRadians(yaw) - rotation;
            MapMarkers.player(g, (int) centerX, (int) centerY, -Math.sin(heading), Math.cos(heading));
        }
        if (cfg.minimapShowCompass) {
            double radius = size * .5 - 10;
            for (int i = 0; i < 4; i++) {
                double angle = i * Math.PI * .5 - rotation;
                int tx = (int) Math.round(centerX + Math.sin(angle) * radius);
                int ty = (int) Math.round(centerY - Math.cos(angle) * radius);
                g.drawString(mc.font, CARDINALS[i], tx - mc.font.width(CARDINALS[i]) / 2, ty - 4, border, true);
            }
        }
        int textY = y + size + 4;
        if (cfg.minimapShowCoords) {
            var pos = mc.player.blockPosition();
            String coordinates = pos.getX() + " / " + pos.getY() + " / " + pos.getZ();
            drawCaption(g, mc, coordinates, x, textY, cfg.minimapCoordColor);
            textY += mc.font.lineHeight + 2;
        }
        if (cfg.minimapShowBiome) {
            String biome = mc.level.getBiome(mc.player.blockPosition()).unwrapKey()
                    .map(key -> key.identifier().getPath().replace('_', ' ')).orElse("Unknown biome");
            drawCaption(g, mc, biome, x, textY, cfg.minimapCoordColor);
        }
    }

    private void drawCaption(GuiGraphics g, Minecraft mc, String text, int x, int y, int color) {
        String fitted = mc.font.plainSubstrByWidth(text, getWidth() - 6);
        if (!fitted.equals(text)) fitted = mc.font.plainSubstrByWidth(text, getWidth() - 18) + "…";
        g.drawString(mc.font, fitted, x + (getWidth() - mc.font.width(fitted)) / 2, y, color, true);
    }

    private void ensureTexture(int size) {
        if (image != null && textureSize == size) return;
        release();
        image = new NativeImage(NativeImage.Format.RGBA, size, size, true);
        texture = new DynamicTexture(() -> "minimap", image);
        Minecraft.getInstance().getTextureManager().register(TEXTURE, texture);
        textureSize = size;
    }

    private void paint(WorldMapClient engine, double px, double pz, int size, int zoom,
                       double cos, double sin, AuroraConfig cfg, int border, int alpha) {
        double center = size * .5, radius = center - .5;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            double sx = x + .5 - center, sy = y + .5 - center;
            double distance = cfg.minimapCircular ? Math.hypot(sx, sy) : Math.max(Math.abs(sx), Math.abs(sy));
            if (distance > radius + .5) { image.setPixel(x, y, 0); continue; }
            if (distance >= radius - 1) {
                int a = (int) ((border >>> 24) * Math.min(1, Math.max(0, radius + .5 - distance)));
                image.setPixel(x, y, (a << 24) | (border & 0xFFFFFF)); continue;
            }
            int color = 0xFF171412; // ABGR unexplored/background
            if (cfg.minimapShowTerrain && engine != null) {
                double blocks = zoom / (double) size;
                int wx = (int) Math.floor(px + (sx * cos - sy * sin) * blocks);
                int wz = (int) Math.floor(pz + (sx * sin + sy * cos) * blocks);
                int terrain = engine.sampleSurfaceAbgr(wx, wz);
                if ((terrain >>> 24) != 0) color = terrain;
            }
            image.setPixelABGR(x, y, (alpha << 24) | (color & 0xFFFFFF));
        }
    }

    private void release() {
        if (texture != null) Minecraft.getInstance().getTextureManager().release(TEXTURE);
        else if (image != null) image.close();
        texture = null; image = null; textureSize = -1;
        previousX = Double.NaN; previousRevision = -1; previousLevel = null;
        entities = List.of(); lastEntities = 0; entityLevel = null;
    }

    @Override public void resetLayoutToDefaults() {
        anchor = HudAnchor.TOP_RIGHT; offsetX = -4; offsetY = 4;
        scale = 1; enabled = true; locked = false;
    }
}
