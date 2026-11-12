package com.aurora.client.feature.impl;

import com.aurora.client.config.profile.ProfileHotkey;
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
 * carry Ctrl, Shift and Alt; it fires only with exactly those held, so
 * Ctrl + P and P can switch to different profiles. Only with no
 * screen open and a player in a world — the same gate as every Aurora hotkey.
 * It changes local settings only and sends nothing anywhere, so it is not a
 * catalog feature and has no fair-play entry.
 */
public class ProfileHotkeyFeature implements Feature {
    public static final String ID = "profile_hotkeys";

    /** One edge detector per stored key, kept across ticks; keys are few. */
    private final Map<Integer, KeyEdge> edges = new HashMap<>();
    private long tick;

    /** A key's rising edge, sampled once per tick however many bindings share the key (P and Ctrl + P). */
    private static final class KeyEdge {
        private final AuroraKey.EdgeDetector detector = new AuroraKey.EdgeDetector();
        private long sampledTick = -1;
        private boolean pressed;

        boolean pressed(int key, long tick) {
            if (sampledTick != tick) {
                sampledTick = tick;
                pressed = detector.justPressed(key);
            }
            return pressed;
        }
    }

    @Override public String id() { return ID; }

    @Override
    public void onTick(Minecraft client) {
        if (client == null) return;
        Map<String, ProfileHotkey> hotkeys = ProfileManager.getInstance().hotkeys();
        if (hotkeys.isEmpty()) return;
        tick++;
        boolean ready = com.aurora.client.adapter.NativeUi.screen(client) == null && client.player != null;
        int held = 0;
        for (int modifier : ProfileHotkey.MODIFIER_KEYS) {
            if (AuroraKey.isDown(modifier)) held |= ProfileHotkey.modifierOf(modifier);
        }
        for (Map.Entry<String, ProfileHotkey> e : hotkeys.entrySet()) {
            ProfileHotkey binding = e.getValue();
            int key = binding.key();
            // Always sampled, so a key held through a menu does not fire on close.
            boolean pressed = edges.computeIfAbsent(key, k -> new KeyEdge()).pressed(key, tick);
            if (!pressed || !ready || !binding.matches(held)) continue;
            String name = e.getKey();
            if (name.equals(ProfileManager.getInstance().currentProfile())) continue;
            if (ProfileManager.getInstance().switchTo(name)) {
                AlertManager.notify("Profile", name, ThemeManager.color(ThemeToken.ACCENT), 1500L);
            }
            break; // one switch per tick; the map may have changed
        }
    }
}
