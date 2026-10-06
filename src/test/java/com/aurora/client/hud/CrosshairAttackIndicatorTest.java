package com.aurora.client.hud;

import com.aurora.client.config.AuroraConfig.CrosshairStyle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CrosshairAttackIndicatorTest {
    @Test void indicatorScalesIndependentlyAndStaysAtVanillaPositionUntilOverlap() {
        for (var style : CrosshairStyle.values()) {
            var small = CrosshairAttackIndicator.layout(100, style, 5, 1, 2, 15, 15);
            var large = CrosshairAttackIndicator.layout(200, style, 5, 1, 2, 15, 15);
            assertEquals(1, small.scale());
            assertEquals(2, large.scale());
            assertEquals(small.top(), large.top(), "Scaling must not change the gap");
            assertEquals(9, small.top(), "Non-overlapping shapes keep vanilla placement");
            var largerCrosshair = CrosshairAttackIndicator.layout(100, style, 10, 1, 4, 15, 15);
            assertEquals(small.scale(), largerCrosshair.scale(), "Crosshair size must not scale the indicator");
            if (style == CrosshairStyle.DOT) assertEquals(9, largerCrosshair.top());
            else assertTrue(largerCrosshair.top() > small.top());
            assertTrue(small.top() > 2);
        }
    }

    @Test void clearanceUsesCanvasAspectRatioAndActiveIndicatorSize() {
        var wide = CrosshairAttackIndicator.layout(100, CrosshairStyle.CUSTOM, 10, 1, 0, 100, 10);
        var tall = CrosshairAttackIndicator.layout(100, CrosshairStyle.CUSTOM, 10, 1, 0, 10, 100);
        assertEquals(9, wide.top());
        assertEquals(17, tall.top());
        assertEquals(wide.scale(), tall.scale());
        assertEquals(31.5f, CrosshairAttackIndicator.layout(100, CrosshairStyle.CROSS, 20, 6, 10, 15, 15).top());
    }

    @Test void aSmallGapDoesNotMoveTheIndicatorButActualOverlapDoes() {
        assertEquals(9, CrosshairAttackIndicator.layout(100, CrosshairStyle.CIRCLE, 8, 1, 0, 15, 15).top());
        assertEquals(11.5f, CrosshairAttackIndicator.layout(100, CrosshairStyle.CIRCLE, 9, 1, 0, 15, 15).top());
        assertEquals(9, CrosshairAttackIndicator.layout(300, CrosshairStyle.DOT, 1, 1, 0, 15, 15).top());
    }
}
