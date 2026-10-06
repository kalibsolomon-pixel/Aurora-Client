package com.aurora.client.theme;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Compatibility and capture-ownership boundaries of the controlled pilot. */
class UnifiedPilotContractTest {
    private static final Path SRC=Path.of("src/client/java/com/aurora/client");
    private String read(String path) throws Exception { return Files.readString(SRC.resolve(path)).replace("\r\n","\n"); }

    @ParameterizedTest @EnumSource(GlassStyle.class)
    void existingProfileKeepsAllUserPreferences(GlassStyle style) {
        var gson=new Gson();
        var original=ThemeDefinition.defaults();
        original.glassStyle=style;original.accent=0xff080914;
        original.roundness=ThemeRoundness.SQUARE;original.mode=ThemeMode.LIGHT;
        original.backgroundOpacity=.37;
        String json=gson.toJson(original);
        var profile=new com.google.gson.JsonObject();
        profile.add("theme",com.google.gson.JsonParser.parseString(json));
        var fields=new java.util.HashMap<String,com.google.gson.JsonElement>();
        fields.put("theme",profile.get("theme"));
        ThemeMigrator.migrateProfileFields(fields,org.slf4j.LoggerFactory.getLogger("UnifiedPilotTest"));
        assertEquals(json,gson.toJson(fields.get("theme")));
        var migrated=com.google.gson.JsonParser.parseString(ThemeMigrator.migrateConfigJson(
                gson.toJson(profile),org.slf4j.LoggerFactory.getLogger("UnifiedPilotTest"))).getAsJsonObject();
        var loaded=gson.fromJson(migrated.get("theme"),ThemeDefinition.class);
        ThemeResolver.resolve(loaded,true);
        assertEquals(original.accent,loaded.accent);
        assertEquals(style,loaded.glassStyle);
        assertEquals(original.roundness,loaded.roundness);
        assertEquals(original.mode,loaded.mode);
        assertEquals(.37,loaded.backgroundOpacity);
        assertEquals(json,gson.toJson(loaded),"resolution must never rewrite saved preferences");
    }

    @Test void onlyTheTwoReachablePilotHostsOptIn() throws Exception {
        assertTrue(read("ui/component/ThemedScreen.java").contains("return false;"));
        assertTrue(read("screen/AuroraScreen.java").contains("unifiedThemePilot() { return true; }"));
        assertTrue(read("screen/FeatureDetailScreen.java").contains("unifiedThemePilot() { return \"theme\".equals(meta.id); }"));
        assertFalse(read("ui/component/MaterialSurface.java").contains("renderPanel("));
        for(String host:new String[]{"screen/AuroraScreen.java","screen/FeatureDetailScreen.java"})
            assertTrue(read(host).contains("if(unifiedThemePilot()) MaterialSurface.disposeTemplates();"));
    }

    @Test void embeddedChildrenDeclineBeforeCaptureAndFloatingBackingIsIndependent() throws Exception {
        String glass=read("ui/component/GlassSurface.java");
        assertTrue(glass.contains("if (MaterialSurface.unified()) return false;\n        return paint"));
        assertTrue(glass.contains("return true; // caller's cached parent face owns tint + boundary after dim"));
        String preview=read("screen/setting/ThemePreviewSetting.java");
        int pass=preview.indexOf("public void renderGlassPass(");
        assertTrue(preview.indexOf("if(MaterialSurface.unified()) return;",pass)
                < preview.indexOf("GlassSurface.control",pass));
        String floating=read("ui/component/MaterialSurface.java");
        assertTrue(floating.contains("color(ThemeToken.BORDER_HOVER),4,true,t)"));
        assertTrue(floating.contains("color(ThemeToken.SURFACE_VARIANT)"));
        assertFalse(floating.substring(floating.indexOf("public static void floating")).contains("WINDOW_FILL"));
    }

    @Test void rendererRetainsSafetyGuardsAndBypassesOnlyLegacyStyle() throws Exception {
        String renderer=read("ui/render/blur/BlurPanelRenderer.java");
        assertTrue(renderer.contains("if (!unified && captureSource == null && ThemeManager.current().glassStyle() == GlassStyle.TRANSPARENT)"));
        int body=renderer.indexOf("private static boolean renderPanelInternal(");
        for(String guard:new String[]{"permanentlyDisabled","suppressGlassUntilMs","menuBackdropValid()"})
            assertTrue(renderer.indexOf(guard,body)>body,guard);
        assertTrue(read("ui/component/GlassSurface.java").contains("new BlurPanelRenderer.Lighting(true,0,0)"));
    }
}
