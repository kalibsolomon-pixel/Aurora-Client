package com.aurora.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import java.util.Arrays;

/** Immediate world overlays through 26.2's staged vertex and prepared render APIs. */
public final class WorldGeometryBuffers implements AutoCloseable {
    private static WorldGeometryBuffers instance;
    private final StagedVertexBuffer vertices = new StagedVertexBuffer(() -> "Aurora world overlays", 65536);
    /**
     * This frame's batches in first-use order, in reused slots: a frame uses one to three
     * render types, so a scan beats a map that re-allocates its entries every frame.
     * {@code prepare()} still runs once per type per frame; it captures the current
     * model-view, scissor and textures.
     */
    private RenderType[] types = new RenderType[4];
    private PreparedRenderType[] prepared = new PreparedRenderType[4];
    private StagedVertexBuffer.Draw[] draws = new StagedVertexBuffer.Draw[4];
    private int used;

    static WorldGeometryBuffers begin() {
        if (instance == null) instance = new WorldGeometryBuffers();
        return instance;
    }

    VertexConsumer getBuffer(RenderType type) {
        for (int i = 0; i < used; i++) {
            if (types[i] == type) return vertices.getVertexBuilder(draws[i]);
        }
        if (used == types.length) {
            types = Arrays.copyOf(types, used * 2);
            prepared = Arrays.copyOf(prepared, used * 2);
            draws = Arrays.copyOf(draws, used * 2);
        }
        PreparedRenderType ready = type.prepare();
        StagedVertexBuffer.Draw draw = vertices.appendDraw(type.format(), type.primitiveTopology(),
                type.sortOnUpload() ? RenderSystem.getProjectionType().vertexSorting() : null);
        types[used] = type;
        prepared[used] = ready;
        draws[used++] = draw;
        return vertices.getVertexBuilder(draw);
    }

    @Override public void close() {
        if (used == 0) return;
        try {
            vertices.upload();
            for (int i = 0; i < used; i++) {
                var info = vertices.getExecuteInfo(draws[i]);
                if (info != null) prepared[i].drawFromBuffer(info);
            }
        } finally {
            vertices.endDraw();
            // Drop this frame's references; the slots themselves are kept.
            Arrays.fill(types, 0, used, null);
            Arrays.fill(prepared, 0, used, null);
            Arrays.fill(draws, 0, used, null);
            used = 0;
        }
    }

    /** Recycles the staged GPU pools once per frame, from the frame-head hook inside Aurora's timing bracket. */
    public static void frameEnd() {
        if (instance != null) instance.vertices.endFrame();
    }

    public static void shutdown() {
        if (instance != null) instance.vertices.close();
        instance = null;
    }
}
