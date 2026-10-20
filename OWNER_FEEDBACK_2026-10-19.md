# Owner feedback pass — 2026-10-19

Six owner requests from in-game screenshots (1.21.11 multiplayer). Premium Design skill
applied for every UI/motion decision; owner rulings (neutral edgeless cards unless an
accent outline is requested) kept.

| Before | After | Why |
|---|---|---|
| HUD editor "Aurora Settings" in the top-left toolbar, edgeless | Centered over the crosshair with the enabled-feature-card accent outline (`Button.Edge.ACCENT` → `ACCENT_MARKER`) | Owner request; the editor's primary exit reads as the selected action |
| "Reset Positions" 120×20, edgeless | Fits its label (+22 px) × 16, grey window edge (`Button.Edge.NEUTRAL` → `BORDER`) | Owner request; secondary corner action takes less room |
| Minimap terrain repainted ≤20 Hz at whole-texel steps from the 20 TPS position | World-aligned `MinimapGrid` (overlap reused, only exposed strips sampled) resampled per frame from the partial-tick pose (`UiLayerCache.writeAffineAbgr`); entities interpolated too | Terrain, markers and rotation step at tick/sample cadence → choppy |
| Flat key cells, accent underline, exponential lerp, "Left Shi" legends | 2 px keycap base, cap travels down on press (pose translate), tint/edge crossfade; interruptible ease-out 70 ms press / 140 ms release; "Shift"/"Ctrl" legends | Owner request; transform + colour only, retargets from the visible value |
| Alternative Glint invisible with ImmediatelyFast | Every buffer source that fixes a foil type gets its vanilla↔masked counterpart fixed at construction | IF's `BatchableBufferSource.getBuffer` never calls super, so the lazy hook never ran; the masked glint flushed before its base item and failed the EQUAL depth test |
| Profiles: full-width rows | Card grid (3 columns at 1080p, fewer when narrow); active card = accent selection outline + Active pill; Duplicate/Delete stacked; hover hint; window fits up to three card rows | Owner request (like the launcher's instance tiles) |
| Theme: accent only | "Background Color": graphite (default, exact no-op) + 8 deep hue presets + Custom; persisted in `theme.background`; re-tints the neutral material family, deepened to keep primary text ≥7:1 | Owner request |
| Theme Custom pickers had an opacity strip | `ColorSetting.opaqueOnly()` for theme colours (alpha pinned to 1) | Window opacity has its own slider |

Reduced motion: Aurora has no reduced-motion preference today, so the keystroke press
offset cannot be gated on one; it is 2 px and ≤140 ms.

## Verification

`./gradlew build`: 604 tests, 0 failures (new: `MinimapGridTest`, `KeystrokesMotionTest`,
`BackgroundColorTest`, glint batching regression in `GlintBuffersTest`; contract tests
updated for the second theme-colour row and the card-grid geometry).
`FramePacingPersistenceTest` failed once in one run and passed 3/3 alone (timing flake, unrelated).

Native captures: `python tools/gui/run.py <name> --plan owner --extra-mod <ImmediatelyFast jar>`
(OpenGL, 1920×1080@3, disposable fixture), metrics `python tools/gui/owner_metrics.py <name>`.
Baseline = 926b109 jar, same plan and ImmediatelyFast 1.14.3:

| Metric | 926b109 | This pass |
|---|---|---|
| Minimap sprint (flying), frozen frame pairs / 11 | 7 (jump every 3rd frame) | 0 (uniform ~2.3) |
| Minimap rotate-with-player, frame deltas | 1.6 / 1.8 / 11.2 repeating | uniform ~6.1 |
| Held enchanted vs plain sword, Alternative Glint | 2.2 (no glint) | 11.5 (glint) |
| Screen frame P50 / P95 (60 fps cap) | 16.70 / 18.39 ms | 16.70 / 18.56 ms |

The frame times are capped and only show no regression. Not covered by automation: real
mouse drags in the HUD editor, keyboard focus traversal of the new rows, Sodium/VulkanMod,
other GPUs, Linux.

## Follow-up (same day)

| Before | After | Why |
|---|---|---|
| Title cards, Aurora-styled vanilla menu buttons and HUD editor buttons: plain glass at window opacity, grey/no edge | `Button.themed`: Theme Background Color fill (density floor 150/255 so it never reads as blank glass) + accent outline | Owner: buttons follow the Theme (outline choice: accent) |
| Title cards 180 px wide | 140 px | Owner: narrower |
| Keycaps chamfered on the logical grid (blocky) | Device-resolution AA caps/base/plate rasterized once per size into templates, blitted on the device grid; press offset snapped to whole device pixels | Owner: per-pixel rendering; one blit per layer keeps fill volume flat |
| Custom fonts showed boxes for glyphs the TTF lacks | Every text font ends with a `reference` to `minecraft:default` | Missing glyphs (symbols, CJK, server resource-pack icons) render exactly as vanilla. A universal hi-def replacement is impossible: server icons exist only in the server's default-font bitmaps |

Verified in `--plan owner` captures (fixture `owner-r2`): themed title/multiplayer/HUD-editor buttons
(purple background, green accent), 140 px title cards, keycap press frames, and a chat line of
ⓘ ⚑ 🌐 ⏲ ⚔ ✦ 中文 █ in Inter rendering through the fallback. 605 tests pass.
