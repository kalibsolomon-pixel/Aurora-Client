package com.aurora.client.ui.util;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.health.HealthTestAccess;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SdfTextStyleTest {
    private AuroraConfig config;
    private AuroraConfig.FontType font;
    private AuroraConfig.TextWeight weight;
    private AuroraConfig.TextRendererMode mode;
    private boolean italic, underline, sdf;

    @BeforeEach void setUp() {
        config = AuroraConfig.get();
        font = config.activeFont;
        weight = config.textWeight;
        mode = config.textRendererMode;
        italic = config.textItalic;
        underline = config.textUnderline;
        sdf = config.textSdf;
        config.textSdf = false;
        config.textRendererMode = AuroraConfig.TextRendererMode.ALL_TEXT;
        config.textUnderline = false;
    }

    @AfterEach void tearDown() {
        config.activeFont = font;
        config.textWeight = weight;
        config.textRendererMode = mode;
        config.textItalic = italic;
        config.textUnderline = underline;
        config.textSdf = sdf;
    }

    @Test void toggleChangesOnlyTheRasterIdSuffix() {
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
        config.textSdf = true;
        config.activeFont = AuroraConfig.FontType.INTER;
        config.textRendererMode = AuroraConfig.TextRendererMode.OFF;
        assertEquals(null, AuroraFontRenderer.getActiveStyle());
        config.textRendererMode = AuroraConfig.TextRendererMode.AURORA_ONLY;
        assertEquals(null, AuroraFontRenderer.getActiveStyle());
    }

    @Test void failedOrYieldedSdfHookPreservesTheRasterStyle() {
        config.textSdf = true;
        config.activeFont = AuroraConfig.FontType.INTER;
        config.textWeight = AuroraConfig.TextWeight.MEDIUM;
        config.textItalic = false;
        var raster = new FontDescription.Resource(Identifier.fromNamespaceAndPath("aurora", "inter_medium"));
        try {
            HealthTestAccess.fail("SdfGlyphRenderStateMixin");
            assertEquals(raster, AuroraFontRenderer.getActiveStyle().getFont());
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

    private void assertRaster(AuroraConfig.FontType family, AuroraConfig.TextWeight textWeight,
                              boolean textItalic, String expected) {
        config.activeFont = family;
        config.textWeight = textWeight;
        config.textItalic = textItalic;
        config.textSdf = false;
        assertEquals(new FontDescription.Resource(Identifier.fromNamespaceAndPath("aurora", expected)),
                AuroraFontRenderer.getActiveStyle().getFont());
        config.textSdf = true;
        assertEquals(new FontDescription.Resource(Identifier.fromNamespaceAndPath("aurora", expected + "_sdf")),
                AuroraFontRenderer.getActiveStyle().getFont());
    }
}
