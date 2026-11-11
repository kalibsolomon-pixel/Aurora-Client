package com.aurora.client.worldmap.capture;

import net.minecraft.world.level.ChunkPos;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Bounded, deduplicating FIFO of chunk positions awaiting capture.
 *
 * <p>Chunk keys are {@link ChunkPos#asLong}-style packed longs, deduped
 * with a companion set so the same chunk is never queued twice (a chunk can
 * be dirtied by dozens of block updates per second — the mixin funnel would
 * otherwise flood the queue). When the capacity bound is hit, new offers are
 * dropped on the floor: a dropped update is always re-dirtied later by the
 * next block change or chunk reload, so staleness self-heals.
 *
 * <p>Thread-safe: producers are the client tick loop and the
 * {@code ClientLevel.setBlocksDirty} mixin hook (nominally main-thread, but
 * synchronized anyway as cheap insurance against off-thread mod callers).
 */
public final class CaptureQueue {

    private static final int CAPACITY = 8192;

    private final ArrayDeque<Long> queue = new ArrayDeque<>(256);
    private final Set<Long> queued = new HashSet<>(256);


    /** Adds a chunk key unless it is already pending. Silently drops when full. */
    public synchronized void add(long chunkKey) {
        if (queued.contains(chunkKey)) return;
        if (queue.size() >= CAPACITY) return;
        queue.addLast(chunkKey);
        queued.add(chunkKey);
    }

    /** Polls the oldest pending chunk, or {@code null} when empty. */
    public synchronized Long poll() {
        Long k = queue.pollFirst();
        if (k != null) queued.remove(k);
        return k;
    }

    /**
     * Re-queues a chunk that could not be captured yet (its region tile is
     * still waiting on a disk load). Bypasses the capacity check so a chunk
     * is never lost once it has been dequeued.
     */
    public synchronized void requeue(long chunkKey) {
        if (queued.contains(chunkKey)) return;
        queue.addLast(chunkKey);
        queued.add(chunkKey);
    }

    public synchronized void clear() {
        queue.clear();
        queued.clear();
    }

    public synchronized int size() {
        return queue.size();
    }
}
