package com.aurora.client.hud.module;

import com.aurora.client.util.HudBackgrounds;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.HudGrowth;
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
 * <p>Slots are square and a fixed pitch apart in every layout: a durability
 * readout wider than its slot is drawn smaller, never widening the slot
 * (2026-11-22). Only the vertical layouts' text column follows the widest
 * readout, which changes slowly, so that width is cached with a 150ms TTL.
 *
 * <p>The module grows away from the screen centre along its layout axis
 * ({@link HudGrowth#AWAY_FROM_CENTER}): beside the hotbar, extra pieces
 * extend outward instead of into it. The anchor places a full four-piece set.
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
    /** Horizontal layout: gap between adjacent cells. */
    private static final int COL_GAP = 4;
    /** Translucent backing behind each durability readout above the hotbar strip. */
    private static final int READOUT_PLATE = 0x8C000000;

    // Durability-bar geometry (matches vanilla hotbar item bar conventions:
    // 13x2 dark frame with a 12x1 colored fill on the top row).
    private static final int BAR_W = 13;
    private static final int BAR_H = 2;
    private static final int BAR_FRAME = 0xFF000000;

    /** Vanilla hotbar-strip geometry: 20 px cell pitch, 22 px strip height. */
    private static final int STRIP_CELL_W = HudBackgrounds.CELL_PITCH;

    /** Plain horizontal layout: slot pitch (16 px icon + gap). */
    private static final int PLAIN_PITCH = ICON + COL_GAP;
    /** The slot count the anchor places: a full armor set. */
    private static final int NOMINAL_SLOTS = 4;
    /** Smallest scale a readout is drawn at to fit its slot. */
    private static final float MIN_TEXT_SCALE = 0.5f;

    private final CachedValue<Integer> cachedMaxText = new CachedValue<>(150L, this::computeMaxText);
    private final HudTextCache textCache = new HudTextCache(6);
    /** Reused per-frame scratch (render thread only) to avoid ArrayList allocations in HUD ticks. */
    private final ItemStack[] scratchSlots = new ItemStack[6];

    public ArmorModule() {
        super(ID);
        this.anchor = com.aurora.client.hud.HudAnchor.MIDDLE_LEFT;
        this.offsetX = 4;
        this.offsetY = -40;
    }

    /**
     * Populates {@code out} with occupied slots in display order: worn armor
     * (head → feet), then main hand, then off hand, returning the slot count.
     */
    private static int fillVisibleSlots(Minecraft client, ItemStack[] out) {
        if (client.player == null) return 0;
        AuroraConfig cfg = AuroraConfig.get();
        int n = 0;
        n = addSlot(client, out, n, EquipmentSlot.HEAD);
        n = addSlot(client, out, n, EquipmentSlot.CHEST);
        n = addSlot(client, out, n, EquipmentSlot.LEGS);
        n = addSlot(client, out, n, EquipmentSlot.FEET);
        if (cfg.armorHudShowMainHand) n = addSlot(client, out, n, EquipmentSlot.MAINHAND);
        if (cfg.armorHudShowOffHand) n = addSlot(client, out, n, EquipmentSlot.OFFHAND);
        return n;
    }

    /** The size of {@link #fillVisibleSlots} without populating an array. */
    private static int visibleSlotCount(Minecraft client) {
        if (client.player == null) return 0;
        AuroraConfig cfg = AuroraConfig.get();
        int n = occupied(client, EquipmentSlot.HEAD) + occupied(client, EquipmentSlot.CHEST)
                + occupied(client, EquipmentSlot.LEGS) + occupied(client, EquipmentSlot.FEET);
        if (cfg.armorHudShowMainHand) n += occupied(client, EquipmentSlot.MAINHAND);
        if (cfg.armorHudShowOffHand) n += occupied(client, EquipmentSlot.OFFHAND);
        return n;
    }

    private static int occupied(Minecraft client, EquipmentSlot slot) {
        ItemStack s = client.player.getItemBySlot(slot);
        return s != null && !s.isEmpty() ? 1 : 0;
    }

    private static int addSlot(Minecraft client, ItemStack[] out, int n, EquipmentSlot slot) {
        ItemStack s = client.player.getItemBySlot(slot);
        if (s != null && !s.isEmpty()) out[n++] = s;
        return n;
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

    /** Whether the VANILLA (hotbar-strip) background is currently active. */
    private static boolean isVanillaBg() {
        return AuroraConfig.get().armorHudBgMode == AuroraConfig.HudBackground.VANILLA;
    }

    /** Widest durability readout among the shown pieces (0 without any). */
    private int computeMaxText() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return 0;
        AuroraConfig.ArmorDurabilityFormat fmt = AuroraConfig.get().armorDurabilityFormat;
        if (fmt == null) fmt = AuroraConfig.ArmorDurabilityFormat.REMAINING;
        int n = fillVisibleSlots(client, scratchSlots);
        int maxText = 0;
        for (int i = 0; i < n; i++) {
            maxText = Math.max(maxText, client.font.width(formatDurability(scratchSlots[i], fmt)));
        }
        return maxText;
    }

    /** Intrinsic width for {@code n} shown slots. Horizontal layouts never depend on the text. */
    private int widthFor(int n) {
        if (n == 0) return STRIP_CELL_W;
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.armorHorizontal) {
            return isVanillaBg() ? n * STRIP_CELL_W + 2 : n * ICON + (n - 1) * COL_GAP;
        }
        int maxText = cfg.armorDurabilityFormat == AuroraConfig.ArmorDurabilityFormat.NONE ? 0 : cachedMaxText.get();
        if (isVanillaBg()) {
            // Vertical strip: 20 px cells, text in a column to the right.
            return STRIP_CELL_W + 2 + (maxText > 0 ? TEXT_GAP + maxText : 0);
        }
        return ICON + TEXT_GAP + Math.max(maxText, 30);
    }

    /** Intrinsic height for {@code n} shown slots. */
    private int heightFor(int n, Minecraft client) {
        if (n == 0) return ROW_H;
        AuroraConfig cfg = AuroraConfig.get();
        int lineH = (client != null && client.font != null) ? client.font.lineHeight : 9;
        AuroraConfig.ArmorDurabilityFormat fmt = cfg.armorDurabilityFormat;
        boolean hasText = fmt != null && fmt != AuroraConfig.ArmorDurabilityFormat.NONE;
        if (isVanillaBg()) {
            if (cfg.armorHorizontal) {
                boolean anyText = hasText && client != null && anyDurabilityText(client, fmt);
                return (anyText ? lineH + TEXT_GAP : 0) + HudBackgrounds.CELL_PITCH + 2; // strip: 22 px tall
            }
            return n * HudBackgrounds.CELL_PITCH + 2;
        }
        if (cfg.armorHorizontal) {
            // Icon row + optional text row above the icons.
            return ICON + (hasText ? TEXT_GAP + lineH : 0);
        }
        // Durability bars render inside the icon's 16-px footprint (vanilla
        // hotbar style), so they no longer change row spacing.
        return ROW_H * n;
    }

    @Override protected HudGrowth growthX() {
        return AuroraConfig.get().armorHorizontal ? HudGrowth.AWAY_FROM_CENTER : HudGrowth.ANCHORED;
    }

    @Override protected HudGrowth growthY() {
        return AuroraConfig.get().armorHorizontal ? HudGrowth.ANCHORED : HudGrowth.AWAY_FROM_CENTER;
    }

    @Override protected int referenceWidth() { return widthFor(NOMINAL_SLOTS); }

    @Override protected int referenceHeight() { return heightFor(NOMINAL_SLOTS, Minecraft.getInstance()); }

    @Override
    public boolean isConfigEnabled() {
        return AuroraConfig.get().armorHudEnabled;
    }

    @Override public int getWidth() {
        Minecraft client = Minecraft.getInstance();
        return widthFor(client != null ? visibleSlotCount(client) : 0);
    }

    @Override public int getHeight() {
        Minecraft client = Minecraft.getInstance();
        return heightFor(client != null ? visibleSlotCount(client) : 0, client);
    }

    /** Whether any visible slot actually has a durability reading (format != NONE). */
    private static boolean anyDurabilityText(Minecraft client, AuroraConfig.ArmorDurabilityFormat fmt) {
        if (fmt == AuroraConfig.ArmorDurabilityFormat.NONE || client.player == null) return false;
        AuroraConfig cfg = AuroraConfig.get();
        return hasDamage(client, EquipmentSlot.HEAD)
                || hasDamage(client, EquipmentSlot.CHEST)
                || hasDamage(client, EquipmentSlot.LEGS)
                || hasDamage(client, EquipmentSlot.FEET)
                || (cfg.armorHudShowMainHand && hasDamage(client, EquipmentSlot.MAINHAND))
                || (cfg.armorHudShowOffHand && hasDamage(client, EquipmentSlot.OFFHAND));
    }

    private static boolean hasDamage(Minecraft client, EquipmentSlot slot) {
        ItemStack s = client.player.getItemBySlot(slot);
        return s != null && !s.isEmpty() && s.getMaxDamage() > 0;
    }

    @Override protected AuroraConfig.HudBackground backgroundMode() {
        // VANILLA is rendered as the strip in renderContent; signal NONE to
        // the shared HudBackgrounds dispatcher so it doesn't also paint a panel.
        if (isVanillaBg()) return AuroraConfig.HudBackground.NONE;
        return AuroraConfig.get().armorHudBgMode;
    }
    @Override protected int backgroundColor() { return AuroraConfig.get().armorHudBgColor; }

    /** Hide the panel entirely when the player has nothing in the shown slots. */
    @Override protected float backgroundAlpha() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return 0f;
        return visibleSlotCount(client) == 0 ? 0f : 1f;
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

        AuroraConfig.ArmorDurabilityFormat fmt = cfg.armorDurabilityFormat;
        if (fmt == null) fmt = AuroraConfig.ArmorDurabilityFormat.REMAINING;
        int n = fillVisibleSlots(client, scratchSlots);
        int color = com.aurora.client.theme.HudText.color(cfg.armorHudTextColor, cfg.hudColor);
        boolean drawBars = cfg.armorShowDurabilityBars;
        if (n == 0) return;

        if (cfg.armorHorizontal) {
            // Horizontal layout: helmet → chest → legs → boots (→ hands),
            // left to right, one 16 px icon per slot at a fixed pitch.
            int textSpace = (fmt != AuroraConfig.ArmorDurabilityFormat.NONE) ? tr.lineHeight + TEXT_GAP : 0;
            for (int i = 0; i < n; i++) {
                ItemStack s = scratchSlots[i];
                int iconX = x + i * PLAIN_PITCH;
                int iconY = y + textSpace;
                ctx.item(s, iconX, iconY);

                String text = formatDurability(s, fmt);
                if (!text.isEmpty()) {
                    drawFitted(ctx, tr, i, text, iconX + ICON / 2f, y, PLAIN_PITCH - 2, color, false);
                }

                if (drawBars && s.getMaxDamage() > 0) {
                    drawDurabilityBar(ctx, s, iconX + 2, iconY + 13);
                }
            }
            return;
        }

        for (int i = 0; i < n; i++) {
            ItemStack s = scratchSlots[i];
            int rowY = y + i * ROW_H;
            int iconY = rowY + (ROW_H - ICON) / 2;
            ctx.item(s, x, iconY);

            String Component = formatDurability(s, fmt);
            if (!Component.isEmpty()) {
                int ty = rowY + (ROW_H - tr.lineHeight) / 2;
                com.aurora.client.hud.HudTextPainter.draw(ctx,tr, textCache.text(i, Component), x + ICON + TEXT_GAP, ty, color);
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
     * The 16×16 item icon is centered in its
     * 20 px cell exactly where vanilla draws hotbar items (+3, +3), the
     * optional durability bar sits in the icon's footprint, and the
     * durability text is drawn outside the strip (above it when
     * horizontal, beside the cell when vertical).
     */
    private void renderContentVanilla(GuiGraphicsExtractor ctx, Minecraft client, int x, int y) {
        Font tr = client.font;
        AuroraConfig cfg = AuroraConfig.get();
        AuroraConfig.ArmorDurabilityFormat fmt = cfg.armorDurabilityFormat;
        if (fmt == null) fmt = AuroraConfig.ArmorDurabilityFormat.REMAINING;
        int textColor = com.aurora.client.theme.HudText.color(cfg.armorHudTextColor, cfg.hudColor);
        boolean drawBars = cfg.armorShowDurabilityBars;
        boolean horizontal = cfg.armorHorizontal;

        int n = fillVisibleSlots(client, scratchSlots);
        if (n == 0) return;

        boolean hasText = fmt != AuroraConfig.ArmorDurabilityFormat.NONE;

        if (horizontal) {
            // Native 20 px square cells, always: the strip is never stretched to fit text.
            boolean anyText = hasText && anyDurabilityText(client, fmt);
            int textSpace = anyText ? tr.lineHeight + TEXT_GAP : 0;
            int stripY = y + textSpace;

            HudBackgrounds.hotbarStrip(ctx, x, stripY, n, 1f, 1f);

            for (int i = 0; i < n; i++) {
                ItemStack stack = scratchSlots[i];
                // Cell k's icon sits where vanilla draws hotbar items: 1 px border + 2 px inset.
                int iconX = x + 3 + i * HudBackgrounds.CELL_PITCH;
                ctx.item(stack, iconX, stripY + 3);

                if (drawBars && stack.getMaxDamage() > 0) {
                    drawDurabilityBar(ctx, stack, iconX + 2, stripY + 3 + 13);
                }

                String text = formatDurability(stack, fmt);
                if (hasText && !text.isEmpty()) {
                    drawFitted(ctx, tr, i, text, iconX + ICON / 2f, y, HudBackgrounds.CELL_PITCH - 3, textColor, true);
                }
            }
            return;
        }

        // Rotate the same capped strip so the outer outline stays continuous.
        HudBackgrounds.hotbarVerticalStrip(ctx, x, y, n, 1f);
        for (int i = 0; i < n; i++) {
            ItemStack stack = scratchSlots[i];
            int rowY = y + i * HudBackgrounds.CELL_PITCH;

            int iconX = x + 3;
            ctx.item(stack, iconX, rowY + 3);

            if (drawBars && stack.getMaxDamage() > 0) {
                drawDurabilityBar(ctx, stack, iconX + 2, rowY + 3 + 13);
            }

            String text = formatDurability(stack, fmt);
            if (hasText && !text.isEmpty()) {
                int tx = x + HudBackgrounds.CELL_PITCH + 2 + TEXT_GAP;
                int ty = rowY + (HudBackgrounds.CELL_PITCH + 2 - tr.lineHeight) / 2;
                com.aurora.client.hud.HudTextPainter.draw(ctx,tr, textCache.text(i, text), tx, ty, textColor);
            }
        }
    }

    /**
     * Draws a durability readout centred on {@code centerX}, at most {@code maxW}
     * wide: a readout wider than its slot is drawn smaller (down to
     * {@value #MIN_TEXT_SCALE}) rather than widening the slot. With {@code plate},
     * a faint backing like the strip below it: the numbers float over the world
     * and washed out on snow and sky (council 2026-11-18).
     */
    private void drawFitted(GuiGraphicsExtractor ctx, Font tr, int slot, String text, float centerX, int y,
                            int maxW, int color, boolean plate) {
        int tw = tr.width(text);
        float s = tw > maxW ? Math.max(MIN_TEXT_SCALE, maxW / (float) tw) : 1f;
        boolean unit = s >= 0.999f;
        if (!unit) {
            ctx.pose().pushMatrix();
            // Keep the smaller line vertically centred in the full-size text row.
            ctx.pose().translate(centerX, y + tr.lineHeight * (1f - s) / 2f);
            ctx.pose().scale(s, s);
        }
        int tx = unit ? Math.round(centerX - tw / 2f) : Math.round(-tw / 2f);
        int ty = unit ? y : 0;
        if (plate) ctx.fill(tx - 2, ty - 1, tx + tw + 1, ty + tr.lineHeight, READOUT_PLATE);
        com.aurora.client.hud.HudTextPainter.draw(ctx, tr, textCache.text(slot, text), tx, ty, color);
        if (!unit) ctx.pose().popMatrix();
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
