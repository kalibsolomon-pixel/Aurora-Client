package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Free Sprite Mipmaps (ADR 0009): every sprite keeps CPU copies of its
 * generated mip levels after the atlas has uploaded them, though only the
 * upload reads them: the first frame is written to the atlas, and an
 * animated sprite copies all its frames into GPU textures
 * ({@code createAnimationState}), both inside {@code upload}. Afterwards the
 * copies above level 0 are freed; level 0, the original image, stays for the
 * transparency checks that read it. Each atlas loads its own sprite contents
 * on every reload, so nothing freed here is uploaded again.
 */
@Mixin(TextureAtlas.class)
public abstract class TextureAtlasFreeMipsMixin {
    @Shadow private List<TextureAtlasSprite> sprites;

    @Inject(method = "upload", at = @At("RETURN"))
    private void aurora$freeUploadedMips(SpriteLoader.Preparations preparations, CallbackInfo ci) {
        if (!AuroraConfig.get().perfFreeSpriteMipmaps) return;
        for (TextureAtlasSprite sprite : sprites) {
            SpriteContents contents = sprite.contents();
            if (!(contents instanceof SpriteContentsMipsAccessor mips)) continue;
            NativeImage[] levels = mips.aurora$byMipLevel();
            if (levels.length <= 1) continue;
            for (int level = 1; level < levels.length; level++) levels[level].close();
            mips.aurora$setByMipLevel(new NativeImage[]{levels[0]});
        }
    }
}
