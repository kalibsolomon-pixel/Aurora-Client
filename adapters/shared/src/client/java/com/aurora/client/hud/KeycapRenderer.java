package com.aurora.client.hud;

import com.aurora.client.theme.KeycapProfile;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.ui.util.UiLayerCache;
import com.aurora.client.worldmap.MapMarkers;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * HUD-owned Cherry keycap endpoint rasters. A settled key is one textured blit; a press transition
 * blends its idle and pressed endpoints. Only misses allocate geometry, images or map entries.
 * This pool is independent of {@code MaterialSurface}'s screen-only templates.
 */
public final class KeycapRenderer {
    static final int MAX_ENTRIES = 48; // all 22 possible visible slots can blend both endpoints
    static final long MAX_BYTES = 4L * 1024 * 1024;
    private static final int PAD = 4;

    private record Entry(UiLayerCache cache, int width, int height, int canvasWidth, int canvasHeight,
                         float radius, float density, long generation, int fill, boolean pressed) {
        long bytes() { return (long) canvasWidth * canvasHeight * 4; }
        boolean matches(int w, int h, int cw, int ch, float r, float d, long g, int f, boolean p) {
            return width == w && height == h && canvasWidth == cw && canvasHeight == ch
                    && radius == r && density == d && generation == g && fill == f && pressed == p;
        }
    }

    private final Long2ObjectLinkedOpenHashMap<Entry> entries = new Long2ObjectLinkedOpenHashMap<>();
    private long bytes;

    public void draw(GuiGraphicsExtractor ctx, float x, float y, int width, int height,
                     float radius, float press, int idle, int accent) {
        float density = MapMarkers.density(ctx);
        long generation = ThemeManager.generation();
        int face = idle | 0xff000000;
        if (press < 1) state(ctx, x, y, width, height, radius, density, generation, face, false, 1);
        if (press > 0) state(ctx, x, y, width, height, radius, density, generation,
                blend(face, accent | 0xff000000, .30f), true, Math.min(1, press));
    }

    private void state(GuiGraphicsExtractor ctx, float x, float y, int width, int height,
                       float radius, float density, long generation, int fill, boolean pressed, float alpha) {
        if (alpha <= .004f) return;
        int canvasW = Math.max(1, Math.round((width + PAD * 2) * density));
        int canvasH = Math.max(1, Math.round((height + PAD * 2) * density));
        long key = identityHash(width, height, canvasW, canvasH, radius, density, generation, fill, pressed);
        Entry entry = entries.getAndMoveToLast(key);
        if (entry != null && !entry.matches(width, height, canvasW, canvasH, radius, density,
                generation, fill, pressed)) {
            entries.remove(key);
            bytes -= entry.bytes();
            entry.cache().retire();
            entry = null;
        }
        if (entry == null) {
            entry = bake(key, width, height, canvasW, canvasH, radius, density, generation, fill, pressed);
        }
        int px = MapMarkers.pixelX(ctx, x - PAD, y - PAD);
        int py = MapMarkers.pixelY(ctx, x - PAD, y - PAD);
        int tint = (Math.round(alpha * 255) << 24) | 0x00ffffff;
        MapMarkers.blitPixels(ctx, entry.cache(), px, py, entry.canvasWidth(), entry.canvasHeight(), tint);
    }

    private Entry bake(long key, int width, int height, int canvasW, int canvasH, float radius,
                       float density, long generation, int fill, boolean pressed) {
        var cache = new UiLayerCache();
        var entry = new Entry(cache, width, height, canvasW, canvasH, radius, density, generation, fill, pressed);
        cache.ensureSize(canvasW, canvasH);
        cache.clear();
        var geometry = new KeycapProfile.Geometry(width * density, height * density,
                radius * density, density, pressed);
        float faceX = PAD * density, faceY = PAD * density;
        cache.writeArgb((px, py) -> geometry.shade(fill, px - faceX, py - faceY));
        cache.softShadow(faceX, faceY, width * density, height * density, radius * density,
                geometry.shadowElevation() * density, geometry.shadowSoftness() * density,
                geometry.shadowAlpha());
        cache.commit(0);

        if (entry.bytes() > MAX_BYTES) {
            cache.retire(); // An oversized raster is transient; deferred blits release it next frame.
            return entry;
        }
        while (!entries.isEmpty() && (entries.size() >= MAX_ENTRIES || bytes + entry.bytes() > MAX_BYTES)) {
            Entry oldest = entries.removeFirst();
            bytes -= oldest.bytes();
            oldest.cache().retire();
        }
        entries.putAndMoveToLast(key, entry);
        bytes += entry.bytes();
        return entry;
    }

    private static long identityHash(int width, int height, int canvasW, int canvasH, float radius,
                                     float density, long generation, int fill, boolean pressed) {
        long key = generation;
        key = key * 65537 + width;
        key = key * 65537 + height;
        key = key * 65537 + canvasW;
        key = key * 65537 + canvasH;
        key = key * 65537 + Float.floatToIntBits(radius);
        key = key * 65537 + Float.floatToIntBits(density);
        key = key * 65537 + fill;
        return key * 31 + (pressed ? 1 : 0);
    }

    private static int blend(int from, int to, float t) {
        int a = (from >>> 24) + Math.round(((to >>> 24) - (from >>> 24)) * t);
        int r = ((from >>> 16) & 255) + Math.round((((to >>> 16) & 255) - ((from >>> 16) & 255)) * t);
        int g = ((from >>> 8) & 255) + Math.round((((to >>> 8) & 255) - ((from >>> 8) & 255)) * t);
        int b = (from & 255) + Math.round(((to & 255) - (from & 255)) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }
}
