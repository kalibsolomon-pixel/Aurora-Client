package dev.phenomena;

import static org.junit.jupiter.api.Assertions.*;

import dev.phenomena.analytics.CombatBreakdown;
import dev.phenomena.analytics.FightAnalytics;
import dev.phenomena.combat.events.CombatEvent;
import dev.phenomena.combat.events.CombatEvent.*;
import dev.phenomena.combat.events.Confidence;
import dev.phenomena.combat.fights.FightRecord;
import dev.phenomena.combat.fights.MatchEvidence;
import dev.phenomena.combat.metrics.Combos;
import dev.phenomena.combat.metrics.Statistics;
import dev.phenomena.combat.quality.HitQuality;
import dev.phenomena.combat.tracking.CombatEngine;
import dev.phenomena.combat.tracking.CombatExchange;
import dev.phenomena.config.Config;
import java.util.*;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Pulsar accuracy pass: inclusive exchange/round boundary handling in {@link Combos},
 * same-tick trade suppression in live {@link CombatEngine}, unclaimed {@code [tick - 1, tick + 1]}
 * knockback pairing in {@link FightAnalytics}, and {@link CombatBreakdown.ComboProfile} / {@link
 * CombatBreakdown.KnockbackControl} ratings.
 */
class PulsarAccuracyPassTest {

  private static CombatEvent hitEv(long seq, long nanos, long tick) {
    return new CombatEvent(
        seq,
        nanos,
        Kind.HURT,
        "local",
        "opp",
        Confidence.CONFIRMED,
        null,
        "local-melee-source",
        tick);
  }

  private static FightRecord.Evaluation hitEval(long seq, int liveCombo) {
    return new FightRecord.Evaluation(
        seq, seq, HitQuality.Grade.PERFECT, Confidence.CONFIRMED, liveCombo, 0.2, "HIT");
  }

  @Test
  void exchangeAndRoundEndTimestampsDoNotSplitFinalHitFromCombo() {
    long sec = 1_000_000_000L;
    List<CombatEvent> events =
        List.of(
            hitEv(1, 1 * sec, 20),
            hitEv(2, (long) (1.5 * sec), 30),
            hitEv(3, 2 * sec, 40));
    List<FightRecord.Evaluation> evals =
        List.of(hitEval(1, 1), hitEval(2, 2), hitEval(3, 3));

    // Exchange closes right on hit 3's exact timestamp (2.0s), and round closes at 2.0s.
    var intervals = List.of(new CombatExchange.Interval(1 * sec, 2 * sec, "RECORDING_END"));
    var rounds = List.of(new MatchEvidence.Round(1, 0L, 2 * sec, FightRecord.Outcome.WIN, "KILLED", Confidence.OBSERVED));

    var res = Combos.analyze(events, evals, "opp", 3.0, rounds, intervals);
    assertEquals(1, res.exchanges().size(), "final hit at endNanos stays in the same exchange");
    assertEquals(3, res.longest(Combos.Side.LOCAL), "3-hit combo must not split at endNanos");
    assertEquals(1, res.combos(Combos.Side.LOCAL).size());

    // A second exchange starting strictly after endNanos still splits cleanly.
    List<CombatEvent> twoExEvents =
        List.of(
            hitEv(1, 1 * sec, 20),
            hitEv(2, (long) (1.5 * sec), 30),
            hitEv(3, 2 * sec, 40),
            hitEv(4, (long) (2.5 * sec), 50));
    List<FightRecord.Evaluation> twoExEvals =
        List.of(hitEval(1, 1), hitEval(2, 2), hitEval(3, 3), hitEval(4, 1));
    var twoIntervals =
        List.of(
            new CombatExchange.Interval(1 * sec, 2 * sec, "INACTIVITY"),
            new CombatExchange.Interval((long) (2.5 * sec), 3 * sec, "RECORDING_END"));
    var splitRes = Combos.analyze(twoExEvents, twoExEvals, "opp", 3.0, List.of(), twoIntervals);
    assertEquals(2, splitRes.exchanges().size());
    assertEquals(3, splitRes.exchanges().get(0).localHits());
    assertEquals(1, splitRes.exchanges().get(1).localHits());
  }

  @Test
  void knockbackPairsAcrossTickMinusOneToPlusOneWithoutDoubleClaiming() {
    long sec = 1_000_000_000L;
    var events = new ArrayList<CombatEvent>();
    long seq = 0;

    // Hit 1 at tick 40: VELOCITY arrived 1 tick earlier on tick 39 (packet order across tick boundary).
    events.add(
        new CombatEvent(
            ++seq,
            (long) (1.95 * sec),
            Kind.VELOCITY,
            "local",
            null,
            Confidence.OBSERVED,
            null,
            "kb-early",
            39L,
            Context.velocity(0.30, 0.35, 0.40, true)));
    events.add(
        new CombatEvent(
            ++seq,
            2 * sec,
            Kind.DAMAGE_IN,
            "opp",
            "local",
            Confidence.CONFIRMED,
            null,
            "hit1",
            40L,
            Context.incoming(Attribution.OPPONENT_DIRECT, "player", true, true, false, true)));

    // Hit 2 at tick 41 (adjacent tick): has NO own velocity packet — must NOT steal tick 39 or tick 42 if tick 42 belongs to Hit 3,
    // wait: if Hit 2 has a small tick-41 velocity and a stronger tick-41 impulse, strongest wins!
    events.add(
        new CombatEvent(
            ++seq,
            (long) (2.05 * sec),
            Kind.DAMAGE_IN,
            "opp",
            "local",
            Confidence.CONFIRMED,
            null,
            "hit2",
            41L,
            Context.incoming(Attribution.OPPONENT_DIRECT, "player", true, true, false, true)));
    events.add(
        new CombatEvent(
            ++seq,
            (long) (2.055 * sec),
            Kind.VELOCITY,
            "local",
            null,
            Confidence.OBSERVED,
            null,
            "small-sync",
            41L,
            Context.velocity(0.05, 0.0, 0.0, true)));
    events.add(
        new CombatEvent(
            ++seq,
            (long) (2.06 * sec),
            Kind.VELOCITY,
            "local",
            null,
            Confidence.OBSERVED,
            null,
            "real-kb",
            41L,
            Context.velocity(0.60, 0.35, 0.0, true)));

    // Hit 3 at tick 42: no remaining velocity packet -> knockback must stay null (cannot reuse tick 41's!).
    events.add(
        new CombatEvent(
            ++seq,
            (long) (2.10 * sec),
            Kind.DAMAGE_IN,
            "opp",
            "local",
            Confidence.CONFIRMED,
            null,
            "hit3",
            42L,
            Context.incoming(Attribution.OPPONENT_DIRECT, "player", true, true, false, true)));

    var fight =
        new FightRecord(
            FightRecord.SCHEMA,
            "kb-test",
            1000L,
            5000L,
            4 * sec,
            "opp",
            FightRecord.Outcome.UNKNOWN,
            "test",
            new Statistics(),
            events,
            FightRecord.Policy.of(new Config()),
            List.of());
    var a = FightAnalytics.of(fight);
    assertEquals(3, a.incoming.size());
    assertEquals(0.50, a.incoming.get(0).knockback(), 1e-6, "tick - 1 velocity must pair with hit 1");
    assertEquals(0.60, a.incoming.get(1).knockback(), 1e-6, "strongest impulse on same tick must win");
    assertNull(a.incoming.get(2).knockback(), "hit 3 must not claim tick 41 velocity already used or weak sync");
  }

  @Test
  void sameTickTradeDoesNotInflateLiveCombo() {
    var out = new ArrayList<FightRecord>();
    var engine = new CombatEngine(new Config(), out::add, (e, g) -> {});
    var atk = Sim.attack(2.8, "sword", null);

    // Incoming attributed damage on tick 50, followed by local hit confirmation on the same tick 50.
    engine.accept(
        new CombatEvent(
            1,
            2_500_000_000L,
            Kind.ATTACK,
            "local",
            "opp",
            Confidence.OBSERVED,
            atk,
            "",
            49L));
    engine.accept(
        new CombatEvent(
            2,
            2_550_000_000L,
            Kind.DAMAGE_IN,
            "opp",
            "local",
            Confidence.CONFIRMED,
            null,
            "in",
            50L,
            Context.incoming(Attribution.OPPONENT_DIRECT, "player", true, true, false, true)));
    engine.accept(
        new CombatEvent(
            3,
            2_555_000_000L,
            Kind.HURT,
            "local",
            "opp",
            Confidence.CONFIRMED,
            null,
            "local-melee-source",
            50L));

    assertEquals(0, engine.stats().currentCombo, "same-tick trade must leave live combo at 0");
    engine.finish(3_000_000_000L, FightRecord.Outcome.UNKNOWN, "done");
    var a = FightAnalytics.of(out.getFirst());
    assertEquals(1, a.breakdown.comboProfile.sameTickTrades());
    assertEquals(0, a.breakdown.comboProfile.localCombos());
  }

  @Test
  void knockbackControlRatingAndEarlyLateBiasAndSpeedReduction() {
    var s = new Sim("unconfigured.test");
    s.hit(.2);
    // 2 ideal same-tick resets with reduced post-hit speed (0.18), 1 early (-2t) with reduced speed (0.20),
    // and 2 no-jump hits with higher post-hit speed (0.40).
    s.incoming(1.0).velocity(1.01, .45, 0).jump(1.02).motion(1.15, .18, 0);
    s.incoming(2.0).velocity(2.01, .45, 0).jump(2.02).motion(2.15, .18, 0);
    s.jump(2.90).incoming(3.0).velocity(3.01, .45, 0).motion(3.15, .20, 0);
    s.incoming(4.0).velocity(4.01, .45, 0).motion(4.15, .40, 0);
    s.incoming(5.0).velocity(5.01, .45, 0).motion(5.15, .40, 0);

    var a = FightAnalytics.of(s.finish(6.5));
    var kb = a.breakdown.knockbackControl;
    assertEquals(5, kb.eligibleHits());
    assertEquals(2, kb.idealResets());
    assertEquals(1, kb.attemptResets());
    assertEquals(1, kb.earlyAttempts());
    assertEquals(0, kb.lateAttempts());
    assertEquals(2, kb.noJump());
    assertEquals(60.0, kb.timingRatePct(), 1e-6);
    assertTrue(Double.isFinite(kb.speedReductionPct()) && kb.speedReductionPct() > 50.0,
        "candidate speed (~0.187) vs no-jump speed (0.40) should yield >50% speed reduction");
    assertTrue(Double.isFinite(kb.rating()) && kb.rating() >= 65.0 && kb.rating() <= 100.0,
        "strong timing + >50% speed reduction should rate >= 65/100");
  }
}
