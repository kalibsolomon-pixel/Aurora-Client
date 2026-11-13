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
| `assert-inactivity afk` | Assert that the fixture's prior inactivity option was restored |
| `settings` | Open Aurora settings |
| `stable-settings` | Open the same screen with the singleplayer world paused |
| `blur` / `blur-radius 9` | Open the native blur harness / set the screen theme blur radius |
| `gui-scale 2` | Change GUI scale through the version's native resize entry point |
| `font-state` / `font-check` | Count deferred sets / compare metrics across all Aurora font IDs |
| `font-first-use aurora:inter_medium_ui` | Time one actual font-width lookup |
| `font-specimen` | Render fallback, bold/italic, decorations and obfuscation samples |
| `hud-editor` | Open the universal HUD editor |
| `factory` | Replace the disposable client's in-memory settings with effective factory defaults |
| `benchmark label` | Export an exploratory eight-second frame sample; not release overhead evidence |
| `resize 854 480` | Set the native window size |
| `layout grid` / `layout list` | Open the selected feature layout |
| `theme ROUND 0.33` / `theme SQUARE 0.19` | Apply factory/live appearance |
| `font INTER AURORA_ONLY` / `font SANSSERIF OFF` | Change the live HUD font policy |
| `detail animations` | Open a feature's native detail screen by ID |
| `search <text>` | Focus and fill the native search field |
| `escape` | Send Escape through the active screen's key handler |
| `capture` | Capture F7 through a real KeybindSetting and save |
| `bindings` | Assert config/vanilla sync and the native C reset default |
| `persisted` | Assert the F7 capture survived restart |
| `keycaps` | Assert the native extra-key label is F7 and show it in the HUD |
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
