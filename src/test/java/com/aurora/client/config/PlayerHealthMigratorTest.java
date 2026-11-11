package com.aurora.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlayerHealthMigratorTest {
    private static final Logger LOG = LoggerFactory.getLogger("test");

    private static JsonElement showPlayers(String json) {
        return JsonParser.parseString(PlayerHealthMigrator.migrateConfigJson(json, LOG))
                .getAsJsonObject().get("playerHealthShowPlayers");
    }

    @Test void aConfigFromBeforeTheSwitchKeepsWhatEntityHealthShowed() {
        assertEquals(new JsonPrimitive(true), showPlayers("{\"playerHealthIndicators\": true}"));
        assertEquals(new JsonPrimitive(false), showPlayers("{\"playerHealthIndicators\": false}"));
    }

    @Test void aSavedSwitchOrAMissingFeatureIsLeftAlone() {
        String saved = "{\"playerHealthIndicators\": true, \"playerHealthShowPlayers\": false}";
        assertSame(saved, PlayerHealthMigrator.migrateConfigJson(saved, LOG));
        String unrelated = "{\"zoomEnabled\": true}";
        assertSame(unrelated, PlayerHealthMigrator.migrateConfigJson(unrelated, LOG));
        String corrupt = "{not json";
        assertSame(corrupt, PlayerHealthMigrator.migrateConfigJson(corrupt, LOG));
    }

    @Test void profilesFromBeforeTheSwitchAreCarriedOverToo() {
        Map<String, JsonElement> fields = new HashMap<>();
        fields.put("playerHealthIndicators", new JsonPrimitive(true));
        PlayerHealthMigrator.migrateProfileFields(fields, LOG);
        assertEquals(new JsonPrimitive(true), fields.get("playerHealthShowPlayers"));

        fields.put("playerHealthShowPlayers", new JsonPrimitive(false));
        PlayerHealthMigrator.migrateProfileFields(fields, LOG);
        assertEquals(new JsonPrimitive(false), fields.get("playerHealthShowPlayers"), "a saved switch wins");
    }

    @Test void loadingAnOldConfigFileRunsTheMigration(@TempDir Path dir) throws Exception {
        ConfigDirectory.set(dir);
        Files.writeString(dir.resolve("aurora.json"), "{\"playerHealthIndicators\": true}");
        AuroraConfig.load();
        assertTrue(AuroraConfig.get().playerHealthIndicators);
        assertTrue(AuroraConfig.get().playerHealthShowPlayers);
        assertFalse(new AuroraConfig().playerHealthShowPlayers, "a fresh install has it off");
    }
}
