package com.aurora.client.performance;

import java.util.Arrays;

/**
 * Remembers which 16³ terrain sections changed recently, so occlusion trusts
 * only terrain whose mesh has had time to be rebuilt and faded in.
 *
 * <p>The client's block state changes the moment a packet lands; the drawn
 * mesh follows a rebuild later and, for a new section, a fade-in. A section
 * (or a whole chunk column, for a chunk load) marked at time {@code t} is
 * unsettled until {@code t + window}. When more sections change within a
 * window than the table holds, every section is unsettled for a window
 * instead: the table never forgets a change it could not store.
 *
 * <p>Open addressing over {@code long} keys with linear probing; expired
 * entries are dropped by an in-place rebuild when the table fills. Nothing
 * allocates after construction.
 */
public final class SectionStability {
    private static final long EMPTY = Long.MIN_VALUE;
    /** Column keys use this section y, outside any world height. */
    private static final int COLUMN_Y = -0x80000;

    private final long[] keys;
    private final long[] times;
    private final long[] spareKeys;
    private final long[] spareTimes;
    private final int mask;
    private final int limit;
    private int size;
    private long unsettledUntil = Long.MIN_VALUE;

    /** @param capacity table slots, a power of two; at most three quarters are used */
    public SectionStability(int capacity) {
        if (capacity < 16 || Integer.bitCount(capacity) != 1) throw new IllegalArgumentException("capacity " + capacity);
        keys = new long[capacity];
        times = new long[capacity];
        spareKeys = new long[capacity];
        spareTimes = new long[capacity];
        Arrays.fill(keys, EMPTY);
        mask = capacity - 1;
        limit = capacity / 4 * 3;
    }

    public static long key(int sectionX, int sectionY, int sectionZ) {
        return ((long) (sectionX & 0x3FFFFF) << 42) | ((long) (sectionZ & 0x3FFFFF) << 20) | (sectionY & 0xFFFFF);
    }

    /** Everything is unsettled until {@code now + window} (level change, gate switched on). */
    public void reset(long now, long window) {
        Arrays.fill(keys, EMPTY);
        size = 0;
        unsettledUntil = Math.max(unsettledUntil, now + window);
    }

    public void markSection(int sectionX, int sectionY, int sectionZ, long now, long window) {
        put(key(sectionX, sectionY, sectionZ), now, window);
    }

    public void markColumn(int chunkX, int chunkZ, long now, long window) {
        put(key(chunkX, COLUMN_Y, chunkZ), now, window);
    }

    /** True when neither the section nor its chunk column changed within the last {@code window}. */
    public boolean settled(int sectionX, int sectionY, int sectionZ, long now, long window) {
        if (now < unsettledUntil) return false;
        if (size == 0) return true;
        return fresh(key(sectionX, sectionY, sectionZ), now, window) && fresh(key(sectionX, COLUMN_Y, sectionZ), now, window);
    }

    private boolean fresh(long key, long now, long window) {
        int slot = slot(key);
        while (keys[slot] != EMPTY) {
            if (keys[slot] == key) return now - times[slot] >= window;
            slot = (slot + 1) & mask;
        }
        return true;
    }

    private void put(long key, long now, long window) {
        if (now < unsettledUntil && now + window <= unsettledUntil) return;
        int slot = slot(key);
        while (keys[slot] != EMPTY) {
            if (keys[slot] == key) {
                times[slot] = now;
                return;
            }
            slot = (slot + 1) & mask;
        }
        if (size >= limit) {
            compact(now, window);
            if (size >= limit) {
                // Full of live changes: unsettle everything rather than forget one.
                reset(now, window);
                return;
            }
            put(key, now, window);
            return;
        }
        keys[slot] = key;
        times[slot] = now;
        size++;
    }

    /** Rebuild the table without the entries that have expired. */
    private void compact(long now, long window) {
        System.arraycopy(keys, 0, spareKeys, 0, keys.length);
        System.arraycopy(times, 0, spareTimes, 0, times.length);
        Arrays.fill(keys, EMPTY);
        size = 0;
        for (int i = 0; i < spareKeys.length; i++) {
            long key = spareKeys[i];
            if (key == EMPTY || now - spareTimes[i] >= window) continue;
            int slot = slot(key);
            while (keys[slot] != EMPTY) slot = (slot + 1) & mask;
            keys[slot] = key;
            times[slot] = spareTimes[i];
            size++;
        }
    }

    int size() {
        return size;
    }

    private int slot(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32)) & mask;
    }
}
