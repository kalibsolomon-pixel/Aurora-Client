package com.aurora.client.ui.render.blur;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

/**
 * Glass through Blaze3D's device/command API. No GL ids, context, pixel-store,
 * native shader compiler or synchronous CPU readback. The Gaussian, straight
 * alpha, single-opacity and lighting contracts match BlurPanelRenderer.
 * Each deferred GUI blit retains its own output until the next frame boundary;
 * the backend owns synchronization and retirement of submitted GPU resources.
 */
final class PortableBlurRenderer {
    private PortableBlurRenderer() {}
    private static final int PARAM_BYTES = 6 * 16;
    private static final RenderPipeline COPY = pipeline("glass_copy");
    private static final RenderPipeline BLUR = pipeline("glass_blur");
    private static final RenderPipeline COMPOSITE = pipeline("glass_composite");
    private static final RenderPipeline PATTERN = pipeline("glass_pattern");
    private static final List<Output> live = new ArrayList<>();
    private static final List<AutoCloseable> retired = new ArrayList<>();
    private static long sequence;
    private static GpuBuffer triangle;

    private static RenderPipeline pipeline(String name) {
        return RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("aurora", "pipeline/" + name))
                .withVertexShader(Identifier.fromNamespaceAndPath("aurora", "core/glass"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("aurora", "core/" + name))
                .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
                .withSampler("InputTexture").withUniform("GlassParams", UniformType.UNIFORM_BUFFER)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false)
                .withCull(false).withoutBlend().build();
    }

    static final class Output {
        final Target[] targets = new Target[5];
        final GpuBuffer[] uniforms = new GpuBuffer[5];
        final float[][] previous = new float[5][];
        boolean registered;
    }

    private static final class Target extends AbstractTexture {
        final Identifier id;
        final int width, height;

        Target(int width, int height) {
            this.width = width;
            this.height = height;
            id = Identifier.fromNamespaceAndPath("aurora", "portable_glass/" + (++sequence));
            var device = RenderSystem.getDevice();
            texture = device.createTexture(id.toString(), GpuTexture.USAGE_RENDER_ATTACHMENT
                    | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_SRC,
                    TextureFormat.RGBA8, width, height, 1, 1);
            textureView = device.createTextureView(texture);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            Minecraft.getInstance().getTextureManager().register(id, this);
        }

        void release() {
            Minecraft.getInstance().getTextureManager().release(id);
        }
    }

    static boolean render(GuiGraphics graphics, Output output, RenderTarget main,
                          int x, int y, int rx, int ry, int rw, int rh,
                          int width, int height, int scale, float radius, float corner,
                          BlurPanelRenderer.Lighting lighting, boolean unified,
                          BlurPanelRenderer.CaptureSource capture) {
        RenderSystem.assertOnRenderThread();
        if (!output.registered) {
            output.registered = true;
            live.add(output);
        }
        ensureTriangle();
        int halfW = Math.max(4, Math.round(rw * .5f));
        int halfH = Math.max(4, Math.round(rh * .5f));
        int quarterW = Math.max(4, Math.round(rw * .25f));
        int quarterH = Math.max(4, Math.round(rh * .25f));
        Target half = target(output, 0, halfW, halfH);
        Target a = target(output, 1, quarterW, quarterH);
        Target b = target(output, 2, quarterW, quarterH);
        Target c = target(output, 3, quarterW, quarterH);
        Target result = target(output, 4, width, height);

        float[] params = new float[PARAM_BYTES / 4];
        // Vulkan attachments and GUI samplers both use top-down texture UVs.
        // Never carry the GL framebuffer's bottom-left convention across devices.
        rect(params, rx / (float) main.width, ry / (float) main.height,
                (rx + rw) / (float) main.width, (ry + rh) / (float) main.height);
        pass(output, 0, COPY, capture != null ? capture.textureView : main.getColorTextureView(), half, params);
        rect(params, 0, 0, 1, 1);
        pass(output, 1, COPY, half.getTextureView(), a, params);
        params[4] = 1f / quarterW;
        params[5] = 0;
        params[6] = Math.max(.65f, radius * .25f / 2f);
        pass(output, 2, BLUR, a.getTextureView(), b, params);
        params[4] = 0;
        params[5] = 1f / quarterH;
        pass(output, 3, BLUR, b.getTextureView(), c, params);

        rect(params, (x - rx) / (float) rw, (y - ry) / (float) rh,
                (x + width - rx) / (float) rw, (y + height - ry) / (float) rh);
        params[8] = width * .5f;
        params[9] = height * .5f;
        params[10] = Math.max(0, Math.min(corner * scale, Math.min(width, height) * .5f - 1));
        params[11] = Math.max(3, 2 * scale);
        params[12] = lighting == null ? 0 : lighting.edgeStrength;
        params[13] = lighting == null ? 0 : lighting.gradStrength;
        params[14] = lighting != null && lighting.depressed ? 1 : 0;
        params[15] = (float) com.aurora.client.theme.ThemeManager.current().backgroundOpacity();
        params[16] = unified ? 1.35f : 1;
        params[17] = unified ? .92f : 1;
        params[20] = BlurPanelRenderer.LIGHT_DIR_X;
        params[21] = BlurPanelRenderer.LIGHT_DIR_Y;
        pass(output, 4, COMPOSITE, c.getTextureView(), result, params);
        graphics.blit(RenderPipelines.GUI_TEXTURED, result.id,
                Math.round(x / (float) scale), Math.round(y / (float) scale), 0f, 0f,
                Math.round(width / (float) scale), Math.round(height / (float) scale),
                width, height, width, height, -1);
        return true;
    }

    private static void ensureTriangle() {
        if (triangle != null) return;
        ByteBuffer vertices = ByteBuffer.allocateDirect(36).order(ByteOrder.nativeOrder());
        vertices.putFloat(-1).putFloat(-1).putFloat(0);
        vertices.putFloat(3).putFloat(-1).putFloat(0);
        vertices.putFloat(-1).putFloat(3).putFloat(0).flip();
        triangle = RenderSystem.getDevice().createBuffer(() -> "Aurora glass triangle",
                GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, vertices);
    }

    static final class SyntheticBackdrop implements AutoCloseable {
        private final Output output = new Output();

        BlurPanelRenderer.CaptureSource render(int width, int height, float scale,
                                               boolean stripes, float slide, float detail) {
            ensureTriangle();
            float[] params = new float[PARAM_BYTES / 4];
            params[4] = slide; params[5] = detail;
            params[8] = width; params[9] = height;
            params[10] = scale; params[11] = stripes ? 1 : 0;
            Target target = target(output, 0, width, height);
            pass(output, 0, PATTERN, Minecraft.getInstance().getMainRenderTarget().getColorTextureView(), target, params);
            return new BlurPanelRenderer.CaptureSource(target.getTextureView(), width, height);
        }

        @Override public void close() {
            if (output.targets[0] != null) retired.add(output.targets[0]::release);
            if (output.uniforms[0] != null) retired.add(output.uniforms[0]);
            output.targets[0] = null; output.uniforms[0] = null; output.previous[0] = null;
        }
    }

    private static void rect(float[] params, float x0, float y0, float x1, float y1) {
        params[0] = x0; params[1] = y0; params[2] = x1; params[3] = y1;
    }

    private static Target target(Output output, int index, int width, int height) {
        Target old = output.targets[index];
        if (old != null && old.width == width && old.height == height && !old.getTexture().isClosed()) return old;
        if (old != null) retired.add(old::release);
        return output.targets[index] = new Target(width, height);
    }

    private static void pass(Output output, int index, RenderPipeline pipeline,
                             GpuTextureView input, Target target, float[] params) {
        // Immutable uniforms per submitted pass: later panels/frames must never
        // overwrite data referenced by Vulkan command buffers still in flight.
        if (!Arrays.equals(output.previous[index], params)) {
            if (output.uniforms[index] != null) retired.add(output.uniforms[index]);
            ByteBuffer data = ByteBuffer.allocateDirect(PARAM_BYTES).order(ByteOrder.nativeOrder());
            for (float value : params) data.putFloat(value);
            data.flip();
            output.uniforms[index] = RenderSystem.getDevice().createBuffer(() -> "Aurora glass params",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, data);
            output.previous[index] = params.clone();
        }
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Aurora glass", target.getTextureView(), OptionalInt.empty())) {
            pass.setPipeline(pipeline);
            pass.setVertexBuffer(0, triangle);
            pass.setUniform("GlassParams", output.uniforms[index]);
            pass.bindTexture("InputTexture", input, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(0, 3);
        }
    }

    static void beginFrame() {
        for (AutoCloseable resource : retired) close(resource);
        retired.clear();
    }

    static void shutdown() {
        beginFrame();
        for (Output output : live) {
            for (Target target : output.targets) if (target != null) target.release();
            for (GpuBuffer buffer : output.uniforms) if (buffer != null) buffer.close();
            Arrays.fill(output.targets, null);
            Arrays.fill(output.uniforms, null);
            Arrays.fill(output.previous, null);
            output.registered = false;
        }
        live.clear();
        if (triangle != null) triangle.close();
        triangle = null;
    }

    private static void close(AutoCloseable resource) {
        try { resource.close(); }
        catch (Exception error) { throw new IllegalStateException("Cannot retire glass resource", error); }
    }
}
