package com.aurora.client.util;

import org.junit.jupiter.api.Test;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class WorldScopeKeyTest {
    @Test
    void persistedKeysRemainByteCompatibleAcrossNamesAndLocales() {
        var keys = new WorldScopeKey();
        for (var locale : new Locale[]{Locale.ROOT, Locale.US, Locale.forLanguageTag("tr-TR")}) {
            for (var name : new String[]{"New World", "  HOST.Example:25565 ", "a\\b/c:*?\"<>|",
                    "Iİıi", "ΣΟΣ", "Emoji 🌎", "", " ", "A\nB", "\u0130"}) {
                for (var prefix : new String[]{"sp:", "mp:"}) {
                    String legacy = prefix + name.toLowerCase(locale)
                            .replaceAll("[\\\\/:*?\"<>|]", "_").trim();
                    assertEquals(legacy, keys.resolve(prefix, name, locale));
                }
            }
        }
    }

    @Test
    void stableValuesReuseKeyButRenamesNamespacesAndLocaleRefreshIt() {
        var keys = new WorldScopeKey();
        String initial = keys.resolve("sp:", "FIRST", Locale.US);
        assertSame(initial, keys.resolve(new String("sp:"), new String("FIRST"), Locale.US));
        assertEquals("sp:second", keys.resolve("sp:", "SECOND", Locale.US));
        assertEquals("mp:second", keys.resolve("mp:", "SECOND", Locale.US));
        assertEquals("mp:i", keys.resolve("mp:", "I", Locale.US));
        assertEquals("mp:ı", keys.resolve("mp:", "I", Locale.forLanguageTag("tr-TR")));
        assertEquals(initial, keys.resolve("sp:", "FIRST", Locale.US));
    }
}
