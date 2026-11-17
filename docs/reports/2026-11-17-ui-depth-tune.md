# Curvature depth tuning handoff — 2026-11-17

Branch `ui/depth-tune`, based on verified HEAD `6c4b9ec`. Local commits only; no push or PR.
Implements the parent's `C:/Dev/Aurora-Client/build/ui-depth/spec-2.md` and preserves the
first spec's owner rulings. The parent specs, screenshots and live config were read only.
No game boot, config write, screenshot capture or runtime benchmark was performed.

## Implementation and files changed

| File (repository-relative) | Spec item / evidence location |
|---|---|
| `core/src/main/java/com/aurora/client/theme/MaterialLighting.java:24` | Device-space rounded-box SDF, including exact square and circle cases. `bevelWidth:31` implements rounded/chamfer bands and the dome's .45-radius plateau. `pillowHeight:37` supplies quarter-round height. `curvatureDelta:47` computes gradient, signed slope, normalized normal, diffuse and rim-only specular. `face:72` combines the face ramp with curvature and HSL mapping; alpha/coverage is unchanged. |
| `adapters/shared/src/client/java/com/aurora/client/ui/util/UiLayerCache.java:159` | `lightFace` loops covered captured pixels using the face's own device geometry (including fractional knob size). `softShadow:170` offsets the SDF down-right, then composites black behind the face. No gradient shader fallback, temporary per-pixel objects or GUI fills. Removed `edgeLine` entirely; no role retains it. |
| `adapters/shared/src/client/java/com/aurora/client/ui/component/MaterialSurface.java:80` | All council recipes, including flat OFF/disabled, separate track/inset, pressed .4 height and light-text primary cap (`controlColors:113`). Opaque modules at `module:150` keep elevation/icon cues and +2 hover. Shadow templates sit behind state endpoints so fades cannot double shadow opacity. Padding at `cached:275` contains scale-correct shadow tails; lighting/shadow calls at `292`/`304` run only on cache rebuild. |
| `adapters/shared/src/client/java/com/aurora/client/ui/component/Slider.java:156` | Brighter dished slider track. Shared dome at `196`, same 9px size/value centre, accent fill and input/focus behaviour. |
| `adapters/shared/src/client/java/com/aurora/client/ui/component/ToggleSwitch.java:129` | Shared dome thumb; same size, corner family, focus growth, ON fill and input. Represented non-interactive preview retains its own painter. These and Slider are the only knob consumers. |
| `core/src/main/java/com/aurora/client/theme/UnifiedPalette.java:77` | Reversible owner-review saturation constants; `tintNeutrals:100` changes only custom-background raised/hover/pressed saturation, preserving lightness/alpha. Graphite remains an exact no-op. |
| `src/test/java/com/aurora/client/theme/MaterialLightingTest.java:7` | SDF/band/device-scale, rounded continuity vs square diagonal facets, upper-left lighting, plateau, inset inversion, pressed flattening, circle dome, alpha/HSL and clamps. Greyscale assertions at `56`, pure timing at `86`. |
| `src/test/java/com/aurora/client/ui/util/UiLayerCacheLightingTest.java:32` | Actual NativeImage raster coverage/alpha, own geometry, square silhouette, directional shadow, scale 1–4 and zero submitted GUI fills. CPU bake timing at `88`. |
| `src/test/java/com/aurora/client/theme/BackgroundColorTest.java:77` | Saturation cap across custom hues/low saturation/grey, lightness/alpha preservation, untouched other tokens, live background/opacity values and existing exact stock no-op. |
| `src/test/java/com/aurora/client/ui/DepthPassContractTest.java:15` | Intended round-2 recipe changes and raster API updated, existing owner constraints retained; new recipe/shadow/primary/disabled contract at `78`. |
| `docs/DESIGN_LANGUAGE.md:1513` | Updated dated depth section with curvature equations, light vector, full role table, saturation policy and supersession of flat bevel/device-pixel rules. Component entries for switch, slider, segmented well and dropdown updated. |
| `docs/reports/2026-11-17-ui-depth-tune.md` | This handoff and measurement evidence. |

The shared field/control recipes also cover buttons, keybinds, chips and selected segments;
existing inset callers cover numeric/preview wells. No duplicate consumer painters were added.
First-pass owner constraints remain: opaque tile/list faces, no new indicator shapes, no
accent tile hover edge/wash, hover/focus-only chevron, existing label wrapping/alignment,
window/layout sizes and positions. Search, represented data, sidebar, tooltips, focus rings,
title screen and HUD editor retain their existing treatment. There are no asset deletions.

## Tuning constants

| Location | Adopted values |
|---|---|
| `MaterialLighting.java:7` | Slope gain 1, diffuse rim gain 14 HSL points, specular gain 8; exponent 24 at `8`. |
| `MaterialLighting.java:9` | Light `normalize(-.35,-.75,.55)`; shadow direction `normalize(.35,.75)` at `13`. |
| `MaterialLighting.java:16` | Raised/dome height 1, inset -1, pressed .4, flat 0; inset/pressed/flat disable specular. |
| `MaterialLighting.java:31` | Band `clamp(round(radiusDevice*.6),scale,3*scale)`; radius below one GUI px uses `scale`; dome band `.55*radius`. |
| `MaterialSurface.java:81` | Raised/hover +5/-3, pressed -3/+2; inset +1/+5 and slider track +3/+7 at `83`; primary +6/-4 or +3/-4 for light `ON_ACTION` at `84`. |
| `MaterialSurface.java:85` | ON +11/+3, OFF -5/-5; hover ON +13/+5, hover OFF -3/-3 at `86`. |
| `MaterialSurface.java:87` | Knob 0/0 ramp, dome, elevation 1 GUI px, black alpha .18. |
| `MaterialSurface.java:88` | Module shadow elevation 2 GUI px / alpha .20; control elevation 1 GUI px / alpha .14. Softness is twice device elevation (`304`). |
| `MaterialSurface.java:113` | Primary cap when `contrast(ON_ACTION,black)>11`, exactly equivalent to relative luminance >.5. |
| `UnifiedPalette.java:77` | Custom raised saturation `min(backgroundSaturation*.35,.10)`; no saved value changes. |

Window outline attenuation remains .55 (`MaterialSurface.java:71`), independent of window
opacity. Factory graphite raised stays `#1d1f27`. Owner Background Color is `#201525`
(`-14674651`, read from canonical `run/config/aurora.json`); raised becomes `#272229`
from first-pass `#291b30`. The window stays `#201525` at alpha 48 for saved opacity .19.

## Computed face greyscale separation

Pure test samples an 80×80 face at scale 3, excluding pixels outside its SDF, and averages
8-bit greyscale `.299*R + .587*G + .114*B`. This measures faces, without icons, backdrop,
shadow or selection ink. It is not a screenshot measurement. Both themes are checked with
both corner families, including the live SQUARE and factory ROUND settings.

| Theme base | Radius (GUI px) | Rest ON − rest OFF | Rest ON − hover OFF | Hover ON − hover OFF |
|---|---:|---:|---:|---:|
| Live `#272229` | 0 | 29.779 | 24.665 | 29.603 |
| Live `#272229` | 10 | 29.784 | 24.670 | 29.606 |
| Factory `#1d1f27` | 0 | 28.459 | 23.644 | 28.344 |
| Factory `#1d1f27` | 10 | 28.464 | 23.649 | 28.349 |

All required differences exceed 8 points. The tile ramps implement exactly +2 HSL points of
hover, and the test separately asserts that hover's greyscale lift is smaller than elevation.

## Bake measurement and performance scope

Tests use `System.nanoTime`, 5 warmups and the median of 9 samples for one 80×80 tile at
scale 3 (240×240 face), ROUND radius 10. Final parity run:

| Minecraft | Pure face/SDF calculation | Headless NativeImage CPU bake |
|---|---:|---:|
| 26.1.2 | 2.822 ms | 5.300 ms |
| 26.2 | 2.921 ms | 5.336 ms |
| 26.3 | 2.871 ms | 5.307 ms |

The NativeImage test times synthetic captured AA-face construction, curvature raster and
the scale-correct ON shadow in a 270×270 padded image. It excludes texture upload and game
rendering. The pure test excludes shadow and NativeImage access. No performance threshold is
asserted from these variable wall-clock samples. Normal cache hits do no lighting work;
static faces add no GUI fills. Controls gain one cached shadow blit; actual blit/frame-time
cost and the release CPU/fill budget are unmeasured. Cache lifecycle and primitive keys stay
bounded by the existing screen/resize/radius rules; no list reconciliation or growing-list
algorithm was introduced. The parent owns runtime benchmarks and visual acceptance.

## Verification and contract changes

`python tools/parity.py` completed successfully for all three lines, including test/build,
adapter ownership and layering checks:

| Minecraft | Total | Passed | Skipped | Failures / errors |
|---|---:|---:|---:|---:|
| 26.1.2 | 1220 | 1219 | 1 | 0 / 0 |
| 26.2 | 1220 | 1219 | 1 | 0 / 0 |
| 26.3 | 1222 | 1221 | 1 | 0 / 0 |

The skip is existing opt-in `PersistenceStressComparison.legacyVersusWriterUnderTransientOpens`.
`python -m unittest discover -s tools`: **43 tests, OK**. Focused 26.3 iterations covered
MaterialLighting, UiLayerCacheLighting, SliderPilot, TogglePilot, BackgroundColor and
UnifiedPalette; full 26.3 test also passed. `git diff --check` and committed-source LF checks
passed. Existing compiler/LWJGL deprecation and native-access warnings are unchanged.

Only `DepthPassContractTest`'s intended contracts changed: new council ramp literals,
`lightFace` replacing vertical/edge passes, and slider's distinct `sliderTrack` call. Added
contracts pin profile choices, primary cap, shadow dimensions/alphas and no disabled shadow.
Obsolete edge-overlay raster/math checks were replaced by curvature/coverage checks because
no role retains the edge API. All other source contracts pass unchanged.

## Clarifications and parent acceptance

One mathematical correction to the draft is necessary: for negative-inside distance `d`,
the SDF gradient points outward and `height=f(-d/b)` has an inward slope. The upward physical
normal therefore uses **+gradient(d)** in XY (or **-gradient(-d)**), rather than the draft's
minus sign on an outward gradient. The literal draft sign illuminates bottom-right; the
implementation meets its explicit upper-left highlight, square facet and inset inversion
requirements. The design reference records this sign clarification. No other intended spec
deviation; SQUARE knobs retain square silhouettes instead of becoming circular.

Parent still needs live/factory ROUND/SQUARE screenshots and settings sweeps (including
opacity), greyscale review, Done text contrast at actual accent, small/large viewports and
long labels in grid/list, interactive rest/hover/focus/press/disabled states, plus frame
fills and mean/p95 CPU probes. No runtime visual/accessibility or performance pass is claimed.
The saturation cap is explicitly reversible for owner review.

## In-client acceptance (parent, 2026-11-17)

Minecraft 26.3, OpenGL, Windows 11, Radeon RX 9070 XT, 1162x1353 window at GUI scale 3, a
disposable copy of the owner's config and world. The live theme is the owner's (Inter, SQUARE,
cyan, 19% opacity); factory is the probe's `factory` plus `theme ROUND 0.33`. All 92 mixins
applied. Each sample is 120 warm-up frames then 600 steady frames (`tools/parity` probe).

| Measure (live theme) | master `29ea9aa` | depth pass final |
|---|---:|---:|
| Feature grid, steady extraction mean / p95 | 0.107 / 0.131 ms | 0.071 / 0.090 ms |
| Feature grid, GUI fills per frame | 51.6 | 8.4 |
| Text & Fonts, steady mean / p95 | 0.090 / 0.112 ms | 0.078 / 0.100 ms |
| Main screen, first open in session (one sample) | 69.6 ms | 92.2 ms |
| Text & Fonts, first open in session (one sample) | 18.0 ms | 21.7 ms |
| Main screen, reopen from gameplay | 12.6 ms (one sample) | 14.9–17.0 ms (four samples) |
| Text & Fonts, reopen from gameplay | 13.8 ms (one sample) | 15.5–15.9 ms (four samples) |

First-open figures are single samples that include first-use work (JIT, fonts, icons);
they are not isolated cache measurements. Before the bake speed-up the same reopens took
19.3 and 21.0 ms. Material templates now persist while moving between screens and are
released when the player returns to gameplay. Tracked image backing at the end of the
steady runs was 6.0 MiB (grid) and 8.5 MiB (Text & Fonts). This is a UI-open measurement,
not release overhead acceptance (`tools/overhead_check.py`).

Visuals: [before, live](evidence/ui-depth/before-live.png),
[after, live](evidence/ui-depth/after-live.png),
[before, factory](evidence/ui-depth/before-factory.png),
[after, factory](evidence/ui-depth/after-factory.png). In greyscale, ON and OFF tiles
separate clearly in both themes, and a hovered OFF tile stays visibly OFF. Not covered:
Vulkan, the 26.1.2 and 26.2 clients, GUI scales other than 3, and long-label overflow
beyond these screens.

## Hygiene and retained work

Workspace-hygiene skill read at task start and before handoff. Task-owned scratch
`build/depth-tune` and the ten exact test-log directories created by these Gradle runs are
removed after preserving evidence here. All twelve pre-existing test-log directories are
preserved. No task-owned servers, browsers or game processes were started.

Normal per-line build/test outputs and compatible core/ports caches remain for parent reuse
(root build about 60.5 MiB). The parent owns their later cleanup and the retained worktree/
branch after integration. The parent's `C:/Dev/Aurora-Client/build/ui-depth/` is untouched.
