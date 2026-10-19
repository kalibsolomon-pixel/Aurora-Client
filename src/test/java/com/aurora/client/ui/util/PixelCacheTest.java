package com.aurora.client.ui.util;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class PixelCacheTest {
    @Test void integerCoverageDoesNotLoseTheFirstPixel() throws Exception {
        int[] pixels = row(2, 6, 0xffabcdef);
        for (int x = 0; x < pixels.length; x++) assertEquals(x >= 2 && x < 6 ? 0xffabcdef : 0, pixels[x]);
    }
    @Test void fractionalCoveragePreservesBothEdgesAndSourceOpacity() throws Exception {
        int[] pixels = row(1.25f, 4.5f, 0x80010203);
        assertEquals(96, pixels[1] >>> 24); assertEquals(128, pixels[2] >>> 24);
        assertEquals(128, pixels[3] >>> 24); assertEquals(64, pixels[4] >>> 24);
        assertEquals(0x010203, pixels[1] & 0xffffff);
    }
    @Test void subpixelSpanStaysWithinOnePixel() throws Exception {
        int[] pixels = row(2.25f, 2.75f, -1);
        assertEquals(128, pixels[2] >>> 24);
        assertEquals(1, java.util.Arrays.stream(pixels).filter(p -> p != 0).count());
    }
    private static int[] row(float left, float right, int color) throws Exception {
        int[] result = new int[8];
        RenderUtil.RectSink sink = (x1,y1,x2,y2,argb) -> {
            for (int x = x1; x < x2; x++) { assertEquals(0, result[x], "coverage spans must not overlap"); result[x] = argb; }
        };
        Method method = RenderUtil.class.getDeclaredMethod("fillAARow", RenderUtil.RectSink.class, float.class, float.class, float.class, int.class);
        method.setAccessible(true); method.invoke(null, sink, left, 0f, right, color);
        return result;
    }
    @Test void bulkUpscalePreservesAbgrAlphaAndNonIntegerRatios() throws Exception {
        UiLayerCache cache = new UiLayerCache();
        int[] source = {0x11223344, 0x55667788, 0x99aabbcc, 0xff010203, 0x80203040, 0};
        try {
            cache.ensureSize(7, 5); cache.writeScaledAbgr(source, 3, 2);
            var field = UiLayerCache.class.getDeclaredField("image"); field.setAccessible(true);
            NativeImage image = (NativeImage) field.get(cache);
            int[] output = image.getPixelsABGR();
            for (int y = 0; y < 5; y++) for (int x = 0; x < 7; x++)
                assertEquals(source[(y * 2 / 5) * 3 + x * 3 / 7], output[y * 7 + x]);
            cache.writeScaledAbgr(new int[]{0x40102030}, 1, 1);
            for (int pixel : image.getPixelsABGR()) assertEquals(0x40102030, pixel);
        } finally { cache.dispose(); }
    }
    @Test void invalidSourceCannotWriteBeyondTheCache() {
        UiLayerCache cache = new UiLayerCache();
        try {
            cache.ensureSize(3, 3);
            assertThrows(IllegalArgumentException.class, () -> cache.writeScaledAbgr(new int[3], 2, 2));
            assertThrows(IllegalArgumentException.class, () -> cache.writeScaledAbgr(new int[0], 0, 2));
        } finally { cache.dispose(); }
    }
}
