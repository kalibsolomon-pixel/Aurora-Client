package com.aurora.client.mixin;

import java.util.function.Supplier;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * GUI Glint Baking (ADR 0009): the glint's texture transform, computed the
 * way its draw computes it. The getter is {@code getMatrix()} on 26.1 and
 * {@code createMatrix()} later; the supplier behind it is the same.
 */
@Mixin(TextureTransform.class)
public interface TextureTransformAccessor {
    @Accessor("supplier")
    Supplier<Matrix4f> aurora$supplier();
}
