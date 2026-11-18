# Shared UI layout verification

The feature overview, inline Settings, feature details, Profiles and Waypoints
now arrange their content in core `ScreenLayout`. The model retains row IDs,
clips to a half-open viewport, and translates by scroll at query time. The
adapter still measures the active font and paints through Minecraft. See
[Architecture](../ARCHITECTURE.md#screen-layout) for bounds ownership.

## Verification

The geometry tests compare legacy row and grid formulas against the arranged
boxes: narrow and wide viewports, both overview modes, one to three manager
columns, mixed setting heights, compact and normal height fixtures, collapsed
sections, a pinned header and fractional scroll. They also cover clipped hit
tests, focus order, reveal, and identity through removal/reinsertion and
object replacement. These are synthetic geometry and source-contract tests,
not a pixel comparison with the native client.

`python tools/parity.py` passed: 1,259 tests on 26.1.2, 1,259 on 26.2,
and 1,261 on 26.3, with one skip on each line. The optional probe JAR built
for every line. `python -m unittest discover -s tools` passed all 43 tests.

The Java 25 micro-benchmark (warmed core arrangement, changing row visibility)
measured the following on the development workstation:

| Rows | Mean (µs) | p95 (µs) |
|---:|---:|---:|
| 40 | 0.43 | 0.60 |
| 200 | 1.28 | 1.40 |
| 1,000 | 7.43 | 8.10 |

To reproduce on Windows after building 26.2:

```powershell
java -cp 'build/minecraft-26.2/classes/java/test;core/build/classes/java/main;ports/build/classes/java/main' com.aurora.client.ui.layout.LayoutBenchmark
```

The benchmark does not measure native font measurement, widget updates or
per-frame CPU. The core reuses boxes and arrays when inputs are unchanged;
host checks for dynamic visibility and existing widget synchronization still
run on paint.

## Native acceptance (parent)

Compare the pre-migration baseline `243ee8d5` with this branch using the
[parity probe](../../tools/parity/README.md) and matching viewport, GUI scale,
font, config and scroll. Capture `ui-bounds <label>` and a screenshot for:

- `layout grid` and `layout list`, with a long-label `search <query>`.
- The Settings tab, including an inline dropdown near a clip edge.
- `detail animations`, a tall crosshair page, and Item Scale with its pinned header.
- `profiles` (filter, `profile-create`, rename) and `waypoints-screen` or a
  disposable `waypoints40` fixture (scroll, row actions, reorder during rename).

Try narrow and wide layouts, partial rows, collapsed/expanded sections, hover,
and Tab navigation that reveals an offscreen control. Compare centred label
ink and face rectangles at factory and live opacity; on the new branch check
that `layoutId` stays constant across scroll and filter changes. No client
was booted as part of the implementation.

Pulsar, the title screen, pack browser and HUD editor are unchanged. Minecraft
still owns child traversal. Native widget-local subrects and popup placement
are not separate retained layout nodes.
