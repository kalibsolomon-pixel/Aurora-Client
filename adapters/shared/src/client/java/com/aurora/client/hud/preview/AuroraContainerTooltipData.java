package com.aurora.client.hud.preview;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Lightweight data carrier produced by Aurora's tooltip mixin and consumed
 * by {@link AuroraContainerTooltipComponent} via Fabric's
 * {@code TooltipComponentCallback}.
 *
 * <p>Holds a snapshot of stacks plus the desired grid layout. Empty stacks
 * are kept so the grid renders contiguous cells; the renderer skips drawing
 * the icon for empty slots.
 */
public final class AuroraContainerTooltipData implements TooltipComponent {

    public final List<ItemStack> stacks;
    public final int columns;
    public final int rows;
    public final boolean showCounts;

    public AuroraContainerTooltipData(List<ItemStack> stacks, int columns, int rows, boolean showCounts) {
        this.stacks = stacks;
        this.columns = Math.max(1, columns);
        this.rows = Math.max(1, rows);
        this.showCounts = showCounts;
    }
}
