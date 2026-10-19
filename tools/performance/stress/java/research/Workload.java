package research;

import com.google.gson.GsonBuilder;
import jdk.jfr.Recording;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Explicit disposable-root driver, kept outside production source sets.
 * Primitive bounded frame buffers; export and JFR dump happen after measurement.
 * Scene operations run on their owning threads and are never physical input.
 */
public final class Workload implements ClientModInitializer {
    private static final long ORIGIN = System.nanoTime();
    private static final int LIMIT = 180_000;
    private static final long[] heads = new long[LIMIT], ends = new long[LIMIT], swaps = new long[LIMIT];
    private static final long[] inputs = new long[LIMIT], applications = new long[LIMIT];
    private static final int[] phases = new int[LIMIT], states = new int[LIMIT];
    private static final List<Map<String, Object>> events = new ArrayList<>(), samples = new ArrayList<>();
    private static volatile boolean enabled, setupComplete;
    private static Path output;
    private static Recording recording;
    private static int count, phase, lastSecond = -1, traversalStep = -1;
    private static long head, swap, lastInput, lastApplied, consumedInput, titleAt, joinedAt, setupAt;
    private static final Map<String, Object> clock = new LinkedHashMap<>();
    private static final java.util.concurrent.ConcurrentLinkedQueue<String> failedCommands = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static boolean opening, setup, finishing;
    private static final String KIND = System.getProperty("aurora.research.kind", "baseline");
    private static final int SECONDS = Integer.getInteger("aurora.research.seconds", 210);

    private static long now() { return System.nanoTime() - ORIGIN; }
    public static void event(String name) {
        var row = new LinkedHashMap<String, Object>();
        row.put("event", name); row.put("ns", now());
        row.put("jvmUptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
        events.add(row);
        org.slf4j.LoggerFactory.getLogger("AuroraResearch").info("Checkpoint {}", name);
    }
    @Override public void onInitializeClient() {
        output = Path.of(System.getProperty("aurora.research.output")).toAbsolutePath().normalize();
        // Deliberate two-factor authorization, refuse original or previously used roots.
        if (!Files.isRegularFile(output.resolve("DISPOSABLE-RESEARCH.txt")) ||
                Files.exists(output.resolve("frames.csv"))) throw new IllegalStateException("New marked research root required");
        event("research-initializer");
        recording = new Recording();
        for (String name : List.of("jdk.GarbageCollection", "jdk.GCPhasePause", "jdk.GCHeapSummary", "jdk.DataLoss"))
            recording.enable(name);
        if (Boolean.getBoolean("aurora.research.profile")) {
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10)).withStackTrace();
            recording.enable("jdk.ObjectAllocationSample").with("throttle", "100/s").withStackTrace();
        }
        recording.setMaxSize(64 * 1024 * 1024); recording.start();
        enabled = true;
    }
    public static void input() { if (enabled) lastInput = now(); }
    public static void applied() {
        if (enabled && lastInput != 0 && consumedInput != lastInput) {
            consumedInput = lastInput; lastApplied = now();
        }
    }
    public static void begin() {
        if (!enabled || finishing) return;
        drive(Minecraft.getInstance());
        head = now(); swap = 0;
    }
    public static void swap() { if (enabled && !finishing) swap = now(); }
    public static void end() {
        if (!enabled || finishing) return;
        var mc = Minecraft.getInstance();
        if (count == LIMIT) throw new IllegalStateException("Research frame buffer overflow");
        heads[count] = head; ends[count] = now(); swaps[count] = swap;
        inputs[count] = lastInput; applications[count] = lastApplied;
        phases[count] = phase;
        states[count] = (mc.level != null ? 1 : 0) | (mc.isWindowActive() ? 2 : 0) |
                (mc.isPaused() ? 4 : 0) | (mc.screen != null ? 8 : 0);
        count++;
    }
    private static void command(Minecraft mc, String text) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), text));
    }
    private static void drive(Minecraft mc) {
        long time = now();
        if (titleAt == 0 && mc.getOverlay() == null && mc.screen != null &&
                (mc.screen instanceof TitleScreen || mc.screen.getClass().getSimpleName().equals("AuroraTitleScreen"))) {
            titleAt = time; event("main-menu-renderable");
            clock.put("nanoBefore", now());
            clock.put("qpcTick", org.lwjgl.glfw.GLFW.glfwGetTimerValue());
            clock.put("qpcFrequency", org.lwjgl.glfw.GLFW.glfwGetTimerFrequency());
            clock.put("nanoAfter", now());
            clock.put("framebufferWidth", mc.getWindow().getWidth());
            clock.put("framebufferHeight", mc.getWindow().getHeight());
        }
        if (Boolean.getBoolean("aurora.research.menuOnly")) {
            int exitAfter = Integer.getInteger("aurora.research.menuExitSeconds", 0);
            if (titleAt != 0 && exitAfter > 0 && time - titleAt >= exitAfter * 1_000_000_000L) {
                finishing = true; event("menu-observation-complete");
                CompletableFuture.runAsync(Workload::export).whenComplete((done, failure) -> {
                    if (failure != null) failure.printStackTrace();
                    mc.execute(mc::stop);
                });
            }
            return;
        }
        if (!opening && titleAt != 0 && time - titleAt > 3_000_000_000L) {
            opening = true; event("world-open-request");
            mc.createWorldOpenFlows().openWorld("Cperf1a", () -> mc.setScreen(new TitleScreen()));
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return;
        if (joinedAt == 0 && mc.screen == null) { joinedAt = time; event("first-interactive-frame"); }
        if (!setup) {
            setup = true; event("scene-setup-start");
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                var source = server.createCommandSourceStack().withSuppressedOutput();
                var commands = server.getCommands();
                List<String> todo = new ArrayList<>(List.of("tick unfreeze", "gamerule minecraft:advance_time false",
                        "gamerule minecraft:advance_weather false", "time set noon", "weather clear", "gamemode spectator @a",
                        "tp @a 0 95 0 0 25"));
                if (!KIND.equals("baseline") && !KIND.equals("startup") && !KIND.equals("input")) {
                    // Setup is outside measurement. Generate the complete fill
                    // footprint before commands that reject unloaded blocks.
                    for (int cx = -2; cx <= 1; cx++) for (int cz = 0; cz <= 3; cz++)
                        server.overworld().getChunk(cx, cz);
                    todo.add("fill -24 79 8 24 79 56 minecraft:stone");
                    todo.add("fill -8 80 20 7 80 35 minecraft:hopper");
                    for (int i = 0; i < 192; i++) {
                        int x = i % 24 - 12, z = 12 + i / 24 * 3;
                        todo.add("summon minecraft:sheep " + x + " 80 " + z + " {PersistenceRequired:1b}");
                    }
                    for (int i = 0; i < 64; i++) todo.add("summon minecraft:item " + (i % 8 - 4) + " 81 " + (24 + i / 8) +
                            " {Item:{id:\"minecraft:cobblestone\",count:64},PickupDelay:32767,Age:-32768}");
                }
                for (String text : todo) commands.performPrefixedCommand(source.withCallback((success, result) -> {
                    if (!success) failedCommands.add(text);
                }), text);
                setupComplete = true;
            });
        }
        if (!setupComplete || joinedAt == 0) return;
        if (setupAt == 0) { setupAt = time; event("scene-setup-complete"); }
        int second = (int)((time - setupAt) / 1_000_000_000L);
        int nextPhase = second < 30 ? 0 : second < 90 ? 1 : second < 150 ? 2 : second < 210 ? 3 : 4;
        if (KIND.equals("baseline") || KIND.equals("startup") || KIND.equals("input")) nextPhase = second < 30 ? 0 : 1;
        if (nextPhase != phase) { phase = nextPhase; event("phase-" + phase); if (phase == 1) InputTiming.start(); }
        if (phase == 2 && second / 3 != traversalStep) {
            traversalStep = second / 3;
            // Exactly 20 steps of 64 blocks, 1280 blocks of new chunk loading.
            command(mc, "tp @a " + ((second - 90) / 3 * 64) + " 120 0 90 25");
        }
        if (phase == 3 && second % 10 == 0 && second != lastSecond) {
            if (mc.screen == null) {
                try { mc.setScreen((net.minecraft.client.gui.screens.Screen) Class.forName("com.aurora.client.screen.AuroraScreen").getMethod("create").invoke(null)); }
                catch (ReflectiveOperationException absent) { mc.pauseGame(false); }
            } else mc.setScreen(null);
        }
        if (phase == 4 && mc.screen != null) mc.setScreen(null);
        if (second != lastSecond) {
            lastSecond = second;
            var runtime = Runtime.getRuntime();
            var sample = new LinkedHashMap<String, Object>();
            sample.put("ns", time); sample.put("second", second); sample.put("phase", phase);
            sample.put("heapUsed", runtime.totalMemory() - runtime.freeMemory());
            sample.put("heapCommitted", runtime.totalMemory()); sample.put("heapMax", runtime.maxMemory());
            sample.put("entities", mc.level.getEntityCount());
            sample.put("x", mc.player.getX()); sample.put("y", mc.player.getY()); sample.put("z", mc.player.getZ());
            sample.put("yaw", mc.player.getYRot()); sample.put("pitch", mc.player.getXRot());
            sample.put("serverAverageTickNs", mc.getSingleplayerServer().getAverageTickTimeNanos());
            samples.add(sample);
            if (second == 1) org.slf4j.LoggerFactory.getLogger("AuroraResearch").info("Scene checkpoint {}", sample);
        }
        if (second >= SECONDS) {
            finishing = true; event("measurement-complete");
            InputTiming.stop();
            mc.disconnectFromWorld(Component.literal("Research complete")); event("world-disconnected");
            CompletableFuture.runAsync(Workload::export).whenComplete((done, failure) -> {
                if (failure != null) failure.printStackTrace();
                mc.execute(mc::stop);
            });
        }
    }
    private static void export() {
        try {
            recording.stop(); recording.dump(output.resolve("profile.jfr")); recording.close();
            InputTiming.export(output);
            try (var writer = Files.newBufferedWriter(output.resolve("frames.csv"), StandardOpenOption.CREATE_NEW)) {
                writer.write("head_ns,end_ns,swap_ns,input_ns,applied_ns,phase,state\n");
                for (int i = 0; i < count; i++) writer.write(heads[i] + "," + ends[i] + "," + swaps[i] + "," + inputs[i] + "," + applications[i] + "," + phases[i] + "," + states[i] + "\n");
            }
            var result = new LinkedHashMap<String, Object>();
            result.put("kind", KIND); result.put("durationSeconds", SECONDS); result.put("frames", count);
            result.put("menuOnly", Boolean.getBoolean("aurora.research.menuOnly"));
            result.put("events", events); result.put("samples", samples);
            result.put("clock", clock);
            result.put("failedSetupCommands", failedCommands);
            result.put("mods", net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().stream().map(m -> m.getMetadata().getId() + ":" + m.getMetadata().getVersion()).sorted().toList());
            Files.writeString(output.resolve("run.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result), StandardOpenOption.CREATE_NEW);
        } catch (Exception failure) { throw new RuntimeException(failure); }
    }
}
