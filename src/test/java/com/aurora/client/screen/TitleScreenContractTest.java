package com.aurora.client.screen;

import com.aurora.client.SourceTree;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The redesigned title screen (owner 2026-11-12, after OneClient's). */
class TitleScreenContractTest {
    private static final String SCREEN = SourceTree.read("screen/AuroraTitleScreen.java");

    @Test void oneSharedScreenWithTheReferenceButtonsInOrder() {
        for (String v : new String[]{"26.1", "26.2", "26.3"}) {
            assertFalse(Files.exists(Path.of("adapters/minecraft-" + v + "/src/client/java/com/aurora/client/screen/AuroraTitleScreen.java")),
                    v + " no longer carries its own copy");
        }
        String[] order = {
                "addCard(0, Component.translatable(\"aurora.button.singleplayer\"), \"person\"",
                "addCard(1, Component.translatable(\"aurora.button.multiplayer\"), \"group\"",
                "addCard(2, Component.translatable(\"menu.online\"), \"public\"",
                "addCard(3, Component.translatable(\"aurora.button.settings\"), \"settings\"",
                "addCard(4, Component.translatable(\"aurora.button.mods\"), \"extension\""};
        int at = -1;
        for (String card : order) {
            int next = SCREEN.indexOf(card);
            assertTrue(next > at, card);
            at = next;
        }
        assertTrue(SCREEN.contains("new SelectWorldScreen(this)"));
        assertTrue(SCREEN.contains("new JoinMultiplayerScreen(this)"));
        assertTrue(SCREEN.contains("new RealmsMainScreen(this)"));
        assertTrue(SCREEN.contains("NativeUi.optionsScreen(this.minecraft, this)"));
        assertTrue(SCREEN.contains("new CreditsAndAttributionScreen(this)"), "the copyright line opens the credits, as vanilla's");
    }

    @Test void modsOpensModMenuAndIsDisabledWithoutIt() {
        assertTrue(SCREEN.contains("AuroraModMenuApi.open(this)"));
        assertTrue(SCREEN.contains("if (!AuroraModMenuApi.available()) {\n            mods.active = false;"));
        assertTrue(SCREEN.contains("aurora.title.modmenu_missing"));
        assertFalse(SCREEN.contains("openOrFallback"), "Mods never falls back to Aurora; Aurora has its own button");
    }

    @Test void topBarIsAuroraFullscreenQuit() {
        int aurora = SCREEN.indexOf("composition.icon(0)");
        int fullscreen = SCREEN.indexOf("composition.icon(1)");
        int quit = SCREEN.indexOf("composition.icon(2)");
        assertTrue(aurora >= 0 && aurora < fullscreen && fullscreen < quit);
        assertTrue(SCREEN.contains("IconButton.brand("));
        assertTrue(SCREEN.contains("option.set(!option.get());"), "fullscreen goes through the video option, as the settings do");
        assertTrue(SCREEN.contains("() -> this.minecraft.stop()"));
    }

    @Test void accountCardOnlyShowsTheSignedInAccountAndNeverTheNetwork() {
        assertTrue(SCREEN.contains("this.minecraft.getSkinManager().createLookup(this.minecraft.getGameProfile(), false)"),
                "the skin comes through Minecraft's own lookup");
        for (String banned : new String[]{"ServerStatusPinger", "ServerList", "HttpClient", "URLConnection", "getAccessToken", "Session"}) {
            assertFalse(SCREEN.contains(banned), banned);
        }
        assertTrue(SCREEN.contains("Add and switch accounts in Aurora Launcher."));
    }

    @Test void wordmarkHasItsOwnFontAndIsNeverReCased() throws Exception {
        assertTrue(SCREEN.contains("Identifier.fromNamespaceAndPath(\"aurora\", \"wordmark\")"));
        assertTrue(SCREEN.contains(".withStyle(WORDMARK).getVisualOrderText()"), "an explicit font: the text options leave it alone");
        assertTrue(Files.readString(Path.of("adapters/shared/src/client/resources/assets/aurora/font/wordmark.json"))
                .contains("\"file\": \"aurora:poppins-bold.ttf\""));
    }

    @Test void atmosphereAllocatesOnceAndHonoursReducedMotion() {
        String atmosphere = SourceTree.read("ui/util/MenuAtmosphere.java");
        int draw = atmosphere.indexOf("public void draw(");
        int end = atmosphere.indexOf("public void dispose()");
        String body = atmosphere.substring(draw, end);
        assertFalse(body.contains("new "), "a frame allocates nothing");
        assertTrue(atmosphere.contains("screenEffectScale().get() <= 0.0"));
        assertTrue(atmosphere.contains("if (reducedMotion()) dt = 0;"));
        assertTrue(SCREEN.contains("atmosphere.draw(ctx, this.width, this.height);"));
        assertTrue(SCREEN.contains("atmosphere.dispose();"), "its textures go when the screen does");
        assertFalse(SCREEN.contains("renderMenuPanorama"), "no panorama and no glass behind the menu");
    }
}
