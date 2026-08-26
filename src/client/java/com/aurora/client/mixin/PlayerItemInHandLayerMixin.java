package com.aurora.client.mixin;

import com.aurora.client.feature.impl.ShieldStatusFeature;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sets the shield-tint holder context for third-person and other-player
 * shield renders. Vanilla {@code submitArmWithItem(S, ItemStackRenderState,
 * ItemStack, HumanoidArm, PoseStack, SubmitNodeCollector, int)} runs once
 * per hand per frame for every visible humanoid; we wrap it so any
 * {@link net.minecraft.client.renderer.special.ShieldSpecialRenderer}
 * submission inside it sees the right entity id.
 *
 * <p>Targeting {@link PlayerItemInHandLayer} (not the generic
 * {@link net.minecraft.client.renderer.entity.layers.ItemInHandLayer})
 * because only player render-states reliably expose
 * {@link AvatarRenderState#id}. Non-player humanoids (zombies, illagers)
 * holding shields will fall through to the AVAILABLE color path, which
 * is fine — we have no cooldown info on them anyway.
 *
 * <p>The first-person path is handled separately by
 * {@code HeldItemRendererTweaksMixin}; both push/pop independently and
 * never nest because vanilla draws first-person OR third-person, never
 * both for the same camera frame.
 */
@Mixin(PlayerItemInHandLayer.class)
public abstract class PlayerItemInHandLayerMixin {

    @Inject(method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("HEAD"))
    private void aurora$pushShieldHolder(AvatarRenderState state,
                                         ItemStackRenderState itemState,
                                         ItemStack stack, HumanoidArm arm,
                                         PoseStack matrices,
                                         SubmitNodeCollector vertices,
                                         int light, CallbackInfo ci) {
        if (state != null) ShieldStatusFeature.pushHolder(state.id);
    }

    @Inject(method = "submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("RETURN"))
    private void aurora$popShieldHolder(AvatarRenderState state,
                                        ItemStackRenderState itemState,
                                        ItemStack stack, HumanoidArm arm,
                                        PoseStack matrices,
                                        SubmitNodeCollector vertices,
                                        int light, CallbackInfo ci) {
        ShieldStatusFeature.popHolder();
    }
}
