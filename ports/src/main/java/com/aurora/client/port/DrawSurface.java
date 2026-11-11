package com.aurora.client.port;

/** Receives non-overlapping ARGB coverage rectangles in physical pixels.
 * The caller owns transforms, batching and lifetime; implementations never retain game objects here. */
@FunctionalInterface
public interface DrawSurface {
    void rect(int x1, int y1, int x2, int y2, int argb);
}
