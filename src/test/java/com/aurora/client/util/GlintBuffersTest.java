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

    /**
     * Mimics ImmediatelyFast's BatchableBufferSource: getBuffer is overridden
     * without calling super (so no lazy hook runs) and non-fixed types are
     * batched by first use, ahead of every fixed buffer.
     */
    private static class FirstUseBatchingSource extends MultiBufferSource.BufferSource {
        final List<RenderType> drawn = new ArrayList<>();
        private final java.util.Set<RenderType> dynamic = new java.util.LinkedHashSet<>();
        FirstUseBatchingSource(ByteBufferBuilder shared, SequencedMap<RenderType, ByteBufferBuilder> fixed) { super(shared, fixed); }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer getBuffer(RenderType type) {
            if (!fixedBuffers.containsKey(type)) dynamic.add(type);
            return null;
        }
        @Override public void endBatch() {
            drawn.addAll(dynamic);
            for (RenderType type : fixedBuffers.keySet()) if (!dynamic.contains(type)) drawn.add(type);
        }
    }

    @Test void alternativeGlintDrawsAfterItsBaseInAnOverridingBatchingSource() {
        var vanilla = type("minecraft", "pipeline/glint");
        var masked = type("aurora", "pipeline/masked_glint");
        var base = type("minecraft", "pipeline/item");
        var sheet = type("minecraft", "pipeline/sheet");
        try (var shared = new ByteBufferBuilder(256)) {
            // Regression: the level source registers only the vanilla foil types, so the
            // masked glint (requested first by getFoilBuffer) was batched before its item.
            var unpaired = new LinkedHashMap<RenderType, ByteBufferBuilder>();
            unpaired.put(sheet, new ByteBufferBuilder(64)); unpaired.put(vanilla, new ByteBufferBuilder(64));
            var broken = new FirstUseBatchingSource(shared, unpaired);
            broken.getBuffer(masked); broken.getBuffer(base); broken.endBatch();
            assertTrue(broken.drawn.indexOf(masked) < broken.drawn.indexOf(base), "reproduces the dropped glint");

            List<ByteBufferBuilder> added = new ArrayList<>();
            var paired = GlintBuffers.withCounterparts(unpaired, java.util.Map.of(vanilla, masked, masked, vanilla), added::add);
            try {
                assertEquals(List.of(sheet, vanilla, masked), new ArrayList<>(paired.keySet()), "counterpart sits next to its partner");
                assertEquals(1, added.size());
                var fixed = new FirstUseBatchingSource(shared, paired);
                fixed.getBuffer(masked); fixed.getBuffer(base); fixed.endBatch();
                assertTrue(fixed.drawn.indexOf(base) < fixed.drawn.indexOf(masked), "glint waits for the base depth");
                assertSame(paired, GlintBuffers.withCounterparts(paired, java.util.Map.of(vanilla, masked, masked, vanilla),
                        b -> fail("complete maps allocate nothing")));
            } finally { added.forEach(ByteBufferBuilder::close); }
            assertSame(unpaired, GlintBuffers.withCounterparts(unpaired, java.util.Map.of(), b -> fail("no foil, no change")));
            unpaired.values().forEach(ByteBufferBuilder::close);
        }
    }
}
