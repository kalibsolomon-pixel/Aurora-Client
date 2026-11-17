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
        var cache=cache(270,270);long[][] samples=new long[2][21];
        long[][] lighting=new long[2][21],shadows=new long[2][21];
        try {
            var image=pixels(cache);
            for(int run=-15;run<samples[0].length;run++) for(int path=0;path<2;path++) {
                long start=System.nanoTime();cache.clear();
                for(int y=0;y<240;y++) for(int x=0;x<240;x++) {
                    double d=MaterialLighting.distance(x+.5,y+.5,240,240,30);
                    int alpha=(int)Math.round(Math.clamp(.5-d,0,1)*255);
                    image.setPixel(x+15,y+15,alpha<<24|0x1d1f27);
                }
                long faceStart=System.nanoTime();
                long shadowStart;
                if(path==0) {
                    legacyFace(image,15,15,240,240,30,3,11,3,LegacyMaterialLighting.Profile.RAISED);
                    shadowStart=System.nanoTime();
                    legacyShadow(image,15,15,240,240,30,6,12,.20f);
                } else {
                    cache.lightFace(15,15,240,240,30,3,11,3,MaterialLighting.Profile.RAISED);
                    shadowStart=System.nanoTime();
                    cache.softShadow(15,15,240,240,30,6,12,.20f);
                }
                long end=System.nanoTime();
                if(run>=0) {
                    samples[path][run]=end-start;
                    lighting[path][run]=shadowStart-faceStart;shadows[path][run]=end-shadowStart;
                }
            }
            for(long[] sample:samples) java.util.Arrays.sort(sample);
            for(long[] sample:lighting) java.util.Arrays.sort(sample);
            for(long[] sample:shadows) java.util.Arrays.sort(sample);
            assertEquals(255,image.getPixel(135,135)>>>24);
            System.out.printf("Depth captured CPU bake 80x80 scale=3 before=%.3f after=%.3f ms (21 paired samples, 15 warmups)%n",
                    samples[0][10]/1e6,samples[1][10]/1e6);
            System.out.printf("Depth phases median ms: lighting %.3f -> %.3f; shadow %.3f -> %.3f%n",
                    lighting[0][10]/1e6,lighting[1][10]/1e6,shadows[0][10]/1e6,shadows[1][10]/1e6);
        } finally { cache.dispose(); }
    }

    // Frozen 4b2b00d raster loops and lighting equations: never call the optimized math here.
    private static void legacyFace(NativeImage image,int x,int y,float width,float height,float radius,
                                   int scale,float top,float bottom,LegacyMaterialLighting.Profile profile) {
        for(int py=Math.max(0,y);py<Math.min(image.getHeight(),y+(int)Math.ceil(height));py++)
            for(int px=Math.max(0,x);px<Math.min(image.getWidth(),x+(int)Math.ceil(width));px++)
                image.setPixel(px,py,LegacyMaterialLighting.face(image.getPixel(px,py),px-x,py-y,
                        width,height,radius,scale,top,bottom,profile));
    }

    private static void legacyShadow(NativeImage image,float x,float y,float width,float height,float radius,
                                     float elevation,float softness,float alpha) {
        double offsetX=elevation*LegacyMaterialLighting.SHADOW_X,offsetY=elevation*LegacyMaterialLighting.SHADOW_Y;
        for(int py=0;py<image.getHeight();py++) for(int px=0;px<image.getWidth();px++) {
            double d=LegacyMaterialLighting.distance(px+.5-x-offsetX,py+.5-y-offsetY,width,height,radius);
            float coverage=(float)Math.max(0,Math.min(1,.5-d/Math.max(1,softness)));
            image.setPixel(px,py,LegacyMaterialLighting.shadowBehind(image.getPixel(px,py),alpha*coverage));
        }
    }

    @Test void optimizedRasterIsBitExactAgainstFrozenBakeAcrossGeometryRolesAndColours() throws Exception {
        int cases=0;long compared=0;
        // Live and factory material colours, saturated accent, neutral and endpoint channels.
        for(int scale:new int[]{2,3}) for(int shape=0;shape<4;shape++)
            for(var profile:MaterialLighting.Profile.values())
                for(int color:new int[]{0xff1f1c22,0xff1d1f27,0xff3987ae,0xff808080,0xff000000,0xffffffff})
                    for(int role=0;role<8;role++) {
            float width=(shape==2 ? 13.5f : 80)*scale, height=(shape==2 ? 13.5f : 41.25f)*scale;
            float radius=shape==0 ? 10*scale : shape==2 ? width*.5f : shape==3 ? 1000 : 0;
            int x=shape==3 ? -3 : 5*scale,y=shape==3 ? -2 : 5*scale;
            int w=(int)Math.ceil(width)+10*scale,h=(int)Math.ceil(height)+10*scale;
            float top=switch(role) { case 0 -> 5; case 1 -> 1; case 2 -> 11; case 3 -> -5;
                case 4 -> -3; case 5 -> 0; case 6 -> 3; default -> 6; };
            float bottom=switch(role) { case 0 -> -3; case 1 -> 5; case 2 -> 3; case 3 -> -5;
                case 4 -> 2; case 5 -> 0; case 6 -> 7; default -> -4; };
            var cache=cache(w,h);
            try(var expected=new NativeImage(NativeImage.Format.RGBA,w,h,false)) {
                var actual=pixels(cache);
                for(int py=0;py<h;py++) for(int px=0;px<w;px++) {
                    double d=LegacyMaterialLighting.distance(px+.5-x,py+.5-y,width,height,radius);
                    int a=(int)Math.round(Math.clamp(.5-d,0,1)*255);
                    // Some nonuniform, translucent pixels exercise colour-cache invalidation and shadow blending.
                    int pixel=a<<24 | ((px+py)%31==0 ? 0x7135b9 : color & 0xffffff);
                    if(a>0 && (px+py)%37==0) pixel=(pixel & 0xffffff) | 0x81000000;
                    actual.setPixel(px,py,pixel); expected.setPixel(px,py,pixel);
                }
                legacyFace(expected,x,y,width,height,radius,scale,top,bottom,
                        LegacyMaterialLighting.Profile.valueOf(profile.name()));
                cache.lightFace(x,y,width,height,radius,scale,top,bottom,profile);
                assertArrayEquals(expected.getPixelsABGR(),actual.getPixelsABGR(),"face case "+cases);
                legacyShadow(expected,x,y,width,height,radius,2*scale,4*scale,.20f);
                cache.softShadow(x,y,width,height,radius,2*scale,4*scale,.20f);
                assertArrayEquals(expected.getPixelsABGR(),actual.getPixelsABGR(),"shadow case "+cases);
                cases++;compared+=(long)w*h*2;
            } finally { cache.dispose(); }
        }
        System.out.printf("Depth frozen differential: %d cases, %d pixel comparisons, max channel difference=0%n",cases,compared);
    }

    @Test void narrowFacesAndFractionalBandTransitionsMatchFrozenMath() {
        var random=new java.util.Random(0x4b2b00d);
        for(float width:new float[]{.75f,1,1.5f,3.25f,7,11.5f,19,40})
            for(float height:new float[]{.9f,2,4.5f,9,18})
                for(float radius:new float[]{0,.25f,1.3f,2.5f,10,1000})
                    for(int scale:new int[]{2,3}) for(var profile:MaterialLighting.Profile.values()) {
            var bake=new MaterialLighting.FaceBake(width,height,radius,scale,5,-3,profile);
            var oldProfile=LegacyMaterialLighting.Profile.valueOf(profile.name());
            int color=random.nextInt();
            for(int y=0;y<Math.ceil(height);y++) for(int x=0;x<Math.ceil(width);x++) {
                if((x+y)%13==0) color=random.nextInt();
                assertEquals(LegacyMaterialLighting.face(color,x,y,width,height,radius,scale,5,-3,oldProfile),
                        bake.shade(color,x,y),"narrow/fractional face must stay bit exact");
            }
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
