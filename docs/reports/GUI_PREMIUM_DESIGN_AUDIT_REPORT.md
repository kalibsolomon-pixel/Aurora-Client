# GUI Premium Design Audit — Report

Date: 2026-10-19 · Branch: `codex/gui-premium-design` (from `codex/unified-mapping` @ `f74ad7c`)
Status: **pass 1 complete and natively validated; not every screen received bespoke work** (see §M).

## A. Executive summary

A native-screenshot audit of every Aurora screen (title, main, 38 feature detail screens,
managers, editors, map, HUD editor, pack browser, dialogs, in-game HUD) found that the
shared design system was in good shape but several *screens* lagged it, plus two real
HUD bugs. Pass 1 fixed the structural problems at their shared cause where one existed:

| Area | Before | After |
|---|---|---|
| Waypoint / Profile managers | Toolbar floated in the viewport corners, over HUD modules, unaligned with the list | One centered window: primary action leads its header, Done trails; search/filters share the list's column |
| Destructive row actions | Solid pink "Delete" block on every row | Tinted chip with red edge + label; commits to solid red on hover/press |
| Feature detail screens | Fixed 300px frame — Zoom's 3 rows sat in a mostly empty well; title 1px off the buttons | Frame fits content (top fixed at open, bottom grows, popup room reserved); title aligned; settings-less screens say so |
| Scroll edges | Content hard-cut at the bottom clip (half tiles) | Bottom fade companion to the existing top fade (main, detail, managers) |
| Color picker | Apply/Cancel straddled the panel's bottom edge; title floated outside | Centered dialog, title in header, labels centered on strips, actions inside (primary rightmost) |
| World map | Toolbar + separate coordinate row + Done stranded in a full-width bottom bar; raw `overworld` id | One toolbar (wraps to two rows when narrow), slim status bar with coords + hint, "Overworld"/"The Nether" |
| HUD editor | Saturated green outlines + glow, every label/badge/handle shown at once (overlapping), button in the screen center, hint across the top | Quiet neutral outlines; only the hovered/dragged module shows label, handles and close badge; toolbar at top; hint as a bottom pill |
| **HUD bug** | "Reset Positions" put CPS, Ping, Reach, Totem Pops, Info… all at TOP_LEFT (4,4) | Each module returns to its own captured default |
| **HUD bug** | Toggle Sprint/Sneak rendered on top of the Info readout by default; Ping and Reach shared y=80 | Non-overlapping defaults (Toggle → bottom-right corner; Reach/Totem own rows) |
| Empty states | Left-pinned single lines, or a blank section (per-effect alerts) | Centered, contextual ("no match for filters" vs "none yet"), explicit hints |
| Tooltips | Every label paid a 1500 ms dwell | First tooltip dwells; adjacent tooltips open instantly (warm hand-off) |
| Copy | `Marker: DIAMOND`, `Visible`, `Name`/`All dims` chips, duplicate "Categories" | `Marker: Diamond`, `Shown on maps`, `Sort: Name`/`All dimensions`, sidebar "Browse" |

Validated natively on OpenGL, Sodium 0.8.14 and VulkanMod 0.6.8 at 1280×720@2,
1920×1080@3/4, 2560×1440@4 and a real 3840×2160@6 framebuffer; every probe check passed,
zero glass ordering reports, zero renderer errors. 576/576 tests pass.

## B. Premium Design skill application

Loaded via the Skill tool (`anthropic-skills:premium-design`) before any audit work. Its
material is web/CSS-oriented; the applicable principles were translated to Aurora's
Java/GuiGraphics renderer:

- **"Unseen details compound" / edge cases handled invisibly** → empty states, content-fit
  frames, bottom scroll fade, popup space reservation, label clamping in the HUD editor.
- **Tooltips: delay the first, instant afterwards** → warm hand-off in
  `FeatureSetting.trackLabelHover` (an existing test pinned the opposite; replaced with a
  test of the new contract).
- **Should this animate at all / frequency rule** → audited every duration: hover 120–140 ms
  ease, press 90 ms ease-out + 180 ms recovery, tooltip 200 ms, scroll 90 ms glide — all
  inside the skill's ranges, so left unchanged. Dropdown entrance animation was considered
  and **deferred**: its option highlights are cached rasters with no alpha path, and a
  partial fade would show mismatched layers (worse than none).
- **Buttons must feel responsive to press** → press feedback already exists as a pressed
  fill; the skill's scale(0.97) is *not* applied in the unified path because the blur mask,
  focus ring and label share one stationary geometry (documented trade-off in `Button`).
- **Review format** → the Before/After/Why table above and in §E.
- **Restraint / cohesion** → HUD editor emphasis only where the pointer is; destructive
  emphasis only on intent.

Owner rulings outranked general skill advice where they conflicted: neutral action cards
stay edgeless (`UNIFIED_THEME_PILOT.md`), destructive keeps its red cue, no duplicate
manager headings, owner-specified title/main geometry (`OWNER_MENU_GEOMETRY.md`,
`OWNER_UI_CORRECTIONS.md`), one UI type size (`DESIGN_LANGUAGE.md` §4).

## C. Screen inventory (source-derived) and final status

| Screen / surface | Entry point | Status |
|---|---|---|
| `AuroraTitleScreen` | game start | Audited at 4 sizes; owner geometry kept — unchanged |
| `AuroraScreen` (grid, list, settings tab, search) | title / keybind | Bottom fade + centered empty-search state added; owner geometry kept |
| `FeatureDetailScreen` × 38 (all registry ids, top + scrolled end captured) | grid right-click / settings header | Content-fit frame, title alignment, fades, empty state on all 38 |
| `WaypointManagerScreen` (empty, 40 records, hover) | detail row / world map | Restructured |
| `ProfileManagerScreen` | sidebar | Restructured |
| `WaypointEditScreen` (new, existing) | manager / map | Copy + error color |
| `ColorPickerScreen` | color rows | Restructured |
| `WorldMapScreen` (wide + narrow) | M | Restructured |
| `MappingDataScreen` | World Map settings | Footer + title alignment |
| `HudEditorScreen` | RShift | Restructured |
| `ResourcePackBrowserScreen` (loading + loaded) | grid tile | Duplicate heading fixed; otherwise audited, unchanged |
| Dialog popups (enum), tooltips, toasts | in screens | Audited; tooltip timing changed |
| In-game HUD modules (12) | gameplay | Default layout + reset bug fixed |
| Vanilla Select World / Multiplayer | title | Captured in-world only (unthemed there) — see §M |
| `BlurTestScreen` | debug keybind | Diagnostic, out of scope |

## D. Design-system changes

- `Button` (unified path): destructive rests as tinted chip, solid on intent; neutral stays edgeless.
- `ScrollFade.drawBottom` — remaining-distance-driven companion to `drawTop`.
- `ManagerListScreen` window geometry (`windowTop/Bottom/X/Width`, `HEADER_H`, `toolbarY`) — one source for frame, header, list clip.
- `EnumSetting.popupHeight()` so hosts can reserve popup space.
- `HudModule.captureDefaultLayout()` (called by `HudModuleManager.register`) — reset target.
- Tokens, palette, fonts, radii, glass recipe: **unchanged** (the audit found the token set coherent; the issues were composition).

## E. Screen-by-screen defects → changes

| Before | After | Why |
|---|---|---|
| Manager toolbar at viewport corners (16,16) | Inside window header, list-column aligned | Toolbar collided with HUD modules and belonged to no surface |
| Waypoint empty text at list top-left | Centered, filter-aware message | Empty well read as broken |
| Detail frame always 300px | Content-fit, top fixed, popup-aware | Big dead wells on short screens; no jumping chrome |
| Title y+12 vs button label y+13 | y+13 | Baseline alignment with Reset/Done |
| Picker Apply at `height-32` | Inside panel, right-aligned | Button straddled the panel edge |
| Map "Go" 30px ("…") | 36px | Label truncated to ellipsis |
| HUD hint auto-wrapped mid-phrase | Two phrase groups | "X / badge: disable" split |
| HUD outlines green + 3-ring glow, always | Neutral 40% at rest, full on hover/drag | Noise on every module |
| `resetLayoutToDefaults` hard-coded (4,4) | Captured constructor defaults | Reset stacked modules |
| Per-effect list blank when empty | "No effects added yet — search above…" | Fresh-install default looked broken |

## F. Navigation, UX, accessibility

All functions remain reachable; no setting semantics changed. Keyboard: Escape/back,
semantic controls and focus availability sweeps untouched (HUD editor close badge stays
keyboard-reachable; it paints whenever focused). World-map click/scroll guards follow the
new chrome bounds. Validation errors in the waypoint editor now use the error color rather
than silently replacing the title. Transactional editing unchanged (Cancel never mutates).

## G. Rendering

No renderer, shader or glass-recipe change; all new chrome uses existing AA primitives,
`MaterialSurface` caches and tokens. Native crops at 3840×2160@6 show crisp text and edges
(no resampling). Detail chrome cache now keys on window height too.

## H. Compatibility

| Run | Backend | Exit | Checks | Glass reports | Renderer errors |
|---|---|---|---|---|---|
| cand2-full (1080p@3, full inventory) | OpenGL | 0 | all pass | 0 | 0 |
| cand2-scale-gl (720p@2 → 4K@6) | OpenGL | 0 | all pass | 0 | 0 |
| cand2-scale-sodium | Sodium 0.8.14 | 0 | all pass | 0 | 0 |
| cand2-scale-vulkan | VulkanMod 0.6.8 (Vulkan) | 0 | all pass | 0 | 0 |

Windows 11 / RX 9070 XT only. **Linux not tested.**

## I. Performance

Matched fixture runs (same world, same actions). Screen-open frame times are capped by the
fixture's limits, so they show *no regression* but cannot prove CPU parity:

| Run | P50 ms | P95 ms | frames |
|---|---|---|---|
| baseline-full / cand2-full | 32.12 / 32.01 | 34.96 / 34.95 | 7236 / 7237 |
| base-scale-gl / cand2-scale-gl | 19.42 / 21.09 | 37.86 / 39.20 | 5088 / 5077 |

The scale pair (single runs, incl. 4K) differs by ~1.7 ms P50 — within the run-to-run
spread seen elsewhere in this repo, but unproven either way. New per-frame work is O(modules)
(HUD editor hover hit-test, 12 modules) or one gradient fill; no new allocation loops or
uncached shape volume. No performance improvement is claimed.

## J. Native evidence

`docs/gui-premium/before-*.png` / `after-*.png` — native-pixel crops (never resampled) from
`build/gui-audit/baseline-full` vs `cand2-full` at 1920×1080@3, plus
`after-vulkan-4k-world-map.png` and `after-narrow-480x270-world-map.png`. Full frames for
every screen remain in `build/gui-audit/*/game/screenshots` (local, untracked).

Reproduce: `./gradlew -I tools/gui/runtime.init.gradle guiProbeRemapJar` then
`python tools/gui/run.py <name> --plan full|scale --renderer opengl|sodium|vulkan --sizes WxH@S,...`
(requires the existing `tools/vulkan` fixture inputs).

## K. Functional testing

576/576 unit/contract tests pass (one tooltip contract intentionally replaced, one added).
The probe drove real screens: hover states, wheel scrolling, typed search (match and
no-match), 40-waypoint lists, profile creation, waypoint editor new/existing, resize and GUI
scale changes. Not covered by automation: drag-resizing HUD modules, keybind capture, full
click-through of every control on every detail screen.

Found but out of scope: `aurora.json.tmp → aurora.json` atomic move intermittently throws
`AccessDeniedException` on Windows during rapid saves — present in the baseline too.

## L. Repository state

- Start: `codex/unified-mapping` @ `f74ad7c`, with pre-existing uncommitted Vulkan work
  (AGENTS.md, ARCHITECTURE.md, PixelCanvasSetting, BlurPanelRenderer, BlurTestScreen,
  ReflexScheduler + untracked Vulkan files). Backed up as a diff; **never staged or modified**.
- Branch: `codex/gui-premium-design`. Commits: `176adb8`, `31a1c9d`, plus this report commit.
- Nothing pushed, merged or published. No `git clean`; no pre-existing file deleted.

## M. Remaining limitations / deferred

- Pass 1 fixed shared causes and the screens with structural defects; the title, main screen
  and pack browser were audited but kept largely as-is under owner rulings.
- Dropdown entrance animation deferred (cached option rasters lack an alpha path).
- Vanilla selection screens were only captured in-world (where Aurora does not theme them).
- Linux, other GPUs/drivers, and uncapped CPU timing not validated.
- Existing users keep their saved HUD layouts; the new defaults apply to fresh installs and
  "Reset Positions".
