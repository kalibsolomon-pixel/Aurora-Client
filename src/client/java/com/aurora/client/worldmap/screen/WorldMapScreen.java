package com.aurora.client.worldmap.screen;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.AuroraConfig.Waypoint;
import com.aurora.client.feature.impl.WaypointFeature;
import com.aurora.client.screen.WaypointManagerScreen;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.RoundedPanel;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.worldmap.WorldMapClient;
import com.aurora.client.worldmap.cache.RegionCache;
import com.aurora.client.worldmap.cache.RegionCache.RegionTile;
import com.aurora.client.worldmap.cache.OverviewCache;
import com.aurora.client.worldmap.cache.OverviewCache.OverviewTile;

import com.aurora.client.worldmap.cache.WorldMapSize;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;

/**
 * Fullscreen, pannable/zoomable world map (Xaero's-style) over the tiles
 * captured by {@link WorldMapClient}.
 *
 * <h2>Interaction</h2>
 * <ul>
 *   <li><b>Pan</b> — left-drag; the first drag cancels follow-player mode.</li>
 *   <li><b>Zoom</b> — mouse wheel in discrete steps (1/16 … 16 px per
 *       block), anchored at the cursor so the block under the mouse stays
 *       fixed. {@code +}/{@code -} zoom at screen center.</li>
 *   <li><b>Follow</b> — on until the first manual pan; the "Center on
 *       Player" button (or {@code P}) re-enables it.</li>
 *   <li><b>Dimension</b> — top-left button cycles vanilla dims (archive
 *       view of saved tiles; live capture continues in the real dim and is
 *       restored when the screen closes).</li>
 *   <li><b>Close</b> — ESC or the map keybind (default M).</li>
 * </ul>
 *
 * <h2>Rendering</h2>
 * Each visible 512×512 region tile is one clipped
 * {@link GuiGraphics#blit} — a viewport touches at most ~54 regions at the
 * farthest full-res zoom (½ px/block). Zooms at or below ¼ px/block switch
 * to the 64×64 {@link OverviewCache} overview layer so zooming far out
 * stays cheap. Regions still loading from disk simply don't draw that frame
 * (the void background shows through). The screen is not a pause screen.
 *
 * <h2>Theming</h2>
 * On the shared component framework: toolbar/prompt buttons are the canonical
 * themed {@link ButtonWidget}, the create-waypoint prompt floats on a shared
 * {@link RoundedPanel}, and the prompt's name field is themed by the mod-wide
 * {@code EditBoxMixin} via {@link ThemedScreen}. Map-content colors (the void
 * backdrop, waypoint markers and the player arrow) are deliberately NOT theme
 * chrome — they render map data and structural shadows, following the same
 * content-vs-chrome convention as the color picker's pad.
 */
public class WorldMapScreen extends Screen implements ThemedScreen {

    /** Discrete zoom steps: rendered pixels per world block (1/16 … 16). */
    private static final double[] ZOOM_STEPS = {
            1 / 16d, 1 / 8d, 1 / 4d, 1 / 2d, 1, 2, 4, 8, 16};
    /** At/below this zoom the overview layer (64×64 tiles) renders instead. */
    private static final double OVERVIEW_MAX_PX = 0.25;
    /** Far-zoom cap on tiles per axis — bounds draw + load churn. */
    private static final int MAX_OVERVIEW_SPAN_X = 40;
    private static final int MAX_OVERVIEW_SPAN_Z = 23;
    /** Void behind tiles (matches the minimap's unloaded color family). */
    private static final int VOID_COLOR = 0xFF121417;

    private double centerX;
    private double centerZ;
    private int zoomIdx = 4;
    private boolean followPlayer = true;
    private boolean dragging;

    private ButtonWidget dimButton;

    /** Themed chrome for the floating create-waypoint prompt (shared component). */
    private final RoundedPanel promptPanel = new RoundedPanel(false, ThemeToken.WINDOW_FILL);

    // ---- Inline "add waypoint" prompt (right-click on the map) ----
    /** Prompt panel size in screen pixels. */
    private static final int PROMPT_W = 220;
    private static final int PROMPT_H = 74;
    /** True while the create-waypoint prompt is open. */
    private boolean promptOpen;
    /** World block coords the prompt will create the waypoint at. */
    private int promptWorldX, promptWorldZ;
    /** Raw right-click position; clamped panel position is in promptPanelX/Y. */
    private int promptX, promptY, promptPanelX, promptPanelY;
    private EditBox promptName;

    /** Tile-rect debug overlay: {@code -Daurora.worldmap.debugTiles=true}. */
    private static final boolean DEBUG_TILES = Boolean.getBoolean("aurora.worldmap.debugTiles");
    /** Per-frame debug counters, reset in {@link #drawRegions}. */
    private int dbgTilesDrawn;
    private final HashSet<String> dbgTexIds = new HashSet<>();
    private String dbgStats = "";

    public WorldMapScreen() {
        super(Component.literal("Aurora World Map"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && followPlayer) {
            centerX = mc.player.getX();
            centerZ = mc.player.getZ();
        }

        dimButton = new ButtonWidget(12, 12, 176, 22,
                Component.literal("Dimension: " + shortDim(currentDim())),
                this::cycleDimension);
        addRenderableWidget(dimButton);

        addRenderableWidget(new ButtonWidget(12 + 176 + 8, 12, 140, 22,
                Component.literal("Center on Player"), () -> followPlayer = true));

        // Opens the existing Waypoint feature's own manager screen — the
        // single list stays owned and persisted there, never copied here.
        addRenderableWidget(new ButtonWidget(12 + 176 + 8 + 140 + 8, 12, 140, 22,
                Component.literal("Waypoints\u2026"), () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new WaypointManagerScreen(this));
                    }
                }));

        if (promptOpen) {
            promptPanelX = Mth.clamp(promptX, 4, Math.max(4, this.width - PROMPT_W - 4));
            promptPanelY = Mth.clamp(promptY, 4, Math.max(4, this.height - PROMPT_H - 4));
            promptName = new EditBox(this.font, promptPanelX + 10, promptPanelY + 26,
                    PROMPT_W - 20, 16, Component.literal("Name"));
            promptName.setMaxLength(48);
            promptName.setValue("Waypoint " + promptWorldX + ", " + promptWorldZ);
            promptName.setFocused(true);
            promptName.moveCursorToEnd(false);
            addRenderableWidget(promptName);
            addRenderableWidget(new ButtonWidget(promptPanelX + 10, promptPanelY + 48,
                    96, 20, Component.literal("Create"), this::createFromPrompt, true));
            addRenderableWidget(new ButtonWidget(promptPanelX + PROMPT_W - 106, promptPanelY + 48,
                    96, 20, Component.literal("Cancel"), this::closePrompt));
        }
    }

    // ================================================================
    //  Input
    // ================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // Widgets (dimension / recenter buttons) take the click first;
            // anywhere else on the map starts a pan drag.
            if (super.mouseClicked(event, doubleClick)) return true;
            dragging = true;
            return true;
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            // Right-click: create a waypoint through the existing Waypoint
            // feature. If a prompt is already open, clicking outside its
            // panel cancels it; clicking inside lets EditBox selection work.
            if (promptOpen) {
                int mx = (int) event.x(), my = (int) event.y();
                boolean inside = mx >= promptPanelX && my >= promptPanelY
                        && mx <= promptPanelX + PROMPT_W && my <= promptPanelY + PROMPT_H;
                if (!inside) closePrompt();
                return true;
            }
            openPrompt((int) event.x(), (int) event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    // ================================================================
    //  Create-waypoint prompt
    // ================================================================

    /** Opens the inline prompt for a waypoint at the clicked world position. */
    private void openPrompt(int mouseX, int mouseY) {
        double px = pxPerBlock();
        promptWorldX = (int) Math.floor(worldLeft() + mouseX / px);
        promptWorldZ = (int) Math.floor(worldTop() + mouseY / px);
        promptX = mouseX;
        promptY = mouseY;
        promptOpen = true;
        rebuildWidgets();
    }

    private void closePrompt() {
        promptOpen = false;
        promptName = null;
        rebuildWidgets();
    }

    /**
     * Creates the waypoint via the existing Waypoint feature's own API —
     * no local copy is kept; the map re-renders from the feature's live
     * list next frame. Y uses the player's current Y (region tiles store
     * color pixels only, so the map has no height data to offer).
     */
    private void createFromPrompt() {
        WaypointFeature feat = WaypointFeature.get();
        if (feat != null) {
            String name = promptName != null ? promptName.getValue().trim() : "";
            if (name.isEmpty()) name = "Waypoint " + promptWorldX + ", " + promptWorldZ;
            Minecraft mc = Minecraft.getInstance();
            int y = mc != null && mc.player != null
                    ? mc.player.blockPosition().getY() : 64;
            feat.addWaypoint(new Waypoint(name, promptWorldX, y, promptWorldZ,
                    currentDim(), 0xFFFFAA00));
        }
        closePrompt();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragging) {
            followPlayer = false;
            centerX -= deltaX / pxPerBlock();
            centerZ -= deltaY / pxPerBlock();
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0) {
            setZoomAnchored(zoomIdx + (scrollY > 0 ? 1 : -1), mouseX, mouseY);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (promptOpen) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                closePrompt();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                createFromPrompt();
                return true;
            }
            return super.keyPressed(event); // route to the focused EditBox
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == AuroraConfig.get().worldMapKey) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) {
            setZoomAnchored(zoomIdx + 1, this.width / 2.0, this.height / 2.0);
            return true;
        }
        if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
            setZoomAnchored(zoomIdx - 1, this.width / 2.0, this.height / 2.0);
            return true;
        }
        if (key == GLFW.GLFW_KEY_P) {
            followPlayer = true;
            return true;
        }
        return super.keyPressed(event);
    }

    /** Non-pausable: the game keeps running behind the map. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }
    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (followPlayer && mc.player != null) {
            centerX = mc.player.getX();
            centerZ = mc.player.getZ();
        }
        // The engine may have switched dimensions under us (portal while the
        // map is open) — snap the dimension button back to the live one.
        WorldMapClient wm = WorldMapClient.get();
        if (wm != null && mc.level != null && dimButton != null
                && wm.isViewingLiveDimension()) {
            dimButton.setMessage(Component.literal(
                    "Dimension: " + shortDim(mc.level.dimension().identifier().toString())));
        }
    }

    @Override
    public void removed() {
        // Leave the engine on the live dimension for background capture.
        WorldMapClient wm = WorldMapClient.get();
        if (wm != null) wm.restoreLiveDimension();
    }

    // ================================================================
    //  Rendering
    // ================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, VOID_COLOR);
        drawRegions(g);
        drawWaypoints(g);
        drawPlayer(g, delta);
        if (promptOpen) drawPromptPanel(g);
        super.render(g, mouseX, mouseY, delta); // buttons
        drawHud(g, mouseX, mouseY);
    }

    /** Backdrop + caption for the create-waypoint prompt (widgets render in super). */
    private void drawPromptPanel(GuiGraphics g) {
        // Shared themed panel chrome: WINDOW_FILL (tracks Background Opacity),
        // WINDOW_OUTLINE ring, theme roundness — identical to every other
        // floating panel in the mod, replacing the old hardcoded near-black
        // fill + white top-edge highlight.
        promptPanel.renderShapes(g, promptPanelX, promptPanelY, PROMPT_W, PROMPT_H);
        g.drawString(this.font, "Add waypoint at " + promptWorldX + ", " + promptWorldZ,
                promptPanelX + 10, promptPanelY + 12,
                ThemeManager.color(ThemeToken.ON_SURFACE), false);
    }

    /**
     * One clipped blit per visible region tile. The sub-rectangle math clips
     * the region to the viewport in block space, then scales to screen
     * pixels — so a zoomed-in view samples a small uv window of the 512²
     * texture and stretches it, and a zoomed-out view samples the whole
     * texture into a smaller rect.
     */
    private void drawRegions(GuiGraphics g) {
        WorldMapClient wm = WorldMapClient.get();
        if (wm == null) return;
        dbgTilesDrawn = 0;
        dbgTexIds.clear();
        double px = pxPerBlock();
        if (px <= OVERVIEW_MAX_PX) {
            drawOverviews(g, wm, px);
        } else {
            drawFullRes(g, wm, px);
        }
    }

    /**
     * Far-zoom pass: one blit per visible region from the 64×64 overview
     * layer ({@link OverviewCache}). The tile span per axis is capped so an
     * extreme zoom-out can't queue thousands of loads or draws; beyond the
     * cap the void background shows.
     */
    private void drawOverviews(GuiGraphics g, WorldMapClient wm, double px) {
        double leftB = worldLeft(), topB = worldTop();
        double rightB = leftB + this.width / px;
        double bottomB = topB + this.height / px;
        int r = WorldMapSize.BLOCKS, down = OverviewCache.DOWN;

        int rx0 = (int) Math.floor(leftB / r), rx1 = (int) Math.floor(rightB / r);
        int rz0 = (int) Math.floor(topB / r), rz1 = (int) Math.floor(bottomB / r);
        if (rx1 - rx0 + 1 > MAX_OVERVIEW_SPAN_X) {
            int cut = rx1 - rx0 + 1 - MAX_OVERVIEW_SPAN_X;
            rx0 += cut / 2;
            rx1 -= cut - cut / 2;
        }
        if (rz1 - rz0 + 1 > MAX_OVERVIEW_SPAN_Z) {
            int cut = rz1 - rz0 + 1 - MAX_OVERVIEW_SPAN_Z;
            rz0 += cut / 2;
            rz1 -= cut - cut / 2;
        }

        for (int rz = rz0; rz <= rz1; rz++) {
            for (int rx = rx0; rx <= rx1; rx++) {
                OverviewTile tile = wm.acquireOverviewForRender(RegionCache.key(rx, rz));
                if (tile == null) continue; // still loading from disk

                int minX = rx * r, minZ = rz * r;
                int cx0 = Math.max(minX, (int) Math.ceil(leftB));
                int cz0 = Math.max(minZ, (int) Math.ceil(topB));
                int cx1 = Math.min(minX + r, (int) Math.ceil(rightB));
                int cz1 = Math.min(minZ + r, (int) Math.ceil(bottomB));
                if (cx1 <= cx0 || cz1 <= cz0) continue;

                // Texel-space window on the 64×64 texture (ceil never gaps).
                float u = (cx0 - minX) / (float) down;
                float v = (cz0 - minZ) / (float) down;
                int sw = (int) Math.ceil((cx1 - cx0) / (double) down);
                int sh = (int) Math.ceil((cz1 - cz0) / (double) down);
                int dx = (int) Math.floor((cx0 - leftB) * px);
                int dy = (int) Math.floor((cz0 - topB) * px);
                int dw = (int) Math.ceil((cx1 - cx0) * px);
                int dh = (int) Math.ceil((cz1 - cz0) * px);

                g.blit(RenderPipelines.GUI_TEXTURED, tile.textureId(),
                        dx, dy, u, v, dw, dh, sw, sh,
                        OverviewCache.SIZE, OverviewCache.SIZE, 0xFFFFFFFF);
                if (DEBUG_TILES) {
                    dbgTileBorder(g, dx, dy, dw, dh, 0xFFFFFF00);
                    dbgTexIds.add(tile.textureId().toString());
                    dbgTilesDrawn++;
                }
            }
        }
        dbgStats = "overview span=" + (rx1 - rx0 + 1) + "x" + (rz1 - rz0 + 1);
    }

    private void drawFullRes(GuiGraphics g, WorldMapClient wm, double px) {
        double leftB = worldLeft(), topB = worldTop();
        double rightB = leftB + this.width / px;
        double bottomB = topB + this.height / px;
        int r = WorldMapSize.BLOCKS;

        int rx0 = (int) Math.floor(leftB / r), rx1 = (int) Math.floor(rightB / r);
        int rz0 = (int) Math.floor(topB / r), rz1 = (int) Math.floor(bottomB / r);

        for (int rz = rz0; rz <= rz1; rz++) {
            for (int rx = rx0; rx <= rx1; rx++) {
                RegionTile tile = wm.acquireForRender(RegionCache.key(rx, rz));
                if (tile == null) continue; // still loading from disk

                int minX = rx * r, minZ = rz * r;
                int cx0 = Math.max(minX, (int) Math.ceil(leftB));
                int cz0 = Math.max(minZ, (int) Math.ceil(topB));
                int cx1 = Math.min(minX + r, (int) Math.ceil(rightB));
                int cz1 = Math.min(minZ + r, (int) Math.ceil(bottomB));
                if (cx1 <= cx0 || cz1 <= cz0) continue;

                float u = cx0 - minX, v = cz0 - minZ;
                int sw = cx1 - cx0, sh = cz1 - cz0;
                int dx = (int) Math.floor((cx0 - leftB) * px);
                int dy = (int) Math.floor((cz0 - topB) * px);
                int dw = (int) Math.ceil(sw * px);
                int dh = (int) Math.ceil(sh * px);

                g.blit(RenderPipelines.GUI_TEXTURED, tile.textureId(),
                        dx, dy, u, v, dw, dh, sw, sh, r, r, 0xFFFFFFFF);
                if (DEBUG_TILES) {
                    dbgTileBorder(g, dx, dy, dw, dh, 0xFF00FFFF);
                    dbgTexIds.add(tile.textureId().toString());
                    dbgTilesDrawn++;
                }
            }
        }
        dbgStats = "fullres span=" + (rx1 - rx0 + 1) + "x" + (rz1 - rz0 + 1);
    }

    /** Debug: 1px border around a tile's dest rect (four fills). */
    private static void dbgTileBorder(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.fill(x, y, x + w, y + 1, argb);           // top
        g.fill(x, y + h - 1, x + w, y + h, argb);   // bottom
        g.fill(x, y, x + 1, y + h, argb);           // left
        g.fill(x + w - 1, y, x + w, y + h, argb);   // right
    }

    /**
     * Player marker: a smooth antialiased isosceles arrow that rotates with
     * the player's yaw (live dimension only). {@link GuiGraphics} has no
     * rotated primitives, so the triangle is software-rasterized:
     * per-scanline edge intersection with fractional pixel coverage emitted
     * as alpha — sub-pixel edges at any angle. Position is interpolated
     * between ticks so the arrow glides while following the player.
     */
    private void drawPlayer(GuiGraphics g, float delta) {
        Minecraft mc = Minecraft.getInstance();
        WorldMapClient wm = WorldMapClient.get();
        if (mc.player == null || wm == null || !wm.isViewingLiveDimension()) return;

        double px = pxPerBlock();
        double wx = Mth.lerp(delta, mc.player.xo, mc.player.getX());
        double wz = Mth.lerp(delta, mc.player.zo, mc.player.getZ());
        double sx = (wx - worldLeft()) * px;
        double sz = (wz - worldTop()) * px;
        if (sx < -24 || sz < -24 || sx > this.width + 24 || sz > this.height + 24) return;

        double rad = Math.toRadians(mc.player.getYRot());
        // Yaw 0 = south (+Z) = screen down; yaw 90° = west (-X) = screen left.
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double rx = -fz, rz = fx; // perpendicular axis

        drawArrow(g, sx, sz, fx, fz, rx, rz, 1.5, 0xB0000000); // dark border
        drawArrow(g, sx, sz, fx, fz, rx, rz, 1.0, 0xFFFFFFFF); // white arrow
    }

    /** Isosceles triangle: apex ahead of the center, base behind it. */
    private static void drawArrow(GuiGraphics g, double cx, double cz,
                                  double fx, double fz, double rx, double rz,
                                  double scale, int argb) {
        double tip = 12 * scale, back = 6.5 * scale, half = 6.5 * scale;
        fillTriangleAa(g,
                cx + fx * tip, cz + fz * tip,                           // apex
                cx - fx * back + rx * half, cz - fz * back + rz * half, // base L
                cx - fx * back - rx * half, cz - fz * back - rz * half, // base R
                argb);
    }

    /** Antialiased filled triangle (scanline coverage → alpha). */
    private static void fillTriangleAa(GuiGraphics g,
            double ax, double ay, double bx, double by,
            double cx, double cy, int argb) {
        int baseA = (argb >>> 24) & 0xFF;
        int rgb = argb & 0x00FFFFFF;
        int y0 = (int) Math.floor(Math.min(ay, Math.min(by, cy)));
        int y1 = (int) Math.ceil(Math.max(ay, Math.max(by, cy)));

        for (int y = y0; y < y1; y++) {
            double yc = y + 0.5;
            double l = crossX(ax, ay, bx, by, yc);
            double m = crossX(bx, by, cx, cy, yc);
            double n = crossX(cx, cy, ax, ay, yc);
            double xl = Double.POSITIVE_INFINITY, xr = Double.NEGATIVE_INFINITY;
            if (!Double.isNaN(l)) { xl = Math.min(xl, l); xr = Math.max(xr, l); }
            if (!Double.isNaN(m)) { xl = Math.min(xl, m); xr = Math.max(xr, m); }
            if (!Double.isNaN(n)) { xl = Math.min(xl, n); xr = Math.max(xr, n); }
            if (xr <= xl) continue;

            // Run-length merge fully covered pixels; edge pixels get alpha.
            int runStart = Integer.MIN_VALUE;
            for (int i = (int) Math.floor(xl); i <= (int) Math.floor(xr); i++) {
                double cov = Math.min(xr, i + 1d) - Math.max(xl, i);
                if (cov >= 0.996) {
                    if (runStart < 0) runStart = i;
                } else {
                    if (runStart >= 0) {
                        g.fill(runStart, y, i, y + 1, argb);
                        runStart = Integer.MIN_VALUE;
                    }
                    if (cov > 0.02) {
                        int a = (int) (baseA * cov);
                        g.fill(i, y, i + 1, y + 1, (a << 24) | rgb);
                    }
                }
            }
            if (runStart >= 0) {
                g.fill(runStart, y, (int) Math.floor(xr) + 1, y + 1, argb);
            }
        }
    }

    /** X where segment (x1,y1)-(x2,y2) crosses the horizontal yc; NaN if not. */
    private static double crossX(double x1, double y1, double x2, double y2, double yc) {
        if ((y1 < yc) == (y2 < yc)) return Double.NaN;
        return x1 + (yc - y1) * (x2 - x1) / (y2 - y1);
    }

    /** Waypoint squares (+ names when zoomed in) for the viewed dimension. */
    private void drawWaypoints(GuiGraphics g) {
        WaypointFeature wf = WaypointFeature.get();
        if (wf == null) return;
        double px = pxPerBlock();
        double leftB = worldLeft(), topB = worldTop();
        String dim = currentDim();

        for (Waypoint w : wf.currentWorldWaypoints()) {
            if (!w.dimension.equals(dim)) continue;
            int sx = (int) Math.round((w.x + 0.5 - leftB) * px);
            int sz = (int) Math.round((w.z + 0.5 - topB) * px);
            if (sx < -40 || sz < -40 || sx > this.width + 40 || sz > this.height + 40) continue;

            g.fill(sx - 4, sz - 4, sx + 5, sz + 5, 0xB0000000);
            if (w.death) {
                // Death markers draw an X so they read differently from
                // manual waypoints' filled squares (color is also distinct
                // via deathWaypointColor).
                int c = w.color == 0 ? 0xFFFF3344 : w.color;
                for (int i = -3; i <= 3; i++) {
                    g.fill(sx + i, sz + i, sx + i + 1, sz + i + 1, c);
                    g.fill(sx + i, sz - i, sx + i + 1, sz - i + 1, c);
                }
            } else {
                g.fill(sx - 3, sz - 3, sx + 4, sz + 4, w.color);
            }
            if (px >= 2) {
                g.drawString(this.font, w.name, sx + 7, sz - 4, 0xFFFFFFFF, false);
            }
        }
    }
    // ================================================================
    //  HUD & helpers
    // ================================================================

    private void drawHud(GuiGraphics g, int mouseX, int mouseY) {
        double px = pxPerBlock();
        int wx = (int) Math.floor(worldLeft() + mouseX / px);
        int wz = (int) Math.floor(worldTop() + mouseY / px);

        String pos = wx + ", " + wz;
        g.drawString(this.font, pos,
                this.width - 8 - this.font.width(pos), this.height - 18, 0xFFFFFFFF, false);
        String zoom = zoomLabel();
        g.drawString(this.font, zoom,
                this.width - 8 - this.font.width(zoom), this.height - 30, 0x88FFFFFF, false);

        String hint = "Drag to pan  \u2022  Scroll to zoom  \u2022  Right-click: waypoint  \u2022  P to recenter  \u2022  M/Esc to close";
        g.drawString(this.font, hint, 12, this.height - 18, 0x88FFFFFF, false);

        if (DEBUG_TILES) {
            g.drawString(this.font, "dbg: " + dbgTilesDrawn + " drawn / "
                    + dbgTexIds.size() + " ids / " + dbgStats,
                    12, this.height - 42, 0xFF00FF00, false);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String me = "Player: " + (int) Math.floor(mc.player.getX())
                    + ", " + (int) Math.floor(mc.player.getY())
                    + ", " + (int) Math.floor(mc.player.getZ());
            g.drawString(this.font, me, 12, this.height - 30, 0x88FFFFFF, false);
        }
    }

    /** Human zoom label: {@code "1/16 px/block"} … {@code "16 px/block"}. */
    private String zoomLabel() {
        double px = pxPerBlock();
        return px >= 1 ? (int) px + " px/block"
                : "1/" + (int) Math.round(1 / px) + " px/block";
    }

    private double pxPerBlock() {
        return ZOOM_STEPS[zoomIdx];
    }

    /** World X of the left screen edge (block units). */
    private double worldLeft() {
        return centerX - (this.width / 2.0) / pxPerBlock();
    }

    /** World Z of the top screen edge (block units). */
    private double worldTop() {
        return centerZ - (this.height / 2.0) / pxPerBlock();
    }

    /**
     * Steps the zoom while keeping the world position under the cursor
     * (or screen center) fixed — the standard map-app zoom feel.
     */
    private void setZoomAnchored(int idx, double mx, double my) {
        int clamped = Mth.clamp(idx, 0, ZOOM_STEPS.length - 1);
        double oldPx = pxPerBlock();
        double wx = worldLeft() + mx / oldPx;
        double wz = worldTop() + my / oldPx;
        zoomIdx = clamped;
        double newPx = pxPerBlock();
        centerX = wx + (this.width / 2.0 - mx) / newPx;
        centerZ = wz + (this.height / 2.0 - my) / newPx;
    }

    private String currentDim() {
        WorldMapClient wm = WorldMapClient.get();
        return wm != null ? wm.dimensionId() : "minecraft:overworld";
    }

    /** Strips the namespace: {@code minecraft:the_nether → the_nether}. */
    private static String shortDim(String dim) {
        int i = dim.indexOf(':');
        return i >= 0 ? dim.substring(i + 1) : dim;
    }

    private void cycleDimension() {
        WorldMapClient wm = WorldMapClient.get();
        if (wm == null) return;
        wm.cycleDimension();
        if (dimButton != null) {
            dimButton.setMessage(Component.literal(
                    "Dimension: " + shortDim(wm.dimensionId())));
        }
    }
}


