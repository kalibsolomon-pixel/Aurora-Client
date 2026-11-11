package com.aurora.client.worldmap.storage;

import com.aurora.client.AuroraClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import com.aurora.client.worldmap.MapIdentity;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Disk persistence for world-map regions. One region covers 32×32 chunks
 * (512×512 blocks) and is stored as a single GZIP-compressed {@code .wmr}
 * file under {@code config/aurora-worldmap/<scope>/<dimension>/r.<rx>.<rz>.wmr}.
 *
 * <h2>Why a whole new storage layer</h2>
 * The previous world-map implementation crashed with
 * {@code InvalidPathException} because it used the raw {@link
 * com.aurora.client.util.WorldScope} key — e.g. {@code "sp:new world"},
 * which contains both a colon and a space — directly as a Windows folder
 * name. Every path component produced here is passed through
 * {@link #sanitize(String)}, which whitelists {@code [a-z0-9_.-]} only.
 *
 * <h2>Format (version 1)</h2>
 * <pre>
 *   int    MAGIC   = 0x41574D52 ("AWMR")
 *   byte   VERSION = 1
 *   long[] stamps  = 32*32 per-chunk capture timestamps (ms since epoch)
 *   int[]  pixels  = 512*512 ABGR pixels (matches NativeImage packing)
 * </pre>
 *
 * <p>The reader additionally tolerates legacy version-2 files (written by a
 * short-lived experimental build that carried an optional cave layer after
 * the surface payload): the cave section is skipped and only the surface
 * layer is used, so no explored data is lost.
 *
 * <h2>Threading</h2>
 * All disk IO runs on one shared daemon worker ({@link #IO}). Callers hand
 * over plain arrays snapshotted on the client thread; results are delivered
 * through a caller-supplied sink that is invoked on the IO thread (the
 * {@code WorldMapClient} pushes them into a concurrent queue drained on the
 * client tick, because NativeImage writes are only legal on the client
 * thread).
 *
 * <p>Saves are atomic: the region is written to a {@code .tmp} sibling and
 * then moved over the destination, so a crash mid-write can never destroy a
 * previously good file. Corrupt or wrong-version files are logged at debug
 * and treated as "no data" rather than crashing the client.
 */
public final class WorldMapStorage {

    /** "AWMR" — Aurora World Map Region. */
    public static final int MAGIC = 0x41574D52;
    public static final byte VERSION = 1;

    /** Region tile dimensions — must match {@code WorldMapSize}. */
    public static final int CHUNKS = 32;
    public static final int BLOCKS = CHUNKS * 16;              // 512
    public static final int PIXELS = BLOCKS * BLOCKS;          // 262144
    public static final int STAMP_COUNT = CHUNKS * CHUNKS;     // 1024
    // Immutable borrowed zero payload for missing files; avoids 1 MiB allocation per
    // unexplored region while panning. Consumers only read/copy these arrays.
    private static final long[] EMPTY_STAMPS = new long[STAMP_COUNT];
    private static final int[] EMPTY_PIXELS = new int[PIXELS];

    /** One shared daemon worker for all region IO — keeps seeks sequential. */
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 0,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(64), r -> {
        Thread t = new Thread(r, "Aurora-WorldMap-IO");
        t.setDaemon(true);
        return t;
    });

    private final Path dir;

    private WorldMapStorage(Path dir) {
        this.dir = dir;
    }

    /**
     * Builds a storage rooted at
     * {@code config/aurora-worldmap/<sanitized scope>/<sanitized dimension>}
     * for explicit legacy access. Directory creation is deferred to the IO worker.
     */
    public static WorldMapStorage forScopeDimension(String scope, String dimension) {
        Path root = FabricLoader.getInstance().getConfigDir()
                .resolve("aurora-worldmap")
                .resolve(sanitize(scope))
                .resolve(sanitize(dimension));
        return new WorldMapStorage(root);
    }

    public static WorldMapStorage forIdentity(MapIdentity identity, String dimension) {
        return new WorldMapStorage(FabricLoader.getInstance().getConfigDir()
                .resolve("aurora-worldmap").resolve(identity.key())
                .resolve(MapIdentity.dimensionFolder(dimension)));
    }

    /** Test seam; no global Minecraft/Fabric state needed for format checks. */
    public static WorldMapStorage at(Path directory) { return new WorldMapStorage(directory); }

    /** Small versioned index preserves discovered modded dimensions across restarts. */
    public static boolean rememberDimension(MapIdentity identity, String dimension, Consumer<java.util.List<String>> sink) {
        Path index = FabricLoader.getInstance().getConfigDir().resolve("aurora-worldmap")
                .resolve(identity.key()).resolve("dimensions.json");
        return execute(() -> {
            java.util.LinkedHashSet<String> dimensions = new java.util.LinkedHashSet<>();
            try {
                if (Files.exists(index)) {
                    if (Files.size(index) > 65536) throw new IOException("Oversized dimension index");
                    var root = com.google.gson.JsonParser.parseString(Files.readString(index)).getAsJsonObject();
                    if (root.get("version").getAsInt() != 1) throw new IOException("Unknown dimension index version");
                    for (var entry : root.getAsJsonArray("dimensions")) {
                        String value = entry.getAsString();
                        if (value.length() <= 256 && value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) dimensions.add(value);
                        if (dimensions.size() >= 256) break;
                    }
                }
                if (dimensions.size() < 256) dimensions.add(dimension);
                Files.createDirectories(index.getParent());
                var root = new com.google.gson.JsonObject(); root.addProperty("version", 1);
                root.add("dimensions", new com.google.gson.Gson().toJsonTree(dimensions));
                Path temporary = index.resolveSibling("dimensions.json.tmp");
                Files.writeString(temporary, root.toString());
                try { Files.move(temporary, index, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, index, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception malformed) {
                AuroraClient.LOGGER.warn("[WorldMap] Dimension index preserved after read/write failure", malformed);
            }
            sink.accept(java.util.List.copyOf(dimensions));
        });
    }

    public static boolean awaitIdle(long millis) {
        java.util.concurrent.CountDownLatch barrier = new java.util.concurrent.CountDownLatch(1);
        if (!execute(barrier::countDown)) return false;
        try { return barrier.await(millis, TimeUnit.MILLISECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
    }

    private static boolean execute(Runnable task) {
        try { IO.execute(task); return true; }
        catch (RejectedExecutionException full) { return false; }
    }

    /**
     * Whitelist sanitizer for path components: lowercase, everything outside
     * {@code [a-z0-9_.-]} becomes {@code '_'}, capped at 80 chars. This is
     * what makes {@code "sp:new world"} safe as a folder name
     * ({@code "sp_new_world"}).
     */
    public static String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) return "none";
        String s = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.\\-]", "_");
        if (s.isEmpty() || s.chars().allMatch(c -> c == '.' || c == '_')) return "none";
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    /** Region-file path for the given region coordinates. */
    public Path fileFor(int rx, int rz) {
        return dir.resolve("r." + rx + "." + rz + ".wmr");
    }

    /** True when at least one region file exists on disk for this scope+dim. */
    public boolean hasAnyData() {
        try (var stream = Files.list(dir)) {
            return stream.anyMatch(p -> p.getFileName().toString().endsWith(".wmr"));
        } catch (IOException e) {
            return false;
        }
    }
    // ================================================================
    //  Async operations
    // ================================================================

    /**
     * Asynchronously persists one region. The arrays are caller-owned
     * snapshots (already copied off the NativeImage on the client thread);
     * the IO thread only reads them.
     */
    public boolean submitSave(long key, int rx, int rz, long[] stamps, int[] pixels,
                              Consumer<Boolean> completion) {
        if (stamps == null || stamps.length != STAMP_COUNT || pixels == null || pixels.length != PIXELS)
            throw new IllegalArgumentException("Invalid region snapshot dimensions");
        Path target = fileFor(rx, rz);
        return execute(() -> {
            boolean saved = false;
            try {
                writeRegion(target, stamps, pixels);
                saved = true;
            } catch (Throwable t) {
                AuroraClient.LOGGER.warn("[WorldMap] Save failed; dirty region retained: {}", target.getFileName(), t);
            }
            completion.accept(saved);
        });
    }

    /**
     * Asynchronously loads one region and hands the decoded payload to
     * {@code sink} <em>on the IO thread</em>. A missing file yields a zeroed
     * payload (valid: "nothing explored here yet"), so the caller can mark
     * the tile as loaded instead of spinning on reloads. Corrupt files are
     * logged and also downgrade to a zeroed payload.
     */
    public boolean submitLoad(long key, int rx, int rz, Consumer<LoadedRegion> sink) {
        Path target = fileFor(rx, rz);
        return execute(() -> {
            LoadedRegion loaded = null;
            boolean valid = true;
            try {
                loaded = readRegion(key, target);
            } catch (Throwable t) {
                valid = false;
                AuroraClient.LOGGER.warn("[WorldMap] Unreadable region preserved without overwrite: {}", target.getFileName(), t);
            }
            if (loaded == null) {
                loaded = new LoadedRegion(key, EMPTY_STAMPS, EMPTY_PIXELS, valid);
            }
            sink.accept(loaded);
        });
    }

    // ================================================================
    //  Raw format
    // ================================================================

    private static void writeRegion(Path target, long[] stamps, int[] pixels) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(
                new BufferedOutputStream(Files.newOutputStream(tmp), 1 << 16)))) {
            out.writeInt(MAGIC);
            out.writeByte(VERSION);
            for (long stamp : stamps) out.writeLong(stamp);
            for (int pixel : pixels) out.writeInt(pixel);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException atomicUnsupported) {
            // FS without atomic move (some network drives) — plain replace is
            // still crash-consistent enough: worst case the .tmp lingers.
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static LoadedRegion readRegion(long key, Path target) throws IOException {
        if (!Files.exists(target)) return null;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(
                new BufferedInputStream(Files.newInputStream(target), 1 << 16)))) {
            if (in.readInt() != MAGIC) throw new IOException("Wrong region magic");
            byte version = in.readByte();
            if (version != 1 && version != 2) throw new IOException("Unsupported region version " + version);
            long[] stamps = new long[STAMP_COUNT];
            for (int i = 0; i < STAMP_COUNT; i++) stamps[i] = in.readLong();
            int[] pixels = new int[PIXELS];
            for (int i = 0; i < PIXELS; i++) pixels[i] = in.readInt();
            if (version == 2) {
                // Legacy experimental cave-layer build: a trailing optional
                // second layer follows. Skip it (nothing is read after this
                // point, so a short skip is harmless) and keep the surface.
                if (in.readByte() == 1) {
                    in.skipNBytes(STAMP_COUNT * 8L + PIXELS * 4L);
                }
            }
            if (in.read() != -1) throw new IOException("Unexpected trailing region payload");
            return new LoadedRegion(key, stamps, pixels, true);
        }
    }

    /** Immutable decoded region payload handed from the IO thread to the client thread. */
    public record LoadedRegion(long key, long[] stamps, int[] pixels, boolean valid) {
        public boolean empty() { return pixels == EMPTY_PIXELS; }
    }

    /** Explicit, copy-only import: legacy folders cannot prove their original identity.
     * Existing destination chunks and original files are never replaced. */
    public boolean importLegacy(MapIdentity identity, String dimension, Consumer<String> completion) {
        Path legacy = FabricLoader.getInstance().getConfigDir().resolve("aurora-worldmap")
                .resolve(sanitize(identity.legacyScope())).resolve(sanitize(dimension));
        return importLegacyFrom(legacy, completion);
    }

    /** Copy-only migration test seam; caller supplies the already-resolved legacy directory. */
    public boolean importLegacyFrom(Path legacy, Consumer<String> completion) {
        return execute(() -> {
            int copied = 0;
            try {
                Files.createDirectories(dir);
                if (Files.isDirectory(legacy)) {
                    try (var files = Files.newDirectoryStream(legacy, "r.*.*.wmr")) {
                        for (Path source : files) {
                            if (!source.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.wmr")) continue;
                            Path target = dir.resolve(source.getFileName());
                            LoadedRegion data = readRegion(0, source);
                            if (data == null) continue;
                            if (Files.exists(target)) {
                                LoadedRegion existing = readRegion(0, target);
                                // Merge only unexplored destination chunks. Existing captures win.
                                for (int chunk = 0; chunk < STAMP_COUNT; chunk++) {
                                    if (existing.stamps()[chunk] != 0 || data.stamps()[chunk] == 0) continue;
                                    int x = (chunk & 31) << 4, z = (chunk >>> 5) << 4;
                                    for (int row = z; row < z + 16; row++)
                                        System.arraycopy(data.pixels(), row * BLOCKS + x,
                                                existing.pixels(), row * BLOCKS + x, 16);
                                    existing.stamps()[chunk] = data.stamps()[chunk];
                                }
                                data = existing;
                            }
                            // Use the atomic writer, never copy a partial file into the destination.
                            writeRegion(target, data.stamps(), data.pixels());
                            copied++;
                        }
                    }
                }
                completion.accept("Imported " + copied + " legacy regions; originals preserved.");
            } catch (Exception failure) {
                AuroraClient.LOGGER.warn("[WorldMap] Legacy import stopped; originals preserved", failure);
                completion.accept("Import stopped; originals preserved. Check the log.");
            }
        });
    }
}

