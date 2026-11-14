package dev.phenomena.recording;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.aurora.client.config.AuroraConfig;
import dev.phenomena.PhenomenaClient;
import dev.phenomena.combat.tracking.ClientCapture;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

/** Fabric's client dispatcher consumes these locally, including on permissionless servers. */
public final class RecordingCommands {
  private RecordingCommands() {}

  public static void register() {
    ClientCommandRegistrationCallback.EVENT.register(
        (dispatcher, access) ->
            install(
                dispatcher,
                RecordingCommands::start,
                RecordingCommands::end,
                RecordingCommands::diagnostics,
                (names, servers) ->
                    PhenomenaClient.diagnostics.export(names, servers, ClientCapture.knownNames())));
  }

  public static void install(
      com.mojang.brigadier.CommandDispatcher<
              net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>
          dispatcher,
      Runnable start,
      Runnable end,
      java.util.function.Consumer<Boolean> diagnostics,
      java.util.function.BiConsumer<Boolean, Boolean> export) {
    var tree = buildTree(start, end, diagnostics, export);
    dispatcher.register(tree);
    var pulsar = dispatcher.getRoot().getChild("pulsar");
    if (pulsar != null)
      for (String alias : new String[] {"phenomena", "kwm"})
        dispatcher.register(literal(alias).executes(pulsar.getCommand()).redirect(pulsar));
  }

  private static com.mojang.brigadier.builder.LiteralArgumentBuilder<
          net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>
      buildTree(
          Runnable start,
          Runnable end,
          java.util.function.Consumer<Boolean> diagnostics,
          java.util.function.BiConsumer<Boolean, Boolean> export) {
    return literal("pulsar")
            .executes(
                ctx -> {
                  var client = net.minecraft.client.Minecraft.getInstance();
                  PhenomenaClient.openDashboard(com.aurora.client.adapter.NativeUi.screen(client));
                  return 1;
                })
            .then(
                literal("start")
                    .executes(
                        ctx -> {
                          if (!available()) return 0;
                          start.run();
                          return 1;
                        }))
            .then(
                literal("end")
                    .executes(
                        ctx -> {
                          if (!available()) return 0;
                          end.run();
                          return 1;
                        }))
            .then(
                literal("diagnostics")
                    .then(
                        literal("on")
                            .executes(
                                ctx -> {
                                  if (!available()) return 0;
                                  diagnostics.accept(true);
                                  return 1;
                                }))
                    .then(
                        literal("off")
                            .executes(
                                ctx -> {
                                  if (!available()) return 0;
                                  diagnostics.accept(false);
                                  return 1;
                                }))
                    .then(
                        literal("export")
                            .executes(
                                ctx -> {
                                  if (!available()) return 0;
                                  export.accept(true, true);
                                  return 1;
                                }))
                    .then(
                        literal("export-names")
                            .executes(
                                ctx -> {
                                  if (!available()) return 0;
                                  export.accept(true, false);
                                  return 1;
                                }))
                    .then(
                        literal("export-servers")
                            .executes(
                                ctx -> {
                                  if (!available()) return 0;
                                  export.accept(false, true);
                                  return 1;
                                })));
  }

  /** A local command never silently enables Aurora's feature gate. */
  private static boolean available() {
    if (!AuroraConfig.get().pulsarEnabled) {
      PhenomenaClient.notice("Pulsar is disabled. Enable the Pulsar feature in Aurora to record.");
      return false;
    }
    PhenomenaClient.ensureReady();
    return PhenomenaClient.isInitialized();
  }

  public static void start() {
    if (!available()) return;
    var mc = net.minecraft.client.Minecraft.getInstance();
    PhenomenaClient.recording.start(
        mc.player == null ? null : ClientCapture.currentHost(),
        mc.player == null ? null : mc.player.getName().getString(),
        ClientCapture.currentWorld(),
        ClientCapture.now(),
        ClientCapture.clientTick());
  }

  public static void end() {
    if (!available()) return;
    PhenomenaClient.recording.end(ClientCapture.now(), ClientCapture.clientTick());
  }

  public static void diagnostics(boolean enabled) {
    if (!available()) return;
    PhenomenaClient.config.diagnosticCapture = enabled;
    PhenomenaClient.diagnostics.enabled(enabled);
    PhenomenaClient.saveConfig();
    PhenomenaClient.notice(
        "Diagnostic capture "
            + (enabled ? "enabled; relevant announcements stay local." : "disabled."));
  }
}
