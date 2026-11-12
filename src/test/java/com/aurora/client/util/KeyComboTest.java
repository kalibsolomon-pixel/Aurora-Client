package com.aurora.client.util;

import com.aurora.client.SourceTree;
import com.aurora.client.config.KeybindMigrator;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.aurora.client.util.KeyCombo.*;
import static org.junit.jupiter.api.Assertions.*;

/** Ctrl / Shift / Alt combos on every keybind (owner request 2026-11-12). */
class KeyComboTest {
    private static final int P = 19, C = 6, LCTRL_STORED = KeybindMigrator.fromGlfw(341), LSHIFT_STORED = KeybindMigrator.fromGlfw(340);

    @Test void oneIntCarriesKeyAndModifiersAndOldValuesArePlain() {
        int ctrlP = of(P, CTRL);
        assertEquals(P, key(ctrlP));
        assertEquals(CTRL, modifiers(ctrlP));
        assertEquals(P, of(P, 0), "a plain key encodes as itself, so every saved binding still reads");
        assertEquals(UNBOUND, of(-1, ALT));
        assertEquals(UNBOUND, key(UNBOUND));
        assertEquals(0, modifiers(UNBOUND));
        assertEquals("Ctrl + Shift + Alt + ", prefix(ALT | SHIFT | CTRL), "a fixed order");
        assertEquals(CTRL, modifierOf(LCTRL_STORED), "GLFW hosts store Left Ctrl as the SDL modifier");
        assertEquals(SHIFT, modifierOf(LSHIFT_STORED));
        assertEquals(ALT, modifierOf(KeybindMigrator.fromGlfw(346)), "Right Alt too");
    }

    @Test void mostSpecificWins() {
        int[] live = {P, of(P, CTRL), C, of(C, CTRL | SHIFT)};
        int n = live.length;
        assertTrue(resolves(P, 0, live, n));
        assertFalse(resolves(of(P, CTRL), 0, live, n), "a combo needs its modifiers");
        assertTrue(resolves(of(P, CTRL), CTRL, live, n));
        assertFalse(resolves(P, CTRL, live, n), "P gives way to Ctrl + P while Ctrl is held");
        assertTrue(resolves(P, SHIFT, live, n), "but not to an unrelated modifier: zoom keeps working while sneaking");
        assertTrue(resolves(of(P, CTRL), CTRL | SHIFT, live, n), "nothing more specific: Ctrl + P still wins");
        assertTrue(resolves(C, CTRL, live, n), "C with only Ctrl: Ctrl + Shift + C is not matched, C stays");
        assertFalse(resolves(C, CTRL | SHIFT, live, n));
        assertTrue(resolves(of(C, CTRL | SHIFT), CTRL | SHIFT | ALT, live, n));
        int[] sprint = {LCTRL_STORED};
        assertTrue(resolves(LCTRL_STORED, CTRL, sprint, 1), "a bound modifier ignores its own bit");
    }

    @Test void captureWaitsAfterAModifier() {
        var capture = new Capture();
        assertNull(capture.press(LCTRL_STORED, false), "Ctrl alone waits for another key");
        assertEquals(CTRL, capture.pending());
        assertEquals("Ctrl + …", captureLabel(capture.pending(), "> press key <"));
        assertNull(capture.press(LSHIFT_STORED, false));
        assertEquals(of(P, CTRL | SHIFT), capture.press(P, false));
        assertEquals(0, capture.pending(), "a finished capture starts over");
        assertEquals(P, capture.press(P, false), "a plain key binds at once");
        assertNull(capture.press(LSHIFT_STORED, false));
        assertEquals(LSHIFT_STORED, capture.release(LSHIFT_STORED), "Shift released alone binds Shift");
        assertNull(capture.press(LCTRL_STORED, false));
        assertNull(capture.press(LSHIFT_STORED, false));
        assertEquals(of(LSHIFT_STORED, CTRL), capture.release(LSHIFT_STORED), "Ctrl held, Shift tapped: Ctrl + Shift");
        assertNull(capture.press(LCTRL_STORED, false));
        assertEquals(UNBOUND, capture.press(-1, true), "Esc or Backspace still clears");
        assertNull(capture.release(P), "releasing an ordinary key changes nothing");
    }

    @Test void profileControlsKeepTheModifiersBesideVanillasString() {
        assertEquals("key.keyboard.p", controlsString("key.keyboard.p", 0), "plain keys keep vanilla's own string");
        String ctrlP = controlsString("key.keyboard.p", CTRL);
        assertEquals("key.keyboard.p|1", ctrlP);
        assertEquals("key.keyboard.p", controlsKey(ctrlP));
        assertEquals(CTRL, controlsModifiers(ctrlP));
        assertEquals(0, controlsModifiers("key.keyboard.p"), "profiles saved before combos read as plain");
        assertEquals(0, controlsModifiers("key.keyboard.p|x"));
    }

    @Test void registryKnowsWhenThereAreNoCombos() {
        Registry.begin();
        Registry.add(P);
        Registry.add(UNBOUND);
        Registry.commit();
        assertEquals(1, Registry.count());
        assertFalse(Registry.hasCombos(), "no combos: vanilla key handling runs untouched");
        Registry.begin();
        for (int i = 0; i < 300; i++) Registry.add(i);
        Registry.add(of(P, ALT));
        Registry.commit();
        assertEquals(301, Registry.count(), "grows past its first array");
        assertTrue(Registry.hasCombos());
        Registry.begin();
        Registry.commit();
    }

    @Test void everyKeyPathGoesThroughTheResolver() throws Exception {
        String key = SourceTree.read("util/AuroraKey.java");
        assertTrue(key.contains("return KeyCombo.resolves(binding, heldModifiers());"), "Aurora's polled keybinds");
        String mapping = SourceTree.read("mixin/KeyMappingComboMixin.java");
        assertTrue(mapping.contains("if (!KeyCombo.Registry.hasCombos()) return false;"), "vanilla untouched without combos");
        assertTrue(mapping.contains("if (state && aurora$dispatch(key, false, true)) ci.cancel();"),
                "a release reaches every mapping, so a combo lets go when its modifier came up first");
        String sync = SourceTree.read("util/AuroraKeybinds.java");
        assertTrue(sync.contains("VanillaKeyCombos.setModifiers(mapping, KeyCombo.modifiers(binding));"));
        assertTrue(sync.contains("return VanillaKeyCombos.binding(mapping);"),
                "the mirror compares whole bindings (a raw encoded int must never reach a GLFW lookup)");
        for (String v : new String[]{"26.1", "26.2", "26.3"}) {
            String keystrokes = Files.readString(Path.of("adapters/minecraft-" + v + "/src/client/java/com/aurora/client/hud/module/KeystrokesModule.java"));
            assertFalse(keystrokes.contains("AuroraKey.isDown(boxed)"), v + ": display keys show the physical key");
        }
        assertTrue(SourceTree.read("screen/setting/KeybindSetting.java").contains("Integer done = capture.press("));
    }
}
