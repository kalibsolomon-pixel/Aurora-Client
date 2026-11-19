package com.aurora.client.mixin;

import com.aurora.client.adapter.PoseStackSink;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.HandPoses;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Animations, first-person poses (26.2): Hand Sway, Compact Eat/Drink,
 * Classic Block Pose and Block-Hit Swing. Display only: the item, its use
 * timing and every gameplay decision stay vanilla's.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class HandMotionMixin {

    @Shadow
    private void swingArm(float animation, PoseStack poseStack, int invert, HumanoidArm arm) {}

    /** The two view-lag rotations that open {@code submitHandsWithItems}. */
    @ModifyArg(method = "submitHandsWithItems", at = @At(value = "INVOKE",
            target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;"), index = 0)
    private float aurora$handSway(float degrees) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.smoothAnimationsEnabled) return degrees;
        return degrees * (float) Math.max(0.0, Math.min(1.0, cfg.animHandSway));
    }

    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyEatTransform(Lcom/mojang/blaze3d/vertex/PoseStack;FLnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)V"))
    private void aurora$eatTransform(ItemInHandRenderer self, PoseStack poseStack, float frameInterp,
                                     HumanoidArm arm, ItemStack itemStack, Player player, Operation<Void> original) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.smoothAnimationsEnabled || cfg.animEatStyle != AuroraConfig.EatStyle.COMPACT) {
            original.call(self, poseStack, frameInterp, arm, itemStack, player);
            return;
        }
        HandPoses.compactEat(PoseStackSink.on(poseStack), player.getUseItemRemainingTicks() - frameInterp + 1.0F,
                itemStack.getUseDuration(player), arm == HumanoidArm.RIGHT ? 1 : -1);
    }

    /**
     * The blocking branch skips its pose for shields. Here a non-shield item can
     * first keep a running swing (Block-Hit Swing) and then take the classic
     * pose in place of vanilla's (reported as a shield so vanilla's is skipped).
     */
    @ModifyExpressionValue(method = "submitArmWithItem", at = @At(value = "CONSTANT",
            args = "classValue=net/minecraft/world/item/ShieldItem"))
    private boolean aurora$blockPose(boolean isShield, @Local(argsOnly = true) PoseStack poseStack,
                                     @Local(argsOnly = true, ordinal = 2) float attack, @Local HumanoidArm arm) {
        if (isShield) return true;
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.smoothAnimationsEnabled) return false;
        int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
        if (cfg.animBlockHitSwing && attack > 0.0F) swingArm(attack, poseStack, invert, arm);
        if (cfg.animBlockStyle != AuroraConfig.BlockStyle.CLASSIC) return false;
        HandPoses.classicBlock(PoseStackSink.on(poseStack), invert);
        return true;
    }
}
