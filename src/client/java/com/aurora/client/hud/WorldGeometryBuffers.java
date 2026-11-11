package com.aurora.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import java.util.LinkedHashMap;

/** Immediate world overlays through 26.2's staged vertex and prepared render APIs. */
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
            for (Batch batch : batches.values()) {
                var info = vertices.getExecuteInfo(batch.draw());
                if (info != null) batch.type().drawFromBuffer(info);
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
