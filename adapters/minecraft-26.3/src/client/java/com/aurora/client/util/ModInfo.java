package com.aurora.client.util;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.Optional;

public final class ModInfo {
    private ModInfo() {}

    public static String version() {
        Optional<ModContainer> c = FabricLoader.getInstance().getModContainer("aurora");
        return c.map(mc -> mc.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    }

    public static boolean isModLoaded(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }
}
