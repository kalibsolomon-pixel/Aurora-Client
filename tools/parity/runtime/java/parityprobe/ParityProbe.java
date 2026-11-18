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
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.nio.file.Path;

/** API-driven visual/input acceptance in a marked disposable fixture only. */
public final class ParityProbe implements ClientModInitializer {
    private Path root;
    /** System.nanoTime() of the last "reload" command while its overlay is up; 0 otherwise. */
    private long reloadStarted;
    /** Degrees the camera turns every tick ("spin"); 0 = still. */
    private float spinPerTick;
    private Object rememberedTooltip;
    /** Particles added around the player every tick ("particles N"); half of them underground. */
    private int particlesPerTick;
    /** The running "fps" sample: label, end time and the once-a-second readings so far. */
    private String fpsLabel;
    private long fpsUntil, fpsNextReading;
    private net.minecraft.client.InactivityFpsLimit fpsPreviousInactivity;
    private final java.util.List<Integer> fpsReadings = new java.util.ArrayList<>();
    /** The running "count" window: label, start, end and frames seen. */
    private String countLabel;
    private long countStart, countUntil, countFrames, countSteps;
    /** The running "pulsar-reach" approach: ticks left and the husk being sprinted at. */
    private int reachTicks;
    private net.minecraft.world.entity.LivingEntity reachTarget;
    @Override public void onInitializeClient() {
        root = Path.of(System.getProperty("aurora.parity.probe"));
        if (!Files.isRegularFile(root.resolve("DISPOSABLE.txt"))) throw new IllegalStateException("Disposable fixture required");
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                var screen = NativeUi.screen(client);
                if (screen == null) UiBounds.cancel();
                UiCacheBenchmark.cancelIfDifferent(screen);
                ShapeBenchmark.cancelUnlessManager(screen instanceof WaypointManagerScreen
                        || screen instanceof dev.phenomena.gui.screens.PhenomenaScreen);
                // Copied port fixtures may ask for acknowledgement before quick-play.
                if (screen != null && (screen.getClass().getSimpleName().equals("BackupConfirmScreen")
                        || screen.getTitle().getString().equals("Upgrading World Completed"))) {
                    for (var child : screen.children()) if (child instanceof Button button
                            && (button.getMessage().getString().equals("I Know What I'm Doing!") || button.getMessage().getString().equals("Yes"))) {
                        button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                        break;
                    }
                }
                if (spinPerTick != 0 && client.player != null) {
                    client.player.setYRot(client.player.getYRot() + spinPerTick);
                    client.player.yRotO = client.player.getYRot() - spinPerTick;
                }
                if (particlesPerTick > 0 && client.level != null && client.player != null) {
                    var random = client.level.getRandom();
                    for (int i = 0; i < particlesPerTick; i++) {
                        double x = client.player.getX() + (random.nextDouble() - .5) * 48;
                        double z = client.player.getZ() + (random.nextDouble() - .5) * 48;
                        // Half above the player, half 16-40 blocks below it, inside the terrain.
                        double y = client.player.getY() + (i % 2 == 0 ? random.nextDouble() * 8 : -16 - random.nextDouble() * 24);
                        client.level.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME, x, y, z, 0, 0.01, 0);
                    }
                }
                if (reachTicks > 0 && client.player != null) reachStep(client);
                if (reloadStarted != 0 && NativeUi.overlay(client) == null) {
                    System.out.println("[parity-probe] reload overlay cleared after "
                            + (System.nanoTime() - reloadStarted) / 1_000_000 + " ms");
                    reloadStarted = 0;
                }
                if (fpsLabel != null && System.nanoTime() >= fpsNextReading) {
                    // Minecraft.getFps() is refreshed once a second; read it once a second after the first.
                    fpsReadings.add(client.getFps());
                    fpsNextReading += 1_000_000_000L;
                    if (fpsNextReading > fpsUntil) {
                        System.out.println("[parity-probe] fps " + fpsLabel + " " + fpsReadings);
                        fpsLabel = null;
                        restoreFpsOptions(client);
                    }
                }
                if (countLabel != null) {
                    long now = System.nanoTime();
                    if (now >= countUntil) {
                        double seconds = (now - countStart) / 1e9;
                        System.out.println(String.format(java.util.Locale.ROOT, "[parity-probe] count %s: %.1f listener updates/s, %.1f text effects/s, %.1f atlas draws/s, %.1f animation steps/s, %.1f fps",
                                countLabel, Counters.LISTENER_UPDATES.get() / seconds, Counters.TEXT_EFFECTS.get() / seconds,
                                Counters.ATLAS_DRAWS.get() / seconds,
                                (com.aurora.client.util.SpriteAnimationClock.ticks() - countSteps) / seconds, (double) client.getFps()));
                        countLabel = null;
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

    /**
     * Sprints at the husk while clicking the real attack key, so attacks go through Minecraft.tick's
     * pick and startAttack with the player moving; restarts the approach from 4.6 blocks on contact.
     */
    private void reachStep(Minecraft client) {
        var player = client.player;
        if (--reachTicks == 0 || !reachTarget.isAlive()) {
            reachTicks = 0;
            ReachCheck.report();
            return;
        }
        double dx = reachTarget.getX() - player.getX(), dz = reachTarget.getZ() - player.getZ();
        double length = Math.hypot(dx, dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        if (length < 1.6) {
            player.snapTo(reachTarget.getX() - dx / length * 4.6, player.getY(), reachTarget.getZ() - dz / length * 4.6, yaw, 0);
            return;
        }
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setXRot(0);
        player.xRotO = 0;
        player.setSprinting(true);
        player.setDeltaMovement(dx / length * .28, player.getDeltaMovement().y, dz / length * .28);
        net.minecraft.client.KeyMapping.click(InputConstants.getKey(client.options.keyAttack.saveString()));
    }

    /** The native modifier bit for `ctrl`/`shift`: GLFW bits on 26.1/26.2, SDL pairs on 26.3. */
    private static int modifierBit(String name) {
        for (int bit : new int[]{1, 2, 3, 64, 128, 192}) {
            var event = new KeyEvent(0, 0, bit);
            if (name.equals("ctrl") && event.hasControlDownWithQuirk() && !event.hasShiftDown()) return bit;
            if (name.equals("shift") && event.hasShiftDown() && !event.hasControlDown()) return bit;
        }
        throw new IllegalArgumentException("Unknown modifier " + name);
    }

    private void execute(Minecraft client, String text) throws Exception {
        String[] args = text.split(" ", 3);
        var config = AuroraConfig.get();
        switch (args[0]) {
            case "pulsar" -> {
                var state = dev.phenomena.gui.ui.UiState.SESSION;
                if (args.length > 1) state.tab = dev.phenomena.gui.ui.UiState.Tab.valueOf(args[1]);
                if (args.length > 2) {
                    switch (state.tab) {
                        case SETTINGS -> state.settings = dev.phenomena.gui.ui.UiState.SettingsSection.valueOf(args[2]);
                        case OFFENSE -> state.offense = dev.phenomena.gui.ui.UiState.OffenseSection.valueOf(args[2]);
                        case DEFENSE -> state.defense = dev.phenomena.gui.ui.UiState.DefenseSection.valueOf(args[2]);
                        case FLOW -> state.flow = dev.phenomena.gui.ui.UiState.FlowSection.valueOf(args[2]);
                        default -> {}
                    }
                }
                dev.phenomena.PhenomenaClient.openDashboard(null);
            }
            case "pulsar-enable" -> config.pulsarEnabled = Boolean.parseBoolean(args[1]);
            case "pulsar-load" -> {
                for (var fight : java.util.List.copyOf(dev.phenomena.PhenomenaClient.history))
                    dev.phenomena.gui.DataCache.requestTimeline(fight.id(), dev.phenomena.PhenomenaClient::historyChanged);
            }
            case "pulsar-detail" -> {
                var fight = dev.phenomena.PhenomenaClient.history.get(args.length > 1 ? Integer.parseInt(args[1]) : 0);
                dev.phenomena.gui.ui.UiState.SESSION.openFight(fight.id());
                dev.phenomena.PhenomenaClient.openDashboard(null);
            }
            case "pulsar-focus" -> dev.phenomena.gui.ui.UiState.SESSION.focus = Integer.parseInt(args[1]);
            case "pulsar-list" -> {
                dev.phenomena.gui.ui.UiState.SESSION.closeDetail();
                dev.phenomena.gui.ui.UiState.SESSION.tab = dev.phenomena.gui.ui.UiState.Tab.HISTORY;
                dev.phenomena.PhenomenaClient.openDashboard(null);
            }
            case "pulsar-delete" -> {
                // The real History delete confirmation: select a recorded fight, press Delete.
                var history = dev.phenomena.PhenomenaClient.history;
                var state = dev.phenomena.gui.ui.UiState.SESSION;
                state.closeDetail();
                state.tab = dev.phenomena.gui.ui.UiState.Tab.HISTORY;
                state.selectedFight = history.get(Math.min(1, history.size() - 1)).id();
                dev.phenomena.PhenomenaClient.openDashboard(null);
                var delete = NativeUi.screen(client).children().stream()
                        .filter(w -> w instanceof dev.phenomena.gui.components.KButton b
                                && b.active && b.getMessage().getString().equals("Delete"))
                        .map(w -> (dev.phenomena.gui.components.KButton) w)
                        .findFirst().orElseThrow(() -> new IllegalStateException("No enabled Delete button"));
                delete.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            }
            case "pulsar-confirm" -> ((dev.phenomena.gui.screens.PhenomenaScreen) NativeUi.screen(client))
                    .confirm("Delete recording?", "Delete this local recording? Exported reports are kept.", "Delete", () -> {});
            case "pulsar-target" -> {
                if (!Files.exists(root.resolve("DISPOSABLE.txt"))) throw new IllegalStateException("Disposable fixture required");
                var server = client.getSingleplayerServer();
                var player = client.player;
                String command = "summon minecraft:husk " + (player.getX() + 1.8) + " " + player.getY() + " " + player.getZ()
                        + " {NoAI:1b,PersistenceRequired:1b,Silent:1b}";
                server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
            }
            case "pulsar-attack" -> {
                NativeUi.setScreen(client, null);
                var target = client.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        client.player.getBoundingBox().inflate(4), e -> e.getType().getDescription().getString().equals("Husk")).getFirst();
                client.player.setYRot(-90);
                client.player.setXRot(0);
                client.gameMode.attack(client.player, target);
                for (var method : client.player.getClass().getMethods()) {
                    if (!method.getName().equals("swing")) continue;
                    if (method.getParameterCount() == 1) {
                        method.invoke(client.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                        break;
                    }
                    if (method.getParameterCount() == 3 && method.getParameterTypes()[1].isEnum()) {
                        method.invoke(client.player, net.minecraft.world.InteractionHand.MAIN_HAND, method.getParameterTypes()[1].getEnumConstants()[0], true);
                        break;
                    }
                }
            }
            case "pulsar-controls" -> {
                var slider = new dev.phenomena.gui.components.KSlider("probe", 5, 0, 10, 1, v -> "" + v, v -> {});
                slider.setX(10); slider.setWidth(155);
                slider.onClick(new net.minecraft.client.input.MouseButtonEvent(94, 10, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
                if (slider.current() != 10) throw new AssertionError("Track endpoint does not map to maximum");
                slider.setFocused(true);
                slider.keyPressed(new KeyEvent(InputConstants.KEY_LEFT, 0, 0));
                if (slider.current() != 9) throw new AssertionError("Arrow did not move exactly one step");
            }
            case "pulsar-measure" -> {
                if (!com.aurora.client.diagnostics.FrameBenchmark.start(1, 5,
                        () -> com.aurora.client.ui.util.RenderUtil.fillsSubmitted()
                                + com.aurora.client.ui.util.NativeShapeRenderer.quadsSubmitted(),
                        com.aurora.client.ui.render.blur.BlurPanelRenderer::capturesSubmitted,
                        stats -> System.out.println("[parity-probe] pulsar-measure " + stats)))
                    throw new IllegalStateException("Benchmark already running");
            }
            case "pulsar-extract-measure" -> {
                if (!(NativeUi.screen(client) instanceof dev.phenomena.gui.screens.PhenomenaScreen))
                    throw new IllegalStateException("Pulsar dashboard required");
                ShapeBenchmark.armPulsar(root.resolve("pulsar-extraction.json"));
            }
            case "pulsar-scroll" -> ((dev.phenomena.gui.screens.PhenomenaScreen) NativeUi.screen(client))
                    .setScroll(Integer.parseInt(args[1]));
            case "pulsar-input" -> {
                var dashboard = (dev.phenomena.gui.screens.PhenomenaScreen) NativeUi.screen(client);
                int[] committed = {0};
                dashboard.confirm("Probe", "Keyboard confirmation", "Delete", () -> committed[0]++);
                dashboard.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                if (committed[0] != 0) throw new AssertionError("Confirmation did not default to Cancel");
                dashboard.confirm("Probe", "Keyboard confirmation", "Delete", () -> committed[0]++);
                dashboard.keyPressed(new KeyEvent(InputConstants.KEY_RIGHT, 0, 0));
                dashboard.keyPressed(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
                if (committed[0] != 1) throw new AssertionError("Confirmation keyboard action failed");
                dashboard.confirm("Probe", "Keyboard confirmation", "Delete", () -> committed[0]++);
                dashboard.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
                if (committed[0] != 1) throw new AssertionError("Escape committed a destructive action");
                double prior = client.options.screenEffectScale().get();
                client.options.screenEffectScale().set(0.0);
                if (!dev.phenomena.gui.theme.Theme.reducedMotion()) throw new AssertionError("Vanilla reduced motion ignored");
                client.options.screenEffectScale().set(prior);
            }
            case "pulsar-readable" -> {
                var dashboard = (dev.phenomena.gui.screens.PhenomenaScreen) NativeUi.screen(client);
                var readable = dashboard.children().stream()
                        .filter(child -> child instanceof dev.phenomena.gui.components.ReadableBlock)
                        .map(child -> (dev.phenomena.gui.components.ReadableBlock) child)
                        .filter(block -> block.narration().stream().anyMatch(line -> line.contains("Target deviation")))
                        .findFirst().orElseThrow();
                dashboard.setFocused(readable);
                readable.setFocused(true);
                client.setLastInputType(net.minecraft.client.InputType.KEYBOARD_ARROW);
                for (int i = 0; i < readable.narration().size() && !readable.description().contains("Target deviation"); i++)
                    dashboard.keyPressed(new KeyEvent(InputConstants.KEY_RIGHT, 0, 0));
                if (!readable.description().contains("Mean absolute difference"))
                    throw new AssertionError("Metric explanation unavailable through keyboard");
                var spoken = new java.util.ArrayList<String>();
                var output = (net.minecraft.client.gui.narration.NarrationElementOutput) java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(), new Class<?>[]{net.minecraft.client.gui.narration.NarrationElementOutput.class},
                        (proxy, method, values) -> {
                            if (method.getName().equals("nest")) return proxy;
                            if (method.getName().equals("narrationTrigger")) return method.getReturnType().getEnumConstants()[0];
                            if (method.getName().equals("add")) {
                                for (Object value : values) {
                                    if (value instanceof net.minecraft.client.gui.narration.NarrationThunk<?> thunk) thunk.getText(spoken::add);
                                    else if (value instanceof net.minecraft.network.chat.Component component) spoken.add(component.getString());
                                    else if (value instanceof String line) spoken.add(line);
                                }
                            }
                            return null;
                        });
                readable.updateNarration(output);
                if (spoken.stream().noneMatch(line -> line.contains("Target deviation") && line.contains("Mean absolute difference")))
                    throw new AssertionError("Native narration omitted full metric explanation: " + spoken);
                System.out.println("[parity-probe] narrated " + spoken);
            }
            case "pulsar-reach" -> {
                if (!Files.exists(root.resolve("DISPOSABLE.txt"))) throw new IllegalStateException("Disposable fixture required");
                NativeUi.setScreen(client, null);
                var server = client.getSingleplayerServer();
                boolean creative = args.length > 2 && args[2].equals("creative");
                server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                        "gamemode " + (creative ? "creative" : "survival") + " @a"));
                reachTarget = client.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        client.player.getBoundingBox().inflate(8), e -> e.getType().getDescription().getString().equals("Husk")).getFirst();
                reachTicks = args.length > 1 ? Integer.parseInt(args[1]) : 400;
                ReachCheck.start(creative);
            }
            case "pulsar-reach-stats" -> {
                var fight = dev.phenomena.PhenomenaClient.history.getFirst();
                if (fight.events() == null || fight.events().isEmpty())
                    throw new IllegalStateException("Latest recording has no loaded events: " + fight.id());
                System.out.println("[parity-probe] " + ReachCheck.stats(fight, args.length > 1 && args[1].equals("creative")));
            }
            case "pulsar-start" -> {
                dev.phenomena.recording.RecordingCommands.start();
                if (!dev.phenomena.PhenomenaClient.recording.manual()) throw new AssertionError("Manual session did not start");
            }
            case "pulsar-end" -> {
                dev.phenomena.recording.RecordingCommands.end();
                if (dev.phenomena.PhenomenaClient.recording.manual()) throw new AssertionError("Manual session did not end");
            }
            case "pulsar-disabled" -> {
                if (dev.phenomena.PhenomenaClient.isInitialized()) throw new AssertionError("Disabled startup initialized storage");
                if (Files.exists(client.gameDirectory.toPath().resolve("phenomena"))) throw new AssertionError("Disabled startup touched storage");
            }
            case "settings" -> NativeUi.setScreen(client, AuroraScreen.create());
            case "ui-bounds" -> {
                if (args.length != 2 || !args[1].matches("[a-zA-Z0-9_-]+"))
                    throw new IllegalArgumentException("A safe, unique result label is required");
                UiBounds.arm(root.resolve(args[1] + ".json"), NativeUi.screen(client));
            }
            case "ui-benchmark", "ui-open-benchmark", "ui-cache-state" -> {
                if (args.length < 2 || !args[1].matches("[a-zA-Z0-9_-]+"))
                    throw new IllegalArgumentException("A safe, unique result label is required");
                Path output = root.resolve(args[1] + ".json");
                if (args[0].equals("ui-cache-state")) {
                    UiCacheBenchmark.write(output, UiCacheBenchmark.cacheState());
                    break;
                }
                var target = NativeUi.screen(client);
                boolean fresh = args[0].equals("ui-open-benchmark");
                if (fresh) {
                    if (args.length < 3 || args[2].equals("modules")) target = AuroraScreen.create();
                    else {
                        FeatureRegistry.modules();
                        var feature = FeatureRegistry.all().stream().filter(f -> f.id.equals(args[2])).findFirst().orElseThrow();
                        target = FeatureDetailScreen.destination(null, feature);
                    }
                }
                if (!(target instanceof AuroraScreen || target instanceof FeatureDetailScreen))
                    throw new IllegalStateException("UI benchmark requires a main or feature detail screen");
                UiCacheBenchmark.arm(output, target, !fresh && args.length > 2 && args[2].equals("scroll"), fresh);
                if (fresh) NativeUi.setScreen(client, target);
            }
            case "ui-suppress" -> com.aurora.client.ui.render.blur.BlurPanelRenderer.noteScreenshotGrab();
            case "ui-tooltip" -> {
                var field = com.aurora.client.screen.setting.FeatureSetting.class.getDeclaredField("tooltipLayout");
                field.setAccessible(true);
                Object layout = field.get(null);
                switch (args[1]) {
                    case "remember" -> {
                        if (layout == null) throw new AssertionError("Hover a described label until its tooltip is visible");
                        rememberedTooltip = layout;
                    }
                    case "unchanged" -> {
                        if (rememberedTooltip == null || layout != rememberedTooltip)
                            throw new AssertionError("Unchanged tooltip did not reuse its layout");
                    }
                    case "changed" -> {
                        if (layout == null || rememberedTooltip == null || layout == rememberedTooltip)
                            throw new AssertionError("Tooltip layout did not invalidate");
                        rememberedTooltip = layout;
                    }
                    case "absent" -> {
                        if (layout != null) throw new AssertionError("Faded tooltip retained its layout");
                        rememberedTooltip = null;
                    }
                    default -> throw new IllegalArgumentException(text);
                }
            }
            case "fps-cap" -> {
                client.options.framerateLimit().set(Integer.parseInt(args[1]));
                client.options.enableVsync().set(false);
            }
            case "stable-settings" -> NativeUi.setScreen(client, new AuroraScreen() {
                @Override public boolean isPauseScreen() { return true; }
            });
            case "world" -> NativeUi.setScreen(client, null);
            // `create-world`: open vanilla's fresh Create World screen (then `press Create New World`).
            case "create-world" -> {
                var type = net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.class;
                var open = java.util.Arrays.stream(type.getMethods())
                        .filter(m -> m.getName().equals("openFresh") && m.getParameterCount() == 2).findFirst().orElseThrow();
                Object closer = open.getParameterTypes()[1] == Runnable.class ? (Runnable) () -> {} : null;
                open.invoke(null, client, closer);
            }
            // `press <label>`: press the open screen's button with exactly that label.
            case "press" -> {
                String label = text.substring(6);
                var button = NativeUi.screen(client).children().stream()
                        .filter(c -> c instanceof Button b && b.getMessage().getString().equals(label))
                        .map(Button.class::cast).findFirst().orElseThrow(() -> new IllegalStateException("No button " + label));
                button.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            }
            // `server <command>`: run a server command in the disposable world (e.g. `time set noon`).
            case "server" -> {
                if (!Files.exists(root.resolve("DISPOSABLE.txt"))) throw new IllegalStateException("Disposable fixture required");
                var server = client.getSingleplayerServer();
                String command = text.substring(7);
                server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
            }
            // `expect-config <field> <json>`: fail unless the live AuroraConfig field equals the value.
            case "expect-config" -> {
                String[] kv = text.substring(14).split(" ", 2);
                var field = AuroraConfig.class.getField(kv[0]);
                Object want = new com.google.gson.Gson().fromJson(kv[1], field.getGenericType());
                if (!java.util.Objects.equals(want, field.get(config)))
                    throw new AssertionError(kv[0] + "=" + field.get(config) + ", expected " + want);
            }
            // `expect-screen <SimpleName|none>`: fail unless the open screen's class has that simple name.
            case "expect-screen" -> {
                var screen = NativeUi.screen(client);
                String name = screen == null ? "none" : screen.getClass().getSimpleName();
                if (!name.equals(args[1])) throw new AssertionError("screen " + name + ", expected " + args[1]);
            }
            // `look <yaw> <pitch>`: point the local player's view (degrees, vanilla convention).
            case "look" -> {
                NativeUi.setScreen(client, null);
                client.player.setYRot(Float.parseFloat(args[1]));
                client.player.setXRot(Float.parseFloat(args[2]));
                client.player.yRotO = client.player.getYRot();
                client.player.xRotO = client.player.getXRot();
            }
            case "pointer" -> {
                // Parks the GUI pointer (window pixels) without moving the OS cursor: GLFW drops
                // synthetic moves at an unchanged position, so the parked value holds.
                for (String name : new String[]{"xpos", "ypos"}) {
                    var field = net.minecraft.client.MouseHandler.class.getDeclaredField(name);
                    field.setAccessible(true);
                    field.setDouble(client.mouseHandler, Double.parseDouble(args[name.equals("xpos") ? 1 : 2]));
                }
            }
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
            case "blur" -> NativeUi.setScreen(client, new com.aurora.client.ui.render.blur.BlurTestScreen());
            case "blur-radius" -> {
                config.themeOrDefault().blurRadius = Integer.parseInt(args[1]);
                ThemeManager.reload();
            }
            case "hud-editor" -> NativeUi.setScreen(client, new HudEditorScreen());
            case "profiles" -> NativeUi.setScreen(client, new ProfileManagerScreen(AuroraScreen.create()));
            case "profile-create" -> {
                var screen = (ProfileManagerScreen) NativeUi.screen(client);
                var begin = ProfileManagerScreen.class.getDeclaredMethod("beginCreate");
                begin.setAccessible(true);
                begin.invoke(screen);
            }
            case "profile-name" -> {
                var field = ProfileManagerScreen.class.getDeclaredField("createField");
                field.setAccessible(true);
                ((EditBox) field.get(NativeUi.screen(client))).setValue(text.substring(13));
            }
            case "hover" -> {
                float scale = client.getWindow().getGuiScale();
                var mouse = client.mouseHandler;
                var x = mouse.getClass().getDeclaredField("xpos");
                var y = mouse.getClass().getDeclaredField("ypos");
                x.setAccessible(true); y.setAccessible(true);
                x.setDouble(mouse, Double.parseDouble(args[1]) * scale);
                y.setDouble(mouse, Double.parseDouble(args[2]) * scale);
            }
            case "click" -> {
                var event = new net.minecraft.client.input.MouseButtonEvent(
                        Double.parseDouble(args[1]), Double.parseDouble(args[2]),
                        new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
                NativeUi.screen(client).mouseClicked(event, false);
                NativeUi.screen(client).mouseReleased(event);
            }
            case "assert-inactivity" -> {
                if (!client.options.inactivityFpsLimit().get().getSerializedName().equals(args[1]))
                    throw new AssertionError("Inactivity FPS option was not restored");
            }
            case "stop" -> { UiCacheBenchmark.cancel(); ShapeBenchmark.cancel(); restoreFpsOptions(client); client.stop(); }
            case "resize" -> client.getWindow().setWindowed(Integer.parseInt(args[1]), Integer.parseInt(args[2]));
            // `semantic <label>`: every semantic control's pointer rect on the current screen.
            case "semantic" -> {
                if (args.length != 2 || !args[1].matches("[a-zA-Z0-9_-]+"))
                    throw new IllegalArgumentException("A safe, unique result label is required");
                var screen = NativeUi.screen(client);
                var rows = new java.util.ArrayList<java.util.Map<String, Object>>();
                for (Class<?> type = screen.getClass(); type != null; type = type.getSuperclass()) {
                    for (var field : type.getDeclaredFields()) {
                        if (!com.aurora.client.ui.interaction.SemanticControlHost.class.isAssignableFrom(field.getType())) continue;
                        field.setAccessible(true);
                        var host = (com.aurora.client.ui.interaction.SemanticControlHost) field.get(screen);
                        if (host == null) continue;
                        for (var control : host.controls()) {
                            var row = new java.util.LinkedHashMap<String, Object>();
                            row.put("name", control.getMessage().getString());
                            row.put("x", control.getX()); row.put("y", control.getY());
                            row.put("w", control.getWidth()); row.put("h", control.getHeight());
                            row.put("available", control.isAvailable());
                            rows.add(row);
                        }
                    }
                }
                var report = new java.util.LinkedHashMap<String, Object>();
                report.put("screen", screen.getClass().getName());
                report.put("controls", rows);
                UiCacheBenchmark.write(root.resolve(args[1] + ".json"), report);
            }
            // `fullscreen on|off`: borderless fullscreen on the monitor the window is on.
            case "fullscreen" -> {
                // Reflective: these Window methods exist on 26.3 only, and the probe builds per line.
                var window = client.getWindow();
                window.getClass().getMethod("setExclusiveFullscreen", boolean.class).invoke(window, false);
                window.getClass().getMethod("setFullscreen", boolean.class).invoke(window, args[1].equals("on"));
                window.getClass().getMethod("updateFullscreenIfChanged").invoke(window);
            }
            // Window geometry and where the GUI believes the pointer is, for pointer-mapping checks.
            case "window-state" -> {
                var window = client.getWindow();
                var mouse = client.mouseHandler;
                var xpos = mouse.getClass().getDeclaredField("xpos");
                var ypos = mouse.getClass().getDeclaredField("ypos");
                xpos.setAccessible(true); ypos.setAccessible(true);
                double mx = xpos.getDouble(mouse), my = ypos.getDouble(mouse);
                System.out.println("[parity-probe] window pos=" + window.getX() + "," + window.getY()
                        + " screen=" + window.getScreenWidth() + "x" + window.getScreenHeight()
                        + " framebuffer=" + window.getWidth() + "x" + window.getHeight()
                        + " gui=" + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight()
                        + " scale=" + window.getGuiScale()
                        + " pointer=" + mx + "," + my
                        + " guiPointer=" + (mx * window.getGuiScaledWidth() / window.getScreenWidth()) + ","
                        + (my * window.getGuiScaledHeight() / window.getScreenHeight()));
            }
            case "gui-scale" -> {
                client.options.guiScale().set(Integer.parseInt(args[1]));
                // This native entry point was renamed in 26.3.
                java.lang.reflect.Method resize;
                try { resize = Minecraft.class.getMethod("resizeGui"); }
                catch (NoSuchMethodException olderLine) { resize = Minecraft.class.getMethod("resizeDisplay"); }
                resize.invoke(client);
            }
            case "pause" -> NativeUi.setScreen(client, new net.minecraft.client.gui.screens.PauseScreen(true));
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
            // `theme-set <field> <json>` sets one ThemeDefinition field and reloads the theme.
            case "theme-set" -> {
                String[] kv = text.substring(10).split(" ", 2);
                var theme = config.themeOrDefault();
                var field = theme.getClass().getField(kv[0]);
                field.set(theme, new com.google.gson.Gson().fromJson(kv[1], field.getGenericType()));
                ThemeManager.reload();
                System.out.println("[parity-probe] theme " + kv[0] + "=" + field.get(theme));
            }
            case "font" -> {
                config.activeFont = AuroraConfig.FontType.valueOf(args[1]);
                config.textRendererMode = AuroraConfig.TextRendererMode.valueOf(args[2]);
            }
            case "font-state" -> {
                var access = (com.aurora.client.mixin.MinecraftFontManagerAccessor) client;
                var fonts = (com.aurora.client.mixin.FontManagerAccessor) access.aurora$fontManager();
                long pending = fonts.aurora$fontSets().values().stream()
                        .filter(font -> font instanceof com.aurora.client.ui.util.DeferredFontSet deferred
                                && deferred.aurora$pending()).count();
                System.out.println("[parity-probe] fonts pending=" + pending);
            }
            case "font-check" -> {
                var access = (com.aurora.client.mixin.MinecraftFontManagerAccessor) client;
                var fonts = (com.aurora.client.mixin.FontManagerAccessor) access.aurora$fontManager();
                var ids = new java.util.TreeSet<>(fonts.aurora$fontSets().keySet());
                long checksum = 1;
                int count = 0;
                for (var id : ids) if (id.getNamespace().equals("aurora")) {
                    var style = net.minecraft.network.chat.Style.EMPTY.withFont(
                            new net.minecraft.network.chat.FontDescription.Resource(id));
                    for (String sample : java.util.List.of("Aurora 0123456789", "\u00c9\u03a9\u0416\u4e16\u754c", "\u00a7k")) {
                        checksum = checksum * 31 + client.font.width(Component.literal(sample).withStyle(style));
                        checksum = checksum * 31 + client.font.width(Component.literal(sample).withStyle(style.withBold(true).withItalic(true)));
                    }
                    var font = fonts.aurora$fontSets().get(id);
                    if (font instanceof com.aurora.client.ui.util.DeferredFontSet deferred && deferred.aurora$pending())
                        throw new AssertionError("Font was not initialized by text lookup: " + id);
                    count++;
                }
                System.out.println("[parity-probe] font-check faces=" + count + " widthChecksum=" + checksum);
            }
            case "font-first-use" -> {
                var style = net.minecraft.network.chat.Style.EMPTY.withFont(
                        new net.minecraft.network.chat.FontDescription.Resource(
                                net.minecraft.resources.Identifier.parse(args[1])));
                long start = System.nanoTime();
                int width = client.font.width(Component.literal("Aurora 0123456789 \u00c9\u03a9\u0416\u4e16\u754c").withStyle(style));
                System.out.println("[parity-probe] font-first-use " + args[1] + " width=" + width
                        + " us=" + (System.nanoTime() - start) / 1000);
            }
            case "font-specimen" -> NativeUi.setScreen(client, new FontSpecimenScreen());
            case "sdf-compare" -> NativeUi.setScreen(client,
                    new SdfCompareScreen(args.length > 1 ? args[1] : "1"));
            case "benchmark" -> {
                var output = root.resolve(args[1] + ".json");
                if (Files.exists(output)) throw new IllegalStateException("Benchmark result already exists");
                var previous = client.options.inactivityFpsLimit().get();
                client.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
                boolean started = com.aurora.client.diagnostics.FrameBenchmark.start(2, 8,
                        com.aurora.client.ui.util.RenderUtil::fillsSubmitted,
                        com.aurora.client.ui.render.blur.BlurPanelRenderer::capturesSubmitted, stats -> {
                            client.options.inactivityFpsLimit().set(previous);
                            try { Files.writeString(output, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(stats) + "\n"); }
                            catch (java.io.IOException failure) { throw new RuntimeException(failure); }
                            System.out.println("[parity-probe] benchmark " + args[1] + "\n"
                                    + com.aurora.client.diagnostics.DiagnosticsReport.build(stats));
                        });
                if (!started) {
                    client.options.inactivityFpsLimit().set(previous);
                    throw new IllegalStateException("Benchmark already running");
                }
            }
            case "detail" -> {
                FeatureRegistry.modules();
                var feature = FeatureRegistry.all().stream().filter(f -> f.id.equals(args[1])).findFirst().orElseThrow();
                NativeUi.setScreen(client, FeatureDetailScreen.destination(AuroraScreen.create(), feature));
            }
            case "search" -> {
                for (var child : NativeUi.screen(client).children()) if (child instanceof EditBox box) {
                    NativeUi.screen(client).setFocused(box); box.setFocused(true); box.setValue(text.substring(7));
                }
            }
            case "escape" -> NativeUi.screen(client).keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
            // `config <field> <json>` sets one AuroraConfig field in memory (disposable fixtures only).
            case "config" -> {
                String[] kv = text.substring(7).split(" ", 2);
                var field = AuroraConfig.class.getField(kv[0]);
                field.set(config, new com.google.gson.Gson().fromJson(kv[1], field.getGenericType()));
                System.out.println("[parity-probe] config " + kv[0] + "=" + field.get(config));
            }
            // `hold <forward|left|back|right|jump|attack|use> <on|off>` holds a vanilla key mapping down.
            case "hold" -> {
                String[] kv = text.substring(5).split(" ");
                var options = client.options;
                var mapping = switch (kv[0]) {
                    case "forward" -> options.keyUp; case "left" -> options.keyLeft;
                    case "back" -> options.keyDown; case "right" -> options.keyRight;
                    case "jump" -> options.keyJump; case "attack" -> options.keyAttack;
                    case "use" -> options.keyUse;
                    default -> throw new IllegalArgumentException("Unknown key " + kv[0]);
                };
                mapping.setDown(kv[1].equals("on"));
            }
            // Text-field editing through the screen's own handlers (the mixin's hooks included):
            // `type <text>` sends each code point as a character, `key <name> [ctrl] [shift]` one key.
            case "type" -> text.substring(5).codePoints().forEach(cp ->
                    NativeUi.screen(client).charTyped(new net.minecraft.client.input.CharacterEvent(cp)));
            case "key" -> {
                String[] parts = text.substring(4).split(" ");
                int mods = 0;
                for (int i = 1; i < parts.length; i++) mods |= modifierBit(parts[i]);
                // Native key and SDL keycode: 26.3 EditBox switches on the keycode (shortcutKey),
                // 26.1/26.2 on the GLFW key (where the second slot is an ignored scancode).
                var key = switch (parts[0]) {
                    case "z" -> new int[]{InputConstants.KEY_Z, 'z'};
                    case "y" -> new int[]{InputConstants.KEY_Y, 'y'};
                    case "a" -> new int[]{InputConstants.KEY_A, 'a'};
                    case "left" -> new int[]{InputConstants.KEY_LEFT, 0x40000050};
                    case "right" -> new int[]{InputConstants.KEY_RIGHT, 0x4000004F};
                    case "home" -> new int[]{InputConstants.KEY_HOME, 0x4000004A};
                    case "end" -> new int[]{InputConstants.KEY_END, 0x4000004D};
                    case "backspace" -> new int[]{InputConstants.KEY_BACKSPACE, 8};
                    case "enter" -> new int[]{InputConstants.KEY_RETURN, 13};
                    default -> throw new IllegalArgumentException("Unknown key " + parts[0]);
                };
                NativeUi.screen(client).keyPressed(new KeyEvent(key[0], key[1], mods));
            }
            case "field-state" -> {
                if (!(NativeUi.screen(client).getFocused() instanceof EditBox box)) throw new AssertionError("No focused text field");
                System.out.println("[parity-probe] field value=\"" + box.getValue() + "\" cursor=" + box.getCursorPosition()
                        + " selected=\"" + box.getHighlighted() + "\"");
            }
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
                if (!(client instanceof com.aurora.client.mixin.MinecraftProfileAccessor a) || !a.aurora$profileFuture().isDone())
                    throw new AssertionError("profile accessor missing or profile still pending");
                System.out.println("[parity-probe] perf-loading closeDelayMs=" + closeDelay);
            }
            case "unlocked" -> {
                client.options.framerateLimit().set(260);
                client.options.enableVsync().set(false);
            }
            case "f3" -> client.debugEntries.setOverlayVisible(Boolean.parseBoolean(args[1]));
            case "fps" -> {
                // "fps <seconds> <label>": skips the first second, then reads once a second.
                restoreFpsOptions(client);
                fpsPreviousInactivity = client.options.inactivityFpsLimit().get();
                client.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
                fpsReadings.clear();
                fpsNextReading = System.nanoTime() + 2_000_000_000L;
                fpsUntil = fpsNextReading + Long.parseLong(args[1]) * 1_000_000_000L;
                fpsLabel = args[2];
            }
            case "animated-inventory" -> {
                // Items whose models use animated sprites, in every inventory slot.
                var items = java.util.List.of(net.minecraft.world.item.Items.MAGMA_BLOCK, net.minecraft.world.item.Items.SEA_LANTERN,
                        net.minecraft.world.item.Items.PRISMARINE, net.minecraft.world.item.Items.COMMAND_BLOCK,
                        net.minecraft.world.item.Items.CHAIN_COMMAND_BLOCK, net.minecraft.world.item.Items.REPEATING_COMMAND_BLOCK,
                        net.minecraft.world.item.Items.STONECUTTER, net.minecraft.world.item.Items.SCULK,
                        net.minecraft.world.item.Items.SCULK_CATALYST, net.minecraft.world.item.Items.SCULK_SENSOR,
                        net.minecraft.world.item.Items.LANTERN, net.minecraft.world.item.Items.SOUL_LANTERN);
                var server = client.getSingleplayerServer();
                var id = client.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(id);
                    // Survival shows all 36 slots; creative opens on a tab of static blocks.
                    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new net.minecraft.world.item.ItemStack(items.get(i % items.size())));
                });
                Thread.sleep(1000);
                NativeUi.setScreen(client, new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player));
            }
            case "stress" -> {
                // "stress <chests> <mobs>": a storage room of closed chests above the
                // player and resting mobs around them, for measuring per-tick costs.
                int chests = Integer.parseInt(args[1]);
                int mobs = Integer.parseInt(args[2]);
                var server = client.getSingleplayerServer();
                var id = client.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(id);
                    var level = player.level();
                    var base = player.blockPosition().above(4);
                    for (int i = 0; i < chests; i++) {
                        var pos = base.offset((i % 32) - 16, i / 1024, ((i / 32) % 32) - 16);
                        level.setBlock(pos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 2);
                    }
                    // Looked up by id: the type constants moved to EntityTypes in 26.3.
                    var pigType = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                            .getOptional(net.minecraft.resources.Identifier.withDefaultNamespace("pig")).orElseThrow();
                    for (int i = 0; i < mobs; i++) {
                        var pig = (net.minecraft.world.entity.Mob) pigType.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                        pig.setNoAi(true);
                        pig.setPos(player.getX() + (i % 20) - 10, player.getY(), player.getZ() + (i / 20) - 10);
                        level.addFreshEntity(pig);
                    }
                });
            }
            case "count" -> {
                // "count <seconds> <label>": work counters over a window.
                Counters.LISTENER_UPDATES.set(0);
                Counters.TEXT_EFFECTS.set(0);
                Counters.ATLAS_DRAWS.set(0);
                countSteps = com.aurora.client.util.SpriteAnimationClock.ticks();
                countStart = System.nanoTime();
                countUntil = countStart + Long.parseLong(args[1]) * 1_000_000_000L;
                countLabel = args[2];
            }
            case "underline" -> {
                config.textUnderline = Boolean.parseBoolean(args[1]);
            }
            case "sprite-mips" -> {
                // CPU bytes held in sprite mip levels above 0, over every loaded atlas.
                long bytes = 0, sprites = 0;
                var byPath = net.minecraft.client.renderer.texture.TextureManager.class.getDeclaredField("byPath");
                byPath.setAccessible(true);
                var mipsField = net.minecraft.client.renderer.texture.SpriteContents.class.getDeclaredField("byMipLevel");
                mipsField.setAccessible(true);
                var spritesField = net.minecraft.client.renderer.texture.TextureAtlas.class.getDeclaredField("sprites");
                spritesField.setAccessible(true);
                for (Object texture : ((java.util.Map<?, ?>) byPath.get(client.getTextureManager())).values()) {
                    if (!(texture instanceof net.minecraft.client.renderer.texture.TextureAtlas atlas)) continue;
                    for (var sprite : (java.util.List<net.minecraft.client.renderer.texture.TextureAtlasSprite>) spritesField.get(atlas)) {
                        var levels = (com.mojang.blaze3d.platform.NativeImage[]) mipsField.get(sprite.contents());
                        sprites++;
                        for (int i = 1; i < levels.length; i++) bytes += (long) levels[i].getWidth() * levels[i].getHeight() * 4;
                    }
                }
                System.out.println("[parity-probe] sprite-mips " + sprites + " sprites hold " + bytes / 1024 + " KiB above level 0");
            }
            case "minimap" -> {
                // "minimap <on|off>": the minimap HUD, rotating with the player so turning repaints it.
                config.minimapEnabled = args[1].equals("on");
                config.minimapRotateWithPlayer = true;
                // The world map engine captures terrain only while enabled; both maps ship off.
                config.worldMapEnabled = true;
            }
            case "spin" -> spinPerTick = Float.parseFloat(args[1]);
            case "particles" -> particlesPerTick = Integer.parseInt(args[1]);
            case "teleport" -> {
                // "teleport <blocks>": moves the player along x on the server, to load fresh chunks.
                var server = client.getSingleplayerServer();
                var id = client.player.getUUID();
                double dx = Double.parseDouble(args[1]);
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(id);
                    player.teleportTo(player.getX() + dx, player.getY() + 40, player.getZ());
                });
            }
            case "cfg" -> AuroraConfig.class.getField(args[1]).setBoolean(config, Boolean.parseBoolean(args[2]));
            case "factory" -> {
                var defaults = new AuroraConfig();
                for (var field : AuroraConfig.class.getFields()) {
                    int flags = field.getModifiers();
                    if (!java.lang.reflect.Modifier.isStatic(flags) && !java.lang.reflect.Modifier.isFinal(flags))
                        field.set(config, field.get(defaults));
                }
                ThemeManager.reload();
            }
            case "screen" -> {
                var screen = NativeUi.screen(client);
                System.out.println("[parity-probe] screen " + screen + (screen == null ? "" :
                        " gui=" + screen.width + "x" + screen.height
                        + " scale=" + client.getWindow().getGuiScale() + " focused=" + screen.getFocused()));
            }
            case "world-map" -> {
                config.worldMapEnabled = true;
                NativeUi.setScreen(client, new com.aurora.client.worldmap.screen.WorldMapScreen());
            }
            case "reload" -> {
                reloadStarted = System.nanoTime();
                client.reloadResourcePacks();
            }
            default -> throw new IllegalArgumentException(text);
        }
        System.out.println("[parity-probe] " + text);
    }

    private void restoreFpsOptions(Minecraft client) {
        if (fpsPreviousInactivity == null) return;
        client.options.inactivityFpsLimit().set(fpsPreviousInactivity);
        fpsPreviousInactivity = null;
    }
}
