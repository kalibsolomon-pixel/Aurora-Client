package com.aurora.client.worldmap.capture;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;

/**
 * Bounded, deduplicating FIFO of chunk positions awaiting capture.
 *
 * <p>Chunk keys are packed longs held in one primitive linked set, so the
 * queue order and the dedup check share a structure and nothing is boxed.
 * A chunk is enqueued only once (a chunk can be dirtied by dozens of block
 * updates per second — the mixin funnel would otherwise flood the queue).
 * When the capacity bound is hit, new offers are
 * dropped on the floor: a dropped update is always re-dirtied later by the
 * next block change or chunk reload, so staleness self-heals.
 *
 * <p>Thread-safe: producers are the client tick loop and the
 * {@code ClientLevel.setBlocksDirty} mixin hook (nominally main-thread, but
 * synchronized anyway as cheap insurance against off-thread mod callers).
 */
public final class CaptureQueue {

    private static final int CAPACITY = 8192;

    private final LongLinkedOpenHashSet queued = new LongLinkedOpenHashSet(256);

    /** Adds a chunk key unless it is already pending. Silently drops when full. */
    public synchronized void add(long chunkKey) {
        if (queued.size() < CAPACITY) queued.add(chunkKey);
    }

    /** Polls the oldest pending chunk, or {@code null} when empty. */
    public synchronized Long poll() {
        return queued.isEmpty() ? null : queued.removeFirstLong();
    }

    /**
     * Re-queues a chunk that could not be captured yet (its region tile is
     * still waiting on a disk load). Bypasses the capacity check so a chunk
     * is never lost once it has been dequeued.
     */
    public synchronized void requeue(long chunkKey) {
        queued.add(chunkKey);
    }

    public synchronized void clear() {
        queued.clear();
    }

    public synchronized int size() {
        return queued.size();
    }
}
