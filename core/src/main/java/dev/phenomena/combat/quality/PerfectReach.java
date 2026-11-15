package dev.phenomena.combat.quality;

import com.aurora.client.pulsar.PulsarReach;

import dev.phenomena.combat.events.CombatEvent.Attack;

/** Only call after a unique source-confirmed melee correlation. */
public final class PerfectReach {
  private PerfectReach() {}

  public static boolean eligible(Attack attack, double target, double tolerance) {
    return attack != null
        && attack.rayContact()
        && Double.isFinite(PulsarReach.comparable(attack))
        && Math.abs(attack.reach() - target) <= tolerance + 1e-9;
  }
}
