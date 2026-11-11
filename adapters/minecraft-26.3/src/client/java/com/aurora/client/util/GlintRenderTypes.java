package com.aurora.client.util;

import com.aurora.client.config.AuroraConfig;
import com.mojang.renderpearl.api.pipeline.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.resources.Identifier;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Preserve vanilla's combined glint / OIT passes; change only the mask composition. */
public final class GlintRenderTypes {
    private static final Map<RenderPipeline, RenderPipeline> PIPELINES = new IdentityHashMap<>();
    private static final Map<OitPipelineSet, OitPipelineSet> OIT = new IdentityHashMap<>();
    private GlintRenderTypes() {}

    public static PreparedRenderType alternative(PreparedRenderType original) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!(cfg.glintColorEnabled && cfg.glintColorAlternative)) return original;
        if (!cfg.glintColorTintArmor && original.name().contains("armor")) return original;
        RenderPipeline pipeline = masked(original.pipeline());
        OitPipelineSet oit = original.oitPipelineSet();
        if (oit != null && pipeline != original.pipeline()) {
            oit = OIT.computeIfAbsent(oit, set -> new OitPipelineSet(masked(set.depthBoundsPipeline()),
                    masked(set.transmittancePipeline()), masked(set.accumulatePipeline())));
        }
        if (pipeline == original.pipeline()) return original;
        return new PreparedRenderType(original.name(), pipeline, oit, original.dynamicTransforms(),
                original.scissorState(), original.textures());
    }

    private static RenderPipeline masked(RenderPipeline original) {
        boolean separate = original == RenderPipelines.GLINT;
        if (!separate && !original.getShaderDefines().flags().contains("GLINT")) return original;
        Identifier fragment = original.getShaders().get(ShaderType.FRAGMENT);
        if (fragment == null || !fragment.getNamespace().equals("minecraft")) return original;
        String path = fragment.getPath();
        if (!(path.equals("core/item") || path.equals("core/entity") || separate)) return original;
        return PIPELINES.computeIfAbsent(original, key -> new MaskedPipeline(key, separate));
    }

    private static final class MaskedPipeline extends RenderPipeline {
        MaskedPipeline(RenderPipeline base, boolean separate) {
            super(Identifier.fromNamespaceAndPath("aurora", base.getLocation().getPath() + "_masked"),
                    shaders(base, separate), base.getShaderDefines(), base.getBindGroupLayouts(),
                    colors(base, separate), base.getDepthStencilState(), base.getPolygonMode(), base.isCull(),
                    base.getVertexFormatBindings().toArray(com.mojang.renderpearl.api.vertex.VertexFormat[]::new),
                    base.getPrimitiveTopology(), base.pushConstantSize(), base.getSortKey());
        }

        private static Map<ShaderType, Identifier> shaders(RenderPipeline base, boolean separate) {
            var shaders = new EnumMap<ShaderType, Identifier>(ShaderType.class);
            shaders.putAll(base.getShaders());
            shaders.put(ShaderType.FRAGMENT, Identifier.fromNamespaceAndPath("aurora", separate
                    ? "core/masked_glint" : base.getShaders().get(ShaderType.FRAGMENT).getPath() + "_masked_glint"));
            return shaders;
        }

        private static ColorTargetState[] colors(RenderPipeline base, boolean separate) {
            var colors = base.getColorTargetStates().toArray(ColorTargetState[]::new);
            if (separate) colors[0] = new ColorTargetState(java.util.Optional.of(new BlendFunction(
                    BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA, BlendFactor.ZERO, BlendFactor.ONE)),
                    colors[0].format(), colors[0].writeMask());
            return colors;
        }
    }
}
