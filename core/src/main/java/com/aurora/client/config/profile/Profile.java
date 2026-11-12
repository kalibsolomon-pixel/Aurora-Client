package com.aurora.client.config.profile;

import com.google.gson.JsonElement;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A named, persistable snapshot of a curated subset of {@link
 * com.aurora.client.config.AuroraConfig} fields.
 *
 * <p>Each profile is stored as its own JSON file under the profiles
 * directory (see {@link ProfileManager}). The {@link #fields} map holds
 * one entry per captured setting, keyed by the config field name, with
 * the value stored as a {@link JsonElement} so Gson round-trips the
 * full heterogeneity of types (primitives, enums, nested POJOs, maps,
 * arrays) without bespoke per-type serialization code.
 *
 * <p>On restore, any entry whose field no longer exists on the live
 * config class is silently skipped (logged as a warning), and any
 * config field absent from the map is left at its factory default —
 * see {@link ProfileManager}.
 */
public class Profile {
    /** Display name. Also derived from / written to the filename. */
    public String name = "";

    /** Epoch millis at creation; purely informational. */
    public long createdAtMs = 0L;

    /**
     * Field-name → captured value. Insertion-ordered so the saved JSON
     * is stable and diffable. Never {@code null} after load (the manager
     * replaces nulls with an empty map).
     */
    public Map<String, JsonElement> fields = new LinkedHashMap<>();

    /**
     * Material Symbols name of the profile's card icon ({@code IconCatalog});
     * empty means the default. Profile metadata, not a config field, so it
     * survives every save of the profile's settings (2026-11-12).
     */
    public String icon = "";

    /**
     * Key that switches to this profile in game: a binding in Aurora's key
     * encoding ({@code KeyCombo}: a stored keyboard code, optionally with
     * Ctrl / Shift / Alt), or -1 for none. Metadata, kept on the profile
     * rather than in config so it works from whichever profile is active.
     */
    public int hotkey = -1;

    /**
     * The game's own (vanilla) key bindings captured with this profile:
     * binding name to saved key string ({@link ControlsBridge}). Null for
     * profiles saved before controls were captured; those leave the live
     * vanilla bindings as they are.
     */
    public Map<String, String> controls;

    public Profile() {}

    public Profile(String name) {
        this.name = name;
        this.createdAtMs = System.currentTimeMillis();
    }
}