package com.aurora.client.hud.module;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.CachedValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Renders the player's 4 armor pieces stacked vertically with
 * item icon + durability Component. Format is controlled by
 * {@link AuroraConfig#armorDurabilityFormat}; an optional thin durability
 * bar (under each icon, mirrors vanilla hotbar style) is gated by
 * {@link AuroraConfig#armorShowDurabilityBars}.
 *
 * <p>Width is driven by which durability Component is widest among the 4 pieces.
 * Recomputing each frame is wasted work — durability changes slowly so we
 * cache the result with a 150ms TTL.
 *
 * <p>When the Armor HUD background is set to {@link AuroraConfig.HudBackground#VANILLA},
 * each armor piece sits inside an authentic vanilla hotbar-slot graphic with the
 * durability readout drawn alongside the slot (outside it) — a look popularised
 * by mods like Uku's ArmorHUD.
 */
public class ArmorModule extends HudModule {
    public static final String ID = "armor";
    private static final int ROW_H = 18;
    private static final int ICON  = 16;
    private static final int TEXT_GAP = 3;
    /** Horizontal layout: gap between adjacent cells. */
    private static final int COL_GAP = 4;

    // Durability-bar geometry (matches vanilla hotbar item bar conventions:
    // 13x2 dark frame with a 12x1 colored fill on the top row).
    private static final int BAR_W = 13;
    private static final int BAR_H = 2;
    private static final int BAR_FILL_W = 12;
    private static final int BAR_FRAME = 0xFF000000;

    /** A single hotbar slot, cropped from the vanilla {@code hud/hotbar} strip. */
    private static final Identifier HOTBAR_SLOT_TEX =
            Identifier.fromNamespaceAndPath(AuroraClient.MOD_ID, "textures/gui/hotbar_slot.png");
    /** Native edge length of a single hotbar slot (24×24 incl. its border). */
    private static final int SLOT = 24;
    /** Vertical spacing between stacked hotbar slots in the VANILLA layout. */
    private static final int VANILLA_GAP = 2;

    private final CachedValue<Integer> cachedWidth = new CachedValue<>(150L, this::computeWidth);

    public ArmorModule() {
        super(ID);
        this.anchor = com.aurora.client.hud.HudAnchor.MIDDLE_LEFT;
        this.offsetX = 4;
        this.offsetY = -40;
    }

    private static ItemStack[] pieces(Minecraft client) {
        if (client.player == null) return new ItemStack[0];
        return new ItemStack[] {
                client.player.getItemBySlot(EquipmentSlot.HEAD),
                client.player.getItemBySlot(EquipmentSlot.CHEST),
                client.player.getItemBySlot(EquipmentSlot.LEGS),
                client.player.getItemBySlot(EquipmentSlot.FEET),
        };
    }

    private static String formatDurability(ItemStack s, AuroraConfig.ArmorDurabilityFormat fmt) {
        if (fmt == AuroraConfig.ArmorDurabilityFormat.NONE || s.getMaxDamage() <= 0) return "";
        int remaining = s.getMaxDamage() - s.getDamageValue();
        return switch (fmt) {
            case REMAINING        -> Integer.toString(remaining);
            case REMAINING_OF_MAX -> remaining + "/" + s.getMaxDamage();
            case PERCENTAGE       -> Math.round(100f * remaining / s.getMaxDamage()) + "%";
            case NONE             -> "";
        };
    }

    /** Whether the VANILLA (hotbar-slot) background is currently active. */
    private static boolean isVanillaBg() {
        return AuroraConfig.get().armorHudBgMode == AuroraConfig.HudBackground.VANILLA;
    }

    private int computeWidth() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return 60;
        AuroraConfig cfg = AuroraConfig.get();
        AuroraConfig.ArmorDurabilityFormat fmt = cfg.armorDurabilityFormat;
        if (fmt == null) fmt = AuroraConfig.ArmorDurabilityFormat.REMAINING;
        Font tr = client.font;
        int maxText = 0;
        for (ItemStack s : pieces(client)) {
            if (s == null || s.isEmpty()) continue;
            maxText = Math.max(maxText, tr.width(formatDurability(s, fmt)));
        }
        
        if (isVanillaBg()) {
            boolean hasText = fmt != AuroraConfig.ArmorDurabilityFormat.NONE;
            if (cfg.armorHorizontal) {
                int cellW = Math.max(SLOT, maxText);
                return 4 * cellW + 3 * VANILLA_GAP;
            } else {
                return SLOT + (hasText ? TEXT_GAP + Math.max(maxText, 0) : 0);
            }
        }
        if (cfg.armorHorizontal) {
            // Each cell is sized to fit whichever is wider — the 16-px
            // icon or that piece's durability text. We use the widest
            // text across all four pieces so cells stay aligned in a
            // tidy grid even when the readings differ in length.
            int cellW = Math.max(ICON, maxText);
            return 4 * cellW + 3 * COL_GAP;
        }
        return ICON + TEXT_GAP + Math.max(maxText, 30);
    }

    @Override
    public boolean isConfigEnabled() {
        return AuroraConfig.get().armorHudEnabled;
    }

    @Override public int getWidth() {
        // computeWidth() accounts for the hotbar slot + durability text
        // column when the VANILLA background is active, and the plain
        // icon + text layout otherwise.
        return cachedWidth.get();
    }

    @Override public int getHeight() {
        AuroraConfig cfg = AuroraConfig.get();
        boolean horizontal = cfg.armorHorizontal;
        Minecraft client = Minecraft.getInstance();
        int lineH = (client != null && client.font != null) ? client.font.lineHeight : 9;
        AuroraConfig.ArmorDurabilityFormat fmt = cfg.armorDurabilityFormat;
        boolean hasText = fmt != null && fmt != AuroraConfig.ArmorDurabilityFormat.NONE;

        if (isVanillaBg()) {
            if (horizontal) {
                return SLOT + (hasText ? TEXT_GAP + lineH : 0);
            } else {
                // Vertical VANILLA: 4 stacked 24×24 hotbar slots with gaps.
                return (SLOT * 4) + (VANILLA_GAP * 3);
            }
        }

        if (horizontal) {
            // Icon row + optional text row below each icon.
            return ICON + (hasText ? TEXT_GAP + lineH : 0);
        }
        // Durability bars render inside the icon's 16-px footprint (vanilla
        // hotbar style), so they no longer change row spacing.
        return ROW_H * 4;
    }

    @Override protected AuroraConfig.HudBackground backgroundMode() {
        // VANILLA is rendered per-piece in renderContent; signal NONE to the
        // shared HudBackgrounds dispatcher so it doesn't also paint a panel.
        if (isVanillaBg()) return AuroraConfig.HudBackground.NONE;
        return AuroraConfig.get().armorHudBgMode;
    }
    @Override protected int backgroundColor() { return AuroraConfig.get().armorHudBgColor; }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft client, int x, int y) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.armorHudEnabled) return;
        if (client.player == null) return;

        if (isVanillaBg()) {
            renderContentVanilla(ctx, client, x, y);
            return;
        }

        Font tr = client.font;
        if (tr == null) return;

        AuroraConfig.ArmorDurabilityFormat fmt = cfg.armorDurabilityFormat;
        if (fmt == null) fmt = AuroraConfig.ArmorDurabilityFormat.REMAINING;
        ItemStack[] stacks = pieces(client);
        int color = com.aurora.client.theme.HudText.color(cfg.hudColor);
        boolean drawBars = cfg.armorShowDurabilityBars;

        if (cfg.armorHorizontal) {
            // Horizontal layout: helmet → chest → legs → boots, left to
            // right. cellW is derived from the cached layout width:
            //   width = 4 * cellW + 3 * COL_GAP   →   cellW = (width - 3 * COL_GAP) / 4
            // so we don't re-loop through stacks calling tr.width() on
            // every frame just to recover the same value computeWidth()
            // produced behind cachedWidth.
            int cellW = Math.max(ICON, (cachedWidth.get() - 3 * COL_GAP) / 4);
            int textSpace = (fmt != AuroraConfig.ArmorDurabilityFormat.NONE) ? tr.lineHeight + TEXT_GAP : 0;
            for (int i = 0; i < stacks.length; i++) {
                ItemStack s = stacks[i];
                if (s == null || s.isEmpty()) continue;
                int cellX = x + i * (cellW + COL_GAP);
                int iconX = cellX + (cellW - ICON) / 2;
                int iconY = y + textSpace;
                ctx.renderItem(s, iconX, iconY);

                String Component = formatDurability(s, fmt);
                if (!Component.isEmpty()) {
                    int tw = tr.width(Component);
                    int tx = cellX + (cellW - tw) / 2;
                    int ty = y;
                    ctx.drawString(tr, Component, tx, ty, color, false);
                }

                if (drawBars && s.getMaxDamage() > 0) {
                    drawDurabilityBar(ctx, s, iconX + 2, iconY + 13);
                }
            }
            return;
        }

        for (int i = 0; i < stacks.length; i++) {
            ItemStack s = stacks[i];
            if (s == null || s.isEmpty()) continue;
            int rowY = y + i * ROW_H;
            int iconY = rowY + (ROW_H - ICON) / 2;
            ctx.renderItem(s, x, iconY);

            String Component = formatDurability(s, fmt);
            if (!Component.isEmpty()) {
                int ty = rowY + (ROW_H - tr.lineHeight) / 2;
                ctx.drawString(tr, Component, x + ICON + TEXT_GAP, ty, color, false);
            }

            if (drawBars && s.getMaxDamage() > 0) {
                // Vanilla position: 13x2 bar at (icon.x + 2, icon.y + 13)
                // — inside the icon's 16-px footprint, not below it.
                drawDurabilityBar(ctx, s, x + 2, iconY + 13);
            }
        }
    }

    /**
     * VANILLA (hotbar-slot) render: each of the 4 armor pieces sits inside its
     * own authentic vanilla hotbar-slot graphic (24×24). Supports both vertical
     * and horizontal layouts. The 16×16 item icon is centered inside the slot,
     * the vanilla durability bar sits at the bottom of the slot, and the
     * configured durability text/percentage is drawn OUTSIDE the slot.
     */
    private static void renderContentVanilla(GuiGraphics ctx, Minecraft client, int x, int y) {
        Font tr = client.font;
        AuroraConfig cfg = AuroraConfig.get();
        AuroraConfig.ArmorDurabilityFormat fmt = cfg.armorDurabilityFormat;
        if (fmt == null) fmt = AuroraConfig.ArmorDurabilityFormat.REMAINING;
        int textColor = com.aurora.client.theme.HudText.color(cfg.hudColor);
        boolean drawBars = cfg.armorShowDurabilityBars;
        boolean horizontal = cfg.armorHorizontal;

        ItemStack[] stacks = pieces(client);
        
        int cellW = SLOT;
        if (horizontal) {
            int maxText = 0;
            if (tr != null) {
                for (ItemStack s : stacks) {
                    if (s == null || s.isEmpty()) continue;
                    maxText = Math.max(maxText, tr.width(formatDurability(s, fmt)));
                }
            }
            cellW = Math.max(SLOT, maxText);
        }

        int textSpace = (fmt != AuroraConfig.ArmorDurabilityFormat.NONE && tr != null) ? tr.lineHeight + TEXT_GAP : 0;

        for (int i = 0; i < stacks.length; i++) {
            ItemStack s = stacks[i];
            
            int slotX, slotY;
            if (horizontal) {
                int cellX = x + i * (cellW + VANILLA_GAP);
                slotX = cellX + (cellW - SLOT) / 2;
                slotY = y + textSpace;
            } else {
                slotX = x;
                slotY = y + i * (SLOT + VANILLA_GAP);
            }

            // The hotbar-slot graphic (cropped from the vanilla hotbar strip).
            ctx.blit(RenderPipelines.GUI_TEXTURED, HOTBAR_SLOT_TEX,
                    slotX, slotY, 0f, 0f,
                    SLOT, SLOT, SLOT, SLOT, SLOT, SLOT);

            if (s == null || s.isEmpty()) continue;

            // Center the 16×16 item icon inside the 24×24 slot (4px inset).
            int iconX = slotX + 4;
            int iconY = slotY + 4;
            ctx.renderItem(s, iconX, iconY);

            // Vanilla durability bar inside the slot at the same icon-relative
            // offset the DEFAULT style uses (icon.x + 2, icon.y + 13).
            if (drawBars && s.getMaxDamage() > 0) {
                drawDurabilityBar(ctx, s, iconX + 2, iconY + 13);
            }

            // Durability text/percentage drawn OUTSIDE the slot.
            String text = formatDurability(s, fmt);
            if (tr != null && !text.isEmpty()) {
                if (horizontal) {
                    int tw = tr.width(text);
                    int cellX = x + i * (cellW + VANILLA_GAP);
                    int tx = cellX + (cellW - tw) / 2;
                    int ty = y;
                    ctx.drawString(tr, text, tx, ty, textColor, false);
                } else {
                    int tx = slotX + SLOT + TEXT_GAP;
                    int ty = slotY + (SLOT - tr.lineHeight) / 2;
                    ctx.drawString(tr, text, tx, ty, textColor, false);
                }
            }
        }
    }

    /**
     * Draws a vanilla-style durability bar at (x, y). Mirrors the algorithm
     * vanilla uses for hotbar item bars: green→yellow→red gradient based
     * on remaining damage fraction.
     */
    private static void drawDurabilityBar(GuiGraphics ctx, ItemStack s, int x, int y) {
        int max = s.getMaxDamage();
        int dmg = s.getDamageValue();
        if (max <= 0) return;
        float frac = Math.max(0f, (max - dmg) / (float) max);
        int filled = Math.round(frac * BAR_FILL_W);

        // Vanilla algorithm: hue lerps from 0 (red) to 1/3 (green) with
        // remaining fraction. HSBtoRGB then drops alpha/composes into ARGB.
        float hue = frac / 3.0f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        int barColor = 0xFF000000 | (rgb & 0x00FFFFFF);

        // 13x2 dark frame, then the colored fill is one row tall on top —
        // matches the vanilla hotbar item bar exactly.
        ctx.fill(x, y, x + BAR_W, y + BAR_H, BAR_FRAME);
        if (filled > 0) {
            ctx.fill(x, y, x + filled, y + 1, barColor);
        }
    }
    @Override public String featureRegistryId() { return "armor_hud"; }
}