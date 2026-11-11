package com.aurora.client.mixin.hitreg;

//version 1.21.2+
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

//version 1.21.9+
import net.minecraft.client.renderer.SubmitNodeCollector;

//version 1.21.8-
//import net.minecraft.client.renderer.MultiBufferSource;

import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.LivingEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.aurora.client.hitreg.settings.Toggle;

/**
 * From BetterHitreg by Jass (modrinth.com/mod/betterhitreg), integrated into
 * Aurora with the author's permission. Logic is upstream's, moved verbatim —
 * see {@link com.aurora.client.hitreg.BetterHitreg} for the integration notes.
 */
@Mixin(HumanoidArmorLayer.class)
public abstract class ArmorMixin {
    //version 1.21.1-
//    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
//            at = @At("HEAD"), cancellable = true)
//    private void render(PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i, LivingEntity livingEntity, float f, float g, float h, float j, float k, float l, CallbackInfo ci) {
//        if (Toggle.HIDE_ARMOR.toggled()) ci.cancel();
//    }

    //version 1.21.2 - 1.21.8
//    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
//            at = @At("HEAD"), cancellable = true)
//    private void render(PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i, HumanoidRenderState bipedEntityRenderState, float f, float g, CallbackInfo ci) {
//        if (Toggle.HIDE_ARMOR.toggled()) ci.cancel();
//    }

    //version 1.21.9+
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    private void render(PoseStack matrixStack, SubmitNodeCollector orderedRenderCommandQueue, int i, HumanoidRenderState bipedEntityRenderState, float f, float g, CallbackInfo ci) {
        if (Toggle.HIDE_ARMOR.toggled()) ci.cancel();
    }
}