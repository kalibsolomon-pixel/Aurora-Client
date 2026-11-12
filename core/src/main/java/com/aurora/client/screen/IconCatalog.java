package com.aurora.client.screen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every Material Symbols Rounded icon Aurora bundles, by name — the
 * catalogue behind the icon browser (profile icons, 2026-11-12). Backed by
 * Google's {@code .codepoints} list shipped beside the font
 * ({@code assets/aurora/icons/material_symbols.codepoints}, one
 * {@code name hex} pair per line). Loaded lazily on first use; searches are
 * memoized per query, so a browser re-asking every frame costs nothing.
 *
 * <p>No Minecraft imports (ADR 0003): the resource is read from the
 * classpath.
 */
public final class IconCatalog {
    private IconCatalog() {}

    private static final String RESOURCE = "/assets/aurora/icons/material_symbols.codepoints";

    /** One icon: its Material name (e.g. {@code sports_esports}) and the glyph string to draw. */
    public record Icon(String name, String glyph) {
        /** The name as words, for display ({@code sports_esports} → {@code sports esports}). */
        public String label() { return name.replace('_', ' '); }
    }

    private static List<Icon> icons;
    private static Map<String, Icon> byName;
    private static String lastQuery;
    private static List<Icon> lastResult = List.of();

    /** Every icon, in the list's (alphabetical) order. Empty if the list is missing. */
    public static synchronized List<Icon> all() {
        if (icons == null) load();
        return icons;
    }

    /** The glyph for {@code name}, or {@code ""} when the name is unknown or blank. */
    public static synchronized String glyph(String name) {
        if (name == null || name.isEmpty()) return "";
        if (byName == null) load();
        Icon icon = byName.get(name);
        return icon == null ? "" : icon.glyph();
    }

    /**
     * Icons whose name contains every word of {@code query} (case-insensitive;
     * underscores and spaces are equivalent). A blank query returns every
     * icon. The last query's result is reused.
     */
    public static synchronized List<Icon> search(String query) {
        String clean = query == null ? "" : query.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (clean.equals(lastQuery)) return lastResult;
        List<Icon> source = all();
        List<Icon> out;
        if (clean.isEmpty()) {
            out = source;
        } else {
            String[] words = clean.split("\\s+");
            out = new ArrayList<>();
            for (Icon icon : source) {
                String label = icon.label();
                boolean all = true;
                for (String w : words) {
                    if (!label.contains(w)) { all = false; break; }
                }
                if (all) out.add(icon);
            }
            out = Collections.unmodifiableList(out);
        }
        lastQuery = clean;
        lastResult = out;
        return out;
    }

    private static void load() {
        List<Icon> list = new ArrayList<>();
        Map<String, Icon> map = new HashMap<>();
        try (InputStream in = IconCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length != 2) continue;
                    int codepoint;
                    try {
                        codepoint = Integer.parseInt(parts[1], 16);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    Icon icon = new Icon(parts[0], new String(Character.toChars(codepoint)));
                    list.add(icon);
                    map.put(icon.name(), icon);
                }
            }
        } catch (IOException ignored) {
            // A missing or unreadable list leaves the catalogue empty; callers fall back.
        }
        icons = Collections.unmodifiableList(list);
        byName = map;
    }
}
