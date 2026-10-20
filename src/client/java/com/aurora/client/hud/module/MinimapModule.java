package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.AuroraConfig.Waypoint;
import com.aurora.client.feature.impl.WaypointFeature;
import com.aurora.client.hud.HudAnchor;
import com.aurora.client.theme.HudText;
import com.aurora.client.theme.HudStatus;
import com.aurora.client.worldmap.MapTransform;
import com.aurora.client.worldmap.MinimapGrid;
import com.aurora.client.worldmap.WorldMapClient;
import com.aurora.client.worldmap.MapMarkers;
import com.aurora.client.ui.util.UiLayerCache;
import com.aurora.client.ui.util.RenderUtil;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Cached presentation of the shared map engine. No world-column reads in rendering.
 * Engine samples live in a world-aligned {@link MinimapGrid} that is only re-sampled
 * when the player crosses a cell (overlap reused) or terrain changes (at most 20 Hz).
 * Each frame with motion resamples that grid at device resolution from the
 * partial-tick-interpolated pose, so terrain, markers and rotation glide with the
 * camera instead of stepping at tick/sample cadence; a stationary map costs nothing.
 * Shape/background/border share the same masked texture (one blit). */
public final class MinimapModule extends HudModule {
    public static final String ID = "minimap";
    private static final String[] CARDINALS = {"N", "E", "S", "W"};
    private UiLayerCache terrain = new UiLayerCache();
    private final java.util.ArrayList<UiLayerCache> retiredTerrains = new java.util.ArrayList<>();
    private long terrainFrame = -1;
    private final MinimapGrid grid = new MinimapGrid();
    private final MinimapGrid.Sampler sampler = this::sampleCell;
    private WorldMapClient samplerEngine;
    private boolean samplerTerrain;
    private double samplerCell = 1;
    private long lastGridSample;
    private int outputSize;
    private int textureSize = -1, previousZoom, previousBorder, previousAlpha;
    private boolean previousCircle, previousTerrain, previousRotate;
    private double previousX = Double.NaN, previousZ;
    private float previousYaw;
    private float previousRadius;
    private long previousRevision = -1, lastPaint;
    private Object previousLevel;
    private List<Entity> entities = List.of();
    private long lastEntities;
    private Object entityLevel;
    private final com.aurora.client.worldmap.MarkerLabelLayout distanceLabels = new com.aurora.client.worldmap.MarkerLabelLayout();
    private final int[] cardinalX = new int[4], cardinalY = new int[4], cardinalWidth = new int[4];

    public MinimapModule() {
        super(ID);
        resetLayoutToDefaults();
        // Fabric disconnect may arrive on Netty. GPU resources and cache state
        // belong to the render/client thread, including during reconnect.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(() -> { release(); MapMarkers.dispose(); }));
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> { release(); MapMarkers.dispose(); });
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
        long frame = com.aurora.client.ui.component.GlassSurface.frame();
        if (terrainFrame != frame) {
            for (UiLayerCache retired : retiredTerrains) retired.dispose();
            retiredTerrains.clear(); terrainFrame = frame;
        }
        var cfg = AuroraConfig.get();
        int size = size(), zoom = zoom();
        int border = HudText.color(cfg.minimapBorderColor);
        float shapeRadius = cfg.minimapCircular ? size * .5f
                : com.aurora.client.theme.ThemeManager.current().roundness().radiusSmall();
        int alpha = (int) (MapTransform.bounded(cfg.minimapTerrainAlpha, 0, 1) * 255);
        // Interpolate to the frame's camera pose (the same partial tick the camera
        // uses), otherwise everything on the map steps at 20 TPS.
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double px = Mth.lerp(partial, mc.player.xo, mc.player.getX());
        double pz = Mth.lerp(partial, mc.player.zo, mc.player.getZ());
        float yaw = mc.player.getViewYRot(partial);
        double rotation = MapTransform.rotation(yaw, cfg.minimapRotateWithPlayer);
        double cos = Math.cos(rotation), sin = Math.sin(rotation);
        WorldMapClient engine = WorldMapClient.get();
        long revision = engine == null ? -1 : engine.terrainRevision();
        long now = System.nanoTime();
        int physicalSize = Math.max(1, Math.min(2048, Math.round(size * MapMarkers.density(g))));
        double blocksPerTexel = zoom / (double) size;
        // One cell per block when zoomed in; one cell per texel when zoomed out.
        double cell = Math.max(1, blocksPerTexel);
        int reach = (int) Math.ceil(size * .5 * blocksPerTexel * (cfg.minimapRotateWithPlayer ? Math.sqrt(2) : 1) / cell) + 2;
        int cells = reach * 2 + 1;
        int originX = (int) Math.floor(px / cell) - reach, originZ = (int) Math.floor(pz / cell) - reach;
        boolean changed = previousLevel != mc.level || textureSize != size || outputSize != physicalSize || previousZoom != zoom || previousRadius != shapeRadius
                || previousCircle != cfg.minimapCircular || previousRotate != cfg.minimapRotateWithPlayer
                || previousTerrain != cfg.minimapShowTerrain || previousBorder != border || previousAlpha != alpha
                || grid.size() != cells;
        samplerEngine = engine; samplerTerrain = cfg.minimapShowTerrain; samplerCell = cell;
        boolean gridUpdated = false;
        if (changed || !grid.valid() || (previousRevision != revision && now - lastGridSample >= 50_000_000L)) {
            if (engine != null && cfg.minimapShowTerrain) engine.requestMinimap(px, pz, zoom);
            grid.reset(cells, originX, originZ, sampler);
            previousRevision = revision; lastGridSample = now; gridUpdated = true;
        } else if (grid.moveTo(originX, originZ, sampler)) {
            if (engine != null && cfg.minimapShowTerrain) engine.requestMinimap(px, pz, zoom);
            gridUpdated = true;
        }
        float density = physicalSize / (float) size;
        // Repaint once the pose moved a fifth of a device pixel (centre or rim).
        boolean motion = (Math.abs(px - previousX) + Math.abs(pz - previousZ)) / blocksPerTexel * density > .2
                || (cfg.minimapRotateWithPlayer && Math.abs(Math.toRadians(yaw - previousYaw)) * size * .5 * density > .2);
        if (textureSize < 0 || changed || gridUpdated || motion) {
            ensureTexture(size, physicalSize);
            // Device pixel (X, Y) -> logical offset s = (X + .5) / density - size / 2 ->
            // world offset R(rotation) * s * blocksPerTexel -> grid cell (world / cell - origin).
            double k = blocksPerTexel / cell, step = k / density, half = .5 / density - size * .5;
            double u0 = px / cell - grid.originX() + k * (half * cos - half * sin);
            double v0 = pz / cell - grid.originZ() + k * (half * sin + half * cos);
            terrain.writeAffineAbgr(grid.cells(), grid.size(), u0, v0,
                    step * cos, step * sin, -step * sin, step * cos, alpha, EMPTY_ABGR);
            float gui = mc.getWindow().getGuiScale();
            terrain.clipShape(g, cfg.minimapCircular, shapeRadius * density);
            RenderUtil.RectSink previous = RenderUtil.beginCapture(terrain.sink());
            try {
                RenderUtil.drawRoundedOutlineAA(g, 0, 0, physicalSize / gui, physicalSize / gui,
                        shapeRadius * density / gui, density / gui, border);
            } finally { RenderUtil.endCapture(previous); }
            terrain.commit(now);
            previousX = px; previousZ = pz; previousYaw = yaw;
            previousLevel = mc.level; previousZoom = zoom;
            previousCircle = cfg.minimapCircular; previousRotate = cfg.minimapRotateWithPlayer;
            previousTerrain = cfg.minimapShowTerrain; previousBorder = border; previousAlpha = alpha;
            lastPaint = now;
            previousRadius = shapeRadius;
        }
        MapMarkers.blitPixels(g, terrain, MapMarkers.pixelX(g, x + 1, y + 1), MapMarkers.pixelY(g, x + 1, y + 1),
                Math.round(size * MapMarkers.density(g)), Math.round(size * MapMarkers.density(g)), -1);
        double centerX = x + 1 + size * .5, centerY = y + 1 + size * .5;
        double scale = size / (double) zoom;
        double limit = size * .5 - 6;
        distanceLabels.reset();
        if (cfg.minimapShowCompass) for (int i = 0; i < 4; i++) {
            double angle = i * Math.PI * .5 - rotation;
            cardinalX[i] = (int) Math.round(centerX + Math.sin(angle) * (size * .5 - 10));
            cardinalY[i] = (int) Math.round(centerY - Math.cos(angle) * (size * .5 - 10));
            cardinalWidth[i] = mc.font.width(CARDINALS[i]);
            distanceLabels.reserve(cardinalX[i] - cardinalWidth[i] * .5f - 2, cardinalY[i] - 6,
                    cardinalWidth[i] + 4, mc.font.lineHeight + 4);
        }
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
                if (clamp < 1 && cfg.minimapShowWaypointDistance && labels < 4) {
                    String text = Math.round(Math.hypot(dx, dz)) + "m";
                    int width = mc.font.width(text);
                    int tx = (int) MapTransform.bounded(markerX - width * .5,
                            x + 8, x + size - width - 8);
                    // Keep labels towards the map interior, inside a circular mask as well.
                    int ty = markerY < centerY ? markerY + 7 : markerY - 13;
                    for (int attempt = 0; attempt < 2; attempt++) {
                        int rowY = ty + attempt * (markerY < centerY ? 1 : -1) * (mc.font.lineHeight + 3);
                        if (cfg.minimapCircular && Math.hypot(tx + width * .5 - centerX, rowY + 4 - centerY) >= limit - width * .5) continue;
                        if (!distanceLabels.place(tx + width * .5f, rowY + mc.font.lineHeight,
                                width + 4, mc.font.lineHeight + 2, g.guiWidth(), g.guiHeight())) continue;
                        MapMarkers.text(g, mc.font, text, distanceLabels.centerX() - width * .5f,
                                distanceLabels.bottomY() - mc.font.lineHeight, 0xFFFFFFFF, true);
                        labels++; break;
                    }
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
                double dx = Mth.lerp(partial, entity.xo, entity.getX()) - px;
                double dz = Mth.lerp(partial, entity.zo, entity.getZ()) - pz;
                double sx = MapTransform.screenX(dx, dz, cos, sin) * scale;
                double sy = MapTransform.screenZ(dx, dz, cos, sin) * scale;
                if (MapTransform.clampScale(sx, sy, limit, cfg.minimapCircular) < 1) continue;
                int ex = (int) Math.round(centerX + sx), ey = (int) Math.round(centerY + sy);
                MapMarkers.dot(g, ex, ey, color); drawn++;
            }
        }
        if (cfg.minimapShowSelfArrow) {
            double heading = Math.toRadians(yaw) - rotation;
            MapMarkers.minimapPlayer(g, (int) centerX, (int) centerY, -Math.sin(heading), Math.cos(heading));
        }
        if (cfg.minimapShowCompass) {
            for (int i = 0; i < 4; i++) {
                MapMarkers.text(g, mc.font, CARDINALS[i], cardinalX[i] - cardinalWidth[i] * .5f,
                        cardinalY[i] - 4, border, true);
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
        MapMarkers.text(g, mc.font, fitted, x + (getWidth() - mc.font.width(fitted)) * .5f, y, color, true);
    }

    private void ensureTexture(int size, int physicalSize) {
        if (textureSize != size || outputSize != physicalSize) {
            // HUD editor can resize after the ordinary HUD has already queued a blit.
            // Keep that texture alive until the next frame's submission boundary.
            retiredTerrains.add(terrain); terrain = new UiLayerCache();
            terrain.ensureSize(physicalSize, physicalSize);
        }
        textureSize = size; outputSize = physicalSize;
    }

    /** Background for cells without loaded terrain (and outside the grid). */
    private static final int EMPTY_ABGR = 0xFF171412;

    /** One engine sample for a world-aligned cell (its centre block). */
    private int sampleCell(int cellX, int cellZ) {
        if (!samplerTerrain || samplerEngine == null) return EMPTY_ABGR;
        int sampled = samplerEngine.sampleSurfaceAbgr((int) Math.floor((cellX + .5) * samplerCell),
                (int) Math.floor((cellZ + .5) * samplerCell));
        return (sampled >>> 24) != 0 ? sampled : EMPTY_ABGR;
    }

    private void release() {
        terrain.dispose(); grid.invalidate(); samplerEngine = null; textureSize = -1; outputSize = 0;
        for (UiLayerCache retired : retiredTerrains) retired.dispose();
        retiredTerrains.clear(); terrainFrame = -1;
        previousX = Double.NaN; previousRevision = -1; previousLevel = null;
        entities = List.of(); lastEntities = 0; entityLevel = null;
    }

    @Override public void resetLayoutToDefaults() {
        anchor = HudAnchor.TOP_RIGHT; offsetX = -4; offsetY = 4;
        scale = 1; enabled = true; locked = false;
    }
}
