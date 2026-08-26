package com.aurora.client.worldmap.cache;

import com.aurora.client.AuroraClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU cache of region tiles. One tile = 32×32 chunks = one 512×512
 * {@link NativeImage} backed by a lazily-created {@link DynamicTexture}.
 *
 * <h2>Why regions instead of per-chunk textures</h2>
 * Any viewport can only ever touch a handful of regions (≤ ~12 at the
 * farthest MVP zoom), so the fullscreen map renders in at most a dozen
 * blits per frame — a straight GPU-side composite with no per-frame
 * recompositing, mirroring how the minimap draws its single terrain
 * texture.
 *
 * <h2>Threading contract</h2>
 * Every method must be called on the client thread: NativeImage pixel
 * writes/uploads and {@code TextureManager} registration are client/render
 * thread only. Disk IO lives in {@code WorldMapStorage} and only ever sees
 * plain-array snapshots handed over by {@code WorldMapClient}.
 *
 * <h2>Eviction</h2>
 * The map is {@code accessOrder=true}, so the eldest entry is the least
 * recently <em>used</em> (rendered or captured), not the least recently
 * created. Evicted dirty tiles are flushed by the {@link EvictionListener}
 * (registered by {@code WorldMapClient}) before their memory is freed.
 */
public final class RegionCache {

    /** Notified on the client thread whenever a tile falls out of the LRU. */
    public interface EvictionListener {
        void onEvict(RegionTile tile);
    }

    private final int capacity;
    private final EvictionListener listener;
    private final Map<Long, RegionTile> tiles = new LinkedHashMap<>(64, 0.75f, true);

    public RegionCache(int capacity, EvictionListener listener) {
        this.capacity = Math.max(8, capacity);
        this.listener = listener;
    }

    /** Packs a region coordinate pair into a long map key. */
    public static long key(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }

    public static int regionX(long key) { return (int) (key >> 32); }
    public static int regionZ(long key) { return (int) key; }

    /**
     * Returns the tile for {@code key}, creating an empty (all-transparent)
     * tile when absent, and bumps its LRU position. Newly created tiles
     * start with {@code loaded == false} — the caller owns submitting the
     * disk load and flipping the flag when it completes.
     */
    public RegionTile acquire(long key) {
        RegionTile tile = tiles.get(key);
        if (tile == null) {
            tile = new RegionTile(key);
            tiles.put(key, tile);
            evictOverflow();
        }
        return tile;
    }

    /** Like {@link #acquire} but never creates and does not bump LRU order. */
    public RegionTile peek(long key) {
        return tiles.get(key);
    }

    /** Snapshot of all live tiles (for autosave sweeps). */
    public Collection<RegionTile> all() {
        return new ArrayList<>(tiles.values());
    }

    public int size() {
        return tiles.size();
    }

    /** Flush+release every tile (scope/dimension switch, disconnect). */
    public void closeAll() {
        Iterator<RegionTile> it = tiles.values().iterator();
        while (it.hasNext()) {
            RegionTile tile = it.next();
            it.remove();
            listener.onEvict(tile);
        }
    }

    private void evictOverflow() {
        Iterator<RegionTile> it = tiles.values().iterator();
        while (it.hasNext() && tiles.size() > capacity) {
            RegionTile tile = it.next();
            it.remove();
            listener.onEvict(tile);
        }
    }
    // ================================================================
    //  Tile
    // ================================================================

    /**
     * One 512×512 region tile. Lifecycle:
     * <ol>
     *   <li>Created transparent; {@code loaded == false} while the disk load
     *       is in flight (captures for the region are deferred until then so
     *       the load result can never overwrite fresh captures).</li>
     *   <li>Pixels painted chunk-by-chunk by {@code ChunkCapturer} (opaque
     *       ARGB for explored chunks; transparent texels show the screen's
     *       void background).</li>
     *   <li>GPU texture created lazily on first draw — tiles that are never
     *       rendered never touch the TextureManager.</li>
     *   <li>Released on LRU eviction / cache close; the listener flushes
     *       dirty tiles to disk first.</li>
     * </ol>
     */
    public static final class RegionTile {

        public final long key;
        /** Per-chunk last-capture timestamp (ms); 0 = never captured. */
        public final long[] stamps = new long[WorldMapSize.CHUNKS * WorldMapSize.CHUNKS];

        public final NativeImage image;
        DynamicTexture tex;
        Identifier texId;

        /** Has unsaved pixel changes. */
        public volatile boolean dirty;
        /** Pixels changed since the last GPU upload. */
        public volatile boolean uploadPending;
        /** A disk load has been submitted for this tile (once per lifetime). */
        public volatile boolean loadRequested;
        /** Disk load completed (or there was nothing on disk). */
        public volatile boolean loaded;


        RegionTile(long key) {
            this.key = key;
            // The NativeImage buffer is zero-initialized (transparent black
            // = "unexplored"); no explicit fill pass needed.
            this.image = new NativeImage(NativeImage.Format.RGBA,
                    WorldMapSize.BLOCKS, WorldMapSize.BLOCKS, false);
        }

        public boolean isLoaded() { return loaded; }
        public boolean isDirty() { return dirty; }

        /** Texture identifier — only valid after {@link #ensureGpu()}. */
        public Identifier textureId() {
            return texId;
        }

        /**
         * Creates and registers the GPU texture on first use. Client thread
         * only. Safe to call repeatedly.
         */
        public void ensureGpu() {
            if (tex != null) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getTextureManager() == null) return;
            String hex = Long.toHexString(key);
            texId = Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "worldmap/" + hex);
            tex = new DynamicTexture(() -> "worldmap_" + hex, image);
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

        /**
         * Snapshot of the raw pixels for the IO thread. Must be called on
         * the client thread (NativeImage read).
         */
        public int[] snapshotPixels() {
            return image.getPixelsABGR();
        }

        /** Frees GPU + CPU memory. The eviction listener flushes first. */
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
    }
}

