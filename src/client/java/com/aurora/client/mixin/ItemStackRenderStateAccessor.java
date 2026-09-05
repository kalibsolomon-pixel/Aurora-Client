package com.aurora.client.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the resolved layer array on {@link ItemStackRenderState} so
 * {@code ItemSpriteRenderer} can read the baked GUI quads a
 * {@code ItemModelResolver} pass produced (for the crisp flat-sprite item
 * icons on the Item Scale screen) without submitting the state through the
 * 3D item pipeline.
 */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {

    @Accessor("layers")
    ItemStackRenderState.LayerRenderState[] aurora$layers();

    @Accessor("activeLayerCount")
    int aurora$activeLayerCount();
}
