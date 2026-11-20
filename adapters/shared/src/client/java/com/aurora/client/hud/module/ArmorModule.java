package com.aurora.client.hud.module;

import com.aurora.client.hud.HudTextPainter;
import com.aurora.client.util.HudBackgrounds;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.CachedValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders occupied equipment slots — worn armor plus, optionally, the main
 * and off hands — with item icon + durability text. Empty slots are never
 * drawn, so the module always shows a compact row/column of what the
 * player actually has.
 *
 * <p>Format is controlled by {@link AuroraConfig#armorDurabilityFormat}; an
 * optional thin durability bar (under each icon, mirrors vanilla hotbar
 * style) is gated by {@link AuroraConfig#armorShowDurabilityBars}.
 *
 * <p>Width is driven by which durability text is widest among the shown
 * pieces. Recomputing each frame is wasted work — durability changes
 * slowly so we cache the result with a 150ms TTL.
 *
 * <p>When the Armor HUD background is set to {@link AuroraConfig.HudBackground#VANILLA},
 * the pieces sit on one continuous vanilla hotbar-strip graphic (a look
 * popularised by mods like Uku's ArmorHUD) with the durability readout
 * drawn beside the slot — not per-piece offhand-slot boxes.
 */
public class ArmorModule extends HudModule {
    public static final String ID = "armor";
    private static final int ROW_H = 18;
    private static final int ICON  = 16;
    private static final int TEXT_GAP = 3;
    private static final int FRACTION_GAP = 3;
    private static final int MAX_SLOTS = 6;
    /** Horizontal layout: gap between adjacent cells. */
    private static final int COL_GAP = 4;
    /** Translucent backing behind each durability readout above the hotbar strip. */
    private static final int READOUT_PLATE = 0x8C000000;

    // Durability-bar geometry (matches vanilla hotbar item bar conventions:
    // 13x2 dark frame with a 12x1 colored fill on the top row).
    private static final int BAR_W = 13;
    private static final int BAR_H = 2;
    private static final int BAR_FRAME = 0xFF000000;

    /** Space for the readout plate and the gap to the next label. */
    private static final int READOUT_SPACING = 6;

    private record Width(int pixels, int labelWidth) {}

    private final CachedValue<Width> cachedWidth = new CachedValue<>(150L, this::computeWidth);
    private final HudTextCache textCache = new HudTextCache(2 * MAX_SLOTS);

    public ArmorModule() {
        super(ID);
        this.anchor = com.aurora.client.hud.HudAnchor.MIDDLE_LEFT;
        this.offsetX = 4;
        this.offsetY = -40;
    }

    /**
     * The occupied slots this module renders, in display order: worn armor
     * (head → feet), then main hand, then off hand. Empty slots drop out;
     * the hands only join when their config switch is on.
     */
    private static List<ItemStack> visibleSlots(Minecraft client) {
        List<ItemStack> out = new ArrayList<>(MAX_SLOTS);
        if (client.player == null) return out;
        AuroraConfig cfg = AuroraConfig.get();
        add(client, out, EquipmentSlot.HEAD);
        add(client, out, EquipmentSlot.CHEST);
        add(client, out, EquipmentSlot.LEGS);
        add(client, out, EquipmentSlot.FEET);
        if (cfg.armorHudShowMainHand) add(client, out, EquipmentSlot.MAINHAND);
        if (cfg.armorHudShowOffHand) add(client, out, EquipmentSlot.OFFHAND);
        return out;
    }

    private static void add(Minecraft client, List<ItemStack> out, EquipmentSlot slot) {
        ItemStack s = client.player.getItemBySlot(slot);
        if (s != null && !s.isEmpty()) out.add(s);
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

    private static AuroraConfig.ArmorDurabilityFormat durabilityFormat(AuroraConfig cfg) {
        return cfg.armorDurabilityFormat == null ? AuroraConfig.ArmorDurabilityFormat.REMAINING : cfg.armorDurabilityFormat;
    }

    private static int horizontalReadoutHeight(int lineHeight, AuroraConfig.ArmorDurabilityFormat fmt) {
        return fmt == AuroraConfig.ArmorDurabilityFormat.REMAINING_OF_MAX ? 2 * lineHeight + FRACTION_GAP : lineHeight;
    }

    /** Whether the VANILLA (hotbar-strip) background is currently active. */
    private static boolean isVanillaBg() {
        return AuroraConfig.get().armorHudBgMode == AuroraConfig.HudBackground.VANILLA;
    }

    private static float horizontalStripScale(int labelWidth) {
        return Math.max(1f, (labelWidth + READOUT_SPACING) / (float) HudBackgrounds.CELL_PITCH);
    }

    private Width computeWidth() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return new Width(60, 0);
        AuroraConfig cfg = AuroraConfig.get();
        AuroraConfig.ArmorDurabilityFormat fmt = durabilityFormat(cfg);
        Font tr = client.font;
        List<ItemStack> stacks = visibleSlots(client);
        int n = stacks.size();
        if (n == 0) return new Width(HudBackgrounds.CELL_PITCH, 0);
        int maxText = 0;
        boolean fraction = cfg.armorHorizontal && fmt == AuroraConfig.ArmorDurabilityFormat.REMAINING_OF_MAX;
        for (ItemStack stack : stacks) {
            if (fraction && stack.getMaxDamage() > 0) {
                int max = stack.getMaxDamage();
                maxText = Math.max(maxText, tr.width(Integer.toString(max - stack.getDamageValue())));
                maxText = Math.max(maxText, tr.width(Integer.toString(max)));
            } else {
                maxText = Math.max(maxText, tr.width(formatDurability(stack, fmt)));
            }
        }

        if (isVanillaBg()) {
            if (cfg.armorHorizontal) {
                return new Width(Math.round(HudBackgrounds.hotbarStripWidth(n) * horizontalStripScale(maxText)), maxText);
            }
            // Vertical strip: 20 px cells, text in a column to the right.
            return new Width(HudBackgrounds.SLOT_SIZE + (maxText > 0 ? TEXT_GAP + maxText : 0), maxText);
        }
        if (cfg.armorHorizontal) {
            // Each cell is sized to fit whichever is wider — the 16-px
            // icon or that piece's durability text. We use the widest
            // text across all pieces so cells stay aligned in a
            // tidy grid even when the readings differ in length.
            int cellW = Math.max(ICON, maxText);
            return new Width(n * cellW + (n - 1) * COL_GAP, maxText);
        }
        return new Width(ICON + TEXT_GAP + Math.max(maxText, 30), maxText);
    }

    @Override
    public boolean isConfigEnabled() {
        return AuroraConfig.get().armorHudEnabled;
    }

    @Override public int getWidth() {
        // computeWidth() accounts for the hotbar strip + durability text
        // column when the VANILLA background is active, and the plain
        // icon + text layout otherwise.
        return cachedWidth.get().pixels();
    }

    @Override public int getHeight() {
        AuroraConfig cfg = AuroraConfig.get();
        boolean horizontal = cfg.armorHorizontal;
        Minecraft client = Minecraft.getInstance();
        int lineH = (client != null && client.font != null) ? client.font.lineHeight : 9;
        AuroraConfig.ArmorDurabilityFormat fmt = durabilityFormat(cfg);
        int n = (client != null) ? visibleSlots(client).size() : 0;
        if (n == 0) return ROW_H;

        if (isVanillaBg()) {
            if (horizontal) {
                int labelWidth = cachedWidth.get().labelWidth();
                int textSpace = labelWidth > 0 ? horizontalReadoutHeight(lineH, fmt) + TEXT_GAP : 0;
                return textSpace + Math.round(HudBackgrounds.SLOT_SIZE * horizontalStripScale(labelWidth));
            }
            return HudBackgrounds.hotbarStripWidth(n);
        }

        if (horizontal) {
            return ICON + (cachedWidth.get().labelWidth() > 0 ? horizontalReadoutHeight(lineH, fmt) + TEXT_GAP : 0);
        }
        // Durability bars render inside the icon's 16-px footprint (vanilla
        // hotbar style), so they no longer change row spacing.
        return ROW_H * n;
    }

    @Override protected AuroraConfig.HudBackground backgroundMode() {
        // VANILLA is rendered as the strip in renderContent; signal NONE to
        // the shared HudBackgrounds dispatcher so it doesn't also paint a panel.
        if (isVanillaBg()) return AuroraConfig.HudBackground.NONE;
        return AuroraConfig.get().armorHudBgMode;
    }
    @Override protected int backgroundColor() { return AuroraConfig.get().armorHudBgColor; }
    @Override protected boolean themeCorners() { return AuroraConfig.get().armorHudThemeCorners; }

    /** Hide the panel entirely when the player has nothing in the shown slots. */
    @Override protected float backgroundAlpha() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return 0f;
        return visibleSlots(client).isEmpty() ? 0f : 1f;
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, Minecraft client, int x, int y) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.armorHudEnabled) return;
        if (client.player == null) return;
        if (client.font == null) return;

        if (isVanillaBg()) {
            renderContentVanilla(ctx, client, x, y);
            return;
        }

        Font tr = client.font;

        AuroraConfig.ArmorDurabilityFormat fmt = durabilityFormat(cfg);
        List<ItemStack> stacks = visibleSlots(client);
        int color = com.aurora.client.theme.HudText.color(cfg.armorHudTextColor, cfg.hudColor);
        boolean drawBars = cfg.armorShowDurabilityBars;
        int n = stacks.size();
        if (n == 0) return;

        if (cfg.armorHorizontal) {
            // Horizontal layout: helmet → chest → legs → boots (→ hands),
            // left to right. cellW is derived from the cached layout width:
            //   width = n * cellW + (n-1) * COL_GAP
            // so we don't re-loop through stacks calling tr.width() on
            // every frame just to recover the same value computeWidth()
            // produced behind cachedWidth.
            int cellW = Math.max(ICON, (getWidth() - (n - 1) * COL_GAP) / n);
            int textSpace = cachedWidth.get().labelWidth() > 0 ? horizontalReadoutHeight(tr.lineHeight, fmt) + TEXT_GAP : 0;
            for (int i = 0; i < n; i++) {
                ItemStack s = stacks.get(i);
                int cellX = x + i * (cellW + COL_GAP);
                int iconX = cellX + (cellW - ICON) / 2;
                int iconY = y + textSpace;
                ctx.item(s, iconX, iconY);

                if (textSpace > 0) drawHorizontalReadout(ctx, tr, s, fmt, i, cellX + cellW / 2, y, color, false);

                if (drawBars && s.getMaxDamage() > 0) {
                    drawDurabilityBar(ctx, s, iconX + 2, iconY + 13);
                }
            }
            return;
        }

        for (int i = 0; i < n; i++) {
            ItemStack s = stacks.get(i);
            int rowY = y + i * ROW_H;
            int iconY = rowY + (ROW_H - ICON) / 2;
            ctx.item(s, x, iconY);

            String Component = formatDurability(s, fmt);
            if (!Component.isEmpty()) {
                int ty = rowY + (ROW_H - tr.lineHeight) / 2;
                HudTextPainter.draw(ctx, tr, textCache.text(i, Component), x + ICON + TEXT_GAP, ty, color);
            }

            if (drawBars && s.getMaxDamage() > 0) {
                // Vanilla position: 13x2 bar at (icon.x + 2, icon.y + 13)
                // — inside the icon's 16-px footprint, not below it.
                drawDurabilityBar(ctx, s, x + 2, iconY + 13);
            }
        }
    }

    /**
     * VANILLA (hotbar-strip) render: the occupied slots sit on one
     * continuous vanilla hotbar-strip graphic — horizontally a single
     * capped hotbar sprite, vertically the same strip rotated clockwise.
     * In horizontal mode, wider readouts scale the strip and its items
     * uniformly. Readouts are drawn at normal text size above the strip
     * or beside the cells in vertical mode.
     */
    private void renderContentVanilla(GuiGraphicsExtractor ctx, Minecraft client, int x, int y) {
        Font tr = client.font;
        AuroraConfig cfg = AuroraConfig.get();
        AuroraConfig.ArmorDurabilityFormat fmt = durabilityFormat(cfg);
        int textColor = com.aurora.client.theme.HudText.color(cfg.armorHudTextColor, cfg.hudColor);
        boolean drawBars = cfg.armorShowDurabilityBars;
        boolean horizontal = cfg.armorHorizontal;

        List<ItemStack> stacks = visibleSlots(client);
        int n = stacks.size();
        if (n == 0) return;

        boolean hasText = fmt != AuroraConfig.ArmorDurabilityFormat.NONE;

        if (horizontal) {
            int labelWidth = cachedWidth.get().labelWidth();
            float stripScale = horizontalStripScale(labelWidth);
            int stripY = y + (labelWidth > 0 ? horizontalReadoutHeight(tr.lineHeight, fmt) + TEXT_GAP : 0);

            ctx.pose().pushMatrix();
            ctx.pose().translate(x, stripY);
            ctx.pose().scale(stripScale, stripScale);
            HudBackgrounds.hotbarStrip(ctx, 0, 0, n, 1f);

            for (int i = 0; i < n; i++) {
                ItemStack stack = stacks.get(i);
                int iconX = i * HudBackgrounds.CELL_PITCH + 3;
                ctx.item(stack, iconX, 3);
                if (drawBars && stack.getMaxDamage() > 0) {
                    drawDurabilityBar(ctx, stack, iconX + 2, 3 + 13);
                }
            }
            ctx.pose().popMatrix();

            if (labelWidth == 0 || !hasText) return;
            for (int i = 0; i < n; i++) {
                int centerX = x + Math.round((HudBackgrounds.SLOT_SIZE / 2f + i * HudBackgrounds.CELL_PITCH) * stripScale);
                drawHorizontalReadout(ctx, tr, stacks.get(i), fmt, i, centerX, y, textColor, true);
            }
            return;
        }

        // Rotate the same capped strip so the outer outline stays continuous.
        HudBackgrounds.hotbarVerticalStrip(ctx, x, y, n, 1f);
        for (int i = 0; i < n; i++) {
            ItemStack stack = stacks.get(i);
            int rowY = y + i * HudBackgrounds.CELL_PITCH;

            int iconX = x + 3;
            ctx.item(stack, iconX, rowY + 3);

            if (drawBars && stack.getMaxDamage() > 0) {
                drawDurabilityBar(ctx, stack, iconX + 2, rowY + 3 + 13);
            }

            String text = formatDurability(stack, fmt);
            if (hasText && !text.isEmpty()) {
                int tx = x + HudBackgrounds.SLOT_SIZE + TEXT_GAP;
                int ty = rowY + (HudBackgrounds.SLOT_SIZE - tr.lineHeight) / 2;
                HudTextPainter.draw(ctx, tr, textCache.text(i, text), tx, ty, textColor);
            }
        }
    }

    private void drawHorizontalReadout(GuiGraphicsExtractor ctx, Font font, ItemStack stack,
                                       AuroraConfig.ArmorDurabilityFormat fmt, int slot,
                                       int centerX, int y, int color, boolean plate) {
        int max = stack.getMaxDamage();
        if (max <= 0 || fmt == AuroraConfig.ArmorDurabilityFormat.NONE) return;

        boolean fraction = fmt == AuroraConfig.ArmorDurabilityFormat.REMAINING_OF_MAX;
        String top = fraction ? Integer.toString(max - stack.getDamageValue()) : formatDurability(stack, fmt);
        String bottom = fraction ? Integer.toString(max) : "";
        int topWidth = font.width(top);
        int bottomWidth = fraction ? font.width(bottom) : 0;
        int width = Math.max(topWidth, bottomWidth);
        int left = centerX - width / 2;
        if (plate) {
            // Keep the readout legible against bright terrain and sky.
            ctx.fill(left - 2, y - 1, left + width + 1, y + horizontalReadoutHeight(font.lineHeight, fmt), READOUT_PLATE);
        }
        HudTextPainter.draw(ctx, font, textCache.text(slot, top), centerX - topWidth / 2, y, color);
        if (fraction) {
            int dividerY = y + font.lineHeight + 1;
            ctx.fill(left, dividerY, left + width, dividerY + 1, color);
            HudTextPainter.draw(ctx, font, textCache.text(MAX_SLOTS + slot, bottom),
                    centerX - bottomWidth / 2, y + font.lineHeight + FRACTION_GAP, color);
        }
    }

    /**
     * Draws a vanilla-style durability bar at (x, y). Mirrors the algorithm
     * vanilla uses for hotbar item bars: green→yellow→red gradient based
     * on remaining damage fraction.
     */
    private static void drawDurabilityBar(GuiGraphicsExtractor ctx, ItemStack s, int x, int y) {
        int max = s.getMaxDamage();
        int dmg = s.getDamageValue();
        if (max <= 0) return;
        float frac = Math.max(0f, (max - dmg) / (float) max);
        // Vanilla's ItemStack.getBarWidth: round(13 - damage * 13 / max), so an item at a few
        // percent still shows a 1 px sliver (12 * frac rounded to 0 at 4%; 2026-11-15).
        int filled = Math.max(0, Math.min(BAR_W, Math.round(BAR_W - dmg * (float) BAR_W / max)));

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
