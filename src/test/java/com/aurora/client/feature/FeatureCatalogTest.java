package com.aurora.client.feature;

import com.aurora.client.SourceTree;
import com.aurora.client.config.AuroraConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the one feature list (ADR 0004) in agreement with the files that
 * must match it: the mixin config, the settings registry and the config
 * schema. A mixin added without a binding, a feature added to one place
 * only, or a gate that names a field that does not exist fails here.
 */
class FeatureCatalogTest {
    private static final Path MIXIN_CONFIG = Path.of("src/client/resources/aurora.mixins.json");
    private static final Path REGISTRY = Path.of("src/client/java/com/aurora/client/screen/FeatureRegistry.java");

    private static JsonObject mixinConfig() throws Exception {
        return JsonParser.parseString(Files.readString(MIXIN_CONFIG)).getAsJsonObject();
    }

    @Test void everyMixinInTheConfigHasABindingInTheSameOrder() throws Exception {
        List<String> listed = new ArrayList<>();
        JsonArray client = mixinConfig().getAsJsonArray("client");
        for (JsonElement e : client) listed.add(e.getAsString());

        List<String> bound = new ArrayList<>();
        for (FeatureCatalog.MixinBinding b : FeatureCatalog.mixins()) bound.add(b.mixin());

        assertEquals(listed, bound,
                "FeatureCatalog must bind exactly the mixins aurora.mixins.json lists, in the same order");
        assertEquals(listed.size(), new HashSet<>(listed).size(), "a mixin is listed twice");
    }

    @Test void theConfigIsFailSoftAndNamesThePlugin() throws Exception {
        JsonObject config = mixinConfig();
        assertFalse(config.get("required").getAsBoolean(),
                "required:true stops the game when any one mixin cannot apply (ADR 0004)");
        assertEquals("com.aurora.client.health.AuroraMixinPlugin", config.get("plugin").getAsString());
        assertEquals(1, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt(),
                "defaultRequire must stay 1: it is what turns a missed injection into a reported failure");
        // Classes inside a mixin package cannot be loaded as ordinary classes.
        assertFalse(config.get("plugin").getAsString().startsWith(config.get("package").getAsString() + "."));
    }

    @Test void everyBindingNamesRealFeatures() {
        for (FeatureCatalog.MixinBinding b : FeatureCatalog.mixins()) {
            assertFalse(b.provides().isBlank(), b.mixin() + " must say what it provides");
            for (String feature : b.features()) {
                assertNotNull(FeatureCatalog.feature(feature),
                        b.mixin() + " names unknown feature '" + feature + "'");
            }
        }
    }

    @Test void everyGateIsAPublicBooleanConfigField() throws Exception {
        for (FeatureCatalog.MixinBinding b : FeatureCatalog.mixins()) {
            for (String gate : b.gates()) {
                Field f;
                try {
                    f = AuroraConfig.class.getField(gate);
                } catch (NoSuchFieldException e) {
                    throw new AssertionError(b.mixin() + " gates on '" + gate + "', which is not an AuroraConfig field");
                }
                assertEquals(boolean.class, f.getType(), b.mixin() + " gate '" + gate + "' must be a boolean");
                assertFalse(Modifier.isStatic(f.getModifiers()), gate + " must be an instance field");
            }
        }
    }

    @Test void theRegistryRegistersExactlyTheCatalogFeatures() throws Exception {
        String source = Files.readString(REGISTRY);
        Matcher m = Pattern.compile(
                "\\badd(?:WithSettings)?\\(\\s*(\"[^\"]*\"|com\\.aurora\\.client\\.hitreg\\.BetterHitreg\\.FEATURE_ID),")
                .matcher(source);
        List<String> registered = new ArrayList<>();
        while (m.find()) {
            String raw = m.group(1);
            registered.add(raw.startsWith("\"") ? raw.substring(1, raw.length() - 1) : "better_hitreg");
        }

        Set<String> catalog = new HashSet<>();
        for (FeatureCatalog.Feature f : FeatureCatalog.features()) catalog.add(f.id());
        assertEquals(catalog, new HashSet<>(registered),
                "FeatureRegistry and FeatureCatalog must list the same feature ids");
        assertEquals(registered.size(), new HashSet<>(registered).size(), "a feature is registered twice");

        // The Settings tab is drawn in registration order; the catalog records the same order.
        List<String> settingsInCatalog = new ArrayList<>();
        for (FeatureCatalog.Feature f : FeatureCatalog.featuresOn(FeatureCatalog.Tab.SETTINGS)) {
            settingsInCatalog.add(f.id());
        }
        List<String> settingsRegistered = new ArrayList<>(registered);
        settingsRegistered.retainAll(settingsInCatalog);
        assertEquals(settingsInCatalog, settingsRegistered);

        // Names and tabs live in the catalog only.
        assertFalse(source.contains("add(MODULES,") || source.contains("add(SETTINGS,")
                        || source.contains("addWithSettings(MODULES,") || source.contains("addWithSettings(SETTINGS,"),
                "FeatureRegistry must take a feature's tab and name from FeatureCatalog");
    }

    // ---- Fair play (ADR 0007) ----

    @Test void everyFeatureHasAFairPlayClassAndAPlainNote() {
        for (FeatureCatalog.Feature f : FeatureCatalog.features()) {
            assertNotNull(f.fairPlay(), f.id() + " has no fair-play class");
            assertFalse(f.fairPlayNote().isBlank(), f.id() + " has no fair-play note");
            assertTrue(f.fairPlayNote().endsWith("."), f.id() + " note must be a sentence");
            assertFalse(f.fairPlayNote().contains("|"), f.id() + " note would break the document's table");
            if (f.fairPlay() == FeatureCatalog.FairPlay.INFORMATION_ADVANTAGE) {
                assertFalse(f.offByDefault().isEmpty(),
                        f.id() + " is an information advantage and must name the switch that ships off");
            }
        }
    }

    @Test void everySwitchThatShipsOffIsABooleanConfigFieldAndOffInTheReleaseDefaults() throws Exception {
        JsonObject releaseDefaults = JsonParser.parseString(Files.readString(
                Path.of("src/client/resources/assets/aurora/defaults/3.2.0.json"))).getAsJsonObject();
        for (FeatureCatalog.Feature f : FeatureCatalog.features()) {
            for (String name : f.offByDefault()) {
                Field field;
                try {
                    field = AuroraConfig.class.getField(name);
                } catch (NoSuchFieldException e) {
                    throw new AssertionError(f.id() + " names '" + name + "', which is not an AuroraConfig field");
                }
                assertEquals(boolean.class, field.getType(), name);
                // The effective default is checked on a real config in FramePacingPersistenceTest;
                // this catches the release-defaults file turning one back on.
                if (releaseDefaults.has(name)) {
                    assertFalse(releaseDefaults.get(name).getAsBoolean(),
                            name + " must be off in the release defaults (ADR 0007)");
                }
            }
        }
    }

    @Test void theFourAgreedSwitchesShipOff() {
        // ADR 0007's first default-off list, by the switch that implements each item.
        assertTrue(FeatureCatalog.require("free_look").offByDefault().contains("freeLookEnabled"));
        assertTrue(FeatureCatalog.require("player_health").offByDefault().contains("playerHealthIndicators"));
        assertTrue(FeatureCatalog.require("totem_pop").offByDefault().containsAll(List.of("totemShowOthers", "nametagTotemPopsEnabled")));
        assertTrue(FeatureCatalog.require("minimap").offByDefault().contains("minimapShowPlayers"));
        assertEquals(FeatureCatalog.FairPlay.LOCAL_TIMING, FeatureCatalog.require("better_hitreg").fairPlay());
    }

    @Test void theFairPlayDocumentRepeatsTheCatalog() throws Exception {
        String doc = Files.readString(Path.of("docs/FAIR_PLAY.md"));
        for (FeatureCatalog.FairPlay c : FeatureCatalog.FairPlay.values()) {
            assertTrue(doc.contains("| " + c.label + " | " + c.meaning + " |"), "class row for " + c);
        }
        for (FeatureCatalog.Feature f : FeatureCatalog.features()) {
            String row = "| " + f.name() + " | " + f.fairPlay().label + " | " + f.fairPlayNote() + " |";
            assertTrue(doc.contains(row),
                    "docs/FAIR_PLAY.md is out of date for " + f.id() + "; run python tools/gen_fair_play_doc.py");
        }
    }

    /**
     * The document tells server owners that Aurora sends nothing of its own.
     * This scans every source file so that stays true: no custom packets or
     * channels, no tick-rate changes, and exactly the two user-triggered
     * vanilla sends the document names.
     */
    @Test void nothingInTheModSendsToAServerExceptTheTwoDocumentedKeys() throws Exception {
        List<String> forbidden = List.of("Serverbound", "ClientPlayNetworking", "PayloadTypeRegistry",
                "CustomPacketPayload", "tickRateManager", "sendCommand(", "sendUnsignedCommand(");
        List<String> chatSends = new ArrayList<>();
        List<String> optionBroadcasts = new ArrayList<>();
        try (var files = SourceTree.javaFiles()) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String source = Files.readString(file);
                String name = SourceTree.relative(file);
                for (String token : forbidden) {
                    assertFalse(source.contains(token), name + " uses " + token + " (ADR 0007: no non-vanilla packets, no tick changes)");
                }
                if (source.contains("sendChat(")) chatSends.add(name);
                if (source.contains("broadcastOptions(")) optionBroadcasts.add(name);
            }
        }
        assertEquals(List.of("hitreg/BetterHitreg.java"), chatSends, "the practice scoreboard's Send key is the only chat send");
        assertEquals(List.of("hitreg/BetterHitreg.java"), optionBroadcasts, "Switch Hand is the only options broadcast");

        // Better Hitreg watches your attack; it must never be able to cancel or change it.
        String attack = SourceTree.read("mixin/hitreg/AttackMixin.java");
        assertTrue(attack.contains("@Inject(method = \"attack\", at = @At(\"HEAD\"))"),
                "the attack hook must stay a plain, non-cancellable HEAD inject");
        assertFalse(attack.contains("cancellable"));
    }

    @Test void gridCardsHaveASummaryAndSettingsEntriesDoNot() {
        for (FeatureCatalog.Feature f : FeatureCatalog.features()) {
            assertFalse(f.name().isBlank(), f.id());
            if (f.tab() == FeatureCatalog.Tab.MODULES) assertFalse(f.summary().isBlank(), f.id() + " needs card text");
            else assertTrue(f.summary().isEmpty(), f.id() + " has no card");
        }
        assertEquals(34, FeatureCatalog.featuresOn(FeatureCatalog.Tab.MODULES).size());
        assertEquals(4, FeatureCatalog.featuresOn(FeatureCatalog.Tab.SETTINGS).size());
    }
}
