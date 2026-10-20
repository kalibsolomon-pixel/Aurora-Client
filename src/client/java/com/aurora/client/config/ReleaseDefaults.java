package com.aurora.client.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Modifier;

/** Portable, reviewed factory settings. Existing JSON overlays these defaults via Gson. */
public final class ReleaseDefaults {
    private static final Gson GSON = new Gson();
    private static final JsonObject VALUES = read();

    private ReleaseDefaults() {}

    private static JsonObject read() {
        try (var stream = ReleaseDefaults.class.getResourceAsStream("/assets/aurora/defaults/3.1.0.json")) {
            if (stream == null) throw new IllegalStateException("Missing Aurora release defaults");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Unreadable Aurora release defaults", e);
        }
    }

    static void apply(AuroraConfig config) {
        for (var entry : VALUES.entrySet()) {
            try {
                var field = AuroraConfig.class.getField(entry.getKey());
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers()))
                    throw new IllegalStateException("Invalid default field: " + entry.getKey());
                // Deserialize afresh: never share mutable maps, arrays, layouts or theme objects.
                field.set(config, GSON.fromJson(entry.getValue(), field.getGenericType()));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Invalid Aurora release default: " + entry.getKey(), e);
            }
        }
    }

    /** Independent copy for position resets, without reading a user's live config. */
    public static AuroraConfig.ModuleLayout layout(String id) {
        var layouts = VALUES.getAsJsonObject("moduleLayouts");
        return layouts.has(id) ? GSON.fromJson(layouts.get(id), AuroraConfig.ModuleLayout.class) : null;
    }
}
