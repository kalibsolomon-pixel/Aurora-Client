package com.aurora.client.ui.util;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/** Experimental single-quad shapes submitted to Minecraft's deferred GUI renderer. */
public final class NativeShapeRenderer {
    private NativeShapeRenderer() {}
    private static final java.util.concurrent.atomic.AtomicLong QUADS = new java.util.concurrent.atomic.AtomicLong();
    public static long quadsSubmitted() { return QUADS.get(); }
    private static final boolean ENABLED = Boolean.getBoolean("aurora.shapes.shader");
    private static final class PipelineHolder {
        static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("aurora", "pipeline/ui_shape"))
            .withVertexShader(Identifier.fromNamespaceAndPath("aurora", "core/ui_shape"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("aurora", "core/ui_shape"))
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .build();
    }

    public static boolean draw(GuiGraphicsExtractor g, float scale, float x, float y, float w, float h,
                               float radius, float thickness, int color, boolean squircle) {
        if (!ENABLED || w <= 0 || h <= 0 || radius <= 0 || scale <= 0) return false;
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(w) || !Float.isFinite(h)
                || !Float.isFinite(radius) || !Float.isFinite(thickness)) return false;
        float pw = w * scale, ph = h * scale;
        float pr = Math.min(radius * scale, Math.min(pw, ph) / 2f);
        float pt = thickness > 0 ? Math.max(1f, thickness * scale) : 0f;
        // Signed-short attributes carry size at 1/8 pixel and radius/stroke at
        // 1/16 pixel; out-of-range cases retain the CPU rasterizer.
        if (pw > 4095f || ph > 4095f || pr > 2047f || pt > 2047f || pr < 1f / 16f
                || (thickness > 0 && pt * 2 >= Math.min(pw, ph))) return false;
        int iw = Math.round(pw * 8), ih = Math.round(ph * 8);
        int ir = Math.round(pr * 16) * (squircle ? -1 : 1), it = Math.round(pt * 16);
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        float pad = ShapeShaderGeometry.padding(scale, pose.m00(), pose.m01(), pose.m10(), pose.m11());
        if (pad <= 0) return false;
        ScreenRectangle scissor = g.scissorStack.peek();
        int bx = (int) Math.floor(x - pad), by = (int) Math.floor(y - pad);
        int br = (int) Math.ceil(x + w + pad), bb = (int) Math.ceil(y + h + pad);
        ScreenRectangle bounds = new ScreenRectangle(bx, by, br - bx, bb - by).transformMaxBounds(pose);
        if (scissor != null) bounds = bounds.intersection(scissor);
        if (bounds != null) {
            g.guiRenderState.addGuiElement(new Shape(pose, x, y, w, h, pad,
                    pw, ph, iw, ih, ir, it, color, scissor, bounds));
            QUADS.incrementAndGet();
        }
        return true;
    }

    private record Shape(Matrix3x2f pose, float x, float y, float w, float h, float pad,
                         float pw, float ph, int iw, int ih, int ir, int it, int color,
                         ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
        @Override public RenderPipeline pipeline() { return PipelineHolder.PIPELINE; }
        @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
        @Override public void buildVertices(VertexConsumer out) {
            float physicalPad = pad * (pw / w);
            vertex(out, x - pad, y - pad, -physicalPad, -physicalPad);
            vertex(out, x - pad, y + h + pad, -physicalPad, ph + physicalPad);
            vertex(out, x + w + pad, y + h + pad, pw + physicalPad, ph + physicalPad);
            vertex(out, x + w + pad, y - pad, pw + physicalPad, -physicalPad);
        }
        private void vertex(VertexConsumer out, float vx, float vy, float u, float v) {
            out.addVertexWith2DPose(pose, vx, vy).setColor(color).setUv(u, v)
                    .setUv1(iw, ih).setUv2(ir, it).setNormal(0, 0, 1);
        }
    }
}
