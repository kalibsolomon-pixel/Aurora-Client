package com.aurora.client.screen;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Phase C-2 source-contract pins for AuroraScreen's semantic host boundary. */
class AuroraScreenSemanticHostingTest {
    private static final Path SCREEN = Path.of(
            "src/client/java/com/aurora/client/screen/AuroraScreen.java");
    private static final Path REGISTRY = Path.of(
            "src/client/java/com/aurora/client/screen/FeatureRegistry.java");

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException e) {
            return fail(path + " not readable: " + e);
        }
    }

    private static String settingsRegistryBlock() {
        String src = read(REGISTRY);
        int start = src.indexOf("// ============ SETTINGS TAB ============");
        int end = src.indexOf("addWithSettings(MODULES, \"animations\"", start);
        assertTrue(start >= 0 && end > start, "settings registry block moved unexpectedly");
        return src.substring(start, end);
    }

    @Test
    void exactInlineInventoryIsReconfirmed() {
        String settings = settingsRegistryBlock();
        assertEquals(3, count(settings, "new EnumSetting<>("), "hostable enum triggers");
        assertEquals(1, count(settings, "new AccentSetting("), "one ten-peer accent row");
        assertEquals(3, count(settings, "new SegmentedSetting<>("), "C-5 rows");
        assertEquals(1, count(settings, "new ThemeOpacitySetting("), "deferred continuous control");
        assertEquals(1, count(settings, "new ThemePreviewSetting("), "preview/data row");
    }

    @Test
    void hostMaterializesOnlyComponentOwnedContractsDuringInit() {
        String src = read(SCREEN);
        int init = src.indexOf("protected void init()");
        int render = src.indexOf("public void render(", init);
        String initBody = src.substring(init, render);
        assertTrue(initBody.contains("List.copyOf(setting.interactionControls())"));
        assertTrue(initBody.contains("semanticHost.register(control)"));
        assertFalse(src.contains("instanceof EnumSetting"));
        assertFalse(src.contains("instanceof AccentSetting"));
        assertFalse(src.contains("instanceof SegmentedSetting"),
                "C-2 must not manufacture C-5 controls");
        assertFalse(src.contains("instanceof ThemeOpacitySetting"),
                "C-2 must not manufacture a slider contract");
    }

    @Test
    void registrationIsNotInTheFramePathAndResizeRebuilds() {
        String src = read(SCREEN);
        int init = src.indexOf("protected void init()");
        int render = src.indexOf("public void render(", init);
        String initBody = src.substring(init, render);
        String renderAndAfter = src.substring(render);
        assertTrue(initBody.contains("semanticHost.beginRebuild()"));
        assertTrue(initBody.contains("semanticHost.finishRebuild()"));
        assertFalse(renderAndAfter.contains("semanticHost.register("),
                "semantic controls must not be allocated/registered per frame");
    }

    @Test
    void availabilityUsesTheC1ClipBandAndActionableControlRect() {
        String src = read(SCREEN);
        assertTrue(src.contains("boolean available = visible && vp.intersects(\n"
                + "                                control.getX(), control.getY(),\n"
                + "                                control.getWidth(), control.getHeight());"));
        assertTrue(src.contains("semanticHost.beginAvailabilitySweep()"));
        assertTrue(src.contains("semanticHost.finishAvailabilitySweep()"));
        assertTrue(src.contains("controls.isEmpty() ? visible : anyControlAvailable"));
    }

    @Test
    void tabSwitchInvalidatesAndPointerFocusDoesNotActivateTwice() {
        String src = read(SCREEN);
        // C-2b: the manual `selectedCategory = i` assignment is gone — tab
        // clicks route through the tab controls into one selectCategory
        // path, which invalidates the host on every real change.
        assertTrue(src.contains("private void selectCategory(int i)"));
        assertTrue(src.contains("semanticHost.deactivateAll();"));
        assertTrue(src.contains("semanticHost.focusAt(mouseX, mouseY);"));
        assertFalse(src.contains("selectedCategory = i;\n                return true;"),
                "tab clicks must not bypass the semantic action");
        assertEquals(1, count(src, "s.mouseClicked(mouseX, mouseY, button"),
                "the component remains the sole manual pointer activation route");
    }

    @Test
    void searchPrecedesSemanticChildrenAndPreviewMocksStayIsolated() {
        String src = read(SCREEN);
        int search = src.indexOf("this.addWidget(searchField)");
        int semantics = src.indexOf("semanticHost.finishRebuild()", search);
        assertTrue(search >= 0 && semantics > search,
                "search is the explicit first child; semantic controls follow");

        String preview = read(Path.of(
                "src/client/java/com/aurora/client/screen/setting/ThemePreviewSetting.java"));
        assertFalse(preview.contains("interactionControl("));
        assertTrue(preview.contains("// Non-interactive preview: never consume."));
    }

    private static int count(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
