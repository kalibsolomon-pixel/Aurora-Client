package com.aurora.client.feature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one list of Aurora's features and of the mixins each one depends on
 * (ADR 0004).
 *
 * <p>Everything else derives from here: the Modules grid
 * ({@code module/ModuleManager}) builds its cards from the
 * {@link Tab#MODULES} entries in this order, {@code screen/FeatureRegistry}
 * takes each entry's name and tab from here and adds only the settings UI,
 * and {@code health/FeatureHealth} uses the mixin bindings to turn a feature
 * off when one of its mixins could not be applied. {@code FeatureCatalogTest}
 * keeps this file, {@code aurora.mixins.json}, {@code FeatureRegistry} and
 * {@code AuroraConfig} in agreement.
 *
 * <p>No Minecraft imports: this class belongs to the Minecraft-free core
 * (ADR 0003).
 */
public final class FeatureCatalog {
    private FeatureCatalog() {}

    /** Which tab of the main Aurora screen shows the feature. */
    public enum Tab { MODULES, SETTINGS }

    /**
     * What a feature means for fair play (ADR 0007). The wording here is what
     * {@code docs/FAIR_PLAY.md} tells server owners, so it must stay true.
     */
    public enum FairPlay {
        COSMETIC("Cosmetic", "Changes only how things look or sound on your own screen."),
        VANILLA_INFORMATION("Vanilla equivalent",
                "Shows your own state or information the vanilla game already presents, or extends a setting or tool vanilla already has."),
        INFORMATION_ADVANTAGE("Information advantage",
                "Shows something about other players or the world that vanilla does not present, or allows a view vanilla does not. Ships off."),
        LOCAL_TIMING("Local timing",
                "Changes when something happens on your own screen. What the game sends to the server, and when, is unchanged.");

        public final String label;
        public final String meaning;

        FairPlay(String label, String meaning) {
            this.label = label;
            this.meaning = meaning;
        }
    }

    /**
     * A user-facing feature.
     *
     * @param summary      the short text on the feature's grid card; empty for
     *                     Settings-tab entries, which have no card
     * @param fairPlay     the feature's fair-play class
     * @param fairPlayNote what the feature does, in terms a server owner can check
     * @param offByDefault boolean {@code AuroraConfig} fields that must be
     *                     {@code false} in a fresh install: the feature's own
     *                     switch when the whole feature is an information
     *                     advantage, otherwise the switches inside it that are
     */
    public record Feature(String id, String name, String summary, Tab tab,
                          FairPlay fairPlay, String fairPlayNote, List<String> offByDefault) {}

    /**
     * What one mixin is for.
     *
     * @param mixin    name as listed in {@code aurora.mixins.json}
     * @param provides what stops working without it, as a phrase that
     *                 completes "Not working in this session: ..."
     * @param features ids of the features that need it; empty for mixins
     *                 that serve Aurora as a whole
     * @param gates    boolean {@code AuroraConfig} fields that are forced off
     *                 for the session when the mixin is not working, so the
     *                 rest of the feature does not run half-attached
     * @param yieldsTo mod ids that own the same area: when one is loaded the
     *                 mixin is not applied and stands down (ADR 0009)
     * @param requires mod id the mixin patches into; without it the mixin is
     *                 not applied and stands down; empty for vanilla targets
     */
    public record MixinBinding(String mixin, String provides, List<String> features, List<String> gates,
                               List<String> yieldsTo, String requires) {
        /**
         * Why this mixin should not be applied, or null when it should.
         * {@code loaded} answers whether a mod id is installed.
         */
        public String standDownReason(java.util.function.Predicate<String> loaded) {
            if (!requires.isEmpty() && !loaded.test(requires)) return "only patches " + requires + ", which is not installed";
            for (String mod : yieldsTo) if (loaded.test(mod)) return "handled by " + mod;
            return null;
        }
    }

    private static final Map<String, Feature> FEATURES = new LinkedHashMap<>();
    private static final Map<String, MixinBinding> MIXINS = new LinkedHashMap<>();

    /** Every feature: the Modules grid in card order, then the Settings tab in display order. */
    public static List<Feature> features() {
        return Collections.unmodifiableList(new ArrayList<>(FEATURES.values()));
    }

    /** The features on one tab, in display order. */
    public static List<Feature> featuresOn(Tab tab) {
        List<Feature> out = new ArrayList<>();
        for (Feature f : FEATURES.values()) if (f.tab() == tab) out.add(f);
        return Collections.unmodifiableList(out);
    }

    /** The feature with this id, or null. */
    public static Feature feature(String id) { return FEATURES.get(id); }

    /** The feature with this id; an unknown id is a programming error. */
    public static Feature require(String id) {
        Feature f = FEATURES.get(id);
        if (f == null) throw new IllegalStateException("Feature '" + id + "' is not in FeatureCatalog");
        return f;
    }

    /** Every mixin binding, in {@code aurora.mixins.json} order. */
    public static List<MixinBinding> mixins() {
        return ActiveMixins.BINDINGS;
    }

    /** The adapter manifest selects native hooks; their feature/gate definitions stay here. */
    private static final class ActiveMixins {
        static final List<MixinBinding> BINDINGS = load();
        private static List<MixinBinding> load() {
            try (var stream = FeatureCatalog.class.getResourceAsStream("/aurora.mixins.json")) {
                if (stream == null) throw new IllegalStateException("Missing adapter mixin manifest");
                var manifest = com.google.gson.JsonParser.parseReader(
                        new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                List<MixinBinding> result = new ArrayList<>();
                var names = new java.util.HashSet<String>();
                for (var entry : manifest.getAsJsonArray("client")) {
                    String name = entry.getAsString();
                    MixinBinding binding = MIXINS.get(name);
                    if (binding == null || !names.add(name)) throw new IllegalStateException("Unknown or duplicate adapter mixin: " + name);
                    result.add(binding);
                }
                return List.copyOf(result);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
    }

    /** The binding for this mixin name, or null. */
    public static MixinBinding mixin(String name) { return MIXINS.get(name); }

    private static void module(String id, String name, String summary) {
        FEATURES.put(id, new Feature(id, name, summary, Tab.MODULES, null, "", List.of()));
    }

    private static void setting(String id, String name) {
        FEATURES.put(id, new Feature(id, name, "", Tab.SETTINGS, null, "", List.of()));
    }

    /** Records a feature's fair-play class; every feature gets exactly one call. */
    private static void classify(String id, FairPlay fairPlay, String note, String... offByDefault) {
        Feature f = require(id);
        if (f.fairPlay() != null) throw new IllegalStateException("Feature '" + id + "' is classified twice");
        FEATURES.put(id, new Feature(f.id(), f.name(), f.summary(), f.tab(), fairPlay, note, List.of(offByDefault)));
    }

    /**
     * Starts a binding; chain {@link Binding#gates} when the mixin has config
     * gates, and {@link Binding#yieldsTo} or {@link Binding#requires} when
     * whether it applies depends on another mod (ADR 0009).
     */
    private static Binding mixin(String name, String provides, String... features) {
        Binding b = new Binding(name);
        MIXINS.put(name, new MixinBinding(name, provides, List.of(features), List.of(), List.of(), ""));
        return b;
    }

    private record Binding(String name) {
        Binding gates(String... gates) {
            MixinBinding m = MIXINS.get(name);
            MIXINS.put(name, new MixinBinding(name, m.provides(), m.features(), List.of(gates), m.yieldsTo(), m.requires()));
            return this;
        }

        Binding yieldsTo(String... mods) {
            MixinBinding m = MIXINS.get(name);
            MIXINS.put(name, new MixinBinding(name, m.provides(), m.features(), m.gates(), List.of(mods), m.requires()));
            return this;
        }

        Binding requires(String mod) {
            MixinBinding m = MIXINS.get(name);
            MIXINS.put(name, new MixinBinding(name, m.provides(), m.features(), m.gates(), m.yieldsTo(), mod));
            return this;
        }
    }

    static {
        // ---- Modules grid, in card order ----
        module("zoom", "Zoom", "Optical-style zoom bound to a key. Hold it to narrow your field of view and magnify whatever you're looking at.");
        module("full_bright", "Full Bright", "Overrides Minecraft's gamma so dark areas become fully visible. Caves, the underwater, and night-time terrain light up.");
        module("no_fog", "No Fog", "Pushes back the distance fog that normally hides far terrain. You see further across oceans and open landscapes.");
        module("pack_tweaks", "Pack Tweaks", "A bundle of small quality-of-life visual tweaks — totem particle size, shield rendering, and water/lava clarity.");
        module("player_health", "Entity Health", "Floats a numeric health value above mobs and players so you can read exactly how much damage is left to deal.");
        module("free_look", "FreeLook", "Hold a key to swivel the camera around your character without changing the direction you're actually moving or facing.");
        module("saturation_bar", "Saturation Bar", "Overlays your hidden saturation level on top of the food bar.");
        module("block_overlay", "Block Overlay", "Replaces the thin vanilla block-selection box with a customizable outline and/or colored fill.");
        module("toggle_sprint_sneak", "Toggle Sprint/Sneak", "Turns sprint and sneak into press-once toggles instead of hold-to-keep keys.");
        module("alerts", "Alerts", "Icon and text warnings for low armor durability and potion effects about to expire, shown as two draggable HUD modules — Armor Alert and Potion Alert — instead of a shared popup. All alerts share the same sound system.");
        module("crosshair", "Crosshair", "Replaces the vanilla crosshair with a fully customizable one.");
        module("hitbox", "Hitbox", "Draws entity hitboxes with your own color, line width, and optional eye-level and look-direction lines.");
        module("better_hitreg", "Better Hitreg", "Client-side hit registration feedback for PvP — plays your hit sound and hurt animation the instant you swing instead of waiting for the server, tracks ghosted and misplaced hits, and adds reach rings, target hitboxes and a practice arena. Original project by Jass.");
        module("hit_color", "Hit Color", "Recolors the red flash that overlays an entity when it takes damage.");
        module("glint_color", "Glint Color", "Recolors the animated shimmer that enchanted items and armor give off.");
        module("info_module", "Info HUD", "A configurable corner readout of useful at-a-glance info — FPS, coordinates, time of day, facing, biome, dimension, light level, playtime and more.");
        module("cps", "CPS", "Shows your current clicks-per-second as a HUD counter.");
        module("armor_hud", "Armor HUD", "Displays your equipped armor pieces — and optionally your main and off hands — with their remaining durability on the HUD.");
        module("reach_display", "Reach Display", "Shows the exact distance to the last entity you attacked.");
        module("potion_hud", "Potion HUD", "A cleaner replacement for the default status-effect icons, listing your active potion effects with a readable countdown timer.");
        module("ping", "Ping", "Surfaces connection latency as real numbers — as a HUD readout, as a value in the tab list, and optionally under player nametags.");
        module("totem_pop", "Totem Pop Counter", "Counts how many Totems of Undying you've popped, and optionally tracks nearby players' pops too.");
        module("waypoints", "Waypoints", "Place persistent world markers rendered as beacon beams and/or block highlights.");
        module("container_preview", "Container Preview", "Hover over a shulker box (or your ender chest) in any inventory to see its full contents in a grid tooltip.");
        module("item_physics", "Item Physics", "Gives dropped items more natural physics — they lie flat where they land instead of hovering and spinning.");
        module("particles", "Particles", "Per-particle control over every vanilla particle type — hide ones you find distracting and resize the rest.");
        module("item_scale", "Item Scale", "Customize the scale, rotation, and screen translation of individual item models.");
        module("held_item_seams", "Held Item Seam Fix", "Hides the hairline seams that can appear between the faces of your first-person held items at certain camera angles.");
        module("animations", "Animations", "Swing & view-bob curves, classic 1.8-style damage camera tilt, retro backwards-walking & sneak poses, idle held-item sway, and smooth entity rotation.");
        module("keystrokes", "Keystrokes", "Displays your WASD, mouse, jump, and sneak keys as a HUD overlay that lights up in real time as you press them.");
        module("minimap", "Minimap", "A top-down map of the world around you, shown as a HUD overlay. Renders terrain, waypoints, and nearby entities as colored dots.");
        module("scoreboard", "Scoreboard HUD", "Replaces the vanilla scoreboard sidebar with a HUD module you can move, resize and restyle. Off in a fresh install, so existing installs keep vanilla's sidebar.");
        module("world_map", "World Map", "A full-screen, pannable world map of your explored terrain.");
        module("resourcepack_browser", "Resourcepack Browser", "Browse and install community resource packs directly from Modrinth. Search, preview, and one-click install straight into your resourcepacks folder.");

        // ---- Settings tab, in display order ----
        setting("custom_title", "Custom Title");
        setting("text_fonts", "Text & Fonts");
        setting("theme", "Theme");
        setting("interface", "Interface");
        // Display Settings has no entry of its own: its sections are spread
        // over the Settings tab's Display, Controls and General pages
        // (FeatureRegistry.settingsPages, 2026-11-12). It stays one feature
        // for fair play, mixin health and its reset prefixes.
        setting("miscellaneous", "Display Settings");
        // Client optimizations Aurora owns (ADR 0009). Most have no row of
        // their own; the Settings tab's Performance page shows the few with
        // a visible trade-off.
        setting("performance", "Performance");

        // ---- Fair play (ADR 0007). docs/FAIR_PLAY.md repeats these lines. ----
        classify("zoom", FairPlay.VANILLA_INFORMATION, "Narrows the field of view while a key is held, as the vanilla spyglass does.");
        // Full Bright and No Fog are visibility aids that ship ON. ADR 0007 keeps
        // their defaults by owner decision, so they are not in the advantage
        // class; the notes say plainly what they do and that they are on.
        classify("full_bright", FairPlay.VANILLA_INFORMATION, "Extends the vanilla Brightness setting past its maximum, so unlit areas are visible. Off in a fresh install.", "fullBright");
        classify("no_fog", FairPlay.VANILLA_INFORMATION, "Pushes distance fog back, so terrain inside your render distance is visible sooner than in vanilla. Off in a fresh install.", "noFogEnabled");
        classify("pack_tweaks", FairPlay.COSMETIC, "Totem particle size, first-person shield position and a tint for your own shield's cooldown. Its water and lava clarity switches improve visibility inside fluids and ship off.", "underwaterClarityEnabled", "lavaClarityEnabled");
        classify("player_health", FairPlay.INFORMATION_ADVANTAGE, "Shows the health of nearby mobs and players above them. Vanilla does not show other players' health. Players have their own switch, also off.", "playerHealthIndicators", "playerHealthShowPlayers");
        classify("free_look", FairPlay.INFORMATION_ADVANTAGE, "Lets the camera look around without turning the character, a view vanilla does not allow.", "freeLookEnabled");
        classify("saturation_bar", FairPlay.VANILLA_INFORMATION, "Shows your own saturation on the food bar.");
        classify("block_overlay", FairPlay.COSMETIC, "Restyles the outline of the block you are looking at.");
        classify("toggle_sprint_sneak", FairPlay.VANILLA_INFORMATION, "Press-once sprint and sneak, as vanilla's accessibility options offer.");
        classify("alerts", FairPlay.VANILLA_INFORMATION, "Warns about your own armor durability and your own expiring effects, as two HUD modules.");
        classify("crosshair", FairPlay.COSMETIC, "Replaces the crosshair's shape and color.");
        classify("hitbox", FairPlay.VANILLA_INFORMATION, "Draws entity hitboxes, as vanilla's F3+B does, with custom colors.");
        classify("better_hitreg", FairPlay.LOCAL_TIMING, "Plays your hit's sound, hurt animation and particles on your screen at once instead of waiting for the server's copy. It does not change what the game sends for movement or combat, or when. Two optional keys, unbound by default, send something when pressed: Switch Hand changes the vanilla Main Hand option, and the practice scoreboard's Send key posts the score as a chat message. The feature is off in a fresh install, and so are its reach rings, extra hitboxes and hiding of other fights.", "hitregEnabled", "hitregRenderHitbox", "hitregRenderServerHitbox", "hitregRenderYourReach", "hitregRenderTheirReach", "hitregRenderYourJump", "hitregRenderTheirJump", "hitregHideOtherFights");
        classify("hit_color", FairPlay.COSMETIC, "Recolors the flash on an entity that takes damage. A flash that follows the hurt entity's health is an information advantage and ships off.", "hitColorHealthGradient");
        classify("glint_color", FairPlay.COSMETIC, "Recolors the enchantment glint.");
        classify("info_module", FairPlay.VANILLA_INFORMATION, "Shows FPS, coordinates, time, biome and similar readouts that the F3 screen also has.");
        classify("cps", FairPlay.VANILLA_INFORMATION, "Counts your own clicks.");
        classify("armor_hud", FairPlay.VANILLA_INFORMATION, "Shows your own armor, hands and their durability.");
        classify("reach_display", FairPlay.VANILLA_INFORMATION, "Shows the distance of your own last hit.");
        classify("potion_hud", FairPlay.VANILLA_INFORMATION, "Lists your own active effects, as the vanilla effect icons do.");
        classify("ping", FairPlay.VANILLA_INFORMATION, "Shows latency as numbers: your own, and other players' as the tab list already reports it.");
        classify("totem_pop", FairPlay.VANILLA_INFORMATION, "Counts your own totem pops. Counting other players' pops, on the HUD or on their nametags, is an information advantage and ships off.", "totemShowOthers", "nametagTotemPopsEnabled");
        classify("waypoints", FairPlay.VANILLA_INFORMATION, "Marks positions you chose, and where you died, from your own coordinates.");
        classify("container_preview", FairPlay.VANILLA_INFORMATION, "Shows a shulker box's contents as a grid instead of vanilla's text list, and your ender chest as you last saw it.");
        classify("item_physics", FairPlay.COSMETIC, "Changes how dropped items lie and tumble on your screen.");
        classify("particles", FairPlay.COSMETIC, "Hides, resizes or recolors particle types on your screen.");
        classify("item_scale", FairPlay.COSMETIC, "Changes the size and position of the item in your hand.");
        classify("held_item_seams", FairPlay.COSMETIC, "Hides texture seams on the item in your hand.");
        classify("animations", FairPlay.COSMETIC, "Changes swing, view-bob and damage-tilt animations and smooths how other entities move on your screen.");
        classify("keystrokes", FairPlay.VANILLA_INFORMATION, "Shows your own key and mouse presses.");
        classify("minimap", FairPlay.VANILLA_INFORMATION, "A map of terrain your client has loaded, with your waypoints and dots for nearby mobs. Dots for other players are an information advantage and ship off.", "minimapShowPlayers");
        classify("scoreboard", FairPlay.VANILLA_INFORMATION, "Replaces the vanilla scoreboard sidebar with a movable, resizable module that shows the same rows and scores as vanilla. Off in a fresh install.", "scoreboardEnabled");
        classify("world_map", FairPlay.VANILLA_INFORMATION, "A full-screen map of terrain you have already visited.");
        classify("resourcepack_browser", FairPlay.COSMETIC, "Downloads resource packs from Modrinth.");
        classify("miscellaneous", FairPlay.LOCAL_TIMING, "Frame pacing, a frame cap, vsync control and the point within a frame where mouse movement is applied. The rate and content of what is sent to the server are unchanged.");
        classify("custom_title", FairPlay.COSMETIC, "Aurora's title screen and menu styling.");
        classify("text_fonts", FairPlay.COSMETIC, "Changes the font.");
        classify("theme", FairPlay.COSMETIC, "Changes the colors of Aurora's own screens.");
        classify("interface", FairPlay.COSMETIC, "Limits the frame rate of Aurora's own screens.");
        classify("performance", FairPlay.LOCAL_TIMING, "Caches, skips or reschedules work your own client does to load, draw and play sound. A new singleplayer world's loading screen closes half a second sooner; what is sent to a multiplayer server, and when, is unchanged.");

        // ---- Mixins, in aurora.mixins.json order ----
        mixin("WindowMixin", "nothing (kept empty for compatibility)");
        mixin("TitleScreenMixin", "the Aurora title screen", "custom_title").gates("customTitleScreen");
        mixin("InGameHudMixin", "hiding the vanilla crosshair under Aurora's", "crosshair").gates("crosshairEnabled");
        mixin("ScoreboardSidebarMixin", "replacing the vanilla scoreboard sidebar", "scoreboard").gates("scoreboardEnabled");
        mixin("GameRendererMixin", "zooming the view", "zoom").gates("zoomEnabled");
        mixin("RenderTargetMixin", "nothing (kept empty for compatibility)");
        mixin("PlayerListHudMixin", "numeric ping in the tab list", "ping").gates("tabPingEnabled");
        mixin("InGameHudFoodMixin", "the saturation overlay on the food bar", "saturation_bar").gates("saturationBarEnabled");
        mixin("MouseMixin", "adjusting zoom with the scroll wheel", "zoom");
        mixin("EntityMixin", "the FreeLook camera", "free_look").gates("freeLookEnabled");
        mixin("CameraUpdateMixin", "the FreeLook camera and camera smoothing", "free_look", "miscellaneous")
                .gates("freeLookEnabled", "smoothCamera");
        mixin("VertexRenderingMixin", "replacing the vanilla block outline", "block_overlay").gates("blockOverlayEnabled");
        mixin("SimpleOptionMixin", "raising brightness past the vanilla limit", "full_bright").gates("fullBright");
        mixin("EntityRenderDispatcherMixin", "replacing the vanilla hitboxes", "hitbox")
                .gates("hitboxEnabled", "hitboxTargetEnabled");
        mixin("RenderSystemMixin", "Aurora's frame limiter after each frame", "miscellaneous")
                .gates("smoothFramePacer", "adaptiveRenderSleeping");
        mixin("GameRendererBobMixin", "the view-bob curve", "animations");
        mixin("HeldItemRendererMixin", "the swing animation curve", "animations");
        mixin("MouseAccessor", "decoupled mouse input", "miscellaneous").gates("inputSamplingDecoupled");
        mixin("MinecraftClientRenderMixin", "frame pacing, decoupled mouse input and blurred glass", "miscellaneous")
                .gates("smoothFramePacer", "adaptiveRenderSleeping", "inputSamplingDecoupled", "lowLatencyRender", "disableVSync");
        mixin("MixinOverlayTexture", "recoloring the hurt flash", "hit_color").gates("hitColorEnabled");
        mixin("EquipmentLayerRendererMixin", "tinting armor and trims with the hit color", "hit_color")
                .gates("hitColorTintArmor", "hitColorTintArmorTrims");
        mixin("HitColorOverlayRefreshMixin", "per-frame pulse and rainbow refresh and gradient row selection", "hit_color").gates("hitColorEnabled");
        mixin("ReloadableTextureMixin", "recoloring the enchantment glint", "glint_color").gates("glintColorEnabled");
        mixin("GlintRenderTypesMixin", "Alternative Glint", "glint_color").gates("glintColorAlternative");
        mixin("GlintBufferSourceMixin", "Alternative Glint", "glint_color").gates("glintColorAlternative");
        mixin("RenderTypesGlintAccessor", "Alternative Glint", "glint_color").gates("glintColorAlternative");
        mixin("InGameHudPotionOverlayMixin", "hiding the vanilla effect icons behind the Potion HUD", "potion_hud");
        mixin("NoFogMixin", "pushing back the distance fog", "no_fog").gates("noFogEnabled");
        mixin("UnderwaterClarityMixin", "underwater clarity", "pack_tweaks").gates("underwaterClarityEnabled");
        mixin("MouseClickTrackerMixin", "counting clicks for the CPS readouts", "cps", "keystrokes", "info_module");
        mixin("DamageTiltMixin", "the 1.8-style damage tilt", "animations").gates("damageTiltEnabled");
        mixin("HotbarItemBounceMixin", "the hotbar item bounce", "animations").gates("hotbarBounceEnabled");
        mixin("PlayerEntityRendererMixin", "ping and totem pops under nametags", "ping", "totem_pop")
                .gates("nametagPingEnabled", "nametagTotemPopsEnabled");
        mixin("LivingEntityRendererExtractMixin", "entity movement smoothing, entity health labels and hit-color health capture", "animations", "player_health", "hit_color")
                .gates("smoothAnimationsEnabled", "entityMovementSmoothingEnabled", "playerHealthIndicators", "hitColorHealthGradient");
        mixin("LavaClarityMixin", "lava clarity", "pack_tweaks").gates("lavaClarityEnabled");
        mixin("LavaOverlayMixin", "hiding the lava overlay", "pack_tweaks").gates("lavaHideOverlay");
        mixin("ClientPlayerAttackMixin", "limiting entity health to entities you attacked", "player_health")
                .gates("playerHealthOnlyAttacked");
        mixin("ClientPlayNetworkHandlerWorldJoinMixin", "clearing per-entity caches when you change worlds");
        mixin("TotemParticleMixin", "totem particle size", "pack_tweaks").gates("totemTweaksEnabled");
        mixin("HeldItemRendererTweaksMixin", "item scale, idle sway and shield position", "item_scale", "animations", "pack_tweaks")
                .gates("itemScaleEnabled", "idleSwayEnabled", "shieldTweaksEnabled");
        mixin("HeldItemSeamFixMixin", "the held item seam fix", "held_item_seams").gates("fixHeldItemSeams");
        mixin("PlayerHealthLabelMixin", "entity health labels", "player_health").gates("playerHealthIndicators");
        mixin("ParticleAccessor", "resizing and recoloring particles", "particles", "pack_tweaks")
                .gates("particleControlsEnabled", "totemTweaksEnabled");
        mixin("ClientPacketListenerEntityEventMixin", "counting totem pops", "totem_pop").gates("totemPopEnabled");
        mixin("ItemTooltipImageMixin", "container previews in tooltips", "container_preview").gates("containerPreviewEnabled");
        mixin("ItemContainerContentsTooltipMixin", "container previews in tooltips", "container_preview")
                .gates("containerPreviewEnabled");
        mixin("ItemEntityRendererExtractMixin", "dropped item physics", "item_physics").gates("itemPhysicsEnabled");
        mixin("ItemEntityRendererSubmitMixin", "dropped item physics", "item_physics").gates("itemPhysicsEnabled");
        mixin("ParticleEngineMixin", "hiding, resizing and recoloring particles", "particles").gates("particleControlsEnabled");
        mixin("PlayerItemInHandLayerMixin", "the shield status color in third person", "pack_tweaks").gates("shieldStatusEnabled");
        mixin("ShieldSpecialRendererMixin", "the shield status color", "pack_tweaks").gates("shieldStatusEnabled");
        mixin("MixinFont", "Aurora's fonts", "text_fonts");
        mixin("TrueTypeProviderDefinitionMixin", "crisp Aurora fonts at every GUI scale", "text_fonts");
        mixin("FontManagerAccessor", "crisp Aurora fonts at every GUI scale", "text_fonts");
        mixin("FontSetAccessor", "crisp Aurora fonts at every GUI scale", "text_fonts");
        mixin("MinecraftFontManagerAccessor", "crisp Aurora fonts at every GUI scale", "text_fonts");
        mixin("ScreenshotMixin", "blurred glass (it cannot be paused safely during screenshots)");
        mixin("GuiRendererGlassMixin", "blurred glass (26.1 captures must run after the world is drawn)");
        mixin("AbstractButtonMixin", "Aurora styling on vanilla buttons", "custom_title");
        mixin("EditBoxMixin", "Aurora styling on text fields", "custom_title");
        mixin("ServerListDragReorderMixin", "drag-to-reorder in the server list", "miscellaneous").gates("serverListDragReorder");
        mixin("MultiplayerServerListWidgetMixin", "numeric ping in the server list", "ping");
        mixin("ClientLevelWorldMapMixin", "refreshing the maps when a block changes", "world_map", "minimap");
        mixin("ReflexMinecraftMixin", "automatic latency reduction");
        mixin("GpuDeviceTimestampAccessor", "automatic latency reduction");
        mixin("ItemStackRenderStateAccessor", "flat item icons in Aurora screens");
        mixin("ItemLayerQuadsAccessor", "flat item icons in Aurora screens");
        mixin("ScreenPanoramaAccessor", "blurred glass over the menu panorama");
        mixin("KeyMappingComboMixin", "Ctrl, Shift and Alt combos on key bindings");
        mixin("KeyBindsScreenComboMixin", "Ctrl, Shift and Alt combos in the Controls screen");
        // ---- Performance (ADR 0009) ----
        mixin("LoadingOverlayMixin", "hiding the resource reload overlay", "performance")
                .gates("perfHideReloadOverlay").yieldsTo("rrls");
        mixin("TitleScreenFadeMixin", "skipping the title screen fade", "performance").gates("perfHideTitleFade");
        mixin("LevelLoadTrackerMixin", "skipping the level loading delay", "performance").gates("perfSkipLevelLoadDelay");
        mixin("MinecraftProfileAccessor", "startup without waiting for the profile", "performance").gates("perfNoProfileWait");
        mixin("InitialScreensProfileMixin", "startup without waiting for the profile", "performance").gates("perfNoProfileWait");
        mixin("SoundEngineListenerMixin", "skipping unchanged sound listener updates", "performance").gates("perfSoundListenerThrottle");
        mixin("DebugScreenEntryCacheMixin", "the F3 entry cache", "performance").gates("perfDebugEntryCache");
        mixin("FontDecorationMergeMixin", "merged underline and strikethrough quads", "performance").gates("perfMergeTextDecorations");
        mixin("FontManagerDeferredMixin", "preparing unused Aurora fonts on first use", "performance")
                .gates("perfDeferAuroraFonts").yieldsTo("modernfix");
        mixin("FontSetDeferredMixin", "preparing unused Aurora fonts on first use", "performance")
                .gates("perfDeferAuroraFonts").yieldsTo("modernfix");
        // ImmediatelyFast's batch_animated_item_updates moves animated items
        // into its own atlas subclass and redraws them in one batch; keeping
        // their slots there freezes them (26.3 set A, 2026-11-12).
        mixin("TextureManagerAnimationMixin", "redrawing animated GUI items only when they change", "performance")
                .gates("perfGuiItemBaking").yieldsTo("immediatelyfast");
        mixin("ItemStackRenderStateAnimationMixin", "redrawing animated GUI items only when they change", "performance")
                .gates("perfGuiItemBaking").yieldsTo("immediatelyfast");
        mixin("CuboidItemModelAnimationMixin", "redrawing animated GUI items only when they change", "performance")
                .gates("perfGuiItemBaking").yieldsTo("immediatelyfast");
        mixin("GuiItemAtlasBakingMixin", "redrawing animated GUI items only when they change", "performance")
                .gates("perfGuiItemBaking").yieldsTo("immediatelyfast");
        mixin("SpriteContentsMipsAccessor", "freeing uploaded sprite mipmaps", "performance").gates("perfFreeSpriteMipmaps");
        mixin("TextureAtlasFreeMipsMixin", "freeing uploaded sprite mipmaps", "performance").gates("perfFreeSpriteMipmaps");
        for (String hitreg : List.of("ArmorMixin", "AttackMixin", "ChunkMixin", "DamageMixin", "EntityAccessor",
                "GlowMixin", "NetworkMixin", "ParticleMixin", "PlayerMixin", "RenderMixin", "ServerMixin",
                "SoundMixin", "SourceMixin")) {
            // Better Hitreg's timing core only works whole: one missing piece
            // (the server-copy cancel without the local replay, say) is worse
            // than none, so any failure turns the entire feature off.
            mixin("hitreg." + hitreg, "Better Hitreg", "better_hitreg").gates("hitregEnabled");
        }
    }
}
