package parityprobe;

import com.aurora.client.adapter.NativeKeyboard;
import com.aurora.client.adapter.NativeUi;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.WaypointFeature;
import com.aurora.client.screen.*;
import com.aurora.client.screen.setting.KeybindSetting;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeRoundness;
import com.aurora.client.ui.interaction.SemanticFeedback;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.nio.file.Path;

/** API-driven visual/input acceptance in a marked disposable fixture only. */
public final class ParityProbe implements ClientModInitializer {
    private Path root;
    /** System.nanoTime() of the last "reload" command while its overlay is up; 0 otherwise. */
    private long reloadStarted;
    @Override public void onInitializeClient() {
        root = Path.of(System.getProperty("aurora.parity.probe"));
        if (!Files.isRegularFile(root.resolve("DISPOSABLE.txt"))) throw new IllegalStateException("Disposable fixture required");
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                var screen = NativeUi.screen(client);
                ShapeBenchmark.cancelUnlessManager(screen instanceof WaypointManagerScreen);
                // Copied port fixtures may ask for acknowledgement before quick-play.
                if (screen != null && (screen.getClass().getSimpleName().equals("BackupConfirmScreen")
                        || screen.getTitle().getString().equals("Upgrading World Completed"))) {
                    for (var child : screen.children()) if (child instanceof Button button
                            && (button.getMessage().getString().equals("I Know What I'm Doing!") || button.getMessage().getString().equals("Yes"))) {
                        button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                        break;
                    }
                }
                if (reloadStarted != 0 && NativeUi.overlay(client) == null) {
                    System.out.println("[parity-probe] reload overlay cleared after "
                            + (System.nanoTime() - reloadStarted) / 1_000_000 + " ms");
                    reloadStarted = 0;
                }
                var command = root.resolve("command.txt");
                if (!Files.exists(command)) return;
                String text = Files.readString(command).strip();
                if (text.equals("world") && client.level == null) return;
                Files.delete(command);
                execute(client, text);
                Files.writeString(root.resolve("result.txt"), text + "\nOK\n");
            } catch (Throwable failure) {
                failure.printStackTrace();
                try { Files.writeString(root.resolve("result.txt"), "FAILED: " + failure); }
                catch (Exception ignored) {}
            }
        });
    }

    private void execute(Minecraft client, String text) throws Exception {
        String[] args = text.split(" ", 3);
        var config = AuroraConfig.get();
        switch (args[0]) {
            case "settings" -> NativeUi.setScreen(client, AuroraScreen.create());
            case "world" -> NativeUi.setScreen(client, null);
            case "waypoints40" -> {
                if (client.level == null) throw new IllegalStateException("A world must be loaded");
                var feature = WaypointFeature.get();
                if (feature == null || WaypointFeature.scopeKey() == null)
                    throw new IllegalStateException("Waypoint scope is unavailable");
                if (!Files.isRegularFile(client.gameDirectory.toPath().resolve("SHAPE-BENCHMARK-DISPOSABLE.txt")))
                    throw new IllegalStateException("Shape benchmark fixture marker missing from game directory");
                if (!feature.currentWorldWaypoints().isEmpty())
                    throw new IllegalStateException("Shape benchmark requires a world with no waypoints");
                String dimension = client.level.dimension().identifier().toString();
                for (int i = 0; i < 40; i++) {
                    feature.addWaypoint(new AuroraConfig.Waypoint("Benchmark " + (i + 1),
                            i * 16, 64, 0, dimension, 0xFF4FC3F7));
                }
                if (feature.currentWorldWaypoints().size() != 40) throw new AssertionError("Expected 40 waypoints");
                NativeUi.setScreen(client, new WaypointManagerScreen(null));
            }
            case "shape-benchmark" -> {
                if (!(NativeUi.screen(client) instanceof WaypointManagerScreen)
                        || WaypointFeature.get().currentWorldWaypoints().size() != 40)
                    throw new IllegalStateException("Run waypoints40 first");
                Files.deleteIfExists(root.resolve("shape-benchmark.json"));
                ShapeBenchmark.arm(root.resolve("shape-benchmark.json"));
            }
            case "waypoints-screen" -> {
                if (client.level == null) throw new IllegalStateException("A world must be loaded");
                NativeUi.setScreen(client, new WaypointManagerScreen(null));
            }
            case "shape-cases" -> NativeUi.setScreen(client, new ShapeTestScreen());
            case "assert-inactivity" -> {
                if (!client.options.inactivityFpsLimit().get().getSerializedName().equals(args[1]))
                    throw new AssertionError("Inactivity FPS option was not restored");
            }
            case "stop" -> { ShapeBenchmark.cancel(); client.stop(); }
            case "resize" -> client.getWindow().setWindowed(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
            case "layout" -> {
                config.moduleGridView = args[1].equals("grid");
                NativeUi.setScreen(client, AuroraScreen.create());
            }
            case "theme" -> {
                // A pixel radius, or a retired Corner Style name (ROUND, SLIGHTLY_ROUND, SQUARE).
                config.themeOrDefault().cornerRadius = args[1].chars().allMatch(Character::isDigit)
                        ? ThemeRoundness.clamp(Integer.parseInt(args[1])) : ThemeRoundness.legacyRadius(args[1]);
                config.themeOrDefault().backgroundOpacity = Double.parseDouble(args[2]);
                ThemeManager.reload();
            }
            case "font" -> {
                config.activeFont = AuroraConfig.FontType.valueOf(args[1]);
                config.textRendererMode = AuroraConfig.TextRendererMode.valueOf(args[2]);
            }
            case "detail" -> {
                var feature = FeatureRegistry.all().stream().filter(f -> f.id.equals(args[1])).findFirst().orElseThrow();
                NativeUi.setScreen(client, FeatureDetailScreen.destination(AuroraScreen.create(), feature));
            }
            case "search" -> {
                for (var child : NativeUi.screen(client).children()) if (child instanceof EditBox box) {
                    NativeUi.screen(client).setFocused(box); box.setFocused(true); box.setValue(text.substring(7));
                }
            }
            case "escape" -> NativeUi.screen(client).keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
            case "capture" -> {
                var row = new KeybindSetting("Zoom", () -> config.zoomKey, key -> config.zoomKey = key);
                row.interactionControl().action().activate(SemanticFeedback.NONE, null);
                if (!row.onKeyPress(InputConstants.KEY_F7, 0)) throw new AssertionError("Key capture declined");
                if (config.zoomKey != NativeKeyboard.toStored(InputConstants.KEY_F7)) throw new AssertionError("Non-portable capture");
                AuroraConfig.save();
            }
            case "bindings" -> {
                boolean found = false;
                for (var mapping : client.options.keyMappings) if (mapping.getName().equals("key.aurora.zoom")) {
                    found = true;
                    var bound = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(mapping);
                    if (NativeKeyboard.toStored(bound.getValue()) != config.zoomKey) throw new AssertionError("Vanilla/config binding drift");
                    if (NativeKeyboard.toStored(mapping.getDefaultKey().getValue()) != com.aurora.client.config.KeybindMigrator.fromGlfw(67))
                        throw new AssertionError("Vanilla reset uses the wrong native key");
                    System.out.println("[parity-probe] zoom=" + config.zoomKey + ", vanilla=" + bound.getName());
                }
                if (!found) throw new AssertionError("Missing vanilla zoom binding");
            }
            case "persisted" -> {
                if (config.zoomKey != NativeKeyboard.toStored(InputConstants.KEY_F7))
                    throw new AssertionError("Captured binding did not survive restart");
            }
            case "keycaps" -> {
                var method = com.aurora.client.hud.module.KeystrokesModule.class.getDeclaredMethod("rawKeyName", int.class);
                method.setAccessible(true);
                int stored = NativeKeyboard.toStored(InputConstants.KEY_F7);
                if (!"F7".equals(method.invoke(null, stored))) throw new AssertionError("Extra-key label is not F7");
                config.keystrokesExtraKeys = new java.util.ArrayList<>(java.util.List.of(stored));
            }
            case "menu" -> client.disconnect(new AuroraTitleScreen(), false);
            case "settings-page" -> {
                // Opens the Settings tab on the named page, as a search result does.
                var screen = AuroraScreen.create();
                NativeUi.setScreen(client, screen);
                int page = -1;
                var pages = FeatureRegistry.settingsPages();
                for (int i = 0; i < pages.size(); i++) if (pages.get(i).label().equals(text.substring(14))) page = i;
                if (page < 0) throw new IllegalArgumentException(text);
                var category = AuroraScreen.class.getDeclaredMethod("selectCategory", int.class);
                var select = AuroraScreen.class.getDeclaredMethod("selectSettingsPage", int.class);
                category.setAccessible(true);
                select.setAccessible(true);
                category.invoke(screen, 1);
                select.invoke(screen, page);
            }
            case "perf-loading" -> {
                // ADR 0009 loading switches, checked on the live classes.
                var tracker = new net.minecraft.client.multiplayer.LevelLoadTracker(500L);
                var delay = net.minecraft.client.multiplayer.LevelLoadTracker.class.getDeclaredField("closeDelayMs");
                delay.setAccessible(true);
                long closeDelay = delay.getLong(tracker);
                if ((closeDelay == 0L) != config.perfSkipLevelLoadDelay) throw new AssertionError("closeDelayMs=" + closeDelay);
                var title = new net.minecraft.client.gui.screens.TitleScreen(true);
                var fading = net.minecraft.client.gui.screens.TitleScreen.class.getDeclaredField("fading");
                fading.setAccessible(true);
                boolean fades = fading.getBoolean(title);
                if (fades == config.perfHideTitleFade) throw new AssertionError("fading=" + fades);
                if (!(client instanceof com.aurora.client.mixin.MinecraftProfileAccessor a) || !a.aurora$profileFuture().isDone())
                    throw new AssertionError("profile accessor missing or profile still pending");
                System.out.println("[parity-probe] perf-loading closeDelayMs=" + closeDelay + " titleFading=" + fades);
            }
            case "reload" -> {
                reloadStarted = System.nanoTime();
                client.reloadResourcePacks();
            }
            default -> throw new IllegalArgumentException(text);
        }
        System.out.println("[parity-probe] " + text);
    }
}
