# Aurora Client status

Updated 2026-11-22. See [ROADMAP](ROADMAP.md) for the order of work,
[architecture](ARCHITECTURE.md) for ownership, and [ADRs](adr/) for decisions.

## Structure and integration

Aurora's frame pacer, global/menu/background frame rate limiters, VSync override, adaptive render sleeping, and automatic Reflex latency scheduler (`FramePacer`, `FramePacing`, `AdaptivePacerCalibrator`, `ParkDiagnostics`, `EphemeraPacingBridge`, `LowLatencyFeature`, `ReflexScheduler`, `RenderSystemMixin`, `ReflexMinecraftMixin`, `GpuDeviceTimestampAccessor`, `FramerateLimitUnfocusedMixin`) were removed on 2026-11-23 so Ephemera owns frame pacing and low-latency rendering. Existing configs and profiles containing those fields load cleanly without errors.

Ephemera 1.0.0 compatibility & optimization pass adds categorized Ephemera rendering controls in Settings → Performance, direct fast-write vertex bridges (`EphemeraBridge`), immutable cached UBOs and saturation-only single-buffer VRAM savings in `WorldPostEffectRenderer`, zero-allocation Sound Volume / Chat Peek / Info HUD / Inventory HUD / Now Playing HUD / Controls conflict hot paths, and fixes `AuroraFontQuality` retrying `rescale()` every frame after a GUI-scale change with deferred fonts. Verified live on Minecraft 26.2 with Ephemera 1.0.0 across OpenGL Multi-Draw (`0.061–0.065 ms` mean), Native Vulkan `VK_EXT_mesh_shader` (`0.079–0.127 ms` mean), and a full all-features Vulkan stress pass (`0.084 ms` mean, 0 warnings/errors).

Pulsar accuracy pass fixes inclusive exchange/round end-boundary combo splitting (`Combos`), prevents live same-tick trade combo inflation (`CombatEngine`), pairs knockback `VELOCITY` packets across `[tick - 1, tick + 1]` without double-claiming and with strongest-impulse tie-breaking (`FightAnalytics`), aligns combo sprint-reset attribution with post-fight `Combos` streaks, and exposes fact-backed **Combo Rating** and **Jump-Reset / Knockback Control Rating** (`CombatBreakdown.ComboProfile` & `KnockbackControl`) across the in-game dashboard (`FlowPage`, `DefensePage`) and offline HTML reports (`ReportExporter`).

Folder-Aware Resource Pack Manager (`resourcepackFoldersEnabled`, on by default, cosmetic) discovers resource packs nested inside organizing subfolders under `resourcepacks/` (path-qualified `file/<folder>/<name>` IDs while keeping top-level vanilla IDs untouched) and adds Aurora's themed `ResourcePackManagerScreen` with folder navigation, clickable breadcrumbs, global recursive search, async pack icon loading, and load-order reordering from both **Options → Resource Packs** and **Settings → Tools → Resource Packs**. The manager keeps vanilla's full-screen pack layout with Aurora's controls inside it instead of a bordered window (2026-11-23).

Media Controls (`mediaControlsEnabled`, off by default, cosmetic) adds cross-platform desktop playback keybinds (Play/Pause, Next, Previous, Volume Up, Volume Down — dispatched asynchronously off the tick thread on Windows, macOS, and Linux) plus a Windows-first **Now Playing** HUD overlay (`NowPlayingModule`, polled off-thread via WinRT GSMTC only while enabled, zero background work when off).

The Settings tab has six pages since 2026-11-23: Appearance, World & HUD, Performance, Audio & Keys, Privacy and Tools. Display Settings (`miscellaneous`) is no longer one catalog feature: Decoupled Input, Tab List, Nametags, Per-Sound Volume, Streamer Mode and Server List each have their own fair-play class, mixin bindings, reset and page entry, and a feature whose mixin is not working shows its warning in place of its page summary. Config fields, defaults and profiles are unchanged for the remaining features.

Aurora windows keep their 460×308 size; on screens under 300 px tall the margin to the screen edge is 6 px instead of 12, and a shrunk main window shows its sidebar as an icon rail so the content column gets the room (GUI scale 4 at 1080p: 396×202 instead of 334×190). `WindowViewportMatrixTest` checks every GUI scale on 768p, 1080p, 1440p and 4K (2026-11-23).

UI cache refinement crops static captures to their panel/body bounds, keeps
unified chrome through screenshot suppression, retires screen textures after
deferred draws, and reuses one prepared setting-tooltip layout. The optional
native probe now measures open-screen extraction and cache payload separately
from gameplay overhead. [Implementation and measured limits](reports/2026-11-17-ui-cache-refinement.md).
Keystrokes has two key styles: Flat (default, Lunar-style legend on a flat coloured face) and cached Cherry-profile 3D keycaps; legends are row-sized and centred on their visible ink.

SDF rendering is the only path for Aurora's custom text, hitboxes and block outlines on all three
lines (owner, 2026-11-19; the opt-in toggles were removed). Text keeps raster layout; hitboxes and
block outlines use vanilla's translucent line pipeline with Aurora's distance-field shader
(anti-aliased edge, round caps). Bitmap text and vanilla lines remain only as the session fallback
when SDF cannot run ([ADR 0010](adr/0010-opt-in-sdf-text-with-vanilla-layout.md)). Checked in-world on 26.1.2, 26.2 and 26.3
(OpenGL; 26.3 also native Vulkan) at widths 0.5, 2 and 5.
Since 2026-11-20 SDF text also runs with ImmediatelyFast, which 3.6.0 stood it down for: ImmediatelyFast's
font atlas resizing enlarges Aurora's SDF pages too, and glyph texture coordinates now use each page's actual
size. Deferred font preparation no longer stands down for ModernFix, which owns no font set work
([report](reports/2026-11-20-sdf-immediatelyfast.md)).
SDF text and shader-drawn UI shapes also wait for the Shader Loader's first apply before judging their
shaders: with RRLS, a first draw that came before it turned both off for the session (observed in 1 of 9 boots
in the initial 26.3 pass, and deterministically under an early lookup on all three lines), and now only a
failure after the apply does ([ADR 0010](adr/0010-opt-in-sdf-text-with-vanilla-layout.md),
[report](reports/2026-11-20-sdf-immediatelyfast.md#rrls-sdf-text-and-the-first-shader-apply)).

Gradient Dithering (Settings → World & HUD, on by default, cosmetic) adds an 8x8 ordered dither of
under half an 8-bit step to the sky, sunrise glow, End sky, clouds and terrain (vanilla and
Sodium), so dusk skies, fog and dark scenes stop banding; GUI and HUD are never dithered. It
stands down for VulkanMod and while an Iris shader pack is in use. Checked on 26.1.2, 26.2 and
26.3 (OpenGL; 26.3 also native Vulkan); [evidence](reports/2026-11-20-gradient-dither.md).

Smooth Fog (Settings → World & HUD, off by default, cosmetic) replaces Minecraft's linear distance fog
ramp with a cubic Hermite S-curve over the exact same start and end distances, composing cleanly
with Sodium and Gradient Dithering in one pass; it stands down for VulkanMod and while an Iris
shader pack is in use. Checked on 26.1.2, 26.2 and 26.3 (OpenGL; 26.2 and 26.3 also native
Vulkan); [evidence](reports/2026-11-21-aurora-262-perf-and-smooth-fog.md).

Inventory HUD (`inventory_hud`, off by default, Vanilla equivalent) renders the player's 27-slot
main inventory — and optionally the 9-slot hotbar — as a draggable, scalable HUD module with
Vanilla (`inventory.png` / `container/slot`), Solid, and None backgrounds, compact packing,
optional stack-count hiding, and empty-state auto-hide across 26.1.2, 26.2, and 26.3.

Tab list and nametag polish expands existing Ping and Display Settings controls across 26.1.2,
26.2, and 26.3 without adding a new feature: Ping's tab-list readout now widens the ping slot so
3-digit latencies never overlap player names and supports an optional `"ms"` suffix and text
shadow, while Settings → World & HUD gains Tab List (hide server header/footer/player heads, highlight
own row, custom background opacity) and Nametags (show own nametag in third person, text shadow,
custom background opacity, and scale).

Pack Tweaks additions bring Blaze3D world post-processing (frame-rate-independent temporal motion
blur and colour saturation, zero shader cost at neutral defaults), client-side Local Time and
Local Weather (vanilla-equivalent, off by default), Custom Skies (sky presets/color, celestial
toggles, star brightness, void dark-disc hiding), first-person Fire Overlay height/opacity/hide,
and Hurt Camera toggle/strength across 26.1.2, 26.2, and 26.3.

Per-Sound Volume (Settings → Audio & Keys, cosmetic, 0–100% attenuation only) lets players lower or mute
individual Minecraft sounds through a searchable mixer with customized, recently played, and
common-sound lists across 26.1.2, 26.2, and 26.3, strictly clamped to 0–100% so sounds can never
be amplified above vanilla.

Streamer Mode (Settings → Privacy, off by default, local display-only privacy masking under Display
Settings) masks your username (with a custom alias across title screen, tab list, chat, and
nametag), replaces your own skin with the default Steve skin, masks server addresses on Direct
Connection, Edit Server, ConnectScreen, raw-IP server list entries and chat, hides coordinates on
Info HUD and Minimap, and optionally replaces other players' nametags, tab-list entries, and chat
mentions with customizable numbered/colored opponent labels. Zero overhead when off.

Chat Suite (`chat`, Modules grid, off by default, vanilla-equivalent) adds compact duplicate
messages (`[xN]`), local timestamps (`HH:mm`, `HH:mm:ss`, 12-hour), inline online player chat heads
via vanilla `PlayerSprite`, an in-chat `Ctrl+F` search filter, right-click / `Ctrl+Click` message
copying, a hold-to-peek keybind (`key.aurora.chat_peek`), configurable history limits up to 5,000
lines, and atomic per-server chat history persisted off-thread via `AtomicJsonWriter`. Never sends
messages or packets.

Per-Server Profile Auto-Switching adds server address bindings (comma-separated hostnames, IPs,
ports, and `*` wildcards such as `*.hypixel.net`) in `ProfileOptionsScreen` and `ProfileManager`.
Joining a matching multiplayer server automatically switches to the most specific bound profile,
and disconnecting restores the prior profile unless the player manually switched profiles mid-session.
Profiles with bound servers show a globe indicator badge and are searchable by server pattern in
`ProfileManagerScreen`.

Screenshot Manager (`Settings → Tools → Screenshots`) adds a themed in-game gallery for
`screenshots/` with async thumbnail/preview decoding and deferred frame-boundary texture eviction
(`UiLayerCache.beginFrame()`), filename search, inline rename with validation, press-again delete
confirmation, OS clipboard image copy, and non-destructive drag-to-crop PNG export.

Pulsar integration adds Phenomena's analytics and recording as Aurora's 40th
feature, off by default. Its configuration and six-tab dashboard follow Aurora's
profile, input and theme contracts. `CombatBreakdown` adds PvP analysis for 1.9+
Sword/NethPot/SMP and Shield/Axe combat (exchange openers & trades, sprint-reset
cadence, attack-charge discipline, shield-disable punish & axe-to-sword swap speed,
health/absorption timeline & burst pacing, turning points and fact-backed takeaways)
across the in-game dashboard, format-v7 offline HTML reports and Aurora Launcher,
paired with tick-capture caching, O(N) incoming sweeps and single-pass report hashing.
See [Pulsar](PULSAR.md) for workflows, source provenance and storage compatibility.
Acceptance and council review are tracked in
[the integration report](reports/2026-11-14-pulsar-integration.md).

Aurora and Pulsar text fields share bounded undo/redo history; see [text-field behavior](DESIGN_LANGUAGE.md#1112-text-field). Dev-client acceptance remains pending.

Keybind conflict warnings (`KeybindConflicts`, `KeybindConflictLookup`, `KeyBindsListEntryConflictMixin`) surface duplicate bindings across Aurora's `KeybindSetting` rows, profile hotkeys (`ProfileManagerScreen`, `ProfileOptionsScreen`), and Minecraft's vanilla Controls screen (including profile hotkeys and default-vs-default collisions involving Aurora bindings, while leaving F3 debug sub-keys and plain-vs-combo `KeyCombo` pairs distinct). Checked in a disposable 26.3 dev client at factory (0.42) and high (0.85) opacity plus 854×480 narrow viewport across Zoom, Sprint & Sneak, Pulsar, Profiles, Profile Options, and vanilla Controls.

Vanilla HUD control (`vanilla_hud`, off by default, vanilla-equivalent) exposes Minecraft's boss bar, action bar, titles, subtitles, toasts, and advancement pop-ups in Aurora's HUD editor (`VanillaHudLayout`, `VanillaHudModule`, `VanillaBossBarMixin`, `VanillaHudOverlayMixin`, `VanillaToastMixin`) so each can be moved, scaled, or hidden independently across 26.1.2, 26.2, and 26.3. Checked in a disposable 26.3 singleplayer dev client across settings detail (0.42/0.85 opacity and 854×480), HUD editor off/on, live moved & scaled overlays, and selective hiding.

Roadmap steps 1–3 and the 26.1.2, 26.2 and 26.3 ports are merged through PRs
#1–#12. The owner authorized autonomous GitHub integration and publication.

Step 5 is merged in [PR #13](https://github.com/kalibsolomon-pixel/Aurora-Client/pull/13): core → ports, with all
Minecraft/Fabric code and resources under `adapters/minecraft-26.3/`. Five ports
serve real core consumers: draw coverage, text measurement, frame clock, key
state and world identity. Every mixin and the entire glass renderer belong to
the adapter. Root Gradle commands and the native UI pipeline are preserved.

Step 5.5 adds the 26.1 and 26.2 adapters to this tree, with 188 shared
Minecraft-facing implementations and common assets. Version-specific rendering,
mixins and input stay in native adapters. One shared registry owns all 39
features. `python tools/parity.py` builds/tests all three lines; each output is
isolated under `build/minecraft-<version>/libs/`.

The Java suite discovers 660 tests on 26.1.2/26.2 and 662 on 26.3 (one opt-in
stress test skipped per line). All 42 Python release/default/parity tests pass.
The build checks native dependency exclusion and shared/native source ownership,
and includes core and ports in each runtime artifact.

## Distribution

The published checkpoints have independent source commits and release checks:

| Minecraft | Aurora | Maintenance after publication |
|---|---|---|
| 26.1.2 | [3.2.2](https://github.com/kalibsolomon-pixel/Aurora-Client/releases/tag/v3.2.2) | Full parity from the next release (ADR 0008) |
| 26.2 | [3.2.3](https://github.com/kalibsolomon-pixel/Aurora-Client/releases/tag/v3.2.3) | Full parity from the next release (ADR 0008) |
| 26.3 | [3.3.0](https://github.com/kalibsolomon-pixel/Aurora-Client/releases/tag/v3.3.0) | All feature work; includes step 5 |

There was never a universal-JAR decision, and ADR 0008 keeps it that way. The
owner's 2026-11-11 revision goes further than the earlier checkpoint publication:
from the next release onward, artifact availability and feature maintenance are
joined — every line from 26.1 onward builds from one source tree and receives all
feature work. Published JARs stay immutable; parity applies to future releases.
Filenames identify both Aurora and Minecraft versions. Release tags remain
`v<aurora-version>`; every artifact has its own exact source and digest.

All three versions are published. Each immutable JAR passed anonymous
redownload and digest/identity checks. [Published artifact identities and
acceptance](releases/PUBLISHED_2026-11-11.md) record the exact source and SHA-256.
Every publication uses the protected release workflow and a verified draft.
The Latest label selects 26.3; PR #14 applies that choice at publication too.

[3.4.0](https://github.com/kalibsolomon-pixel/Aurora-Client/releases/tag/v3.4.0)
is published and Latest: all three lines built from one commit,
[7b51088](https://github.com/kalibsolomon-pixel/Aurora-Client/commit/7b510887c67a3884bd1ef300c7ac344d4a027505).
Publication required all fourteen native matrix rows and the unchanged overhead
gate, bound to runtime/build/pin inputs, then verified every JAR through handoff,
draft and anonymous public redownload. [Artifact identities](releases/PUBLISHED_2026-11-11.md#340-parity-release).

3.5.0 is the next parity release: step 6 shader-drawn shapes, the settings,
profiles, search, text, title-screen and key-combo work, the ADR 0009 performance
optimizations and the UI polish overhaul ([notes](releases/RELEASE_NOTES_3.5.0.md),
[overhaul report](reports/2026-11-14-ui-polish-overhaul.md)). All fourteen matrix rows
passed with six reports each and every judged run within budget
([evidence](releases/evidence/3.5.0-parity.json)); it publishes through the protected
workflow.

3.5.1 follows it: Pulsar analytics (PR #39), the Scoreboard HUD, Armor HUD strip,
alert HUD modules and Hit Color styles (PR #36), and the owner's pre-release polish
(PR #40: one window size, accurate hit areas, centred crosshair previews, the reload
overlay/title fade switches and entity smoothing removed, Better Hitreg trimmed to its
custom delay) ([notes](releases/RELEASE_NOTES_3.5.1.md)). All fourteen matrix rows
passed with six reports each; judged runs 0.129-0.150 ms mean and at most 0.234 ms p95,
zero fills and glass ([evidence](releases/evidence/3.5.1-parity.json)). A first matrix
attempt found that profiles saved before 3.5.1 logged a warning per removed field; restore
now skips those retired fields quietly.

3.6.0 follows: the Animations pass and regroup (PR #67), SDF-only text, hitboxes and block
outlines (PR #68), HUD colour options (PR #69), Potion HUD vanilla layouts (PR #71), the
rounded-tip logo (PR #70) and the UI depth/bevel/polish work since 3.5.1
([notes](releases/RELEASE_NOTES_3.6.0.md)). The matrix found three release blockers from
SDF-only lines and text, fixed before publication: hitboxes for every loaded entity (now
frustum-culled, with cheaper boxes), VulkanMod recompiling the line and shape shaders every
frame (now cached), and ImmediatelyFast garbling vanilla text with SDF text (SDF text now
stands down for it). Fixtures are now native worlds per line. All fourteen rows passed with
six reports each; judged runs at most 0.222 ms mean and 0.290 ms p95, zero fills and glass
([evidence](releases/evidence/3.6.0-parity.json)).

3.7.0 follows: Gradient Dithering (PR #83), Smooth Fog and the 26.2 performance pass (PR #89),
Entity & Block-Entity Occlusion Culling (PR #84), GUI Glint Baking, Flat Faces, Layering and
Left-to-Right Text fast paths (PR #78–#80, #85, #87), SDF compatibility with ImmediatelyFast
and RRLS (PR #81, #86), fixed square corners and layout/spacing polish (PR #73, #88), the
Pulsar PvP analytics/performance/layout/v7 HTML report overhaul (PR #90), and an overall world,
HUD, font and UI-baking efficiency pass ([notes](releases/RELEASE_NOTES_3.7.0.md)). The release
matrix caught a 26.2 native Vulkan crash under Sodium 0.9.2 when Gradient Dithering/Smooth Fog
twins dropped `"sodium"` from their pipeline namespace (fixed by preserving `aurora_sodium` so
Sodium's `VulkanPipelineMixin` attaches push constants). All fourteen rows passed with six
reports each; judged runs 0.034–0.089 ms mean and at most 0.167 ms p95, zero fills and glass
([evidence](releases/evidence/3.7.0-parity.json)).

3.8.0 follows: Vanilla HUD Control, Inventory HUD, Chat Suite, Media Controls & Now Playing HUD,
Pack Tweaks post-processing/skies/local time & weather, Tab List & Nametag polish, Per-Sound Volume,
Streamer Mode, Keybind Conflict Warnings, Per-Server Profile Auto-Switching, Screenshot Manager,
Folder-Aware Resource Pack Manager, Pulsar accuracy pass, and Ephemera `ObjectShare` integration
([notes](releases/RELEASE_NOTES_3.8.0.md), [evidence](releases/evidence/3.8.0-parity.json)).

3.8.1 follows: Ephemera-first engine & frame-pacing ownership (PR #111), Smooth Camera removal plus
the stress-test pass fixing 11 feature Reset prefix lists, dead/hidden settings, Escape popup
handling, Aurora-only text-scope hitboxes, and small-scale Waypoint Editor / Resource Pack Manager
layouts (PR #109), and retiring `smoothCamera` / `smoothCameraStrength` in `ProfileFieldSet` so
existing profiles upgrade without warnings ([notes](releases/RELEASE_NOTES_3.8.1.md)). All fourteen
rows passed with six reports each; judged runs 0.031–0.128 ms mean and at most 0.229 ms p95, zero
fills and glass ([evidence](releases/evidence/3.8.1-parity.json)).

3.9.0 follows: the owner's 2026-11-22 feedback (PR #113): Media Controls and Now Playing working on
Windows, with album art; Per-Sound Volume applied to one-shot sounds; one health-plus-absorption
number on the Health HUD; square Armor HUD slots growing away from the centre; a Potion HUD that
wraps toward the centre (Effects per Line); SDF text edge fix; Pack Tweaks' Lava Clarity fire switch
retired into Hide Fire Overlay; modifiers that start combos waiting for release. Also the
unbound-item-components crash fix (PR #114) ([notes](releases/RELEASE_NOTES_3.9.0.md)). All fourteen
rows passed with six reports each; judged runs 0.034–0.114 ms mean and at most 0.263 ms p95, zero
fills and glass ([evidence](releases/evidence/3.9.0-parity.json)).

Window frames now use a cached, content-clipped bevel with one fixed strength, a little lighter than the former Subtle level (the Window Bevel setting and its levels were retired on 2026-11-18). Below full opacity the rim stays as clear as the window and the glass pass bends the backdrop across the frame band; glass, gem and metal were captured in the 26.3 client on OpenGL and Vulkan and await owner review. Since 2026-11-18 every window keeps a 10 px frame room, so the band is never squeezed by content (it may take up to 6 px) and content keeps a 4 px gap from it (DESIGN_LANGUAGE "Frame room").

## Acceptance and contributor readiness

See [step 5.5 evidence](port/step-5.5.md), [step 5 evidence](port/step-5.md)
and [the compatibility matrix](MOD_MATRIX.md).
Iris is tested only on OpenGL. Native Vulkan has its own baseline/performance
rows. Beryl is a possible separate shader-mod check; no Fabric 26.3 version was
returned by its Modrinth version query, so it is not advertised as tested.

The automatic benchmark now waits for the world with no screen open before
starting. It keeps two warm-up runs, four judged runs and the unchanged 0.25 ms
mean / 0.40 ms p95 budget. Earlier failed measurements remain in the history;
passing results must name the current candidate and fixture.

Contributor guidance, issue forms, a PR template, an updated architecture guide
and a security reporting policy are included. GitHub private vulnerability
reporting is enabled. Older engineering reports and superseded status documents
are retained in `docs/history/`.

## Remaining direction

Step 6 (shader-drawn shapes) is merged, PR #21: live rounded shapes draw
through a native GUI pipeline by default, with a
CPU fallback. Evidence and its limits are in the [step 6 report](reports/2026-11-11-step-6-baseline.md).
Caches are retained until measurement supports removing one. Step 7 (Apollo restrictions, then friends/status,
possible NeoForge and limited optimizations) remain future work. No new gameplay
features are included in this restructure. Apollo licensing/detection needs
verification before implementation. Better Hitreg author-permission provenance
and HitColor's missing repository LICENSE remain documented follow-ups; existing
notices follow the published upstream licenses and are not new permission claims.

## Step 8: client optimizations (in progress)

[ADR 0009](adr/0009-client-optimizations-aurora-owns.md) supersedes ADR 0005's scope rule: measured
optimizations ship on by default under the `performance` feature, stand down for mods that
own their area, and only the ones with a visible trade-off get a row on Settings → Performance.
PR 1 (`feat/perf-foundation`) adds the stand-down mechanism and four loading optimizations;
[evidence](reports/2026-11-12-perf-loading.md). PR 2 (`feat/perf-cpu`) adds four per-frame CPU
optimizations and records two that were measured and dropped; [evidence](reports/2026-11-12-perf-cpu.md).
PR 3 (`feat/perf-atlas`) frees uploaded sprite mipmaps and drops animated sprite ordering;
[evidence](reports/2026-11-12-perf-atlas.md). PR 4 (`feat/perf-maps`) caps the minimap's motion
repaints at 60 Hz and drops two map ideas that had nothing to save; [evidence](reports/2026-11-12-perf-maps.md).
PR 5 (`feat/perf-world-render`)
records the world-render measurements: the section copy cache and particle culling were dropped,
and the GPU-side items wait for GPU-bound tester evidence; [evidence](reports/2026-11-12-perf-world-render.md).
`perf/client-throughput` profiles the owner's reference instance: faster world map region I/O,
capture queue and north-up repaints, no per-frame font scale scan, hitboxes only at vanilla render
distance, GUI Layering and Left-to-Right Text fast paths, and HUD/crosshair trims;
[evidence](reports/2026-11-20-perf-reference-instance.md). `perf/vanilla-hotpaths-2` follows with
block-transferred map regions, fewer GUI layer and text allocations, and startup stand-down of those
optimizations for Dynamic FPS, Polytone, Gnetum, Modern UI and Caxton;
[evidence](reports/2026-11-20-gui-text-map.md).
GUI Glint Baking redraws glint-only GUI items when the glint has moved, not every frame
(never at glint strength 0), pixel-identical; [evidence](reports/2026-11-20-gui-glint-baking.md).
Entity & Block-Entity Occlusion Culling (`perfEntityOcclusion`) skips extracting entities and
contained block entities that a settled full-cube opaque terrain layer certainly hides from the
camera, stands down for `entityculling`, and is pixel-identical across all 11 verification scenes
on 26.1.2 OpenGL, 26.2 OpenGL and 26.3 native Vulkan; [evidence](reports/2026-11-20-entity-occlusion.md).
GUI Flat Faces skips exact zero-area (edge-on) quads inside GUI item atlas draws across all three
lines, pixel-identical; [evidence](reports/2026-11-20-gui-flat-faces.md).
Performance follow-ups (`fix/perf-followups`): `HitboxRenderer` follows main-world visible entities after
vanilla, Sodium, EntityCulling and Aurora occlusion (`HitboxVisibleEntitiesMixin`); `CrosshairRenderer` and
target hitboxes reuse vanilla's block-clipped `crosshairPickEntity`; Item Physics tracks frame-rate-independent
landing ease and settled state per `ItemEntity` (`ItemPhysicsSmoothing`); and `AuroraTitleScreen` waits for
`EntityModelSet` under RRLS.
Aurora 26.2 performance pass (`perf/aurora-262-pass`): upgraded `EntityOcclusion` with direct chunk-section
reads, cross-frame `terrainStamp` block-solidity caching, exact sweep-decision caching and tick/packet-invalidated
leash tracking (`OcclusionLeashMixin`), cutting its render-thread share from 8.8–12.9% to 0.0–1.0% on the
owner's 26.2 instance; added 26.2 OpenGL dispatch caching (`perfGlDispatch`: texture mip-range, per-pass
`glDrawBuffers`, single-target `FrameBufferCache`, `VertexArrayCache$Separate` identity lookup; -3.6% to -7.8%
median frame time, pixel-identical); measured and dropped `DynamicAtlasAllocator` and 26.1/26.3 OpenGL dispatch
hooks that showed no reliable gain; [evidence](reports/2026-11-21-aurora-262-perf-and-smooth-fog.md).
Ephemera integration (`feat/ephemera-integration`): yields overlapping ADR 0009 engine hooks to `ephemera`,
connects zero-compile-coupling Fabric `ObjectShare` bridges (`EphemeraBridge`) for direct SDF glyph/line
native fast-write delegation and live `Ephemera Engine` controls under Settings → Performance, eliminates
steady-state `WeakHashMap` locking in `AuroraFontQuality.beginFrame`, increases `WorldGeometryBuffers`
staging capacity (`64 KiB`), and hoists config/stationary-entity checks in `HitboxRenderer`.
In-house hot paths (`perf/inhouse-hot-paths`, stacked on the Ephemera integration): SDF glyph texture
coordinates are computed once per atlas cell instead of per draw; world lines pack their colour once per
line or box (`setColor(int)`, byte-identical); `WorldGeometryBuffers` reuses per-type slots instead of a map
rebuilt every frame (`RenderType.prepare()` still runs per frame: it captures the model-view and scissor);
the Ping HUD keeps its label until the latency changes; and the copied diagnostics report gains an
`Ephemera:` line with its status and whether the direct fast-write bridge is active. The allocation-free
frustum fallback was not done: 26.2/26.3 expose only `Frustum.isVisible(AABB)`, so it would need a new mixin.

## Next work

The rendering/HUD follow-up from `feat/render-hud-quality` is merged (PR #36):
the odd-radius motion blur alias is reproduced and fixed across the three
OpenGL adapters and native 26.3 Vulkan; unused font preparation is deferred
with measured reload gains, while the selected family stays eager. The reload
overlay and title fade switches were later removed (2026-11-15), so resource
reloads behave as vanilla. [Investigation and evidence](reports/2026-11-12-render-hud-quality.md)
track the remaining HUD work, FPS comparison and acceptance checks.

Step 5.5 (ADR 0008) is complete; step 6 (shader-drawn shapes) is merged, PR #21.
The earlier 26.3 OpenGL overhead failure was resolved before 3.3.0 publication;
the failed historical measurements remain documented. Broader release validation
should add a packaged-client launcher boot, multiplayer and another GPU or
lower-end machine. Vulkan shader-mod support requires a separate pinned build
and shader-pack checks. A broken-feature indicator on grid cards remains a
small UI proposal, not part of the completed restructure.

The SDF line option for hitboxes and block outlines (PR #61, off by default) and the council UI polish pass (PR #62) are on master for the next release. The council pass ended at a unanimous 8 overall from five vision reviewers after 15 rounds, with every round's click and hit-box checks passing ([report](reports/2026-11-18-ui-polish-council.md)).
