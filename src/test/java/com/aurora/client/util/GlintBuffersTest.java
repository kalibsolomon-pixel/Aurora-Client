package com.aurora.client.util;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import static org.junit.jupiter.api.Assertions.*;

class GlintBuffersTest {
    private static RenderType type(String namespace, String path) {
        var pipeline = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath(namespace, path))
                .withVertexShader("core/glint").withFragmentShader("core/glint")
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS).build();
        return RenderType.create(path, RenderSetup.builder(pipeline).createRenderSetup());
    }

    private static class TrackingSource extends MultiBufferSource.BufferSource {
        final List<RenderType> flushed = new ArrayList<>();
        TrackingSource(ByteBufferBuilder shared, SequencedMap<RenderType, ByteBufferBuilder> fixed) { super(shared, fixed); }
        @Override public void endBatch(RenderType type) { flushed.add(type); startedBuilders.remove(type); }
    }

    @Test void foilBufferSurvivesRequestingTheBaseItemBuffer() {
        var glint = type("aurora", "pipeline/masked_glint");
        var base = type("minecraft", "pipeline/item");
        try (var shared = new ByteBufferBuilder(256)) {
            var broken = new TrackingSource(shared, new LinkedHashMap<>());
            broken.getBuffer(glint); broken.getBuffer(base);
            assertEquals(List.of(glint), broken.flushed, "Unfixed glint flushes before base depth exists");
            var fixed = new LinkedHashMap<RenderType, ByteBufferBuilder>();
            GlintBuffers.ensureFixed(fixed, glint);
            try {
                var source = new TrackingSource(shared, fixed);
                source.getBuffer(glint); source.getBuffer(base);
                assertTrue(source.flushed.isEmpty(), "Glint must wait for endBatch after base");
                source.endBatch();
                assertEquals(List.of(base, glint), source.flushed);
            } finally { fixed.values().forEach(ByteBufferBuilder::close); }
        }
    }

    @Test void vanillaAndMaskedTypesRemainFixedAcrossToggleChanges() {
        var original = type("minecraft", "pipeline/glint");
        var masked = type("aurora", "pipeline/masked_glint");
        var base = type("minecraft", "pipeline/item");
        var fixed = new LinkedHashMap<RenderType, ByteBufferBuilder>();
        try {
            GlintBuffers.ensureFixed(fixed, masked);
            GlintBuffers.ensureFixed(fixed, original);
            var buffer = fixed.get(masked);
            GlintBuffers.ensureFixed(fixed, masked);
            GlintBuffers.ensureFixed(fixed, base);
            assertEquals(2, fixed.size()); assertSame(buffer, fixed.get(masked));
            assertEquals(List.of(masked, original), new ArrayList<>(fixed.keySet()));
        } finally { fixed.values().forEach(ByteBufferBuilder::close); }
    }
}
