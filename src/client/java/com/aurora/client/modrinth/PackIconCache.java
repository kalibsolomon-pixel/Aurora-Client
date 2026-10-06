package com.aurora.client.modrinth;

import com.aurora.client.AuroraClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * On-demand thumbnail cache for Modrinth resource-pack icons.
 *
 * <p>Icons are loaded lazily the first time a tile asks for them and cached
 * by URL for the rest of the session. Texture registration is dispatched to
 * the render thread via {@code Minecraft.execute} — GL texture objects are
 * not safe to create off-thread, and {@code DynamicTexture} uploads pixels
 * via a GL call in its constructor.
 *
 * <p>Each entry is a {@link DynamicTexture} registered under a synthetic
 * {@code aurora:modrinth_icon/<index>} identifier. The identifiers are
 * never re-used for a different URL within a session, so stale lookups after
 * a cache clear are impossible.
 *
 * <p>Failures (404, broken PNG, network) remove the URL from the in-flight
 * set with a 30-second backoff before retrying — a single transient failure no
 * longer permanently blacklists an icon.
 */
public final class PackIconCache {

    private static final class Entry {
        final Identifier textureId;
        final DynamicTexture texture;
        final int srcW;
        final int srcH;
        Entry(Identifier id, DynamicTexture tex, int w, int h) {
            this.textureId = id; this.texture = tex; this.srcW = w; this.srcH = h;
        }
    }

    /**
     * Session-wide LRU cache bounded by {@link #MAX_ICONS} entries.
     *
     * <p>The browser no longer calls {@link #clear()} on screen close — icons
     * are kept for the whole session and evicted least-recently-used (releasing
     * their GL textures) only when the cap is exceeded, so re-opening the
     * browser is instant instead of re-downloading everything.
     *
     * <p>NOTE: the value type is explicitly {@code PackIconCache.Entry}.
     * Inside the anonymous {@link LinkedHashMap} body the unqualified name
     * "Entry" resolves to {@code java.util.Map.Entry} (a name-clash erasure
     * error), so the outer-class-qualified name is used everywhere in that body.
     */
    private static final int MAX_ICONS = 128;
    private static final Map<String, PackIconCache.Entry> LOADED = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PackIconCache.Entry> eldest) {
            if (size() <= MAX_ICONS) return false;
            // Evict — release the GL texture + native image for the LRU entry,
            // AND clear it from REQUESTED so it can be re-fetched if the user
            // scrolls back to it. Without the REQUESTED removal the URL would
            // be permanently blacklisted and the icon would never reload.
            PackIconCache.Entry e = eldest.getValue();
            String url = eldest.getKey();
            try { Minecraft.getInstance().getTextureManager().release(e.textureId); } catch (Throwable ignored) {}
            try { e.texture.close(); } catch (Throwable ignored) {}
            REQUESTED.remove(url);
            return true;
        }
    };

    /**
     * URLs with an in-flight or completed-and-cached fetch. A URL is removed
     * from this set on any failure (HTTP error, exception, decode error) so
     * a later request can retry after a 30-second backoff — previously a single transient failure
     * permanently blacklisted the icon for the entire session.
     */
    private static final java.util.Set<String> REQUESTED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static int counter = 0;

    private static final Map<String, Long> RETRY_AFTER = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Long> entry) { return size() > MAX_ICONS; }
    };
    private static int generation;

    private PackIconCache() {}

    /**
     * @return the texture id for {@code url}, or {@code null} if it isn't
     *         loaded yet (or failed to load). Call every frame; once loaded
     *         the id is stable.
     */
    public static Identifier getIfLoaded(String url) {
        if (url == null) return null;
        Entry e = LOADED.get(url);
        return e == null ? null : e.textureId;
    }

    /** Fetch the actual CDN image and normalize it off-thread; never guess a PNG filename. */
    public static void requestAsync(String url) {
        if (url == null || url.isBlank() || LOADED.containsKey(url)) return;
        if (System.nanoTime() < RETRY_AFTER.getOrDefault(url, 0L)) return;
        if (!REQUESTED.add(url)) return;
        int requestGeneration = generation;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "AuroraClient (resourcepack browser icon)")
                    .GET().build();
            ModrinthApi.httpClient().sendAsync(req, HttpResponse.BodyHandlers.ofInputStream())
                    .thenAcceptAsync(resp -> {
                        try (var body = resp.body()) {
                            if (resp.statusCode() / 100 != 2) throw new java.io.IOException("HTTP " + resp.statusCode());
                            byte[] bytes = body.readNBytes(PackIconDecoder.MAX_BYTES + 1);
                            byte[] png = PackIconDecoder.png(bytes);
                            Minecraft.getInstance().execute(() -> {
                                if (generation == requestGeneration) uploadOnRenderThread(url, png);
                            });
                        } catch (Exception error) { failure(url, error, requestGeneration); }
                    }, ModrinthApi.IO_EXECUTOR)
                    .exceptionally(error -> { failure(url, error, requestGeneration); return null; });
        } catch (Exception error) { failure(url, error, requestGeneration); }
    }

    private static void failure(String url, Throwable error, int requestGeneration) {
        Minecraft.getInstance().execute(() -> {
            if (generation != requestGeneration) return;
            REQUESTED.remove(url);
            RETRY_AFTER.put(url, System.nanoTime() + Duration.ofSeconds(30).toNanos());
            AuroraClient.LOGGER.debug("Icon load failed for {}: {}", url, error.toString());
        });
    }

    private static void uploadOnRenderThread(String url, byte[] pngBytes) {
        NativeImage img = null;
        DynamicTexture tex = null;
        Identifier id = null;
        try {
            img = NativeImage.read(pngBytes);
            int w = img.getWidth();
            int h = img.getHeight();
            int idx = counter++;
            id = Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "modrinth_icon/" + idx);
            tex = new DynamicTexture(() -> "modrinth_icon_" + idx, img);
            Minecraft.getInstance().getTextureManager().register(id, tex);
            // CRITICAL: register() only maps the id; the pixel data isn't
            // explicitly uploaded here after registration.
            tex.upload();
            // Store original dimensions so the blit can use the correct
            // source texture size (non-square icons are handled by the
            // blitIcon method which reads srcW/srcH from the entry).
            LOADED.put(url, new Entry(id, tex, w, h));
        } catch (Throwable t) {
            if (id != null) Minecraft.getInstance().getTextureManager().release(id);
            if (tex != null) tex.close();
            else if (img != null) img.close();
            failure(url, t, generation);
        }
    }

    /**
     * Release all cached textures. Retained for explicit teardown paths, but
     * note the browser no longer calls this on screen close — icons are kept
     * for the session and bounded by the {@link #MAX_ICONS} LRU cap instead,
     * so re-opening the browser doesn't re-download everything.
     */
    public static void clear() {
        generation++;
        RETRY_AFTER.clear();
        Iterator<Map.Entry<String, Entry>> it = LOADED.entrySet().iterator();
        while (it.hasNext()) {
            Entry e = it.next().getValue();
            try { Minecraft.getInstance().getTextureManager().release(e.textureId); } catch (Throwable ignored) {}
            try { e.texture.close(); } catch (Throwable ignored) {}
            it.remove();
        }
        REQUESTED.clear();
    }

    /** Pipeline id for blitting cached icons via {@link RenderPipelines#GUI_TEXTURED}. */
    public static void blitIcon(net.minecraft.client.gui.GuiGraphics g, Identifier id,
                                 int x, int y, int size) {
        Entry entry = LOADED.values().stream().filter(e -> e.textureId.equals(id)).findFirst().orElse(null);
        if (entry == null) return;
        float fit = Math.min(size / (float) entry.srcW, size / (float) entry.srcH);
        int w = Math.max(1, Math.round(entry.srcW * fit)), h = Math.max(1, Math.round(entry.srcH * fit));
        g.blit(RenderPipelines.GUI_TEXTURED, id, x + (size - w) / 2, y + (size - h) / 2,
                0f, 0f, w, h, entry.srcW, entry.srcH, entry.srcW, entry.srcH, 0xFFFFFFFF);
    }
}
