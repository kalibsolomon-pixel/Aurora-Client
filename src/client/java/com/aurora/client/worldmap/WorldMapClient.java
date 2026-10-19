package com.aurora.client.worldmap;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.worldmap.cache.OverviewCache;
import com.aurora.client.worldmap.cache.RegionCache;
import com.aurora.client.worldmap.cache.RegionCache.RegionTile;
import com.aurora.client.worldmap.capture.CaptureQueue;
import com.aurora.client.worldmap.capture.ChunkCapturer;
import com.aurora.client.worldmap.storage.WorldMapStorage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Shared observed-terrain engine. World access and GPU resources belong to the client
 * thread. Disk work is bounded and serial. Browsing owns a separate read-only context;
 * it never suspends live capture or changes the minimap's terrain source. */
public final class WorldMapClient {
    private static volatile WorldMapClient instance;
    private static volatile boolean captureEnabled;
    private final CaptureQueue pending = new CaptureQueue();
    private final List<Context> retiring = new ArrayList<>();
    private final ConcurrentLinkedQueue<Runnable> globalResults = new ConcurrentLinkedQueue<>();
    private final LinkedHashSet<String> knownDimensions = new LinkedHashSet<>();
    private String knownScope = "";
    private Context live, archive;
    private ClientLevel level;
    private int requestsRemaining, uploadsRemaining;
    private long revision, lastSave;
    private String importStatus = "";
    private boolean importing;
    private boolean importQueued;
    private Context importContext;

    private WorldMapClient() {}
    public static WorldMapClient get() { return instance; }
    public long terrainRevision() { return revision; }
    public MapIdentity identity() { return live == null ? null : live.identity; }

    public static void init() {
        if (instance != null) return;
        instance = new WorldMapClient();
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            WorldMapClient wm = instance;
            if (captureEnabled && wm.level == level)
                wm.pending.add(chunk.getPos().toLong());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> { instance.leave(); MapIdentity.clear(); });
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> instance.shutdown());
    }

    public static void onBlockChanged(ClientLevel level, BlockPos pos) {
        WorldMapClient wm = instance;
        if (captureEnabled && wm != null && wm.level == level)
            wm.pending.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
    }

    public static void tick(Minecraft mc) {
        WorldMapClient wm = instance;
        if (wm == null) return;
        wm.requestsRemaining = 4;
        wm.uploadsRemaining = 2;
        Runnable global;
        while ((global = wm.globalResults.poll()) != null) global.run();
        wm.drainRetiring();
        AuroraConfig cfg = AuroraConfig.get();
        boolean needed = cfg.worldMapEnabled || (cfg.minimapEnabled && cfg.minimapShowTerrain);
        if (!needed || mc.level == null || mc.player == null) {
            wm.leave();
            return;
        }
        MapIdentity identity = MapIdentity.current();
        if (identity == null) { wm.leave(); return; }
        String dimension = mc.level.dimension().identifier().toString();
        if (wm.level != mc.level || wm.live == null || !wm.live.identity.key().equals(identity.key())) {
            wm.leave();
            // Persistent IO failures retain a bounded number of contexts and stop new capture.
            if (wm.retiring.size() >= 4) return;
            wm.level = mc.level;
            wm.live = wm.context(identity, dimension);
            if (!wm.knownScope.equals(identity.key())) { wm.knownDimensions.clear(); wm.knownScope = identity.key(); }
            wm.knownDimensions.add(dimension);
            WorldMapStorage.rememberDimension(identity, dimension, dimensions -> wm.globalResults.add(() -> {
                if (wm.knownScope.equals(identity.key())) wm.knownDimensions.addAll(dimensions);
            }));
            wm.lastSave = System.currentTimeMillis();
            wm.revision++;
            int cx = mc.player.blockPosition().getX() >> 4;
            int cz = mc.player.blockPosition().getZ() >> 4;
            int radius = Math.min(48, mc.options.renderDistance().get() + 1);
            // Near-first scan allows the minimap to populate before distant chunks.
            for (int ring = 0; ring <= radius; ring++) {
                for (int z = -ring; z <= ring; z++) for (int x = -ring; x <= ring; x++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != ring) continue;
                    if (mc.level.getChunkSource().hasChunk(cx + x, cz + z))
                        wm.pending.add(ChunkPos.asLong(cx + x, cz + z));
                }
            }
        }
        captureEnabled = true;
        wm.live.drain();
        if (wm.archive != null) wm.archive.drain();
        wm.pumpImport();
        int budget = MapTransform.bounded(cfg.worldMapCaptureBudget, 1, 8);
        int attempts = Math.min(32, wm.pending.size());
        boolean importPaused = wm.importing && wm.importContext != null
                && wm.importContext.identity.key().equals(wm.live.identity.key())
                && wm.importContext.dimension.equals(wm.live.dimension);
        while (!importPaused && budget > 0 && attempts-- > 0) {
            Long key = wm.pending.poll();
            if (key == null) break;
            int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
            if (!mc.level.getChunkSource().hasChunk(cx, cz)) continue;
            RegionTile tile = wm.live.acquire(RegionCache.key(cx >> 5, cz >> 5));
            if (tile == null || !tile.isLoaded()) { wm.pending.requeue(key); continue; }
            if (!tile.writable) continue; // malformed files are preserved for recovery
            ChunkCapturer.capture(mc.level, cx, cz, tile);
            wm.live.overviews.paintChunk(tile.key, cx & 31, cz & 31, tile.image);
            wm.revision++;
            budget--;
        }
        long now = System.currentTimeMillis();
        if (now - wm.lastSave >= 30_000) {
            wm.live.saveDirty(4);
            // Continue incremental sweeps until all dirty regions have been submitted.
            if (!wm.live.hasUnsubmittedDirty()) wm.lastSave = now;
        }
        // When the LRU is full of dirty tiles, start durable saves before accepting more.
        if (wm.live.regions.size() >= wm.live.capacity) wm.live.saveDirty(2);
    }

    private void retire(Context context) {
        if (context == null) return;
        context.retired = true;
        context.drain();
        context.saveDirty(4);
        retiring.add(context);
    }

    /** Reuse a pending-save context on rapid reconnect/portal return; never reload
     * an older disk copy over still-dirty explored pixels. */
    private Context context(MapIdentity identity, String dimension) {
        for (var it = retiring.iterator(); it.hasNext();) {
            Context existing = it.next();
            if (!existing.identity.key().equals(identity.key()) || !existing.dimension.equals(dimension)) continue;
            it.remove(); existing.retired = false;
            return existing;
        }
        return new Context(identity, dimension);
    }

    private void leave() {
        captureEnabled = false;
        retire(archive); archive = null;
        retire(live); live = null;
        level = null;
        pending.clear();
    }

    private void drainRetiring() {
        for (var it = retiring.iterator(); it.hasNext();) {
            Context context = it.next();
            context.drain();
            context.saveDirty(4);
            if (!context.hasDirty()) {
                context.closed = true;
                context.regions.closeAll();
                context.overviews.closeAll();
                context.results.clear();
                it.remove();
            }
        }
    }

    private void shutdown() {
        leave();
        long deadline = System.nanoTime() + 10_000_000_000L;
        do {
            requestsRemaining = 16;
            drainRetiring();
            if (retiring.isEmpty()) return;
            WorldMapStorage.awaitIdle(250);
        } while (System.nanoTime() < deadline);
        AuroraClient.LOGGER.error("[WorldMap] Shutdown left unsaved regions after IO failure; see preceding errors.");
    }

    public void flushDirty() { if (live != null) live.saveDirty(4); }
    private Context viewed() { return archive != null ? archive : live; }

    public RegionTile acquireForRender(long key) {
        Context context = viewed();
        if (context == null) return null;
        RegionTile tile = context.acquire(key);
        if (tile == null || !tile.isLoaded()) return null;
        if (!upload(tile)) return null;
        return tile;
    }

    private boolean upload(RegionTile tile) {
        if (tile.textureId() == null || tile.uploadPending) {
            if (uploadsRemaining <= 0) return tile.textureId() != null;
            uploadsRemaining--;
            tile.ensureGpu();
            tile.flushUpload();
        }
        return tile.textureId() != null;
    }

    public OverviewCache.OverviewTile acquireOverviewForRender(long key) {
        Context context = viewed();
        if (context == null) return null;
        var tile = context.overviews.acquire(key);
        if (!tile.isLoaded() && !tile.loadRequested && requestsRemaining > 0 && context.loads < 8) {
            RegionTile resident = context.regions.peek(key);
            if (resident != null && resident.isLoaded()) {
                if (uploadsRemaining <= 0) return null;
                uploadsRemaining--;
                tile.paintAbgr(resident.snapshotPixels(), "resident");
            } else {
                requestsRemaining--;
                context.loads++;
                tile.loadRequested = true;
                boolean queued = context.storage.submitLoad(key, RegionCache.regionX(key), RegionCache.regionZ(key),
                        payload -> context.complete(() -> {
                            context.loads--;
                            if (context.overviews.peek(key) != tile) return;
                            RegionTile current = context.regions.peek(key);
                            if (current != null && current.isLoaded())
                                tile.paintAbgr(current.snapshotPixels(), "resident");
                            else tile.paintAbgr(payload.pixels(), "disk");
                        }));
                if (!queued) { context.loads--; tile.loadRequested = false; }
            }
        }
        if (!tile.isLoaded()) return null;
        if (tile.textureId() == null || tile.needsUpload()) {
            if (uploadsRemaining <= 0) return tile.textureId() == null ? null : tile;
            uploadsRemaining--;
            tile.ensureGpu();
            tile.flushUpload();
        }
        return tile;
    }

    /** Read-only terrain lookup: never visits Minecraft chunks on a render path. */
    public int sampleSurfaceAbgr(int x, int z) {
        if (live == null) return 0;
        RegionTile tile = live.regions.peek(RegionCache.key(x >> 9, z >> 9));
        if (tile == null || !tile.isLoaded()) return 0;
        int argb = tile.image.getPixel(x & 511, z & 511);
        return (argb & 0xFF00FF00) | ((argb >>> 16) & 255) | ((argb & 255) << 16);
    }

    /** Request the minimap footprint once per repaint, never once per texel. */
    public void requestMinimap(double x, double z, int diameter) {
        if (live == null) return;
        int half = diameter; // covers rotated square corners too
        for (int rz = ((int) Math.floor(z) - half) >> 9; rz <= ((int) Math.floor(z) + half) >> 9; rz++)
            for (int rx = ((int) Math.floor(x) - half) >> 9; rx <= ((int) Math.floor(x) + half) >> 9; rx++)
                live.acquire(RegionCache.key(rx, rz));
    }

    public String dimensionId() { return viewed() == null ? "" : viewed().dimension; }
    public boolean isViewingLiveDimension() { return archive == null; }
    public List<String> dimensionOptions() {
        LinkedHashSet<String> dimensions = new LinkedHashSet<>(List.of(
                "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"));
        if (live != null) dimensions.add(live.dimension);
        if (archive != null) dimensions.add(archive.dimension);
        dimensions.addAll(knownDimensions);
        // Waypoint dimensions also retain visited modded dimensions across sessions.
        var wf = com.aurora.client.feature.impl.WaypointFeature.get();
        if (wf != null) for (var w : wf.currentWorldWaypoints()) if (w != null) dimensions.add(w.dimension);
        return List.copyOf(dimensions);
    }

    public String cycleDimension() {
        if (live == null) return "";
        List<String> options = dimensionOptions();
        String next = options.get((options.indexOf(dimensionId()) + 1) % options.size());
        retire(archive); archive = null;
        if (!next.equals(live.dimension)) archive = context(live.identity, next);
        return dimensionId();
    }

    public void restoreLiveDimension() { retire(archive); archive = null; }

    public String importStatus() { return importStatus; }
    public void importLegacy() {
        Context context = viewed();
        if (context == null || importing) return;
        importing = true;
        importQueued = false;
        importContext = context;
        importStatus = "Saving current terrain before import…";
        pumpImport();
    }

    private void pumpImport() {
        if (!importing || importQueued || importContext == null) return;
        Context context = importContext;
        context.drain(); context.saveDirty(4);
        if (context.hasDirty()) return;
        importQueued = true;
        importStatus = "Copying legacy terrain…";
        boolean accepted = context.storage.importLegacy(context.identity, context.dimension,
                status -> globalResults.add(() -> {
                    importing = false;
                    importContext = null;
                    importStatus = status;
                    // Re-open only once durable writes and preceding saves have completed.
                    Context current = viewed();
                    if (current != null && current.identity.key().equals(context.identity.key())
                            && current.dimension.equals(context.dimension)) {
                        if (archive == current) { retire(archive); archive = new Context(context.identity, context.dimension); }
                        else { retire(live); live = new Context(context.identity, context.dimension); }
                        revision++;
                    }
                }));
        if (!accepted) { importQueued = false; importStatus = "Storage busy; import waiting…"; }
    }

    /** Per-context mailboxes prevent old-world results from reaching a new world.
     * Per-tile identity checks also reject results for evicted/recreated tiles. */
    private final class Context {
        final MapIdentity identity;
        final String dimension;
        final int capacity = MapTransform.bounded(AuroraConfig.get().worldMapCacheRegions, 8, 256);
        final RegionCache regions = new RegionCache(capacity, RegionTile::release,
                tile -> !tile.dirty && !tile.saving);
        final OverviewCache overviews = new OverviewCache();
        final WorldMapStorage storage;
        final ConcurrentLinkedQueue<Runnable> results = new ConcurrentLinkedQueue<>();
        volatile boolean closed;
        boolean retired;
        int loads;
        long nextSaveAttempt;

        Context(MapIdentity identity, String dimension) {
            this.identity = identity;
            this.dimension = dimension;
            storage = WorldMapStorage.forIdentity(identity, dimension);
        }
        void complete(Runnable completion) { if (!closed) results.add(completion); }
        void drain() {
            // <=8 loads plus bounded save completions; at most two large pixel copies/tick.
            for (int i = 0; i < 2; i++) {
                Runnable result = results.poll();
                if (result == null) break;
                result.run();
            }
        }
        RegionTile acquire(long key) {
            RegionTile tile = regions.peek(key);
            if (tile == null) {
                if (requestsRemaining <= 0 || loads >= 8) return null;
                tile = regions.acquire(key);
                if (tile == null) return null;
            }
            if (!tile.loaded && !tile.loadRequested && requestsRemaining > 0 && loads < 8) {
                RegionTile requested = tile;
                tile.loadRequested = true;
                requestsRemaining--;
                loads++;
                boolean queued = storage.submitLoad(key, RegionCache.regionX(key), RegionCache.regionZ(key), payload ->
                        complete(() -> {
                            loads--;
                            if (regions.peek(key) != requested) return;
                            for (int i = 0; i < payload.pixels().length; i++)
                                requested.image.setPixelABGR(i & 511, i >>> 9, payload.pixels()[i]);
                            System.arraycopy(payload.stamps(), 0, requested.stamps, 0, 1024);
                            requested.writable = payload.valid();
                            requested.loaded = true;
                            requested.uploadPending = true;
                            revision++;
                        }));
                if (!queued) { tile.loadRequested = false; loads--; }
            }
            return tile;
        }
        void saveDirty(int budget) {
            if (System.nanoTime() < nextSaveAttempt) return;
            for (RegionTile tile : regions.all()) {
                if (!tile.dirty || tile.saving || budget <= 0) continue;
                long savedRevision = tile.revision;
                tile.saving = true;
                boolean queued = storage.submitSave(tile.key, RegionCache.regionX(tile.key), RegionCache.regionZ(tile.key),
                        tile.stamps.clone(), tile.snapshotPixels(), success -> complete(() -> {
                            tile.saving = false;
                            if (success && tile.revision == savedRevision) tile.dirty = false;
                            if (!success) nextSaveAttempt = System.nanoTime() + 5_000_000_000L;
                        }));
                if (!queued) tile.saving = false;
                budget--;
            }
        }
        boolean hasUnsubmittedDirty() {
            for (RegionTile tile : regions.all()) if (tile.dirty && !tile.saving) return true;
            return false;
        }
        boolean hasDirty() {
            for (RegionTile tile : regions.all()) if (tile.dirty || tile.saving) return true;
            return false;
        }
    }
}
