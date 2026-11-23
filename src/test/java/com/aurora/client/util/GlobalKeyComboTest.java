package com.aurora.client.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GlobalKeyComboTest {

    @Test
    void globalShortcutComboAndCompetingAuroraComboResolveModifiers() {
        final int F2 = 59; // SDL scancode for F2
        final int F11 = 68; // SDL scancode for F11

        // Case 1: Screenshot bound to Ctrl + F2 -> plain F2 must not match; Ctrl + F2 matches.
        int screenshotCtrlF2 = KeyCombo.of(F2, KeyCombo.CTRL);
        int[] liveScreenshotCombo = { screenshotCtrlF2 };
        assertFalse(KeyCombo.resolves(screenshotCtrlF2, 0, liveScreenshotCombo, liveScreenshotCombo.length),
                "plain F2 must not fire a Ctrl+F2 screenshot combo");
        assertTrue(KeyCombo.resolves(screenshotCtrlF2, KeyCombo.CTRL, liveScreenshotCombo, liveScreenshotCombo.length),
                "Ctrl+F2 must fire the screenshot combo");

        // Case 2: Fullscreen bound to plain F11, Aurora combo bound to Ctrl + F11 ->
        // plain F11 wins when Ctrl is not held, but Ctrl + F11 suppresses plain F11.
        int plainFullscreen = KeyCombo.of(F11, 0);
        int auroraCtrlF11 = KeyCombo.of(F11, KeyCombo.CTRL);
        int[] liveCompeting = { plainFullscreen, auroraCtrlF11 };
        assertTrue(KeyCombo.resolves(plainFullscreen, 0, liveCompeting, liveCompeting.length),
                "plain F11 still fires fullscreen when no modifier is held");
        assertFalse(KeyCombo.resolves(plainFullscreen, KeyCombo.CTRL, liveCompeting, liveCompeting.length),
                "holding Ctrl must suppress plain F11 so the more specific Ctrl+F11 combo wins");
        assertTrue(KeyCombo.resolves(auroraCtrlF11, KeyCombo.CTRL, liveCompeting, liveCompeting.length),
                "Ctrl+F11 wins when Ctrl is held");
    }

    @Test
    void adapters262And263WireGlobalRawKeyMatchMixin() throws Exception {
        for (String adapter : new String[]{"minecraft-26.2", "minecraft-26.3"}) {
            Path mixinFile = Path.of("adapters", adapter,
                    "src/client/java/com/aurora/client/mixin/KeyMappingGlobalMatchMixin.java");
            assertTrue(Files.isRegularFile(mixinFile), "missing global key combo mixin for " + adapter);
            String src = Files.readString(mixinFile);
            assertTrue(src.contains("matches(Lcom/mojang/blaze3d/platform/InputConstants$Key;)Z"));
            assertTrue(src.contains("VanillaKeyCombos.matchesWithCombos"));

            Path manifest = Path.of("adapters", adapter, "src/client/resources/aurora.mixins.json");
            assertTrue(Files.readString(manifest).contains("\"KeyMappingGlobalMatchMixin\""),
                    "manifest must register KeyMappingGlobalMatchMixin on " + adapter);
        }
    }
}
