package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.GuiFlatFaces;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * GUI Flat Faces (ADR 0009): {@code ItemFeatureRendererFlatFacesMixin} leaves
 * out quads with no area only while a slot of a GUI item atlas is drawn.
 * {@code drawToSlot} draws with an orthographic projection; the model view is
 * checked here. ImmediatelyFast's animated atlas is a subclass and draws
 * through this method too. World, hand and item-frame drawing never run
 * inside it.
 */
@Mixin(GuiItemAtlas.class)
public abstract class GuiItemAtlasFlatFacesMixin {
    @WrapMethod(method = "drawToSlot")
    private void aurora$scopeFlatFaces(int slotX, int slotY, boolean clear, ItemStackRenderState item, Operation<Void> original) {
        GuiFlatFaces.beginAtlasDraw(AuroraConfig.get().perfGuiFlatFaces
                && GuiFlatFaces.keepsDepthOut(RenderSystem.getModelViewStack()));
        try {
            original.call(slotX, slotY, clear, item);
        } finally {
            GuiFlatFaces.endAtlasDraw();
        }
    }
}
