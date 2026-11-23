package com.aurora.client.resourcepack;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Pure, Minecraft-free generation-stamped async icon cache coordinator.
 *
 * <p>Every {@link #invalidateAll()} increments the cache generation so in-flight
 * workers started before a Refresh:
 * <ul>
 *   <li>never block new requests for the same pack ID in the new generation,</li>
 *   <li>never populate the new generation's cache with stale decoded images
 *       (rejected images are closed immediately), and</li>
 *   <li>never poison the new generation's missing set with an obsolete miss.</li>
 * </ul>
 *
 * @param <I> decoded off-thread image handle (must be closed if rejected)
 * @param <T> uploaded GPU texture handle (must be retired when evicted/invalidated)
 */
public final class PackIconCache<I, T> {
    private record InFlightKey(long generation, String packId) {}
    private record ReadyItem<I>(long generation, String packId, I image) {}

    private final int maxCached;
    private final Object stateLock = new Object();
    private final Map<String, T> cache = new LinkedHashMap<>(64, 0.75f, true);
    private final Set<InFlightKey> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<String> missing = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<ReadyItem<I>> ready = new ConcurrentLinkedQueue<>();
    private volatile long generation = 1L;

    public PackIconCache(int maxCached) {
        this.maxCached = Math.max(1, maxCached);
    }

    /** Current cache generation (monotonically increases on each {@link #invalidateAll}). */
    public long generation() {
        return generation;
    }

    /** Returns the cached texture for {@code packId}, or null if not yet uploaded. */
    public T getCached(String packId) {
        if (packId == null) return null;
        return cache.get(packId);
    }

    /**
     * Attempts to claim an async decode slot for {@code packId} in the current
     * generation. Returns the claimed generation ({@code > 0}) when the caller
     * should dispatch work, or {@code -1} when the pack is already cached,
     * known missing, or already decoding in this generation.
     */
    public long tryBeginLoad(String packId) {
        if (packId == null) return -1L;
        synchronized (stateLock) {
            if (cache.containsKey(packId) || missing.contains(packId)) return -1L;
            long gen = generation;
            return inFlight.add(new InFlightKey(gen, packId)) ? gen : -1L;
        }
    }

    /**
     * Publishes a decoded image from a worker thread. If {@code expectedGeneration}
     * is obsolete, {@code closeImage} is invoked immediately and the image is
     * discarded without touching the new generation's queue.
     */
    public boolean completeSuccess(long expectedGeneration, String packId, I image, Consumer<I> closeImage) {
        try {
            if (image == null) {
                completeMissing(expectedGeneration, packId);
                return false;
            }
            boolean accepted;
            synchronized (stateLock) {
                accepted = (expectedGeneration == generation);
                if (accepted) {
                    ready.add(new ReadyItem<>(expectedGeneration, packId, image));
                }
            }
            if (!accepted && closeImage != null) {
                try { closeImage.accept(image); } catch (Throwable ignored) {}
            }
            return accepted;
        } finally {
            inFlight.remove(new InFlightKey(expectedGeneration, packId));
        }
    }

    /**
     * Records a missing/failed icon decode for {@code expectedGeneration}.
     * Obsolete workers are ignored so they cannot suppress a refreshed pack.
     */
    public boolean completeMissing(long expectedGeneration, String packId) {
        try {
            synchronized (stateLock) {
                if (expectedGeneration != generation) return false;
                missing.add(packId);
                return true;
            }
        } finally {
            inFlight.remove(new InFlightKey(expectedGeneration, packId));
        }
    }

    /**
     * Drains up to {@code maxUploads} ready images on the render thread,
     * closing and skipping any stale-generation items that raced with
     * {@link #invalidateAll}.
     */
    public int drainReady(int maxUploads, Function<I, T> uploader, Consumer<I> closeImage, Consumer<T> retireTexture) {
        int uploaded = 0;
        ReadyItem<I> item;
        while (uploaded < maxUploads && (item = ready.poll()) != null) {
            if (item.generation() != generation) {
                if (closeImage != null) {
                    try { closeImage.accept(item.image()); } catch (Throwable ignored) {}
                }
                continue;
            }
            uploaded++;
            T tex;
            try {
                tex = uploader.apply(item.image());
            } catch (Throwable t) {
                if (closeImage != null) {
                    try { closeImage.accept(item.image()); } catch (Throwable ignored) {}
                }
                continue;
            }
            if (tex == null) {
                if (closeImage != null) {
                    try { closeImage.accept(item.image()); } catch (Throwable ignored) {}
                }
                continue;
            }
            if (item.generation() != generation) {
                if (retireTexture != null) retireTexture.accept(tex);
                continue;
            }
            T prev = cache.put(item.packId(), tex);
            if (prev != null && retireTexture != null) retireTexture.accept(prev);
            trimCache(retireTexture);
        }
        return uploaded;
    }

    /**
     * Advances the generation, retires all cached textures, clears missing
     * markers, and closes all queued decoded images.
     */
    public long invalidateAll(Consumer<I> closeImage, Consumer<T> retireTexture) {
        long nextGen;
        synchronized (stateLock) {
            nextGen = ++generation;
            missing.clear();
        }
        if (retireTexture != null) {
            for (T tex : cache.values()) retireTexture.accept(tex);
        }
        cache.clear();
        ReadyItem<I> pending;
        while ((pending = ready.poll()) != null) {
            if (closeImage != null) {
                try { closeImage.accept(pending.image()); } catch (Throwable ignored) {}
            }
        }
        return nextGen;
    }

    private void trimCache(Consumer<T> retireTexture) {
        Iterator<Map.Entry<String, T>> it = cache.entrySet().iterator();
        while (cache.size() > maxCached && it.hasNext()) {
            T oldest = it.next().getValue();
            if (retireTexture != null) retireTexture.accept(oldest);
            it.remove();
        }
    }
}
