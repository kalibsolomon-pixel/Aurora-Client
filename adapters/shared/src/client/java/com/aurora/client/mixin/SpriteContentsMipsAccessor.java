package com.aurora.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A sprite's CPU copies of its mip levels, level 0 being the original image ({@code TextureAtlasFreeMipsMixin}). */
@Mixin(SpriteContents.class)
public interface SpriteContentsMipsAccessor {
    @Accessor("byMipLevel")
    NativeImage[] aurora$byMipLevel();

    @Accessor("byMipLevel")
    void aurora$setByMipLevel(NativeImage[] images);
}
