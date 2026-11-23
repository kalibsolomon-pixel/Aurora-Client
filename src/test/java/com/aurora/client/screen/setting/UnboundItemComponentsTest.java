package com.aurora.client.screen.setting;

import com.aurora.client.SourceTree;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards against {@code NullPointerException: Components not bound yet} when
 * UI screens or HUD previews run before a world/server binds item registry
 * data components (for example on the title screen or during a reload).
 */
class UnboundItemComponentsTest {

    private static String readShared(String path) throws Exception {
        return Files.readString(SourceTree.resolve(SourceTree.ADAPTER + "/src/client/java/com/aurora/client/" + path));
    }

    @Test
    void itemScaleSettingGuardsEveryDefaultInstanceAndUsesSafeIdParsing() throws Exception {
        String src = readShared("screen/setting/ItemScaleSetting.java");

        assertTrue(src.contains("holder.areComponentsBound()"),
                "ItemScaleSetting must check areComponentsBound() before building a default ItemStack");
        assertTrue(src.contains("Component.translatable(item.getDescriptionId()).getString()"),
                "ItemScaleSetting must fall back to the item's description id when components are unbound");
        assertTrue(src.contains("Identifier.tryParse(idStr)"),
                "ItemScaleSetting must parse configured item IDs with Identifier.tryParse");
        assertFalse(src.contains("Identifier.parse(idStr)"),
                "ItemScaleSetting must not throw on malformed configured item IDs");

        // Only defaultStackOrEmpty may call getDefaultInstance() directly.
        int first = src.indexOf(".getDefaultInstance()");
        int last = src.lastIndexOf(".getDefaultInstance()");
        assertTrue(first > 0 && first == last,
                "All getDefaultInstance() calls in ItemScaleSetting must route through defaultStackOrEmpty");
    }

    @Test
    void inventoryModuleGuardsSampleStacksBeforeComponentsBind() throws Exception {
        String src = readShared("hud/module/InventoryModule.java");

        int guardIdx = src.indexOf("holder.areComponentsBound()");
        int allocIdx = src.indexOf("new ItemStack(Items.DIAMOND_PICKAXE)");
        assertTrue(guardIdx > 0 && guardIdx < allocIdx,
                "InventoryModule.sampleStacks() must check areComponentsBound() before allocating sample ItemStacks");
        assertTrue(src.contains("return emptySample;"),
                "InventoryModule must return an uncached empty fallback while components are unbound");
    }

    @Test
    void shieldStatusFeatureGuardsDefaultInstance() throws Exception {
        String src = readShared("feature/impl/ShieldStatusFeature.java");

        assertTrue(src.contains("shieldHolder.areComponentsBound()"),
                "ShieldStatusFeature must check areComponentsBound() before calling Items.SHIELD.getDefaultInstance()");
    }

    @Test
    void nullItemHelpersReturnSafeDefaults() {
        assertEquals("", ItemScaleSetting.displayName(null));
    }
}
