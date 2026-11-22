# Aurora Client 3.8.0

One JAR per Minecraft version: **26.1.2**, **26.2**, and **26.3**, built from the same
source commit. Install the one matching your Minecraft version with Fabric Loader
and Fabric API. Java 25 is required.

## New

- **Vanilla HUD Control** (Modules grid, off by default, vanilla-equivalent): move,
  scale, or hide Minecraft's boss bar, action bar, titles, subtitles, toasts, and
  advancement pop-ups independently from Aurora's HUD Editor.
- **Inventory HUD** (Modules grid, off by default, vanilla-equivalent): draggable,
  scalable overlay of your 27-slot main inventory (and optional 9-slot hotbar)
  with Vanilla (`inventory.png` / `container/slot`), Solid, and None backgrounds,
  compact packing, optional stack-count hiding, and empty-state auto-hide.
- **Chat Suite** (Modules grid, off by default, vanilla-equivalent): compact
  duplicate messages (`[xN]`), local timestamps (`HH:mm`, `HH:mm:ss`, 12-hour),
  inline online player chat heads via vanilla `PlayerSprite`, in-chat `Ctrl+F`
  search filter, right-click / `Ctrl+Click` message copy, hold-to-peek keybind
  (`key.aurora.chat_peek`), configurable history up to 5,000 lines, and atomic
  per-server chat history persisted off-thread. Never sends messages or packets.
- **Media Controls & Now Playing HUD** (Modules grid, off by default, cosmetic):
  cross-platform desktop playback keybinds (Play/Pause, Next, Previous, Volume
  Up, Volume Down — dispatched asynchronously off the tick thread on Windows,
  macOS, and Linux) plus a Windows-first Now Playing HUD overlay (WinRT GSMTC,
  polled off-thread only when enabled).
- **Pack Tweaks Additions:** Blaze3D fullscreen world post-processing (frame-rate-independent
  temporal motion blur and color saturation, zero shader cost at neutral defaults),
  client-side Local Time and Local Weather (off by default, vanilla-equivalent),
  Custom Skies (presets, custom sky color, sun/moon/star toggles, star brightness,
  void dark-disc hiding), first-person Fire Overlay height/opacity/hide, and Hurt
  Camera toggle/strength.
- **Tab List & Nametag Polish:** Ping's tab-list slot widens automatically so
  3-digit latencies never overlap player names, with optional `"ms"` suffix and
  text shadow; Settings → Display gains Tab List (hide server header/footer/player
  heads, highlight own row, custom background opacity) and Nametags (show own
  nametag in third person, text shadow, custom background opacity, scale).
- **Per-Sound Volume Mixer** (Settings → Sound, cosmetic, 0–100% attenuation only):
  lower or mute individual Minecraft sounds through a searchable mixer with
  customized, recently played, and common-sound views. Strictly clamped to
  0–100% so sounds can never be amplified above vanilla.
- **Streamer Mode** (Settings → Display, off by default, local display-only):
  masks your username with a custom alias across title screen, tab list, chat,
  and nametag, replaces your own skin with Steve, masks server addresses on
  Direct Connection, Edit Server, ConnectScreen, raw-IP server list entries, and
  chat, hides coordinates on Info HUD and Minimap, and optionally replaces other
  players' nametags, tab-list names, and chat mentions with customizable numbered
  and colored opponent labels.
- **Keybind Conflict Warnings:** surfaces duplicate bindings across Aurora's
  keybind rows, profile hotkeys, and Minecraft's vanilla Controls screen
  (including profile hotkeys and default-vs-default collisions involving Aurora
  bindings, while keeping F3 debug chords and plain-vs-combo `KeyCombo` pairs
  distinct).
- **Per-Server Profile Auto-Switching:** bind server addresses (hostnames, IPs,
  ports, and `*` wildcards such as `*.hypixel.net`) in Profile Options; joining
  a matching server switches to the most specific bound profile and disconnecting
  restores your previous profile unless manually overridden mid-session.
- **In-Game Screenshot Manager** (Settings → General → Screenshots): themed
  gallery for `screenshots/` with async thumbnail/preview decoding, filename
  search, inline rename, press-again delete confirmation, OS clipboard copy, and
  non-destructive drag-to-crop PNG export.
- **Folder-Aware Resource Pack Manager** (Settings → General → Resource Packs &
  Options → Resource Packs): discovers packs inside organizing subfolders under
  `resourcepacks/` while keeping top-level vanilla IDs untouched, with folder
  navigation, breadcrumbs, recursive search, async pack icons, and load-order
  reordering.
- **Pulsar Accuracy Pass:** fixes inclusive exchange/round end-boundary combo
  splitting, prevents live same-tick trade combo inflation, pairs knockback
  `VELOCITY` packets across `[tick - 1, tick + 1]` without double-claiming and
  with strongest-impulse tie-breaking, aligns combo sprint-reset attribution, and
  adds fact-backed **Combo Rating** and **Jump-Reset / Knockback Control Rating**
  to the in-game dashboard and offline HTML reports.

## Ephemera 1.0.0 Integration & Compatibility

- **Zero-compile-coupling Fabric `ObjectShare` integration** across all three
  Ephemera bridges:
  - `ephemera:fast_write`: delegates Aurora's SDF glyph, SDF wireframe box, and
    world-line writes directly into Ephemera's native vertex writers.
  - `ephemera:frame_pacer`: cooperative frame-pacer handshake (`EphemeraPacingBridge`,
    `AdaptivePacerCalibrator`) so Aurora and Ephemera never double-sleep, Ephemera
    receives Aurora's exact effective FPS deadline for adaptive terrain upload
    budgeting, both pacers share OS park-overshoot calibration, and `ReflexScheduler`
    accounts for Ephemera's asynchronous Vulkan swapchain presenter.
  - `ephemera:settings`: embeds Ephemera's rendering options grouped by category
    (`Terrain`, `Culling`, `Pacing`, `Memory`, `Visuals`, `Diagnostics`) under
    **Settings → Performance → Ephemera Engine**, and reports Ephemera backend,
    fast-write, and frame-pacer status in Diagnostics.
- **Automatic mixin stand-downs:** Aurora yields 19 overlapping engine/culling/GUI
  mixins to Ephemera automatically while preserving Aurora's SDF text, item/glint
  baking, glass, post-effects, and HUD modules.
- **Verified live with Ephemera 1.0.0 (Minecraft 26.2, RX 9070 XT):**
  - OpenGL Multi-Draw: `0.061–0.065 ms` mean, `0.099–0.147 ms` p95 Aurora CPU/frame,
    `0.01 ms` pacer jitter, 0 warnings/errors.
  - Native Vulkan `VK_EXT_mesh_shader (Task+Mesh)`: `0.079–0.127 ms` mean,
    `0.143–0.271 ms` p95 Aurora CPU/frame, `0.01 ms` pacer jitter, 0 warnings/errors.
  - Full all-features Vulkan stress pass (Ephemera Task+Mesh, HUD Frame Cache,
    Underwater Clarity, Atmospheric Horizon, Dynamic Lights, Smooth Chunk Fade,
    Enhanced AO, 8× Anisotropy + Aurora Motion Blur, Saturation, Custom Sky,
    Smooth Fog, Gradient Dithering, Local Weather, Inventory HUD, Vanilla HUD,
    Chat Suite, Streamer Mode, Per-Sound Volume, Frame Pacer, Low Latency):
    `0.084 ms` mean / `0.165 ms` p95, `0.00 ms` jitter, 0 warnings/errors.

## Performance & Fixes

- **Hot-path optimizations:**
  - Cached SDF cell UVs once per glyph, packed world-line colors once per draw,
    reused identity geometry-batch slots in `WorldGeometryBuffers`, and cached
    Ping HUD strings.
  - `WorldPostEffectRenderer` reuses immutable cached `GpuBuffer` uniforms (zero
    per-frame UBO/VMA churn on OpenGL and Vulkan), uses a single scratch texture
    in saturation-only mode (50% less VRAM), and immediately retires textures when
    post-effects are disabled.
  - `ReflexScheduler` eliminates `Long` boxing and caches weighted GPU estimates;
    `FramePacing` eliminates per-frame `PostSwap` allocations and adds anti-sawtooth
    soft phase recovery on mildly late frames.
  - `SoundVolumeMixin`, `ChatComponentSuiteMixin`, `InventoryModule`,
    `NowPlayingModule`, `InfoModule`, and `KeybindConflictLookup` cut per-frame
    and Controls-screen allocations/scans (zero sound-hook allocations when
    Per-Sound Volume is off; single inventory scan per frame; O(N) Controls
    conflict lookup).
- **`AuroraFontQuality` GUI-scale fix:** fixed an infinite per-frame `rescale()`
  and log-spam loop triggered when moving or resizing the window to a new GUI
  scale while `perfDeferAuroraFonts` had 0 loaded providers needing replacement.
- **26.1.2 `NameTagShadowMixin` fix:** corrected the 26.1.2 target method to
  `NameTagFeatureRenderer.renderTranslucent`, verified across all four 26.1.2
  matrix rows.
- **Overhead budget (14-row full-parity GPU acceptance, 56 judged runs across
  26.1.2, 26.2, and 26.3 on OpenGL, native Vulkan, and VulkanMod):**
  **0.035–0.089 ms** mean and **<= 0.170 ms** p95 Aurora CPU per frame (budget:
  0.25 ms mean / 0.40 ms p95), **0.0** UI fills, **0.0** glass captures, all
  mixins healthy and zero Aurora warnings or errors across every row.
