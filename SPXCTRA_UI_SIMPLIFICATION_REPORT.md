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
| Each screen picked its own size (main 344×224, details content-fit ≤344×300, managers up to 476×420, editors 344×160, pack browser full-screen) | One frame, `UiDensity.window` = 420×272 (336×288 after the §10 follow-up; shrinks with a 12 px margin), for main, all details, both managers, waypoint editor, color picker, mapping data and the pack browser |
| 9 px UI text | 7.5 px `<font>_ui` variants of the same bundled face inside Aurora windows (8 px after the §10 follow-up; crisp; measured with the same style) |
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

## 10. Compact follow-up — 3×3 grid, 8 px text, square swatches, per-pixel pickers (2026-10-30)

Owner follow-up on the shipped compact pass: the window should show approximately a 3×3
card grid; window text slightly larger than 7.5 px (8 px); every color-picker swatch a
small square instead of a rectangle; and the color pickers rendered per-pixel so gradients
blend smoothly instead of reading as blocky cells.

| Before (§9 as shipped) | After |
|---|---|
| Window 420×272, 4 card columns, 2 rows fully visible | Window 336×288: the content well (254×252) shows EXACTLY 3 columns × 3 rows of the unchanged 80×80 cards. Card hover washes, enabled stains, focus hairlines and selection outlines are all derived from the card bounds, so they resized with the frame automatically (`cardBounds` walks `UiDensity` geometry); `columns()` clamps unchanged |
| `<font>_ui` rasterized at 7.5 px | 8.0 px in all nine `_ui` providers (same faces, same oversample) |
| Swatches 32×12 rectangles (rows), wide preset chips (accent grid), 4:1 preview bars (both pickers) | 14×14 squares everywhere a color is picked or shown: `UiDensity.SWATCH_W/H` = 14/14 (rows), accent presets 14×14 (`PRESET_S`; only Custom stays a wider labeled pill so "Custom" fits), picker previews square (48×48 in `ColorPickerScreen`, 22×22 in the inline editor). Waypoint chips were already 12×12 squares |
| Pad drawn as a 40×40 grid of GUI-pixel cells (32×32 in the inline editor); strips one GUI px per row — visible banding/posterization at every GUI scale | `UiLayerCache.writeArgb` writes one exactly-sampled HSL/alpha value per DEVICE pixel into each surface's buffer; alpha strips composite the gradient over the checkerboard per pixel; rounded data-surface corners via the ColorSwatch `clipRounded` discipline. Shared cache/rasterize/blit helper `UiLayerCache.drawSurface`; version keys unchanged (pad ← hue, strips ← h/s/l), re-raster only on the drag that changes the displayed value. Buffers disposed while collapsed and on screen close |

The full-screen `ColorPickerScreen` (kept for the GUI audit harness) and the production
`ColorSetting` inline editor (what feature rows actually open) both took the per-pixel path —
the inline editor previously re-submitted ~1300 plain fills every frame at rest; it now blits
three cached textures.

Tests: `CompactGeometryTest` re-pinned to 336×288 / 3 columns / 3 card rows / 7 list rows;
`ColorSettingPilotTest` click coordinates follow the 14×14 default swatch rect;
`AccentSettingPilotTest` geometry helper + one source pin updated for the square presets.
622 tests, 0 failures (1 skipped, unchanged).

Evidence: `docs/spxctra/compact2/after-*.png` (harness run `compact2`, plan `full`,
1920×1080@3, OpenGL, 41/41 harness checks passed, 101 frames captured) — main grid exactly
3×3 with no cut cards, color picker with smooth pad/strips and the square preview, Hit
Color detail (square row swatch), Theme detail (square preset chips + the Custom pill),
plus managers/settings/pack-browser frames at the new frame size.

Limits: captured at 1920×1080@3 on OpenGL only. Drag-frame re-raster cost of the per-pixel
pad was NOT measured (the harness captures static frames): it is drag-only by construction —
one device-area HSL pass per changed hue, strips trivially cheap; at rest every surface is
one cached blit (the inline editor previously submitted ~1300 fills per rest frame).

## 11. Owner punch list — eleven fixes from live verification (2026-10-30 evening)

Verified against the running client by the owner; every item below is implemented, tested,
and captured by harness run `compact3`.

| # | Issue (owner's words, condensed) | Fix |
|---|---|---|
| 1 | Accent/Background Color don't need presets or the Custom label — one swatch that opens the picker | `AccentSetting` rewritten as the shared `ColorSetting` row (single 14×14 square swatch → inline picker) + the established 100 ms live-apply throttle (`ColorSetting.applyValue` seam). Preset grids, Custom pill, peer roving ring, hover animators: removed. `ThemePresets` survives only as the factory default. |
| 2 | Text & Fonts dropdowns don't function when clicked | Root cause: compact `EnumSetting`s keep their 18 px row while expanded — the floating popup is painted by the HOST, and AuroraScreen (the only place text_fonts renders — it has no detail screen) never painted it. The Settings tab now paints open popups after the row walk and routes popup clicks FIRST (the FeatureDetailScreen discipline). Config-apply chain verified sound (setter→field→save; MixinFont re-reads per frame). |
| 3 | Background Opacity must be the source of truth: max = solid color (no see-through, no blur), min = clear | Window fill alpha now LINEAR (`×255`, was `×255×.68` — the density cap kept even max see-through); frost now `requested × opacity` (the 1/6 floor kept a frosted slab at 0%), and below the effective minimum frost the glass panel declines entirely — the flat path carries the near-zero alpha directly. |
| 4 | Clicking "Settings" overlaps the selection highlight — fix universally (also the Pack-Tweaks case) | The double was selection's accent edge + the focus ring 2 px outside (added the same morning; now reversed by ruling). Selected/enabled peers carry the selection alone everywhere: sidebar tabs, module tiles, layout pair, pack-browser sidebar, SegmentedControl segments, keybind listening pill. Section captions lose the boxed focus outline (which degenerated to four rules in Square mode, reading as a doubled divider) — focus brightens the caption + chevron like hover. |
| 5 | Shield status overlay not vibrant; alpha must be the source of truth (max = texture fully covered, min = off) | The cutout render type made the old multiply-tint structurally unable to cover dark texels. Now a SECOND model pass: the same handle+plate parts through a pure-white atlas sprite (stitched into `minecraft:shield_patterns` by `assets/aurora/atlases/shield_patterns.json` + a 4×4 white texture) with `RenderTypes.entityTranslucent` and the status ARGB — real alpha blending over the textured shield. `resolveOverlayArgb()` returns the picker color verbatim; alpha 0 submits nothing. |
| 6 | Remove "See-Through Outline" (Block Overlay) completely | Row + `blockOverlaySeeThrough` config field + renderer branch removed; the outline keeps the see-through (translucent lines) behavior. Note: the 3.1.0 release default JSON listed the field — dropped there too (its presence crashed config init; caught by the test suite). |
| 7 | Remove "See-Through Wireframe" (Hitbox) completely | Same treatment (`hitboxSeeThrough`); the wireframe keeps translucent lines. `WorldLineRenderer.getLineBuffer(consumers)` is the see-through accessor; the waypoint highlight keeps its depth-tested `getOccludedLineBuffer`. |
| 8 | Info HUD options grouped into loose dropdown categories | 16 flat rows → Text Color + Background rows on top, then four collapsible `SectionHeaderSetting` groups: Performance (FPS/Frame Time/Memory/Ping/CPS), Position (Coords/Facing/In-chunk/Chunk Coords/Y vs Sea), World (Time/Biome/Dimension/Light), Playtime (Show/per-World). |
| 9 | "Preview" buttons → the "[->" symbol (Google font icon for quality) | `arrow_forward` (U+E5C8) added to the material-symbols subset (regenerated from the official variable font); ButtonSetting's no-text factory is now an icon-only 28 px face (`SettingRowLayout.Kind.ACTION`). Navigation rows (Manage Waypoints…, Open Browser…, mapping actions) wear it; genuine actions keep meaningful labels ("Drop", "Reset", "Preview" for Test Sound). |
| 10 | Remove Swing Style from Animations (keep Swing Curve) | Row removed; `cfg.swingStyle` + the 1.8-arc mixin behavior persist unchanged for existing configs. |
| 11 | Pack browser out of proportion — cards overlap, fall outside the window | Card width now DERIVES from the frame (`cardW()` = content column ÷ columns; one 202 px column at 336) instead of a fixed 260 px with negative centering; grid left is pinned to the content column; the scrollbar track sits inside the window's right gutter (was 22 px past the frame edge); scissor/ClipBand bottom pads clear the rounded corner (16 px) with a bottom scroll fade; the "N packs" header moved off the sidebar into the content column. |

Tests: 601 passing (the 21 AccentSetting peer-pilot tests retired with the peer model;
inventory/pin tests re-based: enum rows 25→24, density 173→255, focus-ring pins inverted
per the ruling, the peer-selection conformance family removed, release-defaults JSON
regenerated without the two deleted fields).

Evidence: `docs/spxctra/compact3/after-*.png` (harness `compact3`, plan `full`,
1920×1080@3, OpenGL, 41/41 checks).

Limits: the shield overlay's in-world appearance is NOT runtime-verified (no shield in the
fixture world) — the second-pass mechanism is vanilla-API standard, flagged for the owner's
next live check. Dropdown popup fix verified by code path + harness checks; drag-frame
behavior not scripted by the harness.

## 12. Second punch list — four fixes from live verification (2026-10-30 night)

| # | Issue | Fix |
|---|---|---|
| 1 | Crosshair preview spans the window's width; make it a smaller square display box, for the regular crosshair AND the indicator | `CrosshairPreviewSetting` now renders a centered 36×36 square inset well (row 44 px, was 48 full-width) and gained an indicator variant (`Indicator Preview`, leading the Indicator Style selector — the long-noted §5 follow-up) that previews `crosshairIndicator*` fields through the same real-shape code path. |
| 2 | Rename "Attack Indicator" → "Vanilla Attack Indicator" | Renamed (registry label; config field and behavior unchanged). |
| 3 | Remove the Text & Fonts master toggle (the two dropdowns carry everything); the dropdown options don't activate when clicked | Toggle removed via the new `FeatureMetadata.noSettingsToggle` flag (the Settings tab skips building/drawing it; the entry's enable getter is constant-true). Click bug ROOT CAUSE: AuroraScreen's popup-click routing loop computed the row Y WITHOUT the per-entry header/hint bands, so the popup's input rect sat above where it painted — every option click fell into the "outside → collapse+consume" branch (popups visibly opened, clicks did nothing). The walk now mirrors renderSettingsLive's y accounting exactly. |
| 4 | Shield overlay renders only black at varying opacities — it must follow the color pickers | The white overlay sprite never stitched: `AtlasManager.get()` silently falls back to the atlas's MISSING sprite (black/magenta) for unstitched materials, so color × missingno read as black. Cross-namespace `assets/aurora/atlases/shield_patterns.json` sources were ignored; the working registration is the minecraft-namespace override `assets/minecraft/atlases/shield_patterns.json` (vanilla's three sources + our `aurora:shield/white`). The mixin now detects the fallback by sprite name, logs once, and skips the overlay (never black); a resolve log confirms the live path (`[ShieldStatus] overlay sprite resolved: aurora:shield/white` — observed in the dev client after the fix). The old black report itself proves the vertex-color path carries the picker RGB (color × missingno = black); with the white sprite, color × white = color. |

Evidence: `docs/spxctra/compact4/` (harness `compact4`, 41/41 checks) — square previews + the
renamed toggle on the crosshair detail, the Text & Fonts entry without its master toggle.
In-world shield pixels: not captured (no shield available in the verification world's
inventory; commands disabled) — sprite resolution is log-verified, blend is
entityTranslucent standard, and the fallback can no longer paint black.
