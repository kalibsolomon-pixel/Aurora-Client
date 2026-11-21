package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.opengl.VertexArrayCache;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Identity cache for {@code VertexArrayCache$Separate.bindVertexArray}
 * ({@code perfGlDispatch}, ADR 0009, 26.2). Vanilla calls
 * {@code Arrays.asList(vertexBindings)} and hashes all 16 slots in a
 * {@link Map} on every VAO bind, even though each {@code RenderPipeline}
 * supplies the same immutable {@code VertexFormat[]} array every time (and
 * vanilla itself stores {@code Arrays.asList(vertexBindings)} directly as its
 * map key). On an identity hit, both the list wrapper allocation and the
 * 16-element hash/equals lookup are skipped; all OpenGL calls stay vanilla.
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.VertexArrayCache$Separate")
public abstract class VertexArrayCacheSeparateMixin {
    @Unique private final IdentityHashMap<VertexFormat[], VertexArrayCache.VertexArray> aurora$byRef = new IdentityHashMap<>();
    @Unique private VertexFormat[] aurora$lastBindings;
    @Unique private VertexArrayCache.VertexArray aurora$lastVao;
    @Unique private VertexArrayCache.VertexArray aurora$pendingHit;

    @WrapOperation(
            method = "bindVertexArray",
            at = @At(value = "INVOKE", target = "Ljava/util/Arrays;asList([Ljava/lang/Object;)Ljava/util/List;"))
    private List<VertexFormat> aurora$skipAsListOnHit(Object[] array, Operation<List<VertexFormat>> original,
                                                      @Local(argsOnly = true) VertexFormat[] vertexBindings) {
        if (AuroraConfig.get().perfGlDispatch) {
            VertexArrayCache.VertexArray hit = (vertexBindings == this.aurora$lastBindings)
                    ? this.aurora$lastVao
                    : this.aurora$byRef.get(vertexBindings);
            if (hit != null) {
                this.aurora$pendingHit = hit;
                return null;
            }
        }
        this.aurora$pendingHit = null;
        return original.call((Object) array);
    }

    @WrapOperation(
            method = "bindVertexArray",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object aurora$useIdentityHit(Map<?, ?> cache, Object key, Operation<Object> original) {
        VertexArrayCache.VertexArray hit = this.aurora$pendingHit;
        if (hit != null) {
            this.aurora$pendingHit = null;
            return hit;
        }
        return original.call(cache, key);
    }

    @Inject(method = "bindVertexArray", at = @At("RETURN"))
    private void aurora$recordVao(VertexFormat[] vertexBindings,
                                  com.mojang.blaze3d.buffers.GpuBufferSlice[] vertexBuffers,
                                  VertexArrayCache.VertexArray lastBoundVertexArray,
                                  CallbackInfoReturnable<VertexArrayCache.VertexArray> cir) {
        VertexArrayCache.VertexArray vao = cir.getReturnValue();
        if (vao != null && vertexBindings != null) {
            if (vertexBindings != this.aurora$lastBindings) {
                this.aurora$byRef.put(vertexBindings, vao);
                this.aurora$lastBindings = vertexBindings;
                this.aurora$lastVao = vao;
            }
        }
    }
}
