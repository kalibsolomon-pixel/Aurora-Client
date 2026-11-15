package com.aurora.client.config;

import java.lang.reflect.Modifier;
import java.util.Set;

/**
 * Restricts the VANILLA HUD background to the two modules a vanilla texture
 * actually backs (Armor HUD's hotbar strip, Potion HUD's status-effect
 * slots). Every other {@code *BgMode} field treats a persisted
 * VANILLA as SOLID.
 *
 * <p>The VANILLA option shipped in every module's dropdown before the
 * restriction, so configs and profiles can legitimately carry
 * {@code "VANILLA"} for modules that have no vanilla graphic behind them —
 * hand-edited JSON too. Normalization folds those onto SOLID, which is the
 * same flat panel those modules drew behind the VANILLA option.
 *
 * <p>Idempotent and cheap (one reflective pass): run it at config load and
 * after every profile apply. No one-shot migrator guard — unlike
 * {@code ThemeMigrator} or {@code EffectExpiryMigrator} nothing is folded
 * into another representation; the value simply converges, so re-running
 * after each load or profile switch is always correct.
 */
public final class HudBackgroundPolicy {

    private HudBackgroundPolicy() {}

    /** Fields where a vanilla resource actually backs the module. */
    private static final Set<String> VANILLA_ALLOWED = Set.of(
            "armorHudBgMode",   // Armor HUD draws the hotbar-sprite strip
            "potionBgMode");    // Potion HUD draws vanilla status-effect slots

    /** Every {@code *BgMode} field not in {@link #VANILLA_ALLOWED} reads VANILLA as SOLID. */
    public static void normalize(AuroraConfig cfg) {
        if (cfg == null) return;
        for (var field : AuroraConfig.class.getFields()) {
            int mod = field.getModifiers();
            if (Modifier.isStatic(mod) || Modifier.isFinal(mod)) continue;
            if (!field.getName().endsWith("BgMode")) continue;
            if (field.getType() != AuroraConfig.HudBackground.class) continue;
            if (VANILLA_ALLOWED.contains(field.getName())) continue;
            try {
                if (field.get(cfg) == AuroraConfig.HudBackground.VANILLA) {
                    field.set(cfg, AuroraConfig.HudBackground.SOLID);
                }
            } catch (IllegalAccessException ignored) {
                // Public fields on our own class cannot fail; keep the pass total.
            }
        }
    }
}
