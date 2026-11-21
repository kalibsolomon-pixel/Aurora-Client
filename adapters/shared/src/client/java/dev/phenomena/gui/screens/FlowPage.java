package dev.phenomena.gui.screens;

import static dev.phenomena.analytics.Format.*;
import static dev.phenomena.gui.screens.AnalysisSupport.*;
import static dev.phenomena.gui.screens.OverviewPage.stat;

import dev.phenomena.analytics.*;
import dev.phenomena.combat.metrics.Combos;
import dev.phenomena.gui.components.*;
import dev.phenomena.gui.components.Blocks.*;
import dev.phenomena.gui.theme.Theme;
import dev.phenomena.gui.ui.UiState.FlowSection;
import java.util.*;
import net.minecraft.client.gui.components.AbstractWidget;

/** Fight Flow: combos, encounter dominance and exchanges for one focused fight plus the scope. */
final class FlowPage implements PhenomenaScreen.Page {
  private final PhenomenaScreen s;

  FlowPage(PhenomenaScreen s) {
    this.s = s;
  }

  @Override
  public String title() {
    return "Fight Flow";
  }

  @Override
  public List<String> sections() {
    return Arrays.stream(FlowSection.values()).map(v -> v.label).toList();
  }

  @Override
  public int section() {
    return s.state.flow.ordinal();
  }

  @Override
  public void section(int index) {
    s.state.flow = FlowSection.values()[index];
  }

  @Override
  public List<AbstractWidget> toolbar(PhenomenaScreen screen) {
    return scopeToolbar(screen);
  }

  @Override
  public void build(Content c) {
    var a = aggregate(s);
    if (empty(c, a)) return;
    if (a.timelines.isEmpty()) {
      // Flow reads timelines kept in history; opening a recording in History loads only its own detail.
      c.note(
          "Fight Flow needs a recording whose full timeline is kept. Older recordings keep only"
              + " summaries here; open one in History to see its own detail.");
      return;
    }
    int n = a.timelines.size();
    s.state.focus(s.state.focus, n);
    var f = a.timelines.get(n - 1 - s.state.focus);
    focusRow(c, f, n);
    if (f.fight.manual()) {
      c.note(
          "Manual session: select a participant and exchange in the browser report for combos and"
              + " dominance. Session-wide dominance is unavailable.");
      for (var ex : f.fight.session().exchanges())
        c.text(
            opponent(ex.opponentLabel(), ex.opponent())
                + " / "
                + duration(ex.durationNanos())
                + " / "
                + ex.resolution().classification().label);
      c.row(
          new KButton(
                  "View session in browser",
                  () -> dev.phenomena.gui.FightActions.viewInBrowser(f.fight))
              .content());
      return;
    }
    switch (s.state.flow) {
      case COMBOS -> combos(c, a, f);
      case DOMINANCE -> dominance(c, a, f);
      case EXCHANGES -> exchanges(c, f);
    }
  }

  private void focusRow(Content c, FightAnalytics f, int n) {
    c.row(
        new KButton(
                "Older recording",
                () -> {
                  s.state.focus(s.state.focus + 1, n);
                  s.rebuild();
                })
            .enabled(s.state.focus < n - 1)
            .tooltip("Show the previous recording with a timeline")
            .content(),
        new KButton(
                "Newer recording",
                () -> {
                  s.state.focus(s.state.focus - 1, n);
                  s.rebuild();
                })
            .enabled(s.state.focus > 0)
            .tooltip("Show the next recording with a timeline")
            .content());
    // Two short lines: which recording, then how it ended; neither wraps a lone word.
    c.text(
        "Recording "
            + (n - s.state.focus)
            + " of "
            + n
            + " with timelines: "
            + dateTime(f.fight.startEpochMillis())
            + (f.fight.manual()
                ? ", manual session"
                : " vs " + opponent(f.fight.opponentLabel(), f.fight.opponent())));
    c.text(resultLabel(f.fight) + ", " + duration(f.fight.durationNanos()));
  }

  static FlowCharts.ComboTimeline timeline(FightAnalytics f, int selected) {
    List<FlowCharts.Mark> marks = new ArrayList<>();
    for (var m : f.combos.marks())
      marks.add(
          new FlowCharts.Mark(
              f.time(m.nanos()),
              m.side() == Combos.Side.LOCAL,
              m.traded(),
              (m.side() == Combos.Side.LOCAL ? "Your confirmed hit" : "Attributed opponent hit")
                  + " at "
                  + number(f.time(m.nanos()), 2)
                  + " s"
                  + (m.traded() ? " (same-tick trade)" : "")));
    List<FlowCharts.Span> spans = new ArrayList<>();
    for (var st : f.allCombos())
      spans.add(
          new FlowCharts.Span(
              f.time(st.startNanos()),
              f.time(st.endNanos()),
              st.side() == Combos.Side.LOCAL,
              st.length(),
              (st.side() == Combos.Side.LOCAL ? "Your combo: " : "Opponent combo: ")
                  + st.length()
                  + " hits in "
                  + number((st.endNanos() - st.startNanos()) / 1e9, 2)
                  + " s, ended by "
                  + endWord(st.endedBy())));
    List<Double> bounds = new ArrayList<>();
    for (var ex : f.combos.exchanges()) bounds.add(f.time(ex.startNanos()));
    String note =
        switch (f.opponentComboAvailability()) {
          case "Observed" -> null;
          case "Estimated" ->
              "Some incoming damage was unattributed: opponent combos are estimates.";
          default -> "Opponent hits could not be attributed: opponent combos are unavailable.";
        };
    return new FlowCharts.ComboTimeline(f.duration, marks, spans, bounds, selected, note);
  }

  static String endWord(Combos.End e) {
    return switch (e) {
      case RETALIATION -> "a return hit";
      case TRADE -> "a same-tick trade";
      case DISENGAGE -> "disengagement";
      case ROUND_BOUNDARY -> "a round boundary";
      case RECORDING_END -> "the end of the recording";
    };
  }

  private void combos(Content c, Aggregate a, FightAnalytics f) {
    c.heading("Combos in scope", coverage(a));
    c.stats(
        List.of(
            stat("longestCombo", Integer.toString(a.longestCombo), "your best"),
            stat(
                "averageCombo", number(a.averageLocalCombo(), 2), a.localCombos.size() + " combos"),
            stat("comboHits", Integer.toString(a.comboHits), "of " + hits(a.hits)),
            stat("longestUnanswered", Integer.toString(a.longestUnanswered), "ignores gaps"),
            stat(
                "longestOpponentCombo",
                Integer.toString(a.longestOpponentCombo),
                a.unattributedIncoming > 0 ? "estimated" : "attributed hits")));
    var all = f.allCombos();
    if (s.state.combo >= all.size()) s.state.combo = -1;
    c.heading("Combo timeline", "focused recording; x = combo length");
    c.block(FlowCharts.ComboTimeline.height(), timeline(f, s.state.combo));
    c.row(
        new KButton(
                "Previous combo",
                () -> {
                  s.state.combo = s.state.combo <= 0 ? all.size() - 1 : s.state.combo - 1;
                  s.rebuild();
                })
            .enabled(!all.isEmpty())
            .tooltip("Select the previous combo")
            .content(),
        new KButton(
                "Next combo",
                () -> {
                  s.state.combo = s.state.combo >= all.size() - 1 ? 0 : s.state.combo + 1;
                  s.rebuild();
                })
            .enabled(!all.isEmpty())
            .tooltip("Select the next combo")
            .content());
    if (s.state.combo >= 0) {
      var st = all.get(s.state.combo);
      c.heading(
          (st.side() == Combos.Side.LOCAL ? "Your combo " : "Opponent combo ")
              + (s.state.combo + 1)
              + " of "
              + all.size(),
          st.length() + " hits / " + number((st.endNanos() - st.startNanos()) / 1e9, 2) + " s");
      List<String[]> rows = new ArrayList<>();
      Map<Long, FightAnalytics.Hit> hits = new HashMap<>();
      for (var h : f.hits) hits.put(h.attackSequence(), h);
      Map<Long, FightAnalytics.Incoming> incoming = new HashMap<>();
      for (var in : f.incoming) incoming.put(in.sequence(), in);
      int k = 1;
      for (long seq : st.sequences()) {
        var h = hits.get(seq);
        var in = incoming.get(seq);
        rows.add(
            h != null
                ? new String[] {
                  "#" + k++,
                  seconds(h.evidenceTime()),
                  "Your hit",
                  meters(h.reach()),
                  Interpretation.shortItem(h.weapon()),
                  Boolean.TRUE.equals(h.serverCrit())
                      ? "server crit"
                      : h.criticalConditions() ? "crit conditions" : NONE
                }
                : new String[] {
                  "#" + k++,
                  in == null ? NONE : seconds(in.time()),
                  "Opponent hit",
                  NONE,
                  in == null || in.damageType() == null
                      ? NONE
                      : Interpretation.shortItem(in.damageType()),
                  in != null && in.crit() ? "crit on you" : NONE
                });
      }
      c.block(
          Table.height(rows.size()),
          new Table(
              new String[] {"Hit", "Time", "Side", "Reach", "Weapon / type", "Critical"},
              new float[] {.6f, .9f, 1.2f, .9f, 1.6f, 1.3f},
              new boolean[] {false, true, false, true, false, false},
              rows));
      c.text("Ended by " + endWord(st.endedBy()) + ".");
    }
    c.note(
        "A combo is two or more consecutive confirmed hits by one side with no confirmed hit by the"
            + " other in between. It ends on an attributed return hit, a same-tick trade (order"
            + " unknown, so neither side's combo is extended), or a gap longer than the combo"
            + " timeout ("
            + number(f.fight.policy().comboSeconds(), 1)
            + " s). Empty swings, shield blocks, knockback and unattributed damage never extend or"
            + " end a combo.");
  }

  static FlowCharts.DominanceChart chart(FightAnalytics f) {
    var d = f.dominance;
    char[] states = new char[d.states.length];
    for (int i = 0; i < states.length; i++) states[i] = d.states[i].code;
    List<double[]> combos = new ArrayList<>();
    for (var st : f.allCombos())
      if (st.side() == Combos.Side.LOCAL)
        combos.add(new double[] {f.time(st.startNanos()), f.time(st.endNanos())});
    double[] local =
        f.combos.marks().stream()
            .filter(m -> m.side() == Combos.Side.LOCAL)
            .mapToDouble(m -> f.time(m.nanos()))
            .toArray();
    double[] opp =
        f.combos.marks().stream()
            .filter(m -> m.side() == Combos.Side.OPPONENT)
            .mapToDouble(m -> f.time(m.nanos()))
            .toArray();
    List<Double> bounds = new ArrayList<>();
    for (var b : f.boundaries)
      if (b.accepted()
          && (b.kind().equals("START")
              || b.kind().equals("ROUND_START")
              || b.kind().equals("RESULT"))) bounds.add(b.time());
    return new FlowCharts.DominanceChart(
        f.duration, d.step, d.values, states, local, opp, combos, bounds, "No confirmed exchanges");
  }

  private void dominance(Content c, Aggregate a, FightAnalytics f) {
    var d = f.dominance;
    c.heading(
        "Encounter dominance",
        d.availability == Dominance.Availability.AVAILABLE
            ? "focused recording"
            : "focused recording; " + d.availability.label.toLowerCase(Locale.ROOT));
    c.block(FlowCharts.DominanceChart.height(c.compact), chart(f));
    if (d.availability != Dominance.Availability.AVAILABLE)
      c.note(
          d.availability == Dominance.Availability.INCOMPLETE
              ? "Incoming-hit attribution was incomplete, so encounter dominance could not be"
                  + " established confidently. Dotted grey columns mark where unattributed damage"
                  + " was observed; they are not counted for either player."
              : "Too few confirmed exchanges were observed to estimate encounter dominance.",
          Theme.WARN());
    c.stats(
        List.of(
            stat(
                "dominanceShare",
                percent(d.share(Dominance.State.LOCAL)),
                "you, of " + secondsShort(d.activeSeconds) + " active"),
            new Stat(
                "Opponent advantage",
                percent(d.share(Dominance.State.OPPONENT)),
                "of active time",
                "Share of observed active combat time with dominance at or below -0.2."),
            new Stat(
                "Neutral",
                percent(d.share(Dominance.State.NEUTRAL)),
                "balanced",
                "Evidence present, between -0.2 and +0.2."),
            new Stat(
                "Longest advantage",
                d.longestAdvantage == null ? NONE : secondsShort(d.longestAdvantage.length()),
                d.longestAdvantage == null
                    ? "none"
                    : (d.longestAdvantage.side() == Dominance.State.LOCAL ? "you" : "opponent")
                        + " from "
                        + secondsShort(d.longestAdvantage.start()),
                "Longest continuous period on one side."),
            new Stat(
                "Largest swing",
                d.largestSwing == null
                    ? NONE
                    : number(d.largestSwing.toValue() - d.largestSwing.fromValue(), 2),
                d.largestSwing == null
                    ? "none"
                    : "within 3 s at " + secondsShort(d.largestSwing.from()),
                "Largest change of the dominance value within a 3-second window; negative moves"
                    + " toward the opponent."),
            new Stat(
                "Dominance changes",
                Integer.toString(d.changes),
                "you <-> opponent",
                "Passes from one side's advantage to the other's."),
            new Stat(
                "No evidence / incomplete",
                secondsShort(d.insufficientSeconds) + " / " + secondsShort(d.incompleteSeconds),
                "excluded from shares",
                "Inactive time and time with unattributed incoming damage.")));
    c.heading("In scope", coverage(a));
    c.text(
        "Pooled active time favoring you "
            + percent(a.dominanceShare(Dominance.State.LOCAL))
            + ", the opponent "
            + percent(a.dominanceShare(Dominance.State.OPPONENT))
            + ", neutral "
            + percent(a.dominanceShare(Dominance.State.NEUTRAL))
            + ". "
            + a.dominanceAvailability[0]
            + " recordings available, "
            + a.dominanceAvailability[1]
            + " incomplete, "
            + a.dominanceAvailability[2]
            + " insufficient.");
    c.note(
        "Model: each confirmed hit weighs 1 (1.25 when it continues a combo), shield events"
            + " 0.25-0.5, all decaying with a 3 s time constant. D = (you - opponent) / (you +"
            + " opponent + 1), so thin evidence stays near zero and one event can reach at most"
            + " 0.5. Accuracy, aim and reach are not inputs. It describes combat advantage from"
            + " observed hits, not physical momentum and not a win probability.");
  }

  private void exchanges(Content c, FightAnalytics f) {
    var br = f.breakdown;
    var es = br.exchangeSummary;
    c.heading(
        "Exchange openers & trades",
        es.contested() + " contested / " + number(f.combatActiveSeconds > 0 ? f.combatActiveSeconds : f.duration, 1) + " s active");
    c.stats(
        List.of(
            stat(
                "firstHitRate",
                percent(es.firstHitRate()),
                es.openedByYou() + " / " + es.contested() + " (" + es.openedByTrade() + " trades)"),
            stat(
                "firstHitConversionRate",
                percent(es.firstHitConversionRate()),
                es.openersConverted() + " / " + es.openedByYou() + " into combos"),
            new Stat(
                "Exchanges W / L / E",
                es.won() + " / " + es.lost() + " / " + es.even(),
                "by hit differential",
                "Contested exchanges where you landed more, fewer, or equal hits vs attributed"
                    + " opponent hits."),
            stat(
                "tradeRatio",
                Double.isFinite(es.tradeRatio()) ? number(es.tradeRatio(), 2) + "x" : NONE,
                f.hits.size() + " dealt / " + f.attributedIncoming + " taken")));
    if (f.fight.statistics().measurementVersion > 0) {
      c.block(48, new FlowCharts.ExchangeTimeline(f.duration, f.activeExchanges));
    }
    if (!br.exchanges.isEmpty()) {
      c.heading("Exchange breakdown", "opener, hits, HP delta and winner");
      List<String[]> rows = new ArrayList<>();
      for (var e : br.exchanges)
        rows.add(
            new String[] {
              "#" + e.index(),
              number(e.start(), 1) + "-" + number(e.end(), 1) + "s",
              OffensePage.title(e.opener()),
              e.openerConverted() ? "Yes" : NONE,
              e.localHits() + "/" + e.opponentHits(),
              (e.hitDiff() > 0 ? "+" : "") + e.hitDiff(),
              e.damageTaken() > 0 ? "-" + number(e.damageTaken(), 1) : NONE,
              e.healingGained() > 0 ? "+" + number(e.healingGained(), 1) : NONE,
              OffensePage.title(e.winner())
            });
      c.block(
          Table.height(rows.size()),
          new Table(
              new String[] {"#", "Time", "Opener", "Conv", "Hits", "Diff", "Lost", "Heal", "Win"},
              new float[] {.5f, 1.4f, 1.0f, .6f, .9f, .7f, .8f, .8f, .9f},
              new boolean[] {false, true, false, false, true, true, true, true, false},
              rows));
    }
    if (!br.turningPoints.isEmpty()) {
      c.heading("Key turning points");
      List<String[]> trows = new ArrayList<>();
      for (var tp : br.turningPoints)
        trows.add(new String[] {seconds(tp.time()), tp.title(), tp.detail()});
      c.block(
          Table.height(trows.size()),
          new Table(
              new String[] {"Time", "Moment", "Evidence"},
              new float[] {.8f, 1.8f, 2.8f},
              new boolean[] {true, false, false},
              trows));
    }
    if (f.fight.statistics().measurementVersion > 0) {
      c.block(FlowCharts.ComboTimeline.height(), timeline(f, -1));
      c.block(FlowCharts.DominanceChart.height(c.compact), chart(f));
      c.text("Finalization: " + f.fight.reason());
      return;
    }
    c.heading("Interpretation");
    for (var line : Interpretation.of(f)) c.text(line);
  }

  static String secondsShort(double v) {
    return Double.isFinite(v) ? number(v, 1) + " s" : NONE;
  }
}
