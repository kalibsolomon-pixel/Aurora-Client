# Unified Aurora theme — approved production rollout, 2026-10-06

The user accepted the refined pilot ("that looks good") and explicitly requested
adoption across every UI screen/element plus removal of the Frosted/Wireframe cards.
This supersedes the former two-host boundary and review stop. The filename remains
stable for existing project links; this document now records production state.

**UNIFIED THEME — card refinement (2026-10-06):** follow-up visual feedback
supersedes the enclosing-window treatment. Title, main settings/modules and manager
screens have no enclosing background window: visible standalone cards own blur;
detail/editor/browser windows retain their container, with separate blurred action
cards. Aurora Settings on the title screen is neutral. New Profile, Reset, Done
and other shared buttons use translucent faces over independently pooled blur.
Ordinary embedded controls still inherit their card; floating popups remain opaque.
The renderer's bounded pool, priority degradation and screenshot/session/menu/resource
guards remain authoritative. Capture count now follows visible standalone cards.

Production appearance is Dark only. Legacy LIGHT configs/profiles migrate to DARK;
resolution and the dirty-check normalize it too. The Mode selector, Theme preview,
and Text & Fonts/Theme/Interface header popouts are retired. These settings remain
inline, with font/accent/corners/opacity customization intact. Repeated feature and
manager headings are removed; Better Hitreg's attached author credit remains.

Bundled text fonts rasterize at 9 px rather than 11 px; icon fonts remain unchanged.
Button/segment/setting labels reserve control/value space and ellipsize with native
font measurements, preserving full semantic labels. UI scroll tracks use a finite
90 ms glide with one rounded geometry position for all layers, immediate thumb
dragging and exact rest; the pure legacy exponential API remains for diagnostics.
Typing resumes in a visible search after selection, without taking input from a
key-capture/editor or an open browser modal. Slider knobs paint above hover/focus.
Swatch color/checker pixels share a device-pixel rounded mask at rest and hover;
swatch cache eviction occurs only between frames, protecting deferred draws.

Blur remains 28 logical px (64 device px cap), saturation 1.35, brightness .92,
with the existing frost curve. Neutral card density is saved opacity ×255×.68;
primary actions retain a legibility floor while remaining translucent. Data/status
colors and explicit player overrides retain meaning; HUD backing remains neutral.

The old per-element lighting/rim recipes in the renderer are historical/diagnostic paths,
superseded for production by this rollout. The diagnostic `BlurTestScreen` retains
its synthetic controls and lighting comparisons. Verification limits are recorded below.

## Palette and persistence

Dark neutrals: canvas `#101116`, panel `#16171D`, raised `#1D1F27`, hover `#242631`,
pressed `#1A1C24`, sunken `#0D0E13`, border `#24262F`, strong border `#343743`,
text `#EDF1FB`, secondary `#A7B0C5`, muted `#76819A`. They are accent-independent.
Action strong/hover/pressed and their shared foreground are contrast-checked after
rounding; soft selection uses stored accent at alpha `0x24`, independently of action.
The identity `ACCENT` remains the user's RGB for HUD/data consumers.

`ThemeResolver` projects these roles into all legacy surface/text/tile/action aliases,
so the static facade cannot keep a second accent-tinted Dark chrome system alive.
Production uses Dark only; legacy LIGHT settings normalize to DARK.
Factory-off uses the unified Red/Dark material while remembering the user's definition.
HUD world-overlay panels remain Dark in both UI modes for fixed status/explicit text
color context. Solid/vanilla HUD backgrounds and explicit ARGB overrides retain meaning.

`ThemeDefinition` persists accent, roundness and backgroundOpacity, with a legacy mode member normalized to DARK. No default
or stored opacity value changes: `ThemeResolver` maps it once to tint density
`round(normalizedOpacity * 255 * .68)`, maximum 173. The renderer's frost curve remains.
Old structured `glassStyle` values are removed in both config and profile migration;
unrelated fields are preserved. Button's internal OFF/NEUTRAL/STAINED compatibility
API is a separate presentation API, not the removed Frosted/Wireframe taxonomy.

## Surfaces and lifecycle

| Screen / element | Current unified treatment |
|---|---|
| `AuroraScreen` | Standalone blurred sidebar/module/inline-settings cards, no enclosing window |
| Every `FeatureDetailScreen` | Container plus blurred Reset/Done/action cards; no repeated feature heading |
| Profile and Waypoint managers (`ManagerListScreen`) | Independently blurred rows and toolbar/actions, translucent faces, no list window or duplicate heading |
| `AuroraTitleScreen` | Declared panorama; five independent neutral blurred cards; no enclosing panel |
| `ColorPickerScreen` | One parent for the editor; Apply primary; represented gradients/alpha/checkerboard remain data |
| `ResourcePackBrowserScreen` | One parent for the browser; embedded sidebar, cards and install actions; soft category selection; opaque detail modal |
| `HudEditorScreen`, `WorldMapScreen` | Shared themed controls; editor/map data remain visible; map prompt is opaque floating material, with no misleading world capture |
| Edit boxes, enum/keybind/key-list pills, segments, toggles, sliders, swatches, icon actions | Shared roles/corners; no child capture; enum popup has independent opaque backing |
| Aurora-styled vanilla world/multiplayer selection | Shared Button painter and themed fields; native interaction/narration/sound remain authoritative |
| HUD backgrounds, keycaps, alerts | Dark neutral chrome without blur; severity, represented shapes, explicit colors and vanilla/solid background modes retained |
| `BlurTestScreen` | Diagnostic data/renderer fixture; production adoption does not alter the synthetic reference images |

`GlassSurface.card` exposes explicit standalone ownership; embedded control APIs still
decline before capture. Each visible card/window uses a pooled output, respecting
the existing 64-slot bound and priority fallback. Translucent faces render after dim.
Opaque floating helpers never sample another backdrop. Screen/resize boundaries
release stale material templates before GUI draws; swatch eviction is also between
frames. Selected halos and resting faces remain cached.

Shared controls, custom pills, browser hover and icon actions use finite 120 ms CSS
ease; their semantic geometry/callbacks, availability, native keyboard/narration and
exactly-once click feedback remain authoritative. Representational keycaps, color
gradients/checkerboards and mechanical knobs remain data/geometry exceptions.

## Verification and limits

The card-refinement production build passed 398 tests in 41 suites, with zero
failures/errors/skips. A 13-host native pass and five-case focused follow-up each
reported zero GL errors. The focused pass verified Dark-only Theme without preview,
rounded white/yellow swatch containment, slider knob ordering, 9% density cards,
Square profiles and resumed detail-search typing (`s` → `sp`, focused after both
inputs). The Modules search likewise resumed (`z` → `zo`) in the broader pass.
Small (854×480) and full-size (1600×1000) windows were exercised; actual screenshot
suppression outcomes were logged. Evidence is under the same task root's
`card-refinement/`. These checks supersede the historical rollout counts below.

The preceding rollout's production `gradlew.bat --no-daemon test build` passed: 395 tests in 40
suites, zero failures, errors or skips. Headless coverage includes both legacy style migrations,
unchanged preferences, global legacy-role projection, action contrast over an RGB
lattice, frame ownership, semantics/topology and retained renderer safety guards.

Native production-host probes exercise title, main, Better Hitreg, Particles,
profiles, waypoints, color picker, HUD editor, populated Modrinth browser, world-map
prompt, Light/Square/45% profiles, Light keybinds and factory-off. The final 13-case
pass, focused eight-case popup/alert/vanilla-button pass and two-case resize/transition
pass each completed with zero GL errors. Every glass host used at most one capture
per frame; HUD editor/map prompt used none. Screenshot review corrected Light manager
row text, the picker Hex-label overlap and outer detail-screen title contrast.
The focused pass recorded actual `suppressed (screenshot in flight)` outcomes for
five glass hosts; normal and suppressed captures retained readable text. The boundary
pass exercised GUI scale 2 → 3 → 2 without GL errors. Empty dev saves redirected world
selection to vanilla Create World, so the world-selection list itself was not exercised.
The 13-host card pass plus focused follow-up exercise the updated ownership.
Native evidence is under `card-refinement/`; the counts and outcomes above describe
the preceding rollout and are retained as historical verification.
Map/HUD editor checks run without a loaded world; terrain capture/gameplay are unchanged
and no new loaded-world validation is claimed. Browser feeds are read-only; no packs
were installed. Temporary QA entrypoints/classes are restored and excluded from the jar.

Historical evidence: `unified-pilot/` and `unified-pilot/refinement/` under the same
visualization task root retain the original controlled pilot and GL state sentinel.
That refinement observed intermittent automated screenshot glyph loss even with glow
disabled. That observation was not reproduced in the focused normal/suppressed
captures; this rollout does not claim a root-cause fix. A filename ending in
`-fallback` alone is not proof that the 400 ms suppression window was active.

External evidence root:
`C:/Users/kalib/.codex/visualizations/2026/10/06/01a10f3d-27c2-7c92-b21a-0965f8be2b9a/unified-rollout/`.
