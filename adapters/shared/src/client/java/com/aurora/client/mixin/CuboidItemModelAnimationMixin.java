package com.aurora.client.mixin;

import com.aurora.client.util.GuiItemAnimation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * GUI Item Baking (ADR 0009): the second {@code setAnimated()} call in
 * {@code update} is the animated-sprite one (the first is the enchantment
 * glint), so only it is marked as changing with the sprites.
 */
@Mixin(CuboidItemModelWrapper.class)
public abstract class CuboidItemModelAnimationMixin {
    @WrapOperation(method = "update", at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;setAnimated()V"))
    private void aurora$spriteAnimated(ItemStackRenderState output, Operation<Void> original) {
        if (!(output instanceof GuiItemAnimation animation)) {
            original.call(output);
            return;
        }
        animation.aurora$spriteAnimation(true);
        try {
            original.call(output);
        } finally {
            animation.aurora$spriteAnimation(false);
        }
    }
}
