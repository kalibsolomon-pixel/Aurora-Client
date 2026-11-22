package com.aurora.client.ui.component;

import com.aurora.client.SourceTree;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialSurfaceContractTest {

    private static final Path SOURCE = SourceTree.resolve(
            SourceTree.ADAPTER + "/src/client/java/com/aurora/client/ui/component/MaterialSurface.java");

    @Test void retentionInvalidatesOnThemeScaleAndRadiusWithoutScreenOwnership() throws Exception {
        String source=Files.readString(SOURCE);
        assertTrue(source.contains("templateGeneration!=generation"));
        assertTrue(source.contains("templateScale!=scale"));
        assertTrue(source.contains("templateRadius!=radius"));
        assertFalse(source.contains("templateOwner"));
        assertFalse(source.contains("cache.dispose()"),"all template disposal must be deferred");
    }

    @Test void windowTemplatesRetireWhenReturningToGameplay() {
        String glass = SourceTree.read("ui/component/GlassSurface.java");
        int frameBegin = glass.indexOf("public static void beginFrame()");
        int retire = glass.indexOf("MaterialSurface.beginFrame();", frameBegin);
        int drain = glass.indexOf("UiLayerCache.beginFrame();", frameBegin);
        assertTrue(frameBegin >= 0 && retire > frameBegin && drain > retire);
        String material = SourceTree.read("ui/component/MaterialSurface.java");
        assertTrue(material.contains("NativeUi.screen(mc)==null) {\n            if(TEMPLATES.size()>0) disposeTemplates();"));
        assertTrue(material.contains("TEMPLATES.clear();"));
        assertTrue(material.contains("cache.windowFace(raster, fill, pad, logicalW, logicalH);"));
    }

    @Test void bevelIsTheOnlyWindowFrameAndNeverCapturesTheBackdrop() {
        String material = SourceTree.read("ui/component/MaterialSurface.java");
        int parent = material.indexOf("public static void parent(");
        int finish = material.indexOf("windowFinish(g, x, y, w, h, radius, contentInset, 1, true);", parent);
        assertTrue(parent >= 0 && finish > parent);
        // One frame since 2026-11-18: no level switch and no flat legacy outline behind it.
        assertFalse(material.substring(parent, material.indexOf("\n    }", finish)).contains("windowOutline()"));
        assertFalse(material.contains("windowBevelEnabled"));
        assertFalse(material.contains("backgroundOpacity()"));
        for (String host : new String[]{"AuroraScreen", "FeatureDetailScreen", "ManagerListScreen", "IconPickerScreen",
                "ProfileOptionsScreen", "ColorPickerScreen", "ResourcePackBrowserScreen", "ResourcePackManagerScreen"}) {
            String code = SourceTree.read("screen/" + host + ".java");
            int dim = code.indexOf("GlassSurface.overlayDim(", code.indexOf("GlassSurface.beginGlassPass()"));
            assertTrue(dim >= 0 && code.indexOf("MaterialSurface.parent(", dim) > dim, host);
        }
    }

    @Test
    void parentWindowHostsSuppressStandaloneChildCaptures() throws Exception {
        String glass = Files.readString(SourceTree.resolve(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/ui/component/GlassSurface.java"));
        int card = glass.indexOf("public static boolean card(");
        int container = glass.indexOf("public static boolean container(", card);
        String body = glass.substring(card, container);
        assertTrue(body.contains("screen.parentGlassOnly()) return false"));
        assertTrue(body.indexOf("return false") < body.indexOf("return paint"));
        for (String host : new String[]{"AuroraScreen", "FeatureDetailScreen"}) {
            String source = Files.readString(SourceTree.resolve(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/" + host + ".java"));
            assertTrue(source.contains("boolean parentGlassOnly() { return true; }"));
        }
    }

    @Test
    void embeddedChildrenDoNotOwnBlurOrGlassRims() throws Exception {
        String source = Files.readString(SOURCE);
        assertFalse(source.contains("BlurPanelRenderer"));
        assertFalse(source.contains("drawRimFinish"));
        assertTrue(source.contains("ThemeToken.SURFACE_VARIANT"));
        assertTrue(source.contains("ThemeManager.stainedTint()"));
    }

    @Test
    void floatingSurfaceUsesSemanticMaterialTokens() throws Exception {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("ThemeToken.SURFACE"));
        assertTrue(source.contains("ThemeToken.BORDER"));
        assertFalse(source.contains("ContrastDerivations"));
        assertFalse(source.contains("AdaptiveOnAccentTreatment"));
    }
}
