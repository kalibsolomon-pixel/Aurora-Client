package com.aurora.client.util;

import com.aurora.client.config.AuroraConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Combos on Minecraft's own key bindings (owner request 2026-11-12). A
 * {@link KeyMapping} keeps its plain key in options.txt, so removing Aurora
 * leaves working plain bindings; the modifiers live on the mapping
 * ({@link KeyComboHolder}) and persist in
 * {@link AuroraConfig#vanillaKeyModifiers} for every mapping outside
 * Aurora's category (Aurora's own keep them in their config ints).
 */
public final class VanillaKeyCombos {
    private VanillaKeyCombos() {}

    /** The Controls row waiting for a key, and its pending modifiers ("Ctrl + …"). */
    public static KeyMapping capturing;
    public static final KeyCombo.Capture CAPTURE = new KeyCombo.Capture();

    public static int modifiers(KeyMapping mapping) {
        return mapping instanceof KeyComboHolder holder ? holder.aurora$modifiers() : 0;
    }

    public static void setModifiers(KeyMapping mapping, int modifiers) {
        if (mapping instanceof KeyComboHolder holder) holder.aurora$setModifiers(modifiers & KeyCombo.MODIFIER_MASK);
    }

    /** The mapping's keyboard key as a stored code, or -1 (unbound, or a mouse button). */
    public static int storedKey(KeyMapping mapping) {
        return storedKey(KeyMappingHelper.getBoundKeyOf(mapping));
    }

    public static int storedKey(InputConstants.Key key) {
        if (key == null || key.equals(InputConstants.UNKNOWN)) return KeyCombo.UNBOUND;
        if (key.getType() != com.aurora.client.adapter.NativeKeyboard.type()) return KeyCombo.UNBOUND;
        return com.aurora.client.adapter.NativeKeyboard.toStored(key.getValue());
    }

    /** The mapping as a {@link KeyCombo} binding, or -1. */
    public static int binding(KeyMapping mapping) {
        return KeyCombo.of(storedKey(mapping), modifiers(mapping));
    }

    /**
     * Rebuilds {@link KeyCombo.Registry} from every mapping (Minecraft's and
     * the mirrors of Aurora's) plus the profile hotkeys. Once per client tick;
     * no allocation in the steady state.
     */
    public static void rebuildRegistry(Map<String, Integer> profileHotkeys) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        KeyCombo.Registry.begin();
        for (KeyMapping mapping : mc.options.keyMappings) KeyCombo.Registry.add(binding(mapping));
        for (int hotkey : profileHotkeys.values()) KeyCombo.Registry.add(hotkey);
        KeyCombo.Registry.commit();
    }

    /** Applies the saved modifiers to Minecraft's mappings (after options.txt has loaded). */
    public static void load() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        Map<String, Integer> saved = AuroraConfig.get().vanillaKeyModifiers;
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping.getCategory() == AuroraKeybinds.CATEGORY) continue;
            Integer mods = saved == null ? null : saved.get(mapping.getName());
            setModifiers(mapping, mods == null ? 0 : mods);
        }
    }

    /** Writes Minecraft's mappings' modifiers back to the config; saves only when something changed. */
    public static void store() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        Map<String, Integer> next = new LinkedHashMap<>();
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping.getCategory() == AuroraKeybinds.CATEGORY) continue;
            int mods = modifiers(mapping);
            if (mods != 0) next.put(mapping.getName(), mods);
        }
        AuroraConfig cfg = AuroraConfig.get();
        if (next.equals(cfg.vanillaKeyModifiers)) return;
        cfg.vanillaKeyModifiers = next;
        AuroraConfig.save();
    }
}
