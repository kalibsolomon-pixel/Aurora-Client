package com.aurora.client.ui.util;

import com.aurora.client.SourceTree;
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
        String detail = Files.readString(SourceTree.resolve(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/FeatureDetailScreen.java"));
        assertFalse(detail.contains("AuroraBrand"));
        assertFalse(detail.contains("listX + 40"));
        assertTrue(detail.contains("UiDensity.detailTitleX(compactFrame)"));
        assertTrue(detail.contains("parentGlassOnly() { return true; }"));
    }

    @Test void markAndWordmarkLeadTheCentredColumn() {
        // OneClient-style redesign (owner 2026-11-12): mark, wordmark, three full cards, two half cards.
        var menu = MenuComposition.layout(854,480);
        assertEquals(56, menu.logo().width());
        assertEquals(menu.logo().width(), menu.logo().height());
        assertEquals(MenuComposition.LOGO_GAP, menu.wordmark().y() - menu.logo().y() - menu.logo().height());
        assertEquals(MenuComposition.WORDMARK_HEIGHT, menu.wordmark().height());
        assertEquals(MenuComposition.BRAND_GAP, menu.stack().y() - menu.wordmark().y() - menu.wordmark().height());
        assertEquals(200, menu.stack().width());
        assertEquals(4 * 24 + 3 * 6, menu.stack().height());
        assertEquals(menu.stack().height() + 56 + 6 + 20 + 14, menu.bounds().height());
        assertFalse(menu.compact(), "a 854-wide window has room for the right column");
    }

    @Test void cardsAndHalfCardsPaintAndHitTheSameDisjointBounds() {
        var menu = MenuComposition.layout(640,360);
        for (int i=0; i<MenuComposition.FULL_CARDS; i++) {
            var card = menu.card(i);
            assertEquals(200,card.width()); assertEquals(24,card.height());
            assertTrue(card.contains(card.x(),card.y()));
            assertFalse(card.contains(card.x(),card.y()+card.height()));
            if (i > 0) assertEquals(6, card.y() - menu.card(i-1).y() - menu.card(i-1).height());
        }
        var settings = menu.card(3); var mods = menu.card(4);
        assertEquals(settings.y(), mods.y(), "Settings and Mods share a row");
        assertEquals(settings.width(), mods.width());
        assertEquals(MenuComposition.CARD_GAP, mods.x() - settings.x() - settings.width());
        assertEquals(menu.stack().x(), settings.x());
        assertEquals(menu.stack().x() + menu.stack().width(), mods.x() + mods.width());
        assertEquals(6, settings.y() - menu.card(2).y() - menu.card(2).height());
        assertThrows(IndexOutOfBoundsException.class,()->menu.card(5));
        assertThrows(IndexOutOfBoundsException.class,()->menu.icon(3));
    }

    @Test void supportedConstrainedViewportsKeepTheCompleteCompositionCenteredAndInside() {
        for (int width : new int[]{240,320,550,559,560,639,640,854,1280})
            for (int height : new int[]{160,180,240,300,360,480,720}) {
                var menu = MenuComposition.layout(width,height);
                String at = width + "x" + height;
                assertTrue(menu.bounds().y() >= MenuComposition.MARGIN, at);
                assertTrue(menu.bounds().y()+menu.bounds().height() <= height-MenuComposition.MARGIN, at);
                assertTrue(menu.stack().x() >= MenuComposition.MARGIN, at);
                assertTrue(menu.stack().x()+menu.stack().width() <= width-MenuComposition.MARGIN, at);
                assertTrue(Math.abs(2*menu.logo().x()+menu.logo().width()-width)<=1, at);
                assertTrue(Math.abs(2*menu.stack().x()+menu.stack().width()-width)<=1, at);
                assertTrue(Math.abs(2*menu.bounds().y()+menu.bounds().height()-height)<=1, at);
                assertTrue(menu.logo().width() <= 56, at);
                if (menu.logo().width() > 0) assertTrue(menu.logo().y()+menu.logo().height() < menu.card(0).y(), at);
                if (menu.wordmark().height() > 0) assertTrue(menu.wordmark().y()+menu.wordmark().height() < menu.card(0).y(), at);
                var icons = menu.icons();
                assertEquals(width - MenuComposition.MARGIN, icons.x() + icons.width(), at);
                if (height - 2 * MenuComposition.TOP_BAND >= MenuComposition.STACK_HEIGHT)
                    assertTrue(menu.bounds().y() >= icons.y() + icons.height(), at + ": the column clears the icon row");
                if (!menu.compact()) {
                    var model = menu.model(); var account = menu.account();
                    assertTrue(model.x() >= menu.stack().x() + menu.stack().width() + MenuComposition.COLUMN_GAP, at);
                    assertTrue(account.x() + account.width() <= width - MenuComposition.MARGIN, at);
                    assertEquals(model.y() + model.height() + MenuComposition.SIDE_GAP, account.y(), at);
                    assertTrue(model.y() >= icons.y() + icons.height(), at);
                    assertTrue(account.y() + account.height() <= height - MenuComposition.MARGIN, at);
                } else {
                    assertEquals(0, menu.model().width(), at + ": no model when compact");
                    var chip = menu.account();
                    if (chip.width() > 0) {
                        assertEquals(icons.y(), chip.y(), at);
                        assertTrue(chip.x() + chip.width() <= icons.x(), at);
                        assertTrue(chip.x() >= MenuComposition.MARGIN, at);
                    }
                }
            }
        assertTrue(MenuComposition.layout(550, 480).compact(), "about 560 wide and narrower the right column becomes the top-bar chip");
        assertFalse(MenuComposition.layout(560, 480).compact());
        assertFalse(MenuComposition.layout(640, 360).compact());
    }

    @Test void rightColumnStaysNearTheButtonsOnWideWindows() {
        // Owner 2026-11-12: on a wide monitor the model sat stranded at the window edge.
        for (int[] v : new int[][]{{859,486},{1289,729},{1920,540}}) {
            var menu = MenuComposition.layout(v[0], v[1]);
            assertFalse(menu.compact());
            int columnRight = menu.stack().x() + menu.stack().width();
            int before = menu.account().x() - columnRight;
            int after = v[0] - MenuComposition.MARGIN - menu.account().x() - menu.account().width();
            assertTrue(Math.abs(before - after) <= 1, v[0] + ": centred in the space right of the column");
        }
    }

    @Test void guiScalesThreeAndFourRetainNativeAssetResolutionAndIntegerEdges() throws Exception {
        var asset = ImageIO.read(SourceTree.resolve(SourceTree.ADAPTER + "/src/client/resources/assets/aurora/textures/ui/aurora_mark.png").toFile());
        assertEquals(256,asset.getWidth()); assertEquals(asset.getWidth(),asset.getHeight());
        for (int scale : new int[]{3,4}) {
            var menu = MenuComposition.layout((2560+scale-1)/scale,(1412+scale-1)/scale);
            assertEquals(56,menu.logo().width());
            assertTrue(menu.logo().width()*scale <= asset.getWidth());
            for (int i=0;i<5;i++) assertEquals(24*scale,menu.card(i).height()*scale);
        }
    }

    @Test void screenUsesTheSameGeometryForAllButtonsAndTheTexturedUntintedMark() throws Exception {
        String menu = Files.readString(SourceTree.resolve(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/screen/AuroraTitleScreen.java"));
        assertTrue(menu.contains("MenuComposition.layout(this.width, this.height)"));
        assertTrue(menu.contains("var bounds = composition.card(index)"));
        assertTrue(menu.contains("bounds.width(), bounds.height(), label"));
        assertTrue(menu.contains("mark.x(), mark.y(), mark.width()"));
        String brand = Files.readString(SourceTree.resolve(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/ui/util/AuroraBrand.java"));
        assertTrue(brand.contains("RenderPipelines.GUI_TEXTURED"));
        assertFalse(brand.contains("ThemeManager"));
        assertFalse(brand.contains("GlassSurface"));
    }
}
