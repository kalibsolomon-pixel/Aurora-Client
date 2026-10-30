# Spxctra UI simplification — Aurora Client

Date: 2026-10-30 · Branch `spxctra/ui-simplification` (from `codex/release-3.1.0` @ `1a9f0e7`)
Status: **implemented, natively captured and measured; owner visual acceptance pending.**

## 1. Project and renderer

Aurora Client is a client-side Fabric mod (Minecraft 1.21.11, Java 21). Its UI is already
natively rendered: Minecraft's `GuiGraphics` pipeline plus Aurora's own AA primitives,
cached raster layers and a GL/Blaze3D blur material. There is no web runtime. The native-
first mandate is already met, so **no renderer migration was considered or needed**; this
pass changed composition and component treatments only.

## 2. Skills

| Skill | Source / revision | State |
|---|---|---|
| `spxctra-ui` 1.0.0-draft | owner-supplied `SKILL.md` | installed, read, applied (primary authority) |
| `frontend-design` | anthropics/claude-plugins-official `30202c76` (moved from anthropics/claude-code) | installed with Apache-2.0 LICENSE |
| `web-design-guidelines` | vercel-labs/agent-skills `1a2c79d4` + web-interface-guidelines `434b7f91` | installed **locally only** (no license declared; git-excluded) |
| UI/UX Pro Max (optional) | — | not installed (not needed) |

Installed under `.claude/skills/` with `PROVENANCE.md`. Project skills load at session start,
so this session applied their content directly rather than through the Skill tool. The
Spxctra package's `references/REVIEW_CHECKLIST.md` was not supplied; the SKILL.md body was
applied on its own.

## 3. Audit — what was wrong

Baseline: 102 native captures (`build/gui-audit/spx-baseline`, 1920×1080@3, OpenGL, shipped
3.1.0 defaults: green accent, purple background, 33% window opacity).

| Problem | Where | Spxctra principle |
|---|---|---|
| Triple emphasis: accent wash + outline + **glow halo** on every selected/enabled element; glow under primary buttons | main grid, sidebar, layout toggle, profiles, primary buttons | "Never equate polish with effects"; no glow |
| Three treatments in one settings column: opaque purple **outlined** dropdown vs. near-invisible keybind/"Open" chips | every feature detail screen | consistent components; functional clarity |
| **Per-button blur captures** inside windows that already blur: each button sampled whatever was behind it (sky-blue filter chips, brown "Done", green streaks) | managers, world map, waypoint editor, pack browser, picker | "excessive blur"; performance is design |
| Every manager row **outlined** inside an outlined window; red **outlined** "Delete" repeated on every row | waypoints, profiles | repeated borders; competing accents |
| Green primary **Install** repeated on every pack card | pack browser | one primary per screen |
| Forced ALL-CAPS section captions; raw enum identifiers ("SIDE", "LEGACY_1_8", "BRACKETED_TEXT") | detail screens | "no all-caps micro-labels"; copy, not identifiers |
| Redundant blank band between the header and a first section caption | detail screens | dead space |
| Ghost chips behind inactive sidebar items | main screen | decoration without function |
| Crosshair preview: square, outlined box ignoring theme roundness | crosshair detail | geometry consistency |

Kept by design (owner rulings outrank the skill): the blur material on windows, title
cards with their own glass, HUD editor accent actions, Reset/Done edges, search-field edge,
the 18 px band between groups, accent-colored enabled-tile icons.

## 4. Changes (all shared, no per-screen styling)

| Before | After | File(s) |
|---|---|---|
| Selection = wash + edge + halo; primary = fill + halo | wash + one accent edge; primary = fill. No halo raster exists. | `MaterialSurface` |
| Dropdown opaque + outline; keybind / action button at window opacity | One `MaterialSurface.field` face; every plain neutral `Button` wears it (opaque raised tone, no edge, no shadow) | `MaterialSurface`, `Button`, `EnumSetting`, `KeybindSetting`, `KeyListSetting` |
| Windows + every child button capture the backdrop | Windowed screens declare `parentGlassOnly()`; children use cached faces | `ManagerListScreen`, `WaypointEditScreen`, `WorldMapScreen`, `ColorPickerScreen`, `MappingDataScreen`, `ResourcePackBrowserScreen` |
| Manager rows outlined | spacing + tone only | `ManagerListScreen` |
| Delete: red edge + red tint on every row | neutral face + red label; solid red on hover/press | `Button` |
| Install primary on every card | neutral on cards; detail modal keeps the one primary | `ResourcePackBrowserScreen` |
| "PARTICLES / PRESENTATION", "SIDE" | "Particles / Presentation", "Side", "Legacy 1.8" | `SectionHeaderSetting`, `FeatureSetting`, `EnumSetting.humanize` |
| Header band + 18 px band + caption | first visible caption sheds its top band | `SectionHeaderSetting.markLeading` |
| Inactive sidebar items: resting chip | flat at rest; face on hover; focus outline unchanged | `MaterialSurface.selection(…, flatRest)`, `AuroraScreen` |
| Control-hosting opaque panels (map toolbar/status bar, waypoint editor, mapping data) used the raised tone — once per-button blur was gone, raised buttons vanished into them | `MaterialSurface.floatingPanel`: the panel tone, so controls sit one step above it as on every window | `MaterialSurface`, `WorldMapScreen`, `WaypointEditScreen`, `MappingDataScreen` |
| Keyboard focus on a selected tab / enabled tile / selected layout toggle was drawn exactly over the accent selection edge — **invisible** (pre-existing) | focus ring 2 px outside the selection edge | `AuroraScreen` |
| Square outlined preview | rounded inset tone | `CrosshairPreviewSetting` |

No setting, schema, default, keybind, gameplay or persistence change. Rules recorded in
`DESIGN_LANGUAGE.md` ("Spxctra simplification pass") and `AGENTS.md`.

## 5. Evidence

Before/after native-pixel crops (no resampling): `docs/spxctra/before-*.png`,
`docs/spxctra/after-*.png`. Full frames: `build/gui-audit/spx-baseline` vs `spx-final2`
(local, untracked), interaction states in `spx-final3-states`.

| Fixture | Jar | Plan | Result |
|---|---|---|---|
| `spx-baseline` | 3.1.0 HEAD `1a9f0e7` | full (102 captures) | 41/41 probe checks |
| `spx-final2` | this branch minus the focus-ring fix | full (102 captures) | 41/41 checks, 0 glass-ordering reports, 0 renderer errors |
| `spx-final3-states` | this branch (committed) | `spx` hover/focus/scroll states | focus visible on selected tab and field; Delete solid red on hover |
| `spx-glass-base` / `spx-glass-final` | baseline / final | `glass` (≈3 s dwell per screen) | table below |

`spx-final2` differs from the committed jar only in focus-ring geometry, which the full
plan never exercises (no keyboard focus), so its captures stand for the final build.

Interaction states (`docs/spxctra/after-state-*.png`, `*-focus-selected-tab.png`):
inactive sidebar hover face, keyboard focus ring on a field control and on the selected
tab (before: nothing visible), Delete committing to solid red on hover.

## 6. Performance

Glass capture work per frame, frame-weighted from `[GlassStats]` (`-Daurora.glassStats=true`),
same disposable world, OpenGL, RX 9070 XT, 1920×1080@3:

| Screen | Before: captures / glass ms per frame | After |
|---|---|---|
| Main grid | 1.0 / 1.48 | 1.0 / 1.45 |
| Feature detail (World Map) | 1.0 / 1.79 | 1.0 / 1.78 |
| Waypoints (40 rows) | **19.9 / 7.46** (max 30) | **1.0 / 2.64** |
| Profiles | **14.0 / 5.34** | **1.0 / 1.97** |
| Pack browser | 9.0 / 6.32 | 2.0 / 5.06 |
| Color picker | 3.0 / 2.28 | 1.0 / 1.73 |
| Waypoint editor | 6.0 / 1.25 | 0 (no capture) |
| World map | 6.0 / 1.40 | 0 |
| Mapping data | 3.0 / 1.11 | 0 |

These are the renderer's own timings of the glass pipeline, single runs per side. Whole
screen frame times are **not** a valid comparison here: every fixture run (this pass and
the 2026-10-19 audit) is capped and lands bimodally on ≈17 ms or ≈32 ms P50 depending on
the fixture's frame cap, independent of the code under test (baseline 17.5, candidate 2
16.8, final 32.2, final2 17.5). **No FPS improvement is claimed.** Startup time, idle
CPU/memory and input latency were not measured; the change adds no per-frame work (it
removes blur passes and one halo raster per selected element).

## 7. Validation

- `./gradlew build`: **619 tests, 0 failures** (1 pre-existing skip). New
  `SpxctraSimplificationContractTest` (9 tests: halos, field face, rows/destructive/Install,
  parent-only capture hosts, panel tone, focus rings, captions, enum labels, leading
  caption geometry incl. hidden rows). `AuroraScreenNavigationTest` site string updated for
  the new tile focus code (its five-hairline contract is unchanged).
- Native: 4 full captures, 3 glass runs, 3 state runs. Every probe check passed; zero
  `[GlassSurface]` ordering reports.
- Accessibility: focus ring now visible on selected peers (was invisible); destructive
  meaning carried by the word, not color alone; all labels unchanged; no motion added;
  hover/press timings unchanged.

## 8. Limitations and open items

- **Owner visual acceptance is pending.** Shipped defaults (green/purple, 33% opacity) were
  the only theme captured; other accents, Square/Slightly-round corners and other
  opacities were not re-captured.
- OpenGL at 1920×1080@3 only. Sodium, VulkanMod, other GUI scales, Linux not re-run.
- Not driven by automation: real mouse drags, keybind capture, pack install flow, enum
  popup open state.
- One candidate run showed the vanilla Options screen at the waypoint-editor step; the
  cause was not determined (the final run captured the editor correctly; Minecraft pauses
  for that screen, which is normal).
- Deviations from Spxctra kept on purpose (owner rulings): window blur, title/HUD-editor
  standalone glass, purple Background-Color tint on edges, 18 px section band, accent icons
  on enabled tiles. The Spxctra `REVIEW_CHECKLIST.md` was not available.
- Native-renderer assessment: already native (Minecraft GuiGraphics/Blaze3D); no migration.
- Nothing pushed, merged, tagged or released. Pre-existing untracked files
  (`CPERF1B_PARK_BENCHMARK.md`, `CPERF2_PARK_WAKE_INVESTIGATION.md`) untouched.

Reproduce: `./gradlew remapJar` and `./gradlew -I tools/gui/runtime.init.gradle guiProbeRemapJar`, then
`python tools/gui/run.py <name> --plan full|spx|glass --jar <jar> [--jvm-arg=-Daurora.glassStats=true]`,
`python tools/gui/glass_metrics.py <base> <final>`, `python tools/gui/spx_evidence.py <base> <final>`.


## 9. Compact pass — uniform windows, smaller UI, collapsible sections (2026-10-30)

Owner request: one window size for every Aurora screen, smaller text and controls, tighter
spacing, less scrolling, expandable categories. Follow-up ruling mid-pass: keep the feature
cards at their original 80×80 size and don't oversize the window (a first 520×320 draft
with 64-px cards was rejected and replaced).

| Before | After |
|---|---|
| Each screen picked its own size (main 344×224, details content-fit ≤344×300, managers up to 476×420, editors 344×160, pack browser full-screen) | One frame, `UiDensity.window` = 420×272 (shrinks with a 12 px margin), for main, all details, both managers, waypoint editor, color picker, mapping data and the pack browser |
| 9 px UI text | 7.5 px `<font>_ui` variants of the same bundled face inside Aurora windows (crisp; measured with the same style). "Pack Tweaks" label 155 → 130 device px at GUI 3 |
| 24 px rows (sliders 28, wrapped 36), 4–6 px gaps, 18 px controls, 18 px section band, 36 px header | 18 px rows (sliders 20, wrapped 28), 2 px gaps, 14 px controls, 8 px section band, 24 px header with 16 px buttons |
| Long screens scroll through every option | Sections are collapsible categories; lists with ≥3 sections that would scroll open with only the first expanded; runs of collapsed captions pack tightly; choices remembered for the session |
| Waypoint editor hid half its fields behind "Show details" | Location and Details both visible; Save/Cancel on the bottom edge |
| Feature cards 80×80 | unchanged (owner ruling); grid shows 4 columns |

Visible without scrolling at 1920×1080@3: Crosshair 11 settings (was 7); Better Hitreg all 8
categories plus its first section; Display Settings all 6 categories (was a 19-row scroll);
Waypoints 6 rows; the Settings tab rows at a 20 px pitch (was 30).

Evidence: `docs/spxctra/compact/before-*.png` (3.1.0 baseline) and `after-*.png`
(`build/gui-audit/compact3`, 102 captures, 41/41 probe checks, 0 glass-ordering reports) plus
`after-sections-*.png` / `after-display-settings.png` from `compact3-states`.

Tests: 622 passing. Updated tests that pinned the old geometry (row heights, control sizes,
frame size, knob radius, dropdown/key-list click coordinates) to read `UiDensity`; added
collapsible-section behaviour tests; `SectionHeaderSetting` moved from the passive list to
the button-action family in the conformance manifest (it is now a keyboard-operable control).

Limits: captured at 1920×1080@3 on OpenGL only; other GUI scales/renderers not re-run. Users
on the vanilla font (SansSerif or text renderer Off) get the compact spacing but vanilla-size
text. Collapsed state is session-only (not saved to the config). The world map, HUD editor
and title screen are not windows and were left as they are.
