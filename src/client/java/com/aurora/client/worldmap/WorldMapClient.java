package com.aurora.client.worldmap;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.WorldScope;
import com.aurora.client.worldmap.cache.OverviewCache;
import com.aurora.client.worldmap.cache.OverviewCache.OverviewTile;
import com.aurora.client.worldmap.cache.RegionCache;
import com.aurora.client.worldmap.cache.RegionCache.RegionTile;
import com.aurora.client.worldmap.cache.WorldMapSize;
import com.aurora.client.worldmap.capture.CaptureQueue;
import com.aurora.client.worldmap.capture.ChunkCapturer;
import com.aurora.client.worldmap.storage.WorldMapStorage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Client-side engine for the fullscreen world map: owns the capture queue,
 * the region LRU cache, and the per-scope/per-dimension storage context.
 *
 * <h2>Data flow</h2>
 * <pre>
 *   ClientChunkEvents.CHUNK_LOAD ─┐
 *   setBlocksDirty mixin ─────────┤→ CaptureQueue ─→ ChunkCapturer (client thread,
 *                                 │   budgeted ≤N chunks/tick)   writes NativeImage)
 *                                 │        ↓
 *                                 │   RegionCache (LRU, GPU upload)
 *                                 │        ↓ dirty / evict / autosave-30s / disconnect
 *                                 └── WorldMapStorage (async GZIP .wmr on one daemon IO thread)
 * </pre>
 *
 * <h2>Scope &amp; dimension keying</h2>
 * Tiles are stored under {@code aurora-worldmap/<sanitized WorldScope>/<sanitized dim>}.
 * The raw WorldScope string (e.g. {@code "sp:new world"}) is re-sanitized by
 * the storage layer — that missing step is exactly what crashed the previous
 * world-map with {@code InvalidPathException} on Windows.
 *
 * <h2>Threading</h2>
 * Everything except disk IO runs on the client thread (NativeImage writes
 * and GPU uploads are client-thread-only). Disk IO is funneled through
 * {@link WorldMapStorage}'s single daemon worker; completed loads come back
 * through a concurrent queue and are applied on the next client tick.
 */
public final class WorldMapClient {

    private static final long AUTOSAVE_INTERVAL_MS = 30_000;
    /** Join rescan radius (chunks) around the player. */
    private static final int RESCAN_RADIUS = 48;

    /** Cheap static gate checked by the hot block-change mixin path. */
    static volatile boolean captureEnabled;

    private static volatile WorldMapClient instance;

    private final CaptureQueue pending = new CaptureQueue();
    private final ConcurrentLinkedQueue<WorldMapStorage.LoadedRegion> loadedResults =
            new ConcurrentLinkedQueue<>();
    /** Disk-load results for the far-zoom overview layer. */
    private final ConcurrentLinkedQueue<WorldMapStorage.LoadedRegion> overviewResults =
            new ConcurrentLinkedQueue<>();
    /** Resident→overview downsamples allowed per tick (paint runs on the render thread). */
    private static final int OVERVIEW_FAST_PAINTS_PER_TICK = 4;
    /** Remaining fast-path downsample budget this tick; reset in {@link #tick}. */
    private int overviewFastPaintBudget = OVERVIEW_FAST_PAINTS_PER_TICK;

    private RegionCache regions;
    /** Far-zoom overview layer (per dimension context; derived data). */
    private OverviewCache overviews;
    private WorldMapStorage storage;
    private ClientLevel activeLevel;
    private String scopeRaw = "";
    private String dimId = "";
    /** True when {@link #dimId} is the live dimension of {@link #activeLevel}. */
    private boolean liveCapture;
    private boolean inWorld;
    private boolean wasEnabled;
    private long lastAutosaveMs;

    private WorldMapClient() {}

    public static WorldMapClient get() { return instance; }

    /**
     * Client tick pump. Called from {@code WorldMapFeature.onTick()} — that
     * caller already swallows exceptions per feature.
     */
    public static void tick(Minecraft mc) {
        WorldMapClient wm = instance;
        if (wm == null || mc == null) return;

        AuroraConfig cfg = AuroraConfig.get();
        boolean enabled = cfg.worldMapEnabled;
        if (enabled != wm.wasEnabled) {
            wm.wasEnabled = enabled;
            captureEnabled = enabled;
            if (!enabled) wm.onLeaveWorld();
        }
        if (!enabled) return;

        if (mc.level == null || mc.player == null) {
            if (wm.inWorld) wm.onLeaveWorld();
            return;
        }
        if (!wm.inWorld) {
            wm.inWorld = true;
            wm.lastAutosaveMs = System.currentTimeMillis();
        }

        // Scope / dimension drift: server switch, portal, LAN hop. Comparing
        // the ClientLevel reference catches dimension swaps even when two
        // dimensions share an identical scope string.
        String scope = WorldScope.current();
        if (mc.level != wm.activeLevel || !scope.equals(wm.scopeRaw)) {
            wm.switchContext(scope, null);
        }

        wm.drainLoads();
        wm.overviewFastPaintBudget = OVERVIEW_FAST_PAINTS_PER_TICK;
        wm.drainOverviewLoads();
        if (wm.liveCapture) {
            wm.pumpCaptures(mc.level, cfg.worldMapCaptureBudget);
        }

        long now = System.currentTimeMillis();
        if (now - wm.lastAutosaveMs >= AUTOSAVE_INTERVAL_MS) {
            wm.lastAutosaveMs = now;
            wm.flushDirty();
        }
    }

    /**
     * Block-change funnel from {@code ClientLevelWorldMapMixin}. Fires very
     * often, so the fast path is three volatile/pointer checks; the queue
     * dedups the rest. Safe from any thread.
     */
    public static void onBlockChanged(ClientLevel level, BlockPos pos) {
        if (!captureEnabled) return;
        WorldMapClient wm = instance;
        if (wm == null || !wm.liveCapture || level != wm.activeLevel) return;
        wm.pending.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
    }
    /** Called once from {@code WorldMapFeature.onRegister()} (client init). */
    public static void init() {
        if (instance != null) return;
        instance = new WorldMapClient();

        // New chunks streaming in → capture (deduped by the queue).
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (!captureEnabled) return;
            WorldMapClient wm = instance;
            if (wm == null || !wm.liveCapture || level != wm.activeLevel) return;
            wm.pending.add(ChunkPos.asLong(chunk.getPos().x, chunk.getPos().z));
        });

        // Leaving the server/world → flush everything dirty, drop all tiles.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            WorldMapClient wm = instance;
            if (wm != null) wm.onLeaveWorld();
        });
    }

    // ================================================================
    //  Context switching (scope / dimension)
    // ================================================================

    /**
     * Tears down the current region cache (flushing dirty tiles) and builds
     * a fresh one for the given scope and — when {@code forcedDim} is null —
     * the live level's dimension. A non-null {@code forcedDim} puts the
     * client into "archive view" mode for the world-map screen's dimension
     * switcher: tiles load from disk but nothing is captured into them.
     */
    private void switchContext(String scope, String forcedDim) {
        Minecraft mc = Minecraft.getInstance();
        flushAndCloseRegions();
        pending.clear();
        loadedResults.clear();
        scopeRaw = scope;

        ClientLevel level = mc != null ? mc.level : null;
        activeLevel = level;
        String liveDim = level != null ? level.dimension().identifier().toString() : "";
        dimId = forcedDim != null ? forcedDim : liveDim;
        liveCapture = !dimId.isEmpty() && dimId.equals(liveDim);

        if (dimId.isEmpty() || "none".equals(scopeRaw)) {
            storage = null;
            regions = null;
            overviews = null;
            return;
        }
        storage = WorldMapStorage.forScopeDimension(scopeRaw, dimId);
        regions = new RegionCache(AuroraConfig.get().worldMapCacheRegions, this::onEvict);
        overviews = new OverviewCache();
        if (liveCapture && mc != null && mc.player != null) {
            scanLoadedChunks(level, mc.player);
        }
        AuroraClient.LOGGER.debug("[WorldMap] Context → scope='{}' dim='{}' live={}",
                scopeRaw, dimId, liveCapture);
    }

    /** Enqueues every already-loaded client chunk near the player (join/switch). */
    private void scanLoadedChunks(ClientLevel level, LocalPlayer player) {
        int pcx = player.blockPosition().getX() >> 4;
        int pcz = player.blockPosition().getZ() >> 4;
        for (int dz = -RESCAN_RADIUS; dz <= RESCAN_RADIUS; dz++) {
            for (int dx = -RESCAN_RADIUS; dx <= RESCAN_RADIUS; dx++) {
                int cx = pcx + dx, cz = pcz + dz;
                if (level.getChunkSource().hasChunk(cx, cz)) {
                    pending.add(ChunkPos.asLong(cx, cz));
                }
            }
        }
    }
    // ================================================================
    //  Capture pump & load drain (client thread)
    // ================================================================

    private void pumpCaptures(ClientLevel level, int budgetCfg) {
        if (regions == null || storage == null) return;
        int budget = Math.max(1, budgetCfg);
        while (budget > 0) {
            Long k = pending.poll();
            if (k == null) return;
            int cx = ChunkPos.getX(k), cz = ChunkPos.getZ(k);
            if (!level.getChunkSource().hasChunk(cx, cz)) continue; // unloaded meanwhile
            RegionTile tile = acquireTile(RegionCache.key(cx >> 5, cz >> 5));
            if (!tile.isLoaded()) {
                // Region still loading from disk — requeue and wait so the
                // load result can never overwrite this capture.
                pending.requeue(k);
                return;
            }
            ChunkCapturer.capture(level, cx, cz, tile);
            tile.flushUpload();
            if (overviews != null) {
                overviews.paintChunk(RegionCache.key(cx >> 5, cz >> 5),
                        cx & (WorldMapSize.CHUNKS - 1), cz & (WorldMapSize.CHUNKS - 1),
                        tile.image);
            }
            budget--;
        }
    }

    /** Acquires a tile (LRU-bumped) and submits its disk load exactly once. */
    private RegionTile acquireTile(long regionKey) {
        RegionTile tile = regions.acquire(regionKey);
        if (!tile.isLoaded() && !tile.loadRequested) {
            tile.loadRequested = true;
            storage.submitLoad(regionKey,
                    RegionCache.regionX(regionKey), RegionCache.regionZ(regionKey),
                    loadedResults::add);
        }
        return tile;
    }

    /** Applies IO-thread load results into their tiles (if still resident). */
    private void drainLoads() {
        if (regions == null) {
            loadedResults.clear();
            return;
        }
        WorldMapStorage.LoadedRegion lr;
        while ((lr = loadedResults.poll()) != null) {
            RegionTile tile = regions.peek(lr.key());
            if (tile == null || tile.isLoaded()) continue; // evicted meanwhile
            int[] px = lr.pixels();
            for (int i = 0; i < px.length; i++) {
                // 512-wide rows: x = i & 511, y = i >> 9 (BLOCKS == 512).
                tile.image.setPixelABGR(i & (WorldMapSize.BLOCKS - 1),
                        i >> 9, px[i]);
            }
            System.arraycopy(lr.stamps(), 0, tile.stamps, 0, tile.stamps.length);
            tile.loaded = true;
            tile.uploadPending = true;
            tile.flushUpload();
            if (overviews != null) overviews.repaint(lr.key(), tile.image);
        }
    }

    /**
     * Overview-layer counterpart of {@link #acquireForRender(long)} for far
     * zoom: returns the 64×64 downsampled tile. A missing tile is resolved
     * without IO when a resident full-res tile is loaded (downsampled from
     * it directly — at least as fresh as disk); otherwise a one-shot disk
     * load is queued and applied on arrival. Render/client thread only.
     */
    public OverviewTile acquireOverviewForRender(long regionKey) {
        if (overviews == null || storage == null) return null;
        OverviewTile tile = overviews.acquire(regionKey);
        if (!tile.isLoaded() && !tile.loadRequested) {
            // Zero-IO fast path: a resident, loaded full-res tile is at least
            // as fresh as anything on disk — downsample it instead of queueing
            // GZIP IO for data already in memory. Budgeted: the downsample runs
            // on this thread, so a large zoom-out spreads over a few ticks.
            RegionTile resident = regions == null ? null : regions.peek(regionKey);
            if (resident != null && resident.isLoaded()) {
                if (overviewFastPaintBudget <= 0) return null;
                overviewFastPaintBudget--;
                tile.loadRequested = true;
                tile.paintAbgr(resident.snapshotPixels(), "resident-snapshot");
            } else {
                tile.loadRequested = true;
                storage.submitLoad(regionKey,
                        RegionCache.regionX(regionKey), RegionCache.regionZ(regionKey),
                        overviewResults::add);
            }
        }
        if (!tile.isLoaded()) return null;
        tile.ensureGpu();
        tile.flushUpload();
        return tile;
    }

    /** Applies overview disk-load results into their tiles (if still resident). */
    private void drainOverviewLoads() {
        if (overviews == null) {
            overviewResults.clear();
            return;
        }
        WorldMapStorage.LoadedRegion lr;
        while ((lr = overviewResults.poll()) != null) {
            OverviewTile tile = overviews.peek(lr.key());
            if (tile == null) continue;
            // The disk payload can be older than chunks already captured into
            // a resident full-res tile (or a fast-path downsample): bulk-apply
            // first, then replay every chunk whose resident stamp is newer.
            tile.paintAbgr(lr.pixels(), "disk");
            RegionTile resident = regions == null ? null : regions.peek(lr.key());
            if (resident == null || !resident.isLoaded()) continue;
            long[] diskStamps = lr.stamps();
            for (int i = 0; i < diskStamps.length; i++) {
                if (resident.stamps[i] > diskStamps[i]) {
                    overviews.paintChunk(lr.key(),
                            i & (WorldMapSize.CHUNKS - 1),
                            i >>> WorldMapSize.CHUNK_SHIFT,
                            resident.image);
                }
            }
        }
    }

    // ================================================================
    //  Persistence
    // ================================================================

    /** Snapshots + submits every dirty tile (autosave, scope switch, evict). */
    public void flushDirty() {
        if (regions == null || storage == null) return;
        for (RegionTile tile : regions.all()) {
            if (tile.isDirty()) saveTile(tile);
        }
    }

    private void saveTile(RegionTile tile) {
        storage.submitSave(tile.key,
                RegionCache.regionX(tile.key), RegionCache.regionZ(tile.key),
                tile.stamps.clone(), tile.snapshotPixels());
        tile.dirty = false;
    }

    /** RegionCache eviction hook — flush then free. */
    private void onEvict(RegionTile tile) {
        if (tile.isDirty()) saveTile(tile);
        tile.release();
    }

    private void flushAndCloseRegions() {
        if (regions != null) {
            flushDirty();
            regions.closeAll();
            regions = null;
        }
        if (overviews != null) {
            overviews.closeAll();
            overviews = null;
        }
    }

    /** Full reset on disconnect / feature disable. */
    private void onLeaveWorld() {
        inWorld = false;
        liveCapture = false;
        flushAndCloseRegions();
        pending.clear();
        loadedResults.clear();
        overviewResults.clear();
        storage = null;
        activeLevel = null;
        dimId = "";
        scopeRaw = "";
    }
    // ================================================================
    //  WorldMapScreen support
    // ================================================================

    /**
     * Returns the tile for rendering if it is loaded (creating + submitting
     * its disk load when absent), with its GPU texture ensured and pending
     * pixels uploaded. Render/client thread only.
     */
    public RegionTile acquireForRender(long regionKey) {
        if (regions == null || storage == null) return null;
        RegionTile tile = acquireTile(regionKey);
        if (!tile.isLoaded()) return null;
        tile.ensureGpu();
        tile.flushUpload();
        return tile;
    }

    /**
     * Minimap terrain source: reads one captured surface pixel for the block
     * column {@code (worldX, worldZ)} from the resident region tile, purely
     * via {@code peek} — this never creates a tile or submits a disk load.
     * Returns the pixel in ABGR packing (ready for
     * {@code NativeImage.setPixelABGR}), or {@code 0} when there is no
     * captured pixel for that column (never explored, tile not resident,
     * archive-dimension view, or feature disabled) — callers fall back to
     * live chunk sampling. Render/client thread only.
     */
    public int sampleSurfaceAbgr(int worldX, int worldZ) {
        if (!captureEnabled || !liveCapture || regions == null) return 0;
        try {
            int rx = worldX >> 9, rz = worldZ >> 9; // 512-block regions
            RegionTile tile = regions.peek(RegionCache.key(rx, rz));
            if (tile == null || !tile.isLoaded()) return 0;
            int px = worldX - (rx << 9), pz = worldZ - (rz << 9);
            int argb = tile.image.getPixel(px, pz); // getPixel returns ARGB
            if ((argb >>> 24) == 0) return 0;       // transparent = never captured
            // ARGB → ABGR: swap the red and blue bytes.
            return (argb & 0xFF00FF00)
                    | ((argb & 0x00FF0000) >>> 16)
                    | ((argb & 0x000000FF) << 16);
        } catch (Throwable t) {
            return 0;
        }
    }

    public String dimensionId() { return dimId; }

    public boolean isViewingLiveDimension() { return liveCapture; }

    /** Vanilla dims first, then the live dimension (covers modded dims). */
    public List<String> dimensionOptions() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add("minecraft:overworld");
        set.add("minecraft:the_nether");
        set.add("minecraft:the_end");
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            set.add(mc.level.dimension().identifier().toString());
        }
        if (!dimId.isEmpty()) set.add(dimId);
        return new ArrayList<>(set);
    }

    /** Screen dimension switcher: archive-view another dimension's tiles. */
    public String cycleDimension() {
        List<String> options = dimensionOptions();
        if (options.isEmpty()) return dimId;
        int idx = options.indexOf(dimId);
        String next = options.get((idx + 1) % options.size());
        switchContext(scopeRaw, next);
        return dimId;
    }

    /** Back to the live dimension when the screen closes an archive view. */
    public void restoreLiveDimension() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;
        String live = mc.level.dimension().identifier().toString();
        if (!live.equals(dimId)) switchContext(scopeRaw, null);
    }
}



