package com.aurora.client;

import com.aurora.client.hud.BlockOverlayRenderer;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.profile.ProfileManager;
import com.aurora.client.feature.FeatureManager;
import com.aurora.client.hud.HudRenderer;
import com.aurora.client.hud.module.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import com.aurora.client.hud.HitboxRenderer;

public class AuroraClient implements ClientModInitializer {
    public static final String MOD_ID = "aurora";
    public static final Logger LOGGER = LoggerFactory.getLogger("Aurora");

    private static FeatureManager featureManager;
    private static HudRenderer hudRenderer;
    private static HudModuleManager moduleManager;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Aurora Client...");

        AuroraConfig.load();

        // Load + apply the active profile (or create the default one on first
        // run). MUST happen after AuroraConfig.load() (reads activeProfile)
        // and before keybind registration so the keybind getters see the
        // profile's values.
        ProfileManager.getInstance().load();

        // One-time migration of the vendored BetterHitreg config
        // (config/hitreg.properties) into AuroraConfig. It runs HERE, after
        // the active profile is applied, rather than inside
        // AuroraConfig.load(): applyProfile resets every profile-scoped
        // field to its default before overlaying the profile, which would
        // silently wipe values migrated any earlier. Never throws; guarded
        // by cfg.migratedHitregProperties so it fires exactly once.
        com.aurora.client.hitreg.settings.HitregMigrator.runOnce();

        registerAuroraKeybinds();

        // Better Hitreg (Jass's BetterHitreg, integrated): sets Hitreg.client
        // first, then registers its tick / world-render / HUD / keybind hooks.
        // Needs the config + active profile loaded (its settings layer reads
        // AuroraConfig) and the keybinds above registered (dispatch reads
        // the same config ints).
        com.aurora.client.hitreg.BetterHitreg.initialize();


        featureManager = new FeatureManager();
        featureManager.registerAll();

        moduleManager = new HudModuleManager();
        moduleManager.register(new InfoModule());
        moduleManager.register(new CpsModule());
        moduleManager.register(new ArmorModule());
        moduleManager.register(new ReachModule());
        moduleManager.register(new ToggleSprintSneakModule());
        // Two independent single-row modules (sprint / sneak) used only
        // when the Toggle Sprint/Sneak HUD is in INDIVIDUAL display mode.
        // They stay dormant (invisible) until that mode is selected.
        moduleManager.register(new ToggleIndividualModule(ToggleIndividualModule.Kind.SPRINT));
        moduleManager.register(new ToggleIndividualModule(ToggleIndividualModule.Kind.SNEAK));
        moduleManager.register(new PotionModule());
        moduleManager.register(new PingModule());
        moduleManager.register(new com.aurora.client.hud.module.TotemPopModule());
        moduleManager.register(new StatsModule());
        moduleManager.register(new KeystrokesModule());
        moduleManager.register(new MinimapModule());

        HitboxRenderer hitboxRenderer = new HitboxRenderer();
        WorldRenderEvents.AFTER_ENTITIES.register(hitboxRenderer::render);

        BlockOverlayRenderer blockOverlayRenderer = new BlockOverlayRenderer();
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register(blockOverlayRenderer::onBeforeBlockOutline);

        // Module icon PNGs — scan the resource manager so the tile grid
        // can blit per-module artwork as the user ships PNGs into
        // assets/aurora/textures/gui/module_icons/<id>.png.
        net.fabricmc.fabric.api.resource.v1.ResourceLoader
                .get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
                .registerReloader(
                        Identifier.fromNamespaceAndPath("aurora", "module_icons"),
                        new com.aurora.client.screen.ModuleIconRegistry());

        // Container preview — map Aurora's tooltip data to its renderer.
        net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback.EVENT.register(data -> {
            if (data instanceof com.aurora.client.hud.preview.AuroraContainerTooltipData acd) {
                return new com.aurora.client.hud.preview.AuroraContainerTooltipComponent(acd);
            }
            return null;
        });

        com.aurora.client.hud.WaypointRenderer waypointRenderer = new com.aurora.client.hud.WaypointRenderer();
        WorldRenderEvents.AFTER_ENTITIES.register(waypointRenderer::renderWorld);
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            com.aurora.client.ui.util.AuroraFontRenderer.setRenderingAuroraUI(true);
            try {
                waypointRenderer.renderHud(ctx, tickCounter);
            } finally {
                com.aurora.client.ui.util.AuroraFontRenderer.setRenderingAuroraUI(false);
            }
        });

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_WORLD_TICK.register(
                client -> com.aurora.client.util.OverlayReloadListener.callEvent());

        // Two-way sync between Aurora config and the vanilla KeyMapping
        // entries we registered above. END_CLIENT_TICK runs every tick
        // regardless of world state, so rebinds made from the vanilla
        // controls screen (no world tick happens while it's open) are
        // still picked up the moment the screen closes.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
                client -> com.aurora.client.util.AuroraKeybinds.tick());

        // Isolated blur-panel test screen — verification harness for the
        // blur+tint panel foundation. Opens only in-world with no other
        // screen open; never reachable from another Aurora screen.
        final com.aurora.client.util.AuroraKey.EdgeDetector blurTestEdge =
                new com.aurora.client.util.AuroraKey.EdgeDetector();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (blurTestEdge.justPressed(AuroraConfig.get().blurTestKey)
                    && client.screen == null && client.player != null) {
                client.setScreen(new com.aurora.client.ui.render.blur.BlurTestScreen());
            }
        });

        // Snapshot the local ender chest while its screen is open so we can
        // preview its contents from the item tooltip later.
        final String enderChestTitle =
                net.minecraft.network.chat.Component.translatable("container.enderchest").getString();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!AuroraConfig.get().containerPreviewEnabled) return;
            if (!AuroraConfig.get().containerPreviewEnderChest) return;
            if (client.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> acs
                    && acs.getMenu() instanceof net.minecraft.world.inventory.ChestMenu cm
                    && cm.getRowCount() == 3
                    && acs.getTitle().getString().equals(enderChestTitle)) {
                com.aurora.client.hud.preview.EnderChestSnapshot.capture(cm);
            }
        });

        // Hydrate module state from saved layouts (if any).
        applyLayouts(moduleManager);

        // Refresh in-memory HUD module positions whenever a profile is
        // applied (startup load + runtime switch), because applyProfile
        // overwrites AuroraConfig.moduleLayouts but the live HudModule
        // objects hold their own copies of those fields.
        ProfileManager.getInstance().onProfileApplied(n -> applyLayouts(moduleManager));

        hudRenderer = new HudRenderer();
        HudRenderCallback.EVENT.register(hudRenderer::render);

        // Unified alert popup renderer — serves all alert sources
        // (durability, hunger, effect expiry, compliance mode toasts).
        com.aurora.client.hud.AlertRenderer alertRenderer = new com.aurora.client.hud.AlertRenderer();
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            com.aurora.client.ui.util.AuroraFontRenderer.setRenderingAuroraUI(true);
            try {
                alertRenderer.render(ctx);
            } finally {
                com.aurora.client.ui.util.AuroraFontRenderer.setRenderingAuroraUI(false);
            }
        });

        // Persist module layouts on exit so the drag editor's changes stick.
        // Uses the BLOCKING save variant here: the async save() hands its disk
        // write to a daemon thread that the JVM will not wait for on exit, so
        // a final flush during shutdown must run inline to guarantee it lands.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            captureLayouts(moduleManager);
            // Persist the active profile BEFORE the blocking config save so
            // the outgoing state lands on disk. Uses the blocking variant
            // because the async profile save runs on a daemon thread the JVM
            // will not wait for on exit — same rationale as saveBlocking().
            ProfileManager.getInstance().saveCurrentBlocking();
            AuroraConfig.saveBlocking();
        }, "Aurora-ConfigSave"));

        LOGGER.info("Aurora Client ready.");
    }

    /** Copy saved positions onto registered modules. Unknown keys are ignored. */
    private static void applyLayouts(HudModuleManager mgr) {
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.moduleLayouts == null) return;
        for (HudModule m : mgr.all()) {
            AuroraConfig.ModuleLayout layout = cfg.moduleLayouts.get(m.id());
            if (layout == null) continue;
            m.anchor  = layout.anchor;
            m.offsetX = layout.offsetX;
            m.offsetY = layout.offsetY;
            m.enabled = layout.enabled;
            m.scale   = layout.scale;
            m.locked  = layout.locked;
        }
    }

    /** Read current module fields back into the config map. */
    public static void captureLayouts(HudModuleManager mgr) {
        AuroraConfig cfg = AuroraConfig.get();
        if (mgr == null) return;
        for (HudModule m : mgr.all()) {
            cfg.moduleLayouts.put(m.id(),
                    new AuroraConfig.ModuleLayout(m.anchor, m.offsetX, m.offsetY, m.enabled, m.scale, m.locked));
        }

    }

    public static FeatureManager features() { return featureManager; }
    public static HudRenderer hud() { return hudRenderer; }
    public static HudModuleManager modules() { return moduleManager; }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /**
     * Register every Aurora keybind with vanilla so it shows up in the
     * vanilla controls screen under the {@code "Aurora Client"} category
     * and stays in sync with the Aurora UI's {@code KeybindSetting} rows.
     * See {@link com.aurora.client.util.AuroraKeybinds} for the sync
     * details.
     *
     * <p>Defaults here mirror the field defaults in
     * {@link AuroraConfig} — they are only used by vanilla's "reset to
     * default" affordance, not as initial bound values (those come from
     * the config getter at registration time).
     */
    private static void registerAuroraKeybinds() {
        AuroraConfig cfg = AuroraConfig.get();
        com.aurora.client.util.AuroraKeybinds.register(
                "key.aurora.zoom",
                org.lwjgl.glfw.GLFW.GLFW_KEY_C,
                () -> cfg.zoomKey,            v -> cfg.zoomKey = v);
        com.aurora.client.util.AuroraKeybinds.register(
                "key.aurora.free_look",
                org.lwjgl.glfw.GLFW.GLFW_KEY_V,
                () -> cfg.freeLookKey,        v -> cfg.freeLookKey = v);
        com.aurora.client.util.AuroraKeybinds.register(
                "key.aurora.toggle_sprint",
                org.lwjgl.glfw.GLFW.GLFW_KEY_J,
                () -> cfg.toggleSprintKey,    v -> cfg.toggleSprintKey = v);
        com.aurora.client.util.AuroraKeybinds.register(
                "key.aurora.toggle_sneak",
                org.lwjgl.glfw.GLFW.GLFW_KEY_K,
                () -> cfg.toggleSneakKey,     v -> cfg.toggleSneakKey = v);
        com.aurora.client.util.AuroraKeybinds.register(
                "key.aurora.hud_editor",
                org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT,
                () -> cfg.hudEditorKey,       v -> cfg.hudEditorKey = v);
        com.aurora.client.util.AuroraKeybinds.register(
                "key.aurora.hitbox_toggle",
                com.aurora.client.util.AuroraKey.UNBOUND,
                () -> cfg.hitboxToggleKey,    v -> cfg.hitboxToggleKey = v);
        com.aurora.client.util.AuroraKeybinds.register(
                "key.aurora.totem_reset",
                com.aurora.client.util.AuroraKey.UNBOUND,
                () -> cfg.totemResetKey,      v -> cfg.totemResetKey = v);
        com.aurora.client.util.AuroraKeybinds.register(
                "key.aurora.waypoint_drop",
                com.aurora.client.util.AuroraKey.UNBOUND,
                () -> cfg.waypointDropKey,    v -> cfg.waypointDropKey = v);
        com.aurora.client.util.AuroraKeybinds.register(
                "key.aurora.waypoint_manager",
                com.aurora.client.util.AuroraKey.UNBOUND,
                () -> cfg.waypointManagerKey, v -> cfg.waypointManagerKey = v);
        com.aurora.client.util.AuroraKeybinds.register(
                "key.aurora.world_map",
                org.lwjgl.glfw.GLFW.GLFW_KEY_M,
                () -> cfg.worldMapKey,         v -> cfg.worldMapKey = v);
        com.aurora.client.util.AuroraKeybinds.register(
                "key.aurora.minimap_toggle",
                com.aurora.client.util.AuroraKey.UNBOUND,
                () -> cfg.minimapToggleKey,    v -> cfg.minimapToggleKey = v);
        com.aurora.client.util.AuroraKeybinds.register(
                "key.aurora.blur_test",
                com.aurora.client.util.AuroraKey.UNBOUND,
                () -> cfg.blurTestKey,         v -> cfg.blurTestKey = v);
        // Better Hitreg — all six deliberately unbound by default. The
        // original mod bound H (its menu) and all four arrow keys (the
        // practice scoreboard), which grabbed the arrows in every context;
        // the settings are reachable from the Mods grid, so H stays free.
        com.aurora.client.util.AuroraKeybinds.register(
                "key.aurora.hitreg_settings",
                com.aurora.client.util.AuroraKey.UNBOUND,
                () -> cfg.hitregSettingsKey,   v -> cfg.hitregSettingsKey = v);
        com.aurora.client.util.AuroraKeybinds.register(
                "key.aurora.hitreg_switch_hand",
                com.aurora.client.util.AuroraKey.UNBOUND,
                () -> cfg.hitregSwitchHandKey, v -> cfg.hitregSwitchHandKey = v);
        com.aurora.client.util.AuroraKeybinds.register(
                "key.aurora.hitreg_score_left",
                com.aurora.client.util.AuroraKey.UNBOUND,
                () -> cfg.hitregScoreLeftKey,  v -> cfg.hitregScoreLeftKey = v);
        com.aurora.client.util.AuroraKeybinds.register(
                "key.aurora.hitreg_score_right",
                com.aurora.client.util.AuroraKey.UNBOUND,
                () -> cfg.hitregScoreRightKey, v -> cfg.hitregScoreRightKey = v);
        com.aurora.client.util.AuroraKeybinds.register(
                "key.aurora.hitreg_score_send",
                com.aurora.client.util.AuroraKey.UNBOUND,
                () -> cfg.hitregScoreSendKey,  v -> cfg.hitregScoreSendKey = v);
        com.aurora.client.util.AuroraKeybinds.register(
                "key.aurora.hitreg_score_reset",
                com.aurora.client.util.AuroraKey.UNBOUND,
                () -> cfg.hitregScoreResetKey, v -> cfg.hitregScoreResetKey = v);
    }
}

