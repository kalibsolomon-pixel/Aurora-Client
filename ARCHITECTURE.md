# Aurora GUI System — Architecture Snapshot

A code-verified snapshot of Aurora's GUI system, written for a new session getting
oriented. Companion documents: `AGENTS.md` (repo root — the broader project guide: feature
catalog, build facts, config/profile system, known-work list) and `GUI_AUDIT.md` (this repo
root — findings and prioritized plan from the 2026-09-04 full GUI audit).

---

## 0. READ THIS FIRST — repo state (verified from git, 2026-09-08)

**`master` is the canonical, current branch.** Everything the GUI work depends on is committed
there: the mod-wide "glass everywhere" rollout, the ResourcePack browser glass wave, the
**"Reset kills glass" session-latch fix**, the **Frosted/Wireframe GlassStyle**, the
2026-09-04 GUI-audit deliverables and their B5–B19 fix wave, the `GlassSurface` helper with
structural pre-dim layering — COMPLETE on every glass screen as of 2026-09-11
(Profiles/Waypoints → FeatureDetailScreen → AuroraScreen + pack browser), frost-radius-follows-opacity, the
Multiplayer `@Shadow` crash fix (merged 2026-09-07), and the **deferred post-dim rim
finish** (2026-09-08, §3/§6). The working tree is clean. The formerly unmerged
`fix/glass-session-latch-and-glass-style` branch landed long since (its `AGENTS.md`
now lives at the repo root). The three rim/lighting *experiment candidates* were resolved
2026-09-08: **A (`cand-a-rim-post-dim`) was chosen and merged** (rim finish deferred past
the dim via a queue in `GlassSurface`); **B and C were discarded** — B boosted the shared
`Lighting.raised()` preset with no real effect on the target screens but a real side
effect on every other raised control mod-wide. No unmerged local branches remain.

The live dev config (`run/config/aurora.json`) currently runs `glassStyle: TRANSPARENT` with
`backgroundOpacity: 0.1` — the user is actively exercising the flat-style escape hatch.

---

## 1. System map

```
Theme system (source of truth)
  theme/ThemeDefinition      persisted: accent, ThemeMode, ThemeRoundness, GlassStyle, backgroundOpacity
  theme/ThemeManager         lifecycle: reload() / sync() (per-tick 6-field dirty check) / generation stamp
  theme/ThemeResolver        two paths: PaletteEngine.derive (theme on) | fixed factory palette (theme off)
  theme/PaletteEngine        pure function: accent + mode → ~40 tokens; HSL, contrast-checked text
  theme/ResolvedTheme        immutable ordinal-indexed int[]; color(token) is one array read; project()→AuroraTheme
  theme/ThemeToken           ~40 role-named tokens (ACCENT*, SURFACE*, ON_*, BORDER*, SEMANTIC_*, …)
  theme/ThemeRoundness       ROUND/SLIGHTLY_ROUND/SQUARE → RADIUS 10/6/0, RADIUS_LARGE 14/8/0, RADIUS_SMALL 6/3/0
  theme/GlassStyle           FROSTED/TRANSPARENT — rendering-technique switch (see §3)
  util/AuroraTheme           LEGACY projection facade: ~30 files read its statics; ResolvedTheme.project()
                             is the ONLY writer. Names lie: IOS_BLUE/ACCENT_* hold OnePlus RED.

Glass material (per-element, per-frame)
  ui/render/blur/BlurPanelRenderer   capture→blur→composite→readback→blit pipeline + rim-mask cache
                                     + output-texture pool (24) + GlassStats instrumentation
  ui/render/blur/BlurTestScreen      dev harness (unbound keybind blur_test)

Shared components (ui/component/)
  Widget (base: layout/renderShapes/renderOverlay/shapeFingerprint/mouse hooks)
  Button (painter; flat primary/secondary/destructive + GlassStyle OFF/NEUTRAL/STAINED)
  ButtonWidget (AbstractButton adapter — zero paint of its own)
  ToggleSwitch, Slider, SegmentedControl (glass-capable), RoundedPanel, ColorSwatch, ThemedScreen marker

Semantic interaction (ui/interaction/) — Phase A, complete: every ordinary
  button-like control participates (custom-painted via SemanticActionControl,
  vanilla-backed via ButtonWidget.semantic); see §4 for topologies + deferrals
  SemanticAction         name/description/state/enabled metadata + the single activation gate
  SemanticActionControl  invisible Screen child: focus traversal, Enter/Space, narration, pointer adapter
  SemanticFeedback       semantic sound vocabulary; MinecraftSemanticFeedback maps ACTIVATION to UI click

Render utilities (ui/util/)
  RenderUtil        AA fills/outlines/circles at device-pixel precision + beginCapture/RectSink + fills counter
  UiLayerCache      static-chrome raster cache (NativeImage→DynamicTexture, version-keyed)
  AuroraFontRenderer bundled-TTF Style application, scoped by isRenderingAuroraUI flag
  MaterialIconRenderer FreeType-rasterizes icon glyphs at exact device-pixel size (bypasses NEAREST atlas)
  ItemSpriteRenderer, CanvasTexture, Animation (exponential approach)

Screens (screen/) — see §4 inventory
screen/setting/    ~20 FeatureSetting row widgets (the settings vocabulary)

GUI-adjacent mixins (mixin/) — see §5
config/AuroraConfig   public fields = schema; async GSON saves; resetByPrefix reflection
module/ModuleManager  35 hardcoded presentation cards for the Mods grid (drifts from FeatureRegistry — see audit)
modrinth/             keyless REST client + icon cache (feeds pack browser)
```

## 2. Theme/token system — the data flow

```
AuroraConfig.theme (ThemeDefinition)
   │  ThemeFeature.onTick → ThemeManager.sync()   6-field dirty-check per tick
   ▼
ThemeManager.reload() → ThemeResolver.resolve(def, themeEnabled)
   ▼                        ├─ PaletteEngine.derive(accent, mode)   (theme on)
   ▼                        └─ factoryPalette()                      (theme off — fixed table)
ResolvedTheme (immutable, volatile in ThemeManager; generation() stamp for pixel caches)
   ├─ color(token)          one array read, zero alloc, any thread
   ├─ surfaceColor(token)   token RGB + WINDOW_FILL's alpha (how cards/rows inherit panel translucency)
   ├─ stainedTint()         accent RGB at max(WINDOW_FILL alpha, 140) — fixed visibility floor
   ├─ rimPastel()           accent lightened 65% toward white (derived once per resolve)
   └─ project()             → AuroraTheme statics (the ONLY writer; ~30 reader files)
```

Key invariants (protected — see AGENTS.md §5/§6 for the full rules):

- **Background Opacity is applied at exactly one point**: `ThemeResolver.applyBackgroundOpacity`
  stamps it onto `WINDOW_FILL`'s alpha channel only. Nothing else in the pipeline multiplies
  opacity. This rule exists because a second application point killed the previous glass system.
- `ThemeManager.generation()` bumps once per reload; `UiLayerCache` versions and any
  theme-derived pixel cache key off it.
- PaletteEngine surfaces (`SURFACE`, `SURFACE_VARIANT`, …) resolve **opaque** (alpha 0xFF).
  Anything that must be translucent composes with `surfaceColor()` or the `WINDOW_FILL` alpha.
  (This is load-bearing for the glass tint contract — and the pack-browser cards violate it;
  see `GUI_AUDIT.md` B2.)
- Semantic tokens (`SEMANTIC_ERROR/SUCCESS/WARNING`) are fixed-hue, NOT accent-derived — but
  adoption is sparse (12 references mod-wide; the HUD layer doesn't use them at all).

### GlassStyle (Frosted / Wireframe) — the second non-color token

FROSTED (default) = the glass pipeline runs. TRANSPARENT = `BlurPanelRenderer.renderPanel`
declines in a single early-return guard **before any GL work**, so every glass consumer takes
the flat translucent fill it is already required to draw (the fallback contract). No call site
knows the setting exists; Corner Style and Background Opacity keep their meanings in both
styles. TRANSPARENT therefore also skips the entire capture/blur/readback cost — a real perf
escape hatch, currently in active use in the dev config. Display label is "Wireframe"
(renamed from "Transparent" 2026-09-12); the enum constant and persisted value stay
`TRANSPARENT`, so existing configs parse unchanged — label change only, no migration.

## 3. Glass material pipeline (`BlurPanelRenderer`, 1755 lines)

Read the class javadoc before touching anything in it — it encodes hard-won lessons.

**Pipeline per panel, per frame** (live backdrop is never cached, by design):
`glBlitFramebuffer` capture of a padded region from the main render target (via a persistent
reader FBO wrapping the main color texture — 1.21.11's GUI records blits into a deferred
`GuiRenderState`, so the world lives in the MAIN target, not the ambient binding) → quarter-res
two-pass separable Gaussian on grow-only shared chain textures → composite pass (rounded-rect
SDF coverage + two lighting terms) → `glReadPixels` readback into a pooled `NativeImage`/
`DynamicTexture` (RGBA→ARGB Java loop) → ordinary `GuiGraphics.blit`.

**Lighting = exactly two terms** against one fixed light (`normalize(0.25, −1.0)`): a
full-surface directional gradient and a fixed-width directional border stroke. Raised vs
depressed = inversion of both terms. SDF-normal lighting, specular, refraction, bevels are
**out of scope by decision** — they sank the previous glass attempt.

**The rim is a two-half system** driven by the one Background Opacity value:
- IN-GLASS half: composite-pass stroke blends its target color toward the panel's own base as
  opacity rises (`uRimBlend`).
- ABOVE-FILL half: `drawRimFinish(g, x, y, w, h, radius)` — right after the caller's
  tint fill, or (inside a declared glass pass, 2026-09-08) deferred by `GlassSurface` to
  just after the dim — draws a DIRECTIONAL pastel-of-accent stroke (cached SDF-band mask
  texture per device rect+radius, 16-entry cap) with alpha = opacity². Solid pastel at
  100% opacity.

**Reliability machinery** (each entry fixed a real crash/bug — do not "simplify"):
- `permanentlyDisabled` latch: any unexpected GL failure disables glass for the session
  (callers take flat fallbacks). Deliberate safety net; no recovery path except shutdown —
  an open judgment call, see AGENTS.md §9.
- Screenshot (F2) interlock: `ScreenshotMixin` → `noteScreenshotGrab()` suppresses glass 400 ms;
  plus neutral PACK/UNPACK pixel-store state and zeroed pack-buffer bindings at pipeline
  entry/exit (a stale `PACK_ROW_LENGTH` was an out-of-bounds native write → real JVM crashes).
- GL error-queue drain before capture so the latch only fires on OUR errors.
- Rim-mask eviction: cold-only + destruction deferred to `beginFrame()` — destroying a texture
  mid-frame that a pending deferred blit still references was the "Reset kills glass" bug.
- Output pool: 24 pooled output textures, epoch-stamped per frame (`beginFrame()` from the
  render-tick mixin). Concurrent panels each need their own texture (deferred blits).
  **Exhaustion declines extra panels** → flat-fallback flicker; observed live on 2026-09-04
  (see `GUI_AUDIT.md` B1).
- Instrumentation: `-Daurora.glassStats=true` → per-phase ms/panel log line every 60 frames
  (capture/blur/composite/readGL/loop/upload/blit). Zero cost otherwise. Run this BEFORE any
  glass perf work.

**The glass integration idiom** — one implementation, `ui/component/GlassSurface`
(`GUI_AUDIT.md` R1; adopted by `ProfileManagerScreen`, `WaypointManagerScreen`, `Button`,
`EditBoxMixin` as of 2026-09-05; `AuroraScreen`, `FeatureDetailScreen`, the pack browser and
the setting widgets still carry ~8 inline copies of the same sequence, pending the approved
rollout):

```java
// ---- glass pass: EVERY glass surface, before the dim ----
GlassSurface.beginGlassPass();                     // opens the pass: rims defer from here
boolean glass = GlassSurface.control(g, x, y, w, h, radius);   // raised, WINDOW_FILL tint
//              GlassSurface.stainedControl(...)                // raised, stainedTint()
//              GlassSurface.container(...)                     // depressed, WINDOW_FILL
//   each = live-world gate → renderPanel(role lighting) → tint → (rim queued, not drawn)
//   clipped surfaces go through GlassSurface.enableScissor/disableScissor (not the raw
//   calls) so the deferred rim re-applies the clip
// ---- dim: the guarded phase boundary ----
GlassSurface.overlayDim(g, width, height);   // dim fill, then flushes the queued rims
// ---- content pass ----
if (!glass) { /* flat token fill + outline — the screen's own fallback chrome */ }
```

Surfaces painted with NO pass open (screens still on the legacy order) draw their rim in
place, exactly as before — the pass is what switches rim timing.

Conventions (violating these has caused real bugs — full list in AGENTS.md §6):
1. Single opacity application point (above). Two derivations ride that one value without
   being alpha applications: the rim's two halves, and (2026-09-07) the **frost radius** —
   `BlurPanelRenderer.frostRadiusPx` = requested × max(1/6, √opacity), so 0% is clear glass
   (4 px blur) and full 24 px frost arrives from ~70% up. The slider → fill alpha mapping is
   linear with no floor.
2. Containers DEPRESSED, controls RAISED (opposite of their container).
3. Neutral glass tints `WINDOW_FILL`; stained glass (accent, `stainedTint()`) ONLY for
   selected/primary elements. Full-width list rows are containers — mark selection with a
   compact accent element, never a whole-row stain.
4. Toggles, sliders, text entry (except search fields), and the color picker's editing
   surfaces are ALWAYS opaque.
5. Glass needs a valid capture source: a live world, OR — the one declared
   exception — a menu backdrop stamped for the frame via
   `GlassSurface.renderMenuPanorama` (draws the panorama +
   `BlurPanelRenderer.noteMenuBackdropDrawn()`; the title screen since
   2026-09-08, `AuroraScreen`'s no-world path since 2026-09-12).
   `renderBackground` overrides skip vanilla's backdrop sandwich when
   `GlassSurface.liveWorldBackdrop()` and call the panorama helper when no
   level is loaded; decline ⇒ complete flat look (fallback contract).
6. EVERY glass surface — containers and controls — draws its BODY in the glass pass BEFORE
   the screen's `OVERLAY_DIM` fill; content after. Structural everywhere (rollout complete
   2026-09-11): the screen brackets the pass with `GlassSurface.beginGlassPass()`, the dim
   goes through `GlassSurface.overlayDim`, and glass painted after it in the same frame is
   reported (dev: ERROR + stack trace; prod: one-shot WARN) — as are `overlayDim` with no
   pass, a pass never closed by `overlayDim`, and a surface under an untracked raw scissor.
   Inside a pass the rim finish is deferred and flushed right AFTER the dim (bodies veiled,
   highlights crisp). Widgets split via `Widget.renderGlassPass` / `GlassEditBox`. The two
   sanctioned above-the-dim exemptions (§9): `EnumSetting`'s expanded popup
   (`GlassSurface.aboveDimControl`) and the pack browser's detail modal
   (`GlassSurface.aboveDimContainer` + a `beginAboveDim`/`endAboveDim` zone bracket for its
   component-driven buttons).
7. Per-element UV: each panel samples only its own sub-rect (`uUvRect` / sub-rect contracts).
8. Content on stained glass uses `ON_ACCENT` (contrast-derived), never the accent itself.
9. Hover/focus on glass = caret/color/hairline ring/scale/wash — never a tint change; the
   glass rim replaces the outline.

## 4. Screen inventory (glass status verified in code, 2026-09-04)

| Screen | Glass status | Notes |
|---|---|---|
| `AuroraScreen` (main Mods+Settings) | Full | Depressed window + raised tiles/chips/layout buttons; `UiLayerCache` static chrome; fps-adaptive smooth scroll; own scrollbar. Sidebar chips/tiles use literal radii 5/6/4 (audit D-note). **Structural pre-dim pass (2026-09-11)**: window + chrome + tiles (tracked scissor, ROW priority) / Settings-tab inline `renderGlassPass` walk → `overlayDim` → cached blit + content; the cache never holds glass |
| `FeatureDetailScreen` (all 45 detail views) | Full | Depressed window (content-height-sized, scrolls with rows); per-setting glass via `FeatureSetting.renderGlassPass` (geometry-passing); Done/Reset shared glass buttons; `UiLayerCache`; fixed-τ=60 smooth scroll. **Structural pre-dim pass (2026-09-10, the pilot beyond the manager pair)**; `EnumSetting`'s popup above-dim via `aboveDimControl` |
| `ResourcePackBrowserScreen` | Full (2026-09-04 wave; cards flat 2026-09-08; structural pass 2026-09-11 — rollout complete) | Depressed glass sidebar (`surfaceColor(SURFACE)` tint) + detail modal; active tab's accent-lerp wash IS its glass tint (explicit-tint `control` overload, painted in the pass); tokenized radii; `EditBox` search; own toast/spinner/hover-map/scroll; cards deliberately FLAT (audit R9/B2 closed 2026-09-08: their opaque hover-lerp tint hid the blur, so per-card glass was pure cost + the main pool-exhaustion driver, B1 now halved — install buttons remain glass, DETAIL priority, driven in the pass via the shared `forEachVisibleCard` walk); dim at `0x55` strength via `overlayDim`'s argb overload; modal = above-the-dim layer (`aboveDimContainer` + `beginAboveDim` zone for its buttons) |
| `ProfileManagerScreen` | Full | Depressed neutral glass rows (selection = Active badge only), glass toolbar buttons; structural glass pass → `overlayDim` → content, `renderBackground` world-gated (audit B3 closed 2026-09-05); row glass pass still uncapped |
| `WaypointManagerScreen` | Full | Raised glass rows + header buttons; structural glass pass → `overlayDim` → content (audit B4 closed 2026-09-05) |
| `ColorPickerScreen` | Chrome-only, by design | Apply=stained / Cancel=neutral glass; editing surfaces + hex field opaque; literal radii 4/6; ~2000 fills/frame uncached |
| `HudEditorScreen` | Chrome-only | Two glass `ButtonWidget`s; opaque editor chrome by design |
| `EditBoxMixin` search fields (Particles, Item Scale, RP browser, Modules grid + vanilla multiplayer/world-select) | Full | Raised glass, focus = caret + accent hairline; THE canonical search bar |
| Setting widgets: `EnumSetting`, `KeybindSetting`, `KeyListSetting`, `ItemScaleSetting`(+), `ButtonSetting`, `SegmentedSetting` | Full (defaults on) | Glass pills/buttons; `PixelCanvasSetting` + `StringListSetting` buttons remain flat (inconsistent) |
| `ThemePreviewSetting` + `SegmentedControl` | Full (original pilot) | Card/chip/segments glass |
| `WorldMapScreen` | Not started | Flat: void fill + tile blits + `RoundedPanel` prompt + flat buttons; own pan/zoom drag |
| `AuroraTitleScreen` | Not started | 3 texture blits + flat themed buttons; no `RenderUtil` use at all |
| `BlurTestScreen` | Dev harness | A/B radii, synthetic capture FBO, lighting presets, `[S]` crash canary |
| Toggles, sliders, HUD modules, tooltips | Never glass, by convention | Opaque token surfaces |

Settings-widget vocabulary (`screen/setting/`, base class `FeatureSetting`): Boolean, Button,
Color (embedded HSL picker), DoubleSlider, Enum (dropdown popup; canonical Phase B states are
production-wide — all 26 rows; Phase C-2 hosts the AuroraScreen three semantically, see §6),
IntSlider, ItemScale (search + per-item slider stack), Keybind, KeyList, ParticleConfig
(+ParticleRow), PixelCanvas (crosshair editor, `CanvasTexture` cached raster + measured cost
benchmark), SectionHeader, Segmented, StringList, ThemeOpacity, ThemePreview. `FeatureSetting`
provides the shapes/overlay/glassPass render split, static focus registry, label-tooltip dwell
system, and detail-screen lifecycle hooks.

**Phase A (interaction foundation) is COMPLETE for the current architecture** —
every ordinary button-like control participates in the semantic contract
(`SemanticAction`/`SemanticActionControl` for custom-painted controls,
`ButtonWidget.semantic` for vanilla-backed ones). `ButtonSetting.semantic(...))`
is the only construction path; the legacy non-semantic constructors were removed
after a reference search confirmed zero remaining consumers (the migration made
them obsolete). Migrated topology families:

- **Feature-detail rows** (the pilot): Waypoints → Manage Waypoints… / Drop at
  Player Position, Alerts' Test Sound, and the full-rollout rows — Totem Pop's
  Reset Now, Better Hitreg's Reset Tracked Stats, Stats Overlay's Reset Stats /
  Reset Lifetime Fight Totals, Resourcepack Browser's Open Browser… — two or
  more semantic controls coexist with vanilla-tab focus on one screen
  (traversal order is vanilla's spatial top-sort; semantic rows interleave with
  the Done/Reset `ButtonWidget`s).
- **Manager row buttons** (`ManagerListScreen` + both subclasses — ALL row
  actions now): Waypoint Copy/Color/Delete and Profile Duplicate/Delete,
  per-row semantic controls keyed in lockstep with their Buttons, created
  lazily when the row first paints and unregistered via `removeWidget` in
  `reconcileRowCaches` (removal clears focus). Destructive actions take NO
  re-focus after a pointer activation — the action just removed their own row,
  so the refocus would target a control about to leave the screen. The base
  gained the registration lifecycle, now implemented by the narrow shared
  `SemanticControlHost`: `registerSemanticControl` (record pre-init / attach
  post-init / re-add after a resize's `rebuildWidgets`), a per-frame
  availability sweep in `render` (every control starts unavailable; the row
  passes re-mark what the clip band shows, so render truth and input truth
  agree), and the click-drops-semantic-focus rule from
  `FeatureDetailScreen`.
- **Screen-level manual action with dynamic enabled state**
  (`ProfileManagerScreen` Create): enabled exactly while the create field holds
  a non-empty name; the Button mirrors the gate with its existing disabled
  treatment. Deliberately `focusable=false, keyboardEligible=false` — the
  field's Enter owns this form's keyboard commit. Clicks within the button's
  bounds are consumed even when disabled (see consumption rules below).
- **Vanilla-backed Aurora-painted buttons** (`ButtonWidget.semantic`, now every
  consumer): the `SemanticAction` owns only the behavior callback plus
  narration metadata; pointer routing, keyboard activation, focus, the UI
  click sound, and base narration stay vanilla's. The action carries
  `SemanticSound.NONE` (vanilla plays `playDownSound` before `onPress` on both
  the mouse and selection-key paths) and an enabled gate that IS vanilla's
  `isActive()` — the authoritative gate — so inactive buttons (e.g. the title
  screen's Multiplayer when disabled) narrate their disabled state. Consumers:
  FeatureDetail Done/Reset, both managers' toolbar action + Done (Drop / New
  Profile), HudEditor's two buttons, ColorPicker Apply/Cancel, all five
  AuroraTitleScreen buttons, WorldMap's three toolbar buttons + the waypoint
  prompt's Create/Cancel, and the pack browser's Done. (`Button.primary(boolean)`
  / `ButtonWidget.primary(boolean)` were added so semantic construction can
  still select the flat-fallback variant.)
- **Pack browser card + modal actions**: per-(card, phase) install controls —
  IDLE/DONE accept and reject via the install handler's own phase rule
  (RESOLVING/DOWNLOADING reject) — plus the modal's Close. Phase-gated
  rejection is narrated (state line: "Downloading…" etc.) without mirroring a
  disabled look onto the painter (the disabled-treatment pilot is Phase B).
  Pruned cards unregister their controls (the manager-row rule). Modal focus
  containment: while the modal is interactive the covered chrome (search
  field, Done) is made `active=false`, which drops it out of traversal via
  vanilla's own `AbstractWidget.nextFocusPath` gate — the child-level
  mechanism that actually carries containment, because 1.21.11's
  `Screen.keyPressed` invokes the container's `nextFocusPath` NON-virtually
  (`invokespecial`) and a screen-level traversal override is never called (a
  harness-discovered vanilla fact). `openDetail` also clears focus off the
  obscured field (a focused covered EditBox silently swallows typing).

**Pointer-event consumption rules (the rollout's discovered hazards):** a
rejected activation inside a control's rect is CONSUMED-BUT-INERT wherever the
same geometry column could otherwise deliver the click to a DIFFERENT control
underneath — the Profile create row (a rejected Create click would fall onto
row 0's Duplicate) and the pack install buttons (a rejected "Resolving…" click
would fall onto the card body and open the modal) both consume. A rejected
click that merely misses (bounds miss) falls through as before. Selection keys
targeting a focused-but-disabled control are consumed for the same reason
(`SemanticActionControl.keyPressed` returns true after the rejected attempt).

**Intentional deferrals (classified, not omitted):** `AuroraScreen`'s manual
chrome (tiles/tabs/sidebar/layout buttons) waits for Phase C's
viewport/input-bounds coupling — availability-as-input-truth must not be built
on the screen's known render-clip/input disagreement; compact `+`/`x`/Clear/
Default composite-widget actions (`PixelCanvasSetting`, `ItemScaleSetting`,
the Effect list, `KeyList`/`StringList`-style removes) wait for the Phase C
icon-action primitive; pack-browser card bodies and sidebar tabs (selection/
navigation families, Phase B/C scope); `ThemePreviewSetting`'s two mock buttons
(a non-interactive preview — `C: not button-like`); `AbstractButtonMixin`'s
themed vanilla selection-screen buttons and `TitleScreenMixin`'s vanilla
`Button.builder` corner button (vanilla-owned routing already — `D: vanilla-
owned exception`; adapting them means migrating the mixin painter to
`ButtonWidget`, not adding semantic actions); `BlurTestScreen`'s stress
controls (dev harness). WIDGET-mode pointer routing on `SemanticActionControl`
remains unexercised by real consumers (every custom control here is manually
routed by its clipped screen) and kept.

### The three render layers (cache discipline)

Screens split drawing into: **shapes** (cacheable static geometry → rasterized once into
`UiLayerCache`, version = theme generation + content fingerprint + fb size + scale + glass
state), **glass pass** (live per-frame `GlassSurface` calls — ALL glass surfaces, containers
and controls alike, before the dim; rims queue for post-dim), **dim** (`GlassSurface.overlayDim`
— the guarded phase boundary; glass after it is reported, then the queued rims flush), and
**overlay** (text/hover/animation/flat fallbacks,
live). Every glass screen implements this order structurally (rollout complete 2026-09-11:
Profiles/Waypoints 2026-09-05 → FeatureDetailScreen 2026-09-10 → AuroraScreen + pack
browser 2026-09-11). `AuroraScreen` and `FeatureDetailScreen` use the full cache discipline
(the cache never holds glass — panels are texture blits that bypass the fill-capture sink);
ProfileManager, WaypointManager, and the pack browser currently re-render all shapes live
every frame (perf opportunity, audit P-note).

## 5. GUI-relevant mixins

Registered in `aurora.mixins.json` (`required: true`, `defaultRequire: 1` — a missed target is
a launch crash, not a silent skip):

- `EditBoxMixin` — replaces vanilla EditBox rendering with THE canonical themed/glass search
  bar on `ThemedScreen` implementors + multiplayer/world-select (gated by `customTitleScreen`).
  Caret always renders at text end (cursorPos not shadowed — audit B17).
- `AbstractButtonMixin` — themes vanilla `Button`s (exactly `Button.Plain` — the class
  `Button.builder().build()` constructs on 1.21.11; the former `Button.class` check never
  matched anything, fixed 2026-09-12) on the two vanilla selection screens; press-squash
  animation; label width cached forever (audit B18).
- `TitleScreenMixin` — replaces vanilla title with `AuroraTitleScreen` when enabled; else adds
  an "Aurora Settings" corner button.
- `MixinFont` (priority 1500) — swaps bundled TTFs into ALL text paths incl. measurement
  (`width`, `plainSubstrByWidth`); scoped OFF / Aurora-only (class-prefix + volatile flag) /
  ALL via `AuroraFontRenderer`. Perf-sensitive: per-measure allocations in the swapped paths.
- `ScreenshotMixin` — the glass/F2 interlock (`noteScreenshotGrab`).
- `ServerListDragReorderMixin` (549 lines, self-contained) — 3-phase animated server drag.
- `RenderSystemMixin` — replaces vanilla frame pacer for Aurora screens (`guiFpsLimit`) via
  `FramePacer`; uses a `com.aurora.client.screen` class-prefix predicate.
- `InGameHudMixin` (crosshair suppression), `SimpleOptionMixin` (force-set gamma), `MouseMixin`
  (zoom scroll), `MultiplayerServerListWidgetMixin` (numeric ping "42ms" on server rows,
  replacing vanilla's ping-bars icon — registered 2026-09-05 after target verification;
  fixed 2026-09-07 in merge `6726079`: as first registered it crashed the Multiplayer screen,
  because its `@Shadow`s for `getContentX/Y/Width` targeted getters that live on
  `AbstractSelectionList.Entry` two levels up, which Mixin cannot resolve — the fix drops the
  method shadows and calls the inherited public getters through a cast),
  empty-but-registered `WindowMixin`/`RenderTargetMixin` (deliberate).
- `MixinGuiGraphics` is an intentional comment-only stub, not registered.

## 6. Input & interaction patterns

- **Focus**: `FeatureSetting.activeFocused` static registry (request/release/clear) routes
  key/char/scroll to one setting; screens delegate in `keyPressed`/`charTyped`/`mouseScrolled`.
  In parallel, Phase A semantic actions use vanilla `Screen` child focus through
  `SemanticActionControl`. Disabled actions remain narratable (including explicit Disabled
  metadata) but are skipped by focus traversal and reject pointer/keyboard activation.
  Availability is per-frame state on the control: `FeatureDetailScreen` derives it from the
  fade band, `ManagerListScreen` sweeps it false at frame start and lets the row passes
  re-mark what the clip band shows — a scrolled-out control can neither be keyed nor
  pointer-activated while invisible.
- **Activation feedback**: semantic actions select `SemanticSound.ACTIVATION`; the Minecraft
  adapter maps it once to vanilla's UI click. Feature-specific output remains separate (the
  alert-preview sound is the action behavior, not UI feedback). Vanilla-backed controls keep
  vanilla's own click and take `SemanticSound.NONE` — ownership adapts, never duplicates.
  The pilot focus affordance is an opt-in 1 px accent hairline on the existing button
  painter; unfocused legacy and pilot rest pixels are unchanged.
- **Drag routing**: screens keep an `activeDragSetting` through mouseDragged/Released.
- **Smooth scroll**: target-based exponential lerp advanced in `render()` with wall-clock dt —
  but implemented independently per screen with different τ (45–80 ms) and different scrollbar
  code (6 variants mod-wide; only AuroraScreen and the pack browser draw draggable thumbs).
- **Hover easing**: the canonical vocabulary is `util/HoverAnim` (Phase B); the
  former three-idiom split (`HoverAnim` snap-in, `Button`'s hand-rolled easeOutCubic
  copy, and the pack browser's Map-keyed local animator) is fully retired — Button
  since pilot 2, the pack browser's tabs/cards since pilot 8 (2026-09-18).
  Four animation helper classes total (`AnimationCurves`, `AuroraAnim`, `HoverAnim`,
  `ui/util/Animation`); `AuroraAnim` is the shared math library.
  **Phase B pilot 1 (2026-09-15): `HoverAnim.symmetric(ms)`** is the §8.3 canonical hover —
  symmetric enter/exit, no snap from rest (a fresh leg starts at zero progress), and
  mid-flight reversal mirrors the RAW progress fraction (`newRaw₀ = 1 − lastRaw`), which
  continues the eased value exactly for any easing without inverting it. The legacy
  constructors keep the shipped snap-in-from-rest behavior byte-for-byte (pinned by a
  unit test); Phase B re-trains consumers one family at a time.
  **Phase B pilot 2 (2026-09-15): the shared `Button` migrated to
  `HoverAnim.symmetric(140)`** — the second consumer of the canonical
  vocabulary, and the last legacy `EASE_OUT_CUBIC` user. Button was the
  sole remaining legacy hover of its kind; everything that paints through
  the shared Button painter migrates with it (ButtonWidget + every
  vanilla-backed button, ButtonSetting rows, manager row buttons, pack
  install/modal buttons, the theme-preview mocks, WorldMap chrome) — that
  is the shared-primitive scope, NOT a family rollout: Slider, ColorSwatch,
  Enum, Keybind, KeyList, PixelCanvas internals, and the tooltip fade keep
  their own legacy `HoverAnim` instances (snap-in) until deliberately
  re-trained; pack card/tab hover is the browser's own animator, untouched.
  Mid-flight curve changed easeOutCubic → the canonical smoothstep; rest
  and settled-hover pixels are byte-identical (stash A/B: 0 px on
  rest/settled/focus/disabled captures, and 0 px in the pack-modal Close
  ROI; whole-frame modal deltas were panorama drift, matched by a backdrop
  control strip). Cache note: Button's resting-surface templates are keyed
  on (size, variant, scale, theme-generation) — never on hover state — and
  are blitted only at EXACT rest (`hoverT == 0`), so the 140 ms of
  intermediate states paint live over them and the toggle pilot's
  stale-hole class cannot occur (there is no settled state other than rest
  for a key to flip on).
  **Phase B pilot 3 (2026-09-16): `Slider` canonical states, proven on one
  row** (Waypoints → Beam Width) — symmetric hover, the instant +1 px
  drag-grip knob (center never moves; measured 0.1 px value
  correspondence), the focus hairline, preserved disabled treatment, and
  untouched value semantics. Its audit found and fixed the latent
  disabled-color cache staleness (`Slider.shapeFingerprint()` disabled bit
  + `SliderSetting` delegation); hover/drag/focus visuals render in the
  live layer (Button-safe cache category).
**Phase B ROLLOUT (2026-09-16): ToggleSwitch and Slider states are
production-wide.** Sliders: `SliderSetting`'s constructors call
`canonicalStates()` — every setting row (both `of`/`ofInt` factories and
the `ThemeOpacitySetting.createSlider` path) runs the canonical channels;
the ThemePreview mock constructs `Slider` directly and stays legacy by
construction. Toggles: production `BooleanSetting` rows have been
component-canonical since the pilot; the rollout adds
`ToggleSwitch.previewMode()` and marks the ThemePreview mock with it so
a decorative toggle never responds to the pointer (the mock's inertness
is now explicit rather than incidental). **Intentional legacy
exceptions:** ThemePreview's mock slider + mock toggle (non-interactive
previews); AuroraScreen's inline/header toggles (Phase C: manual chrome
with unresolved viewport/input-truth — they receive the component-level
hover wash but never the focus hairline or a semantic control, and that
split stays until Phase C). Button is globally canonical since pilot 2.
Mixed-control consistency verified at runtime: Button, ToggleSwitch, and
Slider on one screen all animate from rest (first samples mid-flight,
none snapped) on the same 140 ms symmetric vocabulary; the
geometry-following focus hairline now holds across three component
geometries plus Button's rectangle (rollout evidence supports calling it
the Phase B canonical focus family for mechanical controls — promotion
is a separate decision). Cache rules unchanged and re-audited for the
broadened set: toggles render fully live; sliders keep the disabled-bit
fingerprint (delegation covers ThemeOpacitySetting, unit-pinned);
hover/drag/focus visuals stay live-layer everywhere.
**Phase B pilot 4 (2026-09-16): `EnumSetting` canonical trigger states,
proven on one row** (Animations → "Swing Curve" via
`EnumSetting.canonicalStates()`; the other 25 enum rows keep the legacy
behavior byte-for-byte). The pilot's central rule — expanded is NOT
hover: the legacy row drove its hover animator with
`hover || expanded`, pinning the pill at its hover endpoint for the
whole time the popup was open; canonical mode drives it from the POINTER
only, so `expanded + not hovered` (pointer down in the option list)
visibly reads as rest surface + popup, with the EXPANDED state carried
by the existing chevron indicator taking the accent color (direction
flip + accent, no new icon; the same accent role the popup's selected
row reads — pixel-verified: the focused-vs-expanded distinction is
exactly the chevron ROI, 180/1584 px changed inside it, 0 outside).
Focus = the Button-family hairline via a canonical-only
`SemanticActionControl` (`interactionControl()` returns null for legacy
rows — the pilot isolation): Tab traversal, Enter/Space open-close with
exactly one §11.7 activation click (the pointer path was silent before —
an intended pilot change), narration carrying value + expanded/collapsed
+ disabled. The narrow keyboard adapter: Escape while expanded collapses
the popup WITHOUT closing the screen (legacy let Escape fall through to
vanilla and killed the whole screen — the correctness defect the
keyboard path required fixing), Up/Down scroll the option list (the
popup's wheel behavior mapped 1:1); full keyboard VALUE selection stays
a dedicated semantic-control pass. Popup rows are deliberately
UNMIGRATED — a selection-list family (accent text = committed value,
immediate wash = transient hover) classified for later Phase B review;
the dark-mode wash's subtlety (240 px vs light's 96%) is an observation
for that review. Cache note: enums render fully live; expanded state
changes row height, already an owning-screen cache version input — no
fingerprint changes.
**Phase B ROLLOUT (2026-09-16, later that day): canonical Enum states are
production-wide — 26/26 rows.** The seam decision: the pilot's
`canonicalStates()` opt-in is REMOVED with the flag — canonical is
`EnumSetting`'s only behavior (the Button/ToggleSwitch end state; Slider
keeps its flag only because the ThemePreview mock constructs it directly,
and Enum has no mock consumer — every production row constructs through
the registry, no `glassButton(false)` exceptions exist). `hoverTarget`'s
pointer-only rule is now unconditional and unit-pinned; a rollout
inventory test pins the 26-row count against FeatureRegistry (the source
file — the registry class itself is not headless-loadable). At the time of
the Phase B rollout, the three AuroraScreen SETTINGS-tab enums (Text
Renderer, Client Font, UI FPS Limit) were canonical AT THE COMPONENT LEVEL
but intentionally host-deferred. Phase C-2 has since closed that seam:
AuroraScreen now asks for and hosts their controls, so they retain the same
hover/chevron/popup behavior and additionally receive Tab focus,
Enter/Space, narration, and the component-owned activation click.
Runtime verification: DevPilot `enumr` boots dark+light, 20/20 oracles
each — all four Animations enums animate from rest (no snap left on a
multi-enum screen), inter-enum ownership (open-one-consumes-the-other,
independent values, close-one-open-another), keyboard on a non-pilot row
(Tab/Enter/Up/Down/Escape), >5 vs ≤5 wheel semantics, and the
AuroraScreen boundary set. Rollout-vs-pilot A/B on the pilot row:
rest and expanded-not-hovered byte-identical (0/7200 px — the treatment
was frozen, the rollout changed only the other 25 rows' path, not their
endpoints). The stash-baseline (pilot tree) boot fails exactly the three
legacy-row enter oracles — the suite discriminates rollout from pilot
state. Remaining Phase B families: ColorSwatch, Keybind, KeyList,
tooltips, pack card/tab hover — all still legacy pending per-family
review.
**Phase B pilot 5 (2026-09-16, later): ColorSwatch canonical states,
proven on one row** (Crosshair → "Color" via `ColorSetting.canonicalStates()`
→ `ColorSwatch.canonicalStates()`; the other 28 color rows and the
Waypoint manager's non-clickable display chips keep the legacy behavior
byte-for-byte). The governing invariant: **the represented color sample
is DATA** — no state modifies its pixels; all interaction treatment
lives on the surrounding chrome (pixel-proven: the interior ROI is
byte-exact to the stored RGB in rest/hover/focus/expanded/disabled for
black, white, red, cyan, and gray, in both themes). The pilot corrected
one real violation: the legacy disabled branch halved the sample's alpha
(`color & 0x55FFFFFF`), changing its rendered color — canonical keeps the
sample literal and communicates disabled through the base ring in the
established disabled-chrome idiom (`ON_BACKGROUND @ 0x33`, EnumSetting's
disabled treatment); the stash-baseline pixel proof shows every stress
color shifting when disabled pre-pilot (white 255→198) and staying
byte-exact after. Canonical channels: hover = symmetric 140 ms with the
unchanged halo endpoint; selected = the existing 1.5 px accent ring 2 px
out, now COMPOSING with hover instead of suppressing it (the legacy
branch dropped hover feedback on a selected swatch — production hosts
never select today; the channel is the component's contract for the
future peer-group migration); focused = the Button-family 1 px hairline
ON the rect — a deliberately different radius/weight from the selection
ring's 2 px-out position, so focus can never read as selection (the
geometry-following family generalized to the small swatch without an
exception); disabled = literal sample + muted ring, no halo, traversal
gated. The host row gains the Enum-trigger interaction contract
(`SemanticActionControl`: Tab focus, Enter/Space open/close with exactly
one activation click, narration carrying the value as hex — the picker's
own vocabulary — plus expanded/collapsed; Escape collapses the inline
picker without closing the screen). NO press treatment by decision: the
editor expanding under the swatch IS the feedback (the row's height
doubles; a scale/deform would only suggest the value changed). Topology
note: ColorSwatch's production reality is single-swatch-opens-editor
(the 29 `ColorSetting` rows); the PEER-GROUP topology with persistent
selection exists only in `AccentSetting`'s hand-rolled preset grid, which
is NOT a ColorSwatch consumer (and renders on AuroraScreen's inline
Settings tab — the Phase C host deferral applies); migrating it is a
separate future task. The Waypoint manager's row chips are data-only
displays that still show a spurious hover halo today — observed, left
legacy, a candidate for a `previewMode()`-class opt-OUT in a later pass.
Cache note: the per-color resting template blits only at exact rest
(`!disabled && !selected && hT <= 0`) and never keys on state — the
Button-safe category; all state chrome paints live.
**Phase B ROLLOUT (2026-09-16, later): canonical color-swatch states are
production-wide — all 29 interactive `ColorSetting` rows** (26 in
FeatureRegistry + AccentSetting's embedded Custom picker +
ParticleRowSetting's overlay row). The seam: `ColorSetting` constructs
its swatch canonical unconditionally (the Enum flagless-row pattern);
the pilot's `ColorSetting.canonicalStates()` opt-in is removed with the
flag, while `ColorSwatch.canonicalStates()` REMAINS — the component-level
opt-in is structurally necessary because the Waypoint manager's
data-only display chips construct `ColorSwatch` directly and stay legacy
by construction (the D-class isolation falls out of the seam; unit-pinned
— and the chips' spurious hover halo is the documented future
`previewMode()`-class cleanup, deliberately not fixed here).
AccentSetting's preset grid remains hand-rolled (NOT ColorSetting — the
peer-selection family, future task; its embedded Custom picker IS a
ColorSetting and is canonical, unit-pinned). The literal
represented-color invariant is now a production invariant — interior
byte-exact to stored RGB across rest/hover/focus/expanded/disabled for
the stress set in both themes (runtime-oracled on the rollout boots);
the legacy disabled alpha-halving stays unreachable from any
production color row (reachable only through direct legacy ColorSwatch
construction — the chips, which are never disabled). Runtime:
DevPilot swatchb rollout boots dark+light 22/22 (the crosshair split is
gone — neighbor canonical; block_overlay's two color rows verified as
the second multi-color screen with independent editor ownership; the
harness now restores on normal/oracle-failure/timeout paths). The
pilot-tree stash baseline fails exactly the second-screen oracle (its
rows still legacy) — the suite discriminates rollout from pilot.
Remaining Phase B families: Keybind, KeyList, tooltips, pack card/tab
hover, AccentSetting's peer grid — all still legacy pending per-family
review.
**Phase B pilot 6 (2026-09-16): `KeybindSetting` canonical capture states,
proven on one row** (Toggle Sprint/Sneak → "Toggle Sprint Key" via
`KeybindSetting.canonicalStates()`; the adjacent Toggle Sneak row and the
other 15 production keybind rows remain legacy byte-for-byte). Inventory:
FeatureRegistry owns 17 detail-screen `KeybindSetting` rows; 16 mirror an
Aurora `KeyMapping`, Stats Reset is Aurora-UI-only, and the vanilla Controls
screen additionally exposes HUD Editor + Blur Test. Keystrokes' one
`KeyListSetting` is a separate multi-value family and is explicitly outside
this pilot. Aurora's binding vocabulary remains raw GLFW KEYSYM ints:
keyboard keys only; mouse buttons and scancodes are not silently coerced.

The pilot separates five channels that legacy conflated: the stored binding
is persistent DATA; hover is pointer-only symmetric 140 ms; semantic focus
is the Tab target and Button-family accent hairline; listening is persistent
ownership of the next keyboard event, communicated by the existing stained
pill + `> press key <`; disabled is the authoritative gate. Focus may exist
without listening, and listening survives pointer departure without pinning
hover. There is no additional press animation by decision: entering the
visibly stained capture state is the activation response. The semantic child
owns pointer and Enter/Space activation, narration reports either `Bound to
<key>` or `Waiting for key input. Current binding: <key>`, and exactly one
vanilla click plays when capture is armed. The activation event returns
through child dispatch before `FeatureSetting.activeFocused` becomes the
capture route, so Enter/Space can never bind itself; a later key event is the
one captured. Successful capture performs exactly one setter call and one
save, releases capture routing, but leaves the semantic child focused.

Cancellation semantics are explicit. Escape and Backspace preserve Aurora's
shipped/advertised behavior and CLEAR to `UNBOUND`; Delete and all other
keyboard keycodes are ordinary bindings. A pointer press while listening is
not bindable: `FeatureDetailScreen` cancels the single canonical capture owner
and consumes that press before any underlying child/row can activate. A
second press is required for the new target. Scrolling the row outside the
interactive viewport, replacing/removing/closing the screen, or opening the
detail screen cancels without mutation/save. `removed()` shares an idempotent
teardown with `onClose()` because screen replacement does not promise the
latter. This also prevents the static focused-setting registry from leaking
into another screen.

The reproduced correctness defect is intentionally left on every non-pilot
row for isolation: legacy `KeybindSetting` never consulted `isDisabled()`, so
a disabled row could enter the stained listening state, accept a key, mutate
config, and save; enabled→listening→disabled also retained capture. On the
pilot, disabled rejects pointer/keyboard activation, removes hover/focus/
stain, keeps a dim but legible bound value, and immediately cancels an active
capture without mutation or save. A final event racing the gate is
consume-but-inert. The test suite pins the pilot fix so a future rollout cannot be confused with accidental global
change.

Cache/perf: the key name is cached by bound int; the semantic child and hover
animator are long-lived. Bound/listening/disabled/hover/focus paint in the
live layer; no state enters `UiLayerCache` and no cache fingerprint changes.
The raised glass split is retained (neutral rest, accent-stained listening,
complete flat fallback); disabled bypasses the glass surface and uses the
established Enum-style muted fill/border.

**Phase B ROLLOUT (2026-09-16, later): canonical keybind capture states are
production-wide — all 17 `KeybindSetting` rows.** The seam decision: the
pilot's `canonicalStates()` opt-in is REMOVED with the flag — canonical is
`KeybindSetting`'s only behavior (the EnumSetting end state; every
production row constructs through FeatureRegistry and no mock/preview/
AuroraScreen-hosted consumer exists, so no legacy escape remains — the
historical disabled bypass, reproduced and pinned by the pilot's legacy
fixture, is unreachable from any construction path). The disabled-gate
correction is therefore production-wide: every row rejects
pointer/keyboard activation while disabled, cancels an active capture the
moment it enters disabled, keeps a stale racing event consume-but-inert,
and never mutates or saves through the gate. Capture lifecycle, the
focus≠listening split, Enter/Space arming (structural, no timing delays),
exactly-once setter+save, Escape/Backspace→UNBOUND clear semantics,
pointer-press-cancel-consume (mouse is not bindable), scroll-away and
screen-replacement teardown (`onClose()`/`removed()` idempotent), and the
single-capture-owner rule (a second pointer press is required to arm a
different row — the frozen two-step interaction) all hold on every row.
Inventory pinned by source test: 17 FeatureRegistry rows (16 mirror an
Aurora `KeyMapping`; Stats Reset is Aurora-UI-only), 18 registered
vanilla mappings (the Controls screen is a separate configuration
surface, untouched — its two-way sync verified live: an Aurora-UI capture
flows into the `KeyMapping` through `AuroraKeybinds.tick()`), and
Keystrokes' one `KeyListSetting` stays the separate legacy multi-value
family (snap-in animator unit-pinned; the next Phase B pilot). Runtime:
DevPilot `keybindb` extended for the rollout — 27/27 oracles on both the
dark and light/bright-accent boots, covering the frozen pilot row
(Toggle Sprint), the adjacent now-canonical row, Better Hitreg as the
dense six-keybind stress case (canonical adapters on three rows,
two-step ownership transfer, exactly-one capture/write/save, forced
disabled fixture, enabled→listening→disabled, 300-step scroll-away
cancel, screen-close teardown), Waypoints as the second topology (both
rows canonical, capture restores, sibling untouched), the vanilla
mapping sync, and full binding/theme restoration verified against the
pre-run snapshot (success, oracle-failure, timeout, and panic paths).
Pixel evidence in `.devpilot-keybindb/rollout-dark` + `rollout-light-bright`
(13 captures each): hover animates rest→early→mid→settled (7277/6476/132
px frame deltas >8/255 — progress, then convergence), disabled is
distinct from rest (43636 px) in both themes, listening survives pointer
departure (focused vs listening-away 10087 px, listening stain stable
while only hover changes: hovered-vs-away 1480 px), and the dense
hitreg/waypoints screens show the same treatments. Baseline
discrimination: the same harness on the pilot commit `1836c9a` fails
exactly 5 oracles — the neighbor/hitreg/waypoint rows read legacy, the
disabled bypass is reachable on them (`rejected=false`), and scroll-away
does not cancel legacy rows — the suite distinguishes rollout from pilot.
(At the time of writing: KeyList, tooltips, pack card/tab hover,
AccentSetting's peer grid remained legacy. KeyList landed as pilot 7
below; the tooltip fade migrated to `HoverAnim.symmetric(200)` — see the
Tooltips entry in this section; pack card/tab hover and AccentSetting's
peer grid remain the open Phase B pilots.)
**Phase B pilot 7 (2026-09-16, later): `KeyListSetting` canonical
multi-value capture states** (Keystrokes → "Extra Keys" — the one
production KeyList, so the pilot's isolation is behavioral-family-level
and the canonical behavior is unconditional; the commit is the isolation
boundary). Baseline topology was ONE manually routed compound control:
per-entry rows with immediate-hover "−" remove chips, a "+ Add Key" pill
(legacy snap-in hover pinned by `hover || listening`), and capture
routed through the static `FeatureSetting` focus registry. Baseline
defects found and fixed: disabled was never consulted anywhere (a
disabled row could add, remove, and save), a duplicate/full capture
SAVED despite no mutation, and a pointer press on a screen child (Done)
while listening could click through (the screen's pre-children guard
only knew Keybind rows).

The pilot's architecture answer — **capture ownership lives at the ADD
level** (capture purpose is always ADD; no rebinding exists, so no
REPLACE(index) semantics were invented) **on a NEW shared exclusive
slot on `FeatureSetting`**: `claimCaptureOwnership()` /
`releaseCaptureOwnership()` / the family `onCancelCapture()` hook, with
the disabled gate inside the claim itself. `KeybindSetting` was migrated
onto the same slot with identical observable behavior (its public
`cancelActiveCapture` remains as a family-named alias), and
`FeatureDetailScreen`'s pre-children pointer guard now cancels
WHATEVER family owns — a KeyList and a Keybind can structurally never
both claim the next input, on one screen or across screens. State model:
list values are DATA (rendered literally, also while disabled); the add
pill's hover is pointer-only symmetric 140 ms, never pinned by
listening; entry rows are removable chips whose immediate hover is kept
deliberately (a scanning family, documented); focus is the add action's
semantic target (Tab/Enter/Space/narration, one activation click arming
capture — Enter cannot self-add); listening is the stained pill +
"> press key <", visible after pointer departure; disabled is
authoritative everywhere (rejects add AND remove at action time, not
render time; entering disabled while listening cancels without
mutation; a stale racing key is consumed-but-inert). Multi-value clear
rule preserved and made explicit: ESC and BACKSPACE CANCEL without
mutation (not Keybind's clear-to-UNBOUND — there is no one value to
clear); Delete and every other keycode are ordinary bindable entries;
duplicates and the 12-entry cap close the capture as an accepted no-op
with NO save. Removal removes exactly one entry with one save; the add
action's semantic control is row-scoped (not entry-scoped), so focus
survives structural mutation with nothing dangling. Scroll-away,
screen close/replacement/open, and viewport loss all cancel through the
existing teardown (`onInteractionAvailabilityChanged`,
`onDetailScreenClose/Open` — no competing global capture manager).
Cache/perf: entries render live; row height is already an owning-screen
cache input; key display names are memoized per GLFW value (the
ellipsized per-frame path no longer formats through InputConstants); no
per-frame child reconstruction, no polling. Unit: 14 focused tests
(`KeyListPilotTest`) incl. the cross-family single-owner invariant and
the empty-list roundtrip; the Keybind suites pass unchanged. Runtime:
DevPilot `keylistb` — 22/22 oracles on BOTH the dark and light/bright
boots (hover settle/exit 1.0/0.0, Tab-focus-not-listening, Enter-arms-
without-self-add, listening-pointer-away hover 0.0, F13 add, duplicate
no-op, exact removal, Escape-cancels-without-clearing-and-without-
closing-the-screen, outside-click + right-click cancel/consume,
scroll-away, disabled rejection of add+remove, enabled→listening→
disabled, empty→add→remove→empty, screen-close teardown, and a
cross-family sanity pass proving the canonical Keybind still arms and
captures on its own screen) with full state restoration (the user's
actual list/enabled/sprint key/theme) verified after every boot.
Captures in `.devpilot-keylistb/rollout-dark` + `rollout-light-bright`
(10 each; whole-frame deltas carry menu-panorama drift, so the state
oracles and logged hover values are the quantitative backbone). The
KeyList stays clearly separate from single `KeybindSetting`: different
clear rule, multi-value mutation, chip-family entry hover.
- **ToggleSwitch — the Phase B state-complete pilot (2026-09-15)**: overlapping state
  channels (on/off × hover × pressed × focused × disabled), not an exclusive enum.
  Hover = the symmetric animator above, one restrained channel (track lerps 10% toward
  `ON_BACKGROUND` — lifts in dark mode, deepens in light). Pressed = a one-shot
  mechanical thumb-compression pulse (8% over 90 ms, recovers over 180 ms) fired from
  `toggle()` — geometry responding, never a second toggle, and a disabled toggle never
  pulses. Focused = opt-in `focusedVisual(boolean)`: a 1 px accent hairline capsule
  1.5 px OUTSIDE the track (visible without hover, distinct from the ON fill, both
  modes — the same provisional-treatment family as the button hairline; the final
  Aurora focus visual is still open). Disabled = `SURFACE_INSET` off-track, 35% accent
  residue on-track, `ON_BACKGROUND_MUTED` thumb — inert but still communicating the
  stored value. `BooleanSetting` mirrors its disabled gate into the widget every frame
  and (on hosts running the semantic lifecycle) exposes the row's
  `SemanticActionControl` — whole-row pointer target preserved, plus Tab/Enter/Space
  activation and on/off narration; hosts that never mark the control available
  (AuroraScreen's inline Settings tab, the Phase C deferral) keep the legacy
  pointer-only path through the availability gate. **Cache note:** the toggle now
  renders FULLY LIVE and returns a constant `shapeFingerprint()` — the cached-settled
  design had a hole (a hover-exit's first frame re-rastered the cache with the toggle
  absent and the settle never refilled it), and going live also deletes the per-flip
  full-frame row-cache re-raster the old on/off fingerprint bit caused (~35 AA fills
  per toggle per frame; 1–6 toggles per screen ≈ 200 fills, far under the §10 line).
- **PixelCanvas actions — Phase B full semantic adoption (2026-09-18)**:
  the three text actions of `PixelCanvasSetting`'s TWO production rows
  (the crosshair main canvas + indicator canvas; 56×16 Clear/Default and
  46×16 Apply) adopted the complete canonical action vocabulary —
  symmetric 140 ms hover (the last legacy snap-in action animators are
  retired), and a `SemanticActionControl` per action (the KeyList add-pill
  pattern, extended: `FeatureSetting.interactionControls()` now returns a
  LIST — the single-control default flows through unchanged — and
  `FeatureDetailScreen` registers/sweeps/refocuses the whole list). Tab
  order is visual/read (Clear, Apply, Default); focus is the Button-family
  1 px accent hairline on each button rect, never while disabled;
  Enter/Space activate with exactly one activation click through
  `MinecraftSemanticFeedback` (pointer paths route through
  `activateFromPointer`, direct-path fallback for headless hosts);
  narration carries per-action names ("Clear Canvas" / "Restore Default
  Shape" / "Apply Resolution") plus concise descriptions. NO press
  animation by decision (the immediate action result + status line is the
  feedback — the Keybind/KeyList precedent). Disabled is the authoritative
  gate everywhere: the row's existing style-based supplier drives render,
  hover suppression, pointer AND keyboard rejection with no mutation or
  save (runtime-oracled: style=CROSS rejects all three). Data semantics
  byte-preserved: Clear zeroes in place with one save; Default commits the
  vanilla 15×15 pattern + dims with one save + focus release; Apply
  validates → measured-benchmark/warn → center-copy resize with one save;
  the W/H fields' own Enter route is untouched (an expanded enum-style
  double-activation is impossible — the fields and the semantic control
  never hold focus simultaneously). The warning panel's Apply Anyway /
  Cancel stay MANUAL (modal-confirmation family, immediate hover, disabled
  while measuring — classified, not migrated), and the canvas body is a
  direct-manipulation data surface untouched by this migration. The
  compact `+`/`×`/`−` glyph family elsewhere stays deferred to the Phase C
  icon-action primitive (unit-pinned). Post-migration legacy `HoverAnim`
  inventory: `Slider`'s default ctor (ThemePreview mock only),
  `ColorSwatch`'s default ctor (Waypoint data chips only — the documented
  E-class cleanup), and `HoverAnim.symmetric`'s own internal private ctor
  — nothing else. Verified by `PixelCanvasActionMigrationTest` (5 tests)
  and DevPilot `pixelcanvasb` (11/11 oracles, v3 tree, plus a
  legacy-expectation baseline boot whose first hover sample is exactly
  1.0 — the snap — vs the migrated tree's 0.04–0.28 from rest).
- **Pack-browser navigation tabs — Phase B pilot 8 (2026-09-18)**: the
  resource-pack browser's category sidebar (23 rows: 21 selectable tabs
  + 2 section headers — the audit inventory's "9 tabs" was stale) is the
  production navigation/tab family, and it models OVERLAPPING state
  channels rather than an exclusive enum. **selected** = the persistent
  `activeCategory` (null = "All"), painted as the accent wash that is
  fully visible at hover 0 (0.30-alpha rest, 0.40 settled-hover) plus
  the 0.55 accent outline on the flat fallback — never through hover
  progress. The pre-pilot code drove the active tab's animator with
  hover-OR-selected, pinning it at its endpoint, so selection was
  expressed ONLY through stuck hover progress; that conflation is gone
  (source-pinned: the boolean expression is banned from the file).
  **hover** = pointer-only `HoverAnim.symmetric(140)` — the screen's
  whole local 150 ms hover family (tabs AND card bodies) is retired in
  the same stroke, so a mid-flight reversal now continues from current
  progress instead of snapping to the far endpoint, and card bodies
  ride the same canonical animator with their rest endpoint (the
  `t == 0` template blit) and compound click ownership byte-preserved.
  **focused** = vanilla child focus on one `SemanticActionControl` per
  selectable tab (the plain existing action infrastructure — no
  navigation-specific adapter was needed; the button-like contract
  models tabs exactly), painted as the Button-family 1 px accent
  hairline, composing with the selected outline without either channel
  losing legibility (selection reads through the fill, focus through
  the stroke). **disabled** = N/A in the current topology (every
  category is always available; the only gating is the per-frame
  AVAILABILITY sweep — a tab outside the sidebar clip band or covered
  by the detail modal can neither take focus nor activate, the
  card-button discipline). Keyboard: Tab traversal through the vanilla
  child list (spatial order, the host's established rule), Enter/Space
  activate the focused inactive tab with exactly one category change;
  activating the already-selected tab is a SILENT consumed no-op (the
  baseline re-ran `submitSearch`, clearing the text-fit cache and
  resetting grid scroll to top on every redundant click). Sound
  ownership: the tab actions carry `SemanticSound.NONE` and play
  exactly one ACTIVATION through `MinecraftSemanticFeedback` inside
  the behavior, only when the category actually changes. NO press
  animation by decision (the immediate selection transition IS the
  press acknowledgment; copying Button's scale would suggest a
  momentary action). Arrow-key navigation DEFERRED (decision B):
  Left/Right roving-tabindex semantics need a reusable tab-group
  primitive owning focus-within-group and selection-follows-focus —
  Phase C component architecture, not a screen-local key handler.
  Narration carries the category name + `Selected`/`Not selected`
  state (metadata-verified; `libflite` remains unavailable). The DONE
  install phase gains the canonical Button disabled treatment
  (`SURFACE_INSET` fill + muted label — DONE is terminal and its
  action has been gate-rejected since Phase A; the painter now agrees,
  and the glass flag went with it since a disabled button never paints
  glass). Cache/perf: animators keyed by stable identity
  (`"tab:"+index`, `"card:"+projectId`), card keys pruned with their
  CardState on result-set change; controls created lazily once per
  screen instance and re-added by `init()` on resize; no per-frame
  construction; the sidebar walk stays O(rows). Pinned by
  `PackTabsPilotTest` (11 tests: exactly-one-click sound model with
  silent reselection, narration metadata, selected-tab focusability,
  symmetric-140 without pinning, and source pins for the pointer-only
  target / one-animator-family / no-op guard / DONE disabled /
  hairline / semantic click routing / arrow deferral). Verified by
  DevPilot `packtabsb` boots — 28/28 oracles on BOTH the dark and
  light/bright themes (captures in the untracked `.devpilot-packtabsb/`;
  pixel evidence: the selected tab at hover 0 differs from inactive rest
  by 9304/9328 ROI px with a 0-px drift floor between identical states,
  the hover delta adds 9176 px, and the focus hairline reads as accent
  edge rows (60,72,133) over base (13,19,44) on an otherwise unfilled
  tab) — and a stashed-baseline boot on `f1ccf95` failing exactly the
  canonical oracles (selected tab pinned at hover 1.0 at rest AND after
  pointer exit AND on a freshly reopened screen; reversal snapped to
  the endpoint; the control/narration/keyboard phases skip — no
  controls exist), with the flipped legacy-expectation oracles passing:
  the suite discriminates the migration from the baseline.
  Remaining Phase B family: AccentSetting peer selection.
- **AccentSetting preset grid — Phase B pilot 9 (2026-09-18): the
  peer-selection family, the last novel Phase B semantic family.**
  The Theme screen's accent grid (10 peers: 9 `ThemePresets` + Custom,
  5 per row, 24 px cells) models a PEER-SELECTION GROUP whose members
  represent VALUES. **Selection is never stored**: the literal accent is
  the source of truth — `ThemePresets.matching(accent)` for presets,
  "no preset matches" for Custom; no selected-index field exists that
  could disagree (unit-pinned), and a picker edit landing exactly on a
  preset value selects that preset by construction. **Hover** =
  pointer-only `HoverAnim.symmetric(140)` per peer (the baseline rings
  were immediate), animating the existing 1 px WINDOW_OUTLINE ring's
  alpha. **Composition replaces the baseline `else if`**: the persistent
  1.5 px selection ring (2 px out, label color) draws unconditionally
  for the matching peer — the baseline drew hover only when NOT
  selected, erasing hover feedback on the selected preset AND on active
  Custom; both compose now. **Focused** = one `SemanticActionControl`
  per peer, materialized as a complete set on the first
  `interactionControls()` ask — the Theme DETAIL screen hosts them
  fully (registration/sweep/refocus, Tab + Enter/Space selection with
  exactly one accent change, narration with `Selected`/`Not selected`,
  the conditional click). At pilot time AuroraScreen's inline Settings tab
  intentionally did not ask; Phase C-2 now materializes and hosts the same
  ten controls there without changing the component contract.
  **Disabled** is the authoritative gate (the baseline PAINTED the
  hover ring on disabled cells while rejecting clicks — fixed): no
  hover target (an in-flight hover eases back to rest on the
  enable→disable transition), no pointer/keyboard activation, no save,
  no sound, the action's enabled gate drops peers from traversal and
  narrates unavailable — while the selected ring stays readable and
  every color interior stays literal. The baseline's disabled treatment
  alpha-halved the active Custom preview's fill — modifying represented
  DATA to say "disabled" — removed per the ColorSwatch principle (the
  inactive chrome fill + dimmed label carry it). Sound ownership: peer
  actions carry `SemanticSound.NONE` and play exactly one ACTIVATION
  through `MinecraftSemanticFeedback` inside the behavior, only when
  the activation had an effect — a preset that changes the accent (or
  closes the open editor) clicks, Custom's open/reset always clicks
  (it always acts), and the already-selected no-op (editor closed) is
  silent. Activation/persistence: one setter call per genuine change
  (the production setter's `persistThemeChange` is THE persistence
  action — the pilot removes the redundant second config save the grid
  path used to issue); already-selected clicks are consumed no-ops with
  zero reloads (ThemeManager generation proven static). NO press
  animation by decision (the immediate selection-ring transfer is the
  feedback). Arrow-key roving within the group DEFERRED to Phase C
  (the tab-group/peer-group host primitive; the same decision as the
  pack tabs). The embedded Custom `ColorSetting` picker is reused
  literally and unmodified — its own semantic control is never
  materialized, so no duplicate sound can arise between peer activation
  and picker actions; `custom-active` is derived, not stored.
  Cache/perf: preset fills remain the cacheable shapes layer; every
  ring/hover/label is live overlay (unchanged split); animators and
  controls are long-lived arrays indexed by grid position;
  `interactionControls()` builds its list once; no per-frame
  construction and matching stays O(9). Pinned by
  `AccentSettingPilotTest` (19 tests: topology, literal-derived
  selection with no stored index, exact preset persistence + transfer,
  no-op policy, editor-close semantics, Custom open-not-change,
  one-save grid path, pointer-only + disabled-rejected hover target,
  independent symmetric animators, else-if ban, disabled rejection,
  literal interiors, conditional sound model, host deferral,
  narration metadata, picker isolation). Verified by DevPilot
  `accentb` boots — 21/21 oracles on BOTH dark and light themes plus
  four offline pixel verdicts per theme (selected-peer hover paints
  624 ROI px over the persistent ring, ACTIVE-Custom hover paints 624,
  the disabled transition withdraws the ring with the pointer still
  parked 628/622, and the Red interior is byte-exact (235,0,41) across
  rest/hover/disabled) — and a stashed-baseline boot on `6134aae`
  passing its applicable oracles (6/6, control/animator phases
  skipped) whose pixel pairs read 0/0/0 on exactly those three
  compositions: the baseline suppressed hover on the selected peer,
  gave active Custom no hover, and kept painting the hover affordance
  while disabled. The suite discriminates. Phase B's novel-family work
  is COMPLETE with this pilot; the documented E-class cleanup
  (Waypoint data chips' spurious hover halo) remains.
- **Waypoint data chips — Phase B E-class cleanup (2026-09-18)**: the
  Waypoint manager's row color chips are DISPLAY-ONLY data samples and
  now say so through the component itself — `ColorSwatch.dataOnly()`
  (the `ToggleSwitch.previewMode()` pattern, named for data semantics).
  Before this, the chips were the last production consumer of
  ColorSwatch's legacy interactive default: a null callback, no click
  routing (the manager's row walk owns input; the Color button owns
  the edit action), no focus, no semantic control — yet the renderer
  still computed pointer hover and painted the legacy snap-in halo,
  advertising an interaction that did not exist. Data-only mode renders
  the shared per-color rest template (checkerboard + opaque literal
  fill + BORDER ring — byte-identical to an interactive swatch at
  rest, one blit) and returns before any interaction state is
  computed: no hover target ever, no halo, no selection/focus paint,
  no press, no click consumption (mouseClicked returns false and never
  runs a callback), no narration surface — mouseX/mouseY unused. The
  mode is an instance flag (no shared/static state to leak); the
  represented color stays literal in every pointer state (the chip is
  neutral data, NOT styled disabled). Interactive swatches are
  untouched: `ColorSetting` still constructs canonical (unit-pinned —
  data-only cannot leak in), and the legacy interactive DEFAULT path
  is deliberately retained as the component's public behavior (its
  disabled alpha-halving stays unreachable from production — pinned by
  `ColorSwatchPilotTest`), so the post-cleanup legacy `new HoverAnim(`
  inventory is exactly the documented three: ColorSwatch's default
  field (now production-unreachable), Slider's ThemePreview mock
  field, and `HoverAnim.symmetric`'s internal factory construction —
  verified by a source-sweep test. Pinned by `ColorSwatchDataOnlyTest`
  (7 tests: pointer/callback rejection, the no-interaction-state
  render branch, instance isolation, the literal-sample invariant, the
  Waypoint adoption + pure-data call surface, ColorSetting's
  canonical isolation, and the inventory sweep). Verified by DevPilot
  `waypointchipb` boots (a temp waypoint swapped into the joined dev
  world's scope key, theme forced to full opacity, GUI scale forced to
  1 so the 460-wide list fits the fixed 854×480 dev window): 10/10
  oracles on dark AND light — the chip's animator reads a flat 0 with
  the pointer parked directly on it, a chip click mutates nothing
  (color/name/count intact, no picker, no editor, not consumed — the
  row's fall-through owns it), Tab traversal is data-safe, a live
  color change reaches the sample with no staleness, scrolling and
  close/reopen leave no stale state. Pixel verdicts: the chip's chrome
  columns are magnitude-identical to same-row control columns under
  hover (41.0 vs 41.0 dark, 405.0 vs 405.0 light — the only delta is
  the row's own legitimate hover wash) and the interior is
  byte-identical (59,130,246) across rest/hover/exit; the stashed
  `7a52582` baseline FAILS the same discrimination — its chip
  animator reads 1.0 under the parked pointer (oracle flipped and
  passing) and its halo column shows 199.4 mean-delta against the same
  41.0 wash. The suite discriminates.

**PHASE B CLOSURE RECORD (2026-09-18, baseline `18f885e`, 140 tests /
0 failures).** Phase B — Motion and State Primitives — is COMPLETE.
The canonical vocabulary, verified from source at closure: ordinary
control hover is `HoverAnim.symmetric(140)` on every custom
interactive family; the tooltip fade is the one deliberate
supplemental exception at symmetric 200 ms behind its preserved
1500 ms dwell; press is 90 ms compression / ~180 ms recovery (Button
and Toggle); capture/press acknowledgment semantics per family as
recorded in each pilot entry above. The global `new HoverAnim(`
inventory at closure is exactly three: Slider's default field
(ThemePreview mock only), ColorSwatch's default field (public
compatibility behavior with ZERO production consumers since
`18f885e`), and `HoverAnim.symmetric`'s internal factory construction
— **no unexplained legacy snap-in consumer remains on any
production-interactive custom surface**. The retired pack-browser
local 150 ms animator family is gone. **Immediate-hover exceptions
(the finite list):** Enum popup option rows and the other
selection-list rows (a scanning family — the committed value already
shows; hover marks the candidate under the pointer, and §11.7 keeps
it classified for the Phase C review); ColorPicker direct-manipulation
surfaces (dragging data — interpolation is meaningless); manager and
grid scrollbars + scrollbar thumbs (direct manipulation chrome);
SegmentedControl segments and AuroraScreen's manual chrome
(tiles/tabs/layout buttons) — the latter two are C-DEFERRED, not
intentional end-states: SegmentedControl still draws immediate hover
with the selected-segment suppression Phase B eliminated elsewhere,
and `AbstractButtonMixin` (the themed vanilla selection-screen
buttons — a vanilla-owned integration adapter) carries a hand-rolled
140 ms snap-in hover with hover/focus aliasing; both migrate when the
Phase C component/host pass reaches them (the mixin's path is the
documented painter migration to `ButtonWidget`). **Data-only/preview
exceptions:** Waypoint color chips (`ColorSwatch.dataOnly()`),
ThemePreview's mock slider (legacy constructor by design) and mock
toggle (`previewMode()`), the preview's mock buttons (non-interactive
painter calls), BlurTestScreen and DevPilot (dev-only). **Phase C
deferral inventory (reconfirmed from source):** AuroraScreen
render/input clipping agreement (the known mismatch is real and
untouched); AuroraScreen manual-chrome semantic hosting (sidebar
tabs, layout buttons, tiles); inline Enum/Accent peer control hosting
on AuroraScreen's Settings tab; the compact `+`/`×`/`−` icon-action
primitive (ItemScale rows, Effect list, KeyList remove chips,
PixelCanvas warning-panel buttons as the classified modal family);
HudEditor's X key + controls; SegmentedControl keyboard/narration;
the reusable tab/peer-group arrow-navigation primitive (pack tabs +
Accent peers both deferred to it); Square-mode/fixed-radius geometry
conformance (AuroraScreen's literal radii, SegmentedControl); the
§15.2 conformance harness; AbstractButtonMixin + SegmentedControl
hover dialects (above). The Phase A ProfileManager create-row
consume-but-inert behavior is the accepted rule, not a defect.
**Phase D deferrals:** composited text contrast at low opacity,
`ON_ACCENT` robustness for difficult accents, light-theme disabled
readability, a unified disabled-material treatment — with literal
represented colors exempt (the ColorSwatch/AccentSetting data
invariants now pin that exemption). **Phase E/F/G** boundaries are
unchanged: glass refinement, sound identity, optional
refraction/distortion R&D; canonical glass remains blur + tint +
face lighting + rim with no distortion. Runtime closure evidence:
DevPilot `phasebclose` boots (dark + light) walk representative
instances of every detail-screen family — Button (chrome painter +
PixelCanvas actions), Toggle, Slider, Enum, Color, Keybind — reading
their REAL animators: each animates from rest (no snap), settles at
1.0, and exits to exactly 0.0, the shared timing vocabulary in one
boot; plus the 200 ms tooltip fade sampled mid-flight, value-channel
independence under parked hover, disabled-authority rejection, and
the Accent peer topology. The pack tabs, accent peers, and Waypoint
chips are covered by their own modes' fresh boots on this tree
(28/28 dark + light, 21/21 dark + light, 10/10 dark + light
respectively). No production code changed in the closure sweep —
documentation and tests only.
- **Tooltips — Phase B canonical fade (2026-09-16)**: the
  `FeatureSetting` label-dwell system is Aurora's ONE production tooltip
  implementation (no other tooltip renderer exists under `screen/ ui/
  worldmap/`; the pack browser has none — its detail modal replaces them).
  The 1500 ms dwell, per-label identity/dwell reset, claim-time frozen
  anchor, 220 px wrap, and screen-edge clamping are unchanged; the fade
  migrated from the legacy snap-in `HoverAnim(200)` to
  `HoverAnim.symmetric(200)` — dwell completion now starts a 200 ms
  fade-in from rest (previously an instant appearance), eligibility ends
  with the symmetric 200 ms fade-out, and mid-flight reversal mirrors raw
  progress continuously. The tooltip's 200 ms is its own
  supplemental-information motion family, deliberately NOT the 140 ms
  control-hover duration. The tooltip legacy snap-in dialect is retired;
  remaining legacy `HoverAnim` constructors after the tooltip migration
  were PixelCanvas's three text buttons (migrated 2026-09-18 — see the
  PixelCanvas entry above), `ColorSwatch`'s default ctor (Waypoint data
  chips only — the documented E-class cleanup), and `Slider`'s default
  ctor (the ThemePreview mock only). Pinned by `TooltipFadeMigrationTest` (dwell
  gate, fade-from-rest, symmetry, reversal, identity, disabled).
- **HUD editor**: drag-move / corner-resize / shift+click settings / right-click hide /
  shift+right-click lock / X disable (R-Shift in world).

**PHASE C IMPLEMENTATION PLAN (2026-09-18 — source-derived audit of `27bf258`;
planning only, no production change has been made).** Phase C is *Geometry and
Conformance* (DESIGN_LANGUAGE §18): rectangular chrome to resolved roundness,
the icon-action primitive, render-clip/input-bounds coupling, and the deferred
host/component work Phase B froze out. Everything below was verified from
source at the Phase B closure baseline; file:line refs are current.

**C-1. Viewport/input bounds coupling (the correctness core).** The known
`AuroraScreen` render/input disagreement is real and has three parts:
(a) the **Settings-tab click walk is un-gated** — render culls rows to the
`viewTop()/viewBot()` band and scissors to `[boxY()+36, boxY()+230]`
(`AuroraScreen.java:517/:555`), but `mouseClicked` walks EVERY inline row,
header-click zone, and toggle zone at raw screen coords with no viewport test
(`:761-791`), so a setting scrolled off-screen (even above the window or below
it, over the dim) is still clickable and its inline `EditBox` focusable;
(b) **Modules-tab tiles use unclamped partial rects** — `cardBounds` culls
fully-invisible tiles but the click/hover tests use the FULL card rect
(`:745-758`, hover `:464`), leaving up to ~78 px of invisible clickable area
below the window border (a tile straddling the bottom edge is toggleable by
clicking the dim outside the window) and hover feedback while the pointer is
over invisible pixels; (c) **three bands disagree** — scissor
`[boxY()+36, boxY()+230]` (inlined 4×: `:342/:356/:458/:517`), the
`viewTop()/viewBot()` cull `[boxY()+48, boxY()+228]`, and `computeMaxScroll`'s
180 px extent (`:659-670`). The same defect class exists in two more hosts:
the **pack browser's card-grid click walk is unclamped** (render culls via
`forEachVisibleCard` under the grid scissor, but `mouseClicked` iterates all
results at raw rects — an invisible card scrolled above `LIST_TOP` opens the
detail modal; `ResourcePackBrowserScreen.java:1607-1634`; the install buttons
are protected only by their availability sweep) and the **manager screens'
inline rename/create editors** keep a stale hit rect and paint outside the
clip band (`ManagerListScreen.java:362/:406/:849`,
`ProfileManagerScreen.java:427-432`). Smaller members: `SemanticActionControl`
keyboard availability is band-level, not rect-level (a half-clipped row button
remains Enter-activatable); `AuroraScreen`'s `FeatureSetting.activeFocused`
routing is not viewport-gated (`:843-847`); PixelCanvas's measuring-state
buttons paint disabled but neither act nor consume (`PixelCanvasSetting.java`
warn buttons: painted `enabled` at draw vs `warnPending != null` click gate);
the pack modal's open-animation render offset is not reflected in its hit rect.

*Fix architecture:* ONE per-frame bounds truth per scrolling host (a tiny
ClipBand value: x-extent + top/bottom + `contains/clamp/intersects`), consumed
by the scissor, the render cull, the click walk, the hover test, the thumb
geometry, and the semantic-availability sweep — the FeatureDetailScreen model
(`TOP_FADE_Y` shared by render scissor and click gate, `:360/:467`) and
ManagerListScreen's clamp (`:699-700`) generalized, not a layout engine.

**C-2 planning snapshot. AuroraScreen semantic hosting + navigation (the Phase B freeze-out).**
Implementation split this item at its dependency boundary: C-2 is the
completed existing-control hosting pass recorded below; manual navigation
adoption remains C-2b.
The Settings tab hosts inline settings whose controls never materialize:
3 `EnumSetting`s (Text Renderer, Client Font, UI FPS Limit), `AccentSetting`'s
10 peers (both already implement `interactionControl(s)` — they materialize
only when a host asks, and AuroraScreen never asks), 3 `SegmentedSetting`s,
`ThemeOpacitySetting`, `ThemePreviewSetting`, and 4 header `ToggleSwitch`es —
all pointer-only, no Tab/Enter/narration. The manual chrome (sidebar
Mods/Settings tabs, Profiles chip, list/grid layout pair, module tiles,
Settings headers) is immediate-hover, silent, keyboard-inaccessible, and
un-narrated. Classification against the frozen pack-navigation contract:
sidebar tabs ARE the navigation family; the layout pair is a segmented
peer-value control (not a tab); tiles are button-like toggles with a secondary
navigation action (§11.10 card contract, not "tab"); Profiles is a plain
action. Hosting duties already exist verbatim in FeatureDetailScreen
(register in `init`, per-frame availability sweep, click-drops-focus, capture
cancel first) and ManagerListScreen (`registerSemanticControl` lifecycle +
frame-start unavailable sweep) — AuroraScreen adopts the same rules; the
registration/sweep machinery is shareable as a small host helper rather than
copied.

**C-3. Peer/navigation group primitive (arrow keys).** Phase B deferred
Left/Right roving for pack tabs and Accent peers. Verified from 1.21.11
bytecode: `Screen.keyPressed` invokes `AbstractContainerEventHandler.
keyPressed`/`nextFocusPath` via **invokespecial** — subclass overrides never
run, so a group CANNOT be implemented as a traversal override; it must be a
`keyPressed` interceptor before `super` (vanilla's own container walk already
moves focus spatially among available children, but is unscoped, unwrapping,
and moves focus only). The primitive: a group registry (ordered members,
wrap, optional selection-follows-focus policy — manual activation recommended:
arrows move focus silently, Enter/Space/click select, since selection has
side effects like grid reset). Consumers: pack tabs (21), Accent peers (10,
5×2 grid), SegmentedControl segments, AuroraScreen sidebar tabs.

**C-4. Icon-action primitive.** No shared icon button exists (`Button` is
text-only). Manual compact glyph actions in production (all immediate-hover or
none, zero focus/keyboard/narration/sound): ItemScale "+" add and per-row "x"
remove + expand chevrons; EffectExpiry "+" add + EffectRow "x" remove;
KeyList "−" remove chips (the ONE with a documented deliberate
immediate-hover scanning rationale — preserve or consciously change);
ParticleRow's ASCII "v"/">" disclosure stand-ins; HudEditor's hand-rasterized
9×9 X disable badge (whose on-screen hint claims an X KEY that does not
exist — no `keyPressed` in the file); AuroraScreen's vector-drawn layout
icons; PixelCanvas's warning-panel Apply Anyway/Cancel (manual, classified
modal-family). `FeatureIcons` has NO add/close/delete codepoints — the subset
must be regenerated (add `add`/`close`/`remove`/`check`-class glyphs) before
any text→icon migration. Primitive = `SemanticAction` + a compact painter
(glyph via `MaterialIconRenderer`, hit target ≥ the §5.2 vocabulary, radius
through the resolver, canonical hover/focus/disabled; per-instance geometry,
NOT one size for all).

**C-5. SegmentedControl conformance.** Semantically a peer-selection VALUE
group (not navigation, not a new family) — the Accent peer contract applies:
pointer-only `HoverAnim.symmetric(140)` (today: immediate + hover suppressed
on the selected segment — the exact conflation pattern Phase B banned
elsewhere), `SemanticActionControl` per segment (Tab/Enter/sound-on-change,
silent no-op on reselect — the `i != selectedIndex` guard already exists),
narration with group/option/selection, arrows via C-3. Radius already
resolves (`min(trackH/2, radiusSmall())` — Square-safe).

**C-6. AbstractButtonMixin.** Targets exactly `Button.Plain` on
JoinMultiplayer/SelectWorld, gated by `customTitleScreen`. Carries a
hand-rolled 140 ms snap-in/ease-out hover with `isHovered() || isFocused()`
aliasing and a press-squash duplicate of Button's math; radius already
tokenized. Path: **painter migration** — delegate the inject body to the
shared Button painter, adopt `HoverAnim.symmetric(140)` with hover/focus
decoupled (focus = the Button-family hairline), keep vanilla routing
(focus/keyboard/narration/click) untouched. Its own pilot; risk is contained
to two vanilla screens.

**C-7. Square-mode / radius conformance.** ~93 production radius sites
resolve through the theme (live `roundness().radius*()` or the projected
`AuroraTheme.RADIUS*` facade — both correct; facade reads are idiom, not
bugs). Genuine SQUARE-mode nonconformances (finite list): AuroraScreen chips
5 / layout buttons 4 / tiles 6 (both glass and flat paths — the only screens
passing literal radii to `GlassSurface`), ManagerListScreen toast 4 (inherited
by both managers), ColorPickerScreen preview 4 + pad/strip frames 6 (policy:
data-adjacent content frames — rule them exempt or tokenize), Keystrokes key
cells 3, HudBackgrounds 2 (documented "boxier" deviation — sanction or
tokenize), four scrollbar-thumb capsules at 2 (policy: mechanical exemption
like toggle/slider tracks, or square them). HudEditor's editor chrome is
plain fill rects (no radius in ANY mode — status-overlay family; ruling).
ToggleSwitch/Slider tracks and all knobs stay circular by the mechanical
exemption. A shared `radiusSmall`-style resolver helper is warranted only if
the facade-idiom unification is wanted; the conformance fixes themselves are
token substitutions.

**C-8. Correctness hardening batch (independent smalls).** Manager editor
clip (C-1e), pack grid clamp + modal-animation rect, PixelCanvas measuring
click-leak, Escape asymmetry (PixelCanvas W/H + the three search fields:
Escape while typing closes the whole screen — route to unfocus like the pack
browser), HudEditor X key (implement or reword the hint), create-row consume
hardening (bounds-based → availability-aware), search-band hit rects 2 px
taller than the painted fields, ColorPicker short-window hex/button overlap,
Enum popup no-flip-up near screen bottom (rows lost off-screen).

**Dependency graph (derived):** C-1 bounds truth → C-2 hosting → C-2b
navigation adoption; C-3 group primitive → C-5 SegmentedControl → arrows on
pack tabs/Accent; C-4 icon primitive → compact-action rollout; C-6 and C-7
independent; C-8 rides anywhere after C-1. **Sequence:** 1) C-1 pilot on
AuroraScreen (+ClipBand) [flagship]; 2) C-7 wave 1 after rulings [flash];
3) C-2 hosting [flagship]; 4) C-2b navigation + sound policy [flagship];
5) C-3 primitive → C-5 segmented → arrows [flagship → flash]; 6) C-4 pilot
(ItemScale/Effect/HudEditor-X) → rollout [flagship → flash]; 7) C-6 vanilla
painter pilot [flagship]; 8) C-8 batch [flash]; 9) §15.2 conformance harness
+ closure audit [flagship]. Each step independently revertible; screenshots:
ROUND pixel-parity A/B everywhere (no intended rest-state change except the
ruling items), SQUARE captures for C-7, hover-timing captures for the
immediate→140 ms migrations, numeric hit-bound oracles as the primary
evidence for C-1 (screenshots alone cannot prove input agreement).

**Phase C closure criteria:** render/click/hover bounds coupled through one
band truth on every scrolling host (AuroraScreen both tabs, pack grid,
manager editors); Square mode squares every non-exempt rectangular chrome
(exception list finite and documented); compact actions canonical (semantic,
canonical hover, narrated, disabled-gated) or explicitly classified
(KeyList chips, popup rows); AuroraScreen hosts the deferred component
contracts (enums, accent peers, segmented, toggles, tabs/layout/tiles with
the pack contract); arrow navigation exists on pack tabs + Accent + segmented
via one group primitive; SegmentedControl conforms; AbstractButtonMixin
painter migrated; C-8 items closed or re-classified; the conformance harness
runs the §15.2 matrix; contrast/disabled-material work handed to Phase D+.
Phase B compatibility guardrails: the Enum popup's immediate-hover rows and
the Profile-create consume-but-inert rule are ACCEPTED Phase B semantics —
not reopened; the KeyList chip hover rationale is preserved unless explicitly
changed; new sounds use only the existing vanilla-click ACTIVATION mapping
(identity is Phase F).

**C-1 PILOT RECORD (2026-09-19, "couple aurora screen viewport input").**
Implemented and verified on AuroraScreen; `ClipBand` exists but ONLY
AuroraScreen adopts it in this pilot (unit-pinned — no opportunistic
rollout).

- **Primitive:** `ui/util/ClipBand` — an immutable 4-int rectangle
  (x, y, width, height; half-open `[x,x+w) × [y,y+h)`, the
  `Widget.inBounds`/`SemanticActionControl.contains` convention; edge
  contact is NOT intersection; degenerate bands are inert). API:
  `contains(px,py)`, `intersects(rx,ry,rw,rh)`, `clampY`, `isEmpty`.
  No Minecraft imports (headless-testable), no layout framework, one
  small allocation per call-site loop (hosts hoist one per frame/event).
- **AuroraScreen viewport ownership:** `contentViewport()` returns the
  ONE truth — the content scissor's own rectangle
  `[mainX(), boxY()+36] .. [mainX()+254, boxY()+230]` (now named via
  `CONTENT_TOP_INSET/CONTENT_BOT_INSET/CONTENT_W`). It drives the
  scissor (all four call sites + the ScrollFade anchor), the tile/row
  render cull, the click walk, the tile hover test, and the
  focused-setting keyboard availability. Two regions deliberately stay
  SEPARATE and documented: the scrollbar TRACK band (`viewTop/viewBot`,
  12px inset at top / 2px at bottom — thumb pixels preserved exactly)
  and `computeMaxScroll`'s 180px visible-height tuning (scroll physics
  unchanged; the former 180-vs-194 extent discrepancy is REPORTED, not
  "fixed" — not unambiguously the same truth).
- **Settings coupling:** the click walk is now inside
  `else if (vp.contains(mouseX, mouseY))` — point ∈ viewport ∩ raw rect
  is the actionable region (a pointer outside the band cannot reach any
  row, header zone, or inline EditBox; partially visible targets answer
  only on their visible pixels). The render walks gate on
  `vp.intersects`, replacing the old 12px-tighter cull band.
- **Modules coupling:** `cardBounds(i, mods, vp)` culls by viewport
  intersection; tile hover and click require `vp.contains(pointer)` —
  the hidden part of a partially clipped tile is inert (hover and
  activation).
- **Keyboard/focus:** the render walk publishes per-row availability
  (`onInteractionAvailabilityChanged`) and tracks whether the
  registry-focused setting intersects the viewport
  (`focusedSettingVisible`, reset at frame start — the Modules tab
  reads unavailable). `keyPressed`/`charTyped`/`mouseScrolled` gate
  routing on it: a scrolled-away focused row no longer takes action
  keys or eats the wheel. Focus is not forcibly cleared (gating =
  "made unavailable", the ManagerListScreen convention; scrolling back
  restores routing — no re-click needed). Capture-family safety: the
  Settings tab hosts no Keybind/KeyList rows (N/A), but the
  availability hook is the same one their teardown uses.
- **Forgiving hit-target ruling:** the header toggle's 40px click zone
  (vs the 28px painted switch) is INTENTIONAL (it tiles the header
  row's right edge against the navigation zone) — retained, now
  viewport-intersected like every other target.
- **SemanticActionControl availability:** unchanged in C-1 (band-level
  availability stays on the Phase B hosts; AuroraScreen materializes
  no controls). Rect-level availability is C-2's host-sweep work.
- **Verification:** 156 tests / 0 failures (140 Phase B + 16 new:
  `ClipBandTest` pins the intersection semantics;
  `AuroraScreenViewportCouplingTest` pins the source contract —
  scissor-from-band, the ungated literals' absence, click/hover gates,
  keyboard gating, and AuroraScreen-only adoption). DevPilot `c1bounds`
  (untracked, wiring removed after): title context, GUI scale 1,
  forced DARK/RED/ROUND/Wireframe@1.0; **v3 18/18 oracles** (the C-1
  matrix, cases 1-15); the stashed `4d480b9` baseline runs the same
  harness at **17/1 with flipped expectations** — the discriminators
  reproduce the defects: a hidden-part zone click flips a setting,
  fully-above-viewport clicks flip settings and open detail screens,
  the clipped tile's hidden pixels toggle a module, and an
  off-viewport focused row still takes arrow keys. The baseline's one
  FAIL is `settings-5` (below-viewport row): its walk is ungated but
  the slider WIDGET's bounds are render-coupled — a never-rendered row
  has no layout, so the baseline is protected by accident, not design
  (v3 rejects structurally). Pixel evidence (`.devpilot-c1/`): rest
  parity v3-vs-baseline 103/86400 (settings) and 41/86400 (modules)
  window px beyond ±8/255, mean 0.41-0.49 (panorama-phase sampling
  noise — the fixture's window interior composites the rotating menu
  panorama, measured at mean 12.7 between 2-tick-apart same-state
  captures, and matched-phase-offset cross-boot pairs align it);
  mid-scroll parity 45 + 11 px (the cull unification's visible effect
  is confined to transient 12px slivers while a row crosses the old
  band edge); the hover discriminator with a control-strip reference:
  baseline lights the tile's visible slice from a pointer on its
  HIDDEN pixels (+1.17 excess over its own sidebar control strip ≈ its
  visible-park control +1.07), v3 does not (−0.06; its visible-park
  control +1.19 lights). SQUARE captured as a regression observation
  only.
- **Remaining ClipBand consumers (C-8/rollout decision):** the pack
  browser's card-grid click walk (unclamped — invisible cards open the
  detail modal), the manager inline rename/create editors (stale hit
  rects + unclipped paint), and the pack modal's open-animation rect.
  Harness note: `c1bounds` also demonstrated the fixture-bookkeeping
  class (grid tile 0 is `zoom`, not `world_map` — key on the live grid
  order).

**C-7 PILOT RECORD (2026-09-19, "conform square mode geometry" — Wave 1).**
The finite literal-radius inventory was rebuilt from source at `3b47974`
(matching the planning audit) and every site classified. Migrated
(RECTANGULAR_CHROME → the existing `radiusSmall()` token, glass and flat
paths together): AuroraScreen sidebar chips (5→6), layout buttons (4→6),
module tiles (6→6 — ROUND-pixel-identical), the manager toast (4→6, the
pack browser's toast already passed the token), and the ColorPicker
preview frame (4→6 via the ColorSwatch `min(h/2, radiusSmall)` pattern —
the preview is a represented-color sample like every ColorSetting swatch).
No new radius abstraction was needed (the planning-audit conclusion held).

*Policy rulings (explicit, pinned at the source sites + by tests):* the
picker's pad/hue/alpha frames are DATA-DRIVEN — color-picking gradient
surfaces (§16's exception domain), Square-exempt; Keystrokes' keycap
radius 3 is REPRESENTATIONAL HUD geometry (drawn keycaps), Square-exempt;
HudBackgrounds' radius 2 is the already-documented "boxier" HUD deviation,
SANCTIONED (not silently changed); scrollbar-thumb capsules (4 sites,
radius 2) are MECHANICAL direct-manipulation chrome (the toggle/slider
track family), Square-exempt; HudEditor's plain-fill chrome is already
square in both modes — conformance satisfied, ROUND rounding deliberately
NOT added (no design-language requirement; would expand Wave 1).
ToggleSwitch/Slider tracks+knobs verified untouched (pinned).

*Verification:* 167 tests / 0 failures (156 + 11 in
`SquareModeConformanceTest`: the token table, the glass/flat source
contracts for every migrated family, the literal-ban pins, and the
explicit-exemption pins). Runtime `c7square` (untracked, wiring removed):
12 captures per tree (ROUND/SQUARE × Wireframe-flat/Frosted-glass on
AuroraScreen, toast, picker) on the migrated tree and the stashed
`3b47974` baseline. SQUARE corner oracle: the picker preview's corner
cell fills 64/64 red in SQUARE vs 58/64 in ROUND (the 6px arc removed) —
decisive; the tile corner cell flips 7/64 px at max=179 with a 0.00
drift floor; the layout-button corner flips 7/64 at max=96. ROUND parity
(after vs before, matched fixtures): module tiles byte-identical; the
total window diffs are 16 px (settings tab — exactly the selected chip's
four corner arcs, 5→6) and 120 px (modules tab — the selected chip +
layout-button corner arcs, 4→6; bands localize to those corner cells and
nowhere else), toast 18 px and preview 52 px full-frame — the deliberate,
quantified cost of replacing accidental literals with the semantic token.
The toast's own corner is palette-invisible (fill ≈ backdrop at the dev
fixture) — its conformance rides the same proven token mechanism plus the
source pin. Cache audit: every affected cache is keyed on
`ThemeManager.generation()` (AuroraScreen's layer cache, Button templates,
manager row templates), which bumps on the roundness reload — no
fingerprint fix needed. C-1 regression: the full `c1bounds` suite re-ran
on the C-7 tree at 18/18. The first capture round was invalid (the
harness forgot to open AuroraScreen — all eight "AuroraScreen" captures
were the title screen); fixed and re-run on both trees.

*Mimosa (per-task requirement):* the plugin's deep project scan ran to
COMPLETION during this task (19 s, seal
`sha256:3489eb…`, 0 findings, dependency scan completed, 0 advisories;
evidence boundary static-only) — the `scanner_enobufs` pre-commit
condition did NOT recur. The scan's coverage is thin (1 package, 19 s
for a 48k-line tree), so this is completion evidence, not a broad
security claim.

*Remaining Square-mode work:* none identified in the Wave-1 inventory —
every production literal radius is now either migrated or an explicitly
ruled exemption. New chrome must resolve through the tokens (the
source-contract tests document the pattern).

**C-2 IMPLEMENTATION RECORD (2026-09-20, AuroraScreen semantic hosting).**
`ui/interaction/SemanticControlHost` now owns the narrow lifecycle shared by
semantic hosts: identity-idempotent registration in visual order, widget-list
rebuilds, frame availability resets, stale vanilla-focus cleanup, pointer-to-
semantic focus, and immediate off-host invalidation. It has no layout or
concrete-setting knowledge. `ManagerListScreen` uses the helper for its
existing dynamic row controls without changing its ordering or availability
policy; `FeatureDetailScreen` remains on its established host path because
folding its capture-aware setting lifecycle into this extraction would have
expanded C-2 and risked Phase-B behavior.

`AuroraScreen.init()` now asks every Settings-row component for the semantic
controls it already owns and adds them as vanilla children exactly once per
screen rebuild. The exact hosted inventory is 13 controls: the two Text &
Fonts `EnumSetting` triggers, `AccentSetting`'s ten visual-order peers (Blue,
Indigo, Purple, Pink, Red, Orange, Yellow, Green, Teal, Custom), and the
Interface `EnumSetting` trigger. The existing vanilla search `EditBox` is
added first and remains singular; it is visible/focusable only on Modules.
No controls are constructed or registered in the frame path.

Availability is re-derived every frame from C-1's single authoritative
`ClipBand`: a hosted action is available only on Settings, while its row is
visible, and while its own actionable rectangle has a non-empty half-open
intersection with the band. Thus partial visibility remains interactive,
matching C-1 pointer policy without focus jumps at an edge; edge contact or a
fully clipped rectangle is unavailable. Component enabled state remains the
separate authoritative `SemanticAction.enabled()` gate, so a geometrically
available disabled action can still narrate its disabled state while traversal
and activation reject it. The sweep clears stale vanilla focus.
Tab changes and screen removal invalidate the whole host immediately, and
resize/re-init clears old focus then re-adds the stable component-owned
controls without accumulating children. Manual pointer activation remains in
the component; after the one accepted click, the host focuses the exact peer
or trigger under the pointer and never replays the action or sound.

The three enums now receive vanilla Tab focus, Enter/Space activation,
component-owned narration/focus chrome/sound, and viewport authority on this
host. Losing availability also collapses an expanded enum, resets its popup
scroll, and releases setting focus, so an offscreen popup cannot keep keyboard
or wheel ownership. Accent's ten existing peers receive the same host
lifecycle; preset selection, literal selected-state derivation, no-op silence,
and Custom's editor-without-value-change semantics remain component-owned.

Explicit deferrals remain explicit: three `SegmentedSetting` rows expose no
semantic controls (C-5); Theme Opacity is still a manual continuous setting;
the four Settings header toggles and AuroraScreen's tabs/sidebar/layout/module
tiles are navigation/chrome work for C-2b; Accent roving/arrow navigation is
C-3. `ThemePreview` stays preview/data only and contributes no child or Tab
stop. C-2 therefore closes hosting for every Phase-B-complete inline contract,
but does not make AuroraScreen generally Phase-C complete.

Verification: 180 tests / 0 failures (the 167-test baseline plus 13 focused
host/AuroraScreen cases), including deterministic identity registration,
rebuild/clear, live registration, availability and stale-focus cleanup,
disabled/unavailable rejection, ordering, exact inventory and deferrals,
C-1 coupling, tab invalidation, pointer ownership, search ordering,
ThemePreview isolation, and Enum/Accent regressions. The untracked `c2host`
harness defines 22 runtime oracles and representative dark/light captures,
but the dev client did not reach its first rendered screen in this environment
after initialization, so 0/22 runtime oracles executed and no new visual
evidence was produced. The pre-task dev-pilot properties and local config were
restored byte-for-byte. No radius code changed; C-7 source/unit contracts pass.
Verdict: **C-2 AURORA SCREEN SEMANTIC HOSTING COMPLETE** at the code boundary;
runtime/visual evidence remains an explicitly recorded environment gap.

**C-2b IMPLEMENTATION RECORD (2026-09-20, "adopt aurora screen navigation
semantics").** AuroraScreen's manual navigation and chrome joined the frozen
semantic contracts through the same C-2 host — 47 new screen-owned
`SemanticActionControl`s alongside the 13 component-owned ones (60 total,
one canonical registration in `init`, identity-stable factories so a
resize's rebuild re-registers the same objects). The rebuilt inventory and
its classification (verified from source; the C-2 report's list confirmed
with two precision notes):

- **Sidebar Mods/Settings tabs — NAVIGATION** (the frozen pack-navigation
  contract, semantics not implementation): persistent selection through the
  stained tint / flat accent wash — never the hover animator (the four
  states selected/unselected × rest/hover never collapse); pointer-only
  `HoverAnim.symmetric(140)` per tab (the former immediate text/fill
  flip); Tab focus + the Button-family 1 px accent hairline; Enter/Space
  and pointer clicks converge on ONE `selectCategory` path that plays
  exactly one ACTIVATION on a real destination change and consumes the
  already-selected click as a silent no-op (no rebuild, no scroll reset —
  per-tab scroll positions survive by construction). A real switch runs
  `semanticHost.deactivateAll()` immediately, so no off-tab control keeps
  focus or can activate.
- **Profiles chip — ACTION** (opens the profile manager; no persistent
  selection, so navigation would misclassify it): canonical hover, focus
  hairline, Enter/Space, narration, one activation click.
- **Layout pair (list/grid) — PEER_SELECTION, DEFERRED TO C-5** (the task's
  default preference): it is the same peer-value family `SegmentedControl`
  belongs to, and C-5 establishes that contract (C-3 the arrow group).
  Remaining gap: immediate hover, no semantic controls, no peer-group
  semantics — all land with C-5. No one-off peer primitive was built.
- **37 module tiles — COMPOUND CARDS**: one control per module, keyed by
  stable module id (finite: the ModuleManager set; search filtering changes
  only availability, never the control set). Primary action (left click /
  Enter / Space) toggles the module through the action — exactly one
  activation, one click, the `Module.setEnabled` save; the enabled state is
  the persistent stained channel, independent of hover (which animates the
  transient wash at 140 ms from the pointer only). The right-click detail
  navigation stays pointer-specific (no keyboard chord invented; C-3/C-5
  don't cover it) and plays one click at the site — parity with the
  Settings header that opens the same destination, so sound policy does
  not depend on the route. Availability is the C-2 partial-visibility rule
  over C-1's ClipBand (the render walk re-marks exactly the
  viewport-intersecting tiles): a hidden tile is not focusable,
  keyboard-actionable, narrated, or hoverable, and clipped pixels are inert
  (the click walk's `vp.contains` gate predates C-2b and is unchanged).
- **Settings headers — 3 NAVIGATION + 1 grouping chrome**: text_fonts,
  theme, and interface navigate to their detail screens (Enter/Space and
  the header click route through a per-entry control; hover is the chevron
  affordance brightening at 140 ms — a text row, so no fill wash is
  invented); custom_title has no detail screen and gets no control (its
  header is a label + toggle only).
- **4 header toggles — TOGGLE (the Phase-B Boolean adapter)**: the existing
  `ToggleSwitch` stays painter/state mechanism (its own canonical hover wash
  and thumb press pulse), and a per-entry control owns the ONE activation
  source — pointer, Enter, Space all run `toggle + save` exactly once
  (`BooleanSetting`'s wiring); the switch itself is never clicked, so no
  double-toggle. The forgiving 40 px zone IS the control bounds (C-1
  ruling retained); the focus hairline paints through
  `ToggleSwitch.focusedVisual`, synced per frame.

Traversal order (child-list = Tab order, unavailable controls skipped):
search field (Modules only — hidden on Settings) → Mods → Settings →
Profiles → [Modules: 37 tiles in ModuleManager grid order | Settings:
custom_title toggle, text_fonts nav+toggle, its 2 enums, theme nav+toggle,
Accent's 10 peers (+ non-hosting rows), interface nav+toggle, its enum].
Sound ownership: every accepted activation lives in the action (tabs carry
`SemanticSound.NONE` and play inside the behavior only on real change;
Profiles/tiles/nav/toggles construct via `SemanticAction.button`); the
host never plays sounds; hover/focus/rejection silent. Press treatment per
family: none for navigation/cards/headers (the state/destination change is
the feedback); the toggle keeps its existing thumb pulse. Narration:
tabs Selected/Not selected, tiles Enabled/Disabled + description, toggles
On/Off, Profiles/header-nav action descriptions. No radius code changed
(C-7 contracts pass); the header hairline reuses the radius passed in from
`renderLive`, keeping the C-7 test's four-derivation pin exact.

Verification: **198 tests / 0 failures** (the 180 baseline + 18: 17 new
`AuroraScreenNavigationTest` source-contract pins — inventory, deferral,
registration order/lifecycle, inactive-tab reachability, state/hover
independence, no-op + exactly-once, narration, ClipBand availability,
press treatment, pointer-ownership ordering — and the updated C-2 hosting
pin for the selectCategory convergence). Runtime (gamescope bypass — see
below): DevPilot `c2bnav` **33/33 on BOTH dark and light boots** (hover
enter/exit/reversal sampled from the real animators, silent no-op, Enter
and Space single-flip, Tab-reach for card/Profiles, persistence under
parked hover, clipped-pixel inertness, hidden-card exclusion, header
nav/toggle keyboard activation, off-viewport unavailability, narration
metadata, tab invalidation both directions, resize singularity); the
deferred **C-2 `c2host` matrix now runs 22/22 on BOTH themes** (updated to
the 60-control inventory with name-based lookups — the component-owned 13
remain an in-order subsequence behind the sidebar trio), closing C-2's
runtime debt; `c1bounds` **18/18**; `c7square` re-captured (ROUND/SQUARE ×
flat/glass — SQUARE AuroraScreen captures included). Pixel/ROI evidence
(dark, `.devpilot-c2bnav/`): the selected Mods chip is byte-stable while
another chip is hovered (0/1408 px — selection persists at hover 0), the
focus hairline reads as a 168-px ring ROI on the chip and an accent
(154,9,34) pixel row on the focused card, and the hover treatments are
live (1404/1408 px per hovered chip). Harness notes: the dev-window
captures are 854×480 (gamescope composites larger), and the C-1
fixture-bookkeeping lesson recurred — ModuleManager grid 0 is `zoom`, not
`world_map` (world_map sits at grid 33, below the fold). Two harness-side
oracle bugs were found by the first boot (a mid-flight exit legitimately
reaches 0 inside 2 ticks — the no-snap proof needs a 50 ms sample; the
tab-invalidation oracle originally asserted the wrong direction for the
new tab's own controls); both were harness fixes, zero production changes.
The C-2 stall did not reproduce under gamescope this session: every boot
reached first render, ran its matrix, and exited cleanly.
Verdict: **C-2B AURORA SCREEN NAVIGATION COMPLETE** — remaining
AuroraScreen Phase C gaps belong to C-3 (arrow groups on the tabs/peers)
and C-5 (SegmentedControl conformance + the layout pair), both documented
above.

**C-3 PILOT RECORD (2026-09-20, "adopt semantic group arrow roving").**
The peer/navigation group primitive landed, with arrows on all three named
consumers.

- **Primitive:** `ui/interaction/SemanticControlGroup` — an ordered member
  ring with a geometry (`horizontal()` Left/Right ±1, `vertical()` Up/Down
  ±1 — the pack sidebar is a top-to-bottom stack, so its tab-list arrows
  are Up/Down, the shape the audit's "Left/Right" shorthand actually
  described; `grid(columns)` ±1 / ±columns). Membership is declared at the
  control's creation site by `attach` (identity-idempotent, first-attach
  order = visual order, for the control's lifetime) and carried as a
  back-reference on the control (`interactionGroup()`), so a host's ONE
  interceptor line serves every group it hosts with no host-side registry
  and zero changes to `SemanticControlHost` — the host still knows nothing
  about tabs/cards/toggles/groups. The screen seam is the static
  `SemanticControlGroup.rove(focused, event, setFocused)`, called in
  `keyPressed` BEFORE `super` (the audit's bytecode fact: 1.21.11's
  `Screen.keyPressed` reaches the container walk via invokespecial, so a
  traversal override cannot implement a group — consuming the arrow before
  super both scopes the move and stops vanilla's unscoped spatial walk from
  escaping the ring). **Manual activation policy** (the audit's recommended
  option, now the only one): arrows move focus SILENTLY — no behavior, no
  sound, no selection; Enter/Space/click stay the only selection paths
  (selection has side effects: category switches reset the pack grid,
  accent changes reload the theme). Cross-axis keys are not owned (a
  horizontal ring lets Up/Down fall through to vanilla). Eligibility
  mirrors `nextFocusPath`'s gate (available && action-enabled && focusable)
  so arrows can never land where Tab cannot; the pack browser's modal
  containment and every ClipBand sweep govern roving targets for free.
- **Movement math, and the bug the boots caught:** candidate order is the
  exact stride target first (grid Up/Down land one row away), then onward
  member-by-member in the step's direction through the whole ring, with
  `current` skipped rather than terminated on. The first draft iterated
  stride multiples (`from + k*step mod n`) — and a stride that divides the
  member count (`gcd(5,10)=5` for the accent grid) visits only a sub-orbit
  {i, i+5}, so with the direct target clipped the scan declared "nowhere
  to rove" while eligible members sat one position off the orbit. The
  first dark boot failed exactly there (DOWN inside a half-clipped accent
  grid dead-ended; UP fell through to vanilla, which spatially escaped to
  the Theme header control), the unit suite had missed it because every
  asserted pair happened to sit inside one orbit, and the fixed walk is
  pinned by `gridRovingWalksTheFullRingWhenTheStrideTargetIsClipped`
  (clipped direct target → next member in direction; whole lower row
  clipped → wraps to the first eligible member; the arc between current
  and the stride target stays reachable).
- **Consumers:** (1) AuroraScreen's sidebar Mods/Settings tabs — one
  horizontal ring; Profiles deliberately NOT a member (a plain action,
  C-2b's ruling — grouping it would make arrows "select-ish" over a
  control that opens another screen). (2) AccentSetting's ten peers — the
  ring is declared ON THE COMPONENT (`grid(PER_ROW)` attached in
  `peerControl(i)`) and travels with `interactionControls()`, so
  AuroraScreen's Settings tab and the Theme `FeatureDetailScreen` get the
  identical ring with zero extra wiring; both hosts added the one `rove`
  line. (3) The pack browser's 21 category tabs — a vertical ring in
  category order (header rows contribute no member), attached in
  `tabControl(i)`, `rove` before `super` after the search-field branch.
  SegmentedControl segments + the AuroraScreen layout pair remain C-5
  (they consume this primitive; the C-2b deferral pins stay accurate).
- **Sound/narration:** unchanged by construction — arrows activate
  nothing, so the existing exactly-one-click ACTIVATION model on real
  selection is untouched, and focus moves narrate through vanilla's own
  focus-change narration.
- **Verification:** unit suite 213/0 (was 198; +10
  `SemanticControlGroupTest` behavioral, +2 AuroraScreenNavigationTest
  roving pins — ring membership, Profiles exclusion, the
  rove-before-super seam, no screen-local arrow math; +2
  AccentSettingPilotTest — component-declared grid + both hosts' seam +
  real-peer roving incl. the disabled gate; PackTabsPilotTest's
  Phase-B deferral pin flipped to an adoption pin). Runtime: DevPilot
  `c3rove` boots under gamescope, dark AND light, **19/19 oracles each**:
  sidebar (silent Right/Left with wrap both ways, selection byte-stable
  during roving, cross-axis falls through, Enter selects the roved-to
  tab), accent grid on BOTH hosts (±1/±row steps, ring wrap, accent
  literal unchanged through every arrow, Enter selects Pink
  `0xFFE91E63` / Orange `0xFFFB8C00` through the real action), pack ring
  (21 tabs, Down one, Up wraps to the last band-visible tab, Enter
  selects category `blocks`). Captures in `.devpilot-c3rove/{dark,light}/`
  (sidebar focus ring on an unselected tab mid-rove, focused accent peer,
  focused pack tab). Harness lessons recorded: probe scroll on the
  grid's SECOND row (a row-1-visible probe leaves the oracles testing a
  half-clipped grid — which is how the stride bug was found), and
  `AURORA_DEV_CAPTURE_DIR` must be absolute (the game CWD is `run/`;
  relative capture paths silently no-op).
Verdict: **C-3 GROUP PRIMITIVE COMPLETE** — arrows exist on the pack tabs,
the Accent peers (both hosts), and the AuroraScreen sidebar through ONE
primitive; C-5 (SegmentedControl conformance + the layout pair) is now
unblocked and is the next phase per the dependency graph.

**C-5 PILOT RECORD (2026-09-20, "conform segmented peer selection").**
SegmentedControl joined the Accent peer-selection family, and the C-2b
layout-pair deferral closed — AuroraScreen's last deferred peer-selection
surface.

- **Inventory (rebuilt, not trusted):** exactly three production
  `SegmentedSetting`s, all on the Theme entry — Mode (2 peers), Corner
  Style (3), Glass Style (2) = 7 peers, hosted by BOTH the Theme detail
  screen and AuroraScreen's Settings tab. No other `SegmentedControl`
  construction exists. The baseline defect was confirmed verbatim: hover
  was immediate (a boolean) AND gated off on the selected segment
  (`!isSelected && inBounds`), the exact Phase-B conflation.
- **Component contract:** selection stays the live-derived enum (no stored
  index); hover is pointer-only `HoverAnim.symmetric(140)` per segment,
  long-lived, NEVER gated on selection — the transient channel is a
  translucent ON_BACKGROUND wash (0x1A·hoverT) that composes over
  stained/neutral glass and flat fills alike (the module-card idiom), so
  selected+hover is representable everywhere; the unselected label lerps
  SECONDARY→ON_BACKGROUND by the same animator. Focus is the Button-family
  1 px accent hairline (0x99), one site, independent of both channels.
  `interactionControls(settingLabel)` materializes one
  `SemanticActionControl` per segment on first ask, attached to a
  component-owned `SemanticControlGroup.horizontal()` — the C-3 ring
  travels with the controls to every host; `SegmentedSetting` just
  forwards them, and BOTH hosts registered them through the existing
  generic C-2 loop with ZERO screen-side segment logic (the hosting test's
  `instanceof SegmentedSetting` ban still holds). Pointer converges on the
  semantic action when materialized (the EnumSetting routing precedent —
  hosts that never asked keep the silent direct path). Sound: the action
  carries NONE and plays exactly one ACTIVATION inside the behavior, only
  on a genuine change; the already-selected peer is a silent consumed
  no-op. Narration: option name + "Sets <row> to <option>." +
  Selected/Not selected, disabled included. Disabled stays the
  authoritative gate (row guard + the action's `!disabled`, synced per
  frame by renderOverlay). **The baseline's double-save is gone:** the
  constructor wrapper added `AuroraConfig.save()` on top of every Theme
  setter's `persistThemeChange()` — the setter is now the one persistence
  path (the exact redundancy the Accent pilot removed). One adjacent
  adapter fix: `MinecraftSemanticFeedback.play` is null-instance-safe
  (headless unit tests have no sound manager; a no-op there is the honest
  adaptation).
- **Layout pair (AuroraScreen):** the narrowest solution per the audit —
  NOT the text-segment component (the 20×20 vector-icon geometry is not a
  text track; forcing it would be a visual redesign) but the peer SEMANTIC
  primitive on the existing painter: two `SemanticActionControl`s + a
  horizontal ring + canonical hover animators + the hairline, registered
  through the same `SemanticControlHost` between Profiles and the module
  cards (Modules-tab traversal: search → Mods/Settings/Profiles → the pair
  → tiles). Visual preservation: 20×20 geometry, vector icons, spacing,
  selected treatment (stained glass / accent wash + border), C-7 radii,
  placement — all unchanged; hover intentionally becomes canonical 140 ms
  (was immediate AND suppressed on the selected peer) and the hairline is
  intentionally new. Clicks route through the actions (the raw
  `gridLayout = …` assignments are gone); the already-selected layout is
  a silent consumed no-op; the represented value is the live `gridLayout`
  field. Availability: marked only in the Modules walk — the Settings tab
  leaves the pair swept unavailable.
- **Traversal (Settings tab, with the three groups inserted):** text_fonts
  nav+toggle, 2 enums, theme nav+toggle, Mode×2, Corner×3, Glass×2,
  Accent×10, interface nav+toggle, UI-FPS enum — the hosted component
  inventory grew 13 → 20 and the screen total 60 → 69 (the c2bnav
  harness's inventory oracles updated accordingly). Within each group:
  Tab walks peer 0→N (vanilla child order = registration order); Left/
  Right is group-local through the C-3 seam the screens already carry.
- **Verification:** unit suite **225/0** (was 213; +12
  `SegmentedSettingPilotTest` — topology, live-derived selection, pointer
  convergence on both paths with exactly-once commit and selected no-op,
  disabled rejection, keyboard exactly-once, horizontal ring + cross-axis
  null, and the source pins: canonical hover with no `!isSelected` gate,
  one hairline site, one play site, no `AuroraConfig.save()` in the row,
  no local arrow math, hosts stay generic). C-2b site-count pins updated
  exactly as grown (7 focus sites, 5 no-press sites, 5 hairline sites,
  layout registration order) and the deferral pin flipped to an adoption
  pin. Runtime: DevPilot `c5segments` boots, **26/26 dark AND light** —
  segmented (silent focus-only arrows with value byte-stable and hover 0
  during keyboard focus, Enter flips Mode exactly once with the reload
  generation bump, selected reactivation consumed with generation STATIC,
  wrap, Corner cross-axis value-untouched, Corner/Glass genuine flips +
  restore), scroll-out (all 7 peers unavailable) and scroll-back, layout
  pair (REAL Tab reach in 4 hops from the search field, Enter-selects
  list, focus-only Right with the value held false, one selection, silent
  no-op, wrap, selected-persists-hover0 with focus independent, canonical
  hover live at 0.63 mid-flight), search-EditBox caret keys kept, enum
  trigger not roved, tab-switch invalidation, resize/re-init with no
  duplicates (69 children, group stable). Pixel evidence: the selected
  Grid button's border reads exactly (235,0,41) full-alpha OnePlus Red
  while the focused List button's edge is the blended hairline channel —
  and the first capture pass exposed a harness lesson now recorded:
  mcCapture grabs the LAST rendered frame, so a capture in the same tick
  as a focus move shows the previous frame's focus (captures moved one
  tick later; the state-reading oracles were never affected).
  Regressions: `c3rove` 19/19 (Accent/pack/sidebar rings unchanged),
  SquareModeConformanceTest green (radii untouched —
  `min(trackH/2, radiusSmall())` and the layout pair's radiusSmall()
  survive verbatim), full suite + build green.
- **Scope kept:** AccentSetting and pack navigation untouched (regression
  only); ThemePreview/mock controls still expose nothing; no Phase D+
  contrast work (the light theme's disabled-label contrast stays Phase D).
Verdict: **C-5 SEGMENTED CONTROL CONFORMANCE COMPLETE** — every production
segmented peer uses the canonical peer-selection contract, AuroraScreen
hosts them generically, the layout pair is adopted through the same
primitive, and C-3 navigation is reused with zero new arrow math.

## 7. Registries (the drift trap)

Three parallel structures with no single source of truth:
1. `FeatureManager` — ~29 runtime `Feature` singletons (tick logic).
2. `screen/FeatureRegistry` — 35 MODULES + 4 SETTINGS `FeatureMetadata` (UI metadata +
   settings widgets + `reset()` via `AuroraConfig.resetByPrefix` reflection over a DEFAULTS
   snapshot; SETTINGS includes Theme — moved from MODULES 2026-09-09. The combined
   "Miscellaneous" entry — the eight former tiles below Interface, one detail screen,
   one unified reset — moved back to MODULES 2026-09-10).
3. `module/ModuleManager` — 35 hardcoded presentation cards consumed by `AuroraScreen`'s grid;
   silently null for unknown ids.

**Enabled state of every feature is a public boolean on `AuroraConfig`**, read fresh each
tick. Known drift: the missing `reflex` card was added 2026-09-05 (audit B5 fix), so the
counts match — but the two lists are still maintained by hand and can drift
again; grid name/description text is maintained separately from FeatureRegistry's and has
already diverged (audit D-note).

## 8. Gotchas a new session should know

- `AuroraTheme` statics are the legacy projection facade, not a dead system: reading them is
  correct; writing them anywhere but `ResolvedTheme.project()` is not. `IOS_BLUE` etc. hold
  OnePlus Red.
- Two unrelated `GlassStyle` enums: `theme.GlassStyle` (Frosted/Wireframe) vs
  `Button.GlassStyle` (OFF/NEUTRAL/STAINED). Same name, different meanings.
- `Button.GlassStyle` glass path disables during press-scale animation (renderPanel can't
  follow the pose) — deliberate, not a bug.
- `ToggleSwitch`/`Slider` knobs are pure white and full-capsule by convention (structural
  neutrals; knobs stay circular in every roundness mode).
- "Is this an Aurora screen?" is answered three different ways: `ThemedScreen` marker
  (EditBoxMixin), `com.aurora.client.` prefix (MixinFont), `com.aurora.client.screen` prefix
  (RenderSystemMixin).
- `defaultRequire: 1` + exact 1.21.11 descriptors (mouse-event records, `prepareText`) mean MC
  updates surface as launch crashes — version bumps are not mechanical here.
- 1.21.11 `GuiGraphics` records blits into a deferred `GuiRenderState` with no flush API:
  never destroy a texture mid-frame (output pool, rim masks, `UiLayerCache` all obey this).
- The deferred-rim queue in `GlassSurface` (§3/§6) has no opt-out for surfaces meant to
  render ABOVE an already-dimmed screen (the pack browser's detail modal, `EnumSetting`'s
  popup). Harmless today — those screens haven't adopted the glass pass, so their rims
  paint in place — but their migration needs a "above the dim, rim included" escape hatch.
- No TODO/FIXME comments exist in `com/aurora` — intent lives in long javadocs. Read them.
- `run/` at repo root is a live dev client (its `logs/latest.log` is where `[BlurPanel]` /
  `[GlassStats]` / `[canvas-cost]` evidence lands).
- The `analyze_image` vision tool is unreliable in this environment: across several
  verification sessions (2026-09-05) its results repeatedly came back echoing corrupted,
  duplicated tokens and destabilized the agent's entire output stream. Prefer
  config-file/log oracles and pixel-diff checks; treat any single vision read as suspect
  and never paste its raw output onward.
