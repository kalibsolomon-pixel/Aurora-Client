package com.aurora.client.worldmap.screen;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.AuroraConfig.Waypoint;
import com.aurora.client.feature.impl.WaypointFeature;
import com.aurora.client.screen.FeatureDetailScreen;
import com.aurora.client.screen.FeatureRegistry;
import com.aurora.client.screen.WaypointEditScreen;
import com.aurora.client.screen.WaypointManagerScreen;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.worldmap.*;
import com.aurora.client.worldmap.cache.OverviewCache;
import com.aurora.client.worldmap.cache.RegionCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Unified explored map: cursor-anchored smooth zoom, clipped region LOD,
 * separate archive browsing, shared waypoint editor and coordinate navigation. */
public final class WorldMapScreen extends Screen implements ThemedScreen {
    private final MapViewport viewport = new MapViewport();
    private boolean follow = true, dragging;
    private ButtonWidget dimensionButton;
    private EditBox coordinates;
    private long lastFrame;
    private List<Waypoint> markers = List.of();
    private long markerRevision = -1, lastMarkers;
    private double markerX, markerZ, markerScale;
    private String markerDimension = "";
    private String status = "";
    private final MarkerLabelLayout nameLayout = new MarkerLabelLayout();

    public WorldMapScreen() { super(Component.literal("World Map")); }

    // ---- Chrome geometry (premium-design audit, 2026-10-19) ----
    // One top toolbar bar (navigation leading, screen actions trailing, Done
    // last) and one slim bottom status bar. On narrow windows the toolbar
    // wraps to two rows instead of overlapping. Everything between the bars
    // is map; click/scroll guards use these same bounds.
    private static final int BAR_PAD = 4, CTRL_H = 22, GAP = 6, STATUS_H = 20;
    private int topBarH = 30;
    private int topBarBottom() { return BAR_PAD + topBarH; }
    private int statusTop() { return height - BAR_PAD - STATUS_H; }

    @Override protected void init() {
        viewport.constrainSize(width, height);
        int inner = width - 2 * BAR_PAD - 2 * 6;
        int left = BAR_PAD + 6, right = width - BAR_PAD - 6;
        int dimW = 100, coordW = 96, goW = 36, doneW = 52;
        int lead = dimW + GAP + coordW + 4 + goW;
        int wideAction = 70;
        int trail = 3 * wideAction + 2 * GAP + GAP + doneW;
        boolean wide = lead + 12 + trail <= inner;
        int actionW = wide ? wideAction : Math.max(40, (inner - dimW - 3 * GAP) / 3);
        topBarH = wide ? 30 : 56;
        int row1 = BAR_PAD + 4, row2 = row1 + CTRL_H + 4;
        dimensionButton = addRenderableWidget(ButtonWidget.semantic(left, row1, dimW, CTRL_H,
                Component.literal(dimensionLabel(dimW)), "Browse dimension maps; live terrain capture continues.", () -> {
                    WorldMapClient engine = WorldMapClient.get();
                    if (engine != null) engine.cycleDimension();
                    follow = false; markerRevision = -1;
                    dimensionButton.setMessage(Component.literal(dimensionLabel(dimensionButton.getWidth())));
                }));
        int coordX = wide ? left + dimW + GAP : left, coordY = (wide ? row1 : row2) + 2;
        coordinates = new EditBox(font, coordX, coordY, coordW, 18, Component.literal("Go to X Z"));
        coordinates.setMaxLength(40); coordinates.setHint(Component.literal("Go to X Z"));
        addRenderableWidget(coordinates);
        addRenderableWidget(ButtonWidget.semantic(coordX + coordW + 4, coordY - 2, goW, CTRL_H, Component.literal("Go"),
                "Center the map at the entered block coordinates.", this::goToCoordinates));
        int doneX = right - doneW, doneY = wide ? row1 : row2;
        addRenderableWidget(ButtonWidget.semantic(doneX, doneY, doneW, CTRL_H,
                Component.literal("Done"), "Close the map.", this::onClose));
        int actionsRight = wide ? doneX - GAP : right;
        int ax = actionsRight - 3 * actionW - 2 * GAP;
        addRenderableWidget(ButtonWidget.semantic(ax, row1, actionW, CTRL_H,
                Component.literal("Player"), "Return to the live dimension and follow the player. Shortcut P.", this::centerPlayer));
        addRenderableWidget(ButtonWidget.semantic(ax + actionW + GAP, row1, actionW, CTRL_H,
                Component.literal("Waypoints"), "Search, edit and organize this world's waypoints.",
                () -> minecraft.setScreen(new WaypointManagerScreen(this))));
        addRenderableWidget(ButtonWidget.semantic(ax + 2 * (actionW + GAP), row1, actionW, CTRL_H,
                Component.literal("Settings"), "Map appearance, caching and legacy import.",
                () -> minecraft.setScreen(new FeatureDetailScreen(this,
                        FeatureRegistry.modules().stream().filter(f -> f.id.equals("world_map")).findFirst().orElseThrow()))));
        if (follow) centerPlayer();
        lastFrame = System.nanoTime();
    }

    private String dimension() {
        WorldMapClient engine = WorldMapClient.get();
        return engine == null ? "" : engine.dimensionId();
    }
    private String dimensionLabel(int width) {
        return AuroraFontRenderer.ellipsize(font, displayDimension(dimension()), width - 10, 1);
    }

    /** "minecraft:the_nether" -> "The Nether"; modded ids keep only their path, title-cased. */
    public static String displayDimension(String id) {
        if (id == null || id.isEmpty()) return "Unknown";
        String path = id.substring(id.indexOf(':') + 1);
        StringBuilder out = new StringBuilder(path.length());
        boolean upper = true;
        for (char c : path.toCharArray()) {
            if (c == '_' || c == '/' || c == '-') { out.append(' '); upper = true; continue; }
            out.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return out.toString();
    }

    private void centerPlayer() {
        WorldMapClient engine = WorldMapClient.get();
        if (engine != null) engine.restoreLiveDimension();
        follow = true;
        if (minecraft.player != null) viewport.center(minecraft.player.getX(), minecraft.player.getZ());
        if (dimensionButton != null) dimensionButton.setMessage(Component.literal(dimensionLabel(dimensionButton.getWidth())));
    }

    private void goToCoordinates() {
        try {
            String[] parts = coordinates.getValue().trim().split("[ ,]+");
            if (parts.length != 2) throw new IllegalArgumentException();
            double x = Double.parseDouble(parts[0]), z = Double.parseDouble(parts[1]);
            if (!Double.isFinite(x) || !Double.isFinite(z) || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000)
                throw new IllegalArgumentException();
            follow = false; viewport.center(x, z); status = ""; setFocused(null);
        } catch (RuntimeException invalid) { status = "Enter two coordinates: X Z"; }
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float delta) {}
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        long now = System.nanoTime();
        viewport.advance((now - lastFrame) / 1_000_000_000d, width, height); lastFrame = now;
        if (follow && minecraft.player != null) viewport.center(minecraft.player.getX(), minecraft.player.getZ());
        g.fill(0, 0, width, height, 0xFF121417);
        g.enableScissor(0, 0, width, height);
        drawTerrain(g);
        updateMarkers(now);
        Waypoint hovered = null;
        var feature = WaypointFeature.get();
        nameLayout.reset();
        nameLayout.reserve(BAR_PAD, BAR_PAD, width - 2 * BAR_PAD, topBarH);
        nameLayout.reserve(BAR_PAD, statusTop(), width - 2 * BAR_PAD, STATUS_H);
        if (minecraft.player != null && minecraft.level != null
                && dimension().equals(minecraft.level.dimension().identifier().toString()))
            nameLayout.reserve(screenX(minecraft.player.getX()) - 8, screenZ(minecraft.player.getZ()) - 8, 16, 16);
        // Establish every icon's exclusion before offering names. A later icon
        // must never overwrite an already-painted label in a dense group.
        for (Waypoint marker : markers) {
            int x = screenX(marker.x + .5), y = screenZ(marker.z + .5);
            if (x < -8 || y < -8 || x > width + 8 || y > height + 8) continue;
            boolean selected = feature != null && marker.id.equals(feature.navigationId());
            MapMarkers.waypoint(g, x, y, marker, selected);
            nameLayout.reserve(x - 7, y - 7, 14, 14);
            if (Math.abs(mx - x) < 8 && Math.abs(my - y) < 8) hovered = marker;
        }
        for (Waypoint marker : markers) {
            int x = screenX(marker.x + .5), y = screenZ(marker.z + .5);
            if (x < -8 || y < -8 || x > width + 8 || y > height + 8) continue;
            boolean selected = feature != null && marker.id.equals(feature.navigationId());
            if (AuroraConfig.get().worldMapShowWaypointNames && (viewport.scale() >= 1 || selected)) {
                String name = AuroraFontRenderer.ellipsize(font, marker.name, Math.min(150, width - 24), 1);
                int textWidth = font.width(name);
                float labelW = textWidth + 8, labelH = font.lineHeight + 6;
                if (nameLayout.place(x + 12 + labelW * .5f, y + labelH * .5f, labelW, labelH, width, height)
                        || nameLayout.place(x - 12 - labelW * .5f, y + labelH * .5f, labelW, labelH, width, height)
                        || nameLayout.place(x, y - 10, labelW, labelH, width, height)
                        || nameLayout.place(x, y + 10 + labelH, labelW, labelH, width, height)) {
                    MapMarkers.labelPlate(g, nameLayout.centerX(), nameLayout.bottomY() - (font.lineHeight + 6) * .5f,
                            textWidth + 8, font.lineHeight + 6, marker.color);
                    MapMarkers.text(g, font, name, nameLayout.centerX() - textWidth * .5f,
                            nameLayout.bottomY() - font.lineHeight - 3, 0xFFFFFFFF, false);
                }
            }
        }
        if (minecraft.player != null && minecraft.level != null
                && dimension().equals(minecraft.level.dimension().identifier().toString())) {
            double yaw = Math.toRadians(minecraft.player.getYRot());
            int x = screenX(minecraft.player.getX()), y = screenZ(minecraft.player.getZ());
            if (x >= 0 && y >= 0 && x < width && y < height) MapMarkers.player(g, x, y, -Math.sin(yaw), Math.cos(yaw));
        }
        g.disableScissor();
        // Opaque semantic surfaces keep controls readable over deferred terrain tiles.
        int barR = Math.round(ThemeManager.current().roundness().radius());
        MaterialSurface.floating(g, BAR_PAD, BAR_PAD, width - 2 * BAR_PAD, topBarH, barR, 1);
        MaterialSurface.floating(g, BAR_PAD, statusTop(), width - 2 * BAR_PAD, STATUS_H,
                Math.round(ThemeManager.current().roundness().radiusSmall()), 1);
        int textY = statusTop() + (STATUS_H - font.lineHeight) / 2 + 1;
        String position = (int) Math.floor(viewport.worldX(mx, width)) + ", "
                + (int) Math.floor(viewport.worldZ(my, height)) + "  \u00b7  " +
                String.format(java.util.Locale.ROOT, "%.2f px/block", viewport.scale());
        g.drawString(font, position, BAR_PAD + 8, textY, ThemeManager.color(ThemeToken.ON_SURFACE), false);
        // Right side of the status bar: a transient status message, else the
        // gesture hint (muted; dropped entirely when there is no room).
        String trailing = status.isEmpty()
                ? "Drag to pan  \u00b7  Scroll to zoom  \u00b7  Right-click to add  \u00b7  Click a marker to edit  \u00b7  P to follow"
                : status;
        int trailingRoom = width - 2 * BAR_PAD - 16 - font.width(position) - 16;
        if (trailingRoom > 40) {
            String fitted = AuroraFontRenderer.ellipsize(font, trailing, trailingRoom, 1);
            g.drawString(font, fitted, width - BAR_PAD - 8 - font.width(fitted), textY,
                    ThemeManager.color(status.isEmpty() ? ThemeToken.ON_BACKGROUND_MUTED : ThemeToken.ON_SURFACE), false);
        }
        super.render(g, mx, my, delta);
        if (hovered != null && my > topBarBottom() && my < statusTop()) {
            String tooltip = hovered.name + " · " + hovered.x + ", " + hovered.y + ", " + hovered.z;
            int tw = Math.min(width - 16, font.width(tooltip) + 16);
            int tx = Math.max(4, Math.min(mx + 8, width - tw - 4));
            MaterialSurface.floating(g, tx, my + 10, tw, 22, 4, 1);
            g.drawString(font, AuroraFontRenderer.ellipsize(font, tooltip, tw - 12, 1), tx + 6, my + 17,
                    ThemeManager.color(ThemeToken.ON_SURFACE), false);
        }
    }

    private int screenX(double x) { return (int) Math.floor((x - viewport.x) * viewport.scale() + width * .5); }
    private int screenZ(double z) { return (int) Math.floor((z - viewport.z) * viewport.scale() + height * .5); }

    private void drawTerrain(GuiGraphics g) {
        WorldMapClient engine = WorldMapClient.get();
        if (engine == null) return;
        double left = viewport.worldX(0, width), top = viewport.worldZ(0, height);
        int rx0 = (int) Math.floor(left / 512), rz0 = (int) Math.floor(top / 512);
        int rx1 = (int) Math.floor(viewport.worldX(width, width) / 512);
        int rz1 = (int) Math.floor(viewport.worldZ(height, height) / 512);
        boolean overview = viewport.scale() <= .25 || (rx1 - rx0 + 1L) * (rz1 - rz0 + 1L)
                > MapTransform.bounded(AuroraConfig.get().worldMapCacheRegions, 8, 256);
        int cx = (rx0 + rx1) >> 1, cz = (rz0 + rz1) >> 1;
        int rings = Math.max(Math.max(cx - rx0, rx1 - cx), Math.max(cz - rz0, rz1 - cz));
        // Center-first IO and texture requests: a large pan never has to load an
        // entire top-left quadrant before showing the area under the cursor.
        for (int ring = 0; ring <= rings; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                int rx = cx + dx;
                if (rx < rx0 || rx > rx1) continue;
                if (cz - ring >= rz0) drawRegion(g, engine, rx, cz - ring, overview);
                if (ring > 0 && cz + ring <= rz1) drawRegion(g, engine, rx, cz + ring, overview);
            }
            for (int dz = -ring + 1; dz < ring; dz++) {
                int rz = cz + dz;
                if (rz < rz0 || rz > rz1) continue;
                if (cx - ring >= rx0) drawRegion(g, engine, cx - ring, rz, overview);
                if (cx + ring <= rx1) drawRegion(g, engine, cx + ring, rz, overview);
            }
        }
    }

    private void drawRegion(GuiGraphics g, WorldMapClient engine, int rx, int rz, boolean overview) {
        long key = RegionCache.key(rx, rz);
        net.minecraft.resources.Identifier texture;
        int source;
        if (overview) {
            var tile = engine.acquireOverviewForRender(key);
            if (tile == null) return;
            texture = tile.textureId(); source = OverviewCache.SIZE;
        } else {
            var tile = engine.acquireForRender(key);
            if (tile == null) return;
            texture = tile.textureId(); source = 512;
        }
        int x = screenX(rx * 512d), y = screenZ(rz * 512d);
        int w = screenX((rx + 1) * 512d) - x, h = screenZ((rz + 1) * 512d) - y;
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0, 0, w, h, source, source, source, source);
    }

    private void updateMarkers(long now) {
        WaypointFeature feature = WaypointFeature.get();
        if (feature == null) { markers = List.of(); return; }
        if (markerRevision == feature.revision() && markerDimension.equals(dimension())
                && markerX == viewport.x && markerZ == viewport.z && markerScale == viewport.scale()) return;
        if (now - lastMarkers < 50_000_000L && markerRevision == feature.revision()) return;
        markerRevision = feature.revision(); markerDimension = dimension(); lastMarkers = now;
        markerX = viewport.x; markerZ = viewport.z; markerScale = viewport.scale();
        var cfg = AuroraConfig.get();
        markers = WaypointQuery.select(feature.currentWorldWaypoints(), dimension(), cfg.waypointHiddenGroups,
                feature.navigationId(), viewport.x, viewport.z,
                Math.hypot(width, height) / viewport.scale(), cfg.waypointMaxVisible);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.y() < topBarBottom() || event.y() >= statusTop()) return true;
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (Waypoint waypoint : markers) {
                if (Math.abs(event.x() - screenX(waypoint.x + .5)) > 8
                        || Math.abs(event.y() - screenZ(waypoint.z + .5)) > 8) continue;
                minecraft.setScreen(new WaypointEditScreen(this, waypoint, false)); return true;
            }
            dragging = true; follow = false; setFocused(null); return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            int x = (int) Math.floor(viewport.worldX(event.x(), width));
            int z = (int) Math.floor(viewport.worldZ(event.y(), height));
            int y = minecraft.player == null ? 64 : minecraft.player.blockPosition().getY();
            // Color regions do not contain heights: the editor exposes Y explicitly.
            minecraft.setScreen(new WaypointEditScreen(this,
                    new Waypoint("Waypoint", x, y, z, dimension(), 0xFFFFAA00), true)); return true;
        }
        return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!dragging) return super.mouseDragged(event, dx, dy);
        viewport.pan(dx, dy); return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) { dragging = false; return super.mouseReleased(event); }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (sy == 0 || y < topBarBottom() || y >= statusTop()) return false;
        follow = false; viewport.zoom(sy > 0 ? 2 : .5, x, y, width, height); return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (coordinates.isFocused()) {
            if (event.key() == GLFW.GLFW_KEY_ENTER) { goToCoordinates(); return true; }
            return super.keyPressed(event);
        }
        switch (event.key()) {
            case GLFW.GLFW_KEY_P -> { centerPlayer(); return true; }
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> { follow = false; viewport.zoom(2, width * .5, height * .5, width, height); return true; }
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> { follow = false; viewport.zoom(.5, width * .5, height * .5, width, height); return true; }
        }
        if (event.key() == AuroraConfig.get().worldMapKey) { onClose(); return true; }
        return super.keyPressed(event);
    }
    @Override public void tick() {
        if (minecraft.level == null || !AuroraConfig.get().worldMapEnabled) { onClose(); return; }
        if (dimensionButton != null) dimensionButton.setMessage(Component.literal(dimensionLabel(dimensionButton.getWidth())));
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { dragging = false; MaterialSurface.disposeTemplates(); }
    @Override public void onClose() {
        WorldMapClient engine = WorldMapClient.get();
        if (engine != null) engine.restoreLiveDimension();
        minecraft.setScreen(null);
    }
}
