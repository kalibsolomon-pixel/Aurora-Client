package mappingprobe;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.AuroraConfig.Waypoint;
import com.aurora.client.feature.impl.WaypointFeature;
import com.aurora.client.screen.WaypointEditScreen;
import com.aurora.client.screen.WaypointManagerScreen;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.worldmap.*;
import com.aurora.client.worldmap.screen.WorldMapScreen;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Repeatable native integration driver, with observed assertions and real GPU screenshots.
 * Only marked disposable roots. API-driven interactions are not physical mouse input.
 * Bounded frame observations export after measurement, never in a frame callback. */
public final class Acceptance implements ClientModInitializer {
    private static final List<Map<String,Object>> checks = new ArrayList<>();
    private static final long[] frames = new long[100_000];
    private static final int[] stages = new int[100_000];
    private static int frameCount, stage;
    private static long started, stageAt, head, titleAt;
    private static boolean opening, finishing;
    private static boolean driving;
    private static Path output;
    private static Waypoint target;
    private static String identity;
    private static final boolean RESUME = Boolean.getBoolean("aurora.mapping.resume");
    private static final boolean MULTIPLAYER = Boolean.getBoolean("aurora.mapping.multiplayer");
    private static void connect(Minecraft mc) {
        String address = "127.0.0.1:25579";
        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new TitleScreen(), mc,
                net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),
                new net.minecraft.client.multiplayer.ServerData("Disposable mapping server", address,
                        net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
    }
    private static void check(String name, boolean passed) {
        checks.add(Map.of("check", name, "passed", passed, "stage", stage));
        System.out.println("[mapping-acceptance] " + name + "=" + passed);
    }
    @Override public void onInitializeClient() {
        output = Path.of(System.getProperty("aurora.mapping.output"));
        if (!Files.exists(output.resolve("DISPOSABLE-RESEARCH.txt"))) throw new IllegalStateException("Disposable marker required");
    }
    public static void begin() {
        if (output == null || finishing || driving) return;
        head = System.nanoTime();
        driving = true;
        try { drive(Minecraft.getInstance()); }
        catch (Throwable failure) {
            failure.printStackTrace(); check("unexpected-" + failure.getClass().getSimpleName(), false);
            finish(Minecraft.getInstance());
        }
        finally { driving = false; }
    }
    public static void end() {
        if (output == null || finishing || driving) return;
        if (frameCount < frames.length) { frames[frameCount] = head; stages[frameCount++] = stage; }
    }
    private static void next(int value) { stage = value; stageAt = System.nanoTime(); }
    private static double elapsed() { return (System.nanoTime() - stageAt) / 1e9; }
    private static void command(Minecraft mc, String text) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), text));
    }
    private static void shot(Minecraft mc, String name) {
        net.minecraft.client.Screenshot.grab(mc.gameDirectory, "mapping-" + name + ".png",
                mc.getMainRenderTarget(), 1, message -> System.out.println("[mapping-shot] " + message.getString()));
    }
    private static String dim(Minecraft mc) { return mc.level.dimension().identifier().toString(); }
    private static void drive(Minecraft mc) throws Exception {
        if (stage == 0) {
            if (mc.getOverlay() == null && mc.screen != null && (mc.screen instanceof TitleScreen
                    || mc.screen.getClass().getSimpleName().equals("AuroraTitleScreen"))) {
                if (titleAt == 0) titleAt = System.nanoTime();
                if (!opening && System.nanoTime() - titleAt > 3_000_000_000L) {
                    opening = true;
                    if (MULTIPLAYER) connect(mc);
                    else mc.createWorldOpenFlows().openWorld("Cperf1a", () -> mc.setScreen(new TitleScreen()));
                }
            }
            if (mc.level != null && mc.player != null && mc.screen == null) {
                started = System.nanoTime(); identity = MapIdentity.current().key();
                if (MULTIPLAYER) next(100);
                else {
                    command(mc, "gamemode spectator @a"); command(mc, "tp @a 0 100 0 0 30");
                    next(RESUME ? 90 : 1);
                }
            }
            return;
        }
        if (stage == 90) {
            if (elapsed() < 20) return;
            var loaded = WaypointFeature.get().currentWorldWaypoints();
            check("relaunch-waypoint-id-and-edit", loaded.stream().anyMatch(w -> w != null
                    && w.name.equals("Acceptance edited") && w.group.equals("Acceptance")));
            check("relaunch-large-list", loaded.size() >= 10_001);
            check("relaunch-terrain", WorldMapClient.get().sampleSurfaceAbgr(0, 0) != 0);
            shot(mc, "relaunch"); next(91); return;
        }
        if (stage == 91) { if (elapsed() > Integer.getInteger("aurora.mapping.reviewSeconds", 60)) finish(mc); return; }
        if (mc.level == null || mc.player == null) {
            if (stage == 16 && elapsed() > 4 && mc.getOverlay() == null) {
                mc.createWorldOpenFlows().openWorld("Cperf1a", () -> mc.setScreen(new TitleScreen())); next(17);
            }
            if (stage == 102 && elapsed() > 4 && mc.getOverlay() == null) { connect(mc); next(103); }
            return;
        }
        var cfg = AuroraConfig.get(); var feature = WaypointFeature.get(); var engine = WorldMapClient.get();
        switch (stage) {
            case 100 -> {
                if (elapsed() < 20) return;
                check("multiplayer-address-identity", identity.equals("map-" + MapIdentity.digest("server:127.0.0.1:25579")));
                check("multiplayer-isolated-from-singleplayer-waypoints", feature.currentWorldWaypoints().isEmpty());
                check("multiplayer-terrain", engine.sampleSurfaceAbgr(mc.player.blockPosition().getX(), mc.player.blockPosition().getZ()) != 0);
                var position = mc.player.blockPosition();
                mc.setScreen(new WaypointEditScreen(new WorldMapScreen(), new Waypoint("Multiplayer acceptance",
                        position.getX(), position.getY(), position.getZ(), dim(mc), 0xFF55EEAA), true));
                mc.screen.children().stream().filter(w -> w instanceof ButtonWidget b && b.getMessage().getString().equals("Save"))
                        .map(w -> (ButtonWidget) w).findFirst().orElseThrow().onPress(null);
                target = feature.currentWorldWaypoints().getLast();
                check("new-marker-selected-for-navigation", target.id.equals(feature.navigationId()));
                mc.setScreen(new WorldMapScreen()); next(101);
            }
            case 101 -> {
                if (elapsed() < 8) return;
                shot(mc, "multiplayer-map");
                next(102); mc.setScreen(null); mc.disconnectFromWorld(Component.literal("Multiplayer reconnect"));
            }
            case 103 -> {
                if (elapsed() < 20) return;
                check("multiplayer-reconnect-identity", identity.equals(MapIdentity.current().key()));
                check("multiplayer-reconnect-waypoint", feature.currentWorldWaypoints().stream().anyMatch(w -> w.id.equals(target.id)));
                check("multiplayer-reconnect-terrain", engine.sampleSurfaceAbgr(target.x, target.z) != 0);
                shot(mc, "multiplayer-reconnect"); next(104);
            }
            case 104 -> { if (elapsed() > 5) finish(mc); }
            case 1 -> {
                if (elapsed() < 25) return;
                check("terrain-captured", engine.sampleSurfaceAbgr(0, 0) != 0);
                target = feature.dropAtPlayer("Acceptance", 0xFF66CCFF, false);
                feature.navigateTo(target); shot(mc, "minimap"); next(2);
            }
            case 2 -> {
                if (elapsed() < 3) return;
                check("waypoint-in-shared-nearby-view", feature.nearbyWaypoints().contains(target));
                mc.setScreen(new WorldMapScreen()); next(3);
            }
            case 3 -> {
                if (elapsed() < 6) return;
                shot(mc, "world-map");
                mc.screen.mouseScrolled(250, 150, 0, -1); next(4);
            }
            case 4 -> {
                if (elapsed() < 4) return;
                shot(mc, "world-map-zoom");
                for (int i = 0; i < 4; i++) mc.screen.mouseScrolled(250, 150, 0, -1);
                next(41);
            }
            case 41 -> {
                if (elapsed() < 8) return;
                shot(mc, "world-map-overview");
                var coordinates = mc.screen.children().stream().filter(w -> w instanceof EditBox)
                        .map(w -> (EditBox) w).findFirst().orElseThrow();
                coordinates.setValue("-2048 1024");
                mc.screen.children().stream().filter(w -> w instanceof ButtonWidget b && b.getMessage().getString().equals("Go"))
                        .map(w -> (ButtonWidget) w).findFirst().orElseThrow().onPress(null);
                var left = new net.minecraft.client.input.MouseButtonEvent(240, 180, new net.minecraft.client.input.MouseButtonInfo(0, 0));
                mc.screen.mouseClicked(left, false);
                check("map-drag-accepted", mc.screen.mouseDragged(left, 100, -40));
                mc.screen.mouseReleased(left);
                next(42);
            }
            case 42 -> {
                if (elapsed() < 5) return;
                shot(mc, "world-map-pan-large");
                var right = new net.minecraft.client.input.MouseButtonEvent(mc.screen.width * .5, mc.screen.height * .5,
                        new net.minecraft.client.input.MouseButtonInfo(1, 0));
                mc.screen.mouseClicked(right, false); next(43);
            }
            case 43 -> {
                if (elapsed() < 2) return;
                check("map-create-opens-editor", mc.screen instanceof WaypointEditScreen);
                var name = mc.screen.children().stream().filter(w -> w instanceof EditBox).map(w -> (EditBox) w).findFirst().orElseThrow();
                name.setValue("Created from map");
                mc.screen.children().stream().filter(w -> w instanceof ButtonWidget b && b.getMessage().getString().equals("Save"))
                        .map(w -> (ButtonWidget) w).findFirst().orElseThrow().onPress(null);
                check("map-created-waypoint-shared", feature.currentWorldWaypoints().stream().anyMatch(w -> w.name.equals("Created from map")));
                mc.setScreen(new WaypointEditScreen(new WorldMapScreen(), target, false)); next(5);
            }
            case 5 -> {
                if (elapsed() < 3) return;
                var fields = mc.screen.children().stream().filter(w -> w instanceof EditBox).map(w -> (EditBox) w).toList();
                fields.get(0).setValue("Acceptance edited"); fields.get(1).setValue("24 70 32");
                fields.get(3).setValue("Acceptance"); fields.get(4).setValue("Native lifecycle check");
                fields.get(5).setValue("FF66CCFF");
                shot(mc, "editor");
                mc.screen.children().stream().filter(w -> w instanceof ButtonWidget b && b.getMessage().getString().equals("Save"))
                        .map(w -> (ButtonWidget) w).findFirst().orElseThrow().onPress(null);
                check("editor-propagated", target.name.equals("Acceptance edited") && target.x == 24 && target.group.equals("Acceptance"));
                List<Waypoint> list = feature.currentWorldWaypoints();
                for (int i = 0; i < 10_000; i++) {
                    Waypoint w = new Waypoint("Stress " + i, (i % 100) * 16 - 800, 64,
                            (i / 100) * 16 - 800, dim(mc), 0xFFFFAA66); w.group = "Stress"; list.add(w);
                }
                feature.touch(); feature.navigateTo(target);
                mc.setScreen(new WaypointManagerScreen(new WorldMapScreen())); next(6);
            }
            case 6 -> {
                if (elapsed() < 8) return;
                check("many-waypoint-draw-cap", feature.nearbyWaypoints().size() <= cfg.waypointMaxVisible);
                check("navigation-priority", !feature.nearbyWaypoints().isEmpty() && feature.nearbyWaypoints().getFirst() == target);
                check("bounded-manager-controls", mc.screen.children().size() < 100);
                shot(mc, "manager-10001");
                mc.screen.mouseScrolled(200, 160, 0, -120); next(7);
            }
            case 7 -> {
                if (elapsed() < 3) return;
                shot(mc, "manager-scrolled");
                mc.setScreen(new WorldMapScreen()); engine.cycleDimension(); next(8);
            }
            case 8 -> {
                if (elapsed() < 7) return;
                check("archive-independent-from-live-terrain", !engine.isViewingLiveDimension()
                        && engine.sampleSurfaceAbgr(0, 0) != 0 && identity.equals(engine.identity().key()));
                shot(mc, "archive"); mc.setScreen(null);
                command(mc, "execute in minecraft:the_nether run tp @a 0 110 0"); next(9);
            }
            case 9 -> {
                if (!dim(mc).equals("minecraft:the_nether")) { if (elapsed() > 60) throw new IllegalStateException("Nether transition timed out"); return; }
                if (elapsed() < 10) return;
                check("dimension-world-isolation", identity.equals(MapIdentity.current().key()) && engine.dimensionId().equals(dim(mc)));
                shot(mc, "nether"); command(mc, "execute in minecraft:overworld run tp @a 0 100 0"); next(10);
            }
            case 10 -> {
                if (!dim(mc).equals("minecraft:overworld")) return;
                if (elapsed() < 8) return;
                check("return-to-original-dimension", feature.currentWorldWaypoints().contains(target) && engine.sampleSurfaceAbgr(0, 0) != 0);
                cfg.minimapEnabled = false; cfg.worldMapEnabled = false; next(11);
            }
            case 11 -> {
                if (elapsed() < 3) return;
                check("disable-releases-live-context", engine.identity() == null);
                cfg.minimapEnabled = true; next(12);
            }
            case 12 -> {
                if (elapsed() < 10) return;
                check("minimap-only-engine", !cfg.worldMapEnabled && engine.identity() != null && engine.sampleSurfaceAbgr(0, 0) != 0);
                cfg.worldMapEnabled = true;
                int count = feature.currentWorldWaypoints().size(); AuroraConfig.resetByPrefix("waypoint"); cfg.waypointsEnabled = true;
                check("settings-reset-preserves-waypoints", feature.currentWorldWaypoints().size() == count);
                mc.reloadResourcePacks(); next(13);
            }
            case 13 -> {
                if (elapsed() < 12 || mc.getOverlay() != null) return;
                check("resource-reload-terrain", engine.sampleSurfaceAbgr(0, 0) != 0);
                cfg.minimapCircular = false; cfg.minimapRotateWithPlayer = true; cfg.minimapShowBiome = true;
                mc.options.guiScale().set(3); mc.resizeDisplay();
                mc.setScreen(new WaypointEditScreen(new WorldMapScreen(), target, false)); next(14);
            }
            case 14 -> {
                if (elapsed() < 5) return;
                shot(mc, "editor-scale3"); mc.getWindow().setWindowed(1600, 1000);
                mc.options.guiScale().set(4); mc.resizeDisplay();
                mc.setScreen(new WaypointManagerScreen(new WorldMapScreen())); next(15);
            }
            case 15 -> {
                if (elapsed() < 5) return;
                shot(mc, "manager-scale4"); cfg.minimapEnabled = true; AuroraConfig.save();
                next(16); mc.setScreen(null); mc.disconnectFromWorld(Component.literal("Mapping reconnect acceptance"));
            }
            case 17 -> {
                if (elapsed() < 18) return;
                check("reconnect-identity", identity.equals(MapIdentity.current().key()));
                check("reconnect-waypoint-id", feature.currentWorldWaypoints().stream().anyMatch(w -> w != null && w.id.equals(target.id)));
                check("reconnect-terrain", engine.sampleSurfaceAbgr(0, 0) != 0);
                shot(mc, "reconnect-square-heading-up"); next(18);
            }
            case 18 -> {
                // Native pointer review window; leave the game available for additional UI checks.
                if (elapsed() > Integer.getInteger("aurora.mapping.reviewSeconds", 90)) finish(mc);
            }
        }
    }
    private static void finish(Minecraft mc) {
        if (finishing) return; finishing = true;
        mc.setScreen(null);
        if (mc.level != null) mc.disconnectFromWorld(Component.literal("Mapping acceptance complete"));
        try {
            Map<String,Object> report = new LinkedHashMap<>();
            report.put("checks", checks); report.put("frames", frameCount); report.put("resume", RESUME);
            report.put("multiplayer", MULTIPLAYER);
            report.put("elapsedSeconds", (System.nanoTime() - started) / 1e9);
            report.put("mods", net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().stream()
                    .map(m -> m.getMetadata().getId() + ":" + m.getMetadata().getVersion()).sorted().toList());
            Files.writeString(output.resolve(RESUME ? "resume-report.json" : "acceptance-report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            try (var writer = Files.newBufferedWriter(output.resolve(RESUME ? "resume-frames.csv" : "acceptance-frames.csv"))) {
                writer.write("head_ns,stage\n");
                for (int i = 0; i < frameCount; i++) writer.write(frames[i] + "," + stages[i] + "\n");
            }
        } catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }
}
