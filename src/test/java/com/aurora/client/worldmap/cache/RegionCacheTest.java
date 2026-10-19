package com.aurora.client.worldmap.cache;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RegionCacheTest {
    @Test void residentReadsPreserveAccessEvictionAndMissesDoNotCreate() {
        List<Long> evicted = new ArrayList<>();
        RegionCache cache = new RegionCache(8, tile -> { evicted.add(tile.key); tile.release(); });
        try {
            for (int i = 0; i < 8; i++) cache.acquire(RegionCache.key(i - 4, -7));
            long oldest = RegionCache.key(-4, -7), second = RegionCache.key(-3, -7);
            assertNotNull(cache.peek(oldest));
            assertNull(cache.peek(Long.MIN_VALUE));
            assertEquals(8, cache.size());
            cache.acquire(RegionCache.key(19, -7));
            assertEquals(List.of(second), evicted);
            assertNotNull(cache.peek(oldest));
            assertNull(cache.peek(second));
        } finally { cache.closeAll(); }
        assertEquals(9, evicted.size());
        assertEquals(9, evicted.stream().distinct().count());
        assertEquals(0, cache.size());
    }

    @Test void packedNegativeKeysRemainDistinctAndPixelsSurviveReads() {
        RegionCache cache = new RegionCache(8, RegionCache.RegionTile::release);
        try {
            assertSame(cache.acquire(0L), cache.peek(0L));
            for (int x : new int[]{Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE}) {
                long key = RegionCache.key(x, -5);
                assertEquals(x, RegionCache.regionX(key));
                assertEquals(-5, RegionCache.regionZ(key));
                var tile = cache.acquire(key);
                tile.image.setPixel(2, 3, 0xff13579b);
                tile.stamps[7] = 12345;
                assertSame(tile, cache.peek(key));
                assertSame(tile, cache.acquire(key));
                assertEquals(0xff13579b, cache.peek(key).image.getPixel(2, 3));
                assertEquals(12345, cache.peek(key).stamps[7]);
            }
        } finally { cache.closeAll(); }
    }
}
