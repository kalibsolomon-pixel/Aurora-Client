package dev.phenomena;

import com.aurora.client.adapter.NativeUi;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.diagnostics.Timed;
import com.aurora.client.pulsar.PulsarConfigBridge;
import com.aurora.client.util.AuroraKey;
import dev.phenomena.combat.fights.FightRecord;
import dev.phenomena.combat.tracking.*;
import dev.phenomena.config.Config;
import dev.phenomena.feedback.FeedbackController;
import dev.phenomena.gui.screens.PhenomenaScreen;
import dev.phenomena.recording.*;
import dev.phenomena.recording.storage.*;
import dev.phenomena.sharing.ReportExporter;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.*;

/** Aurora owns enablement and preferences; the original engine owns combat and recording. */
public final class PhenomenaClient {
  public static final Logger LOG = LoggerFactory.getLogger("aurora-pulsar");
  // Controllers retain this instance. Profile application reloads its fields through the bridge.
  public static final Config config = new Config();
  public static CombatEngine engine;
  public static FightStore store;
  public static AutoReports autoReports;
  public static RecordingController recording;
  public static SessionRecovery recovery;
  public static Diagnostics diagnostics;
  public static Path dataDir;
  public static String status = "Local by default / Enable Pulsar in Aurora";
  public static final List<FightRecord> history = new ArrayList<>();
  public static long historyVersion;
  public static final ExportNotifications exportNotifications =
      new ExportNotifications(System::nanoTime);

  private static final AuroraKey.EdgeDetector OPEN_KEY = new AuroraKey.EdgeDetector();
  private static boolean registered, initialized, enabledLastTick, stopping;
  private static final int MAX_RECOVERED_SESSIONS = 128;
  private static final long RECOVERY_BYTES = FightStore.MAX_RECORDING_BYTES;

  private PhenomenaClient() {}

  /** Called after Aurora applies its profile; repeated calls never register duplicate callbacks. */
  public static void initialize() {
    PulsarConfigBridge.reload(config);
    if (!registered) {
      registered = true;
      RecordingCommands.register();
      ClientTickEvents.START_CLIENT_TICK.register(
          Timed.startTick(
              client -> {
                if (synchronizeEnabled()) ClientCapture.startTick();
              }));
      ClientTickEvents.END_CLIENT_TICK.register(
          Timed.endTick(
              client -> {
                boolean enabled = synchronizeEnabled();
                boolean open = OPEN_KEY.justPressed(AuroraConfig.get().pulsarKey);
                if (enabled) {
                  ClientCapture.tick();
                  diagnostics.tick(ClientCapture.now());
                  if (open) openDashboard(NativeUi.screen(client));
                }
                // Disabling capture must not strand already accepted durable writes or exports.
                if (initialized && !stopping) {
                  autoReports.tick(ClientCapture.now());
                  exportNotifications.tick(client.player != null, PhenomenaClient::notice);
                }
              }));
      // A world replacement or a temporarily missing player is not a disconnection.
      ClientPlayConnectionEvents.DISCONNECT.register(
          (handler, client) -> {
            if (synchronizeEnabled()) ClientCapture.disconnected();
            else ClientCapture.reset();
          });
      ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdown());
    }
    synchronizeEnabled();
  }

  public static boolean isInitialized() {
    return initialized && !stopping;
  }

  /** Explicit dashboard access may load local history even while capture is disabled. */
  public static void ensureReady() {
    PulsarConfigBridge.reload(config);
    if (initialized || stopping) return;
    config.validate();
    var loader = FabricLoader.getInstance();
    // Keep standalone history in place. Never import or rewrite standalone configuration.
    dataDir = loader.getGameDir().resolve("phenomena");
    store = new FightStore(dataDir.resolve("fights"), PhenomenaClient::storageError);
    autoReports =
        new AutoReports(
            store,
            dataDir,
            message -> {
              LOG.info("{}", message);
              Minecraft.getInstance()
                  .execute(
                      () -> {
                        status = message;
                        if (message.contains("failed") || message.contains("exhausted"))
                          notice(message);
                      });
            },
            ReportExporter::export,
            exportNotifications::offer);
    recovery = new SessionRecovery(store, dataDir, PhenomenaClient::notice);
    diagnostics = new Diagnostics(store, dataDir, PhenomenaClient::notice);
    installEngine();
    diagnostics.policy(() -> engine.capturePolicy(), () -> engine.captureLimitation());
    diagnostics.enabled(AuroraConfig.get().pulsarEnabled && config.diagnosticCapture);
    initialized = true;

    // One worker orders the non-destructive legacy copy before loading and report recovery.
    store.submit(() -> LegacyDataMigration.migrate(loader.getGameDir(), msg -> LOG.info("{}", msg)));
    store.submit(
        () -> {
          try {
            Path inventory = dataDir.resolve("server-compatibility.json");
            if (!Files.exists(inventory, LinkOption.NOFOLLOW_LINKS))
              AtomicJson.write(inventory, Announcements.adapters());
          } catch (IOException ex) {
            notice("Compatibility inventory could not be saved: " + ex.getMessage());
          }
        });
    var options = reportOptions();
    store.load(
        loaded -> {
          var fights = recoverBounded(loaded);
          autoReports.recover(fights, options);
          Minecraft.getInstance()
              .execute(
                  () -> {
                    Set<String> ids = new HashSet<>();
                    for (var fight : history) ids.add(fight.id());
                    for (var fight : fights) if (ids.add(fight.id())) history.add(fight);
                    history.sort(Comparator.comparingLong(FightRecord::startEpochMillis).reversed());
                    trimHistory();
                    historyChanged();
                  });
        });
    LOG.info("Pulsar runtime ready; local storage {}", dataDir);
  }

  public static void openDashboard(Screen parent) {
    if (stopping) return;
    ensureReady();
    if (!AuroraConfig.get().pulsarEnabled)
      notice("Pulsar capture is disabled. Enable the Pulsar feature in Aurora to record.");
    NativeUi.setScreen(Minecraft.getInstance(), new PhenomenaScreen(parent));
  }

  private static boolean synchronizeEnabled() {
    if (stopping) return false;
    if (initialized) PulsarConfigBridge.reload(config);
    boolean enabled = AuroraConfig.get().pulsarEnabled;
    if (enabled) {
      PulsarConfigBridge.reload(config);
      ensureReady();
      if (!enabledLastTick) ClientCapture.reset();
      if (diagnostics.enabled() != config.diagnosticCapture)
        diagnostics.enabled(config.diagnosticCapture);
    } else if (enabledLastTick) {
      interrupt("Pulsar disabled");
      diagnostics.enabled(false); // flush once on the transition, never each disabled tick
      ClientCapture.reset();
    }
    enabledLastTick = enabled;
    return enabled && isInitialized();
  }

  private static void interrupt(String reason) {
    if (!initialized) return;
    if (recording.manual() || engine.engaged())
      recording.shutdown(ClientCapture.now(), ClientCapture.clientTick(), reason);
    engine.clearMatchContext();
  }

  private static void shutdown() {
    if (stopping) return;
    if (initialized) {
      interrupt("Client stopped");
      diagnostics.flush();
      autoReports.pump();
      saveConfig();
    }
    stopping = true;
    if (store != null) store.close();
  }

  private static void installEngine() {
    engine =
        new CombatEngine(
            config,
            fight -> recording.automaticFinished(fight),
            FeedbackController::hit,
            ClientCapture::label);
    recording =
        new RecordingController(
            config,
            engine,
            PhenomenaClient::completed,
            recovery::checkpoint,
            PhenomenaClient::notice,
            ClientCapture::label);
    recording.exportStatus(autoReports::availability);
  }

  /** Same checkpoint decoding and finalization as SessionRecovery, with a startup work budget. */
  private static List<FightRecord> recoverBounded(List<FightRecord> loaded) {
    var records = new ArrayList<>(loaded);
    Path folder = dataDir.resolve("pending-sessions");
    if (!Files.isDirectory(folder) || Files.isSymbolicLink(folder)) return records;
    Set<String> ids = new HashSet<>();
    for (var record : records) ids.add(record.id());
    long bytes = 0;
    try (var paths = Files.list(folder)) {
      var candidates =
          paths.limit(2000)
              .filter(p -> p.getFileName().toString().endsWith(".json"))
              // Preserved checkpoints already in history must not consume each pass's budget.
              .filter(
                  p -> {
                    String name = p.getFileName().toString();
                    return !ids.contains(name.substring(0, name.length() - 5));
                  })
              .limit(MAX_RECOVERED_SESSIONS + 1L)
              .toList();
      int examined = 0;
      for (var path : candidates) {
        if (examined++ == MAX_RECOVERED_SESSIONS) {
          notice("Further session checkpoints preserved for a later recovery pass.");
          break;
        }
        try {
          if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)) continue;
          long size = Files.size(path);
          if (size > RECOVERY_BYTES - bytes) {
            notice("Session checkpoint preserved; startup recovery byte budget reached.");
            continue;
          }
          bytes += size;
          var fight = FightStore.decode(Files.readString(path));
          if (!fight.manual()
              || !path.getFileName().toString().equals(fight.id() + ".json")
              || !ids.add(fight.id())) continue;
          store.persistFinalized(fight);
          records.add(fight);
          notice("Recovered manual session: " + fight.session().termination());
        } catch (IOException | RuntimeException ex) {
          notice("Checkpoint preserved; recovery failed: " + ex.getMessage());
        }
      }
    } catch (IOException ex) {
      notice("Session recovery failed: " + ex.getMessage());
    }
    return records;
  }

  public static void historyChanged() {
    historyVersion++;
  }

  public static void notice(String message) {
    LOG.info("{}", message);
    Minecraft.getInstance()
        .execute(
            () -> {
              status = message;
              FeedbackController.chat(Component.literal("[Pulsar] " + message));
            });
  }

  private static void storageError(String message) {
    LOG.warn("{}", message);
    Minecraft.getInstance().execute(() -> status = message);
  }

  private static void completed(FightRecord fight) {
    history.addFirst(fight);
    trimHistory();
    historyChanged();
    diagnostics.outcome(fight);
    if (fight.manual() || config.recording) autoReports.finalized(fight, reportOptions());
    if (config.chatSummary && !fight.manual())
      notice(
          fight.resolution().classification().label
              + " / "
              + fight.statistics().hits
              + (fight.statistics().hits == 1 ? " hit / " : " hits / ")
              + fight.statistics().attempts
              + (fight.statistics().attempts == 1 ? " attack" : " attacks"));
  }

  public static ReportExporter.Options reportOptions() {
    return new ReportExporter.Options(
        config.anonymizeExports, config.exportMotion, config.reducedMotion);
  }

  private static void trimHistory() {
    while (history.size() > config.retention) history.removeLast();
    int eventCount = history.stream().mapToInt(f -> f.events().size()).sum();
    for (int i = history.size() - 1; i >= 0 && eventCount > 50000; i--) {
      var fight = history.get(i);
      eventCount -= fight.events().size();
      history.set(i, fight.summaryOnly());
    }
  }

  public static void resetConfig() {
    interrupt("Configuration reset");
    PulsarConfigBridge.reset();
    PulsarConfigBridge.reload(config);
    config.validate();
    ClientCapture.reset();
    if (diagnostics != null)
      diagnostics.enabled(AuroraConfig.get().pulsarEnabled && config.diagnosticCapture);
  }

  public static void saveConfig() {
    PulsarConfigBridge.reload(config);
    config.validate();
    PulsarConfigBridge.save(config);
  }

  /** Explicit, read-only import; Aurora keeps control of the theme, key and feature state. */
  public static void importStandaloneSettings() {
    ensureReady();
    if (recording.manual() || engine.engaged()) {
      notice("Finish the current recording before importing settings.");
      return;
    }
    store.submit(() -> {
      Path directory = FabricLoader.getInstance().getConfigDir();
      Path source = directory.resolve("phenomena.json");
      if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) source = directory.resolve("kwamster.json");
      try {
        if (Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
          throw new IOException("No standalone phenomena.json or kwamster.json found");
        if (Files.size(source) > 1024 * 1024) throw new IOException("Standalone settings exceed 1 MiB");
        Config imported = AtomicJson.GSON.fromJson(Files.readString(source), Config.class);
        if (imported == null) throw new IOException("Standalone settings are empty");
        imported.validate();
        Minecraft.getInstance().execute(() -> {
          if (recording.manual() || engine.engaged()) {
            notice("Finish the current recording before importing settings.");
            return;
          }
          AuroraConfig.get().pulsarSettings = imported;
          PulsarConfigBridge.reload(config);
          PulsarConfigBridge.save(config);
          historyChanged();
          notice("Standalone settings imported. The original file is unchanged.");
        });
      } catch (IOException | RuntimeException error) {
        notice("Settings import failed: " + error.getMessage());
      }
    });
  }
}
