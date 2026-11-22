package dev.phenomena.analytics;

import com.aurora.client.pulsar.PulsarReach;

import dev.phenomena.combat.events.CombatEvent;
import dev.phenomena.combat.events.CombatEvent.Kind;
import dev.phenomena.combat.fights.FightRecord;
import dev.phenomena.combat.metrics.Combos;
import dev.phenomena.combat.metrics.Geometry;
import dev.phenomena.combat.metrics.Statistics;
import dev.phenomena.combat.quality.AttackTiming;
import dev.phenomena.combat.quality.HitQuality.Grade;
import dev.phenomena.combat.quality.JumpResets;
import dev.phenomena.combat.quality.MeleeAim;
import dev.phenomena.combat.quality.PerfectReach;
import java.util.*;

/**
 * Timeline-derived values for one recorded fight. Built only from stored raw events and the frozen
 * evaluations, so every number can be traced back to a recorded observation. Correlation is not
 * repeated here: an attack is a hit exactly when the engine recorded a hit evaluation for it, and a
 * confirmed shield block exactly when it recorded a shield evaluation.
 *
 * <p>Instances are immutable and independent of Minecraft classes; the in-game GUI and the HTML
 * report both consume them.
 */
public final class FightAnalytics {
  public static final double REACH_BIN = .5, REACH_MAX = 6, FINE_REACH_BIN = .05;
  public static final int REACH_BINS = (int) (REACH_MAX / REACH_BIN);
  public static final int COOLDOWN_BINS = 10;
  public static final int JUMP_OFFSET_LIMIT_MS = 1000;

  /** Vanilla axe disable lasts 5 s (100 ticks); hits inside it count as "after a disable". */
  public static final double DISABLE_FOLLOW_UP_SECONDS = 5;

  /** A correlated hit. Times are seconds since the first recorded event. */
  public record Hit(
      long attackSequence,
      double time,
      double evidenceTime,
      double reach,
      boolean rayContact,
      double aimHorizontal,
      double aimVertical,
      double cooldown,
      Grade readiness,
      boolean sprinting,
      boolean criticalConditions,
      int combo,
      double momentum,
      CombatEvent.Measurement measurement,
      boolean perfectReach,
      String weapon,
      Boolean serverCrit,
      String face,
      double aimDistance,
      Long tick) {}

  /**
   * An attempt without a correlated server report. Empty swings carry geometry only when a
   * plausible recent opponent was in range; uncertain attempts were aimed at an entity.
   */
  public record Miss(
      long sequence,
      double time,
      boolean empty,
      boolean geometry,
      double estimatedReach,
      boolean rayContact,
      double aimHorizontal,
      double aimVertical) {}

  /** Jump observed near incoming opponent damage; offset is jump time minus damage time. */
  public record Jump(
      double time,
      double offsetMillis,
      boolean candidate,
      Long offsetTicks,
      String classification) {}

  public record Timing(long sequence, double time, CombatEvent.Measurement measurement) {}

  /** One graded attack interval with its outcome; weapons are never pooled into one average. */
  public record TimingSample(
      long sequence,
      double time,
      String weapon,
      double attackSpeed,
      double expectedTicks,
      double intervalTicks,
      double deltaTicks,
      AttackTiming.Grade grade,
      String outcome) {
    public boolean graded() {
      return grade == AttackTiming.Grade.PERFECT
          || grade == AttackTiming.Grade.TOO_FAST
          || grade == AttackTiming.Grade.TOO_SLOW;
    }
  }

  /**
   * One incoming damage observation. {@code attribution} is the stored server-source attribution
   * (OPPONENT_DIRECT, OPPONENT_INDIRECT, OTHER_ENTITY, UNATTRIBUTED) or, for recordings before
   * 0.1.3, OPPONENT_LEGACY / UNATTRIBUTED. Damage is the observed local health+absorption drop
   * within two ticks; knockback is the server velocity update within one tick (blocks/tick,
   * horizontal).
   */
  public record Incoming(
      long sequence,
      double time,
      Long tick,
      String attribution,
      boolean attributed,
      String damageType,
      Double damage,
      Double knockback,
      Boolean ground,
      Boolean sprinting,
      Boolean movementValid,
      String jumpClass,
      Long jumpOffsetTicks,
      boolean crit,
      Double speedAfter) {}

  public record ShieldEvent(
      double time, String type, String subject, Long attackSequence, String weapon) {}

  public record Shields(
      boolean available,
      int attacksIntoShield,
      int confirmedBlocks,
      int estimatedBlocks,
      int disables,
      int hitsAfterDisable,
      int bypassed,
      int localBlocks,
      int localDisabled,
      List<ShieldEvent> events) {}

  public record WeaponReach(int eligible, int within, double mean, double deviation, double max) {}

  public record ReachSummary(
      double target,
      double tolerance,
      int eligible,
      int within,
      double mean,
      double deviation,
      double max,
      double meanAbsoluteError,
      double[] values,
      String[] weapons,
      Map<String, WeaponReach> byWeapon) {
    public double withinPercent() {
      return eligible == 0 ? Double.NaN : 100.0 * within / eligible;
    }
  }

  public record AimSample(
      long sequence,
      double time,
      String weapon,
      String face,
      double x,
      double y,
      double z,
      double horizontal,
      double vertical,
      String grade,
      double distance,
      String reachBand,
      boolean hit) {}

  public record AimSummary(
      List<AimSample> samples,
      int[] grades,
      double meanDistance,
      double distanceDeviation,
      Map<String, int[]> byWeapon,
      Map<String, int[]> byReach) {
    public int central() {
      return grades[0] + grades[1];
    }

    public int peripheral() {
      return grades[2] + grades[3];
    }
  }

  /** Experimental, descriptive only: mean horizontal speed 0.1-0.4 s after knockback. */
  public record JumpEffect(int candidates, double candidateSpeed, int others, double otherSpeed) {}

  public record Boundary(double time, String kind, boolean accepted) {}

  public final List<Timing> timings;
  public final List<JumpResets.Sample> jumpSamples;

  public record Point(double time, double value) {}

  public final FightRecord fight;
  public final double duration;
  public final List<Hit> hits;
  public final List<Miss> misses;
  public final List<Double> intervals;
  public final List<Integer> comboLengths;
  public final List<Point> momentumChanges;

  /** Attributed opponent hits only (seconds). */
  public final List<Double> incomingTimes;

  public final List<Jump> jumps;
  public final int jumpsWithoutDamage, motionSamples, sprintTransitions;
  public final int[] readiness = new int[4];
  public final int[] hitReachBins = new int[REACH_BINS + 1];
  public final int[] missReachBins = new int[REACH_BINS + 1];
  public final int[] cooldownBins = new int[COOLDOWN_BINS];
  public final int center, edge, high, low;

  // ---- 0.1.3 views
  public final boolean contextAvailable;
  public final Combos.Result combos;
  public final List<Incoming> incoming;
  public final int attributedIncoming, unattributedIncoming, thirdPartyIncoming;
  public final int unexplainedHealthDrops;
  public final Shields shields;
  public final Map<Long, String> attemptOutcomes;
  public final List<TimingSample> timingSamples;
  public final ReachSummary reach;
  public final AimSummary aim;
  public final JumpEffect jumpEffect;
  public final Dominance dominance;
  public final List<Boundary> boundaries;
  public final int serverCrits;

  public record ActiveExchange(
      double start,
      double end,
      int hits,
      int misses,
      int opponentHits,
      int combos,
      int momentumChanges,
      String endedBy) {}

  public final List<ActiveExchange> activeExchanges;
  public final double combatActiveSeconds;
  public final CombatBreakdown breakdown;

  /** Monotonic origin of every "seconds since fight start" value in this instance. */
  public final long baseNanos;

  private FightAnalytics(FightRecord f) {
    this.fight = f;
    var events =
        dev.phenomena.combat.tracking.AttackObservations.canonicalEvents(f.events(), f.statistics());
    var policy = f.policy();
    String opponent = f.opponent();
    long base =
        f.matchEvidence().timelineStartNanos() != null
            ? f.matchEvidence().timelineStartNanos()
            : events.isEmpty() ? 0 : events.getFirst().nanoTime();
    duration = Math.max(f.durationNanos() / 1e9, 0);
    baseNanos = base;
    contextAvailable = f.schemaVersion() >= 3;
    Map<Long, CombatEvent> bySequence = new HashMap<>();
    for (var e : events) bySequence.put(e.sequence(), e);

    timings =
        events.stream()
            .filter(
                e ->
                    e.kind() == Kind.ATTACK
                        && dev.phenomena.combat.tracking.AttackObservations.outgoing(e)
                        && !"EXCLUDED"
                            .equals(f.statistics().attackClassifications.get(e.sequence()))
                        && e.attack() != null
                        && e.attack().measurement() != null)
            .map(
                e ->
                    new Timing(e.sequence(), seconds(e.nanoTime(), base), e.attack().measurement()))
            .toList();
    jumpSamples = JumpResets.analyze(events, policy.jumpWindowTicks(), opponent);
    Map<Long, JumpResets.Sample> byJump = new HashMap<>();
    jumpSamples.forEach(j -> byJump.put(j.jumpSequence(), j));

    // Server critical animations on the opponent, by tick.
    Set<Long> critTicks = new HashSet<>();
    int crits = 0;
    for (var e : events)
      if (e.kind() == Kind.CRIT
          && e.context() != null
          && "opponent".equals(e.context().subject())
          && e.clientTick() != null) critTicks.add(e.clientTick());

    List<Hit> hitList = new ArrayList<>();
    Set<Long> hitAttacks = new HashSet<>();
    Map<Long, String> outcomes = new HashMap<>();
    List<ShieldEvent> shieldEvents = new ArrayList<>();
    int blocks = 0, disables = 0;
    List<Double> disableTimes = new ArrayList<>();
    int c = 0, ed = 0, hi = 0, lo = 0;
    for (var ev : f.evaluations()) {
      var attackEvent = bySequence.get(ev.attackSequence());
      var evidence = bySequence.get(ev.evidenceSequence());
      if (attackEvent == null || attackEvent.attack() == null) continue;
      var a = attackEvent.attack();
      double evidenceTime =
          evidence == null
              ? seconds(attackEvent.nanoTime(), base)
              : seconds(evidence.nanoTime(), base);
      String weapon = a.measurement() == null ? "unknown" : a.measurement().item();
      if (ev.shield()) {
        boolean disable = FightRecord.Evaluation.SHIELD_DISABLE.equals(ev.result());
        blocks++;
        if (disable) {
          disables++;
          disableTimes.add(evidenceTime);
        }
        outcomes.put(ev.attackSequence(), ev.result());
        shieldEvents.add(
            new ShieldEvent(
                evidenceTime,
                disable ? "DISABLE" : "BLOCK",
                "opponent",
                ev.attackSequence(),
                weapon));
        continue;
      }
      if (!ev.hit()) continue;
      hitAttacks.add(ev.attackSequence());
      outcomes.put(ev.attackSequence(), "HIT");
      double h = a.rayContact() ? Geometry.faceHorizontal(a.aimX(), a.aimZ()) : -1;
      Long tick = evidence == null ? attackEvent.clientTick() : evidence.clientTick();
      Boolean crit =
          !contextAvailable
              ? null
              : tick != null
                  && (critTicks.contains(tick)
                      || critTicks.contains(tick - 1)
                      || critTicks.contains(tick + 1));
      if (Boolean.TRUE.equals(crit)) crits++;
      var m = a.measurement();
      var normalized = new Geometry.Vec(a.aimX(), a.aimY(), a.aimZ());
      hitList.add(
          new Hit(
              ev.attackSequence(),
              seconds(attackEvent.nanoTime(), base),
              evidenceTime,
              PulsarReach.comparable(a),
              a.rayContact(),
              h,
              a.rayContact() ? a.aimY() : -1,
              a.cooldown(),
              ev.readiness(),
              a.sprinting(),
              a.criticalConditions(),
              ev.combo(),
              ev.momentum(),
              m,
              PerfectReach.eligible(a, policy.perfectReachTarget(), policy.perfectReachTolerance()),
              weapon,
              crit,
              a.rayContact() ? MeleeAim.face(normalized) : null,
              m != null && a.rayContact()
                  ? MeleeAim.distance(normalized, m.idealRegion(), m.box())
                  : Double.NaN,
              tick));
      if (ev.readiness() != null) readiness[ev.readiness().ordinal()]++;
      if (PulsarReach.comparable(a) >= 0) hitReachBins[reachBin(PulsarReach.comparable(a))]++;
      cooldownBins[
          Math.min(COOLDOWN_BINS - 1, Math.max(0, (int) (a.cooldown() * COOLDOWN_BINS)))]++;
      if (a.rayContact()) {
        switch (Geometry.placement(a.aimX(), a.aimY(), a.aimZ())) {
          case "Center" -> c++;
          case "Edge" -> ed++;
          case "High" -> hi++;
          default -> lo++;
        }
      }
    }
    center = c;
    edge = ed;
    high = hi;
    low = lo;
    hits = List.copyOf(hitList);
    serverCrits = crits;

    List<Double> intervalList = new ArrayList<>();
    for (int i = 1; i < hits.size(); i++)
      intervalList.add(hits.get(i).time() - hits.get(i - 1).time());
    intervals = List.copyOf(intervalList);

    combos =
        Combos.analyze(
            f.manual() ? List.of() : events,
            f.manual() ? List.of() : f.evaluations(),
            opponent,
            policy.comboSeconds(),
            f.matchEvidence().rounds(),
            f.statistics().combatExchanges);
    comboLengths =
        combos.streaks().stream()
            .filter(s -> s.side() == Combos.Side.LOCAL)
            .map(Combos.Streak::length)
            .toList();

    // ---- attempts, shields, incoming, jumps, health and movement
    List<Miss> missList = new ArrayList<>();
    List<Jump> jumpList = new ArrayList<>();
    List<Double> attributedTimes = new ArrayList<>();
    int noDamage = 0, motion = 0, sprint = 0;
    int intoShield = 0, estimated = 0, bypassed = 0, localBlocks = 0, localDisabled = 0;
    var attributedDamage = events.stream().filter(e -> e.attributedTo(opponent)).toList();
    int attrDamagePtr = 0;
    for (var e : events) {
      double t = seconds(e.nanoTime(), base);
      switch (e.kind()) {
        case ATTACK -> {
          if (!dev.phenomena.combat.tracking.AttackObservations.outgoing(e)) break;
          String classification = f.statistics().attackClassifications.get(e.sequence());
          if (classification != null
              && !classification.equals("HIT")
              && !classification.equals("SHIELD_BLOCK")) outcomes.put(e.sequence(), classification);
          var ctx = e.context();
          boolean empty =
              e.attack() == null || (e.detail() != null && e.detail().startsWith("empty-swing"));
          boolean intoBlock = !empty && ctx != null && Boolean.TRUE.equals(ctx.targetBlocking());
          if (intoBlock) intoShield++;
          if (hitAttacks.contains(e.sequence())) {
            if (intoBlock) bypassed++;
            break;
          }
          if (classification != null
              && (classification.equals("EXCLUDED") || classification.equals("SHIELD_BLOCK")))
            break;
          if (classification == null && outcomes.containsKey(e.sequence())) break;
          var a = e.attack();
          boolean estimatedBlock = intoBlock && Boolean.TRUE.equals(ctx.targetFacing());
          if (estimatedBlock) {
            estimated++;
            shieldEvents.add(
                new ShieldEvent(
                    t,
                    "ESTIMATED_BLOCK",
                    "opponent",
                    e.sequence(),
                    a.measurement() == null ? "unknown" : a.measurement().item()));
          }
          if (classification == null)
            outcomes.put(
                e.sequence(), empty ? "EMPTY" : estimatedBlock ? "ESTIMATED_BLOCK" : "UNCERTAIN");
          missList.add(
              new Miss(
                  e.sequence(),
                  t,
                  empty,
                  a != null,
                  a == null ? -1 : PulsarReach.comparable(a),
                  a != null && a.rayContact(),
                  a != null && a.rayContact() ? Geometry.faceHorizontal(a.aimX(), a.aimZ()) : -1,
                  a != null && a.rayContact() ? a.aimY() : -1));
          if (a != null && PulsarReach.comparable(a) >= 0) missReachBins[reachBin(PulsarReach.comparable(a))]++;
        }
        case DAMAGE_IN -> {
          if (e.attributedTo(opponent)) attributedTimes.add(t);
        }
        case SHIELD -> {
          var ctx = e.context();
          if (ctx != null && "local".equals(ctx.subject())) {
            if ("DISABLE".equals(ctx.shield())) localDisabled++;
            else localBlocks++;
            shieldEvents.add(new ShieldEvent(t, ctx.shield(), "local", null, null));
          }
        }
        case JUMP -> {
          var sample = byJump.get(e.sequence());
          CombatEvent nearest = null;
          if (!attributedDamage.isEmpty()) {
            long tNano = e.nanoTime();
            while (attrDamagePtr + 1 < attributedDamage.size()
                && attributedDamage.get(attrDamagePtr + 1).nanoTime() <= tNano) attrDamagePtr++;
            nearest = attributedDamage.get(attrDamagePtr);
            if (attrDamagePtr + 1 < attributedDamage.size()) {
              var next = attributedDamage.get(attrDamagePtr + 1);
              if (Math.abs(tNano - next.nanoTime()) < Math.abs(tNano - nearest.nanoTime()))
                nearest = next;
            }
          }
          if (nearest == null
              || (sample.offsetTicks() == null
                  && Math.abs(e.nanoTime() - nearest.nanoTime()) > 1_000_000_000L)) noDamage++;
          else {
            boolean candidate =
                sample.grade() == JumpResets.Grade.IDEAL_CANDIDATE
                    || sample.grade() == JumpResets.Grade.ATTEMPT_CANDIDATE;
            jumpList.add(
                new Jump(
                    t,
                    (e.nanoTime() - nearest.nanoTime()) / 1e6,
                    candidate,
                    sample.offsetTicks(),
                    sample.grade().name()));
          }
        }
        case MOTION -> motion++;
        case SPRINT -> sprint++;
        default -> {}
      }
    }
    shieldEvents.sort(Comparator.comparingDouble(ShieldEvent::time));
    int afterDisable = 0;
    for (var h : hits)
      for (double d : disableTimes)
        if (h.evidenceTime() > d && h.evidenceTime() - d <= DISABLE_FOLLOW_UP_SECONDS) {
          afterDisable++;
          break;
        }
    shields =
        new Shields(
            contextAvailable,
            intoShield,
            blocks,
            estimated,
            disables,
            afterDisable,
            bypassed,
            localBlocks,
            localDisabled,
            List.copyOf(shieldEvents));
    attemptOutcomes = Map.copyOf(outcomes);
    misses = List.copyOf(missList);
    incomingTimes = List.copyOf(attributedTimes);
    jumps = List.copyOf(jumpList);
    jumpsWithoutDamage = noDamage;
    motionSamples = motion;
    sprintTransitions = sprint;
    momentumChanges =
        List.copyOf(momentum(hits, incomingTimes, f.statistics().combatExchanges, base));

    // ---- incoming detail
    var built = incoming(events, base, opponent, policy.jumpWindowTicks());
    incoming = built.list;
    unexplainedHealthDrops = built.unexplained.size();
    int att = 0, un = 0, third = 0;
    List<Double> unattributedTimes = new ArrayList<>(built.unexplained);
    for (var in : incoming) {
      if (in.attributed()) att++;
      else if ("OTHER_ENTITY".equals(in.attribution())) {
        third++;
        unattributedTimes.add(in.time());
      } else {
        un++;
        unattributedTimes.add(in.time());
      }
    }
    attributedIncoming = att;
    unattributedIncoming = un;
    thirdPartyIncoming = third;
    jumpEffect = jumpEffect(incoming);

    var eligibleEvents =
        events.stream()
            .filter(
                e ->
                    e.kind() != Kind.ATTACK
                        || dev.phenomena.combat.tracking.AttackObservations.outgoing(e)
                            && !"EXCLUDED"
                                .equals(f.statistics().attackClassifications.get(e.sequence())))
            .toList();
    timingSamples = timingSamples(eligibleEvents, base, outcomes);
    reach = reach(hits, policy.perfectReachTarget(), policy.perfectReachTolerance());
    aim = aim(eligibleEvents, base, hitAttacks);

    // ---- dominance
    List<Dominance.Input> inputs = new ArrayList<>();
    Map<Long, Double> weights = new HashMap<>();
    for (var s : combos.streaks())
      for (int k = 0; k < s.sequences().size(); k++)
        weights.put(
            s.sequences().get(k),
            (s.side() == Combos.Side.LOCAL ? 1 : -1)
                * (Dominance.W_HIT + (k > 0 ? Dominance.W_STREAK_BONUS : 0)));
    for (var m : combos.marks()) {
      double w =
          weights.getOrDefault(
              m.sequence(), m.side() == Combos.Side.LOCAL ? Dominance.W_HIT : -Dominance.W_HIT);
      inputs.add(new Dominance.Input(seconds(m.nanos(), base), w, m.side().name()));
    }
    for (var s : shields.events()) {
      switch (s.type() + "/" + s.subject()) {
        case "BLOCK/opponent" ->
            inputs.add(
                new Dominance.Input(s.time(), -Dominance.W_SHIELD_BLOCKED, "OPPONENT_BLOCK"));
        case "DISABLE/opponent" ->
            inputs.add(
                new Dominance.Input(s.time(), Dominance.W_SHIELD_DISABLED, "OPPONENT_DISABLED"));
        case "BLOCK/local" ->
            inputs.add(new Dominance.Input(s.time(), Dominance.W_LOCAL_BLOCK, "LOCAL_BLOCK"));
        case "DISABLE/local" ->
            inputs.add(
                new Dominance.Input(s.time(), -Dominance.W_SHIELD_DISABLED, "LOCAL_DISABLED"));
        default -> {}
      }
    }
    for (var interval : f.statistics().combatExchanges) {
      inputs.add(new Dominance.Input(seconds(interval.startNanos(), base), 0, "EXCHANGE_RESET"));
      inputs.add(new Dominance.Input(seconds(interval.endNanos(), base), 0, "EXCHANGE_RESET"));
    }
    inputs.sort(
        Comparator.comparingDouble(Dominance.Input::time)
            .thenComparing(in -> !in.kind().equals("EXCHANGE_RESET")));
    dominance =
        f.statistics().measurementVersion > 0
            ? Dominance.of(
                duration,
                f.manual() ? List.of() : inputs,
                unattributedTimes,
                att,
                f.statistics().combatExchanges.stream()
                    .map(
                        ex ->
                            new double[] {
                              seconds(ex.startNanos(), base), seconds(ex.endNanos(), base)
                            })
                    .toList())
            : Dominance.of(duration, f.manual() ? List.of() : inputs, unattributedTimes, att);
    var active = new ArrayList<ActiveExchange>();
    var comboStarts = new HashSet<Long>();
    for (var streak : combos.streaks())
      if (streak.combo() && streak.side() == Combos.Side.LOCAL)
        comboStarts.add(streak.sequences().getFirst());
    int eventIndex = 0, turningIndex = 0;
    var turning = dominance.turningPoints();
    double activeSeconds = 0;
    var measuredIntervals = f.statistics().combatExchanges;
    for (int intervalIndex = 0; intervalIndex < measuredIntervals.size(); intervalIndex++) {
      var interval = measuredIntervals.get(intervalIndex);
      boolean includeEnd =
          intervalIndex + 1 == measuredIntervals.size()
              || measuredIntervals.get(intervalIndex + 1).startNanos() > interval.endNanos();
      int hitCount = 0, missCount = 0, oppCount = 0, comboCount = 0, changes = 0;
      while (eventIndex < events.size()
          && events.get(eventIndex).nanoTime() < interval.startNanos()) eventIndex++;
      while (eventIndex < events.size()
          && (events.get(eventIndex).nanoTime() < interval.endNanos()
              || includeEnd && events.get(eventIndex).nanoTime() == interval.endNanos())) {
        var event = events.get(eventIndex++);
        if (hitAttacks.contains(event.sequence())) hitCount++;
        if ("MISS".equals(f.statistics().attackClassifications.get(event.sequence()))) missCount++;
        if (event.attributedTo(opponent)) oppCount++;
        if (comboStarts.contains(event.sequence())) comboCount++;
      }
      double from = seconds(interval.startNanos(), base), to = seconds(interval.endNanos(), base);
      while (turningIndex < turning.size() && turning.get(turningIndex)[0] < from) turningIndex++;
      while (turningIndex < turning.size() && turning.get(turningIndex)[0] <= to) {
        changes++;
        turningIndex++;
      }
      activeSeconds += Math.max(0, to - from);
      active.add(
          new ActiveExchange(
              from, to, hitCount, missCount, oppCount, comboCount, changes, interval.endedBy()));
    }
    activeExchanges = List.copyOf(active);
    combatActiveSeconds = activeSeconds;

    List<Boundary> bs = new ArrayList<>();
    for (var entry : f.matchEvidence().entries())
      bs.add(new Boundary(seconds(entry.nanoTime(), base), entry.kind(), entry.accepted()));
    boundaries = List.copyOf(bs);
    breakdown = CombatBreakdown.analyze(this, events);
  }

  private record IncomingBuild(List<Incoming> list, List<Double> unexplained) {}

  private static IncomingBuild incoming(
      List<CombatEvent> events, long base, String opponent, int window) {
    List<CombatEvent> damage = new ArrayList<>();
    List<CombatEvent> healthRaw = new ArrayList<>();
    List<CombatEvent> velocityRaw = new ArrayList<>();
    List<CombatEvent> motionRaw = new ArrayList<>();
    List<CombatEvent> jumpsRaw = new ArrayList<>();
    Set<Long> localCrits = new HashSet<>();
    for (var e : events)
      switch (e.kind()) {
        case DAMAGE_IN -> damage.add(e);
        case JUMP -> {
          if (e.clientTick() != null) jumpsRaw.add(e);
        }
        case VELOCITY -> {
          if (e.clientTick() != null && e.context() != null && e.context().vx() != null)
            velocityRaw.add(e);
        }
        case HEALTH -> {
          if (e.context() != null && e.context().health() != null) healthRaw.add(e);
        }
        case MOTION -> {
          if (e.clientTick() != null) motionRaw.add(e);
        }
        case CRIT -> {
          if (e.context() != null
              && "local".equals(e.context().subject())
              && e.clientTick() != null) localCrits.add(e.clientTick());
        }
        default -> {}
      }

    // Pre-extract primitive arrays once (avoids O(D * (H + V + M + J)) scans and repeated MOTION
    // string parsing on long fights).
    int hCount = 0;
    long[] hTick = new long[healthRaw.size()];
    long[] hSeq = new long[healthRaw.size()];
    double[] hVal = new double[healthRaw.size()];
    boolean hMonotonic = true;
    for (var h : healthRaw) {
      if (h.clientTick() == null) continue;
      long tk = h.clientTick();
      long sq = h.sequence();
      if (hCount > 0 && (tk < hTick[hCount - 1] || sq < hSeq[hCount - 1])) hMonotonic = false;
      hTick[hCount] = tk;
      hSeq[hCount] = sq;
      hVal[hCount] = h.context().health() + nz(h.context().absorption());
      hCount++;
    }

    int vCount = velocityRaw.size();
    long[] vTick = new long[vCount];
    double[] vKb = new double[vCount];
    boolean vMonotonic = true;
    for (int i = 0; i < vCount; i++) {
      var v = velocityRaw.get(i);
      long tk = v.clientTick();
      if (i > 0 && tk < vTick[i - 1]) vMonotonic = false;
      vTick[i] = tk;
      vKb[i] = Math.hypot(v.context().vx(), v.context().vz());
    }

    int mCount = 0;
    long[] mTick = new long[motionRaw.size()];
    double[] mSpeed = new double[motionRaw.size()];
    boolean mMonotonic = true;
    for (var m : motionRaw) {
      double[] vel = parseMotion(m.detail());
      if (vel == null) continue;
      long tk = m.clientTick();
      if (mCount > 0 && tk < mTick[mCount - 1]) mMonotonic = false;
      mTick[mCount] = tk;
      mSpeed[mCount] = Math.hypot(vel[0], vel[1]);
      mCount++;
    }

    int jCount = jumpsRaw.size();
    long[] jTick = new long[jCount];
    long[] jSeq = new long[jCount];
    boolean jMonotonic = true;
    for (int i = 0; i < jCount; i++) {
      var j = jumpsRaw.get(i);
      long tk = j.clientTick();
      long sq = j.sequence();
      if (i > 0 && (tk < jTick[i - 1] || sq < jSeq[i - 1])) jMonotonic = false;
      jTick[i] = tk;
      jSeq[i] = sq;
    }

    boolean dMonotonic = true;
    long prevDTick = Long.MIN_VALUE, prevDSeq = Long.MIN_VALUE;
    Set<Long> damageTicks = new HashSet<>(damage.size() * 2 + 1);
    for (var d : damage) {
      if (d.clientTick() != null) {
        long tk = d.clientTick();
        damageTicks.add(tk);
        if (tk < prevDTick || d.sequence() < prevDSeq) dMonotonic = false;
        prevDTick = tk;
        prevDSeq = d.sequence();
      }
    }

    boolean fastHealth = dMonotonic && hMonotonic;
    boolean fastVel = dMonotonic && vMonotonic;
    boolean fastMotion = dMonotonic && mMonotonic;
    boolean fastJumps = dMonotonic && jMonotonic;

    boolean[] vUsed = new boolean[vCount];
    int hBeforePtr = -1, hMinStart = 0, vStart = 0, mStart = 0, jPtr = 0;
    List<Incoming> out = new ArrayList<>(damage.size());
    for (var d : damage) {
      var ctx = d.context();
      boolean attributed = d.attributedTo(opponent);
      String attribution =
          ctx != null && ctx.attribution() != null
              ? ctx.attribution().name()
              : attributed
                  ? "OPPONENT_LEGACY"
                  : d.actor() == null ? "UNATTRIBUTED" : "OTHER_ENTITY";
      Long tickObj = d.clientTick();
      Double dmg = null, kb = null, after = null;
      if (tickObj != null) {
        long tick = tickObj;
        long dSeq = d.sequence();
        Double before = null, min = null;
        if (fastHealth) {
          while (hBeforePtr + 1 < hCount
              && hSeq[hBeforePtr + 1] < dSeq
              && hTick[hBeforePtr + 1] <= tick) hBeforePtr++;
          if (hBeforePtr >= 0) before = hVal[hBeforePtr];
          while (hMinStart < hCount
              && (hTick[hMinStart] < tick || (hTick[hMinStart] == tick && hSeq[hMinStart] < dSeq)))
            hMinStart++;
          for (int k = hMinStart; k < hCount && hTick[k] <= tick + 2; k++) {
            double v = hVal[k];
            min = min == null ? v : Math.min(min, v);
          }
        } else {
          for (int k = 0; k < hCount; k++) {
            double v = hVal[k];
            if (hSeq[k] < dSeq && hTick[k] <= tick) before = v;
            else if (hTick[k] >= tick && hTick[k] <= tick + 2)
              min = min == null ? v : Math.min(min, v);
          }
        }
        if (before != null && min != null && before - min > 1e-6) dmg = before - min;

        // Pair each incoming hit with at most one unclaimed VELOCITY packet in [tick - 1, tick + 1].
        // Priority: exact same tick (delta 0), then tick + 1 (post-damage impulse), then tick - 1
        // (packet arrived across a tick boundary before DamageEvent); strongest horizontal impulse
        // breaks ties on the same tick.
        int bestV = -1;
        int bestRank = Integer.MAX_VALUE;
        double bestKb = -1.0;
        if (fastVel) {
          while (vStart < vCount && vTick[vStart] < tick - 1) vStart++;
          for (int k = vStart; k < vCount && vTick[k] <= tick + 1; k++) {
            if (vUsed[k]) continue;
            if (vTick[k] != tick && damageTicks.contains(vTick[k])) continue;
            long diff = vTick[k] - tick;
            int rank = diff == 0 ? 0 : (diff == 1 ? 1 : 2);
            if (rank < bestRank || (rank == bestRank && vKb[k] > bestKb)) {
              bestRank = rank;
              bestKb = vKb[k];
              bestV = k;
            }
          }
        } else {
          for (int k = 0; k < vCount; k++) {
            if (vUsed[k]) continue;
            if (vTick[k] != tick && damageTicks.contains(vTick[k])) continue;
            long diff = vTick[k] - tick;
            if (diff < -1 || diff > 1) continue;
            int rank = diff == 0 ? 0 : (diff == 1 ? 1 : 2);
            if (rank < bestRank || (rank == bestRank && vKb[k] > bestKb)) {
              bestRank = rank;
              bestKb = vKb[k];
              bestV = k;
            }
          }
        }
        if (bestV >= 0) {
          long claimedTick = vTick[bestV];
          for (int k = fastVel ? vStart : 0; k < vCount; k++) {
            if (vTick[k] == claimedTick) vUsed[k] = true;
            else if (fastVel && vTick[k] > claimedTick) break;
          }
          kb = vKb[bestV];
        }

        double sum = 0;
        int n = 0;
        if (fastMotion) {
          while (mStart < mCount && mTick[mStart] < tick + 2) mStart++;
          for (int k = mStart; k < mCount && mTick[k] <= tick + 8; k++) {
            sum += mSpeed[k];
            n++;
          }
        } else {
          for (int k = 0; k < mCount; k++)
            if (mTick[k] >= tick + 2 && mTick[k] <= tick + 8) {
              sum += mSpeed[k];
              n++;
            }
        }
        if (n > 0) after = sum / n;
      }
      String jumpClass;
      Long offset = null;
      if (!attributed) jumpClass = "NOT_APPLICABLE";
      else if (tickObj == null) jumpClass = "UNAVAILABLE";
      else if (ctx != null && Boolean.FALSE.equals(ctx.movementValid())) jumpClass = "EXCLUDED";
      else {
        long tick = tickObj;
        int bestIdx = -1;
        long best = Long.MAX_VALUE;
        if (fastJumps && jCount > 0) {
          while (jPtr + 1 < jCount && jTick[jPtr + 1] <= tick) jPtr++;
          // Check earliest entry with jTick[lo] == jTick[jPtr] (lowest sequence tie-break)
          int lo = jPtr;
          while (lo > 0 && jTick[lo - 1] == jTick[jPtr]) lo--;
          bestIdx = lo;
          best = Math.abs(jTick[lo] - tick);
          if (jPtr + 1 < jCount) {
            int hi = jPtr + 1;
            long dHi = Math.abs(jTick[hi] - tick);
            if (dHi < best || (dHi == best && jSeq[hi] < jSeq[bestIdx])) {
              best = dHi;
              bestIdx = hi;
            }
          }
        } else {
          for (int k = 0; k < jCount; k++) {
            long delta = Math.abs(jTick[k] - tick);
            if (delta < best || (delta == best && bestIdx >= 0 && jSeq[k] < jSeq[bestIdx])) {
              best = delta;
              bestIdx = k;
            }
          }
        }
        if (bestIdx < 0 || best > 20) jumpClass = "NO_JUMP";
        else {
          offset = jTick[bestIdx] - tick;
          jumpClass =
              offset == 0
                  ? "IDEAL_CANDIDATE"
                  : Math.abs(offset) <= window ? "ATTEMPT_CANDIDATE" : "OUTSIDE_WINDOW";
        }
      }
      boolean crit =
          tickObj != null
              && (localCrits.contains(tickObj)
                  || localCrits.contains(tickObj - 1)
                  || localCrits.contains(tickObj + 1));
      out.add(
          new Incoming(
              d.sequence(),
              seconds(d.nanoTime(), base),
              tickObj,
              attribution,
              attributed,
              ctx == null ? null : ctx.damageType(),
              dmg,
              kb,
              ctx == null ? null : ctx.ground(),
              ctx == null ? null : ctx.sprinting(),
              ctx == null ? null : ctx.movementValid(),
              jumpClass,
              offset,
              crit,
              after));
    }
    // Health decreases with no damage event within two ticks: damage the client could not
    // attribute.
    List<Double> unexplained = new ArrayList<>();
    Double previous = null;
    for (var h : healthRaw) {
      double v = h.context().health() + nz(h.context().absorption());
      if (previous != null && v < previous - 1e-6 && h.clientTick() != null) {
        long t = h.clientTick();
        boolean explained = false;
        for (long k = t - 2; k <= t; k++) explained |= damageTicks.contains(k);
        if (!explained) unexplained.add(seconds(h.nanoTime(), base));
      }
      previous = v;
    }
    return new IncomingBuild(List.copyOf(out), List.copyOf(unexplained));
  }

  private static double nz(Double v) {
    return v == null ? 0 : v;
  }

  /** Horizontal velocity components from a MOTION detail string, or null. */
  static double[] parseMotion(String detail) {
    if (detail == null) return null;
    double vx = Double.NaN, vz = Double.NaN;
    for (String part : detail.split(";")) {
      try {
        if (part.startsWith("vx=")) vx = Double.parseDouble(part.substring(3));
        else if (part.startsWith("vz=")) vz = Double.parseDouble(part.substring(3));
      } catch (NumberFormatException ex) {
        return null;
      }
    }
    return Double.isFinite(vx) && Double.isFinite(vz) ? new double[] {vx, vz} : null;
  }

  private static JumpEffect jumpEffect(List<Incoming> incoming) {
    int nc = 0, no = 0;
    double sc = 0, so = 0;
    for (var in : incoming) {
      if (!in.attributed() || in.speedAfter() == null || in.knockback() == null) continue;
      switch (in.jumpClass()) {
        case "IDEAL_CANDIDATE", "ATTEMPT_CANDIDATE" -> {
          nc++;
          sc += in.speedAfter();
        }
        case "NO_JUMP", "OUTSIDE_WINDOW" -> {
          no++;
          so += in.speedAfter();
        }
        default -> {}
      }
    }
    if (nc < 3 || no < 3) return null;
    return new JumpEffect(nc, sc / nc, no, so / no);
  }

  private static List<TimingSample> timingSamples(
      List<CombatEvent> events, long base, Map<Long, String> outcomes) {
    List<TimingSample> out = new ArrayList<>();
    for (var e : events) {
      if (e.kind() != Kind.ATTACK || e.attack() == null || e.attack().measurement() == null)
        continue;
      var m = e.attack().measurement();
      var t = m.timing();
      if (t == null) continue;
      boolean opener = t.grade() == AttackTiming.Grade.OPENER;
      out.add(
          new TimingSample(
              e.sequence(),
              seconds(e.nanoTime(), base),
              m.item(),
              m.attackSpeed(),
              opener ? Double.NaN : t.expectedTicks(),
              opener ? Double.NaN : t.intervalTicks(),
              opener ? Double.NaN : t.intervalTicks() - t.expectedTicks(),
              t.grade(),
              outcomes.getOrDefault(e.sequence(), "UNCERTAIN")));
    }
    return List.copyOf(out);
  }

  private static ReachSummary reach(List<Hit> hits, double target, double tolerance) {
    List<Double> values = new ArrayList<>();
    List<String> weapons = new ArrayList<>();
    Map<String, List<Double>> by = new TreeMap<>();
    for (var h : hits) {
      if (!h.rayContact() || !(h.reach() >= 0) || !Double.isFinite(h.reach())) continue;
      values.add(h.reach());
      weapons.add(h.weapon());
      by.computeIfAbsent(h.weapon(), k -> new ArrayList<>()).add(h.reach());
    }
    double[] v = values.stream().mapToDouble(Double::doubleValue).toArray();
    Map<String, WeaponReach> byWeapon = new LinkedHashMap<>();
    for (var entry : by.entrySet()) {
      double[] w = entry.getValue().stream().mapToDouble(Double::doubleValue).toArray();
      byWeapon.put(
          entry.getKey(),
          new WeaponReach(w.length, within(w, target, tolerance), mean(w), sd(w), max(w)));
    }
    double mae = Double.NaN;
    if (v.length > 0) {
      mae = 0;
      for (double x : v) mae += Math.abs(x - target);
      mae /= v.length;
    }
    return new ReachSummary(
        target,
        tolerance,
        v.length,
        within(v, target, tolerance),
        mean(v),
        sd(v),
        max(v),
        mae,
        v,
        weapons.toArray(String[]::new),
        Map.copyOf(byWeapon));
  }

  private static int within(double[] v, double target, double tolerance) {
    int n = 0;
    for (double x : v) if (Math.abs(x - target) <= tolerance + 1e-9) n++;
    return n;
  }

  static double mean(double[] v) {
    if (v.length == 0) return Double.NaN;
    double s = 0;
    for (double x : v) s += x;
    return s / v.length;
  }

  /** Population standard deviation; NaN without samples. */
  static double sd(double[] v) {
    if (v.length == 0) return Double.NaN;
    double m = mean(v), s = 0;
    for (double x : v) s += (x - m) * (x - m);
    return Math.sqrt(s / v.length);
  }

  static double max(double[] v) {
    double m = Double.NaN;
    for (double x : v) m = Double.isNaN(m) ? x : Math.max(m, x);
    return m;
  }

  public static String reachBand(double reach) {
    return !(reach >= 0)
        ? "unknown"
        : reach < 2.5 ? "< 2.5 m" : reach < 3 ? "2.5-3.0 m" : ">= 3.0 m";
  }

  private static AimSummary aim(List<CombatEvent> events, long base, Set<Long> hitAttacks) {
    List<AimSample> samples = new ArrayList<>();
    int[] grades = new int[4];
    Map<String, int[]> byWeapon = new TreeMap<>(), byReach = new TreeMap<>();
    List<Double> distances = new ArrayList<>();
    for (var e : events) {
      if (e.kind() != Kind.ATTACK || e.attack() == null || !e.attack().rayContact()) continue;
      var a = e.attack();
      var m = a.measurement();
      boolean hit = hitAttacks.contains(e.sequence());
      var n = new Geometry.Vec(a.aimX(), a.aimY(), a.aimZ());
      String grade = m == null || m.aimQuality() == null ? "UNAVAILABLE" : m.aimQuality().name();
      double dist = m == null ? Double.NaN : MeleeAim.distance(n, m.idealRegion(), m.box());
      String weapon = m == null ? "unknown" : m.item();
      String band = reachBand(PulsarReach.comparable(a));
      samples.add(
          new AimSample(
              e.sequence(),
              seconds(e.nanoTime(), base),
              weapon,
              MeleeAim.face(n),
              a.aimX(),
              a.aimY(),
              a.aimZ(),
              Geometry.faceHorizontal(a.aimX(), a.aimZ()),
              a.aimY(),
              grade,
              dist,
              band,
              hit));
      if (!hit || m == null || m.aimQuality() == null || m.aimQuality().ordinal() >= 4) continue;
      int g = m.aimQuality().ordinal();
      grades[g]++;
      byWeapon.computeIfAbsent(weapon, k -> new int[4])[g]++;
      byReach.computeIfAbsent(band, k -> new int[4])[g]++;
      if (Double.isFinite(dist)) distances.add(dist);
    }
    double[] d = distances.stream().mapToDouble(Double::doubleValue).toArray();
    return new AimSummary(List.copyOf(samples), grades, mean(d), sd(d), byWeapon, byReach);
  }

  /** Seconds since fight start for a monotonic observation time. */
  public double time(long nanos) {
    return (nanos - baseNanos) / 1e9;
  }

  /** Every combo (two or more hits, either side) in time order. */
  public List<Combos.Streak> allCombos() {
    return combos.streaks().stream().filter(Combos.Streak::combo).toList();
  }

  public static FightAnalytics of(FightRecord fight) {
    return new FightAnalytics(fight);
  }

  private static double seconds(long nanos, long base) {
    return (nanos - base) / 1e9;
  }

  public static int reachBin(double reach) {
    return Math.min(REACH_BINS, Math.max(0, (int) (reach / REACH_BIN)));
  }

  /**
   * Legacy momentum change points (0.1.0-0.1.2 model, kept for the recorded per-hit values): +hit
   * on server evidence time, -incoming on attributed opponent damage, clipped to [-1, 1],
   * exponential decay between changes. Superseded for display by {@link Dominance}.
   */
  private static List<Point> momentum(
      List<Hit> hits,
      List<Double> incoming,
      List<dev.phenomena.combat.tracking.CombatExchange.Interval> exchanges,
      long base) {
    record Change(double time, boolean hit, boolean reset) {}
    List<Change> changes = new ArrayList<>();
    for (var h : hits) changes.add(new Change(h.evidenceTime(), true, false));
    for (var t : incoming) changes.add(new Change(t, false, false));
    for (var ex : exchanges) {
      changes.add(new Change(seconds(ex.startNanos(), base), false, true));
      changes.add(new Change(seconds(ex.endNanos(), base), false, true));
    }
    changes.sort(Comparator.comparingDouble(Change::time).thenComparing(ch -> !ch.reset()));
    List<Point> points = new ArrayList<>();
    double value = 0, at = 0;
    for (var ch : changes) {
      double decayed = decay(value, ch.time() - at);
      value =
          ch.reset()
              ? 0
              : ch.hit()
                  ? Math.min(1, decayed + Statistics.MOMENTUM_HIT)
                  : Math.max(-1, decayed - Statistics.MOMENTUM_INCOMING);
      at = ch.time();
      points.add(new Point(at, value));
    }
    return points;
  }

  private static double decay(double value, double seconds) {
    return value * Math.exp(-Math.max(0, seconds) * 1e9 / Statistics.MOMENTUM_DECAY_NANOS);
  }

  /** Legacy momentum estimate at a time in seconds since fight start. */
  public double momentumAt(double time) {
    double value = 0, at = 0;
    for (var p : momentumChanges) {
      if (p.time() > time) break;
      value = p.value();
      at = p.time();
    }
    return decay(value, time - at);
  }

  /** Evenly sampled legacy momentum curve. */
  public double[] momentumSamples(int count) {
    double[] out = new double[Math.max(2, count)];
    for (int i = 0; i < out.length; i++) out[i] = momentumAt(duration * i / (out.length - 1));
    return out;
  }

  public int longestCombo() {
    return combos.longest(Combos.Side.LOCAL);
  }

  /** Mean length of local combos (streaks of two or more hits). */
  public double averageCombo() {
    return combos.averageCombo(Combos.Side.LOCAL);
  }

  /** Accuracy with confirmed shield blocks removed from the denominator. */
  public double accuracy() {
    int attempts = hits.size() + misses.size() + shields.confirmedBlocks();
    int denominator = attempts - shields.confirmedBlocks();
    return denominator == 0 ? Double.NaN : 100.0 * hits.size() / denominator;
  }

  /** Opponent combo availability: attribution decides whether opponent streaks can be trusted. */
  public String opponentComboAvailability() {
    if (fight.manual()) return "Unavailable";
    if (attributedIncoming == 0 && unattributedIncoming + unexplainedHealthDrops > 0)
      return "Unavailable";
    if (unattributedIncoming + unexplainedHealthDrops > 0) return "Estimated";
    return "Observed";
  }
}
