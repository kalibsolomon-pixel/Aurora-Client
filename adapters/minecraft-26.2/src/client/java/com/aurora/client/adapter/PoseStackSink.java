package com.aurora.client.adapter;

import com.aurora.client.util.HandPoses;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/** {@link HandPoses.PoseSink} over this line's {@code PoseStack}; one reused instance, render thread only. */
public final class PoseStackSink implements HandPoses.PoseSink {

    private static final PoseStackSink INSTANCE = new PoseStackSink();
    private PoseStack stack;

    private PoseStackSink() {}

    /** The shared sink, now writing into {@code stack}. */
    public static PoseStackSink on(PoseStack stack) {
        INSTANCE.stack = stack;
        return INSTANCE;
    }

    @Override public void translate(float x, float y, float z) { stack.translate(x, y, z); }
    @Override public void rotateX(float degrees) { stack.mulPose(Axis.XP.rotationDegrees(degrees)); }
    @Override public void rotateY(float degrees) { stack.mulPose(Axis.YP.rotationDegrees(degrees)); }
    @Override public void rotateZ(float degrees) { stack.mulPose(Axis.ZP.rotationDegrees(degrees)); }
}
