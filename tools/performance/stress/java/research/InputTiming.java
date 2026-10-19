package research;

import net.minecraft.client.Minecraft;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Last callback consumed per rendered frame, not a hardware or photon measurement. */
public final class InputTiming {
    private static final boolean ENABLED = System.getProperty("aurora.research.kind", "").equals("input");
    private static final int LIMIT = 12_000;
    private static final long[] arrivals = ENABLED ? new long[LIMIT] : null,
            applications = ENABLED ? new long[LIMIT] : null, cameras = ENABLED ? new long[LIMIT] : null,
            swaps = ENABLED ? new long[LIMIT] : null,
            swapBegins = ENABLED ? new long[LIMIT] : null, swapEnds = ENABLED ? new long[LIMIT] : null,
            swapTicks = ENABLED ? new long[LIMIT] : null;
    private static boolean active;
    private static long latest, consumed, applied, camera, swap, recorded, callbacks;
    private static int count;
    private static int swapCount;
    public static void start() { if (ENABLED) { active = true; latest = consumed = 0; } }
    public static void stop() { active = false; }
    public static void arrival() { if (active) { latest = System.nanoTime(); callbacks++; } }
    public static void applied() {
        if (!active || latest == 0 || latest == consumed) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !mc.mouseHandler.isMouseGrabbed()) return;
        consumed = latest; applied = System.nanoTime(); camera = swap = 0;
    }
    public static void camera() { if (active && consumed != 0) camera = System.nanoTime(); }
    public static void swapEntry() {
        if (!active) return;
        if (swapCount == LIMIT) throw new IllegalStateException("Swap observation buffer overflow");
        swap = System.nanoTime(); swapBegins[swapCount] = swap;
        swapTicks[swapCount] = org.lwjgl.glfw.GLFW.glfwGetTimerValue();
    }
    public static void swapReturn() {
        if (!active) return;
        swapEnds[swapCount] = System.nanoTime(); swapCount++;
    }
    public static void endFrame() {
        if (!active || consumed == 0 || consumed == recorded || camera < applied || swap < camera) return;
        if (count == LIMIT) throw new IllegalStateException("Input observation buffer overflow");
        arrivals[count] = consumed; applications[count] = applied;
        cameras[count] = camera; swaps[count] = swap;
        count++; recorded = consumed;
    }
    public static void export(Path output) throws Exception {
        if (!ENABLED) return;
        try (var writer = Files.newBufferedWriter(output.resolve("input-events.csv"), StandardOpenOption.CREATE_NEW)) {
            writer.write("arrival_ns,application_return_ns,camera_setup_return_ns,swap_entry_ns\n");
            for (int i = 0; i < count; i++) writer.write(arrivals[i] + "," + applications[i] + "," + cameras[i] + "," + swaps[i] + "\n");
        }
        try (var writer = Files.newBufferedWriter(output.resolve("swap-calls.csv"), StandardOpenOption.CREATE_NEW)) {
            writer.write("qpc_tick,entry_ns,return_ns\n");
            for (int i = 0; i < swapCount; i++) writer.write(swapTicks[i] + "," + swapBegins[i] + "," + swapEnds[i] + "\n");
        }
        Files.writeString(output.resolve("input-population.json"), "{\"callbacks\":" + callbacks + ",\"pairedFrames\":" + count +
                ",\"swapCalls\":" + swapCount + ",\"qpcFrequency\":" + org.lwjgl.glfw.GLFW.glfwGetTimerFrequency() +
                ",\"source\":\"GLFW callbacks; last consumed callback per frame\",\"physicalLatencyMeasured\":false}", StandardOpenOption.CREATE_NEW);
    }
}
