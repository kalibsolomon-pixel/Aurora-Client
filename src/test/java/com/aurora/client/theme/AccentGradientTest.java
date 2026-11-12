package com.aurora.client.theme;

import com.aurora.client.SourceTree;
import com.aurora.client.util.ColorEntryHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The automatic accent gradient on switch tracks and slider fills (2026-11-12). */
class AccentGradientTest {

    private static float hueDistance(int a, int b) {
        float d = Math.abs(ColorEntryHelper.argbToHsla(a)[0] - ColorEntryHelper.argbToHsla(b)[0]);
        return Math.min(d, 1f - d);
    }

    @Test void stopsStayCloseToTheAccentAndKeepItsAlpha() {
        for (int accent : new int[]{0xFF43A047, 0xFF4A6CF7, 0xFFEB0029, 0x80FFAA00, 0xFF7E57C2}) {
            int start = AccentGradient.start(accent), end = AccentGradient.end(accent);
            assertEquals(accent >>> 24, start >>> 24, "start keeps the alpha");
            assertEquals(accent >>> 24, end >>> 24, "end keeps the alpha");
            assertTrue(hueDistance(accent, start) <= 0.045f, "start is a few degrees from the accent");
            assertTrue(hueDistance(accent, end) <= 0.045f, "end is a few degrees from the accent");
            assertNotEquals(start, end, "the two stops differ, or there is no gradient");
            assertTrue(ColorEntryHelper.argbToHsla(start)[2] >= ColorEntryHelper.argbToHsla(end)[2],
                    "the left stop is the lighter one");
        }
    }

    @Test void greysOnlyStepInLightness() {
        int grey = 0xFF808080;
        float[] start = ColorEntryHelper.argbToHsla(AccentGradient.start(grey));
        float[] end = ColorEntryHelper.argbToHsla(AccentGradient.end(grey));
        assertTrue(start[1] < 0.02f && end[1] < 0.02f, "no hue is invented for a grey");
        assertTrue(start[2] > end[2]);
    }

    @Test void memoAnswersEachAccentConsistently() {
        int a = AccentGradient.start(0xFF43A047), b = AccentGradient.start(0xFFEB0029);
        assertEquals(a, AccentGradient.start(0xFF43A047));
        assertEquals(b, AccentGradient.start(0xFFEB0029));
    }

    @Test void switchesAndSliderFillsWearIt() {
        String toggle = SourceTree.read("ui/component/ToggleSwitch.java");
        assertEquals(2, toggle.split("drawRoundedRectGradientAA\\(", -1).length - 1, "both switch painters");
        assertTrue(toggle.contains("AccentGradient.start(active)") && toggle.contains("AccentGradient.end(active)"));
        String slider = SourceTree.read("ui/component/Slider.java");
        assertTrue(slider.contains("RenderUtil.drawRoundedRectGradientAA(g, x, ty, filledW, TRACK_H, trackRadius(),"));
        String render = SourceTree.read("ui/util/RenderUtil.java");
        assertTrue(render.contains("NativeShapeRenderer.drawGradient("), "one shader quad");
        assertTrue(render.contains("AuroraAnim.lerpArgb(from, to, 0.5f)"), "flat midpoint where the shader cannot run");
    }
}
