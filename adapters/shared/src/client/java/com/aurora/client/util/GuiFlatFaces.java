package com.aurora.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * GUI Flat Faces (ADR 0009): while the GUI item atlas draws a slot, a quad
 * that would cover no pixel is not submitted. The side faces of flat
 * (generated) item models are such quads: seen head-on, the corners of each
 * meet in pairs. They are most of an atlas drawing's quads.
 *
 * <p>The test is exact, not an angle threshold. Pair a quad's corners 0-1
 * with 2-3, or 0-3 with 1-2; either way, both of its triangles have two
 * paired corners, whichever diagonal splits it. A pair lands on one screen
 * point when every model axis on which its corners differ has a weight of
 * exactly zero in the pose's screen x and y rows: the transform adds
 * {@code 0 * coordinate} for that axis, so both corners get equal x and y.
 * The atlas's orthographic projection, and a model view checked by
 * {@link #keepsDepthOut}, keep model depth out of clip x, y and w, so the GPU
 * maps the two corners to one point too, and a triangle with two equal
 * corners has no area and produces no fragment. Rotated or 3D items fail the
 * zero test in float and keep every quad.
 *
 * <p>That is the exact form of "the normal is perpendicular to the view": a
 * test on the transformed normal could only be an angle threshold, and a quad
 * a hair off edge-on still covers a sliver of pixels. Here a weight of 1e-20
 * keeps the quad.
 */
public final class GuiFlatFaces {
    private GuiFlatFaces() {}

    /** Set on a computed {@link #zeroAreaUnder} table, so 0 can mean "not computed yet". */
    public static final int COMPUTED = 1 << 8;

    /** Set while the GUI item atlas draws a slot with the gate on. */
    private static boolean active;
    /** The pose {@link #lastAxes} belongs to; item submits hold their own copy of the pose. */
    private static @Nullable Object lastPose;
    private static int lastAxes;

    public static void beginAtlasDraw(boolean on) {
        active = on;
        lastPose = null;
    }

    public static void endAtlasDraw() {
        active = false;
        lastPose = null;
    }

    /** Whether the atlas drawing in progress may leave out {@code quad}, drawn with {@code pose}: it covers no pixel. */
    public static boolean skip(PoseStack.Pose pose, Object quad) {
        if (!active || !(quad instanceof GuiEdgeOnQuad edgeOn)) return false;
        if (pose != lastPose) {
            lastPose = pose;
            lastAxes = collapsedAxes(pose.pose());
        }
        return lastAxes >= 0 && (edgeOn.aurora$zeroAreaUnder() >>> lastAxes & 1) != 0;
    }

    /**
     * Bit k set (x 0, y 1, z 2): model axis k has no weight in the pose's
     * screen x and y. -1 for a pose that is not finite, whose output nothing
     * can vouch for.
     */
    public static int collapsedAxes(Matrix4fc pose) {
        if (!pose.isFinite()) return -1;
        return (pose.m00() == 0.0F && pose.m01() == 0.0F ? 1 : 0)
                | (pose.m10() == 0.0F && pose.m11() == 0.0F ? 2 : 0)
                | (pose.m20() == 0.0F && pose.m21() == 0.0F ? 4 : 0);
    }

    /** Whether the model view gives model depth no weight in clip x, y and w. */
    public static boolean keepsDepthOut(Matrix4fc modelView) {
        return modelView.isFinite() && modelView.m20() == 0.0F && modelView.m21() == 0.0F && modelView.m23() == 0.0F;
    }

    /**
     * Bit c (0 to 7) set: the quad covers no pixel under a pose whose
     * {@link #collapsedAxes} are c. 0 when a corner is not finite.
     */
    public static int zeroAreaUnder(Vector3fc p0, Vector3fc p1, Vector3fc p2, Vector3fc p3) {
        if (!p0.isFinite() || !p1.isFinite() || !p2.isFinite() || !p3.isFinite()) return 0;
        int pairsA = differing(p0, p1) | differing(p2, p3);
        int pairsB = differing(p0, p3) | differing(p1, p2);
        int table = 0;
        for (int collapsed = 0; collapsed < 8; collapsed++) {
            if ((pairsA & ~collapsed) == 0 || (pairsB & ~collapsed) == 0) table |= 1 << collapsed;
        }
        return table;
    }

    private static int differing(Vector3fc a, Vector3fc b) {
        return (a.x() != b.x() ? 1 : 0) | (a.y() != b.y() ? 2 : 0) | (a.z() != b.z() ? 4 : 0);
    }
}
