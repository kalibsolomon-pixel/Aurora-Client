package com.aurora.client.worldmap;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Mapping identity is lossless; legacy WorldScope remains unchanged for other features. */
public record MapIdentity(String key, String legacyScope) {
    private static Object owner;
    private static String address;
    private static MapIdentity cached;
    public static MapIdentity current() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return null;
        String source;
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            if (owner == mc.getSingleplayerServer() && cached != null) return cached;
            owner = mc.getSingleplayerServer();
            // Save directory survives display-name changes and distinguishes identical names.
            source = "save:" + mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT)
                    .toAbsolutePath().normalize().toString();
        } else if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null
                && !mc.getCurrentServer().ip.isBlank()) {
            if (owner == mc.getCurrentServer() && mc.getCurrentServer().ip.equals(address) && cached != null)
                return cached;
            owner = mc.getCurrentServer();
            address = mc.getCurrentServer().ip;
            source = "server:" + mc.getCurrentServer().ip.trim().toLowerCase(Locale.ROOT);
        } else {
            // Incomplete identity never writes into a shared "none" namespace.
            return null;
        }
        cached = new MapIdentity("map-" + digest(source), com.aurora.client.util.WorldScope.current());
        return cached;
    }

    public static void clear() { owner = null; address = null; cached = null; }

    public static String digest(String source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static String dimensionFolder(String dimension) {
        return "dim-" + digest(dimension);
    }
}
