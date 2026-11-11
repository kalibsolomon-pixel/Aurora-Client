package com.aurora.client.util;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.rendertype.RenderType;
import java.util.SequencedMap;

/** Foil geometry must survive getBuffer(base) until the base pass has written depth. */
public final class GlintBuffers {
    private GlintBuffers() {}

    public static boolean isGlint(RenderType type) {
        var location = type.pipeline().getLocation();
        return location.getNamespace().equals("aurora") && location.getPath().equals("pipeline/masked_glint")
                || location.getNamespace().equals("minecraft") && location.getPath().equals("pipeline/glint");
    }

    public static ByteBufferBuilder ensureFixed(SequencedMap<RenderType, ByteBufferBuilder> buffers, RenderType type) {
        if (!isGlint(type) || buffers.containsKey(type)) return null;
        ByteBufferBuilder buffer = new ByteBufferBuilder(type.bufferSize());
        buffers.put(type, buffer);
        return buffer;
    }

    /**
     * Construction-time pairing. Batching buffer sources (ImmediatelyFast's
     * {@code BatchableBufferSource}) override {@code getBuffer} without calling
     * super, so the lazy {@link #ensureFixed} hook never runs there and an
     * unregistered foil type is batched by first use — before its base item,
     * which fails the EQUAL depth test and drops the glint. Every source that
     * registers a foil type as fixed therefore also gets that type's counterpart
     * (vanilla ↔ Aurora masked), inserted right after it so the established
     * flush order holds for both modes.
     *
     * @return {@code fixed} itself when nothing is missing, otherwise a new
     *         ordered map; each added buffer is reported to {@code allocated}
     */
    public static SequencedMap<RenderType, ByteBufferBuilder> withCounterparts(
            SequencedMap<RenderType, ByteBufferBuilder> fixed, java.util.Map<RenderType, RenderType> counterparts,
            java.util.function.Consumer<ByteBufferBuilder> allocated) {
        boolean missing = false;
        for (RenderType type : fixed.keySet()) {
            RenderType partner = counterparts.get(type);
            if (partner != null && !fixed.containsKey(partner)) { missing = true; break; }
        }
        if (!missing) return fixed;
        SequencedMap<RenderType, ByteBufferBuilder> paired = new java.util.LinkedHashMap<>();
        for (var entry : fixed.entrySet()) {
            paired.put(entry.getKey(), entry.getValue());
            RenderType partner = counterparts.get(entry.getKey());
            if (partner == null || fixed.containsKey(partner) || paired.containsKey(partner)) continue;
            ByteBufferBuilder buffer = new ByteBufferBuilder(partner.bufferSize());
            paired.put(partner, buffer);
            allocated.accept(buffer);
        }
        return paired;
    }

    private static final java.lang.ref.Cleaner CLEANER = java.lang.ref.Cleaner.create();

    /** A temporary BufferSource must not leak the additional native allocations. */
    public static BufferOwner owner(Object source) {
        BufferOwner owner = new BufferOwner();
        CLEANER.register(source, owner);
        return owner;
    }

    public static final class BufferOwner implements Runnable {
        private final java.util.List<ByteBufferBuilder> buffers = new java.util.ArrayList<>();
        public void add(ByteBufferBuilder buffer) { if (buffer != null) buffers.add(buffer); }
        @Override public void run() { buffers.forEach(ByteBufferBuilder::close); }
    }
}
