package dev.phenomena.gui.screens;

import com.mojang.blaze3d.platform.InputConstants;
import dev.phenomena.PhenomenaClient;
import dev.phenomena.analytics.Format;
import dev.phenomena.combat.fights.FightRecord;
import dev.phenomena.combat.tracking.ClientCapture;
import dev.phenomena.config.Config;
import dev.phenomena.feedback.FeedbackController;
import dev.phenomena.gui.FightActions;
import dev.phenomena.gui.Notices;
import dev.phenomena.gui.components.*;
import dev.phenomena.gui.theme.Theme;
import dev.phenomena.gui.ui.UiState.SettingsSection;
import dev.phenomena.platform.SafePaths;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;


/** Configuration grouped into sections. Changes apply immediately and save on close. */
final class SettingsPage implements PhenomenaScreen.Page {
  /** Curated feedback sounds; any resource identifier can still be typed. */
  static final String[][] SOUNDS = {
    {"XP orb pickup", "minecraft:entity.experience_orb.pickup"},
    {"Note block hat", "minecraft:block.note_block.hat"},
    {"Note block bass", "minecraft:block.note_block.bass"},
    {"Note block pling", "minecraft:block.note_block.pling"},
    {"Note block bell", "minecraft:block.note_block.bell"},
    {"Note block chime", "minecraft:block.note_block.chime"},
    {"Note block xylophone", "minecraft:block.note_block.xylophone"},
    {"Arrow hit player", "minecraft:entity.arrow.hit_player"},
    {"Button click", "minecraft:ui.button.click"}
  };

  private static final String[] GRADES = {"Perfect", "Great", "Normal", "Poor"};
  private final PhenomenaScreen s;

  SettingsPage(PhenomenaScreen s) {
    this.s = s;
  }

  private Config c() {
    return PhenomenaClient.config;
  }

  @Override
  public String title() {
    return "Settings";
  }

  @Override
  public String context() {
    return "";
  }

  @Override
  public List<String> sections() {
    return Arrays.stream(SettingsSection.values()).map(v -> v.label).toList();
  }

  @Override
  public int section() {
    return s.state.settings.ordinal();
  }

  @Override
  public void section(int index) {
    s.state.settings = SettingsSection.values()[index];
  }

  @Override
  public void build(Content c) {
    switch (s.state.settings) {
      case RECORDING -> recording(c);
      case SERVER_CAPTURE -> serverCapture(c);
      case EXCHANGES -> exchanges(c);
      case CLASSIFICATION -> classification(c);
      case AUDIO -> feedback(c);
      case APPEARANCE -> ui(c);
      case DATA -> data(c);
      case DIAGNOSTICS -> diagnostics(c);
      case CONTROLS -> controls(c);
    }
  }

  /** Boolean settings use the real switch control; narration reads the label. */
  static KToggle toggle(String id, BooleanSupplier get, Consumer<Boolean> set) {
    return new KToggle(id, get, () -> set.accept(!get.getAsBoolean())).content();
  }

  private static KSlider slider(
      String id,
      double v,
      double min,
      double max,
      double step,
      DoubleFunction<String> fmt,
      DoubleConsumer set) {
    return new KSlider(id, v, min, max, step, fmt, set);
  }

  private void recording(Content c) {
    var cfg = c();
    c.heading("Tracking");
    c.setting(
        "Automatic capture",
        "Uses configured server announcements or generic combat on other servers. Manual recording"
            + " remains available.",
        toggle(
            "Combat tracking",
            () -> cfg.tracking,
            v -> {
              if (!v && !PhenomenaClient.recording.manual())
                PhenomenaClient.engine.finish(
                    ClientCapture.now(), FightRecord.Outcome.INTERRUPTED, "Tracking paused");
              cfg.tracking = v;
            }));
    c.setting(
        "Record finished fights",
        "Saves JSON and a self-contained HTML report automatically in the background.",
        toggle("Record finished fights", () -> cfg.recording, v -> cfg.recording = v));
    c.setting(
        "Fight summary in chat",
        "One local chat line when a fight ends. Never sent to the server.",
        toggle("Fight summary in chat", () -> cfg.chatSummary, v -> cfg.chatSummary = v));
    c.heading("Retention");
    c.setting(
        "Recordings kept",
        "Maximum recordings in session history; existing files are preserved.",
        slider(
            "Recordings kept",
            cfg.retention,
            1,
            1000,
            1,
            v -> Integer.toString((int) v),
            v -> cfg.retention = (int) v));
    c.setting(
        "Storage budget",
        "Disk budget for manual saves; automatic recordings needed for recovery are always kept.",
        slider(
            "Storage budget",
            cfg.maxStorageMb,
            1,
            1024,
            1,
            v -> (int) v + " MiB",
            v -> cfg.maxStorageMb = v));
    c.note(
        "Configured matches and manual recordings never end because of inactivity. Manual recording"
            + " is always available with /pulsar start and /pulsar end.");
  }

  private void serverCapture(Content c) {
    var cfg = c();
    c.heading("Live capture status");
    c.block(Blocks.Lines.height(9), new Blocks.Lines(SettingsPage::liveStatus));
    c.heading("Capture environments", "how capture starts and ends");
    c.text(
        "Six configured servers are recognized from their announcements and measured with"
            + " server-specific adapters. Every other server uses generic combat capture: two"
            + " players, nearby melee, and a clean inactivity timeout.");
    c.setting(
        "Diagnostic capture",
        "Opt-in local evidence of server announcements; private chat is excluded by default. Export"
            + " with /pulsar diagnostics export.",
        toggle(
            "Diagnostic capture",
            () -> cfg.diagnosticCapture,
            dev.phenomena.recording.RecordingCommands::diagnostics));
    c.note(
        "Compatibility inventory: phenomena/server-compatibility.json next to the game directory."
            + " Server rules themselves are not configurable; they are part of the measurement.");
  }

  private void diagnostics(Content c) {
    c.heading("Reset");
    c.setting(
        "Reset all settings",
        "Restores defaults. Recordings are not affected.",
        new KButton(
                "Reset...",
                KButton.Style.DANGER,
                () ->
                    s.confirm(
                        "Reset settings?",
                        "Restore every Pulsar setting to its default? Recordings and exports are"
                            + " kept.",
                        "Reset",
                        () -> {
                          PhenomenaClient.resetConfig();
                          Notices.post("Settings reset to defaults", Notices.Level.OK);
                          s.rebuild();
                        }))
            .content());
    c.heading("Diagnostics");
    c.text(
        "Diagnostic capture and its export commands live in Server capture. Nothing is uploaded;"
            + " every report path in this dashboard is local.");
  }

  private void exchanges(Content c) {
    var cfg = c();
    c.heading("Two separate timeouts", "generic capture vs combat-exchange measurement");
    c.setting(
        "Generic-capture inactivity timeout",
        "Ends the whole recording when no combat is observed. Applies only to generic capture on"
            + " unconfigured servers; configured matches are never cut by inactivity.",
        slider(
            "Inactivity timeout",
            cfg.inactivitySeconds,
            2,
            30,
            .5,
            v -> Format.number(v, 1) + " s",
            v -> cfg.inactivitySeconds = v));
    c.setting(
        "Combat-exchange inactivity timeout",
        "Ends combat-exchange measurement after no qualifying interaction. The recording itself"
            + " continues, so later exchanges reopen measurement.",
        slider(
            "Combat-exchange timeout",
            cfg.exchangeInactivitySeconds,
            .5,
            30,
            .5,
            v -> Format.number(v, 1) + " s",
            v -> cfg.exchangeInactivitySeconds = v));
    c.setting(
        "Damage confirmation window",
        "How long after an attack a server damage report may confirm it.",
        slider(
            "Damage confirmation window",
            cfg.correlationMillis,
            50,
            500,
            10,
            v -> (int) v + " ms",
            v -> cfg.correlationMillis = v));
    c.setting(
        "Combo timeout",
        "Hits further apart start a new combo.",
        slider(
            "Combo timeout",
            cfg.comboSeconds,
            .5,
            10,
            .1,
            v -> Format.number(v, 1) + " s",
            v -> cfg.comboSeconds = v));
    c.setting(
        "Jump candidate window",
        "Signed client ticks around incoming damage. Candidates do not prove cancellation.",
        slider(
            "Jump window",
            cfg.jumpWindowTicks,
            0,
            10,
            1,
            v -> "\u00b1" + (int) v + " ticks",
            v -> cfg.jumpWindowTicks = (int) v));
    c.note(
        "The generic-capture timeout ends recordings; the exchange timeout only pauses combat"
            + " measurement. Configured matches and manual recordings ignore both.");
  }

  private void classification(Content c) {
    var cfg = c();
    c.heading("Reach and timing thresholds", "frozen into each new recording");
    c.setting(
        "Target reach",
        "Eye to the attack pick's hitbox contact, in blocks.",
        slider(
            "Target reach",
            cfg.perfectReachTarget,
            .1,
            6,
            .01,
            v -> Format.number(v, 2) + " blocks",
            v -> cfg.perfectReachTarget = v));
    c.setting(
        "Reach tolerance",
        "Inclusive window around target reach.",
        slider(
            "Reach tolerance",
            cfg.perfectReachTolerance,
            0,
            .5,
            .01,
            v -> "\u00b1" + Format.number(v, 2),
            v -> cfg.perfectReachTolerance = v));
    c.setting(
        "Timing tolerance",
        "Quantization allowance around one effective cooldown.",
        slider(
            "Timing tolerance",
            cfg.timingToleranceTicks,
            0,
            2,
            .25,
            v -> "\u00b1" + Format.number(v, 2) + " ticks",
            v -> cfg.timingToleranceTicks = v));
    c.note(
        "Thresholds are saved with every recording, so changing them later never reinterprets an"
            + " earlier one. Reach is client geometry; a hit beyond the target is not"
            + " evidence of a rule violation.");
  }

  private void controls(Content c) {
    c.heading("Dashboard key");
    c.text("The dashboard uses Aurora's key binding system, including Ctrl, Shift and Alt combinations.");
    c.row(new KButton("change-key", KButton.Style.NORMAL,
        () -> "Change key (now " + PhenomenaScreen.keyName() + ")", () -> {
      var meta = com.aurora.client.screen.FeatureRegistry.modules().stream().filter(m -> m.id.equals("pulsar")).findFirst().orElseThrow();
      com.aurora.client.adapter.NativeUi.setScreen(net.minecraft.client.Minecraft.getInstance(), new com.aurora.client.screen.FeatureDetailScreen(s, meta));
    }).content());
    c.heading("In this window");
    c.text("1\u20136 switch tabs. Tab and Shift+Tab move focus; Enter or Space activates. Arrow keys read measurements, adjust sliders and change list selection. Page Up/Down and Home/End scroll. Escape dismisses the current dialog, then returns to the previous screen.");
  }

  private void feedback(Content c) {
    var cfg = c();
    c.heading("Hit sounds");
    c.setting(
        "Play hit sounds",
        "Perfect-reach sound after source-confirmed melee hits; at most one per 120 ms.",
        toggle("Play hit sounds", () -> cfg.sound, v -> cfg.sound = v));
    var channels = Config.SoundChannel.values();
    c.setting(
        "Sound category",
        "Minecraft volume slider that controls these sounds.",
        s.dropdown(
                "channel",
                () -> channel(cfg.soundChannel),
                Arrays.stream(channels).map(SettingsPage::channel).toList(),
                () -> cfg.soundChannel.ordinal(),
                i -> cfg.soundChannel = channels[i])
            .content());
    c.setting(
        "Perfect-reach sound",
        "Uses the Perfect sound below, independently of timing.",
        toggle("Perfect reach sound", () -> cfg.perfectReachSound, v -> cfg.perfectReachSound = v));
    c.heading("Sound styles");
    c.text("Perfect is used for perfect reach. Other styles are available for preview.");
    var chips = new KButton[4];
    for (int i = 0; i < 4; i++) {
      int g = i;
      chips[i] =
          new KButton(
                  "grade:" + i,
                  KButton.Style.CHIP,
                  () -> GRADES[g],
                  () -> {
                    s.state.feedbackGrade = g;
                    s.rebuild();
                  })
              .selected(() -> s.state.feedbackGrade == g)
              .content();
    }
    c.row(chips);
    int g = s.state.feedbackGrade;
    var style = cfg.feedback[g];
    c.setting(
        GRADES[g] + " sound",
        "Plays for hits in this category.",
        toggle(GRADES[g] + " sound", () -> style.audio, v -> style.audio = v));
    List<String> names = new ArrayList<>();
    for (var sound : SOUNDS) names.add(sound[0]);
    names.add("Custom identifier");
    IntSupplier current =
        () -> {
          for (int i = 0; i < SOUNDS.length; i++) if (SOUNDS[i][1].equals(style.sound)) return i;
          return SOUNDS.length;
        };
    c.setting(
        "Sound",
        "Choose a preset, or type any sound resource identifier below.",
        s.dropdown(
                "sound",
                () -> names.get(current.getAsInt()),
                names,
                current,
                i -> {
                  if (i < SOUNDS.length) style.sound = SOUNDS[i][1];
                })
            .content());
    var id = new KTextField("Sound identifier", style.sound, 128);
    id.setResponder(v -> style.sound = v);
    c.setting("Sound identifier", "Unknown identifiers are silent.", id);
    c.setting(
        "Volume",
        null,
        slider(
            GRADES[g] + " volume",
            style.volume,
            0,
            1,
            .05,
            v -> Math.round(v * 100) + "%",
            v -> style.volume = (float) v));
    c.setting(
        "Pitch",
        null,
        slider(
            GRADES[g] + " pitch",
            style.pitch,
            .5,
            2,
            .05,
            v -> Format.number(v, 2),
            v -> style.pitch = (float) v));
    c.row(
        new KButton("Preview " + GRADES[g], () -> FeedbackController.preview(g))
            .tooltip("Plays at the configured category and volume")
            .content());
    c.color(
        GRADES[g] + " color",
        "Used for this category in charts.",
        () -> style.color,
        v -> style.color = v);
    c.setting(
        "Per-hit chat line",
        "Local, rate limited to two per second.",
        toggle("Per-hit chat line", () -> cfg.perHitChat, v -> cfg.perHitChat = v));
    c.note(
        "Perfect reach and perfect timing are separate. Openers have no measured interval. Legacy"
            + " readiness thresholds are retained only for historical records.");
  }

  private void data(Content c) {
    var cfg = c();
    c.heading("Export and browser reports");
    c.setting(
        "Anonymize reports",
        "Replaces the opponent's ID and name with \"Opponent\" in exports and browser views.",
        toggle("Anonymize reports", () -> cfg.anonymizeExports, v -> cfg.anonymizeExports = v));
    c.setting(
        "Include motion traces",
        "10 Hz velocity samples. Off makes reports smaller.",
        toggle("Include motion traces", () -> cfg.exportMotion, v -> cfg.exportMotion = v));
    c.setting(
        "Open after export",
        "Export also opens the report in your browser.",
        toggle("Open after export", () -> cfg.openAfterExport, v -> cfg.openAfterExport = v));
    c.heading("Folders", "inside the Minecraft game directory");
    c.row(
        new KButton(
                "Recordings folder", () -> FightActions.openFolder(SafePaths.Managed.FIGHTS))
            .content(),
        new KButton("Exports folder", () -> FightActions.openFolder(SafePaths.Managed.EXPORTS))
            .content());
    c.text(
        "Recordings: phenomena/fights. Reports: phenomena/exports/Fight vs <opponent> <date"
            + " time>/index.html (shared name omitted while anonymizing). Local recording folders"
            + " always retain a known opponent. Reports work offline, make no network requests, and"
            + " are never uploaded.");
    c.heading("Standalone settings");
    c.setting("Import Phenomena settings", "Reads config/phenomena.json or config/kwamster.json. Aurora keeps its theme, key and feature state. The original file is preserved.",
        new KButton("Import...", () -> s.confirm("Import settings?",
            "Replace Pulsar measurement, recording and feedback settings with your standalone settings? Your source file and recordings are kept.",
            "Import", PhenomenaClient::importStandaloneSettings)).content());
  }

  private void ui(Content c) {
    var cfg = c();
    c.heading("Appearance");
    c.text("Pulsar follows Aurora's theme, font and compact controls. Change these in Aurora Settings, Appearance.");
    c.setting(
        "Reduced motion",
        "Reduces motion in offline reports.",
        toggle("Reduced motion", () -> cfg.reducedMotion, v -> cfg.reducedMotion = v));
    c.heading("Charts");
    c.setting(
        "Point markers",
        "Markers on line charts with up to 120 points.",
        toggle(
            "Point markers",
            () -> cfg.chartPoints,
            v -> {
              cfg.chartPoints = v;
              s.rebuild();
            }));
    c.setting(
        "Grid lines",
        null,
        toggle(
            "Grid lines",
            () -> cfg.chartGrid,
            v -> {
              cfg.chartGrid = v;
              s.rebuild();
            }));
    c.setting(
        "Overview window",
        "Recordings included in the Overview summary and trend.",
        slider(
            "Overview window",
            cfg.trendWindow,
            5,
            100,
            1,
            v -> (int) v + " recordings",
            v -> cfg.trendWindow = (int) v));
    c.note(
        "The standalone mod's persistent in-game HUD is retired; this dashboard replaces it. Hit sounds"
            + " remain available.");
  }

  /** An enum constant as words: GENERIC_COMBAT reads "Generic combat". */
  private static String words(Enum<?> value) {
    String s = value.name().replace('_', ' ').toLowerCase(Locale.ROOT);
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  /** Live capture state, one row per fact; read again on every frame and narration. */
  private static List<String> liveStatus() {
    var r = PhenomenaClient.recording;
    String mode = r.manual() ? "Manual session active" : words(r.state());
    return List.of(
      "Recording: " + mode + " / " + Format.duration(r.duration(dev.phenomena.combat.tracking.ClientCapture.now())),
      "Server: "
          + Objects.toString(r.server(), "No world")
          + " / Opponent: "
          + Objects.toString(r.opponent(), "Unknown"),
      "Automatic capture: "
          + (PhenomenaClient.config.tracking ? "Enabled" : "Disabled")
          + " / Policy: "
          + words(PhenomenaClient.engine.capturePolicy().mode()),
      "Environment: "
          + (PhenomenaClient.engine.capturePolicy().environment() == null
              ? "Generic combat"
              : PhenomenaClient.engine.capturePolicy().environment().name()
                  + " / adapter v"
                  + PhenomenaClient.engine.capturePolicy().environment().version()),
      "Match: "
          + (PhenomenaClient.engine.engaged() ? words(PhenomenaClient.engine.state()) : "Idle")
          + " / Round: "
          + Objects.toString(PhenomenaClient.engine.currentRound(), "Unknown")
          + " / Manual: "
          + (r.manual() ? "Active" : "Off"),
      "Diagnostics: " + (PhenomenaClient.diagnostics.enabled() ? "Enabled" : "Off"),
      "Capture rule: " + PhenomenaClient.engine.captureLimitation(),
      "Export: "
          + (r.lastSaved() == null
              ? "No recording saved this run"
              : PhenomenaClient.autoReports.availability(r.lastSaved())),
      "Last saved: "
          + PhenomenaClient.history.stream()
              .filter(f -> f.id().equals(r.lastSaved()))
              .findFirst()
              .map(f -> dev.phenomena.platform.SafePaths.exportName(f, false))
              .orElse("None"));
  }

  static String channel(Config.SoundChannel c) {
    return switch (c) {
      case UI -> "Interface (UI)";
      case PLAYERS -> "Players";
      case MASTER -> "Master";
    };
  }

  static String title(String s) {
    return s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT);
  }
}
