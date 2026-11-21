package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.FrameBufferAttachment;
import com.mojang.blaze3d.opengl.FrameBufferCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Allocation-free 4-entry inline lookup cache for single-color-attachment FBOs
 * ({@code perfGlDispatch}, ADR 0009, 26.2). Matches on the exact
 * {@code (colorId, colorMip, depthId, depthMip)} integers used by
 * {@link FrameBufferCache.CacheKey} and invalidates on {@code destroyFbo}, so
 * {@code createRenderPass} avoids allocating a {@code CacheKey}, {@code int[]},
 * and {@code ArrayList} on every render pass.
 */
@Mixin(FrameBufferCache.class)
public abstract class FrameBufferCacheMixin {
    @Unique private final int[] aurora$cId = new int[4];
    @Unique private final int[] aurora$cMip = new int[4];
    @Unique private final int[] aurora$dId = new int[4];
    @Unique private final int[] aurora$dMip = new int[4];
    @Unique private final int[] aurora$fbo = new int[] { -1, -1, -1, -1 };
    @Unique private int aurora$nextSlot;

    @Inject(method = "getFbo", at = @At("HEAD"), cancellable = true)
    private void aurora$fastLookup(DirectStateAccess dsa, List<FrameBufferAttachment> colors,
                                   FrameBufferAttachment depth, CallbackInfoReturnable<Integer> cir) {
        if (!AuroraConfig.get().perfGlDispatch || colors.size() != 1) return;
        FrameBufferAttachment c = colors.get(0);
        int cId = c != null ? c.glId() : 0;
        int cMip = c != null ? c.fboMipLevel() : 0;
        int dId = depth != null ? depth.glId() : 0;
        int dMip = depth != null ? depth.fboMipLevel() : 0;
        for (int i = 0; i < 4; i++) {
            int fbo = this.aurora$fbo[i];
            if (fbo != -1 && this.aurora$cId[i] == cId && this.aurora$cMip[i] == cMip
                    && this.aurora$dId[i] == dId && this.aurora$dMip[i] == dMip) {
                cir.setReturnValue(fbo);
                return;
            }
        }
    }

    @Inject(method = "getFbo", at = @At("RETURN"))
    private void aurora$recordLookup(DirectStateAccess dsa, List<FrameBufferAttachment> colors,
                                     FrameBufferAttachment depth, CallbackInfoReturnable<Integer> cir) {
        if (!AuroraConfig.get().perfGlDispatch || colors.size() != 1) return;
        FrameBufferAttachment c = colors.get(0);
        int slot = this.aurora$nextSlot++ & 3;
        this.aurora$cId[slot] = c != null ? c.glId() : 0;
        this.aurora$cMip[slot] = c != null ? c.fboMipLevel() : 0;
        this.aurora$dId[slot] = depth != null ? depth.glId() : 0;
        this.aurora$dMip[slot] = depth != null ? depth.fboMipLevel() : 0;
        this.aurora$fbo[slot] = cir.getReturnValueI();
    }

    @Inject(method = "destroyFbo", at = @At("HEAD"))
    private void aurora$invalidateOnDestroy(FrameBufferCache.CacheKey key, CallbackInfo ci) {
        this.aurora$fbo[0] = -1;
        this.aurora$fbo[1] = -1;
        this.aurora$fbo[2] = -1;
        this.aurora$fbo[3] = -1;
    }
}
