package com.aurora.client.config;

import java.nio.file.Path;

/**
 * Where Aurora keeps {@code aurora.json} and {@code profiles/}.
 *
 * <p>The core cannot ask Fabric for its config directory, so the client sets it
 * first thing in {@code AuroraClient.onInitializeClient}. Reading it before then
 * is a bug, and fails loudly rather than writing to a guessed location.
 */
public final class ConfigDirectory {
    private static volatile Path directory;

    private ConfigDirectory() {}

    public static void set(Path dir) {
        if (dir == null) throw new IllegalArgumentException("config directory is null");
        directory = dir;
    }

    public static Path get() {
        Path dir = directory;
        if (dir == null) throw new IllegalStateException("Aurora's config directory was read before the client set it");
        return dir;
    }
}
