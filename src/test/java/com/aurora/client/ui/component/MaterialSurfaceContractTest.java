package com.aurora.client.ui.component;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialSurfaceContractTest {

    private static final Path SOURCE = Path.of(
            "src/client/java/com/aurora/client/ui/component/MaterialSurface.java");

    @Test
    void parentWindowHostsSuppressStandaloneChildCaptures() throws Exception {
        String glass = Files.readString(Path.of("src/client/java/com/aurora/client/ui/component/GlassSurface.java"));
        int card = glass.indexOf("public static boolean card(");
        int container = glass.indexOf("public static boolean container(", card);
        String body = glass.substring(card, container);
        assertTrue(body.contains("screen.parentGlassOnly()) return false"));
        assertTrue(body.indexOf("return false") < body.indexOf("return paint"));
        for (String host : new String[]{"AuroraScreen", "FeatureDetailScreen"}) {
            String source = Files.readString(Path.of("src/client/java/com/aurora/client/screen/" + host + ".java"));
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
