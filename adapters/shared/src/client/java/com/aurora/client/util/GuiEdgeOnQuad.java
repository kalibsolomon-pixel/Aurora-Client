package com.aurora.client.util;

/**
 * GUI Flat Faces (ADR 0009). Added to {@code BakedQuad} by
 * {@code BakedQuadEdgeOnMixin}: which poses leave the quad with no area on
 * screen ({@link GuiFlatFaces#zeroAreaUnder}), computed the first time a GUI
 * atlas drawing asks.
 */
public interface GuiEdgeOnQuad {
    /** {@link GuiFlatFaces#zeroAreaUnder} for this quad's corners, with {@link GuiFlatFaces#COMPUTED} set. */
    int aurora$zeroAreaUnder();
}
