package com.aurora.client.mixin;

import com.aurora.client.util.ItemPhysicsPose;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Implements the Item Physics feature by tweaking two PoseStack calls
 * inside {@code ItemEntityRenderer.submit}: the bob translate and the spin.
 * The pose itself is {@link ItemPhysicsPose}. 26.3 spins with
 * {@code rotate(Axis.YP, spin)} rather than a quaternion, so the spin call
 * is wrapped and replaced with {@code rotate(Quaternionfc)} when Item
 * Physics changes it.
 *
 * <p>Both targets are unique in {@code submit}, so we rely on the
 * INVOKE descriptor without an ordinal.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererSubmitMixin {
    private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

    @ModifyArg(method = SUBMIT,
               at = @At(value = "INVOKE",
                        target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"),
               index = 1,
               require = 0)
    private float aurora$modifyTranslateY(float origY,
                                          @Local(argsOnly = true) ItemEntityRenderState state) {
        return ItemPhysicsPose.translateY(origY, state);
    }

    @WrapOperation(method = SUBMIT,
                   at = @At(value = "INVOKE",
                            target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotate(Lcom/mojang/math/Axis;F)V"),
                   require = 0)
    private void aurora$modifySpin(PoseStack poses, Axis axis, float angle, Operation<Void> original,
                                   @Local(argsOnly = true) ItemEntityRenderState state) {
        if (!ItemPhysicsPose.altersSpin(state)) {
            original.call(poses, axis, angle);
            return;
        }
        poses.rotate(ItemPhysicsPose.spin(axis.rotation(angle), state));
    }
}
