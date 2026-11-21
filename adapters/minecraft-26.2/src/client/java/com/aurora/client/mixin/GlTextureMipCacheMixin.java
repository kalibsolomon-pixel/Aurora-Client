package com.aurora.client.mixin;

import com.aurora.client.render.GlTextureMipCache;
import com.mojang.blaze3d.opengl.GlTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Caches the last GL_TEXTURE_BASE_LEVEL and GL_TEXTURE_MAX_LEVEL sent for this texture (26.2). */
@Mixin(GlTexture.class)
public abstract class GlTextureMipCacheMixin implements GlTextureMipCache {
    @Unique private int aurora$baseMip = -1;
    @Unique private int aurora$maxMip = -1;

    @Override
    public boolean aurora$updateBaseMip(int baseMip) {
        if (this.aurora$baseMip == baseMip) return false;
        this.aurora$baseMip = baseMip;
        return true;
    }

    @Override
    public boolean aurora$updateMaxMip(int maxMip) {
        if (this.aurora$maxMip == maxMip) return false;
        this.aurora$maxMip = maxMip;
        return true;
    }
}
