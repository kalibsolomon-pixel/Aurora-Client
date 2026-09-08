# Aurora UI Engine — Project Guide

A reference for humans and AI agent sessions working on this repo. Everything below was
verified by reading the code on **2026-09-08** (`master` after the Better Hitreg integration
landed in five commits on top of the cand-a rim-post-dim merge; §8 records what landed since
the previous 2026-09-07 pass).
If you change the glass rollout status, theme architecture, or feature
set, update the relevant section here in the same change.

---

## 1. What this is

**Aurora** (`com.aurora.client`, mod id `aurora`) is a **client-side-only Fabric mod** for
Minecraft: a large QoL/performance feature set (30+ features) wrapped in a fully custom,
themeable GUI framework (COSMIC/iOS-flavored) with a glass/blur "material" system.

One feature has a third-party origin: **Better Hitreg** (`feature id better_hitreg`) is
*BetterHitreg by Jass* (modrinth.com/mod/betterhitreg), **integrated into Aurora with the
author's explicit permission** (2026-09-08). It used to be vendored as a separate mod
(`you.jass.betterhitreg`, own entrypoint, own mixin config, own `hitreg.properties`); it is
now ordinary Aurora code — `com.aurora.client.hitreg` (+ `util/`, `settings/`) and
`com.aurora.client.mixin.hitreg` — configured through `AuroraConfig` (`hitreg…` fields), with
an Aurora feature card + detail screen, and credited in-file on every moved class plus a
"Original project by Jass" subtitle on its screen. Its PvP-timing core (attack capture,
packet processing, sound attribution windows, the `DontAnimate`/`OnlyAnimate` marker trick,
the fight state machine) was moved **verbatim** and must stay that way — treat those classes
like the glass renderer: read `hitreg/BetterHitreg.java`'s notes before touching them.

### Build & target facts

| Fact | Value |
|---|---|
| Build | Gradle + Fabric Loom (`fabric-loom-remap` 1.16-SNAPSHOT), `splitEnvironmentSourceSets()` — all code lives in the **client** source set |
| Java | 21 |
| Minecraft | **1.21.11** (`gradle.properties`) with official Mojang mappings |
| Fabric API | 0.141.4+1.21.11; loader 0.19.2 |
| Other deps | Cloth Config (`modApi`, used only by `util/ColorEntryHelper`); ModMenu in `suggests` only |
| Entrypoints | `com.aurora.client.AuroraClient` (Better Hitreg is wired from it via `hitreg/BetterHitreg.initialize()`) |
| Mixin configs | `aurora.mixins.json` only (~70 `client` entries, of which 13 are `hitreg.*`) |
| Run dir | `run/` at repo root is a live dev client dir (`run/config/aurora.json`, `run/config/aurora-worldmap/`, `run/config/profiles/`) |

**Known version drift (harmless but confusing):** `gradle.properties` targets 1.21.11,
`fabric.mod.json` declares `minecraft: "~1.21.8"`, and `build.gradle` run configs pin
`-Dfabric.modVersion.minecraft=1.21.7`. Also the repo `LICENSE` is CC0 while
`fabric.mod.json` says MIT. Don't "fix" these casually — ask.

`build.gradle` deliberately disables `withSourcesJar()` — a sources jar in `mods/` crashes
Mixin at preLaunch (this caused a real startup crash once; comment in build.gradle).

---

## 2. Package map

All paths below are relative to `src/client/java/com/aurora/client/`.

```
AuroraClient.java          Mod entrypoint: registers keybinds, HUD callbacks, feature
                           managers, HUD modules, tooltip component, shutdown-save hook,
                           runs HitregMigrator after the profile apply, and calls
                           hitreg/BetterHitreg.initialize().
├── hitreg/                BETTER HITREG (from BetterHitreg by Jass, integrated with
│   │                      permission): BetterHitreg (init/keybind dispatch/score HUD),
│   │                      Hitreg (fight state machine), Hit, HitType — timing core,
│   │                      VERBATIM upstream.
│   ├── util/              PacketProcessor, Sound, HitTracker, RegQueue, Scheduler,
│   │                      Render (Gizmos overlays), MultiVersion (1.21.11 shim),
│   │                      Animation + DontAnimate/OnlyAnimate DamageSource markers.
│   └── settings/          Settings (facade over AuroraConfig), Toggle/Color enums
│                          (config-backed), HitregMigrator (one-shot Properties import).
├── feature/               Feature SYSTEM (see §3). Feature.java = interface;
│   └── impl/              29 runtime Feature singletons + helper classes (TickSync,
│                           ThrottleDetector, EntityMovementSmoother, …).
├── module/                PRESENTATION-ONLY view-models for the settings grid
│                           (Module, ModuleManager). NOT runtime logic. Hardcoded
│                           list of 34 ids — can drift from FeatureRegistry (§3).
├── hud/                   HUD layer: HudRenderer (top-level callback), HudAnchor,
│                           CrosshairRenderer, HitboxRenderer, BlockOverlayRenderer,
│                           WorldLineRenderer (shared thick lines), WaypointRenderer,
│                           SaturationOverlay (AppleSkin port), AlertManager/AlertRenderer,
│                           CpsTracker, HudBackgrounds is in util/.
│   ├── module/            13 draggable HUD modules + HudModule base + HudModuleManager.
│   └── preview/           Container-preview tooltip components (shulker/ender chest).
├── screen/                Aurora's own Screens + the feature UI registry:
│   │                      AuroraScreen (main settings screen), AuroraTitleScreen,
│   │                      FeatureDetailScreen, HudEditorScreen, WaypointManagerScreen,
│   │                      ProfileManagerScreen, ColorPickerScreen,
│   │                      ResourcePackBrowserScreen (Modrinth browser), ManagerListScreen
│   │                      (shared Profile/Waypoint list-screen foundation, R3: frame
│   │                      skeleton, scroll+thumb, rename editor, toolbar — the two
│   │                      manager screens extend it, keeping only row content/actions),
│   │                      FeatureRegistry
│   │                      (UI metadata + settings widgets), FeatureTile, FeatureIcons,
│   │                      FeatureMetadata, ModuleAccentColors, ModuleIconRegistry,
│   │                      AuroraModMenuApi.
│   └── setting/           ~20 FeatureSetting widget types (BooleanSetting, EnumSetting,
│                           KeybindSetting, sliders, color pickers, PixelCanvasSetting for
│                           the custom crosshair, ParticleConfigSetting, ThemePreview…).
├── theme/                 THEME ENGINE (see §5): ThemeManager, ThemeResolver,
│                          PaletteEngine, ResolvedTheme, ThemeToken (enum), ThemeDefinition,
│                          ThemeMode, ThemeRoundness, ThemePresets, ThemeMigrator.
├── ui/
│   ├── component/         Shared themed widgets: Button (glass styles), ButtonWidget,
│   │                      ToggleSwitch, Slider, SegmentedControl, RoundedPanel,
│   │                      ColorSwatch, Widget base, ThemedScreen (marker interface).
│   ├── render/blur/       Glass material: BlurPanelRenderer + BlurTestScreen (§6).
│   └── util/              RenderUtil (AA primitives + capture), AuroraFontRenderer,
│                          ItemSpriteRenderer (flat item icons), UiLayerCache (static-layer
│                          raster cache), MaterialIconRenderer (native-res icon glyphs —
│                          FreeType-rasterizes material-symbols codepoints at the exact
│                          device-pixel size and blits 1:1; MC 1.21.11's font atlas is
│                          point-sampled NEAREST and degrades when pose-scaled),
│                          Animation (exponential approach animator).
├── mixin/                 ~57 client mixins (features + UI infra; see §4/§6).
│   └── hitreg/            13 Better Hitreg mixins (registered as "hitreg.X"), verbatim.
├── config/                AuroraConfig (GSON → config/aurora.json, public fields = schema)
│   └── profile/           ProfileManager: full-config snapshots as JSON per profile.
├── modrinth/              Keyless Modrinth v2 REST client + WebP→PNG icon cache
│                          (feeds ResourcePackBrowserScreen).
├── worldmap/              World Map pipeline: WorldMapClient (orchestrator),
│   ├── capture/           CaptureQueue + ChunkCapturer (budgeted, map-color + hillshade).
│   ├── cache/             RegionCache (512×512 tiles, LRU DynamicTextures), OverviewCache.
│   ├── storage/           WorldMapStorage (GZIP .wmr files, async IO, strict path sanitize).
│   └── screen/            WorldMapScreen (pan/zoom viewer, waypoint creation).
├── util/                  AuroraShapes (chamfered panels/gradients), AuroraSquircle +
│                          RoundedRect (rounded AA), AuroraTheme (LEGACY static facade —
│                          still the read path for ~30 files, written only by theme/),
│                          AuroraKeybinds + AuroraKey, AnimationCurves, AuroraAnim,
│                          HoverAnim, SmoothScroll (shared scroll easing + thumb
│                          geometry, R2; adopted by every scrolling screen:
│                          FeatureDetail, Aurora (per-tab, fps-keyed tau), pack
│                          browser (grid+sidebar), Profile+Waypoint — the latter
│                          two gained easing + their first visible thumb, a
│                          deliberate new feature), CachedValue,
│                          FramePacer, WorldScope, ColorEntryHelper,
│                          AttackedPlayerTracker, render-state snapshot bridges, and
│   └── reflex/            NVIDIA-Reflex-style latency reduction (CpuTimeCollector,
│                           GpuTimeCollector, ReflexScheduler).
```

Resources: `assets/aurora/font/` (9 bundled TTFs incl. `material_symbols_rounded.ttf` icon
font + JSON providers), `assets/aurora/textures/gui/module_icons/` (22 PNGs — plus two
dev scripts and an SVG that shouldn't ship), `assets/aurora/lang/en_us.json` (only
localization), `assets/minecraft/models/item/totem_of_undying.json` (fixes totem
orientation in item frames). Root-level `subset_script.py` regenerates the icon font
subset; `inspect_font3.py` is a stale one-off with a hardcoded Windows path.

---

## 3. How the feature system works — three parallel registries

There is **no single registry**. Three structures must stay conceptually in sync:

1. **`feature/FeatureManager`** — ~29 long-lived `Feature` singletons with
   `onRegister()`/`onTick(Minecraft)` (interface `feature/Feature.java`).
   `Feature.enabledByDefault()` exists but is **never read anywhere** (dead API).
2. **`screen/FeatureRegistry`** — static UI metadata: **35 MODULES-tab + 11 SETTINGS-tab
   tiles** (`FeatureMetadata`: id, display name, marketing description, enable
   getter/setter, list of `FeatureSetting` widgets, `reset()`).
3. **`module/ModuleManager`** — 35 hardcoded grid cards consumed by `AuroraScreen`.
   A typo'd id here silently returns null metadata. (The missing `reflex` card landed
   2026-09-05, audit B5 — counts now match, but the list is still maintained by hand.)

**Critical rule:** the *enabled state of every feature is a public boolean field on
`AuroraConfig`* (e.g. `zoomEnabled`), read fresh each tick — never a flag on the Feature
object. `FeatureMetadata` wraps `() -> cfg.xEnabled` / `v -> cfg.xEnabled = v`. Per-feature
"Reset to defaults" works via `AuroraConfig.resetByPrefix("zoom")` (reflection over a
`DEFAULTS` snapshot taken at class init).

Several shipped features have **no Feature object at all** (pure mixin + config field):
No Fog, Hit Color, Item Scale, Resourcepack Browser, Reflex, Hotbar Bounce, Keystrokes,
Better Hitreg (its own tick/render/HUD hooks are registered by `hitreg/BetterHitreg`).

**Persistence:** `AuroraConfig` — one pretty-printed GSON file at `<config>/aurora.json`.
All state is public non-static fields; the class *is* the schema. Saves are **async** on a
daemon executor (`Aurora-ConfigSave`) — blocking the tick thread on disk I/O caused real
multiplayer disconnects once; `saveBlocking()` exists for the shutdown hook. Atomic writes
(`.tmp` + `ATOMIC_MOVE`). Load runs `ThemeMigrator.migrateConfigJson` first and never throws.
`hitreg/settings/HitregMigrator.runOnce()` (one-shot import of the legacy
`config/hitreg.properties`, guarded by `migratedHitregProperties`) runs from `AuroraClient`
**after** `ProfileManager.load()` — `applyProfile` resets profile-scoped fields to defaults
before overlaying, so a migration inside `AuroraConfig.load()` would be wiped.

**Profiles:** `config/profile/ProfileManager` snapshots *every* profile-scoped config field
(reflection; excluded: `activeProfile`, playtime telemetry, the lifetime fight counters
`fightStatsTotalFights`/`fightStatsPlaytimeSeconds`, and `migratedHitregProperties`) into
`<config>/profiles/<name>.json`; switch = save outgoing → apply → save pointer. Listeners
(`onProfileApplied`) re-apply HUD layouts.

**Keybinds** (`util/AuroraKeybinds` + raw `util/AuroraKey`): zoom **C**, FreeLook **V**,
toggle sprint **J**, toggle sneak **K**, HUD editor **RShift**, world map **M** (all
rebindable, synced two-way with the vanilla Controls screen). Unbound-by-default: hitbox
toggle, totem reset, waypoint drop/manager, minimap toggle, blur test, and Better Hitreg's six
(open settings, switch hand, four practice-scoreboard keys — upstream bound H + the arrow
keys; unbound here on purpose). Gotcha (observed
2026-09-08): binding one of these by editing `aurora.json` alone does NOT stick — the
two-way sync sees vanilla's still-unbound KeyMapping at boot and writes -1 back into the
config. The vanilla-side binding in `options.txt` (`key_key.aurora.<id>:key.keyboard.x`)
must be set too (that is what the in-game UIs do).

**HUD modules** (`hud/module/`): 13 registered in `AuroraClient` — Info, CPS, Armor, Reach,
ToggleSprintSneak, ToggleIndividual ×2 (sprint/sneak), Potion, Ping, TotemPop, Stats,
Keystrokes, Minimap. Base class `HudModule`: 9-point anchor + offset + scale 0.5–3.0 +
enabled/locked, mirrored into `cfg.moduleLayouts`. `HudEditorScreen` (RShift in-world):
drag = move, corner-drag = resize, shift+click = settings, right-click = hide,
shift+right-click = lock, X = disable.

---

## 4. Feature catalog

### MODULES tab (35 tiles) — behavior + where the code lives

| Feature (id) | What it does | Implementation |
|---|---|---|
| World Map (`world_map`) | Fullscreen pannable map; chunks captured in background (budgeted), stitched into persistent 512×512 region tiles that survive restarts; per-dimension browsing; waypoint creation on click | `feature/impl/WorldMapFeature`, `worldmap/*` (§2), mixin `ClientLevelWorldMapMixin` |
| Theme (`theme`) | Single-accent theming engine (§5) | `theme/*`, `feature/impl/ThemeFeature` (per-tick `ThemeManager.sync()`) |
| Zoom (`zoom`) | Hold-C FOV zoom with easing; scroll adjusts level (max 8×); sensitivity scaled inversely; all scrolling consumed while zoomed | `ZoomFeature`, `GameRendererMixin` (FOV), `MouseMixin` (scroll) |
| Full Bright (`full_bright`) | Gamma 100–1500% via private backing value; saves/restores user gamma, re-asserts each tick | `FullBrightFeature`, `SimpleOptionMixin` (`@Accessor` force-set) |
| No Fog (`no_fog`) | Multiplies atmospheric fog distance 1–100× | `NoFogMixin` only (no Feature object) |
| Pack Tweaks (`pack_tweaks`) | Bundle: totem particle size 25–100%; first-person shield styles (VANILLA/LOWERED/SIDE/COMPACT); shield-status tints; water & lava clarity fog; hide lava orange overlay | `TotemParticleMixin`, `HeldItemRendererTweaksMixin`, `UnderwaterClarityMixin`, `LavaClarityMixin`, `LavaOverlayMixin` |
| Entity Health (`player_health`) | HP above nearby entities (number or heart row); "only attacked" mode | `PlayerHealthLabelMixin` + `LivingEntityRendererExtractMixin` + `ClientPlayerAttackMixin` + `util/AttackedPlayerTracker` + `util/AuroraHealthSnapshots` |
| FreeLook (`free_look`) | Hold-V detached camera (3rd-person forced); adapted from Freelook++ (MIT, credited in-file) | `FreeLookFeature`, `EntityMixin`, `CameraUpdateMixin` |
| Saturation Bar (`saturation_bar`) | Hidden saturation pips over hunger bar; AppleSkin port (Unlicense, credited) | `hud/SaturationOverlay`, `InGameHudFoodMixin` |
| Block Overlay (`block_overlay`) | Custom block-selection outline (color, 1–6px width, see-through, face fill; SOLID or RAINBOW) | `hud/BlockOverlayRenderer` (Fabric `BEFORE_BLOCK_OUTLINE`), `BlockOverlayFeature`, `VertexRenderingMixin` (vanilla outline) |
| Toggle Sprint/Sneak (`toggle_sprint_sneak`) | Press-once sprint/sneak; holds vanilla keys down each tick; 3 HUD display modes | `ToggleSprintFeature`, `hud/module/ToggleSprintSneakModule` + `ToggleIndividualModule`×2 |
| Alerts (`alerts`) | Popup warnings: low durability (per-slot edge), low hunger, effect expiry; optional sound + test | `ArmorAlertFeature` + `StatusAlertFeature` → `hud/AlertManager`/`AlertRenderer` |
| Crosshair (`crosshair`) | Preset or CUSTOM painted crosshair with a **free-form canvas** (any W×H up to 128; dims live in `crosshairCustom{Width,Height}` + flat `boolean[]` pixels, resolved via `util/GridDims`); indicator crosshair when entity attackable; deliberate half-pixel centering fix. Canvas editor renders through a cached `DynamicTexture` (`ui/util/CanvasTexture` — one blit/frame, re-raster only on edit; replaced a per-cell fill loop that cost ~12.8 ms/frame at 33×33), HUD path merges lit cells into run-length fills, and growing the grid first runs a **measured** cost benchmark on the player's machine (`[canvas-cost]` log) with an apply-anyway warning — never hardware-name heuristics | `hud/CrosshairRenderer`, `PixelCanvasSetting`, `CanvasTexture`, `InGameHudMixin` (vanilla suppression) |
| Hitbox (`hitbox`) | Custom entity hitboxes (self/target colors, eye-line, look line, width, see-through). Renders at plain vanilla interpolation — the smoother was **deliberately reverted** (desynced from model) | `hud/HitboxRenderer` (AFTER_ENTITIES) + `WorldLineRenderer`, `HitboxFeature`, `EntityRenderDispatcherMixin` |
| Hit Color (`hit_color`) | Recolors hurt flash (port of harimasa/HitColor, MIT, credited) | `MixinOverlayTexture`, `EquipmentLayerRendererMixin`, `util/OverlayReloadListener` |
| Better Hitreg (`better_hitreg`) | BetterHitreg by Jass, integrated with permission (credited in-file + screen subtitle). Client-side hit feedback: on your swing the target's hurt animation, the correct attack sound and crit/sharpness particles play locally after `hitregDelayMs` (0 = next frame) while the server's late copy is cancelled (`ServerMixin`/`NetworkMixin`→`DontAnimate` marker→`DamageMixin`); "Safe Regs Only"/shield rules; ghost + misplace detection over a rolling 100-hit window (surfaced as live tooltips on the Alert Delays/Ghosts/Misplaces toggles + "Reset Tracked Stats"); audio (mute other fights/self/them/non-hits, 1.8 sounds, OpenAL EFX muffle/sharpen via `SourceMixin`, metronome); render (hide other fights/animations/armor/particles, target + server hitbox, target cross, reach + jump rings, perfect-hit / jump-reset flash); practice arena (Unrender World via `ChunkMixin`, solid floor, floor grid); 19 ARGB overlay colors; six keybinds incl. the practice scoreboard. Fight tracking feeds the Stats Overlay (`Settings.addFight`). No chat/alert output at all (removed at integration). Card toggle = `hitregEnabled` master (ANDed into every `Toggle.toggled()` read); "Custom Hitreg" inside is upstream's own switch | `hitreg/*` (§2), `mixin/hitreg/*` (13), `AuroraConfig.hitreg*` (Reset prefix `hitreg`) |
| Info HUD (`info_module`) | Corner readout, 13 individually toggleable rows (FPS/XYZ/time/facing/biome/light/memory/ping/CPS/playtime…) | `hud/module/InfoModule`, `PlaytimeFeature` (per-world buckets) |
| CPS (`cps`) | L/R clicks-per-second; counts from raw GLFW callback (polling caps at 20) | `CpsModule`, `CpsTracker`, `ClickTrackerFeature`, `MouseClickTrackerMixin` |
| Armor HUD (`armor_hud`) | 4 pieces + durability text/bar, horizontal/vertical, VANILLA slot background | `hud/module/ArmorModule` |
| Reach Display (`reach_display`) | Last attack distance; holds value through smoothstep fade. Polls attack key — no mixin | `ReachModule`, `ReachTrackerFeature` |
| Potion HUD (`potion_hud`) | Replaces vanilla status strip: icon/name/Roman level/countdown rows; panel fades after expiry | `PotionModule`, `InGameHudPotionOverlayMixin` |
| Ping (`ping`) | Corner ping HUD + numeric ping in tab list + colored ping under nametags (one toggle, three sub-flags) | `PingModule`, `PlayerListHudMixin`, `PlayerEntityRendererMixin` (also appends totem pops to nametags) |
| Totem Pop Counter (`totem_pop`) | Your (and optionally others') totem activations; reset keybind; nametag counts | `TotemPopFeature`, `ClientPacketListenerEntityEventMixin`, `TotemPopModule` |
| Stats Overlay (`stats`) | Session kills/deaths/K/D/time (kills heuristic: strike → 4s death window) **plus Better Hitreg's fight stats**: Fights (session + lifetime total), Fight Time (session + lifetime), Last Fight (duration + both accuracies). Lifetime totals persist in `fightStatsTotalFights`/`fightStatsPlaytimeSeconds` (profile-excluded); session values reset with "Reset Stats"; lifetime totals only via the separate "Reset Lifetime Fight Totals" button | `StatsTrackerFeature` (`recordFight`), `StatsModule`, fed by `hitreg/settings/Settings.addFight` |
| Waypoints (`waypoints`) | Per-world persistent markers: beacon beam and/or highlight slab + billboard labels; auto death waypoints (replace-previous or capped) | `WaypointFeature`, `hud/WaypointRenderer` (two passes), `WaypointManagerScreen` |
| Minimap (`minimap`) | HUD minimap; rotation baked into the sampling pass (cheap); biome tint, hillshade, depth water; waypoint/entity dots, compass; can read World Map's region cache instead of live chunks | `hud/module/MinimapModule` (805 lines), `MinimapFeature`, `worldmap/WorldMapClient.sampleSurfaceAbgr` |
| Container Preview (`container_preview`) | Tooltip grid for shulker contents + ender chest (snapshot while chest screen open — 1.21.x limitation) | `ItemTooltipImageMixin`, `ItemContainerContentsTooltipMixin`, `hud/preview/*` |
| Item Physics (`item_physics`) | Dropped items lie flat, tumble by motion | `ItemEntityRendererExtractMixin` + `ItemEntityRendererSubmitMixin` + `util/AuroraItemPhysicsSnapshots` |
| Particles (`particles`) | Per-particle-type visibility/scale/ARGB tint with search. Visibility gated at HEAD of `createParticle` (RETURN is too late) | `ParticleControlFeature`, `ParticleEngineMixin`, `ParticleAccessor` |
| Item Scale (`item_scale`) | Per-item held scale/rotation/translation; per-hand defaults | `HeldItemRendererTweaksMixin`, `setting/ItemScaleSetting`, `ui/util/ItemSpriteRenderer` + `mixin/ItemStackRenderStateAccessor` |
| Resourcepack Browser (`resourcepack_browser`) | Modrinth search/install for resource packs into `resourcepacks/` (never auto-enables) | `ResourcePackBrowserScreen`, `modrinth/ModrinthApi`, `modrinth/PackIconCache` |
| Minecraft Reflex (`reflex`) | Reflex-style latency reduction: GL timer-query GPU time + EWMA CPU frame time → hold CPU before input sampling | `ReflexMinecraftMixin`, `util/reflex/*` |
| Animations (`animations`) | Swing curve + 1.8 swing arc, view-bob curve/amplitude, 1.7/1.8 damage tilt, idle held-item sway, frame-rate-independent entity movement smoothing (tau scales with server packet bundling) | `HeldItemRendererMixin`, `GameRendererBobMixin`, `DamageTiltMixin`, `LivingEntityRendererExtractMixin` + `EntityMovementSmoother`, `util/AnimationCurves`, cross-cutting `ThrottleDetector` |
| Hotbar Bounce (`hotbar_bounce`) | White pulse outline on hotbar slot when stack count grows | `HotbarItemBounceMixin` → `HotbarBounceTracker` |
| Keystrokes (`keystrokes`) | Key-panel overlay: WASD/mouse/CPS/space/sneak/sprint + up to 12 custom keys; animated accent press | `hud/module/KeystrokesModule` |

### SETTINGS tab (11 tiles)

Custom Title (`custom_title` — themed title screen + starfield + themed vanilla buttons on
multiplayer/world-select via `TitleScreenMixin`, `AuroraTitleScreen`,
`SelectionScreenBackgroundMixin`, `AbstractButtonMixin`), Text & Fonts (`text_fonts` —
bundled Google fonts scoped OFF/Aurora-only/ALL via `MixinFont` + `AuroraFontRenderer`),
Interface (`interface` — FPS cap for Aurora screens), Smooth Camera, Frame Pacer
(`RenderSystemMixin` + `util/FramePacer`, replaces vanilla `limitDisplayFPS`),
Low Latency (VSync-off, zero-latency camera, adaptive render sleep; **`highFrequencyInput`
is advertised but has no implementation**), Tick Sync (retunes client tick rate to entity
packet arrival; `TickSyncNetworkMixin`), Decoupled Input (per-frame cursor delta),
Drag-to-Reorder Servers (`ServerListDragReorderMixin` — 3-phase animated drag),
Compliance Mode, Accessibility (colorblind LMS daltonization matrices).

### Cross-cutting systems worth knowing

- **`ThrottleDetector`**: measures server movement-packet bundling (median inter-move tick
  gap, 32 samples) → factor 1–6. Feeds `EntityMovementSmoother` only. Was also feeding
  hitbox smoothing, which was reverted (§9).
- **`ComplianceModeFeature`**: auto-disables reach/toggle-sprint/hitbox/keystrokes/particles
  on a built-in list of strict servers (hypixel etc.); restores on leave; user safe-list
  overrides.
- **`TickSyncFeature`**: adapts *client* tick rate toward server packet timing; resets on
  join/disconnect.
- **WorldScope** (`util/WorldScope`): stable per-world id (`mp:<ip>` / `sp:<level>`) keys
  waypoints, playtime, and worldmap storage folders.

---

## 5. Theme/token architecture (COSMIC-style)

**The one-sentence model:** the user picks ONE accent color (+ mode, roundness, opacity);
`PaletteEngine` derives every UI color from it; tokens are read through a cached
`ResolvedTheme`; legacy statics are a write-only-from-one-place facade.

### Data flow (who writes what)

```
AuroraConfig.theme : ThemeDefinition   (accent int, ThemeMode, ThemeRoundness, GlassStyle, backgroundOpacity)
        │  ThemeFeature.onTick → ThemeManager.sync()   (6-field dirty-check per tick)
        ▼
ThemeManager.reload()  →  ThemeResolver.resolve(def, cfg.themeEnabled)
        ▼                                     │
PaletteEngine.derive(accent, mode)  ←─────────┘   pure function, no MC imports
        ▼
ResolvedTheme  (immutable ordinal-indexed int[]; volatile static in ThemeManager)
   ├─ color(ThemeToken)      — one array access, zero alloc, safe from any thread
   ├─ surfaceColor(token)    — token RGB with WINDOW_FILL's opacity-driven alpha
   ├─ stainedTint()          — accent RGB at max(WINDOW_FILL alpha, floor 140)
   ├─ generation()           — AtomicLong stamp; key any theme-derived pixel cache off this
   └─ project()  ──────────► AuroraTheme statics (util/AuroraTheme.java)
```

- **`ThemeToken`** (enum, ~40 tokens): named by *role* — `ACCENT` family, surfaces
  (`SURFACE`, `WINDOW_FILL`, …), tiles, backdrop, `ON_*` text tokens (Material convention),
  `BORDER*`, fixed-hue `SEMANTIC_ERROR/SUCCESS/WARNING` (deliberately NOT accent-derived),
  `OVERLAY_DIM`/`ON_OVERLAY`, `HUD_BACKDROP_*`.
- **`ThemeManager.reload()`** re-reads config → resolve → project → bump generation. Call
  on config load, profile apply, and any theme edit. **`sync()`** is the per-tick cheap
  dirty-check (accent/mode/roundness/opacity/enabled) that also catches profile switches.
- **`PaletteEngine`** rules: backgrounds take accent *hue only* with mode-locked lightness
  (dark ~6–15%, light ~90–97% — a near-white accent can't wash out the UI); text is
  contrast-checked via relative luminance; simple HSL, never throws on any input.
- **`ThemeResolver`** has two paths: derived palette (`themeEnabled`) and a verbatim fixed
  *factory palette* (theme off — intentionally not the derived form of the defaults).
  **`applyBackgroundOpacity` stamps the opacity onto the `WINDOW_FILL` token's ALPHA ONLY —
  this is the single opacity application point for the whole UI (§6).**
- **`ThemeRoundness`** (Stage 3's non-color token): ROUND/SLIGHTLY_ROUND/SQUARE →
  `RADIUS` 10/6/0, `RADIUS_LARGE` 14/8/0, `RADIUS_SMALL` 6/3/0, projected like colors.
  Knobs/thumbs stay circular in every mode.
- **`GlassStyle`** (the second non-color token): FROSTED (default) / TRANSPARENT.
  A *rendering-technique* switch, not a look of its own — TRANSPARENT makes
  `BlurPanelRenderer.renderPanel` decline in a single early-return guard, so every
  glass consumer takes the flat fallback it is already required to draw (§6
  fallback contract). No call site knows the setting exists. Corner Style and
  Background Opacity keep their exact meanings in both styles (they live in the
  caller's fill, which the renderer never touches), and TRANSPARENT pays none of
  the capture/blur/readback cost. Surfaced as a `SegmentedSetting` ("Glass Style")
  between Corner Style and Background Opacity on the Theme screen.
- **`ThemePresets`**: 9 curated accents (Blue…Teal; RED `0xFFEB0029` "OnePlus Red" is
  factory default). No preset ids stored — just RGB values.
- **`ThemeMigrator`**: one-way sanitize/migrate of legacy JSON (`themePrimaryColor` →
  structured `theme` object; drops the obsolete secondary seed). Used by config load AND
  profile apply. Never throws.
- **`util/AuroraTheme` is NOT a legacy dead system** — it's the *Stage 1 projection
  facade*: ~30 render files still read `AuroraTheme.X` statics for pixel-identical output,
  and `ResolvedTheme.project()` is **the only writer** of those statics. Migrating readers
  to `ThemeManager.color(token)` is future work. Gotcha: the `IOS_BLUE`/`ACCENT_*` names
  actually hold OnePlus **Red**; `GREEN_ACCENTS_ENABLED` is hardcoded `false`.

### Stage history (useful context in javadocs)

Stage 1 = token enum + projection facade; Stage 2 = single-accent `PaletteEngine` +
light mode; Stage 3 = roundness token; Stage 4 = screen consolidation (`AuroraScreen` is
the single canonical Modules+Settings screen).

### Shared component framework (`ui/component/`)

`Widget` base (shapes / glass pass / overlay split — `renderGlassPass` is the pre-dim
surface hook, §6); `Button` (painter-based, hover/press spring scale, `GlassStyle` OFF /
NEUTRAL / STAINED — §6; paints its surface in `renderGlassPass` when the screen runs one,
else in place), `ButtonWidget` (Screen-widget wrapper forwarding the same painter, incl.
`renderGlassPass(g)`), `ToggleSwitch`, `Slider`, `SegmentedControl` (glass track; neutral
unselected / accent-stained selected), `RoundedPanel` (themed window panel), `ColorSwatch`
(transparent-color checkerboard, corner-clipped to the rounded rect), `ThemedScreen`
(marker — opts a screen's vanilla `EditBox`es into the mod-wide themed/glass search bar
via `EditBoxMixin`), `GlassEditBox` (glass-pass hook `EditBoxMixin` implements onto every
`EditBox`), `GlassSurface` (the shared glass-material painter: `container` / `control` /
`stainedControl`, each = live-world gate → `renderPanel` with the role's lighting → tint →
rim, returning whether glass drew; plus the frame-phase machinery
`beginFrame`/`beginGlassPass`/`overlayDim` that makes the pre-dim layering rule structural
and defers each surface's rim finish past the dim — §6. Adopted by
`ProfileManagerScreen`, `WaypointManagerScreen`, `Button`, `EditBoxMixin` (2026-09-05);
the ~8 remaining inline copies of the idiom migrate in the approved follow-up).

**HUD status colors — `theme/HudStatus`** (R6 Part 1, 2026-09-08): every red/green/amber the
in-game HUD paints (latency tiers, toggle ON/OFF text, potion minutes, minimap entity dots,
alert severities) reads from this one fixed-hue, mode-independent palette — NOT from the
`SEMANTIC_*` tokens. That split is deliberate, not an oversight: the HUD's legacy hexes were
never value-identical to the token values, the semantic tokens are UI-mode-locked (they
darken in light mode — wrong for text floating over a bright world), and the 5-tier latency
ladder can't collapse into 3 tokens. Unifying the VALUES with `SEMANTIC_*` would be a
visible restyle and belongs to a screenshot-gated decision, not a casual refactor; until
then `HudStatus` is the single place to change HUD status colors. Also in the HUD color
story: `util/HudBackgrounds`' `AURORA` background mode is what reads the
`HUD_BACKDROP_TOP/BOT` tokens (the audit's "exist unused" was stale).

Rendering utilities: `RenderUtil` (float-precision AA rounded rects/circles/outlines +
`beginCapture`/`RectSink` used by `UiLayerCache` — plus `DISCARD_SINK` and the
capture-aware `fillLogical` for plain integer-cell fills), `AuroraShapes` (chamfered-octagon
iOS-style panels, gradients, shadows — note: *chamfered*, not truly rounded),
`RoundedRect`/`AuroraSquircle` (true rounded AA fills/masks; profiling said rounded fills
were the biggest GUI cost), `UiLayerCache` (rasterizes static screen chrome once into a
`DynamicTexture`, blits per frame; version-keyed dirty; `blitAt` is the positional form
behind the row/card TEMPLATE pattern — see §10), `AuroraFontRenderer` (applies
bundled TTFs as text `Style`; `isRenderingAuroraUI` flag scopes the custom font),
`ItemSpriteRenderer` (crisp flat item icons via `ItemStackRenderStateAccessor`, with 3D
fallback for tinted/multi-layer models).

**Toggles and sliders are always opaque** (theme tokens, never glass) — deliberate, part
of the glass conventions in §6.

---

## 6. Glass material system — how it works, conventions, rollout status

`ui/render/blur/BlurPanelRenderer` (+ test harness `BlurTestScreen`, opened via the
unbound `blur_test` keybind in-world). Read that class's javadoc before touching anything
in it — it encodes hard-won lessons.

**History:** an earlier, more ambitious glass attempt (SDF-normal lighting, specular,
refraction/distortion, dome/bevel geometry) *failed and was removed*. The current renderer
is a deliberately conservative rebuild. Refraction in particular was "never once cleanly
confirmed working" and is explicitly deferred to a standalone future task — **not built,
by decision**. What exists is a two-term *lighting impression*: a directional face
gradient + a fixed-width directional border stroke against one fixed light
(`normalize(0.25, -1.0)`, i.e. up and ~14° right). Raised vs depressed = simple inversion
of both terms. The rim is a TWO-HALF system driven by the one Background Opacity value:
the IN-GLASS stroke (composite pass, `uRimBlend` = resolved opacity) is the bright light
catch at low opacity and retires into the panel's own per-pixel base as opacity rises;
the ABOVE-FILL half, `BlurPanelRenderer.drawRimFinish` (in place right after each glass
tint fill, or — inside a declared glass pass — deferred by `GlassSurface` to just after the
dim so the highlight is not veiled with the body, see convention 6), draws a DIRECTIONAL
rounded border in the current accent's pastel
(`ResolvedTheme.rimPastel()`
— accent lightened 65% toward white, derived once per resolve) with alpha = opacity² —
negligible while translucent, a SOLID fully-opaque pastel stroke at 100% opacity, because
an opaque fill occludes anything left inside the glass texture. The finish's directionality
is the composite shader's own border-stroke math (SDF band with soft internal taper ×
light-facing smoothstep), rasterized once per shape into a cached white mask texture and
tinted with pastel × opacity at blit time — the opacity behavior modifies the directional
falloff, never replaces it. The face gradient is
deliberately NOT part of either half.

**Pipeline:** blit-capture a padded region of the main render target (`glBlitFramebuffer`)
→ quarter-res two-pass separable Gaussian → composite pass (rounded-rect coverage +
lighting) → `glReadPixels` → `NativeImage`/`DynamicTexture` → ordinary `GuiGraphics.blit`.
Straight (non-premultiplied) alpha; RGBA8 intermediates on purpose. Readback is per panel,
measured 2026-09-08 with `GlassStats` at ~0.2 ms per button-sized panel and ~0.55 ms per
full-width list row, linear in panel count — accepted for simplicity/robustness.

**Output pool + degradation priority (R10, 2026-09-08):** every concurrent panel in a frame
takes its own pooled output texture (`OUTPUT_POOL = 64`, was 24; slots are lazily sized so
the constant is free — it caps the per-frame pipeline bill, ~15–20 ms when a static list
screen actually fills it). A frame that asks for more declines panels by
`BlurPanelRenderer.Priority` — lowest first, the same panels every frame — instead of
render order: `WINDOW` (windows, sidebars, modals, preview card) > `CONTROL` (toolbar/Done/
Reset buttons, chips, fields, setting widgets — the default) > `ROW` (repeated rows, tiles,
cards) > `DETAIL` (per-row Copy/Color/Duplicate, per-card Install). The mechanism is a
reservation from LAST frame's per-tier demand (`nextOutput`), so it costs nothing on frames
that fit and needs one warm-up frame after a screen opens; within a tier the pool stays
first-come (rows degrade from the bottom of the list up). Measured demand: Theme 13, Mods
grid ~15, detail ≤ ~20, Profiles 2/row + 2, Waypoints 3/row + 2 (47 on a 1080p window — the
old pool went flat from row 9 down AND dropped the Done button), pack browser 4 + 1/card (54
on a 6-column 4K grid). `BlurTestScreen`'s `stress=N` state token renders N tagged panels in
adversarial order to check the policy; `-Daurora.glassStats=true` reports per-tier
claims/declines.

### The conventions (violating these has caused real bugs)

> Conventions 1–4 and 9 are centralized (not changed) by `ui/component/GlassSurface`; the
> layering contract (6) is enforced by its frame-phase guard (`GlassSurface.overlayDim`) on
> the screens that have adopted it — `ProfileManagerScreen` and `WaypointManagerScreen` as of
> 2026-09-05, the rest pending a separate, explicitly approved rollout (see 6 below). The
> `renderBackground` world-gate (5) is still a per-screen override; use
> `GlassSurface.liveWorldBackdrop()` for it.

1. **Single opacity application point.** The theme's Background Opacity exists ONLY as the
   alpha of the `WINDOW_FILL` token (stamped by `ThemeResolver`). The glass pipeline adds
   no opacity of its own; the caller tints the blur with an ordinary translucent
   `WINDOW_FILL` fill. Never multiply a second opacity factor anywhere in this pipeline —
   a second application point is *the* bug class that killed the previous attempt.
   (The rim stroke's convergence target is the one deliberate consumer of that same
   single value — a lighting-term derivation, not an alpha change; see the pipeline
   paragraph above.) The **frost radius** is the other such derivation (2026-09-07):
   the blur a panel is rendered with is `requested × max(1/6, √opacity)` —
   `BlurPanelRenderer.frostRadiusPx` — so the slider's bottom is clear glass (4 px at the
   default 24) and full frost arrives from ~70% up. Before this the blur was a constant,
   which is why a 0% panel still read as a solid slab even though its fill alpha was
   already 0: the frost, not the alpha, was the "opacity" at the low end. Slider → alpha
   itself is linear, 0..1 in 0.01 steps, no floor (`ThemeResolver.applyBackgroundOpacity`);
   the only floor anywhere is the stained tint's 140 (convention 3, primary/selected
   elements only).
2. **Raised vs depressed.** Windows/main containers = DEPRESSED (recessed); interactive
   controls (buttons, rows, chips, segments, search fields) = RAISED. Same two lighting
   terms, inverted.
3. **Neutral vs stained.** Neutral glass tints with `WINDOW_FILL` (tracks the opacity
   slider). Accent-stained glass (`Button.GlassStyle.STAINED` /
   `ThemeManager.stainedTint()`) is ONLY for selected/primary elements (toggle-ON track,
   slider fill, selected segment, primary button, selected row). Stained alpha =
   `max(windowAlpha, 140)` — a fixed visibility floor so the tint reads at low opacity;
   it is a style constant, **not** a second opacity control. Content drawn ON a stained
   surface (labels, icons) takes `ON_ACCENT` — the token `PaletteEngine.pickOnColor`
   contrast-derives against that same accent reference — never the accent itself
   (raw-accent glyphs were invisible on stained tiles; fixed 2026-08-29 in
   `AuroraScreen.drawTileIcon`/`drawLayoutButton`). Scope note (user ruling, 2026-08-29):
   a full-width list row reads as a CONTAINER, not a selected control — the Profiles
   screen's active row took a whole-row `stainedTint` and was twice flagged as an
   accent-tinted container. On container-like rows, mark selection with a compact accent
   element (the Active badge), never a whole-row tint.
4. **Always opaque, never glass:** toggles, sliders, text entry that isn't a search field
   (e.g. the hex field), and the color picker's editing surfaces (pad/strips/swatch —
   glass would blur/tint the very values being edited).
5. **World vs no-world capture.** With no level loaded the main render target holds no
   valid world, so `renderPanel` **declines** (menu-context guard) and the caller must
   draw its flat themed fallback + keep the vanilla backdrop. Screens that want glass
   over the live world override `renderBackground` to skip the vanilla backdrop sandwich
   when `minecraft.level != null` — the shared test is `GlassSurface.liveWorldBackdrop()`
   (used by `ProfileManagerScreen`, `WaypointManagerScreen`); `AuroraScreen`,
   `FeatureDetailScreen`, `ColorPickerScreen`, `HudEditorScreen` and
   `ResourcePackBrowserScreen` still carry private copies. Every glass integration follows
   the same **fallback contract**: decline (menu, screenshot suppression, failure) ⇒
   complete flat look returns.
6. **Layering contract — universal, structural.** EVERY glass surface — containers AND
   controls (rows, buttons, chips, pills, search/rename fields) — is painted in the screen's
   **glass pass**, BEFORE the screen's `OVERLAY_DIM` fill, so the dim veils the glass like
   it veils the world; content (text, icons, badges, hover washes, focus rings, carets) and
   any flat fallback are painted after the dim. Structurally: a screen brackets the pass
   with `GlassSurface.beginGlassPass()` at the top of render and fills its dim ONLY through
   `GlassSurface.overlayDim`, which stamps the frame and then flushes the deferred rims
   (below). Any glass body painted later in that frame is reported (`ERROR` + stack trace
   in a dev environment, one-shot `WARN` per screen class otherwise — the surface still
   paints, so a mis-ordered screen keeps its glass and the log shows the defect on frame
   one; `overlayDim` without a pass, a pass never closed by `overlayDim`, and a surface
   painted under a raw scissor the pass isn't tracking are reported the same way).
   **Rim timing (2026-09-08, the landed cand-a approach):** inside a declared pass the
   surface BODY (capture, blur, lighting, tint) is painted pre-dim, but each surface's
   above-fill rim finish is QUEUED and painted by `overlayDim` right after the dim fill —
   the body is veiled like the world while the highlight stays crisp above it, so raised
   controls keep their gloss. Because the flush happens outside whatever scissor the
   surface was painted under, screens wrap clipped surfaces in
   `GlassSurface.enableScissor`/`disableScissor` (thin wrappers) so each deferred rim
   re-applies its clip. Surfaces painted with no pass open paint their rim in place (the
   legacy order). Widgets split accordingly:
   `Widget.renderGlassPass` (`Button`, forwarded by `ButtonWidget`) and
   `GlassEditBox.aurora$renderGlassPass` (every `EditBox`, via `EditBoxMixin`) paint the
   surface pre-dim; the widget's normal render then paints content only — or, on a screen
   that never ran the pass, the surface in place (the legacy order).
   **Rollout status (2026-09-05):** enforced on `ProfileManagerScreen` and
   `WaypointManagerScreen` (closing audit B3/B4). `AuroraScreen`, `FeatureDetailScreen`,
   `ResourcePackBrowserScreen` and the setting widgets still fill a raw dim and paint their
   controls AFTER it — the historical, unguarded order (only their window/container surface
   was ever pre-dim) — pending a separate, explicitly approved follow-up. Until then the
   rule above is true only on the two migrated screens.
7. **Per-element UV mapping.** Each panel samples only its own sub-rect of the shared
   blur chain (`uUvRect`); a full `[0..1]` UV sweep would put the whole capture into a
   small element — "the UV bug" this contract exists to prevent (see comments near the
   composite shader).
8. **Screenshot (F2) interlock.** Vanilla's grab path leaks `GL_PACK_*` state; a stale
   `PACK_ROW_LENGTH` turned this pipeline's `glReadPixels` into an out-of-bounds native
   write (real JVM crashes). Fixes, all in place: neutral pixel-store state at pipeline
   entry/exit, pack-buffer bindings forced to 0, and `ScreenshotMixin` →
   `noteScreenshotGrab()` suppressing all glass for 400 ms around a grab (callers fall
   back opaque for that frame). `[S]` on `BlurTestScreen` is a permanent crash canary.
9. **Focus/hover on glass** is carried by the caret/text color/hairline focus ring/scale —
   never by changing the tint. The glass rim REPLACES the outline (no double outline).
10. **Never destroy a texture mid-frame.** 1.21.11's `GuiGraphics` RECORDS blits into a
   deferred `GuiRenderState` and there is no flush API, so a texture blitted earlier in
   the frame is still pending when you close it — the driver then binds a deleted name
   (`GL_INVALID_OPERATION in glBindTexture(non-gen name)`) at submit. This is the same
   rule the output pool documents; the rim-mask cache violated it and cost a whole
   session of glass (see §9, "Reset kills glass"). Queue evictions and free them in
   `beginFrame()`.
11. **Never poll `glGetError` without draining first.** It reports the OLDEST queued
   error from ANY GL call, so an error raised by foreign code (or an earlier frame) is
   misattributed to yours. This pipeline treats a GL error after its capture blit as
   session-ending, so `Frame.run` drains the queue before touching GL.
12. **Name a `Priority` for repeated glass.** Windows/containers pass `WINDOW`
   (`GlassSurface.container` does), repeated rows/tiles/cards pass `ROW`, and repeated
   small controls inside them pass `DETAIL` (`Button.priority(...)`). Anything unnamed is
   `CONTROL`. A new list screen that leaves its rows at the default makes them compete
   with the toolbar's Done button the moment the list outgrows the pool.

### Per-screen rollout status — verified in code on master, 2026-09-07

Enrollment history: glass started as a Theme-screen pilot, then a staged
`GLASS_PILOT_IDS` set (`theme`, `block_overlay`, `pack_tweaks`) in
`FeatureDetailScreen`. That mechanism is retired (landed 2026-09-05) — glass
flags now default ON mod-wide. Status below is committed `master`.

| Screen | Status | Detail |
|---|---|---|
| `AuroraScreen` (main settings) | **Full** | Depressed glass window + raised glass tiles, search chip, profile button, bottom buttons; world-gated |
| `FeatureDetailScreen` (all 45 detail views) | **Full** | Retires `GLASS_PILOT_IDS`; depressed glass window + glass Done/Reset for every feature |
| `ProfileManagerScreen` | **Full** | Rows are this screen's containers: DEPRESSED neutral glass (`WINDOW_FILL` only; the active-row `stainedTint` read as an accent-tinted container — user-flagged twice). Selection shown solely by the accent Active badge; New Profile/Done/Duplicate/Create all neutral raised |
| Theme screen widgets (`ThemePreviewSetting`, `SegmentedControl`) | **Full** (the original pilot) | Preview card/chips/buttons glass; segments neutral-unselected/stained-selected |
| `EditBoxMixin` search fields (Particles, Item Scale, ResourcePacks, Modules grid, + themed vanilla screens) | **Full** | Raised glass, focus = caret + accent hairline ring, tint constant; flat fallback without a world |
| Setting widgets: `EnumSetting` (button + expanded popup), `KeybindSetting`, `KeyListSetting`, `ItemScaleSetting`, `ButtonSetting`, `SegmentedSetting` | **Full** (defaults flipped opt-in → default-on) | `FeatureRegistry` still contains now-redundant `.glassButton(true)`/`.glassSegments(true)` "pilot" calls (lines ~86, 96, 180, 292) — harmless cleanup candidates |
| `ColorPickerScreen` | **Chrome-only, by design** | Apply = STAINED, Cancel = neutral raised; editing surfaces + hex field deliberately opaque |
| `HudEditorScreen` | **Chrome-only** | Two floating action buttons, neutral raised ("navigation action, not a primary state") |
| `WaypointManagerScreen` | **Full** | Raised glass rows + header/add buttons |
| `ResourcePackBrowserScreen` | **Full** (2026-09-04; cards flat 2026-09-08) | Depressed glass sidebar (pre-dim pass, `SURFACE` tint, `sidebarGlass` flag) + detail modal; **cards FLAT by decision (audit R9/B2)** — their opaque hover-lerp tint fully occluded the blur, so per-card glass was pure cost and the main output-pool driver (B1 halved; each card's install button is still a glass `Button`); active category tab = accent-stained raised glass (inactive tabs stay flat by design — small transient rows inside an already-glass container); install/Retry/Close buttons are the shared `Button` painter (Install = stained primary, Retry = destructive, progress/Done = neutral; **success-green is not expressible through `Button` — mapped to stained/neutral, flagged**); `renderBackground` world-gating added; every radius now a token (`RADIUS_LARGE`/`RADIUS`/`RADIUS_SMALL`; only scrollbar-thumb capsule literals remain). Title removed + count moved below the search bar (was overlapping it). Thumbnails, search field, toast, scrollbars stay opaque/unchanged per convention. (The 2026-09-04 in-client verification at ~7/25/93% opacity, ROUND + SQUARE, predates the flat-cards change; the flat look is the screen's own long-standing decline-path appearance.) |
| `WorldMapScreen` | **Not started** | Flat `RoundedPanel` prompt; only the name field is themed (via `EditBoxMixin`) |
| `AuroraTitleScreen` | **Not started** | Zero glass references |
| Toggles, sliders, HUD modules, tooltips/dropdowns-as-tooltips | **Never glass, by convention** | Opaque token surfaces |

`BlurTestScreen` is the development harness (A/B radius toggles, synthetic capture FBO,
`CaptureSource` override, lighting presets) — not user-facing.

---

## 7. On-disk artifacts (dev run dir: `run/`)

- `config/aurora.json` — the entire config (§3).
- `config/profiles/*.json` — profile snapshots.
- `config/aurora-worldmap/<scope>/<dim>/r.X.Z.wmr` — GZIP region tiles (magic `AWMR`,
  per-chunk timestamps + 512×512 ABGR pixels). Paths pass a strict `[a-z0-9_.-]` sanitize
  — raw `WorldScope` names with spaces once crashed Windows (`InvalidPathException`) and
  killed the previous world-map implementation.
- `config/hitreg.properties` — legacy BetterHitreg file. Read exactly once by
  `HitregMigrator` (2026-09-08) and never written again; deleted from the dev run dir after
  the migration was verified. A user's copy is harmless and can be removed.

---

## 8. Repo state & recent history (updated 2026-09-08)

Nothing is in flight — the working tree is clean and `master` is the canonical branch. The
section formerly here described the 2026-08-29 *uncommitted* "glass everywhere" wave; all of
it landed on `master` (2026-09-05) and is documented as the normal state in §5/§6:
`FeatureDetailScreen`'s `GLASS_PILOT_IDS` retirement, glass search fields via `EditBoxMixin`,
glass defaults on in the setting widgets, glass chrome on
`ColorPickerScreen`/`HudEditorScreen`/`WaypointManagerScreen`, the `ColorSwatch` corner clip,
the two-half rim (`drawRimFinish` + `ResolvedTheme.rimPastel()`), `ProfileManagerScreen`'s
neutral depressed rows, the `AuroraScreen` tab-pitch/scrollbar fixes, the
`ResourcePackBrowserScreen` glass rollout, the crosshair canvas rework, and
`ItemStackRenderStateAccessor` + `ui/util/ItemSpriteRenderer` (both registered and tracked).
Landed after that wave: the 2026-09-04 GUI audit (`GUI_AUDIT.md` + `ARCHITECTURE.md`) and its
B5–B19 fix commits; B20 (numeric-ping `MultiplayerServerListWidgetMixin` registered,
`MultiplayerScreenMixin` deleted); the `GlassSurface` helper adopted on
`ProfileManagerScreen`/`WaypointManagerScreen`/`Button`/`EditBoxMixin` with structural
pre-dim layering (closes B3/B4); frost-radius-follows-opacity (`0c7c416`); the
`MultiplayerServerListWidgetMixin` `@Shadow` crash fix (merged 2026-09-07, `6726079`); and
the deferred post-dim rim finish (candidate A, merged 2026-09-08 — §6 convention 6).

Landed 2026-09-08 after that: the **Better Hitreg integration** in five revertible commits
(entrypoint + mixin fold → `hitreg.properties` migration → feature card/detail screen → fight
stats merged into Stats Overlay → cleanup/attribution/docs). `you.jass.betterhitreg`,
`betterhitreg.mixins.json`, the second entrypoint, the `/hitreg` command tree, every chat
alert, the hand-drawn `ui/` menu and the dead `WorldMixin` are gone.

Unmerged local branches: none. The three rim/lighting experiment candidates were resolved
2026-09-08: `cand-a-rim-post-dim` (post-dim rim finish via a deferred queue in
`GlassSurface`) was chosen and merged; `cand-b-lighting-boost` and `cand-c-rim-plus-boost`
were discarded — B boosted the shared `Lighting.raised()` preset, which had no real effect
on the target screens but a real side effect on every other raised control mod-wide.

Landed 2026-09-08 after that (audit R3/D6 + a standing perf note): AGENTS.md §10 gained
the scale-check rule ("before calling a UI change complete, check whether it could scale
badly"), prompted by the O(rows²) reconciliation fix `0a0caaa`. Then the
Profile/Waypoint manager screens' duplicated machinery — frame skeleton (glass pass → dim
→ content), `SmoothScroll` + the R2-C scrollbar/thumb treatment, inline-rename editor
lifecycle, toolbar row, `fitName` memoization, flat-row/shadow painters, thumb input
handling — was merged into `screen/ManagerListScreen<T>`, an abstract screen template
(the utilities `GlassSurface`/`SmoothScroll` stay composition-based; what was duplicated
here was control flow, which composition cannot absorb honestly). Zero intended
visual/behavioral change, verified by a standalone differential harness (176,892
comparisons over scroll bounds, visibility predicates, editor layout, thumb geometry,
hit zones and the commit state machine — 0 diffs), compile, and a dev-client boot smoke.
Deliberately kept screen-side as genuine differences: row glass role (Profile rows
depressed containers, Waypoint rows raised controls — both §6 rulings), the tail paint
order (Profile draws editor-then-toast, Waypoint toast-then-editor; observable only when
the bottom row's editor overlaps the toast band), Profile's create row + switch-on-body-
click, rename-apply semantics, and the nameFitCache clearing policy.

Landed 2026-09-08 after that (audit P2/R8, the 2-3 fps fix): the manager screens' shared
**row-surface template** — both lists' rows are geometrically identical, so each row's
static shape layer (six shadow-ring outlines + the row's glass tint fill, ~1k AA fills)
rasterizes ONCE into a small `UiLayerCache` and blits per row
(`ManagerListScreen.paintTemplatedRowSurface`: the row's ordinary glass pass still runs
live for the live-world panels and deferred rims, but its shape fills are redirected to
`DISCARD_SINK`; two variants — tinted vs shadow-only — chosen per row by whether its glass
drew). The row BUTTONS self-template inside the shared `Button` (resting glass-tint and
flat surfaces, per size/variant, session-static like the rim-mask cache), and row
color swatches self-template per color in `ColorSwatch` (LRU-capped). Root cause was
volume × MC 1.21.11's quadratic per-fill record cost — measured and closed with numbers
in GUI_AUDIT.md P2 (Waypoints 30 rows: 2 fps / ~29k fills → 28-47 fps / 19 fills). Same
pattern on the pack browser's flat cards (one at-rest card template, hovered card live)
and the color picker's four per-surface caches (R8; a drag re-rasterizes only the surfaces
whose values changed). Pixel parity verified by A/B screenshot diff (≤0.11% beyond ±8/255,
world-blur noise only).

---

## 9. Known outstanding work, dead code, and hazards

**Incomplete / follow-up candidates**
- The deferred-rim queue in `GlassSurface` (§6 convention 6) has no opt-out for surfaces
  deliberately meant to render ABOVE an already-dimmed screen — e.g. the pack browser's
  detail modal or `EnumSetting`'s expanded popup. Not blocking today (those screens haven't
  adopted the glass pass; their surfaces paint with no pass open, so rims paint in place),
  but whoever migrates them needs a "render above the dim, rim included" escape hatch.
- The shared `Button` has no success variant, so `ResourcePackBrowserScreen`'s
  Install/Installed states render accent-stained/neutral instead of success-green
  (§6 table). A `Button.success`-style variant would restore the old semantics.
- FeatureRegistry's redundant `.glassButton(true)`/`.glassSegments(true)` pilot calls.
- `highFrequencyInput` (Low Latency) is advertised in the UI with no implementation.
- Accessibility colorblind correction: matrices are computed but the screen-reader item is
  deferred and the scroll-remap tick is a documented no-op.
- ModMenu integration is minimal (reflective shim; "Mods" button retired as duplicating
  Aurora Settings).

**Fixed, with a standing judgment call**
- *"Reset kills glass for the session"* (fixed 2026-09-04): `BlurPanelRenderer`'s
  rim-mask cache enforced its 16-entry cap with a wholesale `releaseRimMasks()` called
  mid-frame. Masks already blitted into the deferred `GuiRenderState` were destroyed
  while pending → `GL_INVALID_OPERATION in glBindTexture(non-gen name)` at submit → the
  error sat in the queue until the next frame's `Frame.capture()` polled `glGetError`,
  misattributed it to its own `glBlitFramebuffer`, threw, and latched
  `permanentlyDisabled` for the session (every panel mod-wide then took its flat
  fallback — "the UI went flat and stayed flat"). Theme **Reset** reproduced it because
  it changes `roundness`, and every mask key is radius-derived: a whole new generation
  of shapes is inserted on top of the existing set and crosses the cap mid-frame.
  Fix: cold-only eviction + destruction deferred to `beginFrame()`, plus an error-queue
  drain so the latch can only ever fire on our own errors. The latch itself was
  deliberately left intact.
- **Open judgment call:** `permanentlyDisabled` still has no recovery short of a full
  client restart (`shutdown()` clears it, but that only runs on shutdown/resource
  reload). One transient failure anywhere costs glass for the rest of the session. A
  world-load/leave reset is the obvious candidate; it was NOT implemented unilaterally
  because re-arming a latch that exists to stop a misbehaving GL call from corrupting
  renderer state trades a known-safe degraded mode for a possible crash loop. Decide
  before adding one.

**Deliberate reverts (don't "restore" casually)**
- Hitbox positional smoothing: `HitboxPositionSmoother` exists but `HitboxRenderer`
  deliberately renders at vanilla interpolation — the smoother desynced boxes from models.
  `ThrottleDetector.sample()` still runs; its output now only scales entity-model smoothing.
- `WindowMixin` / `RenderTargetMixin` are registered but intentionally EMPTY (abandoned FSR
  idea; 1.21.11 GPU API rewrite crash) — kept for config compatibility.
- `MixinGuiGraphics` is an unregistered stub (superseded by `MixinFont`).

**Dead code / drift hazards**
- `MultiplayerScreenMixin` was deleted 2026-09-05 (abandoned refresh-all feature; never
  registered), and `MultiplayerServerListWidgetMixin` (numeric ping on server list rows)
  was registered the same day — the former "complete but not listed in aurora.mixins.json"
  pair no longer exists.
- `timeChangerEnabled` + `TimeOfDayPreset` in config: no consumers.
- `Feature.enabledByDefault()`: never read. `AutoSprintFeature`: dormant stub.
  `FpsDisplayFeature`: no-op marker (FPS lives in Info HUD).
- `ColorEntryHelper.addPickerButton`: builds nothing (stub).
- `module/ModuleManager` (35 hardcoded cards; count-matched with `FeatureRegistry` since
  the missing `reflex` card landed 2026-09-05, audit B5; `better_hitreg` added 2026-09-08) —
  ids here still silently fail on typos. Consider deriving one from the other someday.
- ComplianceMode's `hitboxFeatureEnabled` config field is an orphan (force-false path
  writes fields renderers don't read for that one).
- Four different animation helpers (`util/AnimationCurves`, `util/AuroraAnim`,
  `util/HoverAnim`, `ui/util/Animation`) — pick the right one per context.
- `AuroraTheme`'s `IOS_*`/`ACCENT_*` names hold OnePlus Red (§5).
- Naming collision: `module/ModuleManager` (settings grid) vs `hud/module/HudModuleManager`
  (draggable HUD panels) are unrelated.

**Hygiene**
- The former "vendored BetterHitreg has no license/attribution headers — do not
  redistribute" flag is **resolved** (2026-09-08): Jass granted explicit permission for the
  integration, the code is integrated (not vendored), and credit is preserved in-file on
  every moved class, in `hitreg/BetterHitreg.java`'s notes, and as the detail screen's
  subtitle. Still open and unrelated: repo LICENSE (CC0) ≠ fabric.mod.json (`MIT`).
- Better Hitreg's card icon is the distinct `swords` glyph (U+F889, resolved 2026-09-08;
  it previously reused Crosshair's `gps_fixed`). The subset was regenerated from the real
  Material Symbols Rounded variable font placed at root-level `full_material.ttf`
  (gitignored by design; if missing, re-download
  `variablefont/MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf` from
  google/material-design-icons and run `subset_script.py`'s fontTools command — the script
  itself still hardcodes a Windows path). The bundled `material_symbols_rounded.ttf`
  remains a subset of exactly the `FeatureIcons` codepoints.
- `assets/aurora/textures/gui/module_icons/` ships two dev scripts + `settings.svg`;
  root-level `inspect_font3.py` has a hardcoded Windows path; font `license.txt` covers a
  removed font (Source Sans Pro).
- No TODO/FIXME comments exist in `com/aurora` — intent lives in long javadocs. Read them;
  they record 1.21.11 API renames (`setupFog`, `swingArm`, `EntityHitboxDebugRenderer.
  emitGizmos`, …) and the reasons behind the conventions above.

---

## 10. Quick orientation for a new session

- To add a **feature toggle**: config boolean field → `FeatureRegistry` tile (+ optional
  `FeatureManager` Feature) → settings widgets → mixin/renderer reads the field per tick.
  Remember `resetByPrefix` naming and a ModuleManager card if it should appear in the grid.
- To theme a new screen: implement `ThemedScreen`, use `ui/component` widgets +
  `ThemeManager.color(token)` (or `AuroraTheme.*` statics), radius from
  `ThemeManager.current().roundness()`.
- To add **glass** to a surface: follow §6 conventions exactly — pre-dim pass, world-gate,
  flat fallback, `WINDOW_FILL` tint only, raised vs depressed, stained only for
  selected/primary, and update the rollout table in §6.
- To debug glass: `BlurTestScreen` (bind `blur_test`), `[S]` crash canary, renderer logs
  `[BlurPanel]` decline reasons (`lastOutcome`).
- **Before calling any UI or feature change complete, check whether it could scale badly.**
  Widget-cache reconciliation patterns, per-frame allocations, anything that could be O(n²)
  or worse — especially on screens whose lists can grow large (Profiles, Waypoints, the
  Modules grid, anything similar). This is a standing rule, not a one-off instruction: a
  real O(rows²) widget-cache reconciliation on the Profile/Waypoint manager screens (fixed
  in `0a0caaa`) shipped invisibly since the 2026-09-05 glass rollout because no one had
  exercised a large list. Reconcile per-row caches against a `Set`, not a `List`; fetch
  lists once per frame; memoize per-frame text fitting; and when you find a pattern like
  this, fix it proactively instead of waiting for it to be reported as user-visible lag.
- **Count shape-fill volume, not just algorithmic complexity.** MC 1.21.11's
  `GuiRenderState` runs an intersection search over every element recorded SO FAR for each
  submitted `GuiGraphics.fill`, so N disjoint fills per frame — exactly what the AA
  rounded-rect strip fills produce — cost O(N²) at record time (~29k fills/frame = 2 fps;
  ~2k = imperceptible). One list row's static shapes (six shadow-ring outlines + tint
  fills) are ~1k fills, so a screen re-submitting them live per row is the same bug class
  as the O(rows²) reconciliation above — that was audit P2, fixed 2026-09-08: Waypoints
  with 30 rows went 2 fps / ~29.3k fills → 28-47 fps / 19 fills (Profiles 2-4 → 41-53,
  pack browser 3-7 → 35-49, picker 33-43 → 73-103) via the manager screens' shared row
  TEMPLATE (`ManagerListScreen.paintTemplatedRowSurface`: raster once via
  `RenderUtil.beginCapture`, `DISCARD_SINK`-wrap the live surface pass, blit per row so
  scrolling never re-rasterizes), per-button resting-surface templates inside the shared
  `Button`, a per-color template in `ColorSwatch`, a card template on the pack browser
  grid, and per-surface caches on the color picker (R8). Measure with
  `RenderUtil.fillsSubmitted()`/`[ui-perf]` before/after any shape-heavy UI change; static
  per-row-identical shapes belong in a template raster (`UiLayerCache.blitAt`) or the
  full-screen layer cache, never re-submitted live.
