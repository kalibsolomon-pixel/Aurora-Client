package com.aurora.client.ui.util;

import com.aurora.client.config.AuroraConfig;
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
    private boolean italic, underline, rendering;
    private String property;

    @BeforeEach void setUp() {
        config = AuroraConfig.get();
        font = config.activeFont;
        weight = config.textWeight;
        mode = config.textRendererMode;
        italic = config.textItalic;
        underline = config.textUnderline;
        rendering = AuroraFontRenderer.isRenderingAuroraUI();
        property = System.getProperty("aurora.text.sdf");
        System.clearProperty("aurora.text.sdf");
        AuroraFontRenderer.setRenderingAuroraUI(true);
        config.textRendererMode = AuroraConfig.TextRendererMode.AURORA_ONLY;
        config.textUnderline = false;
    }

    @AfterEach void tearDown() {
        config.activeFont = font;
        config.textWeight = weight;
        config.textRendererMode = mode;
        config.textItalic = italic;
        config.textUnderline = underline;
        AuroraFontRenderer.setRenderingAuroraUI(rendering);
        if (property == null) System.clearProperty("aurora.text.sdf");
        else System.setProperty("aurora.text.sdf", property);
    }

    @Test void flagOffResolvesExactlyTheExistingRasterIds() {
        assertRaster(AuroraConfig.FontType.INTER, AuroraConfig.TextWeight.MEDIUM, false,
                "inter_medium");
        assertRaster(AuroraConfig.FontType.ROBOTO, AuroraConfig.TextWeight.SEMIBOLD, false,
                "roboto_semibold");
        assertRaster(AuroraConfig.FontType.INTER, AuroraConfig.TextWeight.REGULAR, true,
                "inter_regular_italic");
        assertRaster(AuroraConfig.FontType.JETBRAINS_MONO, AuroraConfig.TextWeight.BOLD, false,
                "jetbrains_mono_bold");
    }

    private void assertRaster(AuroraConfig.FontType family, AuroraConfig.TextWeight textWeight,
                              boolean textItalic, String expected) {
        config.activeFont = family;
        config.textWeight = textWeight;
        config.textItalic = textItalic;
        assertEquals(new FontDescription.Resource(Identifier.fromNamespaceAndPath("aurora", expected)),
                AuroraFontRenderer.getActiveStyle().getFont());
    }
}
