package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import com.aurora.client.hud.AlertHudState;
import com.aurora.client.hud.AlertManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Watches the player's active potion effects for expiry, feeding the
 * {@code potion_alert} HUD module
 * ({@link com.aurora.client.hud.module.EffectExpiryHudModule}) through
 * {@link AlertHudState}. The shared popup card no longer shows this
 * source; the alert sound still plays when an effect first crosses the
 * threshold.
 *
 * <p><b>Effect expiry</b> — when an active potion effect's remaining
 * duration drops below the configured threshold (seconds). Uses
 * per-effect tracking so each effect alerts once per application. While
 * the effect stays at or below the threshold its row stays up with a live
 * seconds readout; re-applying it (or milk, or natural expiry) clears the
 * row. Only effects on the curated inclusion list
 * ({@code effectExpiryIncludedEffects}, the Per-Effect Alerts
 * search-then-add list) alert; an empty list means nothing does. Configs
 * from the one-version exclusion-set model are migrated once by
 * {@code EffectExpiryMigrator} so alert behavior carries over.
 */
public class StatusAlertFeature implements Feature {
    public static final String ID = "status_alerts";

    /** Tracks which effects have already fired their expiry alert. */
    private final Map<Holder<MobEffect>, Boolean> alertedEffects = new HashMap<>();

    /** Localized display names, cached per translation key (tiny key space). */
    private static final Map<String, String> NAME_CACHE = new HashMap<>();

    private static StatusAlertFeature instance;
    /** The registered singleton, for the world-join reset mixin. */
    public static StatusAlertFeature get() { return instance; }

    @Override public String id() { return ID; }

    @Override
    public void onRegister() {
        instance = this;
    }

    @Override
    public void onTick(Minecraft client) {
        if (client == null || client.player == null) return;
        AuroraConfig cfg = AuroraConfig.get();

        checkEffects(client, cfg);
    }

    // ===== Effect expiry =====

    private void checkEffects(Minecraft client, AuroraConfig cfg) {
        if (!cfg.effectExpiryAlertEnabled) {
            if (!alertedEffects.isEmpty()) {
                alertedEffects.clear();
                AlertHudState.setEffectRows(List.of());
            }
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
        List<AlertHudState.EffectRow> rows = new ArrayList<>();
        for (Map.Entry<Holder<MobEffect>, MobEffectInstance> entry : active.entrySet()) {
            Holder<MobEffect> effect = entry.getKey();
            MobEffectInstance inst = entry.getValue();

            // Per-effect granularity: only effects on the curated inclusion
            // list (Alerts → Per-Effect Alerts, the search-then-add list)
            // alert. Empty list = nothing alerts — the new-install default.
            String effectId = effect.unwrapKey().map(k -> k.identifier().toString()).orElse(null);
            if (effectId == null || !cfg.effectExpiryIncludedEffects.contains(effectId)) {
                continue;
            }

            int remainingSecs = inst.getDuration() / 20; // ticks → seconds
            boolean alreadyAlerted = alertedEffects.getOrDefault(effect, false);

            if (remainingSecs <= thresholdSecs) {
                if (!alreadyAlerted) {
                    alertedEffects.put(effect, true);
                    AlertManager.playAlertSound();
                }
                // The row stays up (with a live countdown) while the effect
                // remains at or below the threshold, then goes with it.
                rows.add(new AlertHudState.EffectRow(effectId,
                        effectSprite(effectId), displayName(effect), Math.max(0, remainingSecs)));
            } else {
                // Effect was re-applied or has plenty of time — reset.
                alertedEffects.put(effect, false);
            }
        }
        AlertHudState.setEffectRows(rows);
    }

    /** The vanilla 18×18 status-effect sprite in the GUI atlas: {@code <ns>:mob_effect/<path>}. */
    private static Identifier effectSprite(String effectId) {
        Identifier loc = Identifier.parse(effectId);
        return Identifier.fromNamespaceAndPath(loc.getNamespace(), "mob_effect/" + loc.getPath());
    }

    /** Localized effect name ("Strength"), cached per translation key. */
    private static String displayName(Holder<MobEffect> effect) {
        String key = effect.value().getDescriptionId();
        String cached = NAME_CACHE.get(key);
        if (cached != null) return cached;
        String name = net.minecraft.network.chat.Component.translatable(key).getString();
        if (name == null || name.isEmpty()) name = "Effect";
        NAME_CACHE.put(key, name);
        return name;
    }

    /** Forgets every latched effect and clears the rows (world join / respawn). */
    public void resetSession() {
        if (alertedEffects.isEmpty()) return;
        alertedEffects.clear();
        AlertHudState.setEffectRows(List.of());
    }
}
