package com.aurora.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Vanilla's translucent line type with Aurora's distance-field line shader:
 * same vertex format, depth, blend and layering as {@code linesTranslucent()},
 * plus an anti-aliased edge and round caps (see {@link WorldLineRenderer}).
 */
final class SdfLineRenderTypes {
    private SdfLineRenderTypes() {}

    private static boolean shaderUnavailable;

    /** The SDF line type, or null when its shader cannot be used this session. */
    static RenderType linesOrNull() {
        if (shaderUnavailable) return null;
        boolean available;
        try {
            var compiled = RenderSystem.getDevice().precompilePipeline(Holder.PIPELINE);
            available = compiled != null && compiled.isValid();
        } catch (RuntimeException | LinkageError failure) {
            shaderUnavailable = true;
            com.aurora.client.AuroraClient.LOGGER.warn("SDF line shader failed; using vanilla lines for this session", failure);
            return null;
        }
        if (!available) {
            shaderUnavailable = true;
            com.aurora.client.AuroraClient.LOGGER.warn("SDF line shader unavailable; using vanilla lines for this session");
            return null;
        }
        return Holder.TYPE;
    }

    private static final class Holder {
        static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("aurora", "pipeline/sdf_lines"))
                .withVertexShader(Identifier.fromNamespaceAndPath("aurora", "core/sdf_lines"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("aurora", "core/sdf_lines"))
                .withDepthStencilState(java.util.Optional.ofNullable(RenderPipelines.LINES_TRANSLUCENT.getDepthStencilState()))
                .withColorTargetState(RenderPipelines.LINES_TRANSLUCENT.getColorTargetState())
                .build();
        static final RenderType TYPE = RenderType.create("aurora_sdf_lines", RenderSetup.builder(PIPELINE)
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                .createRenderSetup());
    }
}
