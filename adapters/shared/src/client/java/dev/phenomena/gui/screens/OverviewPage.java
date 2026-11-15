package dev.phenomena.gui.screens;

import static dev.phenomena.analytics.Format.*;

import dev.phenomena.PhenomenaClient;
import dev.phenomena.analytics.*;
import dev.phenomena.gui.DataCache;
import dev.phenomena.gui.FightActions;
import dev.phenomena.gui.components.*;
import dev.phenomena.gui.components.Blocks.*;
import dev.phenomena.gui.theme.Theme;
import dev.phenomena.gui.ui.UiState;
import java.util.*;
import net.minecraft.client.gui.components.AbstractWidget;

/** Recent-performance summary: headline statistics, a switchable trend chart, recent fights. */
final class OverviewPage implements PhenomenaScreen.Page {
  private final PhenomenaScreen s;

  OverviewPage(PhenomenaScreen s) {
    this.s = s;
  }

  private int window() {
    return PhenomenaClient.config.trendWindow;
  }

  @Override
  public String title() {
    return "Overview";
  }

  @Override
  public String context() {
    int total = PhenomenaClient.history.size();
    return total == 0 ? null : "Last " + Math.min(window(), total) + " of " + total + " recordings";
  }

  @Override
  public void build(Content c) {
    recording(c);
    var a = DataCache.aggregate(window());
    if (a.fights == 0) {
      c.heading("Your first recording starts here");
      c.text("Automatic capture records supported matches and nearby combat. Start a manual session to choose when recording begins and ends.");
      c.note("After a recording, explore accuracy, reach, aim, defense and combat flow. History keeps your recordings and offline reports.");
      c.text("Recordings stay on this computer. Open Pulsar with " + PhenomenaScreen.keyName() + " or /pulsar.");
      return;
    }
    var latest = PhenomenaClient.history.getFirst();
    var r = latest.resolution();
    c.heading(
        latest.manual()
            ? PhenomenaClient.recording.manual() ? "Previous manual session" : "Latest manual session"
            : "Latest fight",
        dateTime(latest.startEpochMillis())
            + (latest.manual()
                ? ""
                : " vs " + opponent(latest.opponentLabel(), latest.opponent())));
    var ls = latest.statistics();
    if (latest.manual() && ls.attempts == 0 && ls.hits == 0) {
      // An empty manual session is one line, so it never pushes real results below the fold.
      c.text(
          r.source().label
              + ", "
              + duration(latest.durationNanos())
              + ". No combat was recorded in this manual session.");
      recent(c, a);
      return;
    }
    var full = DataCache.full(latest.id());
    var fa = full == null ? null : DataCache.analytics(full);
    c.stats(
        List.of(
            new Stat(
                "Result",
                AnalysisSupport.resultLabel(latest),
                r.source().label
                    + (r.derived() && r.source() != dev.phenomena.combat.fights.Resolution.Source.LEGACY
                        ? " (older recording)"
                        : ""),
                "Result, evidence source and confidence are stored separately. Confidence: "
                    + r.confidence().name().toLowerCase(Locale.ROOT)
                    + "."),
            new Stat(
                "Duration",
                duration(latest.durationNanos()),
                ls.attempts + " attempts",
                "Recorded engagement length."),
            stat(
                "accuracy",
                percent(Aggregate.trendValue(latest, TrendMetric.ACCURACY)),
                ls.hits + " / " + ls.accuracyDenominator()),
            stat(
                "reachWithin",
                fa == null || fa.reach.eligible() == 0
                    ? NONE
                    : fa.reach.within() + " / " + fa.reach.eligible(),
                fa == null
                    ? "timeline not loaded"
                    : fa.reach.eligible() == 0 ? "no eligible hits" : "eligible hits"),
            stat("longestCombo", Integer.toString(ls.longestCombo), ls.comboCount + " combos"),
            stat(
                "dominanceShare",
                fa == null
                    ? NONE
                    : percent(fa.dominance.share(dev.phenomena.analytics.Dominance.State.LOCAL)),
                fa == null
                    ? "timeline not loaded"
                    : fa.dominance.availability.label.toLowerCase(Locale.ROOT))));
    if (fa != null) {
      var lines = dev.phenomena.analytics.Interpretation.of(fa);
      for (int i = 1; i < Math.min(5, lines.size()); i++) c.text(lines.get(i));
    }
    recent(c, a);
  }

  private void recent(Content c, Aggregate a) {
    c.heading("Recent performance", AnalysisSupport.recordings(a.fights));
    c.stats(
        List.of(
            stat(
                "accuracy",
                percent(a.accuracy()),
                a.hits + " of " + a.accuracyDenominator() + " counted"),
            stat("hits", Integer.toString(a.hits), "server-confirmed source"),
            // The eligible ray-contact hits Offense uses, so both tabs show one average.
            stat(
                "averageReach",
                meters(Arrays.stream(a.reachValues).average().orElse(Double.NaN)),
                a.reachValues.length + " eligible hits"),
            stat(
                "reachWithin",
                percent(a.reachWithinPercent()),
                a.reachWithin + " of " + a.reachEligible),
            stat(
                "criticalRate",
                percent(a.criticalRate()),
                a.criticalConditions + " with crit conditions"),
            stat("averageCombo", number(a.averageLocalCombo(), 2), "longest " + a.longestCombo),
            stat(
                "dominanceShare",
                percent(a.dominanceShare(dev.phenomena.analytics.Dominance.State.LOCAL)),
                "opponent "
                    + percent(a.dominanceShare(dev.phenomena.analytics.Dominance.State.OPPONENT))),
            stat(
                "averageInterval",
                seconds(a.averageInterval()),
                "SD " + seconds(a.intervalDeviation())),
            stat(
                "fights",
                Integer.toString(PhenomenaClient.history.size()),
                a.fights + " in this summary")));

    c.heading("Performance trend", "per recording, oldest to newest");
    var metrics = TrendMetric.values();
    var chips = new AbstractWidget[metrics.length];
    for (int i = 0; i < metrics.length; i++) {
      var m = metrics[i];
      chips[i] =
          new KButton(
                  "trend:" + m,
                  KButton.Style.CHIP,
                  m::shortLabel,
                  () -> {
                    s.state.trend = m;
                    s.rebuild();
                  })
              .selected(() -> s.state.trend == m)
              .content();
    }
    c.row(chips);
    var m = s.state.trend;
    double[] values = a.trend(m);
    String[] labels = new String[values.length];
    for (int i = 0; i < labels.length; i++)
      labels[i] = shortDate(a.chronological.get(i).startEpochMillis());
    int missing = 0;
    for (double v : values) if (!Double.isFinite(v)) missing++;
    var cfg = PhenomenaClient.config;
    c.block(
        c.chartHeight(),
        new Chart(
            List.of(new Series(m.label, null, values, Theme.SERIES_1, Mark.LINE)),
            m.min,
            m.max,
            m.unit,
            "",
            labels,
            cfg.chartGrid,
            cfg.chartPoints,
            "No " + m.label.toLowerCase(Locale.ROOT) + " samples yet"));
    String explain =
        switch (m) {
          case ACCURACY ->
              "Accuracy counts uncertain attacks as attempts, so it is a conservative lower bound.";
          case REACH -> "Average eye to attack-pick distance on confirmed hits.";
          case CRITICAL ->
              "Share of confirmed hits made with critical conditions. Critical damage is not"
                  + " verified.";
          case TIMING ->
              "Average gap between consecutive confirmed hits; shorter is not automatically"
                  + " better.";
        };
    c.text(
        explain
            + (missing > 0 ? " " + missing + " recording(s) without samples are shown as gaps." : ""));

    c.heading("Recent recordings", "select to inspect");
    var recent = PhenomenaClient.history.subList(0, Math.min(5, PhenomenaClient.history.size()));
    int rowH = 24;
    c.widget(
        new FightList(
            List.copyOf(recent),
            rowH,
            () -> null,
            id -> openDetail(id),
            id -> openDetail(id),
            id -> FightActions.viewInBrowser(DataCache.summary(id)),
            s::reveal),
        FightList.heightFor(recent.size(), rowH));
    c.row(
        new KButton("All recordings", () -> s.selectTab(UiState.Tab.HISTORY)).content(),
        new KButton("Offense", () -> s.selectTab(UiState.Tab.OFFENSE)).content(),
        new KButton("Fight Flow", () -> s.selectTab(UiState.Tab.FLOW)).content());
    c.note(
        "Hits are server-reported melee damage matched to your attacks; reach and aim are client"
            + " geometry. Results are confirmed only by an exact server announcement naming both"
            + " players; a kill message or a death alone is inferred, and an opponent's death with"
            + " no announcement stays Unknown.");
  }

  private void recording(Content c) {
    c.heading("Recording");
    c.block(12, (g, x, y, w, h, mx, my) -> {
      var r = PhenomenaClient.recording;
      String status = r.manual() ? "Manual session / " + duration(r.duration(dev.phenomena.combat.tracking.ClientCapture.now()))
          : PhenomenaClient.engine.engaged() ? "Automatic capture active" : "Ready for your next fight";
      if (!com.aurora.client.config.AuroraConfig.get().pulsarEnabled) status = "Capture is off. Enable Pulsar in Features.";
      g.text(net.minecraft.client.Minecraft.getInstance().font, KButton.fit(net.minecraft.client.Minecraft.getInstance().font, status, w), x, y, Theme.MUTED(), false);
    });
    c.row(
        new KButton(
                "Start recording",
                // The action available now carries the accent.
                PhenomenaClient.recording.manual() ? KButton.Style.NORMAL : KButton.Style.PRIMARY,
                () -> {
                  dev.phenomena.recording.RecordingCommands.start();
                  s.rebuild();
                })
            .enabled(com.aurora.client.config.AuroraConfig.get().pulsarEnabled && net.minecraft.client.Minecraft.getInstance().level != null && !PhenomenaClient.recording.manual())
            .content(),
        new KButton(
                "End recording",
                PhenomenaClient.recording.manual() ? KButton.Style.PRIMARY : KButton.Style.NORMAL,
                () -> {
                  dev.phenomena.recording.RecordingCommands.end();
                  s.rebuild();
                })
            .enabled(PhenomenaClient.recording.manual())
            .content());
    c.row(
        new KButton(
                "Recordings folder",
                () -> FightActions.openFolder(dev.phenomena.platform.SafePaths.Managed.FIGHTS))
            .content(),
        new KButton(
                "Exports folder",
                () -> FightActions.openFolder(dev.phenomena.platform.SafePaths.Managed.EXPORTS))
            .content());
  }

  private void openDetail(String id) {
    s.state.openFight(id);
    s.rebuild();
  }

  static Stat stat(String key, String value, String caption) {
    var d = MetricDefs.get(key);
    String label = key.equals("reachError") ? "Target deviation" : d.label();
    // The definition first; its evidence class closes the sentence rather than tagging it.
    return new Stat(
        label, value, caption, d.help() + " Evidence: " + d.confidence().toLowerCase(Locale.ROOT) + ".");
  }
}
