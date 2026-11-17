package com.aurora.client.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Owner retired the Translucent key style (2026-11-17): Flat is the default and old saves load as Flat. */
class KeystrokesKeyStyleTest {

    @Test void flatIsTheDefault() {
        assertEquals(AuroraConfig.KeystrokesKeyStyle.FLAT, new AuroraConfig().keystrokesKeyStyle);
    }

    @Test void savedTranslucentLoadsAsFlat() {
        var style = new Gson().fromJson("\"TRANSLUCENT\"", AuroraConfig.KeystrokesKeyStyle.class);
        assertEquals(AuroraConfig.KeystrokesKeyStyle.FLAT, style);
    }

    @Test void onlyFlatAndKeycapRemain() {
        assertArrayEquals(new AuroraConfig.KeystrokesKeyStyle[]{
                AuroraConfig.KeystrokesKeyStyle.FLAT, AuroraConfig.KeystrokesKeyStyle.KEYCAP},
                AuroraConfig.KeystrokesKeyStyle.values());
    }
}
