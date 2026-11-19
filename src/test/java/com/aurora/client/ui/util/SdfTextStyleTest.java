package com.aurora.client.ui.util;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.health.HealthTestAccess;
import com.aurora.client.ui.text.SdfTextSupport;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SdfTextStyleTest {
    private AuroraConfig config;
    private AuroraConfig.FontType font;
    private AuroraConfig.TextWeight weight;
    private AuroraConfig.TextRendererMode mode;
    private boolean italic, underline;

    @BeforeEach void setUp() {
        config = AuroraConfig.get();
        font = config.activeFont;
        weight = config.textWeight;
        mode = config.textRendererMode;
        italic = config.textItalic;
        underline = config.textUnderline;
        config.textRendererMode = AuroraConfig.TextRendererMode.ALL_TEXT;
        config.textUnderline = false;
    }

    @AfterEach void tearDown() {
        config.activeFont = font;
        config.textWeight = weight;
        config.textRendererMode = mode;
        config.textItalic = italic;
        config.textUnderline = underline;
    }

    @Test void sdfAliasAddsOnlyTheSuffixToTheRasterId() {
        assertRaster(AuroraConfig.FontType.INTER, AuroraConfig.TextWeight.MEDIUM, false,
                "inter_medium");
        assertRaster(AuroraConfig.FontType.ROBOTO, AuroraConfig.TextWeight.SEMIBOLD, false,
                "roboto_semibold");
        assertRaster(AuroraConfig.FontType.INTER, AuroraConfig.TextWeight.REGULAR, true,
                "inter_regular_italic");
        assertRaster(AuroraConfig.FontType.JETBRAINS_MONO, AuroraConfig.TextWeight.BOLD, false,
                "jetbrains_mono_bold");
    }

    @Test void offAndWindowOnlyOutsideAWindowDoNotSubstitute() {
        config.activeFont = AuroraConfig.FontType.INTER;
        config.textRendererMode = AuroraConfig.TextRendererMode.OFF;
        assertEquals(null, AuroraFontRenderer.getActiveStyle());
        config.textRendererMode = AuroraConfig.TextRendererMode.AURORA_ONLY;
        assertEquals(null, AuroraFontRenderer.getActiveStyle());
    }

    @Test void failedOrYieldedSdfHookPreservesTheRasterStyle() {
        config.activeFont = AuroraConfig.FontType.INTER;
        config.textWeight = AuroraConfig.TextWeight.MEDIUM;
        config.textItalic = false;
        var raster = new FontDescription.Resource(Identifier.fromNamespaceAndPath("aurora", "inter_medium"));
        try {
            HealthTestAccess.fail("SdfGlyphRenderStateMixin");
            assertEquals(raster, AuroraFontRenderer.getActiveStyle().getFont());
            assertFalse(SdfTextSupport.isSdfStyle(AuroraFontRenderer.getActiveStyle()));
        } finally {
            HealthTestAccess.reset(null);
        }
        try {
            HealthTestAccess.standDown("SdfFontManagerMixin", "handled by modernui");
            assertEquals(raster, AuroraFontRenderer.getActiveStyle().getFont());
        } finally {
            HealthTestAccess.reset(null);
        }
    }

    @Test void hudUsesSdfWhenTheActiveFontAndScopeAllowIt() {
        config.activeFont = AuroraConfig.FontType.INTER;
        boolean wasDrawingHud = AuroraFontRenderer.isDrawingHud();
        AuroraFontRenderer.setDrawingHud(true);
        try {
            assertTrue(SdfTextSupport.isSdfStyle(AuroraFontRenderer.getActiveStyle()));
            config.activeFont = AuroraConfig.FontType.SANSSERIF;
            assertFalse(SdfTextSupport.isSdfStyle(AuroraFontRenderer.getActiveStyle()));
            config.activeFont = AuroraConfig.FontType.INTER;
            config.textRendererMode = AuroraConfig.TextRendererMode.AURORA_ONLY;
            assertFalse(SdfTextSupport.isSdfStyle(AuroraFontRenderer.getActiveStyle()));
        } finally {
            AuroraFontRenderer.setDrawingHud(wasDrawingHud);
        }
    }

    private void assertRaster(AuroraConfig.FontType family, AuroraConfig.TextWeight textWeight,
                              boolean textItalic, String expected) {
        config.activeFont = family;
        config.textWeight = textWeight;
        config.textItalic = textItalic;
        assertEquals(new FontDescription.Resource(Identifier.fromNamespaceAndPath("aurora", expected + "_sdf")),
                AuroraFontRenderer.getActiveStyle().getFont());
        try {
            HealthTestAccess.fail("SdfGlyphRenderStateMixin");
            assertEquals(new FontDescription.Resource(Identifier.fromNamespaceAndPath("aurora", expected)),
                    AuroraFontRenderer.getActiveStyle().getFont(), "raster is the fallback when SDF is not working");
        } finally {
            HealthTestAccess.reset(null);
        }
    }
}
