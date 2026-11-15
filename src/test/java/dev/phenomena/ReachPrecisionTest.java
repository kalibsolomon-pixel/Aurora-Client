package dev.phenomena;

import static org.junit.jupiter.api.Assertions.*;

import com.aurora.client.pulsar.PulsarReach;
import dev.phenomena.analytics.FightAnalytics;
import dev.phenomena.combat.events.*;
import dev.phenomena.combat.events.CombatEvent.*;
import dev.phenomena.combat.metrics.Geometry;
import dev.phenomena.combat.quality.AttackTiming;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Reach matches vanilla's attack pick: survival hits never exceed 3.0, creative hits stay out. */
class ReachPrecisionTest {
  private static final Geometry.Vec EYE = new Geometry.Vec(0, 1.62, 0);
  private static final Geometry.Vec NORTH = new Geometry.Vec(0, 0, 1);

  /** A player box whose near face is {@code near} blocks ahead, inflated by pick radius 0. */
  private static Geometry.Box box(double near) {
    return new Geometry.Box(-.3, 0, near, .3, 1.8, near + .6);
  }

  @Test
  void vanillaPickContactIsTheReach() {
    // Sprinting into a strafing target: the frame-interpolated box sits 0.28 further than the box
    // the tick pick hit. Only the pick's contact counts.
    var pick = new Geometry.Vec(0, 1.62, 2.98);
    var reach = PulsarReach.measure(EYE, NORTH, box(3.26), 3, pick);
    assertEquals(2.98, reach.distance(), 1e-9);
    assertEquals(pick, reach.contact().orElseThrow());
  }

  @Test
  void emptySwingIntersectsTheCurrentBox() {
    var inRange = PulsarReach.measure(EYE, NORTH, box(2.5), 3, null);
    assertEquals(2.5, inRange.distance(), 1e-9);
    assertTrue(inRange.contact().isPresent());
    var outOfRange = PulsarReach.measure(EYE, NORTH, box(3.4), 3, null);
    assertTrue(outOfRange.contact().isEmpty(), "beyond attack range there is no ray contact");
    assertEquals(3.4, outOfRange.distance(), 1e-9);
  }

  @Test
  void creativeHitsStayOutOfReachStatistics() throws Exception {
    var s = new Sim("unconfigured.test");
    s.hit(1, 2.95);
    s.emit(1.6, Kind.ATTACK, "local", Sim.B, Confidence.OBSERVED, creative(4.7), "test", null)
        .hurt(1.61, Sim.B);
    var fight = s.finish(3);
    var a = FightAnalytics.of(fight);
    assertEquals(2, a.hits.size(), "the creative hit still counts as a hit");
    assertEquals(1, a.reach.eligible());
    assertEquals(2.95, a.reach.max(), 1e-9);
    assertEquals(2.95, fight.statistics().maxReach, 1e-9);
    assertEquals(1, fight.statistics().reachCount);
    assertTrue(a.hits.stream().allMatch(h -> !(h.reach() > 3)), "no hit reach above 3.0");
  }

  @Test
  void serverHitNeverConfirmsAnEarlierEmptySwing() throws Exception {
    // An empty swing sends no attack packet, so its out-of-range miss distance must not become a
    // hit's reach when the server reports the next real attack.
    var s = emptySwingBefore(1.4); // four ticks later: a separate input
    var fight = s.finish(3);
    var a = FightAnalytics.of(fight);
    assertEquals(2, a.hits.size());
    assertEquals(2.95, a.reach.max(), 1e-9);
    assertEquals(2.95, fight.statistics().maxReach, 1e-9);
    assertEquals(1, a.misses.size(), "the empty swing stays a miss");
  }

  @Test
  void realAttackReplacesAnEmptySwingInTheSameInput() throws Exception {
    var s = emptySwingBefore(1.25); // one tick later: folded into the empty swing's input
    var fight = s.finish(3);
    var a = FightAnalytics.of(fight);
    assertEquals(2, a.hits.size());
    assertEquals(0, a.misses.size());
    assertEquals(2.95, a.reach.max(), 1e-9);
    assertEquals(2.95, fight.statistics().maxReach, 1e-9);
  }

  /** A confirmed hit, an aimed empty swing at 4.1 blocks, then a confirmed 2.95-block attack. */
  private static Sim emptySwingBefore(double attack) {
    var s = new Sim("unconfigured.test");
    s.hit(1, 2.9);
    s.emit(
        1.2,
        Kind.ATTACK,
        "local",
        Sim.B,
        Confidence.OBSERVED,
        Sim.attack(4.1, "minecraft:diamond_sword", null),
        "empty-swing; plausible recent opponent",
        null);
    return s.attack(attack, Sim.B, 2.95, false, false).hurt(attack + .01, Sim.B);
  }

  @Test
  void everyAdapterMeasuresFromTheTickPick() throws Exception {
    for (String version : new String[] {"26.1", "26.2", "26.3"}) {
      String src =
          Files.readString(
              Path.of(
                  "adapters/minecraft-" + version
                      + "/src/client/java/dev/phenomena/combat/tracking/ClientCapture.java"));
      assertTrue(src.contains("PulsarReach.measure("), version);
      assertTrue(src.contains("player.getEyePosition(1)"), version);
      assertTrue(src.contains("hit.getEntity() == target"), version);
      assertTrue(src.contains("PulsarReach.CREATIVE_MODE"), version);
      assertFalse(src.contains("getEyePosition(partial)"), version + " measures interpolated eye");
      assertFalse(src.contains("getPosition(partial)"), version + " measures interpolated box");
    }
  }

  private static Attack creative(double reach) {
    var a = Sim.attack(reach, "minecraft:diamond_sword", new AttackTiming.Result(AttackTiming.Grade.OPENER, 0, 12.5));
    var m = a.measurement();
    return new Attack(
        a.cooldown(),
        reach,
        a.aimX(),
        a.aimY(),
        a.aimZ(),
        a.sprinting(),
        a.criticalConditions(),
        a.rayContact(),
        new Measurement(
            m.weapon(),
            m.attackSpeed(),
            m.cooldownTicks(),
            m.timing(),
            m.eye(),
            m.box(),
            m.idealRegion(),
            m.aimQuality(),
            PulsarReach.CREATIVE_MODE));
  }
}
