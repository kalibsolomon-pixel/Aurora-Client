package com.aurora.client.feature.impl;

import com.aurora.client.config.profile.ProfileManager;
import com.aurora.client.feature.Feature;
import com.aurora.client.hud.AlertManager;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.util.AuroraKey;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-profile hotkeys (2026-11-12): a key bound on a profile's card switches
 * to that profile in game and confirms it with a silent popup. A binding may
 * carry Ctrl, Shift and Alt ({@code KeyCombo}); like every keybind the most
 * specific match wins, so Ctrl + P and P can switch to different profiles.
 * Only with no
 * screen open and a player in a world — the same gate as every Aurora hotkey.
 * It changes local settings only and sends nothing anywhere, so it is not a
 * catalog feature and has no fair-play entry.
 */
public class ProfileHotkeyFeature implements Feature {
    public static final String ID = "profile_hotkeys";

    /** One edge detector per binding (P and Ctrl + P are different bindings), kept across ticks; bindings are few. */
    private final Map<Integer, AuroraKey.EdgeDetector> edges = new HashMap<>();

    @Override public String id() { return ID; }

    @Override
    public void onTick(Minecraft client) {
        if (client == null) return;
        Map<String, Integer> hotkeys = ProfileManager.getInstance().hotkeys();
        if (hotkeys.isEmpty()) return;
        boolean ready = com.aurora.client.adapter.NativeUi.screen(client) == null && client.player != null;
        for (Map.Entry<String, Integer> e : hotkeys.entrySet()) {
            int binding = e.getValue();
            // Always sampled, so a key held through a menu does not fire on close.
            boolean pressed = edges.computeIfAbsent(binding, k -> new AuroraKey.EdgeDetector()).justPressed(binding);
            if (!pressed || !ready) continue;
            String name = e.getKey();
            if (name.equals(ProfileManager.getInstance().currentProfile())) continue;
            if (ProfileManager.getInstance().switchTo(name)) {
                AlertManager.notify("Profile", name, ThemeManager.color(ThemeToken.ACCENT), 1500L);
            }
            break; // one switch per tick; the map may have changed
        }
    }
}
