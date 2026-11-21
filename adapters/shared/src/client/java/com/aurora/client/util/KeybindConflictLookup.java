package com.aurora.client.util;

import com.aurora.client.config.profile.ProfileManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared adapter bridge that collects live vanilla, Aurora and profile
 * bindings for {@link KeybindConflicts}.
 *
 * <p>Aurora UI queries ({@link #conflictsForAurora} and
 * {@link #conflictsForProfile}) read Aurora's config getters directly (via
 * {@link AuroraKeybinds}) so rebinding inside an Aurora screen updates
 * conflict state on the same frame without waiting for the next client tick.
 * Results are cached against a cheap allocation-free fingerprint of every
 * binding so steady-state screen frames allocate nothing.
 *
 * <p>Vanilla Controls screen queries ({@link #extraVanillaControlsConflicts})
 * read {@code mc.options.keyMappings} directly because {@code KeyBindsScreen}
 * mutates the {@link KeyMapping} instances before {@link AuroraKeybinds#tick()}
 * copies them back into Aurora's config.
 */
public final class KeybindConflictLookup {
    private static long cachedFingerprint = Long.MIN_VALUE;
    private static Map<Integer, List<KeybindConflicts.Entry>> byBinding = Map.of();
    private static final Map<String, List<String>> resolvedCache = new HashMap<>();

    private KeybindConflictLookup() {}

    /** True for F3+&lt;key&gt; debug sub-keys that never fire on a standalone press. */
    public static boolean isDebugChord(KeyMapping mapping) {
        if (mapping == null || mapping.getCategory() != KeyMapping.Category.DEBUG) return false;
        String name = mapping.getName();
        return !"key.debug.overlay".equals(name) && !"key.debug.modifier".equals(name);
    }

    /** Conflicts for an Aurora {@code KeybindSetting} row (e.g. {@code "key.aurora.zoom"}). */
    public static List<String> conflictsForAurora(String keyId, int binding) {
        return lookupCached(keyId, binding);
    }

    /** Conflicts for a profile hotkey (e.g. profile {@code "PvP"}). */
    public static List<String> conflictsForProfile(String profileName, int binding) {
        if (profileName == null) return List.of();
        return lookupCached("profile:" + profileName, binding);
    }

    /**
     * Extra conflicts to surface in vanilla's Controls screen for {@code self}
     * (profile hotkeys + default-vs-default collisions involving Aurora).
     */
    public static List<String> extraVanillaControlsConflicts(KeyMapping self) {
        if (self == null || self.isUnbound() || isDebugChord(self)) return List.of();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null || mc.options.keyMappings == null) return List.of();
        int selfBinding = VanillaKeyCombos.binding(self);
        if (selfBinding < 0) return List.of();

        KeybindConflicts.Entry selfEntry = new KeybindConflicts.Entry(
                self.getName(),
                Component.translatable(self.getName()).getString(),
                selfBinding,
                false,
                self.getCategory() == AuroraKeybinds.CATEGORY,
                self.isDefault());

        List<KeybindConflicts.Entry> candidates = new ArrayList<>();
        for (KeyMapping other : mc.options.keyMappings) {
            if (other == self || other.isUnbound() || isDebugChord(other)) continue;
            if (!self.same(other)) continue;
            int otherBinding = VanillaKeyCombos.binding(other);
            if (otherBinding != selfBinding) continue;
            candidates.add(new KeybindConflicts.Entry(
                    other.getName(),
                    Component.translatable(other.getName()).getString(),
                    otherBinding,
                    false,
                    other.getCategory() == AuroraKeybinds.CATEGORY,
                    other.isDefault()));
        }
        Map<String, Integer> hotkeys = ProfileManager.getInstance().hotkeys();
        if (!hotkeys.isEmpty()) {
            for (Map.Entry<String, Integer> e : hotkeys.entrySet()) {
                int hk = e.getValue();
                if (hk == selfBinding) {
                    candidates.add(new KeybindConflicts.Entry(
                            "profile:" + e.getKey(),
                            "Profile: " + e.getKey(),
                            hk, false, true, false));
                }
            }
        }
        return KeybindConflicts.extraVanillaControlsConflicts(selfEntry, candidates);
    }

    /** Clears the snapshot cache (used by tests or after bulk profile changes). */
    public static void invalidate() {
        cachedFingerprint = Long.MIN_VALUE;
        byBinding = Map.of();
        resolvedCache.clear();
    }

    private static List<String> lookupCached(String selfId, int binding) {
        if (binding < 0) return List.of();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null || mc.options.keyMappings == null) return List.of();
        ensureSnapshot(mc);
        List<KeybindConflicts.Entry> bucket = byBinding.get(binding);
        if (bucket == null || bucket.isEmpty()) return List.of();
        if (bucket.size() == 1 && selfId != null && selfId.equals(bucket.get(0).id())) return List.of();
        String cacheKey = selfId == null ? ("#|" + binding) : (selfId + "|" + binding);
        return resolvedCache.computeIfAbsent(cacheKey, k -> KeybindConflicts.forAuroraBinding(selfId, binding, bucket));
    }

    private static void ensureSnapshot(Minecraft mc) {
        Map<String, Integer> hotkeys = ProfileManager.getInstance().hotkeys();
        long fp = fingerprint(mc.options.keyMappings, hotkeys);
        if (fp == cachedFingerprint) return;

        Map<Integer, List<KeybindConflicts.Entry>> next = new HashMap<>();
        // 1. Live Aurora config bindings (source of truth while Aurora screens are open).
        AuroraKeybinds.forEach((mapping, binding) -> {
            if (binding < 0) return;
            String id = mapping.getName();
            String label = Component.translatable(id).getString();
            next.computeIfAbsent(binding, k -> new ArrayList<>(2))
                    .add(new KeybindConflicts.Entry(id, label, binding, false, true, mapping.isDefault()));
        });
        // 2. Vanilla & other mods' KeyMappings (skip Aurora category, already added above).
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping.getCategory() == AuroraKeybinds.CATEGORY) continue;
            if (isDebugChord(mapping)) continue;
            int binding = VanillaKeyCombos.binding(mapping);
            if (binding < 0) continue;
            String id = mapping.getName();
            String label = Component.translatable(id).getString();
            next.computeIfAbsent(binding, k -> new ArrayList<>(2))
                    .add(new KeybindConflicts.Entry(id, label, binding, false, false, mapping.isDefault()));
        }
        // 3. Profile hotkeys.
        if (!hotkeys.isEmpty()) {
            for (Map.Entry<String, Integer> e : hotkeys.entrySet()) {
                int hk = e.getValue();
                if (hk < 0) continue;
                next.computeIfAbsent(hk, k -> new ArrayList<>(2))
                        .add(new KeybindConflicts.Entry("profile:" + e.getKey(), "Profile: " + e.getKey(), hk, false, true, false));
            }
        }
        byBinding = next;
        resolvedCache.clear();
        cachedFingerprint = fp;
    }

    private static long fingerprint(KeyMapping[] mappings, Map<String, Integer> hotkeys) {
        long h = 0x9E3779B97F4A7C15L;
        // Include Aurora's live config values first so same-frame Aurora edits bust the cache.
        h = AuroraKeybinds.fingerprint(h);
        for (KeyMapping mapping : mappings) {
            if (mapping.getCategory() == AuroraKeybinds.CATEGORY) continue;
            h = h * 31L + VanillaKeyCombos.binding(mapping);
        }
        h = h * 31L + hotkeys.size();
        for (Map.Entry<String, Integer> e : hotkeys.entrySet()) {
            h = h * 31L + e.getKey().hashCode();
            h = h * 31L + e.getValue();
        }
        return h;
    }
}
