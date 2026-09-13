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
| Other deps | None at runtime (the former Cloth Config `modApi` was dropped 2026-09-11 — its only consumer was `ColorEntryHelper`'s dead Cloth Config half); ModMenu in `suggests` only |
| Entrypoints | `com.aurora.client.AuroraClient` (Better Hitreg is wired from it via `hitreg/BetterHitreg.initialize()`) |
| Mixin configs | `aurora.mixins.json` only (~70 `client` entries, of which 13 are `hitreg.*`) |
| Run dir | `run/` at repo root is a live dev client dir (`run/config/aurora.json`, `run/config/aurora-worldmap/`, `run/config/profiles/`) |

**Version metadata (aligned 2026-09-11):** the former drift is resolved — `gradle.properties`,
`fabric.mod.json` (`minecraft: "~1.21.11"`; verified against the Loader 0.19.2 jar to mean
`>=1.21.11 <1.22.0`), and `build.gradle`'s run-config `-Dfabric.modVersion.minecraft`
(no code reads it; the dev sandbox skips Loader's dependency check anyway) all declare
1.21.11.
(The former LICENSE-CC0-vs-fabric.mod.json-MIT drift was resolved 2026-09-11 — the
repo `LICENSE` is now the standard MIT text, per user decision.)

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
│   └── impl/              26 runtime Feature singletons + helper classes
│                           (ThrottleDetector, EntityMovementSmoother, …).
├── module/                PRESENTATION-ONLY view-models for the settings grid
│                           (Module, ModuleManager). NOT runtime logic. Hardcoded
│                           list of 37 ids — can drift from FeatureRegistry (§3).
├── hud/                   HUD layer: HudRenderer (top-level callback), HudAnchor,
│                           CrosshairRenderer, HitboxRenderer, BlockOverlayRenderer,
│                           WorldLineRenderer (shared thick lines), WaypointRenderer,
│                           SaturationOverlay (AppleSkin port), AlertManager/AlertRenderer,
│                           CpsTracker, HudBackgrounds is in util/.
│   ├── module/            13 draggable HUD modules + HudModule base + HudModuleManager.
│   └── preview/           Container-preview tooltip components (shulker/ender chest).
├── screen/                Aurora's own Screens + the feature UI registry:
│   │                      AuroraScreen (main settings screen), AuroraTitleScreen,
│   │                      FeatureDetailScreen (per-feature settings list; carries the
│   │                      design-language title + attached-credit-subtitle chrome,
│   │                      DESIGN_LANGUAGE.md §1), HudEditorScreen, WaypointManagerScreen,
│   │                      ProfileManagerScreen, ColorPickerScreen,
│   │                      ResourcePackBrowserScreen (Modrinth browser), ManagerListScreen
│   │                      (shared Profile/Waypoint list-screen foundation, R3: frame
│   │                      skeleton, scroll+thumb, rename editor, toolbar — the two
│   │                      manager screens extend it, keeping only row content/actions),
│   │                      FeatureRegistry
│   │                      (UI metadata + settings widgets), FeatureIcons,
│   │                      FeatureMetadata, ModuleAccentColors,
│   │                      AuroraModMenuApi.
│   └── setting/           ~24 FeatureSetting widget types (the design-language §4
│                           `valueLine` live-state subtitle lives on the FeatureSetting
│                           base, opt-in — BooleanSetting is its only user so far;
│                           EnumSetting, KeybindSetting, KeyListSetting, sliders, color
│                           pickers, PixelCanvasSetting for the custom crosshair,
│                           ParticleConfigSetting, ThemePreview, SectionFooterSetting —
│                           the design language's §3 group footer…).
├── theme/                 THEME ENGINE (see §5): ThemeManager, ThemeResolver,
│                          PaletteEngine, ResolvedTheme, ThemeToken (enum), ThemeDefinition,
│                          ThemeMode, ThemeRoundness, ThemePresets, ThemeMigrator,
│                          HudStatus + HudText (HUD color policy: fixed-hue status
│                          palette; follow-accent text-color sentinel).
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
font + JSON providers), `assets/aurora/lang/en_us.json` (only
localization), `assets/minecraft/models/item/totem_of_undying.json` (fixes totem
orientation in item frames). Root-level `subset_script.py` regenerates the icon font
subset. (The former `module_icons/` PNG set + stray design-source SVGs + the stale
`inspect_font3.py` were removed by the 2026-09-11 dead-code sweep, audit D9.)

---

## 3. How the feature system works — three parallel registries

There is **no single registry**. Three structures must stay conceptually in sync:

1. **`feature/FeatureManager`** — 26 long-lived `Feature` singletons with
   `onRegister()`/`onTick(Minecraft)` (interface `feature/Feature.java`).
   (The never-read `Feature.enabledByDefault()` was removed 2026-09-11, audit D9.)
2. **`screen/FeatureRegistry`** — static UI metadata: **37 MODULES-tab + 4 SETTINGS-tab
   tiles** (`FeatureMetadata`: id, display name, marketing description, enable
   getter/setter, list of `FeatureSetting` widgets, `reset()`).
3. **`module/ModuleManager`** — 37 hardcoded grid cards consumed by `AuroraScreen`.
   A typo'd id here silently returns null metadata. (The missing `reflex` card landed
   2026-09-05, audit B5 — counts now match, but the list is still maintained by hand.)

**Critical rule:** the *enabled state of every feature is a public boolean field on
`AuroraConfig`* (e.g. `zoomEnabled`), read fresh each tick — never a flag on the Feature
object. `FeatureMetadata` wraps `() -> cfg.xEnabled` / `v -> cfg.xEnabled = v`. Per-feature
"Reset to defaults" works via `AuroraConfig.resetByPrefix("zoom")` (reflection over a
`DEFAULTS` snapshot taken at class init).

Several shipped features have **no Feature object at all** (pure mixin + config field):
No Fog, Hit Color, Glint Color, Item Scale, Resourcepack Browser, Reflex, Hotbar Bounce,
Keystrokes, Better Hitreg (its own tick/render/HUD hooks are registered by `hitreg/BetterHitreg`).

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

### MODULES tab (37 tiles) — behavior + where the code lives

| Feature (id) | What it does | Implementation |
|---|---|---|
| World Map (`world_map`) | Fullscreen pannable map; chunks captured in background (budgeted), stitched into persistent 512×512 region tiles that survive restarts; per-dimension browsing; waypoint creation on click | `feature/impl/WorldMapFeature`, `worldmap/*` (§2), mixin `ClientLevelWorldMapMixin` |
| Theme (`theme`) — **moved to the SETTINGS tab 2026-09-09** | Single-accent theming engine (§5); presentation unchanged — Settings-tab header + toggle + inline settings, detail screen reachable via header click | `theme/*`, `feature/impl/ThemeFeature` (per-tick `ThemeManager.sync()`) |
| Zoom (`zoom`) | Hold-C FOV zoom with easing; scroll adjusts level (max 8×); sensitivity scaled inversely; all scrolling consumed while zoomed | `ZoomFeature`, `GameRendererMixin` (FOV), `MouseMixin` (scroll) |
| Full Bright (`full_bright`) | Gamma 100–1500% via private backing value; saves/restores user gamma, re-asserts each tick | `FullBrightFeature`, `SimpleOptionMixin` (`@Accessor` force-set) |
| No Fog (`no_fog`) | Multiplies atmospheric fog distance 1–100× | `NoFogMixin` only (no Feature object) |
| Pack Tweaks (`pack_tweaks`) | Bundle: totem particle size 25–100%; first-person shield styles (VANILLA/LOWERED/SIDE/COMPACT); shield-status tints; water & lava clarity fog; hide lava orange overlay | `TotemParticleMixin`, `HeldItemRendererTweaksMixin`, `UnderwaterClarityMixin`, `LavaClarityMixin`, `LavaOverlayMixin` |
| Entity Health (`player_health`) | HP above nearby entities (number or heart row); "only attacked" mode | `PlayerHealthLabelMixin` + `LivingEntityRendererExtractMixin` + `ClientPlayerAttackMixin` + `util/AttackedPlayerTracker` + `util/AuroraHealthSnapshots` |
| FreeLook (`free_look`) | Hold-V detached camera (3rd-person forced); adapted from Freelook++ (MIT, credited in-file) | `FreeLookFeature`, `EntityMixin`, `CameraUpdateMixin` |
| Saturation Bar (`saturation_bar`) | Hidden saturation pips over hunger bar; AppleSkin port (Unlicense, credited) | `hud/SaturationOverlay`, `InGameHudFoodMixin` |
| Block Overlay (`block_overlay`) | Custom block-selection outline (color, 1–6px width, see-through, face fill; SOLID or RAINBOW) | `hud/BlockOverlayRenderer` (Fabric `BEFORE_BLOCK_OUTLINE`), `BlockOverlayFeature`, `VertexRenderingMixin` (vanilla outline) |
| Toggle Sprint/Sneak (`toggle_sprint_sneak`) | Press-once sprint/sneak; holds vanilla keys down each tick; 3 HUD display modes | `ToggleSprintFeature`, `hud/module/ToggleSprintSneakModule` + `ToggleIndividualModule`×2 |
| Alerts (`alerts`) | Popup warnings: low durability (per-slot edge), low hunger, effect expiry; optional sound + test. Effect expiry has **per-effect granularity** (2026-09-12, rebuilt same day): a "Per-Effect Alerts" section following the `ItemScaleSetting` search-then-add pattern — search field + "+" add button suggesting one matching effect (sprite + "Add: <name>", ItemScale's three match tiers over the effect registry), adding to the curated inclusion list `AuroraConfig.effectExpiryIncludedEffects` (registry-id keys, present = alerts, empty = nothing alerts — the fresh-install default); added rows render alphabetically as `EffectRowSetting` (38px ItemScale row geometry, `mob_effect/` sprite, trash "x" remove), so only curated effects render rather than all ~40 every frame. Migration: `config/EffectExpiryMigrator.runOnce()` (HitregMigrator shape — one-shot, guarded by `migratedEffectExpiryExclusions`, profile-EXCLUDED, called from `AuroraClient` after the profile apply) flips the one-version legacy exclusion set into registry-minus-exclusions; `AuroraConfig.loadedFromDisk()` (set in `load()` — the file-existence probe can't be deferred because load() creates the file on fresh installs) keeps genuinely fresh configs at an empty list. The section's master toggle gates everything, Low-Latency style (behavioral, not visual dimming). Reset prefix `effectExpiry` covers the list; the two reflection reset paths (`resetByPrefix`, `ProfileManager.applyProfile`) defensively copy mutable collection defaults since this list arrived | `ArmorAlertFeature` + `StatusAlertFeature` → `hud/AlertManager`/`AlertRenderer`, `screen/setting/EffectRowSetting` + `EffectExpiryListSetting` (the Particles list remains on the shared `SearchListSetting`) |
| Crosshair (`crosshair`) | Preset or CUSTOM painted crosshair with a **free-form canvas** (any W×H up to 128; dims live in `crosshairCustom{Width,Height}` + flat `boolean[]` pixels, resolved via `util/GridDims`); indicator crosshair when entity attackable; deliberate half-pixel centering fix. Canvas editor renders through a cached `DynamicTexture` (`ui/util/CanvasTexture` — one blit/frame, re-raster only on edit; replaced a per-cell fill loop that cost ~12.8 ms/frame at 33×33), HUD path merges lit cells into run-length fills, and growing the grid first runs a **measured** cost benchmark on the player's machine (`[canvas-cost]` log) with an apply-anyway warning — never hardware-name heuristics | `hud/CrosshairRenderer`, `PixelCanvasSetting`, `CanvasTexture`, `InGameHudMixin` (vanilla suppression) |
| Hitbox (`hitbox`) | Custom entity hitboxes (self/target colors, eye-line, look line, width, see-through). Renders at plain vanilla interpolation — the smoother was **deliberately reverted** (desynced from model) | `hud/HitboxRenderer` (AFTER_ENTITIES) + `WorldLineRenderer`, `HitboxFeature`, `EntityRenderDispatcherMixin` |
| Hit Color (`hit_color`) | Recolors hurt flash (port of harimasa/HitColor, MIT, credited) | `MixinOverlayTexture`, `EquipmentLayerRendererMixin`, `util/OverlayReloadListener` |
| Glint Color (`glint_color`) — **added 2026-09-13, mirrors Hit Color's structure** | Recolors the enchantment glint (held/GUI/dropped items + worn armor) by tinting the two glint textures vanilla's four glint render types sample (`enchanted_glint_item.png`/`enchanted_glint_armor.png`, ids read from `ItemRenderer.<clinit>` bytecode). 1.21.11's glint is NOT a HitColor-style flat overlay: it's a scrolling texture pattern blended additively (`BlendFunction.GLINT` = `SRC_COLOR,ONE`) on the `pipeline/glint` shader, so the color lives in the texture pixels — recolor = **colorize** (2026-09-13 same-day fix: the original per-channel multiply could only dim channels, so any target far from the source's dim purple `0x4b237b` read weak — green capped at 35/255, white was literal identity): each pixel's intensity (`max(R,G,B)`, HSV value — weighted luma's green-dominant weights would re-dim the R/B-heavy purple) drives the picker color at full strength, preserving the shimmer's value ramp and near-black background (alpha untouched; picker alpha scales strength), applied at HEAD of `ReloadableTexture.apply` (the last CPU-side moment before upload+close; `SimpleTexture.loadContents` couldn't host the hook because Mixin can't shadow its inherited `resourceId()`). **No disabled-state constant exists at all** — the d84298c byte-swap bug class is structurally eliminated: off = vanilla's own untouched file load. Mid-session toggle/color changes force a synchronous reload of exactly the two textures via `TextureManager.release`+`getTexture` from END_CLIENT_TICK (`util/GlintColor.tick`, HitColor's change-detection pattern; between frames per §6 convention 10, skipped while a resource reload is in flight). Settings mirror Hit Color exactly: master toggle + `ColorSetting` + "Tint Armor" sub-toggle (`glintColorEnabled`/`glintColor`/`glintColorTintArmor`; reset prefix `glintColor`) | `mixin/ReloadableTextureMixin`, `util/GlintColor`, END_CLIENT_TICK hook in `AuroraClient` |
| Better Hitreg (`better_hitreg`) | BetterHitreg by Jass, integrated with permission (credited in-file + screen subtitle). Client-side hit feedback: on your swing the target's hurt animation, the correct attack sound and crit/sharpness particles play locally after `hitregDelayMs` (0 = next frame) while the server's late copy is cancelled (`ServerMixin`/`NetworkMixin`→`DontAnimate` marker→`DamageMixin`); "Safe Regs Only"/shield rules; ghost + misplace detection over a rolling 100-hit window (surfaced as live value lines under the Alert Delays/Ghosts/Misplaces toggles via `BooleanSetting.valueLine`, plus tooltips; "Reset Tracked Stats" isolated in a trailing Maintenance section per DESIGN_LANGUAGE §7.3); audio (mute other fights/self/them/non-hits, 1.8 sounds, OpenAL EFX muffle/sharpen via `SourceMixin`, metronome); render (hide other fights/animations/armor/particles, target + server hitbox, target cross, reach + jump rings, perfect-hit / jump-reset flash); practice arena (Unrender World via `ChunkMixin`, solid floor, floor grid); 19 ARGB overlay colors; six keybinds incl. the practice scoreboard. Fight tracking feeds the Stats Overlay (`Settings.addFight`). No chat/alert output at all (removed at integration). Card toggle = `hitregEnabled` master (ANDed into every `Toggle.toggled()` read); "Custom Hitreg" inside is upstream's own switch | `hitreg/*` (§2), `mixin/hitreg/*` (13), `AuroraConfig.hitreg*` (Reset prefix `hitreg`) |
| Info HUD (`info_module`) | Corner readout, 13 individually toggleable rows (FPS/XYZ/time/facing/biome/light/memory/ping/CPS/playtime…); default background is the theme-derived `AURORA` gradient (`HUD_BACKDROP_*`, R6 P2 — was `NONE`); text follows the theme accent by default (shared `hudColor` sentinel, R6 ext — `theme/HudText`) | `hud/module/InfoModule`, `PlaytimeFeature` (per-world buckets) |
| CPS (`cps`) | L/R clicks-per-second; counts from raw GLFW callback (polling caps at 20) | `CpsModule`, `CpsTracker`, `ClickTrackerFeature`, `MouseClickTrackerMixin` |
| Armor HUD (`armor_hud`) | 4 pieces + durability text/bar, horizontal/vertical, VANILLA slot background | `hud/module/ArmorModule` |
| Reach Display (`reach_display`) | Last attack distance; holds value through smoothstep fade. Polls attack key — no mixin | `ReachModule`, `ReachTrackerFeature` |
| Potion HUD (`potion_hud`) | Replaces vanilla status strip: icon/name/Roman level/countdown rows; panel fades after expiry | `PotionModule`, `InGameHudPotionOverlayMixin` |
| Ping (`ping`) | Corner ping HUD + numeric ping in tab list + colored ping under nametags (one toggle, three sub-flags) | `PingModule`, `PlayerListHudMixin`, `PlayerEntityRendererMixin` (also appends totem pops to nametags) |
| Totem Pop Counter (`totem_pop`) | Your (and optionally others') totem activations; reset keybind; nametag counts | `TotemPopFeature`, `ClientPacketListenerEntityEventMixin`, `TotemPopModule` |
| Stats Overlay (`stats`) | Session kills/deaths/K/D/time (kills heuristic: strike → 4s death window) **plus Better Hitreg's fight stats**: Fights (session + lifetime total), Fight Time (session + lifetime), Last Fight (duration + both accuracies). Lifetime totals persist in `fightStatsTotalFights`/`fightStatsPlaytimeSeconds` (profile-excluded); session values reset with "Reset Stats"; lifetime totals only via the separate "Reset Lifetime Fight Totals" button | `StatsTrackerFeature` (`recordFight`), `StatsModule`, fed by `hitreg/settings/Settings.addFight` |
| Waypoints (`waypoints`) | Per-world persistent markers: beacon beam and/or highlight slab + billboard labels; auto death waypoints (replace-previous or capped) | `WaypointFeature`, `hud/WaypointRenderer` (two passes), `WaypointManagerScreen` |
| Minimap (`minimap`) | HUD minimap; rotation baked into the sampling pass (cheap); biome tint, hillshade, depth water; waypoint/entity dots, compass; can read World Map's region cache instead of live chunks. Frame ring + compass letters = chrome, follow the theme accent via `minimapBorderColor`'s `0` sentinel (`HudText.color()`); terrain/biome tints = data, never themed | `hud/module/MinimapModule` (805 lines), `MinimapFeature`, `worldmap/WorldMapClient.sampleSurfaceAbgr` |
| Container Preview (`container_preview`) | Tooltip grid for shulker contents + ender chest (snapshot while chest screen open — 1.21.x limitation) | `ItemTooltipImageMixin`, `ItemContainerContentsTooltipMixin`, `hud/preview/*` |
| Item Physics (`item_physics`) | Dropped items lie flat, tumble by motion | `ItemEntityRendererExtractMixin` + `ItemEntityRendererSubmitMixin` + `util/AuroraItemPhysicsSnapshots` |
| Particles (`particles`) | Per-particle-type visibility/scale/ARGB tint with search. Visibility gated at HEAD of `createParticle` (RETURN is too late) | `ParticleControlFeature`, `ParticleEngineMixin`, `ParticleAccessor` |
| Item Scale (`item_scale`) | Per-item held scale/rotation/translation; per-hand defaults | `HeldItemRendererTweaksMixin`, `setting/ItemScaleSetting`, `ui/util/ItemSpriteRenderer` + `mixin/ItemStackRenderStateAccessor` |
| Held Item Seam Fix (`held_item_seams`) | Hides the hairline texture-bleed seams between first-person held-item faces (visible at some camera angles, worse with hi-res packs) via a uniform sub-pixel scale-up — default 1.001 (0.1%), "Fix Strength" slider 0–50 per-mille, mixin clamps 1.0–1.05 | `HeldItemSeamFixMixin` only (no Feature object); written in the initial commit but inert until 2026-09-11 — see §8 |
| Resourcepack Browser (`resourcepack_browser`) | Modrinth search/install for resource packs into `resourcepacks/` (never auto-enables) | `ResourcePackBrowserScreen`, `modrinth/ModrinthApi`, `modrinth/PackIconCache` |
| Minecraft Reflex (`reflex`) | Reflex-style latency reduction: GL timer-query GPU time + EWMA CPU frame time → hold CPU before input sampling; render hooks exception-safe (2026-09-13, §8): an interrupted query pair drops that frame's GPU sample instead of throwing, and any unexpected hook exception disables Reflex for the session with one ERROR log rather than propagating into vanilla's render/disconnect paths (the real disconnect crash) | `ReflexMinecraftMixin` → `ReflexScheduler.beginFrame/beforeFlush/endFrame` (all guarded), `util/reflex/*` |
| Animations (`animations`) | Swing curve + 1.8 swing arc, view-bob curve/amplitude, 1.7/1.8 damage tilt, idle held-item sway, frame-rate-independent entity movement smoothing (tau scales with server packet bundling) | `HeldItemRendererMixin`, `GameRendererBobMixin`, `DamageTiltMixin`, `LivingEntityRendererExtractMixin` + `EntityMovementSmoother`, `util/AnimationCurves`, cross-cutting `ThrottleDetector` |
| Hotbar Bounce (`hotbar_bounce`) | White pulse outline on hotbar slot when stack count grows | `HotbarItemBounceMixin` → `HotbarBounceTracker` |
| Keystrokes (`keystrokes`) | Key-panel overlay: WASD/mouse/CPS/space/sneak/sprint + up to 12 custom keys; pressed-key accent follows the theme accent by default (`keystrokesAccentColor == 0`, R6 P2; explicit color overrides); key labels follow the shared `hudColor` sentinel (R6 ext) | `hud/module/KeystrokesModule` |
| Miscellaneous (`miscellaneous`) — **moved to the MODULES grid 2026-09-10** | The 2026-09-09 consolidation of the tiles that used to sit below Interface (Smooth Camera, Frame Pacer, Low Latency, Tick Sync, Decoupled Input, Drag-to-Reorder Servers, Compliance Mode, Accessibility — Compliance/Accessibility removed entirely 2026-09-12, **Tick Sync removed entirely 2026-09-13**, see §8) behind one tile + detail screen; grid right-click opens the same detail screen as every other tile — an explicit "for now" grouping, not a taxonomy decision. The Low Latency section was audited 2026-09-11 (§8): the dead Zero-Latency Camera and High-Frequency Input rows are gone, Adaptive Render Sleeping is genuinely gated on the section's Enabled toggle, and descriptions match verified behavior. **Disable VSync's fullscreen-transition hole (F11 silently re-enabling vsync behind the toggle, wedged until cycled off/on) was fixed 2026-09-13 — §8.** A **Frame Cap section was added 2026-09-13** (between Frame Pacer and Low Latency): the global `frameCapFps` (0 = Off, 10–2000) enforced at the top of each frame in `MinecraftClientRenderMixin` — see §8's 2026-09-13 entry for why it lives there and how it composes with every other limiter | `FeatureRegistry` MODULES entry (byte-verbatim settings + unified reset), `ModuleManager` grid card, "?" (`question_mark` U+EB8B) icon |

### SETTINGS tab (4 tiles)

Custom Title (`custom_title` — themed title screen: vanilla panorama + 5 glass buttons
(§6 rollout table; the custom logo/starfield blits were removed 2026-09-08) via
`TitleScreenMixin`/`AuroraTitleScreen`; themed vanilla buttons + search field on
multiplayer/world-select via `AbstractButtonMixin`/`EditBoxMixin` — the starfield
background those screens carried until 2026-09-12 (`SelectionScreenBackgroundMixin`)
is gone; they show the vanilla backdrop),
Text & Fonts (`text_fonts` —
bundled Google fonts scoped OFF/Aurora-only/ALL via `MixinFont` + `AuroraFontRenderer`),
Theme (`theme` — moved here from the Modules tab 2026-09-09; the single-accent theming
engine of §5 with all its settings and its detail screen unchanged, reached from this
tab instead of the grid), and Interface (`interface` — FPS cap for Aurora screens).

Settings-tab presentation: every entry renders as header + toggle (+ inline settings
rows); a header click opens the entry's detail screen (chevron affordance;
`FeatureMetadata.settingsDetailOnly` opts an entry out of inline rows entirely —
no entry sets it since Miscellaneous moved to the Modules grid 2026-09-10, but the
machinery stays; see §9). Entries separate at `SETTINGS_ENTRY_GAP` (16px, the
design language's §6 group rhythm — render, scroll-height, and click walks share
the constant; `385e704`).

### Cross-cutting systems worth knowing

- **`ThrottleDetector`**: measures server movement-packet bundling (median inter-move tick
  gap, 32 samples) → factor 1–6. Feeds `EntityMovementSmoother` only. Was also feeding
  hitbox smoothing, which was reverted (§9).
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
`HUD_BACKDROP_TOP/BOT` tokens (the audit's "exist unused" was stale), and — R6 Part 2
(2026-09-08), the accent-theming pilot — Keystrokes' pressed-key highlight defaults to
following the theme accent (`keystrokesAccentColor == 0` → `ThemeManager.color(ACCENT)`; any
explicit color overrides; the pre-R6 hardcoded azure `0xFF30A5FF` is gone) and the Info
HUD's default background mode is `AURORA` (was `NONE`), so the corner readout's panel is the
theme-derived HUD_BACKDROP gradient out of the box. Both stay never-glass — color-token
wiring only.

**HUD informational text — the `hudColor` sentinel, `theme/HudText`** (R6 extension,
2026-09-08): the shared `AuroraConfig.hudColor` field read by every plain-readout HUD
module — Info, CPS, Stats, Totem Pops, Reach, Armor durability text, Ping's
unknown-latency fallback ("Ping: —"), and Keystrokes key labels (8 modules, 10 read
sites) — now defaults to `0`, a follow-accent sentinel resolved through
`HudText.color()` → `ThemeManager.color(ACCENT)`, the same resolution the Part 2 pilot
gave `keystrokesAccentColor`. Any non-zero ARGB wins verbatim, including legacy configs
that persisted the old white default `-1` — existing users see no change until they
reset the field; the only reinterpreted value is a literal `0`, which previously drew
fully-transparent (invisible) text. Keystrokes' key labels are part of the family (its
old `c == 0 → white` guard was dead code); the pressed-key FILL stays the independent
`keystrokesAccentColor`, so override + accent coexist on one panel. Deliberately NOT
`HudStatus` (that palette is fixed-hue, never accent); both live in `theme/` as the
HUD color policy pair. Verified by DevPilot `p3*` boots: sentinel text tracks the
accent (OnePlus Red / Blue preset) across all 8 modules, explicit white overrides in
every one of them while keystrokes fills + the Info panel's AURORA backdrop still
track the theme, a key-absent config resolves to the accent, and the fixed-hue
elements (HudStatus alert card, armor durability bars, minimap entity dots) are
byte-identical across accents. The remaining hard calls stay deferred: Minimap
(terrain/biome tint), Crosshair/Hitbox/BlockOverlay, SaturationOverlay,
Armor's durability bar, `AuroraTitleScreen` — each has
data-vs-chrome or fixed-hue questions the pilot sessions deferred on purpose.
(`WorldMapScreen` left this list 2026-09-08: its chrome got the chrome-only glass
treatment — §6 table — and its map content/readouts were ruled content, hardcoded
neutrals over map data.) (Minimap's frame ring also left the list 2026-09-11, user
ruling: the ring/frame and compass letters are CHROME and now follow the theme
accent via `minimapBorderColor`'s `0` follow-accent sentinel — the same
`HudText.color()` resolution as `hudColor`, default flipped black→0 so fresh
configs get the accent while legacy persisted colors win verbatim; the compass
letters (previously hardcoded white) take the same resolved color so ring +
compass move as one chrome unit. Terrain/biome tints stay DATA — real world
colors, untouched — and entity dots/waypoint colors stay pinned by the original
R6 pass. Verified by p3red/p3blue boots: ring + compass track the accent, map
interior byte-identical across accents.)

Landed 2026-09-08 after that: **glass on the title screen, over the vanilla panorama**
(two commits — mechanism, then adoption). Investigation first established what the
panorama IS at the GL level on 1.21.11: `Screen.renderPanorama` → `CubeMap.render` is
an *eager* Blaze3D render pass straight into `getMainRenderTarget()`'s color texture
(not a recorded/deferred `GuiGraphics` command), i.e. the exact texture the glass
pipeline's world reader wraps — so the panorama is capturable by the *existing*
capture path the moment `renderPanorama` returns, and the only blocker was the
menu-context guard. The mechanism (`BlurPanelRenderer`):
`noteMenuBackdropDrawn()` / `menuBackdropValid()` — a declaration stamped with the
current `poolEpoch` that the guard (and `GlassSurface.paint`'s capture-validity gate,
which previously short-circuited on `liveWorldBackdrop()` alone) reads as its one
exemption: `mc.level == null && !menuBackdropValid()`. Frame-scoped by construction
(expires at the next `beginFrame()`), declaration-not-detection (no content probe;
only the code that just drew a full-viewport backdrop can say so), single-observer
(one screen renders per frame) — see §6 convention 5 for the full reasoning. The
adoption (`AuroraTitleScreen`): the custom logo/backdrop/starfield blits and their
PNGs are gone (`logo.png`, `logo_backdrop.png` deleted; `title_background.png` was
retained for `SelectionScreenBackgroundMixin` until that starfield mixin itself was
removed 2026-09-12, taking the PNG with it), the screen calls `renderPanorama`
+ `noteMenuBackdropDrawn()` and renders its 5 buttons as the standard glass
`ButtonWidget`s (4 neutral, Aurora Settings stained), chrome-only depth, stack
re-centered now that the logo block is gone. Verified by five DevPilot `title`-mode
boots (all clean exits, no `[BlurPanel]` errors): GlassStats shows exactly 5
CONTROL panels/frame on the title screen (~1.6–2.5 ms/frame, 0 declines); the
world-capture probe read real panorama pixels (non-black RGB) from the main target;
the F2 interlock observed directly (`lastOutcome='suppressed (screenshot in
flight)'` through the real `Screenshot.takeScreenshot` path while panels were
active); and the guard was tested DIRECTLY, not by inspection — a harness probe
calling `renderPanel` mid-menu declined `'no level (menu context)'` both on the
title screen sampled between frames (proving the stamp does not outlive its frame)
and on `AuroraScreen` opened from the title (where the vanilla panorama genuinely
IS in the main target via its `renderBackground`, yet non-declaring screens still
decline — confirmed quantitatively by zero glass panels across ~9 s there and
pixel-uniform flat tile fills in the capture).

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
**The capture downsample is TWO ~2× `GL_LINEAR` blit steps through a half-res
intermediate, never one 4× blit** (2026-09-09, the shimmer fix). A single 4× LINEAR blit
is a point-ish sample — on the dev machine's Mesa driver it reads ~1 of every 16 source
texels (driver-probe measured: bit-identical output under a 1.05 px shift; on other
drivers it is a 2×2 center tap = 4 of 16), so any content moving against the sample grid
(world camera motion, the title panorama, a scrolling panel sliding the grid over a
static world) aliased into low-frequency blotches that changed every frame and that the
chain Gaussian cannot remove — the "flicker/waver across the whole panel on moving
backdrops" bug, measured at 2.3–3.7× the temporal change of a noiseless area-average
reference on every motion scenario (title panorama, camera pan, list scroll; bit-stable
on a frozen world, so the pipeline itself is deterministic). At an exact 2× ratio a
LINEAR blit's sample point lands on texel corners and weights the 4 neighbors equally —
an exact 2×2 box — so two steps are a 4×4 area average (driver-probe verified; the extra
blit costs ~0.005 ms/panel).

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
   complete flat look returns. The ONE exemption (2026-09-08; generalized 2026-09-12): the
   menu-context guard (and
   `GlassSurface.paint`'s capture-validity gate, which co-declines with it) admits a
   caller that has DECLARED a valid menu backdrop for the current frame —
   `BlurPanelRenderer.noteMenuBackdropDrawn()`, reached through the shared
   `GlassSurface.renderMenuPanorama(Screen, GuiGraphics, float)` (draws the panorama,
   then declares; returns false with a live world). Declaring screens: `AuroraTitleScreen`
   since 2026-09-08 (from its `render`) and `AuroraScreen`'s no-world path since 2026-09-12
   (from its `renderBackground` — before that, opening it from the title screen fell back
   flat with no frosted glass at all). On 1.21.11 `CubeMap.render` is an eager Blaze3D pass
   straight into the main target's color texture, so the panorama is genuinely capturable by
   the existing world-reader path. The stamp is frame-scoped (`poolEpoch` — expires at the
   next `beginFrame()`), so it cannot leak to any other screen or frame — whichever screen
   declared; `liveWorldBackdrop()`
   itself is unchanged (its `renderBackground`-skip meaning is a separate question).
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
   **Rollout status (2026-09-11): COMPLETE — every glass screen enforces the
   structural pass.** Enforced on `ProfileManagerScreen`,
   `WaypointManagerScreen`, `FeatureDetailScreen`, `AuroraScreen` and
   `ResourcePackBrowserScreen` (the last two landed back-to-back, closing
   the rollout tracked here since 2026-09-05). The widget side carried the
   load throughout: the four pill widgets
   (`EnumSetting`/`KeybindSetting`/`KeyListSetting`/`ItemScaleSetting`),
   `SegmentedControl` (driven by `SegmentedSetting`), `ButtonSetting`'s
   embedded Button, the settings' search/numeric `EditBox`es,
   `Button`/`ButtonWidget` and `EditBoxMixin` all paint surface in
   `renderGlassPass` while a pass is open (widgets gate on
   `GlassSurface.passOpen()` themselves) and content in render. The two
   sanctioned above-the-dim exemptions (§9): `EnumSetting`'s expanded popup
   (`GlassSurface.aboveDimControl`) and the pack browser's detail modal
   (`GlassSurface.aboveDimContainer` for the panel; a
   `beginAboveDim`/`endAboveDim` zone bracket around its component-driven
   buttons, whose in-place painting the ordering guard exempts by
   declaration).
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
| `AuroraScreen` (main settings) | **Full** | Depressed glass window + raised glass tiles, search chip, profile button, layout buttons; world-gated — and since 2026-09-12 the no-world path draws the shared menu-panorama backdrop + declaration (`GlassSurface.renderMenuPanorama` from `renderBackground`), so opening the screen from the title screen shows real panorama-blurred glass under Frosted instead of the flat fallback (flat look under Wireframe/decline unchanged). **Structural pre-dim pass (2026-09-11)**: `beginGlassPass` → window + chips/Profiles/layout/search field + tiles (tracked scissor, ROW priority) or the Settings tab's inline `renderGlassPass` walk (same scissor) → `overlayDim` → cached blit + content. `UiLayerCache` never holds glass (panels are texture blits bypassing the fill-capture sink); the Theme preview card gained its glass surface here for the first time |
| `FeatureDetailScreen` (all 45 detail views) | **Full** | Retires `GLASS_PILOT_IDS`; depressed glass window + glass Done/Reset for every feature. **Structural pre-dim pass (2026-09-10, the Part B pilot)**: `beginGlassPass` → every surface (window via `GlassSurface.container`, the four pill widgets, segments, `ButtonSetting`, the search/numeric fields, preview card, Done/Reset) → `GlassSurface.overlayDim` → cached blit + content. UiLayerCache unaffected: panels are texture blits that bypass the fill-capture sink, and the cached chrome stays a content layer above the dim. `EnumSetting`'s expanded popup stays above the dim via `GlassSurface.aboveDimControl` |
| `ProfileManagerScreen` | **Full** | Rows are this screen's containers: DEPRESSED neutral glass (`WINDOW_FILL` only; the active-row `stainedTint` read as an accent-tinted container — user-flagged twice). Selection shown solely by the accent Active badge; New Profile/Done/Duplicate/Create all neutral raised |
| Theme screen widgets (`ThemePreviewSetting`, `SegmentedControl`) | **Full** (the original pilot) | Preview card/chips/buttons glass; segments neutral-unselected/stained-selected |
| `EditBoxMixin` search fields (Particles, Item Scale, ResourcePacks, Modules grid, + themed vanilla screens) | **Full** | Raised glass, focus = caret + accent hairline ring, tint constant; flat fallback without a world |
| Setting widgets: `EnumSetting` (button + expanded popup), `KeybindSetting`, `KeyListSetting`, `ItemScaleSetting`, `ButtonSetting`, `SegmentedSetting` | **Full** (defaults flipped opt-in → default-on) | `FeatureRegistry` still contains now-redundant `.glassButton(true)`/`.glassSegments(true)` "pilot" calls (lines ~86, 96, 180, 292) — harmless cleanup candidates |
| `ColorPickerScreen` | **Chrome-only, by design** | Apply = STAINED, Cancel = neutral raised; editing surfaces + hex field deliberately opaque |
| `HudEditorScreen` | **Chrome-only** | Two floating action buttons, neutral raised ("navigation action, not a primary state") |
| `WaypointManagerScreen` | **Full** | Raised glass rows + header/add buttons |
| `ResourcePackBrowserScreen` | **Full** (2026-09-04; cards flat 2026-09-08; structural pass 2026-09-11 — closing the rollout) | Depressed glass sidebar (SURFACE-token tint via `GlassSurface.container`) + detail modal; **cards FLAT by decision (audit R9/B2)** — their opaque hover-lerp tint fully occluded the blur, so per-card glass was pure cost and the main output-pool driver (B1 halved; each card's install button is still a glass `Button`, DETAIL priority, driven in the pass under the tracked grid clip via the shared `forEachVisibleCard` culling walk with `drivenPhase` stamping); active category tab = raised glass whose accent-lerp wash IS its tint (the explicit-tint `GlassSurface.control` overload, evaluated in the pass); the dim runs at this screen's original `0x55` strength through `overlayDim`'s argb overload; the detail modal is the screen's one ABOVE-the-dim layer — panel via `GlassSurface.aboveDimContainer`, its buttons driven inside a `beginAboveDim`/`endAboveDim` zone; inactive tabs stay flat by design — small transient rows inside an already-glass container; install/Retry/Close buttons are the shared `Button` painter (Install = stained primary, Retry = destructive, progress/Done = neutral; **success-green is not expressible through `Button` — mapped to stained/neutral, flagged**); `renderBackground` world-gating; tokenized radii. Thumbnails, toast, scrollbars stay opaque/unchanged per convention |
| `WorldMapScreen` | **Chrome-only** (2026-09-08) | The 3 toolbar buttons + the prompt's Cancel in NEUTRAL raised glass (`ButtonWidget.glassBackground`), prompt Create in STAINED (primary-action convention), and the create-waypoint prompt itself on DEPRESSED `GlassSurface.container` glass (WINDOW priority, `WINDOW_FILL` tint — the pack-browser detail-modal treatment; flat `RoundedPanel` fallback on decline); the name field was already raised glass via `EditBoxMixin`. `renderBackground` world-gate added (skip vanilla backdrop sandwich in-world — also saves its blur post-chain under a viewport the map fills anyway). Map content — void, tiles, waypoint markers, player arrow, bottom readouts — deliberately untouched; the readouts keep hardcoded white/gray because they float over map data where a mode-locked `ON_*` text token could go dark-on-dark in light mode. Glass samples the live backdrop behind the screen, NOT the map tiles: 1.21.11's deferred `GuiRenderState` means tile blits never reach the main target before the blur pass reads it — the same physics every glass surface has (only eager passes, like the title screen's panorama, are capturable) |
| `AuroraTitleScreen` | **Chrome-only** (2026-09-08) | 5 floating glass buttons over the live vanilla panorama (4 neutral raised, Aurora Settings accent-stained — the ColorPicker-Apply convention); custom logo/backdrop/starfield blits and their PNGs removed (`title_background.png` went too when the selection-screen starfield was removed 2026-09-12). The screen calls `renderPanorama` then `BlurPanelRenderer.noteMenuBackdropDrawn()` — the frame-scoped declaration that is the one menu-context-guard exemption (§6 convention 5) — so the buttons blur the panorama through the ordinary world-reader capture; flat fallback whenever glass declines |
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

Landed 2026-09-08 after that (R6 extension of the Part 2 pilot): the shared
`AuroraConfig.hudColor` field gained the follow-accent sentinel (default `0` →
`theme/HudText.color()` → `ACCENT`; any non-zero value, including the pre-sentinel
persisted white `-1`, wins verbatim). Its 8 consumer modules — Info, CPS, Stats, Totem
Pops, Reach, Armor durability text, Ping's unknown-latency fallback, Keystrokes key
labels — now render accent text out of the box; Keystrokes' `LABEL_FALLBACK` (dead
code) was removed and its labels route through `HudText` while the pressed-key fill
stays on the independent `keystrokesAccentColor`. Verified with the extended DevPilot
`hud`-mode `p3*` variants (which now place every consumer at once, force the
unknown-ping state, poke the totem/reach trackers, equip armor, and capture via
vanilla `Screenshot.takeScreenshot` — framebuffer-exact, no desktop-grab geometry),
across red/blue accents and explicit-white/fresh-config overrides; fixed-hue elements
(alert card, armor bars, minimap dots) verified byte-stable. §5 has the full story.

Landed 2026-09-08 after that: **`WorldMapScreen` joined the glass rollout at
chrome-only depth** (the `HudEditorScreen`/`ColorPickerScreen` tier — a few glass
elements, no window treatment, no glass pass/overlay dim; surfaces paint in place,
legacy order). The screen had already been migrated to shared components in an
earlier pass (its §6 row said "Not started" but the buttons were `ButtonWidget`s and
the prompt a token `RoundedPanel` — flat, since `Button.glassStyle` defaults OFF);
this change opted the chrome into glass: the 3 toolbar buttons + prompt Cancel as
NEUTRAL raised, prompt Create as STAINED (primary-action convention), the prompt
panel itself as DEPRESSED `GlassSurface.container` glass (WINDOW priority — the
pack-browser detail-modal treatment, chosen over "raised" per convention 2: a
floating prompt that contains controls is a container/window, and windows recess),
flat `RoundedPanel` fallback on decline, plus the standard `renderBackground`
world-gate (also skips vanilla's blur post-chain, pure waste under a viewport the
map fills). The map area is untouched by decision — tiles, void, waypoint markers,
player arrow, pan/zoom logic all render through their existing path, and the bottom
readouts keep hardcoded white/gray (content floating over map data; a mode-locked
`ON_*` text token could go dark-on-dark in light mode). Noted openly: the glass
samples the live backdrop, not the map tiles — tile blits are recorded into 1.21.11's
deferred `GuiRenderState`, so they are never in the main target when the blur pass
reads it; this is the same physics every glass surface has (the title screen's
panorama is the one exception, being an eager pass). Verified by two DevPilot
map-mode boots (pre/post change, same forced world state, tiles from the same
saved regions, capture budget 0): chrome visibly changed to glass (GlassStats: 3
CONTROL panels with the toolbar, +1 WINDOW/3 CONTROL with the prompt open,
declines=0), and an A/B framebuffer diff confined to the chrome rects — map
pixels byte-identical outside them (the player arrow's AA edge shifts with frame
phase; documented, not a rendering change).

Landed 2026-09-09: **the glass shimmer fix** — "every glass panel flickers/wavers
across its whole surface whenever the backdrop behind it moves" (title-screen
panorama, camera motion in-world, scrolling lists over a static world). Root cause:
the capture step's single 4× `GL_LINEAR` minification blit is a point-ish sample (the
dev machine's Mesa driver reads ~1 of every 16 source texels; the GL bilinear model is
4 of 16), so content moving against the sample grid aliased into per-frame low-frequency
blotches the chain Gaussian cannot remove. Fix: two ~2× LINEAR blit steps through a
half-res intermediate = a 4×4 area average (§6 pipeline paragraph has the full story,
including the driver probe and the DevPilot frame-sequence harness that measured
2.3–3.7× temporal noise pre-fix, bit-stable static, and the post-fix collapse of the
popping — the old kernel's per-pair delta distribution had p90/median ≈ 7.6× (violent
phase pops), the new one ≈ 1.3× (smooth tracking). `BlurTestScreen` gained the
`slide=`/`slideamp=` state tokens (deterministic known-motion stripes + uint32-hash
detail, phase logged per frame) that made the pipeline measurable against an analytically
computable ideal. One commit, renderer + harness + this doc.

Landed 2026-09-09 after that: **Settings-tab consolidation — the "Miscellaneous"
entry**. The eight Settings-tab tiles below "Interface" (Smooth Camera, Frame Pacer,
Low Latency, Tick Sync, Decoupled Input, Drag-to-Reorder Servers, Compliance Mode,
Accessibility — everything from Smooth Camera down; Interface itself stays standalone)
merged into one `miscellaneous` SETTINGS entry. Settings rows moved byte-verbatim under
per-tile `SectionHeaderSetting`s (the Better Hitreg multi-section pattern), each tile's
master toggle becoming that section's "Enabled" row with the original getter/setter —
config fields and behavior untouched. The Settings tab gained detail-screen navigation:
a header click on any entry with settings opens its `FeatureDetailScreen` (chevron
affordance; previously Settings-tab entries had NO reachable detail screen, hence no
reachable reset — Miscellaneous's unified Reset, prefix list = union of the eight
tiles' fields, is new capability). `FeatureMetadata.settingsDetailOnly` (Miscellaneous
is the only user) opts an entry out of inline rows: header + toggle + "Click to
configure" hint on the tab, the 33-row list on the detail screen. Master toggle =
Pack Tweaks/Alerts pattern (OR of the eight sub-flags; setter flips all). Verified by
a DevPilot `tabs`-mode boot: Settings tab shows Custom Title / Text & Fonts /
Interface / Miscellaneous only; the detail screen's 33 rows enumerate every original
setting by label; master-toggle flip and reset roundtrip logged correct with fields
restored. SETTINGS count 11 → 4 (MODULES unchanged at 35).

Landed 2026-09-09 after that: **Theme moved from the Modules tab to the Settings tab**
(second, independent commit). The `theme` `FeatureRegistry` entry moved from the
MODULES bucket to SETTINGS (placed after Text & Fonts — the appearance cluster:
Custom Title, Text & Fonts, Theme, Interface, Miscellaneous); its metadata, six
settings, reset behavior, and detail screen are untouched. The Modules grid lost the
theme card (`ModuleManager`, 35 → 34; `Module.meta()` always searched both buckets so
no other lookup changed), and `FeatureRegistry.all()` now returns both buckets — it
previously returned MODULES only, which would have broken id-based lookups (the dev
harness's theme-detail opener) after the move. Theme's Settings-tab presentation is
the tab's standard one: header + toggle + inline settings, detail screen one header
click away. Verified by A/B DevPilot boots: the Theme detail screen is pixel-identical
to the pre-move baseline (same world, same capture path; diff noise confined to glass
frame-phase AA at panel edges), and both tabs' screenshots show the new arrangement.
MODULES 35 → 34, SETTINGS 4 → 5.

Landed 2026-09-10 after that: **Miscellaneous moved from the Settings tab back to the
Modules grid** (the reverse of the Theme move, mirroring `3c213d2`). The `miscellaneous`
`FeatureRegistry` entry moved from the SETTINGS bucket to MODULES — byte-verbatim
metadata, all 33 setting rows, master toggle, and unified reset untouched — and
`ModuleManager` regained a grid card for it (35 cards, tail position). The tile's icon
is the `question_mark` glyph (U+EB8B, newly added to the material-symbols subset —
regenerated from the real `full_material.ttf`, so the subset is again exactly the
`FeatureIcons` codepoint set; the regeneration also dropped five orphan glyphs
(album/videocam/done_all/verified_user/delete) left over from the pre-consolidation
tiles). The Settings-tab-only `settingsDetailOnly` presentation did not travel with the
move: on the grid the card's right-click opens the same detail screen as every other
tile, so the flag now has no registered user (machinery kept — see §9). Verified by a
DevPilot `tabs`-mode boot: `rows=33` with every original label, master-toggle flip +
reset roundtrip correct with fields restored, the Settings tab shows Custom Title /
Text & Fonts / Theme / Interface only, and the Modules grid's last tile is
Miscellaneous with its "?" icon. MODULES 34 → 35, SETTINGS 5 → 4.

Landed 2026-09-10 after that: **the design-language pilot** — the first application
of `DESIGN_LANGUAGE.md` (repo root, this revision) to three deliberately different
screens, in three revertible commits. Shared chrome, mod-wide on every
`FeatureDetailScreen` (`28d8aca`): the screen title the spec's §1 believed existed
but never did (feature name, `ON_BACKGROUND`, at the window's content-column
indent) with the optional credit subtitle attached beneath it in
`ON_BACKGROUND_SECONDARY`, plus `SectionHeaderSetting`'s new rhythm (10px blank
above the caption — §2 — and its token moved off a legacy tertiary static to
`ON_BACKGROUND_MUTED`, pixel-identical in both factory palettes). Better Hitreg
(`764c891`): new `SectionFooterSetting` (§3 body text; exactly one use — the
Tracking footer), `BooleanSetting.valueLine` (§4 live-state subtitle, on the three
alert toggles; deliberately toggle-only since every other control already shows
its state at a glance), and "Reset Tracked Stats" isolated into a trailing
Maintenance section (§7.3). `AuroraScreen` (`385e704`): Settings-tab
`SETTINGS_ENTRY_GAP` 8 → 16 (§6 group rhythm — measured 40→56 / 61→77 device px
with row gaps unchanged); every other spec rule is a documented grid exemption
(the spec's "Grid screens" section). Verified by four DevPilot `pilot3` boots
(framebuffer-exact before/after captures in `.devpilot-pilot/`; functional
spot-checks incl. the hitreg reset firing through the real Button wiring).
Rollout posture from here is per-screen, per the spec's rollout section — known
next candidates: a crosshair shape preview (§5).

Landed 2026-09-10 after that: **the §7.3 destructive-isolation sweep, mod-wide**
(two revertible commits, completing the pilot's first named next candidate).
Every `ButtonSetting` across `FeatureRegistry` plus the composite widgets'
internal actions were audited against §7.3; three violations existed and all
three are fixed. Stats Overlay (`4cf5b6b`): "Reset Lifetime Fight Totals"
(left mid-Fights, under "Show Last Fight") and "Reset Stats" (first row of
Session, above the Reset Key keybind) both moved into a new trailing
"Maintenance" section (same naming as Better Hitreg's), ordered least-to-most
destructive; button bodies and descriptions byte-verbatim. Totem Pop Counter
(`7406655`): "Reset Now" moved from row 2 (between a keybind and a toggle) to
the screen's last row, deliberately WITHOUT a new section — that screen is a
flat sectionless 8-row list where a lone section header would do no grouping
work (§7.3's own-section clause is conditional on "more than a couple of
groups"; the trailing position satisfies the rule's intent on a screen this
size). Audited and cleared, for the record: Alerts' "Test Sound" (preview,
not destructive), Waypoints' "Manage Waypoints…"/"Drop at Player Position"
(navigation/additive), Resourcepack Browser's "Open Browser…" (navigation),
the detail screen's bottom-bar Reset (shared chrome button band, isolated by
construction), `PixelCanvasSetting`'s internal Clear/Default (widget-internal
editing surface — the spec's "no change to any control widget's own
rendering" clause), the per-item "−" removes inside KeyList/StringList/
ItemScale (per-entry editing), and the manager screens' per-row Delete plus
HudEditorScreen's floating "Reset Positions" (management/editor UIs, not
settings sections). Verified by three DevPilot `s73` boots (baseline + one
per commit; framebuffer-exact captures in `.devpilot-pilot/s73-*`, functional
spot-checks driving every moved reset through the real
`ButtonSetting` → `Button` → `onPress` wiring with seeded counters and
persisted lifetime fields restored afterwards). Operational note for future
harness boots: nothing in the tracked tree wires `DevPilot` in — add a
temporary uncommitted `DevPilot.install();` at the end of
`AuroraClient.onInitializeClient`, boot, and revert it before committing;
and never SIGKILL a running dev client (a hard kill once left kwin/XWayland
wedged so every subsequent boot hung inside `glfwCreateWindow`; a minimal
GLFW create/destroy/terminate cycle from a plain JVM cleared it).
Recurred 2026-09-11 (D9 sweep boots, no SIGKILL involved): the wedge is
specific to VISIBLE + CORE-profile window creation — hidden and/or compat
windows and `glxinfo`'s core context all work, which is why the plain-JVM
clear cycle no longer clears it. **Working bypass: run the boot inside
gamescope** (`gamescope -W 1280 -H 720 -- ./gradlew --no-daemon runClient`;
its nested XWayland is fresh, `--no-daemon` so the game JVM inherits
gamescope's DISPLAY) — the D9 smoke ran full-speed under it and exited
cleanly. Terminating a wedged boot with SIGTERM is safe; SIGKILL is not.

Landed 2026-09-10 after that: **the §4 subtitle audit** (`082d97f`) — a
sweep for further `valueLine` candidates plus the first check of the rev-2
"state hidden behind a nested screen" clause. Part 1 came up empty: the
supplier-description pattern that identified hitreg's three alert toggles
matches nothing else, and every other live figure in the feature set
(kills/fights, totem pops, ping, CPS, playtime) is already surfaced at a
glance by its own HUD module — the exclusion clause covers them all, so the
hitreg three remain the only `FeatureSetting.valueLine` users. Part 2 found
exactly one genuine nested-state case, in `ItemScaleSetting`: collapsed
per-item rows hid all seven configured values behind the expand chevron, so
each collapsed row now carries an in-widget §4 subtitle ("Scale 1.40×",
`TEXT_SECONDARY` beneath the label — the drawValueLine idiom, drawn in-widget
because the item rows aren't FeatureSettings; row header 26→38 / stride
30→42 across render/baseHeight/click/drag). Expanded rows drop the subtitle
(the scale slider's readout then shows it — exclusion clause); the other six
values stay expand-only deliberately. `PixelCanvasSetting` is a clean
negative — no collapsed state exists; the canvas renders inline at all times
and IS the state — and EnumSetting/ColorSetting/KeybindSetting/
KeyList/StringList/ParticleConfigSetting all already show state inline.
Verified by DevPilot `s4` boots (stashed baseline + post-change captures +
functional hit-test clicks proving the new 38/42 geometry in
`.devpilot-pilot/s4-*`).

Landed 2026-09-10 after that: **the §5 crosshair preset preview** — the
last unsatisfied candidate from the spec's original §5 list. The crosshair
detail screen gained a leading live-preview row (`CrosshairPreviewSetting`,
first row, before the Style enum it demonstrates — the spec's default
placement; Theme's trailing exception is a cumulative-result case that
doesn't apply). The preview draws the selected preset through the REAL
shape code: `CrosshairRenderer`'s shape switch was extracted into a public
`drawShape(...)` that both the HUD path and the preview call (the
`ItemSpriteRenderer` one-pipeline principle — no second implementation to
drift). Fixed demo dimensions (7/2/2) at a stable row size — the preview's
job is the SHAPE, the one thing no other control shows; Size/Thickness/Gap
sliders and the Color swatch already surface their values (§4's exclusion
clause applied to §5), and the color is the `ON_BACKGROUND` token so the
shape stays legible on the inset panel in both modes. CUSTOM previews the
user's real canvas. Drawn in the live overlay layer (cached shapes can't
promise preset-switch immediacy). Verified by DevPilot `s5` boots: the
preview shows CROSS/CIRCLE/SQUARE correctly and switches live with the
enum; the in-game HUD is pixel-identical pre/post refactor (80×80 center
crop A/B: 6400/6400 identical, 112 lit crosshair pixels both); and the
preview's lit-pixel pattern is EXACTLY the in-game shape's pattern
(112 px, centroid-relative offsets equal — same args through the same
code). Captures in `.devpilot-pilot/s5-*`. The indicator-style enum
(second selector further down) deliberately got no preview — this change
was scoped to the preset selector; a second preview there is a possible
follow-up.

Landed 2026-09-10 after that: **the mod-wide pre-dim rollout, Part A +
Part B** (two revertible commits — the explicitly-approved follow-up
§6 convention 6 had been waiting on since 2026-09-05). Part A (`f1f101f`)
gave the four pill widgets the `Button`/`EditBoxMixin` surface/content
split: `EnumSetting`/`KeybindSetting`/`KeyListSetting`/`ItemScaleSetting`
paint their glass SURFACE in `renderGlassPass` while a structural pass is
open and content-only in render otherwise, with `FeatureSetting.
renderGlassPass` now receiving the row's live geometry from the screen
(the same walk the overlay loop uses — remembered rects would lag the
eased scroll by a frame). The split is inert on legacy-order screens by
construction (widgets gate on `GlassSurface.passOpen()`), and the
in-place path routes through `GlassSurface.control`, whose no-pass
behavior is call-identical to the raw idiom it replaced (same gate,
CONTROL priority, raised lighting, `WINDOW_FILL` tint, rim in place).
Verified by DevPilot `predim` phase-a A/B boots (frozen-world fixture,
framebuffer-exact): keystrokes/zoom/crosshair-enum/theme byte-identical
pre/post; item-scale identical inside the settings window (72 differing
pixels at ±1/255, all at the screen's far-left world edge — ambient
animation, not UI). Part B (`1d35651`) piloted ONE screen —
`FeatureDetailScreen`, chosen over the alternatives after investigation:
it is the primary consumer of Part A's widgets; it is the only candidate
with no raw scissors; and the `UiLayerCache` question resolved clean
(glass panels are texture blits that bypass the fill-capture sink, so
glass never enters the cache and the cached chrome stays a content layer
above the dim — no cache changes needed; `AuroraScreen`, by contrast,
adds two scissored tabs and 35 glass tiles that would all move pre-dim
at once, and the pack browser adds the §9 modal + three scissor
conversions while exercising none of Part A). The screen now runs the
`ManagerListScreen` skeleton (`beginGlassPass` → surfaces →
`overlayDim` → cached blit + content), with `SegmentedControl` gaining
the split, `SegmentedSetting`/`ButtonSetting`/`ParticleConfigSetting`/
`PixelCanvasSetting`/`StringListSetting` driving their embedded
components, `ThemePreviewSetting` converting to the shared helper
(deferred rims), Done/Reset driven the `ButtonWidget` way, and
`GlassSurface.aboveDimControl` (§9's escape hatch) keeping
`EnumSetting`'s expanded popup above the dim. Verified by two phase-b
boots: captures at Background Opacity 0.85 and 0.25 across four screens
(crosshair + expanded popup, item_scale, keystrokes with the stained
listening pill, theme with segments + preview card) — glass veiled under
the dim, rims crisp above it, popup floating above; `declines=0`,
`poolExhausted=0` on every GlassStats line; ZERO guard reports in normal
operation; and the guard negative-tested IN-FRAME via `ScreenEvents`
afterRender (END_CLIENT_TICK probes can't see the dim — the frame epoch
rolls before the tick — so the probe runs inside a rendered frame, where
a `GlassSurface.control` after `overlayDim` on the live screen fires the
ordering-violation ERROR with the probe's own stack; exactly one report
in the whole boot, the probe's). Captures in `.devpilot-pilot/predim-*`.
Deliberately NOT extended this session: `AuroraScreen` and the pack
browser stay on the legacy order; both are now unblocked mechanically
(every widget they use carries the split), and each still has its own
scissor/`UiLayerCache`/modal decisions to make — the same per-screen
handoff every prior pilot made.

Landed 2026-09-11: **the pre-dim rollout's final two screens — the rollout
is COMPLETE** (two revertible commits; every glass screen in the mod now
enforces the structural pre-dim layering rule, §6 convention 6).
`AuroraScreen` (`1e170eb`): the `ManagerListScreen` skeleton across the
whole screen — window via `GlassSurface.container`, the sidebar
chips/Profiles/layout buttons/search field driven by a new
`paintGlassPass`, the Modules grid's tiles painted under the TRACKED
scissor (`GlassSurface.enableScissor`, deferred rims keep the list clip,
ROW priority), and the Settings tab's inline rows driven through the
settings' own `renderGlassPass` under the same tracked scissor (the same
walk `renderSettingsLive` performs; the Theme preview card gained its
glass surface here for the first time — its hook was never called on this
screen before, +1 panel on the GlassStats line vs baseline, everything
else count-identical). The `UiLayerCache` question was re-verified for
this screen specifically rather than assumed: `RenderUtil`'s capture sink
redirects only its own AA fills, so glass panels (texture blits) can never
enter the cache; the capture block runs after the window glass and before
the pass's tint fills; and this screen's cache holds only static window
chrome (no scroll term), so the live pass never interacts with it.
`ResourcePackBrowserScreen` (`d3b4bfd`): the dim through `overlayDim`'s
argb overload at the screen's original `0x55` strength; the sidebar via
`GlassSurface.container`; the ACTIVE category tab through a NEW
explicit-tint `GlassSurface.control` overload (its historical
accent-lerp wash IS its tint — evaluated in the pass so the hover ease
cannot disagree with the label pass); the per-card Install buttons driven
under the tracked grid clip through a `forEachVisibleCard` culling walk
now shared by pass and content, with a `drivenPhase` stamp so a download
thread flipping a card's phase mid-frame cannot hand the content render
an undriven (post-dim-painting) button; and the detail modal — §9's other
named above-the-dim case, confirmed still intended — served by TWO new
`GlassSurface` pieces: `aboveDimContainer` (the container-shaped sibling
of `aboveDimControl`: depressed, `SURFACE`-token tint, rim in place) for
the panel, and a `beginAboveDim`/`endAboveDim` ZONE bracket for
component-driven surfaces the caller doesn't own (the modal's shared
Buttons drive their passes in place inside it — driving them in the main
pass would bury their glass under the modal's backdrop + panel; `paint()`
skips the ordering report inside a zone, `beginFrame()` reports a leak).
Verified per the standing standard: DevPilot `predim` phase-c/phase-d A/B
boots on the frozen-world fixture at Background Opacity 0.85 + 0.25 —
AuroraScreen: glass bodies (35-tile grid included) now veiled with rims
above, content pixel-stable; pack browser: card bodies/text byte-stable,
the veiled Install buttons + active tab the only >32/255 deltas, and the
modal capture at 0.25 differing by ZERO pixels beyond ±11/255 (the
above-dim layer preserved its look); `declines=0`, `poolExhausted=0` on
every GlassStats line of both screens' boots; ZERO `[GlassSurface]`
reports in normal operation; and the guard negative-tested IN-FRAME on
each screen via the `ScreenEvents` afterRender probe (`drew=true
dimPainted=true`; exactly one report per boot, the probe's own). Captures
in `.devpilot-pilot/predim-aurora-*` and `.devpilot-pilot/predim-packs-*`.
**(Correction, same day — that "one report, the probe's own" was this
entry's own blind spot, see the next entry: the report the phase-c boots
logged was almost certainly the `ThemePreviewSetting` violation below,
not the probe's.)**

Landed 2026-09-11 after that: **the AuroraScreen pre-dim regression —
`ThemePreviewSetting`'s mock buttons painted post-dim** (found by an
unrelated boot-smoke, stash-verified pre-existing on `1e170eb`'s master).
Root cause: `ThemePreviewSetting` was the one composite widget that never
wired its embedded `Button`s into the structural pass — its
`renderGlassPass` drove only the card + accent chip, while the buttons
were invoked as full `Button.renderOverlay` calls from **`renderShapes`**
(the cacheable-static-geometry phase). `Button.renderOverlay` takes its
in-place surface branch whenever its own `renderGlassPass` didn't stamp
the frame — so the buttons painted a glass body wherever `renderShapes`
happened to run. Screen-order decided legality: on
`FeatureDetailScreen` `renderShapes` runs inside the cache-raster block
(between `beginGlassPass` and `overlayDim`) → pre-dim → silent; on
`AuroraScreen`'s Settings tab it runs in the content phase, post-dim →
the `[GlassSurface]` ordering ERROR, plus a visible symptom (the two
buttons floated UNVEILED above the dim while the card was veiled). The
same wrong phase had a second visible defect on the detail screen: the
buttons' surfaces AND labels only painted on cache-raster frames, so a
settled (cache-hit) theme detail screen rendered the preview card with
its two buttons entirely missing. Fix: the `ButtonSetting` discipline —
`renderGlassPass` now drives `primaryBtn`/`secondaryBtn.renderGlassPass`
(same geometry `renderShapes` derives), and the button calls moved from
`renderShapes` to `renderOverlay` (content only: label + flat fallback).
Why phase-c missed it: its Settings-tab scroll (-42px) DID reveal the
Theme preview row (a -16px notch is enough), so its boot fired this
violation once — but the guard reports once per (screen class, message)
per session, and the later same-key probe report was suppressed as the
duplicate, so the log's single report looked exactly like the expected
negative-test result. Lesson now part of the standing method: read the
report's STACK, don't count reports (the probe's report carries the
probe's own stack), and the `gfix` DevPilot mode exists as the broader
harness — a six-depth bidirectional Settings-tab scroll sweep with
settles, the settled theme detail screen, and every structural screen
(Waypoints/Profiles/pack browser incl. modal), no probe armed, so ANY
report is a real defect. Verified pre/post with it: pre-fix fires the
ERROR at the first scroll depth (captures `g2` unveiled buttons, `g7`
buttons missing on the settled detail screen); post-fix ZERO reports
across all twelve captures, buttons veiled with the card (`g2`),
present with labels when settled (`g7`), `declines=0 poolExhausted=0`
throughout. Captures in `.devpilot-pilot/gfix-{pre,post}/`. Same-gap
audit: every other composite `FeatureSetting` either drives its embedded
glass component in `renderGlassPass` (`ButtonSetting`, `SegmentedSetting`,
`ParticleConfigSetting`, `PixelCanvasSetting`, `StringListSetting`, the
four Part A pills, `EnumSetting`) or embeds only opaque components
(toggles, sliders, `ColorSwatch`) — `ThemePreviewSetting` was the only
instance.

Landed 2026-09-11 after that: **version metadata alignment** — the §1 "known version
drift" is resolved (user decision). `fabric.mod.json`'s `minecraft` constraint went
`~1.21.8` → `~1.21.11` (tilde semantics verified empirically against the cached Loader
0.19.2 jar: `~1.21.11` = `>=1.21.11 <1.22.0`; the old `~1.21.8` range already admitted
1.21.11, which is why the drift never bit), and the run-config
`-Dfabric.modVersion.minecraft` pin went `1.21.7` → `1.21.11` (three spots in
`build.gradle`; no code reads the property). The dead `aurora.subtitle` lang entry
(`"1.21.8"`, referenced by no code) was aligned too. Deliberately untouched: the
verbatim upstream `hitreg/` comments and `CrosshairRenderer`'s javadoc that mention
1.21.8 API behavior (factual code-path notes, not metadata). Verified by a clean
build, a DevPilot boot smoke, and Fabric API 0.141.4+1.21.11's own
`>=1.21.11- <1.21.12-` constraint still being satisfied by 1.21.11.

Landed 2026-09-11 after that: **the dead-code sweep (audit item D9)** — one
revertible commit per deleted subsystem, every item re-verified for references
(including reflection, resource/mixin configs, and dynamic path building) before
deletion. Removed: `ModuleIconRegistry` + its registration + the 22-PNG
`module_icons/` assets and their two dev scripts + stray SVG; `FeatureTile`;
`SliderFocus`; the Time Changer config trio (`timeChangerEnabled`/`timeOfDay`/
`TimeOfDayPreset`); the orphan `hitboxFeatureEnabled` (+ two stale javadocs that
pointed at it); `Feature.enabledByDefault()` + 3 overrides; `ColorEntryHelper`'s
uncalled Cloth Config half (`add`/`addPickerButton`) and with it the Cloth Config
dependency itself; dead constants (`BooleanSetting.TRACK_H`,
`ThemePreviewSetting.KNOB_R`, `SegmentedSetting.SEG_GAP`, `WaypointRenderer.
LABEL_LIFT`); `HudEditorScreen.drawGrid`/`grid`; the comment-only unregistered
`MixinGuiGraphics`; `Module.Category` + its field + the uncalled
`getModulesByCategory` (audit D8's stale-taxonomy finding); write-only fields
(`FeatureSetting`'s vestigial wrap cache, `ButtonSetting.lastBtnX/lastBtnY`);
unreferenced textures (`icons.png`, `gui/knob.png`, `gui/panel.png`,
`gui/panel_outline.png`); the four unreferenced design-source SVGs in
`textures/gui/`; stale `inspect_font3.py`; and the dangling `fabric.mod.json`
icon reference (logo.png was deleted with the title-screen rework, `9ce08dd`).
Deliberately NOT deleted: `AuroraModMenuApi` — `openOrFallback()` has zero
callers, but `AuroraTitleScreen` carries an explicit 2026-09-08 retention
comment ("stays in place for a future re-enable once Mod Menu ships a stable
1.21.11 release"), which outranks the audit; and the documented-deliberate
items (empty `WindowMixin`/`RenderTargetMixin`, `FeatureSetting`'s no-op
description hooks, `settingsDetailOnly`, `HitboxPositionSmoother`).
Verified per deletion by `compileClientJava`, at the end by a full `build`
+ jar-content audit (no removed assets ship), and by a DevPilot boot smoke.

Landed 2026-09-11 after that: **the Low Latency cluster audit** (five revertible
commits, following a dedicated investigation of the "High-Frequency Input"
setting). The investigation disassembled vanilla 1.21.11's input/frame path from
the mapped jar and established: GLFW events are pumped ONLY in
`RenderSystem.flipFrame` (twice, around the buffer swap) and
`limitDisplayFPS` (`glfwWaitEventsTimeout` during the cap wait) — both at frame
END; `MouseHandler.handleAccumulatedMovement` applies the accumulated cursor
delta EVERY frame (runTick render section, before `GameRenderer.render`) and
zeroes it; vanilla applies `glfwSwapInterval` only at startup and on a vsync
option change (`Window.updateVsync`). Consequences acted on: **Zero-Latency
Camera removed** — its `Camera.setup` accumulator read was structurally always
zero (nothing pumps between the zeroing and camera setup), a placebo since the
initial commit; **High-Frequency Input removed** — never implemented in any
commit/branch/stash (the late-latch pump it describes is the §9 candidate);
**LowLatencyFeature fixed** — it used to force `glfwSwapInterval(1)` on first
tick and again on every un-forcing transition (overriding a vanilla vsync-off
preference in both directions), and turning the Low Latency master off while
Disable VSync was on left swap interval 0 in place forever (the master's early
return skipped the restore); now it only asserts 0 while active and hands back
the vanilla option's own value on the way out; **Adaptive Render Sleeping is now genuinely gated on the
Low Latency master** (both mixin sites AND in `lowLatencyRender`, read fresh
each tick; the `RenderSystemMixin` cancel branch falls through to the precise
pacer when off, since canceling without the runTick sleep would leave no
limiter) and its runTick-HEAD sleep gained the Aurora-GUI exemption the cancel
site always had — previously a HEAD sleep paced at the vanilla cap (120) kept
re-anchoring the GUI limiter and Aurora screens ran at ~120 fps, silently
defeating the Interface FPS cap (`guiFpsLimit`, 60); **three descriptions
corrected** — Decoupled Input's vanilla-samples-at-20Hz claim (false on
1.21.11; it actually applies the batch at the top of runTick, before the tick
block, raw — bypassing cinematic-camera smoothing), the Low Latency "Enabled"
row (a section master, not a queue-shrinking mechanism of its own), and
Adaptive Render Sleeping (now states its prerequisites). The Low Latency
section is now: Enabled, Disable VSync, Adaptive Render Sleeping — nothing
advertised that isn't real. (Config-block neighbor noted during the audit:
`fixHeldItemSeams`/`heldItemInflation` have an implementation —
`HeldItemSeamFixMixin` — but no UI row anywhere. Resolved by the next entry.)

Landed 2026-09-11 after that: **Held Item Seam Fix finished and surfaced** —
the orphan the Low Latency audit flagged, resolved by investigation rather
than by reflex. The investigation found the `reflex`-B5 shape one level
deeper: the fields AND a complete mixin (`HeldItemSeamFixMixin`: uniform
first-person held-item scale, default 1.001, clamped 1.0–1.05 — overlaps
adjacent cube faces by a sub-pixel to hide mipmap texture-bleed seams)
arrived in the initial commit (a big-bang import), but the mixin was absent
from `aurora.mixins.json` — the ONLY unregistered mixin in the codebase —
and the fields appear in no FeatureRegistry/ModuleManager revision in any
commit: not "unreachable", outright inert since day one. No retirement
commits, no intent comments — accidental omission, so the feature was
finished, not left headless or deleted. Changes: the mixin is registered,
and its pop inject was fixed `@At("TAIL")` → `@At("RETURN")` before
activation — vanilla 1.21.11's `renderArmWithItem` early-returns at offset 7
on `player.isScoping()` (verified in the mapped jar), so a TAIL-only pop
would leak the HEAD's `pushPose` on every scoping frame (the sibling
`HeldItemRendererTweaksMixin` already used RETURN for exactly this shape);
new `held_item_seams` MODULES entry — master toggle plus a per-mille "Fix
Strength" int slider 0–50 mapping 1.000–1.050 (the shared double slider's
0.01 step and two-decimal readout cannot express thousandths) — with
explicit reset prefixes (the two field names share no common camelCase
prefix); a ModuleManager card after Item Scale (35 → 36 cards); the
`texture` glyph (U+E421) added to `FeatureIcons` with the icon-font subset
regenerated from the real `full_material.ttf` (40 glyphs, exactly the
codepoint set). Verified by DevPilot `seamfix` boots (captures in
`.devpilot-pilot/seamfix-post/`): the registry row is found with the
slider; a slider drag-to-max driven through the REAL press/drag/release
interaction path writes `heldItemInflation=1.05`; master-toggle and reset
roundtrips exact with fields restored afterwards; and a first-person A/B at
inflation 1.05 with the toggle on/off (frozen scene, sway disabled) diffs
exactly on the held diamond sword's silhouette — the previously-inert
config fields now demonstrably drive rendering.

Landed 2026-09-12 after that: **styled-text colors restored under the
custom font** — the command-syntax coloring in the chat input (and, it
turned out, every styled `FormattedCharSequence`) was flattened to
uniform white whenever Text & Fonts applied a bundled TTF (ALL_TEXT;
AURORA_ONLY never touches chat). Vanilla ground truth, read from the
mapped 1.21.11 jar: `CommandSuggestions`'s constructor installs
`formatChat` via `EditBox.addFormatter`; `formatText` builds a COMPOSITE
`FormattedCharSequence` from per-segment `FormattedCharSequence.forward`
runs carrying `Style`s — literals GRAY (`ChatFormatting.GRAY`), parsed
arguments cycling a 5-entry AQUA/YELLOW/GREEN/LIGHT_PURPLE/GOLD list,
and the unparsed tail after a syntax error RED — and `EditBox.renderWidget`
submits it through `GuiGraphics.drawString` → `GuiTextRenderState` →
`Font.prepareText(FormattedCharSequence)` at flush time. (The suggestion
popup is a separate mechanism: plain-string `drawString` with explicit
int colors — selected `0xFFFFFF00` yellow, others `0xFFAAAAAA` gray —
never affected.) Root cause: MixinFont's two sequence interceptors
(`drawInBatch(FormattedCharSequence)` and `prepareText(FormattedCharSequence)`)
extracted the plain string via `getSequenceString` and re-wrapped it as
`Component.literal(plain).withStyle(fontOnlyStyle)` — destroying every
per-character style, not just colors. Pixel-verified pre-fix: the input
line AND colored chat history lost ALL chromatic pixels under the font;
the popup was unaffected. Fix (general, not command-specific):
`AuroraFontRenderer.withCustomFont` returns a decorating sequence whose
sink swaps ONLY the font (`Style.withFont`, which preserves color, bold,
italic, obfuscated, shadow) with a single-slot identity cache so the swap
costs one Style per styled segment, not per character; both interceptors
now return the decorated sequence instead of flattening (the drawInBatch
one converted from cancel-and-redispatch to a plain `@ModifyVariable`),
`AuroraFontRenderer`'s own sequence `drawString` overload uses the same
helper, and the icon-glyph guard (`sequenceHasCustomFont`) is unchanged.
Measured widths stay consistent: the width interceptors measure
all-custom-font just like the decorator renders. Verified by DevPilot
`chatfont` boots (captures in `.devpilot-pilot/chatfont-{pre,post}/`,
framebuffer-exact, same command `/give @s minecraft:not_a_real_item`,
config snapshot/restore): font OFF shows the vanilla gray/aqua/red
segments; font ON pre-fix showed uniform white input + flattened history;
font ON post-fix shows the segments back (input line: red `~#FC5454`
tail, aqua selector, gray literal; history: red/green/aqua/yellow/gold
all present) while the glyphs are genuinely Inter (39.6% structural
pixel diff vs the font-OFF line — the font still applies), and the
`/gamemode` popup keeps its yellow selected entry. The vision-model
screenshot reads were wrong in both directions (claimed PRE history was
colored, POST input white) — chromatic-pixel counting + color-isolation
overlays were the reliable instrument; noted for future harness work.

Landed 2026-09-12 after that: **the §8 top-edge scroll fade** (pilot +
extension, then a field-regression fix the same day — read all three
together; the regression is the important part). The pilot gave every
scrollable screen a top-edge fade (`util/ScrollFade`: a 16px gradient in
the surface color verbatim, engagement `min(1, scroll/16)`) — detail
screens, both manager lists, `AuroraScreen` both tabs, then the pack
browser's sidebar (`SURFACE`-token panel) and card grid (the screen's own
0x55 dim). Rule + cases in DESIGN_LANGUAGE.md §8. The pilot's
detail-screen implementation used a CAPPED variant for the then-unscissored
list: a painted cover of `WINDOW_FILL` above the boundary, "hiding" rows
that slid toward the title band. **That cover's hiding power was
`WINDOW_FILL`'s alpha — the user-tunable Background Opacity — and the dev
config this machine actually plays at runs 0.1**, so at full engagement
the "opaque" cover was 90% transparent and rows slid right over the
title/subtitle band on Minimap and Better Hitreg (the user's two
screenshots; reproduced pre-fix with the `ovfix` harness: 1,126 stray
text pixels in Minimap's under-title band, title+subtitle+row text
bunched on Hitreg, both clean at opacity 1.0 — which is all the pilot's
verification had effectively exercised). Fix: `FeatureDetailScreen` gained
a real GL scissor — its single `UiLayerCache` split into `chromeCache`
(window panel, blits unscissored: a container MAY slide under the title
band) and `rowCache` (rows' shapes, blitted under a boundary scissor that
descends TOP_FADE_Y+16 → TOP_FADE_Y as the fade engages, so a resting
list clips nothing), the live overlay loop under the same scissor, the
gradient kept as the softener only, and `ScrollFade.drawTopCapped`
DELETED (a painted cover is opacity-bound and can never be trusted to
hide content — the §8 rule now says every scrollable viewport must
scissor, and a screen whose rows live in a cache needs two cache layers
to scissor rows but not chrome). **The verification lesson, now standing
method (same family as the `gfix` "read the report's stack" lesson): a
visual-fix verification that runs at ONE theme configuration has verified
nothing about the settings axis — the fade pilot's boots ran on this very
machine at the user's 0.1 opacity, the pixel-diff showed the expected
band and only small deltas (a 90%-transparent veil still dims bright
text past a fixed threshold), the vision model even described the
remaining ghost and was dismissed as unreliable, and the "verified, all
45 screens" claim shipped while the exact reported bug was live on the
exact screens captured. Opacity/glass/mode are one-line theme mutations —
harness verification of anything whose correctness depends on a theme
token's VALUE must sweep at least {user's live value, factory default},
and a verification claim should name the values it ran at.**

Landed 2026-09-12 after that: **per-effect Effect-Expiry alert granularity**
(two revertible commits). The Alerts feature's Effect Expiry section grew
a "Per-Effect Alerts" group (§2 `SectionHeaderSetting`) listing every
registered potion effect — localized display name + the vanilla
`mob_effect/` sprite (PotionModule's icon path, ItemScale's row geometry:
label indented past an 18×18 icon) + a shared `ToggleSwitch`, alphabetical
by display name, behind a search field that filters by label OR raw
registry id. The container is the Particles list's mechanism extracted,
not duplicated: `SearchListSetting<T extends FeatureSetting>` (search
EditBox + memoized filtered recompute + search-band hit-test + event
forwarding + the EditBox glass pass; subclass supplies only `matches()`)
— `ParticleConfigSetting` is now a thin subclass, pixel-identical
(verified by a same-code control boot: cross-boot pixel diff over a live
world is ~27% >±8 from world-through-glass drift alone, and the
pre/post refactor diff measured BELOW that noise floor, with text-row
band positions byte-identical). The extraction also hoisted a
`baseHeight()` call out of the render loop's row-cull condition (it
re-summed every row height per row — the O(rows²) §10 shape, inherited
from the original). Data model: an exclusion SET,
`AuroraConfig.effectExpiryExcludedEffects` (registry-id keys, absent =
alert), chosen so the factory default (empty) preserves the
single-toggle behavior — an old config loads with every effect alerting
(or none, if the master was off; logged both in the harness). The master
toggle gates everything behaviorally, Low-Latency style (no visual
dimming of the rows). `StatusAlertFeature.checkEffects` skips excluded
ids before threshold tracking (an excluded effect never even enters the
fired map). Rows carry no §4 subtitles (toggle shows state) and no §5
preview (name+icon self-explanatory); the list inherits the detail
screen's §8 scissor fade for free (it's ordinary rows in the settings
list). Hardening that came with it: both reflection reset paths
(`AuroraConfig.resetByPrefix`, `ProfileManager.applyProfile`'s reset
step) now defensively COPY mutable collection defaults — the new Set is
the first collection field whose reset prefix actually matches
(`effectExpiry`), and aliasing the DEFAULTS snapshot's collection would
let later edits mutate the factory default (the same hazard
`ThemeDefinition.copyOf` already guards against; profiles saved before
the field existed would have handed the live config the aliased Set via
the reset step). Harness notes: AlertManager's spam guard drops queued
alerts sharing the active alert's TITLE, so every expiry alert in one
burst collapses to one popup — the `fxe` harness staggers its two test
effects 4s apart and also reads `StatusAlertFeature.alertedEffects`
(the exact boundary the exclusion sits at) via reflection. Verified by
three `fxe` boots (baseline, full, control): exclusion set loads `[]`
from the old config; a real row click writes
`[minecraft:strength]` through the real `mouseClicked → ToggleSwitch →
setter → save` wiring; excluded Strength never fires while Speed does
(`Speed · 10s` popup captured in-world); re-enabling Strength makes both
fire; master off fires nothing; zero ERRORs; captures in
`.devpilot-pilot/fxe-*` (untracked).

Rebuilt the same day (also 2026-09-12, one commit): **the per-effect
list flipped from render-every-effect-with-a-toggle to the
`ItemScaleSetting` search-then-add pattern** — perf motivation first
(only curated effects render; the toggle version drew all ~40 rows every
frame), interaction shape matched exactly: standalone `FeatureSetting`
container (NOT the `SearchListSetting` filter-list base — Particles keeps
that), search field + responder → one suggested effect, "+" glass add
button, "Add: <name>" sprite line, added rows as `EffectRowSetting` with
ItemScale's exact 38px row geometry (panel wash, hover outline, sprite,
label, trash "x"; no chevron — presence in the list IS the state, there
is nothing to expand). Data model flipped exclusion → inclusion:
`effectExpiryIncludedEffects` (registry ids, present = alerts, empty =
nothing alerts — the fresh-install default). The migration is the
behavior-critical part: `EffectExpiryMigrator.runOnce()` (HitregMigrator
discipline — one-shot flag `migratedEffectExpiryExclusions`, armed even
on fresh installs, profile-EXCLUDED so an apply can neither re-run it
over the user's curation nor be flattened by one; called after the
profile apply; result saved to config AND profile) populates
registry-minus-legacy-exclusions when the boot loaded a pre-existing
aurora.json, so "exclusion empty" (the whole existing user base) lands
on "everything alerts" — what they had — while a genuinely fresh config
stays empty. The file-existed signal (`AuroraConfig.loadedFromDisk()`)
must be captured in `load()` itself: load() creates the file on fresh
installs, so a later existence probe would always say "existing".
`StatusAlertFeature` skips effects absent from the list (an excluded
effect never enters the fired map). Verified by the `fxe2` harness on a
seeded just-shipped-version config (exclusion set
[strength, blindness], master on, no inclusion key, no flag):
post-boot inclusion = registry minus those two; in-world, strength never
fired while speed did (pre-rebuild behavior preserved); then inclusion
forced empty, and the full UI flow drove real
`EffectExpiryListSetting.mouseClicked` wiring — search "str" →
suggestion, "+" added minecraft:strength, trash removed it (second trash
click correctly missed), re-add; then with ONLY strength on the list,
speed was silent and strength fired — the exact inverse. Zero ERRORs;
captures in `.devpilot-pilot/fxe2/` (untracked).

Landed 2026-09-12 after that: **the out-of-world UI simplification** (three
revertible commits). (1) **The selection-screen starfield is gone** —
`SelectionScreenBackgroundMixin` and its `title_background.png` deleted;
Singleplayer/Multiplayer show whatever vanilla renders (on 1.21.11 that is
panorama + accessibility blur + menu texture via `Screen.renderBackground`
itself). `AbstractButtonMixin`/`EditBoxMixin` theming on those screens is
untouched — verified PIXEL-identical pre/post (same per-color histogram in
the button band, zero-diff rows through the search field) by a stashed-baseline
A/B boot. That A/B also surfaced a PRE-EXISTING breakage, deliberately not
fixed this session: 1.21.11's `Button$Builder.build()` constructs
`Button$Plain`, an inner SUBCLASS, so `AbstractButtonMixin`'s
`getClass() == Button.class` gate excludes every vanilla button — the
vanilla-button theming has been inert (all vanilla "Buttons" render stock;
only the search-field theming, whose `EditBox` target has no such indirection,
applies). Fixed later the same day — see the follow-up entry at the end of
this section. (2) **The
title screen's panorama-capture mechanism generalized to any no-world
screen**: new shared `GlassSurface.renderMenuPanorama(Screen, GuiGraphics,
float)` — draw `renderPanorama` (via the new `ScreenPanoramaAccessor`
`@Invoker` mixin; the method is protected and only Screen subclasses could
call it before) + `noteMenuBackdropDrawn()`, one call from a screen's
`renderBackground` when `mc.level == null`, returning false with a live
world. `AuroraTitleScreen` now goes through it, and `AuroraScreen`'s
no-world path calls it from `renderBackground` — opening Aurora from the
title screen now shows real panorama-blurred glass under Frosted (was: the
flat fallback, because vanilla's own `renderBackground` drew the panorama
but nobody declared it) and the flat look under Wireframe; the in-world
path is unchanged. Verified by DevPilot `oow` boots (one boot, both styles,
then an in-world control pass; captures in `.devpilot-pilot/oow/`,
untracked): `lastOutcome='rendered'` + GlassStats 16 panels/frame
(W1/C6/R9) `declines=0` on AuroraScreen-from-title under Frosted,
`'transparent style (glass off)'` under Wireframe, in-world both styles
captured; guard negative-tests ALL decline `'no level (menu context)'`
sampled between frames on the title screen, on the now-declaring
AuroraScreen (the stamp must not outlive its frame even for the declarer),
and on a NON-declaring screen (`ProfileManagerScreen` — the leakage test;
zero glass claims in its GlassStats window); the F2 interlock still
suppresses (`'suppressed (screenshot in flight)'` through the real
`takeScreenshot` path). Deliberately NOT extended this session (staged
discipline): recommended next is `FeatureDetailScreen` (the direct next
click from `AuroraScreen` in the same no-world context), then
`ColorPickerScreen` and the pack browser (reachable no-world from detail
rows / the Resourcepack tile), then the Profiles screen (sidebar button);
`HudEditorScreen` opens in-world only and needs nothing. (3) **Display-label
rename Transparent → Wireframe**: `GlassStyle.TRANSPARENT.displayName` +
the FeatureRegistry description + human-facing comments/docs; the enum
constant and persisted value stay `TRANSPARENT`, so existing configs parse
unchanged — a label change, no save-migration. Label verified on-screen
("Frosted | Wireframe", Frosted selected) by the `oow3` harness capture.
Harness notes for future boots: the `oow`/`oow3` modes live in the
untracked `DevPilot.java`; `FeatureRegistry.all()` does NOT lazy-init the
buckets (only `modules()`/`settings()` do — calling `all()` first returns
empty lists; harness code must touch `settings()` first).

Fixed the same day, one follow-up commit: **the `AbstractButtonMixin`
vanilla-button gate** — now an exact `getClass() == Button.Plain.class`
match (the class `Button.builder().build()` constructs on 1.21.11; the
mapped name is compile-checked, no stringly matching).
`getSuperclass() == Button.class` was rejected: ImageButton/LockIconButton
extend Button DIRECTLY and would take this painter; the exact match fails
closed for any future vanilla subclass (stays vanilla) instead of silently
restyle-ing it, and if Mojang ever changes what the builder constructs the
failure resurfaces as the honest un-themed look. Verified by before/after
boots (captures `.devpilot-pilot/btnfix-before|after/`, untracked): both
selection screens' buttons render the themed dark fills (was vanilla gray
bevels — button-band histograms before vs after), the already-themed
search-field band is pixel-identical pre/post (in-frame control — only the
button path changed; Aurora's own Button/ButtonWidget are screen-gated out
and untouched), and the press-squash — gated by the same dead check, so
equally dead before — fires: a real `mouseClicked` reaches `onClick` on
`Button$Plain`, and the armed state renders the scale ease 1.0 → 0.96
(probe values 0.995→0.960 across the 90 ms press-down; the pressed frame
shows the button inset toward its center). Harness note: EVERY button on
these two screens navigates or rebuilds the screen in its onPress (even
Refresh does `setScreen(new JoinMultiplayerScreen(...))`), discarding the
armed instance before the next frame renders — the press-capture arms the
merged `aurora$pressDownStartMs` field reflectively on a still-rendering
button instead.

Landed 2026-09-12: **the Accessibility (colorblind) feature was removed entirely**
(one revertible commit, D9 discipline — reference-search before delete). The feature
never functioned: `AccessibilityFeature.getColorMatrix()`'s daltonization matrices had
no render-path consumer at all, the screen-reader item was deferred, and the
scroll-remap tick (`scrollUpRemap`/`scrollDownRemap`) was a documented no-op. Removed:
`AccessibilityFeature`, its registration, the `colorblindMode`/`colorblindStrength`/
scroll-remap config fields + `ColorblindMode` enum, and the Miscellaneous screen's
Accessibility section (master toggle/getter/setter/reset-prefix entries updated; the
Miscellaneous tile itself stays, now seven former tiles). Old configs carrying the
removed fields load clean — GSON's lenient parsing ignores unknown fields, for both
direct load and ProfileManager snapshots.

Landed 2026-09-12 after that: **Compliance Mode removed entirely** (second revertible
commit of the day, per the user's decision to remove rather than patch its
config-persistence bug). The feature auto-disabled reach display, toggle sprint/sneak,
hitbox (both sub-toggles), keystrokes, and particles on a built-in strict-server list
with user safe/strict list overrides — a complete reference search confirmed those six
config booleans were written by `ComplianceModeFeature.onTick` alone (it flipped the
plain `AuroraConfig` fields; no other feature or mixin read compliance state), so with
the feature gone each of the five features is back to purely user-controlled behavior.
Removed: `ComplianceModeFeature`, its registration, the `complianceModeEnabled`/
`complianceModeToast`/`complianceSafeServers`/`complianceStrictServers` config fields,
the Miscellaneous screen's Compliance Mode section, `HudStatus.RESTORED` (compliance
toast color, now unused), and `screen/setting/StringListSetting` (its only consumer was
the two compliance server lists). Old configs carrying the removed fields load clean,
direct and profile-snapshot both.

Landed 2026-09-13: **the global Frame Cap** (Miscellaneous, own "Frame Cap" section
between Frame Pacer and Low Latency) — `AuroraConfig.frameCapFps`, 0 = Off (default,
fresh installs see no behavior change), 10–2000 active, presented as a slider whose
value readout is click-to-edit. Investigation first mapped every limiter in the frame:
vanilla 1.21.11's game loop paces through `RenderSystem.limitDisplayFPS(fps)` after the
swap **only while `FramerateLimitTracker.getFramerateLimit() < 260`** (260 = the
Unlimited sentinel; the tracker layers iconified-10 / AFK-10-or-30 / menu-60 throttles
over the option), Aurora's `RenderSystemMixin` replaces that call (Aurora screens →
`guiFpsLimit`, else the Frame Pacer strategies, or cancels it when Adaptive Render
Sleeping owns pacing), and the adaptive sleep itself paces at runTick HEAD from
`options.framerateLimit()`. The load-bearing discovery: **with the vanilla option at
Unlimited the game loop never calls `limitDisplayFPS` at all** — any cap implemented
there is silently dead (this also explains, and pre-dates this change, why
`guiFpsLimit` is dead in-world with the option Unlimited — a known quirk, deliberately
NOT fixed here), so the new cap lives at runTick HEAD in `MinecraftClientRenderMixin`,
the one pacing point that runs every frame regardless of the option. It shares the
adaptive sleep's anchor chain (mutually exclusive owners; the re-anchor-on-fps-change
covers handovers), folds into the adaptive pacer's fps when that is active
(`min(option, cap)`, one wait), and when the Frame Pacer is off / VANILLA-strategy it
paces with the fixed hybrid profile — the same fallback the GUI limiter uses. The
composition rule (why nothing fights): every pacer is an independent anchored
"wait until my target", and one looser than the actual cadence degenerates to a no-op
re-anchor, so effective fps = min(vanilla option, vanilla throttles, guiFpsLimit on
Aurora screens, this cap) — tightest wins, nothing defeats anything. Verified in-game
(DevPilot `framecap` boots, vsync forced off, frozen dev world): cap 30 and 144 with
the option Unlimited hold exactly 30/144; cap 2000 rides at the natural ~224; option
120 + cap 144 → 120 (vanilla tighter wins — the audit's defeat bug class, re-checked);
option 120 + cap 30 → 30; adaptive@60 + cap 144 → 60; adaptive@60 + cap 30 → 30; and
the Miscellaneous screen itself holds the cap (30) with the option Unlimited, where
the post-swap limiter is dead. The card master toggle ORs `frameCapFps > 0` and
master-off zeroes it (master-on does NOT force a cap value — it's a value setting,
not a toggle); reset prefix `frameCap`. The same change added the general
`SliderSetting.editableValue()` capability (click the value readout → in-place
`EditBox`; Enter or click-away commits through the slider's snap+clamp+setter, Escape
reverts, out-of-range clamps, empty/non-numeric reverts — matching PixelCanvas's W/H
fields' strip-responder idiom; focus-loss commit keyed off the detail screen's
clearFocus-on-click) plus `valueFormatter` (the cap's "Off" at 0) and `Slider.setValue`
— deliberately a general enhancement, not a one-off widget, since the addition to the
shared slider row is small and other wide-range settings (0–2000 sliders drag poorly)
will want it.

Landed 2026-09-13 after that: **Glint Color (`glint_color`)** — the enchant-glint
recolor, built mirroring Hit Color's real structure (master toggle + color picker +
"Tint Armor" sub-toggle — Hit Color exposes a picker, not just an on/off) after
re-reading the `d84298c` fix (the disabled-state pixel was the byte-swapped blue twin
of vanilla's red overlay pixel, so toggle-off painted the wrong color). Investigation
by disassembly established that 1.21.11's glint is a *completely different mechanism*
from Hit Color's overlay atlas: every glint draw (held, GUI/hotbar, dropped items via
`ItemRenderer.getFoilBuffer`'s four render types; worn armor via
`EquipmentLayerRenderer` → `armorEntityGlint`) is a second pass of the item's quads on
the `pipeline/glint` shader with vertex format POSITION_TEX (no per-vertex color),
blend `BlendFunction.GLINT` = `glBlendFuncSeparate(SRC_COLOR, ONE, ZERO, ONE)` — the
fragment RGB is *added* to the framebuffer, so both the purple hue and the shimmer
pattern live in exactly two 128×128 textures (`textures/misc/enchanted_glint_item.png`
/ `_armor.png`, ids read from `ItemRenderer.<clinit>`'s `ldc`s, `blur: true`), whose
pixels the shader multiplies by ColorModulator (white in practice) and a fog fade.
Recoloring therefore means rewriting those pixels, not swapping a blend color: the hook
(`mixin/ReloadableTextureMixin` → `util/GlintColor`) tints them at HEAD of
`ReloadableTexture.apply` — the one concrete funnel every reloadable texture passes
through, and the last CPU-side moment before vanilla uploads and *closes* the
NativeImage (a first attempt targeted `SimpleTexture.loadContents` but Mixin cannot
@Shadow the inherited `resourceId()` — caught by the boot, retargeted). The tint is a
per-channel multiply by the picker color (pattern intensity carries the shimmer;
picker alpha scales strength; alpha channel preserved — the glint shader only uses it
for a <0.1 discard), recomposed exclusively via `net.minecraft.util.ARGB` helpers.
(The multiply semantics were superseded within hours by the colorize fix — see the
next entry.)
The `d84298c` lesson is answered *structurally*: **there is no disabled-state constant
anywhere** — off returns vanilla's untouched file load bit-for-bit, and every texture
id compared against came from bytecode, not hand-typing. Mid-session toggle/color
changes can't rewrite closed pixels, so `GlintColor.tick()` (END_CLIENT_TICK from
`AuroraClient`, HitColor's change-detection pattern: short-circuit unless
enabled/color/tintArmor changed) forces a synchronous reload of exactly the two
textures via `TextureManager.release`+`getTexture` — vanilla's own
`registerAndLoad → loadContents → apply` path re-entering the hook; runs between
frames (§6 convention 10) and is skipped while `mc.getOverlay() != null` (a resource
reload's async PendingReloads would otherwise apply() onto a closed texture), leaving
the change pending for the next tick. F3+T reloads re-tint automatically by
construction. Verified by three DevPilot boots under gamescope (log evidence + the
`[glintColor] tinted …` load-hook lines with exact before/after pixels):
framebuffer-exact captures of a Sharpness-V diamond sword + Protection-IV diamond
armor show vanilla purple off (5189 purple blade-stripe pixels), red when on (red 7354
/ purple 8 in the held-item region; armor differential red 111 vs purple 19), and —
the d84298c failure mode affirmatively ruled out — vanilla purple again after a
mid-session toggle-off (10628 purple blade stripes, 0 red; armor differential purple
1052 vs red 45); a `glintui` boot confirmed the Modules-grid tile and the detail
screen (title "Glint Color", color picker + Tint Armor rows, `settings=2`). Settings
`glintColorEnabled`/`glintColor`/`glintColorTintArmor`, reset prefix `glintColor`
(camelCase-derived). Harness gotchas fixed along the way: the generic tick-40
(spectator) / tick-180 (tp into terrain) preamble blocks now exempt `glintcolor`, and
equipment set via the client *inventory* (`setItem(0/36..39)` + `setSelectedSlot`)
survives chunk equipment resyncs that wipe client-side `setItemSlot` writes.

Landed 2026-09-13 after that: **the Glint Color colorize fix** — the recolor read
strong only near the source's purple hue and weak everywhere else. Diagnosis
confirmed against the code before changing anything: the tint multiplied each
source channel by the picker's channel (`new = orig × target/255`), and a multiply
can only dim or zero channels, never brighten — the source glint is a dim purple
(`0x4b237b` ≈ 29% R / 14% G / 48% B), so a green target could never exceed 35/255
and a white target was literal identity (vanilla purple, unchanged). The fix is a
colorize: each pixel reduces to an intensity = `max(R,G,B)` (HSV value) and the
output is `intensity × target` — the target at FULL strength wherever the pattern
was bright, near-black wherever it was dark, so the shimmer survives as a value
ramp of the target hue instead of a flat block; the texture's alpha stays
untouched and the picker's alpha still scales strength. `max` was chosen over
weighted luminance (`0.299R+0.587G+0.114B`) deliberately: the pattern's energy
lives in R and B (purple), which green-dominant luma weights systematically
undervalue — a second-order version of the very dimming being fixed. One
implementation bug was caught by reading the boot log's `[glintColor]` probe
lines before trusting any capture: the first draft kept the target channel on
the 0..1 float scale inside `Math.round(...)`, which collapses to 0 or 1 and
turned the whole texture black (`ff4b237b -> ff000000` for every target); the
target channel must stay on the 0..255 scale. Verified by a `glintcolor2` DevPilot
boot under gamescope (four targets + mid-session off, 1p held sword + 3p armor;
log probe pixels: purple→`ff4c247b` (≈ vanilla's own `ff4b237b`), green→
`ff006219`, white→`ff7b7b7b` — arithmetically impossible under multiply —
red→`ff7b0000`): a near-vanilla purple target reproduces vanilla with a net-zero
signed blade differential (no regression); green reads vivid at the far hue
(blade hue counts green 27955 / purple 4; signed diff +88 G against off); white
is the armor's silver shimmer (3p: zero purple-dominant pixels while on); red
present with +29 R / −64 B against off (red light in, the purple's blue out);
the shimmer still animates (an animation-pair capture differs on 14921/61740
blade pixels — a moving pattern, not a static tint) with value-STD 14–38 across
captures (not flattened); and the mid-session toggle-off restores vanilla purple
(blade purple 25996, B-dominant differential, zero residual target hues) — the
`d84298c` failure mode stays affirmatively ruled out.

Landed 2026-09-13 after that: **the Glint Color tile icon** — `auto_awesome`
(the Material sparkles glyph, U+E65F) on the Modules-grid card, next to Hit
Color's dropper. Codepoint verified against the real `full_material.ttf` cmap
(not hand-typed), the material-symbols subset regenerated from it (41 glyphs —
exactly the `FeatureIcons` codepoint set again), and the subset's kept glyph
outline-verified byte-identical to the source font's `auto_awesome`. Verified
in-game by a `glinticon` DevPilot boot: the enabled tile renders the sparkles
glyph in ON_ACCENT on the stained glass.

Landed 2026-09-13 after that: **the Reflex disconnect-during-render crash —
root cause** (found in real live gameplay, not the dev harness). A server sent
a `ServerTransferS2CPacket`; during `Minecraft.disconnect()`'s handling of the
transfer, Aurora's Reflex render hook threw `IllegalStateException("startTimeGpu
is null")` from `GpuTimeCollector.endQueryCheck` — the uncaught exception
interrupted the disconnect partway (interaction manager nulled, teardown
unfinished) and the next frame's `GameRenderer.renderHand` NPE'd on the
half-torn state. Mechanism: Reflex frames a GPU timer-query PAIR per frame
(`glQueryCounter` start at runTick head, end at `updateDisplay`; results
polled later by availability), and queued main-thread tasks — exactly how the
transfer packet's disconnect runs, inside `runTick`'s task drain and again
inside `disconnect`'s own `runAllTasks` mid-teardown (verified in the 1.21.11
bytecode) — can interrupt the frame between a pair's begin and end checks,
leaving an end query whose start query never reported a result. Out-of-order
`glQueryCounter` completion is also spec-legal on its own. The old code
treated "start result missing at end-check time" as an API violation and
threw; it is a normal interrupted-frame state, so `endQueryCheck` now DROPS
that frame's GPU sample: deletes the orphaned start query, logs one WARN per
session (DEBUG afterwards), returns "done" so the caller retires the
collector, and never runs the end callback (`endTimeSystem` needs the missing
`startTimeSystem`). `startQueryCheck` is likewise null-safe for an
already-consumed start query, and `reset()` now deletes still-outstanding
query objects so abandoned collectors (stale-frame removal, an interrupted
frame) don't leak them in the driver. Verified by a `reflex0` DevPilot boot
pair under gamescope — the harness reproduces the crash state
deterministically on any driver (query-end called with the start query
swapped for a never-issued id; such a query object reports "not available"
forever, which is exactly what `startTimeGpu == null` encodes after
`startQueryCheck`): the PRE-fix boot reproduced the exact `startTimeGpu is
null` ISE (stack through `Minecraft.runTick` → `Minecraft.run`); the POST-fix
boot returned `done=true` with no throw plus the WARN, and the normal
pipeline was unchanged (deque drains, GPU estimate fills, 60 fps).

Landed 2026-09-13 after that: **Reflex's render hooks are exception-safe,
mod-wide** (the general fix — same defense-in-depth posture as
`BlurPanelRenderer.permanentlyDisabled`). A render-loop hook that can throw
an uncaught exception into Minecraft's own critical paths — disconnect, frame
rendering — is a structural risk independent of the specific bug above, so
the three `ReflexMinecraftMixin` injections are now thin one-line delegations
to `ReflexScheduler.beginFrame()/beforeFlush()/endFrame()` (the hook bodies
moved into the scheduler, including the CPU-time collection that lived as an
@Unique mixin field), and every body runs under `runGuarded`: on ANY
Throwable, Reflex logs exactly one ERROR with the full stack, drains its
half-processed collectors (`reset()` releases their GL query objects), and
latches itself off for the rest of the session — pacing skipped, everything
else untouched, `reflexEnabled` untouched (next launch retries). This closes
the whole CLASS: any future edge case in the timing logic degrades Reflex
instead of crashing the game, while staying loud in the log (nothing is
silently swallowed). Verified by the full `reflex` DevPilot boot: the harness
drives a failure through the same `runGuarded` the hooks use — one ERROR +
stack logged, `isSessionDisabled()` latched, a second guarded call no-ops,
and the game kept rendering at 60 fps for 10 s with the latch on before a
clean exit; the same boot re-verified normal pacing (Phase A) and the
graceful drop (Phase B). Clean no-DevPilot smoke boot after.

Landed 2026-09-13 after that: **Tick Sync removed entirely** (one revertible
commit, same discipline as the Compliance/Accessibility removals). Two
independent reasons, both established by a same-day audit:
(1) **Licensing** — the feature was a near-verbatim, uncredited port of
LogGamja's "Tick Sync" (modrinth `tick-sync`, ARR license; the algorithm,
field names, and constants matched upstream line for line), breaking this
repo's own credit convention (Freelook++/AppleSkin/HitColor/Better Hitreg
are all credited/permissioned); removal was chosen over pursuing
permission. (2) **Anticheat exposure** — the audit (verified against
1.21.11 bytecode, not assumption) traced the feature's real effect: it
wrote sub-20 tick rates into `ClientLevel.tickRateManager()`, which
vanilla's `DeltaTracker$Timer` consults via
`getTickTargetMillis = max(50, millisecondsPerTick())`, occasionally
stretching a single client tick to ~60–105 ms (≤1/s while desynced) —
which shifts the cadence of every serverbound gameplay packet
(`LocalPlayer.tick` → `sendPosition`, plus tick-consumed attack/use
clicks), i.e. exactly the deviation category competitive Timer checks
(Vulcan/Grim/Matrix) measure; note the port had silently DROPPED
upstream's `getTickTargetMillis` un-cap mixin, so Fast Sync's pull
direction was already inert — push-only, in the slow direction, but a
deliberate cadence deviation nonetheless. Removed: `TickSyncFeature` +
registration, `TickSyncNetworkMixin` + `aurora.mixins.json` entry, the
Miscellaneous Tick Sync section (master getter/setter + Auto Margin/Fast
Sync/Netty Thread rows + `tickSync` reset prefix), the four `AuroraConfig`
fields, description strings, and the DevPilot `ticksync` harness mode
(untracked). With the feature gone, no non-vanilla writer of the client
tick-rate manager exists — 20 TPS / standard movement-packet cadence by
construction. Old configs load clean (default Gson ignores the unknown
fields; ProfileManager WARNs "field … no longer exists; skipped" and both
`aurora.json` and profiles rewrite without them on next save — verified on
a real boot carrying all four old fields, master ON). Verified by a
DevPilot `tabs` boot under gamescope: Miscellaneous detail screen
`rows=19` with every label enumerated (was 24; no Tick Sync section
anywhere), master-toggle roundtrip flips exactly the five remaining
sub-flags, unified reset restores pacer/camera fields, world joined, 60
fps, clean exit, zero tick-rate mutations in the log. Feature count
29 → 26 (§2/§3 updated); MODULES tile count unchanged at 37
(Miscellaneous keeps its tile). `HITREG_INTEGRATION_CATALOG.md`'s
mixin-collision note updated (the `handleMoveEntity`/`handleTickingState`
injections went with the mixin).

Landed 2026-09-13 after that: **Disable VSync survives fullscreen
transitions** (fixes the F11 hole found by the same day's
efficiency/effectiveness audit of Frame Pacer / Frame Cap / Disable VSync).
The audit measured the bug live: Disable VSync active and the client
uncapped (234.9 fps, un-quantized) → F11 → 60.0 fps vblank-quantized →
F11 back → STILL 60.0 — only an explicit off/on of the setting recovered
it. Root cause, verified against 1.21.11 bytecode: vanilla asserts the
swap interval from THREE places, not the two the feature's javadoc
claimed — the `Minecraft` constructor, the vsync option's observer, and
`Window.updateDisplay`, which on every pending fullscreen transition calls
`updateFullscreen(this.vsync, …)` → `updateVsync(this.vsync)`, re-asserting
the FIELD's value. Aurora's old code called `glfwSwapInterval(0)` directly,
so `Window.vsync` still held the vanilla option's value (true) — the
transition faithfully re-enabled vsync, and `LowLatencyFeature`'s
`wantNoVSync == forcedNoVSync` edge detection never fired because its own
state genuinely hadn't changed. Fix (the "route through vanilla" candidate
from the audit's three options, plus the cheap watchdog): every Aurora
write now goes through the public `Window.updateVsync` — the field always
tracks the state Aurora actually forced, so the fullscreen re-assert lands
on interval 0 by construction — and while forcing is active the feature
re-asserts `updateVsync(false)` every tick (20 trivial calls/s), which
additionally self-heals the one writer routing alone can't cover: the
vanilla option's observer, which can apply `updateVsync(true)` mid-forcing.
The restore path is unchanged in semantics (falling edge → one routed
`updateVsync(vanillaOptionValue)`, never forcing vsync on), and the class
javadoc now documents all three call sites. Verified by DevPilot `perf`
boots under gamescope — with a harness lesson worth recording: the
per-phase vsync transition driver added during the audit (to de-contaminate
its tail phases) MASKS this bug, because cycling `disableVSync` off/on
across a phase boundary is exactly the recovery toggle; an A/B boot
without the fix exposed the masking, and the `f11-*`/`optobs` phases now
suppress the cycle and enter with continuous forcing. Results: the exact
repro sequence holds uncapped throughout — 576.7 fps (windowed) → 567.7
fps (fullscreen, p50 1.52 ms, un-quantized) → 555.8 fps (back windowed),
versus 60.0/60.0 pre-fix; the new `optobs` phase (fires the vanilla
observer's `updateVsync(true)` mid-forcing — the config round-trip case,
no off/on) holds 493.7 fps un-quantized; the restore path re-quantizes to
60.1 fps / p50 16.63 ms as designed; render-thread CPU in the forced
phases (83–85% of one core) matches the audit's uncapped baselines
(84.7/85.1%) — the per-tick re-assert costs nothing measurable; and a
stash-A/B boot pinned the one suspicious number (cap30-fix miss15% 50.3%
vs the audit's 0.0%) on phase position / just-loaded-world jitter, not
the fix (no-fix at the same early position: 47.8%, statistically
identical).

---

## 9. Known outstanding work, dead code, and hazards

**Incomplete / follow-up candidates**
- ~~`FeatureIcons` codepoint U+E4E3 ("flash_on", used by `tab_ping` and `reflex`) does not
  exist in the material-symbols font at all — `flash_on` is U+E3E7 there — so both icons
  render the .notdef box (visible on the Reflex grid card).~~ **Fixed (2026-09-11)**: both
  entries now reference U+E3E7 (verified against the full font's cmap: `flash_on` lives at
  U+E3E7; U+E4E3 does not exist), and the bundled subset was regenerated from the real
  `full_material.ttf` — it is again exactly the `FeatureIcons` codepoint set (39 glyphs).
  Verified on the Modules grid (Reflex tile renders the lightning bolt).
- ~~The deferred-rim queue in `GlassSurface` (§6 convention 6) has no opt-out for surfaces
  deliberately meant to render ABOVE an already-dimmed screen~~ **Built, complete (2026-09-11)**:
  `GlassSurface.aboveDimControl` (neutral raised, `WINDOW_FILL` tint — `EnumSetting`'s expanded
  popup) and `GlassSurface.aboveDimContainer` (depressed, `SURFACE`-token tint, WINDOW priority —
  the pack browser's detail modal) paint the identical idiom entirely in place in the content
  pass, exempt from the pass ordering; and `beginAboveDim`/`endAboveDim` brackets an above-dim
  ZONE for component-driven surfaces the caller doesn't own (the modal's shared Buttons drive
  their own passes in place inside it) — `paint()` skips the ordering report inside a zone, and
  `beginFrame()` reports one left open.
- The shared `Button` has no success variant, so `ResourcePackBrowserScreen`'s
  Install/Installed states render accent-stained/neutral instead of success-green
  (§6 table). A `Button.success`-style variant would restore the old semantics.
- FeatureRegistry's redundant `.glassButton(true)`/`.glassSegments(true)` pilot calls.
- ~~`highFrequencyInput` (Low Latency) is advertised in the UI with no
  implementation.~~ **Resolved (2026-09-11)**: removed along with the inert
  Zero-Latency Camera (see §8). Remaining follow-up: the **late-latch input
  pump** they were both meant to be — poll GLFW right before `Camera.setup`
  (masked/replayed callbacks so key/button/resize events don't dispatch
  mid-render) and re-add a consumer there. Medium scope (pump mixin +
  mask/replay utility + verification harness); deliberately NOT built in the
  audit session.
- ~~Accessibility colorblind correction: matrices are computed but the screen-reader item is
  deferred and the scroll-remap tick is a documented no-op~~ **Removed entirely
  (2026-09-12)** — the feature never functioned (the color matrices had no render-path
  consumer at all); `AccessibilityFeature`, the colorblind config fields/enum, and the
  Miscellaneous screen's Accessibility section are gone. See §8.
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
- `FeatureMetadata.settingsDetailOnly` (+ the `SETTINGS_HINT_H` walks in `AuroraScreen`)
  has had no registered user since Miscellaneous moved to the Modules grid (2026-09-10);
  the flag is inert off the Settings tab. Kept as Settings-tab presentation machinery —
  remove it if no user materializes.
- `MultiplayerScreenMixin` was deleted 2026-09-05 (abandoned refresh-all feature; never
  registered), and `MultiplayerServerListWidgetMixin` (numeric ping on server list rows)
  was registered the same day — the former "complete but not listed in aurora.mixins.json"
  pair no longer exists.
- ~~`timeChangerEnabled` + `TimeOfDayPreset` in config: no consumers~~ **Removed
  (2026-09-11, audit D9)**; old configs deserialize cleanly (GSON ignores unknown fields).
- ~~`Feature.enabledByDefault()`: never read~~ **Removed (2026-09-11, audit D9)** with its
  three never-read overrides. `AutoSprintFeature`: dormant stub.
  `FpsDisplayFeature`: no-op marker (FPS lives in Info HUD).
- ~~`ColorEntryHelper.addPickerButton`: builds nothing (stub)~~ **Removed (2026-09-11,
  audit D9)** along with the equally-uncalled `add()` — the class keeps only the
  `hslaToArgb`/`argbToHsla` math ColorSetting/ColorPickerScreen use, and the now-orphaned
  Cloth Config dependency was dropped from build.gradle + fabric.mod.json.
- `module/ModuleManager` (34 hardcoded cards; count-matched with `FeatureRegistry` since
  the missing `reflex` card landed 2026-09-05, audit B5; `better_hitreg` added 2026-09-08;
  the `theme` card removed 2026-09-09 when Theme moved to the Settings tab) —
  ids here still silently fail on typos. Consider deriving one from the other someday.
- ~~ComplianceMode's `hitboxFeatureEnabled` config field is an orphan~~ **Removed
  (2026-09-11, audit D9)**; ~~ComplianceMode and the hitbox keybind flip the real
  `hitboxEnabled`/`hitboxTargetEnabled` sub-toggles~~ — and ComplianceMode itself was
  **removed entirely 2026-09-12** (§8); the hitbox keybind flips the real sub-toggles.
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
  subtitle. The formerly-still-open license mismatch is also **resolved** (2026-09-11,
  user decision): the repo `LICENSE` is now the standard MIT text —
  `Copyright (c) 2026 Kalib` (holder from `fabric.mod.json`'s authors), matching the
  `MIT` license `fabric.mod.json` has always declared.
- Better Hitreg's card icon is the distinct `swords` glyph (U+F889, resolved 2026-09-08;
  it previously reused Crosshair's `gps_fixed`). The subset was regenerated from the real
  Material Symbols Rounded variable font placed at root-level `full_material.ttf`
  (gitignored by design; if missing, re-download
  `variablefont/MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf` from
  google/material-design-icons and run `subset_script.py`'s fontTools command — the script
  itself still hardcodes a Windows path). The bundled `material_symbols_rounded.ttf`
  remains a subset of exactly the `FeatureIcons` codepoints.
- ~~`assets/aurora/textures/gui/module_icons/` ships two dev scripts + `settings.svg`~~
  **Resolved (2026-09-11, audit D9)**: the whole unused PNG mechanism
  (`ModuleIconRegistry` + the 22 PNGs + the scripts + the SVG) was deleted, along with the
  four design-source SVGs in `textures/gui/` and root-level `inspect_font3.py`. Font
  `license.txt` still covers a removed font (Source Sans Pro).
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
