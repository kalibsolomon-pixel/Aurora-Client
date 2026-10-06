package com.aurora.client.theme;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Migration and capture-ownership boundaries of the production rollout. */
class UnifiedPilotContractTest {
    private static final Path SRC=Path.of("src/client/java/com/aurora/client");
    private String read(String path) throws Exception { return Files.readString(SRC.resolve(path)).replace("\r\n","\n"); }

    @ParameterizedTest @ValueSource(strings={"FROSTED","TRANSPARENT"})
    void legacyConfigAndProfileDropOnlyRetiredStyle(String style) {
        var gson=new Gson();
        var original=ThemeDefinition.defaults();
        original.accent=0xff080914;
        original.roundness=ThemeRoundness.SQUARE;original.mode=ThemeMode.LIGHT;
        original.backgroundOpacity=.37;
        String json=gson.toJson(original);
        var profile=new com.google.gson.JsonObject();
        profile.add("theme",com.google.gson.JsonParser.parseString(json));
        profile.getAsJsonObject("theme").addProperty("glassStyle",style);
        profile.addProperty("zoomEnabled",false);
        String legacyJson=gson.toJson(profile);
        var fields=new java.util.HashMap<String,com.google.gson.JsonElement>();
        fields.put("theme",profile.get("theme"));
        ThemeMigrator.migrateProfileFields(fields,org.slf4j.LoggerFactory.getLogger("UnifiedPilotTest"));
        assertEquals(json,gson.toJson(fields.get("theme")));
        var migrated=com.google.gson.JsonParser.parseString(ThemeMigrator.migrateConfigJson(
                legacyJson,org.slf4j.LoggerFactory.getLogger("UnifiedPilotTest"))).getAsJsonObject();
        var loaded=gson.fromJson(migrated.get("theme"),ThemeDefinition.class);
        ThemeResolver.resolve(loaded,true);
        assertEquals(original.accent,loaded.accent);
        assertFalse(migrated.getAsJsonObject("theme").has("glassStyle"));
        assertFalse(migrated.get("zoomEnabled").getAsBoolean());
        assertEquals(original.roundness,loaded.roundness);
        assertEquals(original.mode,loaded.mode);
        assertEquals(.37,loaded.backgroundOpacity);
        assertEquals(json,gson.toJson(loaded),"resolution must never rewrite saved preferences");
    }

    @Test void everyProductionAuroraHostUsesTheApprovedMaterial() throws Exception {
        assertTrue(read("ui/component/ThemedScreen.java").contains("default boolean unifiedTheme() { return true; }"));
        assertTrue(read("screen/AuroraScreen.java").contains("unifiedTheme() { return true; }"));
        assertTrue(read("screen/FeatureDetailScreen.java").contains("unifiedTheme() { return true; }"));
        assertFalse(read("ui/component/MaterialSurface.java").contains("renderPanel("));
        for(String host:new String[]{"screen/AuroraScreen.java","screen/FeatureDetailScreen.java"})
            assertTrue(read(host).contains("if(unifiedTheme()) MaterialSurface.disposeTemplates();"));
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

    @Test void outsideFocusKeepsTheSquareCornerContract() {
        for(ThemeRoundness style:ThemeRoundness.values()) {
            float radius=style.radiusSmall();
            float actual=com.aurora.client.ui.component.MaterialSurface.outsetRadius(radius,2);
            assertEquals(style==ThemeRoundness.SQUARE ? 0 : radius+2,actual);
        }
    }

    @Test void densityExposesBackdropWithoutChangingSavedOpacity() {
        int previous=-1;
        for(int alpha=0;alpha<=255;alpha++) {
            int density=ThemeResolver.backgroundAlpha(alpha/255d);
            assertTrue(density>=previous);
            assertTrue(density<=alpha);
            previous=density;
        }
        assertEquals(0,ThemeResolver.backgroundAlpha(0));
        assertEquals(173,ThemeResolver.backgroundAlpha(1));
        for(ThemeMode mode:ThemeMode.values()) {
            var original=ThemeDefinition.defaults(); original.mode=mode;
            for(double opacity:new double[]{0,.37,1}) {
                original.backgroundOpacity=opacity;
                var resolved=ThemeResolver.resolve(original,true);
                assertEquals(Math.round((float)opacity*255f*.68f),resolved.color(ThemeToken.WINDOW_FILL)>>>24);
                assertEquals(opacity,original.backgroundOpacity);
            }
        }
    }

    @Test void rendererRetainsSafetyGuardsAfterStyleRemoval() throws Exception {
        String renderer=read("ui/render/blur/BlurPanelRenderer.java");
        assertFalse(renderer.contains("GlassStyle.TRANSPARENT"));
        int body=renderer.indexOf("private static boolean renderPanelInternal(");
        for(String guard:new String[]{"permanentlyDisabled","suppressGlassUntilMs","menuBackdropValid()"})
            assertTrue(renderer.indexOf(guard,body)>body,guard);
        assertTrue(read("ui/component/GlassSurface.java").contains("new BlurPanelRenderer.Lighting(true,0,0)"));
        assertTrue(renderer.contains("unified ? 1.35f : 1f, unified ? .92f : 1f"),
                "filter state must explicitly reset for compatibility hosts");
    }
}
