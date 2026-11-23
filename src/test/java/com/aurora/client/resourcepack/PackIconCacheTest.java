package com.aurora.client.resourcepack;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PackIconCacheTest {

    private static final class FakeImage {
        final String label;
        final AtomicBoolean closed = new AtomicBoolean();
        FakeImage(String label) { this.label = label; }
        void close() { closed.set(true); }
    }

    @Test
    void obsoleteDecodeAfterRefreshIsClosedAndNeverPopulatesNewGenerationCache() {
        PackIconCache<FakeImage, String> cache = new PackIconCache<>(16);
        List<String> retired = new ArrayList<>();

        long gen1 = cache.tryBeginLoad("file/pack.zip");
        assertTrue(gen1 > 0);

        // Refresh invalidates while gen1 decode is still in flight.
        long gen2 = cache.invalidateAll(FakeImage::close, retired::add);
        assertEquals(gen1 + 1, gen2);

        // New generation can immediately start its own decode without waiting for gen1.
        long claimed2 = cache.tryBeginLoad("file/pack.zip");
        assertEquals(gen2, claimed2);

        // Obsolete gen1 finishes with the old image -> rejected and closed immediately.
        FakeImage stale = new FakeImage("old-icon");
        assertFalse(cache.completeSuccess(gen1, "file/pack.zip", stale, FakeImage::close));
        assertTrue(stale.closed.get(), "rejected stale image must be closed");
        cache.drainReady(8, img -> img.label, FakeImage::close, retired::add);
        assertNull(cache.getCached("file/pack.zip"));

        // Fresh gen2 finishes with the new image -> uploaded into the cache.
        FakeImage fresh = new FakeImage("new-icon");
        assertTrue(cache.completeSuccess(gen2, "file/pack.zip", fresh, FakeImage::close));
        assertEquals(1, cache.drainReady(8, img -> img.label, FakeImage::close, retired::add));
        assertEquals("new-icon", cache.getCached("file/pack.zip"));
        assertFalse(fresh.closed.get());
    }

    @Test
    void obsoleteMissAfterRefreshDoesNotSuppressNewGenerationIcon() {
        PackIconCache<FakeImage, String> cache = new PackIconCache<>(16);

        long gen1 = cache.tryBeginLoad("file/changing.zip");
        assertTrue(gen1 > 0);

        long gen2 = cache.invalidateAll(FakeImage::close, t -> {});

        // Old worker fails/misses AFTER refresh -> must not enter gen2's missing set.
        assertFalse(cache.completeMissing(gen1, "file/changing.zip"));

        // New generation must still be allowed to load and populate the icon.
        assertEquals(gen2, cache.tryBeginLoad("file/changing.zip"));
        FakeImage fresh = new FakeImage("recovered-icon");
        assertTrue(cache.completeSuccess(gen2, "file/changing.zip", fresh, FakeImage::close));
        assertEquals(1, cache.drainReady(8, img -> img.label, FakeImage::close, t -> {}));
        assertEquals("recovered-icon", cache.getCached("file/changing.zip"));
    }
}
