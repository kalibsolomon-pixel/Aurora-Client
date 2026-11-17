package com.aurora.client.ui.util;

/** Optical placement in GUI pixels. Draw origins are Minecraft text y, not typographic baselines. */
public final class TextAlignment {
    private TextAlignment() {}

    /** One spacing unit between descriptor lines and between a label and its descriptor. */
    public static final int DESCRIPTOR_GAP = 2;
    /** Clears both the widest rounded bevel (4px) and square facet (2.25px). */
    public static final int FACE_PAD = 5;

    /** Actual cap/x-height glyph bounds relative to a Minecraft draw origin. Descenders are excluded. */
    public record Ink(float top, float bottom) {
        public Ink {
            if (!Float.isFinite(top) || !Float.isFinite(bottom) || bottom <= top)
                throw new IllegalArgumentException("Invalid ink bounds");
        }
        public float center() { return (top + bottom) / 2; }
        public float height() { return bottom - top; }
    }

    public static int textY(float y, float height, Ink ink) {
        return Math.round(y + height / 2 - ink.center());
    }

    /** First origin of an optically centred multiline block; pitch is shared with descriptors. */
    public static int blockY(float y, float height, int lines, Ink ink, int pitch) {
        return Math.round(y + height / 2 - ink.center() - Math.max(0, lines - 1) * pitch / 2f);
    }

    public static float iconCenter(float y, float height) { return y + height / 2; }
    public static int centeredX(float x, float width, int drawnWidth) {
        return Math.round(x + (width - drawnWidth) / 2);
    }

    /** Text area excludes a trailing glyph slot; identical padding on the area's two sides. */
    public static int faceTextWidth(int width, int trailingSlot) {
        return Math.max(0, width - trailingSlot - 2 * FACE_PAD);
    }
}
