package com.aurora.client.render;

/**
 * Per-texture OpenGL mip-range cache ({@code perfGlDispatch}, ADR 0009).
 * Implemented by {@code GlTextureMipCacheMixin} on each adapter's {@code GlTexture}
 * so {@code GlCommandEncoder} skips redundant {@code GL_TEXTURE_BASE_LEVEL} and
 * {@code GL_TEXTURE_MAX_LEVEL} {@code glTexParameteri} driver calls when a texture's
 * mip range has not changed.
 */
public interface GlTextureMipCache {
    /** Records {@code baseMip} and returns {@code true} if it differed from the last bound value. */
    boolean aurora$updateBaseMip(int baseMip);

    /** Records {@code maxMip} and returns {@code true} if it differed from the last bound value. */
    boolean aurora$updateMaxMip(int maxMip);
}
