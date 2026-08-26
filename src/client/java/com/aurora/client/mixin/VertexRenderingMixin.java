package com.aurora.client.mixin;

import com.aurora.client.feature.impl.BlockOverlayFeature;
import net.minecraft.client.renderer.ShapeRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Recolors the targeted-block wireframe outline. When the outline sub-toggle
 * is off, returns fully-transparent black so the wireframe is effectively
 * hidden — the face fill (if enabled) still renders separately via
 * BlockOverlayRenderer.
 *
 * <p>1.21.11: {@code BlockOutlineRenderState#drawOutline} was removed;
 * the actual draw moved to {@link ShapeRenderer#renderShape(
 * com.mojang.blaze3d.vertex.PoseStack,
 * com.mojang.blaze3d.vertex.VertexConsumer,
 * net.minecraft.world.phys.shapes.VoxelShape,
 * double, double, double, int, float)}. We retarget there and override
 * the {@code int color} argument.
 *
 * <p>Caveat: {@code ShapeRenderer.renderShape} is used by multiple debug
 * draws (block outline, lookat/raycast debug, etc.). The recolor will
 * apply to anything that funnels through this method while the feature is
 * enabled. In practice the targeted-block outline is the only ShapeRenderer
 * caller in a normal play session, so the impact is intentional.
 */
@Mixin(ShapeRenderer.class)
public abstract class VertexRenderingMixin {

    /**
     * {@code renderShape} doesn't call the per-edge helper directly — it
     * builds an {@code invokedynamic} lambda that {@code VoxelShape.forAllEdges}
     * later invokes for every edge. The actual {@code int} color reaches the
     * vertex buffer via {@code VertexConsumer.setColor(int)} calls inside
     * the lambda body ({@code method_62299}). We inject there and override
     * every {@code setColor} argument.
     */
    @ModifyArg(
            method = "method_62299(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;DDDIFDDDDDD)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
            require = 1
    )
    private static int aurora$recolorOutlineSetColor(int originalColor) {
        return aurora$mapColor(originalColor);
    }

    private static int aurora$mapColor(int original) {
        // Aurora's BlockOverlayRenderer now draws its own outline via the
        // GPU-side line expansion shader (RenderTypes.lines() with
        // per-vertex setLineWidth) whenever the feature is enabled, so
        // vanilla's 1-px wireframe must be hidden in BOTH cases:
        //   - outline sub-toggle ON  → our thick line covers vanilla;
        //                              hide vanilla to avoid a 1-px ghost.
        //   - outline sub-toggle OFF → hide vanilla outright (face fill
        //                              may still draw separately).
        // When the master block-overlay toggle is OFF, return original so
        // vanilla outlines render normally.
        if (!com.aurora.client.config.AuroraConfig.get().blockOverlayEnabled) {
            return original;
        }
        return 0x00000000;
    }
}