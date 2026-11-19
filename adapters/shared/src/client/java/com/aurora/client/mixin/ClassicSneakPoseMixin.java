package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Classic Sneak Pose, for your own avatar only.
 *
 * <p>The crouching model bends the same way it did in 1.8 (body leaned, arms
 * and head lowered relative to the legs). Modern clients then drop the whole
 * model a further 2 px to match the 1.5-block crouch hitbox; 1.8's model sat
 * that much higher, feet still on the ground. Removing that drop gives the
 * 1.8 silhouette. Only the drawn model moves: the camera, hitbox, name tag
 * and collision are vanilla's.
 */
@Mixin(AvatarRenderer.class)
public abstract class ClassicSneakPoseMixin {

    @Inject(method = "getRenderOffset(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), cancellable = true)
    private void aurora$classicSneak(AvatarRenderState state, CallbackInfoReturnable<Vec3> cir) {
        if (!state.isCrouching) return;
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.smoothAnimationsEnabled || !cfg.animClassicSneak) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || state.id != mc.player.getId()) return;
        cir.setReturnValue(cir.getReturnValue().add(0.0, state.scale * 2.0F / 16.0F, 0.0));
    }
}
