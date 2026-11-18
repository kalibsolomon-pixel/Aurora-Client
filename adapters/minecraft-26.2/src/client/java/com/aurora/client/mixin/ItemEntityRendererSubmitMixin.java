package com.aurora.client.mixin;

import com.aurora.client.util.ItemPhysicsPose;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Implements the Item Physics feature by tweaking two PoseStack calls
 * inside {@code ItemEntityRenderer.submit}: the bob translate and the
 * {@code mulPose(spinQuat)} spin. The pose itself is {@link ItemPhysicsPose};
 * 26.3 reaches its spin through {@code rotate(Axis, float)} instead.
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

    @ModifyArg(method = SUBMIT,
               at = @At(value = "INVOKE",
                        target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"),
               index = 0,
               require = 0)
    private Quaternionfc aurora$modifySpin(Quaternionfc origSpin,
                                           @Local(argsOnly = true) ItemEntityRenderState state) {
        return ItemPhysicsPose.spin(origSpin, state);
    }
}
