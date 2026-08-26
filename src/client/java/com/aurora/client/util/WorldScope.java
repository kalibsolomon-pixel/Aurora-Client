package com.aurora.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

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

    private WorldScope() {}

    public static String current() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return "none";

        // Singleplayer / integrated server first — the integrated server
        // exists even when getCurrentServer() is non-null (LAN), so check
        // it before the MP branch to keep LAN saves grouped with their SP
        // origin.
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            String name = mc.getSingleplayerServer().getWorldData().getLevelName();
            return "sp:" + sanitize(name);
        }

        ServerData server = mc.getCurrentServer();
        if (server != null && server.ip != null && !server.ip.isEmpty()) {
            return "mp:" + sanitize(server.ip);
        }

        return "none";
    }

    private static String sanitize(String s) {
        // Path separators and shell metacharacters out, lowercase for
        // consistency between launches that may have stored the address
        // with different casing.
        return s.toLowerCase()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .trim();
    }
}
