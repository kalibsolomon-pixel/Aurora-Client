package com.aurora.client.hud.preview;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Renders a grid of items as part of an item's tooltip image, used by the
 * Aurora Container Preview feature for shulker boxes, ender chests, and
 * any other BlockItem carrying a {@code DataComponents.CONTAINER} payload.
 *
 * <p>Layout matches vanilla inventory slots (18 × 18 each) with a thin
 * frame around the grid for visual separation from the surrounding
 * tooltip text. The frame is drawn with {@link GuiGraphics#fill} so it
 * works under the same rendering pipeline as vanilla tooltips and
 * doesn't need a sprite resource.
 */
public final class AuroraContainerTooltipComponent implements ClientTooltipComponent {

    private static final int SLOT = 18;
    private static final int PAD  = 1;
    private static final int FRAME_BG    = 0xC0101018; // dark translucent
    private static final int FRAME_LIGHT = 0x40FFFFFF; // soft inner highlight

    private final AuroraContainerTooltipData data;

    public AuroraContainerTooltipComponent(AuroraContainerTooltipData data) {
        this.data = data;
    }

    @Override
    public int getHeight(Font font) {
        return data.rows * SLOT + PAD * 2 + 2;
    }

    @Override
    public int getWidth(Font font) {
        return data.columns * SLOT + PAD * 2;
    }

    @Override
    public void renderImage(Font font, int x, int y, int w, int h, GuiGraphics g) {
        int gridW = data.columns * SLOT + PAD * 2;
        int gridH = data.rows * SLOT + PAD * 2;

        // Outer frame
        g.fill(x, y, x + gridW, y + gridH, FRAME_BG);
        // Subtle 1px inner highlight on top edge for depth
        g.fill(x + 1, y + 1, x + gridW - 1, y + 2, FRAME_LIGHT);

        List<ItemStack> stacks = data.stacks;
        int total = data.columns * data.rows;
        for (int i = 0; i < total; i++) {
            int col = i % data.columns;
            int row = i / data.columns;
            int sx = x + PAD + col * SLOT + 1;
            int sy = y + PAD + row * SLOT + 1;
            ItemStack stack = i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY;
            if (stack == null || stack.isEmpty()) continue;
            g.renderItem(stack, sx, sy, i);
            // Always draw durability bar / cooldown overlays. The "Show
            // Stack Counts" toggle gates only the count text — passing an
            // empty override to renderItemDecorations suppresses the count
            // while leaving the bar/cooldown intact.
            g.renderItemDecorations(font, stack, sx, sy,
                    data.showCounts ? null : "");
        }
    }
}
