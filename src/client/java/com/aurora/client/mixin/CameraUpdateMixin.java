/*
 * Camera-rotation logic adapted from Freelook++ (BloodredX/FreelookPlusPlus, MIT).
 * https://github.com/BloodredX/FreelookPlusPlus/blob/main/LICENSE
 */
package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.CameraSmoothFeature;
import com.aurora.client.feature.impl.FreeLookFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overrides camera rotation per frame in two cases:
 *  - FreeLook active: re-applies the stored detached yaw/pitch.
 *  - Smooth-camera enabled (and FreeLook off): re-applies an exponentially
 *    smoothed yaw/pitch instead of vanilla's snap-to-latest.
 */
@Mixin(Camera.class)
public abstract class CameraUpdateMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @org.spongepowered.asm.mixin.Unique private static boolean aurora$loggedZeroLatency = false;

    @Inject(
            method = "setup",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;setRotation(FF)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            ),
            require = 1
    )
    private void aurora$applyRotation(
            Level level, Entity focusedEntity, boolean thirdPerson,
            boolean inverseView, float tickDelta, CallbackInfo ci) {

        if (!(focusedEntity instanceof LocalPlayer)) return;

        if (AuroraConfig.get().zeroLatencyCamera) {
            Minecraft self = Minecraft.getInstance();
            if (self.mouseHandler != null && self.player != null && self.mouseHandler.isMouseGrabbed()) {
                com.aurora.client.mixin.MouseAccessor mouseAccess = (com.aurora.client.mixin.MouseAccessor) self.mouseHandler;
                double dx = mouseAccess.aurora$getCursorDeltaX();
                double dy = mouseAccess.aurora$getCursorDeltaY();
                if (dx != 0.0 || dy != 0.0) {
                    if (!aurora$loggedZeroLatency) {
                        com.aurora.client.AuroraClient.LOGGER.info("[Aurora-LateInput] Zero-Latency late-stage camera injection active.");
                        aurora$loggedZeroLatency = true;
                    }
                    double sens = self.options.sensitivity().get() * 0.6 + 0.2;
                    double scale = sens * sens * sens * 8.0;
                    boolean invert = self.options.invertMouseY().get();
                    self.player.turn((float) (dx * scale), (float) (dy * scale * (invert ? -1 : 1)));
                    this.setRotation(self.player.getYRot(), self.player.getXRot());
                    mouseAccess.aurora$setCursorDeltaX(0.0);
                    mouseAccess.aurora$setCursorDeltaY(0.0);
                }
            }
        }

        // Priority: FreeLook wins over smooth camera.
        if (AuroraConfig.get().freeLookEnabled && FreeLookFeature.isActive()) {
            if (FreeLookFeature.isFirstTime() && Minecraft.getInstance().player != null) {
                LocalPlayer p = Minecraft.getInstance().player;
                FreeLookFeature.seedFromPlayer(p.getYRot(), p.getXRot());
                FreeLookFeature.markFirstTime(false);
            }
            this.setRotation(FreeLookFeature.getCameraYaw(), FreeLookFeature.getCameraPitch());
            return;
        } else {
            FreeLookFeature.markFirstTime(true);
        }

        // Smooth camera path.
        if (CameraSmoothFeature.apply((Camera) (Object) this, tickDelta)) {
            this.setRotation(
                    CameraSmoothFeature.getSmoothedYaw(),
                    CameraSmoothFeature.getSmoothedPitch()
            );
        }
    }
}