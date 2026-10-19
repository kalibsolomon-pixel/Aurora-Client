package com.aurora.client.worldmap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapTransformTest {
    @Test void minecraftHeadingsPointUpWhenRotated() {
        for (float yaw : new float[]{0, 90, 180, 270, -90, 361}) {
            double heading = Math.toRadians(yaw), rotation = MapTransform.rotation(yaw, true);
            double dx = -Math.sin(heading), dz = Math.cos(heading);
            assertEquals(0, MapTransform.screenX(dx, dz, Math.cos(rotation), Math.sin(rotation)), 1e-9);
            assertEquals(-1, MapTransform.screenZ(dx, dz, Math.cos(rotation), Math.sin(rotation)), 1e-9);
        }
    }
    @Test void squareAndCircleClampToTheirOwnBoundaries() {
        assertEquals(.5, MapTransform.clampScale(20, 20, 10, false));
        assertEquals(10 / Math.sqrt(800), MapTransform.clampScale(20, 20, 10, true));
        assertEquals(1, MapTransform.clampScale(0, 0, 10, true));
    }
    @Test void zoomPreservesCursorAnchorThroughEveryAnimationStep() {
        MapViewport viewport = new MapViewport();
        viewport.center(-513.25, 991.5);
        double x = viewport.worldX(211, 640), z = viewport.worldZ(123, 360);
        viewport.zoom(.0625, 211, 123, 640, 360);
        for (int i = 0; i < 200; i++) {
            viewport.advance(.016, 640, 360);
            assertEquals(x, viewport.worldX(211, 640), 1e-8);
            assertEquals(z, viewport.worldZ(123, 360), 1e-8);
        }
        assertEquals(.0625, viewport.scale());
        viewport.pan(12, -5);
        assertEquals(x - 12 / .0625, viewport.worldX(211, 640), 1e-8);
    }
    @Test void losslessIdentityDistinguishesSanitizerCollisions() {
        assertNotEquals(MapIdentity.digest("server:a:b"), MapIdentity.digest("server:a/b"));
        assertNotEquals(MapIdentity.digest("save:one/New World"), MapIdentity.digest("save:two/New World"));
        assertNotEquals(MapIdentity.dimensionFolder("a:b_c"), MapIdentity.dimensionFolder("a_b:c"));
        assertTrue(MapIdentity.dimensionFolder("../odd:dimension").matches("dim-[a-f0-9]{64}"));
    }
    @Test void giantViewportsBoundTileResidencyWithoutDiscardingViewportEdges() {
        MapViewport viewport = new MapViewport();
        viewport.zoom(.0001, 2000, 1000, 3840, 2160);
        for (int i = 0; i < 200; i++) viewport.advance(.016, 3840, 2160);
        double tiles = (3840 / (512 * viewport.scale()) + 2) * (2160 / (512 * viewport.scale()) + 2);
        assertTrue(tiles < 1400);
        viewport.constrainSize(7680, 4320);
        tiles = (7680 / (512 * viewport.scale()) + 2) * (4320 / (512 * viewport.scale()) + 2);
        assertTrue(tiles < 1400);
    }
}
