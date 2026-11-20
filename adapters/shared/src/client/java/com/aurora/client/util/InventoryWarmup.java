package com.aurora.client.util;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;

public final class InventoryWarmup {
    private InventoryWarmup() {}

    public static void preload() {
        if (!AuroraConfig.get().perfPreloadInventory) return;
        ClassLoader loader = InventoryWarmup.class.getClassLoader();
        // Survival inventory resolves the creative screen to check whether it should redirect.
        // Class loading applies Fabric's GUI mixins. Leave initialization to the first real use.
        for (String name : new String[] {
                "net.minecraft.client.gui.screens.inventory.InventoryScreen",
                "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen",
                "net.minecraft.client.gui.screens.inventory.EffectsInInventory",
                "net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent",
                "net.minecraft.client.gui.screens.recipebook.GhostSlots",
                "net.minecraft.client.gui.screens.recipebook.RecipeBookPage",
                "net.minecraft.client.gui.screens.recipebook.RecipeButton",
                "net.minecraft.client.gui.render.pip.GuiEntityRenderer"
        }) {
            try {
                Class.forName(name, false, loader);
            } catch (ClassNotFoundException | LinkageError failure) {
                AuroraClient.LOGGER.debug("Inventory warmup could not link {}", name, failure);
            }
        }
    }
}
