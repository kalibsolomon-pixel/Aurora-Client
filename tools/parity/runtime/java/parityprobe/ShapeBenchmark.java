package parityprobe;

import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.ui.util.NativeShapeRenderer;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;

/** Measures elapsed time in GUI extraction, not deferred rendering or total GPU time. */
public final class ShapeBenchmark {
    private static final int WARMUP = 120, SAMPLES = 600;
    private static final long[] nanos = new long[SAMPLES];
    private static final long[] intervals = new long[SAMPLES];
    private static Path result;
    private static int frames;
    private static long start, previousStart, firstFills, firstQuads;

    private ShapeBenchmark() {}

    public static void arm(Path output) {
        result = output;
        frames = 0;
        start = 0;
        previousStart = 0;
    }

    public static void begin() {
        if (result == null) return;
        if (frames == WARMUP) {
            firstFills = RenderUtil.fillsSubmitted();
            firstQuads = NativeShapeRenderer.quadsSubmitted();
        }
        start = System.nanoTime();
        if (frames >= WARMUP && previousStart != 0) intervals[frames - WARMUP] = start - previousStart;
        previousStart = start;
    }

    public static void end() {
        if (result == null || start == 0) return;
        long elapsed = System.nanoTime() - start;
        start = 0;
        if (frames >= WARMUP) nanos[frames - WARMUP] = elapsed;
        if (++frames != WARMUP + SAMPLES) return;
        long fills = RenderUtil.fillsSubmitted() - firstFills;
        long quads = NativeShapeRenderer.quadsSubmitted() - firstQuads;
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        long[] sortedIntervals = intervals.clone();
        Arrays.sort(sortedIntervals);
        double mean = Arrays.stream(nanos).average().orElseThrow() / 1_000_000.0;
        var window = Minecraft.getInstance().getWindow();
        var data = new LinkedHashMap<String, Object>();
        data.put("scope", "Elapsed time in WaypointManagerScreen.extractRenderState; excludes deferred GUI rendering and total GPU time");
        data.put("waypoints", 40);
        data.put("warmupFrames", WARMUP);
        data.put("sampleFrames", SAMPLES);
        data.put("framebufferWidth", window.getWidth());
        data.put("framebufferHeight", window.getHeight());
        data.put("guiScale", window.getGuiScale());
        data.put("meanMs", mean);
        data.put("p95Ms", sorted[(int) Math.ceil(SAMPLES * .95) - 1] / 1_000_000.0);
        data.put("frameIntervalMeanMs", Arrays.stream(intervals).average().orElseThrow() / 1_000_000.0);
        data.put("frameIntervalP95Ms", sortedIntervals[(int) Math.ceil(SAMPLES * .95) - 1] / 1_000_000.0);
        data.put("fillsPerFrame", (double) fills / SAMPLES);
        data.put("shaderQuadsPerFrame", (double) quads / SAMPLES);
        try {
            Files.writeString(result, new GsonBuilder().setPrettyPrinting().create().toJson(data) + "\n");
        } catch (Exception failure) {
            throw new RuntimeException("Could not write shape benchmark", failure);
        } finally {
            result = null;
        }
    }
}
