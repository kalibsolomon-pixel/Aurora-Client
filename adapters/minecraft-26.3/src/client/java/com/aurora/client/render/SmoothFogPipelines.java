package com.aurora.client.render;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;

/**
 * Smooth Fog's twin pipelines ({@link SmoothFog}, 26.3). Composes cleanly on top
 * of vanilla, Sodium, and {@link GradientDitherPipelines} twins, and stands down
 * while an Iris shader pack is active.
 */
public final class SmoothFogPipelines {
    private SmoothFogPipelines() {}

    private static final class Twin {
        final RenderPipeline pipeline;
        boolean checked;
        boolean ready;

        Twin(RenderPipeline pipeline) { this.pipeline = pipeline; }
    }

    private static final Map<RenderPipeline, Twin> TWINS = new WeakHashMap<>();
    private static final Twin KEEP = new Twin(null);
    private static final Set<String> PATCHED = ConcurrentHashMap.newKeySet();
    private static boolean reloading;
    private static boolean stoodDown;

    public static Object patchSource(Identifier location, Object source) {
        if (!(source instanceof String text) || !SmoothFog.patchesShader(location.getNamespace(), location.getPath())) return source;
        String patched = SmoothFog.patch(text);
        if (patched != text) PATCHED.add(location.toString());
        return patched;
    }

    public static RenderPipeline select(RenderPipeline pipeline) {
        if (!AuroraConfig.get().smoothFog || pipeline instanceof Smoothed || !RenderSystem.isOnRenderThread()) return pipeline;
        Twin twin = TWINS.get(pipeline);
        if (twin == null) {
            twin = eligible(pipeline) ? new Twin(new Smoothed(pipeline)) : KEEP;
            TWINS.put(pipeline, twin);
        }
        if (twin == KEEP || standingDown()) return pipeline;
        if (!twin.checked) compile(twin);
        return twin.ready ? twin.pipeline : pipeline;
    }

    private static boolean standingDown() {
        boolean pack = ShaderPackState.irisPackInUse();
        if (pack != stoodDown) {
            stoodDown = pack;
            AuroraClient.LOGGER.info(pack ? "Smooth Fog: standing down while an Iris shader pack draws the world"
                    : "Smooth Fog: no Iris shader pack in use; smooth fog is active again");
        }
        return pack;
    }

    public static void onShadersReloaded() {
        for (Twin twin : TWINS.values()) twin.checked = false;
        int patched = PATCHED.size();
        PATCHED.clear();
        if (!AuroraConfig.get().smoothFog || standingDown()) return;
        int smoothed = 0;
        reloading = true;
        try {
            for (List<RenderPipeline> pipelines : List.of(RenderPipelines.requiredPipelines(), RenderPipelines.optionalPipelines())) {
                for (RenderPipeline pipeline : pipelines) if (select(pipeline) != pipeline) smoothed++;
            }
        } finally {
            reloading = false;
        }
        AuroraClient.LOGGER.info("Smooth Fog: {} shader files patched, {} world pipelines smoothed", patched, smoothed);
    }

    private static void compile(Twin twin) {
        twin.checked = true;
        try {
            twin.ready = RenderSystem.getCompiledPipelineNullable(twin.pipeline) != null;
        } catch (RuntimeException | LinkageError failure) {
            twin.ready = false;
            AuroraClient.LOGGER.warn("Smooth Fog: {} failed to compile", twin.pipeline.getLocation(), failure);
        }
        if (!twin.ready) {
            AuroraClient.LOGGER.warn("Smooth Fog: {} is unavailable; drawn with vanilla fog until shaders reload",
                    twin.pipeline.getLocation());
        } else if (!reloading) {
            AuroraClient.LOGGER.info("Smooth Fog: {} active", twin.pipeline.getLocation());
        }
    }

    private static boolean eligible(RenderPipeline pipeline) {
        ShaderDefines defines = pipeline.getShaderDefines();
        Set<String> names = new HashSet<>(defines.values().keySet());
        names.addAll(defines.flags());
        Identifier location = pipeline.getLocation();
        return SmoothFog.appliesTo(location.getNamespace(), location.getPath(),
                String.valueOf(pipeline.getShaders().get(ShaderType.FRAGMENT)), names);
    }

    private static final class Smoothed extends RenderPipeline {
        Smoothed(RenderPipeline base) {
            super(Identifier.fromNamespaceAndPath("aurora", "smooth_fog/" + base.getLocation().getNamespace() + "/" + base.getLocation().getPath()),
                    base.getShaders(),
                    base.getShaderDefines().withOverrides(ShaderDefines.builder().define(SmoothFog.DEFINE).build()),
                    base.getBindGroupLayouts(),
                    base.getColorTargetStates().toArray(new ColorTargetState[0]),
                    base.getDepthStencilState(),
                    base.getPolygonMode(),
                    base.isCull(),
                    base.getVertexFormatBindings().toArray(new VertexFormat[0]),
                    base.getPrimitiveTopology(),
                    base.pushConstantSize(),
                    base.getSortKey());
        }
    }
}
