package com.aurora.client.resourcepack;

import com.aurora.client.adapter.NativeUi;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Async loader and bounded LRU GPU cache for resource-pack {@code pack.png} icons.
 *
 * <p>Decodes PNGs off-thread and registers {@link DynamicTexture} instances on
 * the render thread. Evicted textures are retired and freed in {@link #drainRetired()}
 * at the start of the next frame (rendering rule 10). Cache entries, in-flight
 * jobs, ready uploads, and missing markers are stamped with a refresh generation
 * via {@link PackIconCache} so pre-Refresh decodes can never poison the new cache.
 */
public final class PackIconLoader {

    private static final int MAX_CACHED_ICONS = 96;
    private static final int ICON_SIZE = 64;
    private static final AtomicInteger SEQ = new AtomicInteger();

    private static final ExecutorService IO_POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "aurora-pack-icon-io");
        t.setDaemon(true);
        return t;
    });

    public record LoadedIcon(Identifier textureId, DynamicTexture texture, int width, int height) {}

    private record RetiredTexture(Identifier id, DynamicTexture texture) {}

    private static final PackIconCache<NativeImage, LoadedIcon> CACHE = new PackIconCache<>(MAX_CACHED_ICONS);
    private static final List<RetiredTexture> RETIRED = new ArrayList<>();

    private PackIconLoader() {}

    /**
     * Returns the cached icon for {@code pack} if ready, otherwise queues an async decode.
     * Must be called on the render thread.
     */
    public static LoadedIcon request(Pack pack) {
        if (pack == null) return null;
        drainReady();
        String id = pack.getId();
        LoadedIcon cached = CACHE.getCached(id);
        if (cached != null) return cached;
        long gen = CACHE.tryBeginLoad(id);
        if (gen < 0L) return null;

        IO_POOL.execute(() -> {
            NativeImage raw = null;
            NativeImage scaled = null;
            boolean published = false;
            try {
                byte[] bytes = NativeUi.readPackIconPng(pack);
                if (bytes == null || bytes.length == 0) {
                    CACHE.completeMissing(gen, id);
                    published = true;
                    return;
                }
                raw = NativeImage.read(new ByteArrayInputStream(bytes));
                scaled = downscaleIfLarge(raw);
                if (scaled != raw) {
                    raw.close();
                    raw = null;
                }
                CACHE.completeSuccess(gen, id, scaled, NativeImage::close);
                published = true;
            } catch (Throwable ignored) {
                if (scaled != null && scaled != raw) {
                    try { scaled.close(); } catch (Throwable closeIgnored) {}
                }
                if (raw != null) {
                    try { raw.close(); } catch (Throwable closeIgnored) {}
                }
                if (!published) {
                    CACHE.completeMissing(gen, id);
                }
            }
        });
        return null;
    }

    /** Uploads decoded icon images queued by worker threads. Call on the render thread. */
    public static void drainReady() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        CACHE.drainReady(8, img -> {
            Identifier texId = Identifier.fromNamespaceAndPath(
                    "aurora", "pack_icon/" + SEQ.incrementAndGet());
            DynamicTexture dyn = new DynamicTexture(() -> "aurora_pack_icon", img);
            mc.getTextureManager().register(texId, dyn);
            return new LoadedIcon(texId, dyn, img.getWidth(), img.getHeight());
        }, NativeImage::close, PackIconLoader::retire);
    }

    /** Retires all cached icons (e.g. on Refresh) so they re-read from disk safely on next frame. */
    public static void invalidateAll() {
        CACHE.invalidateAll(NativeImage::close, PackIconLoader::retire);
    }

    /** Frees retired textures at frame start before any GUI blits are recorded (rendering rule 10). */
    public static void drainRetired() {
        if (RETIRED.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        for (RetiredTexture r : RETIRED) {
            try {
                if (mc != null) mc.getTextureManager().release(r.id());
            } catch (Throwable ignored) {}
            try {
                r.texture().close();
            } catch (Throwable ignored) {}
        }
        RETIRED.clear();
    }

    private static void retire(LoadedIcon icon) {
        if (icon != null) RETIRED.add(new RetiredTexture(icon.textureId(), icon.texture()));
    }

    private static NativeImage downscaleIfLarge(NativeImage src) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        if (sw <= ICON_SIZE && sh <= ICON_SIZE) return src;
        double scale = Math.min((double) ICON_SIZE / sw, (double) ICON_SIZE / sh);
        int dw = Math.max(1, (int) Math.round(sw * scale));
        int dh = Math.max(1, (int) Math.round(sh * scale));
        NativeImage dst = new NativeImage(NativeImage.Format.RGBA, dw, dh, false);
        for (int y = 0; y < dh; y++) {
            int sy = Math.min(sh - 1, (int) ((y + 0.5) * sh / dh));
            for (int x = 0; x < dw; x++) {
                int sx = Math.min(sw - 1, (int) ((x + 0.5) * sw / dw));
                dst.setPixel(x, y, src.getPixel(sx, sy));
            }
        }
        return dst;
    }
}
