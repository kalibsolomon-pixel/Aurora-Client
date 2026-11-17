package com.aurora.client.ui.text;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4fc;

/** Selects SDF only for GUI rendering; world text and every failure use the raster delegate. */
final class SdfRenderable implements TextRenderable.Styled {
    private final TextRenderable.Styled sdf;
    private final TextRenderable.Styled raster;
    private boolean gui;

    SdfRenderable(TextRenderable.Styled sdf, TextRenderable.Styled raster) {
        this.sdf = sdf;
        this.raster = raster;
    }

    private TextRenderable.Styled active() {
        return gui && SdfTextSupport.pipelineAvailable() ? sdf : raster;
    }

    @Override public RenderPipeline guiPipeline() {
        gui = true;
        return active().guiPipeline();
    }

    @Override public RenderType renderType(Font.DisplayMode displayMode) {
        gui = false;
        return raster.renderType(displayMode);
    }

    @Override public GpuTextureView textureView() { return active().textureView(); }

    @Override public void render(Matrix4fc pose, VertexConsumer consumer, int light, boolean noDepth) {
        active().render(pose, consumer, light, noDepth);
    }

    // Bounds deliberately stay raster: SDF spread and transparent padding are not visible ink.
    @Override public float left() { return raster.left(); }
    @Override public float top() { return raster.top(); }
    @Override public float right() { return raster.right(); }
    @Override public float bottom() { return raster.bottom(); }
    @Override public net.minecraft.network.chat.Style style() { return raster.style(); }
    @Override public float activeLeft() { return raster.activeLeft(); }
    @Override public float activeTop() { return raster.activeTop(); }
    @Override public float activeRight() { return raster.activeRight(); }
    @Override public float activeBottom() { return raster.activeBottom(); }
}
