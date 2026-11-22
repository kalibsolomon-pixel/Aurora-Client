package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Allows the local player's own nametag to render in third-person camera views
 * when {@link AuroraConfig#nametagStyleShowSelf} is enabled, while preserving
 * all vanilla visibility rules (F1 HUD hide, invisibility, riding, distance).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererNameTagMixin {

    @ModifyExpressionValue(
            method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getCameraEntity()Lnet/minecraft/world/entity/Entity;")
    )
    private Entity aurora$allowSelfNameTagInThirdPerson(Entity cameraEntity, LivingEntity entity) {
        if (!AuroraConfig.get().nametagStyleShowSelf) return cameraEntity;
        if (entity == null || entity != cameraEntity) return cameraEntity;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || entity != mc.player) return cameraEntity;
        if (mc.options.getCameraType().isFirstPerson()) return cameraEntity;
        return null;
    }
}
