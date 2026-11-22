package com.aurora.client.screenshot;

import com.aurora.client.AuroraClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Asynchronous PNG thumbnail/preview decoding, deferred GPU texture disposal
 * (obeying rendering rule 10: textures are retired and freed at the start of
 * the next frame inside {@code beginFrame()}), non-destructive cropping, and
 * OS image clipboard copy.
 */
public final class ScreenshotIO {
    private static final Logger LOGGER = LoggerFactory.getLogger("Aurora");
    private static final int MAX_CACHED_THUMBS = 36;
    private static final int THUMB_MAX_W = 280;
    private static final int THUMB_MAX_H = 160;
    private static final int PREVIEW_MAX_W = 960;
    private static final int PREVIEW_MAX_H = 540;

    private static final AtomicLong SEQ = new AtomicLong();
    private static final ExecutorService IO_POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Aurora-ScreenshotIO");
        t.setDaemon(true);
        return t;
    });

    public record LoadedTexture(Identifier id, int width, int height, int sourceWidth, int sourceHeight) {}

    private record DecodedResult(String key, NativeImage image, int sourceW, int sourceH) {}

    private static final Queue<Identifier> RETIRED_IDS = new ConcurrentLinkedQueue<>();
    private static final Queue<DecodedResult> READY_UPLOADS = new ConcurrentLinkedQueue<>();
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    // Render-thread-only bounded LRU cache (access-ordered)
    private static final LinkedHashMap<String, LoadedTexture> CACHE = new LinkedHashMap<>(32, 0.75f, true);

    private ScreenshotIO() {}

    /**
     * Frees textures retired during previous frames. Invoked once per frame
     * from {@code UiLayerCache.beginFrame()} before any new blits are queued.
     */
    public static void drainRetired() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getTextureManager() == null) return;
        Identifier id;
        while ((id = RETIRED_IDS.poll()) != null) {
            try {
                mc.getTextureManager().release(id);
            } catch (Throwable ignored) {}
        }
    }

    /** Uploads any decoded images ready from the worker pool (call on render thread). */
    public static void pumpUploads() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getTextureManager() == null) return;
        int budget = 3; // cap per-frame uploads so scrolling stays smooth
        DecodedResult ready;
        while (budget-- > 0 && (ready = READY_UPLOADS.poll()) != null) {
            NativeImage img = ready.image();
            if (img == null) continue;
            long seq = SEQ.incrementAndGet();
            Identifier texId = Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "screenshots/" + seq);
            DynamicTexture tex = new DynamicTexture(() -> "aurora_screenshot_" + seq, img);
            mc.getTextureManager().register(texId, tex);
            LoadedTexture prev = CACHE.put(ready.key(),
                    new LoadedTexture(texId, img.getWidth(), img.getHeight(), ready.sourceW(), ready.sourceH()));
            if (prev != null) RETIRED_IDS.add(prev.id());
            evictIfOverCap();
        }
    }

    /** Requests a thumbnail texture for {@code entry}; returns null while decoding asynchronously. */
    public static LoadedTexture thumbnail(ScreenshotIndex.Entry entry) {
        return request(entry, false);
    }

    /** Requests a higher-resolution preview texture for {@code entry}; returns null while decoding. */
    public static LoadedTexture preview(ScreenshotIndex.Entry entry) {
        return request(entry, true);
    }

    private static LoadedTexture request(ScreenshotIndex.Entry entry, boolean large) {
        if (entry == null) return null;
        String key = (large ? "L:" : "T:") + entry.path() + ":" + entry.modifiedMs();
        LoadedTexture cached = CACHE.get(key);
        if (cached != null) return cached;
        if (FAILED.contains(key) || !IN_FLIGHT.add(key)) return null;

        int maxW = large ? PREVIEW_MAX_W : THUMB_MAX_W;
        int maxH = large ? PREVIEW_MAX_H : THUMB_MAX_H;
        Path path = entry.path();
        IO_POOL.execute(() -> {
            try (InputStream in = Files.newInputStream(path);
                 NativeImage raw = NativeImage.read(in)) {
                int srcW = raw.getWidth();
                int srcH = raw.getHeight();
                NativeImage scaled = downscale(raw, maxW, maxH);
                READY_UPLOADS.add(new DecodedResult(key, scaled, srcW, srcH));
            } catch (Throwable t) {
                FAILED.add(key);
                LOGGER.warn("[Screenshots] failed to decode {}: {}", path.getFileName(), t.getMessage());
            } finally {
                IN_FLIGHT.remove(key);
            }
        });
        return null;
    }

    /** Retires all cached screenshot textures safely (actual GPU release happens next frame). */
    public static void retireAll() {
        for (LoadedTexture lt : CACHE.values()) {
            RETIRED_IDS.add(lt.id());
        }
        CACHE.clear();
        FAILED.clear();
        DecodedResult pending;
        while ((pending = READY_UPLOADS.poll()) != null) {
            if (pending.image() != null) pending.image().close();
        }
    }

    private static void evictIfOverCap() {
        Iterator<Map.Entry<String, LoadedTexture>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_CACHED_THUMBS && it.hasNext()) {
            Map.Entry<String, LoadedTexture> oldest = it.next();
            RETIRED_IDS.add(oldest.getValue().id());
            it.remove();
        }
    }

    /** Downscales {@code src} into a fresh {@link NativeImage} preserving aspect ratio. */
    private static NativeImage downscale(NativeImage src, int maxW, int maxH) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        double scale = Math.min(1.0, Math.min((double) maxW / sw, (double) maxH / sh));
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

    /** Crops {@code source} off-thread into a new sibling PNG and returns the output path. */
    public static CompletableFuture<Path> cropAsync(Path source, float nx0, float ny0, float nx1, float ny1) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream in = Files.newInputStream(source);
                 NativeImage raw = NativeImage.read(in)) {
                ScreenshotIndex.CropRect rect = ScreenshotIndex.computeCropRect(
                        raw.getWidth(), raw.getHeight(), nx0, ny0, nx1, ny1);
                if (rect.width() < 4 || rect.height() < 4) {
                    throw new IllegalArgumentException("Crop selection is too small");
                }
                Path out = ScreenshotIndex.croppedOutputPath(source);
                try (NativeImage cropped = new NativeImage(NativeImage.Format.RGBA, rect.width(), rect.height(), false)) {
                    for (int y = 0; y < rect.height(); y++) {
                        for (int x = 0; x < rect.width(); x++) {
                            cropped.setPixel(x, y, raw.getPixel(rect.x() + x, rect.y() + y));
                        }
                    }
                    cropped.writeToFile(out);
                }
                return out;
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }, IO_POOL);
    }

    /**
     * Copies a PNG screenshot to the OS clipboard off-thread. Falls back to
     * copying the file path to Minecraft's clipboard if OS image clipboard
     * transfer is unavailable.
     */
    public static CompletableFuture<String> copyImageToClipboardAsync(Path imagePath) {
        return CompletableFuture.supplyAsync(() -> {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            Path abs = imagePath.toAbsolutePath().normalize();
            try {
                if (os.contains("win")) {
                    String escaped = abs.toString().replace("'", "''");
                    String ps = "Add-Type -AssemblyName System.Windows.Forms; "
                            + "$img = [System.Drawing.Image]::FromFile('" + escaped + "'); "
                            + "try { [System.Windows.Forms.Clipboard]::SetImage($img) } finally { $img.Dispose() }";
                    Process p = new ProcessBuilder("powershell", "-NoProfile", "-STA", "-Command", ps)
                            .redirectErrorStream(true).start();
                    if (p.waitFor() == 0) return "Copied image to clipboard";
                } else if (os.contains("mac")) {
                    String escaped = abs.toString().replace("\\", "\\\\").replace("\"", "\\\"");
                    Process p = new ProcessBuilder("osascript", "-e",
                            "set the clipboard to (read (POSIX file \"" + escaped + "\") as «class PNGf»)")
                            .redirectErrorStream(true).start();
                    if (p.waitFor() == 0) return "Copied image to clipboard";
                } else {
                    Process p = new ProcessBuilder("sh", "-c",
                            "(command -v wl-copy >/dev/null && wl-copy -t image/png < \"$1\") || "
                                    + "(command -v xclip >/dev/null && xclip -selection clipboard -t image/png -i \"$1\")",
                            "sh", abs.toString())
                            .redirectErrorStream(true).start();
                    if (p.waitFor() == 0) return "Copied image to clipboard";
                }
            } catch (Throwable t) {
                LOGGER.warn("[Screenshots] OS image clipboard transfer failed: {}", t.getMessage());
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> mc.keyboardHandler.setClipboard(abs.toString()));
            }
            return "Copied screenshot path";
        }, IO_POOL);
    }
}
