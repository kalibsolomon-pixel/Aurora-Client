package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.GuiItemGlint;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * GUI Glint Baking (ADR 0009): the first {@code setAnimated()} call in
 * {@code update} is the enchantment glint. A standard glint changes only with
 * its texture transform, so it marks the item instead of animating it: the
 * GUI atlas keeps a glint-only item's slot and {@code GuiItemAtlasGlintMixin}
 * redraws it when the transform moves. An item animated for any other reason
 * (sprites, another mod's {@code setAnimated()}) is still animated and is
 * redrawn every frame. Only the GUI atlas and oversized GUI items read
 * {@code isAnimated()}; {@code OversizedItemGlintMixin} keeps glint items
 * live there.
 */
@Mixin(CuboidItemModelWrapper.class)
public abstract class CuboidItemModelGlintMixin {
    @Shadow private static boolean hasSpecialAnimatedTexture(ItemStack itemStack) { throw new AssertionError(); }

    @WrapOperation(method = "update", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;setAnimated()V"))
    private void aurora$markGlint(ItemStackRenderState output, Operation<Void> original,
            @Local(argsOnly = true) ItemStack item) {
        if (AuroraConfig.get().perfGuiGlintBaking && output instanceof GuiItemGlint glint
                && !hasSpecialAnimatedTexture(item)) {
            glint.aurora$markGlint();
            return;
        }
        original.call(output);
    }
}
