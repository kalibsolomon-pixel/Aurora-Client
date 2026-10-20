package guiprobe;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.profile.ProfileManager;
import com.aurora.client.feature.impl.WaypointFeature;
import com.aurora.client.screen.*;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.worldmap.screen.WorldMapScreen;
import com.google.gson.GsonBuilder;
import guiprobe.mixin.MouseAccess;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;

/**
 * Native GUI-audit capture fixture (disposable directories only; never shipped).
 * Drives real screens through Minecraft/Aurora APIs and grabs the completed frame
 * before present, so captures are valid on OpenGL, Sodium and VulkanMod.
 */
public final class GuiAudit implements ClientModInitializer {
    static Path root;
    static final List<Map<String, Object>> checks = new ArrayList<>();
    static final List<Map<String, Object>> shots = new ArrayList<>();
    static final List<Runnable> actions = new ArrayList<>();
    static final List<Long> frameNanos = new ArrayList<>();
    static long start, at, lastFrame;
    static int stage, action, frames, settle = 2;
    static boolean driving, done;
    static Minecraft mc;
    static String plan;
    static String label = "";
    static String[] sizes;
    static int titleIndex;

    @Override public void onInitializeClient() {
        root = Path.of(System.getProperty("aurora.gui.output"));
        if (!Files.isRegularFile(root.resolve("DISPOSABLE-RESEARCH.txt"))) throw new IllegalStateException("Disposable fixture required");
        plan = System.getProperty("aurora.gui.plan", "full");
        sizes = System.getProperty("aurora.gui.sizes", "1920x1080@3").split(",");
        start = System.nanoTime();
    }

    static void check(String name, boolean value) {
        checks.add(Map.of("check", name, "passed", value, "frames", frames));
        System.out.println("[gui-audit] " + name + "=" + value);
    }

    static void shot(String name) {
        String file = label + name;
        var window = mc.getWindow();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", file);
        entry.put("screen", mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
        entry.put("framebuffer", window.getWidth() + "x" + window.getHeight());
        entry.put("guiScale", window.getGuiScale());
        entry.put("glass", BlurPanelRenderer.lastOutcome());
        shots.add(entry);
        Screenshot.grab(mc.gameDirectory, file + ".png", mc.getMainRenderTarget(), 1,
                text -> System.out.println("[gui-shot] " + text.getString()));
    }

    static void command(String value) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), value));
    }

    /** Places the (virtual) cursor in GUI coordinates; hover is computed from MouseHandler. */
    static void hover(double guiX, double guiY) {
        double s = mc.getWindow().getGuiScale();
        ((MouseAccess) mc.mouseHandler).gui$setX(guiX * s);
        ((MouseAccess) mc.mouseHandler).gui$setY(guiY * s);
    }

    static void park() { hover(-50, -50); }

    static void click(double x, double y) {
        if (mc.screen == null) return;
        var ev = new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
        mc.screen.mouseClicked(ev, false);
        mc.screen.mouseReleased(ev);
    }

    static void scroll(double amount) {
        if (mc.screen == null) return;
        mc.screen.mouseScrolled(mc.screen.width / 2.0, mc.screen.height / 2.0, 0, amount);
    }

    static void resize(String spec) {
        String[] parts = spec.split("@");
        String[] wh = parts[0].split("x");
        int w = Integer.parseInt(wh[0]), h = Integer.parseInt(wh[1]);
        mc.getWindow().setWindowed(w, h);
        mc.options.guiScale().set(Integer.parseInt(parts[1]));
        mc.resizeDisplay();
        label = parts[0] + "-s" + parts[1] + "-";
    }

    static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method m = target.getClass().getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Exception failure) { throw new RuntimeException(failure); }
    }

    static void field(Object target, String name, Object value) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception failure) { throw new RuntimeException(failure); }
    }

    static AuroraScreen aurora(int tab, boolean grid) {
        AuroraConfig.get().moduleGridView = grid;
        AuroraScreen screen = new AuroraScreen();
        mc.setScreen(screen);
        if (tab != 0) invoke(screen, "selectCategory", new Class<?>[]{int.class}, tab);
        return screen;
    }

    public static void frame() {
        if (root == null || driving || done) return;
        driving = true;
        try {
            mc = Minecraft.getInstance(); frames++;
            long now = System.nanoTime();
            if (lastFrame != 0 && mc.screen != null) frameNanos.add(now - lastFrame);
            lastFrame = now;
            if (now - start > 600_000_000_000L) throw new IllegalStateException("GUI audit timeout at " + stage + "/" + action);
            if (stage == 0) {
                if (mc.getOverlay() == null && mc.screen != null && mc.screen.getClass().getSimpleName().equals("AuroraTitleScreen")) {
                    if (at == 0) { at = now; resize(sizes[0]); mc.setScreen(new AuroraTitleScreen()); }
                    if (elapsed() > 3) {
                        if (titleIndex < sizes.length) {
                            resize(sizes[titleIndex]);
                            mc.setScreen(new AuroraTitleScreen());
                            titleIndex++;
                            stage = 10; at = now;
                        } else {
                            resize(sizes[0]);
                            check("title-rendered", true);
                            mc.createWorldOpenFlows().openWorld("New World", () -> mc.setScreen(new TitleScreen()));
                            stage = 1; at = now;
                        }
                    }
                }
            } else if (stage == 10) {
                // title captures at each size, with a hovered first card
                if (elapsed() > 1.2) {
                    park(); shot("title");
                    var s = mc.screen;
                    hover(s.width / 2.0, s.height / 2.0 + 4);
                    stage = 11; at = now;
                }
            } else if (stage == 11) {
                if (elapsed() > 0.6) { shot("title-hover"); park(); stage = 0; at = now - 3_000_000_000L; }
            } else if (stage == 1 && mc.player != null && mc.level != null && mc.screen == null) {
                check("world-entered", true);
                configure(); setupActions(); stage = 2; at = now;
            } else if (stage == 2 && burst > 0) {
                // Consecutive completed frames: motion smoothness is a per-frame property.
                if (burstStep != null) burstStep.run();
                shot(burstName + "-" + String.format("%02d", burst));
                if (--burst == 0) at = System.nanoTime();
            } else if (stage == 2 && mc.getOverlay() == null && elapsed() > 0.9) {
                if (action < actions.size()) { actions.get(action++).run(); at = System.nanoTime(); }
                else finish();
            }
        } catch (Throwable failure) {
            failure.printStackTrace(); check("unexpected-" + failure.getClass().getSimpleName(), false); finish();
        } finally { driving = false; }
    }

    static double elapsed() { return (System.nanoTime() - at) / 1e9; }

    static void configure() throws Exception {
        var cfg = AuroraConfig.get();
        for (String field : List.of("minimapEnabled", "worldMapEnabled", "waypointsEnabled", "crosshairEnabled", "keystrokesEnabled"))
            AuroraConfig.class.getField(field).setBoolean(cfg, true);
        command("tp @a 0 120 0 0 20"); command("time set day"); command("weather clear");
        command("gamemode creative @a");
        command("give @a diamond_sword");
        mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
    }

    static void removeAllWaypoints() {
        for (var w : new ArrayList<>(WaypointFeature.get().currentWorldWaypoints())) WaypointFeature.get().remove(w);
    }

    static void seedWaypoints(int count) {
        String dim = mc.level.dimension().identifier().toString();
        String[] groups = {"Base", "Mining", "Farms", "Portals", "Long expedition destination names"};
        int[] colors = {0xFF4FC3F7, 0xFFFF8A65, 0xFF81C784, 0xFFBA68C8, 0xFFFFD54F, 0xFFE57373};
        for (int i = 0; i < count; i++) {
            var w = new AuroraConfig.Waypoint(i == 3 ? "An unusually long waypoint name that should truncate cleanly" : "Waypoint " + (i + 1),
                    (i * 37) % 900 - 450, 64 + i % 40, (i * 53) % 900 - 450, dim, colors[i % colors.length]);
            try { AuroraConfig.Waypoint.class.getField("group").set(w, groups[i % groups.length]); } catch (Exception ignored) {}
            WaypointFeature.get().addWaypoint(w);
        }
    }

    static void addShots(String base, java.util.function.Supplier<Screen> open) {
        actions.add(() -> { park(); mc.setScreen(open.get()); });
        actions.add(() -> shot(base));
    }

    /** Remaining consecutive-frame captures (one per frame), with an optional per-frame step. */
    static int burst;
    static String burstName = "";
    static Runnable burstStep;

    static void burst(String name, int count, Runnable step) {
        actions.add(() -> { burstName = name; burst = count; burstStep = step; });
        actions.add(() -> {}); // let the burst finish before the next action
    }

    static void slot(int i) { mc.player.getInventory().setSelectedSlot(i); }

    static void themeBackground(int argb) {
        try {
            var def = AuroraConfig.get().themeOrDefault();
            def.getClass().getField("background").setInt(def, argb);
            com.aurora.client.theme.ThemeManager.reload();
        } catch (ReflectiveOperationException absent) { System.out.println("[gui-audit] no background field (baseline jar)"); }
    }

    /** Owner-feedback acceptance captures (2026-10-19): HUD editor, minimap, keystrokes, glint, profiles, theme. */
    static void ownerActions() {
        var cfg = AuroraConfig.get();
        actions.add(() -> {
            command("give @a diamond_sword[enchantments={sharpness:5}]");
            command("give @a enchanted_book[stored_enchantments={mending:1}]");
            cfg.glintColorEnabled = true; cfg.glintColorAlternative = true;
            cfg.glintColor = 0xFF00E5FF; cfg.glintColorIntensity = 100; cfg.glintColorTintArmor = true;
        });
        // ---- Glint: enchanted vs plain held item, alternative then additive ----
        actions.add(() -> slot(1));
        actions.add(() -> shot("glint-alt-enchanted"));
        actions.add(() -> slot(0));
        actions.add(() -> shot("glint-alt-plain"));
        actions.add(() -> { slot(1); cfg.glintColorAlternative = false; });
        actions.add(() -> shot("glint-additive-enchanted"));
        actions.add(() -> { cfg.glintColorAlternative = true; slot(0); });
        // ---- Keystrokes: press W + LMB, then release (consecutive frames) ----
        actions.add(() -> shot("keys-rest"));
        burst("keys-press", 6, () -> { mc.options.keyUp.setDown(true); mc.options.keyAttack.setDown(true); });
        burst("keys-release", 8, () -> { mc.options.keyUp.setDown(false); mc.options.keyAttack.setDown(false); });
        // ---- Minimap: sprinting straight, then turning with rotate-with-player ----
        // Flying at a fixed height so terrain never blocks the sprint.
        actions.add(() -> { mc.player.getAbilities().flying = true; command("tp @a 0 140 0 0 0"); cfg.minimapRotateWithPlayer = false; });
        actions.add(() -> { mc.options.keyUp.setDown(true); mc.options.keySprint.setDown(true); });
        burst("minimap-walk", 12, () -> {});
        actions.add(() -> { mc.options.keyUp.setDown(false); mc.options.keySprint.setDown(false); cfg.minimapRotateWithPlayer = true; });
        burst("minimap-turn", 12, () -> { mc.player.turn(12, 0); });
        actions.add(() -> cfg.minimapRotateWithPlayer = false);
        // ---- HUD editor: centred accent Aurora Settings, compact grey Reset Positions ----
        addShots("hud-editor", HudEditorScreen::new);
        actions.add(() -> { var s = mc.screen; hover(s.width - 30, 14); });
        actions.add(() -> shot("hud-editor-reset-hover"));
        // ---- Profiles: card grid ----
        actions.add(() -> {
            var pm = ProfileManager.getInstance();
            for (String n : List.of("PvP", "Building", "Speedrun – tuned performance", "Bedwars")) if (!pm.listProfileNames().contains(n)) pm.create(n);
        });
        addShots("profiles", () -> new ProfileManagerScreen(null));
        actions.add(() -> { var s = mc.screen; hover(s.width / 2.0 + 60, s.height / 2.0 - 60); });
        actions.add(() -> shot("profiles-hover"));
        actions.add(() -> { park(); var s = (ProfileManagerScreen) mc.screen; invoke(s, "beginCreate", new Class<?>[0]); });
        actions.add(() -> shot("profiles-create"));
        // ---- Theme: Background Color presets and the opaque custom picker ----
        var theme = FeatureRegistry.all().stream().filter(m -> m.id.equals("theme")).findFirst().orElseThrow();
        addShots("theme-default", () -> new FeatureDetailScreen(null, theme));
        actions.add(() -> themeBackground(0xFF13203A));
        addShots("theme-background-blue", () -> new FeatureDetailScreen(null, theme));
        actions.add(() -> { park(); aurora(0, true); });
        actions.add(() -> shot("main-background-blue"));
        actions.add(() -> themeBackground(0xFF22163A));
        actions.add(() -> { park(); aurora(1, true); });
        actions.add(() -> shot("settings-background-purple"));
        actions.add(() -> {
            themeBackground(0xFF3A6F2B); // custom: opens the Custom Background editor on revisit
            for (var setting : theme.settings) {
                if (!setting.getClass().getSimpleName().equals("AccentSetting")) continue;
                try {
                    var open = setting.getClass().getDeclaredField("editorOpen"); open.setAccessible(true); open.setBoolean(setting, true);
                    var f = setting.getClass().getDeclaredField("customPicker"); f.setAccessible(true);
                    Object picker = f.get(setting);
                    var m = picker.getClass().getDeclaredMethod("toggleExpanded"); m.setAccessible(true); m.invoke(picker);
                } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            }
            park(); mc.setScreen(new FeatureDetailScreen(null, theme));
        });
        actions.add(() -> shot("theme-custom-pickers"));
        actions.add(() -> scroll(-400));
        actions.add(() -> shot("theme-custom-pickers-end"));
        actions.add(() -> { themeBackground(0xFF16171D); park(); mc.setScreen(null); });
        actions.add(() -> shot("gameplay-hud"));
        // ---- Owner follow-up: themed buttons, glyph fallback ----
        actions.add(() -> {
            cfg.textRendererMode = AuroraConfig.TextRendererMode.ALL_TEXT;
            cfg.activeFont = AuroraConfig.FontType.INTER;
            // Scoreboard-style symbols, an emoji, CJK and a block: none are in Inter.
            StringBuilder glyphs = new StringBuilder("say Glyphs:");
            for (int cp : new int[]{0x24D8, 0x2691, 0x1F310, 0x23F2, 0x2694, 0x2726, 0x4E2D, 0x6587, 0x2588})
                glyphs.append(' ').appendCodePoint(cp);
            command(glyphs.toString());
        });
        actions.add(() -> shot("glyph-fallback"));
        actions.add(() -> { themeBackground(0xFF22163A); cfg.themeOrDefault().accent = 0xFF43A047; com.aurora.client.theme.ThemeManager.reload(); });
        addShots("hud-editor-purple-green", HudEditorScreen::new);
        actions.add(() -> { park(); mc.setScreen(new JoinMultiplayerScreen(new AuroraTitleScreen())); });
        actions.add(() -> shot("multiplayer-themed"));
        actions.add(() -> { park(); mc.setScreen(new AuroraTitleScreen()); });
        actions.add(() -> shot("title-themed"));
        actions.add(() -> { themeBackground(0xFF16171D); cfg.themeOrDefault().accent = 0xFFEB0029; com.aurora.client.theme.ThemeManager.reload(); mc.setScreen(null); });
    }

    static void setupActions() {
        if (plan.equals("persist")) { PersistAudit.plan(Integer.getInteger("aurora.gui.phase", 1)); return; }
        if (plan.equals("owner")) { FeatureRegistry.modules(); FeatureRegistry.settings(); ownerActions(); return; }
        FeatureRegistry.modules(); FeatureRegistry.settings();
        boolean full = plan.equals("full");
        actions.add(() -> shot("gameplay-hud"));
        for (int i = 0; i < (full ? 1 : sizes.length); i++) {
            final String size = sizes[i];
            actions.add(() -> { resize(size); mc.setScreen(null); });
            actions.add(() -> { park(); aurora(0, true); });
            actions.add(() -> shot("main-grid"));
            actions.add(() -> { var s = mc.screen; hover(s.width / 2.0 - 20, s.height / 2.0 - 10); });
            actions.add(() -> shot("main-grid-hover"));
            actions.add(() -> { park(); aurora(0, false); });
            actions.add(() -> shot("main-list"));
            actions.add(() -> { park(); aurora(1, true); });
            actions.add(() -> shot("main-settings"));
            if (full) {
                actions.add(() -> scroll(-40));
                actions.add(() -> shot("main-settings-scrolled"));
                actions.add(() -> { aurora(0, true); typeSearch("zzzz"); });
                actions.add(() -> shot("main-search-empty"));
                actions.add(() -> { aurora(0, true); typeSearch("color"); });
                actions.add(() -> shot("main-search-color"));
            }
            List<FeatureMetadata> metas = full ? FeatureRegistry.all()
                    : FeatureRegistry.all().stream().filter(m -> Set.of("zoom", "better_hitreg", "crosshair", "theme").contains(m.id)).toList();
            for (var meta : metas) {
                actions.add(() -> { park(); mc.setScreen(new FeatureDetailScreen(null, meta)); });
                actions.add(() -> { shot("detail-" + meta.id); check("detail-" + meta.id, mc.screen instanceof FeatureDetailScreen); });
                if (full) {
                    actions.add(() -> scroll(-400));
                    actions.add(() -> shot("detail-" + meta.id + "-end"));
                }
            }
            actions.add(() -> { removeAllWaypoints(); });
            addShots("waypoints-empty", () -> new WaypointManagerScreen(null));
            actions.add(() -> { mc.setScreen(null); seedWaypoints(40); });
            addShots("waypoints-dense", () -> new WaypointManagerScreen(null));
            actions.add(() -> { var s = mc.screen; hover(s.width / 2.0, s.height / 2.0); });
            actions.add(() -> shot("waypoints-dense-hover"));
            if (full) {
                addShots("waypoint-edit-new", () -> new WaypointEditScreen(null,
                        new AuroraConfig.Waypoint("", 10, 70, -20, mc.level.dimension().identifier().toString(), 0xFF4FC3F7), true));
                addShots("waypoint-edit-existing", () -> new WaypointEditScreen(null,
                        WaypointFeature.get().currentWorldWaypoints().get(3), false));
                actions.add(() -> {
                    var pm = ProfileManager.getInstance();
                    for (String n : List.of("PvP", "Building", "Speedrun – tuned performance")) if (!pm.listProfileNames().contains(n)) pm.create(n);
                });
            }
            addShots("profiles", () -> new ProfileManagerScreen(null));
            addShots("color-picker", () -> new ColorPickerScreen(null, "Block Overlay Color", 0xC04FC3F7, v -> {}));
            addShots("hud-editor", HudEditorScreen::new);
            actions.add(() -> { var s = mc.screen; hover(40, 140); });
            actions.add(() -> shot("hud-editor-hover"));
            addShots("world-map", WorldMapScreen::new);
            if (full) {
                addShots("mapping-data", () -> new MappingDataScreen(null));
                addShots("pack-browser", () -> new ResourcePackBrowserScreen(null));
                actions.add(() -> {});
                actions.add(() -> shot("pack-browser-loaded"));
                actions.add(() -> { park(); mc.setScreen(new SelectWorldScreen(new TitleScreen())); });
                actions.add(() -> shot("vanilla-select-world"));
                actions.add(() -> { park(); mc.setScreen(new JoinMultiplayerScreen(new TitleScreen())); });
                actions.add(() -> shot("vanilla-multiplayer"));
            }
            actions.add(() -> { park(); mc.setScreen(null); });
            actions.add(() -> shot("gameplay-hud"));
        }
        actions.add(() -> check("glass-not-disabled", !BlurPanelRenderer.lastOutcome().contains("disabled")));
    }

    static void typeSearch(String text) {
        for (char c : text.toCharArray()) mc.screen.charTyped(new net.minecraft.client.input.CharacterEvent(c, 0));
    }

    static void finish() {
        if (done) return;
        done = true;
        try {
            var report = new LinkedHashMap<String, Object>();
            report.put("backend", com.mojang.blaze3d.systems.RenderSystem.getDevice().getBackendName());
            report.put("device", com.mojang.blaze3d.systems.RenderSystem.getDevice().getRenderer());
            report.put("plan", plan);
            report.put("frames", frames);
            long[] sorted = frameNanos.stream().mapToLong(Long::longValue).sorted().toArray();
            if (sorted.length > 0) {
                report.put("screenFrameMsP50", sorted[sorted.length / 2] / 1e6);
                report.put("screenFrameMsP95", sorted[(int) (sorted.length * 0.95)] / 1e6);
                report.put("screenFrames", sorted.length);
            }
            report.put("checks", checks);
            report.put("shots", shots);
            Files.writeString(root.resolve(plan.equals("persist") ? "report-phase" + Integer.getInteger("aurora.gui.phase", 1) + ".json" : "report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (Exception failure) { failure.printStackTrace(); }
        mc.stop();
    }
}
