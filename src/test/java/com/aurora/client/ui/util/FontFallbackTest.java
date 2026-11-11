package com.aurora.client.ui.util;

import com.aurora.client.SourceTree;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characters a bundled TTF lacks (symbols, CJK, server resource-pack icons
 * that only exist in {@code minecraft:default}) must fall through to vanilla's
 * default font instead of rendering as missing-glyph boxes.
 */
class FontFallbackTest {
    private static final Path FONTS = SourceTree.resolve(SourceTree.ADAPTER + "/src/client/resources/assets/aurora/font");

    @Test void everyTextFontFallsBackToTheVanillaDefaultFont() throws Exception {
        int checked = 0;
        try (var files = Files.list(FONTS)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".json"))::iterator) {
                if (file.getFileName().toString().equals("material_symbols.json")) continue; // icon font, explicit codepoints
                // The *_ui variants (compact Aurora-window size) must fall back exactly like the originals.
                String text = Files.readString(file, StandardCharsets.UTF_8).replace("﻿", "");
                JsonArray providers = JsonParser.parseString(text).getAsJsonObject().getAsJsonArray("providers");
                assertEquals("ttf", providers.get(0).getAsJsonObject().get("type").getAsString(), file + " keeps its TTF first");
                JsonObject last = providers.get(providers.size() - 1).getAsJsonObject();
                assertEquals("reference", last.get("type").getAsString(), file + " ends with the vanilla fallback");
                assertEquals("minecraft:default", last.get("id").getAsString(), file.toString());
                checked++;
            }
        }
        assertEquals(18, checked, "all nine selectable text fonts and their compact UI variants");
    }
}
