package com.aurora.client.mixin;

import com.aurora.client.util.GuiItemGlint;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.render.pip.OversizedItemRenderer;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * GUI Glint Baking (ADR 0009): an oversized GUI item reuses its texture only
 * when it is not animated. A glint item {@code CuboidItemModelGlintMixin}
 * left unanimated still counts as animated here, so it is redrawn every
 * frame as in vanilla.
 */
@Mixin(OversizedItemRenderer.class)
public abstract class OversizedItemGlintMixin {
    @WrapOperation(method = "textureIsReadyToBlit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/TrackingItemStackRenderState;isAnimated()Z"))
    private boolean aurora$glintStaysLive(TrackingItemStackRenderState item, Operation<Boolean> original) {
        return original.call(item) || item instanceof GuiItemGlint glint && glint.aurora$glint();
    }
}
