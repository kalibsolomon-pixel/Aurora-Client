package com.aurora.client.ui.util;

import com.aurora.client.SourceTree;
import com.aurora.client.port.TextMetrics;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/** Real Minecraft/FreeType advances for the factory active font; no game window or GPU. */
class SettingResetFontTest {
    @Test void factoryFontKeepsLongLabelsAndResetTargetsInsideBothHostColumns() throws Exception {
        byte[] bytes = Files.readAllBytes(SourceTree.resolve(SourceTree.ADAPTER + "/src/client/resources/assets/aurora/font/fira-sans-medium.ttf"));
        for (int size : new int[]{8, 9}) for (int scale : new int[]{2, 3}) {
            var memory = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
            try (var stack = MemoryStack.stackPush()) {
                var pointer = stack.mallocPointer(1);
                synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                    int error = FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), memory, 0, pointer);
                    assertEquals(0, error);
                }
                try (var provider = new TrueTypeGlyphProvider(memory, FT_Face.create(pointer.get(0)), size, scale, 0, 0, "")) {
                    TextMetrics metrics = text -> {
                        float advance = 0;
                        for (int codepoint : text.codePoints().toArray()) advance += provider.getGlyph(codepoint).info().getAdvance();
                        return (int) Math.ceil(advance);
                    };
                    String longBoolean = "Disable VSync (causes tearing without VRR)";
                    String longSlider = "Underwater Fog Multiplier";
                    System.out.printf("Fira Sans Medium %dpx scale %d: Boolean=%d Slider=%d%n", size, scale, metrics.width(longBoolean), metrics.width(longSlider));
                    for (int[] viewport : new int[][]{{240, 180}, {320, 240}, {640, 360}, {1280, 720}}) {
                        var frame = UiDensity.window(viewport[0], viewport[1]);
                        for (boolean grid : new boolean[]{false, true}) {
                            // Module grid/list choice does not alter a detail/Settings row's column.
                            for (int width : new int[]{frame.width(), UiDensity.mainContent(frame).width() - 8}) {
                                for (var kind : new SettingRowLayout.Kind[]{SettingRowLayout.Kind.BOOLEAN, SettingRowLayout.Kind.SLIDER,
                                        SettingRowLayout.Kind.ENUM, SettingRowLayout.Kind.KEYBIND, SettingRowLayout.Kind.COLOR}) {
                                    String label = kind == SettingRowLayout.Kind.SLIDER ? longSlider : longBoolean;
                                    var row = SettingRowLayout.measure(kind, label, width, 9, metrics);
                                    var reset = SettingRowLayout.reset(row.label().x(), 0, row.label().width(), row.height(), metrics.width(label));
                                    assertTrue(reset.action().x() + 18 <= row.label().x() + row.label().width(),
                                            "viewport=" + viewport[0] + " grid=" + grid + " kind=" + kind);
                                    assertTrue(reset.action().x() + 18 < row.control().x());
                                    for (String line : WordWrap.lines(label, reset.label().width(), 1, metrics))
                                        assertTrue(metrics.width(line) <= reset.label().width());
                                    if (scale == 2 && grid && kind == SettingRowLayout.Kind.SLIDER)
                                        System.out.printf("  %dx%d row=%d slider label=%d fitted budget=%d icon=%d..%d value=%d%n",
                                                viewport[0], viewport[1], width, row.label().width(), reset.label().width(),
                                                reset.action().x(), reset.action().x() + 18, row.value().x());
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
