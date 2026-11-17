# UI depth pass implementation handoff — 2026-11-17

Branch: `ui/depth-pass`, based on master `29ea9aa`. Local commits only; no push or PR.
The parent owns `C:/Dev/Aurora-Client/build/ui-depth/spec.md` and its baseline screenshots;
these were read only. No client boot, config write or runtime probe was performed.

## Spec mapping

| Spec item | Implementation |
|---|---|
| Vertical lightness-only face lighting | Core `MaterialLighting.lightness`; captured raster `UiLayerCache.lightVertical`, not the shader fallback. |
| Device-pixel bevel lines clipped to shape | `edgeLine` finds each column's first/last covered pixel and composites edge ink source-atop without altering coverage. |
| Raised / hover / pressed / inset / primary / disabled | `MaterialSurface.Light` recipes and cached endpoint textures. Disabled control faces are flat panel; existing muted thumb content remains legible. |
| Opaque ON / OFF module faces | `MaterialSurface.module`, shared by grid and list in `AuroraScreen.renderModulesLive`. ON uses raised light/shadow; OFF is flat -3. Hover is +2 to either face. No tile perimeter/hover outline, wash, accent edge or extra state shape. |
| Chevron / icon / composition | Existing hover/focus chevron visibility, accent-on/secondary-off icon colour, label wrapping, centring, dimensions, gaps, columns and row heights retained. |
| Window outline / body | Fixed 0.55 outline-alpha multiplier in `windowOutline`, used by parent, main-screen static chrome and `RoundedPanel`. Independent of `WINDOW_FILL`; no window body ramp. |
| Dropdown / keybind | Existing `field` callers inherit raised recipes. Dropdown value starts at +6 px and fits before the chevron; keybind content remains centred, including selected listening treatment. |
| Neutral Button / ButtonWidget | Shared Button painter uses cached raised/hover/reversed-press states. ButtonWidget delegates unchanged. Ghost and owner-themed branches keep their treatment. Done uses the primary recipe and existing shadow; shadow fades out into pressed. |
| Pick-one segmented controls | One inset well, text-only unselected cells with faint hover lift, selected raised cell inset by 1 GUI px with selection wash and existing thin accent edge. |
| Independent toggle chips | ToggleGroupSetting's existing SegmentedControl toggle mode becomes separate raised chips, with existing gaps and no shared well/divider. ON adds a centred leading IconCatalog check + label group, selected wash and edge. |
| Slider | Unified track uses the cached inset in overlay (outside the parent shape capture). Same-size cached bevel/shadow knob; fill colour, value text and focus/hover rings unchanged. |
| ToggleSwitch | Cached inset OFF track, existing flat accent ON fill and hover-wash behaviour, same-size cached bevel/shadow thumb; focus growth unchanged. Non-interactive represented preview retains its old look. |
| Numeric field / preview well | Non-search EditBox uses opaque cached inset; search's glass path is unchanged. Crosshair preview well uses inset, represented crosshair pixels unchanged. |
| Exclusions | No new depth applied to title/HUD editor themed actions, window body, sidebar, headings, text/icons, scrollbars, tooltips, focus rings, search or represented colour/checkerboard/crosshair data. |
| Docs | DESIGN_LANGUAGE §11 entries updated and a dated depth section records the full recipe table and explicit owner supersession. |

No implementation item was intentionally omitted. Visual acceptance, small/large viewport
and long-label checks, greyscale screenshots, glass/settings sweeps and runtime CPU/fill
measurements are deferred to the parent as explicitly instructed. The spec's shadow distances
are implemented in **device pixels**, like the bevel lines; the selected cell's 1 px inner
margin remains a GUI pixel. Existing geometry and focus treatments remain unchanged.

## Raster and lightness implementation

`core/src/main/java/com/aurora/client/theme/MaterialLighting.java:12` computes HSL lightness
L = (max RGB + min RGB)/2, L' = clamp(L + delta/100), then scales each channel's offset from L
by `(1 - abs(2*L' - 1)) / (1 - abs(2*L - 1))`. This preserves hue and HSL saturation without
allocating an HSL array or doing a hue conversion. Neutral endpoints use zero chroma. RGB is
rounded to 8 bits; alpha is copied exactly. Transparent pixels and zero delta are unchanged.

`UiLayerCache.java:148` linearly samples the top/bottom delta over physical rows, including
both endpoints (a one-row surface takes the midpoint). NativeImage ARGB accessors avoid raw
ABGR confusion. `edgeLine` at line 159 applies white/black overlay RGB source-atop to exactly
one covered device pixel per column, top or bottom; alpha and empty corners are preserved.

`softShadow` at line 174 evaluates a rounded-box signed distance with a vertical offset and
linear softness ramp, then composites black **behind** existing straight-alpha pixels.
Module shadows are separate cached templates so crossfading faces cannot double their
opacity. Knobs bake the same shadow behind their lit faces. Square face pixels remain square.

Material recipes live in `MaterialSurface.java:80`. Raised: +5/-3, white 0x1A/black 0x38;
hover: +5/-3, white 0x1F/black 0x38; pressed: -3/+2, black 0x33 top only; inset: -3/+2,
black 0x3D/white 0x0F; primary: +6/-4, white 0x29/black 0x38. ON modules reuse raised;
OFF is -3/-3 with no edges, hover is +7/-1 or -1/-1. Knobs retain their existing face colour
with white 0x1A/black 0x38 lines and no face ramp. These are the nearest 8-bit alpha values
to the percentages in the spec. Legacy RGB `shadeVertical` remains solely for existing
owner-themed cards/popups/tooltips, preserving their previous pixels; windows skip it.

## Cache changes and static draw cost

`MaterialSurface.java:264` retains the primitive packed key for width, height, radius,
kind and shadow. `light.ordinal()*1024` reserves distinct recipe/state families, including
ON/OFF and hover. The version is now `((generation*65537 + scale)*31 + fill)*31 + border`,
so different fills/edges invalidate the existing texture in place. Screen, viewport, GUI
scale and radius lifecycle invalidation remains in `beginFrame`; no new eviction path or
mid-frame texture destruction was introduced. Cached states blend through texture alpha;
settled states draw one face, avoiding double AA coverage. No state map is keyed by module
identity or a continuously changing hover value. Segmented labels cache fitted sequences
and widths per real width, theme/font/case style; callbacks and input routing are unchanged.

Headless raster tests measured **zero added RenderUtil GUI fills** during the raster passes.
This is not a per-frame measurement. Existing fill/CPU benchmarks are game probes under
`tools/parity/runtime/java/parityprobe`; they were not run. Runtime fills/frame, mean/p95 CPU
and cache-blit cost are **unmeasured**. Parent must run the probes before claiming the budget.

## Verification

Final `python tools/parity.py` built and tested all supported lines successfully:

| Minecraft | Reported tests | Passed | Skipped | Failures/errors |
|---|---:|---:|---:|---:|
| 26.1.2 | 1210 | 1209 | 1 | 0 / 0 |
| 26.2 | 1210 | 1209 | 1 | 0 / 0 |
| 26.3 | 1212 | 1211 | 1 | 0 / 0 |

The skip is the existing opt-in `PersistenceStressComparison.legacyVersusWriterUnderTransientOpens`
assumption, on every line. Disabled selected chips were explicitly checked to retain no accent edge. `python -m unittest discover -s tools`: **43 tests, OK**.
Focused 26.3 iterations covered pure math, native-image raster, UI component and setting
semantics. New coverage: 7 pure math, 4 NativeImage raster and 5 owner-contract tests. Raster
coverage includes alpha/channel preservation, rounded clipping, edges at scale 1–4 and
shadow composition; no graphics context is needed. Source and diff whitespace checks pass.

Only intended existing contracts changed:

- `SpxctraSimplificationContractTest.trailingFieldControlsShareOneFace`: old `FIELD_KIND`
  implementation snippet replaced by the shared depth-control delegate. Neutral controls
  still have no perimeter outline; baked top/bottom lighting is now owner-approved.
- `SquareModeConformanceTest`: added the new unified module call's resolved `tileRadius`
  assertion; the old embedded/stained checks remain for the supported legacy path.
- New `DepthPassContractTest` pins opaque elevation, no tile outlines, hover/focus-only cues,
  fixed outline alpha, chip/well structure, cache recipe/state separation and search/data
  boundaries. Existing input, focus, narration and setting tests remain unchanged.

## Parent visual tuning

Start with the binding values; tune only after live/factory screenshot comparisons.

| Constants / location | Initial value / purpose |
|---|---|
| `MaterialSurface.java:69` `WINDOW_OUTLINE_ALPHA` | .55, fixed token-alpha multiplier |
| `MaterialSurface.java:76` shadow constants | offset 1, module softness 2, knob softness 1 device px; black alpha .14 |
| `MaterialSurface.java:78` `PEER_HOVER_ALPHA` / `SWITCH_HOVER_ALPHA` | .16 faint peer lift / existing .08 switch hover wash |
| `MaterialSurface.java:81`–86 `Light` enum | All face ramps and top/bottom edge alpha constants |
| `SegmentedControl.java:62` `CHECK_EM` / `CHECK_GAP` | 10 / 4 GUI px, centred group with fitted label |
| `EnumSetting.java:373` value offset | Binding 6 GUI px left padding; reserve stays before the chevron |

Parent acceptance: live Inter/SQUARE/cyan/19% and factory Fira/ROUND/indigo/33%; grid + list,
Text & Fonts, Crosshair, Zoom, Settings sheet; rest/hover/focus/press/disabled; both long
labels and small/large viewports. Check greyscale ON/OFF, faint OFF track visibility, curved
one-pixel edges, primary Done, numeric entry and a screenshot-suppression glass fallback.
No design questions remain; shadow unit interpretation is recorded above for review.

## Changed files

- `core/src/main/java/com/aurora/client/theme/MaterialLighting.java`
- `adapters/shared/src/client/java/com/aurora/client/ui/util/UiLayerCache.java`
- `adapters/shared/src/client/java/com/aurora/client/ui/component/MaterialSurface.java`
- `adapters/shared/src/client/java/com/aurora/client/screen/AuroraScreen.java`
- `adapters/shared/src/client/java/com/aurora/client/ui/component/Button.java`
- `adapters/shared/src/client/java/com/aurora/client/ui/component/SegmentedControl.java`
- `adapters/shared/src/client/java/com/aurora/client/ui/component/Slider.java`
- `adapters/shared/src/client/java/com/aurora/client/ui/component/ToggleSwitch.java`
- `adapters/shared/src/client/java/com/aurora/client/ui/component/RoundedPanel.java`
- `adapters/shared/src/client/java/com/aurora/client/screen/setting/EnumSetting.java`
- `adapters/shared/src/client/java/com/aurora/client/screen/setting/CrosshairPreviewSetting.java`
- `adapters/shared/src/client/java/com/aurora/client/mixin/EditBoxMixin.java`
- `src/test/java/com/aurora/client/theme/MaterialLightingTest.java`
- `src/test/java/com/aurora/client/ui/util/UiLayerCacheLightingTest.java`
- `src/test/java/com/aurora/client/ui/DepthPassContractTest.java`
- `src/test/java/com/aurora/client/ui/SpxctraSimplificationContractTest.java`
- `src/test/java/com/aurora/client/ui/util/SquareModeConformanceTest.java`
- `docs/DESIGN_LANGUAGE.md`
- `docs/reports/2026-11-17-ui-depth-pass.md`

ButtonWidget, KeybindSetting, SegmentedSetting and ToggleGroupSetting already delegate to
the changed components, so they require no duplicate painter edits. No dependency/framework,
asset, config-schema or feature-default changes.

## Hygiene and retained work

Read/applied workspace hygiene at start and handoff. Removed 13 exact task-owned
`aurora-client-tests-*` log directories; preserved all 12 pre-existing ones.
Task scratch/logs under this worktree's `build/depth-pass` are removed after recording this
report. Normal per-line incremental outputs/reports and core/ports caches remain for parent
reuse (root build about 64.8 MiB). Parent owns their lifecycle after review/integration.
Worktree and branch remain in place. Parent baseline screenshots/spec were not modified.
