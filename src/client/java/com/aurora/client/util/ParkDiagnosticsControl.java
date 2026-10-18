package com.aurora.client.util;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Developer-only local commands. Collection is off even when commands are opted in. */
public final class ParkDiagnosticsControl {
    private static final AtomicBoolean EXPORTING = new AtomicBoolean();
    private ParkDiagnosticsControl() {}

    public static void initialize() {
        // Initialize the bounded storage during mod startup, never on the
        // first disabled frame. This does not enable collection or read a clock.
        ParkDiagnostics.INSTANCE.isEnabled();
        // Freeze the independent experimental opt-in during Aurora startup.
        if (FramePacer.EXPERIMENTAL_PARK_REPARK) {
            AuroraClient.LOGGER.info("[PARK experiment] Bounded re-park enabled; maximum 2 calls, fixed 1ms retry.");
        }
        if (!Boolean.getBoolean("aurora.parkDiagnostics")) return;
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                literal("aurorapark")
                        .then(literal("start").executes(c -> start(c.getSource())))
                        .then(literal("reset").executes(c -> start(c.getSource())))
                        .then(literal("stop").executes(c -> {
                            ParkDiagnostics.INSTANCE.stop(GLFW.glfwGetTime());
                            return feedback(c.getSource(), "PARK diagnostics OFF; data retained for snapshot.");
                        }))
                        .then(literal("status").executes(c -> feedback(c.getSource(),
                                ParkDiagnostics.INSTANCE.isEnabled() ? "PARK diagnostics ACTIVE" : "PARK diagnostics OFF")))
                        .then(literal("snapshot").executes(c -> export(c.getSource())))));
        AuroraClient.LOGGER.info("[PARK diagnostics] Developer commands enabled; collection OFF. Use /aurorapark start.");
    }

    private static int start(FabricClientCommandSource source) {
        String build = FabricLoader.getInstance().getModContainer("aurora")
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        ParkDiagnostics.INSTANCE.start(build, GLFW.glfwGetTime());
        return feedback(source, "PARK diagnostics ACTIVE; fresh session. Snapshot exports only the current segment.");
    }

    /** No new context object or timestamp; called only while collection is enabled. */
    public static void observe(FramePacing.Decision decision, AuroraConfig cfg, boolean customPacer,
                               boolean focused, double now) {
        ParkDiagnostics.Owner owner = switch (decision.owner()) {
            case NONE -> ParkDiagnostics.Owner.NONE;
            case NATIVE -> ParkDiagnostics.Owner.NATIVE;
            case POST_SWAP -> ParkDiagnostics.Owner.POST_SWAP;
            case FRAME_HEAD -> cfg.frameCapFps > 0 ? ParkDiagnostics.Owner.FRAME_HEAD_GLOBAL
                    : ParkDiagnostics.Owner.FRAME_HEAD_ADAPTIVE;
        };
        AuroraConfig.PacingStrategy strategy = cfg.framePacingStrategy == null
                ? AuroraConfig.PacingStrategy.PARK : cfg.framePacingStrategy;
        String actualStrategy = switch (decision.owner()) {
            case NONE, NATIVE -> "NONE";
            default -> customPacer ? (strategy == AuroraConfig.PacingStrategy.PARK
                    && FramePacer.EXPERIMENTAL_PARK_REPARK ? "PARK_REPARK" : strategy.name()) : "HYBRID_FALLBACK";
        };
        ParkDiagnostics.INSTANCE.observe(owner, actualStrategy, decision.fps(), cfg.frameCapFps,
                cfg.guiFpsLimit, cfg.framePacerSpinThresholdMicros, cfg.framePacerParkThresholdMicros,
                cfg.framePacerLowCpuMode, focused, now);
    }

    private static int export(FabricClientCommandSource source) {
        if (!EXPORTING.compareAndSet(false, true)) return feedback(source, "A PARK snapshot export is already in progress.");
        var snapshot = ParkDiagnostics.INSTANCE.snapshot(GLFW.glfwGetTime());
        var directory = FabricLoader.getInstance().getGameDir().resolve("debug/aurora-park");
        var file = directory.resolve(snapshot.sessionId() + "-" + java.util.UUID.randomUUID() + ".json");
        // Explicit bounded copy above is outside the wait. Formatting and disk I/O
        // use the existing common pool; only one job can be retained/in flight.
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(directory);
                String json = new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(snapshot);
                Files.writeString(file, json, StandardOpenOption.CREATE_NEW);
                source.getClient().execute(() -> feedback(source, "PARK snapshot saved under debug/aurora-park."));
            } catch (Exception e) {
                // Do not log environment paths or arbitrary exception messages.
                source.getClient().execute(() -> feedback(source, "PARK snapshot export failed (" + e.getClass().getSimpleName() + ")."));
            } finally {
                EXPORTING.set(false);
            }
        });
        return feedback(source, "PARK snapshot captured; exporting asynchronously.");
    }

    private static int feedback(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal(message));
        return 1;
    }
}
