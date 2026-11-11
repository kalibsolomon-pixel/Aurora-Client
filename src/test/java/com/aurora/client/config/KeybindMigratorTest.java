package com.aurora.client.config;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class KeybindMigratorTest {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(KeybindMigratorTest.class);

    @Test void legacyVanillaNamesKeepTheirMeaning() throws Exception {
        JsonObject legacy = JsonParser.parseString(Files.readString(
                Path.of("src/test/resources/config/legacy-keybinds-26.2.json"))).getAsJsonObject();
        for (var key : legacy.entrySet()) {
            int migrated = KeybindMigrator.fromGlfw(key.getValue().getAsInt());
            if (key.getKey().equals("key.keyboard.unknown") || key.getKey().equals("key.keyboard.f25")) {
                assertEquals(-1, migrated, key.getKey());
            } else {
                assertEquals(InputConstants.getKey(key.getKey()).getValue(), migrated, key.getKey());
            }
        }
    }

    @Test void everyPersistedKeyFieldIsCovered() {
        var actual = new HashSet<String>();
        for (var field : AuroraConfig.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType() == int.class && field.getName().endsWith("Key"))
                actual.add(field.getName());
        }
        assertEquals(actual, KeybindMigrator.KEY_FIELDS);
    }

    @Test void configAndImportedProfilesMigrateIndependentlyAndExactlyOnce() {
        String json = "{\"zoomKey\":67,\"freeLookKey\":86,\"hudEditorKey\":344,"
                + "\"hitregScoreSendKey\":-1,\"keystrokesExtraKeys\":[65,290,333],\"other\":23}";
        String first = KeybindMigrator.migrateConfigJson(json, LOG);
        JsonObject migrated = JsonParser.parseString(first).getAsJsonObject();
        assertEquals(InputConstants.KEY_C, migrated.get("zoomKey").getAsInt());
        assertEquals(InputConstants.KEY_V, migrated.get("freeLookKey").getAsInt());
        assertEquals(InputConstants.KEY_RSHIFT, migrated.get("hudEditorKey").getAsInt());
        assertEquals(-1, migrated.get("hitregScoreSendKey").getAsInt());
        assertEquals("[4,58,86]", migrated.get("keystrokesExtraKeys").toString());
        assertEquals(23, migrated.get("other").getAsInt());
        assertFalse(migrated.has("worldMapKey"), "Missing fields retain new defaults");
        assertEquals(first, KeybindMigrator.migrateConfigJson(first, LOG));
        var fields = JsonParser.parseString(json).getAsJsonObject().asMap();
        KeybindMigrator.migrateProfileFields(fields, LOG);
        assertEquals(migrated.asMap(), fields);
        KeybindMigrator.migrateProfileFields(fields, LOG);
        assertEquals(migrated.asMap(), fields);
        assertTrue(com.aurora.client.config.profile.ProfileFieldSet.isProfileScoped("keybindVersion"));
    }

    @Test void invalidLegacyCodesCannotBecomeUnrelatedKeys() {
        String first = KeybindMigrator.migrateConfigJson(
                "{\"zoomKey\":314,\"freeLookKey\":999,\"hudEditorKey\":1.5,\"toggleSprintKey\":null}", LOG);
        JsonObject obj = JsonParser.parseString(first).getAsJsonObject();
        for (String key : new String[]{"zoomKey", "freeLookKey", "hudEditorKey", "toggleSprintKey"})
            assertEquals(-1, obj.get(key).getAsInt(), key);
        assertEquals("broken", KeybindMigrator.migrateConfigJson("broken", LOG));
        assertEquals("[]", KeybindMigrator.migrateConfigJson("[]", LOG));
    }
}
