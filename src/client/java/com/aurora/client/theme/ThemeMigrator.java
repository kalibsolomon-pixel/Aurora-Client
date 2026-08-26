package com.aurora.client.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;

import java.util.Map;

/**
 * One-way upgrades of persisted theme data into the Stage 1 token format,
 * used by {@code AuroraConfig.load()} (the main config) and
 * {@code ProfileManager.applyProfile} (per-profile snapshots).
 *
 * <p>Handles three shapes of old/partial data without ever throwing:
 * <ul>
 *   <li><b>Legacy flat key</b> ({@code themePrimaryColor}) → folded into a
 *       structured {@code theme} object, so existing installs keep their
 *       accent. The legacy {@code themeSecondaryColor} is dropped — Stage 2
 *       derives neutrals from the accent instead of a second seed.</li>
 *   <li><b>Malformed new-format data</b> (wrong member types, theme not an
 *       object) → offending members are dropped and factory defaults take
 *       over, without poisoning the rest of the config.</li>
 *   <li><b>Garbage JSON entirely</b> → passed through untouched; the
 *       caller's GSON deserialization and its catch-all fallback handle
 *       it.</li>
 * </ul>
 *
 * <p>Kept free of Minecraft/Fabric dependencies so it is unit-testable in
 * isolation.
 */
public final class ThemeMigrator {

    private ThemeMigrator() {}

    /**
     * Pre-process the raw {@code aurora.json} text: validate/sanitize the
     * {@code theme} member, migrate legacy flat color keys into it, and
     * return the (possibly rewritten) JSON. Returns the input unchanged
     * when nothing needs fixing or the text isn't a JSON object.
     */
    public static String migrateConfigJson(String json, Logger log) {
        try {
            JsonElement rootEl = JsonParser.parseString(json);
            if (!rootEl.isJsonObject()) {
                return json; // let normal deserialization surface/fallback on it
            }
            JsonObject root = rootEl.getAsJsonObject();
            boolean dirty = false;

            // 1) Sanitize an existing "theme" member.
            JsonElement themeEl = root.get("theme");
            if (themeEl != null && !themeEl.isJsonNull()) {
                if (themeEl.isJsonObject()) {
                    dirty |= sanitizeThemeObject(themeEl.getAsJsonObject(), log);
                } else {
                    log.warn("Theme config: 'theme' is not an object; discarding it and falling back to defaults");
                    root.remove("theme");
                    dirty = true;
                }
            }

            // 2) Extract legacy flat keys (dropped from the file either way —
            //    they have no home on the new config class).
            JsonElement legacyPrimary = root.remove("themePrimaryColor");
            JsonElement legacySecondary = root.remove("themeSecondaryColor");
            if (legacyPrimary != null || legacySecondary != null) {
                dirty = true;
            }

            // 3) If no theme object exists, synthesize one from the legacy keys.
            if (!root.has("theme") && (legacyPrimary != null || legacySecondary != null)) {
                root.add("theme", themeFromLegacy(legacyPrimary, legacySecondary));
                log.info("Theme config: migrated legacy primary color to the token format "
                                + "(accent=#{})",
                        String.format("%08X", asInt(legacyPrimary, ThemeDefinition.DEFAULT_ACCENT)));
                dirty = true;
            }

            return dirty ? root.toString() : json;
        } catch (Throwable t) {
            // Corrupt beyond parsing — defer to the caller's fallback path.
            log.warn("Theme config: pre-pass skipped (unparseable JSON); falling back to normal handling");
            return json;
        }
    }

    /**
     * Fix up a profile snapshot's field map in place: fold legacy
     * {@code themePrimaryColor}/{@code themeSecondaryColor} entries into a
     * structured {@code theme} entry (only when no valid one exists) and
     * sanitize a malformed existing {@code theme} object. Never throws.
     */
    public static void migrateProfileFields(Map<String, JsonElement> fields, Logger log) {
        if (fields == null) return;
        try {
            JsonElement themeEl = fields.get("theme");
            boolean hasTheme = themeEl != null && themeEl.isJsonObject();

            JsonElement legacyPrimary = fields.remove("themePrimaryColor");
            JsonElement legacySecondary = fields.remove("themeSecondaryColor");

            if (hasTheme) {
                sanitizeThemeObject(themeEl.getAsJsonObject(), log);
                if (legacyPrimary != null || legacySecondary != null) {
                    log.info("Profile theme: legacy color keys present alongside a token-format theme; kept the theme object");
                }
                return;
            }
            if (legacyPrimary == null && legacySecondary == null) return;

            if (themeEl != null && !themeEl.isJsonNull()) {
                log.warn("Profile theme: 'theme' entry was not an object; rebuilding it from legacy colors");
            }
            fields.put("theme", themeFromLegacy(legacyPrimary, legacySecondary));
            log.info("Profile theme: migrated legacy primary color to the token format "
                            + "(accent=#{})",
                    String.format("%08X", asInt(legacyPrimary, ThemeDefinition.DEFAULT_ACCENT)));
        } catch (Throwable t) {
            log.warn("Profile theme: migration skipped (unexpected error); using defaults");
        }
    }

    /**
     * Validate the members of a theme object in place: {@code mode} must be
     * a known mode name and {@code accent} must be a number. Bad members are
     * removed so factory defaults fill the gap.
     *
     * @return whether anything was changed
     */
    private static boolean sanitizeThemeObject(JsonObject theme, Logger log) {
        boolean dirty = false;

        JsonElement mode = theme.get("mode");
        if (mode != null && !mode.isJsonNull()) {
            boolean valid = mode.isJsonPrimitive() && isKnownModeName(mode.getAsString());
            if (!valid) {
                log.warn("Theme config: unrecognized 'mode' value {}; resetting to DARK", mode);
                theme.remove("mode");
                dirty = true;
            }
        }

        dirty |= sanitizeIntMember(theme, "accent", log);
        return dirty;
    }

    private static boolean isKnownModeName(String name) {
        for (ThemeMode m : ThemeMode.values()) {
            if (m.name().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private static boolean sanitizeIntMember(JsonObject theme, String member, Logger log) {
        JsonElement el = theme.get(member);
        if (el == null || el.isJsonNull()) return false;
        if (tryAsInt(el) != null) return false; // valid number
        log.warn("Theme config: '{}' is not a number; resetting to default", member);
        theme.remove(member);
        return true;
    }

    private static JsonObject themeFromLegacy(JsonElement legacyPrimary, JsonElement legacySecondary) {
        JsonObject theme = new JsonObject();
        theme.addProperty("mode", ThemeMode.DARK.name());
        theme.addProperty("accent", asInt(legacyPrimary, ThemeDefinition.DEFAULT_ACCENT));
        return theme;
    }

    /** Best-effort int read of a JSON element; {@code fallback} if absent/garbage. */
    private static int asInt(JsonElement el, int fallback) {
        Integer v = el != null ? tryAsInt(el) : null;
        return v != null ? v : fallback;
    }

    private static Integer tryAsInt(JsonElement el) {
        try {
            if (el != null && el.isJsonPrimitive()) {
                return el.getAsInt();
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }
}
