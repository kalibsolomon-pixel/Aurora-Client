package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import com.aurora.client.hud.AlertManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Fires {@link AlertManager} popups for:
 * <ul>
 *   <li><b>Low hunger</b> — when the player's food level drops below the
 *       configured threshold (falling-edge so it doesn't repeat).</li>
 *   <li><b>Effect expiry</b> — when an active potion effect's remaining
 *       duration drops below the configured threshold (seconds). Uses
 *       per-effect tracking so each effect alerts once per application.</li>
 * </ul>
 *
 * <p>Works in the same tick-driven "threshold crossed → fire once" pattern
 * as {@link ArmorAlertFeature}, reusing the shared {@link AlertManager}
 * queue and renderer so all alert sources share one popup.
 */
public class StatusAlertFeature implements Feature {
    public static final String ID = "status_alerts";

    /** Tracks which effects have already fired their expiry alert. */
    private final Map<Holder<MobEffect>, Boolean> alertedEffects = new HashMap<>();
    private boolean wasLowHunger = false;

    @Override public String id() { return ID; }

    @Override
    public void onTick(Minecraft client) {
        if (client == null || client.player == null) return;
        AuroraConfig cfg = AuroraConfig.get();

        checkHunger(client, cfg);
        checkEffects(client, cfg);
    }

    // ===== Hunger =====

    private void checkHunger(Minecraft client, AuroraConfig cfg) {
        if (!cfg.hungerAlertEnabled) {
            wasLowHunger = false;
            return;
        }

        FoodData food = client.player.getFoodData();
        if (food == null) return;

        int level = food.getFoodLevel();
        int threshold = Math.max(1, Math.min(20, cfg.hungerAlertThreshold));
        boolean nowLow = level <= threshold;

        if (nowLow && !wasLowHunger) {
            AlertManager.fire("LOW HUNGER",
                    level + " / 20 drumsticks",
                    null, com.aurora.client.theme.HudStatus.ALERT_CAUTION,
                    AlertManager.Priority.WARNING, 3_000L);
        }
        wasLowHunger = nowLow;
    }

    // ===== Effect expiry =====

    private void checkEffects(Minecraft client, AuroraConfig cfg) {
        if (!cfg.effectExpiryAlertEnabled) {
            alertedEffects.clear();
            return;
        }

        int thresholdSecs = Math.max(1, cfg.effectExpiryThresholdSeconds);

        // Build a set of currently-active effects so we can prune stale
        // entries from the tracking map (effects that expired naturally
        // without hitting our threshold — e.g. drank milk).
        Map<Holder<MobEffect>, MobEffectInstance> active = new HashMap<>();
        for (MobEffectInstance inst : client.player.getActiveEffects()) {
            // Only alert for effects with a visible (finite) duration.
            if (inst.isInfiniteDuration()) continue;
            active.put(inst.getEffect(), inst);
        }

        // Prune effects no longer active from our tracking map.
        Iterator<Map.Entry<Holder<MobEffect>, Boolean>> it = alertedEffects.entrySet().iterator();
        while (it.hasNext()) {
            if (!active.containsKey(it.next().getKey())) {
                it.remove();
            }
        }

        // Check each active effect for threshold crossing.
        for (Map.Entry<Holder<MobEffect>, MobEffectInstance> entry : active.entrySet()) {
            Holder<MobEffect> effect = entry.getKey();
            MobEffectInstance inst = entry.getValue();

            int remainingSecs = inst.getDuration() / 20; // ticks → seconds
            boolean alreadyAlerted = alertedEffects.getOrDefault(effect, false);

            if (remainingSecs <= thresholdSecs && !alreadyAlerted) {
                String name = inst.getEffect().value().getDescriptionId();
                // Fallback if the effect has no registered translation key.
                if (name == null || name.isEmpty()) {
                    name = "Effect";
                }
                // Strip the "effect." / "effect.minecraft." prefix for a
                // cleaner display ("effect.minecraft.strength" → "strength").
                name = net.minecraft.network.chat.Component.translatable(name).getString();
                if (name == null || name.isEmpty()) name = "Effect";
                AlertManager.fire("EFFECT EXPIRING",
                        name + " · " + Math.max(0, remainingSecs) + "s",
                        null, 0xFF88CCFF, AlertManager.Priority.INFO, 3_000L);
                alertedEffects.put(effect, true);
            } else if (remainingSecs > thresholdSecs) {
                // Effect was re-applied or has plenty of time — reset.
                alertedEffects.put(effect, false);
            }
        }
    }
}