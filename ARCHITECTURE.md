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
Color (embedded HSL picker), DoubleSlider, Enum (dropdown popup), IntSlider, ItemScale
(search + per-item slider stack), Keybind, KeyList, ParticleConfig (+ParticleRow), PixelCanvas
(crosshair editor, `CanvasTexture` cached raster + measured cost benchmark), SectionHeader,
Segmented, StringList, ThemeOpacity, ThemePreview. `FeatureSetting` provides the
shapes/overlay/glassPass render split, static focus registry, label-tooltip dwell system, and
detail-screen lifecycle hooks.

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
- **Drag routing**: screens keep an `activeDragSetting` through mouseDragged/Released.
- **Smooth scroll**: target-based exponential lerp advanced in `render()` with wall-clock dt —
  but implemented independently per screen with different τ (45–80 ms) and different scrollbar
  code (6 variants mod-wide; only AuroraScreen and the pack browser draw draggable thumbs).
- **Hover easing**: three idioms coexist — `util/HoverAnim` (smoothstep, used by widgets),
  `Button`'s hand-rolled easeOutCubic copy, and the pack browser's Map-keyed `updateHover`.
  Four animation helper classes total (`AnimationCurves`, `AuroraAnim`, `HoverAnim`,
  `ui/util/Animation`); `AuroraAnim` is the shared math library.
- **Tooltips**: `FeatureSetting` label-dwell tooltip system (1500 ms dwell, 200 ms fade).
- **HUD editor**: drag-move / corner-resize / shift+click settings / right-click hide /
  shift+right-click lock / X disable (R-Shift in world).

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
