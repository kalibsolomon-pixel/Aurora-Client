package com.aurora.client.config.profile;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.ConfigDirectory;
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
        // Owner request 2026-11-12: Ctrl / Shift / Alt + a key.
        var manager = ProfileManager.getInstance();
        String plain = unique("plain"), combo = unique("combo");
        assertTrue(manager.create(plain));
        assertTrue(manager.create(combo));
        assertTrue(manager.setHotkey(plain, new ProfileHotkey(19, 0)));
        assertTrue(manager.setHotkey(combo, new ProfileHotkey(19, ProfileHotkey.CTRL | ProfileHotkey.SHIFT)));
        assertEquals(new ProfileHotkey(19, 0), manager.binding(plain), "P and Ctrl + Shift + P sit on different profiles");
        assertEquals(new ProfileHotkey(19, ProfileHotkey.CTRL | ProfileHotkey.SHIFT), manager.binding(combo));
        assertTrue(manager.switchTo(combo));
        manager.saveCurrentBlocking();
        Profile disk = GSON.fromJson(Files.readString(temporary.resolve("profiles/" + combo + ".json")), Profile.class);
        assertEquals(19, disk.hotkey);
        assertEquals(ProfileHotkey.CTRL | ProfileHotkey.SHIFT, disk.hotkeyModifiers, "a settings save keeps the modifiers");
        assertTrue(manager.setHotkey(plain, new ProfileHotkey(19, ProfileHotkey.CTRL | ProfileHotkey.SHIFT)));
        assertEquals(ProfileHotkey.NONE, manager.binding(combo), "the same combo moves, as a plain key does");
        assertTrue(manager.switchTo("default"));
        Profile old = GSON.fromJson("{\"name\":\"old\",\"hotkey\":30}", Profile.class);
        assertEquals(0, old.hotkeyModifiers, "files from before combos read as plain keys");
    }

    @Test void captureWaitsAfterAModifierAndMatchingIsExact() {
        var capture = new ProfileHotkey.Capture();
        int lctrl = KeybindMigratorBridge.glfwToStored(341), lshift = KeybindMigratorBridge.glfwToStored(340);
        assertEquals(ProfileHotkey.CTRL, ProfileHotkey.modifierOf(lctrl), "GLFW hosts store Left Ctrl as the SDL modifier");
        assertEquals(ProfileHotkey.SHIFT, ProfileHotkey.modifierOf(lshift));
        assertEquals(ProfileHotkey.ALT, ProfileHotkey.modifierOf(KeybindMigratorBridge.glfwToStored(346)), "Right Alt too");
        assertNull(capture.press(lctrl, false), "Ctrl alone waits for another key");
        assertEquals(ProfileHotkey.CTRL, capture.pending());
        assertNull(capture.press(lshift, false));
        assertEquals(new ProfileHotkey(19, ProfileHotkey.CTRL | ProfileHotkey.SHIFT), capture.press(19, false));
        assertEquals(0, capture.pending(), "a finished capture starts over");
        assertNull(capture.press(lshift, false));
        assertEquals(new ProfileHotkey(lshift, 0), capture.release(lshift), "Shift released alone binds Shift");
        assertNull(capture.press(lctrl, false));
        assertEquals(ProfileHotkey.NONE, capture.press(-1, true), "Esc or Backspace still clears");
        assertNull(capture.release(19), "releasing an ordinary key changes nothing");

        var combo = new ProfileHotkey(19, ProfileHotkey.CTRL);
        assertTrue(combo.matches(ProfileHotkey.CTRL));
        assertFalse(combo.matches(0), "P alone does not fire Ctrl + P");
        assertFalse(combo.matches(ProfileHotkey.CTRL | ProfileHotkey.SHIFT), "nor does Ctrl + Shift + P");
        assertFalse(new ProfileHotkey(19, 0).matches(ProfileHotkey.CTRL), "and P does not fire on Ctrl + P");
        assertTrue(new ProfileHotkey(lshift, 0).matches(ProfileHotkey.SHIFT), "a bound modifier ignores its own bit");
        assertEquals("Ctrl + Shift + ", ProfileHotkey.modifierPrefix(ProfileHotkey.SHIFT | ProfileHotkey.CTRL));
        assertFalse(new ProfileHotkey(-1, ProfileHotkey.ALT).bound());
        assertEquals(0, new ProfileHotkey(-1, ProfileHotkey.ALT).modifiers(), "an unbound key carries no modifiers");
    }

    /** The 26.1 / 26.2 adapters store {@code KeybindMigrator.fromGlfw(key)}. */
    private static final class KeybindMigratorBridge {
        static int glfwToStored(int glfw) { return com.aurora.client.config.KeybindMigrator.fromGlfw(glfw); }
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
        assertEquals(new ProfileHotkey(44, 0), manager.hotkeys().get(b));
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
