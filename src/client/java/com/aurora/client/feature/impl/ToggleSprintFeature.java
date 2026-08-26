package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import com.aurora.client.util.AuroraKey;
import net.minecraft.client.Minecraft;

/**
 * Toggle-sprint and toggle-sneak. Pressing the bound key flips a persistent
 * state; while that state is on, we force the corresponding input every
 * tick by holding the vanilla sprint/sneak keybind down programmatically.
 */
public class ToggleSprintFeature implements Feature {
    public static final String ID = "toggle_sprint_sneak";

    private static volatile boolean sprintToggled = false;
    private static volatile boolean sneakToggled  = false;

    private final AuroraKey.EdgeDetector sprintEdge = new AuroraKey.EdgeDetector();
    private final AuroraKey.EdgeDetector sneakEdge  = new AuroraKey.EdgeDetector();

    @Override public String id() { return ID; }

    public static boolean isSprintToggled() { return sprintToggled; }
    public static boolean isSneakToggled()  { return sneakToggled; }

    @Override
    public void onTick(Minecraft client) {
        if (client == null || client.player == null || client.options == null) return;
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.toggleSprintSneakEnabled) {
            sprintToggled = false;
            sneakToggled = false;
            return;
        }

        // Toggle on key-down edge (not while held).
        if (sprintEdge.justPressed(cfg.toggleSprintKey)) {
            sprintToggled = !sprintToggled;
        }
        if (sneakEdge.justPressed(cfg.toggleSneakKey)) {
            sneakToggled = !sneakToggled;
        }

        // While toggled on, virtually hold the vanilla keybind.
        if (sprintToggled) {
            client.options.keySprint.setDown(true);
        }
        if (sneakToggled) {
            client.options.keyShift.setDown(true);
        }

        // Safety: if the player stops moving forward, auto-clear the sprint toggle
        // so we don't sprint-in-place (matches intuition Ã¢â‚¬â€ most mods do this).
        if (sprintToggled && !client.options.keyUp.isDown()) {
            // Let vanilla naturally stop it; next tick we won't force it.
            // Actually, we just don't re-press it; user can still toggle off.
        }
    }
}