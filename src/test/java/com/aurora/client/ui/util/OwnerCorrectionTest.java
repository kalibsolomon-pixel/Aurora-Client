package com.aurora.client.ui.util;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OwnerCorrectionTest {
    private String source(String path) throws Exception {
        return Files.readString(Path.of("src/client/java/com/aurora/client/" + path));
    }
    @Test void nativeInkSurvivesOddBoundsAtEverySupportedScale() {
        for (int scale = 1; scale <= 6; scale++) for (int ink = 1; ink <= 192; ink++) {
            int canvas = NativeIconGeometry.canvasSize(ink, scale);
            assertTrue(canvas >= ink);
            assertTrue(canvas - ink < 2 * scale);
            assertEquals(0, canvas % (2 * scale));
            assertEquals(canvas, (canvas / scale) * scale);
        }
    }
    @Test void nativeBlitUsesPaddedRastersAndBothAddOwnersUseAntialiasedSurfaces() throws Exception {
        String renderer = source("ui/util/MaterialIconRenderer.java");
        assertTrue(renderer.contains("NativeIconGeometry.canvasSize(w, cachedGuiScale)"));
        assertTrue(renderer.contains("int guiW = r.width() / guiScale"));
        assertFalse(renderer.contains("Math.round(guiW)"));
        for (String owner : new String[]{"ItemScaleSetting", "EffectExpiryListSetting"}) {
            String row = source("screen/setting/" + owner + ".java");
            assertTrue(row.contains("addAction.paintGlyph(ctx, tr, plusX, plusY, 24, 20)"));
            assertTrue(row.contains("drawRoundedRectAA(ctx, plusX, plusY, 24, 20"));
            assertFalse(row.contains("AuroraShapes.panel(ctx, plusX"));
        }
    }
    @Test void mainKeepsTextOnlyBrandAndSquareCardsWithoutSeparateConfigureTargets() throws Exception {
        assertEquals(UiDensity.CARD_WIDTH, UiDensity.CARD_HEIGHT);
        assertEquals(6, UiDensity.GRID_GAP);
        String main = source("screen/AuroraScreen.java");
        assertTrue(main.contains("g.drawString(tr, \"AURORA\""));
        assertFalse(main.contains("AuroraBrand.draw"));
        assertFalse(main.contains("configureActions"));
        assertFalse(main.contains("bx + 72, by + 36, 1"));
        assertTrue(main.contains("cx + cw / 2f, cy + 30, 28f"));
        assertTrue(main.contains("button == 1"));
        assertTrue(main.contains("new FeatureDetailScreen(this, fm)"));
    }
    @Test void sharedDetailsUseMeasuredLeftLabelsAndNestedItemSlidersUseActualHeights() throws Exception {
        String host = source("screen/FeatureDetailScreen.java");
        assertTrue(host.contains("private boolean compact() { return true; }"));
        assertTrue(host.contains("setting.measureCompact(LIST_W)"));
        assertTrue(host.contains("this.font, meta.displayName,"));
        String row = source("screen/setting/FeatureSetting.java");
        assertTrue(row.contains("int textX = x + box.x()"));
        assertTrue(row.contains("drawValueLine(ctx, x + box.x()"));
        assertFalse(row.contains("drawCenteredLines(font, ctx, wrappedLabel"));
        String toggle = source("screen/setting/BooleanSetting.java");
        assertTrue(toggle.contains("int lineX = x + LABEL_PAD;"));
        assertFalse(toggle.contains("drawValueLineCentered(ctx, x + LABEL_PAD"));
        String items = source("screen/setting/ItemScaleSetting.java");
        assertFalse(items.contains("7 * 36"));
        assertTrue(items.contains("getOrCreateSliders(id)) h += slider.height()"));
        assertTrue(items.contains("slider.clearCompactMeasurement()"));
    }
    @Test void statusSubtitlesFitAlongsideAnUnchangedSwitchAndLabelInset() {
        var row = SettingRowLayout.measure(SettingRowLayout.Kind.BOOLEAN, "Alert Delays", 344, 9, s -> s.length()*6)
                .withSubtitle(12, 9);
        assertTrue(row.height() >= 30);
        assertEquals(12, row.label().x());
        assertEquals(28, row.control().width()); assertEquals(15, row.control().height());
        assertTrue(row.controlHit().y()+row.controlHit().height() <= row.height());
        assertTrue(row.label().width()+row.label().x() < row.control().x());
    }
    @Test void mainMenuMarkStandsAboveTheCardsAndButtonsStayTextOnly() throws Exception {
        String menu = source("screen/AuroraTitleScreen.java");
        assertTrue(menu.contains("stackTop() - BRAND_GAP - mark"));
        assertTrue(menu.contains("(this.width - mark) / 2"));
        assertTrue(menu.contains("AuroraBrand.draw(ctx"));
        assertFalse(menu.contains("auroraBrand(true)"));
        assertFalse(source("ui/component/Button.java").contains("auroraBrand"));
        assertFalse(source("ui/component/ButtonWidget.java").contains("auroraBrand"));
    }
}
