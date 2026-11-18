package com.aurora.client.hud;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Health Indicators' vanilla heart bar, HUD readout, and the crosshair blend's rectangle union. */
class HealthIndicatorsTest {
    private record Placed(int x, int y, String sprite, int layer) {}

    private static List<Placed> bar(float health, float max, float absorption, int display, boolean blink) {
        int[] raw = HeartBar.layout(health, max, absorption, display, blink, HeartBar.Type.NORMAL, false, 7);
        assertEquals(0, raw.length % HeartBar.STRIDE);
        List<Placed> out = new ArrayList<>();
        for (int i = 0; i < raw.length; i += HeartBar.STRIDE)
            out.add(new Placed(raw[i], raw[i + 1], HeartBar.spritePath(raw[i + 2]), raw[i + 3]));
        return out;
    }

    private static long count(List<Placed> bar, String sprite) {
        return bar.stream().filter(p -> p.sprite.equals(sprite)).count();
    }

    @Test void spritePathsAreVanillasHeartSprites() {
        assertEquals("hud/heart/container", HeartBar.spritePath(HeartBar.sprite(HeartBar.Type.CONTAINER, false, false, false)));
        assertEquals("hud/heart/container_hardcore_blinking", HeartBar.spritePath(HeartBar.sprite(HeartBar.Type.CONTAINER, true, false, true)));
        assertEquals("hud/heart/full", HeartBar.spritePath(HeartBar.sprite(HeartBar.Type.NORMAL, false, false, false)));
        assertEquals("hud/heart/half_blinking", HeartBar.spritePath(HeartBar.sprite(HeartBar.Type.NORMAL, false, true, true)));
        assertEquals("hud/heart/hardcore_full", HeartBar.spritePath(HeartBar.sprite(HeartBar.Type.NORMAL, true, false, false)));
        assertEquals("hud/heart/absorbing_half", HeartBar.spritePath(HeartBar.sprite(HeartBar.Type.ABSORBING, false, true, false)));
        assertEquals("hud/heart/frozen_hardcore_full_blinking", HeartBar.spritePath(HeartBar.sprite(HeartBar.Type.FROZEN, true, false, true)));
        for (int i = 0; i < HeartBar.SPRITE_COUNT; i++) assertTrue(HeartBar.spritePath(i).startsWith("hud/heart/"));
    }

    @Test void fullHealthIsTenContainersAndTenHeartsAtVanillaSpacing() {
        var bar = bar(20, 20, 0, 20, false);
        assertEquals(10, count(bar, "hud/heart/container"));
        assertEquals(10, count(bar, "hud/heart/full"));
        assertEquals(0, count(bar, "hud/heart/half"));
        assertEquals(HeartBar.SPACING * 9 + HeartBar.SPRITE_SIZE, HeartBar.width(HeartBar.layout(20, 20, 0, 20, false, HeartBar.Type.NORMAL, false, 0)));
        assertTrue(bar.stream().allMatch(p -> p.y == 0));
        // Each heart sits on its container, above it in layer order.
        for (Placed p : bar) if (p.sprite.equals("hud/heart/full"))
            assertTrue(bar.stream().anyMatch(c -> c.sprite.equals("hud/heart/container") && c.x == p.x && c.layer < p.layer));
    }

    @Test void healthRoundsUpToHalfHeartsAndEmptyContainersStay() {
        var bar = bar(6.2f, 20, 0, 7, false);  // ceil -> 7 halves: 3 full + 1 half
        assertEquals(10, count(bar, "hud/heart/container"));
        assertEquals(3, count(bar, "hud/heart/full"));
        assertEquals(1, count(bar, "hud/heart/half"));
    }

    @Test void absorptionAddsGoldHeartsAfterTheMaximum() {
        var bar = bar(20, 20, 4, 20, false);
        assertEquals(12, count(bar, "hud/heart/container"));
        assertEquals(2, count(bar, "hud/heart/absorbing_full"));
        // The eleventh container wraps to a second row above the first.
        assertTrue(bar.stream().anyMatch(p -> p.y < 0 && p.sprite.equals("hud/heart/absorbing_full")));
    }

    @Test void blinkShowsTheOldHealthUnderTheCurrent() {
        var bar = bar(10, 20, 0, 16, true);
        assertEquals(8, count(bar, "hud/heart/full_blinking"));
        assertEquals(5, count(bar, "hud/heart/full"));
        assertEquals(10, count(bar, "hud/heart/container_blinking"));
    }

    @Test void lowHealthJigglesAndBossBarsFitOneRow() {
        boolean moved = false;
        for (int seed = 0; seed < 20 && !moved; seed++) {
            int[] raw = HeartBar.layout(3, 20, 0, 3, false, HeartBar.Type.NORMAL, false, seed);
            for (int i = 1; i < raw.length; i += HeartBar.STRIDE) moved |= raw[i] != 0;
        }
        assertTrue(moved, "hearts jiggle at 4 HP or less");
        var wither = bar(300, 300, 0, 300, false);
        assertEquals(10, count(wither, "hud/heart/container"));
        assertEquals(10, count(wither, "hud/heart/full"));
        assertTrue(wither.stream().allMatch(p -> p.y == 0));
        var hurtWither = bar(150, 300, 0, 150, false);
        assertEquals(5, count(hurtWither, "hud/heart/full"));
    }

    @Test void animationBlinksOnDamageAndCatchesUpAfterASecond() {
        var a = new HeartBar.Animation();
        a.update(20, false, 0, 0);
        assertEquals(20, a.displayHealth());
        a.update(14, true, 1, 50);
        assertEquals(20, a.displayHealth(), "old health stays while blinking");
        boolean blinked = false;
        for (long t = 1; t < 21; t++) blinked |= a.blink(t);
        assertTrue(blinked);
        assertFalse(a.blink(21));
        a.update(14, false, 30, 1_200);
        assertEquals(14, a.displayHealth());
        a.update(16, false, 31, 1_250);
        assertFalse(a.blink(32), "healing blinks only during the hurt animation");
    }

    @Test void animationsForgetEntitiesNoLongerDrawn() {
        var all = new HeartBar.Animations();
        UUID gone = UUID.randomUUID(), here = UUID.randomUUID();
        all.get(gone, 0).update(20, false, 0, 0);
        for (long ms = 0; ms <= 8_000; ms += 1_000) all.get(here, ms).update(20, false, ms / 50, ms);
        assertEquals(1, all.size());
    }

    @Test void readoutShowsTwoDecimalsAndAbsorption() {
        assertEquals("20.00", HealthReadout.format(20));
        assertEquals("19.47", HealthReadout.format(19.4700f));
        assertEquals("0.50", HealthReadout.format(0.5f));
        assertEquals("0.00", HealthReadout.format(-1));
        var r = new HealthReadout();
        r.update(13.25f, 0);
        assertEquals("13.25", r.health());
        assertEquals("", r.absorption());
        r.update(13.25f, 4);
        assertEquals("+4.00", r.absorption());
        String same = r.health();
        r.update(13.25f, 4);
        assertSame(same, r.health(), "an unchanged value formats nothing");
    }

    @Test void rectUnionCoversEachPixelOnce() {
        // A gapless, thickness-2 cross: arms overlap each other and the hub.
        int[] rects = {-5, -1, 0, 1, 0, -1, 5, 1, -1, -5, 1, 0, -1, 0, 1, 5, -1, -1, 1, 1};
        Set<String> want = new HashSet<>();
        for (int i = 0; i < rects.length; i += 4)
            for (int y = rects[i + 1]; y < rects[i + 3]; y++)
                for (int x = rects[i]; x < rects[i + 2]; x++) want.add(x + "," + y);
        Set<String> got = new HashSet<>();
        int[] union = RectUnion.of(rects);
        for (int i = 0; i < union.length; i += 4)
            for (int y = union[i + 1]; y < union[i + 3]; y++)
                for (int x = union[i]; x < union[i + 2]; x++) assertTrue(got.add(x + "," + y), "covered twice: " + x + "," + y);
        assertEquals(want, got);
        assertEquals(0, RectUnion.of(new int[]{0, 0, 0, 5}).length, "empty rectangles are ignored");
    }
}
