package com.aurora.client.ui.component;

import com.aurora.client.ui.util.UiLayerCache;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

/** Shared screen-independent rasters. Only misses allocate; primitive LRU hits allocate nothing.
 * Evictions retire at the next extraction boundary, after deferred blits have finished. */
final class MaterialTemplates {
    static final long MAX_BYTES = 16L * 1024 * 1024;
    static final int MAX_ENTRIES = 256;
    private final long maxBytes;
    private final int maxEntries;
    private final Long2ObjectLinkedOpenHashMap<Entry> entries = new Long2ObjectLinkedOpenHashMap<>();
    private long bytes;

    MaterialTemplates() { this(MAX_BYTES,MAX_ENTRIES); }
    MaterialTemplates(long maxBytes,int maxEntries) { this.maxBytes=maxBytes; this.maxEntries=maxEntries; }

    private record Entry(UiLayerCache cache,long version,int width,int height,float faceWidth,float faceHeight,
                         float radius,int kind,boolean shadow,int fill,int border) {
        long bytes() { return (long)width*height*4; }
        boolean matches(long v,int w,int h,float fw,float fh,float r,int k,boolean s,int f,int b) {
            return version==v && width==w && height==h && faceWidth==fw && faceHeight==fh
                    && radius==r && kind==k && shadow==s && fill==f && border==b;
        }
    }

    UiLayerCache get(long version,int width,int height,float faceWidth,float faceHeight,float radius,int kind,boolean shadow,
                     int fill,int border) {
        // Exact floating geometry matters for fractional knobs. Verify the full identity on
        // a hit too: a hash collision is a safe miss, never another face's pixels.
        long key=version;
        key=key*65537+width; key=key*65537+height;
        key=key*65537+Float.floatToIntBits(faceWidth); key=key*65537+Float.floatToIntBits(faceHeight);
        key=key*65537+Float.floatToIntBits(radius); key=key*65537+kind; key=key*31+(shadow ? 1 : 0);
        key=key*65537+fill; key=key*65537+border;
        Entry entry=entries.getAndMoveToLast(key);
        if(entry!=null && entry.matches(version,width,height,faceWidth,faceHeight,radius,kind,shadow,fill,border)) return entry.cache;
        if(entry!=null) { entries.remove(key); bytes-=entry.bytes(); entry.cache.retire(); }
        var cache=new UiLayerCache();
        entry=new Entry(cache,version,width,height,faceWidth,faceHeight,radius,kind,shadow,fill,border);
        if(entry.bytes()>maxBytes) { cache.retire(); return cache; } // Oversized rasters live for this frame only.
        while(!entries.isEmpty() && (entries.size()>=maxEntries || bytes+entry.bytes()>maxBytes)) {
            Entry oldest=entries.removeFirst(); bytes-=oldest.bytes(); oldest.cache.retire();
        }
        entries.putAndMoveToLast(key,entry); bytes+=entry.bytes();
        return cache;
    }

    void clear() {
        for(Entry entry:entries.values()) entry.cache.retire();
        entries.clear(); bytes=0;
    }

    long bytes() { return bytes; }
    int size() { return entries.size(); }
}
