package com.aurora.client.hud.module;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Potion HUD's Condensed layout shows whole minutes, then seconds in the last minute. */
class PotionCondensedTimeTest {

    @Test void wholeMinutesWhileAMinuteOrMoreRemains() {
        assertEquals("90m", PotionModule.condensedTime(90 * 60));
        assertEquals("8m", PotionModule.condensedTime(8 * 60));
        assertEquals("1m", PotionModule.condensedTime(119));
        assertEquals("1m", PotionModule.condensedTime(60));
    }

    @Test void secondsCountingDownInTheLastMinute() {
        assertEquals("59s", PotionModule.condensedTime(59));
        assertEquals("1s", PotionModule.condensedTime(1));
    }
}
