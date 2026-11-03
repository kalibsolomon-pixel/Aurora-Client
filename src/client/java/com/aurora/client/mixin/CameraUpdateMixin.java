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
 *
 * <p>Vanilla 1.21.11 {@code Camera.setup} reaches its entity-rotation
 * {@code setRotation} through two mutually exclusive arms — the
 * minecart-lerp branch (ordinal 0) and the common path (ordinal 1). The
 * handler is injected after BOTH (verified against the mapped jar during the
 * 2026-11-03 responsiveness audit); before that it hung on ordinal 1 only,
 * so while riding a minecart with experimental lerp behavior neither
 * FreeLook nor Smooth Camera applied at all.
 */
@Mixin(Camera.class)
public abstract class CameraUpdateMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(
            method = "setup",
            at = {
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/Camera;setRotation(FF)V",
                            ordinal = 0,
                            shift = At.Shift.AFTER
                    ),
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/Camera;setRotation(FF)V",
                            ordinal = 1,
                            shift = At.Shift.AFTER
                    )
            },
            require = 2
    )
    private void aurora$applyRotation(
            Level level, Entity focusedEntity, boolean thirdPerson,
            boolean inverseView, float tickDelta, CallbackInfo ci) {

        if (!(focusedEntity instanceof LocalPlayer)) return;

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