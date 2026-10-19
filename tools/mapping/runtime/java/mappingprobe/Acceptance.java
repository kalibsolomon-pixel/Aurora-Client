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
    private static final int[] states = new int[100_000];
    private static final Map<String,Object> clock = new LinkedHashMap<>();
    private static int frameCount, stage;
    private static long started, stageAt, head, titleAt;
    private static boolean opening, finishing;
    private static boolean driving;
    private static Path output;
    private static Waypoint target;
    private static String identity;
    private static final boolean RESUME = Boolean.getBoolean("aurora.mapping.resume");
    private static final boolean MULTIPLAYER = Boolean.getBoolean("aurora.mapping.multiplayer");
    private static final boolean EXTENDED = Boolean.getBoolean("aurora.mapping.extended");
    private static final boolean RENDERING = Boolean.getBoolean("aurora.mapping.rendering");
    private static final int[][] SIZES = {{1920,1080,2},{1920,1080,3},{2560,1440,3},{2560,1440,4},{3840,2160,4},{3840,2160,6}};
    private static int renderCase;
    private static long stableRevision, stableAt, stationaryPaint;
    private static final List<Map<String,Object>> renderEvidence = new ArrayList<>();
    private static void renderingSize(Minecraft mc) {
        int[] size = SIZES[renderCase];
        mc.setScreen(null); mc.getWindow().setWindowed(size[0],size[1]);
        mc.options.guiScale().set(size[2]); mc.resizeDisplay();
        var cfg = AuroraConfig.get(); cfg.minimapCircular = renderCase % 2 == 0;
        var minimap = com.aurora.client.AuroraClient.modules().get("minimap");
        minimap.scale = renderCase % 2 == 0 ? 1 : 1.25f;
    }
    private static int explorationStep, maxResident, maxLoads, maxOverviews, maxRegionTextures, maxOverviewTextures;
    private static final List<Map<String, Object>> observed = new ArrayList<>();
    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true);
        return field.get(owner);
    }
    private static void moveToRegion(Minecraft mc) {
        int x = (explorationStep % 8) * 512 + 256, z = (explorationStep / 8) * 512 + 256;
        command(mc, "tp @a " + x + " 180 " + z + " 0 45");
    }
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
        if (frameCount < frames.length) {
            var mc = Minecraft.getInstance();
            frames[frameCount] = head; stages[frameCount] = stage;
            states[frameCount++] = (mc.level != null ? 1 : 0) | (mc.isWindowActive() ? 2 : 0)
                    | (mc.isPaused() ? 4 : 0) | (mc.screen != null ? 8 : 0);
        }
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
                clock.put("nanoBefore",System.nanoTime());
                clock.put("qpcTick",org.lwjgl.glfw.GLFW.glfwGetTimerValue());
                clock.put("qpcFrequency",org.lwjgl.glfw.GLFW.glfwGetTimerFrequency());
                clock.put("nanoAfter",System.nanoTime());
                if (MULTIPLAYER) next(100);
                else {
                    command(mc, "gamemode spectator @a"); command(mc, "tp @a 0 100 0 0 30");
                    if (RENDERING) {
                        command(mc,"tick freeze");
                        mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                        var cfg = AuroraConfig.get();
                        cfg.hitboxEnabled = false; cfg.hitregEnabled = false;
                        cfg.minimapShowCoords = true; cfg.minimapShowBiome = true; cfg.minimapSize = 128;
                        cfg.minimapRotateWithPlayer = true; cfg.minimapBorderColor = 0; cfg.minimapCoordColor = -1;
                        cfg.waypointDisplay = AuroraConfig.WaypointDisplay.BOTH; cfg.waypointLabelScale = 1.25;
                        cfg.textRendererMode = AuroraConfig.TextRendererMode.AURORA_ONLY; cfg.activeFont = AuroraConfig.FontType.INTER;
                        var feature = WaypointFeature.get();
                        for (int i = 0; i < 18; i++) {
                            var marker = new Waypoint("Marker " + (i + 1), (i % 6) * 12 - 30, 80,
                                    20 + (i / 6) * 24, dim(mc), new int[]{0xFF66CCFF,0xFFFFB46E,0xFFA8D9AE}[i % 3]);
                            marker.marker = AuroraConfig.WaypointMarker.values()[i % 3]; marker.group = "Rendering";
                            feature.addWaypoint(marker); if (i == 0) target = marker;
                        }
                        feature.navigateTo(target); renderingSize(mc); next(300);
                    }
                    else if (EXTENDED) { moveToRegion(mc); next(200); }
                    else next(RESUME ? 90 : 1);
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
            if (stage == 203 && elapsed() > 4 && mc.getOverlay() == null) {
                mc.createWorldOpenFlows().openWorld("Cperf1a", () -> mc.setScreen(new TitleScreen())); next(204);
            }
            return;
        }
        var cfg = AuroraConfig.get(); var feature = WaypointFeature.get(); var engine = WorldMapClient.get();
        if (EXTENDED && engine.identity() != null) {
            Object live = field(engine, "live");
            var regions = (com.aurora.client.worldmap.cache.RegionCache) field(live, "regions");
            maxResident = Math.max(maxResident, regions.size());
            maxLoads = Math.max(maxLoads, (int) field(live, "loads"));
            var overviewTiles = (Map<?, ?>) field(field(live, "overviews"), "tiles");
            maxOverviews = Math.max(maxOverviews, overviewTiles.size());
            int regionTextures = 0, overviewTextures = 0;
            for (Object tile : ((Map<?, ?>) field(regions, "tiles")).values())
                if (((com.aurora.client.worldmap.cache.RegionCache.RegionTile) tile).textureId() != null) regionTextures++;
            for (Object tile : overviewTiles.values())
                if (((com.aurora.client.worldmap.cache.OverviewCache.OverviewTile) tile).textureId() != null) overviewTextures++;
            maxRegionTextures = Math.max(maxRegionTextures, regionTextures);
            maxOverviewTextures = Math.max(maxOverviewTextures, overviewTextures);
        }
        switch (stage) {
            case 300 -> {
                long revision = engine.terrainRevision();
                if (revision != stableRevision) { stableRevision = revision; stableAt = System.nanoTime(); }
                if (elapsed() > 60) throw new IllegalStateException("Terrain never settled");
                if (elapsed() < 15 || System.nanoTime() - stableAt < 2_000_000_000L) return;
                stationaryPaint = (long) field(com.aurora.client.AuroraClient.modules().get("minimap"), "lastPaint"); next(301);
            }
            case 301 -> {
                if (elapsed() < 3) return;
                check("stationary-terrain-no-repaint", stationaryPaint == (long) field(com.aurora.client.AuroraClient.modules().get("minimap"), "lastPaint"));
                next(302);
            }
            case 302 -> {
                if (elapsed() < 3) return;
                int[] size = SIZES[renderCase];
                check("native-framebuffer-" + renderCase, mc.getWindow().getWidth() == size[0] && mc.getWindow().getHeight() == size[1]);
                renderEvidence.add(Map.of("case",renderCase,"width",mc.getWindow().getWidth(),"height",mc.getWindow().getHeight(),
                        "guiScale",mc.getWindow().getGuiScale(),"circle",cfg.minimapCircular,
                        "hudScale",com.aurora.client.AuroraClient.modules().get("minimap").scale));
                shot(mc,"pixels-"+renderCase+"-hud"); mc.setScreen(new WorldMapScreen()); next(303);
            }
            case 303 -> {
                if (elapsed() < 3) return;
                shot(mc,"pixels-"+renderCase+"-map"); mc.setScreen(new WaypointManagerScreen(new WorldMapScreen())); next(304);
            }
            case 304 -> {
                if (elapsed() < 2) return;
                check("manager-controls-bounded-"+renderCase, mc.screen.children().size() < 100);
                shot(mc,"pixels-"+renderCase+"-manager"); mc.setScreen(new WaypointEditScreen(new WorldMapScreen(), target, false)); next(305);
            }
            case 305 -> {
                if (elapsed() < 2) return;
                shot(mc,"pixels-"+renderCase+"-editor"); mc.setScreen(null);
                if (++renderCase < SIZES.length) { renderingSize(mc); next(302); }
                else {
                    mc.setScreen(new com.aurora.client.screen.HudEditorScreen()); next(306);
                }
            }
            case 306 -> {
                if (elapsed() < 3) return;
                shot(mc,"pixels-hud-editor"); mc.setScreen(null); mc.reloadResourcePacks(); next(307);
            }
            case 307 -> {
                if (elapsed() < 10 || mc.getOverlay()!=null) return;
                check("render-resource-reload", engine.sampleSurfaceAbgr(0,0)!=0);
                mc.options.guiScale().set(6); mc.resizeDisplay(); cfg.minimapCircular=true;
                command(mc,"tp @a 0 100 0 0 30"); next(310);
            }
            case 310 -> {
                mc.player.setYRot(mc.player.getYRot()+.5f);
                if (elapsed() < 15) return;
                check("render-waypoints-preserved", feature.currentWorldWaypoints().contains(target));
                shot(mc,"pixels-final-hud");
                Files.writeString(output.resolve("render-evidence.json"),new GsonBuilder().setPrettyPrinting().create().toJson(renderEvidence));
                finish(mc);
            }
            case 200 -> {
                if (elapsed() < 4) return;
                int x = (explorationStep % 8) * 512 + 256, z = (explorationStep / 8) * 512 + 256;
                int pixel = engine.sampleSurfaceAbgr(x, z);
                boolean arrived = Math.abs(mc.player.getX() - x) < 2 && Math.abs(mc.player.getZ() - z) < 2;
                if (!arrived || pixel == 0) {
                    if (elapsed() > 25) throw new IllegalStateException("Region capture timed out at " + explorationStep);
                    return;
                }
                observed.add(Map.of("x", x, "z", z, "abgr", pixel));
                System.out.println("[mapping-extended] observed " + explorationStep + " regions=" + maxResident);
                if (++explorationStep < 40) { moveToRegion(mc); next(200); }
                else {
                    check("forty-observed-region-centers", observed.size() == 40);
                    check("eight-region-residency-bound", maxResident <= 8);
                    check("eight-inflight-load-bound", maxLoads <= 8);
                    check("overview-residency-bound", maxOverviews <= 2048);
                    engine.flushDirty(); mc.setScreen(new WorldMapScreen());
                    for (int i = 0; i < 5; i++) mc.screen.mouseScrolled(220, 140, 0, -1);
                    next(201);
                }
            }
            case 201 -> {
                if (elapsed() < 10) return;
                shot(mc, "extended-overview");
                var coordinates = mc.screen.children().stream().filter(w -> w instanceof EditBox)
                        .map(w -> (EditBox) w).findFirst().orElseThrow();
                coordinates.setValue("2048 1280");
                mc.screen.children().stream().filter(w -> w instanceof ButtonWidget b && b.getMessage().getString().equals("Go"))
                        .map(w -> (ButtonWidget) w).findFirst().orElseThrow().onPress(null);
                next(202);
            }
            case 202 -> {
                if (elapsed() < 12) return;
                shot(mc, "extended-large-map");
                Files.writeString(output.resolve("observed-regions.json"), new GsonBuilder().setPrettyPrinting()
                        .create().toJson(Map.of("identity", identity, "dimensionFolder", MapIdentity.dimensionFolder(dim(mc)),
                                "points", observed, "maxResident", maxResident, "maxLoads", maxLoads, "maxOverviews", maxOverviews,
                                "maxRegionTextures", maxRegionTextures, "maxOverviewTextures", maxOverviewTextures)));
                next(203); mc.setScreen(null); mc.disconnectFromWorld(Component.literal("Extended persistence check"));
            }
            case 204 -> {
                if (elapsed() < 15) return;
                check("extended-reconnect-identity", identity.equals(MapIdentity.current().key()));
                check("extended-reconnect-terrain", engine.sampleSurfaceAbgr(mc.player.blockPosition().getX(), mc.player.blockPosition().getZ()) != 0);
                shot(mc, "extended-reconnect"); next(205);
            }
            case 205 -> { if (elapsed() > 5) finish(mc); }
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
                var colorField = mc.screen.children().stream().filter(w -> w instanceof EditBox).map(w -> (EditBox) w).toList().get(5);
                int originalColor = target.color;
                colorField.setValue("FF112233");
                check("color-preview-is-transactional", target.color == originalColor
                        && ((Waypoint)field(mc.screen,"draft")).color == 0xFF112233);
                mc.screen.children().stream().filter(w -> w instanceof ButtonWidget b && b.getMessage().getString().equals("Cancel"))
                        .map(w -> (ButtonWidget)w).findFirst().orElseThrow().onPress(null);
                check("color-preview-cancel-preserves-record", target.color == originalColor);
                mc.setScreen(new WaypointEditScreen(new WorldMapScreen(), target, false));
                var fields = mc.screen.children().stream().filter(w -> w instanceof EditBox).map(w -> (EditBox) w).toList();
                fields.get(0).setValue("Acceptance edited"); fields.get(1).setValue("24 70 32");
                fields.get(3).setValue("Acceptance"); fields.get(4).setValue("Native lifecycle check");
                fields.get(5).setValue("FF3399CC");
                shot(mc, "editor");
                mc.screen.children().stream().filter(w -> w instanceof ButtonWidget b && b.getMessage().getString().equals("Save"))
                        .map(w -> (ButtonWidget) w).findFirst().orElseThrow().onPress(null);
                check("editor-propagated", target.name.equals("Acceptance edited") && target.x == 24 && target.group.equals("Acceptance"));
                check("editor-color-saved", target.color == 0xFF3399CC);
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
            report.put("extended", EXTENDED);
            report.put("rendering", RENDERING);
            report.put("clock", clock);
            report.put("elapsedSeconds", (System.nanoTime() - started) / 1e9);
            report.put("mods", net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().stream()
                    .map(m -> m.getMetadata().getId() + ":" + m.getMetadata().getVersion()).sorted().toList());
            Files.writeString(output.resolve(RESUME ? "resume-report.json" : "acceptance-report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
            try (var writer = Files.newBufferedWriter(output.resolve(RESUME ? "resume-frames.csv" : "acceptance-frames.csv"))) {
                writer.write("head_ns,stage,state\n");
                for (int i = 0; i < frameCount; i++) writer.write(frames[i] + "," + stages[i] + "," + states[i] + "\n");
            }
        } catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }
}
