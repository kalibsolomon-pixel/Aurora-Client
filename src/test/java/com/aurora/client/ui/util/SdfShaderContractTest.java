package com.aurora.client.ui.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Specks under SDF glyphs (2026-11-22): the antialiasing width read texels outside the glyph's cell. */
class SdfShaderContractTest {
    private static final String[] LINES = {"26.1", "26.2", "26.3"};

    @Test void theEdgeWidthComesFromTheUvsOnEveryLine() throws Exception {
        for (String line : LINES) {
            String shader = Files.readString(Path.of("adapters/minecraft-" + line
                    + "/src/client/resources/assets/aurora/shaders/core/gui_text_sdf.fsh"));
            assertFalse(shader.contains("fwidth(signedDistance)"), line);
            assertTrue(shader.contains("fwidth(texCoord0 * vec2(textureSize(Sampler0, 0)))"), line);
        }
    }

    @Test void everySdfPageStartsCleared() throws Exception {
        for (String line : LINES) {
            String source = Files.readString(Path.of("adapters/minecraft-" + line
                    + "/src/client/java/com/aurora/client/ui/text/NativeSdfText.java"));
            assertTrue(source.contains("clear(page.getTexture());"), line);
            assertTrue(source.contains("new NativeImage(NativeImage.Format.LUMINANCE, w, h, true)"), line);
        }
    }
}
