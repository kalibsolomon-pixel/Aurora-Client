package com.aurora.client.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * GUI Glint Baking (ADR 0009), one per GUI item atlas: which glint transform
 * each glint-only slot was drawn with. Between frames, the glint pass's only
 * changing inputs are that transform (from the clock and the glint speed),
 * the glint strength, the glint texture and which glint render type draws it
 * (Glint Color), so a slot drawn with the current transform and the same
 * other inputs already shows what a redraw would. The transform moves at most
 * once a millisecond, so above 1,000 frames a second many frames reuse the
 * drawing. At strength 0 the glint pass changes no pixel (vanilla's adds
 * black, Glint Color's masked pass discards), so the transform does not
 * matter at all ({@link #INVISIBLE}).
 *
 * <p>A drawing is recorded only when the transform was the same before and
 * after it, so it holds one transform throughout. Like vanilla, an item is
 * drawn at most once a frame. Client thread only.
 */
public final class GlintBakeTracker {
    /** The key for every drawing made at strength 0. */
    public static final Matrix4fc INVISIBLE = new Matrix4f().zero();

    private final Map<Object, Matrix4f> drawnWith = new HashMap<>();
    private final Set<Object> seenThisFrame = new HashSet<>();
    private double strength = Double.NaN;
    private int style = Integer.MIN_VALUE;
    private boolean settling;
    private int seenTexture;

    /** Bumped when the item glint texture is (re)loaded: a resource reload or Glint Color. */
    private static int textureGeneration;

    public static void glintTextureChanged() { textureGeneration++; }

    /**
     * Forgets every drawing when the glint strength, render type or texture
     * changed. The strength in the shader is copied at the start of the frame,
     * so the next frame forgets them again in case the change came after
     * that copy.
     */
    public void inputs(double strengthNow, int styleNow) {
        if (seenTexture != textureGeneration || styleNow != style) {
            seenTexture = textureGeneration;
            style = styleNow;
            drawnWith.clear();
        }
        if (Double.compare(strengthNow, strength) == 0) return;
        // The first reading has no earlier drawings to doubt.
        settling = !Double.isNaN(strength);
        strength = strengthNow;
        drawnWith.clear();
    }

    /**
     * Whether this is the item's first look-up this frame. Later look-ups
     * reuse the first one's drawing, as vanilla's do.
     */
    public boolean firstThisFrame(Object item) {
        return seenThisFrame.add(item);
    }

    /** Whether a slot this item already holds must be drawn again for this transform. */
    public boolean stale(Object item, Matrix4fc now) {
        return now == null || !now.equals(drawnWith.get(item));
    }

    /** Records a drawing made between {@code before} and {@code after}. */
    public void drawn(Object item, Matrix4fc before, Matrix4fc after) {
        if (before != null && before.equals(after)) {
            drawnWith.computeIfAbsent(item, k -> new Matrix4f()).set(before);
        } else {
            drawnWith.remove(item);
        }
    }

    /** End of the frame: {@code used} is every key that still holds a slot. */
    public void endFrame(Set<?> used) {
        seenThisFrame.clear();
        if (settling) {
            settling = false;
            drawnWith.clear();
        }
        if (drawnWith.size() > 64 && drawnWith.size() > 2 * used.size()) {
            drawnWith.keySet().retainAll(used);
        }
    }

    /** Test hook: whether a drawing is recorded for this item. */
    boolean recorded(Object item) {
        return drawnWith.containsKey(Objects.requireNonNull(item));
    }
}
