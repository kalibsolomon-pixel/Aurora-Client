package dev.phenomena.gui.screens;

import dev.phenomena.PhenomenaClient;
import dev.phenomena.analytics.*;
import dev.phenomena.gui.DataCache;
import dev.phenomena.gui.components.KButton;
import dev.phenomena.gui.ui.UiState.Scope;
import java.util.*;
import net.minecraft.client.gui.components.AbstractWidget;

/** Shared pieces of the Offense, Defense and Fight Flow tabs. */
final class AnalysisSupport {
  private AnalysisSupport() {}

  static List<AbstractWidget> scopeToolbar(PhenomenaScreen screen) {
    var scopes = Scope.values();
    return List.of(
        screen.dropdown(
            "scope",
            () -> screen.state.scope.label,
            Arrays.stream(scopes).map(v -> v.label).toList(),
            () -> screen.state.scope.ordinal(),
            i -> screen.state.scope = scopes[i]));
  }

  static Aggregate aggregate(PhenomenaScreen screen) {
    return DataCache.aggregate(screen.state.scope.limit);
  }

  /** Writes the empty state and returns true when no fight is in scope. */
  static boolean empty(Content c, Aggregate a) {
    if (a.fights > 0) return false;
    c.heading("No data in scope");
    c.text("Analytics appear after the first recording.");
    return true;
  }

  static String coverage(Aggregate a) {
    return a.timelineFights == a.fights
        ? recordings(a.fights)
        : recordings(a.fights) + "; per-hit detail for " + a.timelineFights;
  }

  /** Weapon filter chips; "All weapons" plus each weapon seen in the samples. */
  static void weaponFilter(Content c, PhenomenaScreen s, Collection<String> weapons) {
    var list = new ArrayList<>(new TreeSet<>(weapons));
    if (list.size() < 2) {
      s.state.weapon = null;
      return;
    }
    if (s.state.weapon != null && !list.contains(s.state.weapon)) s.state.weapon = null;
    var chips = new ArrayList<AbstractWidget>();
    chips.add(
        new KButton(
                "weapon:all",
                KButton.Style.CHIP,
                () -> "All weapons",
                () -> {
                  s.state.weapon = null;
                  s.rebuild();
                })
            .selected(() -> s.state.weapon == null)
            .content());
    for (var w : list)
      chips.add(
          new KButton(
                  "weapon:" + w,
                  KButton.Style.CHIP,
                  () -> Interpretation.shortItem(w),
                  () -> {
                    s.state.weapon = w;
                    s.rebuild();
                  })
              .selected(() -> w.equals(s.state.weapon))
              .tooltip(w)
              .content());
    c.chips(chips);
  }

  static boolean weaponMatches(PhenomenaScreen s, String weapon) {
    return s.state.weapon == null || s.state.weapon.equals(weapon);
  }

  static boolean grid() {
    return PhenomenaClient.config.chartGrid;
  }

  static boolean points() {
    return PhenomenaClient.config.chartPoints;
  }

  static String[] fightLabels(Aggregate a) {
    String[] labels = new String[a.chronological.size()];
    for (int i = 0; i < labels.length; i++)
      labels[i] = Format.shortDate(a.chronological.get(i).startEpochMillis());
    return labels;
  }

  static String pct(int v, int total) {
    return total == 0 ? "no samples" : Format.percent(100.0 * v / total);
  }

  /** The result's source, confidence and scope as one plain sentence. */
  static String provenance(dev.phenomena.combat.fights.Resolution r) {
    String confidence = switch (r.confidence()) {
      case CONFIRMED -> "confirmed by the server";
      case OBSERVED -> "observed by the client";
      case INFERRED -> "inferred, not directly observed";
    };
    String scope = switch (r.scope()) {
      case MATCH -> "the whole match";
      case ROUND -> "this round";
      case ENGAGEMENT -> "this engagement";
    };
    boolean legacy = r.source() == dev.phenomena.combat.fights.Resolution.Source.LEGACY;
    return "Result source: " + r.source().label + ", " + confidence + ", covering " + scope
        + (r.derived() && !legacy ? "; derived from an older recording" : "") + ".";
  }

  /** Explains why per-hit detail covers fewer recordings than the totals. */
  static void timelineNote(Content c, Aggregate a) {
    int summaries = a.fights - a.timelineFights;
    if (summaries <= 0) return;
    // Aggregate reads timelines kept in history (oldest are trimmed first), not the detail cache,
    // so opening a recording elsewhere never changes these figures.
    c.note(
        "Per-hit charts and tables use the "
            + a.timelineFights
            + " recordings whose full timelines are kept; "
            + (summaries == 1 ? "the other 1 counts" : "the other " + summaries + " count")
            + " only in totals. Older timelines are dropped to save memory.");
  }

  /** "Latest fight" or "Latest manual session" for the newest analysed recording. */
  static String latestLabel(dev.phenomena.analytics.FightAnalytics latest) {
    return latest.fight.manual() ? "Latest manual session" : "Latest fight";
  }

  /** "1 recording", "2 recordings": a count that can include manual sessions. */
  static String recordings(long n) {
    return n + (n == 1 ? " recording" : " recordings");
  }

  /**
   * The result headline: a manual session has no declared outcome, so it reads "No result" rather
   * than "Unknown", which would look like a missing value.
   */
  static String resultLabel(dev.phenomena.combat.fights.FightRecord f) {
    return noResult(f) ? "No result" : f.resolution().classification().label;
  }

  private static boolean noResult(dev.phenomena.combat.fights.FightRecord f) {
    return f.manual()
        && f.resolution().classification() == dev.phenomena.combat.fights.Resolution.Classification.UNKNOWN;
  }

  /** "1 hit", "2 hits". */
  static String hits(long n) {
    return n + (n == 1 ? " hit" : " hits");
  }

  /** Context-dependent views note that older recordings lack these observations. */
  static void contextNote(Content c, Aggregate a, String what) {
    int older = a.timelineFights - a.contextFights;
    if (older > 0)
      c.note(
          older
              + " of the "
              + a.timelineFights
              + (older == 1
                  ? " recordings with timelines is older and has no "
                  : " recordings with timelines are older and have no ")
              + what
              + (older == 1 ? ". It is" : ". They are")
              + " shown as unavailable, not as zero.");
  }
}
