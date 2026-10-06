package com.aurora.client.mixin;

import com.aurora.client.util.GlintBuffers;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.LinkedHashMap;
import java.util.SequencedMap;

/** Keep both vanilla and custom foil types fixed, regardless of config at startup. */
@Mixin(MultiBufferSource.BufferSource.class)
public abstract class GlintBufferSourceMixin {
    @Shadow @Final @Mutable
    protected SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers;

    @Unique private GlintBuffers.BufferOwner aurora$glintBuffers;

    @Inject(method = "getBuffer", at = @At("HEAD"))
    private void aurora$deferGlintUntilBase(RenderType type, CallbackInfoReturnable<VertexConsumer> cir) {
        if (!GlintBuffers.isGlint(type) || fixedBuffers.containsKey(type)) return;
        // immediate() can supply an immutable empty map. Copy only when an
        // unregistered foil variant is first requested, preserving draw order.
        fixedBuffers = new LinkedHashMap<>(fixedBuffers);
        if (aurora$glintBuffers == null) aurora$glintBuffers = GlintBuffers.owner(this);
        aurora$glintBuffers.add(GlintBuffers.ensureFixed(fixedBuffers, type));
    }
}
