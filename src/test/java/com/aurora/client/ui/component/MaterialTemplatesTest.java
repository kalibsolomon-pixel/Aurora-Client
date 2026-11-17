package com.aurora.client.ui.component;

import com.aurora.client.ui.util.UiLayerCache;
import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaterialTemplatesTest {
    private static UiLayerCache get(MaterialTemplates pool,int kind) {
        return pool.get(7,10,10,6,6,2,kind,false,0xff1d1f27,0);
    }
    private static NativeImage image(UiLayerCache cache) throws Exception {
        var field=UiLayerCache.class.getDeclaredField("image");field.setAccessible(true);
        return (NativeImage)field.get(cache);
    }

    @Test void lruHitsSurviveScreenIndependentLookupsAndEvictionWaitsForFrameBoundary() throws Exception {
        var pool=new MaterialTemplates(800,2);
        try {
            var first=get(pool,1);first.ensureSize(10,10);
            var second=get(pool,2);second.ensureSize(10,10);
            assertSame(first,get(pool,1)); // first becomes most recently used
            get(pool,3);
            assertEquals(2,pool.size());assertEquals(800,pool.bytes());
            assertNotNull(image(second),"eviction must preserve already-recorded blits");
            UiLayerCache.beginFrame();
            assertNull(image(second));assertNotNull(image(first));
            assertSame(first,get(pool,1));
            assertNotSame(second,get(pool,2));
        } finally { pool.clear();UiLayerCache.beginFrame(); }
    }

    @Test void invalidationAndOversizeEntriesNeverRetainUnboundedMemory() throws Exception {
        var pool=new MaterialTemplates(399,4);
        try {
            var transientCache=get(pool,1);transientCache.ensureSize(10,10);
            assertEquals(0,pool.size());assertEquals(0,pool.bytes());
            assertNotNull(image(transientCache));
            UiLayerCache.beginFrame();assertNull(image(transientCache));
        } finally { pool.clear();UiLayerCache.beginFrame(); }
        pool=new MaterialTemplates(800,2);
        try {
            var first=get(pool,1);first.ensureSize(10,10);
            pool.clear();
            assertEquals(0,pool.size());assertEquals(0,pool.bytes());
            assertNotNull(image(first));UiLayerCache.beginFrame();assertNull(image(first));
            assertNotSame(first,get(pool,1));
        } finally { pool.clear();UiLayerCache.beginFrame(); }
    }

    @Test void byteAndEntryCapsApplyIndependently() {
        var byteLimited=new MaterialTemplates(799,256);
        var entryLimited=new MaterialTemplates(10000,1);
        try {
            get(byteLimited,1);get(byteLimited,2);
            assertEquals(1,byteLimited.size());assertEquals(400,byteLimited.bytes());
            get(entryLimited,1);get(entryLimited,2);
            assertEquals(1,entryLimited.size());assertEquals(400,entryLimited.bytes());
        } finally { byteLimited.clear();entryLimited.clear();UiLayerCache.beginFrame(); }
    }

    @Test void exactGeometryColoursAndVersionArePartOfIdentity() {
        var pool=new MaterialTemplates();
        try {
            var a=pool.get(7,10,10,6.1f,6,2,1,false,10,20);
            assertSame(a,pool.get(7,10,10,6.1f,6,2,1,false,10,20));
            assertNotSame(a,pool.get(7,10,10,6.2f,6,2,1,false,10,20));
            assertNotSame(a,pool.get(7,10,10,6.1f,6,2.1f,1,false,10,20));
            assertNotSame(a,pool.get(8,10,10,6.1f,6,2,1,false,10,20));
            // Old version formula collides for these colours (31*fill+border).
            assertNotSame(a,pool.get(7,10,10,6.1f,6,2,1,false,11,-11));
            // Deliberate new hash collision must replace/retire, never reuse the wrong pixels.
            assertNotSame(a,pool.get(7,10,10,6.1f,6,2,1,false,11,20-65537));
            assertNotSame(a,pool.get(7,10,10,6.1f,6,2,1,true,10,20));
            assertNotSame(a,pool.get(7,10,10,6.1f,6,2,2,false,10,20));
        } finally { pool.clear();UiLayerCache.beginFrame(); }
    }
}
