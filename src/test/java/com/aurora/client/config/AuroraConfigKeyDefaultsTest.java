package com.aurora.client.config;

import org.junit.jupiter.api.Test;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The core cannot see LWJGL, so AuroraConfig spells its default keys as literals.
 * Read from the source because the release-defaults resource overlays a constructed
 * config, which would hide a wrong initializer.
 */
class AuroraConfigKeyDefaultsTest {
    private static final Path SOURCE = Path.of("core/src/main/java/com/aurora/client/config/AuroraConfig.java");

    @Test void literalKeyDefaultsAreTheSdlScancodesTheyName() throws Exception {
        String src = Files.readString(SOURCE);
        assertInitializer(src, "zoomKey", com.aurora.client.TestKeyboard.stored(InputConstants.KEY_C));
        assertInitializer(src, "freeLookKey", com.aurora.client.TestKeyboard.stored(InputConstants.KEY_V));
        assertInitializer(src, "toggleSprintKey", com.aurora.client.TestKeyboard.stored(InputConstants.KEY_J));
        assertInitializer(src, "toggleSneakKey", com.aurora.client.TestKeyboard.stored(InputConstants.KEY_K));
        assertInitializer(src, "hudEditorKey", com.aurora.client.TestKeyboard.stored(InputConstants.KEY_RSHIFT));
        assertInitializer(src, "worldMapKey", com.aurora.client.TestKeyboard.stored(InputConstants.KEY_M));
        for (String field : KeybindMigrator.KEY_FIELDS) {
            if (!java.util.Set.of("zoomKey", "freeLookKey", "toggleSprintKey", "toggleSneakKey", "hudEditorKey", "worldMapKey").contains(field))
                assertInitializer(src, field, -1);
        }
        try (var in = getClass().getResourceAsStream("/assets/aurora/defaults/3.2.0.json")) {
            var defaults = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
            assertEquals(KeybindMigrator.VERSION, defaults.get("keybindVersion").getAsInt());
            for (String field : KeybindMigrator.KEY_FIELDS) if (defaults.has(field))
                assertNotEquals(0, defaults.get(field).getAsInt(), field + " must use Aurora's -1 unbound sentinel");
        }
    }

    private static void assertInitializer(String src, String field, int expected) {
        Matcher m = Pattern.compile("public int " + field + "\\s*=\\s*(-?\\d+);").matcher(src);
        assertTrue(m.find(), field + " has no literal initializer");
        assertEquals(expected, Integer.parseInt(m.group(1)), field);
    }
}
