package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.GuiAreaList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;

/**
 * GUI Layering (ADR 0009): a layer's element, item, text and
 * picture-in-picture lists are {@link GuiAreaList}s, which index their
 * elements' bounds for {@code GuiRenderStateLayeringMixin}. Glyph lists are
 * never searched and stay plain.
 */
@Mixin(targets = "net.minecraft.client.renderer.state.gui.GuiRenderState$Node")
public abstract class GuiRenderStateNodeMixin {
    @WrapOperation(method = {"addGuiElement", "addItem", "addText", "addPicturesInPictureState"},
            at = @At(value = "NEW", target = "()Ljava/util/ArrayList;"))
    private ArrayList<?> aurora$indexedLayer(Operation<ArrayList<?>> original) {
        return AuroraConfig.get().perfGuiLayering ? new GuiAreaList<>() : original.call();
    }
}
