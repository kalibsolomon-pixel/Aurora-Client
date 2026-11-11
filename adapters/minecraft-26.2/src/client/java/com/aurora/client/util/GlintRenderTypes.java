package com.aurora.client.util;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;

/** Same geometry, depth, scrolling and targets as vanilla; color blends through a mask. */
public final class GlintRenderTypes {
    private GlintRenderTypes() {}
    public static RenderType item() { return Holder.ITEM; }
    public static RenderType translucent() { return Holder.TRANSLUCENT; }
    public static RenderType entity() { return Holder.ENTITY; }
    public static RenderType armor() { return Holder.ARMOR; }

    private static RenderPipeline pipeline() {
        RenderPipeline vanilla = RenderPipelines.GLINT;
        var builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("aurora", "pipeline/masked_glint"))
                .withVertexShader(vanilla.getVertexShader())
                .withFragmentShader(Identifier.fromNamespaceAndPath("aurora", "core/masked_glint"))
                .withVertexBinding(0, vanilla.getVertexFormatBinding(0)).withPrimitiveTopology(vanilla.getPrimitiveTopology())
                // Vanilla glint's depth test, depth write, cull and write mask; Aurora's own blend.
                .withDepthStencilState(java.util.Optional.ofNullable(vanilla.getDepthStencilState()))
                .withCull(vanilla.isCull())
                .withColorTargetState(new ColorTargetState(java.util.Optional.of(new BlendFunction(
                        BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendFactor.ZERO, BlendFactor.ONE)),
                        vanilla.getColorTargetState().format(), vanilla.getColorTargetState().writeMask()));
        for (var layout : vanilla.getBindGroupLayouts()) builder.withBindGroupLayout(layout);
        return builder.build();
    }

    private static RenderType type(String name, Identifier texture, TextureTransform transform,
                                   boolean armor, boolean translucent) {
        var builder = RenderSetup.builder(Holder.PIPELINE)
                .withTexture("Sampler0", texture).setTextureTransform(transform);
        if (armor) builder.setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING);
        if (translucent) builder.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET);
        return RenderType.create("aurora_masked_" + name, builder.createRenderSetup());
    }

    private static final class Holder {
        private static final RenderPipeline PIPELINE = pipeline();
        private static final RenderType ITEM = type("glint", GlintColor.GLINT_ITEM,
                TextureTransform.GLINT_TEXTURING, false, false);
        private static final RenderType TRANSLUCENT = type("glint_translucent", GlintColor.GLINT_ITEM,
                TextureTransform.GLINT_TEXTURING, false, true);
        private static final RenderType ENTITY = type("entity_glint", GlintColor.GLINT_ITEM,
                TextureTransform.ENTITY_GLINT_TEXTURING, false, false);
        private static final RenderType ARMOR = type("armor_entity_glint", GlintColor.GLINT_ARMOR,
                TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING, true, false);
    }
}
