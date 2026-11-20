package com.aurora.client.theme;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class ThemeCornerAndBlurTest {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ThemeCornerAndBlurTest.class);

    @Test void configDropsRetiredCornersAndKeepsTheEffectiveBlur() {
        String json = """
                {"zoomEnabled":true,"keystrokesThemeCorners":true,"theme":{
                  "cornerRadius":15,"roundness":"ROUND","backgroundOpacity":0.19,"accent":-1
                }}
                """;
        String migrated = ThemeMigrator.migrateConfigJson(json, LOG);
        JsonObject config = JsonParser.parseString(migrated).getAsJsonObject();
        JsonObject theme = config.getAsJsonObject("theme");

        assertFalse(config.has("keystrokesThemeCorners"));
        assertTrue(config.get("zoomEnabled").getAsBoolean());
        assertFalse(theme.has("roundness"));
        assertFalse(theme.has("cornerRadius"));
        assertEquals(-1, theme.get("accent").getAsInt());
        // Before the slider, 28 GUI px at 19% opacity rounded to 5 px.
        assertEquals(5, ThemeResolver.resolve(new Gson().fromJson(theme, ThemeDefinition.class), true).blurRadius());
        assertEquals(migrated, ThemeMigrator.migrateConfigJson(migrated, LOG));
    }

    @Test void profilesDropRetiredCornersAndKeepSavedBlur() {
        var fields = new HashMap<String, JsonElement>();
        fields.put("minimapThemeCorners", JsonParser.parseString("true"));
        fields.put("minimapCircular", JsonParser.parseString("true"));
        fields.put("theme", JsonParser.parseString("""
                {"roundness":"SLIGHTLY_ROUND","cornerRadius":{"invalid":true},"blurRadius":9}
                """));
        ThemeMigrator.migrateProfileFields(fields, LOG);
        JsonObject theme = fields.get("theme").getAsJsonObject();

        assertFalse(fields.containsKey("minimapThemeCorners"));
        assertTrue(fields.get("minimapCircular").getAsBoolean());
        assertFalse(theme.has("roundness"));
        assertFalse(theme.has("cornerRadius"));
        assertEquals(9, theme.get("blurRadius").getAsInt());
    }

    @Test void missingOpacitySeedsTheOriginalBlurRadius() {
        String json = "{\"theme\":{\"roundness\":\"SLIGHTLY_ROUND\"}}";
        JsonObject theme = JsonParser.parseString(ThemeMigrator.migrateConfigJson(json, LOG))
                .getAsJsonObject().getAsJsonObject("theme");
        assertEquals(ThemeDefinition.DEFAULT_BLUR_RADIUS, theme.get("blurRadius").getAsInt());
    }

    @Test void copiesClampHandEditedBlur() {
        ThemeDefinition def = new ThemeDefinition();
        def.blurRadius = -3;
        assertEquals(ThemeDefinition.MIN_BLUR_RADIUS, def.copy().blurRadius);
    }

    @Test void releaseDefaultsKeepFactoryBlurAndHaveNoCornerSetting() throws Exception {
        try (var in = ThemeCornerAndBlurTest.class.getResourceAsStream("/assets/aurora/defaults/3.2.0.json")) {
            assertNotNull(in);
            JsonObject theme = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("theme");
            assertFalse(theme.has("roundness"));
            assertFalse(theme.has("cornerRadius"));
            // The original factory blur at 42% opacity rounded to 12 px.
            assertEquals(Math.round(ThemeDefinition.DEFAULT_BLUR_RADIUS * theme.get("backgroundOpacity").getAsDouble()),
                    theme.get("blurRadius").getAsInt());
        }
    }
}
