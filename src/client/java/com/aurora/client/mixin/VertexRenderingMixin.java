package com.aurora.client.mixin;

import net.minecraft.client.renderer.feature.ShapeOutlineFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Hides vanilla's targeted-block wireframe while Aurora draws its own outline/fill.
 * 26.2 builds those edges in ShapeOutlineFeatureRenderer before GPU submission. */
@Mixin(ShapeOutlineFeatureRenderer.class)
public abstract class VertexRenderingMixin {

    /** The edge lambda supplies the color to each emitted vertex. */
    @ModifyArg(
            method = "lambda$buildGroup$0(Lorg/joml/Vector3f;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lcom/mojang/blaze3d/vertex/PoseStack$Pose;IFDDDDDD)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(I)Lcom/mojang/blaze3d/vertex/VertexConsumer;")
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