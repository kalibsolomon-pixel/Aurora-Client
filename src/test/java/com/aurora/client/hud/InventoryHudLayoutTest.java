package com.aurora.client.hud;

import com.aurora.client.SourceTree;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.FeatureCatalog;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and source-contract coverage for the Inventory HUD module.
 */
class InventoryHudLayoutTest {

    @Test
    void fixedGridDimensionsMatchVanillaInventoryPanel() {
        // 9x3 main inventory: 162x54 grid, plus 7px vanilla panel border on each edge = 176x68.
        assertEquals(162, InventoryHudLayout.width(false, 0, false));
        assertEquals(54, InventoryHudLayout.height(false, 0, false, false));
        assertEquals(176, InventoryHudLayout.width(false, 0, true));
        assertEquals(68, InventoryHudLayout.height(false, 0, false, true));

        // With hotbar: adds 4px gap + 18px row = 76 grid / 90 vanilla panel.
        assertEquals(162, InventoryHudLayout.width(false, 27, false));
        assertEquals(76, InventoryHudLayout.height(false, 27, true, false));
        assertEquals(176, InventoryHudLayout.width(false, 27, true));
        assertEquals(90, InventoryHudLayout.height(false, 27, true, true));
    }

    @Test
    void compactDimensionsShrinkToOccupiedItems() {
        // Empty keeps a single 1x1 cell so the HUD editor frame stays visible and draggable.
        assertEquals(1, InventoryHudLayout.compactCols(0));
        assertEquals(1, InventoryHudLayout.compactRows(0));
        assertEquals(18, InventoryHudLayout.width(true, 0, false));
        assertEquals(18, InventoryHudLayout.height(true, 0, false, false));
        assertEquals(32, InventoryHudLayout.width(true, 0, true));
        assertEquals(32, InventoryHudLayout.height(true, 0, false, true));

        // 5 items -> single row of 5
        assertEquals(5, InventoryHudLayout.compactCols(5));
        assertEquals(1, InventoryHudLayout.compactRows(5));
        assertEquals(90, InventoryHudLayout.width(true, 5, false));
        assertEquals(18, InventoryHudLayout.height(true, 5, false, false));

        // 10 items -> 9 cols x 2 rows
        assertEquals(9, InventoryHudLayout.compactCols(10));
        assertEquals(2, InventoryHudLayout.compactRows(10));
        assertEquals(162, InventoryHudLayout.width(true, 10, false));
        assertEquals(36, InventoryHudLayout.height(true, 10, false, false));
        assertEquals(176, InventoryHudLayout.width(true, 10, true));
        assertEquals(50, InventoryHudLayout.height(true, 10, false, true));

        // 36 items -> 9 cols x 4 rows (no hotbar gap in compact packing)
        assertEquals(9, InventoryHudLayout.compactCols(36));
        assertEquals(4, InventoryHudLayout.compactRows(36));
        assertEquals(72, InventoryHudLayout.height(true, 36, true, false));
    }

    @Test
    void inventorySlotIndexMapsMainThenHotbar() {
        assertEquals(27, InventoryHudLayout.slotCount(false));
        assertEquals(36, InventoryHudLayout.slotCount(true));

        // Display 0..26 -> Inventory 9..35
        assertEquals(9, InventoryHudLayout.inventorySlotAt(0));
        assertEquals(18, InventoryHudLayout.inventorySlotAt(9));
        assertEquals(35, InventoryHudLayout.inventorySlotAt(26));

        // Display 27..35 -> Hotbar 0..8
        assertEquals(0, InventoryHudLayout.inventorySlotAt(27));
        assertEquals(8, InventoryHudLayout.inventorySlotAt(35));

        assertThrows(IllegalArgumentException.class, () -> InventoryHudLayout.inventorySlotAt(-1));
        assertThrows(IllegalArgumentException.class, () -> InventoryHudLayout.inventorySlotAt(36));
    }

    @Test
    void slotCoordinatesRespectVanillaBorderAndHotbarGap() {
        assertEquals(0, InventoryHudLayout.fixedSlotX(0, false));
        assertEquals(0, InventoryHudLayout.fixedSlotY(0, false));
        assertEquals(7, InventoryHudLayout.fixedSlotX(0, true));
        assertEquals(7, InventoryHudLayout.fixedSlotY(0, true));

        // Second row, column 4
        assertEquals(7 + 4 * 18, InventoryHudLayout.fixedSlotX(13, true));
        assertEquals(7 + 18, InventoryHudLayout.fixedSlotY(13, true));

        // Hotbar row (display index 27..35) sits 4px below the 3rd main row
        assertEquals(7, InventoryHudLayout.fixedSlotX(27, true));
        assertEquals(7 + 54 + 4, InventoryHudLayout.fixedSlotY(27, true));
        assertEquals(54 + 4, InventoryHudLayout.fixedSlotY(27, false));

        // Compact packing coordinates
        assertEquals(7, InventoryHudLayout.compactSlotX(0, true));
        assertEquals(7, InventoryHudLayout.compactSlotY(0, true));
        assertEquals(7 + 18, InventoryHudLayout.compactSlotX(10, true));
        assertEquals(7 + 18, InventoryHudLayout.compactSlotY(10, true));
    }

    @Test
    void defaultsAndCatalogAreOffAndVanillaInformation() {
        AuroraConfig cfg = new AuroraConfig();
        assertFalse(cfg.inventoryHudEnabled);
        assertEquals(AuroraConfig.HudBackground.VANILLA, cfg.inventoryHudBgMode);
        assertEquals(0x80000000, cfg.inventoryHudBgColor);
        assertTrue(cfg.inventoryHudShowCounts);
        assertFalse(cfg.inventoryHudShowHotbar);
        assertFalse(cfg.inventoryHudCompact);
        assertFalse(cfg.inventoryHudHideWhenEmpty);

        FeatureCatalog.Feature feature = FeatureCatalog.require("inventory_hud");
        assertEquals(FeatureCatalog.Tab.MODULES, feature.tab());
        assertEquals(FeatureCatalog.FairPlay.VANILLA_INFORMATION, feature.fairPlay());
        assertTrue(feature.offByDefault().contains("inventoryHudEnabled"));
    }

    @Test
    void everyMinecraftAdapterRegistersInventoryModule() throws Exception {
        for (String adapter : List.of("adapters/minecraft-26.1", "adapters/minecraft-26.2", "adapters/minecraft-26.3")) {
            String src = Files.readString(SourceTree.resolve(
                    adapter + "/src/client/java/com/aurora/client/AuroraClient.java"));
            assertTrue(src.contains("new InventoryModule()"),
                    adapter + " must register InventoryModule in HudModuleManager");
        }
    }
}
