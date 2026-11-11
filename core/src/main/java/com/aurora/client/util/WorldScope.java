package com.aurora.client.util;

import com.aurora.client.port.WorldPort;
import java.util.Objects;
import java.util.Locale;

/**
 * Resolves a stable identifier for the player's current world / server.
 * Used by per-world features (playtime, waypoints, etc.) so their data
 * stays scoped to where it was created instead of leaking across
 * servers and singleplayer saves.
 *
 * <p>Format:
 * <ul>
 *   <li>{@code mp:<server-address>}        — multiplayer (case-folded)</li>
 *   <li>{@code sp:<level-name>}            — singleplayer / LAN host</li>
 *   <li>{@code none}                       — no level loaded (title screen)</li>
 * </ul>
 *
 * <p>The string is intentionally filesystem-safe enough for use as the
 * stem of a per-scope JSON file (slashes scrubbed, lowercase) without
 * adding a separate sanitization step at every call site.
 */
public final class WorldScope {
    private static final WorldScopeKey KEYS = new WorldScopeKey();

    private static WorldPort world = new WorldPort() {
        public boolean loaded() { return false; }
        public String singleplayerName() { return null; }
        public String serverAddress() { return null; }
    };
    public static void install(WorldPort nativeWorld) { world = Objects.requireNonNull(nativeWorld); }
    private WorldScope() {}

    public static String current() {
        if (!world.loaded()) return "none";
        String name = world.singleplayerName();
        if (name != null) return KEYS.resolve("sp:", name, Locale.getDefault());
        String address = world.serverAddress();
        if (address != null && !address.isEmpty()) return KEYS.resolve("mp:", address, Locale.getDefault());
        return "none";
    }
}
