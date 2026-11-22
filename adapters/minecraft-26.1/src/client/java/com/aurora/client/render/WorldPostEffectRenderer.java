package com.aurora.client.render;

import com.aurora.client.adapter.NativeUi;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.util.ShaderLoad;
import com.aurora.client.util.PackTweaksMath;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Fullscreen Blaze3D world post-processing for Pack Tweaks (motion blur and
 * colour saturation) on Minecraft 26.1.2. Runs after the world finishes
 * rendering and before GUI/glass extraction so HUD and menus are untouched.
 */
public final class WorldPostEffectRenderer {
    private WorldPostEffectRenderer() {}

    private static final int PARAM_BYTES = 16;
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("aurora", "pipeline/world_post"))
            .withVertexShader(Identifier.fromNamespaceAndPath("aurora", "core/world_post"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("aurora", "core/world_post"))
            .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
            .withSampler("InputTexture")
            .withSampler("HistoryTexture")
            .withUniform("PostParams", UniformType.UNIFORM_BUFFER)
            .withDepthStencilState(Optional.empty())
            .withColorTargetState(new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_ALL))
            .withCull(false)
            .build();

    private static final List<AutoCloseable> retired = new ArrayList<>();
    private static final ByteBuffer paramScratch = ByteBuffer.allocateDirect(PARAM_BYTES).order(ByteOrder.nativeOrder());
    private static final GpuBuffer[] passUniforms = new GpuBuffer[2];
    private static final float[] lastBlend = {Float.NaN, Float.NaN};
    private static final float[] lastSat = {Float.NaN, Float.NaN};
    private static GpuBuffer triangle;
    private static GpuTexture texA, texB;
    private static GpuTextureView viewA, viewB;
    private static int texW, texH;
    private static boolean useAAsHistory = true;
    private static boolean historyValid = false;
    private static ClientLevel lastLevel;
    private static long lastFrameNanos;
    private static boolean failed;

    public static void applyIfNeeded() {
        if (failed) return;
        AuroraConfig cfg = AuroraConfig.get();
        if (!PackTweaksMath.isPostEffectActive(cfg.motionBlurStrength, cfg.colorSaturation)) {
            if (texA != null || texB != null) retireTextures();
            historyValid = false;
            lastFrameNanos = 0L;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            historyValid = false;
            lastLevel = null;
            return;
        }
        if (!ShaderLoad.settled()) return;
        RenderTarget main = NativeUi.mainTarget(mc);
        if (main == null || main.width <= 0 || main.height <= 0 || main.getColorTextureView() == null) return;

        long now = System.nanoTime();
        double dt = lastFrameNanos == 0L ? (1.0 / 60.0) : (now - lastFrameNanos) / 1_000_000_000.0;
        lastFrameNanos = now;

        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            historyValid = false;
        }

        try {
            boolean needHistory = PackTweaksMath.clampMotionBlur(cfg.motionBlurStrength) > 0.001;
            ensureResources(main.width, main.height, needHistory);
            float blend = (needHistory && historyValid) ? PackTweaksMath.motionBlurBlend(cfg.motionBlurStrength, dt) : 0.0f;
            float sat = (float) PackTweaksMath.clampSaturation(cfg.colorSaturation);

            if (!needHistory) {
                runPass(0, main.getColorTextureView(), viewA, viewA, 0.0f, 1.0f);
                runPass(1, viewA, viewA, main.getColorTextureView(), 0.0f, sat);
                historyValid = false;
                return;
            }

            GpuTextureView histRead = useAAsHistory ? viewA : viewB;
            GpuTextureView histWrite = useAAsHistory ? viewB : viewA;

            // Pass 1: main (+ history if motion blur) -> histWrite (pre-saturation so saturation never compounds).
            runPass(0, main.getColorTextureView(), histRead, histWrite, blend, 1.0f);
            // Pass 2: histWrite -> main (applies saturation once to the final output).
            runPass(1, histWrite, histWrite, main.getColorTextureView(), 0.0f, sat);

            useAAsHistory = !useAAsHistory;
            historyValid = true;
        } catch (Throwable t) {
            failed = true;
            com.aurora.client.AuroraClient.LOGGER.warn("[PackTweaks] World post-effect shader unavailable; disabling for session", t);
        }
    }

    private static void ensureResources(int width, int height, boolean needHistory) {
        var device = RenderSystem.getDevice();
        if (triangle == null) {
            ByteBuffer vertices = ByteBuffer.allocateDirect(36).order(ByteOrder.nativeOrder());
            vertices.putFloat(-1).putFloat(-1).putFloat(0);
            vertices.putFloat(3).putFloat(-1).putFloat(0);
            vertices.putFloat(-1).putFloat(3).putFloat(0).flip();
            triangle = device.createBuffer(() -> "Aurora world post triangle",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, vertices);
        }
        int usage = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
        if (texA == null || texW != width || texH != height || texA.isClosed()) {
            retireTextures();
            texA = device.createTexture("aurora:world_post_a", usage, TextureFormat.RGBA8, width, height, 1, 1);
            viewA = device.createTextureView(texA);
            texW = width;
            texH = height;
            historyValid = false;
        }
        if (needHistory) {
            if (texB == null || texB.isClosed()) {
                texB = device.createTexture("aurora:world_post_b", usage, TextureFormat.RGBA8, width, height, 1, 1);
                viewB = device.createTextureView(texB);
                historyValid = false;
            }
        } else if (texB != null || viewB != null) {
            if (viewB != null) retired.add(viewB);
            if (texB != null) retired.add(texB);
            viewB = null;
            texB = null;
        }
    }

    private static GpuBuffer uniformFor(int slot, float blend, float saturation) {
        GpuBuffer current = passUniforms[slot];
        if (current != null && !current.isClosed()
                && Math.abs(blend - lastBlend[slot]) < 0.002f
                && Math.abs(saturation - lastSat[slot]) < 1e-4f) {
            return current;
        }
        if (current != null) retired.add(current);
        paramScratch.clear();
        paramScratch.putFloat(blend).putFloat(saturation).putFloat(0f).putFloat(0f).flip();
        GpuBuffer next = RenderSystem.getDevice().createBuffer(() -> "Aurora world post params",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, paramScratch);
        passUniforms[slot] = next;
        lastBlend[slot] = blend;
        lastSat[slot] = saturation;
        return next;
    }

    private static void runPass(int slot, GpuTextureView input, GpuTextureView history, GpuTextureView output,
                                float blend, float saturation) {
        GpuBuffer uniform = uniformFor(slot, blend, saturation);
        var sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Aurora world post", output, OptionalInt.empty())) {
            pass.setPipeline(PIPELINE);
            pass.setVertexBuffer(0, triangle);
            pass.setUniform("PostParams", uniform);
            pass.bindTexture("InputTexture", input, sampler);
            pass.bindTexture("HistoryTexture", history, sampler);
            pass.draw(0, 3);
        }
    }

    private static void retireTextures() {
        if (viewA != null) retired.add(viewA);
        if (texA != null) retired.add(texA);
        if (viewB != null) retired.add(viewB);
        if (texB != null) retired.add(texB);
        viewA = null;
        texA = null;
        viewB = null;
        texB = null;
        texW = 0;
        texH = 0;
    }

    public static void beginFrame() {
        if (retired.isEmpty()) return;
        for (AutoCloseable r : retired) closeQuietly(r);
        retired.clear();
    }

    public static void shutdown() {
        retireTextures();
        for (int i = 0; i < passUniforms.length; i++) {
            if (passUniforms[i] != null) retired.add(passUniforms[i]);
            passUniforms[i] = null;
            lastBlend[i] = Float.NaN;
            lastSat[i] = Float.NaN;
        }
        if (triangle != null) retired.add(triangle);
        triangle = null;
        beginFrame();
        historyValid = false;
        lastLevel = null;
    }

    private static void closeQuietly(AutoCloseable r) {
        try { r.close(); } catch (Exception ignored) {}
    }
}
