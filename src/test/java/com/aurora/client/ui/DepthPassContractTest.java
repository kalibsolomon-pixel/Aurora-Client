package com.aurora.client.ui;

import com.aurora.client.SourceTree;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

/** Binding owner rulings, 2026-11-17; legacy and explicitly themed paths remain available. */
class DepthPassContractTest {
    private static String read(String path) throws Exception {
        return Files.readString(SourceTree.resolve(SourceTree.ADAPTER+"/src/client/java/com/aurora/client/"+path));
    }

    @Test void moduleStateIsOpaqueElevationAndHoverNeverAddsAnOutline() throws Exception {
        String screen=read("screen/AuroraScreen.java"), material=read("ui/component/MaterialSurface.java");
        assertTrue(screen.contains("MaterialSurface.module(g,cx,cy,cw,ch,tileRadius,on,hoverT)"));
        assertFalse(screen.contains("TILE_EDGE_REST"));
        assertTrue(screen.contains("Math.max(hoverT, control != null && control.isFocused() ? 1f : 0f)"),
                "settings chevrons stay hover/focus-only");
        String module=material.substring(material.indexOf("public static void module("),material.indexOf("public static void peerHover("));
        assertTrue(module.contains("withAlpha(ThemeManager.color(ThemeToken.MATERIAL_RAISED),255)"));
        assertFalse(module.contains("ACCENT"));
        assertFalse(module.contains("selection("));
        assertTrue(material.contains("MODULE_ON(5,-3,0x1affffff,0x38000000)"));
        assertTrue(material.contains("MODULE_OFF(-3,-3,0,0)"));
        assertTrue(material.contains("MODULE_ON_HOVER(7,-1,0x1affffff,0x38000000)"));
        assertTrue(material.contains("MODULE_OFF_HOVER(-1,-1,0,0)"));
    }

    @Test void outlineAttenuationIsIndependentOfWindowOpacity() throws Exception {
        String material=read("ui/component/MaterialSurface.java");
        String outline=material.substring(material.indexOf("public static int windowOutline()"),material.indexOf("private enum Light"));
        assertTrue(material.contains("WINDOW_OUTLINE_ALPHA = .55f"));
        assertTrue(outline.contains("token >>> 24"));
        assertFalse(outline.contains("WINDOW_FILL"));
        assertTrue(read("screen/AuroraScreen.java").contains("MaterialSurface.windowOutline()"));
        assertTrue(read("ui/component/RoundedPanel.java").contains("MaterialSurface.windowOutline()"));
    }

    @Test void pickOneWellAndIndependentCheckedChipsHaveDistinctStructure() throws Exception {
        String segments=read("ui/component/SegmentedControl.java");
        assertTrue(segments.contains("if(MaterialSurface.unified() && toggledOn==null)"));
        assertTrue(segments.contains("MaterialSurface.inset(g,x,y,w,trackH,radius,disabled)"));
        assertTrue(segments.contains("MaterialSurface.control(g,sx+1,y+1,sw-2,trackH-2,"));
        assertTrue(segments.contains("if(toggledOn!=null) MaterialSurface.control(g,sx,y,sw,trackH"));
        assertTrue(segments.contains("IconCatalog.glyph(\"check\")"));
        assertTrue(segments.contains("boolean checked=toggledOn!=null && selected"));
        assertTrue(segments.contains("text -> font.width(AuroraFontRenderer.verbatim(text))"));
    }

    @Test void controlsUseCachedRasterStatesAndRecipeIsPartOfKey() throws Exception {
        String material=read("ui/component/MaterialSurface.java");
        assertTrue(material.contains("kind += light.ordinal()*1024"));
        assertTrue(material.contains("+scale)*31+fill)*31+border"));
        assertTrue(material.contains("cache.lightVertical("));
        assertTrue(material.contains("cache.edgeLine("));
        assertTrue(material.contains("cache.softShadow("));
        String states=material.substring(material.indexOf("public static void controlColors("),material.indexOf("public static void inset("));
        assertFalse(states.contains("drawRoundedRect"));
        assertFalse(states.contains("drawRoundedOutline"));
        assertTrue(states.contains("Light.PRESSED"));
        assertTrue(states.contains("int edge=selected && !disabled ?"),
                "disabled selected controls are flat without an accent edge");
        assertTrue(material.contains("if(shadow && kind!=120 && light==Light.FLAT)"),
                "window body never receives the old panel gradient");
    }

    @Test void fieldsAndKnobsPreserveSearchAndDataBoundaries() throws Exception {
        String edit=read("mixin/EditBoxMixin.java"), slider=read("ui/component/Slider.java");
        assertTrue(edit.contains("boolean opaqueInset = MaterialSurface.unified() && !aurora$search()"));
        assertTrue(edit.contains("boolean glassOk = !opaqueInset"));
        assertTrue(edit.contains("MaterialSurface.inset(ctx,x,y,w,h,radius,!self.isActive())"));
        assertTrue(slider.contains("MaterialSurface.knob(g,knobX-knobR,knobY-knobR,knobR*2"));
        assertTrue(slider.contains("MaterialSurface.inset(g,x,ty,w,TRACK_H,trackRadius(),disabled)"));
        assertTrue(read("ui/component/ToggleSwitch.java").contains("MaterialSurface.switchTrack(g,x,y,w,h,r,t,hover,disabled)"));
        assertTrue(read("screen/setting/EnumSetting.java").contains("MaterialSurface.unified() ? btnX + 6"));
    }
}
