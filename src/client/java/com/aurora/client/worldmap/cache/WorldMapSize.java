package com.aurora.client.worldmap.cache;

/**
 * Shared size constants for the region grid. Kept in their own type so the
 * storage layer can validate payloads against the same numbers without a
 * circular dependency on the cache internals.
 */
public final class WorldMapSize {
    /** Chunks per region side. */
    public static final int CHUNKS = 32;
    /** log2(CHUNKS) — for packing chunk-local coords into stamp indices. */
    public static final int CHUNK_SHIFT = 5;
    /** Blocks per region side = 32 chunks × 16 blocks. */
    public static final int BLOCKS = CHUNKS * 16;


    private WorldMapSize() {}
}
