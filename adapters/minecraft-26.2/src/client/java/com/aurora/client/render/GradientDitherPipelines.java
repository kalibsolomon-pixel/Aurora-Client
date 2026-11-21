package com.aurora.client.render;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;

/**
 * Gradient Dithering's twin pipelines ({@link GradientDither}). Each eligible world pipeline
 * gets one twin, a copy whose only difference is the dither define; while the feature is on,
 * every render pass that binds the original binds the twin. Vanilla's twins compile with the
 * shader reload, Sodium's on first use. A twin that does not compile is drawn as the original
 * until the next reload.
 */
public final class GradientDitherPipelines {
    private GradientDitherPipelines() {}

    private static final class Twin {
        final RenderPipeline pipeline;
        boolean checked;
        boolean ready;

        Twin(RenderPipeline pipeline) { this.pipeline = pipeline; }
    }

    /** Twins by original; weak so pipelines another mod rebuilds do not accumulate. Render thread only. */
    private static final Map<RenderPipeline, Twin> TWINS = new WeakHashMap<>();
    /** The entry of a pipeline that keeps its own shaders: every GUI and HUD pipeline, among others. */
    private static final Twin KEEP = new Twin(null);
    /** Shader files patched since the last reload was applied; resources load on worker threads. */
    private static final Set<String> PATCHED = ConcurrentHashMap.newKeySet();
    private static boolean reloading;
    private static boolean stoodDown;

    /** The fragment source with the dither added behind its define, for the shader files that get it. */
    public static Object patchSource(Identifier location, Object source) {
        if (!(source instanceof String text) || !GradientDither.patchesShader(location.getNamespace(), location.getPath())) return source;
        String patched = GradientDither.patch(text);
        if (patched != text) PATCHED.add(location.toString());
        return patched;
    }

    /**
     * The pipeline to draw with: its twin while Gradient Dithering is on, no Iris shader pack is
     * in use and the twin compiled. Pipelines that keep their shaders return after one lookup.
     */
    public static RenderPipeline select(RenderPipeline pipeline) {
        if (!AuroraConfig.get().gradientDither || pipeline instanceof Dithered || !RenderSystem.isOnRenderThread()) return pipeline;
        Twin twin = TWINS.get(pipeline);
        if (twin == null) {
            GradientDither.Mode mode = modeOf(pipeline);
            twin = mode == null ? KEEP : new Twin(new Dithered(pipeline, mode));
            TWINS.put(pipeline, twin);
        }
        if (twin == KEEP || standingDown()) return pipeline;
        if (!twin.checked) compile(twin);
        return twin.ready ? twin.pipeline : pipeline;
    }

    /** True while an Iris shader pack draws the world; logs each change. */
    private static boolean standingDown() {
        boolean pack = ShaderPackState.irisPackInUse();
        if (pack != stoodDown) {
            stoodDown = pack;
            AuroraClient.LOGGER.info(pack ? "Gradient Dithering: standing down while an Iris shader pack draws the world"
                    : "Gradient Dithering: no Iris shader pack in use; the world is dithered again");
        }
        return pack;
    }

    /**
     * The reload cleared every compiled pipeline, twins included: check each twin again, and
     * compile vanilla's now so the first world frame does not wait for them.
     */
    public static void onShadersReloaded() {
        for (Twin twin : TWINS.values()) twin.checked = false;
        int patched = PATCHED.size();
        PATCHED.clear();
        if (!AuroraConfig.get().gradientDither || standingDown()) return;
        int dithered = 0;
        reloading = true;
        try {
            for (RenderPipeline pipeline : RenderPipelines.getStaticPipelines()) if (select(pipeline) != pipeline) dithered++;
        } finally {
            reloading = false;
        }
        AuroraClient.LOGGER.info("Gradient Dithering: {} shader files patched, {} world pipelines dithered", patched, dithered);
    }

    private static void compile(Twin twin) {
        twin.checked = true;
        try {
            // Cached by the device, as every pipeline is; later binds reuse it.
            CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(twin.pipeline);
            twin.ready = compiled != null && compiled.isValid();
        } catch (RuntimeException | LinkageError failure) {
            twin.ready = false;
            AuroraClient.LOGGER.warn("Gradient Dithering: {} failed to compile", twin.pipeline.getLocation(), failure);
        }
        if (!twin.ready) {
            AuroraClient.LOGGER.warn("Gradient Dithering: {} is unavailable; drawn without dithering until shaders reload",
                    twin.pipeline.getLocation());
        } else if (!reloading) {
            // Pipelines other mods build (Sodium's terrain) compile on first use.
            AuroraClient.LOGGER.info("Gradient Dithering: {} dithered", twin.pipeline.getLocation());
        }
    }

    private static GradientDither.Mode modeOf(RenderPipeline pipeline) {
        ColorTargetState[] targets = pipeline.getColorTargetStates();
        ColorTargetState target = targets.length == 1 ? targets[0] : null;
        boolean single = target != null && target.format() == GpuFormat.RGBA8_UNORM
                && (target.writeMask() & ColorTargetState.WRITE_COLOR) == ColorTargetState.WRITE_COLOR;
        GradientDither.Blend blend = single ? blendOf(target.blendFunction()) : GradientDither.Blend.OTHER;
        ShaderDefines defines = pipeline.getShaderDefines();
        Set<String> names = new HashSet<>(defines.values().keySet());
        names.addAll(defines.flags());
        Identifier location = pipeline.getLocation();
        return GradientDither.modeFor(location.getNamespace(), location.getPath(),
                String.valueOf(pipeline.getFragmentShader()), names, single, blend);
    }

    private static GradientDither.Blend blendOf(Optional<BlendFunction> blend) {
        if (blend.isEmpty()) return GradientDither.Blend.NONE;
        return blend.get().color().equals(BlendFunction.TRANSLUCENT.color())
                ? GradientDither.Blend.STRAIGHT_ALPHA : GradientDither.Blend.OTHER;
    }

    /** The original with the dither define added; same shaders, layouts, state and sort key. */
    private static final class Dithered extends RenderPipeline {
        Dithered(RenderPipeline base, GradientDither.Mode mode) {
            super(Identifier.fromNamespaceAndPath("aurora", "gradient_dither/" + base.getLocation().getNamespace() + "/" + base.getLocation().getPath()),
                    base.getVertexShader(),
                    base.getFragmentShader(),
                    base.getShaderDefines().withOverrides(ShaderDefines.builder().define(GradientDither.DEFINE, mode.value).build()),
                    base.getBindGroupLayouts(),
                    base.getColorTargetStates().clone(),
                    base.getDepthStencilState(),
                    base.getPolygonMode(),
                    base.isCull(),
                    base.getVertexFormatBindings().clone(),
                    base.getPrimitiveTopology(),
                    base.getSortKey());
        }
    }
}
