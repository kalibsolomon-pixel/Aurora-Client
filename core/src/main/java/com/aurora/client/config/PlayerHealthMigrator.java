package com.aurora.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Carries Entity Health's old behaviour into the {@code playerHealthShowPlayers}
 * switch (2026-11-11, ADR 0007).
 *
 * <p>Before the switch existed, turning Entity Health on showed players and mobs
 * together. A config or profile saved before then has no
 * {@code playerHealthShowPlayers} key, and Gson would give it the new default,
 * {@code false}, silently hiding players from someone who had them. So a missing
 * key takes the saved {@code playerHealthIndicators} value: whoever had Entity
 * Health on keeps seeing players, and everyone else gets the fair-play default.
 * Runs on {@code aurora.json} and on every profile applied. Never throws.
 */
public final class PlayerHealthMigrator {
    static final String SHOW_PLAYERS = "playerHealthShowPlayers";
    static final String ENABLED = "playerHealthIndicators";

    private PlayerHealthMigrator() {}

    /** The (possibly rewritten) {@code aurora.json} text; the input when nothing changes. */
    public static String migrateConfigJson(String json, Logger log) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return json;
            JsonObject obj = root.getAsJsonObject();
            JsonElement migrated = migrated(obj.has(SHOW_PLAYERS), obj.get(ENABLED));
            if (migrated == null) return json;
            obj.add(SHOW_PLAYERS, migrated);
            log.info("Entity Health: config predates the players switch; set it to {}", migrated);
            return obj.toString();
        } catch (Throwable t) {
            return json; // the caller's own parse reports a corrupt file
        }
    }

    /** Fixes up a profile snapshot's field map in place. */
    public static void migrateProfileFields(Map<String, JsonElement> fields, Logger log) {
        if (fields == null) return;
        try {
            JsonElement migrated = migrated(fields.containsKey(SHOW_PLAYERS), fields.get(ENABLED));
            if (migrated == null) return;
            fields.put(SHOW_PLAYERS, migrated);
            log.info("Entity Health: profile predates the players switch; set it to {}", migrated);
        } catch (Throwable t) {
            log.warn("Entity Health: profile migration skipped", t);
        }
    }

    /** The value to add, or null when the key is present or there is nothing to carry over. */
    private static JsonElement migrated(boolean hasSwitch, JsonElement enabled) {
        if (hasSwitch || enabled == null || !enabled.isJsonPrimitive()) return null;
        JsonPrimitive p = enabled.getAsJsonPrimitive();
        return p.isBoolean() ? new JsonPrimitive(p.getAsBoolean()) : null;
    }
}
