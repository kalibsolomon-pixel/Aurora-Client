package com.aurora.client.util;

import com.aurora.client.config.AuroraConfig;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** A mask substitution must retain native depth, vertex bindings and every OIT phase. */
class GlintPipelineTest {
    @Test void hurtArmorRetainsVanillaLightingGlintAndDecalDepth() {
        var texture = Identifier.fromNamespaceAndPath("minecraft", "test_armor");
        RenderPipeline[] nativePipelines = {RenderPipelines.ARMOR_CUTOUT_NO_CULL,
                RenderPipelines.ARMOR_CUTOUT_NO_CULL_GLINT, RenderPipelines.ARMOR_DECAL_CUTOUT_NO_CULL};
        RenderPipeline[] tintedPipelines = {HitColorTrimRenderTypes.armor(texture, false).pipeline(),
                HitColorTrimRenderTypes.armor(texture, true).pipeline(), HitColorTrimRenderTypes.trim(texture, true).pipeline()};
        for (int i = 0; i < nativePipelines.length; i++) {
            var original = nativePipelines[i];
            var tinted = tintedPipelines[i];
            assertFalse(tinted.getShaderDefines().flags().contains("NO_OVERLAY"));
            assertTrue(tinted.getShaderDefines().flags().contains("PER_FACE_LIGHTING"));
            assertEquals(original.getShaderDefines().flags().contains("GLINT"), tinted.getShaderDefines().flags().contains("GLINT"));
            assertEquals(original.getShaders(), tinted.getShaders());
            assertEquals(original.getDepthStencilState(), tinted.getDepthStencilState());
            assertEquals(original.getColorTargetStates(), tinted.getColorTargetStates());
            assertEquals(original.getVertexFormatBindings(), tinted.getVertexFormatBindings());
            assertEquals(original.getPrimitiveTopology(), tinted.getPrimitiveTopology());
            assertEquals(original.isCull(), tinted.isCull());
        }
    }

    @Test void combinedGlintPreservesNativeStateAndFeatureGates() {
        AuroraConfig cfg = AuroraConfig.get();
        boolean enabled = cfg.glintColorEnabled, alternative = cfg.glintColorAlternative,
                armor = cfg.glintColorTintArmor;
        try {
            var original = new PreparedRenderType("item", RenderPipelines.ITEM_TRANSLUCENT_GLINT,
                    RenderPipelines.OIT_ITEM_GLINT, null, null, List.of());
            cfg.glintColorEnabled = false;
            cfg.glintColorAlternative = true;
            assertSame(original, GlintRenderTypes.alternative(original));
            cfg.glintColorEnabled = true;
            cfg.glintColorAlternative = false;
            assertSame(original, GlintRenderTypes.alternative(original));
            cfg.glintColorAlternative = true;
            var masked = GlintRenderTypes.alternative(original);
            preserved(original.pipeline(), masked.pipeline());
            preserved(original.oitPipelineSet().depthBoundsPipeline(), masked.oitPipelineSet().depthBoundsPipeline());
            preserved(original.oitPipelineSet().transmittancePipeline(), masked.oitPipelineSet().transmittancePipeline());
            preserved(original.oitPipelineSet().accumulatePipeline(), masked.oitPipelineSet().accumulatePipeline());
            assertSame(original.textures(), masked.textures());
            assertSame(masked.pipeline(), GlintRenderTypes.alternative(original).pipeline(), "Reuse pipelines per frame");
            assertSame(masked.oitPipelineSet(), GlintRenderTypes.alternative(original).oitPipelineSet());
            var armorType = new PreparedRenderType("armor", RenderPipelines.ARMOR_CUTOUT_NO_CULL_GLINT,
                    null, null, null, List.of());
            cfg.glintColorTintArmor = false;
            assertSame(armorType, GlintRenderTypes.alternative(armorType));
            cfg.glintColorTintArmor = true;
            preserved(armorType.pipeline(), GlintRenderTypes.alternative(armorType).pipeline());
        } finally {
            cfg.glintColorEnabled = enabled;
            cfg.glintColorAlternative = alternative;
            cfg.glintColorTintArmor = armor;
        }
    }

    private static void preserved(RenderPipeline original, RenderPipeline masked) {
        assertEquals(original.getShaderDefines(), masked.getShaderDefines());
        assertEquals(original.getColorTargetStates(), masked.getColorTargetStates());
        assertEquals(original.getDepthStencilState(), masked.getDepthStencilState());
        assertEquals(original.getBindGroupLayouts(), masked.getBindGroupLayouts());
        assertEquals(original.getVertexFormatBindings(), masked.getVertexFormatBindings());
        assertEquals(original.getPrimitiveTopology(), masked.getPrimitiveTopology());
        assertEquals(original.getPolygonMode(), masked.getPolygonMode());
        assertEquals(original.isCull(), masked.isCull());
        assertEquals(original.pushConstantSize(), masked.pushConstantSize());
        assertEquals(original.getShaders().get(ShaderType.VERTEX), masked.getShaders().get(ShaderType.VERTEX));
        if (original.getShaderDefines().flags().contains("GLINT")) {
            assertEquals("aurora", masked.getShaders().get(ShaderType.FRAGMENT).getNamespace());
        }
    }
}
