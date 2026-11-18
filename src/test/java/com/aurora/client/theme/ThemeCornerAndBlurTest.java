package com.aurora.client.theme;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/** The Corner Radius and Blur Radius theme values (2026-11-12) and their migration. */
class ThemeCornerAndBlurTest {
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger("ThemeCornerAndBlurTest");

    @Test void radiiKeepTheRetiredCornerStylesExactly() {
        assertEquals(10, ThemeRoundness.ROUND.radius());
        assertEquals(14, ThemeRoundness.ROUND.radiusLarge());
        assertEquals(6, ThemeRoundness.ROUND.radiusSmall());
        assertEquals(6, ThemeRoundness.SLIGHTLY_ROUND.radius());
        assertEquals(8, ThemeRoundness.SLIGHTLY_ROUND.radiusLarge());
        assertEquals(3, ThemeRoundness.SLIGHTLY_ROUND.radiusSmall());
        assertEquals(0, ThemeRoundness.SQUARE.radiusLarge());
        assertEquals(ThemeRoundness.DEFAULT_RADIUS, ThemeRoundness.ROUND.radius());
    }

    @Test void valuesAreInternedAndClamped() {
        assertSame(ThemeRoundness.of(7), ThemeRoundness.of(7));
        assertEquals(ThemeRoundness.MAX_RADIUS, ThemeRoundness.of(999).radius());
        assertEquals(0, ThemeRoundness.of(-4).radius());
    }

    @Test void capsulesAreFullAtTheDefaultAndSquareAtZero() {
        assertEquals(6f, ThemeRoundness.ROUND.capsule(12f));
        assertEquals(6f, ThemeRoundness.of(ThemeRoundness.MAX_RADIUS).capsule(12f), "never rounder than a capsule");
        assertEquals(3f, ThemeRoundness.of(5).capsule(12f));
        assertEquals(0f, ThemeRoundness.SQUARE.capsule(12f));
        float previous = -1;
        for (int r = 0; r <= ThemeRoundness.MAX_RADIUS; r++) {
            float c = ThemeRoundness.of(r).capsule(4f);
            assertTrue(c >= previous, "the capsule rule grows with the radius");
            previous = c;
        }
    }

    @Test void legacyNamesMapToTheirRadii() {
        assertEquals(10, ThemeRoundness.legacyRadius("ROUND"));
        assertEquals(6, ThemeRoundness.legacyRadius("slightly_round"));
        assertEquals(0, ThemeRoundness.legacyRadius("SQUARE"));
        assertEquals(10, ThemeRoundness.legacyRadius("BOGUS"));
        assertEquals(10, ThemeRoundness.legacyRadius(null));
    }

    @Test void configMigrationKeepsTheCornerStyleAndTheEffectiveBlur() {
        String json = "{\"zoomEnabled\":true,\"theme\":{\"mode\":\"DARK\",\"roundness\":\"SQUARE\","
                + "\"backgroundOpacity\":0.19,\"accent\":-1,\"background\":-14543302}}";
        JsonObject theme = JsonParser.parseString(ThemeMigrator.migrateConfigJson(json, LOG))
                .getAsJsonObject().getAsJsonObject("theme");
        assertFalse(theme.has("roundness"), "the retired member is dropped");
        assertEquals(0, theme.get("cornerRadius").getAsInt());
        // Before the slider: 28 GUI px x 0.19 opacity = 5.32 -> 5.
        assertEquals(5, theme.get("blurRadius").getAsInt());
        ThemeDefinition loaded = new Gson().fromJson(theme, ThemeDefinition.class);
        assertEquals(0, ThemeResolver.resolve(loaded, true).roundness().radius());
        assertEquals(5, ThemeResolver.resolve(loaded, true).blurRadius());
    }

    @Test void slightlyRoundAndMissingOpacityMigrate() {
        String json = "{\"theme\":{\"roundness\":\"SLIGHTLY_ROUND\"}}";
        JsonObject theme = JsonParser.parseString(ThemeMigrator.migrateConfigJson(json, LOG))
                .getAsJsonObject().getAsJsonObject("theme");
        assertEquals(6, theme.get("cornerRadius").getAsInt());
        assertEquals(ThemeDefinition.DEFAULT_BLUR_RADIUS, theme.get("blurRadius").getAsInt(),
                "the initializer opacity (1.0) blurred at the full 28");
    }

    @Test void savedRadiiAreNeverOverwritten() {
        String json = "{\"theme\":{\"roundness\":\"SQUARE\",\"cornerRadius\":15,\"blurRadius\":3,"
                + "\"backgroundOpacity\":1.0}}";
        JsonObject theme = JsonParser.parseString(ThemeMigrator.migrateConfigJson(json, LOG))
                .getAsJsonObject().getAsJsonObject("theme");
        assertFalse(theme.has("roundness"));
        assertEquals(15, theme.get("cornerRadius").getAsInt());
        assertEquals(3, theme.get("blurRadius").getAsInt());
        String current = "{\"theme\":{\"cornerRadius\":4,\"blurRadius\":12,\"backgroundOpacity\":0.5}}";
        assertEquals(current, ThemeMigrator.migrateConfigJson(current, LOG), "a current theme is left untouched");
    }

    @Test void profilesMigrateTheSameWay() {
        var fields = new HashMap<String, com.google.gson.JsonElement>();
        fields.put("theme", JsonParser.parseString("{\"roundness\":\"ROUND\",\"backgroundOpacity\":0.33}"));
        ThemeMigrator.migrateProfileFields(fields, LOG);
        JsonObject theme = fields.get("theme").getAsJsonObject();
        assertEquals(10, theme.get("cornerRadius").getAsInt());
        assertEquals(9, theme.get("blurRadius").getAsInt());
        assertFalse(theme.has("roundness"));
    }

    @Test void copiesClampHandEditedRadii() {
        ThemeDefinition def = new ThemeDefinition();
        def.cornerRadius = 500;
        def.blurRadius = -3;
        ThemeDefinition copy = def.copy();
        assertEquals(ThemeRoundness.MAX_RADIUS, copy.cornerRadius);
        assertEquals(ThemeDefinition.MIN_BLUR_RADIUS, copy.blurRadius);
    }

    @Test void releaseDefaultsKeepTheFactoryLook() throws Exception {
        try (var in = ThemeCornerAndBlurTest.class.getResourceAsStream("/assets/aurora/defaults/3.2.0.json")) {
            assertNotNull(in);
            JsonObject theme = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("theme");
            assertFalse(theme.has("roundness"));
            assertEquals(ThemeRoundness.DEFAULT_RADIUS, theme.get("cornerRadius").getAsInt());
            // The factory blur before the slider: 28 GUI px x 0.33 opacity = 9.24 -> 9.
            assertEquals(Math.round(ThemeDefinition.DEFAULT_BLUR_RADIUS * theme.get("backgroundOpacity").getAsDouble()),
                    theme.get("blurRadius").getAsInt());
        }
    }
}
