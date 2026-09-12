package com.aurora.client.config;

import com.aurora.client.AuroraClient;
import com.aurora.client.hud.HudAnchor;
import com.aurora.client.theme.ThemeDefinition;
import com.aurora.client.theme.ThemeMigrator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simple GSON-backed config. Persists to {@code config/aurora.json}.
 *
 * <p>Module positions live in {@link #moduleLayouts}, keyed by module id,
 * so adding or removing modules doesn't invalidate existing configs.
 */
public class AuroraConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
        FabricLoader.getInstance().getConfigDir().resolve("aurora.json");

    private static AuroraConfig INSTANCE = new AuroraConfig();

    /**
     * Dedicated single-thread executor for config persistence. Disk writes are
     * slow and unpredictable (antivirus scanning, slow HDDs, network drives),
     * so {@link #save()} never touches the filesystem on the calling thread.
     *
     * <p>This is critical: {@code save()} is called from the client tick thread
     * (e.g. the 60s playtime autosave and every UI setting change). Blocking
     * that thread stalls keepalive packet processing on
     * {@code ClientPacketListener}, which is the direct cause of the
     * "seemingly random" multiplayer disconnects — the server stops receiving
     * keepalive responses within its timeout window and drops us.
     */
    private static final ExecutorService SAVE_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Aurora-ConfigSave");
                t.setDaemon(true);
                return t;
            });

    /**
     * Snapshot of factory defaults — built once at class init from a fresh
     * {@code new AuroraConfig()}, so every public field on this instance
     * carries the exact value that field's initializer produces. Used by
     * {@link #resetByPrefix(String)} as the source-of-truth for resets,
     * independent of any state the live {@code INSTANCE} may be carrying.
     */
    private static final AuroraConfig DEFAULTS = new AuroraConfig();

    /**
     * Reset every {@code public}, non-static, non-final field on the live
     * config whose name starts with {@code camelCasePrefix} back to its
     * factory-default value. Used by the per-feature "Reset" affordances
     * in the Aurora UI; matching is by camelCase prefix so a feature id
     * like {@code smooth_camera} cleanly resets {@code smoothCamera*}.
     *
     * <p>Silently ignores fields that fail to reflect (e.g. transient
     * collections that GSON skipped) — never throws to a caller.
     */
    public static void resetByPrefix(String camelCasePrefix) {
        if (camelCasePrefix == null || camelCasePrefix.isEmpty()) return;
        for (var field : AuroraConfig.class.getFields()) {
            int mod = field.getModifiers();
            if (java.lang.reflect.Modifier.isStatic(mod) || java.lang.reflect.Modifier.isFinal(mod)) continue;
            if (!field.getName().startsWith(camelCasePrefix)) continue;
            try {
                Object def = field.get(DEFAULTS);
                // Object-valued fields (the theme definition) must not alias
                // the DEFAULTS snapshot - hand the live config its own copy.
                if (def instanceof ThemeDefinition td) def = ThemeDefinition.copyOf(td);
                field.set(INSTANCE, def);
            } catch (IllegalAccessException ignored) {}
        }
    }

    /** Convenience: reset every prefix in the given list. */
    public static void resetByPrefixes(java.util.List<String> prefixes) {
        if (prefixes == null) return;
        for (String p : prefixes) resetByPrefix(p);
    }

    // ---- Profiles ----
    /**
     * Name of the profile currently applied to the live config. Managed by
     * {@link com.aurora.client.config.profile.ProfileManager}; persisted here
     * so the client resumes the same profile across restarts. This field is
     * excluded from profile snapshots (see
     * {@link com.aurora.client.config.profile.ProfileFieldSet}) to avoid the
     * circularity of a profile rewriting its own pointer.
     */
    public String activeProfile = "default";

    // ---- General ----
    public boolean customTitleScreen = true;
    public boolean reflexEnabled = false;
    public int reflexWaitTimeOffset = 0;

    public enum TextRendererMode {
        OFF("Off"),
        AURORA_ONLY("Aurora UI Only"),
        ALL_TEXT("All Text");

        private final String label;

        TextRendererMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum FontType {
        INTER("Inter Medium", "inter-frozen-medium.ttf"),
        INTER_ITALIC("Inter Italic", "inter-frozen-medium-italic.ttf"),
        JETBRAINS_MONO("JetBrains Mono", "jetbrains-mono-medium.ttf"),
        // NOTE: Avoid thin/light fonts — at 11px they render too faint to read.
        // Removed: Source Han Sans CN (too thin), Montserrat (too thin), Raleway (too thin).
        ROBOTO("Roboto", "roboto-variable.ttf"),
        POPPINS("Poppins", "poppins-medium.ttf"),
        OSWALD("Oswald", "oswald-variable.ttf"),
        NUNITO("Nunito", "nunito-variable.ttf"),
        QUICKSAND("Quicksand", "quicksand-variable.ttf"),
        FIRA_SANS("Fira Sans", "fira-sans-medium.ttf"),
        SANSSERIF("SansSerif", null);

        private final String label;
        private final String fileName;

        FontType(String label, String fileName) {
            this.label = label;
            this.fileName = fileName;
        }

        public String getLabel() {
            return label;
        }

        public String getFileName() {
            return fileName;
        }
    }

    public TextRendererMode textRendererMode = TextRendererMode.AURORA_ONLY;
    public FontType activeFont = FontType.INTER;
    public int guiFpsLimit = 60;

    /** Discrete GUI FPS-limit options (value 1000 = unlimited). */
    public enum GuiFps {
        F15(15), F25(25), F30(30), F60(60), F120(120), UNLIMITED(1000);
        public final int value;
        GuiFps(int v) { this.value = v; }
        public String label() { return this == UNLIMITED ? "Unlimited" : value + " FPS"; }
        public static GuiFps fromValue(int v) {
            for (GuiFps f : values()) if (f.value == v) return f;
            return F60;
        }
    }

    // ---- Theme ----
    /** Master switch: false = fixed factory palette instead of the user's colors. */
    public boolean themeEnabled = true;
    /**
     * Structured theme definition (Stage 1 token rework) - the single
     * source of truth for the UI's colors. Replaces the old flat
     * {@code themePrimaryColor}/{@code themeSecondaryColor} pair; legacy
     * values are migrated on load by
     * {@link com.aurora.client.theme.ThemeMigrator}.
     */
    public ThemeDefinition theme = ThemeDefinition.defaults();

    /**
     * Self-healing accessor for the theme definition. Never returns null:
     * if the field was missing/null in a corrupt config, factory defaults
     * are re-created and stored back so subsequent writes persist.
     */
    public ThemeDefinition themeOrDefault() {
        if (theme == null) theme = ThemeDefinition.defaults();
        return theme;
    }

    // ---- Features ----
    public boolean zoomEnabled = true;
    public double  zoomLevel   = 4.0D;
    public double zoomSmoothness = 0.2; // 0.01 = very smooth, 1.0 = snap
    public boolean fullBright = false;
    public int fullBrightGamma = 500; // percentage, 100..1500
    public boolean smoothFramePacer = true;
    public PacingStrategy framePacingStrategy = PacingStrategy.HYBRID;
    public int framePacerSpinThresholdMicros = 500;   // switch to pure spin within this much of target
    public int framePacerParkThresholdMicros = 2000;  // switch from park to yield within this
    public boolean framePacerLowCpuMode = false;      // longer parks, gives up some precision

    public enum PacingStrategy {
        VANILLA,    // disabled
        YIELD,      // pure Thread.onSpinWait, max precision, ~1 core
        PARK,       // LockSupport.parkNanos + spin tail (low CPU)
        HYBRID,     // park -> yield -> spin (recommended)
        SPIN        // pure busy loop (max precision, wastes a core)
    }
    public boolean smoothCamera = false;
    public double smoothCameraStrength = 0.5; // 0.05 = very smooth, 1.0 = instant
    public boolean smoothAnimationsEnabled = false;
    public AnimationCurve swingAnimationCurve = AnimationCurve.QUARTIC_OUT;
    public AnimationCurve viewBobCurve = AnimationCurve.SMOOTHSTEP;

    // ---- Expanded animation options ----
    /** First-person swing arc style. VANILLA = modern curved path; LEGACY_1_8 = steeper old-style arc. */
    public SwingStyle swingStyle = SwingStyle.VANILLA;

    public enum SwingStyle {
        VANILLA,
        LEGACY_1_8
    }

    /** Multiplier on the camera head-bob amplitude. 1.0 = vanilla, 0.0 = none, 2.0 = double. */
    public double viewBobAmplitude = 1.0;
    /** Subtle held-item breathing/sway when standing still. */
    public boolean idleSwayEnabled = false;
    /** Strength of the idle sway (0.0..10.0). Allows much higher intensities for dramatic drift. */
    public double idleSwayStrength = 1.0;
    /** Movement curve shaping the idle sway oscillation. */
    public IdleSwayCurve idleSwayCurve = IdleSwayCurve.SINE_BREATH;

    // ---- Entity Movement Smoothing ----
    /**
     * Eases other entities' rendered positions toward their tick-interpolated
     * targets every frame, smoothing out per-tick "kinks" (especially during
     * knockback arcs) and the freeze-then-leap cadence of throttled servers.
     * Auto-scales with the detected server bundling factor.
     */
    public boolean entityMovementSmoothingEnabled = true;
    /** 0.0 = barely-there, 1.0 = very smooth glide. */
    public double entityMovementSmoothingStrength = 0.3;

    /**
     * Movement curves for idle held-item sway. Each shapes the oscillation
     * differently, from a gentle breath to dramatic figure-8 drifts.
     */
    public enum IdleSwayCurve {
        /** Single pure sine — a gentle, even breathing motion. */
        SINE_BREATH,
        /** Sum of two sines (lissajous 1:2) — a smooth figure-8 / infinity path. */
        LISSAJOUS,
        /** Cubic ease shaping — flatter at the ends, more deliberate drift. */
        SMOOTHSTEP_DRIFT,
        /** Double-frequency sine — quicker, shallower wobble (nervous/twitchy). */
        DOUBLE_SINE,
        /** Triple-harmonic pendulum — rich, organic, pendulum-like sway. */
        PENDULUM,
        /** Circular orbit — the item traces a clean circle (high intensity = dramatic). */
        CIRCULAR
    }

    /** @deprecated kept only so old configs don't error on load. Use swingAnimationCurve / viewBobCurve. */
    @Deprecated
    public AnimationCurve animationCurve = AnimationCurve.SMOOTHSTEP;

    public enum AnimationCurve {
        LINEAR,
        SINE,
        SMOOTHSTEP,
        QUARTIC,
        QUINTIC,
        CUBIC_OUT,
        QUARTIC_OUT,
        /** @deprecated use QUINTIC. Kept for save-compat. */
        @Deprecated
        CUBIC_HERMITE
    }
    public boolean inputSamplingDecoupled = false;
    public boolean lowLatencyRender = true;
    public boolean disableVSync = false;
    
    // ---- Tick Sync ----
    public boolean tickSyncEnabled = false;
    public boolean tickSyncUseAutoMargin = true;
    public boolean tickSyncUseFastSync = false;
    public boolean tickSyncUseNettyCriteria = false;

    // ---- Late-Stage Responsive Features ----
    public boolean highFrequencyInput = false;
    public boolean adaptiveRenderSleeping = false;
    public boolean fixHeldItemSeams = true;
    public double heldItemInflation = 1.001; // 1.0 = no fix, 1.005 = strong fix
    public boolean hitColorEnabled = false;
    public int hitColor = 0x66FF0000; // ARGB: ~40% red, similar to vanilla flash
    public boolean hitColorTintArmor = true; // also tint armor when an entity is hurt
    // ---- Info module (FPS / coords / time toggles) ----
    public boolean showFps    = true;
    public boolean showCoords = true;
    public boolean showTime   = true;
    /**
     * Shared informational-text color for the plain-readout HUD modules
     * (Info, CPS, Stats, Totem Pops, Reach, Armor text, Ping fallback,
     * Keystrokes labels). 0 = follow the theme accent (factory default,
     * resolved via {@link com.aurora.client.theme.HudText}); any non-zero
     * ARGB is an explicit override and wins verbatim — including legacy
     * configs that persisted the old white default 0xFFFFFFFF.
     */
    public int     hudColor   = 0;

    // ---- Info module — extended survival/QoL rows ----
    /** "Facing: N (-12°)" — cardinal direction + yaw degrees. */
    public boolean infoShowFacing       = false;
    /** "In-chunk: 7, 12" — block coords within the current chunk (0..15). */
    public boolean infoShowChunkLocal   = false;
    /** "Chunk: 3, -2" — chunk indices. */
    public boolean infoShowChunkCoords  = false;
    /** "Y: 71 (+8)" — height with delta from sea level for the current dim. */
    public boolean infoShowYRelSea      = false;
    /** "Biome: minecraft:plains". */
    public boolean infoShowBiome        = false;
    /** "Dim: overworld". */
    public boolean infoShowDimension    = false;
    /** "Light: 14 (sky 15 / block 0)" at the player's feet. */
    public boolean infoShowLightAtFeet  = false;

    // ---- Info module — performance & input telemetry ----
    /** "Frame: 6.9 ms" — average frame time derived from the FPS counter. */
    public boolean infoShowFrameTime    = false;
    /** "Mem: 38% (780/2048 MB)" — JVM heap usage. */
    public boolean infoShowMemory       = false;
    /** "Ping: 45 ms" — latency to the current server (multiplayer only). */
    public boolean infoShowPing         = false;
    /** "CPS: L4 R0" — left/right clicks per second over the last second. */
    public boolean infoShowCps          = false;

    // ---- Info module — playtime tracker ----
    /** Show a "Played:" row in the Info HUD. */
    public boolean infoShowPlaytime     = false;
    /**
     * When {@code true} the Info HUD shows time spent in the current
     * world / server only. When {@code false} it shows the global total
     * across all saves and servers.
     */
    public boolean infoPlaytimePerWorld = true;
    /** Lifetime total milliseconds spent in any world. */
    public long playtimeTotalMs = 0L;
    /**
     * Per-scope playtime in milliseconds, keyed by {@code WorldScope.current()}
     * (e.g. {@code "sp:my_world"}, {@code "mp:play.example.com"}). Updated
     * each tick by {@code PlaytimeFeature} and persisted as part of the
     * config JSON.
     */
    public java.util.Map<String, Long> playtimePerWorld = new java.util.HashMap<>();

    // ---- Module enables ----
    public boolean infoEnabled        = true;
    public boolean pingHudEnabled = true;
    public boolean nametagPingEnabled = true;
    public boolean cpsEnabled         = true;
    public boolean armorHudEnabled    = true;
    public boolean reachEnabled       = true;
    public ArmorDurabilityFormat armorDurabilityFormat = ArmorDurabilityFormat.REMAINING;

    public enum ArmorDurabilityFormat {
        REMAINING, REMAINING_OF_MAX, PERCENTAGE, NONE
    }
    public boolean armorAlertEnabled = true;
    public int armorAlertThresholdPct = 10;  // 1..100
    public boolean armorAlertSound = true;   // master enable for the alert sound
    public ArmorAlertSoundChoice armorAlertSoundChoice = ArmorAlertSoundChoice.PLING;
    public boolean armorShowDurabilityBars = false;
    /** Lay the four armor pieces horizontally (helmet → boots, left → right). */
    public boolean armorHorizontal = false;

    public enum ArmorAlertSoundChoice {
        PLING, EXPERIENCE_ORB, BELL, ARMOR_EQUIP, ARROW_HIT, ANVIL_LAND, NOTE_BASS
    }

    // ---- Status Alerts (hunger + effect expiry) ----
    /** Alert when the player's hunger drops below the threshold. */
    public boolean hungerAlertEnabled = true;
    /** Hunger level (0..20 drumsticks) below which the alert fires. */
    public int hungerAlertThreshold = 6;
    /** Alert when a potion effect is about to expire (seconds remaining). */
    public boolean effectExpiryAlertEnabled = true;
    /** Seconds remaining at which the effect-expiry alert fires. */
    public int effectExpiryThresholdSeconds = 10;
    /** When true, also alert on held-tool low durability (not just armor). */
    public boolean toolDurabilityAlertEnabled = true;

    // ---- Batch 2 features ----
    public boolean freeLookEnabled       = true;
    public double freeLookSensitivity    = 1.0;  // 0.1..3.0 multiplier on MouseHandler delta
    public boolean saturationBarEnabled  = true;
    public boolean tabPingEnabled        = true;
    public boolean toggleSprintSneakEnabled = true;
    public boolean toggleSprintSneakHudEnabled = true;

    /**
     * How the Toggle Sprint/Sneak HUD module is rendered.
     *
     * <ul>
     *   <li>{@code BOTH} — the classic single module showing both
     *       {@code "Sprint: ON/OFF"} and {@code "Sneak: ON/OFF"} rows.</li>
     *   <li>{@code INDIVIDUAL} — splits into two independent, draggable
     *       HUD modules (one for sprint, one for sneak) that the user can
     *       position, resize and show/hide separately in the HUD editor.</li>
     *   <li>{@code BRACKETED_TEXT} — a compact single-line module that
     *       only lists currently active toggles inside brackets, e.g.
     *       {@code "[Sprint]"} / {@code "[Sneak]"} / {@code "[Sprint] [Sneak]"}.
     *       Renders nothing while neither toggle is active.</li>
     * </ul>
     */
    public ToggleSprintDisplayMode toggleSprintSneakDisplayMode = ToggleSprintDisplayMode.BOTH;

    public enum ToggleSprintDisplayMode {
        BOTH,
        INDIVIDUAL,
        BRACKETED_TEXT
    }

    // Block Overlay â€” outline + face fill on the targeted block.
    public boolean blockOverlayEnabled = true;             // master toggle
    public boolean blockOverlayOutlineEnabled = true;      // sub-toggle: wireframe outline
    public int     blockOverlayOutlineColor = 0xFF000000;  // outline ARGB
    public boolean blockOverlayFillEnabled = false;        // sub-toggle: face fill (opt-in)
    public int     blockOverlayFillColor = 0x40FFFFFF;     // fill ARGB (translucent white default)
    public BlockOverlayMode blockOverlayMode = BlockOverlayMode.SOLID;
    public float   blockOverlayRainbowSpeed = 1.0f;        // cycles per second
    public double blockOverlayLineWidth = 2.0;    // line width in pixels (GPU-expanded)
    public boolean blockOverlaySeeThrough = true; // see-through outline (Lunar/NoRisk style)

    public boolean potionHudEnabled = true;

    public enum HudBackground { NONE, SOLID, AURORA, VANILLA }

    // ---- Totem tweaks ----
    public boolean totemTweaksEnabled = false;
    public int totemParticleScale = 100; // 25..100, percentage

    // ---- Shield tweaks (first person) ----
    public boolean shieldTweaksEnabled = false;
    public ShieldStyle shieldStyle = ShieldStyle.VANILLA;

    public enum ShieldStyle {
        /** Vanilla rendering â€” no transform applied. */
        VANILLA,
        /** Translate downward to clear the field of view, slight scale-down. */
        LOWERED,
        /** Rotate around Y-axis so the shield is held edge-on, plus translate to outer edge. */
        SIDE,
        /** Uniform scale-down with vanilla position. */
        COMPACT
    }

    // ---- Shield statuses (color overlay on rendered shields) ----
    /** Master toggle for the shield-status color overlay. When off the
     *  ShieldSpecialRenderer mixin returns vanilla {@code -1} and no
     *  tint is applied. */
    public boolean shieldStatusEnabled = false;
    /** ARGB tint applied to a shield model when it is considered "available":
     *  the local player's shield while NOT on cooldown, or another holder's
     *  shield while they are NOT actively blocking. See
     *  {@link com.aurora.client.feature.impl.ShieldStatusFeature} for the
     *  full state-resolution logic. Note: the alpha channel is reinterpreted
     *  as tint strength because shield rendering uses cutout blending. */
    public int shieldStatusAvailableColor = 0x6630D158; // iOS green @ 40% alpha
    /** ARGB tint applied to a shield model when it is considered "disabled":
     *  the local player's shield during the cooldown that follows an axe
     *  disabling it ({@code Player.getCooldowns().isOnCooldown(SHIELD)} —
     *  vanilla applies a 100-tick cooldown via {@code ShieldItem#disableFor}).
     *  Other players' cooldowns aren't synced to clients, so we can't detect
     *  their disabled state and their shields always use the available color. */
    public int shieldStatusDisabledColor  = 0x66FF453A; // iOS red @ 40% alpha

    // ---- Item Scale Module ----
    public boolean itemScaleEnabled = false;
    public float mainHandDefaultScale = 1.0f;
    public float offHandDefaultScale = 1.0f;
    public Map<String, ItemScaleData> itemScales = new HashMap<>();

    public static class ItemScaleData {
        public float scale = 1.0f;
        public float translationX = 0.0f;
        public float translationY = 0.0f;
        public float translationZ = 0.0f;
        public float pitch = 0.0f;
        public float yaw = 0.0f;
        public float roll = 0.0f;

        public ItemScaleData() {}
    }

    // ---- Player health indicators ----
    public boolean playerHealthIndicators = false;
    public boolean playerHealthOnlyAttacked = false;

    /**
     * How entity health is displayed above their heads when
     * {@link #playerHealthIndicators} is on.
     *
     * <ul>
     *   <li>{@code NUMBER} — a numeric value with a heart prefix
     *       (e.g. "❤ 16"), the classic behaviour.</li>
     *   <li>{@code HEARTS} — a row of individual heart icons that
     *       visually mimic the vanilla health bar (each heart = 2 HP,
     *       red for full, dark red for half; empty hearts are not shown,
     *       gold for absorption).</li>
     * </ul>
     */
    public HealthDisplayMode playerHealthDisplayMode = HealthDisplayMode.NUMBER;

    public enum HealthDisplayMode {
        NUMBER("Number"),
        HEARTS("Hearts");

        private final String label;

        HealthDisplayMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public boolean noFogEnabled = false;
    public double fogDistanceMultiplier = 4.0; // 1x = vanilla, 100x = effectively no fog
    public boolean underwaterClarityEnabled = false;
    public double underwaterFogMultiplier = 8.0;

    // Lava clarity â€” same idea, separate fog modifier + screen overlay tint.
    public boolean lavaClarityEnabled = false;
    public double  lavaFogMultiplier = 8.0;
    public boolean lavaHideOverlay = false;   // true = cancel orange overlay entirely

    // Per-module background settings
    /**
     * Info HUD background mode. Defaults to {@code AURORA} (R6 Part 2): the
     * corner readout's panel is the theme-derived HUD_BACKDROP gradient —
     * near-black hued toward the accent at the top, accent at the bottom —
     * so the panel follows the theme like the rest of the HUD accents.
     */
    public HudBackground infoBgMode = HudBackground.AURORA;
    public int infoBgColor = 0x80000000;

    public HudBackground keystrokesBgMode = HudBackground.SOLID;
    public int keystrokesBgColor = 0xFF14141C;

    public HudBackground cpsBgMode = HudBackground.NONE;
    public int cpsBgColor = 0x80000000;

    public HudBackground armorHudBgMode = HudBackground.NONE;
    public int armorHudBgColor = 0x80000000;

    public HudBackground reachBgMode = HudBackground.NONE;
    public int reachBgColor = 0x80000000;

    public HudBackground sprintHudBgMode = HudBackground.NONE;
    public int sprintHudBgColor = 0x80000000;

    public HudBackground potionBgMode = HudBackground.NONE;
    public int potionBgColor = 0x80000000;

    // ---- Totem pop counter ----
    public boolean totemPopEnabled  = false;
    /** Show additional lines for other players' pops, not just your own. */
    public boolean totemShowOthers  = false;
    /** How many other players to display when {@link #totemShowOthers} is on. */
    public int     totemTopOthers   = 3;
    /** Reset the self counter on every death. */
    public boolean totemResetOnDeath = true;
    public HudBackground totemPopBgMode = HudBackground.NONE;
    public int totemPopBgColor = 0x80000000;
    /** Append "[N]" to other players' nametags showing their totem pop count. */
    public boolean nametagTotemPopsEnabled = true;

    // ---- Stats overlay (per-session combat stats) ----
    public boolean statsEnabled     = false;
    /** Kills this session (melee, heuristically attributed). */
    public boolean statsShowKills   = true;
    /** Your deaths this session. */
    public boolean statsShowDeaths  = true;
    /** Kill/death ratio (deaths floored at 1). */
    public boolean statsShowKd      = true;
    /** Wall-clock time since the session began / last reset. */
    public boolean statsShowSession = true;
    // Fight rows — data comes from Better Hitreg's fight tracker (see
    // StatsTrackerFeature#recordFight): a fight is 10 s–10 min of exchange
    // with at least one landed hit, ended by leaving the 30-block radius.
    /** "Fights: N (M total)" — fights this session and the lifetime total. */
    public boolean statsShowFights  = true;
    /** "Fight Time: Xm Ys (Zh total)" — time spent in tracked fights, session and lifetime. */
    public boolean statsShowFightTime = true;
    /** "Last Fight: 1m 12s · You 62% · Them 45%" — duration and both accuracies of the last tracked fight. */
    public boolean statsShowLastFight = true;
    public HudBackground statsBgMode = HudBackground.NONE;
    public int statsBgColor = 0x80000000;

    // ---- Aurora keybinds ----
    // Stored as raw GLFW key codes. -1 means unbound. Polled directly via
    // AuroraKey rather than registered with vanilla's KeyMapping system,
    // so they don't appear in the vanilla controls menu and rebind from
    // each feature's own settings page.
    public int zoomKey         = org.lwjgl.glfw.GLFW.GLFW_KEY_C;
    public int freeLookKey     = org.lwjgl.glfw.GLFW.GLFW_KEY_V;
    public int toggleSprintKey = org.lwjgl.glfw.GLFW.GLFW_KEY_J;
    public int toggleSneakKey  = org.lwjgl.glfw.GLFW.GLFW_KEY_K;
    public int hudEditorKey    = org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
    /** Press to toggle the hitbox sub-toggles ({@link #hitboxEnabled} +
     *  {@link #hitboxTargetEnabled}) on/off together. -1 = unbound. */
    public int hitboxToggleKey = -1;
    /** Press to call {@code TotemPopFeature.resetAll()}. -1 = unbound. */
    public int totemResetKey   = -1;
    /** Press to reset the Stats overlay counters. -1 = unbound. */
    public int statsResetKey   = -1;
    /** Press to drop a waypoint at the player's current block position. -1 = unbound. */
    public int waypointDropKey = -1;
    /** Press to open the Waypoint Manager screen. -1 = unbound. */
    public int waypointManagerKey = -1;

    // ---- Waypoints ----
    /**
     * Lightweight POJO persisted in the config JSON. One per logical waypoint.
     * Scoped per-world via {@link #waypointsByWorld}.
     */
    public static class Waypoint {
        /** Display name. Editable in the manager screen. */
        public String name = "Waypoint";
        public int x = 0, y = 64, z = 0;
        /** Vanilla dimension id, e.g. {@code "minecraft:overworld"}. */
        public String dimension = "minecraft:overworld";
        /** ARGB. Beam + label color. */
        public int color = 0xFFFFAA00;
        public long createdAtMs = 0L;
        /** Marks waypoints auto-dropped by the death-waypoint sub-feature. */
        public boolean death = false;

        public Waypoint() {}
        public Waypoint(String name, int x, int y, int z, String dimension, int color) {
            this.name = name;
            this.x = x; this.y = y; this.z = z;
            this.dimension = dimension;
            this.color = color;
            this.createdAtMs = System.currentTimeMillis();
        }
    }

    /**
     * How a waypoint's world marker is drawn.
     *
     * <ul>
     *   <li>{@code BEAM} — vertical beacon-style column.</li>
     *   <li>{@code BLOCK} — translucent highlight on the exact block(s)
     *       where the waypoint was placed.</li>
     *   <li>{@code BOTH} — beam + block highlight together.</li>
     * </ul>
     */
    public enum WaypointDisplay { BEAM, BLOCK, BOTH }

    public boolean waypointsEnabled       = false;
    public WaypointDisplay waypointDisplay = WaypointDisplay.BEAM;
    /** Beam width in blocks (square cross-section). */
    public double  waypointBeamWidth      = 0.30;
    /** Multiplier on the screen-space label font size. 1.0 = vanilla 10px. */
    public double  waypointLabelScale     = 1.00;
    /**
     * Horizontal (X/Z) radius in blocks around the stored block position
     * when {@link WaypointDisplay#BLOCK} (or {@link WaypointDisplay#BOTH})
     * is active. The highlight is always exactly one block tall — the
     * radius only widens the floor slab, it never grows vertically.
     * {@code 0} = single block; {@code 1} = 3×3 slab; {@code r} =
     * {@code (2r+1)×(2r+1)} slab.
     */
    public int     waypointBlockRadius    = 0;
    /**
     * Outline color override for the block highlight. {@code 0} reuses
     * the waypoint's own color (typical) — exposed only as a config
     * field for future expansion.
     */
    public int     waypointBlockOutlineColorOverride = 0;
    /** Show the screen-space label (name + distance) above the beam. */
    public boolean waypointShowLabel      = true;
    /** Auto-drop a "Death @ HH:mm" waypoint when the local player dies. */
    public boolean deathWaypointEnabled   = true;
    /** Replace the previous death waypoint instead of accumulating them. */
    public boolean deathWaypointReplacesPrevious = true;
    /**
     * Maximum death waypoints kept when {@link #deathWaypointReplacesPrevious}
     * is off. The oldest (by creation time) is pruned when a new death
     * waypoint would exceed the cap. Values below 1 are treated as 1.
     */
    public int deathWaypointMaxCount = 3;
    /** Color for auto-dropped death waypoints. */
    public int     deathWaypointColor     = 0xFFFF3344;
    /**
     * Per-world lists, keyed by {@link com.aurora.client.util.WorldScope#current()}.
     * GSON serializes nested lists fine; an empty map is created on first
     * use to keep the JSON clean for fresh installs.
     */
    public java.util.Map<String, java.util.List<Waypoint>> waypointsByWorld = new java.util.HashMap<>();

    public enum BlockOverlayMode {
        SOLID, RAINBOW
    }

    // ---- Crosshair ----
    public enum CrosshairStyle { CROSS, DOT, CIRCLE, SQUARE, CUSTOM }

    public boolean        crosshairEnabled = false;
    public CrosshairStyle crosshairStyle   = CrosshairStyle.CROSS;
    public int     crosshairColor     = 0xFFFFFFFF;
    public int     crosshairSize      = 5;
    public int     crosshairThickness = 1;
    public int     crosshairGap       = 2;
    public boolean crosshairIndicatorEnabled = false;
    public CrosshairStyle crosshairIndicatorStyle = CrosshairStyle.CROSS;
    public int crosshairIndicatorSize = 5;
    public int crosshairIndicatorThickness = 1;
    public int crosshairIndicatorGap = 2;
    public int crosshairIndicatorColor = 0xFFFF5555; // default reddish so it visibly differs
    public boolean[] crosshairCustomPixels = defaultCustomPixels();
    public int crosshairCustomWidth = 11;
    public int crosshairCustomHeight = 11;
    public boolean[] crosshairIndicatorCustomPixels = defaultCustomPixels();
    public int crosshairIndicatorCustomWidth = 11;
    public int crosshairIndicatorCustomHeight = 11;

    /** Default custom-canvas pattern: single lit pixel dead-center of the 11×11 grid. */
    private static boolean[] defaultCustomPixels() {
        boolean[] out = new boolean[121];
        out[5 * 11 + 5] = true;  // center pixel
        return out;
    }

   // ----Hitboxes----
   public boolean hitboxEnabled = true;
    public boolean hitboxEyeLine = true;
    public boolean hitboxLookDirection = true;
    public double hitboxLookLength = 2.0;
    public int hitboxColor = 0xFFFFFFFF;
    public double hitboxLineWidth = 2.0;          // line width in pixels (GPU-expanded)
    public boolean hitboxSeeThrough = true;       // see-through wireframe (Lunar/NoRisk style)
    public boolean hitboxProjectileColorEnabled = false;
    public int hitboxProjectileColor = 0xFFFFFF00;
    public double hitboxProjectileLineWidth = 2.0;
    public boolean hitboxTargetEnabled = true;
    public boolean hitboxTargetEyeLine = true;
    public boolean hitboxTargetLookDirection = true;
    public int hitboxTargetColor = 0xFFFF5555;

    // ---- Particle controls ----
    /** Master toggle for the per-particle visibility/scale system. When
     *  off, the ParticleEngine mixin no-ops regardless of the maps below. */
    public boolean particleControlsEnabled = true;
    /** Per-particle visibility, keyed by registry id (e.g. "minecraft:flame").
     *  Absent key ⇒ visible. */
    public Map<String, Boolean> particleVisibility = new HashMap<>();
    /** Per-particle scale multiplier, keyed by registry id. Absent key ⇒ 1.0. */
    public Map<String, Float> particleScale = new HashMap<>();
    /**
     * Per-particle ARGB color overlay, keyed by registry id. Absent key ⇒
     * no tint (vanilla colors). The RGB channels are multiplied into the
     * particle's {@code rCol/gCol/bCol}; the alpha channel controls tint
     * strength (0 = no effect, 255 = full recolor).
     */
    public Map<String, Integer> particleColor = new HashMap<>();

    // ---- Module layouts (anchor + offset for each module id) ----
    public Map<String, ModuleLayout> moduleLayouts = new HashMap<>();

    public static class ModuleLayout {
        public HudAnchor anchor;
        public int       offsetX;
        public int       offsetY;
        public boolean   enabled = true;
        public float     scale   = 1.0f;
        public boolean   locked  = false;

        public ModuleLayout() {}
        public ModuleLayout(HudAnchor a, int ox, int oy, boolean en, float sc) {
            this.anchor = a; this.offsetX = ox; this.offsetY = oy;
            this.enabled = en; this.scale = sc;
        }
        public ModuleLayout(HudAnchor a, int ox, int oy, boolean en, float sc, boolean lk) {
            this.anchor = a; this.offsetX = ox; this.offsetY = oy;
            this.enabled = en; this.scale = sc; this.locked = lk;
        }
    }

    // Smooth feel features
    public boolean damageTiltEnabled = false;
    public double damageTiltStrength = 1.0; // multiplier on tilt magnitude

    // Save-compat stubs — the Smooth FOV feature was removed; these
    // fields exist only so older config files deserialize cleanly.
    // Do not wire them up.
    public boolean smoothFovEnabled = false;
    public double smoothFovSpeed = 0.15;

    public boolean hotbarBounceEnabled = false;

    // ---- Container Preview ----
    /** Master switch for the rich container tooltip preview. */
    public boolean containerPreviewEnabled    = true;
    /** Show preview for shulker box items (any color). */
    public boolean containerPreviewShulker    = true;
    /** Show preview for the local player's ender chest items when hovering an Ender Chest item. */
    public boolean containerPreviewEnderChest = true;
    /** Save-compat stub — the "Other Containers" toggle was removed; this
     *  field exists only so older config files deserialize cleanly. Do not
     *  wire it up. */
    public boolean containerPreviewOtherBlocks = true;
    /** Render stack counts under each preview slot. */
    public boolean containerPreviewShowCounts = true;

    // ---- Item Physics ----
    /** Master switch for item-physics rendering tweaks. */
    public boolean itemPhysicsEnabled        = false;
    /** Lay dropped items flat on the ground (suppresses bob + spin while grounded). */
    public boolean itemPhysicsFlatOnGround   = true;
    /** Strength of the in-air X-axis tumble, 0..2. 1.0 ≈ a full flip every ~1.6s at full speed. */
    public double  itemPhysicsTumbleStrength = 1.0;

    // ---- Multiplayer server list ----
    /**
     * Allow reordering servers in the multiplayer server list by click-and-drag
     * instead of vanilla's up/down arrow buttons.
     */
    public boolean serverListDragReorder = true;

    // ---- Keystrokes HUD Module ----
    /** Master toggle for the keystrokes overlay. */
    public boolean keystrokesEnabled = false;
    /** Show the W/A/S/D movement block (follows your movement keybinds). */
    public boolean keystrokesShowMovement = true;
    /** Show the left/right mouse buttons. */
    public boolean keystrokesShowMouse = true;
    /** Show the middle (pick-block) mouse button. Only used when Show Mouse is on. */
    public boolean keystrokesShowMiddleMouse = true;
    /** Show the rolling clicks-per-second counter. */
    public boolean keystrokesShowCps = true;
    /** Show the spacebar (jump) bar. */
    public boolean keystrokesShowSpacebar = true;
    /** Show the sneak/sprint key row. */
    public boolean keystrokesShowExtra = true;
    /**
     * ARGB accent color used for the pressed state of a key. {@code 0} (the
     * default, R6 Part 2) means "follow the theme accent" — the pressed-key
     * highlight tracks {@code ThemeManager.color(ACCENT)}; any explicit ARGB
     * value overrides it.
     */
    public int keystrokesAccentColor = 0;
    /** Backing panel opacity, 0..1. Key labels are unaffected. */
    public double keystrokesBgOpacity = 0.55;
    /**
     * Additional tracked keys as raw GLFW codes (e.g. GLFW_KEY_F = 70),
     * auto-arranged in rows of three below the standard rows. Capped at
     * {@link com.aurora.client.hud.module.KeystrokesModule#MAX_EXTRA_KEYS}.
     */
    public java.util.List<Integer> keystrokesExtraKeys = new java.util.ArrayList<>();

    // ---- Minimap ----
    /** Master toggle for the minimap HUD module. */
    public boolean minimapEnabled = false;
    /** Press to show/hide the minimap. -1 = unbound. */
    public int minimapToggleKey = -1;

    /** Minimap pixel size (edge length — the map is square). */
    public int minimapSize = 128;

    // ---- Better Hitreg (BetterHitreg by Jass, integrated) ----
    /**
     * Master enable for the whole Better Hitreg feature card. Distinct from
     * the original mod's "custom hitreg" switch (which only gates the
     * client-side hit feedback): when this is off every hitreg mixin,
     * overlay, sound filter and keybind is dormant.
     */
    public boolean hitregEnabled = true;
    /** Opens the Better Hitreg detail screen. -1 = unbound (the original bound H). */
    public int hitregSettingsKey   = -1;
    /** Swaps the main hand (5-tick cooldown). -1 = unbound. */
    public int hitregSwitchHandKey = -1;
    /** Practice scoreboard: +1 left. -1 = unbound (the original bound LEFT). */
    public int hitregScoreLeftKey  = -1;
    /** Practice scoreboard: +1 right. -1 = unbound (the original bound RIGHT). */
    public int hitregScoreRightKey = -1;
    /** Practice scoreboard: send "L-R" to chat. -1 = unbound (the original bound UP). */
    public int hitregScoreSendKey  = -1;
    /** Practice scoreboard: reset both scores. -1 = unbound (the original bound DOWN). */
    public int hitregScoreResetKey = -1;

    // Better Hitreg settings — one field per key of the original
    // hitreg.properties (migrated once by HitregMigrator; the property key
    // is noted on each field). All share the "hitreg" prefix so the card's
    // Reset covers them as one group. Semantics preserved from upstream:
    // hitregDelayMs 0 is NOT "off" (hitregCustomHitreg is the switch),
    // hitregMetronome below 10 means off, muffle/sharpen are 0..1.
    /** {@code toggle} — the original "custom hitreg" master: replaces the server's hit feedback with the client's own. */
    public boolean hitregCustomHitreg = true;
    /** {@code hitreg} — ms the client waits before playing its own hit feedback (0 = next frame). */
    public int hitregDelayMs = 0;
    /** {@code muffle_amount} — OpenAL low-pass strength on your hit sounds, 0..1. */
    public double hitregMuffleAmount = 0.0;
    /** {@code sharpen_amount} — OpenAL high-pass strength on your hit sounds, 0..1. */
    public double hitregSharpenAmount = 0.0;
    /** {@code metronome} — click every N ticks; values below 10 disable it. */
    public int hitregMetronome = 0;
    /** {@code floor_grid_size} — ground grid spacing in blocks (0 = off). */
    public int hitregFloorGridSize = 0;

    /** {@code safeRegsOnly} */          public boolean hitregSafeRegsOnly = true;
    /** {@code ignoreShieldHolders} */   public boolean hitregIgnoreShieldHolders = false;
    /** {@code alertDelays} — retained as a plain toggle; its tooltip shows the rolling average delay. */
    public boolean hitregAlertDelays = false;
    /** {@code alertGhosts} — retained as a plain toggle; its tooltip shows the rolling ghost ratio. */
    public boolean hitregAlertGhosts = false;
    /** {@code alertInconsistencies} — retained as a plain toggle; its tooltip shows the rolling misplace ratio. */
    public boolean hitregAlertInconsistencies = false;
    /**
     * Repurposed from {@code alertFights} (which only gated the retired
     * post-fight chat summary): whether a completed fight is recorded into
     * the fight statistics at all. Defaults ON and is deliberately NOT
     * migrated from the old key — upstream recorded every fight regardless
     * of the alert toggle, and this keeps that behavior.
     */
    public boolean hitregTrackFights = true;
    /** {@code legacySounds} */          public boolean hitregLegacySounds = false;
    /** {@code hideAnimations} */        public boolean hitregHideAnimations = false;
    /** {@code hideArmor} */             public boolean hitregHideArmor = false;
    /** {@code hideAllParticles} */      public boolean hitregHideAllParticles = false;
    /** {@code hideOtherParticles} */    public boolean hitregHideOtherParticles = false;
    /** {@code particlesEveryHit} */     public boolean hitregParticlesEveryHit = false;
    /** {@code silenceOtherFights} */    public boolean hitregSilenceOtherFights = false;
    /** {@code silenceSelf} */           public boolean hitregSilenceSelf = false;
    /** {@code silenceThem} */           public boolean hitregSilenceThem = false;
    /** {@code silenceNonHits} */        public boolean hitregSilenceNonHits = false;
    /** {@code hideOtherFights} */       public boolean hitregHideOtherFights = false;
    /** {@code renderHitbox} */          public boolean hitregRenderHitbox = false;
    /** {@code renderCross} */           public boolean hitregRenderCross = false;
    /** {@code RenderServerHitbox} */    public boolean hitregRenderServerHitbox = false;
    /** {@code RenderYourReach} */       public boolean hitregRenderYourReach = false;
    /** {@code RenderTheirReach} */      public boolean hitregRenderTheirReach = false;
    /** {@code RenderYourJump} */        public boolean hitregRenderYourJump = false;
    /** {@code RenderTheirJump} */       public boolean hitregRenderTheirJump = false;
    /** {@code PerfectHitColor} */       public boolean hitregPerfectHitColor = false;
    /** {@code JumpResetColor} */        public boolean hitregJumpResetColor = false;
    /** {@code VoidWorld} — clears every visible chunk section (practice-arena "void"); extremely invasive. */
    public boolean hitregVoidWorld = false;
    /** {@code SolidFloor} */            public boolean hitregSolidFloor = false;

    // Overlay colors, ARGB (the original stored "<name>_color" as bare hex
    // plus "<name>_opacity" 0..255; both fold into one int here).
    public int hitregColorCrossFar            = 0xFFFFFFFF;
    public int hitregColorCrossNear           = 0xFFFF0000;
    public int hitregColorCrossFarWithHitbox  = 0xFF0000FF;
    public int hitregColorCrossNearWithHitbox = 0xFF0000FF;
    public int hitregColorHitboxFar           = 0xFFFFFFFF;
    public int hitregColorHitboxNear          = 0xFFFF0000;
    public int hitregColorServerHitbox        = 0x7D7F00FF; // opacity 125
    public int hitregColorYourReachFar        = 0xFFFFFFFF;
    public int hitregColorYourReachNear       = 0xFFFF0000;
    public int hitregColorTheirReachFar       = 0xFFFFFFFF;
    public int hitregColorTheirReachNear      = 0xFFFF0000;
    public int hitregColorYourJumpFar         = 0xFF007FFF;
    public int hitregColorYourJumpNear        = 0xFF007FFF;
    public int hitregColorTheirJumpFar        = 0xFF007FFF;
    public int hitregColorTheirJumpNear       = 0xFF007FFF;
    public int hitregColorJumpReset           = 0xFFFFFF00;
    public int hitregColorPerfectHit          = 0xFF00FF00;
    public int hitregColorGrid                = 0xFFFFFFFF;
    public int hitregColorFloor               = 0xFF000000;

    // ---- Fight statistics (lifetime telemetry, from BetterHitreg) ----
    // Deliberately outside the "hitreg" and "stats" reset prefixes and
    // excluded from profile snapshots (ProfileFieldSet), like playtime:
    // a per-machine lifetime record that neither a card Reset nor a
    // profile switch may wipe.
    /** {@code total_fights} — lifetime tracked fights (10 s–10 min with at least one landed hit). */
    public int fightStatsTotalFights = 0;
    /** {@code fight_playtime_(seconds)} — lifetime seconds spent in tracked fights. */
    public long fightStatsPlaytimeSeconds = 0L;
    /**
     * Set once {@code HitregMigrator} has run (whether or not a
     * hitreg.properties file existed), so the one-way migration can never
     * fire twice and overwrite settings edited since. Excluded from profile
     * snapshots and from every reset prefix on purpose.
     */
    public boolean migratedHitregProperties = false;

    // ---- Blur panel test harness ----
    /**
     * Press to open the isolated blur-panel test screen. -1 = unbound by
     * default; bind via vanilla Controls → "Blur Panel Test". Verification
     * harness for the blur+tint panel foundation — intentionally not
     * reachable from any other screen.
     */
    public int blurTestKey = -1;

    /**
     * How many blocks fit across the minimap's diameter. Lower = more zoomed-in.
     * A value of 64 means the minimap shows a 64×64 block area centered on the player.
     */
    public int minimapZoom = 64;
    /** Circular (true) vs square (false) minimap shape. */
    public boolean minimapCircular = true;
    /**
     * Rotate the map with the player's facing direction (true) or keep north
     * up (false). Default north-locked — cheaper (no rotation re-sampling)
     * and matches the fullscreen map's fixed orientation.
     */
    public boolean minimapRotateWithPlayer = false;
    /** Show terrain (block colors sampled from the chunk). */
    public boolean minimapShowTerrain = true;
    /** Alpha of the terrain layer (0..1). */
    public double minimapTerrainAlpha = 1.0;
    /**
     * When on, the minimap reads terrain from the World Map's captured
     * region tiles (the same cache the fullscreen map renders from) and only
     * falls back to live chunk sampling for columns not yet captured —
     * cheaper per sample and consistent with the fullscreen map. Ignored
     * whenever the World Map feature is disabled; live sampling covers
     * everything in that case.
     */
    public boolean minimapUseWorldMapCache = true;
    /** Show waypoints from {@link com.aurora.client.feature.impl.WaypointFeature} as colored dots. */
    public boolean minimapShowWaypoints = true;
    /**
     * Show the block distance next to waypoints pinned to the minimap rim
     * (out-of-view direction/distance indicator). At most four labels.
     */
    public boolean minimapShowWaypointDistance = true;
    /** Show other entities (players, mobs) as dots. */
    public boolean minimapShowEntities = true;
    /** Show hostile mobs (zombies, skeletons, etc.) as red dots. */
    public boolean minimapShowHostiles = false;
    /** Show passive/neutral mobs as green dots. */
    public boolean minimapShowPassives = true;
    /** Show other players as white dots. */
    public boolean minimapShowPlayers = true;
    /** Show a directional arrow for the player at the center. */
    public boolean minimapShowSelfArrow = true;
    /** Show a coordinate readout below the minimap. */
    public boolean minimapShowCoords = true;
    /** Show a cardinal-direction compass (N/E/S/W) ring around the minimap. */
    public boolean minimapShowCompass = true;
    /** Minimap background mode. */
    public HudBackground minimapBgMode = HudBackground.AURORA;
    /** Minimap border/frame color (ARGB). */
    public int minimapBorderColor = 0xFF000000;
    /** Minimap coordinate text color (ARGB). */
    public int minimapCoordColor = 0xFFFFFFFF;

    // ---- World Map ----
    /**
     * Master toggle for the World Map module card.
     */
    public boolean worldMapEnabled = true;

    /**
     * GLFW key that opens the fullscreen world map. Default M.
     */
    public int worldMapKey = org.lwjgl.glfw.GLFW.GLFW_KEY_M;

    /**
     * Maximum chunks re-sampled into region tiles per client tick — the
     * frame-time budget knob for the capture pipeline. 2 chunks/tick keeps
     * the cost under ~1-2ms while filling a full render distance in a few
     * seconds.
     */
    public int worldMapCaptureBudget = 2;

    /**
     * Region tiles (32×32 chunks, one 512×512 texture each) kept in the
     * RAM/GPU LRU cache. Dirty tiles are saved to disk on eviction.
     */
    public int worldMapCacheRegions = 64;

    // ---- Resourcepack Browser ----
    /**
     * Master toggle for the in-game Modrinth resource-pack browser. The
     * browser itself is opened from its settings panel — this toggle only
     * controls whether the module card appears in the grid, mirroring how
     * other pure-UI features (e.g. container preview) expose an enable bit.
     */
    public boolean resourcepackBrowserEnabled = true;

    // ---- Compliance Mode ----
    /**
     * When enabled, automatically disables features that strict server
     * anti-cheats may flag (e.g. reach display, toggle sprint) based on
     * the connected server's known rules. See
     * {@link com.aurora.client.feature.impl.ComplianceModeFeature}.
     */
    public boolean complianceModeEnabled = false;
    /** Show a brief toast notification when compliance mode activates/deactivates on a server. */
    public boolean complianceModeToast = true;
    /**
     * User-maintained list of server address patterns (substring match)
     * that are known-safe for all Aurora features. Entries here override
     * the built-in detection — useful for private servers with custom AC.
     */
    public java.util.List<String> complianceSafeServers = new java.util.ArrayList<>();
    /**
     * User-maintained list of server address patterns (substring match)
     * that should be treated as strict (compliance on) regardless of
     * built-in detection.
     */
    public java.util.List<String> complianceStrictServers = new java.util.ArrayList<>();

    // ---- Accessibility ----
    /** Colorblind correction filter type. OFF = no filter. */
    public ColorblindMode colorblindMode = ColorblindMode.OFF;
    /** Strength of the colorblind correction filter (0..100%). */
    public int colorblindStrength = 100;
    /** Remap scroll-wheel up to a custom action (0 = vanilla, -1 = unbound). */
    public int scrollUpRemap = 0;
    /** Remap scroll-wheel down to a custom action (0 = vanilla, -1 = unbound). */
    public int scrollDownRemap = 0;

    public enum ColorblindMode {
        OFF("Off"),
        PROTANOPIA("Protanopia (red-blind)"),
        DEUTERANOPIA("Deuteranopia (green-blind)"),
        TRITANOPIA("Tritanopia (blue-blind)"),
        PROTANOMALY("Protanomaly (red-weak)"),
        DEUTERANOMALY("Deuteranomaly (green-weak)");

        private final String label;
        ColorblindMode(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public static AuroraConfig get() { return INSTANCE; }

    public static void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                // Theme pre-pass: fold legacy themePrimary/themeSecondary keys
                // into the structured "theme" object and drop malformed theme
                // members so one bad field can't take the whole config down.
                json = ThemeMigrator.migrateConfigJson(json, AuroraClient.LOGGER);
                AuroraConfig loaded = GSON.fromJson(json, AuroraConfig.class);
                if (loaded != null) {
                    if (loaded.moduleLayouts == null) loaded.moduleLayouts = new HashMap<>();
                    if (loaded.particleVisibility == null) loaded.particleVisibility = new HashMap<>();
                    if (loaded.particleScale == null) loaded.particleScale = new HashMap<>();
                    if (loaded.particleColor == null) loaded.particleColor = new HashMap<>();
                    if (loaded.itemScales == null) loaded.itemScales = new HashMap<>();
                    if (loaded.complianceSafeServers == null) loaded.complianceSafeServers = new java.util.ArrayList<>();
                    if (loaded.complianceStrictServers == null) loaded.complianceStrictServers = new java.util.ArrayList<>();
                    if (loaded.keystrokesExtraKeys == null) loaded.keystrokesExtraKeys = new java.util.ArrayList<>();
                    if (loaded.theme == null) loaded.theme = ThemeDefinition.defaults();
                    INSTANCE = loaded;
                }
            } else {
                save();
            }
        } catch (Exception e) {
            // IOException (disk problems) or JsonSyntaxException (corrupt
            // file): either way fall back to the in-memory defaults -
            // loading must never crash client init.
            AuroraClient.LOGGER.error("Failed to load Aurora config", e);
        }
    }

    /**
     * Persist the config asynchronously.
     *
     * <p>Serialization happens on the calling thread (normally the client tick
     * thread) so we read a consistent snapshot — GSON is not safe against
     * concurrent mutation of the live maps by other tick logic. The resulting
     * JSON string is then handed to {@link #SAVE_EXECUTOR} for the actual disk
     * write, which is the slow / unpredictable part (filesystem + antivirus).
     * Keeping that off the main thread prevents it from stalling packet
     * handling and dropping the player from servers.
     *
     * <p>Use {@link #saveBlocking()} on JVM shutdown, where the daemon write
     * thread would otherwise be killed before it could finish.
     */
    public static void save() {
        final String json;
        try {
            json = GSON.toJson(INSTANCE);
        } catch (Throwable t) {
            AuroraClient.LOGGER.error("Failed to serialize Aurora config", t);
            return;
        }
        SAVE_EXECUTOR.submit(() -> writeJsonAtomic(json));
    }

    /**
     * Persist the config synchronously (blocking). Should only be used during
     * shutdown — the async {@link #save()} enqueues work onto a daemon thread
     * that the JVM will not wait for on exit, so a final save must be done
     * inline to guarantee it lands on disk.
     */
    public static void saveBlocking() {
        try {
            writeJsonAtomic(GSON.toJson(INSTANCE));
        } catch (Throwable t) {
            AuroraClient.LOGGER.error("Failed to save Aurora config", t);
        }
    }

    /**
     * Write the JSON to the config path atomically: write to a sibling
     * {@code .tmp} file then move it into place. A crash or shutdown mid-write
     * therefore can never corrupt or truncate the existing config.
     */
    private static void writeJsonAtomic(String json) {
        Path tmp = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(tmp, json);
            // Prefer an atomic same-volume rename (default on Windows / most
            // POSIX FS). Fall back to a plain replace if the FS doesn't support
            // atomic moves so the save still lands rather than being dropped.
            try {
                Files.move(tmp, CONFIG_PATH,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException amnse) {
                Files.move(tmp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            AuroraClient.LOGGER.error("Failed to save Aurora config", e);
            // Best-effort cleanup of the orphaned temp file.
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        } catch (Throwable t) {
            AuroraClient.LOGGER.error("Unexpected error saving Aurora config", t);
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }
}

