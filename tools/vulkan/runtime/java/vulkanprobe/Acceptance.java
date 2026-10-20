package vulkanprobe;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.WaypointFeature;
import com.aurora.client.screen.*;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.render.blur.BlurTestScreen;
import com.aurora.client.worldmap.screen.WorldMapScreen;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import java.nio.file.*;
import java.util.*;

/** Actual client/gameplay fixture, excluded from shipping artifacts. API-driven. */
public final class Acceptance implements ClientModInitializer {
    static Path root;
    static final List<Map<String,Object>> checks = new ArrayList<>();
    static final List<Runnable> actions = new ArrayList<>();
    static long start, at;
    static int stage, action, frames, worldFrames, entityEvents, outlineEvents;
    static boolean driving, done;
    static double moveX, moveZ;
    static Minecraft mc;

    @Override public void onInitializeClient() {
        root = Path.of(System.getProperty("aurora.vulkan.output"));
        if (!Files.isRegularFile(root.resolve("DISPOSABLE-RESEARCH.txt"))) throw new IllegalStateException("Disposable fixture required");
        WorldRenderEvents.AFTER_ENTITIES.register(ctx -> entityEvents++);
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((ctx, state) -> { outlineEvents++; return true; });
        start = System.nanoTime();
    }
    static void check(String name, boolean value) {
        checks.add(Map.of("check",name,"passed",value,"frames",frames));
        System.out.println("[vulkan-acceptance] " + name + "=" + value);
    }
    static void shot(String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), 1,
                text -> System.out.println("[vulkan-shot] " + text.getString()));
    }
    static void command(String value) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), value));
    }
    static void state(String value) {
        try { Files.writeString(Path.of("blurtest_state.txt"), value); }
        catch (Exception failure) { throw new RuntimeException(failure); }
    }
    public static void frame() {
        if (root == null || driving || done) return;
        driving = true;
        try {
            mc = Minecraft.getInstance(); frames++;
            if (mc.level != null) worldFrames++;
            if (System.nanoTime() - start > 240_000_000_000L) throw new IllegalStateException("Acceptance timeout at " + stage + "/" + action);
            if (stage == 0) {
                if (mc.getOverlay() == null && mc.screen != null && (mc.screen.getClass().getSimpleName().equals("AuroraTitleScreen")
                        || Boolean.getBoolean("aurora.vulkan.isolate") && mc.screen instanceof TitleScreen)) {
                    if (at == 0) at = System.nanoTime();
                    if (elapsed() > 3) {
                        check("title-rendered", true); shot("01-title");
                        mc.createWorldOpenFlows().openWorld("New World", () -> mc.setScreen(new TitleScreen()));
                        stage = 1; at = System.nanoTime();
                    }
                }
            } else if (stage == 1 && mc.player != null && mc.level != null && mc.screen == null) {
                check("world-entered", true);
                if (Boolean.getBoolean("aurora.release.defaults")) {
                    var cfg = AuroraConfig.get();
                    check("release-default-font", cfg.activeFont == AuroraConfig.FontType.FIRA_SANS);
                    check("release-default-huds", cfg.armorHudEnabled && cfg.potionHudEnabled && cfg.keystrokesEnabled
                            && !cfg.infoEnabled && !cfg.cpsEnabled && !cfg.minimapEnabled);
                    check("release-default-layout", cfg.moduleLayouts.get("armor").offsetX == -241
                            && cfg.moduleLayouts.get("keystrokes").offsetX == 8);
                    shot("02-fresh-defaults");
                }
                configure(); setupActions(); stage = 2; at = System.nanoTime();
            } else if (stage == 2 && mc.getOverlay() == null && elapsed() > 1.5) {
                if (action < actions.size()) { actions.get(action++).run(); at = System.nanoTime(); }
                else { finish(); }
            }
        } catch (Throwable failure) {
            failure.printStackTrace(); check("unexpected-" + failure.getClass().getSimpleName(), false); finish();
        } finally { driving = false; }
    }
    static double elapsed() { return (System.nanoTime() - at) / 1e9; }
    static void configure() throws Exception {
        var cfg = AuroraConfig.get();
        for (String field : List.of("minimapEnabled","worldMapEnabled","waypointsEnabled","crosshairEnabled","crosshairAttackIndicatorEnabled","hitColorEnabled","glintColorEnabled","itemPhysicsEnabled","keystrokesEnabled")) AuroraConfig.class.getField(field).setBoolean(cfg,true);
        cfg.blockOverlayFillEnabled = true; cfg.blockOverlayOutlineColor = 0xFFFF00FF;
        cfg.glintColor = 0xFF00FF00;
        command("fill -12 119 -12 12 119 12 minecraft:stone");
        command("tp @a 0 120 0 0 20"); command("time set day"); command("weather clear");
        command("gamemode creative @a"); command("effect give @a speed 300 1 true");
        command("give @a diamond_sword[enchantment_glint_override=true]");
        command("item replace entity @a armor.chest with diamond_chestplate[enchantment_glint_override=true]");
        command("summon zombie 0 120 3 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"aurora_probe\"],Invulnerable:1b}");
        command("item replace entity @e[tag=aurora_probe,limit=1] armor.head with netherite_helmet");
        mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        WaypointFeature.get().addWaypoint(new AuroraConfig.Waypoint("Vulkan marker",5,120,8,mc.level.dimension().identifier().toString(),0xFF00FFFF));
    }
    static void setupActions() {
        FeatureRegistry.modules(); FeatureRegistry.settings();
        actions.add(() -> shot("02-gameplay-additive-glint"));
        actions.add(() -> { AuroraConfig.get().glintColorAlternative=true; });
        actions.add(() -> shot("03-gameplay-masked-glint"));
        actions.add(() -> { command("damage @e[tag=aurora_probe,limit=1] 1 minecraft:generic"); mc.player.setYRot(0); mc.player.setXRot(30); });
        actions.add(() -> { shot("04-world-overlays"); check("fabric-entity-events",entityEvents>20); check("fabric-outline-events",outlineEvents>0); });
        actions.add(() -> { mc.player.setXRot(85); mc.player.setYRot(90); });
        actions.add(() -> { shot("04b-block-fill-outline"); check("targeted-block-outline",outlineEvents>10); });
        actions.add(() -> {
            boolean attacked = false;
            for (var entity : mc.level.entitiesForRendering()) {
                if (entity instanceof net.minecraft.world.entity.LivingEntity && entity != mc.player && entity.distanceTo(mc.player)<6) {
                    mc.gameMode.attack(mc.player,entity);
                    mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    attacked = true; break;
                }
            }
            check("gameplay-attack-dispatched",attacked);
        });
        actions.add(() -> { mc.player.setXRot(20); mc.player.setYRot(0); moveX=mc.player.getX(); moveZ=mc.player.getZ(); mc.options.keyUp.setDown(true); });
        actions.add(() -> { mc.options.keyUp.setDown(false); check("gameplay-movement",Math.hypot(mc.player.getX()-moveX,mc.player.getZ()-moveZ)>0.5); });
        actions.add(() -> mc.setScreen(new HudEditorScreen()));
        actions.add(() -> { shot("05-hud-editor"); mc.setScreen(new WorldMapScreen()); });
        actions.add(() -> { shot("06-world-map"); mc.setScreen(new WaypointManagerScreen(null)); });
        actions.add(() -> { shot("07-waypoints"); mc.setScreen(new ProfileManagerScreen(null)); });
        actions.add(() -> { shot("08-profiles"); mc.setScreen(new ColorPickerScreen(null,"Vulkan color",0xFF00FF00,value -> {})); });
        actions.add(() -> { shot("09-color-picker"); mc.setScreen(new AuroraScreen()); });
        actions.add(() -> shot("10-settings-grid"));
        for (var meta : Boolean.getBoolean("aurora.vulkan.quick") ? List.<FeatureMetadata>of() : FeatureRegistry.all()) {
            actions.add(() -> mc.setScreen(new FeatureDetailScreen(null,meta)));
            actions.add(() -> { shot("detail-"+meta.id); check("detail-"+meta.id,mc.screen instanceof FeatureDetailScreen); });
        }
        actions.add(() -> {
            AuroraConfig.get().crosshairStyle = AuroraConfig.CrosshairStyle.CUSTOM;
            var meta = FeatureRegistry.all().stream().filter(value -> value.id.equals("crosshair")).findFirst().orElseThrow();
            mc.setScreen(new FeatureDetailScreen(null,meta));
            try {
                for (var setting : meta.settings) if (setting instanceof com.aurora.client.screen.setting.PixelCanvasSetting) {
                    var hardware = setting.getClass().getDeclaredMethod("hardwareContext"); hardware.setAccessible(true);
                    check("canvas-backend-hardware-query",((String)hardware.invoke(setting)).contains(com.mojang.blaze3d.systems.RenderSystem.getDevice().getRenderer()));
                    break;
                }
            } catch (Exception failure) { throw new RuntimeException(failure); }
        });
        actions.add(() -> shot("10b-custom-canvas"));
        actions.add(() -> { mc.setScreen(new BlurTestScreen()); state("backdrop=SOLID lighting=off radius=24 opacity=0.0 ping=solid"); });
        actions.add(() -> { shot("11-glass-solid"); state("backdrop=STRIPES lighting=off radius=24 opacity=0.0 ping=blur"); });
        actions.add(() -> { shot("12-glass-stripes"); state("backdrop=STRIPES lighting=on depressed=off radius=24 opacity=0.5 ping=raised"); });
        actions.add(() -> { shot("13-glass-raised"); state("backdrop=STRIPES lighting=on depressed=on radius=24 opacity=0.5 ping=depressed"); });
        actions.add(() -> { shot("14-glass-depressed"); state("backdrop=STRIPES lighting=on radius=24 opacity=0.5 stress=100 ping=pool"); });
        actions.add(() -> { shot("15-glass-pool"); check("glass-not-disabled", !debug().contains("disabled")); check("portable-texture-pool-bounded",portableTextures()<=321); mc.setScreen(null); });
        actions.add(() -> { mc.reloadResourcePacks(); });
        actions.add(() -> { check("reload-returned",mc.level!=null); mc.setScreen(new AuroraScreen()); });
        actions.add(() -> { shot("16-after-resource-reload"); mc.setScreen(null); });
        actions.add(() -> { BlurPanelRenderer.shutdown(); check("portable-textures-released",portableTextures()==0); });
        actions.add(() -> { mc.setScreen(new AuroraScreen()); });
        actions.add(() -> { shot("17-after-glass-reset"); check("glass-reset-rendered",!debug().contains("disabled")); mc.setScreen(null); });
        actions.add(() -> { mc.getWindow().setWindowed(1152,768); mc.options.guiScale().set(3); mc.resizeDisplay(); mc.setScreen(new AuroraScreen()); });
        actions.add(() -> { shot("17b-resized-scale3"); check("resize-scale3",mc.getWindow().getGuiScale()==3); });
        actions.add(() -> { mc.getWindow().setWindowed(1280,720); mc.options.guiScale().set(2); mc.resizeDisplay(); mc.setScreen(null); });
        actions.add(() -> { check("resize-restored-scale2",mc.getWindow().getGuiScale()==2); });
        actions.add(() -> { command("execute in minecraft:the_nether run tp @a 0 90 0"); });
        actions.add(() -> { shot("18-nether"); command("execute in minecraft:overworld run tp @a 0 120 0"); });
        actions.add(() -> { shot("19-return-overworld"); check("world-survived",mc.player!=null); });
    }
    static String debug() { return BlurPanelRenderer.lastOutcome(); }
    static long portableTextures() {
        return ((vulkanprobe.mixin.TextureAccess)mc.getTextureManager()).aurora$allTextures().keySet().stream()
                .filter(id -> id.getNamespace().equals("aurora") && id.getPath().startsWith("portable_glass/")).count();
    }
    static void finish() {
        if (done) return;
        done = true;
        try {
            var report = new LinkedHashMap<String,Object>();
            report.put("backend",com.mojang.blaze3d.systems.RenderSystem.getDevice().getBackendName());
            report.put("device",com.mojang.blaze3d.systems.RenderSystem.getDevice().getRenderer());
            report.put("frames",frames); report.put("worldFrames",worldFrames);
            report.put("entityEvents",entityEvents); report.put("outlineEvents",outlineEvents);
            report.put("checks",checks);
            Files.writeString(root.resolve("report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }
}
