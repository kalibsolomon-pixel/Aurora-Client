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
