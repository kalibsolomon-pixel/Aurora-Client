package com.aurora.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FireOverlayMigratorTest {
    private static final Logger LOG = LoggerFactory.getLogger("test");

    private static JsonObject migrated(String json) {
        return JsonParser.parseString(FireOverlayMigrator.migrateConfigJson(json, LOG)).getAsJsonObject();
    }

    @Test void fireHiddenThroughLavaClarityStaysHidden() {
        JsonObject o = migrated("{\"lavaClarityEnabled\": true, \"lavaHideOverlay\": true}");
        assertEquals(new JsonPrimitive(true), o.get("fireOverlayHide"));
        assertFalse(o.has("lavaHideOverlay"));
    }

    @Test void theRetiredSwitchDidNothingWithoutLavaClarity() {
        JsonObject o = migrated("{\"lavaClarityEnabled\": false, \"lavaHideOverlay\": true, \"fireOverlayHide\": false}");
        assertEquals(new JsonPrimitive(false), o.get("fireOverlayHide"));
        assertFalse(o.has("lavaHideOverlay"));
    }

    @Test void configsWithoutTheRetiredKeyAreLeftAlone() {
        String json = "{\"lavaClarityEnabled\": true}";
        assertSame(json, FireOverlayMigrator.migrateConfigJson(json, LOG));
        String corrupt = "{not json";
        assertSame(corrupt, FireOverlayMigrator.migrateConfigJson(corrupt, LOG));
    }

    @Test void profilesAreCarriedOverToo() {
        Map<String, JsonElement> fields = new HashMap<>();
        fields.put("lavaClarityEnabled", new JsonPrimitive(true));
        fields.put("lavaHideOverlay", new JsonPrimitive(true));
        FireOverlayMigrator.migrateProfileFields(fields, LOG);
        assertEquals(new JsonPrimitive(true), fields.get("fireOverlayHide"));
        assertFalse(fields.containsKey("lavaHideOverlay"));

        Map<String, JsonElement> off = new HashMap<>();
        off.put("lavaHideOverlay", new JsonPrimitive(true));
        FireOverlayMigrator.migrateProfileFields(off, LOG);
        assertFalse(off.containsKey("fireOverlayHide"));
        FireOverlayMigrator.migrateProfileFields(null, LOG);
    }
}
