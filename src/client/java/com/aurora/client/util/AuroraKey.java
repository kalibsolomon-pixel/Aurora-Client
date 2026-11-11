package com.aurora.client.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

/**
 * Polls SDL physical scancodes for Aurora-owned held bindings. AuroraKeybinds
 * also exposes and synchronizes them through vanilla Controls. Each feature
 * reads its current binding from AuroraConfig and uses EdgeDetector for
 * one-shot actions.
 *
 * <p>Convention: {@code -1} means "unbound". {@link #isDown(int)} short-
 * circuits to {@code false} for unbound keys so features can call it
 * unconditionally.
 */
public final class AuroraKey {
    public static final int UNBOUND = -1;

    private AuroraKey() {}

    /**
     * Live held-state for an SDL scancode, suppressed while any
     * {@link net.minecraft.client.gui.screens.Screen} is open so Aurora
     * keybinds don't fire while the user is typing in chat, navigating
     * menus, or interacting with the Aurora settings UI itself.
     * {@code false} for {@link #UNBOUND}.
     */
    public static boolean isDown(int scancodeKey) {
        if (scancodeKey == UNBOUND) return false;
        if (suppressed()) return false;
        return isDownRaw(scancodeKey);
    }

    /**
     * Same as {@link #isDown} but bypasses the screen-open suppression.
     * Used by {@link EdgeDetector} so we can keep tracking the physical
     * key state across screen transitions and avoid spurious edge fires
     * when a screen closes with the key still held.
     */
    private static boolean isDownRaw(int scancodeKey) {
        if (scancodeKey == UNBOUND) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return false;
        try {
            return InputConstants.isKeyDown(scancodeKey);
        } catch (Exception e) {
            return false;
        }
    }

    /** True when any GUI/screen is open — Aurora keybinds suppress here. */
    private static boolean suppressed() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.gui.screen() != null;
    }

    /**
     * Per-feature edge detector. Tracks the previous physical key state
     * (raw, screen-suppression aside) and returns {@code true} once on
     * the rising edge (false→true) — but only when no screen is open.
     * Tracking raw state across screen transitions avoids the false-edge
     * that would otherwise fire if a key was held when a screen opened
     * and was still held when it closed.
     */
    public static final class EdgeDetector {
        private boolean wasDown = false;

        /** Polls and returns true exactly once per press, in-game only. */
        public boolean justPressed(int scancodeKey) {
            boolean rawDown = isDownRaw(scancodeKey);
            boolean edge = rawDown && !wasDown && !suppressed();
            wasDown = rawDown;
            return edge;
        }

        /** Reset memory so the next press is treated as fresh. */
        public void reset() { wasDown = false; }
    }
}
