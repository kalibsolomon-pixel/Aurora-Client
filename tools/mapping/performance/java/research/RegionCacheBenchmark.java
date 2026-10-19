package research;

import com.aurora.client.worldmap.cache.RegionCache;
import java.lang.management.ManagementFactory;

/** Same actual RegionCache class, run with preserved reference or candidate output. */
public final class RegionCacheBenchmark {
    private static volatile Object sink;
    public static void main(String[] args) {
        var memory = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        memory.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        RegionCache cache = new RegionCache(8, RegionCache.RegionTile::release);
        long[] keys = new long[8];
        for (int i = 0; i < keys.length; i++) { keys[i] = RegionCache.key(i - 4, -9); cache.acquire(keys[i]); }
        try {
            for (int i = 0; i < 500_000; i++) sink = cache.peek(keys[i & 7]);
            long allocated = memory.getThreadAllocatedBytes(thread), start = System.nanoTime();
            int calls = 2_000_000;
            for (int i = 0; i < calls; i++) sink = cache.peek(keys[i & 7]);
            long elapsed = System.nanoTime() - start, bytes = memory.getThreadAllocatedBytes(thread) - allocated;
            System.out.println("{\"calls\":" + calls + ",\"nsPerCall\":" + elapsed / (double)calls +
                    ",\"allocatedBytesPerCall\":" + bytes / (double)calls + "}");
        } finally { cache.closeAll(); }
    }
}
