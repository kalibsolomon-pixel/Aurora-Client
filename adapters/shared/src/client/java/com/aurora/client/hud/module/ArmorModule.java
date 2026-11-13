package com.aurora.client.hud.module;

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
    /** Horizontal layout: gap between adjacent cells. */
    private static final int COL_GAP = 4;

    // Durability-bar geometry (matches vanilla hotbar item bar conventions:
    // 13x2 dark frame with a 12x1 colored fill on the top row).
    private static final int BAR_W = 13;
    private static final int BAR_H = 2;
    private static final int BAR_FILL_W = 12;
    private static final int BAR_FRAME = 0xFF000000;

    /** Vanilla hotbar-strip geometry: 20 px cell pitch, 22 px strip height. */
    private static final int STRIP_CELL_W = HudBackgrounds.CELL_PITCH;

    private final CachedValue<Integer> cachedWidth = new CachedValue<>(150L, this::computeWidth);
    private final HudTextCache textCache = new HudTextCache(6);

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
        List<ItemStack> out = new ArrayList<>(6);
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

    /** Whether the VANILLA (hotbar-strip) background is currently active. */
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
        List<ItemStack> stacks = visibleSlots(client);
        int n = stacks.size();
        if (n == 0) return STRIP_CELL_W;
        int maxText = 0;
        for (ItemStack s : stacks) {
            maxText = Math.max(maxText, tr.width(formatDurability(s, fmt)));
        }

        if (isVanillaBg()) {
            boolean hasText = fmt != AuroraConfig.ArmorDurabilityFormat.NONE && maxText > 0;
            if (cfg.armorHorizontal) {
                // One continuous strip; cells widen (stretch uniformly) only
                // when a durability text needs more than its 20 px cell.
                float s = hasText ? Math.max(1f, (maxText + 2) / (float) HudBackgrounds.CELL_PITCH) : 1f;
                return Math.round((n * HudBackgrounds.CELL_PITCH + 2) * s);
            }
            // Vertical strip: 20 px cells, text in a column to the right.
            return STRIP_CELL_W + (hasText ? TEXT_GAP + maxText : 0);
        }
        if (cfg.armorHorizontal) {
            // Each cell is sized to fit whichever is wider — the 16-px
            // icon or that piece's durability text. We use the widest
            // text across all pieces so cells stay aligned in a
            // tidy grid even when the readings differ in length.
            int cellW = Math.max(ICON, maxText);
            return n * cellW + (n - 1) * COL_GAP;
        }
        return ICON + TEXT_GAP + Math.max(maxText, 30);
    }

    @Override
    public boolean isConfigEnabled() {
        return AuroraConfig.get().armorHudEnabled;
    }

    @Override public int getWidth() {
        // computeWidth() accounts for the hotbar strip + durability text
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
        int n = (client != null) ? visibleSlots(client).size() : 0;
        if (n == 0) return ROW_H;

        if (isVanillaBg()) {
            if (horizontal) {
                boolean anyText = hasText && client.font != null && anyDurabilityText(client, fmt);
                return (anyText ? lineH + TEXT_GAP : 0) + HudBackgrounds.CELL_PITCH + 2; // strip: 22 px tall
            }
            // Vertical VANILLA: a column of touching 22 px hotbar slot cells.
            return n * (HudBackgrounds.CELL_PITCH + 2);
        }

        if (horizontal) {
            // Icon row + optional text row below each icon.
            return ICON + (hasText ? TEXT_GAP + lineH : 0);
        }
        // Durability bars render inside the icon's 16-px footprint (vanilla
        // hotbar style), so they no longer change row spacing.
        return ROW_H * n;
    }

    /** Whether any visible slot actually has a durability reading (format != NONE). */
    private static boolean anyDurabilityText(Minecraft client, AuroraConfig.ArmorDurabilityFormat fmt) {
        for (ItemStack s : visibleSlots(client)) {
            if (!formatDurability(s, fmt).isEmpty()) return true;
        }
        return false;
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

        AuroraConfig.ArmorDurabilityFormat fmt = cfg.armorDurabilityFormat;
        if (fmt == null) fmt = AuroraConfig.ArmorDurabilityFormat.REMAINING;
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
            int cellW = Math.max(ICON, (cachedWidth.get() - (n - 1) * COL_GAP) / n);
            int textSpace = (fmt != AuroraConfig.ArmorDurabilityFormat.NONE) ? tr.lineHeight + TEXT_GAP : 0;
            for (int i = 0; i < n; i++) {
                ItemStack s = stacks.get(i);
                int cellX = x + i * (cellW + COL_GAP);
                int iconX = cellX + (cellW - ICON) / 2;
                int iconY = y + textSpace;
                ctx.item(s, iconX, iconY);

                String Component = formatDurability(s, fmt);
                if (!Component.isEmpty()) {
                    int tw = tr.width(Component);
                    int tx = cellX + (cellW - tw) / 2;
                    int ty = y;
                    com.aurora.client.hud.HudTextPainter.draw(ctx,tr, textCache.text(i, Component), tx, ty, color);
                }

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
     * stretched sub-region of the hotbar sprite, vertically a column of
     * touching hotbar slot cells. The 16×16 item icon is centered in its
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

        List<ItemStack> stacks = visibleSlots(client);
        int n = stacks.size();
        if (n == 0) return;

        boolean hasText = fmt != AuroraConfig.ArmorDurabilityFormat.NONE;

        if (horizontal) {
            // Uniform strip scale: widen every cell equally when a text
            // needs more than its 20 px column (native cells otherwise).
            int maxText = 0;
            if (hasText) {
                for (ItemStack s : stacks) maxText = Math.max(maxText, tr.width(formatDurability(s, fmt)));
            }
            float s = hasText && maxText > 0 ? Math.max(1f, (maxText + 2) / (float) HudBackgrounds.CELL_PITCH) : 1f;
            int textSpace = (hasText && maxText > 0) ? tr.lineHeight + TEXT_GAP : 0;
            int stripY = y + textSpace;

            HudBackgrounds.hotbarStrip(ctx, x, stripY, n, s, 1f);

            for (int i = 0; i < n; i++) {
                ItemStack stack = stacks.get(i);
                // Cell k's center sits at sprite x = 10.5 + k*20 (1 px left
                // border + 18 px interior + shared right border, pitch 20).
                float centerX = (10.5f + i * HudBackgrounds.CELL_PITCH) * s;
                int iconX = x + Math.round(centerX) - ICON / 2;
                ctx.item(stack, iconX, stripY + 3);

                if (drawBars && stack.getMaxDamage() > 0) {
                    drawDurabilityBar(ctx, stack, iconX + 2, stripY + 3 + 13);
                }

                String text = formatDurability(stack, fmt);
                if (hasText && !text.isEmpty()) {
                    int tw = tr.width(text);
                    int tx = x + Math.round(centerX) - tw / 2;
                    int ty = y;
                    com.aurora.client.hud.HudTextPainter.draw(ctx,tr, textCache.text(i, text), tx, ty, textColor);
                }
            }
            return;
        }

        // Vertical: a column of touching hotbar slot cells (a 20×22 crop
        // that carries both borders per row), text to the right.
        for (int i = 0; i < n; i++) {
            ItemStack stack = stacks.get(i);
            int rowY = y + i * (HudBackgrounds.CELL_PITCH + 2);
            HudBackgrounds.hotbarCell(ctx, x, rowY, HudBackgrounds.CELL_PITCH, HudBackgrounds.CELL_PITCH + 2, 1f);

            int iconX = x + (HudBackgrounds.CELL_PITCH - ICON) / 2;
            ctx.item(stack, iconX, rowY + 3);

            if (drawBars && stack.getMaxDamage() > 0) {
                drawDurabilityBar(ctx, stack, iconX + 2, rowY + 3 + 13);
            }

            String text = formatDurability(stack, fmt);
            if (hasText && !text.isEmpty()) {
                int tx = x + HudBackgrounds.CELL_PITCH + TEXT_GAP;
                int ty = rowY + (HudBackgrounds.CELL_PITCH + 2 - tr.lineHeight) / 2;
                com.aurora.client.hud.HudTextPainter.draw(ctx,tr, textCache.text(i, text), tx, ty, textColor);
            }
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
