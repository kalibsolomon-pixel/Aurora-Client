package com.aurora.client.util;

import com.aurora.client.config.profile.ControlsBridge;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Profiles' view of the game's own key bindings ("Separate controls",
 * 2026-11-12): every {@link KeyMapping} outside Aurora's category, by name,
 * as the saved key string options.txt uses. Applying sets each known
 * binding, rebuilds vanilla's key lookup and saves options — exactly what
 * the Controls screen does on close — and never sends anything to a server.
 */
public final class VanillaControlsBridge implements ControlsBridge {

    @Override
    public Map<String, String> capture() {
        Map<String, String> out = new LinkedHashMap<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return out;
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping.getCategory() == AuroraKeybinds.CATEGORY) continue; // in the profile's config fields
            // "key.keyboard.p|1" for Ctrl + P; plain keys keep vanilla's own string.
            out.put(mapping.getName(), KeyCombo.controlsString(mapping.saveString(), VanillaKeyCombos.modifiers(mapping)));
        }
        return out;
    }

    @Override
    public void apply(Map<String, String> controls) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null || controls == null) return;
        boolean changed = false;
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping.getCategory() == AuroraKeybinds.CATEGORY) continue;
            String value = controls.get(mapping.getName());
            if (value == null) continue;
            String saved = KeyCombo.controlsKey(value);
            int modifiers = KeyCombo.controlsModifiers(value);
            if (saved.equals(mapping.saveString()) && modifiers == VanillaKeyCombos.modifiers(mapping)) continue;
            InputConstants.Key key;
            try {
                key = InputConstants.getKey(saved);
            } catch (RuntimeException unknown) {
                continue; // a key this version does not know: leave the binding as it is
            }
            mapping.setKey(key);
            VanillaKeyCombos.setModifiers(mapping, modifiers);
            changed = true;
        }
        if (changed) {
            KeyMapping.resetMapping();
            mc.options.save();
            VanillaKeyCombos.store();
        }
    }
}
