package com.aurora.client.hud.module;

import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudTextCacheTest {
    @Test void repeatedFramesReuseTheComponentAndItsShapedText() {
        var cache = new HudTextCache(2);
        var first = cache.text(0, "Left Shift", null);
        for (int frame = 0; frame < 1_000; frame++) {
            var next = cache.text(0, new String("Left Shift"), null);
            assertSame(first, next);
            assertSame(first.getVisualOrderText(), next.getVisualOrderText());
        }
        assertEquals("Left Shift", first.getString());
    }

    @Test void rebindingAndDurabilityChangesInvalidateOnlyTheirSlot() {
        var cache = new HudTextCache(2);
        var key = cache.text(0, "F7", null);
        var armor = cache.text(1, "100%", null);
        assertEquals("F8", cache.text(0, "F8", null).getString());
        assertNotSame(key, cache.text(0, "F8", null));
        assertSame(armor, cache.text(1, "100%", null));
        assertEquals("99%", cache.text(1, "99%", null).getString());
    }

    @Test void fontStyleChangesInvalidateCachedShaping() {
        var cache = new HudTextCache(2);
        var first = cache.text(0, "W", null);
        var other = cache.text(1, "A", null);
        var changed = cache.text(0, "W", Style.EMPTY.withBold(true));
        assertNotSame(first, changed);
        assertTrue(changed.getStyle().isBold());
        assertNotSame(other, cache.text(1, "A", Style.EMPTY.withBold(true)));
        assertEquals(Style.EMPTY, cache.text(0, "W", null).getStyle());
    }
}
