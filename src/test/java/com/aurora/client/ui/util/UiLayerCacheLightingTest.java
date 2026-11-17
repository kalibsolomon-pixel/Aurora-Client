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

    @Test void curvatureUsesOwnGeometryAndPreservesEveryCoveredAlphaWithoutFills() throws Exception {
        long fills=RenderUtil.fillsSubmitted();
        for(int scale=1;scale<=4;scale++) {
            var cache=cache(16*scale,16*scale);
            try {
                var image=pixels(cache);
                for(int y=0;y<12*scale;y++) for(int x=0;x<12*scale;x++) {
                    double d=MaterialLighting.distance(x+.5,y+.5,12*scale,12*scale,4*scale);
                    int alpha=(int)Math.round(Math.clamp(.5-d,0,1)*129);
                    image.setPixel(x+2*scale,y+2*scale,alpha<<24|0x3987ae);
                }
                int[] before=new int[16*scale*16*scale];
                for(int y=0;y<16*scale;y++) for(int x=0;x<16*scale;x++) before[y*16*scale+x]=image.getPixel(x,y);
                cache.lightFace(2*scale,2*scale,12*scale,12*scale,4*scale,scale,5,-3,MaterialLighting.Profile.RAISED);
                for(int y=0;y<16*scale;y++) for(int x=0;x<16*scale;x++) {
                    int original=before[y*16*scale+x];
                    assertEquals(original>>>24,image.getPixel(x,y)>>>24);
                    if(x>=2*scale && x<14*scale && y>=2*scale && y<14*scale)
                        assertEquals(MaterialLighting.face(original,x-2*scale,y-2*scale,12*scale,12*scale,
                                4*scale,scale,5,-3,MaterialLighting.Profile.RAISED),image.getPixel(x,y));
                    else assertEquals(original,image.getPixel(x,y));
                }
            } finally { cache.dispose(); }
        }
        assertEquals(fills,RenderUtil.fillsSubmitted());
    }

    @Test void squareLightingNeverRoundsOrEnlargesItsCapturedSilhouette() throws Exception {
        var cache=cache(10,10);
        try {
            cache.rect(2,2,8,8,0xff808080);
            cache.lightFace(2,2,6,6,0,1,5,-3,MaterialLighting.Profile.RAISED);
            assertEquals(255,pixels(cache).getPixel(2,2)>>>24);
            assertEquals(0,pixels(cache).getPixel(1,2));
            assertEquals(0,pixels(cache).getPixel(2,1));
            assertTrue((pixels(cache).getPixel(4,2)&255)>(pixels(cache).getPixel(4,7)&255));
        } finally { cache.dispose(); }
    }

    @Test void directionalShadowFallsDownRightWithRoleScaleAndNoClippedTail() throws Exception {
        for(int scale=1;scale<=4;scale++) {
            var cache=cache(20*scale,20*scale);
            try {
                cache.rect(5*scale,5*scale,15*scale,15*scale,0xff3978ab);
                cache.softShadow(5*scale,5*scale,10*scale,10*scale,0,2*scale,4*scale,.20f);
                var image=pixels(cache);
                assertTrue((image.getPixel(15*scale,10*scale)>>>24)>(image.getPixel(4*scale,10*scale)>>>24));
                assertTrue((image.getPixel(10*scale,15*scale)>>>24)>(image.getPixel(10*scale,4*scale)>>>24));
                for(int i=0;i<20*scale;i++) {
                    assertEquals(0,image.getPixel(i,0));assertEquals(0,image.getPixel(i,20*scale-1));
                    assertEquals(0,image.getPixel(0,i));assertEquals(0,image.getPixel(20*scale-1,i));
                }
            } finally { cache.dispose(); }
        }
    }

    @Test void measureCapturedTileLightingAndShadowBake() throws Exception {
        // Synthetic captured AA face; measures CPU raster work, not texture upload or game frame time.
        var cache=cache(270,270);long[] samples=new long[9];
        try {
            var image=pixels(cache);
            for(int run=-5;run<samples.length;run++) {
                long start=System.nanoTime();cache.clear();
                for(int y=0;y<240;y++) for(int x=0;x<240;x++) {
                    double d=MaterialLighting.distance(x+.5,y+.5,240,240,30);
                    int alpha=(int)Math.round(Math.clamp(.5-d,0,1)*255);
                    image.setPixel(x+15,y+15,alpha<<24|0x1d1f27);
                }
                cache.lightFace(15,15,240,240,30,3,11,3,MaterialLighting.Profile.RAISED);
                cache.softShadow(15,15,240,240,30,6,12,.20f);
                if(run>=0) samples[run]=System.nanoTime()-start;
            }
            java.util.Arrays.sort(samples);
            assertEquals(255,image.getPixel(135,135)>>>24);
            System.out.printf("Depth captured CPU bake 80x80 scale=3 median=%.3f ms (9 samples, 5 warmups)%n",samples[4]/1e6);
        } finally { cache.dispose(); }
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
