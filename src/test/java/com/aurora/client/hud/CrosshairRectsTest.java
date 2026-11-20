package com.aurora.client.hud;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CrosshairRectsTest {
    private static int[] coverage(int[] rects, int w, int h) {
        int[] covered = new int[w * h];
        for (int i = 0; i < rects.length; i += 4) {
            for (int y = rects[i + 1]; y < rects[i + 3]; y++)
                for (int x = rects[i]; x < rects[i + 2]; x++) covered[y * w + x]++;
        }
        return covered;
    }

    private static boolean[] grid(String... rows) {
        boolean[] pixels = new boolean[rows.length * rows[0].length()];
        for (int y = 0; y < rows.length; y++)
            for (int x = 0; x < rows[y].length(); x++) pixels[y * rows[0].length() + x] = rows[y].charAt(x) == '#';
        return pixels;
    }

    /** An inverting blend flips a pixel per cover, so every lit cell must be covered exactly once. */
    @Test void rectanglesCoverEachLitCellOnce() {
        Random random = new Random(5);
        for (int round = 0; round < 3000; round++) {
            int w = random.nextInt(1, 34), h = random.nextInt(1, 34);
            boolean[] pixels = new boolean[w * h];
            double density = random.nextDouble();
            for (int i = 0; i < pixels.length; i++) pixels[i] = random.nextDouble() < density;
            int[] covered = coverage(CrosshairRenderer.customRects(pixels, w, h), w, h);
            for (int i = 0; i < pixels.length; i++) assertEquals(pixels[i] ? 1 : 0, covered[i], "round " + round + " cell " + i);
        }
    }

    @Test void theOwnersCrosshairsNeedFewerFills() {
        boolean[] plus = grid(".............", ".............", "......#......", "......#......", "......#......",
                "......#......", "..#########..", "......#......", "......#......", "......#......", "......#......",
                ".............", ".............");
        assertEquals(3 * 4, CrosshairRenderer.customRects(plus, 13, 13).length, "two arms and the bar");
        boolean[] indicator = grid(".............", ".............", "......#......", "...##.#.##...", "...#..#..#...",
                "......#......", "..#########..", "......#......", "...#..#..#...", "...##.#.##...", "......#......",
                ".............", ".............");
        int fills = CrosshairRenderer.customRects(indicator, 13, 13).length / 4;
        assertTrue(fills < 17, "fewer fills than the 17 row runs: " + fills);
    }
}
