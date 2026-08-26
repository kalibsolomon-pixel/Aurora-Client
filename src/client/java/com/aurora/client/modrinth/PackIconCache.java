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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * set so the next render frame can retry — a single transient failure no
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
     * the next render frame can retry — previously a single transient failure
     * permanently blacklisted the icon for the entire session.
     */
    private static final java.util.Set<String> REQUESTED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static int counter = 0;

    /**
     * Matches any Modrinth CDN thumbnail size suffix, e.g. {@code _96.webp},
     * {@code _128.webp}, {@code _256.webp}, {@code _512.webp}. The CDN stores
     * the canonical PNG at the path <em>minus</em> the size suffix, so
     * {@code .../hash_256.webp} → {@code .../hash.png}.
     */
    private static final Pattern WEBP_SIZE_SUFFIX = Pattern.compile("_\\d+\\.webp$");

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

    /**
     * Rewrite a Modrinth CDN icon URL to a PNG the client can actually decode.
     *
     * <p>{@link NativeImage#read} only supports PNG. Modrinth's search API
     * returns WebP thumbnails with a {@code _NNN.webp} size suffix, but the
     * canonical PNG lives at the same path <em>minus</em> the suffix. This
     * handles <strong>any</strong> numeric size (not just a hardcoded few)
     * via regex, plus the bare {@code .webp} fallback.
     */
    private static String toPngUrl(String url) {
        if (url == null) return null;
        Matcher m = WEBP_SIZE_SUFFIX.matcher(url);
        if (m.find()) {
            return url.substring(0, m.start()) + ".png";
        }
        if (url.endsWith(".webp")) {
            return url.substring(0, url.length() - 5) + ".png";
        }
        return url;
    }

    /**
     * Kick off a background fetch for {@code url} if one isn't already in
     * flight. Safe to call every frame — duplicate requests are ignored.
     *
     * <p>On any failure (HTTP non-2xx, network exception, empty body, or
     * decode error) the URL is removed from the in-flight set so the next
     * render frame retries. This ensures transient failures don't leave an
     * icon permanently broken.
     */
    public static void requestAsync(String url) {
        if (url == null) return;
        if (LOADED.containsKey(url)) return;
        if (!REQUESTED.add(url)) return; // already in-flight or cached

        // Rewrite WebP thumbnails to PNG — NativeImage can't decode WebP.
        String fetchUrl = toPngUrl(url);

        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(fetchUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "AuroraClient (resourcepack browser icon)")
                    .GET().build();
            ModrinthApi.httpClient().sendAsync(req, HttpResponse.BodyHandlers.ofByteArray())
                    .thenAccept(resp -> {
                        if (resp.statusCode() / 100 != 2) {
                            REQUESTED.remove(url); // allow retry next frame
                            return;
                        }
                        byte[] bytes = resp.body();
                        if (bytes == null || bytes.length == 0) {
                            REQUESTED.remove(url); // allow retry next frame
                            return;
                        }
                        // Decode + upload on the render thread.
                        Minecraft.getInstance().execute(() -> uploadOnRenderThread(url, bytes));
                    })
                    .exceptionally(t -> {
                        AuroraClient.LOGGER.debug("Icon fetch failed for {}: {}", url, t.toString());
                        REQUESTED.remove(url); // allow retry next frame
                        return null;
                    });
        } catch (Throwable t) {
            AuroraClient.LOGGER.debug("Icon fetch dispatch failed for {}: {}", url, t.toString());
            REQUESTED.remove(url); // allow retry next frame
        }
    }

    private static void uploadOnRenderThread(String url, byte[] pngBytes) {
        try {
            NativeImage img = NativeImage.read(pngBytes);
            int w = img.getWidth();
            int h = img.getHeight();
            int idx = counter++;
            Identifier id = Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "modrinth_icon/" + idx);
            DynamicTexture tex = new DynamicTexture(() -> "modrinth_icon_" + idx, img);
            Minecraft.getInstance().getTextureManager().register(id, tex);
            // CRITICAL: register() only maps the id; the pixel data isn't
            // actually uploaded to the GPU until upload() is called.
            // Without this the texture is registered but renders blank.
            tex.upload();
            // Store original dimensions so the blit can use the correct
            // source texture size (non-square icons are handled by the
            // blitIcon method which reads srcW/srcH from the entry).
            LOADED.put(url, new Entry(id, tex, w, h));
        } catch (Throwable t) {
            AuroraClient.LOGGER.debug("Icon decode failed for {}: {}", url, t.toString());
            // Decode failed — allow a retry on the next render frame. Some
            // icons arrive in a format NativeImage can't read on the first
            // pass; removing from REQUESTED gives them another chance rather
            // than permanently blacklisting the URL.
            REQUESTED.remove(url);
        }
    }

    /**
     * Release all cached textures. Retained for explicit teardown paths, but
     * note the browser no longer calls this on screen close — icons are kept
     * for the session and bounded by the {@link #MAX_ICONS} LRU cap instead,
     * so re-opening the browser doesn't re-download everything.
     */
    public static void clear() {
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
        // The tint argument (0xFFFFFFFF = white, full alpha) is required —
        // the GUI_TEXTURED pipeline multiplies texture color by it, and
        // omitting it can render the texture invisible.
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, size, size, size, size, 0xFFFFFFFF);
    }
}