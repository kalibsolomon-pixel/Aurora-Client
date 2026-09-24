package com.aurora.client.ui.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScrollbarChromeTest {

    @Test
    void quantizesBothEdgesWithoutLosingFractionalHeight() {
        ScrollbarChrome.Thumb thumb = ScrollbarChrome.thumb(40.0, 181.0, 47.6, 0.37, 2.0);
        double top = 40.0 + (181.0 - 47.6) * 0.37;
        double expectedTop = Math.round(top * 2.0) / 2.0;
        double expectedBottom = Math.round((top + 47.6) * 2.0) / 2.0;
        assertEquals(expectedTop, thumb.y());
        assertEquals(expectedBottom, thumb.y() + thumb.height());
    }

    @Test
    void reachesBothTrackEdgesExactly() {
        ScrollbarChrome.Thumb top = ScrollbarChrome.thumb(40.0, 180.0, 48.0, 0.0, 3.0);
        ScrollbarChrome.Thumb bottom = ScrollbarChrome.thumb(40.0, 180.0, 48.0, 1.0, 3.0);
        assertEquals(40.0, top.y());
        assertEquals(220.0, bottom.y() + bottom.height());
    }

    @Test
    void clampsOutOfRangeRatios() {
        assertEquals(10.0, ScrollbarChrome.thumb(10.0, 100.0, 25.0, -1.0, 2.0).y());
        ScrollbarChrome.Thumb bottom = ScrollbarChrome.thumb(10.0, 100.0, 25.0, 2.0, 2.0);
        assertEquals(110.0, bottom.y() + bottom.height());
    }
}
