package com.aurora.client.config;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.profile.ProfileManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One-way, one-shot migration flipping the per-effect Effect-Expiry alert
 * from the exclusion-set model the feature first shipped with
 * ({@link AuroraConfig#effectExpiryExcludedEffects} — absent = alert) to
 * the inclusion-list model ({@link AuroraConfig#effectExpiryIncludedEffects}
 * — present = alert, the {@code ItemScaleSetting} search-then-add list).
 *
 * <p>Semantics, so nobody's actual alert behavior changes on upgrade:
 * <ul>
 *   <li><b>Config with prior data</b> (an aurora.json existed at load):
 *       inclusion := every registered effect NOT in the legacy exclusion
 *       set. An empty legacy set (the overwhelming case — the exclusion
 *       feature's own default) yields every effect, exactly what alerted
 *       before; a curated exclusion set yields precisely the complement,
 *       what was still alerting.</li>
 *   <li><b>Genuinely fresh config</b> (no file at load): the inclusion
 *       list stays empty — the new paradigm's actual default, fine for new
 *       installs because there is no prior behavior to preserve.</li>
 * </ul>
 *
 * <p>Same shape as {@code HitregMigrator}/ThemeMigrator: never throws,
 * logs what it did, armed even on fresh installs, guarded by
 * {@link AuroraConfig#migratedEffectExpiryExclusions} (profile-excluded)
 * so it fires exactly once. Call after the active profile has been applied
 * (see the call site in {@code AuroraClient}) — applyProfile resets
 * profile-scoped fields to defaults before overlaying, so a migration run
 * any earlier would be wiped; the legacy exclusion value itself survives
 * applyProfile either from the profile (if it was captured) or from
 * aurora.json (if not), so reading the live config here sees it either
 * way. The result is saved to both aurora.json and the active profile so
 * a later profile apply cannot undo it.
 */
public final class EffectExpiryMigrator {
    private EffectExpiryMigrator() {}

    public static void runOnce() {
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.migratedEffectExpiryExclusions) return;
        Logger log = AuroraClient.LOGGER;
        try {
            if (AuroraConfig.loadedFromDisk()) {
                Set<String> legacy = cfg.effectExpiryExcludedEffects;
                List<String> inclusion = new ArrayList<>();
                for (MobEffect eff : BuiltInRegistries.MOB_EFFECT) {
                    Identifier key = BuiltInRegistries.MOB_EFFECT.getKey(eff);
                    if (key == null || legacy.contains(key.toString())) continue;
                    inclusion.add(key.toString());
                }
                int before = cfg.effectExpiryIncludedEffects.size();
                for (String id : inclusion) {
                    if (!cfg.effectExpiryIncludedEffects.contains(id)) {
                        cfg.effectExpiryIncludedEffects.add(id);
                    }
                }
                log.info("[Aurora] migrated effect-expiry alerts to the inclusion list: {} effects alert "
                                + "(legacy exclusion set had {}), {} pre-existing entries kept",
                        inclusion.size(), legacy.size(), before);
            } else {
                log.info("[Aurora] fresh config: effect-expiry inclusion list starts empty (no prior behavior to preserve)");
            }
        } catch (Throwable t) {
            log.warn("[Aurora] effect-expiry migration failed; keeping the inclusion list as loaded", t);
        }
        cfg.migratedEffectExpiryExclusions = true;
        try {
            AuroraConfig.save();
        } catch (Throwable t) {
            log.warn("[Aurora] failed to save config after effect-expiry migration", t);
        }
        try {
            ProfileManager.getInstance().saveCurrent();
        } catch (Throwable t) {
            log.warn("[Aurora] failed to sync active profile after effect-expiry migration", t);
        }
    }
}
