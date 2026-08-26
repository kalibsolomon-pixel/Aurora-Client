package com.aurora.client.worldmap.capture;

import com.aurora.client.worldmap.cache.RegionCache.RegionTile;
import com.aurora.client.worldmap.cache.WorldMapSize;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.WaterFluid;

/**
 * Renders one chunk (16×16 columns) into its region tile's NativeImage.
 *
 * <p>The column sampler is a direct port of the minimap's proven pipeline
 * ({@code MinimapModule#sampleColumn}): WORLD_SURFACE heightmap top block →
 * map color + biome tint, water darkened by depth, hillshade relief against
 * the north (-Z) neighbor. The only difference is the shading reference:
 * the minimap compares against screen-up (it rotates); the world map has a
 * fixed north-up orientation, so the neighbor is always the column at
 * {@code z - 1} — giving the classic Xaero's-style relief lighting.
 *
 * <p>Pixel packing matches {@link com.mojang.blaze3d.platform.NativeImage#setPixelABGR}
 * ({@code 0xAABBGGRR}).
 *
 * <p>Client thread only — NativeImage writes.
 */
public final class ChunkCapturer {

    /** ABGR for columns with no loaded surface (matches the minimap's void color). */
    private static final int ABGR_UNLOADED = 0xFF121417;
    /** ABGR fallback when sampling throws. */
    private static final int ABGR_FALLBACK = 0xFF202020;
    /** Default water color when biome tint is unavailable (matches the minimap). */
    private static final int WATER_R = 38, WATER_G = 96, WATER_B = 204;

    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos WATER_POS = new BlockPos.MutableBlockPos();

    private ChunkCapturer() {}

    /**
     * Captures chunk {@code (cx, cz)} into {@code tile}. Idempotent —
     * re-captures simply overwrite previous pixels and bump the chunk's
     * timestamp.
     */
    public static void capture(ClientLevel level, int cx, int cz, RegionTile tile) {

        int minY = level.getMinY();
        BlockColors colors = Minecraft.getInstance().getBlockColors();
        long now = System.currentTimeMillis();

        // Chunk-local origin inside the 512×512 region image.
        int px0 = (cx & (WorldMapSize.CHUNKS - 1)) << 4;
        int pz0 = (cz & (WorldMapSize.CHUNKS - 1)) << 4;

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                tile.image.setPixelABGR(px0 + x, pz0 + z,
                        sampleColumn(level, (cx << 4) + x, (cz << 4) + z, minY, colors));
            }
        }

        int stampIdx = ((cz & (WorldMapSize.CHUNKS - 1)) << WorldMapSize.CHUNK_SHIFT)
                | (cx & (WorldMapSize.CHUNKS - 1));
        tile.stamps[stampIdx] = now;
        tile.dirty = true;
        tile.uploadPending = true;
    }

    /** Surface height via the WORLD_SURFACE heightmap; MIN_VALUE when unloaded. */
    private static int surfaceHeight(ClientLevel level, int worldX, int worldZ, int minY) {
        try {
            int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);
            return h <= minY ? Integer.MIN_VALUE : h;
        } catch (Throwable t) {
            return Integer.MIN_VALUE;
        }
    }

    /**
     * Resolve the map color for the surface block at (worldX, worldZ),
     * applying biome tint and hillshade relief. Returns an ABGR int.
     */
    private static int sampleColumn(ClientLevel level, int worldX, int worldZ, int minY,
                                    BlockColors colors) {
        try {
            int height = surfaceHeight(level, worldX, worldZ, minY);
            if (height == Integer.MIN_VALUE) return ABGR_UNLOADED;

            POS.set(worldX, height - 1, worldZ);
            BlockState state = level.getBlockState(POS);
            if (state.isAir()) {
                POS.set(worldX, height - 2, worldZ);
                state = level.getBlockState(POS);
                if (state.isAir()) return ABGR_UNLOADED;
            }

            boolean water = state.getFluidState().getType() instanceof WaterFluid;

            int depth = 0;
            if (water) {
                WATER_POS.set(POS);
                while (WATER_POS.getY() > minY && depth < 12) {
                    WATER_POS.move(0, -1, 0);
                    if (!(level.getBlockState(WATER_POS).getFluidState().getType() instanceof WaterFluid)) break;
                    depth++;
                }
            }

            int tint = -1;
            try {
                tint = colors.getColor(state, level, POS, 0);
            } catch (Throwable ignored) {}

            int r, gr, b;
            if (water) {
                if (tint != -1) {
                    r  = (tint >> 16) & 0xFF;
                    gr = (tint >> 8) & 0xFF;
                    b  = tint & 0xFF;
                } else {
                    r = WATER_R; gr = WATER_G; b = WATER_B;
                }
                float dk = Math.max(0.42f, 1f - depth * 0.07f);
                r  = (int) (r  * dk);
                gr = (int) (gr * dk);
                b  = (int) (b  * dk);
            } else {
                MapColor mc = state.getMapColor(level, POS);
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

            // Hillshade against the north (-Z) neighbor — fixed north-up map.
            float shade = 1.0f;
            int north = surfaceHeight(level, worldX, worldZ - 1, minY);
            if (north != Integer.MIN_VALUE) {
                shade = 1.0f + (height - north) * 0.075f;
                if (shade < 0.62f) shade = 0.62f;
                if (shade > 1.32f) shade = 1.32f;
            }
            r  = clamp8(r  * shade);
            gr = clamp8(gr * shade);
            b  = clamp8(b * shade);

            return (0xFF << 24) | (b << 16) | (gr << 8) | r;
        } catch (Throwable t) {
            return ABGR_FALLBACK;
        }
    }

    private static int clamp8(double v) {
        return v <= 0 ? 0 : (v >= 255 ? 255 : (int) v);
    }
}

