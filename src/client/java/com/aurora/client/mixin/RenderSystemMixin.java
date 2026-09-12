package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.FramePacer;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla's frame-pacing logic with Aurora's configurable pacer.
 *
 * <p>Vanilla's {@code limitDisplayFPS} uses a single {@code glfwWaitEventsTimeout}
 * which on Windows is bound to the OS scheduler quantum (~15.6 ms) and produces
 * uneven frame intervals at any FPS cap above ~64. It also sets
 * {@code lastDrawTime = now} (instead of {@code target}), so every frame's
 * drift accumulates — the visible cap ends up several FPS below the configured one.
 *
 * <p>This pacer:
 * <ul>
 *   <li>Dispatches to one of five strategies (vanilla / yield / park / hybrid / spin)
 *       per the user's {@link AuroraConfig#framePacingStrategy}.</li>
 *   <li>Anchors {@code aurora$nextFrameTarget} to the prior target plus
 *       {@code 1/fps}, so OS jitter doesn't accumulate.</li>
 *   <li>Re-anchors if we fall more than half a frame behind (handles
 *       hitches like alt-tab and GC pauses cleanly).</li>
 * </ul>
 */
@Mixin(value = RenderSystem.class, remap = false, priority = 10000)
public abstract class RenderSystemMixin {

    @Unique private static double aurora$nextFrameTarget = 0.0;
    @Unique private static int aurora$lastFps = 0;

    @Inject(method = "limitDisplayFPS(I)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void aurora$preciseLimit(int fps, CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        boolean isAuroraGui = (mc != null && mc.screen != null && mc.screen.getClass().getName().startsWith("com.aurora.client.screen"));
        if (isAuroraGui) {
            fps = cfg.guiFpsLimit;
        }

        if (fps <= 0) return;

        if (!isAuroraGui) {
            if (!cfg.smoothFramePacer) return;
            if (cfg.framePacingStrategy == AuroraConfig.PacingStrategy.VANILLA) return;
            if (cfg.lowLatencyRender && cfg.adaptiveRenderSleeping) {
                ci.cancel(); // Let the start of runTick handle adaptive JIT sleeping instead
                return;
            }
        }

        ci.cancel();

        double now = GLFW.glfwGetTime();
        double frameTime = 1.0 / fps;

        // Reset anchor on first call, after FPS cap change, or after a
        // significant hitch (we're more than half a frame past target).
        if (aurora$nextFrameTarget == 0.0
                || aurora$lastFps != fps
                || now > aurora$nextFrameTarget + frameTime * 0.5) {
            aurora$nextFrameTarget = now + frameTime;
            aurora$lastFps = fps;
            return;
        }

        if (isAuroraGui && (!cfg.smoothFramePacer || cfg.framePacingStrategy == AuroraConfig.PacingStrategy.VANILLA)) {
            FramePacer.waitUntilHybrid(aurora$nextFrameTarget, 2000, 500);
        } else {
            FramePacer.waitUntil(aurora$nextFrameTarget, cfg);
        }

        // Advance anchor by one full frame from the prior target. This
        // keeps cadence locked even if the wait overshot by a few µs.
        aurora$nextFrameTarget += frameTime;
    }
}