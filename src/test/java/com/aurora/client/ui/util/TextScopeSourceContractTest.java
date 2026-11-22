package com.aurora.client.ui.util;

import com.aurora.client.SourceTree;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the text-scope wiring (owner ruling 2026-11-17) honest across all
 * three adapters: the window-scope mixin is registered and bound, the frame
 * boundary closes a scope an exception left open, the style-key memos fold
 * in applicability, and every HUD rendering path raises the HUD flag so
 * HUD text — editor previews included — never styles as window text.
 */
class TextScopeSourceContractTest {

    private static final List<String> ADAPTERS = List.of(
            "adapters/minecraft-26.1", "adapters/minecraft-26.2", "adapters/minecraft-26.3");

    private static String read(Path path) {
        try {
            return Files.readString(path).replace("\r\n", "\n");
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test void theWindowScopeMixinIsRegisteredBoundAndFailSoft() {
        String mixin = SourceTree.read("mixin/ScreenTextScopeMixin.java");
        assertTrue(mixin.contains("@Mixin(Screen.class)"), "the scope wraps the screen extraction");
        assertTrue(mixin.contains("extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"),
                "the one public final entry point all three lines call");
        assertTrue(mixin.contains("at = @At(\"HEAD\")") && mixin.contains("at = @At(\"RETURN\")"),
                "the scope opens at HEAD and closes at RETURN");
        assertTrue(mixin.contains("require = 0"), "a missed injection is reported, never fatal (ADR 0004)");
        assertTrue(mixin.contains("enterWindowScope") && mixin.contains("exitWindowScope"),
                "the scope state lives in AuroraFontRenderer");

        for (String adapter : ADAPTERS) {
            String json = read(Path.of(adapter, "src/client/resources/aurora.mixins.json"));
            assertTrue(json.contains("\"ScreenTextScopeMixin\""), adapter + " must register ScreenTextScopeMixin");
        }
        String catalog = SourceTree.read("feature/FeatureCatalog.java");
        assertTrue(catalog.contains("mixin(\"ScreenTextScopeMixin\", \"scoping the custom font to Aurora windows\", \"text_fonts\")"),
                "FeatureCatalog must bind the mixin so a failure is reported on Text & Fonts");
    }

    @Test void anExceptionCannotLeaveTheScopeOpenAcrossFrames() {
        String glass = SourceTree.read("ui/component/GlassSurface.java");
        assertTrue(glass.contains("AuroraFontRenderer.beginTextFrame()"),
                "the frame boundary must reset the window scope");
        String fonts = SourceTree.read("ui/util/AuroraFontRenderer.java");
        int begin = fonts.indexOf("public static void beginTextFrame()");
        assertTrue(begin > 0);
        assertTrue(fonts.substring(begin, fonts.indexOf('}', begin)).contains("windowScopeDepth = 0"));
    }

    @Test void measurementMemosFoldInApplicability() {
        String fonts = SourceTree.read("ui/util/AuroraFontRenderer.java");
        assertTrue(fonts.contains("styleKey(AuroraConfig.get(), compactUi()) * 2 + (getActiveStyle() != null ? 1 : 0)"),
                "a styled window measurement must never be served to an unstyled HUD draw, or vice versa");
    }

    @Test void theAuroraOnlyDecisionRunsThroughThePureScope() {
        String fonts = SourceTree.read("ui/util/AuroraFontRenderer.java");
        assertTrue(fonts.contains("TextScope.auroraOnly(drawingHud, windowScopeDepth > 0,"),
                "isAuroraSurface must use the tested truth table");
        assertTrue(fonts.contains("MixinHealth.failed(SCREEN_SCOPE_MIXIN)"),
                "AURORA_ONLY must fall back when the scope mixin is not working");
    }

    @Test void everyHudRenderingPathRaisesTheHudFlag() {
        String modules = SourceTree.read("hud/module/HudModuleManager.java");
        assertTrue(SourceTree.read("hud/HudRenderer.java").contains("setDrawingHud(true)"),
                "the HUD layer marks its pass");
        assertTrue(modules.contains("setDrawingHud(true)"),
                "renderAll marks its pass: the HUD editor's previews run through it and must look in-game");

        String tabPing = SourceTree.read("mixin/PlayerListHudMixin.java");
        assertTrue(tabPing.contains("setDrawingHud(true)"), "the tab-list ping is HUD text");

        for (String adapter : ADAPTERS) {
            String client = read(Path.of(adapter, "src/client/java/com/aurora/client/AuroraClient.java"));
            assertTrue(client.contains("setDrawingHud(true)"),
                    adapter + " must mark its HUD layers (waypoint labels, alerts)");
            String serverList = read(Path.of(adapter, "src/client/java/com/aurora/client/mixin/MultiplayerServerListWidgetMixin.java"));
            assertTrue(serverList.contains("setDrawingHud(true)"),
                    adapter + " must keep its latency label out of window scoping");
        }
    }

    @Test void windowSetupAndResizeMeasureInsideTheScope() throws Exception {
        String mixin = java.nio.file.Files.readString(java.nio.file.Path.of(
                "adapters/shared/src/client/java/com/aurora/client/mixin/ScreenTextScopeMixin.java"));
        // Text measured in init/resize must use the same style as the window's drawing.
        org.junit.jupiter.api.Assertions.assertTrue(mixin.contains("method = {\"init(II)V\", \"resize(II)V\"}, at = @At(\"HEAD\")"));
        org.junit.jupiter.api.Assertions.assertTrue(mixin.contains("method = {\"init(II)V\", \"resize(II)V\"}, at = @At(\"RETURN\")"));
    }

    @Test void interactiveLayoutRefreshesMeasureInsideTheScope() {
        // Input handlers (mouseClicked, mouseDragged, mouseScrolled) run outside
        // ScreenTextScopeMixin's render scope; refreshing layout there without
        // entering the window scope flips AURORA_ONLY to vanilla widths and shifts
        // hitboxes away from the rendered controls.
        for (String screen : List.of(
                "screen/AuroraScreen.java",
                "screen/FeatureDetailScreen.java",
                "screen/ManagerListScreen.java")) {
            String src = SourceTree.read(screen);
            assertTrue(src.contains("AuroraFontRenderer.enterWindowScope(this)"),
                    screen + " must open the window text scope when refreshing layout during input");
            assertTrue(src.contains("AuroraFontRenderer.exitWindowScope()"),
                    screen + " must close the window text scope in a finally block");
        }
    }
}
