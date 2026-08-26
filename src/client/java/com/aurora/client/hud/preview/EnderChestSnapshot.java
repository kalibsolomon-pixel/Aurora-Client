package com.aurora.client.hud.preview;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Client-side cache of the local player's ender chest contents.
 *
 * <p>On 1.21.x the container backing the open ender chest screen is a fresh
 * {@code SimpleContainer} created when the screen opens, not the
 * {@code PlayerEnderChestContainer} attached to {@code Player}, so reading
 * {@code player.getEnderChestInventory()} on the client always returns
 * empty stacks. This class captures the live menu's contents while the
 * ender chest screen is open (see {@code AuroraClient} tick hook) and
 * exposes a stable snapshot for the tooltip mixin.
 */
public final class EnderChestSnapshot {

    private static final ItemStack[] CACHE = new ItemStack[27];
    private static boolean valid = false;

    static { Arrays.fill(CACHE, ItemStack.EMPTY); }

    private EnderChestSnapshot() {}

    /** Mirror the chest container's 27 slots into the cache. */
    public static void capture(ChestMenu menu) {
        if (menu.getRowCount() != 3) return;
        Container c = menu.getContainer();
        if (c.getContainerSize() < 27) return;
        for (int i = 0; i < 27; i++) {
            CACHE[i] = c.getItem(i).copy();
        }
        valid = true;
    }

    /** Read-only view of the most recently captured slots. */
    public static List<ItemStack> view() {
        return Collections.unmodifiableList(new ArrayList<>(Arrays.asList(CACHE)));
    }

    public static boolean hasData() { return valid; }
}
