package com.aurora.client.ui.util;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OwnerMenuGeometryTest {
    @Test void detailIdentityReclaimsTheLogoBandAtTheOrdinaryContentInset() throws Exception {
        for (int[] viewport : new int[][]{{854,480},{640,360},{320,240},{240,180}}) {
            var frame = UiDensity.detailFrame(viewport[0], viewport[1]);
            assertEquals(frame.x() + 12, UiDensity.detailTitleX(frame));
            assertTrue(UiDensity.detailTitleX(frame) < frame.x() + 40);
        }
        String detail = Files.readString(Path.of("src/client/java/com/aurora/client/screen/FeatureDetailScreen.java"));
        assertFalse(detail.contains("AuroraBrand"));
        assertFalse(detail.contains("listX + 40"));
        assertTrue(detail.contains("UiDensity.detailTitleX(compactFrame)"));
        assertTrue(detail.contains("parentGlassOnly() { return true; }"));
    }

    @Test void originalLogoLeadsNarrowerCardsAndAShorterCompleteComposition() {
        var menu = MenuComposition.layout(854,480);
        assertEquals(48, menu.logo().width());
        assertEquals(menu.logo().width(), menu.logo().height());
        assertEquals(48, MenuComposition.LOGO_SIZE);
        assertEquals(186, menu.bounds().height());
        assertTrue(menu.bounds().height() < 5 * 30 + 4 * 6 + 48 + 12);
        assertEquals(126, menu.stack().height());
        assertTrue(menu.stack().height() < 5 * 30 + 4 * 6);
        assertEquals(12, menu.stack().y() - menu.logo().y() - menu.logo().height());
        assertEquals(140, menu.stack().width()); // owner 2026-10-19: narrower again (was 180)
    }

    @Test void allFivePaintAndHitBoundsAreIdenticalAndDisjoint() {
        var menu = MenuComposition.layout(640,360);
        for (int i=0; i<MenuComposition.CARD_COUNT; i++) {
            var card = menu.card(i);
            assertEquals(140,card.width()); assertEquals(22,card.height());
            assertTrue(card.height() < 30);
            assertTrue(card.contains(card.x(),card.y()));
            assertFalse(card.contains(card.x(),card.y()+card.height()));
            if (i > 0) {
                var prev = menu.card(i-1);
                assertEquals(26,card.y()-prev.y());
                assertEquals(4,card.y()-prev.y()-prev.height());
                assertFalse(prev.contains(card.x(),card.y()));
                assertFalse(card.contains(prev.x(),prev.y()+prev.height()-1));
            }
        }
        assertThrows(IndexOutOfBoundsException.class,()->menu.card(5));
    }

    @Test void supportedConstrainedViewportsKeepTheCompleteCompositionCenteredAndInside() {
        for (int width : new int[]{240,320,639,640,854,1280})
            for (int height : new int[]{160,180,240,300,360,480,720}) {
                var menu = MenuComposition.layout(width,height);
                assertTrue(menu.bounds().y() >= MenuComposition.MARGIN);
                assertTrue(menu.bounds().y()+menu.bounds().height() <= height-MenuComposition.MARGIN);
                assertTrue(menu.stack().x() >= MenuComposition.MARGIN);
                assertTrue(menu.stack().x()+menu.stack().width() <= width-MenuComposition.MARGIN);
                assertTrue(Math.abs(2*menu.logo().x()+menu.logo().width()-width)<=1);
                assertTrue(Math.abs(2*menu.stack().x()+menu.stack().width()-width)<=1);
                assertTrue(Math.abs(2*menu.bounds().y()+menu.bounds().height()-height)<=1);
                assertTrue(menu.logo().width() <= 48);
                assertTrue(menu.logo().y()+menu.logo().height() < menu.card(0).y());
            }
    }

    @Test void guiScalesThreeAndFourRetainNativeAssetResolutionAndIntegerEdges() throws Exception {
        var asset = ImageIO.read(Path.of("src/client/resources/assets/aurora/textures/ui/aurora_mark.png").toFile());
        assertEquals(256,asset.getWidth()); assertEquals(asset.getWidth(),asset.getHeight());
        for (int scale : new int[]{3,4}) {
            var menu = MenuComposition.layout((2560+scale-1)/scale,(1412+scale-1)/scale);
            assertEquals(48,menu.logo().width());
            assertTrue(menu.logo().width()*scale <= asset.getWidth());
            for (int i=0;i<5;i++) assertEquals(22*scale,menu.card(i).height()*scale);
        }
    }

    @Test void screenUsesTheSameGeometryForAllButtonsAndTheTexturedUntintedMark() throws Exception {
        String menu = Files.readString(Path.of("src/client/java/com/aurora/client/screen/AuroraTitleScreen.java"));
        assertTrue(menu.contains("MenuComposition.layout(this.width, this.height)"));
        assertTrue(menu.contains("var bounds = composition.card(index)"));
        assertTrue(menu.contains("bounds.width(), bounds.height(), label"));
        assertTrue(menu.contains("mark.x(), mark.y(), mark.width()"));
        String brand = Files.readString(Path.of("src/client/java/com/aurora/client/ui/util/AuroraBrand.java"));
        assertTrue(brand.contains("RenderPipelines.GUI_TEXTURED"));
        assertFalse(brand.contains("ThemeManager"));
        assertFalse(brand.contains("GlassSurface"));
    }
}
