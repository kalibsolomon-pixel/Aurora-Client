package com.aurora.client.hud;

import com.aurora.client.config.AuroraConfig.HudBackground;
import com.aurora.client.theme.HudText;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudAppearanceTest {
    @Test void oldAuroraBackdropsMigrateToSolidInConfigsAndProfiles() {
        Gson gson = new Gson();
        assertEquals(HudBackground.SOLID, gson.fromJson("\"AURORA\"", HudBackground.class));
        assertEquals("\"SOLID\"", gson.toJson(HudBackground.SOLID));
        assertEquals(HudBackground.VANILLA, gson.fromJson("\"VANILLA\"", HudBackground.class));
        assertEquals(3, HudBackground.values().length);
    }

    @Test void explicitModuleColorsOverrideLegacyWithoutChangingTheirAlpha() {
        assertEquals(0xffabcdef, HudText.color(null, 0xffabcdef));
        assertEquals(0xff000000, HudText.color(0xff000000, 0xffabcdef));
        assertEquals(0x00123456, HudText.color(0x00123456, 0xffabcdef));
        assertEquals(0, HudText.color(0, 0xffabcdef));
    }
}
