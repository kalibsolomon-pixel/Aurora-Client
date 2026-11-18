# Native acceptance probe

This optional Fabric helper drives Aurora's existing screens and key-capture
paths inside a disposable dev client. It is excluded from production sources,
dependencies and JARs. It adds no renderer or feature behavior.

Build for the selected native classpath:

```sh
./gradlew parityProbeJar -Paurora.minecraft=26.2 -I tools/parity/runtime.init.gradle
```

Copy `build/minecraft-26.2/acceptance-tooling/parity-probe.jar` into a disposable
fixture's `mods/`. Create an absolute command directory with `DISPOSABLE.txt`.
Pass `-Daurora.parity.probe=<directory>` to the game JVM and select the fixture
with `-Paurora.runDir=<fixture>`. Never install this helper in the owner's live
instance or include it during overhead measurements.

Write one command to `command.txt`, then wait for matching `result.txt` ending
in `OK`. A `FAILED` result must be investigated; commands are consumed once.
Use native desktop screenshots to inspect the resulting screen.
`python tools/parity/command.py <control-directory> '<command>'` performs that
exchange atomically and refuses an unmarked fixture or a pending command.

| Command | Action |
|---|---|
| `world` | Wait for a loaded world, then close the current screen |
| `waypoints40` | Seed 40 rows in an empty disposable waypoint scope and open their manager |
| `waypoints-screen` | Open the waypoint manager without changing its rows |
| `shape-benchmark` | Measure 120 warm-up and 600 sampled manager extraction frames; write `shape-benchmark.json` |
| `shape-cases` | Open fixed shape, scissor and transform cases for visual comparison |
| `ui-open-benchmark label modules` / `ui-open-benchmark label text_fonts` | Arm before opening a fresh main/detail screen; write `label.json` after 120 warm-up and 600 sampled extraction frames |
| `ui-benchmark label [scroll]` | Sample the current main/detail screen; optional alternating wheel input every four frames |
| `ui-bounds label` | For one frame of the current main, feature detail, Profiles or Waypoints screen, write `label.json` with the native face/ink observations and arranged `layout` boxes (GUI px). A native control whose centre hits an arranged box also carries that box's `layoutId`. Compare face/ink with the baseline and use the IDs to match rows across scroll and filter changes. |
| `ui-cache-state label` | Snapshot observed layer-cache allocation/commit totals and tracked CPU image bytes |
| `ui-suppress` | Trigger the same 400 ms glass suppression as F2, without writing a screenshot |
| `ui-tooltip remember` / `unchanged` / `changed` / `absent` | Assert prepared-tooltip reuse, invalidation or release after hovering a described label |
| `fps-cap 120` | Set the disposable client's native FPS option and disable VSync |
| `assert-inactivity afk` | Assert that the fixture's prior inactivity option was restored |
| `settings` | Open Aurora settings |
| `stable-settings` | Open the same screen with the singleplayer world paused |
| `blur` / `blur-radius 9` | Open the native blur harness / set the screen theme blur radius |
| `gui-scale 2` | Change GUI scale through the version's native resize entry point |
| `font-state` / `font-check` | Count deferred sets / compare metrics across all Aurora font IDs |
| `font-first-use aurora:inter_medium_ui` | Time one actual font-width lookup |
| `font-specimen` | Render fallback, bold/italic, decorations and obfuscation samples |
| `hud-editor` | Open the universal HUD editor |
| `factory` | Replace the disposable client's in-memory settings with field initializers (does not overlay the shipped defaults JSON) |
| `benchmark label` | Export an exploratory eight-second frame sample; not release overhead evidence |
| `resize 854 480` | Set the native window size |
| `layout grid` / `layout list` | Open the selected feature layout |
| `theme ROUND 0.33` / `theme SQUARE 0.19` | Apply factory/live appearance |
| `font INTER AURORA_ONLY` / `font SANSSERIF OFF` | Change the live HUD font policy |
| `detail animations` | Open a feature's native detail screen by ID |
| `search <text>` | Focus and fill the native search field |
| `escape` | Send Escape through the active screen's key handler |
| `config field json` | Set one `AuroraConfig` field in memory from a JSON value (e.g. `config keystrokesKeyStyle "KEYCAP"`) |
| `hold forward on` | Hold a vanilla key mapping down (`forward left back right jump attack use`, `on`/`off`) |
| `type <text>` | Send each code point through the active screen's character handler |
| `key z ctrl shift` | Send one key (`z y a left right home end backspace enter`, optional `ctrl`/`shift`) through the screen's key handler |
| `field-state` | Log the focused text field's value, caret and selected text |
| `capture` | Capture F7 through a real KeybindSetting and save |
| `bindings` | Assert config/vanilla sync and the native C reset default |
| `persisted` | Assert the F7 capture survived restart |
| `keycaps` | Assert the native extra-key label is F7 and show it in the HUD |
| `pulsar-target` | Summon a still husk 1.8 blocks east of the player |
| `pulsar-reach 400 [creative]` | Switch to survival (or creative), then for N ticks sprint at the husk clicking the real attack key; logs `[parity-probe] pulsar-reach PASS/FAIL` comparing recorded reach with the vanilla pick (Reach Display) and the old interpolated formula |
| `pulsar-delete` | Select the second-newest recording in History and press its Delete button (opens the real confirmation; Escape cancels) |
| `pulsar-reach-stats [creative]` | After `pulsar-end`, assert the newest recording's hit reach statistics never exceed 3.0 (creative: hold no reach samples) |
| `menu` | Disconnect to Aurora's title screen |
| `stop` | Shut down normally |

For an Aurora-absent/installed/candidate FPS comparison, `fpsObserverJar` builds
a separate `fps-observer.jar` with **no Aurora dependency**. Install only that
observer in marked disposable fixtures with otherwise identical mod sets. It
waits 20 seconds in-world, then samples `Minecraft.getFps()` once a second for
20 seconds and writes `fps-observer.json` before closing normally. VSync is off,
the frame limit is Unlimited and the inactivity policy is MINIMIZED. This is a
short FPS observation, not frame-time, input-latency or release-overhead proof.
Do not install both observer JARs together. Neither belongs in an overhead boot.

`tools/performance/blur_phase_compare.py` drives the actual blur pipeline with
a four-device-pixel stripe pattern at fixed phases. `patternscale` and `phase`
are harness-only state tokens. A radius-9 test should remain constant within
two 8-bit intensity levels. Supply a body ROI matching the window and GUI scale;
the default ROI is for 1280x960 at scale 3, and scale 2 uses
`--box 440,500,840,610`.

Check both layouts at small and large sizes, factory ROUND/33% and live
SQUARE/19%, details, focused search, and menu settings without a world. Repeat
on OpenGL and native Vulkan (VulkanMod on 26.1.2). Run `capture`, then `bindings`
on a later tick; restart the same fixture and run `persisted` and `bindings`.
This verifies native handlers and persistence; it does not synthesize OS keys
or replace a physical-input/accessibility audit.

## Screen/cache measurements

The UI benchmark separates first extraction, warm-up and steady extraction,
and reports frame intervals separately. It observes `UiLayerCache` allocation
boundaries and commits through optional probe-only mixins; production Aurora
has no measurement callbacks. Upload payload is image width × height × 4,
including shared material templates. It is not GPU traffic measured by a
driver. Tracked backing bytes cover currently observed CPU images, not VRAM,
Java heap, every font cache or a process-wide memory total. Weak owner keys
avoid retaining closed screens. Totals accumulate for the probe session;
`cacheDuringRun` subtracts the counters captured at arm time.

Elapsed extraction includes scheduling stalls and excludes deferred GUI draws
and GPU work. The first extraction includes other first-use work such as fonts
and icons. Use repeated runs with matching framebuffer, scale, theme, world,
font and backend; do not infer an FPS gain from a smaller upload. A scrolling
sample must actually overflow: `text_fonts` at 854×480, GUI scale 2, does.
Wheel direction alternates every 120 frames, so time-based smoothing may
produce different commit counts. Compare payload per rebuild as well as totals.

Labels must be unique and contain only letters, digits, underscores or hyphens;
existing JSON files are never overwritten. Command acknowledgement means the
sample was armed; wait for its JSON or the completion log before changing
screens. The inactivity policy is temporarily `MINIMIZED` and restored on
completion, cancellation, screen replacement or `stop`. Other sampling modes
should be run sequentially. Record `-Daurora.ui.revision=<source>` and
`-Daurora.ui.backend=OPENGL` (or `VULKAN`) on the game JVM. These are provenance
labels; verify the selected backend in the actual launch log.

Native Unlimited currently takes precedence over Aurora's GUI FPS cap. The
report records the native option and tracker limit; Aurora's own frame pacing
can reduce the rate further. Inspect frame intervals, rather than assuming the
configured GUI cap applied. `fps-cap` changes only the disposable fixture's
options. This opt-in screen benchmark supplements the separate gameplay
[overhead gate](../../docs/OVERHEAD_BUDGET.md); never install the probe in a
release-overhead run.

The shape benchmark measures elapsed time inside the manager's extraction method
and `RenderUtil` fill and experimental shader quad submissions. It also measures
the interval between successive extractions, including frame pacing and waits.
Extraction time can include waits; it is not a CPU-only timer. Neither measure
isolates GPU time, and cold cache creation is outside the samples. Compare runs with the same
window, GUI scale, theme, backend, world and mod set; record these independently.
While sampling, the probe temporarily switches the inactivity FPS option to
`MINIMIZED` to prevent Minecraft's AFK cap from changing the comparison. It
restores the prior option on completion, cancellation or screen change.
The `waypoints40` command requires an empty waypoint scope and a second marker,
`SHAPE-BENCHMARK-DISPOSABLE.txt`, in the actual game directory. Create a fresh
disposable configuration for each new seed; the command refuses existing rows.
Pass `-Daurora.shapes.cpu=true` to the game JVM to compare the CPU path with
the default live shader path. Caches retain CPU capture coverage.

To check compile-failure fallback, run `python tools/parity/shape_failure_pack.py
26.3 <game-dir>` against a marked disposable game directory, enable the
generated `file/AuroraShapeFailure` resource pack, and pass
`-Daurora.shapes.expectFallback=true` to the game JVM. Remove any old
`shape-cases-rendered.txt` from the probe command directory, send `shape-cases`
and wait for the new marker. The probe asserts that the wide shape used CPU
fills instead of a shader quad. Check the log for the single Aurora fallback
warning; the deliberately invalid shader produces expected compiler errors.
The fallback lasts until the client restarts.
