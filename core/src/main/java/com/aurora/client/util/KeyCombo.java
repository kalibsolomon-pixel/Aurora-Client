package com.aurora.client.util;

/**
 * Keybind combos (owner request 2026-11-12): any binding — Aurora's own and
 * Minecraft's — may carry Ctrl, Shift and Alt.
 *
 * <p><b>Encoding.</b> A binding is one int: the key's stored code (SDL
 * physical scancode, {@link AuroraKey}) in the low 16 bits and the modifiers
 * in bits 16–18. {@code -1} is unbound, and every plain key saved before
 * combos is simply a binding with no modifiers, so no migration is needed.
 *
 * <p><b>Resolution — most specific wins.</b> A binding applies when its key
 * is down, all of its modifiers are held, and no other live binding on the
 * same key has a strictly larger modifier set that is also held. So C (zoom)
 * keeps working while Ctrl is held for sprint, unless something is bound to
 * Ctrl + C. A modifier bound on its own ignores its own bit.
 *
 * <p>Fn is not a modifier: on most keyboards it never reaches the OS.
 */
public final class KeyCombo {
    public static final int CTRL = 1, SHIFT = 2, ALT = 4;
    public static final int MODIFIER_MASK = CTRL | SHIFT | ALT;
    public static final int UNBOUND = -1;
    private static final int SHIFT_BITS = 16, KEY_MASK = 0xFFFF;

    // SDL / USB HID scancodes of the modifier keys.
    static final int LCTRL = 224, LSHIFT = 225, LALT = 226, RCTRL = 228, RSHIFT = 229, RALT = 230;
    /** Every modifier key (both sides), for polling which modifiers are held. */
    public static final int[] MODIFIER_KEYS = {LCTRL, LSHIFT, LALT, RCTRL, RSHIFT, RALT};

    private KeyCombo() {}

    public static int of(int key, int modifiers) {
        return key < 0 ? UNBOUND : (key & KEY_MASK) | (modifiers & MODIFIER_MASK) << SHIFT_BITS;
    }

    /** The key's stored code, or -1. */
    public static int key(int binding) {
        return binding < 0 ? UNBOUND : binding & KEY_MASK;
    }

    public static int modifiers(int binding) {
        return binding < 0 ? 0 : binding >>> SHIFT_BITS & MODIFIER_MASK;
    }

    /** The modifier bit a stored key is (either side), or 0 for an ordinary key. */
    public static int modifierOf(int scancode) {
        return switch (scancode) {
            case LCTRL, RCTRL -> CTRL;
            case LSHIFT, RSHIFT -> SHIFT;
            case LALT, RALT -> ALT;
            default -> 0;
        };
    }

    /** "Ctrl + Shift + " for the modifiers, in a fixed order; empty for none. */
    public static String prefix(int modifiers) {
        StringBuilder sb = new StringBuilder();
        if ((modifiers & CTRL) != 0) sb.append("Ctrl + ");
        if ((modifiers & SHIFT) != 0) sb.append("Shift + ");
        if ((modifiers & ALT) != 0) sb.append("Alt + ");
        return sb.toString();
    }

    /** The modifiers a binding needs from {@code held}: its own, minus a bound modifier's own bit. */
    private static int required(int binding) {
        return modifiers(binding) & ~modifierOf(key(binding));
    }

    private static int effectiveHeld(int binding, int held) {
        return held & ~modifierOf(key(binding));
    }

    /**
     * Whether {@code binding} wins when its key is down with {@code held}
     * modifiers, among the {@code count} live bindings in {@code live}: its
     * modifiers are all held, and no binding on the same key needs a strictly
     * larger set that is also held.
     */
    public static boolean resolves(int binding, int held, int[] live, int count) {
        if (binding < 0) return false;
        int need = required(binding);
        int have = effectiveHeld(binding, held);
        if ((have & need) != need) return false;
        int key = key(binding);
        for (int i = 0; i < count; i++) {
            int other = live[i];
            if (other < 0 || key(other) != key) continue;
            int otherNeed = required(other);
            if (otherNeed != need && (otherNeed & need) == need && (have & otherNeed) == otherNeed) return false;
        }
        return true;
    }

    /** {@link #resolves} against the global {@link Registry}. */
    public static boolean resolves(int binding, int held) {
        return resolves(binding, held, Registry.live, Registry.count);
    }

    /**
     * The modifiers used by the combos on {@code key} that win with {@code held}
     * (0 when only a plain binding wins, or nothing does): pressing the key
     * completes those combos, so their modifiers did not start a binding of their own.
     */
    public static int comboBits(int key, int held, int[] live, int count) {
        int bits = 0;
        for (int i = 0; i < count; i++) {
            int b = live[i];
            if (b < 0 || key(b) != key) continue;
            int need = required(b);
            if (need != 0 && resolves(b, held, live, count)) bits |= need;
        }
        return bits;
    }

    /** {@link #comboBits} against the global {@link Registry}. */
    public static int comboBits(int key, int held) {
        return comboBits(key, held, Registry.live, Registry.count);
    }

    /**
     * Every live binding (Aurora's and Minecraft's, plus profile hotkeys), so
     * a plain key can tell when a combo on the same key outranks it. Rebuilt
     * once per client tick by the adapter into a reused array.
     */
    public static final class Registry {
        private static int[] live = new int[128];
        private static int count;
        private static int[] next = new int[128];
        private static int nextCount;
        private static boolean combos, nextCombos;
        private static int prefixes, nextPrefixes;

        private Registry() {}

        public static void begin() { nextCount = 0; nextCombos = false; nextPrefixes = 0; }

        public static void add(int binding) {
            if (binding < 0) return;
            if (modifiers(binding) != 0) nextCombos = true;
            nextPrefixes |= required(binding);
            if (nextCount == next.length) next = java.util.Arrays.copyOf(next, next.length * 2);
            next[nextCount++] = binding;
        }

        /** Publishes the bindings added since {@link #begin}. */
        public static void commit() {
            int[] swap = live;
            live = next;
            count = nextCount;
            combos = nextCombos;
            prefixes = nextPrefixes;
            next = swap.length >= live.length ? swap : new int[live.length];
        }

        public static int count() { return count; }

        /** Whether any live binding has modifiers; with none, every key behaves exactly as vanilla's. */
        public static boolean hasCombos() { return combos; }

        /** The modifiers some live combo needs: pressing one may be the start of a combo. */
        public static int prefixes() { return prefixes; }
    }

    /**
     * Holds back a modifier key's own bindings while it may be starting a combo
     * (owner request 2026-11-22). With Sprint on Ctrl and Next Track on Ctrl +
     * Right, pressing Ctrl on the way to Right must not sprint. So a modifier
     * that some live combo uses does nothing on press:
     * <ul>
     *   <li>released with no combo completed, it taps: its bindings get one
     *       press, one tick long;</li>
     *   <li>held past {@link #HOLD_MS} with no combo, it becomes a normal held
     *       key for hold bindings (hold-to-sneak on Shift still works);
     *       one-shot and toggle bindings never fire from a hold, only from the tap;</li>
     *   <li>once a combo used it, its own bindings stay quiet until it is let go.</li>
     * </ul>
     * A modifier no combo uses behaves as before. Main thread only: key events
     * and client ticks both run there.
     */
    public static final class Deferral {
        /** How long a modifier is held, with no combo key, before it counts as held. */
        public static final long HOLD_MS = 200L;

        private static boolean enabled;
        private static final long[] since = new long[MODIFIER_KEYS.length]; // 0 = not held
        private static final boolean[] used = new boolean[MODIFIER_KEYS.length];
        private static final boolean[] holding = new boolean[MODIFIER_KEYS.length];
        private static final boolean[] tapped = new boolean[MODIFIER_KEYS.length];

        private Deferral() {}

        /** Off unless the adapter can feed key events (its mixin applied); off clears everything. */
        public static void setEnabled(boolean on) {
            if (enabled && !on) reset();
            enabled = on;
        }

        public static void reset() {
            java.util.Arrays.fill(since, 0L);
            java.util.Arrays.fill(used, false);
            java.util.Arrays.fill(holding, false);
            java.util.Arrays.fill(tapped, false);
        }

        private static int slot(int scancode) {
            for (int i = 0; i < MODIFIER_KEYS.length; i++) if (MODIFIER_KEYS[i] == scancode) return i;
            return -1;
        }

        /** Whether this key's bindings wait: it is a modifier that some live combo uses. */
        public static boolean defers(int scancode) {
            int bit = modifierOf(scancode);
            return enabled && bit != 0 && (Registry.prefixes() & bit) != 0;
        }

        /**
         * A key went down. Returns true while its own bindings must wait (a deferred
         * modifier not yet held long enough); repeats keep the first press's time.
         */
        public static boolean press(int scancode, long nowMs) {
            if (!defers(scancode)) return false;
            int s = slot(scancode);
            if (since[s] == 0L) {
                since[s] = Math.max(1L, nowMs);
                used[s] = false;
                holding[s] = false;
            }
            return !holding[s];
        }

        /** Whether the key is down and still waiting (neither tapped nor held yet). */
        public static boolean waiting(int scancode) {
            int s = slot(scancode);
            return s >= 0 && since[s] != 0L && !holding[s];
        }

        /** A combo using {@code bits} completed: those modifiers started no binding of their own. */
        public static void consume(int bits) {
            if (bits == 0) return;
            for (int s = 0; s < MODIFIER_KEYS.length; s++) {
                if (since[s] != 0L && (modifierOf(MODIFIER_KEYS[s]) & bits) != 0) used[s] = true;
            }
        }

        /**
         * A key came up. True when it taps: a deferred modifier, released in game
         * with no combo and before it counted as held.
         */
        public static boolean release(int scancode, boolean inGame) {
            int s = slot(scancode);
            if (s < 0 || since[s] == 0L) return false;
            boolean tap = inGame && !used[s] && !holding[s];
            since[s] = 0L;
            used[s] = false;
            holding[s] = false;
            if (tap) tapped[s] = true;
            return tap;
        }

        /**
         * Marks modifiers held past {@link #HOLD_MS} with no combo as held. Returns the
         * newly held ones as bits over {@link KeyCombo#MODIFIER_KEYS} indices.
         */
        public static int promoteDue(long nowMs) {
            int promoted = 0;
            for (int s = 0; s < MODIFIER_KEYS.length; s++) {
                if (since[s] != 0L && !used[s] && !holding[s] && nowMs - since[s] >= HOLD_MS) {
                    holding[s] = true;
                    promoted |= 1 << s;
                }
            }
            return promoted;
        }

        /** Whether a deferred modifier now counts as held (for hold bindings). */
        public static boolean held(int scancode) {
            int s = slot(scancode);
            return s >= 0 && holding[s];
        }

        /** Whether the key tapped since the last {@link #endTick}. */
        public static boolean tapped(int scancode) {
            int s = slot(scancode);
            return s >= 0 && tapped[s];
        }

        /** Ends the tick a tap was visible in. */
        public static void endTick() {
            java.util.Arrays.fill(tapped, false);
        }

        /** A screen took the keyboard: modifiers held now tap nothing when let go. */
        public static void cancelHeld() {
            for (int s = 0; s < MODIFIER_KEYS.length; s++) if (since[s] != 0L) used[s] = true;
        }
    }

    /**
     * Capture state for a binding field: a modifier pressed alone waits for a
     * second key; another key completes the binding with the modifiers held;
     * releasing a modifier before that binds the modifier itself.
     */
    public static final class Capture {
        private int modifiers;

        /** Modifiers held so far, for the "Ctrl + …" label. */
        public int pending() { return modifiers; }

        public void reset() { modifiers = 0; }

        /**
         * A key went down (stored code). {@code clear} marks Esc and
         * Backspace. Returns the finished binding ({@link #UNBOUND} to clear),
         * or null to keep waiting.
         */
        public Integer press(int scancode, boolean clear) {
            if (clear) { reset(); return UNBOUND; }
            int bit = modifierOf(scancode);
            if (bit != 0) { modifiers |= bit; return null; }
            int done = of(scancode, modifiers);
            reset();
            return done;
        }

        /** A key came up: a held modifier released with no second key binds itself. Null to keep waiting. */
        public Integer release(int scancode) {
            int bit = modifierOf(scancode);
            if (bit == 0 || (modifiers & bit) == 0) return null;
            int done = of(scancode, modifiers & ~bit);
            reset();
            return done;
        }
    }

    /** "Ctrl + …" while a capture waits, else "Press a key…". */
    public static String captureLabel(int pending, String idle) {
        return pending == 0 ? idle : prefix(pending) + "…";
    }

    /**
     * A vanilla binding in a profile's controls map: the saved key string,
     * plus {@code "|<modifiers>"} when it has any. Older strings carry no
     * suffix and read as plain keys.
     */
    public static String controlsString(String saved, int modifiers) {
        return (modifiers & MODIFIER_MASK) == 0 ? saved : saved + "|" + (modifiers & MODIFIER_MASK);
    }

    /** The saved key string of a {@link #controlsString}. */
    public static String controlsKey(String value) {
        int bar = value.indexOf('|');
        return bar < 0 ? value : value.substring(0, bar);
    }

    /** The modifiers of a {@link #controlsString}; 0 when absent or malformed. */
    public static int controlsModifiers(String value) {
        int bar = value.indexOf('|');
        if (bar < 0) return 0;
        try {
            return Integer.parseInt(value.substring(bar + 1)) & MODIFIER_MASK;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
