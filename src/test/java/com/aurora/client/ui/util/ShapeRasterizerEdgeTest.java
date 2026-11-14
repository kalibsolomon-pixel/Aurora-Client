package com.aurora.client.ui.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rows a rounded shape only partly covers (fractional device sizes) used to fall back to
 * full-width spans: a flat line under the shape with square corners, and a short tick inside an
 * outline. Seen on the Keystrokes Space key at a 2.45 HUD density (2026-11-14).
 */
class ShapeRasterizerEdgeTest {
    private static final float GUI = 2f;

    @Test void bottomRowsFollowTheArcAtFractionalDensities() {
        for (float density = 2f; density <= 4.01f; density += 0.05f)
            for (int[] size : new int[][]{{72, 16}, {22, 22}}) {
                float f = density / GUI, x = f, y = f, w = size[0] * f, h = size[1] * f, r = 5 * f;
                int cols = Math.round((size[0] + 2) * density) + 2, rows = Math.round((size[1] + 2) * density) + 2;
                int[][] outline = new int[rows][cols], filled = new int[rows][cols];
                ShapeRasterizer.drawRoundedOutlineAA(sink(outline), GUI, x, y, w, h, r, f, 0xFFFFFFFF);
                ShapeRasterizer.drawRoundedRectAA(sink(filled), GUI, x, y, w, h, r, 0xFFFFFFFF);
                int bottom = (int) Math.ceil((y + h) * GUI);
                int corner = (int) Math.floor((x + r * 0.3f) * GUI) - 1;
                String at = "density " + density + " size " + size[0] + "x" + size[1];
                for (int[][] g : new int[][][]{outline, filled}) {
                    for (int row = bottom; row < rows; row++)
                        for (int col = 0; col < cols; col++) assertTrue(g[row][col] == 0, at + ": row " + row + " past the bottom edge");
                    assertTrue(g[bottom - 1][corner] == 0, at + ": square bottom corner");
                }
                // Body rows of the outline carry only its side strips.
                int body0 = (int) Math.ceil((y + r) * GUI) + 1, body1 = (int) Math.floor((y + h - r) * GUI) - 1;
                for (int row = body0; row <= body1; row++)
                    for (int col = cols / 3; col < 2 * cols / 3; col++)
                        assertTrue(outline[row][col] == 0, at + ": stray span in body row " + row);
            }
    }

    private static com.aurora.client.port.DrawSurface sink(int[][] grid) {
        return (x1, y1, x2, y2, argb) -> {
            for (int row = Math.max(0, y1); row < Math.min(grid.length, y2); row++)
                for (int col = Math.max(0, x1); col < Math.min(grid[0].length, x2); col++) grid[row][col]++;
        };
    }
}
