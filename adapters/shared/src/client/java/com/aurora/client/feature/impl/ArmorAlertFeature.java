package com.aurora.client.feature.impl;

import com.aurora.client.alerts.DurabilityLatch;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import com.aurora.client.hud.AlertHudState;
import com.aurora.client.hud.AlertManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Watches the player's armor pieces (and optionally held tools) for low
 * durability, feeding the {@code armor_alert} HUD module
 * ({@link com.aurora.client.hud.module.ArmorAlertHudModule}) through
 * {@link AlertHudState}. The shared popup card no longer shows this
 * source; the alert sound still plays when a latch fires.
 *
 * <p>Latching is per item fingerprint — (registry id, max damage, damage)
 * — via the pure {@link DurabilityLatch}, not per slot: unequipping a low
 * piece, moving it through the inventory and re-equipping it never
 * re-fires, repairing or replacing it re-arms the warning naturally, and
 * two identical items at different damage levels latch independently.
 * Items never equipped do not alert: equipment slots observe (may latch),
 * the main inventory only refreshes existing latches. Latched items that
 * leave the player's possession stop rendering once the scan stops seeing
 * them and prune from the latch {@link DurabilityLatch#PRUNE_AFTER_TICKS}
 * later.
 *
 * <p>The sound is selectable via
 * {@link AuroraConfig.ArmorAlertSoundChoice}; the picker UI also exposes a
 * "Preview" button that calls {@link #playPreview()} directly.
 */
public class ArmorAlertFeature implements Feature {
    public static final String ID = "armor_alert";

    private static final DurabilityLatch LATCH = new DurabilityLatch();

    /** Row rendering data captured at fire time: the slot label and a live stack for the icon. */
    private record RowData(String label, ItemStack stack) {}
    private static final Map<DurabilityLatch.Fingerprint, RowData> ROW_DATA = new HashMap<>();

    private static long tick = 0;

    @Override public String id() { return ID; }
    @Override public void onRegister() {}

    @Override
    public void onTick(Minecraft client) {
        AuroraConfig cfg = AuroraConfig.get();
        boolean trackArmor = cfg.armorAlertEnabled;
        boolean trackTools = cfg.toolDurabilityAlertEnabled;
        if (!trackArmor && !trackTools) {
            resetSession();
            return;
        }
        if (client == null || client.player == null) return;

        int thresholdPct = Math.max(1, Math.min(100, cfg.armorAlertThresholdPct));
        tick++;

        // Occurrences of each fingerprint this tick, so a row can show how
        // many identical stacks share it ("×2") without suppressing either.
        Map<DurabilityLatch.Fingerprint, Integer> counts = new HashMap<>();

        // ---- Equipment: tracked slots observe (may latch + fire), the
        // rest refresh (an item moved to an untracked slot keeps its
        // latch alive but cannot create one). ----
        observe(client.player.getItemBySlot(EquipmentSlot.HEAD),
                trackArmor, "Helmet", thresholdPct, counts);
        observe(client.player.getItemBySlot(EquipmentSlot.CHEST),
                trackArmor, "Chestplate", thresholdPct, counts);
        observe(client.player.getItemBySlot(EquipmentSlot.LEGS),
                trackArmor, "Leggings", thresholdPct, counts);
        observe(client.player.getItemBySlot(EquipmentSlot.FEET),
                trackArmor, "Boots", thresholdPct, counts);
        observe(client.player.getItemBySlot(EquipmentSlot.MAINHAND),
                trackTools, "Main Hand", thresholdPct, counts);
        observe(client.player.getItemBySlot(EquipmentSlot.OFFHAND),
                trackTools, "Off Hand", thresholdPct, counts);

        // ---- Main inventory: refresh only — items parked there keep
        // their latch (the move must not re-fire, and unequipping must
        // not arm a piece that never alerted). ----
        for (int i = 0; i < 36; i++) {
            refresh(client.player.getInventory().getItem(i), thresholdPct, counts);
        }

        LATCH.prune(tick, DurabilityLatch.PRUNE_AFTER_TICKS);
        // Row data for pruned fingerprints is stale by definition.
        ROW_DATA.keySet().removeIf(fp -> !LATCH.isLatched(fp));

        // ---- Rows: one per latched fingerprint the player still holds. ----
        List<AlertHudState.ArmorRow> rows = new ArrayList<>();
        for (DurabilityLatch.Entry entry : LATCH.entries()) {
            DurabilityLatch.Fingerprint fp = entry.fingerprint;
            int count = counts.getOrDefault(fp, 0);
            if (count == 0) continue; // dropped / stored: row goes now, latch prunes later
            RowData data = ROW_DATA.get(fp);
            if (data == null) continue; // unreachable in practice; defensive
            rows.add(new AlertHudState.ArmorRow(
                    AlertHudState.ArmorRow.DurabilityFingerprint.of(fp),
                    data.label(), data.stack(), fp.remainingPct(), count));
        }
        AlertHudState.setArmorRows(rows);
    }

    private static void observe(ItemStack stack, boolean tracked, String label,
                                int thresholdPct,
                                Map<DurabilityLatch.Fingerprint, Integer> counts) {
        DurabilityLatch.Fingerprint fp = fingerprint(stack, thresholdPct, counts);
        if (fp == null) return;
        if (tracked) {
            if (LATCH.observe(fp, tick)) {
                ROW_DATA.put(fp, new RowData(label, stack));
                AlertManager.playAlertSound();
            }
        } else {
            LATCH.refresh(fp, tick);
        }
    }

    private static void refresh(ItemStack stack, int thresholdPct,
                                Map<DurabilityLatch.Fingerprint, Integer> counts) {
        DurabilityLatch.Fingerprint fp = fingerprint(stack, thresholdPct, counts);
        if (fp == null) return;
        LATCH.refresh(fp, tick);
    }

    /** The fingerprint when this stack is at or below the threshold; null otherwise (and counts nothing). */
    private static DurabilityLatch.Fingerprint fingerprint(ItemStack stack, int thresholdPct,
                                                           Map<DurabilityLatch.Fingerprint, Integer> counts) {
        if (stack == null || stack.isEmpty() || stack.getMaxDamage() <= 0) return null;
        DurabilityLatch.Fingerprint fp = new DurabilityLatch.Fingerprint(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.getMaxDamage(), stack.getDamageValue());
        if (fp.remainingPct() > thresholdPct) return null;
        counts.merge(fp, 1, Integer::sum);
        return fp;
    }

    /**
     * Forgets every latch and row: world join / respawn (via the
     * ClientPlayNetworkHandler world-join mixin) and whenever the alert
     * sources are all off.
     */
    public static void resetSession() {
        LATCH.reset();
        ROW_DATA.clear();
        AlertHudState.setArmorRows(List.of());
    }

    /** Plays the currently-configured alert sound for the user (preview button). */
    public static void playPreview() {
        AlertManager.playPreview();
    }
}
