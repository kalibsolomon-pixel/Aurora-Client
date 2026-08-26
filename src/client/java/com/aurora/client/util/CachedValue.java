package com.aurora.client.util;

import java.util.function.Supplier;

/**
 * Holds a value with a time-to-live. Recomputes via the supplier only when
 * stale. Used by HUD modules to cache dynamic strings/dimensions instead of
 * recomputing them every frame (which can happen 200+ times per second).
 */
public final class CachedValue<T> {
    private final Supplier<T> supplier;
    private final long ttlMillis;
    private T cached;
    private long lastRefresh = 0L;

    public CachedValue(long ttlMillis, Supplier<T> supplier) {
        this.ttlMillis = ttlMillis;
        this.supplier = supplier;
    }

    public T get() {
        long now = System.currentTimeMillis();
        if (cached == null || now - lastRefresh >= ttlMillis) {
            cached = supplier.get();
            lastRefresh = now;
        }
        return cached;
    }

    public void invalidate() {
        cached = null;
    }
}