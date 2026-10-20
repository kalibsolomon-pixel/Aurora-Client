package com.aurora.client.worldmap;

import com.aurora.client.ui.util.UiLayerCache;
import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MinimapGridTest {
    private static int color(int x, int z) { return 0xFF000000 | ((x * 73856093) ^ (z * 19349663)) & 0xFFFFFF; }

    @Test void movingReusesOverlapAndMatchesAFreshSample() {
        AtomicInteger calls = new AtomicInteger();
        MinimapGrid.Sampler sampler = (x, z) -> { calls.incrementAndGet(); return color(x, z); };
        MinimapGrid moved = new MinimapGrid();
        moved.reset(9, -4, 7, sampler);
        int[][] steps = {{1, 0}, {0, -1}, {-3, 2}, {2, 2}, {0, 0}, {-8, 0}, {20, -20}};
        int ox = -4, oz = 7;
        for (int[] step : steps) {
            ox += step[0]; oz += step[1];
            calls.set(0);
            boolean updated = moved.moveTo(ox, oz, sampler);
            assertEquals(step[0] != 0 || step[1] != 0, updated);
            int dx = Math.abs(step[0]), dz = Math.abs(step[1]);
            int expectedSamples = dx >= 9 || dz >= 9 ? 81 : 81 - (9 - dx) * (9 - dz);
            assertEquals(expectedSamples, calls.get(), "only newly exposed cells are sampled");
            MinimapGrid fresh = new MinimapGrid();
            fresh.reset(9, ox, oz, MinimapGridTest::color);
            assertArrayEquals(fresh.cells(), moved.cells());
        }
    }

    @Test void movingRequiresAnInitialSample() {
        assertThrows(IllegalStateException.class, () -> new MinimapGrid().moveTo(0, 0, MinimapGridTest::color));
    }

    @Test void affineResampleFollowsSubPixelTranslationAndRotation() throws Exception {
        int n = 5;
        int[] grid = new int[n * n];
        for (int i = 0; i < grid.length; i++) grid[i] = 0xFF000000 | i;
        UiLayerCache cache = new UiLayerCache();
        try {
            cache.ensureSize(8, 8);
            double angle = .3, step = .4;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double u0 = 1.15, v0 = .9;
            cache.writeAffineAbgr(grid, n, u0, v0, step * cos, step * sin, -step * sin, step * cos, 0x80, 0x00ABCDEF);
            var field = UiLayerCache.class.getDeclaredField("image"); field.setAccessible(true);
            int[] out = ((NativeImage) field.get(cache)).getPixelsABGR();
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                double u = u0 + x * step * cos - y * step * sin, v = v0 + x * step * sin + y * step * cos;
                int expected = u < 0 || v < 0 || u >= n || v >= n ? 0x00ABCDEF
                        : grid[(int) Math.floor(v) * n + (int) Math.floor(u)] & 0xFFFFFF;
                assertEquals(0x80000000 | expected, out[y * 8 + x], "pixel " + x + "," + y);
            }
        } finally { cache.dispose(); }
    }
}
