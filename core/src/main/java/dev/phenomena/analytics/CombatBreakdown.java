package dev.phenomena.analytics;

import dev.phenomena.combat.events.CombatEvent;
import dev.phenomena.combat.events.CombatEvent.Kind;
import dev.phenomena.combat.metrics.Combos;
import java.util.*;

/**
 * PvP-focused derived analytics built purely from existing schema events and {@link FightAnalytics}
 * views. Covers 1.9+ Sword / NethPot / SMP (sprint-reset cadence, cooldown discipline, healing &
 * damage pacing) and Shield / Axe PvP (disable conversion, punish latency, axe-to-sword swap), plus
 * exchange openers, trades, turning points, and concise fact-backed takeaways.
 *
 * <p>Every calculation is deterministic and traces directly to recorded client observations.
 */
public final class CombatBreakdown {
  public static final double TRADE_WINDOW_SECONDS = 0.10;
  public static final double SPRINT_RESET_MAX_GAP_SECONDS = 2.5;
  public static final double FULL_CHARGE_THRESHOLD = 0.90;
  public static final double AXE_SWAP_WINDOW_SECONDS = 3.0;
  public static final double BURST_WINDOW_SECONDS = 3.0;
  public static final double BURST_HEAL_THRESHOLD_HP = 2.0;

  public record HealthPoint(double time, double health, double absorption, double total) {}

  public record HealthPacing(
      List<HealthPoint> trace,
      double startHp,
      double minHp,
      double minHpTime,
      double endHp,
      double totalDamageTaken,
      double totalHealing,
      int burstHeals,
      double absorptionGained,
      double damageTakenPerSec,
      double hitsPerSec,
      double worstBurstDamage,
      double worstBurstTime) {}

  public record SwordMechanics(
      int sprintResetOpportunities,
      int sprintResets,
      double sprintResetRate,
      int comboSprintResetOpportunities,
      int comboSprintResets,
      double comboSprintResetRate,
      int fullChargeHits,
      int earlyHits,
      double earlySwingRate,
      double meanHitCooldown,
      int critHits,
      double critRate,
      int critChains) {}

  public record ShieldPunish(
      int disablesInflicted,
      int disablesConverted,
      double disableConversionRate,
      double meanDisablePunishDelay,
      double meanPunishHitsPerDisable,
      int axeToSwordSwaps,
      double meanAxeToSwordSeconds,
      double bestAxeToSwordSeconds,
      int localDisables,
      int hitsTakenAfterLocalDisable,
      double damageTakenAfterLocalDisable) {}

  public record Exchange(
      int index,
      double start,
      double end,
      double duration,
      String opener,
      boolean openerConverted,
      int localHits,
      int opponentHits,
      int hitDiff,
      double damageTaken,
      double healingGained,
      double netHpDelta,
      double dpsTaken,
      double hitsPerSec,
      int crits,
      int sprintResets,
      int opponentDisables,
      int localDisables,
      String winner,
      String endedBy) {}

  public record ExchangeSummary(
      int total,
      int contested,
      int openedByYou,
      int openedByOpponent,
      int openedByTrade,
      double firstHitRate,
      int openersConverted,
      double firstHitConversionRate,
      int won,
      int lost,
      int even,
      double tradeRatio) {}

  public record ComboProfile(
      int localCombos,
      int opponentCombos,
      int longestLocal,
      int longestOpponent,
      int longestUnansweredLocal,
      int localComboHits,
      int totalLocalHits,
      double comboHitSharePct,
      double avgLocalCombo,
      double avgOpponentCombo,
      int sameTickTrades,
      double rating) {}

  public record KnockbackControl(
      int eligibleHits,
      int idealResets,
      int attemptResets,
      int earlyAttempts,
      int lateAttempts,
      int outsideWindow,
      int noJump,
      double timingRatePct,
      double idealSharePct,
      double meanAbsOffsetTicks,
      int kbSamples,
      double meanKbCandidate,
      double meanKbNoJump,
      double meanSpeedCandidate,
      double meanSpeedNoJump,
      double speedReductionPct,
      double rating) {}

  public record TurningPoint(
      double time, String kind, String tone, String title, String detail) {}

  public record Takeaway(String tone, String category, String headline, String detail) {}

  public final HealthPacing health;
  public final SwordMechanics sword;
  public final ShieldPunish shields;
  public final ExchangeSummary exchangeSummary;
  public final ComboProfile comboProfile;
  public final KnockbackControl knockbackControl;
  public final List<Exchange> exchanges;
  public final List<TurningPoint> turningPoints;
  public final List<Takeaway> takeaways;

  private CombatBreakdown(
      HealthPacing health,
      SwordMechanics sword,
      ShieldPunish shields,
      ExchangeSummary exchangeSummary,
      ComboProfile comboProfile,
      KnockbackControl knockbackControl,
      List<Exchange> exchanges,
      List<TurningPoint> turningPoints,
      List<Takeaway> takeaways) {
    this.health = health;
    this.sword = sword;
    this.shields = shields;
    this.exchangeSummary = exchangeSummary;
    this.comboProfile = comboProfile;
    this.knockbackControl = knockbackControl;
    this.exchanges = exchanges;
    this.turningPoints = turningPoints;
    this.takeaways = takeaways;
  }

  public static CombatBreakdown analyze(FightAnalytics a, List<CombatEvent> events) {
    long base = a.baseNanos;
    List<HealthPoint> trace = new ArrayList<>();
    List<Double> sprintStops = new ArrayList<>();
    for (var e : events) {
      if (e.kind() == Kind.HEALTH && e.context() != null && e.context().health() != null) {
        double hp = e.context().health();
        double abs = e.context().absorption() == null ? 0.0 : e.context().absorption();
        trace.add(new HealthPoint((e.nanoTime() - base) / 1e9, hp, abs, hp + abs));
      } else if (e.kind() == Kind.SPRINT && "false".equals(e.detail())) {
        sprintStops.add((e.nanoTime() - base) / 1e9);
      }
    }

    HealthPacing health = buildHealth(a, trace);
    SprintResetResult sprintResult = analyzeSword(a, sprintStops);
    ShieldPunish shieldPunish = buildShieldPunish(a);
    ExchangeBuild exchangeBuild = buildExchanges(a, trace, sprintResult.resetHitIndices);
    ComboProfile comboProfile = buildComboProfile(a);
    KnockbackControl knockbackControl = buildKnockbackControl(a);
    List<TurningPoint> turningPoints = buildTurningPoints(a, health, shieldPunish, trace);
    List<Takeaway> takeaways =
        buildTakeaways(
            a, health, sprintResult.sword, shieldPunish, exchangeBuild.summary, knockbackControl);

    return new CombatBreakdown(
        health,
        sprintResult.sword,
        shieldPunish,
        exchangeBuild.summary,
        comboProfile,
        knockbackControl,
        exchangeBuild.exchanges,
        turningPoints,
        takeaways);
  }

  private record Drop(double time, double amount) {}

  private static HealthPacing buildHealth(FightAnalytics a, List<HealthPoint> trace) {
    double startHp = Double.NaN, endHp = Double.NaN, minHp = Double.NaN, minHpTime = Double.NaN;
    double damageTaken = 0.0, healing = 0.0, absorptionGained = 0.0;
    int burstHeals = 0;
    List<Drop> drops = new ArrayList<>();
    HealthPoint prev = null;
    for (var p : trace) {
      if (prev == null) {
        startHp = p.total();
        minHp = p.total();
        minHpTime = p.time();
      } else {
        double delta = p.total() - prev.total();
        if (delta < -1e-6) {
          double lost = -delta;
          damageTaken += lost;
          drops.add(new Drop(p.time(), lost));
        } else if (delta > 1e-6) {
          healing += delta;
          if (delta >= BURST_HEAL_THRESHOLD_HP) burstHeals++;
        }
        if (p.absorption() > prev.absorption() + 1e-6) {
          absorptionGained += p.absorption() - prev.absorption();
        }
        if (p.total() < minHp - 1e-6) {
          minHp = p.total();
          minHpTime = p.time();
        }
      }
      endHp = p.total();
      prev = p;
    }

    double worstBurst = 0.0;
    double worstBurstTime = Double.NaN;
    int left = 0;
    double windowSum = 0.0;
    for (int right = 0; right < drops.size(); right++) {
      windowSum += drops.get(right).amount();
      while (left < right
          && drops.get(right).time() - drops.get(left).time() > BURST_WINDOW_SECONDS + 1e-9) {
        windowSum -= drops.get(left).amount();
        left++;
      }
      if (windowSum > worstBurst + 1e-6) {
        worstBurst = windowSum;
        worstBurstTime = drops.get(left).time();
      }
    }

    double activeSpan = a.combatActiveSeconds > 0 ? a.combatActiveSeconds : a.duration;
    double dpsTaken = activeSpan > 0 && !trace.isEmpty() ? damageTaken / activeSpan : Double.NaN;
    double hitsPerSec = activeSpan > 0 && !a.hits.isEmpty() ? a.hits.size() / activeSpan : Double.NaN;

    return new HealthPacing(
        List.copyOf(trace),
        startHp,
        minHp,
        minHpTime,
        endHp,
        damageTaken,
        healing,
        burstHeals,
        absorptionGained,
        dpsTaken,
        hitsPerSec,
        worstBurst,
        worstBurstTime);
  }

  private record SprintResetResult(SwordMechanics sword, Set<Integer> resetHitIndices) {}

  private static SprintResetResult analyzeSword(FightAnalytics a, List<Double> sprintStops) {
    var hits = a.hits;
    // Derive combo follow-up membership from post-fight Combos.analyze (immune to same-tick trade
    // ordering noise and exchange/round boundary artifacts in live ev.combo()).
    Set<Long> comboFollowSeqs = new HashSet<>();
    boolean hasStreakAnalysis = a.combos != null && !a.combos.streaks().isEmpty();
    if (hasStreakAnalysis) {
      for (var st : a.combos.streaks()) {
        if (st.side() == Combos.Side.LOCAL && st.combo()) {
          for (int k = 1; k < st.sequences().size(); k++) {
            comboFollowSeqs.add(st.sequences().get(k));
          }
        }
      }
    }
    int opp = 0, resets = 0, comboOpp = 0, comboResets = 0;
    Set<Integer> resetIndices = new HashSet<>();
    int stopPtr = 0;
    for (int i = 1; i < hits.size(); i++) {
      var prev = hits.get(i - 1);
      var curr = hits.get(i);
      double dt = curr.time() - prev.time();
      if (prev.sprinting() && dt > 0 && dt <= SPRINT_RESET_MAX_GAP_SECONDS) {
        opp++;
        boolean inCombo = hasStreakAnalysis ? comboFollowSeqs.contains(curr.attackSequence()) : curr.combo() >= 2;
        if (inCombo) comboOpp++;
        while (stopPtr < sprintStops.size() && sprintStops.get(stopPtr) <= prev.time()) stopPtr++;
        boolean stoppedBetween =
            stopPtr < sprintStops.size() && sprintStops.get(stopPtr) < curr.time();
        if (curr.sprinting() && stoppedBetween) {
          resets++;
          resetIndices.add(i);
          if (inCombo) comboResets++;
        }
      }
    }

    int fullCharge = 0, early = 0, crits = 0, critChains = 0;
    double cooldownSum = 0.0;
    boolean prevCrit = false;
    double prevTime = Double.NaN;
    for (var h : hits) {
      double cd = h.cooldown();
      if (Double.isFinite(cd)) {
        cooldownSum += cd;
        if (cd >= FULL_CHARGE_THRESHOLD) fullCharge++;
        else early++;
      }
      boolean crit = isCrit(a, h);
      if (crit) {
        crits++;
        if (prevCrit && Double.isFinite(prevTime) && h.time() - prevTime <= SPRINT_RESET_MAX_GAP_SECONDS) {
          critChains++;
        }
      }
      prevCrit = crit;
      prevTime = h.time();
    }

    int totalCd = fullCharge + early;
    SwordMechanics sword =
        new SwordMechanics(
            opp,
            resets,
            ratioPct(resets, opp),
            comboOpp,
            comboResets,
            ratioPct(comboResets, comboOpp),
            fullCharge,
            early,
            ratioPct(early, totalCd),
            totalCd == 0 ? Double.NaN : cooldownSum / totalCd,
            crits,
            ratioPct(crits, hits.size()),
            critChains);
    return new SprintResetResult(sword, resetIndices);
  }

  private static boolean isCrit(FightAnalytics a, FightAnalytics.Hit h) {
    return a.contextAvailable ? Boolean.TRUE.equals(h.serverCrit()) : h.criticalConditions();
  }

  public static String weaponCategory(String weapon) {
    if (weapon == null || weapon.isEmpty()) return "OTHER";
    int semi = weapon.indexOf(';');
    String base = (semi >= 0 ? weapon.substring(0, semi) : weapon).toLowerCase(Locale.ROOT);
    if (base.contains("_axe") || base.endsWith(":axe")) return "AXE";
    if (base.contains("_sword") || base.endsWith(":sword")) return "SWORD";
    if (base.contains("mace")) return "MACE";
    return "OTHER";
  }

  private static ShieldPunish buildShieldPunish(FightAnalytics a) {
    List<FightAnalytics.ShieldEvent> oppDisables = new ArrayList<>();
    List<FightAnalytics.ShieldEvent> localDisables = new ArrayList<>();
    for (var s : a.shields.events()) {
      if ("DISABLE".equals(s.type())) {
        if ("opponent".equals(s.subject())) oppDisables.add(s);
        else if ("local".equals(s.subject())) localDisables.add(s);
      }
    }

    int converted = 0, punishHitsSum = 0;
    double punishDelaySum = 0.0;
    List<Double> axeSwaps = new ArrayList<>();
    Set<Long> countedSwapSeqs = new HashSet<>();

    for (var d : oppDisables) {
      double dt = d.time();
      int followHits = 0;
      double firstDelay = Double.NaN;
      boolean foundSwordAfterDisable = false;
      for (var h : a.hits) {
        if (h.evidenceTime() <= dt) continue;
        double delta = h.evidenceTime() - dt;
        if (delta > FightAnalytics.DISABLE_FOLLOW_UP_SECONDS) break;
        if (followHits == 0) firstDelay = delta;
        followHits++;
        if (!foundSwordAfterDisable
            && delta <= AXE_SWAP_WINDOW_SECONDS
            && "SWORD".equals(weaponCategory(h.weapon()))
            && ("AXE".equals(weaponCategory(d.weapon())) || d.weapon() == null)) {
          foundSwordAfterDisable = true;
          double swapDelta = h.time() - dt;
          if (swapDelta > 0 && swapDelta <= AXE_SWAP_WINDOW_SECONDS && countedSwapSeqs.add(h.attackSequence())) {
            axeSwaps.add(swapDelta);
          }
        }
      }
      if (followHits > 0) {
        converted++;
        punishDelaySum += firstDelay;
        punishHitsSum += followHits;
      }
    }

    // Also measure direct axe-hit -> sword-hit follow-ups (stun-slam / axe-sword combo swaps).
    for (int i = 1; i < a.hits.size(); i++) {
      var prev = a.hits.get(i - 1);
      var curr = a.hits.get(i);
      if ("AXE".equals(weaponCategory(prev.weapon()))
          && "SWORD".equals(weaponCategory(curr.weapon()))) {
        double delta = curr.time() - prev.time();
        if (delta > 0 && delta <= AXE_SWAP_WINDOW_SECONDS && countedSwapSeqs.add(curr.attackSequence())) {
          axeSwaps.add(delta);
        }
      }
    }

    double swapSum = 0.0, bestSwap = Double.NaN;
    for (double s : axeSwaps) {
      swapSum += s;
      bestSwap = Double.isNaN(bestSwap) ? s : Math.min(bestSwap, s);
    }

    int hitsTakenAfterLocal = 0;
    double dmgTakenAfterLocal = 0.0;
    Set<Long> countedIncoming = new HashSet<>();
    for (var ld : localDisables) {
      double dt = ld.time();
      for (var in : a.incoming) {
        if (!in.attributed() || in.time() <= dt) continue;
        if (in.time() - dt > FightAnalytics.DISABLE_FOLLOW_UP_SECONDS) break;
        if (countedIncoming.add(in.sequence())) {
          hitsTakenAfterLocal++;
          if (in.damage() != null && Double.isFinite(in.damage())) dmgTakenAfterLocal += in.damage();
        }
      }
    }

    int inflicted = oppDisables.size();
    return new ShieldPunish(
        inflicted,
        converted,
        ratioPct(converted, inflicted),
        converted == 0 ? Double.NaN : punishDelaySum / converted,
        inflicted == 0 ? Double.NaN : (double) punishHitsSum / inflicted,
        axeSwaps.size(),
        axeSwaps.isEmpty() ? Double.NaN : swapSum / axeSwaps.size(),
        bestSwap,
        localDisables.size(),
        hitsTakenAfterLocal,
        dmgTakenAfterLocal);
  }

  private record Span(double start, double end, String endedBy) {}

  private record ExchangeBuild(ExchangeSummary summary, List<Exchange> exchanges) {}

  private static ExchangeBuild buildExchanges(
      FightAnalytics a, List<HealthPoint> trace, Set<Integer> resetHitIndices) {
    List<Span> spans = new ArrayList<>();
    if (!a.activeExchanges.isEmpty()) {
      for (var ex : a.activeExchanges) spans.add(new Span(ex.start(), ex.end(), ex.endedBy()));
    } else if (!a.combos.exchanges().isEmpty()) {
      for (var ex : a.combos.exchanges())
        spans.add(
            new Span(
                (ex.startNanos() - a.baseNanos) / 1e9,
                (ex.endNanos() - a.baseNanos) / 1e9,
                "GAP"));
    } else if (!a.hits.isEmpty() || !a.incomingTimes.isEmpty()) {
      spans.add(new Span(0.0, a.duration, "FIGHT"));
    }

    List<Exchange> rows = new ArrayList<>(spans.size());
    int hitCursor = 0, incCursor = 0, hpCursor = 1, shCursor = 0;
    var shieldEvents = a.shields.events();

    int contested = 0, openedYou = 0, openedOpp = 0, openedTrade = 0, converted = 0;
    int won = 0, lost = 0, even = 0;

    for (int i = 0; i < spans.size(); i++) {
      var sp = spans.get(i);
      boolean includeEnd = i + 1 == spans.size() || spans.get(i + 1).start() > sp.end() + 1e-9;
      double endBound = sp.end() + (includeEnd ? 1e-6 : -1e-9);

      while (hitCursor < a.hits.size() && a.hits.get(hitCursor).evidenceTime() < sp.start() - 1e-6)
        hitCursor++;
      List<Integer> exHitIdx = new ArrayList<>();
      while (hitCursor < a.hits.size() && a.hits.get(hitCursor).evidenceTime() <= endBound) {
        exHitIdx.add(hitCursor++);
      }

      while (incCursor < a.incoming.size() && a.incoming.get(incCursor).time() < sp.start() - 1e-6)
        incCursor++;
      List<FightAnalytics.Incoming> exInc = new ArrayList<>();
      while (incCursor < a.incoming.size() && a.incoming.get(incCursor).time() <= endBound) {
        var in = a.incoming.get(incCursor++);
        if (in.attributed()) exInc.add(in);
      }

      double dmg = 0.0, heal = 0.0;
      while (hpCursor < trace.size() && trace.get(hpCursor).time() < sp.start() - 1e-6) hpCursor++;
      while (hpCursor < trace.size() && trace.get(hpCursor).time() <= endBound) {
        double delta = trace.get(hpCursor).total() - trace.get(hpCursor - 1).total();
        if (delta < -1e-6) dmg += -delta;
        else if (delta > 1e-6) heal += delta;
        hpCursor++;
      }

      int oppDisables = 0, locDisables = 0;
      while (shCursor < shieldEvents.size() && shieldEvents.get(shCursor).time() < sp.start() - 1e-6)
        shCursor++;
      while (shCursor < shieldEvents.size() && shieldEvents.get(shCursor).time() <= endBound) {
        var se = shieldEvents.get(shCursor++);
        if ("DISABLE".equals(se.type())) {
          if ("opponent".equals(se.subject())) oppDisables++;
          else if ("local".equals(se.subject())) locDisables++;
        }
      }

      int exCrits = 0, exResets = 0;
      for (int hi : exHitIdx) {
        if (isCrit(a, a.hits.get(hi))) exCrits++;
        if (resetHitIndices.contains(hi)) exResets++;
      }

      Double firstLocal = exHitIdx.isEmpty() ? null : a.hits.get(exHitIdx.getFirst()).evidenceTime();
      Double firstOpp = exInc.isEmpty() ? null : exInc.getFirst().time();
      String opener;
      if (firstLocal == null && firstOpp == null) opener = "NONE";
      else if (firstOpp == null) opener = "YOU";
      else if (firstLocal == null) opener = "OPPONENT";
      else if (Math.abs(firstLocal - firstOpp) <= TRADE_WINDOW_SECONDS + 1e-9) opener = "TRADE";
      else if (firstLocal < firstOpp) opener = "YOU";
      else opener = "OPPONENT";

      boolean openerConv = false;
      if ("YOU".equals(opener) && exHitIdx.size() >= 2) {
        double secondHitTime = a.hits.get(exHitIdx.get(1)).evidenceTime();
        if (firstOpp == null || secondHitTime < firstOpp - 1e-9) openerConv = true;
      }

      int localHits = exHitIdx.size();
      int oppHits = exInc.size();
      int hitDiff = localHits - oppHits;
      String winner = hitDiff > 0 ? "YOU" : hitDiff < 0 ? "OPPONENT" : "EVEN";

      if (!"NONE".equals(opener)) {
        contested++;
        switch (opener) {
          case "YOU" -> openedYou++;
          case "OPPONENT" -> openedOpp++;
          case "TRADE" -> openedTrade++;
          default -> {}
        }
        if (openerConv) converted++;
        switch (winner) {
          case "YOU" -> won++;
          case "OPPONENT" -> lost++;
          default -> even++;
        }
      }

      double dur = Math.max(0.0, sp.end() - sp.start());
      double dps = dur >= 0.5 && dmg > 0 ? dmg / dur : Double.NaN;
      double hps = dur >= 0.5 && localHits > 0 ? localHits / dur : Double.NaN;
      rows.add(
          new Exchange(
              i + 1,
              sp.start(),
              sp.end(),
              dur,
              opener,
              openerConv,
              localHits,
              oppHits,
              hitDiff,
              dmg,
              heal,
              heal - dmg,
              dps,
              hps,
              exCrits,
              exResets,
              oppDisables,
              locDisables,
              winner,
              sp.endedBy()));
    }

    double tradeRatio =
        a.attributedIncoming == 0 ? Double.NaN : (double) a.hits.size() / a.attributedIncoming;
    ExchangeSummary summary =
        new ExchangeSummary(
            rows.size(),
            contested,
            openedYou,
            openedOpp,
            openedTrade,
            ratioPct(openedYou, contested),
            converted,
            ratioPct(converted, openedYou),
            won,
            lost,
            even,
            tradeRatio);
    return new ExchangeBuild(summary, List.copyOf(rows));
  }

  private static List<TurningPoint> buildTurningPoints(
      FightAnalytics a,
      HealthPacing health,
      ShieldPunish shields,
      List<HealthPoint> trace) {
    List<TurningPoint> points = new ArrayList<>();

    if (a.dominance.largestSwing != null) {
      var sw = a.dominance.largestSwing;
      double delta = sw.toValue() - sw.fromValue();
      if (Math.abs(delta) >= 0.35) {
        boolean positive = delta > 0;
        points.add(
            new TurningPoint(
                sw.from(),
                "SWING",
                positive ? "POSITIVE" : "NEGATIVE",
                positive ? "Momentum swing in your favor" : "Momentum swing to opponent",
                String.format(
                    Locale.ROOT,
                    "Dominance moved %+.2f -> %+.2f (%.1fs-%.1fs)",
                    sw.fromValue(),
                    sw.toValue(),
                    sw.from(),
                    sw.to())));
      }
    }

    Combos.Streak bestLocal = null, bestOpp = null;
    for (var s : a.combos.streaks()) {
      if (!s.combo()) continue;
      if (s.side() == Combos.Side.LOCAL) {
        if (bestLocal == null || s.length() > bestLocal.length()) bestLocal = s;
      } else if ("Observed".equals(a.opponentComboAvailability())) {
        if (bestOpp == null || s.length() > bestOpp.length()) bestOpp = s;
      }
    }
    if (bestLocal != null && bestLocal.length() >= 3) {
      double start = (bestLocal.startNanos() - a.baseNanos) / 1e9;
      double span = Math.max(0.0, (bestLocal.endNanos() - bestLocal.startNanos()) / 1e9);
      points.add(
          new TurningPoint(
              start,
              "LOCAL_COMBO",
              "POSITIVE",
              bestLocal.length() + "-hit combo",
              String.format(Locale.ROOT, "%d unanswered hits over %.1fs", bestLocal.length(), span)));
    }
    if (bestOpp != null && bestOpp.length() >= 3) {
      double start = (bestOpp.startNanos() - a.baseNanos) / 1e9;
      double span = Math.max(0.0, (bestOpp.endNanos() - bestOpp.startNanos()) / 1e9);
      points.add(
          new TurningPoint(
              start,
              "OPPONENT_COMBO",
              "NEGATIVE",
              "Opponent " + bestOpp.length() + "-hit combo",
              String.format(Locale.ROOT, "%d consecutive incoming hits over %.1fs", bestOpp.length(), span)));
    }

    if (health.worstBurstDamage() >= 6.0 && Double.isFinite(health.worstBurstTime())) {
      points.add(
          new TurningPoint(
              health.worstBurstTime(),
              "DAMAGE_BURST",
              "NEGATIVE",
              String.format(Locale.ROOT, "Heavy burst (-%.1f HP)", health.worstBurstDamage()),
              String.format(
                  Locale.ROOT,
                  "Lost %.1f HP inside a %.0fs window",
                  health.worstBurstDamage(),
                  BURST_WINDOW_SECONDS)));
    }

    // Best opponent shield disable punish & worst local shield disable
    FightAnalytics.ShieldEvent bestOppDisable = null;
    int bestOppDisableHits = 0;
    for (var s : a.shields.events()) {
      if ("DISABLE".equals(s.type()) && "opponent".equals(s.subject())) {
        int follow = 0;
        for (var h : a.hits) {
          if (h.evidenceTime() > s.time()
              && h.evidenceTime() - s.time() <= FightAnalytics.DISABLE_FOLLOW_UP_SECONDS)
            follow++;
        }
        if (follow > bestOppDisableHits) {
          bestOppDisableHits = follow;
          bestOppDisable = s;
        }
      }
    }
    if (bestOppDisable != null && bestOppDisableHits >= 2) {
      points.add(
          new TurningPoint(
              bestOppDisable.time(),
              "SHIELD_PUNISH",
              "POSITIVE",
              "Shield disable punish (" + bestOppDisableHits + " hits)",
              String.format(
                  Locale.ROOT,
                  "Landed %d confirmed hits in the 5s window after breaking shield",
                  bestOppDisableHits)));
    } else if (shields.localDisables() > 0 && shields.hitsTakenAfterLocalDisable() >= 2) {
      for (var s : a.shields.events()) {
        if ("DISABLE".equals(s.type()) && "local".equals(s.subject())) {
          points.add(
              new TurningPoint(
                  s.time(),
                  "LOCAL_SHIELD_BREAK",
                  "NEGATIVE",
                  "Shield broken",
                  String.format(
                      Locale.ROOT,
                      "Took %d hits (-%.1f HP) during local disable windows",
                      shields.hitsTakenAfterLocalDisable(),
                      shields.damageTakenAfterLocalDisable())));
          break;
        }
      }
    }

    // Largest single burst heal >= 4.0 HP (splash pot / gapple / totem)
    double bestHeal = 0.0, bestHealTime = Double.NaN;
    for (int i = 1; i < trace.size(); i++) {
      double gain = trace.get(i).total() - trace.get(i - 1).total();
      if (gain > bestHeal + 1e-6) {
        bestHeal = gain;
        bestHealTime = trace.get(i).time();
      }
    }
    if (bestHeal >= 4.0 && Double.isFinite(bestHealTime)) {
      points.add(
          new TurningPoint(
              bestHealTime,
              "BURST_HEAL",
              "NEUTRAL",
              String.format(Locale.ROOT, "Burst recovery (+%.1f HP)", bestHeal),
              String.format(Locale.ROOT, "Health + absorption rose by %.1f HP", bestHeal)));
    }

    points.sort(Comparator.comparingDouble(TurningPoint::time));
    return points.size() <= 6 ? List.copyOf(points) : List.copyOf(points.subList(0, 6));
  }

  private static ComboProfile buildComboProfile(FightAnalytics a) {
    var c = a.combos;
    int localCombos = c.combos(Combos.Side.LOCAL).size();
    int oppCombos = c.combos(Combos.Side.OPPONENT).size();
    int longestLocal = c.longest(Combos.Side.LOCAL);
    int longestOpp = c.longest(Combos.Side.OPPONENT);
    int longestUnanswered = c.longestUnansweredLocal();
    int comboHits = c.comboHits(Combos.Side.LOCAL);
    int totalHits = a.hits.size();
    double sharePct = ratioPct(comboHits, totalHits);
    double avgLocal = c.averageCombo(Combos.Side.LOCAL);
    double avgOpp = c.averageCombo(Combos.Side.OPPONENT);
    int tradedLocalMarks = 0;
    for (var m : c.marks()) {
      if (m.traded() && m.side() == Combos.Side.LOCAL) tradedLocalMarks++;
    }
    double rating = Double.NaN;
    if (totalHits >= 3) {
      double shareScore = Double.isFinite(sharePct) ? sharePct : 0.0;
      double lenScore = Math.min(100.0, (Double.isFinite(avgLocal) ? (avgLocal - 1.0) * 35.0 : 0.0));
      int oppComboHits = c.comboHits(Combos.Side.OPPONENT);
      double diffScore =
          (comboHits + oppComboHits) == 0
              ? 50.0
              : 100.0 * comboHits / (comboHits + oppComboHits);
      rating = clampScore(0.45 * shareScore + 0.30 * lenScore + 0.25 * diffScore);
    }
    return new ComboProfile(
        localCombos,
        oppCombos,
        longestLocal,
        longestOpp,
        longestUnanswered,
        comboHits,
        totalHits,
        sharePct,
        avgLocal,
        avgOpp,
        tradedLocalMarks,
        rating);
  }

  private static KnockbackControl buildKnockbackControl(FightAnalytics a) {
    int eligible = 0, ideal = 0, attempt = 0, early = 0, late = 0, outside = 0, noJump = 0;
    double absOffsetSum = 0.0;
    int offsetCount = 0;
    int kbSamples = 0, kbCandCount = 0, kbNoJumpCount = 0;
    double kbCandSum = 0.0, kbNoJumpSum = 0.0;
    int spCandCount = 0, spNoJumpCount = 0;
    double spCandSum = 0.0, spNoJumpSum = 0.0;

    for (var in : a.incoming) {
      if (!in.attributed()) continue;
      String jc = in.jumpClass();
      if ("EXCLUDED".equals(jc) || "UNAVAILABLE".equals(jc) || "NOT_APPLICABLE".equals(jc)) {
        continue;
      }
      eligible++;
      boolean isCandidate = "IDEAL_CANDIDATE".equals(jc) || "ATTEMPT_CANDIDATE".equals(jc);
      switch (jc) {
        case "IDEAL_CANDIDATE" -> {
          ideal++;
          offsetCount++;
        }
        case "ATTEMPT_CANDIDATE" -> {
          attempt++;
          if (in.jumpOffsetTicks() != null) {
            long off = in.jumpOffsetTicks();
            if (off < 0) early++;
            else if (off > 0) late++;
            absOffsetSum += Math.abs(off);
            offsetCount++;
          }
        }
        case "OUTSIDE_WINDOW" -> outside++;
        case "NO_JUMP" -> noJump++;
        default -> {}
      }
      if (in.knockback() != null) {
        kbSamples++;
        if (isCandidate) {
          kbCandCount++;
          kbCandSum += in.knockback();
        } else {
          kbNoJumpCount++;
          kbNoJumpSum += in.knockback();
        }
      }
      if (in.speedAfter() != null && in.knockback() != null) {
        if (isCandidate) {
          spCandCount++;
          spCandSum += in.speedAfter();
        } else {
          spNoJumpCount++;
          spNoJumpSum += in.speedAfter();
        }
      }
    }

    double timingRate = ratioPct(ideal + attempt, eligible);
    double idealShare = ratioPct(ideal, eligible);
    double meanAbsOff = offsetCount == 0 ? Double.NaN : absOffsetSum / offsetCount;
    double meanKbCand = kbCandCount == 0 ? Double.NaN : kbCandSum / kbCandCount;
    double meanKbNo = kbNoJumpCount == 0 ? Double.NaN : kbNoJumpSum / kbNoJumpCount;
    double meanSpCand = spCandCount < 2 ? Double.NaN : spCandSum / spCandCount;
    double meanSpNo = spNoJumpCount < 2 ? Double.NaN : spNoJumpSum / spNoJumpCount;
    double reductionPct =
        Double.isFinite(meanSpCand) && Double.isFinite(meanSpNo) && meanSpNo > 1e-6
            ? 100.0 * (1.0 - meanSpCand / meanSpNo)
            : Double.NaN;

    double rating = Double.NaN;
    if (eligible >= 3) {
      double precisionScore =
          offsetCount == 0
              ? 0.0
              : clampScore(100.0 * (1.0 - meanAbsOff / Math.max(1.0, a.fight.policy().jumpWindowTicks())));
      if (Double.isFinite(reductionPct)) {
        double redScore = clampScore(50.0 + reductionPct * 1.25);
        rating = clampScore(0.40 * timingRate + 0.30 * precisionScore + 0.30 * redScore);
      } else {
        rating = clampScore(0.55 * timingRate + 0.45 * precisionScore);
      }
    }
    return new KnockbackControl(
        eligible,
        ideal,
        attempt,
        early,
        late,
        outside,
        noJump,
        timingRate,
        idealShare,
        meanAbsOff,
        kbSamples,
        meanKbCand,
        meanKbNo,
        meanSpCand,
        meanSpNo,
        reductionPct,
        rating);
  }

  private static double clampScore(double value) {
    if (!Double.isFinite(value)) return Double.NaN;
    return Math.max(0.0, Math.min(100.0, value));
  }

  private static List<Takeaway> buildTakeaways(
      FightAnalytics a,
      HealthPacing health,
      SwordMechanics sword,
      ShieldPunish shields,
      ExchangeSummary ex,
      KnockbackControl kb) {
    List<Takeaway> list = new ArrayList<>();

    // 1. Exchange openers & trade efficiency
    if (ex.contested() >= 2 && Double.isFinite(ex.firstHitRate())) {
      String tone =
          ex.firstHitRate() >= 60.0
              ? "POSITIVE"
              : ex.firstHitRate() <= 40.0 ? "NEGATIVE" : "NEUTRAL";
      String headline =
          ex.firstHitRate() >= 60.0
              ? String.format(Locale.ROOT, "Won %.0f%% of exchange openers", ex.firstHitRate())
              : ex.firstHitRate() <= 40.0
                  ? String.format(
                      Locale.ROOT,
                      "Opponent opened %d/%d exchanges",
                      ex.openedByOpponent(),
                      ex.contested())
                  : String.format(Locale.ROOT, "Split openers (%.0f%% first-hit)", ex.firstHitRate());
      String tradePart =
          Double.isFinite(ex.tradeRatio())
              ? String.format(Locale.ROOT, " | %.2fx hit trade", ex.tradeRatio())
              : "";
      String convPart =
          ex.openedByYou() > 0
              ? String.format(
                  Locale.ROOT, ", converted %d/%d into combos", ex.openersConverted(), ex.openedByYou())
              : "";
      String detail =
          String.format(
              Locale.ROOT,
              "Opened %d/%d contested exchanges%s (%dW-%dL-%dE%s).",
              ex.openedByYou(),
              ex.contested(),
              convPart,
              ex.won(),
              ex.lost(),
              ex.even(),
              tradePart);
      list.add(new Takeaway(tone, "Openers", headline, detail));
    }

    // 2. Sprint-reset & 1.9+ cooldown discipline
    if (sword.sprintResetOpportunities() >= 4 && Double.isFinite(sword.sprintResetRate())) {
      String tone =
          sword.sprintResetRate() >= 65.0
              ? "POSITIVE"
              : sword.sprintResetRate() < 40.0 ? "NEGATIVE" : "NEUTRAL";
      String headline =
          sword.sprintResetRate() >= 65.0
              ? String.format(Locale.ROOT, "Consistent sprint resets (%.0f%%)", sword.sprintResetRate())
              : sword.sprintResetRate() < 40.0
                  ? String.format(Locale.ROOT, "Low sprint-reset rate (%.0f%%)", sword.sprintResetRate())
                  : String.format(Locale.ROOT, "Moderate sprint resets (%.0f%%)", sword.sprintResetRate());
      String comboPart =
          sword.comboSprintResetOpportunities() >= 2
              ? String.format(
                  Locale.ROOT, " (%.0f%% inside combos)", sword.comboSprintResetRate())
              : "";
      String cdPart =
          sword.earlyHits() > 0
              ? String.format(
                  Locale.ROOT, " | %d early hits (<90%% charge)", sword.earlyHits())
              : "";
      String detail =
          String.format(
              Locale.ROOT,
              "Reset sprint before %d/%d sprinting follow-ups%s%s.",
              sword.sprintResets(),
              sword.sprintResetOpportunities(),
              comboPart,
              cdPart);
      list.add(new Takeaway(tone, "Sword & Movement", headline, detail));
    } else if (a.hits.size() >= 6 && Double.isFinite(sword.earlySwingRate()) && sword.earlySwingRate() >= 25.0) {
      list.add(
          new Takeaway(
              "NEGATIVE",
              "Cooldown",
              String.format(Locale.ROOT, "Early swings on %.0f%% of hits", sword.earlySwingRate()),
              String.format(
                  Locale.ROOT,
                  "%d/%d confirmed hits landed below 90%% charge (mean %.2f).",
                  sword.earlyHits(),
                  sword.fullChargeHits() + sword.earlyHits(),
                  sword.meanHitCooldown())));
    }

    // 3. Shield / Axe punish
    if (shields.disablesInflicted() >= 1) {
      String tone =
          shields.disableConversionRate() >= 60.0
              ? "POSITIVE"
              : shields.disableConversionRate() < 50.0 ? "NEGATIVE" : "NEUTRAL";
      String headline =
          String.format(
              Locale.ROOT,
              "Converted %d/%d shield disables",
              shields.disablesConverted(),
              shields.disablesInflicted());
      StringBuilder detail = new StringBuilder();
      if (Double.isFinite(shields.meanDisablePunishDelay())) {
        detail.append(
            String.format(
                Locale.ROOT,
                "Avg %.2fs to first punish hit (%.1f hits/disable)",
                shields.meanDisablePunishDelay(),
                shields.meanPunishHitsPerDisable()));
      } else {
        detail.append("No confirmed follow-up hits within 5s of a disable");
      }
      if (shields.axeToSwordSwaps() > 0 && Double.isFinite(shields.meanAxeToSwordSeconds())) {
        detail.append(
            String.format(
                Locale.ROOT,
                " | %.2fs avg axe-to-sword swap (%d)",
                shields.meanAxeToSwordSeconds(),
                shields.axeToSwordSwaps()));
      }
      detail.append('.');
      list.add(new Takeaway(tone, "Shield & Axe", headline, detail.toString()));
    } else if (shields.localDisables() >= 1 && shields.hitsTakenAfterLocalDisable() >= 2) {
      list.add(
          new Takeaway(
              "NEGATIVE",
              "Shield Defense",
              "Punished after shield disable",
              String.format(
                  Locale.ROOT,
                  "Took %d hits (-%.1f HP) in 5s windows after %d local shield disable(s).",
                  shields.hitsTakenAfterLocalDisable(),
                  shields.damageTakenAfterLocalDisable(),
                  shields.localDisables())));
    }

    // 4. Health & damage pacing
    if (!health.trace().isEmpty() && health.totalDamageTaken() >= 4.0) {
      boolean heavyBurst = health.worstBurstDamage() >= 8.0;
      String tone = heavyBurst ? "NEGATIVE" : health.totalHealing() >= 6.0 ? "NEUTRAL" : "NEUTRAL";
      String headline =
          heavyBurst
              ? String.format(
                  Locale.ROOT, "Sharpest burst: -%.1f HP in 3s", health.worstBurstDamage())
              : health.totalHealing() >= 4.0
                  ? String.format(Locale.ROOT, "Recovered +%.1f HP across fight", health.totalHealing())
                  : String.format(
                      Locale.ROOT, "Health lost: %.1f HP (min %.1f)", health.totalDamageTaken(), health.minHp());
      String dpsPart =
          Double.isFinite(health.damageTakenPerSec())
              ? String.format(Locale.ROOT, " (%.2f HP/s)", health.damageTakenPerSec())
              : "";
      String healPart =
          health.totalHealing() > 0.5
              ? String.format(
                  Locale.ROOT,
                  ", recovered +%.1f HP (%d burst heal%s)",
                  health.totalHealing(),
                  health.burstHeals(),
                  health.burstHeals() == 1 ? "" : "s")
              : "";
      String detail =
          String.format(
              Locale.ROOT,
              "Lost %.1f HP%s%s; lowest point %.1f HP at %.1fs.",
              health.totalDamageTaken(),
              dpsPart,
              healPart,
              health.minHp(),
              health.minHpTime());
      list.add(new Takeaway(tone, "Health Pacing", headline, detail));
    }

    // 4b. Jump-reset & knockback control timing
    if (kb.eligibleHits() >= 4 && Double.isFinite(kb.rating())) {
      String tone = kb.rating() >= 65.0 ? "POSITIVE" : kb.rating() < 35.0 ? "NEGATIVE" : "NEUTRAL";
      String bias =
          kb.earlyAttempts() > kb.lateAttempts() + 1
              ? " (tending early)"
              : kb.lateAttempts() > kb.earlyAttempts() + 1 ? " (tending late)" : "";
      String redPart =
          Double.isFinite(kb.speedReductionPct())
              ? String.format(Locale.ROOT, " | %.0f%% post-hit speed reduction", kb.speedReductionPct())
              : "";
      list.add(
          new Takeaway(
              tone,
              "Jump Resets",
              String.format(Locale.ROOT, "Jump-reset timing %.0f/100", kb.rating()),
              String.format(
                  Locale.ROOT,
                  "Timed %d/%d hits (%d same-tick)%s%s.",
                  kb.idealResets() + kb.attemptResets(),
                  kb.eligibleHits(),
                  kb.idealResets(),
                  bias,
                  redPart)));
    }

    // 5. Fallback accuracy/reach takeaway for short or older fights
    if (list.size() < 3) {
      double acc = a.accuracy();
      int denom = a.hits.size() + a.misses.size();
      if (denom >= 6 && Double.isFinite(acc)) {
        String tone = acc >= 65.0 ? "POSITIVE" : acc < 45.0 ? "NEGATIVE" : "NEUTRAL";
        list.add(
            new Takeaway(
                tone,
                "Accuracy",
                String.format(Locale.ROOT, "%.0f%% accuracy (%d/%d)", acc, a.hits.size(), denom),
                String.format(
                    Locale.ROOT,
                    "Confirmed %d hits with %d non-hit attempts (longest combo %d).",
                    a.hits.size(),
                    a.misses.size(),
                    a.longestCombo())));
      }
    }

    return list.size() <= 4 ? List.copyOf(list) : List.copyOf(list.subList(0, 4));
  }

  private static double ratioPct(int num, int den) {
    return den == 0 ? Double.NaN : 100.0 * num / den;
  }
}
