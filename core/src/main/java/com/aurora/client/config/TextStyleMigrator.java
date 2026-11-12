package com.aurora.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Text options (2026-11-12): "Inter Italic" stops being a font of its own
 * and becomes Inter with the Italic modifier, now that every family has its
 * own italic and weights. Runs on {@code aurora.json} and on every profile
 * applied, like {@link com.aurora.client.theme.ThemeMigrator}. Idempotent:
 * a config already on Inter is left alone.
 */
public final class TextStyleMigrator {
    private TextStyleMigrator() {}

    static final String LEGACY_ITALIC = "INTER_ITALIC";

    /** Rewrites a config file's JSON; returns it unchanged when there is nothing to migrate or it does not parse. */
    public static String migrateConfigJson(String json, Logger log) {
        if (json == null || !json.contains(LEGACY_ITALIC)) return json;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return json;
            JsonObject obj = root.getAsJsonObject();
            if (!migrate(obj.get("activeFont"), obj::add)) return json;
            log.info("[TextStyle] Inter Italic is now Inter with Italic on (aurora.json).");
            return obj.toString();
        } catch (RuntimeException e) {
            return json;
        }
    }

    /** Rewrites a profile's field map in place. */
    public static void migrateProfileFields(Map<String, JsonElement> fields, Logger log) {
        if (fields == null) return;
        if (migrate(fields.get("activeFont"), fields::put)) {
            log.info("[TextStyle] Inter Italic is now Inter with Italic on (profile).");
        }
    }

    private static boolean migrate(JsonElement font, java.util.function.BiConsumer<String, JsonElement> put) {
        if (font == null || !font.isJsonPrimitive() || !LEGACY_ITALIC.equals(font.getAsString())) return false;
        put.accept("activeFont", new JsonPrimitive("INTER"));
        put.accept("textItalic", new JsonPrimitive(true));
        return true;
    }
}
