package com.aurora.client.hud.module;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.AuroraConfig.Waypoint;
import com.aurora.client.feature.impl.WaypointFeature;
import com.aurora.client.hud.HudAnchor;
import com.aurora.client.worldmap.WorldMapClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;
import java.util.List;

/**
 * Top-down minimap HUD module.
 *
 * <p><b>Render strategy:</b> a square {@link DynamicTexture} sized to the
 * minimap's pixel edge is repainted each frame from sampled chunk block
 * data around the player. The texture is always screen-aligned — rotation
 * (when enabled) is baked into the <em>sampling</em> pass, not the blit.
 * For each output texel we inverse-rotate its screen-space offset by the
 * player's yaw to find which world block it represents. This means:
 * <ul>
 *   <li>No pose-stack rotation at blit time — just a straight blit.</li>
 *   <li>No √2 texture enlargement — square and circular modes use the
 *       same texture size, so performance is identical (circular is
 *       actually ~21% cheaper since corner texels are skipped).</li>
 *   <li>The circular alpha mask can be baked into the texture directly
 *       (corner texels are fully transparent), so there is no black fill
 *       outside the inscribed circle — corners show through to the game.</li>
 * </ul>
 *
 * <p><b>Terrain colors</b> are post-processed Xaeros-style:
 * <ul>
 *   <li>Biome tint multiplied into grass/foliage/leaves/water via
 *       {@link BlockColors} (tintIndex 0).</li>
 *   <li>Hillshade relief — each texel's height is compared against the
 *       one directly above it on screen, giving a consistent 3D terrain
 *       read regardless of facing direction.</li>
 *   <li>Water is darkened by depth for a strong depth cue.</li>
 * </ul>
 *
 * <p><b>Pixel packing:</b> {@link NativeImage#setPixelABGR} expects
 * {@code 0xAABBGGRR} ordering; terrain colors are repacked accordingly.
 *
 * <p>Entity + waypoint overlays are drawn as screen-space dots AFTER the
 * terrain blit. A coordinate readout and compass ring (N/E/S/W) render
 * below/around the map.
 */
  public class MinimapModule extends HudModule {
      public static final String ID = "minimap";
  
      private DynamicTexture terrainTex;
      private NativeImage terrainImage;
      private int lastTexSize = -1;
  
      /**
       * Reusable rolling-row buffers for hillshade. Allocated once per
       * texture size and swapped by reference each row — eliminates the
       * per-row {@code new int[size]} allocation that produced GC churn
       * (size allocations/frame, e.g. 128 at default size).
       */
      private int[] rowA;
      private int[] rowB;
      /** Reused per-texel mutable blockpos so the sampling loop never allocates. */
      private final BlockPos.MutableBlockPos samplePos = new BlockPos.MutableBlockPos();
      /** Reused mutable pos for water-depth probing (separate Y descent). */
      private final BlockPos.MutableBlockPos waterPos = new BlockPos.MutableBlockPos();
  
      /**
       * Terrain re-sample throttle. Repainting the whole texture every
       * frame is wasteful: at the default 128px / 64-block zoom each texel
       * covers ~0.5 blocks, and the player only moves ~0.08 blocks/frame
       * at 60fps — so the underlying terrain is effectively static between
       * samples. We re-sample at most ~10×/sec, which is visually
       * indistinguishable from per-frame (the map is already a coarse
       * abstraction) but cuts the O(size²) chunk-data access cost by ~6×.
       *
       * <p>Sample immediately when the player has crossed enough blocks to
       * shift a texel, on dimension change, or on zoom/size change, so
       * there's never visible lag after a teleport or settings change.
       */
      private long lastTerrainSampleMs = 0L;
      private static final long TERRAIN_SAMPLE_INTERVAL_MS = 100L;
      private double lastSamplePlayerX = Double.NaN;
      private double lastSamplePlayerZ = Double.NaN;
      private float lastSampleYaw = Float.NaN;
      private int lastSampleSize = -1;
      private int lastSampleZoom = -1;
      private boolean lastSampleCircular;
      private boolean lastSampleRotate;
      private String lastSampleDim;
  
      /** ABGR packing for unloaded/void columns. */
      private static final int ABGR_UNLOADED = 0xFF121417;
      /** ABGR fallback when sampling throws. */
      private static final int ABGR_FALLBACK  = 0xFF202020;
      /** Dark background for empty circular minimap (terrain off). */
      private static final int CIRCULAR_EMPTY_FILL = 0xFF1A1A1A;
  
      private static final Identifier TEX_ID =
              Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "minimap_terrain");

    public MinimapModule() {
        super(ID);
        this.anchor = HudAnchor.TOP_RIGHT;
        this.offsetX = -4;
        this.offsetY = 4;
    }

    @Override
    public boolean isConfigEnabled() {
        return AuroraConfig.get().minimapEnabled;
    }

    @Override
    protected AuroraConfig.HudBackground backgroundMode() {
        return AuroraConfig.HudBackground.NONE; // we draw our own frame
    }

    @Override
    public String featureRegistryId() {
        return "minimap";
    }

    @Override
    public int getWidth() {
        return AuroraConfig.get().minimapSize + 2; // +2 for 1px frame each side
    }

    @Override
    public int getHeight() {
        int base = AuroraConfig.get().minimapSize + 2;
        if (AuroraConfig.get().minimapShowCoords) {
            base += Minecraft.getInstance().font.lineHeight + 2;
        }
        return base;
    }

    @Override
    protected void renderContent(GuiGraphics g, Minecraft mc, int x, int y) {
        AuroraConfig cfg = AuroraConfig.get();
        if (mc.level == null || mc.player == null) return;

        int size = Math.max(32, cfg.minimapSize);
        int mapX = x + 1;
        int mapY = y + 1;

        boolean circular = cfg.minimapCircular;
        boolean rotate = cfg.minimapRotateWithPlayer;
        int border = cfg.minimapBorderColor;
        int cx = mapX + size / 2;
        int cy = mapY + size / 2;

        // --- Terrain texture ---
        ensureTexture(size);

        if (cfg.minimapShowTerrain) {
            // Throttle the O(size²) chunk-sampling pass. The terrain texture
            // is only re-sampled when the player has moved far enough to shift
            // a texel OR the interval elapsed (whichever comes first). All
            // overlays (entities/waypoints/compass/coords) still render every
            // frame, so there's no loss of responsiveness — only the static
            // terrain map is decimated, which is visually identical at these
            // cadences (the player moves <1 texel between samples).
            long now = System.currentTimeMillis();
            String curDim = mc.level.dimension().identifier().toString();
            float curYaw = rotate ? mc.player.getYRot() : 0f;
            if (shouldResampleTerrain(now, mc.player, size, cfg.minimapZoom,
                    circular, rotate, curDim, curYaw)) {
                sampleTerrain(mc.level, mc.player, size, cfg.minimapZoom, circular, rotate);
                terrainTex.upload();
                lastTerrainSampleMs = now;
                lastSamplePlayerX = mc.player.getX();
                lastSamplePlayerZ = mc.player.getZ();
                lastSampleYaw = curYaw;
                lastSampleSize = size;
                lastSampleZoom = cfg.minimapZoom;
                lastSampleCircular = circular;
                lastSampleRotate = rotate;
                lastSampleDim = curDim;
            }
        } else {
            clearTerrain(size);
        }

        // --- Draw terrain + border ---
        if (circular) {
            float halfSize = size * 0.5f;
            int ring = ringThickness(size);
            float ringInner = halfSize - ring;

            if (cfg.minimapShowTerrain) {
                // Terrain first — its disc edge is hidden by the ring on top.
                drawTerrain(g, mapX, mapY, size, (float) cfg.minimapTerrainAlpha);
            } else {
                // Dark fill inside the ring when terrain is off.
                fillCircle(g, cx, cy, (int) Math.floor(ringInner), CIRCULAR_EMPTY_FILL);
            }
            // Thick, even ring drawn LAST (on top) so its single-pass edge
            // defines the border cleanly, hiding the terrain disc's stair-step.
            drawRing(g, cx, cy, halfSize, ringInner, border);
        } else {
            drawTerrain(g, mapX, mapY, size, (float) cfg.minimapTerrainAlpha);
            // 1px square frame
            g.fill(x, y, x + size + 2, y + 1, border);                      // top
            g.fill(x, y + size + 1, x + size + 2, y + size + 2, border);    // bottom
            g.fill(x, y, x + 1, y + size + 2, border);                      // left
            g.fill(x + size + 1, y, x + size + 2, y + size + 2, border);    // right
        }

        // --- Entity + waypoint overlays (screen-space) ---
        float pxPerBlock = size / (float) cfg.minimapZoom;
        float yaw = rotate ? mc.player.getYRot() : 0f;
        float rad = (float) Math.toRadians(yaw);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        if (cfg.minimapShowWaypoints) {
            drawWaypoints(g, mc, cx, cy, pxPerBlock, cos, sin, size, circular);
        }
        if (cfg.minimapShowEntities) {
            drawEntities(g, mc, cx, cy, pxPerBlock, cos, sin, size, circular);
        }

        // --- Self arrow at center ---
        if (cfg.minimapShowSelfArrow) {
            drawSelfArrow(g, cx, cy, mc.player.getYRot(), rotate);
        }

        // --- Compass letters ---
        if (cfg.minimapShowCompass) {
            drawCompass(g, mapX, mapY, size, mc.player.getYRot(), rotate, mc);
        }

        // --- Coordinate readout ---
        if (cfg.minimapShowCoords) {
            BlockPos p = mc.player.blockPosition();
            String coords = p.getX() + " / " + p.getY() + " / " + p.getZ();
            int tw = mc.font.width(coords);
            int tx = x + (getWidth() - tw) / 2;
            int ty = y + size + 2 + 1;
            g.drawString(mc.font, coords, tx, ty, cfg.minimapCoordColor, false);
        }
    }

    // ============================================================
    //  Terrain sampling
    // ============================================================

    private void ensureTexture(int size) {
        if (terrainImage == null || lastTexSize != size) {
            if (terrainTex != null) {
                terrainTex.close();
                terrainTex = null;
            }
            terrainImage = new NativeImage(NativeImage.Format.RGBA, size, size, false);
            clearTerrain(size);
            terrainTex = new DynamicTexture(() -> "minimap_terrain", terrainImage);
            Minecraft.getInstance().getTextureManager().register(TEX_ID, terrainTex);
            lastTexSize = size;
        }
    }

    private void clearTerrain(int size) {
        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                terrainImage.setPixelABGR(px, py, 0x00000000);
            }
        }
        if (terrainTex != null) terrainTex.upload();
    }

    /**
     * Decide whether the terrain texture should be re-sampled this frame.
     * Re-samples immediately (returns true) when:
     * <ul>
     *   <li>there is no prior sample (first frame / after teleport detected as NaN);</li>
     *   <li>the dimension changed (nether portal etc.);</li>
     *   <li>the minimap size/zoom/shape/rotate setting changed;</li>
     *   <li>the player moved by at least one texel's worth of blocks since
     *       the last sample — guarantees the map scrolls smoothly with no lag.</li>
     * </ul>
     * Otherwise, re-sample at most once per {@link #TERRAIN_SAMPLE_INTERVAL_MS}.
     * This reduces the chunk-data sampling cost by ~6× at 60fps with zero
     * perceptible difference: the map is a coarse abstraction and the player
     * traverses well under one texel between samples.
     */
    private boolean shouldResampleTerrain(long nowMs, LocalPlayer player, int size,
                                          int zoom, boolean circular, boolean rotate,
                                          String curDim, float curYaw) {
        // First-ever sample, or state changed.
        if (lastSampleSize != size || lastSampleZoom != zoom
                || lastSampleCircular != circular || lastSampleRotate != rotate) {
            return true;
        }
        if (lastSampleDim == null || !lastSampleDim.equals(curDim)) {
            return true;
        }
        // Distance since last sample, in blocks. Re-sample as soon as the
        // player has crossed ~half a texel so scroll never visibly lags.
        if (Double.isNaN(lastSamplePlayerX) || Double.isNaN(lastSamplePlayerZ)) {
            return true;
        }
        double dxP = player.getX() - lastSamplePlayerX;
        double dzP = player.getZ() - lastSamplePlayerZ;
        double movedBlocks = Math.sqrt(dxP * dxP + dzP * dzP);
        double blocksPerTexel = zoom / (double) size;
        if (movedBlocks >= blocksPerTexel * 0.5) {
            return true;
        }
        // When rotation is enabled, the player's yaw is baked into the
        // sampling pass — so turning in place must also re-sample to keep
        // the map rotation smooth. Re-sample as soon as the yaw has moved
        // by ~half a texel's angular width at the map edge.
        // (When rotate is off, curYaw is always 0 and lastSampleYaw is
        // pinned to 0 at sample time, so this branch is inert.)
        if (rotate && !Float.isNaN(lastSampleYaw)) {
            float yawDelta = Math.abs(angleDeltaDeg(curYaw, lastSampleYaw));
            // Angular size of one texel at the map edge, in degrees.
            double edgeBlocks = (zoom / 2.0);
            double texelAngleDeg = Math.toDegrees(Math.atan2(blocksPerTexel, edgeBlocks));
            if (yawDelta >= texelAngleDeg * 0.5) {
                return true;
            }
        }
        // Interval fallback — even when standing perfectly still we refresh
        // the map periodically so block edits / growth catch up.
        return (nowMs - lastTerrainSampleMs) >= TERRAIN_SAMPLE_INTERVAL_MS;
    }

    /**
     * Shortest signed angular difference between two yaw values in degrees,
     * returned as an absolute value in {@code [0, 180]}. Handles the
     * 360&deg;/-0&deg; wraparound so a turn from 359&deg; to 1&deg;
     * correctly reads as 2&deg; rather than 358&deg;.
     */
    private static float angleDeltaDeg(float a, float b) {
        float d = ((a - b) % 360f + 540f) % 360f - 180f;
        return d;
    }

    /**
     * Sample one block per output texel. The texture is always screen-aligned.
     * When {@code rotate} is on, we inverse-rotate each texel's screen offset
     * by the player's yaw to find the corresponding world block — no pose
     * rotation is needed at blit time, and no texture enlargement is needed.
     *
     * <p>When {@code circular} is on, texels outside the inscribed disc
     * (minus 1px for the border ring) are set to fully transparent and
     * skipped, which also reduces sampling cost by ~21%.
     *
     * <p><b>Allocation-free hot loop:</b> the two rolling hillshade rows
     * ({@link #rowA}/{@link #rowB}) and both mutable block positions
     * ({@link #samplePos}/{@link #waterPos}) are reused across calls, so
     * the only per-sample cost is the chunk-data access itself — zero GC
     * pressure regardless of size.
     */
    private void sampleTerrain(ClientLevel level, LocalPlayer player, int size,
                               int zoom, boolean circular, boolean rotate) {
        double playerX = player.getX();
        double playerZ = player.getZ();
        int minY = level.getMinY();
        BlockColors colors = Minecraft.getInstance().getBlockColors();

        // World Map tile cache as the primary terrain source (config-gated).
        // A null engine, disabled feature, non-resident tile, or uncaptured
        // column all degrade to the live sampler below via the 0 sentinel —
        // the minimap keeps working with the World Map feature turned off.
        WorldMapClient wm = AuroraConfig.get().minimapUseWorldMapCache
                ? WorldMapClient.get() : null;

        float center = size * 0.5f;
        float invPxPerBlock = zoom / (float) size; // blocks per texel
        float terrainRadius = circular ? center - 1f : center;
        float terrainRadiusSq = terrainRadius * terrainRadius;

        float yawRad = rotate ? (float) Math.toRadians(player.getYRot()) : 0f;
        float cos = (float) Math.cos(yawRad);
        float sin = (float) Math.sin(yawRad);

        // Ensure the reusable rolling-row buffers are sized for this texture.
        if (rowA == null || rowA.length != size) {
            rowA = new int[size];
            rowB = new int[size];
        }
        // aboveRow = rowA (reset to MIN_VALUE), curRow = rowB (overwritten).
        int[] aboveRow = rowA;
        int[] curRow = rowB;
        Arrays.fill(aboveRow, Integer.MIN_VALUE);

        for (int ty = 0; ty < size; ty++) {
            // Swap buffers: this row's curRow is the next row's aboveRow.
            int[] tmp = aboveRow;
            aboveRow = curRow;
            curRow = tmp;

            for (int tx = 0; tx < size; tx++) {
                float sx = tx + 0.5f - center;
                float sy = ty + 0.5f - center;

                // Circular alpha mask — corner texels are fully transparent.
                if (circular && sx * sx + sy * sy > terrainRadiusSq) {
                    terrainImage.setPixelABGR(tx, ty, 0x00000000);
                    curRow[tx] = Integer.MIN_VALUE;
                    continue;
                }

                // Inverse-rotate screen offset to world offset.
                float worldDX, worldDZ;
                if (rotate) {
                    worldDX = (sx * cos - sy * sin) * invPxPerBlock;
                    worldDZ = (sx * sin + sy * cos) * invPxPerBlock;
                } else {
                    worldDX = sx * invPxPerBlock;
                    worldDZ = sy * invPxPerBlock;
                }

                int worldX = (int) Math.floor(playerX + worldDX);
                int worldZ = (int) Math.floor(playerZ + worldDZ);

                // Cache fast path: one array read from the resident region
                // tile instead of a heightmap lookup + blockstate query +
                // biome tint. The captured pixel already carries tint and
                // baked north-up hillshade, so no height reference is kept
                // for the rolling hillshade rows (MIN_VALUE disables shading
                // for the next row, exactly like an unloaded column).
                int cached = wm != null ? wm.sampleSurfaceAbgr(worldX, worldZ) : 0;
                if (cached != 0 && cached != ABGR_UNLOADED) {
                    curRow[tx] = Integer.MIN_VALUE;
                    terrainImage.setPixelABGR(tx, ty, cached);
                    continue;
                }

                int h = surfaceHeight(level, worldX, worldZ, minY);
                curRow[tx] = h;
                int color = sampleColumn(level, worldX, worldZ, minY, samplePos,
                        h, aboveRow[tx], colors);
                terrainImage.setPixelABGR(tx, ty, color);
            }
        }
        // Publish the buffers back for next call (order doesn't matter;
        // both are reset to MIN_VALUE at the top of the next sample).
        rowA = aboveRow;
        rowB = curRow;
    }

    private int surfaceHeight(ClientLevel level, int worldX, int worldZ, int minY) {
        try {
            int h = level.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                    worldX, worldZ);
            return h <= minY ? Integer.MIN_VALUE : h;
        } catch (Throwable t) {
            return Integer.MIN_VALUE;
        }
    }

    /**
     * Resolve the map color for the surface block at (worldX, worldZ),
     * applying biome tint and hillshade relief (Xaeros-style). Returns an
     * ABGR int for {@link NativeImage#setPixelABGR}.
     */
    private int sampleColumn(ClientLevel level, int worldX, int worldZ, int minY,
                             BlockPos.MutableBlockPos pos, int height,
                             int aboveHeight, BlockColors colors) {
        try {
            if (height == Integer.MIN_VALUE) return ABGR_UNLOADED;

            pos.set(worldX, height - 1, worldZ);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                pos.set(worldX, height - 2, worldZ);
                state = level.getBlockState(pos);
                if (state.isAir()) return ABGR_UNLOADED;
            }

            boolean water = state.getFluidState().getType() instanceof WaterFluid;

            int depth = 0;
            if (water) {
                BlockPos.MutableBlockPos wp = pos;
                while (wp.getY() > minY && depth < 12) {
                    wp.move(0, -1, 0);
                    if (!(level.getBlockState(wp).getFluidState().getType() instanceof WaterFluid)) break;
                    depth++;
                }
            }

            int tint = -1;
            try {
                tint = colors.getColor(state, level, pos, 0);
            } catch (Throwable ignored) {}

            int r, gr, b;
            if (water) {
                if (tint != -1) {
                    r  = (tint >> 16) & 0xFF;
                    gr = (tint >> 8) & 0xFF;
                    b  = tint & 0xFF;
                } else {
                    r = 38; gr = 96; b = 204;
                }
                float dk = Math.max(0.42f, 1f - depth * 0.07f);
                r  = (int) (r  * dk);
                gr = (int) (gr * dk);
                b  = (int) (b  * dk);
            } else {
                MapColor mc = state.getMapColor(level, pos);
                if (mc == null) {
                    r = 32; gr = 32; b = 32;
                } else {
                    int col = mc.col;
                    r  = (col >> 16) & 0xFF;
                    gr = (col >> 8) & 0xFF;
                    b  = col & 0xFF;
                    if (tint != -1) {
                        r  = r  * ((tint >> 16) & 0xFF) / 255;
                        gr = gr * ((tint >> 8) & 0xFF) / 255;
                        b  = b  * (tint & 0xFF) / 255;
                    }
                }
            }

            // Hillshade: compare against the screen-space "up" neighbor.
            float shade = 1.0f;
            if (aboveHeight != Integer.MIN_VALUE) {
                int diff = height - aboveHeight;
                shade = 1.0f + diff * 0.075f;
                if (shade < 0.62f) shade = 0.62f;
                if (shade > 1.32f) shade = 1.32f;
            }
            r  = clamp8(r  * shade);
            gr = clamp8(gr * shade);
            b  = clamp8(b  * shade);

            return (0xFF << 24) | (b << 16) | (gr << 8) | r;
        } catch (Throwable t) {
            return ABGR_FALLBACK;
        }
    }

    private static int clamp8(double v) {
        return v <= 0 ? 0 : (v >= 255 ? 255 : (int) v);
    }

    // ============================================================
    //  Drawing — terrain blit + helpers
    // ============================================================

    private void drawTerrain(GuiGraphics g, int x, int y, int size, float alpha) {
        // Simple axis-aligned blit — rotation is handled in the sampling pass.
        int tint = ((int) (alpha * 255) << 24) | 0x00FFFFFF;
        g.blit(RenderPipelines.GUI_TEXTURED, TEX_ID,
                x, y, 0f, 0f, size, size, size, size, tint);
    }

    /** Fill a solid disc of the given radius centered at (cx, cy). */
    private static void fillCircle(GuiGraphics g, int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int span = (int) Math.round(Math.sqrt(Math.max(0, (long) radius * radius - (long) dy * dy)));
            int rowY = cy + dy;
            g.fill(cx - span, rowY, cx + span + 1, rowY + 1, color);
        }
    }

    /**
     * Draw a ring (annulus) between {@code outerR} and {@code innerR}.
     * Uses {@code Math.round} on both spans for balanced pixel coverage,
     * producing a smoother, more even circle than {@code floor}.
     */
    private static void drawRing(GuiGraphics g, int cx, int cy,
                                 float outerR, float innerR, int color) {
        int max = (int) Math.ceil(outerR);
        float oR2 = outerR * outerR;
        float iR2 = innerR * innerR;
        for (int dy = -max; dy <= max; dy++) {
            float fy2 = dy * dy;
            if (fy2 > oR2) continue;
            int outerSpan = (int) Math.round(Math.sqrt(oR2 - fy2));
            int innerSpan = fy2 < iR2 ? (int) Math.round(Math.sqrt(iR2 - fy2)) : -1;
            int rowY = cy + dy;
            if (innerSpan >= 0) {
                g.fill(cx - outerSpan, rowY, cx - innerSpan, rowY + 1, color);
                g.fill(cx + innerSpan + 1, rowY, cx + outerSpan + 1, rowY + 1, color);
            } else {
                g.fill(cx - outerSpan, rowY, cx + outerSpan + 1, rowY + 1, color);
            }
        }
    }

    /** Ring thickness in pixels — scales mildly with minimap size (2px min). */
    private static int ringThickness(int size) {
        return Math.max(2, Math.round(size * 0.025f));
    }

    // ============================================================
    //  Overlays — waypoints, entities, self arrow, compass
    // ============================================================

    private void drawWaypoints(GuiGraphics g, Minecraft mc,
                               int cx, int cy, float pxPerBlock,
                               float cos, float sin, int size,
                               boolean circular) {
        WaypointFeature feat = WaypointFeature.get();
        if (feat == null) return;
        List<Waypoint> list = feat.currentWorldWaypoints();
        if (list.isEmpty()) return;

        LocalPlayer player = mc.player;
        if (player == null) return;
        String curDim = mc.level.dimension().identifier().toString();

        double playerX = player.getX();
        double playerZ = player.getZ();
        float maxLen = overlayMaxLen(size, circular);

        boolean showDist = AuroraConfig.get().minimapShowWaypointDistance;
        int labelsDrawn = 0;

        for (Waypoint w : list) {
            if (w.dimension != null && !w.dimension.equals(curDim)) continue;
            double dx = (w.x + 0.5) - playerX;
            double dz = (w.z + 0.5) - playerZ;
            float rx = (float) (dx * cos + dz * sin);
            float rz = (float) (-dx * sin + dz * cos);

            float pX = rx * pxPerBlock;
            float pY = rz * pxPerBlock;
            float len = (float) Math.sqrt(pX * pX + pY * pY);
            boolean clamped = len > maxLen;
            if (clamped) {
                pX = pX / len * maxLen;
                pY = pY / len * maxLen;
            }
            int sx = cx + (int) pX;
            int sy = cy + (int) pY;

            int c = w.color == 0 ? 0xFFFFAA00 : w.color;
            g.fill(sx - 2, sy, sx + 3, sy + 1, c);
            g.fill(sx, sy - 2, sx + 1, sy + 3, c);
            g.fill(sx - 1, sy - 1, sx + 2, sy + 2, c);

            // Distance label on rim-clamped (out-of-view) waypoints — the
            // cheap direction+distance indicator. Capped at four labels so
            // a long waypoint list can't crowd the rim.
            if (clamped && showDist && labelsDrawn < 4) {
                labelsDrawn++;
                String s = formatDistance(Math.sqrt(dx * dx + dz * dz));
                int tw = mc.font.width(s);
                // Text sits toward the map interior (opposite the rim side).
                int lx = pX >= 0 ? sx - 6 - tw : sx + 6;
                g.drawString(mc.font, s, lx, sy - 4, 0xFFFFFFFF, false);
            }
        }
    }

    /** Compact block distance: {@code 812} → "812", {@code 12345} → "12.3k". */
    private static String formatDistance(double blocks) {
        if (blocks < 1000) return String.valueOf((int) Math.round(blocks));
        return String.format(java.util.Locale.ROOT, "%.1fk", blocks / 1000.0);
    }

    private void drawEntities(GuiGraphics g, Minecraft mc,
                              int cx, int cy, float pxPerBlock,
                              float cos, float sin, int size,
                              boolean circular) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        AuroraConfig cfg = AuroraConfig.get();

        // Early exit: if no entity category is visible, skip the (allocation +
        // iteration heavy) entity query entirely. The AABB allocation and
        // getEntities search would otherwise run every frame for nothing.
        if (!cfg.minimapShowPlayers && !cfg.minimapShowHostiles && !cfg.minimapShowPassives) {
            return;
        }

        double playerX = player.getX();
        double playerZ = player.getZ();
        int zoom = cfg.minimapZoom;
        AABB area = AABB.ofSize(
                net.minecraft.world.phys.Vec3.atCenterOf(player.blockPosition()),
                zoom * 2, zoom * 2, zoom * 2);

        List<Entity> entities = mc.level.getEntities(player, area);
        float maxLen = overlayMaxLen(size, circular);

        for (Entity e : entities) {
            if (e == player) continue;
            if (!(e instanceof LivingEntity)) continue;
            if (e.isInvisible()) continue;

            int color;
            boolean show;
            if (e instanceof Player) {
                color = 0xFFFFFFFF;
                show = cfg.minimapShowPlayers;
            } else if (e instanceof Monster) {
                color = com.aurora.client.theme.HudStatus.DOT_HOSTILE;
                show = cfg.minimapShowHostiles;
            } else if (e instanceof Animal) {
                color = com.aurora.client.theme.HudStatus.DOT_PASSIVE;
                show = cfg.minimapShowPassives;
            } else {
                color = com.aurora.client.theme.HudStatus.DOT_NEUTRAL;
                show = cfg.minimapShowPassives;
            }
            if (!show) continue;

            double dx = e.getX() - playerX;
            double dz = e.getZ() - playerZ;
            float rx = (float) (dx * cos + dz * sin);
            float rz = (float) (-dx * sin + dz * cos);
            float pX = rx * pxPerBlock;
            float pY = rz * pxPerBlock;

            float len = (float) Math.sqrt(pX * pX + pY * pY);
            if (len > maxLen) {
                pX = pX / len * maxLen;
                pY = pY / len * maxLen;
            }
            int sx = cx + (int) pX;
            int sy = cy + (int) pY;
            g.fill(sx - 1, sy - 1, sx + 2, sy + 2, color);
        }
    }

    /**
     * Maximum radial distance (in pixels) an overlay dot may sit from the
     * map center. Circular maps clamp to the terrain disc (which is 1px
     * inside the border ring); square maps to the half-edge.
     */
    private static float overlayMaxLen(int size, boolean circular) {
        float half = size * 0.5f;
        // Circular maps clamp to inside the ring; square to inside the frame.
        float lim = circular ? half - ringThickness(size) - 1f : half - 2f;
        return Math.max(1f, lim);
    }

    private void drawSelfArrow(GuiGraphics g, int cx, int cy, float yawDeg, boolean rotateWithPlayer) {
        float arrowYaw = rotateWithPlayer ? 0f : yawDeg;
        float rad = (float) Math.toRadians(arrowYaw);
        int color = 0xFFFFFFFF;

        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, color);

        int steps = 5;
        float sx = (float) Math.sin(rad);
        float sy = -(float) Math.cos(rad);
        for (int i = 1; i <= steps; i++) {
            int lx = cx + Math.round(sx * i);
            int ly = cy + Math.round(sy * i);
            g.fill(lx, ly, lx + 1, ly + 1, color);
        }
    }

    private void drawCompass(GuiGraphics g, int x, int y, int size,
                             float yawDeg, boolean rotateWithPlayer, Minecraft mc) {
        float cx = x + size * 0.5f;
        float cy = y + size * 0.5f;
        float radius = size * 0.5f - 6;

        String[] letters = {"N", "E", "S", "W"};
        float[] angles = {0, 90, 180, 270};

        for (int i = 0; i < 4; i++) {
            float a = rotateWithPlayer ? angles[i] - yawDeg : angles[i];
            float rad = (float) Math.toRadians(a);
            float lx = (float) Math.sin(rad) * radius;
            float ly = -(float) Math.cos(rad) * radius;
            int sx = (int) (cx + lx);
            int sy = (int) (cy + ly);
            int tw = mc.font.width(letters[i]);
            g.drawString(mc.font, letters[i], sx - tw / 2, sy - 4, 0xFFFFFFFF, false);
        }
    }

    @Override
    public void resetLayoutToDefaults() {
        this.anchor = HudAnchor.TOP_RIGHT;
        this.offsetX = -4;
        this.offsetY = 4;
        this.scale = 1.0f;
        this.enabled = true;
        this.locked = false;
    }
}