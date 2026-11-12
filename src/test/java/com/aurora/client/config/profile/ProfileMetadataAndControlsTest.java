package com.aurora.client.config.profile;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.ConfigDirectory;
import com.aurora.client.util.KeyCombo;
import com.google.gson.Gson;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Profile icons, hotkeys and "Separate controls" (2026-11-12). */
class ProfileMetadataAndControlsTest {
    @TempDir static Path temporary;
    private static final Gson GSON = new Gson();

    /** A stand-in for the game's bindings. */
    static final class FakeBridge implements ControlsBridge {
        Map<String, String> live = new LinkedHashMap<>(Map.of("key.jump", "key.keyboard.space"));
        final List<Map<String, String>> applied = new ArrayList<>();
        @Override public Map<String, String> capture() { return new LinkedHashMap<>(live); }
        @Override public void apply(Map<String, String> controls) { applied.add(controls); live = new LinkedHashMap<>(controls); }
    }

    private static final FakeBridge BRIDGE = new FakeBridge();

    @BeforeAll static void isolate() {
        ConfigDirectory.set(temporary);
        ProfileManager.getInstance().load();
        ProfileManager.getInstance().setControlsBridge(BRIDGE);
    }

    @AfterAll static void detach() {
        ProfileManager.getInstance().setControlsBridge(null);
        AuroraConfig.get().profilesOwnControls = true;
    }

    private static String unique(String prefix) {
        return prefix + "-" + System.nanoTime();
    }

    @Test void iconAndHotkeySurviveEverySaveAndSwitch() throws Exception {
        var manager = ProfileManager.getInstance();
        String name = unique("meta");
        assertTrue(manager.create(name));
        assertTrue(manager.setIcon(name, "swords"));
        assertTrue(manager.setHotkey(name, 30));
        assertTrue(manager.switchTo(name));
        AuroraConfig.get().zoomLevel = 7.5;
        manager.saveCurrentBlocking();
        Profile disk = GSON.fromJson(Files.readString(temporary.resolve("profiles/" + name + ".json")), Profile.class);
        assertEquals("swords", disk.icon, "a settings save keeps the icon");
        assertEquals(30, disk.hotkey, "a settings save keeps the hotkey");
        assertEquals("swords", manager.icon(name));
        assertEquals(30, manager.hotkey(name));
        assertTrue(manager.switchTo("default"));
        assertEquals("swords", manager.icon(name), "switching away keeps it too");
    }

    @Test void combosAreTheirOwnBindingsAndSurviveSaves() throws Exception {
        // Owner request 2026-11-12: Ctrl / Shift / Alt + a key, one int (KeyCombo).
        var manager = ProfileManager.getInstance();
        String plain = unique("plain"), combo = unique("combo");
        int ctrlShiftP = KeyCombo.of(19, KeyCombo.CTRL | KeyCombo.SHIFT);
        assertTrue(manager.create(plain));
        assertTrue(manager.create(combo));
        assertTrue(manager.setHotkey(plain, 19));
        assertTrue(manager.setHotkey(combo, ctrlShiftP));
        assertEquals(19, manager.hotkey(plain), "P and Ctrl + Shift + P sit on different profiles");
        assertEquals(ctrlShiftP, manager.hotkey(combo));
        assertTrue(manager.switchTo(combo));
        manager.saveCurrentBlocking();
        Profile disk = GSON.fromJson(Files.readString(temporary.resolve("profiles/" + combo + ".json")), Profile.class);
        assertEquals(ctrlShiftP, disk.hotkey, "a settings save keeps the combo");
        assertTrue(manager.setHotkey(plain, ctrlShiftP));
        assertEquals(-1, manager.hotkey(combo), "the same combo moves, as a plain key does");
        assertTrue(manager.switchTo("default"));
        Profile old = GSON.fromJson("{\"name\":\"old\",\"hotkey\":30}", Profile.class);
        assertEquals(30, KeyCombo.key(old.hotkey));
        assertEquals(0, KeyCombo.modifiers(old.hotkey), "files from before combos read as plain keys");
    }

    @Test void oneKeyBelongsToOneProfileAndDuplicatesStartWithout() {
        var manager = ProfileManager.getInstance();
        String a = unique("a"), b = unique("b");
        assertTrue(manager.create(a));
        assertTrue(manager.create(b));
        assertTrue(manager.setHotkey(a, 44));
        assertTrue(manager.setHotkey(b, 44));
        assertEquals(-1, manager.hotkey(a), "binding the key elsewhere takes it away");
        assertEquals(44, manager.hotkey(b));
        String copy = unique("copy");
        assertTrue(manager.duplicate(b, copy));
        assertEquals(-1, manager.hotkey(copy), "a duplicate does not share the key");
        assertEquals(44, manager.hotkeys().get(b));
        assertTrue(manager.setHotkey(b, -1));
        assertFalse(manager.hotkeys().containsKey(b));
    }

    @Test void separateControlsDecidesWhetherBindingsFollowTheProfile() {
        var manager = ProfileManager.getInstance();
        var live = AuroraConfig.get();
        Profile p = GSON.fromJson("{\"name\":\"keys\",\"keybindVersion\":1,\"fields\":{\"zoomKey\":7,\"keybindVersion\":1}}", Profile.class);
        p.controls = new LinkedHashMap<>(Map.of("key.jump", "key.keyboard.j"));

        live.profilesOwnControls = false;
        live.zoomKey = 9;
        BRIDGE.applied.clear();
        manager.applyProfile(p);
        assertEquals(9, live.zoomKey, "off: Aurora's bindings stay global");
        assertTrue(BRIDGE.applied.isEmpty(), "off: the game's bindings are left alone");

        live.profilesOwnControls = true;
        manager.applyProfile(p);
        assertEquals(7, live.zoomKey, "on: the profile's Aurora binding applies");
        assertEquals(List.of(Map.of("key.jump", "key.keyboard.j")), BRIDGE.applied, "on: the game's bindings apply");
    }

    @Test void capturesIncludeTheGamesBindingsWhateverTheToggle() {
        var manager = ProfileManager.getInstance();
        AuroraConfig.get().profilesOwnControls = false;
        BRIDGE.live = new LinkedHashMap<>(Map.of("key.sneak", "key.keyboard.left.shift"));
        assertEquals(Map.of("key.sneak", "key.keyboard.left.shift"), manager.captureCurrent().controls,
                "captured even when off, so turning it on later finds them");
        AuroraConfig.get().profilesOwnControls = true;
    }

    @Test void oldProfilesLoadAndLeaveTheGamesBindingsAlone() {
        Profile old = GSON.fromJson("{\"name\":\"old\",\"createdAtMs\":1,\"fields\":{}}", Profile.class);
        assertEquals("", old.icon);
        assertEquals(-1, old.hotkey);
        assertNull(old.controls);
        BRIDGE.applied.clear();
        ProfileManager.getInstance().applyProfile(old);
        assertTrue(BRIDGE.applied.isEmpty(), "no captured controls, nothing applied");
    }

    @Test void theToggleIsGlobal() {
        assertFalse(ProfileFieldSet.isProfileScoped("profilesOwnControls"));
        assertTrue(new AuroraConfig().profilesOwnControls, "on by default: today's behaviour");
    }
}
