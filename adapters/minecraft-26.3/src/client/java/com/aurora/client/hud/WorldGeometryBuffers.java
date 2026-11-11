package com.aurora.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import java.util.LinkedHashMap;

/** Immediate world overlays through the native staged vertex and render-pass APIs. */
public final class WorldGeometryBuffers implements AutoCloseable {
    private static WorldGeometryBuffers instance;
    private final StagedVertexBuffer vertices = new StagedVertexBuffer(() -> "Aurora world overlays", 16384);
    private final LinkedHashMap<RenderType, Batch> batches = new LinkedHashMap<>();

    static WorldGeometryBuffers begin() {
        if (instance == null) instance = new WorldGeometryBuffers();
        return instance;
    }

    VertexConsumer getBuffer(RenderType type) {
        Batch batch = batches.computeIfAbsent(type, key -> new Batch(key.prepare(), vertices.appendDraw(
                key.format(), key.primitiveTopology(), key.sortOnUpload() ? RenderSystem.getProjectionType().vertexSorting() : null)));
        return vertices.getVertexBuilder(batch.draw());
    }

    @Override public void close() {
        try {
            vertices.upload();
            if (!batches.isEmpty()) {
                var target = net.minecraft.client.Minecraft.getInstance().gameRenderer.mainRenderTarget();
                try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "Aurora world overlays", target.getColorTextureView(), java.util.Optional.empty(),
                        target.getDepthTextureView(), java.util.OptionalDouble.empty())) {
                    for (Batch batch : batches.values()) {
                        var info = vertices.getExecuteInfo(batch.draw());
                        if (info != null) batch.type().drawFromBuffer(info, pass);
                    }
                }
            }
        } finally {
            vertices.endFrame();
            batches.clear();
        }
    }

    public static void shutdown() {
        if (instance != null) instance.vertices.close();
        instance = null;
    }

    private record Batch(PreparedRenderType type, StagedVertexBuffer.Draw draw) {}
}
