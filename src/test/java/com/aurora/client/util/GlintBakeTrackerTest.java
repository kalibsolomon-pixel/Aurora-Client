package com.aurora.client.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class GlintBakeTrackerTest {
    /** The transform vanilla's glint texturing builds for a clock value, without the clock. */
    private static Matrix4f glint(long millis) {
        float offset0 = (float) (millis % 110000L) / 110000.0F;
        float offset1 = (float) (millis % 30000L) / 30000.0F;
        return new Matrix4f().translation(-offset0, offset1, 0.0F).rotateZ((float) (Math.PI / 18)).scale(8.0F);
    }

    private static final Object SWORD = List.of("sword", "foil");

    @Test
    void redrawsOnlyWhenTheTransformMoved() {
        GlintBakeTracker t = new GlintBakeTracker();
        t.inputs(0.75, 0);
        assertTrue(t.firstThisFrame(SWORD));
        t.drawn(SWORD, glint(400), glint(400));
        t.endFrame(Set.of(SWORD));

        assertTrue(t.firstThisFrame(SWORD));
        assertFalse(t.stale(SWORD, glint(400)), "same millisecond: the drawing is current");
        t.endFrame(Set.of(SWORD));

        assertTrue(t.firstThisFrame(SWORD));
        assertTrue(t.stale(SWORD, glint(404)));
    }

    @Test
    void aDrawingAcrossAClockStepIsNotKept() {
        GlintBakeTracker t = new GlintBakeTracker();
        t.inputs(0.75, 0);
        t.drawn(SWORD, glint(400), glint(404));
        assertFalse(t.recorded(SWORD));
        assertTrue(t.stale(SWORD, glint(404)));
    }

    @Test
    void onceAFrameLikeVanilla() {
        GlintBakeTracker t = new GlintBakeTracker();
        assertTrue(t.firstThisFrame(SWORD));
        assertFalse(t.firstThisFrame(SWORD), "a second copy reuses the first one's slot");
        t.endFrame(Set.of(SWORD));
        assertTrue(t.firstThisFrame(SWORD));
    }

    @Test
    void aStrengthChangeForgetsDrawingsForTwoFrames() {
        GlintBakeTracker t = new GlintBakeTracker();
        t.inputs(0.75, 0);
        t.drawn(SWORD, glint(400), glint(400));
        t.endFrame(Set.of(SWORD));

        t.inputs(0.5, 0);
        assertTrue(t.stale(SWORD, glint(400)));
        t.drawn(SWORD, glint(400), glint(400));
        t.endFrame(Set.of(SWORD));
        assertTrue(t.stale(SWORD, glint(400)), "the frame after a change draws again");

        t.inputs(0.5, 0);
        t.drawn(SWORD, glint(400), glint(400));
        t.endFrame(Set.of(SWORD));
        assertFalse(t.stale(SWORD, glint(400)));
    }

    @Test
    void aGlintTextureReloadForgetsDrawings() {
        GlintBakeTracker t = new GlintBakeTracker();
        t.inputs(0.75, 0);
        t.drawn(SWORD, glint(400), glint(400));
        GlintBakeTracker.glintTextureChanged();
        t.inputs(0.75, 0);
        assertTrue(t.stale(SWORD, glint(400)));
    }

    @Test
    void anUnknownTransformAlwaysRedraws() {
        GlintBakeTracker t = new GlintBakeTracker();
        t.drawn(SWORD, null, null);
        assertFalse(t.recorded(SWORD));
        assertTrue(t.stale(SWORD, null));
    }

    @Test
    void forgetsItemsThatLostTheirSlots() {
        GlintBakeTracker t = new GlintBakeTracker();
        for (int i = 0; i < 100; i++) t.drawn(List.of("item", i), glint(1), glint(1));
        t.endFrame(Set.of(SWORD));
        assertFalse(t.recorded(List.of("item", 7)));
    }
}
