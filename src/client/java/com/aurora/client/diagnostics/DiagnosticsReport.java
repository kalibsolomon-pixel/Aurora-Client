package com.aurora.client.diagnostics;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.render.RenderBackend;
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
        if (mc.screen != null && mc.level != null) mc.setScreen(null);
        boolean started = FrameBenchmark.start(WARMUP_SECONDS, MEASURE_SECONDS, RenderUtil::fillsSubmitted, stats -> {
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
    private static int ticksInWorld;
    private static boolean logged;

    /** Once per client tick. */
    public static void tick(Minecraft mc) {
        FrameBenchmark.tick();
        if (!LOG_ONCE || logged) return;
        ticksInWorld = mc.level == null ? 0 : ticksInWorld + 1;
        if (ticksInWorld < 60) return;
        logged = true;
        FrameBenchmark.start(0, MEASURE_SECONDS, RenderUtil::fillsSubmitted,
                stats -> AuroraClient.LOGGER.info("[diagnostics]\n{}", build(stats)));
    }

    /** The report for the current session. */
    public static String build(FrameBenchmark.Stats stats) {
        Minecraft mc = Minecraft.getInstance();
        FabricLoader loader = FabricLoader.getInstance();
        DiagnosticsText.Environment env = new DiagnosticsText.Environment(
                version(loader, "aurora"),
                SharedConstants.getCurrentVersion().name(),
                version(loader, "fabricloader"),
                RenderBackend.isOpenGl() ? "OpenGL" : "not OpenGL (Vulkan or other)",
                gpu(),
                mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + ", GUI scale " + mc.getWindow().getGuiScale(),
                measuredOn(mc),
                settings(mc));
        return DiagnosticsText.format(env, mods(loader), stats, RecentErrors.lines());
    }

    private static String version(FabricLoader loader, String id) {
        return loader.getModContainer(id).map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }

    private static String gpu() {
        try {
            var device = RenderSystem.getDevice();
            return device.getRenderer() + " (" + device.getVersion() + ")";
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /** What the benchmark looked at. Never a world or server name. */
    private static String measuredOn(Minecraft mc) {
        if (mc.level == null) return "in a menu, where Minecraft limits the frame rate";
        String where = mc.hasSingleplayerServer() ? "in a singleplayer world" : "on a multiplayer server";
        return mc.screen == null ? where : where + " with a screen open";
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
            SystemToast.add(mc.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal(title), Component.literal(message));
        } catch (Throwable ignored) {}
    }
}
