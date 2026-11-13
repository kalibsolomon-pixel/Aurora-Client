package com.aurora.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * VANILLA folding ({@code HudBackgroundPolicy}): allowed fields pass
 * through, every other {@code *BgMode} field reads VANILLA as SOLID,
 * non-VANILLA values and other fields are untouched, and the pass is
 * idempotent. Uses a fresh instance so nothing touches the singleton.
 */
class HudBackgroundPolicyTest {

    @Test
    void vanillaSurvivesOnTheBackedModules() {
        AuroraConfig cfg = new AuroraConfig();
        cfg.armorHudBgMode = AuroraConfig.HudBackground.VANILLA;
        cfg.potionBgMode = AuroraConfig.HudBackground.VANILLA;
        HudBackgroundPolicy.normalize(cfg);
        assertEquals(AuroraConfig.HudBackground.VANILLA, cfg.armorHudBgMode);
        assertEquals(AuroraConfig.HudBackground.VANILLA, cfg.potionBgMode);
    }

    @Test
    void vanillaFoldsToSolidEverywhereElse() {
        AuroraConfig cfg = new AuroraConfig();
        cfg.infoBgMode = AuroraConfig.HudBackground.VANILLA;
        cfg.cpsBgMode = AuroraConfig.HudBackground.VANILLA;
        cfg.reachBgMode = AuroraConfig.HudBackground.VANILLA;
        cfg.totemPopBgMode = AuroraConfig.HudBackground.VANILLA;
        cfg.keystrokesBgMode = AuroraConfig.HudBackground.VANILLA;
        cfg.sprintHudBgMode = AuroraConfig.HudBackground.VANILLA;
        HudBackgroundPolicy.normalize(cfg);
        assertEquals(AuroraConfig.HudBackground.SOLID, cfg.infoBgMode);
        assertEquals(AuroraConfig.HudBackground.SOLID, cfg.cpsBgMode);
        assertEquals(AuroraConfig.HudBackground.SOLID, cfg.reachBgMode);
        assertEquals(AuroraConfig.HudBackground.SOLID, cfg.totemPopBgMode);
        assertEquals(AuroraConfig.HudBackground.SOLID, cfg.keystrokesBgMode);
        assertEquals(AuroraConfig.HudBackground.SOLID, cfg.sprintHudBgMode);
    }

    @Test
    void nonVanillaValuesAreUntouched() {
        AuroraConfig cfg = new AuroraConfig();
        cfg.infoBgMode = AuroraConfig.HudBackground.NONE;
        cfg.armorHudBgMode = AuroraConfig.HudBackground.SOLID;
        cfg.potionBgMode = AuroraConfig.HudBackground.NONE;
        HudBackgroundPolicy.normalize(cfg);
        assertEquals(AuroraConfig.HudBackground.NONE, cfg.infoBgMode);
        assertEquals(AuroraConfig.HudBackground.SOLID, cfg.armorHudBgMode);
        assertEquals(AuroraConfig.HudBackground.NONE, cfg.potionBgMode);
    }

    @Test
    void idempotent() {
        AuroraConfig cfg = new AuroraConfig();
        cfg.infoBgMode = AuroraConfig.HudBackground.VANILLA;
        cfg.armorHudBgMode = AuroraConfig.HudBackground.VANILLA;
        HudBackgroundPolicy.normalize(cfg);
        HudBackgroundPolicy.normalize(cfg);
        HudBackgroundPolicy.normalize(cfg);
        assertEquals(AuroraConfig.HudBackground.SOLID, cfg.infoBgMode);
        assertEquals(AuroraConfig.HudBackground.VANILLA, cfg.armorHudBgMode);
    }

    @Test
    void nullConfigIsSafe() {
        HudBackgroundPolicy.normalize(null);
    }
}
