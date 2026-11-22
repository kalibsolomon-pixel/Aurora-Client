package com.aurora.client.config.profile;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.multiplayer.ServerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connects Fabric's multiplayer join/disconnect lifecycle to
 * {@link ProfileManager#onServerJoin(String)} and
 * {@link ProfileManager#onServerDisconnect()} so bound profiles activate
 * automatically when joining a matching server and restore cleanly on leave.
 */
public final class ServerProfileSwitcher {
    private static final Logger LOGGER = LoggerFactory.getLogger("Aurora");
    private static boolean registered;

    private ServerProfileSwitcher() {}

    public static void register() {
        if (registered) return;
        registered = true;

        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> mc.execute(() -> {
            ServerData server = mc.getCurrentServer();
            if (server == null || server.ip == null || server.ip.isBlank()) return;
            String switched = ProfileManager.getInstance().onServerJoin(server.ip);
            if (switched != null) {
                LOGGER.info("[Profiles] Auto-switched to '{}' for server '{}'", switched, server.ip);
            }
        }));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(() -> {
            String restored = ProfileManager.getInstance().onServerDisconnect();
            if (restored != null) {
                LOGGER.info("[Profiles] Restored profile '{}' after disconnect", restored);
            }
        }));
    }
}
