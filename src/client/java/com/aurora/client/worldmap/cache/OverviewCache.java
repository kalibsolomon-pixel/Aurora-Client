package com.aurora.client.worldmap.cache;

import com.aurora.client.AuroraClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Far-zoom overview layer: a 64×64 box-downsampled copy of every 512×512
 * region tile in a large dedicated LRU (16 KB per region). A tile becomes
 * renderable only after a full-image paint — a one-shot disk load of its
 * region, or a downsample of an already-resident full-res tile. Per-chunk
 * capture refreshes update loaded tiles but never promote unloaded ones
 * (2 of 4096 texels is not an overview). Derived data: never persisted,
 * dropped with the dimension context. Client thread only.
 */
public final class OverviewCache {

    /** One-shot full-paint logging when {@code -Daurora.worldmap.debugTiles=true}. */
    static final boolean DEBUG_TILES = Boolean.getBoolean("aurora.worldmap.debugTiles");
    /** Overview texels per region side. */
    public static final int SIZE = 64;
    /** Blocks per overview texel (512 / 64). */
    public static final int DOWN = WorldMapSize.BLOCKS / SIZE;
    /** Tile budget: 2048 × 16 KB ≈ 32 MB. */
    private static final int CAPACITY = 2048;

    private final Map<Long, OverviewTile> tiles = new LinkedHashMap<>(256, 0.75f, true);

    /** Returns the tile for {@code key}, creating an unloaded one when absent. */
    public OverviewTile acquire(long key) {
        OverviewTile tile = tiles.get(key);
        if (tile == null) {
            tile = new OverviewTile(key);
            tiles.put(key, tile);
            evictOverflow();
        }
        return tile;
    }

    /** Like {@link #acquire} but never creates and does not bump LRU order. */
    public OverviewTile peek(long key) {
        return tiles.get(key);
    }

    /** Releases every tile (dimension/scope switch, disconnect). */
    public void closeAll() {
        Iterator<OverviewTile> it = tiles.values().iterator();
        while (it.hasNext()) {
            OverviewTile tile = it.next();
            it.remove();
            tile.release();
        }
    }

    private void evictOverflow() {
        Iterator<OverviewTile> it = tiles.values().iterator();
        while (it.hasNext() && tiles.size() > CAPACITY) {
            OverviewTile tile = it.next();
            it.remove();
            tile.release();
        }
    }

    /** Refreshes one chunk's 2×2 overview texels from a full-res tile image. */
    public void paintChunk(long key, int chunkLx, int chunkLz, NativeImage src) {
        acquire(key).paintChunk(chunkLx, chunkLz, src);
    }

    /** Refreshes a whole overview from a full-res tile image (after disk load). */
    public void repaint(long key, NativeImage src) {
        acquire(key).paintFull(src, "resident-repaint");
    }

    /** One 64×64 downsampled region; lifecycle mirrors {@code RegionTile}. */
    public static final class OverviewTile {

        public final long key;
        // useCalloc: unpainted texels must read as transparent black, never
        // malloc residue — per-chunk refreshes leave most texels untouched on
        // tiles that may already be rendered.
        final NativeImage image = new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, true);
        DynamicTexture tex;
        Identifier texId;
        /** This tile's data source was resolved — resident-tile downsample or
         *  disk load submission (once per lifetime). */
        public volatile boolean loadRequested;
        volatile boolean loaded;
        volatile boolean uploadPending;
        /** One-shot debug logging latch ({@link #DEBUG_TILES}). */
        boolean loggedFullPaint;

        OverviewTile(long key) {
            this.key = key;
        }

        public boolean isLoaded() {
            return loaded;
        }

        /** Texture identifier — only valid after {@link #ensureGpu()}. */
        public Identifier textureId() {
            return texId;
        }

        /** Creates and registers the GPU texture on first use. Client thread only. */
        public void ensureGpu() {
            if (tex != null) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getTextureManager() == null) return;
            String hex = Long.toHexString(key);
            texId = Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "worldmap/ov_" + hex);
            tex = new DynamicTexture(() -> "worldmap_ov_" + hex, image);
            mc.getTextureManager().register(texId, tex);
            tex.upload();
            uploadPending = false;
        }

        /** Uploads pending pixels if (and only if) a GPU texture exists. */
        public void flushUpload() {
            if (tex != null && uploadPending) {
                tex.upload();
                uploadPending = false;
            }
        }

        /** Frees GPU + CPU memory. */
        public void release() {
            if (tex != null) {
                try {
                    // TextureManager.release also closes the texture.
                    Minecraft.getInstance().getTextureManager().release(texId);
                } catch (Throwable ignored) {
                }
                tex = null;
                texId = null;
            }
            try {
                image.close();
            } catch (Throwable ignored) {
            }
        }

        /** Full repaint from the full-res tile image (ARGB reads). */
        void paintFull(NativeImage src, String source) {
            for (int ty = 0; ty < SIZE; ty++) {
                for (int tx = 0; tx < SIZE; tx++) {
                    image.setPixel(tx, ty, average(src, tx << 3, ty << 3));
                }
            }
            finishFullPaint(source);
        }

        /**
         * Repaints the 2×2 texels covered by one 16×16-block chunk. Live
         * refresh only — never marks the tile loaded (2 texels of 4096 is
         * not a renderable overview; the bulk paints do that).
         */
        void paintChunk(int chunkLx, int chunkLz, NativeImage src) {
            int tx0 = chunkLx << 1, tz0 = chunkLz << 1;
            for (int tz = tz0; tz < tz0 + 2; tz++) {
                for (int tx = tx0; tx < tx0 + 2; tx++) {
                    image.setPixel(tx, tz, average(src, tx << 3, tz << 3));
                }
            }
            finishPartialPaint();
        }

        /** Full repaint from an ABGR disk payload (the .wmr pixel layout). */
        public void paintAbgr(int[] abgr, String source) {
            for (int ty = 0; ty < SIZE; ty++) {
                for (int tx = 0; tx < SIZE; tx++) {
                    long a = 0, r = 0, g = 0, b = 0;
                    for (int y = 0; y < 8; y++) {
                        int row = ((ty << 3) + y) << 9; // BLOCKS == 512 wide
                        for (int x = 0; x < 8; x++) {
                            int p = abgr[row + (tx << 3) + x];
                            int pa = (p >>> 24) & 0xFF;
                            a += pa;
                            r += (p & 0xFF) * pa;          // ABGR: red is the low byte
                            g += ((p >>> 8) & 0xFF) * pa;
                            b += ((p >>> 16) & 0xFF) * pa;
                        }
                    }
                    image.setPixel(tx, ty, pack(a, r, g, b));
                }
            }
            finishFullPaint(source);
        }

        /** Full-image paint: the tile is now complete and renderable. */
        private void finishFullPaint(String source) {
            loaded = true;
            uploadPending = true;
            flushUpload();
            if (DEBUG_TILES && !loggedFullPaint) {
                loggedFullPaint = true;
                logPaintSummary(source);
            }
        }

        /**
         * Debug aid for the far-zoom color-flood investigation: logs this
         * tile's key, paint source and average painted color once per
         * lifetime. Many tiles logging identical averages would mean the
         * content pipeline is feeding every tile the same pixels — the
         * exact "one tile's color floods the map" signature.
         */
        private void logPaintSummary(String source) {
            long r = 0, g = 0, b = 0;
            int opaque = 0;
            for (int ty = 0; ty < SIZE; ty++) {
                for (int tx = 0; tx < SIZE; tx++) {
                    int p = image.getPixel(tx, ty); // ARGB
                    if ((p >>> 24) == 0) continue;
                    opaque++;
                    r += (p >>> 16) & 0xFF;
                    g += (p >>> 8) & 0xFF;
                    b += p & 0xFF;
                }
            }
            String where = Long.toHexString(key) + " (" + RegionCache.regionX(key)
                    + "," + RegionCache.regionZ(key) + ")";
            if (opaque == 0) {
                AuroraClient.LOGGER.info("[WorldMap] ov paint {} src={} fully transparent", where, source);
            } else {
                AuroraClient.LOGGER.info("[WorldMap] ov paint {} src={} avgRGB=({},{},{}) opaque={}/{}",
                        where, source, r / opaque, g / opaque, b / opaque, opaque, SIZE * SIZE);
            }
        }

        /** Partial (per-chunk) refresh: upload only; load state is unchanged. */
        private void finishPartialPaint() {
            uploadPending = true;
            flushUpload();
        }

        /** Alpha-weighted 8×8 average read as ARGB via {@code getPixel}. */
        private static int average(NativeImage src, int x0, int y0) {
            long a = 0, r = 0, g = 0, b = 0;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int p = src.getPixel(x0 + x, y0 + y);
                    int pa = (p >>> 24) & 0xFF;
                    a += pa;
                    r += ((p >>> 16) & 0xFF) * pa;        // ARGB
                    g += ((p >>> 8) & 0xFF) * pa;
                    b += (p & 0xFF) * pa;
                }
            }
            return pack(a, r, g, b);
        }

        /** Packs weighted sums into an opaque ARGB texel (transparent under 15% cover). */
        private static int pack(long a, long r, long g, long b) {
            if (a < 64L * 255L * 15L / 100L) return 0;
            int rr = (int) ((r + a / 2) / a);
            int gg = (int) ((g + a / 2) / a);
            int bb = (int) ((b + a / 2) / a);
            return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
        }
    }
}