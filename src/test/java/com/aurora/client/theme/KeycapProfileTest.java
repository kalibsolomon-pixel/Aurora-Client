package com.aurora.client.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeycapProfileTest {
    @Test void cherryWallsAreWiderAtFrontThanBackAndSides() {
        var key = new KeycapProfile.Geometry(22, 22, KeycapProfile.DEFAULT_RADIUS, 1, false);
        assertEquals(22 * KeycapProfile.FRONT_WALL_SHARE, key.frontWall(), 1e-6);
        assertEquals(22 * KeycapProfile.BACK_WALL_SHARE, key.backWall(), 1e-6);
        assertEquals(22 * KeycapProfile.SIDE_WALL_SHARE, key.sideWall(), 1e-6);
        assertTrue(key.frontWall() > key.sideWall());
        assertTrue(key.sideWall() > key.backWall());
    }

    @Test void concaveDishLightsOppositeToAConvexFace() {
        var key = new KeycapProfile.Geometry(22, 22, 2, 1, false);
        float y = key.topY() + key.topHeight() * .5f;
        double left = key.lightingDelta(key.topX() + key.topWidth() * .2f, y);
        double center = key.lightingDelta(key.topX() + key.topWidth() * .5f, y);
        double right = key.lightingDelta(key.topX() + key.topWidth() * .8f, y);
        assertTrue(left < center && center < right, left + " " + center + " " + right);
        // A convex face under this upper-left light has the opposite signs.
        assertTrue(MaterialLighting.curvatureDelta(.5, 40, 80, 80, 10, 1,
                MaterialLighting.Profile.RAISED) > 0);
        assertTrue(MaterialLighting.curvatureDelta(79.5, 40, 80, 80, 10, 1,
                MaterialLighting.Profile.RAISED) < 0);
    }

    @Test void pressedTopSinksAndFrontWallAndShadowTighten() {
        var idle = new KeycapProfile.Geometry(22, 22, 2, 3, false);
        var pressed = new KeycapProfile.Geometry(22, 22, 2, 3, true);
        assertEquals(KeycapProfile.PRESS_TRAVEL * 3, pressed.topY() - idle.topY(), 1e-6);
        assertEquals(KeycapProfile.PRESS_TRAVEL * 3, idle.frontWall() - pressed.frontWall(), 1e-6);
        assertEquals(idle.topHeight(), pressed.topHeight(), 1e-6);
        assertTrue(pressed.shadowElevation() < idle.shadowElevation());
        assertTrue(pressed.shadowSoftness() < idle.shadowSoftness());
        assertTrue(pressed.shadowAlpha() < idle.shadowAlpha());
    }

    @Test void zeroThemeRadiusKeepsSquareSilhouetteAndPlanarFacets() {
        var square = new KeycapProfile.Geometry(66, 66, 0, 3, false);
        assertEquals(0, square.topRadius());
        assertEquals(1, square.outerCoverage(.5f, .5f));
        float depth = square.backWall() * .5f;
        double topA = square.lightingDelta(33, depth);
        double topB = square.lightingDelta(22, depth + .2f);
        assertEquals(topA, topB, 1e-9, "the back wall is one planar facet");
        assertTrue(topA > square.lightingDelta(33, 66 - square.frontWall() * .5f));
        var rounded = new KeycapProfile.Geometry(66, 66, 30, 3, false);
        assertTrue(rounded.topRadius() > 0);
        assertTrue(rounded.outerCoverage(.5f, .5f) < 1);
    }

    @Test void labelAreaIsExactlyTheTopSurface() {
        var key = new KeycapProfile.Geometry(144, 32, 4, 2, false);
        assertEquals(key.topX(), key.labelX());
        assertEquals(key.topY(), key.labelY());
        assertEquals(key.topWidth(), key.labelWidth());
        assertEquals(key.topHeight(), key.labelHeight());
        assertEquals(KeycapProfile.topX(72) * 2, key.labelX(), 1e-6);
        assertEquals(KeycapProfile.topY(16, 0) * 2, key.labelY(), 1e-6);
        assertEquals(KeycapProfile.topWidth(72) * 2, key.labelWidth(), 1e-6);
        assertEquals(KeycapProfile.topHeight(16) * 2, key.labelHeight(), 1e-6);
    }
}
