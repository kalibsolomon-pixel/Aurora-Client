package com.aurora.client.launcher;

import com.aurora.client.AuroraClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;

/** Event adapter only. No mixins, tick hooks, config fields or renderer dependencies. */
public final class LauncherActivityIntegration {
    private LauncherActivityIntegration() {}

    public static void initialize() {
        LauncherActivityBridge bridge = null;
        try {
            BridgeBootstrap bootstrap = BridgeBootstrap.fromEnvironment(System.getenv());
            if (bootstrap == null) return;
            bridge = new LauncherActivityBridge(bootstrap, AuroraClient.LOGGER::debug);
            LauncherActivityBridge active = bridge;
            ClientLifecycleEvents.CLIENT_STARTED.register(client -> report(active, client));
            ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> report(active, client));
            ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> report(active, client));
            // Fabric 0.141.4 world-change callback excludes null worlds. DISCONNECT is
            // required and fires before teardown; do not project the still-loaded level here.
            ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> active.publish(ActivitySnapshot.mainMenu()));
            ClientLifecycleEvents.CLIENT_STOPPING.register(client -> active.close());
        } catch (Exception | LinkageError failure) {
            if (bridge != null) bridge.close();
            AuroraClient.LOGGER.debug("Aurora launcher activity bridge unavailable for this session.");
        }
    }

    private static void report(LauncherActivityBridge bridge, Minecraft client) {
        if (bridge.isStopped()) return;
        try {
            var integrated = client.getSingleplayerServer();
            ServerData server = client.getCurrentServer();
            String worldSaveId = integrated == null ? null
                    : integrated.getWorldPath(LevelResource.ROOT).getFileName().toString();
            bridge.publish(ActivitySnapshot.project(client.level != null, client.hasSingleplayerServer(),
                    integrated == null ? null : integrated.getWorldData().getLevelName(),
                    server == null ? null : server.name, server == null ? null : server.ip,
                    worldSaveId, server == null ? null : server.ip));
        } catch (Exception | LinkageError failure) {
            bridge.close();
            AuroraClient.LOGGER.debug("Aurora launcher activity bridge state unavailable for this session.");
        }
    }
}
