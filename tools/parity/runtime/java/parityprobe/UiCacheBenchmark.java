package parityprobe;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.util.NativeShapeRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.google.gson.GsonBuilder;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Disposable-fixture instrumentation; never included in Aurora's production artifact. */
public final class UiCacheBenchmark {
    private static final int WARMUP = 120, SAMPLES = 600;
    private static final long[] elapsed = new long[WARMUP + SAMPLES];
    private static final long[] intervals = new long[SAMPLES];
    private record Storage(Object image, long bytes) {}
    private static final Map<Object, Storage> storage = new WeakHashMap<>();
    private static long allocations, commits, uploadBytes, commitNanos;
    private static Path output;
    private static Screen expected;
    private static InactivityFpsLimit previousInactivity;
    private static int frames;
    private static boolean scrolling, fresh;
    private static long start, previousStart, firstFills, firstQuads;
    private static Map<String, Long> cacheStart;

    private UiCacheBenchmark() {}

    /** Called only by the optional layer-cache mixin, once at allocation boundaries. */
    public static void observe(Object owner, Object image, int width, int height) {
        if (image == null) return;
        Storage old = storage.get(owner);
        if (old == null || old.image != image) {
            allocations++;
            storage.put(owner, new Storage(image, (long) width * height * 4));
        }
    }

    public static void dispose(Object owner) { storage.remove(owner); }

    public static void commit(long nanos, int width, int height) {
        commits++;
        uploadBytes += (long) width * height * 4;
        commitNanos += nanos;
    }

    public static Map<String, Long> cacheState() {
        long bytes = 0;
        for (Storage s : storage.values()) bytes += s.bytes;
        return Map.of("allocations", allocations, "commits", commits,
                "uploadPayloadBytes", uploadBytes, "commitElapsedNanos", commitNanos,
                "trackedBackingBytes", bytes, "trackedImages", (long) storage.size());
    }

    public static void arm(Path destination, Screen screen, boolean scroll, boolean newlyOpened) {
        if (Files.exists(destination)) throw new IllegalStateException("Result already exists: " + destination);
        cancel();
        previousInactivity = Minecraft.getInstance().options.inactivityFpsLimit().get();
        Minecraft.getInstance().options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
        output = destination;
        expected = screen;
        scrolling = scroll;
        fresh = newlyOpened;
        frames = 0;
        start = previousStart = 0;
        cacheStart = cacheState();
        firstFills = RenderUtil.fillsSubmitted();
        firstQuads = NativeShapeRenderer.quadsSubmitted();
    }

    public static void cancelIfDifferent(Screen current) {
        if (output != null && current != expected) cancel();
    }

    public static void cancel() {
        output = null;
        expected = null;
        start = 0;
        if (previousInactivity != null) {
            Minecraft.getInstance().options.inactivityFpsLimit().set(previousInactivity);
            previousInactivity = null;
        }
    }

    public static void begin(Object screen) {
        if (output == null || screen != expected) return;
        // Deterministic wheel input is outside the extraction clock. Intervals
        // still include it, rendering, the world, scheduling and pacing waits.
        if (scrolling && frames % 4 == 0) expected.mouseScrolled(
                expected.width / 2.0, expected.height / 2.0, 0, (frames / 120) % 2 == 0 ? -1 : 1);
        long now = System.nanoTime();
        if (frames >= WARMUP) intervals[frames - WARMUP] = now - previousStart;
        previousStart = start = now;
    }

    public static void end(Object screen) {
        if (output == null || screen != expected || start == 0) return;
        elapsed[frames++] = System.nanoTime() - start;
        start = 0;
        if (frames != elapsed.length) return;
        try {
            var mc = Minecraft.getInstance();
            var cfg = AuroraConfig.get();
            var report = new LinkedHashMap<String, Object>();
            report.put("scope", "Elapsed screen extraction; excludes deferred GUI rendering and GPU time; may include thread stalls");
            report.put("intervalScope", "Time between extraction starts, including rendering, wheel input, world work and pacing");
            report.put("cacheScope", "Observed UiLayerCache images and commit payloads, including shared material templates; not measured VRAM");
            report.put("screen", expected.getClass().getName());
            report.put("freshScreen", fresh);
            report.put("scrolling", scrolling);
            report.put("revision", System.getProperty("aurora.ui.revision", "unspecified"));
            report.put("backend", System.getProperty("aurora.ui.backend", "unspecified"));
            report.put("minecraft", System.getProperty("fabric.modVersion.minecraft", "unspecified"));
            report.put("java", System.getProperty("java.version"));
            report.put("framebufferWidth", mc.getWindow().getWidth());
            report.put("framebufferHeight", mc.getWindow().getHeight());
            report.put("guiScale", mc.getWindow().getGuiScale());
            report.put("worldLoaded", mc.level != null);
            report.put("backgroundOpacity", cfg.themeOrDefault().backgroundOpacity);
            report.put("cornerRadius", com.aurora.client.util.AuroraTheme.RADIUS);
            report.put("nativeFpsOption", mc.options.framerateLimit().get());
            report.put("effectiveNativeFpsLimit", mc.getFramerateLimitTracker().getFramerateLimit());
            report.put("warmupFrames", WARMUP);
            report.put("sampleFrames", SAMPLES);
            report.put("firstExtractionMs", elapsed[0] / 1e6);
            report.put("warmup", summarize(Arrays.copyOf(elapsed, WARMUP)));
            report.put("steadyExtraction", summarize(Arrays.copyOfRange(elapsed, WARMUP, elapsed.length)));
            report.put("steadyFrameIntervals", summarize(intervals));
            report.put("cacheAtArm", cacheStart);
            report.put("cacheAtEnd", cacheState());
            var delta = new LinkedHashMap<String, Long>();
            var end = cacheState();
            for (String key : new String[]{"allocations", "commits", "uploadPayloadBytes", "commitElapsedNanos"})
                delta.put(key, end.get(key) - cacheStart.get(key));
            report.put("cacheDuringRun", delta);
            report.put("fillsDuringRun", RenderUtil.fillsSubmitted() - firstFills);
            report.put("shaderQuadsDuringRun", NativeShapeRenderer.quadsSubmitted() - firstQuads);
            write(output, report);
            System.out.println("[parity-probe] UI cache benchmark complete: " + output);
        } catch (Exception failure) {
            throw new RuntimeException("UI cache benchmark failed", failure);
        } finally {
            cancel();
        }
    }

    private static Map<String, Double> summarize(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return Map.of("meanMs", Arrays.stream(values).average().orElseThrow() / 1e6,
                "p95Ms", sorted[(int) Math.ceil(sorted.length * .95) - 1] / 1e6,
                "p99Ms", sorted[(int) Math.ceil(sorted.length * .99) - 1] / 1e6,
                "maxMs", sorted[sorted.length - 1] / 1e6);
    }

    public static void write(Path destination, Object data) throws Exception {
        Files.writeString(destination, new GsonBuilder().setPrettyPrinting().create().toJson(data) + "\n",
                StandardOpenOption.CREATE_NEW);
    }
}
