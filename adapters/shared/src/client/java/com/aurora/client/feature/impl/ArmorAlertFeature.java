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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Watches the player's armor pieces (and optionally held tools) for low
 * durability, feeding the {@code armor_alert} HUD module
 * ({@link com.aurora.client.hud.module.ArmorAlertHudModule}) through
 * {@link AlertHudState}. The shared popup card no longer shows this
 * source; the alert sound still plays when a latch fires.
 *
 * <p>Latching follows each item, not its slot, via the pure
 * {@link DurabilityLatch}: further wear, unequipping a low piece, moving it
 * through the inventory and re-equipping it never re-fire. The warning
 * re-arms only once the player holds that kind of item above the threshold
 * again (mended or replaced). Items never equipped do not alert: equipment
 * slots may latch, the main inventory only keeps existing latches alive.
 * Latched items that leave the player's possession stop rendering at once
 * and prune from the latch {@link DurabilityLatch#PRUNE_AFTER_TICKS} later.
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

        List<DurabilityLatch.Observation> low = new ArrayList<>();
        Set<DurabilityLatch.Kind> healthy = new HashSet<>();

        // Equipment: tracked slots may latch, the rest only keep a latch alive.
        scan(client.player.getItemBySlot(EquipmentSlot.HEAD), trackArmor, "Helmet", thresholdPct, low, healthy);
        scan(client.player.getItemBySlot(EquipmentSlot.CHEST), trackArmor, "Chestplate", thresholdPct, low, healthy);
        scan(client.player.getItemBySlot(EquipmentSlot.LEGS), trackArmor, "Leggings", thresholdPct, low, healthy);
        scan(client.player.getItemBySlot(EquipmentSlot.FEET), trackArmor, "Boots", thresholdPct, low, healthy);
        scan(client.player.getItemBySlot(EquipmentSlot.MAINHAND), trackTools, "Main Hand", thresholdPct, low, healthy);
        scan(client.player.getItemBySlot(EquipmentSlot.OFFHAND), trackTools, "Off Hand", thresholdPct, low, healthy);
        // Main inventory, minus the selected hotbar stack already scanned as the main hand.
        int selected = client.player.getInventory().getSelectedSlot();
        for (int i = 0; i < 36; i++) {
            if (i == selected) continue;
            scan(client.player.getInventory().getItem(i), false, null, thresholdPct, low, healthy);
        }

        if (!LATCH.update(low, healthy, tick).isEmpty()) AlertManager.playAlertSound();

        // Rows: one per latched item the player still holds.
        List<AlertHudState.ArmorRow> rows = new ArrayList<>();
        for (DurabilityLatch.Entry entry : LATCH.entries()) {
            if (!entry.present() || !(entry.payload() instanceof RowData data)) continue;
            DurabilityLatch.Fingerprint fp = entry.fingerprint();
            rows.add(new AlertHudState.ArmorRow(
                    AlertHudState.ArmorRow.DurabilityFingerprint.of(fp),
                    data.label(), data.stack(), fp.remainingPct(), 1));
        }
        AlertHudState.setArmorRows(rows);
    }

    /** Sorts a damageable stack into this tick's low observations or healthy kinds. */
    private static void scan(ItemStack stack, boolean tracked, String label, int thresholdPct,
                             List<DurabilityLatch.Observation> low, Set<DurabilityLatch.Kind> healthy) {
        if (stack == null || stack.isEmpty() || stack.getMaxDamage() <= 0) return;
        DurabilityLatch.Fingerprint fp = new DurabilityLatch.Fingerprint(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                stack.getMaxDamage(), stack.getDamageValue());
        if (fp.remainingPct() > thresholdPct) {
            healthy.add(fp.kind());
        } else {
            low.add(new DurabilityLatch.Observation(fp, tracked, tracked ? new RowData(label, stack) : null));
        }
    }

    /**
     * Forgets every latch and row: world join / respawn (via the
     * ClientPlayNetworkHandler world-join mixin) and whenever the alert
     * sources are all off.
     */
    public static void resetSession() {
        LATCH.reset();
        AlertHudState.setArmorRows(List.of());
    }

    /** Plays the currently-configured alert sound for the user (preview button). */
    public static void playPreview() {
        AlertManager.playPreview();
    }
}
