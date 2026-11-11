package com.aurora.client.config;

import com.google.gson.*;
import org.slf4j.Logger;
import java.util.Map;
import java.util.Set;

/** Converts pre-26.3 GLFW key identifiers to SDL scancodes, once per config/profile. */
public final class KeybindMigrator {
    public static final int VERSION = 1;
    private static final String MARKER = "keybindVersion";
    public static final Set<String> KEY_FIELDS = Set.of(
            "zoomKey",
            "freeLookKey",
            "toggleSprintKey",
            "toggleSneakKey",
            "hudEditorKey",
            "hitboxToggleKey",
            "totemResetKey",
            "waypointDropKey",
            "waypointManagerKey",
            "minimapToggleKey",
            "hitregSettingsKey",
            "hitregSwitchHandKey",
            "hitregScoreLeftKey",
            "hitregScoreRightKey",
            "hitregScoreSendKey",
            "hitregScoreResetKey",
            "blurTestKey",
            "worldMapKey");

    private KeybindMigrator() {}

    public static String migrateConfigJson(String json, Logger log) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return json;
            JsonObject obj = root.getAsJsonObject();
            if (!migrate(obj.asMap(), log)) return json;
            return obj.toString();
        } catch (RuntimeException e) {
            return json; // the config loader reports malformed JSON
        }
    }

    public static void migrateProfileFields(Map<String, JsonElement> fields, Logger log) {
        if (fields != null) migrate(fields, log);
    }

    private static boolean migrate(Map<String, JsonElement> fields, Logger log) {
        if (fields.containsKey(MARKER)) return false;
        for (String field : KEY_FIELDS) {
            if (fields.containsKey(field)) fields.put(field, convert(fields.get(field), log));
        }
        JsonElement extras = fields.get("keystrokesExtraKeys");
        if (extras != null && extras.isJsonArray()) {
            JsonArray converted = new JsonArray();
            for (JsonElement key : extras.getAsJsonArray()) converted.add(convert(key, log));
            fields.put("keystrokesExtraKeys", converted);
        }
        fields.put(MARKER, new JsonPrimitive(VERSION));
        log.info("Migrated Aurora keyboard bindings to SDL scancodes");
        return true;
    }

    private static JsonElement convert(JsonElement element, Logger log) {
        try {
            // Reject non-integral / out-of-range input instead of reinterpreting it as a key.
            int old = element.getAsBigDecimal().intValueExact();
            int key = fromGlfw(old);
            if (key == -1 && old != -1) log.warn("Legacy keyboard code {} has no SDL equivalent; unbound", old);
            return new JsonPrimitive(key);
        } catch (RuntimeException e) {
            log.warn("Invalid legacy keyboard binding {}; unbound", element);
            return new JsonPrimitive(-1);
        }
    }

    /** Aurora's -1 sentinel survives; unmappable keys (including F25) become unbound. */
    public static int fromGlfw(int key) {
        return switch (key) {
            case 48 -> 39; // 0
            case 49 -> 30; // 1
            case 50 -> 31; // 2
            case 51 -> 32; // 3
            case 52 -> 33; // 4
            case 53 -> 34; // 5
            case 54 -> 35; // 6
            case 55 -> 36; // 7
            case 56 -> 37; // 8
            case 57 -> 38; // 9
            case 65 -> 4; // a
            case 66 -> 5; // b
            case 67 -> 6; // c
            case 68 -> 7; // d
            case 69 -> 8; // e
            case 70 -> 9; // f
            case 71 -> 10; // g
            case 72 -> 11; // h
            case 73 -> 12; // i
            case 74 -> 13; // j
            case 75 -> 14; // k
            case 76 -> 15; // l
            case 77 -> 16; // m
            case 78 -> 17; // n
            case 79 -> 18; // o
            case 80 -> 19; // p
            case 81 -> 20; // q
            case 82 -> 21; // r
            case 83 -> 22; // s
            case 84 -> 23; // t
            case 85 -> 24; // u
            case 86 -> 25; // v
            case 87 -> 26; // w
            case 88 -> 27; // x
            case 89 -> 28; // y
            case 90 -> 29; // z
            case 290 -> 58; // f1
            case 291 -> 59; // f2
            case 292 -> 60; // f3
            case 293 -> 61; // f4
            case 294 -> 62; // f5
            case 295 -> 63; // f6
            case 296 -> 64; // f7
            case 297 -> 65; // f8
            case 298 -> 66; // f9
            case 299 -> 67; // f10
            case 300 -> 68; // f11
            case 301 -> 69; // f12
            case 302 -> 104; // f13
            case 303 -> 105; // f14
            case 304 -> 106; // f15
            case 305 -> 107; // f16
            case 306 -> 108; // f17
            case 307 -> 109; // f18
            case 308 -> 110; // f19
            case 309 -> 111; // f20
            case 310 -> 112; // f21
            case 311 -> 113; // f22
            case 312 -> 114; // f23
            case 313 -> 115; // f24
            case 282 -> 83; // num.lock
            case 320 -> 98; // keypad.0
            case 321 -> 89; // keypad.1
            case 322 -> 90; // keypad.2
            case 323 -> 91; // keypad.3
            case 324 -> 92; // keypad.4
            case 325 -> 93; // keypad.5
            case 326 -> 94; // keypad.6
            case 327 -> 95; // keypad.7
            case 328 -> 96; // keypad.8
            case 329 -> 97; // keypad.9
            case 334 -> 87; // keypad.add
            case 330 -> 220; // keypad.decimal
            case 335 -> 88; // keypad.enter
            case 336 -> 103; // keypad.equal
            case 332 -> 85; // keypad.multiply
            case 331 -> 84; // keypad.divide
            case 333 -> 86; // keypad.subtract
            case 264 -> 81; // down
            case 263 -> 80; // left
            case 262 -> 79; // right
            case 265 -> 82; // up
            case 39 -> 52; // apostrophe
            case 92 -> 49; // backslash
            case 44 -> 54; // comma
            case 61 -> 46; // equal
            case 96 -> 53; // grave.accent
            case 91 -> 47; // left.bracket
            case 45 -> 45; // minus
            case 46 -> 55; // period
            case 93 -> 48; // right.bracket
            case 59 -> 51; // semicolon
            case 47 -> 56; // slash
            case 32 -> 44; // space
            case 258 -> 43; // tab
            case 342 -> 226; // left.alt
            case 341 -> 224; // left.control
            case 340 -> 225; // left.shift
            case 343 -> 227; // left.win
            case 346 -> 230; // right.alt
            case 345 -> 228; // right.control
            case 344 -> 229; // right.shift
            case 347 -> 231; // right.win
            case 257 -> 40; // enter
            case 256 -> 41; // escape
            case 259 -> 42; // backspace
            case 261 -> 76; // delete
            case 269 -> 77; // end
            case 268 -> 74; // home
            case 260 -> 73; // insert
            case 267 -> 78; // page.down
            case 266 -> 75; // page.up
            case 280 -> 57; // caps.lock
            case 284 -> 72; // pause
            case 281 -> 71; // scroll.lock
            case 348 -> 118; // menu
            case 283 -> 70; // print.screen
            case 161 -> 100; // world.1
            case 162 -> 50; // world.2
            default -> -1;
        };
    }
}
