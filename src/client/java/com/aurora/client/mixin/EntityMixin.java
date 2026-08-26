/*
 * MouseHandler-input routing adapted from Freelook++ (BloodredX/FreelookPlusPlus, MIT).
 */
package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.FreeLookFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "turn(DD)V", at = @At("HEAD"), cancellable = true)
    private void aurora$divertMouseToFreeLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (!AuroraConfig.get().freeLookEnabled) return;
        if (!FreeLookFeature.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (((Object) this) != mc.player) return; // only divert local player input
        if (!(((Object) this) instanceof LocalPlayer)) return;

        FreeLookFeature.updateRotation(cursorDeltaX, cursorDeltaY);
        ci.cancel();
    }
}