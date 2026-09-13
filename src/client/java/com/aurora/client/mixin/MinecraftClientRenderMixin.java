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

        // Frame pacing at the top of the frame (before input application and
        // the render queue — the Reflex-style position: the CPU sleeps, then
        // samples input, then builds the frame). Two pacing sources own this
        // block and share one anchor chain (they are mutually exclusive per
        // frame, and the anchor re-anchors whenever the effective fps
        // changes, so handovers between them are clean):
        //
        //  1. Adaptive (Reflex-style) render sleep — paced at the vanilla
        //     framerate option, gated on the Low Latency master + Frame
        //     Pacer prerequisites, and skipped inside Aurora's own screens
        //     where the GUI limiter (RenderSystemMixin, guiFpsLimit) has
        //     historically owned pacing.
        //  2. The global frame cap (frameCapFps > 0) — paced at the cap on
        //     every frame, Aurora screens included. It must live here rather
        //     than in limitDisplayFPS because the 1.21.11 game loop only
        //     calls RenderSystem.limitDisplayFPS while the vanilla option is
        //     below its Unlimited sentinel (260): with vanilla "Unlimited"
        //     nothing else in the chain ever runs, so this is the only point
        //     that can enforce a cap in that (very common) configuration.
        //
        // Composition guarantee (the "no two limiters fighting" invariant):
        // every pacer in the frame — vanilla's own, the Frame Pacer, the GUI
        // limiter, this cap — is an independent anchored "wait until my
        // target". A pacer whose cadence is looser than the actual frame
        // cadence hits its re-anchor branch (late by > half a frame) and
        // waits nothing; the tightest active constraint therefore always
        // determines the rate: min(vanilla option, vanilla's iconified/AFK/
        // menu throttles, guiFpsLimit on Aurora screens, this cap).
        boolean auroraGui = self.screen != null
                && self.screen.getClass().getName().startsWith("com.aurora.client.screen");
        boolean adaptivePacing = !auroraGui && cfg.lowLatencyRender && cfg.smoothFramePacer
                && cfg.adaptiveRenderSleeping && cfg.framePacingStrategy != AuroraConfig.PacingStrategy.VANILLA;
        int capFps = Math.max(0, cfg.frameCapFps);
        if (adaptivePacing || capFps > 0) {
            // The adaptive sleep is paced by the vanilla option (260 is its
            // Unlimited sentinel — pacing at it is the historical no-op the
            // adaptive path has always performed); the cap tightens whichever
            // rate is active.
            int fps = adaptivePacing ? self.options.framerateLimit().get() : Integer.MAX_VALUE;
            if (capFps > 0) fps = Math.min(fps, capFps);
            if (fps > 0 && fps < Integer.MAX_VALUE) {
                double now = org.lwjgl.glfw.GLFW.glfwGetTime();
                double frameTime = 1.0 / fps;
                if (aurora$nextAdaptiveFrameTarget == 0.0
                        || aurora$lastAdaptiveFps != fps
                        || now > aurora$nextAdaptiveFrameTarget + frameTime * 0.5) {
                    aurora$nextAdaptiveFrameTarget = now + frameTime;
                    aurora$lastAdaptiveFps = fps;
                } else {
                    if (adaptivePacing && !aurora$loggedAdaptiveSleep) {
                        com.aurora.client.AuroraClient.LOGGER.info("[Aurora-Reflex] Adaptive JIT Render-Queue sleeping initialized and active.");
                        aurora$loggedAdaptiveSleep = true;
                    }
                    if (adaptivePacing) {
                        com.aurora.client.util.FramePacer.waitUntil(aurora$nextAdaptiveFrameTarget, cfg);
                    } else if (cfg.smoothFramePacer && cfg.framePacingStrategy != AuroraConfig.PacingStrategy.VANILLA) {
                        com.aurora.client.util.FramePacer.waitUntil(aurora$nextAdaptiveFrameTarget, cfg);
                    } else {
                        // Cap-only with the Frame Pacer off or on the VANILLA
                        // strategy: the configured strategies have no vanilla
                        // equivalent for a custom cap, so pace with the fixed
                        // hybrid profile — the same fallback the Aurora-GUI
                        // limiter uses in RenderSystemMixin.
                        com.aurora.client.util.FramePacer.waitUntilHybrid(aurora$nextAdaptiveFrameTarget, 2000, 500);
                    }
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