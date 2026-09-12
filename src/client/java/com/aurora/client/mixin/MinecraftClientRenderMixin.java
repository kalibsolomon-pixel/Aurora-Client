package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
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

    @org.spongepowered.asm.mixin.Unique private static double aurora$nextAdaptiveFrameTarget = 0.0;
    @org.spongepowered.asm.mixin.Unique private static int aurora$lastAdaptiveFps = 0;
    @org.spongepowered.asm.mixin.Unique private static boolean aurora$loggedAdaptiveSleep = false;

    @Inject(method = "runTick", at = @At("HEAD"), require = 1)
    private void aurora$applyMouseDeltaPerFrame(boolean tick, CallbackInfo ci) {
        Minecraft self = (Minecraft) (Object) this;
        AuroraConfig cfg = AuroraConfig.get();

        // Exact frame-boundary stamp for the glass panel output pool — must
        // run once per frame BEFORE any GUI rendering (see BlurPanelRenderer
        // .beginFrame / nextOutput for why an exact signal is required).
        com.aurora.client.ui.render.blur.BlurPanelRenderer.beginFrame();
        // Same boundary for the glass-surface phase stamp (glass pass → dim →
        // content): resets "dim painted" so every frame starts in the pass.
        com.aurora.client.ui.component.GlassSurface.beginFrame();

        // Adaptive (Reflex-style) render sleep. Gated on the Low Latency
        // master, and skipped inside Aurora's own screens: there the GUI
        // limiter (RenderSystemMixin, guiFpsLimit) owns pacing, and a HEAD
        // sleep paced at the vanilla cap would outrun its anchor and
        // silently defeat the Interface FPS cap.
        boolean auroraGui = self.screen != null
                && self.screen.getClass().getName().startsWith("com.aurora.client.screen");
        if (!auroraGui && cfg.lowLatencyRender && cfg.smoothFramePacer
                && cfg.adaptiveRenderSleeping && cfg.framePacingStrategy != AuroraConfig.PacingStrategy.VANILLA) {
            int fps = self.options.framerateLimit().get();
            if (fps > 0) {
                double now = org.lwjgl.glfw.GLFW.glfwGetTime();
                double frameTime = 1.0 / fps;
                if (aurora$nextAdaptiveFrameTarget == 0.0
                        || aurora$lastAdaptiveFps != fps
                        || now > aurora$nextAdaptiveFrameTarget + frameTime * 0.5) {
                    aurora$nextAdaptiveFrameTarget = now + frameTime;
                    aurora$lastAdaptiveFps = fps;
                } else {
                    if (!aurora$loggedAdaptiveSleep) {
                        com.aurora.client.AuroraClient.LOGGER.info("[Aurora-Reflex] Adaptive JIT Render-Queue sleeping initialized and active.");
                        aurora$loggedAdaptiveSleep = true;
                    }
                    com.aurora.client.util.FramePacer.waitUntil(aurora$nextAdaptiveFrameTarget, cfg);
                    aurora$nextAdaptiveFrameTarget += frameTime;
                }
            }
        }

        if (!cfg.inputSamplingDecoupled) return;

        if (self.mouseHandler == null || self.player == null) return;
        if (!self.mouseHandler.isMouseGrabbed()) return;

        MouseAccessor mouseAccess = (MouseAccessor) self.mouseHandler;
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