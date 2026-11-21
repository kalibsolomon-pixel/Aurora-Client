package com.aurora.client.mixin;

import com.aurora.client.util.GuiFlatFaces;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * GUI Flat Faces (ADR 0009): inside a GUI item atlas drawing
 * ({@code GuiItemAtlasFlatFacesMixin}), a quad with no area on screen is not
 * written ({@link GuiFlatFaces}). 26.3 draws the glint in the item's own pass,
 * and the special foil of compass-like items through
 * {@code putBakedQuadWithGlint}. The vertex builder is still fetched for a
 * skipped quad, so the draws keep their order.
 */
@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureRendererFlatFacesMixin {
    @WrapOperation(method = "prepareMainSubmit", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBakedQuad(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"))
    private void aurora$skipEdgeOn(VertexConsumer buffer, PoseStack.Pose pose, BakedQuad quad, QuadInstance instance,
            Operation<Void> original) {
        if (!GuiFlatFaces.skip(pose, quad)) original.call(buffer, pose, quad, instance);
    }

    @WrapOperation(method = "prepareMainSubmit", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBakedQuadWithGlint(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)V"))
    private void aurora$skipEdgeOnSpecialFoil(VertexConsumer buffer, PoseStack.Pose pose, BakedQuad quad, QuadInstance instance,
            PoseStack.Pose decalPose, Operation<Void> original) {
        if (!GuiFlatFaces.skip(pose, quad)) original.call(buffer, pose, quad, instance, decalPose);
    }
}
