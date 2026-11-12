package com.aurora.client.config;

import com.aurora.client.SourceTree;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Text options: fonts at every weight, the Inter Italic migration and the HUD shadow (2026-11-12). */
class TextOptionsTest {
    private static final Logger LOG = LoggerFactory.getLogger("TextOptionsTest");
    private static final Path FONTS = Path.of("adapters/shared/src/client/resources/assets/aurora/font");

    @Test void everyFamilyHasEveryWeightAndItsItalics() throws Exception {
        for (AuroraConfig.FontType font : AuroraConfig.FontType.choices()) {
            if (font.fileStem() == null) continue; // SansSerif: Minecraft's own font
            String provider = font.fileStem().replace('-', '_');
            boolean italics = font != AuroraConfig.FontType.OSWALD && font != AuroraConfig.FontType.QUICKSAND;
            for (AuroraConfig.TextWeight weight : AuroraConfig.TextWeight.values()) {
                for (boolean italic : italics ? new boolean[]{false, true} : new boolean[]{false}) {
                    for (String ui : new String[]{"", "_ui"}) {
                        String id = provider + "_" + weight.suffix() + (italic ? "_italic" : "") + ui;
                        Path json = FONTS.resolve(id + ".json");
                        assertTrue(Files.exists(json), id);
                        String file = JsonParser.parseString(Files.readString(json)).getAsJsonObject()
                                .getAsJsonArray("providers").get(0).getAsJsonObject().get("file").getAsString();
                        assertTrue(Files.exists(FONTS.resolve(file.substring("aurora:".length()))), id + " -> " + file);
                    }
                }
            }
        }
        String renderer = SourceTree.read("ui/util/AuroraFontRenderer.java");
        assertTrue(renderer.contains("family + \"_\" + weight.suffix() + (realItalic ? \"_italic\" : \"\") + (compact ? \"_ui\" : \"\")"),
                "the renderer resolves the same ids tools/build_fonts.py writes");
        assertFalse(Files.exists(FONTS.resolve("inter-frozen-medium.ttf")), "the single-weight files are gone");
    }

    @Test void interItalicBecomesInterWithItalicOn() {
        String json = "{\"activeFont\":\"INTER_ITALIC\",\"zoomLevel\":4.0}";
        var migrated = JsonParser.parseString(TextStyleMigrator.migrateConfigJson(json, LOG)).getAsJsonObject();
        assertEquals("INTER", migrated.get("activeFont").getAsString());
        assertTrue(migrated.get("textItalic").getAsBoolean());
        assertEquals(4.0, migrated.get("zoomLevel").getAsDouble(), "everything else is kept");

        String current = "{\"activeFont\":\"ROBOTO\",\"textItalic\":false}";
        assertEquals(current, TextStyleMigrator.migrateConfigJson(current, LOG), "a current config is left untouched");

        Map<String, JsonElement> fields = new HashMap<>(Map.of("activeFont", new JsonPrimitive("INTER_ITALIC")));
        TextStyleMigrator.migrateProfileFields(fields, LOG);
        assertEquals("INTER", fields.get("activeFont").getAsString());
        assertTrue(fields.get("textItalic").getAsBoolean());
    }

    @Test void defaultsKeepTodaysLook() {
        AuroraConfig fresh = new AuroraConfig();
        assertEquals(AuroraConfig.TextWeight.MEDIUM, fresh.textWeight, "Medium is what every family shipped before");
        assertFalse(fresh.textItalic);
        assertFalse(fresh.textUnderline);
        assertEquals(AuroraConfig.TextCase.NORMAL, fresh.textCase);
        assertEquals(AuroraConfig.TextShadow.OFF, fresh.textHudShadow);
        assertTrue(fresh.searchInstant);
        assertEquals(2, fresh.searchTypoTolerance);
        assertFalse(java.util.Arrays.asList(AuroraConfig.FontType.choices()).contains(AuroraConfig.FontType.INTER_ITALIC),
                "the picker no longer offers Inter Italic");
    }

    @Test void hudTextGoesThroughThePainter() throws Exception {
        for (Path root : new Path[]{SourceTree.SHARED, SourceTree.CLIENT}) {
            try (var files = Files.walk(root.resolve("com/aurora/client/hud"))) {
                for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                    if (file.getFileName().toString().equals("HudTextPainter.java")) continue;
                    String src = Files.readString(file);
                    assertFalse(src.contains("ctx.text("), file + " draws HUD text without the shadow option");
                }
            }
        }
    }
}
