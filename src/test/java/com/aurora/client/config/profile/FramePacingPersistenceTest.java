package com.aurora.client.config.profile;

import com.aurora.client.config.AuroraConfig;
import com.google.gson.Gson;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class FramePacingPersistenceTest {
    @TempDir static Path temporary;
    private static Object priorConfigDir;
    private static Field configDir;
    private static final Gson GSON = new Gson();

    @BeforeAll static void isolateAllPersistenceFromDeveloperSettings() throws Exception {
        configDir = FabricLoaderImpl.class.getDeclaredField("configDir");
        configDir.setAccessible(true);
        priorConfigDir = configDir.get(FabricLoaderImpl.INSTANCE);
        configDir.set(FabricLoaderImpl.INSTANCE, temporary);
        // These owners cache their paths at initialization. Fail before any
        // write if another test initialized either with a different location.
        for (var owner : new Class<?>[]{AuroraConfig.class, ProfileManager.class}) {
            String name = owner == AuroraConfig.class ? "CONFIG_PATH" : "PROFILES_DIR";
            Field path = owner.getDeclaredField(name);
            path.setAccessible(true);
            assertEquals(temporary, ((Path) path.get(null)).getParent());
        }
    }

    @AfterAll static void restoreLoaderPath() throws Exception {
        configDir.set(FabricLoaderImpl.INSTANCE, priorConfigDir);
    }

    @Test void factoryDefaultsAreStillTheAuditedDefaults() {
        AuroraConfig cfg = new AuroraConfig();
        assertEquals(AuroraConfig.PacingStrategy.PARK, cfg.framePacingStrategy);
        assertTrue(cfg.smoothFramePacer);
        assertEquals(500, cfg.framePacerSpinThresholdMicros);
        assertEquals(2000, cfg.framePacerParkThresholdMicros);
        assertFalse(cfg.framePacerLowCpuMode);
        assertTrue(cfg.lowLatencyRender);
        assertFalse(cfg.adaptiveRenderSleeping);
        assertEquals(0, cfg.frameCapFps);
    }

    @ParameterizedTest @EnumSource(AuroraConfig.PacingStrategy.class)
    void realConfigPersistenceAndProfileRestorePreserveEachStrategyAndModifier(AuroraConfig.PacingStrategy strategy) throws Exception {
        for (boolean lowCpu : new boolean[]{false, true}) {
            var live = AuroraConfig.get();
            live.framePacingStrategy = strategy;
            live.framePacerLowCpuMode = lowCpu;
            live.framePacerSpinThresholdMicros = 1750;
            live.framePacerParkThresholdMicros = 6500;
            AuroraConfig.saveBlocking();
            String disk = Files.readString(temporary.resolve("aurora.json"));
            assertTrue(disk.contains("\"framePacingStrategy\": \"" + strategy + "\""));
            live.framePacingStrategy = AuroraConfig.PacingStrategy.PARK;
            live.framePacerLowCpuMode = !lowCpu;
            live.framePacerSpinThresholdMicros = 500;
            AuroraConfig.load();
            assertSettings(strategy, lowCpu, 1750, 6500);

            var manager = ProfileManager.getInstance();
            manager.saveCurrentBlocking();
            Profile restored = GSON.fromJson(Files.readString(temporary.resolve("profiles/default.json")), Profile.class);
            AuroraConfig.get().framePacingStrategy = AuroraConfig.PacingStrategy.SPIN;
            AuroraConfig.get().framePacerLowCpuMode = !lowCpu;
            AuroraConfig.get().framePacerParkThresholdMicros = 2000;
            manager.applyProfile(restored);
            assertSettings(strategy, lowCpu, 1750, 6500);
        }
    }

    @Test void olderProfilesKeepDefaultsAndRemovedStrategyKeepsTheExistingNullFallback() {
        var manager = ProfileManager.getInstance();
        manager.applyProfile(GSON.fromJson("{\"name\":\"old\",\"fields\":{\"framePacerLowCpuMode\":true}}", Profile.class));
        assertSettings(AuroraConfig.PacingStrategy.PARK, true, 500, 2000);
        manager.applyProfile(GSON.fromJson("{\"name\":\"old\",\"fields\":{\"framePacingStrategy\":\"YIELD\",\"framePacerSpinThresholdMicros\":1234}}", Profile.class));
        assertNull(AuroraConfig.get().framePacingStrategy);
        assertEquals(1234, AuroraConfig.get().framePacerSpinThresholdMicros);
        assertFalse(AuroraConfig.get().framePacerLowCpuMode);
    }

    @Test void createThenImmediateSwitchSeesTheQueuedProfileAndDeleteIsNotUndone() throws Exception {
        // Regression (2026-10-19 relaunch acceptance): create() queues the
        // file asynchronously; switchTo() used to scan the directory before
        // it existed and silently refuse the switch.
        var manager = ProfileManager.getInstance();
        String name = "ryw-" + System.nanoTime();
        assertTrue(manager.create(name));
        assertTrue(manager.switchTo(name), "switching to a just-created profile must work");
        assertEquals(name, manager.currentProfile());
        assertTrue(manager.switchTo("default"));
        assertTrue(manager.delete(name));
        assertFalse(manager.listProfileNames().contains(name), "a queued delete is visible at once");
        assertTrue(AuroraConfig.flushPending(5000));
        manager.saveCurrentBlocking();
        assertFalse(Files.exists(temporary.resolve("profiles/" + name + ".json")), "no queued save resurrects it");
    }

    private static void assertSettings(AuroraConfig.PacingStrategy strategy, boolean lowCpu, int spin, int park) {
        AuroraConfig cfg = AuroraConfig.get();
        assertEquals(strategy, cfg.framePacingStrategy);
        assertEquals(lowCpu, cfg.framePacerLowCpuMode);
        assertEquals(spin, cfg.framePacerSpinThresholdMicros);
        assertEquals(park, cfg.framePacerParkThresholdMicros);
    }

    @Test void releaseDefaultsArePortableIndependentAndKeepTheOwnersVisualChoices() throws Exception {
        var first = new AuroraConfig();
        var second = new AuroraConfig();
        assertEquals(AuroraConfig.FontType.FIRA_SANS, first.activeFont);
        assertEquals(AuroraConfig.TextRendererMode.ALL_TEXT, first.textRendererMode);
        assertFalse(first.infoEnabled);
        assertFalse(first.cpsEnabled);
        assertTrue(first.keystrokesEnabled);
        assertTrue(first.armorHudEnabled);
        assertTrue(first.potionHudEnabled);
        assertFalse(first.minimapEnabled);
        assertFalse(first.hitregEnabled);
        assertEquals(88, first.freeLookKey);
        assertTrue(first.waypointsByWorld.isEmpty());
        assertTrue(first.playtimePerWorld.isEmpty());
        assertEquals(0, first.playtimeTotalMs);
        assertEquals(0, first.fightStatsTotalFights);
        assertNotSame(first.theme, second.theme);
        assertNotSame(first.crosshairCustomPixels, second.crosshairCustomPixels);
        first.moduleLayouts.get("minimap").offsetX = 999;
        assertEquals(-5, second.moduleLayouts.get("minimap").offsetX);
        try (var in = getClass().getResourceAsStream("/assets/aurora/defaults/3.1.0.json")) {
            String resource = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            for (String forbidden : new String[]{"C:\\", "Users", "accessToken", "activeProfile",
                    "waypointsByWorld", "waypointNavigationByWorld", "playtimeTotalMs",
                    "playtimePerWorld", "fightStats", "framePacer", "worldMapCacheRegions"})
                assertFalse(resource.contains(forbidden), forbidden);
        }
    }

    @Test void freshLoadAndExistingUpgradePreserveTheirSeparateSettings() throws Exception {
        assertTrue(AuroraConfig.flushPending(5000));
        Path path = temporary.resolve("aurora.json");
        Files.deleteIfExists(path);
        AuroraConfig.load();
        assertTrue(AuroraConfig.flushPending(5000));
        var fresh = GSON.fromJson(Files.readString(path), AuroraConfig.class);
        assertEquals(AuroraConfig.FontType.FIRA_SANS, fresh.activeFont);
        assertEquals(-5, fresh.moduleLayouts.get("minimap").offsetX);
        String old = "{\"activeFont\":\"INTER\",\"freeLookKey\":86,\"infoEnabled\":true,"
                + "\"moduleLayouts\":{\"info\":{\"anchor\":\"BOTTOM_LEFT\",\"offsetX\":73,\"scale\":1.5}},"
                + "\"playtimeTotalMs\":123456,\"waypointsByWorld\":{}}";
        Files.writeString(path, old);
        AuroraConfig.load();
        var live = AuroraConfig.get();
        assertEquals(AuroraConfig.FontType.INTER, live.activeFont);
        assertEquals(86, live.freeLookKey);
        assertTrue(live.infoEnabled);
        assertEquals(73, live.moduleLayouts.get("info").offsetX);
        assertEquals(123456, live.playtimeTotalMs);
        assertEquals(old, Files.readString(path), "loading an existing config must not rewrite it");
        assertTrue(live.keystrokesEnabled, "missing fields receive release defaults");
        var manager = ProfileManager.getInstance();
        var snapshot = manager.captureCurrent();
        manager.applyProfile(snapshot);
        assertEquals(73, live.moduleLayouts.get("info").offsetX);
        assertEquals(AuroraConfig.FontType.INTER, live.activeFont);
        assertEquals(86, live.freeLookKey);
        AuroraConfig.save();
        assertTrue(AuroraConfig.flushPending(5000));
        AuroraConfig.load();
        assertEquals(73, AuroraConfig.get().moduleLayouts.get("info").offsetX);
    }

    @Test void corruptInputIsBackedUpBeforeRecoveryCanSave() throws Exception {
        assertTrue(AuroraConfig.flushPending(5000));
        Path path = temporary.resolve("aurora.json");
        String broken = "{broken config";
        Files.writeString(path, broken);
        AuroraConfig.load();
        try (var backups = Files.list(temporary)) {
            assertTrue(backups.filter(p -> p.getFileName().toString().startsWith("aurora.json.corrupt-"))
                    .anyMatch(p -> { try { return Files.readString(p).equals(broken); }
                        catch (Exception e) { return false; } }));
        }
        assertEquals(broken, Files.readString(path));
    }

    @Test void resetPositionsRestoresReleaseGeometryAndPreservesVisibilityAndLocks() {
        for (String id : new String[]{"info", "cps", "armor", "reach", "ping", "potion",
                "totem_pop", "toggle_sprint_sneak", "toggle_sprint", "toggle_sneak", "keystrokes", "minimap"}) {
            var module = new com.aurora.client.hud.module.HudModule(id) {
                public int getWidth() { return 20; }
                public int getHeight() { return 20; }
                protected void renderContent(net.minecraft.client.gui.GuiGraphics g,
                        net.minecraft.client.Minecraft mc, int x, int y) {}
            };
            module.offsetX = 999;
            module.enabled = false;
            module.locked = true;
            module.scale = 3;
            module.resetLayoutToDefaults();
            var expected = com.aurora.client.config.ReleaseDefaults.layout(id);
            assertEquals(expected.anchor, module.anchor, id);
            assertEquals(expected.offsetX, module.offsetX, id);
            assertEquals(expected.offsetY, module.offsetY, id);
            assertEquals(expected.scale, module.scale, id);
            assertFalse(module.enabled, id);
            assertTrue(module.locked, id);
        }
    }

    @Test void defaultVisibleHudBoundsStaySeparateAtReleaseDisplaySizes() {
        var defaults = new AuroraConfig();
        // Conservative GUI-space envelopes: 4 armor slots, 8 effect rows,
        // movement/mouse/space keys. The owner's minimap is disabled by default.
        for (int[] size : new int[][]{{480,270},{640,360},{854,480},{960,540},{1280,720}}) {
            var boxes = new java.util.ArrayList<int[]>();
            for (Object[] spec : new Object[][]{{"armor",104,40},{"potion",190,224},
                    {"keystrokes",72,105}}) {
                var layout = defaults.moduleLayouts.get((String)spec[0]);
                int w = Math.round((int)spec[1] * layout.scale);
                int h = Math.round((int)spec[2] * layout.scale);
                int x = layout.anchor.applyX(size[0], w) + layout.offsetX;
                int y = layout.anchor.applyY(size[1], h) + layout.offsetY;
                assertTrue(x >= 0 && y >= 0 && x+w <= size[0] && y+h <= size[1], (String)spec[0]);
                for (int[] b : boxes)
                    assertFalse(x < b[0]+b[2] && x+w > b[0] && y < b[1]+b[3] && y+h > b[1]);
                boxes.add(new int[]{x,y,w,h});
            }
        }
    }
}
