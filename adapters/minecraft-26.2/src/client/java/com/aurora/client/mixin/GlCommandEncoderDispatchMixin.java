package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.render.GlTextureMipCache;
import com.aurora.client.render.ShaderPackState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.opengl.GlTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skips redundant OpenGL draw-setup driver calls ({@code perfGlDispatch}, ADR 0009, 26.2):
 * <ul>
 *   <li>Caches each {@link GlTexture}'s last {@code GL_TEXTURE_BASE_LEVEL} and
 *       {@code GL_TEXTURE_MAX_LEVEL} so unchanged mip ranges skip both
 *       {@code glTexParameteri} calls.</li>
 *   <li>Applies {@code glDrawBuffers} once per {@code GlRenderPass} instead of
 *       re-sending identical attachment indices on every draw inside the pass.</li>
 * </ul>
 * Yields to active Iris shader packs at draw time so custom shader FBO state is never elided.
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class GlCommandEncoderDispatchMixin {
    private static final int GL_TEXTURE_BASE_LEVEL = 33084;
    private static final int GL_TEXTURE_MAX_LEVEL = 33085;

    @Unique private Object aurora$drawBuffersPass;
    @Unique private int aurora$drawBuffersCount = -1;

    @Inject(method = "createRenderPass", at = @At("HEAD"))
    private void aurora$resetDrawBuffersOnCreate(CallbackInfoReturnable<?> cir) {
        this.aurora$drawBuffersPass = null;
        this.aurora$drawBuffersCount = -1;
    }

    @Inject(method = "submitRenderPass", at = @At("HEAD"))
    private void aurora$resetDrawBuffersOnSubmit(CallbackInfo ci) {
        this.aurora$drawBuffersPass = null;
        this.aurora$drawBuffersCount = -1;
    }

    @WrapOperation(
            method = "trySetup",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_texParameter(III)V"))
    private void aurora$skipUnchangedMipRange(int target, int pname, int param,
                                              Operation<Void> original,
                                              @Local GlTexture texture) {
        if (AuroraConfig.get().perfGlDispatch
                && !ShaderPackState.irisPackInUse()
                && texture instanceof GlTextureMipCache cache) {
            if (pname == GL_TEXTURE_BASE_LEVEL && !cache.aurora$updateBaseMip(param)) return;
            if (pname == GL_TEXTURE_MAX_LEVEL && !cache.aurora$updateMaxMip(param)) return;
        }
        original.call(target, pname, param);
    }

    @WrapOperation(
            method = "trySetup",
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL33C;glDrawBuffers([I)V"))
    private void aurora$skipRepeatDrawBuffers(int[] drawBuffers,
                                              Operation<Void> original,
                                              @Local(argsOnly = true, ordinal = 0) Object renderPass) {
        if (AuroraConfig.get().perfGlDispatch && !ShaderPackState.irisPackInUse()) {
            if (this.aurora$drawBuffersPass == renderPass && this.aurora$drawBuffersCount == drawBuffers.length) {
                return;
            }
            this.aurora$drawBuffersPass = renderPass;
            this.aurora$drawBuffersCount = drawBuffers.length;
        } else {
            this.aurora$drawBuffersPass = null;
            this.aurora$drawBuffersCount = -1;
        }
        original.call((Object) drawBuffers);
    }
}
