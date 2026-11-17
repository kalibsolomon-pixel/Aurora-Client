package com.aurora.client.ui.text;

import com.aurora.client.SourceTree;
import com.aurora.client.ui.util.SdfTextMath;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SdfGlyphTest {
    private static final int ASCII_START = 32;
    private static final int ASCII_END = 126;

    private static Path font(String name) {
        return SourceTree.resolve(SourceTree.ADAPTER + "/src/client/resources/assets/aurora/font/" + name + ".ttf");
    }

    @Test void printableAsciiUsesTheExactRasterMetricsAtAllRoleSizes() throws Exception {
        for (String face : new String[]{"inter-medium", "fira-sans-medium", "jetbrains-mono-bold-italic"}) {
            byte[] bytes = Files.readAllBytes(font(face));
            for (int size : new int[]{8, 9, 11, 20}) for (int scale : new int[]{1, 2, 3}) {
                var memory = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
                try (var stack = MemoryStack.stackPush()) {
                    var pointer = stack.mallocPointer(1);
                    synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                        assertEquals(0, FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), memory, 0, pointer));
                    }
                    try (var raster = new TrueTypeGlyphProvider(memory, FT_Face.create(pointer.get(0)), size, scale, 0, 0, "")) {
                        var sdf = new SdfGlyphProvider(raster, null);
                        float rasterPen = 0, sdfPen = 0;
                        for (int codePoint = ASCII_START; codePoint <= ASCII_END; codePoint++) {
                            var expected = raster.getGlyph(codePoint).info();
                            var actual = sdf.getGlyph(codePoint).info();
                            assertSame(expected, actual);
                            assertEquals(expected.getAdvance(false), actual.getAdvance(false));
                            assertEquals(expected.getAdvance(true), actual.getAdvance(true));
                            rasterPen += expected.getAdvance(false);
                            sdfPen += actual.getAdvance(false);
                            assertEquals(rasterPen, sdfPen);
                        }
                    }
                }
            }
        }
    }

    @Test void firstUseRasterizationHasAnOutsideBorderAndReportsItsCpuCost() throws Exception {
        int samples = ASCII_END - ASCII_START;
        long[] times = new long[samples];
        try (var input = Files.newInputStream(font("inter-medium"))) {
            long start = System.nanoTime();
            try (var strike = SdfStrike.open(input)) {
                long open = System.nanoTime() - start;
                assertNull(strike.render(ASCII_START));
                for (int index = 0; index < samples; index++) {
                    start = System.nanoTime();
                    var bitmap = strike.render(ASCII_START + index + 1);
                    times[index] = System.nanoTime() - start;
                    assertNotNull(bitmap);
                    assertTrue(bitmap.width() > 2 * (SdfTextMath.SPREAD + SdfTextMath.PADDING));
                    assertTrue(bitmap.height() > 2 * (SdfTextMath.SPREAD + SdfTextMath.PADDING));
                    byte[] pixels = bitmap.pixels();
                    boolean inside = false;
                    for (int row = 0; row < bitmap.height(); row++) for (int column = 0; column < bitmap.width(); column++) {
                        int value = Byte.toUnsignedInt(pixels[row * bitmap.width() + column]);
                        if (row == 0 || column == 0 || row == bitmap.height() - 1 || column == bitmap.width() - 1) {
                            assertEquals(0, value);
                        } else if (value > SdfTextMath.EDGE_BYTE) {
                            inside = true;
                        }
                    }
                    assertTrue(inside);
                }
                long total = Arrays.stream(times).sum();
                Arrays.sort(times);
                System.out.printf(java.util.Locale.ROOT,
                        "SDF CPU first use: face open %.3f ms; %d glyphs %.3f ms total, %.3f ms median, %.3f ms p95%n",
                        open / 1e6, samples, total / 1e6, times[samples / 2] / 1e6,
                        times[(int) Math.ceil(samples * .95) - 1] / 1e6);
            }
        }
    }
}
