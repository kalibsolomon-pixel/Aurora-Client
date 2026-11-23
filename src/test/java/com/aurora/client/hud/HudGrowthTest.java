package com.aurora.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudGrowthTest {
    private static final int SCREEN = 480;

    @Test void anchoredKeepsTheAnchorRule() {
        assertEquals(100, HudGrowth.ANCHORED.start(100, 82, 122, SCREEN));
    }

    @Test void awayFromCentreKeepsTheInnerEdge() {
        // Left of centre: the right edge (facing the hotbar) stays; extra pieces extend left.
        assertEquals(100 + 82 - 122, HudGrowth.AWAY_FROM_CENTER.start(100, 82, 122, SCREEN));
        assertEquals(100 + 82 - 42, HudGrowth.AWAY_FROM_CENTER.start(100, 82, 42, SCREEN));
        // Right of centre: the left edge stays; extra pieces extend right.
        assertEquals(300, HudGrowth.AWAY_FROM_CENTER.start(300, 82, 122, SCREEN));
    }

    @Test void towardCentreKeepsTheOuterEdge() {
        // Top-right potion list: the right edge at the screen border stays.
        assertEquals(456 - 99, HudGrowth.TOWARD_CENTER.start(456 - 24, 24, 99, SCREEN));
        assertEquals(4, HudGrowth.TOWARD_CENTER.start(4, 24, 99, SCREEN));
    }

    @Test void theNominalSizeLandsOnTheReferenceBox() {
        for (HudGrowth g : HudGrowth.values()) {
            assertEquals(100, g.start(100, 82, 82, SCREEN), g.name());
            assertEquals(300, g.start(300, 82, 82, SCREEN), g.name());
        }
    }

    @Test void aBoxAcrossTheCentreGrowsBothWays() {
        assertEquals(200 + (82 - 122) / 2, HudGrowth.AWAY_FROM_CENTER.start(200, 82, 122, SCREEN));
        assertEquals(200 + (82 - 122) / 2, HudGrowth.TOWARD_CENTER.start(200, 82, 122, SCREEN));
    }

    @Test void refStartInvertsStart() {
        for (HudGrowth g : HudGrowth.values()) {
            for (int size : new int[] {20, 82, 122}) {
                // Where the rule switches edges at the centre, a drag skips at most half the growth.
                int slack = (Math.abs(size - 82) + 1) / 2;
                for (int x = -50; x <= SCREEN + 50; x++) {
                    int placed = g.start(g.refStart(x, 82, size, SCREEN), 82, size, SCREEN);
                    assertTrue(Math.abs(placed - x) <= slack, g + " size " + size + " at " + x + " placed " + placed);
                    boolean nearCentre = Math.abs(x - SCREEN / 2) < 82 + size;
                    if (!nearCentre) assertEquals(x, placed, g + " size " + size + " at " + x);
                }
            }
        }
    }

    @Test void wrapGridFillsLinesAndFlipsTowardTheCentre() {
        WrapGrid g = WrapGrid.of(8, 6);
        assertEquals(6, g.along());
        assertEquals(2, g.lines());
        assertEquals(0, g.alongSlot(0, false));
        assertEquals(5, g.alongSlot(0, true), "flipped, the first effect sits at the outer end");
        assertEquals(1, g.lineSlot(7, false));
        assertEquals(0, g.lineSlot(7, true));
        assertEquals(4, g.alongSlot(7, true), "a partial last line stays flush with the outer edge");

        WrapGrid one = WrapGrid.of(0, 6);
        assertEquals(1, one.along());
        assertEquals(1, one.lines());
        assertEquals(3, WrapGrid.of(3, 0).lines(), "perLine below 1 behaves as 1");
    }
}
