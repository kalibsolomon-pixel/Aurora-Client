package com.aurora.client.mixin;

import com.aurora.client.util.GuiAreaList;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.ScreenArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * GUI Layering (ADR 0009): vanilla's search for the layer a new GUI element
 * goes on asks each layer whether any of its elements intersects the new
 * one. An indexed layer ({@link GuiAreaList}) answers from its bounds array;
 * any other list takes vanilla's scan. Harmless alone: without
 * {@code GuiRenderStateNodeMixin} no list is indexed.
 */
@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateLayeringMixin {
    @Inject(method = "hasIntersection", at = @At("HEAD"), cancellable = true)
    private void aurora$indexedIntersection(ScreenRectangle bounds, List<? extends ScreenArea> states,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (states instanceof GuiAreaList<?> layer && layer.indexed()) cir.setReturnValue(layer.intersects(bounds));
    }
}
