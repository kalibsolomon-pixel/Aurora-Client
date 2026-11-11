package com.aurora.client.util;

import com.mojang.renderpearl.api.pipeline.*;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.resources.Identifier;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Native armor lighting, glint and decal depth, with the hurt-overlay sampler enabled. */
public final class HitColorTrimRenderTypes {
    private HitColorTrimRenderTypes() {}
    private record Key(Identifier texture, boolean decal, boolean glint) {}
    private static final Map<Key, RenderType> TYPES = new HashMap<>();

    public static RenderType armor(Identifier texture, boolean glint) { return type(new Key(texture, false, glint)); }
    public static RenderType trim(Identifier texture, boolean decal) { return type(new Key(texture, decal, false)); }

    private static RenderType type(Key key) {
        return TYPES.computeIfAbsent(key, k -> {
            var builder = RenderSetup.builder(k.decal ? Pipelines.DECAL : k.glint ? Pipelines.GLINT : Pipelines.ARMOR)
                    .withTexture("Sampler0", k.texture).useLightmap().useOverlay()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .affectsCrumbling().setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE);
            if (k.glint) builder.withTexture("GlintSampler", ItemFeatureRenderer.ENCHANTED_GLINT_ARMOR)
                    .setTextureTransform(TextureTransform.ARMOR_ENTITY_GLINT_TEXTURING);
            return RenderType.create("aurora_hit_color_armor", builder.createRenderSetup());
        });
    }

    private static RenderPipeline pipeline(String name, boolean decal, boolean glint) {
        var builder = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("aurora", "pipeline/hit_color_" + name))
                .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                .withShaderDefine("ALPHA_CUTOUT", 0.1F).withShaderDefine("PER_FACE_LIGHTING")
                .withCull(false).withColorTargetState(ColorTargetState.DEFAULT);
        if (decal) builder.withDepthStencilState(new DepthStencilState(CompareOp.EQUAL, false));
        if (glint) builder.withSnippet(RenderPipelines.GLINT_SNIPPET);
        return builder.build();
    }

    private static final class Pipelines {
        static final RenderPipeline ARMOR = pipeline("armor", false, false);
        static final RenderPipeline DECAL = pipeline("trim_decal", true, false);
        static final RenderPipeline GLINT = pipeline("armor_glint", false, true);
    }
}
