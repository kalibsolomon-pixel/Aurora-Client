package com.aurora.client.ui;

import com.aurora.client.screen.setting.FeatureSetting;
import com.aurora.client.screen.setting.SectionHeaderSetting;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spxctra simplification pass (2026-10-30): one emphasis per state, one face per
 * trailing field control, one backdrop capture per window, no forced capitals.
 */
class SpxctraSimplificationContractTest {

    private static String read(String rel) throws Exception {
        return Files.readString(Path.of("src/client/java/com/aurora/client/" + rel));
    }

    @Test
    void selectionAndPrimaryCarryNoAccentGlow() throws Exception {
        String material = read("ui/component/MaterialSurface.java");
        assertFalse(material.contains("selectionGlow"), "selected peers: wash + one accent edge only");
        assertFalse(material.contains("Halo"), "no accent/primary halo rasterization");
        assertFalse(read("ui/component/Button.java").contains("? 81 : 80"));
    }

    @Test
    void trailingFieldControlsShareOneFace() throws Exception {
        assertTrue(read("screen/setting/EnumSetting.java").contains("MaterialSurface.field(ctx,btnX,btnY,BTN_W,BTN_H"));
        assertTrue(read("screen/setting/KeybindSetting.java").contains("MaterialSurface.field(ctx,btnX,btnY,BTN_W,BTN_H"));
        assertTrue(read("screen/setting/KeyListSetting.java").contains("MaterialSurface.field(ctx,addX,addY,addW,ADD_H"));
        String button = read("ui/component/Button.java");
        assertTrue(button.contains("boolean asField=!primary && look==Theming.NONE;"),
                "plain neutral and destructive actions wear the same opaque field face");
        assertTrue(button.contains("withAlpha(fill, asField ? 255 :"));
        String material = read("ui/component/MaterialSurface.java");
        int field = material.indexOf("public static void field(");
        String body = material.substring(field, material.indexOf("}\n", field));
        assertTrue(body.contains(",0,FIELD_KIND"), "field controls have no outline");
    }

    @Test
    void repeatedRowsAndDestructiveActionsRestQuietly() throws Exception {
        String manager = read("screen/ManagerListScreen.java");
        assertTrue(manager.contains("ThemeManager.color(ThemeToken.WINDOW_FILL),0,110,false)"), "rows carry no per-row border");
        String button = read("ui/component/Button.java");
        int destructive = button.indexOf("if(destructive) {");
        assertTrue(button.substring(destructive, button.indexOf("}", destructive)).contains("edge=0;"),
                "destructive rests without the red outline; hover/press commit to solid red");
        String packs = read("screen/ResourcePackBrowserScreen.java");
        assertTrue(packs.contains("newPhaseButton(renderPhase, false)") && packs.contains("newPhaseButton(st.phase, true)"),
                "only the detail modal's Install is primary");
    }

    @Test
    void windowedScreensOwnTheOnlyCapture() throws Exception {
        for (String host : new String[]{"screen/ManagerListScreen.java", "screen/WaypointEditScreen.java",
                "worldmap/screen/WorldMapScreen.java", "screen/ColorPickerScreen.java", "screen/MappingDataScreen.java",
                "screen/ResourcePackBrowserScreen.java", "screen/AuroraScreen.java", "screen/FeatureDetailScreen.java"})
            assertTrue(read(host).contains("public boolean parentGlassOnly() { return true; }"), host);
        // Owner rulings: the title keeps five independent cards; the HUD editor has no window.
        assertFalse(read("screen/AuroraTitleScreen.java").contains("parentGlassOnly"));
        assertFalse(read("screen/HudEditorScreen.java").contains("parentGlassOnly"));
    }

    @Test
    void sectionCaptionsRenderAsAuthored() throws Exception {
        assertFalse(read("screen/setting/SectionHeaderSetting.java").contains("toUpperCase"));
        assertFalse(read("screen/setting/FeatureSetting.java").contains("label.toUpperCase"));
    }

    enum Sample { DOT, BRACKETED_TEXT, LEGACY_1_8, SINE_BREATH }
    enum Labelled { A_B; @Override public String toString() { return "Own label"; } }

    @Test
    void dropdownValuesNeverShowRawIdentifiers() throws Exception {
        java.lang.reflect.Method humanize = com.aurora.client.screen.setting.EnumSetting.class
                .getDeclaredMethod("humanize", Enum.class);
        humanize.setAccessible(true);
        assertEquals("Dot", humanize.invoke(null, Sample.DOT));
        assertEquals("Bracketed text", humanize.invoke(null, Sample.BRACKETED_TEXT));
        assertEquals("Legacy 1.8", humanize.invoke(null, Sample.LEGACY_1_8));
        assertEquals("Sine breath", humanize.invoke(null, Sample.SINE_BREATH));
        assertEquals("Own label", humanize.invoke(null, Labelled.A_B));
    }

    @Test
    void onlyALeadingCaptionDropsItsTopBand() {
        SectionHeaderSetting first = new SectionHeaderSetting("General");
        SectionHeaderSetting second = new SectionHeaderSetting("Appearance");
        int normal = second.baseHeight();
        SectionHeaderSetting.markLeading(List.<FeatureSetting>of(first, second));
        assertTrue(first.baseHeight() < normal, "leading caption sheds the band above it");
        assertEquals(normal, second.baseHeight(), "later captions keep group separation");

        SectionHeaderSetting hidden = new SectionHeaderSetting("Hidden");
        hidden.visibleWhen(() -> false);
        SectionHeaderSetting.markLeading(List.<FeatureSetting>of(hidden, first, second));
        assertTrue(first.baseHeight() < normal, "first VISIBLE row decides");
        SectionHeaderSetting.markLeading(List.<FeatureSetting>of(second, first));
        assertEquals(normal, first.baseHeight(), "re-marking clears a stale leading flag");
    }
}
