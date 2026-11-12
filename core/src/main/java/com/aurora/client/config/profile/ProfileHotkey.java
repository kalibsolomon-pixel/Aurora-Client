package com.aurora.client.config.profile;

/**
 * A profile hotkey: a key in Aurora's stored encoding (SDL physical scancode)
 * plus the modifiers that must be held with it, so "Ctrl + P" and "P" are
 * different bindings (owner request 2026-11-12). Fn is not a modifier: on
 * most keyboards it never reaches the operating system.
 */
public record ProfileHotkey(int key, int modifiers) {
    public static final int CTRL = 1, SHIFT = 2, ALT = 4;
    public static final ProfileHotkey NONE = new ProfileHotkey(-1, 0);

    // SDL / USB HID scancodes of the modifier keys.
    static final int LCTRL = 224, LSHIFT = 225, LALT = 226, RCTRL = 228, RSHIFT = 229, RALT = 230;

    public ProfileHotkey {
        if (key < 0) { key = -1; modifiers = 0; }
        modifiers &= CTRL | SHIFT | ALT;
    }

    public boolean bound() { return key >= 0; }

    /** The modifier bit a stored key is (either side), or 0 for an ordinary key. */
    public static int modifierOf(int scancode) {
        return switch (scancode) {
            case LCTRL, RCTRL -> CTRL;
            case LSHIFT, RSHIFT -> SHIFT;
            case LALT, RALT -> ALT;
            default -> 0;
        };
    }

    /** Every modifier key (both sides), for polling which modifiers are held. */
    public static final int[] MODIFIER_KEYS = {LCTRL, LSHIFT, LALT, RCTRL, RSHIFT, RALT};

    /**
     * Whether this binding fires when its key goes down with {@code held}
     * modifiers down: exactly its own, so "P" does not fire on Ctrl + P. A
     * modifier bound alone ignores its own bit (holding Shift is holding Shift).
     */
    public boolean matches(int held) {
        return bound() && (held & ~modifierOf(key)) == modifiers;
    }

    /** "Ctrl + Shift + " for the modifiers, in a fixed order; empty for none. */
    public static String modifierPrefix(int modifiers) {
        StringBuilder sb = new StringBuilder();
        if ((modifiers & CTRL) != 0) sb.append("Ctrl + ");
        if ((modifiers & SHIFT) != 0) sb.append("Shift + ");
        if ((modifiers & ALT) != 0) sb.append("Alt + ");
        return sb.toString();
    }

    /**
     * Capture state for a hotkey chip: a modifier pressed alone waits for a
     * second key; another key completes the binding with the modifiers held;
     * releasing a modifier before that binds the modifier itself.
     */
    public static final class Capture {
        private int modifiers;

        /** Modifiers held so far, for the "Ctrl + …" label. */
        public int pending() { return modifiers; }

        public void reset() { modifiers = 0; }

        /**
         * A key went down. {@code clear} marks Esc and Backspace, which clear
         * the binding. Returns the finished binding ({@link #NONE} to clear),
         * or null to keep waiting.
         */
        public ProfileHotkey press(int scancode, boolean clear) {
            if (clear) { reset(); return NONE; }
            int bit = modifierOf(scancode);
            if (bit != 0) { modifiers |= bit; return null; }
            ProfileHotkey done = new ProfileHotkey(scancode, modifiers);
            reset();
            return done;
        }

        /** A key came up: a held modifier released with no second key binds itself. Null to keep waiting. */
        public ProfileHotkey release(int scancode) {
            int bit = modifierOf(scancode);
            if (bit == 0 || (modifiers & bit) == 0) return null;
            ProfileHotkey done = new ProfileHotkey(scancode, modifiers & ~bit);
            reset();
            return done;
        }
    }
}
