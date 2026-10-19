package com.aurora.client.util;

import java.lang.management.ManagementFactory;
import java.util.Locale;

/** Explicit standalone hot-path diagnostic, not a game/FPS benchmark. */
public final class WorldScopeBenchmark {
    private static volatile String sink;

    public static void main(String[] args) {
        boolean cached = args[0].equals("cached");
        int count = 500_000;
        var keys = new WorldScopeKey();
        // Identical stable identity, as observed at the profiled call sites.
        String source = new String(" Cperf1a:World/Example ");
        for (int i = 0; i < 100_000; i++) sink = resolve(cached, keys, source);
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        long bytes = bean.getThreadAllocatedBytes(thread);
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) sink = resolve(cached, keys, source);
        long duration = System.nanoTime() - start;
        bytes = bean.getThreadAllocatedBytes(thread) - bytes;
        System.out.printf(Locale.ROOT,
                "{\"path\":\"%s\",\"calls\":%d,\"nsPerCall\":%.3f,\"bytesPerCall\":%.3f,\"key\":\"%s\"}%n",
                args[0], count, (double) duration / count, (double) bytes / count, sink);
    }

    private static String resolve(boolean cached, WorldScopeKey keys, String source) {
        return cached ? keys.resolve("sp:", source, Locale.getDefault())
                : "sp:" + source.toLowerCase().replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
