package com.aurora.client.screen;

import com.aurora.client.AuroraClient;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.profile.ProfileManager;
import com.aurora.client.screen.setting.*;
import com.aurora.client.theme.GlassStyle;
import com.aurora.client.theme.ThemeMode;
import com.aurora.client.theme.ThemeRoundness;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Registry of feature metadata. Features are split into two tabs:
 * <ul>
 *   <li><b>Modules</b> â€” Aurora's gameplay/visual features (the grid),
 *       plus the combined "Miscellaneous" entry that groups the smaller
 *       settings (camera, frame pacer, latency, tick sync, input, servers,
 *       compliance, accessibility) behind one detail screen - an explicit
 *       "for now" grouping (moved here from Settings 2026-09-10)</li>
 *   <li><b>Settings</b> - global client settings: title screen, fonts,
 *       theme (moved here from Modules 2026-09-09), and the UI fps limit</li>
 * </ul>
 */
public final class FeatureRegistry {

    private static final List<FeatureMetadata> MODULES = new ArrayList<>();
    private static final List<FeatureMetadata> SETTINGS = new ArrayList<>();
    private static boolean initialized = false;

    private FeatureRegistry() {}

    public static List<FeatureMetadata> modules() {
        if (!initialized) init();
        return MODULES;
    }

    public static List<FeatureMetadata> settings() {
        if (!initialized) init();
        return SETTINGS;
    }

    private static void init() {
        initialized = true;
        AuroraConfig cfg = AuroraConfig.get();

        // ============ MODULES TAB ============

        addWithSettings(MODULES, "world_map", "World Map",
                "A full-screen, pannable world map of your explored terrain. Chunks you visit are captured in the background and stitched into persistent region tiles on disk, so the map survives restarts and grows as you explore.",
                () -> cfg.worldMapEnabled, v -> cfg.worldMapEnabled = v,
                List.of(
                        new KeybindSetting("Open Key",
                                () -> cfg.worldMapKey, v -> cfg.worldMapKey = v)
                                .description("The key that opens the fullscreen world map. Drag to pan, scroll to zoom, and use the dimension button to browse other dimensions' saved terrain. Press ESC or Backspace while rebinding to clear it."),
                        SliderSetting.ofInt("Capture Budget",
                                () -> cfg.worldMapCaptureBudget, v -> cfg.worldMapCaptureBudget = v, 1, 8)
                                .description("How many chunks the map may re-sample per game tick. Higher values fill the map faster but use more frame time; 2 keeps the cost under ~1-2ms per tick."),
                        SliderSetting.ofInt("Region Cache",
                                () -> cfg.worldMapCacheRegions, v -> cfg.worldMapCacheRegions = v, 8, 192)
                                .description("How many 512x512 region tiles (32x32 chunks each) stay in memory at full zoom detail. 64 covers a fully zoomed-out 1080p viewport; far zoom-out uses a separate lightweight overview layer and is not limited by this.")
                ));

        addWithSettings(MODULES, "zoom", "Zoom",
                "Optical-style zoom bound to a key. Hold it to narrow your field of view and magnify whatever you're looking at — useful for spotting distant players, reading far-off signs, or lining up ranged shots without a spyglass.",
                () -> cfg.zoomEnabled, v -> cfg.zoomEnabled = v,
                List.of(
                        new KeybindSetting("Hold Key",
                                () -> cfg.zoomKey, v -> cfg.zoomKey = v)
                                .description("The key you hold to activate zoom. Release to return to normal view instantly. Press ESC or Backspace while rebinding to clear it."),
                        SliderSetting.of("Zoom Level",
                                () -> cfg.zoomLevel, v -> cfg.zoomLevel = v, 1.5, 50.0)
                                .description("How far the zoom magnifies. Higher values pull distant objects much closer but make the view more sensitive to mouse movement and harder to hold steady. 4-8x suits general spotting; 20x+ is for scouting far terrain."),
                        SliderSetting.of("Smoothness",
                                () -> cfg.zoomSmoothness, v -> cfg.zoomSmoothness = v, 0.01, 2.0)
                                .description("How gradually the zoom eases in and out when you press/release the key. Low values snap instantly; higher values give a smooth cinematic glide. Purely a feel preference — no effect on performance.")
                ));

        addWithSettings(MODULES, "full_bright", "Full Bright",
                "Overrides Minecraft's gamma so dark areas become fully visible. Caves, the underwater, and night-time terrain light up as if you had permanent Night Vision — without the screen-edge pulsing of the real potion effect.",
                () -> cfg.fullBright, v -> cfg.fullBright = v,
                List.of(
                        SliderSetting.ofInt("Brightness %",
                                () -> cfg.fullBrightGamma,
                                v -> cfg.fullBrightGamma = v, 100, 1500)
                                .description("How aggressively darkness is lifted. 100% is vanilla 'Bright'; higher values flatten shadows further until even unlit caves are clearly readable. Very high values wash out the world's contrast — most players prefer 500-1000%.")
                ));

        addWithSettings(MODULES, "no_fog", "No Fog",
                "Pushes back the distance fog that normally hides far terrain. You see further across oceans and open landscapes, but the game has to render more of the world each frame — so higher settings cost FPS.",
                () -> cfg.noFogEnabled, v -> cfg.noFogEnabled = v,
                List.of(
                        SliderSetting.of("Distance Multiplier",
                                () -> cfg.fogDistanceMultiplier,
                                v -> cfg.fogDistanceMultiplier = v, 1.0, 100.0)
                                .description("Multiplies how far the fog starts. 1x is vanilla; higher values progressively clear the haze for a longer view distance. The visible range is still capped by your Render Distance, so increases beyond that point only reduce fog density, not draw distance.")
                ));

        // ---- Pack Tweaks ----
        // Single feature consolidating the old totem_tweaks, shield_tweaks,
        // and water_lava modules. Each former feature gets
        // its own SectionHeaderSetting divider so the settings list stays
        // legible. Master toggle is ON if ANY sub-feature flag is on; the
        // setter flips all sub-flags together.
        addWithSettings(MODULES, "pack_tweaks", "Pack Tweaks",
                "A bundle of small quality-of-life visual tweaks — totem particle size, shield rendering, and water/lava clarity. Toggling the bundle on enables all of them; expand it to fine-tune each one individually.",
                () -> cfg.totemTweaksEnabled
                        || cfg.shieldTweaksEnabled
                        || cfg.shieldStatusEnabled
                        || cfg.underwaterClarityEnabled || cfg.lavaClarityEnabled,
                v -> {
                    cfg.totemTweaksEnabled       = v;
                    cfg.shieldTweaksEnabled      = v;
                    cfg.shieldStatusEnabled      = v;
                    cfg.underwaterClarityEnabled = v;
                    cfg.lavaClarityEnabled       = v;
                },
                java.util.Arrays.asList(
                        new SectionHeaderSetting("Totem"),
                        SliderSetting.ofInt("Particle Size %",
                                () -> cfg.totemParticleScale,
                                v -> cfg.totemParticleScale = v, 25, 100)
                                .description("Shrinks the burst of totem particles that erupts when you pop a Totem of Undying. Lower values clear the screen faster so you can see the fight again sooner after a pop — a common competitive preference. 100% is vanilla size."),

                        new SectionHeaderSetting("Shield Style"),
                        new BooleanSetting("Enabled",
                                () -> cfg.shieldTweaksEnabled,
                                v -> cfg.shieldTweaksEnabled = v)
                                .description("Repositions your held shield in first person so it blocks less of your view while still protecting you — making it easier to track an opponent while blocking. Turn off to keep the vanilla shield position."),
                        new EnumSetting<>("Style",
                                AuroraConfig.ShieldStyle.class,
                                () -> cfg.shieldStyle,
                                v -> cfg.shieldStyle = v)
                                .glassButton(true) // glass pilot: pack_tweaks detail screen
                                .valueDescriptions(style -> switch (style) {
                                    case VANILLA -> "Default first-person position — no transform applied.";
                                    case LOWERED -> "Pulls the shield downward to clear your field of view while still protecting you.";
                                    case SIDE -> "Rotates the shield edge-on and shifts it toward the outer edge of your screen. Automatically switches to a compact front-facing render while you are actively blocking.";
                                    case COMPACT -> "Uniformly shrinks the shield while keeping its default position.";
                                }),

                        new SectionHeaderSetting("Shield Statuses"),
                        new BooleanSetting("Enabled",
                                () -> cfg.shieldStatusEnabled,
                                v -> cfg.shieldStatusEnabled = v)
                                .description("Tints rendered shields by status. Your shield shows the Available color at all times, except during the brief cooldown after an axe disables it — then it turns Disabled. Other players' and mobs' cooldowns aren't synced, so theirs always show Available."),
                        new ColorSetting("Available Color",
                                () -> cfg.shieldStatusAvailableColor,
                                v -> cfg.shieldStatusAvailableColor = v)
                                .description("Applied to your shield whenever it is usable — including while actively blocking — and to all other entities' shields. Note: because shield rendering uses cutout blending, the alpha channel controls tint strength rather than transparency — lower alpha gives a subtler tint."),
                        new ColorSetting("Disabled Color",
                                () -> cfg.shieldStatusDisabledColor,
                                v -> cfg.shieldStatusDisabledColor = v)
                                .description("Applied to your shield only during the cooldown that follows an axe disabling it (the 'rendered unusable' window). Note: the alpha channel controls tint strength, not transparency — lower alpha gives a subtler tint."),

                        new SectionHeaderSetting("Water & Lava"),
                        SliderSetting.of("Underwater Fog Multiplier",
                                () -> cfg.underwaterFogMultiplier,
                                v -> cfg.underwaterFogMultiplier = v, 1.0, 50.0)
                                .description("Free in lakes/rivers. May cost FPS in deep ocean — tune down to 4-8x there."),
                        SliderSetting.of("Lava Fog Multiplier",
                                () -> cfg.lavaFogMultiplier,
                                v -> cfg.lavaFogMultiplier = v, 1.0, 50.0)
                                .description("Pushes back the thick orange fog when your head is submerged in lava, letting you see further through it. Invaluable for spotting ancient debris or an exit while swimming lava in the Nether. Higher = clearer."),
                        new BooleanSetting("Hide Lava Orange Overlay",
                                () -> cfg.lavaHideOverlay,
                                v -> cfg.lavaHideOverlay = v)
                                .description("Hides the orange tint when submerged. Also hides the on-fire screen tint.")
                ),
                // Prefixes match camelCase config field names via
                // AuroraConfig.resetByPrefix (String.startsWith). The broad
                // "shield" prefix is safe — every shield* field belongs to
                // Pack Tweaks. For totem we must be specific ("totemTweaks"
                // and "totemParticle") because the Totem Pop Counter module
                // also has totem* fields that must NOT be reset here.
                java.util.Arrays.asList(
                        "totemTweaks", "totemParticle", "shield",
                        "underwaterClarity", "underwaterFog",
                        "lavaClarity", "lavaFog", "lavaHide"));

        addWithSettings(MODULES, "player_health", "Entity Health",
                "Shows how much health nearby mobs and players have left, either as a precise number or as a row of heart icons that mirror the vanilla HUD. Great for judging whether one more hit will finish a target — in PvP and against tanky mobs alike.",
                () -> cfg.playerHealthIndicators, v -> cfg.playerHealthIndicators = v,
                List.of(
                        new EnumSetting<>("Display Mode",
                                AuroraConfig.HealthDisplayMode.class,
                                () -> cfg.playerHealthDisplayMode,
                                v -> cfg.playerHealthDisplayMode = v)
                                .valueDescriptions(m -> switch (m) {
                                    case NUMBER -> "A single heart symbol followed by the exact HP value (e.g. \"❤ 16\"). Best for precise math.";
                                    case HEARTS -> "A row of individual heart icons that mimic the vanilla health bar: each heart represents 2 HP. A full heart is bright red (gold for absorption), a half heart is dark red, and empty hearts are not shown. Extra hearts beyond the entity's normal maximum are gold.";
                                })
                                .description("Choose how health is drawn above each entity. Number is precise; Hearts is a quick visual read like your own health bar."),
                        new BooleanSetting("Only Attacked",
                                () -> cfg.playerHealthOnlyAttacked,
                                v -> cfg.playerHealthOnlyAttacked = v)
                                .description("Only show the health number above entities you've actually hit this session, instead of every entity in view. Keeps the screen uncluttered in crowded areas while still tracking your current targets.")
                ));

        addWithSettings(MODULES, "free_look", "FreeLook",
                "Hold a key to swivel the camera around your character without changing the direction you're actually moving or facing. Great for watching your back while running, lining up a scenic screenshot, or keeping an enemy in view while retreating. Releasing the key snaps your view back to where it was.",
                () -> cfg.freeLookEnabled, v -> cfg.freeLookEnabled = v,
                List.of(
                        new KeybindSetting("Hold Key",
                                () -> cfg.freeLookKey, v -> cfg.freeLookKey = v)
                                .description("Hold this key to detach the camera so you can look around freely; release to restore your original view. Press ESC or Backspace while rebinding to clear it."),
                        SliderSetting.of("Camera Sensitivity",
                                () -> cfg.freeLookSensitivity,
                                v -> cfg.freeLookSensitivity = v,
                                0.1, 3.0)
                                .description("Multiplier on top of vanilla MouseHandler sensitivity. 1.0 = native feel.")
                ));

        add(MODULES, "saturation_bar", "Saturation Bar",
                "Overlays your hidden saturation level on top of the food bar. Saturation is the buffer that must drain before your hunger shanks start dropping — seeing it tells you exactly when you'll next need to eat, so you can manage food efficiently and avoid wasting it.",
                () -> cfg.saturationBarEnabled, v -> cfg.saturationBarEnabled = v);

        addWithSettings(MODULES, "block_overlay", "Block Overlay",
                "Replaces the thin vanilla block-selection box with a customizable outline and/or colored fill on whatever block you're aiming at. Makes your target far easier to see, which helps with precise building, mining the right block, and lining up clutch placements.",
                () -> cfg.blockOverlayEnabled, v -> cfg.blockOverlayEnabled = v,
                List.of(
                        new BooleanSetting("Show Outline",
                                () -> cfg.blockOverlayOutlineEnabled,
                                v -> cfg.blockOverlayOutlineEnabled = v)
                                .description("Draws a colored wireframe around the targeted block's edges. This is the clearest, lowest-clutter way to highlight your target without obscuring its texture."),
                        new ColorSetting("Outline Color",
                                () -> cfg.blockOverlayOutlineColor,
                                v -> cfg.blockOverlayOutlineColor = v),
                        SliderSetting.of("Outline Width",
                                () -> cfg.blockOverlayLineWidth,
                                v -> cfg.blockOverlayLineWidth = v, 1.0, 6.0)
                                .description("Thickness of the outline in screen pixels. Uses a GPU-side line expansion shader, so lines render cleanly at any width on all GPUs."),
                        new BooleanSetting("See-Through Outline",
                                () -> cfg.blockOverlaySeeThrough,
                                v -> cfg.blockOverlaySeeThrough = v)
                                .description("When on, the outline renders without depth testing, so it shows through the block itself — the Lunar/NoRisk style look. Turn off for a more subtle outline that is occluded by geometry in front."),
                        new BooleanSetting("Show Fill",
                                () -> cfg.blockOverlayFillEnabled,
                                v -> cfg.blockOverlayFillEnabled = v)
                                .description("Tints the whole targeted block with a translucent color. More eye-catching than the outline alone, but it partially hides the block's texture. Combine with the outline or use on its own."),
                        new ColorSetting("Fill Color",
                                () -> cfg.blockOverlayFillColor,
                                v -> cfg.blockOverlayFillColor = v),
                        new EnumSetting<>("Color Mode", AuroraConfig.BlockOverlayMode.class,
                                () -> cfg.blockOverlayMode, v -> cfg.blockOverlayMode = v)
                                .glassButton(true) // glass pilot: block_overlay detail screen
                                .description("STATIC uses the fixed colors you picked above; RAINBOW cycles the hue automatically over time for a lively animated highlight. Rainbow ignores the manual color pickers."),
                        SliderSetting.of("Rainbow Speed",
                                () -> (double) cfg.blockOverlayRainbowSpeed,
                                v -> cfg.blockOverlayRainbowSpeed = (float) v, 0.1, 10.0)
                                .description("How fast the hue cycles when Color Mode is RAINBOW. Low values drift slowly through the spectrum; high values strobe quickly. Has no effect in STATIC mode.")
                ));

        addWithSettings(MODULES, "toggle_sprint_sneak", "Toggle Sprint/Sneak",
                "Turns sprint and sneak into press-once toggles instead of hold-to-keep keys. Tap to start sprinting or sneaking and it stays active until you tap again — saving your fingers on long journeys and letting you hold a sneak edge without keeping a key pressed. An optional HUD shows which toggles are active.",
                () -> cfg.toggleSprintSneakEnabled, v -> cfg.toggleSprintSneakEnabled = v,
                List.of(
                        new KeybindSetting("Toggle Sprint Key",
                                () -> cfg.toggleSprintKey, v -> cfg.toggleSprintKey = v)
                                .description("Press once to start sprinting, again to stop."),
                        new KeybindSetting("Toggle Sneak Key",
                                () -> cfg.toggleSneakKey, v -> cfg.toggleSneakKey = v)
                                .description("Press once to start sneaking, again to stop."),
                        new BooleanSetting("Show Status HUD",
                                () -> cfg.toggleSprintSneakHudEnabled,
                                v -> cfg.toggleSprintSneakHudEnabled = v),
                        new EnumSetting<>("HUD Display Mode",
                                AuroraConfig.ToggleSprintDisplayMode.class,
                                () -> cfg.toggleSprintSneakDisplayMode,
                                v -> cfg.toggleSprintSneakDisplayMode = v)
                                .valueDescriptions(m -> switch (m) {
                                    case BOTH -> "Classic two-row layout showing both \"Sprint: ON/OFF\" and \"Sneak: ON/OFF\" together in one module.";
                                    case INDIVIDUAL -> "Splits into two independent HUD modules (one for sprint, one for sneak) that you can drag, resize and show/hide separately in the HUD editor.";
                                    case BRACKETED_TEXT -> "Compact single line that only lists currently active toggles inside brackets, e.g. [Sprint] / [Sneak] / [Sprint] [Sneak]. Renders nothing while neither is active.";
                                })
                                .description("Controls how the toggle status appears on the HUD."),
                        new EnumSetting<>("HUD Background", AuroraConfig.HudBackground.class,
                                () -> cfg.sprintHudBgMode, v -> cfg.sprintHudBgMode = v),
                        new ColorSetting("HUD Background Color (when SOLID)",
                                () -> cfg.sprintHudBgColor, v -> cfg.sprintHudBgColor = v)
                ));

        // ---- Alerts (combined: armor durability + hunger + effect expiry) ----
        // Single module consolidating all on-screen popup warnings. Master
        // toggle is ON if ANY sub-alert is on; the setter flips all together.
        addWithSettings(MODULES, "alerts", "Alerts",
                "On-screen popup warnings for low armor durability, low hunger, and potion effects about to expire. Never get caught off-guard by breaking gear, starvation, or a buff running out mid-fight. All alerts share the same popup and sound system.",
                () -> cfg.armorAlertEnabled || cfg.toolDurabilityAlertEnabled
                        || cfg.hungerAlertEnabled || cfg.effectExpiryAlertEnabled,
                v -> {
                    cfg.armorAlertEnabled = v;
                    cfg.toolDurabilityAlertEnabled = v;
                    cfg.hungerAlertEnabled = v;
                    cfg.effectExpiryAlertEnabled = v;
                },
                List.of(
                        new SectionHeaderSetting("Armor & Tools"),
                        new BooleanSetting("Armor Durability Alert",
                                () -> cfg.armorAlertEnabled,
                                v -> cfg.armorAlertEnabled = v)
                                .description("Pops a warning when a piece of equipped armor is about to break, so you can swap or repair it before it's destroyed."),
                        new BooleanSetting("Also Alert Held Tools",
                                () -> cfg.toolDurabilityAlertEnabled,
                                v -> cfg.toolDurabilityAlertEnabled = v)
                                .description("When on, low-durability warnings also fire for the item in your main and off hands (sword, pickaxe, etc.), not just armor."),
                        SliderSetting.ofInt("Threshold (%)",
                                () -> cfg.armorAlertThresholdPct,
                                v -> cfg.armorAlertThresholdPct = v, 1, 100)
                                .description("Durability percentage at which the warning fires. Higher values warn you earlier (more lead time to react); lower values only alert at the last moment. Around 10-15% gives a good balance."),

                        new SectionHeaderSetting("Hunger"),
                        new BooleanSetting("Low Hunger Alert",
                                () -> cfg.hungerAlertEnabled,
                                v -> cfg.hungerAlertEnabled = v)
                                .description("Fires a popup when your hunger drumsticks drop below the threshold."),
                        SliderSetting.ofInt("Hunger Threshold",
                                () -> cfg.hungerAlertThreshold,
                                v -> cfg.hungerAlertThreshold = v, 1, 20)
                                .description("Hunger level (in drumsticks) at which the alert fires. 6 is a good default — it gives you time to eat before you start starving."),

                        new SectionHeaderSetting("Effect Expiry"),
                        new BooleanSetting("Effect Expiry Alert",
                                () -> cfg.effectExpiryAlertEnabled,
                                v -> cfg.effectExpiryAlertEnabled = v)
                                .description("Fires a popup when an active potion effect is about to run out, so you can re-apply it before it's gone."),
                        SliderSetting.ofInt("Expiry Threshold (seconds)",
                                () -> cfg.effectExpiryThresholdSeconds,
                                v -> cfg.effectExpiryThresholdSeconds = v, 1, 60)
                                .description("How many seconds before an effect expires the alert should fire. 10 seconds gives you time to re-buff; lower values alert closer to the wire."),

                        new SectionHeaderSetting("Sound"),
                        new BooleanSetting("Play Sound",
                                () -> cfg.armorAlertSound, v -> cfg.armorAlertSound = v)
                                .description("Also play an audio cue with the popup, so you notice the warning even when your eyes are elsewhere."),
                        new EnumSetting<>("Sound Choice",
                                AuroraConfig.ArmorAlertSoundChoice.class,
                                () -> cfg.armorAlertSoundChoice,
                                v -> cfg.armorAlertSoundChoice = v)
                                .description("Which sound effect plays for the alert. Pick one distinct enough to recognize instantly mid-game. Use Test Sound below to preview."),
                        new ButtonSetting("Test Sound",
                                com.aurora.client.feature.impl.ArmorAlertFeature::playPreview)
                                .description("Plays the currently selected alert sound once so you can preview it.")
                ),
                List.of("armorAlert", "toolDurability", "hungerAlert", "effectExpiry"));

        addWithSettings(MODULES, "crosshair", "Crosshair",
                "Replaces the vanilla crosshair with a fully customizable one — pick a preset shape or draw your own, set its size, thickness, gap and color, and add a separate indicator that appears when an attackable entity is in reach. A clearer, personalized crosshair makes aiming and reach timing easier.",
                () -> cfg.crosshairEnabled, v -> cfg.crosshairEnabled = v,
                List.of(
                        // §5: leading live preview of the selected preset's
                        // shape — the real CrosshairRenderer shape code, so
                        // what you see here is what renders in-game.
                        new CrosshairPreviewSetting("Preset Preview"),
                        new EnumSetting<>("Style", AuroraConfig.CrosshairStyle.class,
                                () -> cfg.crosshairStyle, v -> cfg.crosshairStyle = v)
                                .description("The crosshair shape. Presets include classic cross, dot, and others; CUSTOM uses the pixel canvas you draw below. Size/Thickness/Gap apply to the presets."),
                        SliderSetting.ofInt("Size", () -> cfg.crosshairSize, v -> cfg.crosshairSize = v, 1, 20)
                                .description("Overall scale, length, or radius of the crosshair."),
                        SliderSetting.ofInt("Thickness", () -> cfg.crosshairThickness, v -> cfg.crosshairThickness = v, 1, 6)
                                .description("Thickness of the crosshair. Ignored for CIRCLE and CUSTOM styles.")
                                .disabled(() -> cfg.crosshairStyle == AuroraConfig.CrosshairStyle.CIRCLE || cfg.crosshairStyle == AuroraConfig.CrosshairStyle.CUSTOM),
                        SliderSetting.ofInt("Gap", () -> cfg.crosshairGap, v -> cfg.crosshairGap = v, 0, 10)
                                .description("Creates broken shapes. Only enabled for CIRCLE or SQUARE.")
                                .disabled(() -> cfg.crosshairStyle != AuroraConfig.CrosshairStyle.CIRCLE && cfg.crosshairStyle != AuroraConfig.CrosshairStyle.SQUARE),
                        new ColorSetting("Color", () -> cfg.crosshairColor, v -> cfg.crosshairColor = v),
                        new PixelCanvasSetting("Custom Canvas (used when style = CUSTOM)",
                                () -> cfg.crosshairCustomPixels, v -> cfg.crosshairCustomPixels = v,
                                () -> cfg.crosshairCustomWidth, v -> cfg.crosshairCustomWidth = v,
                                () -> cfg.crosshairCustomHeight, v -> cfg.crosshairCustomHeight = v)
                                .description("Left-click to paint, right-click to erase, drag to stroke. Enter any width × height up to "
                                        + PixelCanvasSetting.MAX_DIM + " and press Apply — growing the grid first measures the real render cost on your machine. Default restores the vanilla 15×15 crosshair shape.")
                                .disabled(() -> cfg.crosshairStyle != AuroraConfig.CrosshairStyle.CUSTOM),
                        new BooleanSetting("Indicator (entity in reach)",
                                () -> cfg.crosshairIndicatorEnabled, v -> cfg.crosshairIndicatorEnabled = v),
                        new EnumSetting<>("Indicator Style", AuroraConfig.CrosshairStyle.class,
                                () -> cfg.crosshairIndicatorStyle, v -> cfg.crosshairIndicatorStyle = v)
                                .disabled(() -> !cfg.crosshairIndicatorEnabled),
                        SliderSetting.ofInt("Indicator Size",
                                () -> cfg.crosshairIndicatorSize, v -> cfg.crosshairIndicatorSize = v, 1, 20)
                                .disabled(() -> !cfg.crosshairIndicatorEnabled),
                        SliderSetting.ofInt("Indicator Thickness",
                                () -> cfg.crosshairIndicatorThickness, v -> cfg.crosshairIndicatorThickness = v, 1, 6)
                                .disabled(() -> !cfg.crosshairIndicatorEnabled || cfg.crosshairIndicatorStyle == AuroraConfig.CrosshairStyle.CIRCLE || cfg.crosshairIndicatorStyle == AuroraConfig.CrosshairStyle.CUSTOM),
                        SliderSetting.ofInt("Indicator Gap",
                                () -> cfg.crosshairIndicatorGap, v -> cfg.crosshairIndicatorGap = v, 0, 10)
                                .disabled(() -> !cfg.crosshairIndicatorEnabled || (cfg.crosshairIndicatorStyle != AuroraConfig.CrosshairStyle.CIRCLE && cfg.crosshairIndicatorStyle != AuroraConfig.CrosshairStyle.SQUARE)),
                        new ColorSetting("Indicator Color",
                                () -> cfg.crosshairIndicatorColor, v -> cfg.crosshairIndicatorColor = v)
                                .disabled(() -> !cfg.crosshairIndicatorEnabled),
                        new PixelCanvasSetting("Indicator Custom Canvas",
                                () -> cfg.crosshairIndicatorCustomPixels, v -> cfg.crosshairIndicatorCustomPixels = v,
                                () -> cfg.crosshairIndicatorCustomWidth, v -> cfg.crosshairIndicatorCustomWidth = v,
                                () -> cfg.crosshairIndicatorCustomHeight, v -> cfg.crosshairIndicatorCustomHeight = v)
                                .description("Drawn when aiming at a targetable entity. Left-click paints, right-click erases.")
                                .disabled(() -> !cfg.crosshairIndicatorEnabled || cfg.crosshairIndicatorStyle != AuroraConfig.CrosshairStyle.CUSTOM)
                ));
addWithSettings(MODULES, "hitbox", "Hitbox",
                "Draws entity hitboxes with your own color, line width, and optional eye-level and look-direction lines — a cleaner, always-on replacement for vanilla's F3+B boxes. Seeing exact hitboxes helps you understand reach and where an entity can actually be hit. Movement smoothing is automatic: Aurora detects servers that bundle or throttle movement packets (like minemen.club's anticheat) and smooths the hitbox in real time, so it never stutters or jumps.",
                () -> cfg.hitboxEnabled || cfg.hitboxTargetEnabled,
                v -> { cfg.hitboxEnabled = v; cfg.hitboxTargetEnabled = v; },
                List.of(
                        new SectionHeaderSetting("General"),
                        new KeybindSetting("Toggle Key",
                                () -> cfg.hitboxToggleKey, v -> cfg.hitboxToggleKey = v)
                                .description("Optional. Toggles both self and target hitbox halves on/off. Defaults to unbound."),
                        new BooleanSetting("Show Eye-Level Line",
                                () -> cfg.hitboxEyeLine, v -> cfg.hitboxEyeLine = v)
                                .description("Draws a box at eye height inside the hitbox, matching vanilla F3+B."),
                        new BooleanSetting("Show Look Direction",
                                () -> cfg.hitboxLookDirection, v -> cfg.hitboxLookDirection = v)
                                .description("Draws a line from the entity's eye showing which way it's facing."),
                        SliderSetting.of("Look Length",
                                () -> cfg.hitboxLookLength, v -> cfg.hitboxLookLength = v, 0.1, 20.0)
                                .description("Length of the look-direction line, in blocks."),

                        new SectionHeaderSetting("Appearance"),
                        SliderSetting.of("Line Width",
                                () -> cfg.hitboxLineWidth,
                                v -> cfg.hitboxLineWidth = v, 0.1, 6.0)
                                .description("Thickness of the hitbox outline in screen pixels. Uses a GPU-side line expansion shader, so lines render cleanly at any width on all GPUs."),
                        new BooleanSetting("See-Through Wireframe",
                                () -> cfg.hitboxSeeThrough,
                                v -> cfg.hitboxSeeThrough = v)
                                .description("When on, the full hitbox wireframe is always visible through the entity model — the signature Lunar/NoRisk competitive-client look. Turn off for depth-tested wireframe where edges behind geometry are properly occluded."),
                        new ColorSetting("Hitbox Color",
                                () -> cfg.hitboxColor, v -> cfg.hitboxColor = v),

                        new SectionHeaderSetting("Projectiles"),
                        new BooleanSetting("Separate Projectile Color",
                                () -> cfg.hitboxProjectileColorEnabled, v -> cfg.hitboxProjectileColorEnabled = v)
                                .description("Toggle a separate color specifically for projectile hitboxes (arrows, fireballs, etc.)."),
                        new ColorSetting("Projectile Color",
                                () -> cfg.hitboxProjectileColor, v -> cfg.hitboxProjectileColor = v),
                        SliderSetting.of("Projectile Line Width",
                                () -> cfg.hitboxProjectileLineWidth,
                                v -> cfg.hitboxProjectileLineWidth = v, 0.1, 6.0),

                        new SectionHeaderSetting("Targeted Entity"),
                        new BooleanSetting("Targeted Eye-Level Line",
                                () -> cfg.hitboxTargetEyeLine, v -> cfg.hitboxTargetEyeLine = v)
                                .description("Uses the targeted-entity color/width for the entity you're currently aiming at."),
                        new BooleanSetting("Targeted Look Direction",
                                () -> cfg.hitboxTargetLookDirection, v -> cfg.hitboxTargetLookDirection = v),
                        new ColorSetting("Targeted Color",
                                () -> cfg.hitboxTargetColor, v -> cfg.hitboxTargetColor = v)
                                .description("Color used for the entity you're currently aiming at, to make it stand out.")
                ));

        addWithSettings(MODULES, "hit_color", "Hit Color",
                "Recolors the red flash that overlays an entity when it takes damage. A brighter or more saturated flash makes it much easier to confirm your hits landed during a fast fight — useful feedback in PvP and mob grinding.",
                () -> cfg.hitColorEnabled, v -> cfg.hitColorEnabled = v,
                List.of(
                        new ColorSetting("Flash Color", () -> cfg.hitColor, v -> cfg.hitColor = v)
                                .description("The color (and opacity) of the damage flash. Pick something that pops against the mobs you fight most — e.g. bright white or cyan reads more clearly than the default dim red."),
                        new BooleanSetting("Tint Armor",
                                () -> cfg.hitColorTintArmor,
                                v -> cfg.hitColorTintArmor = v)
                                .description("Also tints worn armor with the flash color when an entity is hurt.")
                ));

        // HUD MODULES â€” each has Background mode + Background Color

        addWithSettings(MODULES, "info_module", "Info HUD",
                "A configurable corner readout of useful at-a-glance info — FPS, coordinates, time of day, facing, biome, dimension, light level, playtime and more. Toggle exactly the rows you want so you get the data you care about without opening the F3 debug screen.",
                () -> cfg.infoEnabled, v -> cfg.infoEnabled = v,
                List.of(
                        new BooleanSetting("Show FPS", () -> cfg.showFps, v -> cfg.showFps = v),
                        new BooleanSetting("Show Coords", () -> cfg.showCoords, v -> cfg.showCoords = v),
                        new BooleanSetting("Show Time", () -> cfg.showTime, v -> cfg.showTime = v),
                        new BooleanSetting("Show Facing",
                                () -> cfg.infoShowFacing, v -> cfg.infoShowFacing = v),
                        new BooleanSetting("Show In-chunk Position",
                                () -> cfg.infoShowChunkLocal, v -> cfg.infoShowChunkLocal = v),
                        new BooleanSetting("Show Chunk Coords",
                                () -> cfg.infoShowChunkCoords, v -> cfg.infoShowChunkCoords = v),
                        new BooleanSetting("Show Y vs Sea Level",
                                () -> cfg.infoShowYRelSea, v -> cfg.infoShowYRelSea = v),
                        new BooleanSetting("Show Biome",
                                () -> cfg.infoShowBiome, v -> cfg.infoShowBiome = v),
                        new BooleanSetting("Show Dimension",
                                () -> cfg.infoShowDimension, v -> cfg.infoShowDimension = v),
                        new BooleanSetting("Show Light Level",
                                () -> cfg.infoShowLightAtFeet, v -> cfg.infoShowLightAtFeet = v),
                        new BooleanSetting("Show Frame Time",
                                () -> cfg.infoShowFrameTime, v -> cfg.infoShowFrameTime = v)
                                .description("Average milliseconds per frame, derived from the FPS counter. Lower is smoother; watch for spikes that indicate stutter even when average FPS looks fine."),
                        new BooleanSetting("Show Memory",
                                () -> cfg.infoShowMemory, v -> cfg.infoShowMemory = v)
                                .description("JVM heap usage as a percentage and used/max in MB. A steadily climbing figure that never drops can hint at a memory leak; frequent jumps to 100% cause GC lag spikes."),
                        new BooleanSetting("Show Ping",
                                () -> cfg.infoShowPing, v -> cfg.infoShowPing = v)
                                .description("Round-trip latency to the current server in milliseconds. Only shown in multiplayer; updates each server tick."),
                        new BooleanSetting("Show CPS",
                                () -> cfg.infoShowCps, v -> cfg.infoShowCps = v)
                                .description("Left/right mouse clicks per second over the last second — handy for monitoring click speed without a separate CPS overlay."),
                        new BooleanSetting("Show Playtime",
                                () -> cfg.infoShowPlaytime, v -> cfg.infoShowPlaytime = v)
                                .description("Tracks real wall-clock time spent in any world. Pauses when the game is paused or no world is loaded."),
                        new BooleanSetting("Playtime per-World",
                                () -> cfg.infoPlaytimePerWorld, v -> cfg.infoPlaytimePerWorld = v)
                                .description("On = show time spent in the current world / server only. Off = show the global lifetime total."),
                        new ColorSetting("HUD Component Color", () -> cfg.hudColor, v -> cfg.hudColor = v)
                                .description("Text color shared by the plain-readout HUD modules (this one, CPS, Stats, Totem Pops, Reach, Armor text, Keystrokes labels and Ping's unknown state). Defaults to following your theme accent; pick an explicit color to override it."),
                        new EnumSetting<>("Background", AuroraConfig.HudBackground.class,
                                () -> cfg.infoBgMode, v -> cfg.infoBgMode = v)
                                .description("Backdrop drawn behind the HUD text. NONE is transparent; SOLID fills a panel with the color below (best for readability over bright scenery); other modes use the theme's blurred/translucent styles."),
                        new ColorSetting("Background Color (when SOLID)",
                                () -> cfg.infoBgColor, v -> cfg.infoBgColor = v)
                                .description("The fill color and opacity used when Background is set to SOLID. Ignored for other background modes.")
                ));

        addWithSettings(MODULES, "cps", "CPS",
                "Shows your current clicks-per-second as a HUD counter. Handy for practicing a consistent attack rhythm in PvP, verifying a clicking technique, or just satisfying curiosity about your click speed.",
                () -> cfg.cpsEnabled, v -> cfg.cpsEnabled = v,
                List.of(
                        new EnumSetting<>("Background", AuroraConfig.HudBackground.class,
                                () -> cfg.cpsBgMode, v -> cfg.cpsBgMode = v),
                        new ColorSetting("Background Color (when SOLID)",
                                () -> cfg.cpsBgColor, v -> cfg.cpsBgColor = v)
                ));

        addWithSettings(MODULES, "armor_hud", "Armor HUD",
                "Displays your four equipped armor pieces and their remaining durability on the HUD. Lets you watch your protection at a glance without opening the inventory, so you know when to repair or swap before something breaks mid-fight.",
                () -> cfg.armorHudEnabled, v -> cfg.armorHudEnabled = v,
                List.of(
                        new EnumSetting<>("Durability Format",
                                AuroraConfig.ArmorDurabilityFormat.class,
                                () -> cfg.armorDurabilityFormat,
                                v -> cfg.armorDurabilityFormat = v),
                        new BooleanSetting("Show Durability Bars",
                                () -> cfg.armorShowDurabilityBars,
                                v -> cfg.armorShowDurabilityBars = v),
                        new BooleanSetting("Horizontal Layout",
                                () -> cfg.armorHorizontal,
                                v -> cfg.armorHorizontal = v),
                        new EnumSetting<>("Background", AuroraConfig.HudBackground.class,
                                () -> cfg.armorHudBgMode, v -> cfg.armorHudBgMode = v)
                                .valueDescriptions(bg -> switch (bg) {
                                    case NONE -> "No background behind the armor icons.";
                                    case SOLID -> "Fills a panel behind the armor icons with the color below.";
                                    case AURORA -> "Aurora's themed gradient panel behind the armor icons.";
                                    case VANILLA -> "Each armor piece sits inside an authentic vanilla hotbar slot, with the durability readout drawn outside the slot — the look popularised by Uku's ArmorHUD.";
                                }),
                        new ColorSetting("Background Color (when SOLID)",
                                () -> cfg.armorHudBgColor, v -> cfg.armorHudBgColor = v)
                ));

        addWithSettings(MODULES, "reach_display", "Reach Display",
                "Shows the exact distance to the last entity you attacked. A practical tool for understanding your effective reach in PvP and for spotting suspiciously long reaches from others. Updates each time you land a hit.",
                () -> cfg.reachEnabled, v -> cfg.reachEnabled = v,
                List.of(
                        new EnumSetting<>("Background", AuroraConfig.HudBackground.class,
                                () -> cfg.reachBgMode, v -> cfg.reachBgMode = v),
                        new ColorSetting("Background Color (when SOLID)",
                                () -> cfg.reachBgColor, v -> cfg.reachBgColor = v)
                ));

        addWithSettings(MODULES, "potion_hud", "Potion HUD",
                "A cleaner replacement for the default status-effect icons, listing your active potion effects with a readable countdown timer. Makes it easy to track when buffs like Strength or Fire Resistance are about to expire so you can re-apply them in time.",
                () -> cfg.potionHudEnabled, v -> cfg.potionHudEnabled = v,
                List.of(
                        new EnumSetting<>("Background", AuroraConfig.HudBackground.class,
                                () -> cfg.potionBgMode, v -> cfg.potionBgMode = v),
                        new ColorSetting("Background Color (when SOLID)",
                                () -> cfg.potionBgColor, v -> cfg.potionBgColor = v)
                ));

        addWithSettings(MODULES, "ping", "Ping",
                "Surfaces connection latency as real numbers instead of the vague signal-bar icon — as a HUD readout, as a value in the tab list, and optionally under player nametags. Knowing actual ping helps you account for hit registration delay on a server and spot laggy players.",
                () -> cfg.pingHudEnabled || cfg.tabPingEnabled,
                v -> { cfg.pingHudEnabled = v; cfg.tabPingEnabled = v; },
                List.of(
                        new BooleanSetting("Show Ping HUD",
                                () -> cfg.pingHudEnabled,
                                v -> cfg.pingHudEnabled = v)
                                .description("Shows your ping as a corner HUD module."),
                        new BooleanSetting("Show in Tab List",
                                () -> cfg.tabPingEnabled,
                                v -> cfg.tabPingEnabled = v)
                                .description("Replaces the bar icon with a numeric ping value in the tab list."),
                        new BooleanSetting("Show in Player Nametags",
                                () -> cfg.nametagPingEnabled,
                                v -> cfg.nametagPingEnabled = v)
                                .description("Adds a colored ping value below each player's name.")
                ),
                List.of("pingHud", "tabPing", "nametagPing"));

        addWithSettings(MODULES, "totem_pop", "Totem Pop Counter",
                "Counts how many Totems of Undying you've popped, and optionally tracks nearby players' pops too. In crystal PvP and similar fights, knowing how many totems an opponent has burned through tells you how close they are to being finishable.",
                () -> cfg.totemPopEnabled, v -> cfg.totemPopEnabled = v,
                List.of(
                        new KeybindSetting("Reset Counter Key",
                                () -> cfg.totemResetKey, v -> cfg.totemResetKey = v)
                                .description("Press to clear self pops and all tracked players. Defaults to unbound."),
                        new BooleanSetting("Show Other Players",
                                () -> cfg.totemShowOthers, v -> cfg.totemShowOthers = v)
                                .description("Adds extra HUD lines for nearby players' pop counts."),
                        SliderSetting.ofInt("Top N Others",
                                () -> cfg.totemTopOthers, v -> cfg.totemTopOthers = v, 1, 10),
                        new BooleanSetting("Reset On Death",
                                () -> cfg.totemResetOnDeath, v -> cfg.totemResetOnDeath = v)
                                .description("Resets your own counter to 0 every time you die."),
                        new BooleanSetting("Show in Player Nametags",
                                () -> cfg.nametagTotemPopsEnabled, v -> cfg.nametagTotemPopsEnabled = v)
                                .description("Appends each player's pop count under their nametag."),
                        new EnumSetting<>("Background", AuroraConfig.HudBackground.class,
                                () -> cfg.totemPopBgMode, v -> cfg.totemPopBgMode = v),
                        new ColorSetting("Background Color (when SOLID)",
                                () -> cfg.totemPopBgColor, v -> cfg.totemPopBgColor = v),
                        // §7.3: destructive actions go last, never interleaved with
                        // adjustable settings. Deliberately NO new section here,
                        // unlike Stats/Hitreg: this screen is a single flat 8-row
                        // list with no sections at all — minting its first section
                        // header around one button would make the header do no
                        // grouping work. The trailing position satisfies the rule's
                        // intent (nothing harmless sits below it to mis-click).
                        // The reset used to sit between Reset Counter Key and
                        // Show Other Players.
                        new ButtonSetting("Reset Now",
                                () -> {
                                    com.aurora.client.feature.impl.TotemPopFeature f =
                                            com.aurora.client.feature.impl.TotemPopFeature.get();
                                    if (f != null) f.resetAll();
                                })
                ));

        // ---- Better Hitreg (BetterHitreg by Jass, integrated with permission) ----
        // Every setting of the original mod has a row here — including the
        // five that were only reachable through /hitreg or the properties
        // file (Unrender World, Solid Floor, grid size, all overlay colors),
        // since no command fallback remains. Reset covers the "hitreg"
        // prefix explicitly (the id-derived "betterHitreg" would match none).
        {
            List<FeatureSetting> hitregRows = new ArrayList<>();
            hitregRows.add(new SectionHeaderSetting("Hitreg"));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.TOGGLE, "Custom Hitreg")
                    .description("Plays your own hit feedback — the attack sound, the target's hurt animation and crit/sharpness particles — the moment you swing, instead of waiting for the server to confirm the hit. The server's own feedback for that hit is suppressed so you never hear it twice. This is the original mod's master switch; the card toggle above it turns the whole feature off."));
            hitregRows.add(SliderSetting.ofInt("Hitreg Delay (ms)",
                    () -> cfg.hitregDelayMs, v -> cfg.hitregDelayMs = v, 0, 300)
                    .description("How long to wait after your swing before playing the client-side feedback. 0 plays it on the very next frame; raise it to approximate a server's typical registration delay. This is a delay, not a switch — Custom Hitreg controls whether it plays at all."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.SAFE_REGS_ONLY, "Safe Regs Only")
                    .description("Only replace the server's feedback when the hit is very likely to register: skips your first hit on a target, hits right after a ghost, hits while someone else is also hitting them, and hits far from where you last swung."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.IGNORE_SHIELD_HOLDERS, "Ignore Shield Holders")
                    .description("Never use custom hitreg against players holding a shield, raised or not — shield desync is the most common cause of a confidently-played hit that the server then rejects."));

            hitregRows.add(new SectionHeaderSetting("Tracking"));
            // §4 live-value subtitles: these three toggles no longer alert
            // (chat output was retired at integration) — their whole
            // remaining purpose is the live figure, so it surfaces under
            // the label instead of behind the 1.5 s description tooltip.
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.ALERT_DELAYS, "Alert Delays")
                    .description(() -> "Average server registration delay over the last 100 tracked hits: "
                            + com.aurora.client.hitreg.Hitreg.last100Regs.getAverageDelay() + " ms. "
                            + "Measured from your swing to the server's damage packet for that target; hits over 500 ms are not counted. Chat alerts were retired — this toggle is kept for the live figure.")
                    .valueLine(() -> {
                        var q = com.aurora.client.hitreg.Hitreg.last100Regs;
                        return q.delaySampleSize() == 0 ? "no hits tracked yet" : q.getAverageDelay() + " ms average";
                    }));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.ALERT_GHOSTS, "Alert Ghosts")
                    .description(() -> "Ghosted share of the last 100 tracked hits: "
                            + com.aurora.client.hitreg.Hitreg.last100Regs.getGhostRatio() + "%. "
                            + "A ghost is a hit the server never animated within 500 ms. New-target, blocked, invisible-target and contested hits are excluded from the sample. Chat alerts were retired — this toggle is kept for the live figure.")
                    .valueLine(() -> {
                        var q = com.aurora.client.hitreg.Hitreg.last100Regs;
                        return q.ghostSampleSize() == 0 ? "no hits tracked yet" : q.getGhostRatio() + "% ghosted";
                    }));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.ALERT_INCONSISTENCIES, "Alert Misplaces")
                    .description(() -> "Misplaced share of the last 100 tracked knockback/critical hits: "
                            + com.aurora.client.hitreg.Hitreg.last100Regs.getInconsistencyRatio() + "%. "
                            + "A misplace is a hit the server registered as a different type than the one you landed (judged from the sound it sent back). Chat alerts were retired — this toggle is kept for the live figure.")
                    .valueLine(() -> {
                        var q = com.aurora.client.hitreg.Hitreg.last100Regs;
                        return q.inconsistencySampleSize() == 0 ? "no hits tracked yet" : q.getInconsistencyRatio() + "% misplaced";
                    }));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.TRACK_FIGHTS, "Track Fight Statistics")
                    .description("Record each completed fight (10 seconds to 10 minutes long, with at least one landed hit) into the Stats Overlay: fight count, time spent fighting, and both players' accuracy for the last fight. Defaults on; the old post-fight chat summary is gone."));
            // §3 footer: the only group on this screen whose purpose the
            // header + labels genuinely don't convey (the "Alert" toggles
            // no longer alert anything — chat output was retired; the
            // figures are the point now).
            hitregRows.add(new SectionFooterSetting("The alert toggles surface live figures from your rolling hit sample under their labels; Track Fight Statistics records completed fights into the Stats Overlay."));

            hitregRows.add(new SectionHeaderSetting("Audio"));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.SILENCE_OTHER_FIGHTS, "Mute Other Fights")
                    .description("Silence hit sounds (and hide hurt animations) that belong to other players' fights, so only your own exchange is audible. Also tightens the window used to attribute a sound to you or your target from 50 ms to 15 ms."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.LEGACY_SOUNDS, "1.8 Hit Sounds")
                    .description("Replace the modern attack sounds (sweep, crit, knockback, strong, weak) with the single classic hurt sound, both for your own feedback and for what the server sends."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.SILENCE_NON_HITS, "Mute Non-hit Sounds")
                    .description("Mute every player-sourced sound that is not an attack or hurt sound — footsteps, item use, armor equips — so hit sounds stand out."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.SILENCE_SELF, "Mute Your Hits")
                    .description("Play no sound for the hits you land (client-side feedback and the server's confirmation alike)."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.SILENCE_THEM, "Mute Their Hits")
                    .description("Play no sound when your target hits you — including your own hurt sound."));
            hitregRows.add(SliderSetting.of("Hit Muffling",
                    () -> cfg.hitregMuffleAmount, v -> cfg.hitregMuffleAmount = v, 0.0, 1.0).percent()
                    .description("Low-pass filter strength applied to your hit sounds through OpenAL EFX — higher values sound duller and further away. 0% leaves them untouched."));
            hitregRows.add(SliderSetting.of("Hit Sharpening",
                    () -> cfg.hitregSharpenAmount, v -> cfg.hitregSharpenAmount = v, 0.0, 1.0).percent()
                    .description("High-pass filter strength applied to your hit sounds — higher values sound thinner and crisper. 0% leaves them untouched."));
            hitregRows.add(SliderSetting.ofInt("Metronome (ticks)",
                    () -> cfg.hitregMetronome, v -> cfg.hitregMetronome = (v < 10 ? 0 : v), 0, 25)
                    .description("Play a click every N game ticks (20 ticks = 1 second) as a rhythm reference for combos. Values below 10 switch it off, matching the original mod."));

            hitregRows.add(new SectionHeaderSetting("Render"));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.HIDE_OTHER_FIGHTS, "Hide Other Fights")
                    .description("While you are in a fight, hide other players (and their text displays) that are more than 5 blocks from both you and your target, and drop particles beyond that range."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.HIDE_ANIMATIONS, "Hide Animations")
                    .description("Suppress every hurt animation — the red flash and the flinch — including the ones your own hits would play."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.HIDE_ARMOR, "Hide Armor")
                    .description("Do not render worn armor on any entity, so hitboxes and body movement stay readable."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.HIDE_ALL_PARTICLES, "Hide All Particles")
                    .description("Spawn no hit particles at all — crit, sweep and enchanted-hit — from either side. Fireworks are exempt."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.HIDE_OTHER_PARTICLES, "Hide Other Particles")
                    .description("Keep only crit and sweep particles; everything else, including sharpness sparkles, is dropped."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.PARTICLES_EVERY_HIT, "Always Hit Particles")
                    .description("Force the enchanted-hit sparkle on every registered hit, even without Sharpness, as a clear visual confirmation."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.RENDER_HITBOX, "Show Target Hitbox")
                    .description("Draw your current target's client-side (interpolated) hitbox. Turns red while the target is within your 3-block reach."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.RENDER_CROSS, "Show Target Cross")
                    .description("Draw a small cross at the closest point on the target's hitbox to your eyes — the spot your reach is measured to."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.RENDER_SERVER_HITBOX, "Show Server Hitbox")
                    .description("Draw the target's hitbox at its un-interpolated (last received) position — where the server currently thinks they are."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.RENDER_YOUR_REACH, "Show Your Hit Range")
                    .description("Draw a 3-block ring on the ground around you; it changes color while the target is in reach."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.RENDER_THEIR_REACH, "Show Their Hit Range")
                    .description("Draw the same 3-block ring around your target."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.RENDER_YOUR_JUMP, "Show Your Jump Range")
                    .description("Draw a 4-block ring around you — roughly the distance a jump closes before your next hit."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.RENDER_THEIR_JUMP, "Show Their Jump Range")
                    .description("Draw the same 4-block ring around your target."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.PERFECT_HIT_COLOR, "Perfect Hit Color")
                    .description("Flash the target (glow, or the hitbox color if one is shown) for half a second after a perfect hit — one landed on the first tick the target came into reach."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.JUMP_RESET_COLOR, "Jump Reset Color")
                    .description("Flash the target for half a second when you land a jump reset — jumping within a tick of being hit."));

            hitregRows.add(new SectionHeaderSetting("Practice Arena"));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.VOID_WORLD, "Unrender World")
                    .description("Stop rendering every chunk so only entities remain — a blank practice arena. Extremely invasive: the world is still there, you just cannot see it. Pair with Solid Floor or a grid. Was only reachable via /hitreg VoidWorld before."));
            hitregRows.add(hitregToggle(com.aurora.client.hitreg.settings.Toggle.SOLID_FLOOR, "Solid Floor")
                    .description("Draw a large flat plane at your ground level (color below) — gives Unrender World a floor to stand on."));
            hitregRows.add(SliderSetting.ofInt("Floor Grid Size (blocks)",
                    () -> cfg.hitregFloorGridSize, v -> cfg.hitregFloorGridSize = v, 0, 32)
                    .description("Spacing of a ground grid drawn around you (fades out at 16 blocks). 0 turns it off."));

            hitregRows.add(new SectionHeaderSetting("Colors"));
            hitregRows.add(hitregColor(com.aurora.client.hitreg.settings.Color.HITBOX_FAR, "Target Hitbox (Out of Reach)"));
            hitregRows.add(hitregColor(com.aurora.client.hitreg.settings.Color.HITBOX_NEAR, "Target Hitbox (In Reach)"));
            hitregRows.add(hitregColor(com.aurora.client.hitreg.settings.Color.CROSS_FAR, "Target Cross (Out of Reach)"));
            hitregRows.add(hitregColor(com.aurora.client.hitreg.settings.Color.CROSS_NEAR, "Target Cross (In Reach)"));
            hitregRows.add(hitregColor(com.aurora.client.hitreg.settings.Color.CROSS_FAR_WITH_HITBOX, "Cross with Hitbox (Out of Reach)"));
            hitregRows.add(hitregColor(com.aurora.client.hitreg.settings.Color.CROSS_NEAR_WITH_HITBOX, "Cross with Hitbox (In Reach)"));
            hitregRows.add(hitregColor(com.aurora.client.hitreg.settings.Color.SERVER_HITBOX, "Server Hitbox"));
            hitregRows.add(hitregColor(com.aurora.client.hitreg.settings.Color.YOUR_REACH_FAR, "Your Hit Range (Out of Reach)"));
            hitregRows.add(hitregColor(com.aurora.client.hitreg.settings.Color.YOUR_REACH_NEAR, "Your Hit Range (In Reach)"));
            hitregRows.add(hitregColor(com.aurora.client.hitreg.settings.Color.THEIR_REACH_FAR, "Their Hit Range (Out of Reach)"));
            hitregRows.add(hitregColor(com.aurora.client.hitreg.settings.Color.THEIR_REACH_NEAR, "Their Hit Range (In Reach)"));
            hitregRows.add(hitregColor(com.aurora.client.hitreg.settings.Color.YOUR_JUMP_FAR, "Your Jump Range (Out of Range)"));
            hitregRows.add(hitregColor(com.aurora.client.hitreg.settings.Color.YOUR_JUMP_NEAR, "Your Jump Range (In Range)"));
            hitregRows.add(hitregColor(com.aurora.client.hitreg.settings.Color.THEIR_JUMP_FAR, "Their Jump Range (Out of Range)"));
            hitregRows.add(hitregColor(com.aurora.client.hitreg.settings.Color.THEIR_JUMP_NEAR, "Their Jump Range (In Range)"));
            hitregRows.add(hitregColor(com.aurora.client.hitreg.settings.Color.PERFECT_HIT, "Perfect Hit Flash"));
            hitregRows.add(hitregColor(com.aurora.client.hitreg.settings.Color.JUMP_RESET, "Jump Reset Flash"));
            hitregRows.add(hitregColor(com.aurora.client.hitreg.settings.Color.GRID, "Floor Grid"));
            hitregRows.add(hitregColor(com.aurora.client.hitreg.settings.Color.FLOOR, "Solid Floor"));

            hitregRows.add(new SectionHeaderSetting("Keybinds"));
            hitregRows.add(new KeybindSetting("Open Settings Key",
                    () -> cfg.hitregSettingsKey, v -> cfg.hitregSettingsKey = v)
                    .description("Opens this screen from in-game. The original mod bound H; it defaults to unbound here since the Mods grid already reaches it. Press ESC or Backspace while rebinding to clear it."));
            hitregRows.add(new KeybindSetting("Switch Hand Key",
                    () -> cfg.hitregSwitchHandKey, v -> cfg.hitregSwitchHandKey = v)
                    .description("Swaps your main hand between left and right (with a short cooldown). Defaults to unbound."));
            hitregRows.add(new KeybindSetting("Score: Left +1",
                    () -> cfg.hitregScoreLeftKey, v -> cfg.hitregScoreLeftKey = v)
                    .description("Practice scoreboard: adds one to the left score. The score shows top-left of the HUD while either side is non-zero. Defaults to unbound (the original used the arrow keys)."));
            hitregRows.add(new KeybindSetting("Score: Right +1",
                    () -> cfg.hitregScoreRightKey, v -> cfg.hitregScoreRightKey = v)
                    .description("Practice scoreboard: adds one to the right score. Defaults to unbound."));
            hitregRows.add(new KeybindSetting("Score: Send to Chat",
                    () -> cfg.hitregScoreSendKey, v -> cfg.hitregScoreSendKey = v)
                    .description("Sends the current score as \"L-R\" to chat (only when a score is non-zero). Defaults to unbound."));
            hitregRows.add(new KeybindSetting("Score: Reset",
                    () -> cfg.hitregScoreResetKey, v -> cfg.hitregScoreResetKey = v)
                    .description("Resets both scores to zero. Defaults to unbound."));

            // §7.3: destructive actions go last, isolated in their own
            // section — never interleaved with adjustable settings. The
            // reset used to sit between Alert Misplaces and Track Fight
            // Statistics inside Tracking.
            hitregRows.add(new SectionHeaderSetting("Maintenance"));
            hitregRows.add(new ButtonSetting("Reset Tracked Stats", "Reset",
                    () -> com.aurora.client.hitreg.Hitreg.last100Regs = new com.aurora.client.hitreg.util.RegQueue(100))
                    .description("Clears the rolling last-100-hits sample behind the delay/ghost/misplace figures in Tracking. Does not touch fight statistics — those live in the Stats Overlay."));

            addWithSettings(MODULES, com.aurora.client.hitreg.BetterHitreg.FEATURE_ID, "Better Hitreg",
                    "Client-side hit registration feedback for PvP, from BetterHitreg by Jass: your hit sound, the target's hurt animation and particles play the instant you swing instead of after the server's round trip, while the server's late copy is suppressed. Also tracks ghosted and misplaced hits, records fight statistics into the Stats Overlay, and adds reach/jump rings, target and server hitboxes, sound muffling, and a practice arena.",
                    () -> cfg.hitregEnabled, v -> cfg.hitregEnabled = v,
                    hitregRows,
                    List.of("hitreg"));
            MODULES.get(MODULES.size() - 1).subtitle("Original project by Jass");
        }

        addWithSettings(MODULES, "stats", "Stats Overlay",
                "A combat readout — kills, deaths, K/D and session time, plus fight statistics from Better Hitreg: fights this session and lifetime, time spent fighting, and the last fight's duration with both players' accuracy. Session counters reset when you choose; lifetime fight totals persist across sessions and profiles. (Totem pops have their own dedicated counter module.)",
                () -> cfg.statsEnabled, v -> cfg.statsEnabled = v,
                List.of(
                        new BooleanSetting("Show Kills",
                                () -> cfg.statsShowKills, v -> cfg.statsShowKills = v)
                                .description("Kills landed this session. Attributed heuristically from melee hits, so ranged finishes and assists may not always be counted."),
                        new BooleanSetting("Show Deaths",
                                () -> cfg.statsShowDeaths, v -> cfg.statsShowDeaths = v)
                                .description("How many times you have died this session."),
                        new BooleanSetting("Show K/D",
                                () -> cfg.statsShowKd, v -> cfg.statsShowKd = v)
                                .description("Kill/death ratio. Deaths are floored at 1 so a clean session reads as your kill count."),
                        new BooleanSetting("Show Session Time",
                                () -> cfg.statsShowSession, v -> cfg.statsShowSession = v)
                                .description("Wall-clock time since the session began or you last reset the counters."),
                        new SectionHeaderSetting("Fights"),
                        new BooleanSetting("Show Fights",
                                () -> cfg.statsShowFights, v -> cfg.statsShowFights = v)
                                .description("Tracked fights this session and your lifetime total. A fight is an exchange of 10 seconds to 10 minutes with at least one landed hit, ended by the two of you separating by more than 30 blocks (Better Hitreg's tracker; needs its Track Fight Statistics toggle on)."),
                        new BooleanSetting("Show Fight Time",
                                () -> cfg.statsShowFightTime, v -> cfg.statsShowFightTime = v)
                                .description("Time spent inside tracked fights, this session and lifetime. Different from Session time, which is plain wall-clock since your last reset."),
                        new BooleanSetting("Show Last Fight",
                                () -> cfg.statsShowLastFight, v -> cfg.statsShowLastFight = v)
                                .description("Duration of the last tracked fight and both players' accuracy (landed hits over swings), the same figures the original mod printed after a fight."),
                        new SectionHeaderSetting("Session"),
                        new KeybindSetting("Reset Key",
                                () -> cfg.statsResetKey, v -> cfg.statsResetKey = v)
                                .description("Press to reset the Stats overlay counters. Defaults to unbound."),
                        new EnumSetting<>("Background", AuroraConfig.HudBackground.class,
                                () -> cfg.statsBgMode, v -> cfg.statsBgMode = v),
                        new ColorSetting("Background Color (when SOLID)",
                                () -> cfg.statsBgColor, v -> cfg.statsBgColor = v),

                        // §7.3: destructive actions go last, isolated in their own
                        // section — never interleaved with adjustable settings. The
                        // resets used to sit inside their data sections (Reset
                        // Lifetime Fight Totals mid-Fights, Reset Stats as the
                        // first Session row). Order within the section runs
                        // least-to-most destructive: session counters, then the
                        // persisted lifetime totals.
                        new SectionHeaderSetting("Maintenance"),
                        new ButtonSetting("Reset Stats", "Reset",
                                () -> {
                                    com.aurora.client.feature.impl.StatsTrackerFeature feat =
                                            com.aurora.client.feature.impl.StatsTrackerFeature.get();
                                    if (feat != null) feat.resetAll();
                                })
                                .description("Clears the session counters back to zero: kills, deaths, the session timer, session fights and the last-fight readout. Lifetime fight totals are kept."),
                        new ButtonSetting("Reset Lifetime Fight Totals", "Reset",
                                () -> {
                                    com.aurora.client.feature.impl.StatsTrackerFeature feat =
                                            com.aurora.client.feature.impl.StatsTrackerFeature.get();
                                    if (feat != null) feat.resetLifetimeFightTotals();
                                })
                                .description("Zeroes the persisted lifetime fight count and fight time. Separate from Reset Stats on purpose: these survive sessions and profile switches like playtime, so a session reset never touches them. Better Hitreg's own Reset Tracked Stats clears something else again — its rolling delay/ghost/misplace sample.")
                ));

        addWithSettings(MODULES, "waypoints", "Waypoints",
                "Place persistent world markers rendered as beacon beams and/or block highlights with a name and distance label. Find your way back to bases, mines, or points of interest — and have a waypoint dropped automatically wherever you die so you can recover your stuff.",
                () -> cfg.waypointsEnabled, v -> cfg.waypointsEnabled = v,
                List.of(
                        new ButtonSetting("Manage Waypoints…",
                                () -> {
                                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                                    if (mc != null) {
                                        mc.setScreen(new WaypointManagerScreen(mc.screen));
                                    }
                                }),
                        new ButtonSetting("Drop at Player Position",
                                () -> {
                                    com.aurora.client.feature.impl.WaypointFeature feat =
                                            com.aurora.client.feature.impl.WaypointFeature.get();
                                    if (feat != null) {
                                        feat.dropAtPlayer("Waypoint", 0xFFFFAA00, false);
                                    }
                                })
                                .description("Adds a waypoint at the player's current block position."),
                        new KeybindSetting("Drop Key",
                                () -> cfg.waypointDropKey, v -> cfg.waypointDropKey = v)
                                .description("Press to drop a waypoint at your current position. Defaults to unbound."),
                        new KeybindSetting("Open Manager Key",
                                () -> cfg.waypointManagerKey, v -> cfg.waypointManagerKey = v)
                                .description("Press to open the Waypoint Manager screen. Defaults to unbound."),
                        new EnumSetting<>("Display Style", AuroraConfig.WaypointDisplay.class,
                                () -> cfg.waypointDisplay, v -> cfg.waypointDisplay = v)
                                .description("BEAM = beacon column. BLOCK = highlight the exact block(s). BOTH = both."),
                        SliderSetting.of("Beam Width",
                                () -> cfg.waypointBeamWidth, v -> cfg.waypointBeamWidth = v, 0.1, 1.0),
                        SliderSetting.ofInt("Block Highlight Radius",
                                () -> cfg.waypointBlockRadius, v -> cfg.waypointBlockRadius = v, 0, 4)
                                .description("Widens the floor slab on the horizontal plane only — the highlight is always one block tall. 0 = single block, 1 = 3×3 slab, etc. Only used when display style includes BLOCK."),
                        new BooleanSetting("Show Label",
                                () -> cfg.waypointShowLabel, v -> cfg.waypointShowLabel = v)
                                .description("Billboarded name + distance above the beam."),
                        SliderSetting.of("Label Scale",
                                () -> cfg.waypointLabelScale, v -> cfg.waypointLabelScale = v, 0.5, 2.5),
                        new BooleanSetting("Death Waypoint",
                                () -> cfg.deathWaypointEnabled, v -> cfg.deathWaypointEnabled = v)
                                .description("Automatically drops a waypoint where you died."),
                        new BooleanSetting("Replace Previous Death Waypoint",
                                () -> cfg.deathWaypointReplacesPrevious,
                                v -> cfg.deathWaypointReplacesPrevious = v)
                                .description("When on, only the most recent death waypoint is kept."),
                        SliderSetting.ofInt("Max Death Waypoints",
                                () -> cfg.deathWaypointMaxCount, v -> cfg.deathWaypointMaxCount = v, 1, 20)
                                .description("How many death waypoints to keep when Replace Previous is off; the oldest is pruned automatically. Death waypoints live in the Waypoints feature's list and also appear on the World Map as X markers."),
                        new ColorSetting("Death Waypoint Color",
                                () -> cfg.deathWaypointColor, v -> cfg.deathWaypointColor = v)
                ));

        addWithSettings(MODULES, "minimap", "Minimap",
                "A top-down map of the world around you, shown as a HUD overlay. Renders terrain sampled from loaded chunks, with waypoints and nearby entities drawn as colored dots. Optional circular frame, auto-rotation to match your facing, and a compass so you always know where north is.",
                () -> cfg.minimapEnabled, v -> cfg.minimapEnabled = v,
                List.of(
                        new KeybindSetting("Toggle Key",
                                () -> cfg.minimapToggleKey, v -> cfg.minimapToggleKey = v)
                                .description("Press to show or hide the minimap HUD overlay, independently of the fullscreen World Map. Defaults to unbound."),

                        new SectionHeaderSetting("Shape & Size"),
                        SliderSetting.ofInt("Size (px)",
                                () -> cfg.minimapSize, v -> cfg.minimapSize = v, 64, 256)
                                .description("Edge length of the minimap in pixels. Larger shows more detail but takes more screen space. 128 is a good default for most resolutions."),
                        new BooleanSetting("Circular",
                                () -> cfg.minimapCircular, v -> cfg.minimapCircular = v)
                                .description("Renders the minimap as a round disc instead of a square. Corners outside the inscribed circle are masked with the border color."),
                        new BooleanSetting("Rotate with Player",
                                () -> cfg.minimapRotateWithPlayer, v -> cfg.minimapRotateWithPlayer = v)
                                .description("When on, the map rotates so your facing direction is always up. When off, north is always up and the player arrow rotates to show your heading."),

                        new SectionHeaderSetting("Terrain"),
                        new BooleanSetting("Show Terrain",
                                () -> cfg.minimapShowTerrain, v -> cfg.minimapShowTerrain = v)
                                .description("Draws block colors sampled from loaded chunks. Turn off for a blank map with just the overlays."),
                        SliderSetting.of("Terrain Alpha",
                                () -> cfg.minimapTerrainAlpha, v -> cfg.minimapTerrainAlpha = v, 0.0, 1.0)
                                .description("Opacity of the terrain layer. Lower values make the terrain translucent so the background shows through."),
                        new BooleanSetting("Use World Map Cache",
                                () -> cfg.minimapUseWorldMapCache, v -> cfg.minimapUseWorldMapCache = v)
                                .description("Reads terrain from the World Map's captured region tiles — the same cache the fullscreen map draws — instead of re-sampling chunk data. Cheaper per frame and identical to the fullscreen map. Columns not yet captured (and everything, while the World Map feature is off) fall back to live chunk sampling."),

                        new SectionHeaderSetting("Overlays"),
                        new BooleanSetting("Show Waypoints",
                                () -> cfg.minimapShowWaypoints, v -> cfg.minimapShowWaypoints = v)
                                .description("Draws your active waypoints as colored diamonds on the map. Uses the same waypoints as the Waypoints feature — drop one and it appears here."),
                        new BooleanSetting("Waypoint Distance",
                                () -> cfg.minimapShowWaypointDistance, v -> cfg.minimapShowWaypointDistance = v)
                                .description("Shows the block distance beside waypoints that sit outside the minimap's view, pinned to the rim (a simple direction + distance indicator). At most four labels are drawn."),
                        new BooleanSetting("Show Entities",
                                () -> cfg.minimapShowEntities, v -> cfg.minimapShowEntities = v)
                                .description("Draws nearby living entities as dots on the map. Configure which entity types appear below."),
                        new BooleanSetting("Show Hostile Mobs",
                                () -> cfg.minimapShowHostiles, v -> cfg.minimapShowHostiles = v)
                                .description("Draws zombies, skeletons, and other monsters as red dots. Useful for PvP awareness and avoiding threats."),
                        new BooleanSetting("Show Passive Mobs",
                                () -> cfg.minimapShowPassives, v -> cfg.minimapShowPassives = v)
                                .description("Draws animals and other peaceful mobs as green dots."),
                        new BooleanSetting("Show Players",
                                () -> cfg.minimapShowPlayers, v -> cfg.minimapShowPlayers = v)
                                .description("Draws other players as white dots on the map."),
                        new BooleanSetting("Show Self Arrow",
                                () -> cfg.minimapShowSelfArrow, v -> cfg.minimapShowSelfArrow = v)
                                .description("Draws an arrow at the center showing which direction you're facing."),
                        new BooleanSetting("Show Compass",
                                () -> cfg.minimapShowCompass, v -> cfg.minimapShowCompass = v)
                                .description("Draws N/E/S/W letters around the edge of the minimap."),
                        new BooleanSetting("Show Coordinates",
                                () -> cfg.minimapShowCoords, v -> cfg.minimapShowCoords = v)
                                .description("Shows your current X/Y/Z below the minimap."),

                        new SectionHeaderSetting("Zoom"),
                        SliderSetting.ofInt("Zoom (blocks across)",
                                () -> cfg.minimapZoom, v -> cfg.minimapZoom = v, 16, 256)
                                .description("How many blocks fit across the minimap's diameter. Lower = more zoomed in (more detail per pixel); higher = wider view of the surrounding area."),

                        new SectionHeaderSetting("Appearance"),
                        new ColorSetting("Border Color",
                                () -> cfg.minimapBorderColor, v -> cfg.minimapBorderColor = v)
                                .description("Color of the frame ring around the minimap and the compass letters. Default (0) follows the theme accent; pick a color to override."),
                        new ColorSetting("Coordinate Text Color",
                                () -> cfg.minimapCoordColor, v -> cfg.minimapCoordColor = v)
                                .description("Color of the X/Y/Z readout shown below the minimap when Show Coordinates is on.")
                ),
                List.of("minimap"));

        addWithSettings(MODULES, "container_preview", "Container Preview",
                "Hover over a shulker box (or your ender chest) in any inventory to see its full contents in a grid tooltip — no need to place and open it. Massively speeds up sorting storage and finding the right shulker at a glance.",
                () -> cfg.containerPreviewEnabled, v -> cfg.containerPreviewEnabled = v,
                List.of(
                        new BooleanSetting("Shulker Boxes",
                                () -> cfg.containerPreviewShulker,
                                v -> cfg.containerPreviewShulker = v),
                        new BooleanSetting("Ender Chest",
                                () -> cfg.containerPreviewEnderChest,
                                v -> cfg.containerPreviewEnderChest = v)
                                .description("Shows your ender chest contents. Only accurate after you've opened it at least once this session."),
                        new BooleanSetting("Show Stack Counts",
                                () -> cfg.containerPreviewShowCounts,
                                v -> cfg.containerPreviewShowCounts = v)
                ));

        addWithSettings(MODULES, "item_physics", "Item Physics",
                "Gives dropped items more natural physics — they lie flat where they land instead of hovering and spinning, and tumble realistically while airborne. Purely cosmetic flair that makes loot on the ground look more grounded and tactile.",
                () -> cfg.itemPhysicsEnabled, v -> cfg.itemPhysicsEnabled = v,
                List.of(
                        new BooleanSetting("Lie Flat on Ground",
                                () -> cfg.itemPhysicsFlatOnGround,
                                v -> cfg.itemPhysicsFlatOnGround = v)
                                .description("When the item touches the ground, kill the bob and lay the sprite face-up."),
                        SliderSetting.of("Air Tumble Strength",
                                () -> cfg.itemPhysicsTumbleStrength,
                                v -> cfg.itemPhysicsTumbleStrength = v, 0.0, 2.0)
                                .description("Adds an X-axis flip scaled by motion while the item is airborne. 0 disables.")
                ));

        // Particles — list every vanilla particle as a collapsible row,
        // wrapped in a single ParticleConfigSetting that provides a smart
        // search bar (like the Item Scale module) to filter the long list.
        // Each row exposes a visibility toggle, a 0–150% scale slider, and
        // a color overlay picker. Sorted alphabetically by display name so
        // the list is navigable. Built once at registry init; the registry
        // contents don't change at runtime so re-iteration on screen open
        // is unnecessary.
        List<ParticleRowSetting> particleRows = new ArrayList<>();
        List<Identifier> particleIds = new ArrayList<>();
        for (ParticleType<?> type : BuiltInRegistries.PARTICLE_TYPE) {
            Identifier key = BuiltInRegistries.PARTICLE_TYPE.getKey(type);
            if (key != null) particleIds.add(key);
        }
        particleIds.sort(Comparator.comparing(Identifier::getPath));
        for (Identifier id : particleIds) {
            particleRows.add(new ParticleRowSetting(id.toString(), humanizeParticleId(id.getPath())));
        }
        addWithSettings(MODULES, "particles", "Particles",
                "Per-particle control over every vanilla particle type — hide ones you find distracting, resize the rest, or recolor them with a custom tint. Use the search bar to quickly find a specific particle by name. Great for cutting visual clutter (e.g. hiding explosion or splash-potion particles in busy fights) or reducing particle load for a small FPS gain.",
                () -> cfg.particleControlsEnabled, v -> cfg.particleControlsEnabled = v,
                List.of(new ParticleConfigSetting(particleRows)));

        addWithSettings(MODULES, "item_scale", "Item Scale",
                "Customize the scale, rotation (pitch, yaw, roll), and screen translation of individual item models. Type any item name in the search bar and click '+' to start customizing its model to fit your visual preferences.",
                () -> cfg.itemScaleEnabled, v -> cfg.itemScaleEnabled = v,
                List.of(
                        SliderSetting.of("Main-Hand Default Scale",
                                () -> (double) cfg.mainHandDefaultScale,
                                v -> cfg.mainHandDefaultScale = (float) v, 0.5, 1.5).percent()
                                .description("Fallback scale for any item held in the main hand that doesn't have a specific scale override below."),
                        SliderSetting.of("Off-Hand Default Scale",
                                () -> (double) cfg.offHandDefaultScale,
                                v -> cfg.offHandDefaultScale = (float) v, 0.5, 1.5).percent()
                                .description("Fallback scale for any item held in the off hand that doesn't have a specific scale override below."),
                        new ItemScaleSetting()
                ),
                // "itemScale" covers itemScaleEnabled + the itemScales overrides map;
                // the per-hand defaults have their own field names and need their own
                // prefixes (the FeatureMetadata javadoc's reason resetPrefixes exist).
                List.of("itemScale", "mainHandDefaultScale", "offHandDefaultScale"));

        addWithSettings(MODULES, "held_item_seams", "Held Item Seam Fix",
                "Removes the hairline seams that can appear between the faces of your first-person held items — thin texture-bleed lines that show up at certain camera angles, especially with higher-resolution resource packs. A tiny uniform scale-up (a fraction of a pixel by default) overlaps the adjacent cube faces just enough to hide them without visibly changing the item's size.",
                () -> cfg.fixHeldItemSeams, v -> cfg.fixHeldItemSeams = v,
                List.of(
                        SliderSetting.ofInt("Fix Strength",
                                () -> (int) Math.round((cfg.heldItemInflation - 1.0) * 1000.0),
                                v -> cfg.heldItemInflation = 1.0 + v / 1000.0, 0, 50)
                                .description("How far the held-item model is scaled up, in thousandths of its size (1 = 0.1%). 0 applies no scaling at all; 1 (the default) is a sub-pixel nudge that hides the seams while staying invisible; 5 and up hide them aggressively but items start to read as very slightly larger; 50 is the maximum the renderer will apply.")
                ),
                // Field names share no common camelCase prefix, so reset them explicitly.
                List.of("fixHeldItemSeams", "heldItemInflation"));

        add(SETTINGS, "custom_title", "Custom Title",
                "Replaces Minecraft's default main menu with Aurora's themed title screen — the diamond emblem, starfield backdrop, and restyled buttons. Purely cosmetic; turn it off to restore the vanilla menu. Takes effect the next time you return to the main menu.",
                () -> cfg.customTitleScreen, v -> cfg.customTitleScreen = v);

        addWithSettings(MODULES, "resourcepack_browser", "Resourcepack Browser",
                "Browse and install community resource packs directly from Modrinth — no browser, no manual file dropping. Search by name, preview the top results with thumbnails and download counts, then one-click install straight into your resourcepacks folder. Installed packs still need to be enabled in Options → Resource Packs, just like any manually-added pack.",
                () -> cfg.resourcepackBrowserEnabled, v -> cfg.resourcepackBrowserEnabled = v,
                List.of(
                        new ButtonSetting("Open Browser…",
                                () -> {
                                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                                    if (mc != null) {
                                        mc.setScreen(new ResourcePackBrowserScreen(mc.screen));
                                    }
                                })
                                .description("Opens the Modrinth resource-pack browser. Search, preview, and install packs straight into your game.")
                ));



        // ============ SETTINGS TAB ============

        addWithSettings(SETTINGS, "text_fonts", "Text & Fonts",
                "Choose the font Aurora uses across its own screens and, optionally, across all of Minecraft's vanilla UI. Pick a rendering scope and a Google Font — the choice applies live.",
                () -> cfg.textRendererMode != AuroraConfig.TextRendererMode.OFF,
                v -> cfg.textRendererMode = v ? AuroraConfig.TextRendererMode.AURORA_ONLY : AuroraConfig.TextRendererMode.OFF,
                List.of(
                        new EnumSetting<>("Text Renderer", AuroraConfig.TextRendererMode.class,
                                () -> cfg.textRendererMode, v -> cfg.textRendererMode = v)
                                .labels(AuroraConfig.TextRendererMode::getLabel)
                                .description("Which text uses the custom font: Off (vanilla everywhere), Aurora UI Only, or All Text (Aurora + every vanilla string)."),
                        new EnumSetting<>("Client Font", AuroraConfig.FontType.class,
                                () -> cfg.activeFont, v -> cfg.activeFont = v)
                                .labels(AuroraConfig.FontType::getLabel)
                                .description("The Google Font applied to text. SansSerif restores Minecraft's default font.")
                ));

        addWithSettings(SETTINGS, "theme", "Theme",
                "Customize Aurora Client's appearance, COSMIC-style: pick one accent color and the whole palette — backgrounds, surfaces, text — is derived automatically and stays legible. Light and dark mode, corner roundness, and background opacity included.",
                () -> cfg.themeEnabled, v -> cfg.themeEnabled = v,
                List.of(
                        new AccentSetting("Accent Color",
                                () -> cfg.themeOrDefault().accent,
                                v -> {
                                    cfg.themeOrDefault().accent = v;
                                    com.aurora.client.theme.ThemeManager.reload();
                                    persistThemeChange();
                                })
                                .description("The one color the whole theme is built from. Pick a preset swatch, or open Custom to use the color picker — every background, border, and text color is derived from it automatically."),
                        new SegmentedSetting<>("Mode", ThemeMode.class,
                                () -> cfg.themeOrDefault().mode != null ? cfg.themeOrDefault().mode : ThemeMode.DARK,
                                v -> {
                                    cfg.themeOrDefault().mode = v;
                                    com.aurora.client.theme.ThemeManager.reload();
                                    persistThemeChange();
                                })
                                .glassSegments(true) // glass pilot: Theme screen only
                                .description("Whether Aurora's UI renders in a dark or light palette. Both are derived from your accent, with text contrast checked automatically. There is no Auto mode — Minecraft has no reliable OS dark-mode signal."),
                        new SegmentedSetting<>("Corner Style", ThemeRoundness.class,
                                () -> cfg.themeOrDefault().roundness != null ? cfg.themeOrDefault().roundness : ThemeRoundness.ROUND,
                                v -> {
                                    cfg.themeOrDefault().roundness = v;
                                    com.aurora.client.theme.ThemeManager.reload();
                                    persistThemeChange();
                                },
                                ThemeRoundness::displayName)
                                .glassSegments(true) // glass pilot: Theme screen only
                                .description("How round Aurora's panels and buttons are: Round (the classic look), Slightly Round, or Square. Applies everywhere corners are drawn — including this screen."),
                        new SegmentedSetting<>("Glass Style", GlassStyle.class,
                                () -> cfg.themeOrDefault().glassStyle != null ? cfg.themeOrDefault().glassStyle : GlassStyle.FROSTED,
                                v -> {
                                    cfg.themeOrDefault().glassStyle = v;
                                    com.aurora.client.theme.ThemeManager.reload();
                                    persistThemeChange();
                                },
                                GlassStyle::displayName)
                                .glassSegments(true)
                                .description("How Aurora draws panel backgrounds. Frosted blurs whatever is behind each panel; Transparent skips the blur and uses a flat translucent fill, which is cheaper to render and suits lower-end hardware. Corner Style and Background Opacity apply to both."),
                        new ThemeOpacitySetting("Background Opacity",
                                () -> cfg.themeOrDefault().backgroundOpacity,
                                v -> {
                                    cfg.themeOrDefault().backgroundOpacity = v;
                                    com.aurora.client.theme.ThemeManager.reload();
                                    persistThemeChange();
                                })
                                .description("How transparent Aurora's panel backgrounds are. 100% is fully opaque; lower values let the world behind show through. Only transparency changes — the colors stay derived from your accent. Drag updates the look live; the change is saved when you release."),
                        new ThemePreviewSetting("Live Preview")
                                .description("A live mock-up using the exact colors and corner style the UI will use — changes apply here instantly, before you leave this screen.")
                ));

        addWithSettings(SETTINGS, "interface", "Interface",
                "Caps the framerate while an Aurora screen is open, independent of Minecraft's main FPS cap. A lower limit keeps the UI smooth while freeing GPU time for the world behind it.",
                () -> cfg.guiFpsLimit != 1000,
                v -> cfg.guiFpsLimit = v ? 60 : 1000,
                List.of(
                        new EnumSetting<>("UI FPS Limit", AuroraConfig.GuiFps.class,
                                () -> AuroraConfig.GuiFps.fromValue(cfg.guiFpsLimit), v -> cfg.guiFpsLimit = v.value)
                                .labels(AuroraConfig.GuiFps::label)
                                .description("The framerate Aurora screens run at. 'Unlimited' removes the cap so the GUI matches your main FPS.")
                ));

        addWithSettings(MODULES, "reflex", "Minecraft Reflex",
                "Uses the Nvidia Reflex principle to reduce rendering latency in Minecraft, automatically locking the frame by estimating the CPU and GPU time. Allows any GPU to use.",
                () -> cfg.reflexEnabled, v -> cfg.reflexEnabled = v,
                List.of(
                        SliderSetting.ofInt("Wait Time Offset",
                                () -> cfg.reflexWaitTimeOffset, v -> cfg.reflexWaitTimeOffset = v, -5000000, 5000000)
                                .description("Wait time offset in nanoseconds. Increase if GPU utilization drops, decrease (negative) if latency increases.")
                ));

        addWithSettings(MODULES, "animations", "Animations",
                "Animation & movement polish: swing & view-bob curves, classic 1.8-style damage camera tilt, configurable idle held-item sway with multiple movement curves, and frame-rate-independent entity movement smoothing that rounds off per-tick knockback kinks and absorbs multiplayer server throttle jitter.",
                () -> cfg.smoothAnimationsEnabled, v -> cfg.smoothAnimationsEnabled = v,
                List.of(
                        new SectionHeaderSetting("Swing"),
                        new EnumSetting<>("Swing Curve",
                                AuroraConfig.AnimationCurve.class,
                                () -> cfg.swingAnimationCurve,
                                v -> cfg.swingAnimationCurve = v)
                                .valueDescriptions(c -> switch (c) {
                                    case LINEAR -> "Vanilla — no easing. Constant arm velocity. Feels limp.";
                                    case SINE -> "Gentlest easing. Even acceleration in and out. Smooth but not punchy.";
                                    case SMOOTHSTEP -> "Cubic ease-in-out (3t²−2t³). Subtle smoothing on both ends.";
                                    case QUARTIC -> "Quartic ease-in-out. Holds endpoints flatter than cubic. Slightly weighty feel.";
                                    case QUINTIC, CUBIC_HERMITE -> "Quintic ease-in-out (Perlin's smootherstep). Heaviest pendulum feel — flat acceleration at endpoints.";
                                    case CUBIC_OUT -> "Cubic ease-out. Fast windup, slow follow-through. Combat-snappy.";
                                    case QUARTIC_OUT -> "Quartic ease-out. Very snappy windup, long follow-through. The most 'punchy' curve. Recommended for swings.";
                                }),
                        new EnumSetting<>("Swing Style",
                                AuroraConfig.SwingStyle.class,
                                () -> cfg.swingStyle,
                                v -> cfg.swingStyle = v)
                                .valueDescriptions(s -> switch (s) {
                                    case VANILLA -> "Modern curved first-person swing arc — the default look.";
                                    case LEGACY_1_8 -> "Steeper, punchier old-style arc reminiscent of 1.8. Pairs well with the Quartic Out swing curve.";
                                })
                                .description("Overrides the shape of the first-person attack swing. Legacy 1.8 adds a sharper chop and a wider outward sweep."),

                        new SectionHeaderSetting("View Bob"),
                        new EnumSetting<>("View Bob Curve",
                                AuroraConfig.AnimationCurve.class,
                                () -> cfg.viewBobCurve,
                                v -> cfg.viewBobCurve = v)
                                .valueDescriptions(c -> switch (c) {
                                    case LINEAR -> "Vanilla — uniform footstep cadence.";
                                    case SINE -> "Gentle wave shaping. Smooth, low-key.";
                                    case SMOOTHSTEP -> "Cubic ease-in-out. Subtle weight on each step. Recommended.";
                                    case QUARTIC -> "Quartic ease-in-out. Heavier step landing. Mild motion-sickness risk if View Bobbing is on.";
                                    case QUINTIC, CUBIC_HERMITE -> "Quintic — heaviest. May cause motion sickness with View Bobbing on; prefer disabling vanilla bob if you use this.";
                                    case CUBIC_OUT -> "Ease-out distorts the bob wave. Not recommended for view bob.";
                                    case QUARTIC_OUT -> "Strong ease-out. Causes weird wave shape on bob — not recommended.";
                                }),
                        SliderSetting.of("View Bob Amplitude",
                                () -> cfg.viewBobAmplitude,
                                v -> cfg.viewBobAmplitude = v, 0.0, 2.0).percent()
                                .description("Scales the camera head-bob strength. 100% is vanilla, 0% cancels the bob, 200% doubles it. Requires vanilla View Bobbing to be on."),

                        new SectionHeaderSetting("Damage Tilt"),
                        new BooleanSetting("Enable Damage Tilt",
                                () -> cfg.damageTiltEnabled, v -> cfg.damageTiltEnabled = v)
                                .description("Enable classic 1.7/1.8-style damage camera roll. The roll direction follows where the hit came from."),
                        SliderSetting.of("Damage Tilt Strength",
                                () -> cfg.damageTiltStrength,
                                v -> cfg.damageTiltStrength = v, 0.0, 1.0).percent()
                                .description("How far the camera rolls on each hit. 100% matches the pronounced 1.8 tilt."),

                        new SectionHeaderSetting("Idle Sway"),
                        new BooleanSetting("Idle Sway",
                                () -> cfg.idleSwayEnabled, v -> cfg.idleSwayEnabled = v)
                                .description("Adds a breathing/float to your held items while standing still. Pauses during swings so it never fights the attack animation. Supports very high intensities for dramatic drift."),
                        new EnumSetting<>("Idle Sway Curve",
                                AuroraConfig.IdleSwayCurve.class,
                                () -> cfg.idleSwayCurve,
                                v -> cfg.idleSwayCurve = v)
                                .valueDescriptions(c -> switch (c) {
                                    case SINE_BREATH -> "Single pure sine — a gentle, even breathing motion.";
                                    case LISSAJOUS -> "Lissajous (1:2) — a smooth figure-8 / infinity path.";
                                    case SMOOTHSTEP_DRIFT -> "Cubic ease shaping — flatter at the ends, more deliberate drift.";
                                    case DOUBLE_SINE -> "Double-frequency sine — quicker, shallower wobble (nervous/twitchy).";
                                    case PENDULUM -> "Triple-harmonic pendulum — rich, organic, pendulum-like sway.";
                                    case CIRCULAR -> "Circular orbit — the item traces a clean circle (very dramatic at high intensity).";
                                })
                                .description("Movement curve shaping the idle sway oscillation. Some curves look far more dramatic at high intensity than others."),
                        SliderSetting.of("Idle Sway Strength",
                                () -> cfg.idleSwayStrength,
                                v -> cfg.idleSwayStrength = v, 0.0, 10.0)
                                .description("How pronounced the idle sway is. 1.0 is a gentle breath; 5.0+ is dramatic drift. Some curves (Circular, Lissajous) shine at high values."),

                        new SectionHeaderSetting("Entity Movement"),
                        new BooleanSetting("Smooth Entity Movement",
                                () -> cfg.entityMovementSmoothingEnabled,
                                v -> cfg.entityMovementSmoothingEnabled = v)
                                .description("Eases other entities' rendered positions toward their tick-interpolated targets every frame. This smooths out the per-tick \"kinks\" that make knockback arcs look disjointed (velocity changes abruptly between ticks), and absorbs the freeze-then-leap jitter of multiplayer servers that bundle or throttle movement packets. Strength auto-scales with the detected server throttle. Your own player is never smoothed."),
                        SliderSetting.of("Smoothing Strength",
                                () -> cfg.entityMovementSmoothingStrength,
                                v -> cfg.entityMovementSmoothingStrength = v, 0.0, 1.0).percent()
                                .description("How aggressively entity positions are eased. 0% is a barely-there rounding of per-tick kinks (great for singleplayer); 100% is a very smooth glide that masks heavy server-side throttling. On throttled PvP servers the effective smoothing is automatically increased beyond this setting.")
                ),
                java.util.List.of(
                        "smoothAnimations", "swingStyle",
                        "viewBobAmplitude", "idleSway",
                        "entityMovementSmoothing",
                        "damageTilt"));

        add(MODULES, "hotbar_bounce", "Hotbar Bounce",
                "Pops a small bounce/pulse on a hotbar slot the moment an item lands in it or a stack grows. Gives you instant peripheral feedback that a pickup happened without looking down at the slot — handy when mining or fighting. Purely cosmetic.",
                () -> cfg.hotbarBounceEnabled, v -> cfg.hotbarBounceEnabled = v);

        // ---- Keystrokes Display ----
        addWithSettings(MODULES, "keystrokes", "Keystrokes",
                "A clean keystroke overlay: movement keys, mouse buttons, a CPS counter and jump/sneak inputs shown as one cohesive panel that lights up with a smooth animated accent while pressed. Popular for PvP montages, streaming, and analyzing your inputs. Fully draggable and resizable in the HUD editor.",
                () -> cfg.keystrokesEnabled, v -> cfg.keystrokesEnabled = v,
                List.of(
                        new BooleanSetting("Show WASD Keys",
                                () -> cfg.keystrokesShowMovement,
                                v -> cfg.keystrokesShowMovement = v)
                                .description("The W/A/S/D block, following your movement keybinds."),
                        new BooleanSetting("Show Mouse Buttons",
                                () -> cfg.keystrokesShowMouse,
                                v -> cfg.keystrokesShowMouse = v)
                                .description("Left and right mouse buttons below the movement keys."),
                        new BooleanSetting("Show Middle Mouse",
                                () -> cfg.keystrokesShowMiddleMouse,
                                v -> cfg.keystrokesShowMiddleMouse = v)
                                .description("Also show the middle (pick-block) mouse button.")
                                .disabled(() -> !cfg.keystrokesShowMouse),
                        new BooleanSetting("Show CPS Counter",
                                () -> cfg.keystrokesShowCps,
                                v -> cfg.keystrokesShowCps = v)
                                .description("Rolling clicks-per-second readout under the mouse buttons (left / right, 1-second window)."),
                        new BooleanSetting("Show Spacebar",
                                () -> cfg.keystrokesShowSpacebar,
                                v -> cfg.keystrokesShowSpacebar = v)
                                .description("A wide bar for the jump input."),
                        new BooleanSetting("Show Sneak/Sprint",
                                () -> cfg.keystrokesShowExtra,
                                v -> cfg.keystrokesShowExtra = v)
                                .description("Sneak and sprint keys in a split row, following your keybinds."),
                        new KeyListSetting("Extra Keys",
                                () -> cfg.keystrokesExtraKeys,
                                v -> cfg.keystrokesExtraKeys = v)
                                .description("Additional keys to track (Space, Shift, hotbar or ability keys). Click \"+ Add Key\", then press a key; ESC cancels. Keys auto-arrange in rows of three."),
                        new ColorSetting("Accent Color",
                                () -> cfg.keystrokesAccentColor,
                                v -> cfg.keystrokesAccentColor = v)
                                .description("The color a key turns while pressed. Defaults to following your theme accent; pick an explicit color to override it. Reset via \"Reset to defaults\"."),
                        SliderSetting.of("Panel Opacity",
                                () -> cfg.keystrokesBgOpacity,
                                v -> cfg.keystrokesBgOpacity = v, 0.0, 1.0).percent()
                                .description("Opacity of the backing panel only — key labels stay fully legible even at 0%."),
                        SliderSetting.of("Scale",
                                () -> {
                                    var mgr = com.aurora.client.AuroraClient.modules();
                                    var m = mgr == null ? null : mgr.get(com.aurora.client.hud.module.KeystrokesModule.ID);
                                    return m == null ? 1.0 : m.scale;
                                },
                                v -> {
                                    var mgr = com.aurora.client.AuroraClient.modules();
                                    var m = mgr == null ? null : mgr.get(com.aurora.client.hud.module.KeystrokesModule.ID);
                                    if (m != null) {
                                        m.scale = (float) Math.max(com.aurora.client.hud.module.HudModule.MIN_SCALE,
                                                Math.min(com.aurora.client.hud.module.HudModule.MAX_SCALE, v));
                                    }
                                },
                                com.aurora.client.hud.module.HudModule.MIN_SCALE,
                                com.aurora.client.hud.module.HudModule.MAX_SCALE)
                                .description("Uniform size multiplier for the whole cluster. You can also drag the module's corner in the HUD editor (Right Shift) to resize."),
                        new EnumSetting<>("Background", AuroraConfig.HudBackground.class,
                                () -> cfg.keystrokesBgMode, v -> cfg.keystrokesBgMode = v),
                        new ColorSetting("Background Color (when SOLID)",
                                () -> cfg.keystrokesBgColor, v -> cfg.keystrokesBgColor = v)
                ));

        // ---- Miscellaneous ----
        // Consolidates the eight former standalone Settings-tab tiles that
        // sat below "Interface" (Smooth Camera, Frame Pacer, Low Latency,
        // Tick Sync, Decoupled Input, Drag-to-Reorder Servers, Compliance
        // Mode, Accessibility) into one entry - an explicit "for now"
        // grouping, not a permanent taxonomy decision. Every original
        // setting row is preserved verbatim under its own SectionHeader,
        // and each section's "Enabled" row carries the exact getter/setter
        // that tile's header toggle used (config fields and behavior are
        // unchanged). Moved to the Modules grid 2026-09-10 (was a Settings-tab
        // entry); the Settings-tab-only settingsDetailOnly header+hint
        // presentation does not travel with the move - on the grid the
        // card's right-click opens the same detail screen, like every
        // other tile. Master toggle follows the Pack
        // Tweaks/Alerts pattern: ON if ANY sub-feature is on, and the
        // setter flips all sub-flags together. Reset is one unified reset
        // whose prefix list is exactly the union of the original tiles'
        // config fields - none of these had a reachable reset before
        // (Settings-tab entries had no detail screen), so this is new
        // capability, consistent with the other combined features.
        addWithSettings(MODULES, "miscellaneous", "Miscellaneous",
                "A temporary home for smaller settings that have not been given a dedicated feature screen of their own yet: smooth camera, frame pacing, latency, tick sync, input handling, server-list dragging, compliance and accessibility. Each keeps its own enable toggle and settings under its section; open the feature to configure them.",
                () -> cfg.smoothCamera || cfg.smoothFramePacer || cfg.lowLatencyRender
                        || cfg.tickSyncEnabled || cfg.inputSamplingDecoupled
                        || cfg.serverListDragReorder || cfg.complianceModeEnabled
                        || cfg.colorblindMode != AuroraConfig.ColorblindMode.OFF,
                v -> {
                    cfg.smoothCamera = v;
                    cfg.smoothFramePacer = v;
                    cfg.lowLatencyRender = v;
                    cfg.tickSyncEnabled = v;
                    cfg.inputSamplingDecoupled = v;
                    cfg.serverListDragReorder = v;
                    cfg.complianceModeEnabled = v;
                    if (!v) cfg.colorblindMode = AuroraConfig.ColorblindMode.OFF;
                    else if (cfg.colorblindMode == AuroraConfig.ColorblindMode.OFF)
                        cfg.colorblindMode = AuroraConfig.ColorblindMode.DEUTERANOMALY;
                },
                List.of(
                        new SectionHeaderSetting("Smooth Camera"),
                        new BooleanSetting("Enabled",
                                () -> cfg.smoothCamera,
                                v -> cfg.smoothCamera = v)
                                .description("Adds inertia to your mouse look so the camera eases into and out of movement instead of stopping instantly. Gives panning a weighty, cinematic feel — great for recording or relaxed play. Note: it adds aim latency, so competitive players usually leave it off."),
                        SliderSetting.of("Strength",
                                () -> cfg.smoothCameraStrength, v -> cfg.smoothCameraStrength = v, 0.05, 1.0)
                                .description("How much smoothing is applied. Low values add a barely-there glide that still feels responsive; high values produce heavy, floaty camera drift with noticeable lag between your mouse and the view. Start around 0.2-0.3 if you want subtle smoothing."),

                        new SectionHeaderSetting("Frame Pacer"),
                        new BooleanSetting("Enabled",
                                () -> cfg.smoothFramePacer,
                                v -> cfg.smoothFramePacer = v)
                                .description("A high-precision replacement for Minecraft's built-in FPS cap. Vanilla's limiter spaces frames unevenly, which you feel as micro-stutter even at high FPS. The Frame Pacer holds each frame to a near-exact interval, so motion looks visibly smoother at the same average framerate. Set your FPS cap in vanilla Video Settings; this controls *how* that cap is enforced."),
                        new EnumSetting<>("Strategy",
                                AuroraConfig.PacingStrategy.class,
                                () -> cfg.framePacingStrategy,
                                v -> cfg.framePacingStrategy = v)
                                .valueDescriptions(s -> switch (s) {
                                    case VANILLA -> "Disabled â€” uses Minecraft's default frame pacing. Notice the jitter at high FPS caps above ~64.";
                                    case YIELD -> "Pure yield-spin. Excellent precision but uses ~1 full CPU core. Best for desktops with cooling headroom.";
                                    case PARK -> "Sleeps for most of the wait â€” very low CPU. Slightly less stable timing (Â±1-2ms) but cool & quiet. Good for laptops.";
                                    case HYBRID -> "Recommended. Sleeps when far from the frame deadline, spins for the last microseconds. Best balance of precision and CPU.";
                                    case SPIN -> "Pure busy-loop. Maximum precision (sub-microsecond) but wastes a CPU core. Useful for benchmarking only.";
                                }),
                        new BooleanSetting("Low CPU Mode (laptops)",
                                () -> cfg.framePacerLowCpuMode,
                                v -> cfg.framePacerLowCpuMode = v)
                                .description("Pushes the sleep window further out for ~1-2ms less precision in exchange for cooler CPU. Great for laptops on battery."),
                        SliderSetting.ofInt("Spin Threshold (Âµs)",
                                () -> cfg.framePacerSpinThresholdMicros,
                                v -> cfg.framePacerSpinThresholdMicros = v, 100, 2000)
                                .description("Microseconds before the frame deadline to switch to pure spin. Lower = lower CPU, higher = more precise timing."),
                        SliderSetting.ofInt("Park Threshold (Âµs)",
                                () -> cfg.framePacerParkThresholdMicros,
                                v -> cfg.framePacerParkThresholdMicros = v, 1000, 8000)
                                .description("When to switch from coarse OS-level sleep to fine yield-spin. Higher = lower CPU, lower = more stable timing."),

                        new SectionHeaderSetting("Low Latency"),
                        new BooleanSetting("Enabled",
                                () -> cfg.lowLatencyRender,
                                v -> cfg.lowLatencyRender = v)
                                .description("Master switch for the latency settings in this section: the VSync-off enforcement and the Reflex-style render sleep below. With this off, neither has any effect, even with their own toggles on."),
                        new BooleanSetting("Disable VSync (causes tearing without VRR)",
                                () -> cfg.disableVSync, v -> cfg.disableVSync = v)
                                .description("Turns off vertical sync, removing the frame-buffering delay it adds for the lowest possible input lag. The trade-off is screen tearing (a horizontal seam during fast motion) unless your monitor has a variable refresh rate like G-Sync or FreeSync. Leave on if you see tearing."),
                        new BooleanSetting("Adaptive Render Sleeping (Reflex-style)",
                                () -> cfg.adaptiveRenderSleeping, v -> cfg.adaptiveRenderSleeping = v)
                                .description("Moves the frame-rate limiter's wait from the end of the frame to the very start of the next one, so the CPU sleeps just before your input is applied and the frame is built — keeping the GPU render queue short. Requires this section's Enabled toggle plus the Frame Pacer above being on with a non-Vanilla strategy."),

                        new SectionHeaderSetting("Tick Sync"),
                        new BooleanSetting("Enabled",
                                () -> cfg.tickSyncEnabled,
                                v -> cfg.tickSyncEnabled = v)
                                .description("Reduces the delay between server packets and client ticks by dynamically adjusting the client tick rate to match the server's packet delivery. Helps minimize delay when working with command blocks or fast entity updates. Only shifts ticks when a desync is detected."),
                        new BooleanSetting("Auto Margin",
                                () -> cfg.tickSyncUseAutoMargin, v -> cfg.tickSyncUseAutoMargin = v)
                                .description("Automatically adjust the sync margin based on connection stability."),
                        new BooleanSetting("Fast Sync",
                                () -> cfg.tickSyncUseFastSync, v -> cfg.tickSyncUseFastSync = v)
                                .description("Allow accelerating client ticks (pull) to catch up, rather than only delaying (push)."),
                        new BooleanSetting("Use Netty Thread (Experimental)",
                                () -> cfg.tickSyncUseNettyCriteria, v -> cfg.tickSyncUseNettyCriteria = v)
                                .description("Use the time packets arrive on the Netty network thread instead of the Render thread."),

                        new SectionHeaderSetting("Decoupled Input"),
                        new BooleanSetting("Enabled",
                                () -> cfg.inputSamplingDecoupled,
                                v -> cfg.inputSamplingDecoupled = v)
                                .description("Feeds your mouse movement into the game at the very top of each rendered frame — ahead of the game-tick logic, instead of at vanilla's own per-frame application point just before the world renders. On frames that run a game tick, your aim is already in place before tick decisions are made instead of landing just after. Applies the raw batch, so vanilla's cinematic-camera smoothing is bypassed while this is on."),

                        new SectionHeaderSetting("Drag-to-Reorder Servers"),
                        new BooleanSetting("Enabled",
                                () -> cfg.serverListDragReorder,
                                v -> cfg.serverListDragReorder = v)
                                .description("Lets you reorder servers in the Multiplayer server list by click-and-drag instead of vanilla's up/down arrow buttons. Click and hold a server, drag it to a new position, and release — the new order is saved instantly. Normal clicks still select and join servers as usual."),

                        new SectionHeaderSetting("Compliance Mode"),
                        new BooleanSetting("Enabled",
                                () -> cfg.complianceModeEnabled,
                                v -> cfg.complianceModeEnabled = v)
                                .description("Automatically disables Aurora features that strict server anti-cheats may flag when you join known-strict servers (Hypixel, CubeCraft, etc.). Protects you from false-positive bans without manually toggling features every time you switch servers. Aurora restores your features when you leave."),
                        new BooleanSetting("Show Activation Toast",
                                () -> cfg.complianceModeToast,
                                v -> cfg.complianceModeToast = v)
                                .description("Pops a brief on-screen notification when compliance mode turns on or off."),
                        new StringListSetting("Safe Servers (override)",
                                () -> cfg.complianceSafeServers,
                                v -> cfg.complianceSafeServers = v)
                                .description("Server address patterns treated as always-safe. Entries here override built-in strict detection — useful for private servers with custom anti-cheat that you trust. One address per line; substring match (e.g. 'myserver.com' matches any subdomain)."),
                        new StringListSetting("Strict Servers (custom)",
                                () -> cfg.complianceStrictServers,
                                v -> cfg.complianceStrictServers = v)
                                .description("Additional server address patterns to treat as strict (compliance on). One address per line; substring match."),

                        new SectionHeaderSetting("Accessibility"),
                        new BooleanSetting("Enabled",
                                () -> cfg.colorblindMode != AuroraConfig.ColorblindMode.OFF,
                                v -> { if (!v) cfg.colorblindMode = AuroraConfig.ColorblindMode.OFF;
                                                       else if (cfg.colorblindMode == AuroraConfig.ColorblindMode.OFF)
                                                           cfg.colorblindMode = AuroraConfig.ColorblindMode.DEUTERANOMALY; })
                                .description("Colorblind correction filters and scroll-wheel remapping for players who need them. Colorblind modes apply a daltonization color matrix to the whole screen so reds, greens, and blues are distinguishable for each type of deficiency."),
                        new EnumSetting<>("Colorblind Mode",
                                AuroraConfig.ColorblindMode.class,
                                () -> cfg.colorblindMode,
                                v -> cfg.colorblindMode = v)
                                .description("Select the type of color vision deficiency to correct. The filter shifts colors into ranges you can distinguish. OFF disables the filter."),
                        SliderSetting.ofInt("Correction Strength",
                                () -> cfg.colorblindStrength,
                                v -> cfg.colorblindStrength = v, 0, 100)
                                .description("How strongly the correction is applied. 100% is full correction; lower values blend toward the original colors for a subtler effect.")
                ),
                // Union of the eight tiles' config fields (see the
                // resetByPrefix comment on pack_tweaks for prefix rules).
                List.of(
                        "smoothCamera",
                        "smoothFramePacer", "framePacer", "framePacing",
                        "lowLatencyRender", "disableVSync", "adaptiveRenderSleeping",
                        "tickSync", "inputSamplingDecoupled", "serverListDragReorder",
                        "compliance", "colorblind"));

        // ---- Compliance Mode ----
        // ---- Accessibility ----
    }



    /** Boolean row bound to one Better Hitreg {@code Toggle} (raw stored value, not the master-gated read). */
    private static BooleanSetting hitregToggle(com.aurora.client.hitreg.settings.Toggle toggle, String label) {
        return new BooleanSetting(label, toggle::get, toggle::set);
    }

    /** ARGB color row bound to one Better Hitreg {@code Color}. */
    private static ColorSetting hitregColor(com.aurora.client.hitreg.settings.Color color, String label) {
        return new ColorSetting(label, color::argb, color::set);
    }

    private static void add(List<FeatureMetadata> bucket, String id, String displayName,
                            String description, BooleanSupplier getter, Consumer<Boolean> setter) {
        bucket.add(new FeatureMetadata(id, displayName, description, getter, setter));
    }

    /** Converts a particle registry path like {@code "dripping_water"}
     *  into a display label like {@code "Dripping Water"}. */
    private static String humanizeParticleId(String path) {
        StringBuilder sb = new StringBuilder(path.length());
        boolean upper = true;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '_') {
                sb.append(' ');
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static void addWithSettings(List<FeatureMetadata> bucket, String id,
                                        String displayName, String description,
                                        BooleanSupplier getter, Consumer<Boolean> setter,
                                        List<FeatureSetting> settings) {
        bucket.add(new FeatureMetadata(id, displayName, description, getter, setter,
                new ArrayList<>(settings)));
    }

    /**
     * Variant that lets the caller specify explicit reset-prefixes for
     * combined features whose underlying config fields don't share a
     * single camelCase prefix derived from the feature id.
     */
    private static void addWithSettings(List<FeatureMetadata> bucket, String id,
                                        String displayName, String description,
                                        BooleanSupplier getter, Consumer<Boolean> setter,
                                        List<FeatureSetting> settings,
                                        List<String> resetPrefixes) {
        bucket.add(new FeatureMetadata(id, displayName, description, getter, setter,
                new ArrayList<>(settings), resetPrefixes));
    }

    /**
     * Every registered feature across both tabs, modules first then
     * settings. Id lookups by callers that should not care which tab a
     * feature lives on (e.g. the dev harness) use this.
     */
    public static List<FeatureMetadata> all() {
        List<FeatureMetadata> out = new ArrayList<>(MODULES);
        out.addAll(SETTINGS);
        return out;
    }

    /**
     * Persists a theme edit to BOTH the main config and the active profile's
     * snapshot. Theme fields are profile-scoped: without the profile sync, a
     * change saved to aurora.json alone is silently reverted by the profile's
     * stale snapshot on the next startup or profile apply (the "I turned it
     * on and it was off again" failure mode once seen with a past theme toggle).
     */
    private static void persistThemeChange() {
        try {
            AuroraConfig.save();
        } catch (Throwable t) {
            AuroraClient.LOGGER.warn("[Theme] failed to save config after theme change", t);
        }
        try {
            ProfileManager.getInstance().saveCurrent();
        } catch (Throwable t) {
            AuroraClient.LOGGER.warn("[Theme] failed to sync active profile after theme change", t);
        }
    }
}

