package com.aurora.client.hud;

/**
 * Pure layout geometry and slot indexing for the Inventory HUD module.
 *
 * <p>Lives in the Minecraft-free core (ADR 0003) so fixed-grid, hotbar-row,
 * compact-packing, and vanilla-border sizing can be unit-tested without a
 * Minecraft client.
 */
public final class InventoryHudLayout {
    public static final int COLUMNS = 9;
    public static final int MAIN_ROWS = 3;
    public static final int MAIN_START = 9;
    public static final int MAIN_COUNT = COLUMNS * MAIN_ROWS;
    public static final int HOTBAR_START = 0;
    public static final int HOTBAR_COUNT = COLUMNS;
    public static final int TOTAL_WITH_HOTBAR = MAIN_COUNT + HOTBAR_COUNT;

    /** Vanilla inventory slot pitch in GUI pixels. */
    public static final int SLOT_SIZE = 18;
    /** Standard Minecraft item icon size in GUI pixels. */
    public static final int ICON_SIZE = 16;
    /** Offset of the 16x16 item icon inside an 18x18 slot cell. */
    public static final int ICON_INSET = 1;
    /** Vertical separation between the 3x9 main inventory and the 1x9 hotbar (matches vanilla inventory.png). */
    public static final int HOTBAR_GAP = 4;
    /** Outer beveled panel margin in vanilla inventory.png. */
    public static final int VANILLA_BORDER = 7;

    private InventoryHudLayout() {}

    /** Outer margin around the slot grid for the active background mode. */
    public static int border(boolean vanillaBg) {
        return vanillaBg ? VANILLA_BORDER : 0;
    }

    /** Number of player inventory slots scanned in the current mode. */
    public static int slotCount(boolean showHotbar) {
        return showHotbar ? TOTAL_WITH_HOTBAR : MAIN_COUNT;
    }

    /**
     * Maps a display-order slot index (0..26 main inventory, 27..35 hotbar) to
     * Minecraft's {@code Inventory} slot index (9..35 main, 0..8 hotbar).
     */
    public static int inventorySlotAt(int displayIndex) {
        if (displayIndex < 0 || displayIndex >= TOTAL_WITH_HOTBAR) {
            throw new IllegalArgumentException("Display index out of range: " + displayIndex);
        }
        if (displayIndex < MAIN_COUNT) {
            return MAIN_START + displayIndex;
        }
        return HOTBAR_START + (displayIndex - MAIN_COUNT);
    }

    /** Column count for compact mode (1..9). An empty inventory keeps 1 cell so the frame stays grabbable. */
    public static int compactCols(int itemCount) {
        if (itemCount <= 0) return 1;
        return Math.min(COLUMNS, itemCount);
    }

    /** Row count for compact mode (1..4). */
    public static int compactRows(int itemCount) {
        if (itemCount <= 0) return 1;
        return (itemCount + COLUMNS - 1) / COLUMNS;
    }

    /** Inner slot-grid width (excluding outer vanilla border). */
    public static int gridWidth(boolean compact, int itemCount) {
        int cols = compact ? compactCols(itemCount) : COLUMNS;
        return cols * SLOT_SIZE;
    }

    /** Inner slot-grid height (excluding outer vanilla border). */
    public static int gridHeight(boolean compact, int itemCount, boolean showHotbar) {
        if (compact) {
            return compactRows(itemCount) * SLOT_SIZE;
        }
        int base = MAIN_ROWS * SLOT_SIZE;
        return showHotbar ? base + HOTBAR_GAP + SLOT_SIZE : base;
    }

    /** Total intrinsic module width at scale 1. */
    public static int width(boolean compact, int itemCount, boolean vanillaBg) {
        return gridWidth(compact, itemCount) + border(vanillaBg) * 2;
    }

    /** Total intrinsic module height at scale 1. */
    public static int height(boolean compact, int itemCount, boolean showHotbar, boolean vanillaBg) {
        return gridHeight(compact, itemCount, showHotbar) + border(vanillaBg) * 2;
    }

    /** Local X of a fixed-grid slot (top-left of its 18x18 cell). */
    public static int fixedSlotX(int displayIndex, boolean vanillaBg) {
        return border(vanillaBg) + (displayIndex % COLUMNS) * SLOT_SIZE;
    }

    /** Local Y of a fixed-grid slot (top-left of its 18x18 cell). */
    public static int fixedSlotY(int displayIndex, boolean vanillaBg) {
        int pad = border(vanillaBg);
        if (displayIndex < MAIN_COUNT) {
            return pad + (displayIndex / COLUMNS) * SLOT_SIZE;
        }
        return pad + MAIN_ROWS * SLOT_SIZE + HOTBAR_GAP;
    }

    /** Local X of a compact-grid slot (top-left of its 18x18 cell). */
    public static int compactSlotX(int compactIndex, boolean vanillaBg) {
        return border(vanillaBg) + (compactIndex % COLUMNS) * SLOT_SIZE;
    }

    /** Local Y of a compact-grid slot (top-left of its 18x18 cell). */
    public static int compactSlotY(int compactIndex, boolean vanillaBg) {
        return border(vanillaBg) + (compactIndex / COLUMNS) * SLOT_SIZE;
    }
}
