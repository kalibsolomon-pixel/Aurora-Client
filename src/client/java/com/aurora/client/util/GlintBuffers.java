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
