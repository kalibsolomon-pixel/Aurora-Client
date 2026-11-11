package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.FramePacing;
import com.aurora.client.util.FramePacer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the MouseHandler's accumulated cursor delta at the very top of
 * each frame render — before the game-tick block — instead of vanilla's
 * own per-frame application point in MouseHandler.handleAccumulatedMovement
 * (runTick's render section, just before GameRenderer.render). On frames
 * that run a game tick, tick logic sees this frame's aim rather than the
 * previous one's. The batch is applied raw, bypassing vanilla's
 * cinematic-camera smoothing curve.
 *
 * <p>Network rate is unchanged: LocalPlayer.sendMovementPackets
 * runs at tick rate and only sends the latest yaw/pitch.
 */
@Mixin(value = Minecraft.class, priority = 10000)
public abstract class MinecraftClientRenderMixin {
    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "renderFrame",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuSurface$PresentMode;getSupportedVsyncMode(Ljava/util/Collection;Z)Lcom/mojang/blaze3d/systems/GpuSurface$PresentMode;"), index = 1)
    private boolean aurora$surfaceVsync(boolean vanilla) {
        AuroraConfig cfg = AuroraConfig.get();
        return cfg.lowLatencyRender && cfg.disableVSync ? false : vanilla;
    }


    /** Loading and disconnect screens also render outside runTick. */
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void aurora$beginGlassFrame(boolean advanceGameTime, CallbackInfo ci) {
        // The staged overlay pools recycle at the frame boundary, not per overlay draw.
        com.aurora.client.diagnostics.Timed.glass(com.aurora.client.hud.WorldGeometryBuffers::frameEnd);
        com.aurora.client.ui.render.blur.BlurPanelRenderer.beginFrame();
        com.aurora.client.ui.component.GlassSurface.beginFrame();
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void aurora$applyMouseDeltaPerFrame(boolean tick, CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;
        AuroraConfig cfg = AuroraConfig.get();
        // Diagnostics benchmark: one volatile read unless a run is in progress.
        com.aurora.client.diagnostics.FrameBenchmark.onFrame();

        // Use the effective native target, including menu/AFK/iconified
        // restrictions. A positive global cap must also work when Minecraft
        // skips limitDisplayFPS at Unlimited. One shared deadline owns both
        // hooks; post-swap rechecks restrictions after event pumping.
        boolean auroraGui = com.aurora.client.adapter.NativeUi.screen(self) != null
                && com.aurora.client.adapter.NativeUi.screen(self).getClass().getName().startsWith("com.aurora.client.screen");
        boolean customPacer = cfg.smoothFramePacer
                && cfg.framePacingStrategy != AuroraConfig.PacingStrategy.VANILLA;
        var limit = FramePacing.resolve(self.getFramerateLimitTracker().getFramerateLimit(),
                cfg.frameCapFps, cfg.guiFpsLimit, auroraGui, customPacer,
                cfg.lowLatencyRender && cfg.adaptiveRenderSleeping);
        double pacingNow = org.lwjgl.glfw.GLFW.glfwGetTime();
        if (com.aurora.client.util.ParkDiagnostics.INSTANCE.isEnabled()) {
            com.aurora.client.util.ParkDiagnosticsControl.observe(limit, cfg, customPacer,
                    self.isWindowActive(), pacingNow);
        }
        double target = FramePacing.INSTANCE.beginFrame(limit, pacingNow);
        if (target != 0.0) {
            if (customPacer) FramePacer.waitUntil(target, cfg);
            else FramePacer.waitUntilHybrid(target, 2000);
        }

        if (!cfg.inputSamplingDecoupled) return;

        if (self.mouseHandler == null || self.player == null) return;
        if (!self.mouseHandler.isMouseGrabbed()) return;

        // MouseAccessor is a separate mixin; if it could not be applied the
        // handler does not carry the interface (ADR 0004).
        if (!(self.mouseHandler instanceof MouseAccessor mouseAccess)) return;
        double dx = mouseAccess.aurora$getCursorDeltaX();
        double dy = mouseAccess.aurora$getCursorDeltaY();
        if (dx == 0.0 && dy == 0.0) return;

        // Apply the same sensitivity scaling vanilla does in updateMouse.
        double sens = self.options.sensitivity().get() * 0.6 + 0.2;
        double scale = sens * sens * sens * 8.0;
        boolean invert = self.options.invertMouseY().get();

        self.player.turn(dx * scale, dy * scale * (invert ? -1 : 1));

        // Drain consumed delta so vanilla's updateMouse won't double-apply it.
        mouseAccess.aurora$setCursorDeltaX(0.0);
        mouseAccess.aurora$setCursorDeltaY(0.0);
    }
}
