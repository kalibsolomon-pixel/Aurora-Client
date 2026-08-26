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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    /** One shared daemon worker for all region IO — keeps seeks sequential. */
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
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
     * and creates the directory tree eagerly so per-region saves never race
     * {@code mkdirs}.
     */
    public static WorldMapStorage forScopeDimension(String scope, String dimension) {
        Path root = FabricLoader.getInstance().getConfigDir()
                .resolve("aurora-worldmap")
                .resolve(sanitize(scope))
                .resolve(sanitize(dimension));
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            AuroraClient.LOGGER.warn("[WorldMap] Could not create storage dir {}", root, e);
        }
        return new WorldMapStorage(root);
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
    public void submitSave(long key, int rx, int rz, long[] stamps, int[] pixels) {
        if (stamps == null || pixels == null) return;
        Path target = fileFor(rx, rz);
        IO.execute(() -> {
            try {
                writeRegion(target, stamps, pixels);
            } catch (Throwable t) {
                AuroraClient.LOGGER.debug("[WorldMap] Failed to save region {}", target.getFileName(), t);
            }
        });
    }

    /**
     * Asynchronously loads one region and hands the decoded payload to
     * {@code sink} <em>on the IO thread</em>. A missing file yields a zeroed
     * payload (valid: "nothing explored here yet"), so the caller can mark
     * the tile as loaded instead of spinning on reloads. Corrupt files are
     * logged and also downgrade to a zeroed payload.
     */
    public void submitLoad(long key, int rx, int rz, Consumer<LoadedRegion> sink) {
        Path target = fileFor(rx, rz);
        IO.execute(() -> {
            LoadedRegion loaded = null;
            try {
                loaded = readRegion(key, target);
            } catch (Throwable t) {
                AuroraClient.LOGGER.debug("[WorldMap] Failed to load region {}", target.getFileName(), t);
            }
            if (loaded == null) {
                loaded = new LoadedRegion(key, new long[STAMP_COUNT], new int[PIXELS]);
            }
            sink.accept(loaded);
        });
    }

    // ================================================================
    //  Raw format
    // ================================================================

    private static void writeRegion(Path target, long[] stamps, int[] pixels) throws IOException {
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
        } catch (IOException atomicUnsupported) {
            // FS without atomic move (some network drives) — plain replace is
            // still crash-consistent enough: worst case the .tmp lingers.
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static LoadedRegion readRegion(long key, Path target) throws IOException {
        if (!Files.exists(target)) return null;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(
                new BufferedInputStream(Files.newInputStream(target), 1 << 16)))) {
            if (in.readInt() != MAGIC) return null;
            byte version = in.readByte();
            if (version != 1 && version != 2) return null;
            long[] stamps = new long[STAMP_COUNT];
            for (int i = 0; i < STAMP_COUNT; i++) stamps[i] = in.readLong();
            int[] pixels = new int[PIXELS];
            for (int i = 0; i < PIXELS; i++) pixels[i] = in.readInt();
            if (version == 2) {
                // Legacy experimental cave-layer build: a trailing optional
                // second layer follows. Skip it (nothing is read after this
                // point, so a short skip is harmless) and keep the surface.
                if (in.readByte() == 1) {
                    in.skipBytes(STAMP_COUNT * 8);
                    in.skipBytes(PIXELS * 4);
                }
            }
            return new LoadedRegion(key, stamps, pixels);
        }
    }

    /** Immutable decoded region payload handed from the IO thread to the client thread. */
    public record LoadedRegion(long key, long[] stamps, int[] pixels) {}
}

