# Unified Aurora theme — approved production rollout, 2026-10-06

The user accepted the refined pilot ("that looks good") and explicitly requested
adoption across every UI screen/element plus removal of the Frosted/Wireframe cards.
This supersedes the former two-host boundary and review stop. The filename remains
stable for existing project links; this document now records production state.

**UNIFIED THEME — production rollout (2026-10-06):** the user accepted the
Launcher-derived pilot and requested adoption across the mod, plus removal of the
Frosted/Wireframe option cards. Every production `ThemedScreen` now uses the approved
material. `UnifiedPalette` roles also project through legacy tokens and `AuroraTheme`,
so custom rows, cards and remaining facade readers share the neutral hierarchy.
`theme.GlassStyle` and its persisted field/selector/render guard are retired;
`ThemeMigrator` removes only the old structured `glassStyle` member from configs
and profiles. Accent, mode, roundness, density, presets and preview remain.

Parents own at most one backdrop capture per frame; the cached neutral face and
boundary draw above dim on success or decline. Embedded controls never capture.
Floating tooltips, enum popups and browser/map modals have opaque cached backing.
Soft selection uses a wash and cached halo, with no left indicator bars; primary
actions use readable strong fills and a slightly stronger halo. Shared controls,
custom pills, browser categories/cards and icon actions use the 120 ms CSS easing.
Blur requests 28 logical px (64 device px cap), saturation 1.35 and brightness .92.
`WINDOW_FILL` alpha is the sole density application point: saved opacity ×255×.68.
Saved preference values are not rewritten. Fixed HUD status colors, represented
item/color/map data and explicit player color overrides remain data. HUD backing
uses the same Dark neutral family in both UI modes, without live blur.

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
Light retains the existing Client companion; no Launcher Light reference is invented.
Factory-off uses the unified Red/Dark material while remembering the user's definition.
HUD world-overlay panels remain Dark in both UI modes for fixed status/explicit text
color context. Solid/vanilla HUD backgrounds and explicit ARGB overrides retain meaning.

`ThemeDefinition` persists accent, mode, roundness and backgroundOpacity. No default
or stored opacity value changes: `ThemeResolver` maps it once to tint density
`round(normalizedOpacity * 255 * .68)`, maximum 173. The renderer's frost curve remains.
Old structured `glassStyle` values are removed in both config and profile migration;
unrelated fields are preserved. Button's internal OFF/NEUTRAL/STAINED compatibility
API is a separate presentation API, not the removed Frosted/Wireframe taxonomy.

## Surfaces and lifecycle

| Screen / element | Current unified treatment |
|---|---|
| `AuroraScreen`, every `FeatureDetailScreen` | One parent capture, cached neutral face/boundary, embedded controls, soft selection, strong primary actions |
| Profile and Waypoint managers (`ManagerListScreen`) | One parent list panel; opaque raised rows; active profile wash/halo; toolbar and row actions share Button |
| `AuroraTitleScreen` | Declared vanilla panorama; one parent for the five-button stack; Settings is primary |
| `ColorPickerScreen` | One parent for the editor; Apply primary; represented gradients/alpha/checkerboard remain data |
| `ResourcePackBrowserScreen` | One parent for the browser; embedded sidebar, cards and install actions; soft category selection; opaque detail modal |
| `HudEditorScreen`, `WorldMapScreen` | Shared themed controls; editor/map data remain visible; map prompt is opaque floating material, with no misleading world capture |
| Edit boxes, enum/keybind/key-list pills, segments, toggles, sliders, swatches, preview, icon actions | Shared roles/corners; no child capture; enum popup has independent opaque backing |
| Aurora-styled vanilla world/multiplayer selection | Shared Button painter and themed fields; native interaction/narration/sound remain authoritative |
| HUD backgrounds, keycaps, alerts | Dark neutral chrome without blur; severity, represented shapes, explicit colors and vanilla/solid background modes retained |
| `BlurTestScreen` | Diagnostic data/renderer fixture; production adoption does not alter the synthetic reference images |

`GlassSurface` resets a parent claim at the frame boundary. Only WINDOW-priority
parents may claim it; repeated rows and controls decline before capture. Parent
capture stays pre-dim, neutral cached face/boundary stays post-dim on every outcome.
Above-dim popup/modal helpers paint cached opaque floating material without another
capture. The world-map prompt uses floating material directly because deferred map
tiles are GUI data, not the live world texture. HUD stays never-glass.

Material templates are keyed by geometry/role and invalidated by theme generation.
The frame boundary releases templates when the screen, GUI size or scale changes,
including vanilla hosts. Each custom host also disposes them at removal. Resting
selection halos/primary elevation are cached; transient hover uses live interpolation.
Shared controls, custom pills, browser hover and icon actions use finite 120 ms CSS
ease; their semantic geometry/callbacks, availability, native keyboard/narration and
exactly-once click feedback remain authoritative. Representational keycaps, color
gradients/checkerboards and mechanical knobs remain data/geometry exceptions.

## Verification and limits

The final production `gradlew.bat --no-daemon test build` passed: 395 tests in 40
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
