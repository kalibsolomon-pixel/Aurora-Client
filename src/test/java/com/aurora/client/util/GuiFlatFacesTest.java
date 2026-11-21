package com.aurora.client.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class GuiFlatFacesTest {
    private static final float MIN_Z = 7.5F / 16.0F;
    private static final float MAX_Z = 8.5F / 16.0F;

    /** The corners vanilla's FaceBakery gives a flat item's top edge of pixel (x, y) of a 16x16 sprite. */
    private static Vector3f[] topEdge(int x, int y) {
        float x0 = x / 16.0F, x1 = (x + 1) / 16.0F, top = (16 - y) / 16.0F;
        return new Vector3f[]{new Vector3f(x0, top, MIN_Z), new Vector3f(x0, top, MAX_Z),
                new Vector3f(x1, top, MAX_Z), new Vector3f(x1, top, MIN_Z)};
    }

    private static Vector3f[] leftEdge(int x, int y) {
        float left = x / 16.0F, y0 = (16 - y - 1) / 16.0F, y1 = (16 - y) / 16.0F;
        return new Vector3f[]{new Vector3f(left, y1, MIN_Z), new Vector3f(left, y0, MIN_Z),
                new Vector3f(left, y0, MAX_Z), new Vector3f(left, y1, MAX_Z)};
    }

    private static Vector3f[] front() {
        return new Vector3f[]{new Vector3f(0, 1, MAX_Z), new Vector3f(0, 0, MAX_Z),
                new Vector3f(1, 0, MAX_Z), new Vector3f(1, 1, MAX_Z)};
    }

    /** The pose GuiItemAtlas.drawToSlot and ItemStackRenderState give a GUI item, slot (3, 5) of 64 px slots. */
    private static Matrix4f guiPose(Matrix4f display) {
        return new Matrix4f().translate(3 * 64 + 32, 5 * 64 + 32, 0).scale(64, -64, 64)
                .mul(display).translate(-0.5F, -0.5F, -0.5F);
    }

    /** An item model's display transform, as ItemTransform.apply builds it. */
    private static Matrix4f display(float rx, float ry, float rz, float scale) {
        Quaternionf rotation = new Quaternionf().rotationXYZ(
                (float) Math.toRadians(rx), (float) Math.toRadians(ry), (float) Math.toRadians(rz));
        return new Matrix4f().rotate(rotation).scale(scale);
    }

    private static boolean skipped(Matrix4f pose, Vector3f[] q) {
        int axes = GuiFlatFaces.collapsedAxes(pose);
        return axes >= 0 && (GuiFlatFaces.zeroAreaUnder(q[0], q[1], q[2], q[3]) >>> axes & 1) != 0;
    }

    /** Screen x and y of each corner, through the same JOML transform putBakedQuad uses. */
    private static float[][] screen(Matrix4f pose, Vector3f[] q) {
        float[][] out = new float[4][];
        for (int i = 0; i < 4; i++) {
            Vector3f p = pose.transformPosition(q[i], new Vector3f());
            out[i] = new float[]{p.x, p.y};
        }
        return out;
    }

    private static boolean same(float[] a, float[] b) {
        return a[0] == b[0] && a[1] == b[1];
    }

    /** Both triangles of either split have two equal corners: no arithmetic can give them area. */
    private static boolean cornersMeetInPairs(Matrix4f pose, Vector3f[] q) {
        float[][] s = screen(pose, q);
        return same(s[0], s[1]) && same(s[2], s[3]) || same(s[0], s[3]) && same(s[1], s[2]);
    }

    private static double area(float[][] s) {
        double a = 0;
        for (int i = 0; i < 4; i++) {
            float[] p = s[i], n = s[(i + 1) % 4];
            a += (double) p[0] * n[1] - (double) n[0] * p[1];
        }
        return Math.abs(a / 2);
    }

    @Test
    void aFlatItemsSideFacesAreSkippedHeadOnAndItsFrontIsNot() {
        Matrix4f pose = guiPose(new Matrix4f()); // item/generated has no gui display entry
        assertEquals(4, GuiFlatFaces.collapsedAxes(pose), "only model depth drops out");
        for (Vector3f[] side : List.of(topEdge(4, 2), topEdge(15, 0), leftEdge(0, 7), leftEdge(9, 15))) {
            assertTrue(skipped(pose, side));
            assertTrue(cornersMeetInPairs(pose, side));
        }
        assertFalse(skipped(pose, front()));
        assertEquals(64.0 * 64.0, area(screen(pose, front())), 1e-3);
    }

    @Test
    void aBlockItemKeepsEveryFace() {
        Matrix4f pose = guiPose(display(30, 225, 0, 0.625F)); // block/block's gui transform
        assertEquals(0, GuiFlatFaces.collapsedAxes(pose));
        for (Vector3f[] face : List.of(topEdge(4, 2), leftEdge(0, 7), front())) {
            assertFalse(skipped(pose, face));
        }
    }

    @Test
    void aTurnAboutTheViewAxisKeepsSideFacesEdgeOn() {
        Matrix4f pose = guiPose(display(0, 0, 45, 1));
        assertEquals(4, GuiFlatFaces.collapsedAxes(pose));
        assertTrue(skipped(pose, topEdge(4, 2)));
        assertTrue(cornersMeetInPairs(pose, topEdge(4, 2)));
        assertFalse(skipped(pose, front()));
    }

    @Test
    void aQuarterTurnFlattensTheFacesItTurnsEdgeOn() {
        // JOML builds this turn exactly (w * w - y * y is 0), so model x drops
        // out: the front and the top and bottom edges are edge-on, the left and
        // right edges now face the view.
        Matrix4f pose = guiPose(display(0, 90, 0, 1));
        assertEquals(1, GuiFlatFaces.collapsedAxes(pose));
        for (Vector3f[] edgeOn : List.of(front(), topEdge(4, 2))) {
            assertTrue(skipped(pose, edgeOn));
            assertTrue(cornersMeetInPairs(pose, edgeOn));
        }
        assertFalse(skipped(pose, leftEdge(0, 7)));
        assertEquals(16.0, area(screen(pose, leftEdge(0, 7))), 1e-3);
    }

    @Test
    void aTurnThatLeavesDepthATinyWeightKeepsEveryFace() {
        // The quaternion half turn ItemTransform builds is exact (JOML takes
        // the cosine from the sine), so its side faces are edge-on ...
        Matrix4f exact = guiPose(display(0, 180, 0, 1));
        assertEquals(4, GuiFlatFaces.collapsedAxes(exact));
        assertTrue(skipped(exact, topEdge(4, 2)));
        assertTrue(cornersMeetInPairs(exact, topEdge(4, 2)));
        // ... but a matrix half turn gives depth a weight of sin(pi) in float,
        // about 1e-7. Nothing then vouches for the side faces, so they are kept.
        Matrix4f pose = guiPose(new Matrix4f().rotateY((float) Math.PI));
        assertEquals(0, GuiFlatFaces.collapsedAxes(pose));
        assertTrue(pose.m20() != 0.0F);
        for (Vector3f[] face : List.of(front(), topEdge(4, 2), leftEdge(0, 7))) {
            assertFalse(skipped(pose, face));
        }
    }

    @Test
    void aTiltThatGivesDepthAnyWeightKeepsTheFaceItShowsAsASliver() {
        // Turning a flat item by a hair about the horizontal axis gives model
        // depth a weight of sin(angle) in screen y: its top edge projects to a
        // sliver, which may cover pixels, so it must be drawn. No tilt however
        // small (down to the smallest float) is rounded to "edge-on".
        for (float degrees : new float[]{45, 1, 0.01F, 1e-4F, 1e-6F}) {
            Matrix4f pose = guiPose(display(degrees, 0, 0, 1));
            assertEquals(pose.m21() == 0.0F ? 4 : 0, GuiFlatFaces.collapsedAxes(pose) & 4, "tilt " + degrees);
            if (pose.m21() != 0.0F) {
                for (Vector3f[] face : List.of(topEdge(4, 2), topEdge(0, 15), leftEdge(9, 1))) {
                    assertFalse(skipped(pose, face), "tilt " + degrees);
                }
            }
        }
        Matrix4f smallest = guiPose(new Matrix4f());
        smallest.m21(Float.MIN_VALUE);
        assertEquals(0, GuiFlatFaces.collapsedAxes(smallest) & 4);
        assertFalse(skipped(smallest, topEdge(4, 2)));
        // The same for a turn about a diagonal axis: depth leaks into x and y.
        Matrix4f diagonal = guiPose(new Matrix4f().rotate((float) Math.toRadians(5), 0.70710677F, 0.70710677F, 0));
        assertEquals(0, GuiFlatFaces.collapsedAxes(diagonal));
        for (Vector3f[] face : List.of(topEdge(4, 2), leftEdge(9, 1), front())) {
            assertFalse(skipped(diagonal, face));
        }
    }

    @Test
    void aNegativeZeroWeightCountsAsZero() {
        // -0 compares equal to 0 and adds nothing: the model's mirrored y axis
        // (scale(64, -64, 64)) produces such entries.
        Matrix4f pose = guiPose(new Matrix4f());
        pose.m20(-0.0F).m21(-0.0F);
        assertEquals(4, GuiFlatFaces.collapsedAxes(pose));
        assertTrue(skipped(pose, topEdge(4, 2)));
        assertTrue(cornersMeetInPairs(pose, topEdge(4, 2)));
    }

    @Test
    void aZeroScaleFlattensEveryFace() {
        Matrix4f pose = guiPose(new Matrix4f().scale(0, 1, 1));
        assertEquals(1 | 4, GuiFlatFaces.collapsedAxes(pose));
        assertTrue(skipped(pose, front()));
        assertTrue(cornersMeetInPairs(pose, front()));
        assertTrue(skipped(pose, topEdge(4, 2)));
    }

    @Test
    void nothingIsSkippedForANonFinitePoseOrCorner() {
        Matrix4f pose = guiPose(new Matrix4f());
        pose.m30(Float.NaN);
        assertEquals(-1, GuiFlatFaces.collapsedAxes(pose));
        Vector3f[] side = topEdge(4, 2);
        side[1].z = Float.POSITIVE_INFINITY;
        assertEquals(0, GuiFlatFaces.zeroAreaUnder(side[0], side[1], side[2], side[3]));
    }

    @Test
    void aSkippedQuadAlwaysHasPairedCornersOnScreen() {
        // Random axis-aligned faces of random boxes under random GUI transforms
        // built from whole-degree turns and scales: every skip must be exact,
        // and every axis-aligned face seen exactly edge-on must be skipped.
        Random random = new Random(20261120);
        float[] angles = {0, 90, 180, 270, 45, 30, 225, -90};
        int skips = 0;
        for (int i = 0; i < 4000; i++) {
            Matrix4f pose = guiPose(display(angles[random.nextInt(angles.length)] * (random.nextInt(3) == 0 ? 1 : 0),
                    angles[random.nextInt(angles.length)] * (random.nextInt(3) == 0 ? 1 : 0),
                    angles[random.nextInt(angles.length)], 0.25F + random.nextInt(8) / 4.0F));
            for (Vector3f[] face : boxFaces(random)) {
                if (skipped(pose, face)) {
                    skips++;
                    assertTrue(cornersMeetInPairs(pose, face), "skipped a quad with area");
                    assertEquals(0.0, area(screen(pose, face)));
                } else if (cornersMeetInPairs(pose, face) && GuiFlatFaces.collapsedAxes(pose) == 4 && face[0].z != face[2].z) {
                    throw new AssertionError("kept an edge-on side face");
                }
            }
        }
        assertTrue(skips > 1000, "the sample exercises skipping: " + skips);
    }

    private static List<Vector3f[]> boxFaces(Random random) {
        float x0 = random.nextInt(16) / 16.0F, y0 = random.nextInt(16) / 16.0F, z0 = random.nextInt(16) / 16.0F;
        float x1 = x0 + (1 + random.nextInt(4)) / 16.0F, y1 = y0 + (1 + random.nextInt(4)) / 16.0F;
        float z1 = z0 + (1 + random.nextInt(4)) / 16.0F;
        List<Vector3f[]> faces = new ArrayList<>();
        faces.add(new Vector3f[]{new Vector3f(x0, y1, z0), new Vector3f(x0, y1, z1), new Vector3f(x1, y1, z1), new Vector3f(x1, y1, z0)});
        faces.add(new Vector3f[]{new Vector3f(x0, y1, z1), new Vector3f(x0, y0, z1), new Vector3f(x1, y0, z1), new Vector3f(x1, y1, z1)});
        faces.add(new Vector3f[]{new Vector3f(x1, y1, z1), new Vector3f(x1, y0, z1), new Vector3f(x1, y0, z0), new Vector3f(x1, y1, z0)});
        return faces;
    }

    @Test
    void theModelViewMustKeepDepthOutOfTheScreen() {
        assertTrue(GuiFlatFaces.keepsDepthOut(new Matrix4f()));
        assertTrue(GuiFlatFaces.keepsDepthOut(new Matrix4f().translate(0, 0, -11000).scale(2)));
        assertFalse(GuiFlatFaces.keepsDepthOut(new Matrix4f().rotateY(0.3F)), "a world view rotation");
        assertFalse(GuiFlatFaces.keepsDepthOut(new Matrix4f().m23(-1)), "a perspective divide");
    }

    private record Quad(int zeroAreaUnder) implements GuiEdgeOnQuad {
        @Override
        public int aurora$zeroAreaUnder() { return zeroAreaUnder; }
    }

    @Test
    void onlyAnAtlasDrawingWithTheGateOnSkips() {
        Vector3f[] side = topEdge(4, 2);
        Quad quad = new Quad(GuiFlatFaces.zeroAreaUnder(side[0], side[1], side[2], side[3]) | GuiFlatFaces.COMPUTED);
        PoseStack stack = new PoseStack();
        stack.last().pose().set(guiPose(new Matrix4f()));
        PoseStack.Pose pose = stack.last().copy();
        try {
            assertFalse(GuiFlatFaces.skip(pose, quad), "outside an atlas drawing");
            GuiFlatFaces.beginAtlasDraw(false);
            assertFalse(GuiFlatFaces.skip(pose, quad), "gate off");
            GuiFlatFaces.beginAtlasDraw(true);
            assertTrue(GuiFlatFaces.skip(pose, quad));
            assertFalse(GuiFlatFaces.skip(pose, "not a quad"));
            PoseStack.Pose turned = pose.copy();
            turned.pose().rotateY(0.5F);
            assertFalse(GuiFlatFaces.skip(turned, quad), "the collapsed axes follow the pose");
            assertTrue(GuiFlatFaces.skip(pose, quad));
        } finally {
            GuiFlatFaces.endAtlasDraw();
        }
        assertFalse(GuiFlatFaces.skip(pose, quad));
    }
}
