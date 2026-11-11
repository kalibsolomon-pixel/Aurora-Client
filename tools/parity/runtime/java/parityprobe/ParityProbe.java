package parityprobe;

import com.aurora.client.adapter.NativeKeyboard;
import com.aurora.client.adapter.NativeUi;
import com.aurora.client.config.AuroraConfig;
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
    @Override public void onInitializeClient() {
        root = Path.of(System.getProperty("aurora.parity.probe"));
        if (!Files.isRegularFile(root.resolve("DISPOSABLE.txt"))) throw new IllegalStateException("Disposable fixture required");
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                var screen = NativeUi.screen(client);
                // Copied port fixtures may ask for acknowledgement before quick-play.
                if (screen != null && (screen.getClass().getSimpleName().equals("BackupConfirmScreen")
                        || screen.getTitle().getString().equals("Upgrading World Completed"))) {
                    for (var child : screen.children()) if (child instanceof Button button
                            && (button.getMessage().getString().equals("I Know What I'm Doing!") || button.getMessage().getString().equals("Yes"))) {
                        button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                        break;
                    }
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
            case "stop" -> client.stop();
            case "resize" -> client.getWindow().setWindowed(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
            case "layout" -> {
                config.moduleGridView = args[1].equals("grid");
                NativeUi.setScreen(client, AuroraScreen.create());
            }
            case "theme" -> {
                config.themeOrDefault().roundness = ThemeRoundness.valueOf(args[1]);
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
            default -> throw new IllegalArgumentException(text);
        }
        System.out.println("[parity-probe] " + text);
    }
}
