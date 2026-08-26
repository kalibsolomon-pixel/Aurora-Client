package com.aurora.client.theme;

import java.util.List;

/**
 * The named preset accents a user can pick from, modeled on COSMIC's
 * default set. A preset is just a curated RGB constant — the theme engine
 * treats it identically to a custom accent; there is no separate "preset
 * id" stored in the config (an accent either happens to equal a preset or
 * is custom).
 *
 * <p>Values are picked for good perceptual separation and reasonable
 * saturation — deliberately not pure primaries, so derived palettes stay
 * pleasant rather than garish. {@link #RED} is the factory default and
 * matches the Stage 1 "OnePlus Red" so existing users see no jarring
 * change on upgrade.
 */
public final class ThemePresets {

    private ThemePresets() {}

    public static final int BLUE   = 0xFF3B82F6;
    public static final int INDIGO = 0xFF5C6BC0;
    public static final int PURPLE = 0xFF8E44AD;
    public static final int PINK   = 0xFFE91E63;
    public static final int RED    = 0xFFEB0029;
    public static final int ORANGE = 0xFFFB8C00;
    public static final int YELLOW = 0xFFFBC02D;
    public static final int GREEN  = 0xFF43A047;
    public static final int TEAL   = 0xFF00897B;

    /** All presets, in display order. */
    public static final List<Entry> ALL = List.of(
            new Entry("Blue",   BLUE),
            new Entry("Indigo", INDIGO),
            new Entry("Purple", PURPLE),
            new Entry("Pink",   PINK),
            new Entry("Red",    RED),
            new Entry("Orange", ORANGE),
            new Entry("Yellow", YELLOW),
            new Entry("Green",  GREEN),
            new Entry("Teal",   TEAL));

    /**
     * A named preset accent. Exposed as a small value type so the debug
     * screen (and, later, the Stage 3 settings UI) can iterate names and
     * values together.
     */
    public static final class Entry {
        public final String name;
        public final int argb;

        public Entry(String name, int argb) {
            this.name = name;
            this.argb = argb;
        }
    }

    /** Case-insensitive lookup of a preset by its display name, or {@code null}. */
    public static Entry byName(String name) {
        if (name == null) return null;
        for (Entry e : ALL) {
            if (e.name.equalsIgnoreCase(name)) return e;
        }
        return null;
    }

    /**
     * The preset whose RGB matches {@code argb} (comparing RGB only, alpha
     * ignored), or {@code null} when {@code argb} is a custom accent.
     */
    public static Entry matching(int argb) {
        int rgb = argb & 0x00FFFFFF;
        for (Entry e : ALL) {
            if ((e.argb & 0x00FFFFFF) == rgb) return e;
        }
        return null;
    }
}
