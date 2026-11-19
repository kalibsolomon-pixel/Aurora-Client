package com.aurora.client.util;

/**
 * First-person hand poses for the Animations feature, as plain numbers.
 *
 * <p>Each pose is written into a {@link PoseSink}; each adapter supplies one
 * backed by that line's {@code PoseStack} (whose rotation call differs between
 * versions). The values are Aurora's own, tuned against in-game captures on
 * 2026-11-18; see docs/reports/OVERFLOW_ANIMATIONS_RESEARCH.md for scope.
 *
 * <p>{@code invert} is +1 for a right arm and -1 for a left arm, as in
 * vanilla's hand renderer, so every pose mirrors with the arm.
 */
public final class HandPoses {

    private HandPoses() {}

    /** The pose stack operations a pose needs, in the order they are applied. */
    public interface PoseSink {
        void translate(float x, float y, float z);
        void rotateX(float degrees);
        void rotateY(float degrees);
        void rotateZ(float degrees);
    }

    /**
     * Compact Eat/Drink: the same timing as vanilla's consume motion (a quick
     * approach once use starts, a bob until 80% of the use time), with less
     * travel. Vanilla carries the item across to the screen centre and over
     * the crosshair; this stops it short of the centre and lower, with a
     * smaller turn and a gentler bob, so the item still visibly comes up to
     * the face.
     *
     * @param usedTicks    vanilla's remaining-use ticks minus the partial tick plus one
     * @param useDuration  the item's total use duration, in ticks
     */
    public static void compactEat(PoseSink pose, float usedTicks, int useDuration, int invert) {
        float progress = useDuration > 0 ? usedTicks / useDuration : 1.0F;
        if (progress < 0.8F) {
            float bob = Math.abs((float) Math.cos(usedTicks / 4.0F * (float) Math.PI) * COMPACT_BOB);
            pose.translate(0.0F, bob, 0.0F);
        }
        // Vanilla's approach curve: near 1 for almost the whole use, falling to 0 at its very end.
        float approach = 1.0F - (float) Math.pow(progress, 27.0);
        pose.translate(approach * COMPACT_X * invert, approach * COMPACT_Y, approach * COMPACT_Z);
        pose.rotateY(invert * approach * COMPACT_YAW);
        pose.rotateX(approach * COMPACT_PITCH);
        pose.rotateZ(invert * approach * COMPACT_ROLL);
    }

    /**
     * Classic Block Pose: a non-shield item in a blocking use, held the 1.7/1.8
     * way, angled up and across the body with its edge toward the view.
     * Applied after vanilla's arm placement, in place of vanilla's block pose.
     */
    public static void classicBlock(PoseSink pose, int invert) {
        pose.translate(invert * BLOCK_X, BLOCK_Y, BLOCK_Z);
        pose.rotateY(invert * BLOCK_YAW);
        pose.rotateX(BLOCK_PITCH);
        pose.rotateZ(invert * BLOCK_ROLL);
    }

    // ---- Tuned values (26.3 captures, 2026-11-19) ----
    private static final float COMPACT_BOB = 0.05F;
    private static final float COMPACT_X = 0.32F;
    private static final float COMPACT_Y = -0.24F;
    private static final float COMPACT_Z = 0.0F;
    private static final float COMPACT_YAW = 60.0F;
    private static final float COMPACT_PITCH = 8.0F;
    private static final float COMPACT_ROLL = 18.0F;

    private static final float BLOCK_X = -0.18F;
    private static final float BLOCK_Y = 0.22F;
    private static final float BLOCK_Z = 0.10F;
    private static final float BLOCK_YAW = 60.0F;
    private static final float BLOCK_PITCH = -80.0F;
    private static final float BLOCK_ROLL = 0.0F;
}
