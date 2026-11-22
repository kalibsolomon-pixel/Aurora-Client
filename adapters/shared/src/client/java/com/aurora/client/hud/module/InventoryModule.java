package com.aurora.client.hud.module;

import com.aurora.client.adapter.NativeUi;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.HudAnchor;
import com.aurora.client.hud.InventoryHudLayout;
import com.aurora.client.screen.HudEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Draggable, scalable HUD overlay displaying the player's main inventory
 * (slots 9..35) and optionally the hotbar (slots 0..8).
 *
 * <p>Supports three background styles matching the rest of Aurora's HUD
 * modules: {@code VANILLA} (blits directly from vanilla's
 * {@code inventory.png} and {@code container/slot} sprite so resource packs
 * theme it automatically with zero fills), {@code SOLID} (translucent panel
 * with subtle 1px slot dividers), and {@code NONE} (floating items only).
 *
 * <p>Zero per-frame allocations: slot reads reuse a preallocated scratch
 * array, and the HUD Editor falls back to a cached sample layout whenever
 * the player's inventory is empty so the module is always easy to place.
 */
public final class InventoryModule extends HudModule {
    public static final String ID = "inventory";

    private static final Identifier INVENTORY_TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/inventory.png");
    private static final Identifier SLOT_SPRITE =
            Identifier.withDefaultNamespace("container/slot");

    private static final int TEX_W = 256;
    private static final int TEX_H = 256;
    private static final int VANILLA_PANEL_W = 176;
    private static final int VANILLA_MAIN_V = 83;
    private static final int VANILLA_MAIN_H = 54;
    private static final int VANILLA_GAP_V = 137;
    private static final int VANILLA_GAP_H = 4;
    private static final int VANILLA_BOTTOM_V = 159;
    private static final int VANILLA_MAIN_WITH_HOTBAR_AND_BOTTOM_H = 83;

    /** Subtle 1px grid divider used inside the SOLID panel style. */
    private static final int GRID_LINE = 0x24FFFFFF;

    /** Reused per-frame scratch (render thread only). */
    private final ItemStack[] scratch = new ItemStack[InventoryHudLayout.TOTAL_WITH_HOTBAR];
    /** True while inside {@link #render} so width/height/backgroundAlpha/renderContent share one inventory scan. */
    private boolean frameScratchValid;
    private int frameNonEmpty;
    private boolean frameEditor;

    /** Lazily created editor sample stacks when the player's inventory is empty. */
    private ItemStack[] sampleFixed;

    public InventoryModule() {
        super(ID);
        this.anchor = HudAnchor.BOTTOM_LEFT;
        this.offsetX = 6;
        this.offsetY = -6;
    }

    @Override
    public String featureRegistryId() {
        return "inventory_hud";
    }

    @Override
    public boolean isConfigEnabled() {
        return AuroraConfig.get().inventoryHudEnabled;
    }

    private static boolean isVanillaBg() {
        return AuroraConfig.get().inventoryHudBgMode == AuroraConfig.HudBackground.VANILLA;
    }

    private static boolean isEditor(Minecraft client) {
        return client != null && NativeUi.screen(client) instanceof HudEditorScreen;
    }

    private static int countLiveItems(Minecraft client, boolean showHotbar) {
        if (client == null || client.player == null) return 0;
        Inventory inv = client.player.getInventory();
        int total = InventoryHudLayout.slotCount(showHotbar);
        int count = 0;
        for (int i = 0; i < total; i++) {
            ItemStack s = inv.getItem(InventoryHudLayout.inventorySlotAt(i));
            if (s != null && !s.isEmpty()) count++;
        }
        return count;
    }

    private int activeCount(Minecraft client, boolean showHotbar) {
        if (frameScratchValid) return frameNonEmpty;
        int live = countLiveItems(client, showHotbar);
        if (live > 0) return live;
        if (isEditor(client)) return sampleItemCount(showHotbar);
        return 0;
    }

    @Override
    protected AuroraConfig.HudBackground backgroundMode() {
        if (isVanillaBg()) return AuroraConfig.HudBackground.NONE;
        return AuroraConfig.get().inventoryHudBgMode;
    }

    @Override
    protected int backgroundColor() {
        return AuroraConfig.get().inventoryHudBgColor;
    }

    @Override
    protected float backgroundAlpha() {
        AuroraConfig cfg = AuroraConfig.get();
        if (frameScratchValid) {
            if (frameEditor) return 1f;
            return (cfg.inventoryHudHideWhenEmpty && frameNonEmpty == 0) ? 0f : 1f;
        }
        Minecraft client = Minecraft.getInstance();
        if (isEditor(client)) return 1f;
        if (client == null || client.player == null) return 0f;
        if (cfg.inventoryHudHideWhenEmpty && countLiveItems(client, cfg.inventoryHudShowHotbar) == 0) {
            return 0f;
        }
        return 1f;
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, Minecraft client, int screenW, int screenH) {
        if (!enabled) return;
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.inventoryHudEnabled || client == null || client.font == null) return;
        boolean editor = isEditor(client);
        if (client.player == null && !editor) return;

        int nonEmpty = populateScratch(client, cfg.inventoryHudShowHotbar, cfg.inventoryHudCompact, editor);
        if (nonEmpty == 0 && !editor && cfg.inventoryHudHideWhenEmpty) return;

        frameNonEmpty = nonEmpty;
        frameEditor = editor;
        frameScratchValid = true;
        try {
            super.render(ctx, client, screenW, screenH);
        } finally {
            frameScratchValid = false;
        }
    }

    @Override
    public int getWidth() {
        AuroraConfig cfg = AuroraConfig.get();
        int count = cfg.inventoryHudCompact
                ? activeCount(Minecraft.getInstance(), cfg.inventoryHudShowHotbar)
                : 0;
        return InventoryHudLayout.width(cfg.inventoryHudCompact, count, isVanillaBg());
    }

    @Override
    public int getHeight() {
        AuroraConfig cfg = AuroraConfig.get();
        int count = cfg.inventoryHudCompact
                ? activeCount(Minecraft.getInstance(), cfg.inventoryHudShowHotbar)
                : 0;
        return InventoryHudLayout.height(cfg.inventoryHudCompact, count, cfg.inventoryHudShowHotbar, isVanillaBg());
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, Minecraft client, int x, int y) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.inventoryHudEnabled) return;
        if (client.font == null) return;

        boolean editor = isEditor(client);
        if (client.player == null && !editor) return;

        boolean showHotbar = cfg.inventoryHudShowHotbar;
        boolean compact = cfg.inventoryHudCompact;
        boolean vanillaBg = isVanillaBg();
        boolean solidBg = cfg.inventoryHudBgMode == AuroraConfig.HudBackground.SOLID;

        int nonEmpty = frameScratchValid
                ? frameNonEmpty
                : populateScratch(client, showHotbar, compact, editor);
        if (nonEmpty == 0 && !editor && cfg.inventoryHudHideWhenEmpty) return;

        if (vanillaBg) {
            drawVanillaBackdrop(ctx, x, y, compact, nonEmpty, showHotbar);
        } else if (solidBg) {
            drawSolidGridLines(ctx, x, y, compact, nonEmpty, showHotbar);
        }

        String countOverride = cfg.inventoryHudShowCounts ? null : "";
        if (compact) {
            for (int i = 0; i < nonEmpty; i++) {
                ItemStack stack = scratch[i];
                if (stack == null || stack.isEmpty()) continue;
                int sx = x + InventoryHudLayout.compactSlotX(i, vanillaBg) + InventoryHudLayout.ICON_INSET;
                int sy = y + InventoryHudLayout.compactSlotY(i, vanillaBg) + InventoryHudLayout.ICON_INSET;
                ctx.item(stack, sx, sy, i);
                ctx.itemDecorations(client.font, stack, sx, sy, countOverride);
            }
            return;
        }

        int totalSlots = InventoryHudLayout.slotCount(showHotbar);
        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = scratch[i];
            if (stack == null || stack.isEmpty()) continue;
            int sx = x + InventoryHudLayout.fixedSlotX(i, vanillaBg) + InventoryHudLayout.ICON_INSET;
            int sy = y + InventoryHudLayout.fixedSlotY(i, vanillaBg) + InventoryHudLayout.ICON_INSET;
            ctx.item(stack, sx, sy, i);
            ctx.itemDecorations(client.font, stack, sx, sy, countOverride);
        }
    }

    /**
     * Fills {@link #scratch} with live inventory stacks (or the editor sample
     * if empty while in {@link HudEditorScreen}) and returns the non-empty count.
     */
    private int populateScratch(Minecraft client, boolean showHotbar, boolean compact, boolean editor) {
        int totalSlots = InventoryHudLayout.slotCount(showHotbar);
        int nonEmpty = 0;
        if (client.player != null) {
            Inventory inv = client.player.getInventory();
            if (compact) {
                for (int i = 0; i < totalSlots; i++) {
                    ItemStack s = inv.getItem(InventoryHudLayout.inventorySlotAt(i));
                    if (s != null && !s.isEmpty()) scratch[nonEmpty++] = s;
                }
            } else {
                for (int i = 0; i < totalSlots; i++) {
                    ItemStack s = inv.getItem(InventoryHudLayout.inventorySlotAt(i));
                    if (s != null && !s.isEmpty()) {
                        scratch[i] = s;
                        nonEmpty++;
                    } else {
                        scratch[i] = ItemStack.EMPTY;
                    }
                }
            }
        }
        if (nonEmpty > 0 || !editor) return nonEmpty;

        ItemStack[] sample = sampleStacks();
        if (compact) {
            for (int i = 0; i < totalSlots; i++) {
                ItemStack s = sample[i];
                if (s != null && !s.isEmpty()) scratch[nonEmpty++] = s;
            }
        } else {
            for (int i = 0; i < totalSlots; i++) {
                ItemStack s = sample[i];
                scratch[i] = s;
                if (s != null && !s.isEmpty()) nonEmpty++;
            }
        }
        return nonEmpty;
    }

    private void drawVanillaBackdrop(GuiGraphicsExtractor ctx, int x, int y,
                                     boolean compact, int itemCount, boolean showHotbar) {
        if (!compact) {
            // Top 7px beveled panel cap from vanilla inventory.png
            ctx.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE,
                    x, y, 0f, 0f, VANILLA_PANEL_W, InventoryHudLayout.VANILLA_BORDER, TEX_W, TEX_H);
            if (showHotbar) {
                // Main 3x9 grid + 4px gap + 1x9 hotbar + 7px bottom cap in one blit
                ctx.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE,
                        x, y + InventoryHudLayout.VANILLA_BORDER,
                        0f, VANILLA_MAIN_V, VANILLA_PANEL_W, VANILLA_MAIN_WITH_HOTBAR_AND_BOTTOM_H, TEX_W, TEX_H);
            } else {
                // Main 3x9 grid + side borders, then 7px bottom cap
                ctx.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE,
                        x, y + InventoryHudLayout.VANILLA_BORDER,
                        0f, VANILLA_MAIN_V, VANILLA_PANEL_W, VANILLA_MAIN_H, TEX_W, TEX_H);
                ctx.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE,
                        x, y + InventoryHudLayout.VANILLA_BORDER + VANILLA_MAIN_H,
                        0f, VANILLA_BOTTOM_V, VANILLA_PANEL_W, InventoryHudLayout.VANILLA_BORDER, TEX_W, TEX_H);
            }
            return;
        }

        // Compact mode: 9-slice vanilla panel frame + individual container/slot sprites.
        int innerW = InventoryHudLayout.gridWidth(true, itemCount);
        int innerH = InventoryHudLayout.gridHeight(true, itemCount, false);
        int b = InventoryHudLayout.VANILLA_BORDER;
        int innerU = b;
        int rightU = VANILLA_PANEL_W - b;
        int innerSrcW = VANILLA_PANEL_W - b * 2;

        // Interior panel fill sampled from inventory.png's 4px gap band so resource packs theme it.
        ctx.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE,
                x + b, y + b, innerU, VANILLA_GAP_V, innerW, innerH, innerSrcW, VANILLA_GAP_H, TEX_W, TEX_H);

        // Corners
        ctx.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE, x, y, 0f, 0f, b, b, TEX_W, TEX_H);
        ctx.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE, x + b + innerW, y, rightU, 0f, b, b, TEX_W, TEX_H);
        ctx.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE, x, y + b + innerH, 0f, VANILLA_BOTTOM_V, b, b, TEX_W, TEX_H);
        ctx.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE, x + b + innerW, y + b + innerH, rightU, VANILLA_BOTTOM_V, b, b, TEX_W, TEX_H);

        // Edges
        ctx.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE,
                x + b, y, innerU, 0f, innerW, b, innerSrcW, b, TEX_W, TEX_H);
        ctx.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE,
                x + b, y + b + innerH, innerU, VANILLA_BOTTOM_V, innerW, b, innerSrcW, b, TEX_W, TEX_H);
        ctx.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE,
                x, y + b, 0f, VANILLA_MAIN_V, b, innerH, b, VANILLA_MAIN_H, TEX_W, TEX_H);
        ctx.blit(RenderPipelines.GUI_TEXTURED, INVENTORY_TEXTURE,
                x + b + innerW, y + b, rightU, VANILLA_MAIN_V, b, innerH, b, VANILLA_MAIN_H, TEX_W, TEX_H);

        // Slot cells (at least one empty cell when count == 0)
        int cells = Math.max(1, itemCount);
        for (int i = 0; i < cells; i++) {
            int sx = x + InventoryHudLayout.compactSlotX(i, true);
            int sy = y + InventoryHudLayout.compactSlotY(i, true);
            ctx.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_SPRITE,
                    sx, sy, InventoryHudLayout.SLOT_SIZE, InventoryHudLayout.SLOT_SIZE);
        }
    }

    private static void drawSolidGridLines(GuiGraphicsExtractor ctx, int x, int y,
                                           boolean compact, int itemCount, boolean showHotbar) {
        int slot = InventoryHudLayout.SLOT_SIZE;
        if (compact) {
            int cols = InventoryHudLayout.compactCols(itemCount);
            int rows = InventoryHudLayout.compactRows(itemCount);
            int w = cols * slot;
            int h = rows * slot;
            for (int c = 1; c < cols; c++) {
                ctx.fill(x + c * slot, y, x + c * slot + 1, y + h, GRID_LINE);
            }
            for (int r = 1; r < rows; r++) {
                ctx.fill(x, y + r * slot, x + w, y + r * slot + 1, GRID_LINE);
            }
            return;
        }

        int w = InventoryHudLayout.COLUMNS * slot;
        int mainH = InventoryHudLayout.MAIN_ROWS * slot;
        for (int c = 1; c < InventoryHudLayout.COLUMNS; c++) {
            ctx.fill(x + c * slot, y, x + c * slot + 1, y + mainH, GRID_LINE);
        }
        for (int r = 1; r < InventoryHudLayout.MAIN_ROWS; r++) {
            ctx.fill(x, y + r * slot, x + w, y + r * slot + 1, GRID_LINE);
        }
        if (showHotbar) {
            int sepY = y + mainH + InventoryHudLayout.HOTBAR_GAP / 2;
            ctx.fill(x, sepY, x + w, sepY + 1, GRID_LINE);
            int hotbarY = y + mainH + InventoryHudLayout.HOTBAR_GAP;
            for (int c = 1; c < InventoryHudLayout.COLUMNS; c++) {
                ctx.fill(x + c * slot, hotbarY, x + c * slot + 1, hotbarY + slot, GRID_LINE);
            }
        }
    }

    private int sampleItemCount(boolean showHotbar) {
        ItemStack[] sample = sampleStacks();
        int total = InventoryHudLayout.slotCount(showHotbar);
        int count = 0;
        for (int i = 0; i < total; i++) {
            if (sample[i] != null && !sample[i].isEmpty()) count++;
        }
        return count;
    }

    private ItemStack[] sampleStacks() {
        if (sampleFixed != null) return sampleFixed;
        ItemStack[] s = new ItemStack[InventoryHudLayout.TOTAL_WITH_HOTBAR];
        java.util.Arrays.fill(s, ItemStack.EMPTY);

        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        pick.setDamageValue(pick.getMaxDamage() / 3);
        s[0] = pick;
        s[1] = new ItemStack(Items.GOLDEN_APPLE, 16);
        s[2] = new ItemStack(Items.ENDER_PEARL, 16);
        s[3] = new ItemStack(Items.OBSIDIAN, 64);
        s[4] = new ItemStack(Items.TORCH, 64);
        s[9] = new ItemStack(Items.COOKED_BEEF, 32);
        s[10] = new ItemStack(Items.ARROW, 64);
        s[11] = new ItemStack(Items.COBBLESTONE, 64);
        s[18] = new ItemStack(Items.TOTEM_OF_UNDYING, 1);

        // Hotbar sample (slots 27..35 in display order)
        s[27] = new ItemStack(Items.DIAMOND_SWORD);
        s[28] = new ItemStack(Items.SHIELD);
        s[29] = new ItemStack(Items.WATER_BUCKET);

        sampleFixed = s;
        return sampleFixed;
    }
}
