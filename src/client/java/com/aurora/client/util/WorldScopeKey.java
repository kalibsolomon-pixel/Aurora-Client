package com.aurora.client.util;

import java.util.Locale;
import java.util.regex.Pattern;

/** One bounded cached scope key; no world, server or client objects are retained. */
final class WorldScopeKey {
    private static final Pattern UNSAFE = Pattern.compile("[\\\\/:*?\"<>|]");
    // Immutable publication keeps simultaneous readers from mixing source and key.
    private volatile Entry last;

    String resolve(String prefix, String source, Locale locale) {
        Entry cached = last;
        if (cached != null && cached.prefix.equals(prefix)
                && cached.source.equals(source) && cached.locale.equals(locale)) {
            return cached.key;
        }
        // Keep the original locale-sensitive lower-case / replacement / trim order:
        // these strings already identify persisted playtime, waypoints and map data.
        String key = prefix + UNSAFE.matcher(source.toLowerCase(locale)).replaceAll("_").trim();
        last = new Entry(prefix, source, locale, key);
        return key;
    }

    private record Entry(String prefix, String source, Locale locale, String key) {}
}
