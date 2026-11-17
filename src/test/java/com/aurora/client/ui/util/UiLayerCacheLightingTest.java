package com.aurora.client.ui.util;

import com.aurora.client.theme.MaterialLighting;
import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Headless native-image checks: no client, texture upload, shader or graphics context. */
class UiLayerCacheLightingTest {
    private static NativeImage pixels(UiLayerCache cache) throws Exception {
        var field=UiLayerCache.class.getDeclaredField("image");
        field.setAccessible(true);
        return (NativeImage)field.get(cache);
    }
    private static UiLayerCache cache(int w,int h) {
        var cache=new UiLayerCache();cache.ensureSize(w,h);cache.clear();return cache;
    }

    @Test void hslRasterUsesArgbAndClipsToFaceBounds() throws Exception {
        var cache=cache(8,8);
        try {
            cache.rect(2,2,6,6,0x813987ae);
            cache.lightVertical(2,2,4,4,5,-3);
            var image=pixels(cache);
            assertEquals(MaterialLighting.lightness(0x813987ae,5),image.getPixel(2,2));
            assertEquals(MaterialLighting.lightness(0x813987ae,-3),image.getPixel(5,5));
            assertEquals(0,image.getPixel(1,2));
            assertEquals(129,image.getPixel(3,3)>>>24);
        } finally { cache.dispose(); }
    }

    @Test void edgeFollowsRoundedCoverageWithoutPaintingEmptyCorners() throws Exception {
        var cache=cache(8,8);
        try {
            // A captured rounded silhouette: outer columns start lower, with partial coverage.
            cache.rect(1,3,2,5,0x40808080);cache.rect(2,2,3,6,0x80808080);
            cache.rect(3,1,5,7,0xff808080);cache.rect(5,2,6,6,0x80808080);
            cache.rect(6,3,7,5,0x40808080);
            cache.edgeLine(1,1,6,6,true,0x1affffff);
            cache.edgeLine(1,1,6,6,false,0x38000000);
            var image=pixels(cache);
            assertEquals(0x408d8d8d,image.getPixel(1,3));
            assertEquals(0x808d8d8d,image.getPixel(2,2));
            assertEquals(0xff8d8d8d,image.getPixel(3,1));
            assertEquals(0xff646464,image.getPixel(3,6));
            assertEquals(0xff808080,image.getPixel(3,2));
            assertEquals(0,image.getPixel(1,1));
        } finally { cache.dispose(); }
    }

    @Test void squareEdgesAreExactlyOneDeviceRowAtAnyRasterScale() throws Exception {
        for(int scale:new int[]{1,2,3,4}) {
            var cache=cache(6*scale,6*scale);
            try {
                cache.rect(scale,scale,5*scale,5*scale,0xff808080);
                cache.edgeLine(scale,scale,4*scale,4*scale,true,0xffffffff);
                var image=pixels(cache);
                assertEquals(0xffffffff,image.getPixel(scale,scale));
                assertEquals(0xff808080,image.getPixel(scale,scale+1));
                assertEquals(0,image.getPixel(scale-1,scale));
            } finally { cache.dispose(); }
        }
    }

    @Test void shadowsStayBehindFaceAndDoNotSubmitFills() throws Exception {
        long before=RenderUtil.fillsSubmitted();
        var cache=cache(16,16);
        try {
            cache.rect(4,4,12,12,0xff3978ab);
            cache.lightVertical(4,4,8,8,5,-3);
            int face=pixels(cache).getPixel(6,6);
            cache.softShadow(4,4,8,8,0,1,2,.14f);
            assertEquals(face,pixels(cache).getPixel(6,6));
            assertTrue((pixels(cache).getPixel(6,12)>>>24)>0);
            assertEquals(0,pixels(cache).getPixel(0,0));
            assertEquals(before,RenderUtil.fillsSubmitted());
        } finally { cache.dispose(); }
    }
}
