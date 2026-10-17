package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.FramePacer;
import com.aurora.client.util.FramePacing;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces a native post-swap wait only when Aurora enforces its effective restriction. */
@Mixin(value = RenderSystem.class, remap = false, priority = 10000)
public abstract class RenderSystemMixin {

    @Inject(method = "limitDisplayFPS(I)V", at = @At("HEAD"), cancellable = true, require = 1)
    private static void aurora$preciseLimit(int fps, CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        boolean auroraGui = mc != null && mc.screen != null
                && mc.screen.getClass().getName().startsWith("com.aurora.client.screen");
        boolean customPacer = cfg.smoothFramePacer
                && cfg.framePacingStrategy != AuroraConfig.PacingStrategy.VANILLA;
        var limit = FramePacing.resolve(fps, cfg.frameCapFps, cfg.guiFpsLimit,
                auroraGui, customPacer, cfg.lowLatencyRender && cfg.adaptiveRenderSleeping);
        var wait = FramePacing.INSTANCE.postSwap(limit, GLFW.glfwGetTime());
        if (!wait.cancelNative()) return;
        if (wait.waitTarget() != 0.0) {
            if (customPacer) FramePacer.waitUntil(wait.waitTarget(), cfg);
            else FramePacer.waitUntilHybrid(wait.waitTarget(), 2000);
        }
        ci.cancel();
    }
}
