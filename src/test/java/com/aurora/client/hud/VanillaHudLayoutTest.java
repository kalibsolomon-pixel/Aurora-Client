package com.aurora.client.hud;

import com.aurora.client.SourceTree;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.module.HudModuleManager;
import com.aurora.client.hud.module.VanillaHudModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Pure geometry, module wiring, and cross-version contract tests for Vanilla HUD control. */
class VanillaHudLayoutTest {

    @AfterEach
    void restoreConfig() {
        AuroraConfig.get().resetByPrefix("vanillaHud");
    }

    @Test void defaultsMatchVanillaCoordinatesAtUnitScale() {
        int sw = 640, sh = 360;

        var boss = VanillaHudLayout.Element.BOSS_BAR;
        var bossT = VanillaHudLayout.topLeft(boss.defaultAnchor, boss.defaultOffsetX, boss.defaultOffsetY, 1.0f,
                boss.width, boss.height, sw, sh);
        assertTrue(bossT.isIdentity(sw * 0.5f - 91f, 3f), "boss bar default matches vanilla: " + bossT);

        var action = VanillaHudLayout.Element.ACTION_BAR;
        var actionT = VanillaHudLayout.center(action.defaultAnchor, action.defaultOffsetX, action.defaultOffsetY, 1.0f,
                action.width, action.height, sw, sh);
        assertTrue(actionT.isIdentity(sw * 0.5f, sh - 68f), "action bar default matches vanilla: " + actionT);

        var title = VanillaHudLayout.Element.TITLE;
        var titleT = VanillaHudLayout.center(title.defaultAnchor, title.defaultOffsetX, title.defaultOffsetY, 1.0f,
                title.width, title.height, sw, sh);
        assertTrue(titleT.isIdentity(sw * 0.5f, sh * 0.5f - 22f), "title default matches vanilla: " + titleT);

        var subtitle = VanillaHudLayout.Element.SUBTITLE;
        var subT = VanillaHudLayout.center(subtitle.defaultAnchor, subtitle.defaultOffsetX, subtitle.defaultOffsetY, 1.0f,
                subtitle.width, subtitle.height, sw, sh);
        assertTrue(subT.isIdentity(sw * 0.5f, sh * 0.5f + 19f), "subtitle default matches vanilla: " + subT);

        var toast = VanillaHudLayout.Element.TOASTS;
        var fullyShown = VanillaHudLayout.toast(toast.defaultAnchor, toast.defaultOffsetX, toast.defaultOffsetY, 1.0f,
                sw, sh, 160, 32, 0, 1.0f);
        assertEquals(sw - 160f, fullyShown.x(), 0.01f);
        assertEquals(0f, fullyShown.y(), 0.01f);

        var halfSlide = VanillaHudLayout.toast(toast.defaultAnchor, toast.defaultOffsetX, toast.defaultOffsetY, 1.0f,
                sw, sh, 160, 32, 2, 0.5f);
        assertEquals(sw - 80f, halfSlide.x(), 0.01f);
        assertEquals(64f, halfSlide.y(), 0.01f);
    }

    @Test void toastPlacementAdaptsSlideDirectionScaleAndBottomStacking() {
        int sw = 800, sh = 450;

        // Top-left at 0.75x slides in from the left and stacks downward.
        var leftHalf = VanillaHudLayout.toast(HudAnchor.TOP_LEFT, 10, 12, 0.75f, sw, sh, 160, 32, 1, 0.5f);
        assertEquals(10f - 0.5f * 160f * 0.75f, leftHalf.x(), 0.01f);
        assertEquals(12f + 32f * 0.75f, leftHalf.y(), 0.01f);
        assertEquals(0.75f, leftHalf.scale(), 0.001f);

        // Bottom-right stacks upward so stacked toasts stay inside the viewport.
        var bottomSlot0 = VanillaHudLayout.toast(HudAnchor.BOTTOM_RIGHT, -8, -8, 1.0f, sw, sh, 160, 32, 0, 1.0f);
        var bottomSlot1 = VanillaHudLayout.toast(HudAnchor.BOTTOM_RIGHT, -8, -8, 1.0f, sw, sh, 160, 32, 1, 1.0f);
        assertEquals(bottomSlot0.y() - 32f, bottomSlot1.y(), 0.01f);

        // Top-center slides vertically from above without horizontal shift.
        var centerStart = VanillaHudLayout.toast(HudAnchor.TOP_CENTER, 0, 6, 1.0f, sw, sh, 160, 32, 0, 0.0f);
        var centerEnd = VanillaHudLayout.toast(HudAnchor.TOP_CENTER, 0, 6, 1.0f, sw, sh, 160, 32, 0, 1.0f);
        assertEquals(centerStart.x(), centerEnd.x(), 0.01f);
        assertEquals(centerEnd.y() - 32f, centerStart.y(), 0.01f);
    }

    @Test void titleAlphaMatchesVanillaFadeInStayAndFadeOut() {
        // fadeIn=10, stay=70, fadeOut=20 (vanilla defaults: total 100 ticks)
        assertEquals(0, VanillaHudLayout.titleAlpha(0, 10, 70, 20, 0f));
        assertEquals(0, VanillaHudLayout.titleAlpha(100, 10, 70, 20, 0f));
        assertEquals(127, VanillaHudLayout.titleAlpha(95, 10, 70, 20, 0f));
        assertEquals(255, VanillaHudLayout.titleAlpha(50, 10, 70, 20, 0f));
        assertEquals(127, VanillaHudLayout.titleAlpha(10, 10, 70, 20, 0f));
        assertEquals(255, VanillaHudLayout.titleAlpha(20, 10, 70, 20, 0f));
        assertEquals(255, VanillaHudLayout.titleAlpha(5, 0, 10, 0, 0f), "zero fade times never divide by zero");
    }

    @Test void modulesRegisterAllSixElementsAndHonorConfigAndEditorVisibility() {
        HudModuleManager mgr = new HudModuleManager();
        VanillaHudModule.registerAll(mgr);

        AuroraConfig cfg = AuroraConfig.get();
        assertFalse(cfg.vanillaHudEnabled, "Vanilla HUD ships off in a fresh install");

        for (VanillaHudLayout.Element el : VanillaHudLayout.Element.values()) {
            VanillaHudModule mod = VanillaHudModule.of(el);
            assertNotNull(mod, el.id + " must register");
            assertSame(mod, mgr.get(el.id));
            assertEquals("vanilla_hud", mod.featureRegistryId());
            assertFalse(mod.isConfigEnabled(), "off while vanillaHudEnabled is false");
        }

        cfg.vanillaHudEnabled = true;
        assertTrue(VanillaHudModule.of(VanillaHudLayout.Element.BOSS_BAR).isConfigEnabled());
        assertTrue(VanillaHudModule.isVisible(VanillaHudLayout.Element.BOSS_BAR));

        // Hiding via config switch hides the element in game and from the editor.
        cfg.vanillaHudShowBossBar = false;
        assertFalse(VanillaHudModule.isVisible(VanillaHudLayout.Element.BOSS_BAR));
        assertFalse(VanillaHudModule.of(VanillaHudLayout.Element.BOSS_BAR).isConfigEnabled());
        assertTrue(VanillaHudModule.isVisible(VanillaHudLayout.Element.ACTION_BAR), "independent per element");

        // Hiding via HUD editor right-click (module.enabled = false) hides in game while keeping editor frame.
        cfg.vanillaHudShowBossBar = true;
        VanillaHudModule.of(VanillaHudLayout.Element.TITLE).enabled = false;
        assertFalse(VanillaHudModule.isVisible(VanillaHudLayout.Element.TITLE));
        assertTrue(VanillaHudModule.isVisible(VanillaHudLayout.Element.SUBTITLE), "title and subtitle hide independently");

        cfg.resetByPrefix("vanillaHud");
        assertFalse(cfg.vanillaHudEnabled);
        assertTrue(cfg.vanillaHudShowBossBar);
    }

    @Test void mixinsAndRegistrationExistAcrossAllThreeMinecraftLines() throws Exception {
        for (String v : new String[]{"26.1", "26.2", "26.3"}) {
            String manifest = Files.readString(Path.of("adapters/minecraft-" + v + "/src/client/resources/aurora.mixins.json"));
            assertTrue(manifest.contains("\"VanillaBossBarMixin\""), v + " manifest");
            assertTrue(manifest.contains("\"VanillaHudOverlayMixin\""), v + " manifest");
            assertTrue(manifest.contains("\"VanillaToastMixin\""), v + " manifest");

            String client = Files.readString(Path.of("adapters/minecraft-" + v + "/src/client/java/com/aurora/client/AuroraClient.java"));
            assertTrue(client.contains("VanillaHudModule.registerAll(moduleManager);"), v + " AuroraClient registration");

            String overlayMixin = Files.readString(Path.of("adapters/minecraft-" + v + "/src/client/java/com/aurora/client/mixin/VanillaHudOverlayMixin.java"));
            assertTrue(overlayMixin.contains("extractOverlayMessage") && overlayMixin.contains("extractTitle"), v + " overlay mixin");
        }
        String toastMixin = SourceTree.read("mixin/VanillaToastMixin.java");
        assertTrue(toastMixin.contains("ToastManager$ToastInstance"));
        assertTrue(toastMixin.contains("vanillaHudEnabled"));
    }
}
