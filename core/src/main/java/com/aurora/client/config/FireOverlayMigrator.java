package com.aurora.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Carries Pack Tweaks' retired "Hide 1st Person Fire (Lava Clarity)" switch
 * ({@code lavaHideOverlay}, removed 2026-11-22) into Hide Fire Overlay
 * ({@code fireOverlayHide}), which does the same thing without needing Lava
 * Clarity. Whoever had the fire hidden through Lava Clarity keeps it hidden;
 * everyone else is unchanged. Runs on {@code aurora.json} and on every profile
 * applied. Never throws.
 */
public final class FireOverlayMigrator {
    static final String RETIRED = "lavaHideOverlay";
    static final String LAVA_CLARITY = "lavaClarityEnabled";
    static final String HIDE_FIRE = "fireOverlayHide";

    private FireOverlayMigrator() {}

    /** The (possibly rewritten) {@code aurora.json} text; the input when nothing changes. */
    public static String migrateConfigJson(String json, Logger log) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return json;
            JsonObject obj = root.getAsJsonObject();
            if (!obj.has(RETIRED)) return json;
            boolean hide = hidFire(obj.get(RETIRED), obj.get(LAVA_CLARITY));
            obj.remove(RETIRED);
            if (hide && !isTrue(obj.get(HIDE_FIRE))) {
                obj.addProperty(HIDE_FIRE, true);
                log.info("Pack Tweaks: Lava Clarity's fire switch is retired; Hide Fire Overlay turned on");
            }
            return obj.toString();
        } catch (Throwable t) {
            return json; // the caller's own parse reports a corrupt file
        }
    }

    /** Fixes up a profile snapshot's field map in place. */
    public static void migrateProfileFields(Map<String, JsonElement> fields, Logger log) {
        if (fields == null || !fields.containsKey(RETIRED)) return;
        try {
            if (hidFire(fields.get(RETIRED), fields.get(LAVA_CLARITY)) && !isTrue(fields.get(HIDE_FIRE))) {
                fields.put(HIDE_FIRE, new JsonPrimitive(true));
                log.info("Pack Tweaks: profile used Lava Clarity's retired fire switch; Hide Fire Overlay turned on");
            }
            fields.remove(RETIRED);
        } catch (Throwable t) {
            log.warn("Pack Tweaks: fire overlay profile migration skipped", t);
        }
    }

    /** The retired switch only hid the fire while Lava Clarity was on. */
    private static boolean hidFire(JsonElement retired, JsonElement lavaClarity) {
        return isTrue(retired) && isTrue(lavaClarity);
    }

    private static boolean isTrue(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }
}
