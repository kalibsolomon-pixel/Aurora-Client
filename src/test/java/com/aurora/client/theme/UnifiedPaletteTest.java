package com.aurora.client.theme;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class UnifiedPaletteTest {
    @ParameterizedTest
    @ValueSource(ints={0xff080914,0xfffdfdfe,0xffbd2dff,0xff808080,0xffeb0029,0xff8b80ff,0xff5bd0e0,0xffe2b655})
    void actionStatesReadableAndStoredPreferenceUnchanged(int accent) {
        ThemeDefinition def = ThemeDefinition.defaults(); def.accent = accent;
        var theme = ThemeResolver.resolve(def, true);
        var a = UnifiedPalette.accent(accent, ThemeMode.DARK);
        assertEquals(accent, def.accent); assertEquals(accent, theme.accent());
        assertEquals(accent, a.base()); assertNotEquals(a.base(), a.strong());
        for (int fill : new int[]{a.strong(), a.hover(), a.pressed()})
            assertTrue(UnifiedPalette.contrast(fill,a.onAction()) >= 4.5, Integer.toHexString(fill));
        assertTrue(UnifiedPalette.contrast(a.marker(), 0xff16171d) >= 3);
        assertEquals(0x24, a.soft() >>> 24); assertNotEquals(a.strong(),a.soft());
    }
    @Test void darkNeutralsAndStatusIndependentOfAccent() {
        ThemeDefinition red = ThemeDefinition.defaults(), cyan = red.copy(); cyan.accent=0xff5bd0e0;
        var a=ThemeResolver.resolve(red,true); var b=ThemeResolver.resolve(cyan,true);
        for (ThemeToken t: ThemeToken.values()) if (t.name().startsWith("MATERIAL_") || t.name().startsWith("STATUS_"))
            assertEquals(a.color(t),b.color(t),t.name());
        assertEquals(0xff101116,a.color(ThemeToken.MATERIAL_CANVAS));
        assertEquals(0xff16171d,a.color(ThemeToken.MATERIAL_PANEL));
        assertEquals(0xff242631,a.color(ThemeToken.MATERIAL_HOVER));
        assertEquals(0xffedf1fb,a.color(ThemeToken.MATERIAL_TEXT));
    }
    @Test void lightUsesExistingClientCompanionAndFactoryStillRemembersSource() {
        var def=ThemeDefinition.defaults(); def.mode=ThemeMode.LIGHT; def.accent=0xff123456;
        var light=ThemeResolver.resolve(def,true);
        assertEquals(light.color(ThemeToken.SURFACE),light.color(ThemeToken.MATERIAL_PANEL));
        assertEquals(light.color(ThemeToken.ON_BACKGROUND),light.color(ThemeToken.MATERIAL_TEXT));
        assertEquals(def.accent,ThemeResolver.resolve(def,false).accent());
    }
}
