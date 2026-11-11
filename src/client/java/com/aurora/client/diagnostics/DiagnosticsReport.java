package com.aurora.client.diagnostics;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Copy Diagnostics" action (ADR 0004): closes the open screen, measures
 * a few seconds of frames, then puts a plain-text report on the clipboard.
 * The player pastes it wherever they are asking for help; Aurora sends
 * nothing anywhere.
 */
public final class DiagnosticsReport {
    private DiagnosticsReport() {}

    private static final double WARMUP_SECONDS = 0.5;
    private static final double MEASURE_SECONDS = 5.0;

    /** Start the benchmark; the report is copied when it finishes. Client thread. */
    public static void runAndCopy() {
        Minecraft mc = Minecraft.getInstance();
        // The benchmark is about the game, not about the Aurora screen this was pressed on.
        if (mc.gui.screen() != null && mc.level != null) mc.gui.setScreen(null);
        boolean started = FrameBenchmark.start(WARMUP_SECONDS, MEASURE_SECONDS, RenderUtil::fillsSubmitted,
                BlurPanelRenderer::capturesSubmitted, stats -> {
            try {
                Minecraft client = Minecraft.getInstance();
                client.keyboardHandler.setClipboard(build(stats));
                toast(client, "Aurora diagnostics copied", "Paste the report where you are asking for help.");
            } catch (Throwable t) {
                AuroraClient.LOGGER.error("Could not build the diagnostics report", t);
            }
        });
        if (started) toast(mc, "Measuring for 5 seconds", "The diagnostics report is copied when it finishes.");
    }

    /**
     * {@code -Daurora.diagnostics.log=true}: three seconds after a world is
     * joined, run the benchmark once and write the report to the log instead
     * of the clipboard. For scripted boots (the tested mod matrix).
     */
    private static final boolean LOG_ONCE = Boolean.getBoolean("aurora.diagnostics.log");
    /** {@code -Daurora.diagnostics.runs=N}: log N reports back to back, for a stable overhead baseline. */
    private static final int LOG_RUNS = Math.max(1, Integer.getInteger("aurora.diagnostics.runs", 1));
    private static int ticksInWorld;
    private static int logged;

    /** Once per client tick. */
    public static void tick(Minecraft mc) {
        FrameBenchmark.tick();
        if (!LOG_ONCE || logged >= LOG_RUNS || FrameBenchmark.running()) return;
        ticksInWorld = mc.level == null || mc.gui.screen() != null ? 0 : ticksInWorld + 1;
        if (ticksInWorld < 60) return;
        int run = ++logged;
        FrameBenchmark.start(0, MEASURE_SECONDS, RenderUtil::fillsSubmitted, BlurPanelRenderer::capturesSubmitted,
                stats -> AuroraClient.LOGGER.info("[diagnostics] run {} of {}\n{}", run, LOG_RUNS, build(stats)));
    }

    /** The report for the current session. */
    public static String build(FrameBenchmark.Stats stats) {
        Minecraft mc = Minecraft.getInstance();
        FabricLoader loader = FabricLoader.getInstance();
        DiagnosticsText.Environment env = new DiagnosticsText.Environment(
                version(loader, "aurora"),
                SharedConstants.getCurrentVersion().name(),
                version(loader, "fabricloader"),
                RenderSystem.getDevice().getDeviceInfo().backendName(),
                gpu(),
                mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + ", GUI scale " + mc.getWindow().getGuiScale(),
                measuredOn(mc),
                mc.level != null && mc.gui.screen() == null,
                settings(mc));
        return DiagnosticsText.format(env, mods(loader), stats, RecentErrors.lines());
    }

    private static String version(FabricLoader loader, String id) {
        return loader.getModContainer(id).map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    private static String gpu() {
        try {
            var device = RenderSystem.getDevice();
            return device.getDeviceInfo().name() + " (" + device.getDeviceInfo().driverInfo() + ")";
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /** What the benchmark looked at. Never a world or server name. */
    private static String measuredOn(Minecraft mc) {
        if (mc.level == null) return "in a menu, where Minecraft limits the frame rate";
        String where = mc.hasSingleplayerServer() ? "in a singleplayer world" : "on a multiplayer server";
        return mc.gui.screen() == null ? where : where + " with a screen open";
    }

    private static List<String> settings(Minecraft mc) {
        AuroraConfig cfg = AuroraConfig.get();
        List<String> out = new ArrayList<>();
        try {
            out.add("fps limit " + mc.options.framerateLimit().get());
            out.add("vsync " + (mc.options.enableVsync().get() ? "on" : "off"));
            out.add("render distance " + mc.options.renderDistance().get());
        } catch (Throwable ignored) {}
        out.add("Aurora frame cap " + (cfg.frameCapFps > 0 ? cfg.frameCapFps : "off"));
        out.add("frame pacer " + (cfg.smoothFramePacer ? String.valueOf(cfg.framePacingStrategy) : "off"));
        out.add("low latency " + (cfg.lowLatencyRender ? "on" : "off"));
        return out;
    }

    /** Top-level mods only: the modules nested inside another mod's jar are noise. */
    private static List<String> mods(FabricLoader loader) {
        List<String> out = new ArrayList<>();
        boolean fabricApi = loader.isModLoaded("fabric-api");
        for (ModContainer mod : loader.getAllMods()) {
            if (mod.getContainingMod().isPresent()) continue;
            String id = mod.getMetadata().getId();
            if (id.equals("java") || id.equals("minecraft") || id.equals("fabricloader")) continue;
            // In a development run Fabric API's modules are not nested; one line covers them.
            if (fabricApi && id.startsWith("fabric-") && !id.equals("fabric-api")) continue;
            out.add(id + " " + mod.getMetadata().getVersion().getFriendlyString());
        }
        out.sort(String::compareTo);
        return out;
    }

    private static void toast(Minecraft mc, String title, String message) {
        try {
            SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal(title), Component.literal(message));
        } catch (Throwable ignored) {}
    }
}
