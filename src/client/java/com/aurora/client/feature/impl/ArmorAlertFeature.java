package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import com.aurora.client.hud.AlertManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Watches the player's armor pieces (and optionally held tools) for low
 * durability. When any item crosses below the configured threshold, fires
 * a brief popup alert via the shared {@link AlertManager}.
 *
 * <p>Each slot is tracked independently with a "was-low" flag, so an alert
 * only re-triggers after the piece is repaired or replaced above threshold
 * and then drops below again — no spam on every tick while already broken.
 *
 * <p>The alert sound is selectable via
 * {@link AuroraConfig.ArmorAlertSoundChoice}; the picker UI also exposes a
 * "Preview" button that calls {@link #playPreview()} directly.
 */
public class ArmorAlertFeature implements Feature {
    public static final String ID = "armor_alert";

    private final Map<EquipmentSlot, Boolean> wasLowArmor = new EnumMap<>(EquipmentSlot.class);
    private boolean wasLowMainHand = false;
    private boolean wasLowOffHand = false;

    @Override public String id() { return ID; }
    @Override public void onRegister() {}

    @Override
    public void onTick(Minecraft client) {
        if (client == null || client.player == null) return;
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.armorAlertEnabled) return;

        int thresholdPct = Math.max(1, Math.min(100, cfg.armorAlertThresholdPct));

        // ---- Armor slots ----
        for (EquipmentSlot slot : new EquipmentSlot[] {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            ItemStack s = client.player.getItemBySlot(slot);
            if (s == null || s.isEmpty() || s.getMaxDamage() <= 0) {
                wasLowArmor.put(slot, false);
                continue;
            }

            int remaining = s.getMaxDamage() - s.getDamageValue();
            int pct = (int) Math.round(100.0 * remaining / s.getMaxDamage());
            boolean nowLow = pct <= thresholdPct;
            boolean prevLow = wasLowArmor.getOrDefault(slot, false);

            if (nowLow && !prevLow) {
                String slotName = slot == EquipmentSlot.HEAD ? "Helmet"
                        : slot == EquipmentSlot.CHEST ? "Chestplate"
                        : slot == EquipmentSlot.LEGS ? "Leggings"
                        : "Boots";
                AlertManager.fire("LOW DURABILITY",
                        slotName + " · " + pct + "%",
                        s, 0xFFFF5555, AlertManager.Priority.WARNING, 3_000L);
            }
            wasLowArmor.put(slot, nowLow);
        }

        // ---- Held tools (optional) ----
        if (cfg.toolDurabilityAlertEnabled) {
            checkHeldTool(client, EquipmentSlot.MAINHAND, cfg, thresholdPct);
            checkHeldTool(client, EquipmentSlot.OFFHAND, cfg, thresholdPct);
        } else {
            wasLowMainHand = false;
            wasLowOffHand = false;
        }
    }

    private void checkHeldTool(Minecraft client, EquipmentSlot slot,
                                AuroraConfig cfg, int thresholdPct) {
        ItemStack s = client.player.getItemBySlot(slot);
        if (s == null || s.isEmpty() || s.getMaxDamage() <= 0) {
            if (slot == EquipmentSlot.MAINHAND) wasLowMainHand = false;
            else wasLowOffHand = false;
            return;
        }

        int remaining = s.getMaxDamage() - s.getDamageValue();
        int pct = (int) Math.round(100.0 * remaining / s.getMaxDamage());
        boolean nowLow = pct <= thresholdPct;
        boolean prevLow = slot == EquipmentSlot.MAINHAND ? wasLowMainHand : wasLowOffHand;

        if (nowLow && !prevLow) {
            String label = slot == EquipmentSlot.MAINHAND ? "Main Hand" : "Off Hand";
            AlertManager.fire("LOW DURABILITY",
                    label + " · " + pct + "%",
                    s, 0xFFFF5555, AlertManager.Priority.WARNING, 3_000L);
        }
        if (slot == EquipmentSlot.MAINHAND) wasLowMainHand = nowLow;
        else wasLowOffHand = nowLow;
    }

    /** Plays the currently-configured alert sound for the user (preview button). */
    public static void playPreview() {
        AlertManager.playPreview();
    }
}