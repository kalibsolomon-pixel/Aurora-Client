package dev.phenomena;

import static org.junit.jupiter.api.Assertions.*;

import dev.phenomena.analytics.*;
import dev.phenomena.combat.events.CombatEvent;
import dev.phenomena.combat.events.CombatEvent.*;
import dev.phenomena.combat.events.Confidence;
import dev.phenomena.combat.fights.*;
import dev.phenomena.combat.metrics.Geometry;
import dev.phenomena.combat.metrics.Statistics;
import dev.phenomena.combat.quality.HitQuality;
import dev.phenomena.combat.tracking.CombatExchange;
import dev.phenomena.config.Config;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link CombatBreakdown} across Sword/NethPot and Shield/Axe PvP scenarios, plus {@link
 * FightAnalytics} incoming O(N) sweep equivalence.
 */
class CombatBreakdownTest {

  private static CombatEvent.Attack attack(
      double cooldown, boolean sprinting, boolean crit, String item) {
    var box = new Geometry.Box(0, 0, 0, .6, 1.8, .6);
    var origin = new Geometry.Vec(0, 1.62, -2.8);
    var m =
        new CombatEvent.Measurement(
            item + ";slot=0;attributes=0",
            item.contains("axe") ? 1.0 : 1.6,
            item.contains("axe") ? 20.0 : 12.5,
            null,
            origin,
            box,
            null,
            null,
            "MELEE_ATTEMPT");
    return new CombatEvent.Attack(cooldown, 2.85, .5, .6, 0, sprinting, crit, true, m);
  }

  private static FightRecord syntheticFight() {
    long t0 = 1_000_000_000L;
    long sec = 1_000_000_000L;
    String opp = "opp-1";
    List<CombatEvent> events = new ArrayList<>();
    List<FightRecord.Evaluation> evals = new ArrayList<>();
    long seq = 0;

    // Initial full HP (20 + 4 absorption from gapple)
    events.add(
        new CombatEvent(
            ++seq, t0, Kind.HEALTH, "local", null, Confidence.OBSERVED, null, "hp", 10L, Context.health(20.0, 4.0)));

    // Exchange 1 (0.0s - 3.0s): You open with axe shield disable, swap to sword in 0.40s, land 2
    // sprint-reset hits -> opener converted into combo!
    long axeSeq = ++seq;
    events.add(
        new CombatEvent(
            axeSeq,
            t0 + (long) (0.5 * sec),
            Kind.ATTACK,
            "local",
            opp,
            Confidence.OBSERVED,
            attack(1.0, true, false, "minecraft:netherite_axe"),
            "axe-disable",
            20L,
            Context.target(true, true)));
    long disEvSeq = ++seq;
    events.add(
        new CombatEvent(
            disEvSeq,
            t0 + (long) (0.55 * sec),
            Kind.SHIELD,
            null,
            null,
            Confidence.OBSERVED,
            null,
            "shield disable sound",
            21L,
            Context.shield("DISABLE", "opponent", 1.1)));
    evals.add(
        new FightRecord.Evaluation(
            axeSeq,
            disEvSeq,
            HitQuality.Grade.PERFECT,
            Confidence.CONFIRMED,
            0,
            0,
            FightRecord.Evaluation.SHIELD_DISABLE));

    // Swap to sword 0.40s after disable -> hit 1 (sprinting, full charge)
    long sw1 = ++seq;
    events.add(
        new CombatEvent(
            sw1,
            t0 + (long) (0.95 * sec),
            Kind.ATTACK,
            "local",
            opp,
            Confidence.OBSERVED,
            attack(1.0, true, false, "minecraft:netherite_sword"),
            "sword-1",
            29L,
            null));
    long hurt1 = ++seq;
    events.add(
        new CombatEvent(
            hurt1, t0 + (long) (1.00 * sec), Kind.HURT, "local", opp, Confidence.CONFIRMED, null, "hit", 30L, null));
    evals.add(
        new FightRecord.Evaluation(
            sw1, hurt1, HitQuality.Grade.PERFECT, Confidence.CONFIRMED, 1, 0.2, "HIT"));

    // Sprint release (W-tap) between sw1 and sw2
    events.add(
        new CombatEvent(
            ++seq, t0 + (long) (1.20 * sec), Kind.SPRINT, "local", null, Confidence.OBSERVED, null, "false", 34L, null));
    events.add(
        new CombatEvent(
            ++seq, t0 + (long) (1.35 * sec), Kind.SPRINT, "local", null, Confidence.OBSERVED, null, "true", 37L, null));

    // Hit 2 (sprinting, combo=2, full charge)
    long sw2 = ++seq;
    events.add(
        new CombatEvent(
            sw2,
            t0 + (long) (1.65 * sec),
            Kind.ATTACK,
            "local",
            opp,
            Confidence.OBSERVED,
            attack(0.96, true, false, "minecraft:netherite_sword"),
            "sword-2",
            43L,
            null));
    long hurt2 = ++seq;
    events.add(
        new CombatEvent(
            hurt2, t0 + (long) (1.70 * sec), Kind.HURT, "local", opp, Confidence.CONFIRMED, null, "hit", 44L, null));
    evals.add(
        new FightRecord.Evaluation(
            sw2, hurt2, HitQuality.Grade.PERFECT, Confidence.CONFIRMED, 2, 0.4, "HIT"));

    // Hit 3 (sprinting, NO sprint stop before it -> missed reset, early charge 0.72)
    long sw3 = ++seq;
    events.add(
        new CombatEvent(
            sw3,
            t0 + (long) (2.15 * sec),
            Kind.ATTACK,
            "local",
            opp,
            Confidence.OBSERVED,
            attack(0.72, true, false, "minecraft:netherite_sword"),
            "sword-3",
            53L,
            null));
    long hurt3 = ++seq;
    events.add(
        new CombatEvent(
            hurt3, t0 + (long) (2.20 * sec), Kind.HURT, "local", opp, Confidence.CONFIRMED, null, "hit", 54L, null));
    evals.add(
        new FightRecord.Evaluation(
            sw3, hurt3, HitQuality.Grade.NORMAL, Confidence.CONFIRMED, 3, 0.6, "HIT"));

    // Exchange 2 (6.0s - 9.0s): Opponent disables our shield & deals 10.0 HP burst, we splash pot +6.0 HP
    events.add(
        new CombatEvent(
            ++seq,
            t0 + (long) (6.0 * sec),
            Kind.SHIELD,
            null,
            null,
            Confidence.OBSERVED,
            null,
            "shield disable sound",
            130L,
            Context.shield("DISABLE", "local", 0.2)));
    events.add(
        new CombatEvent(
            ++seq,
            t0 + (long) (6.4 * sec),
            Kind.DAMAGE_IN,
            opp,
            "local",
            Confidence.CONFIRMED,
            null,
            "dmg",
            138L,
            Context.incoming(Attribution.OPPONENT_DIRECT, "minecraft:player_attack", true, true, false, true)));
    events.add(
        new CombatEvent(
            ++seq,
            t0 + (long) (6.45 * sec),
            Kind.HEALTH,
            "local",
            null,
            Confidence.OBSERVED,
            null,
            "hp",
            139L,
            Context.health(19.0, 0.0))); // -5.0 HP
    events.add(
        new CombatEvent(
            ++seq,
            t0 + (long) (7.2 * sec),
            Kind.DAMAGE_IN,
            opp,
            "local",
            Confidence.CONFIRMED,
            null,
            "dmg",
            154L,
            Context.incoming(Attribution.OPPONENT_DIRECT, "minecraft:player_attack", true, true, false, true)));
    events.add(
        new CombatEvent(
            ++seq,
            t0 + (long) (7.25 * sec),
            Kind.HEALTH,
            "local",
            null,
            Confidence.OBSERVED,
            null,
            "hp",
            155L,
            Context.health(14.0, 0.0))); // -5.0 HP (10.0 HP burst in 0.8s)
    // Burst heal (splash potion +6.0 HP)
    events.add(
        new CombatEvent(
            ++seq,
            t0 + (long) (8.5 * sec),
            Kind.HEALTH,
            "local",
            null,
            Confidence.OBSERVED,
            null,
            "hp",
            180L,
            Context.health(20.0, 0.0)));

    var stats = new Statistics();
    stats.measurementVersion = 1;
    for (long id : new long[] {axeSeq, sw1, sw2, sw3}) stats.attackCanonicalIds.put(id, id);
    stats.hits = 3;
    stats.attempts = 4;
    stats.shieldBlocks = 1;
    stats.shieldDisables = 1;
    stats.incoming = 2;
    stats.damageTaken = 10.0;
    stats.longestCombo = 3;
    stats.comboCount = 1;
    stats.exchangeTimeoutSeconds = 3.0;
    stats.combatExchanges =
        List.of(
            new CombatExchange.Interval(t0, t0 + 3 * sec, "INACTIVITY"),
            new CombatExchange.Interval(t0 + 6 * sec, t0 + 9 * sec, "RECORDING_END"));

    return new FightRecord(
        3,
        "11111111-2222-3333-4444-555555555555",
        1700000000000L,
        1700000010000L,
        10 * sec,
        opp,
        FightRecord.Outcome.WIN,
        "test",
        stats,
        events,
        FightRecord.Policy.of(new Config()),
        evals,
        "Rival",
        MatchEvidence.unavailable(),
        new Resolution(
            Resolution.Classification.CONFIRMED_WIN,
            Resolution.Source.SERVER_ROUND_RESULT,
            Confidence.CONFIRMED,
            Resolution.Scope.ROUND,
            t0 + 10 * sec,
            null,
            null,
            List.of(),
            false),
        null);
  }

  @Test
  void computesSwordShieldHealthAndExchangeBreakdownAccurately() {
    var fight = syntheticFight();
    var a = FightAnalytics.of(fight);
    var b = a.breakdown;

    // Health & pacing
    assertEquals(4, b.health.trace().size());
    assertEquals(24.0, b.health.startHp(), 1e-6);
    assertEquals(14.0, b.health.minHp(), 1e-6);
    assertEquals(20.0, b.health.endHp(), 1e-6);
    assertEquals(10.0, b.health.totalDamageTaken(), 1e-6);
    assertEquals(6.0, b.health.totalHealing(), 1e-6);
    assertEquals(1, b.health.burstHeals());
    assertEquals(10.0, b.health.worstBurstDamage(), 1e-6);

    // Sword mechanics: 2 sprint-reset opportunities (sw1->sw2 reset, sw2->sw3 no reset)
    assertEquals(2, b.sword.sprintResetOpportunities());
    assertEquals(1, b.sword.sprintResets());
    assertEquals(50.0, b.sword.sprintResetRate(), 1e-6);
    assertEquals(2, b.sword.comboSprintResetOpportunities());
    assertEquals(1, b.sword.comboSprintResets());
    assertEquals(2, b.sword.fullChargeHits());
    assertEquals(1, b.sword.earlyHits());
    assertEquals(100.0 / 3.0, b.sword.earlySwingRate(), 1e-6);

    // Shield & Axe punish
    assertEquals(1, b.shields.disablesInflicted());
    assertEquals(1, b.shields.disablesConverted());
    assertEquals(100.0, b.shields.disableConversionRate(), 1e-6);
    assertEquals(0.45, b.shields.meanDisablePunishDelay(), 1e-6);
    assertEquals(3.0, b.shields.meanPunishHitsPerDisable(), 1e-6);
    assertEquals(1, b.shields.axeToSwordSwaps());
    assertEquals(0.40, b.shields.meanAxeToSwordSeconds(), 1e-6);
    assertEquals(1, b.shields.localDisables());
    assertEquals(2, b.shields.hitsTakenAfterLocalDisable());
    assertEquals(10.0, b.shields.damageTakenAfterLocalDisable(), 1e-6);

    // Exchanges: Ex 1 opened by YOU (converted), won 3-0; Ex 2 opened by OPPONENT, lost 0-2
    assertEquals(2, b.exchangeSummary.contested());
    assertEquals(1, b.exchangeSummary.openedByYou());
    assertEquals(1, b.exchangeSummary.openedByOpponent());
    assertEquals(50.0, b.exchangeSummary.firstHitRate(), 1e-6);
    assertEquals(1, b.exchangeSummary.openersConverted());
    assertEquals(100.0, b.exchangeSummary.firstHitConversionRate(), 1e-6);
    assertEquals(1, b.exchangeSummary.won());
    assertEquals(1, b.exchangeSummary.lost());
    assertEquals(1.5, b.exchangeSummary.tradeRatio(), 1e-6);

    // Takeaways & turning points are non-empty and deterministic
    assertFalse(b.takeaways.isEmpty());
    assertFalse(b.turningPoints.isEmpty());
    assertTrue(
        b.turningPoints.stream().anyMatch(tp -> "DAMAGE_BURST".equals(tp.kind())),
        "expected worst damage burst turning point");
    assertTrue(
        b.turningPoints.stream().anyMatch(tp -> "SHIELD_PUNISH".equals(tp.kind())),
        "expected shield disable punish turning point");
  }

  @Test
  void incomingSweepHandlesLongFightFastAndAccurately() {
    var fight = syntheticFight();
    var a = FightAnalytics.of(fight);
    assertEquals(2, a.incoming.size());
    assertEquals(5.0, a.incoming.get(0).damage(), 1e-6);
    assertEquals(5.0, a.incoming.get(1).damage(), 1e-6);
  }
}
