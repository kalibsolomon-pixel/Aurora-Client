package com.aurora.client.ui.util;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A cropped capture must reproduce the old framebuffer capture pixel for pixel. */
class UiLayerRegionTest {
    @Test void croppingPreservesAntialiasingClippingAndSourceOverAtEveryScale() throws Exception {
        for (int scale = 1; scale <= 4; scale++) {
            UiLayerCache full = new UiLayerCache();
            UiLayerCache cropped = new UiLayerCache();
            try {
                full.ensureSize(128 * scale, 96 * scale);
                cropped.ensureRegion(24 * scale, 12 * scale, 64 * scale, 48 * scale);
                for (UiLayerCache target : new UiLayerCache[]{full, cropped}) {
                    target.clear();
                    // Cross every capture edge, then overlap translucent AA edges and outlines.
                    target.rect(-10, -10, 140 * scale, 110 * scale, 0x19223344);
                    ShapeRasterizer.drawRoundedRectAA(target, scale, 17.25f, 8.75f, 80.5f, 60.25f, 9.5f, 0x80336699);
                    ShapeRasterizer.drawRoundedOutlineAA(target, scale, 29.75f, 16.25f, 37.5f, 30.5f, 6.25f, 1.25f, 0xaacc8844);
                    ShapeRasterizer.drawCircleAA(target, scale, 71.25f, 40.75f, 24.5f, 24.5f, 0x6544cc88);
                }
                NativeImage reference = image(full), actual = image(cropped);
                for (int y = 0; y < 48 * scale; y++) {
                    for (int x = 0; x < 64 * scale; x++) {
                        assertEquals(reference.getPixel(x + 24 * scale, y + 12 * scale), actual.getPixel(x, y),
                                "scale=" + scale + ", pixel=" + x + "," + y);
                    }
                }
            } finally {
                full.dispose();
                cropped.dispose();
            }
        }
    }

    @Test void sameSizeRegionsReuseStorageAndLocalCapturesResetTheOrigin() throws Exception {
        UiLayerCache cache = new UiLayerCache();
        try {
            cache.ensureRegion(100, 50, 8, 6);
            NativeImage storage = image(cache);
            cache.clear();
            cache.rect(100, 50, 101, 51, 0xff123456);
            assertEquals(0xff123456, storage.getPixel(0, 0));

            cache.ensureRegion(200, 70, 8, 6);
            assertSame(storage, image(cache), "moving a region should not allocate a new image");
            cache.clear();
            cache.rect(207, 75, 220, 90, 0xffabcdef);
            assertEquals(0xffabcdef, storage.getPixel(7, 5));
            assertEquals(0, storage.getPixel(0, 0));

            cache.ensureSize(8, 6);
            cache.clear();
            cache.rect(0, 0, 1, 1, 0xff654321);
            assertEquals(0xff654321, storage.getPixel(0, 0));
        } finally {
            cache.dispose();
        }
    }

    private static NativeImage image(UiLayerCache cache) throws Exception {
        var field = UiLayerCache.class.getDeclaredField("image");
        field.setAccessible(true);
        return (NativeImage) field.get(cache);
    }
}
