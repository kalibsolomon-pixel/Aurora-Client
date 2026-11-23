package com.aurora.client.util;

import com.aurora.client.port.InputPort;
import java.util.Objects;

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

    private static InputPort input = new InputPort() {
        public boolean isDown(int scancode) { return false; }
        public boolean suppressed() { return true; }
    };
    public static void install(InputPort nativeInput) { input = Objects.requireNonNull(nativeInput); }
    private AuroraKey() {}

    /**
     * Live held-state for a binding ({@link KeyCombo}: a key, optionally with
     * Ctrl / Shift / Alt), suppressed while any native screen is open so
     * Aurora keybinds don't fire while the user is typing in chat, navigating
     * menus, or interacting with the Aurora settings UI itself. A combo needs
     * its modifiers held, and a plain key gives way only to a combo on the
     * same key that matches what is held (most specific wins).
     * {@code false} for {@link #UNBOUND}.
     */
    public static boolean isDown(int binding) {
        if (binding == UNBOUND) return false;
        if (suppressed()) return false;
        return isDownRaw(binding);
    }

    /**
     * Physical state of one key, with no combo rules — for display keys such
     * as Keystrokes' extra keys, which show the key itself, not a binding.
     */
    public static boolean isKeyDown(int scancode) {
        if (scancode == UNBOUND || suppressed()) return false;
        return input.isDown(KeyCombo.key(scancode));
    }

    /** The modifiers physically held now ({@link KeyCombo#CTRL} and so on), screens aside. */
    public static int heldModifiers() {
        int held = 0;
        for (int key : KeyCombo.MODIFIER_KEYS) {
            if (input.isDown(key)) held |= KeyCombo.modifierOf(key);
        }
        return held;
    }

    /**
     * Same as {@link #isDown} but bypasses the screen-open suppression.
     * Used by {@link EdgeDetector} so we can keep tracking the physical
     * key state across screen transitions and avoid spurious edge fires
     * when a screen closes with the key still held.
     */
    private static boolean isDownRaw(int binding) {
        return isDownRaw(binding, true);
    }

    /**
     * {@code hold}: a modifier that is waiting for a combo ({@link KeyCombo.Deferral})
     * also reads down once it counts as held; without, only its one-tick tap does,
     * so one-shot bindings never fire from a held modifier.
     */
    private static boolean isDownRaw(int binding, boolean hold) {
        if (binding == UNBOUND) return false;
        int key = KeyCombo.key(binding);
        if (KeyCombo.Deferral.defers(key)) {
            boolean down = KeyCombo.Deferral.tapped(key) || (hold && KeyCombo.Deferral.held(key));
            return down && KeyCombo.resolves(binding, heldModifiers());
        }
        if (!input.isDown(key)) return false;
        int held = heldModifiers();
        if (!KeyCombo.resolves(binding, held)) return false;
        // A combo completed: its modifiers started no binding of their own.
        KeyCombo.Deferral.consume(KeyCombo.modifiers(binding));
        return true;
    }

    /** True when any GUI/screen is open — Aurora keybinds suppress here. */
    private static boolean suppressed() {
        return input.suppressed();
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

        /** Polls a binding and returns true exactly once per press, in-game only. */
        public boolean justPressed(int scancodeKey) {
            boolean rawDown = isDownRaw(scancodeKey, false);
            boolean edge = rawDown && !wasDown && !suppressed();
            wasDown = rawDown;
            return edge;
        }

        /** Reset memory so the next press is treated as fresh. */
        public void reset() { wasDown = false; }
    }
}
