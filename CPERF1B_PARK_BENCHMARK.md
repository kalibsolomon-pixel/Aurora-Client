# C-PERF.1B — Controlled PARK timing and efficiency benchmark

Date: 2026-10-17. Completed bounded 240 FPS comparison after the owner made the host available. **Retain the production 500 µs default.** The 250 µs setting consistently reduced observed spin wall time, but all three paired P99 frame-interval differences exceeded the predeclared 0.1 ms screen, and positive deadline-error tails worsened in every pair. CPU savings are not established.

## A. Repository baseline

- Root: `C:\Dev\Aurora-Client`; origin fetch/push: `https://github.com/kalibsolomon-pixel/Aurora-Client.git`.
- Branch: `codex/unified-theme-pilot`; full starting and final HEAD: `ab86f03e419d0656e84bfc748e21313d22886436`.
- Initial status: clean, including no untracked files. Final status: only `?? CPERF1B_PARK_BENCHMARK.md`; index empty, no modified tracked files.
- Before substantial work, recorded history, tracked-file set, critical documentation/build/Fabric metadata, and individual sizes/SHA-256 for 433 tracked files and 742 pre-existing files under `run/`.
- Verified prior commits `cd3f50d`, `93c3c07`, `4045692`, `ab86f03` are ancestors. Read AGENTS/ARCHITECTURE, relevant C-PERF.3 reports, diagnostics validation, the full observer report and current pacing/command paths. No standalone original C-PERF.1 report exists in the checkout; the C-PERF.3 provenance qualification remains.
- Preserved the one-park/final-spin algorithm, GLFW clock, anchored deadlines, late recovery, native precedence, global/GUI ownership, HYBRID-only Low CPU fix and optional diagnostics. Defaults remain 500 / 2000. No scheduler, settings, profile implementation or Launcher source was edited.

The initial preflight stopped because an owner multiplayer game and video browser were open. The owner subsequently replied, “I’ll close them; run the benchmark.” A fresh window inventory showed only Codex before benchmark launch. No owner game/browser was closed or controlled by this task. The earlier blocked draft is retained as `run/cperf1b-benchmark/blocked-preflight-report.md`; it is superseded by this completed report.

Baseline audit: `run/cperf1b-preflight-20261017/baseline.json`. Current audit: `run/cperf1b-benchmark/final-preservation.json`.

## Mandatory C-PERF.1A-V review gate



The authoritative [observer report](CPERF1A_OBSERVER_VALIDATION.md) recommends proceeding with a **timing-focused** C-PERF.1B while restricting CPU conclusions. It identifies no demonstrated diagnostic correctness or frame-scheduling blocker. It does **not** establish negligible observer CPU overhead. Diagnostics must be On in both threshold conditions; CPU remains secondary. An Off control was included in the procedure declared before collection.

The following numbers are **historical C-PERF.1A-V observations**, not new C-PERF.1B results. FPS and percentages retain the report's published rounding. Percentiles are internal HUD callback intervals, not display-presentation timing. CPU percentages use one logical core as 100%.

| Target / order | Diagnostics | Callbacks | FPS | Median / P95 / P99 ms | Max ms | Render CPU % | Process CPU % | Bytes/callback | GC count/ms | Validity |
| --- | --- | ---: | ---: | --- | ---: | ---: | ---: | ---: | ---: | --- |
| 60 / 1 | Off | 1801 | 60.03 | 16.657 / 17.105 / 17.281 | 17.85 | 6.41 | 20.16 | 180618 | 0/0 | Valid |
| 60 / 2 | On | 1803 | 60.00 | 16.627 / 17.152 / 17.364 | 18.82 | 5.10 | 16.69 | 178221 | 0/0 | Valid |
| 60 / 3 | Off | 1803 | 60.00 | 16.629 / 17.155 / 17.441 | 29.59 | 5.41 | 16.28 | 177850 | 3/35 | Valid |
| 60 / 4 | On | 1803 | 60.00 | 16.625 / 17.132 / 17.402 | 28.72 | 5.36 | 15.08 | 177749 | 0/0 | Valid |
| 60 / 5 | On | 1803 | 60.00 | 16.638 / 17.098 / 17.316 | 19.61 | 4.84 | 13.78 | 177812 | 0/0 | Valid |
| 60 / 6 | Off | 1803 | 60.00 | 16.643 / 17.106 / 17.290 | 18.25 | 5.30 | 12.22 | 177912 | 0/0 | Valid |
| 240 / 1 | Off | 7210 | 239.94 | 4.163 / 4.364 / 4.609 | 16.51 | 34.21 | 69.57 | 168841 | 1/12 | Valid |
| 240 / 2 | On | 7209 | 239.92 | 4.162 / 4.395 / 4.642 | 18.79 | 29.54 | 48.05 | 168531 | 1/15 | Valid |
| 240 / 3 | Off | 7210 | 239.95 | 4.163 / 4.396 / 4.655 | 11.53 | 22.93 | 45.29 | 168131 | 1/10 | Valid |
| 240 / 4 | On | 7198 | 239.94 | 4.163 / 4.374 / 4.604 | 14.08 | 41.41 | 74.27 | 168827 | 3/17 | Valid |
| 240 / 5 | On | 7198 | 239.93 | 4.164 / 4.362 / 4.620 | 17.30 | 35.57 | 50.94 | 168521 | 1/13 | Valid |
| 240 / 6 | Off | 7210 | 239.93 | 4.163 / 4.397 / 4.668 | 14.46 | 32.65 | 52.26 | 168141 | 3/17 | Valid |

All 12 historical measurement windows were valid, 30.000–30.050 seconds, with no discarded measurement window or extra repeat. A premeasurement launch closed normally before sampling; it was preparation, not a missing/unfavorable window. The earlier C-PERF.1A exploratory observer pass separately encountered native AFK 30 during its fourth observer phase; that phase remains excluded and is not part of these twelve windows. No historical window exceeded 50 ms. At 240, On/Off counts were 16/15 above 1.25 target periods and 3/3 above two periods.

At 60, Off/On mean render CPU was 5.71/5.10%, difference -0.61 pp (-10.7% relative); adjacent On-minus-Off differences were -1.31, -0.05, -0.47 pp, against Off range 1.10 pp and On range 0.52 pp. Process CPU declined over time from 20.16 to 12.22%. Mean FPS difference was -0.01 (-0.016%). Mean per-window P99 increased 24 µs (+0.14%); pairs were +83, -38, +26 µs, against an Off P99 range of 159 µs. Allocation difference was -866 bytes/callback (-0.48%), within the 2768-byte Off range.

At 240, Off/On mean render CPU was **29.93/35.50%**, difference **+5.57 pp (+18.6%)**. Pairs were **-4.68, +18.47, +2.92 pp**; Off range **11.28 pp**, On range **11.87 pp**. The positive mean cannot be attributed to instrumentation or dismissed. Process difference was +2.05 pp (+3.7%), with pairs -21.52, +28.98, -1.32 pp. Mean callback-rate difference was -0.014 FPS (-0.006%); median difference <1 µs, P95 -9 µs (-0.20%), P99 -22 µs (-0.47%). P99 pairs were +33, -51, -47 µs, against Off range 58 µs. Allocation changed +255 bytes/callback (+0.15%); pairs -310, +696, +380 bytes, against Off range 710 bytes.

All six historical On exports had FRAME_HEAD_GLOBAL / PARK, correct target, 500 µs spin, configured 2000 µs HYBRID threshold, focus true, segment 1, zero condition changes and zero discarded invocations. Histogram populations and asynchronous exports passed. At 240 the three mean spin-wall times were 980/984/991 µs and mean wake errors -464/-471/-479 µs. Early park returns explain observed spin durations exceeding the configured allowance; spin wall time includes possible preemption and is not active CPU time.

Remaining limitations: one JVM for all historical windows, only three observations per condition/target, mixed CPU effects, uncontrolled migration/boost/background/JIT/GC variation, tick-level validity checks, shared observer overhead, no Off internal deadline distributions, and no precise CPU detection bound. A hypothetical full 250 µs reduction on every 240 FPS frame equals 6% of one core; that is an illustration, not a prediction, and is smaller than the historical 11.28 pp Off variation. CPU accounting granularity was about 0.052 pp over 30 seconds, far below the observed variation. Historical fullscreen was 144 Hz despite the 240 Hz desktop. None of these results compare 250 against 500 µs.


## B. Environment

| Item | Actual benchmark environment |
| --- | --- |
| CPU | Intel Core i7-14700F, 20 physical / 28 logical processors |
| GPU / driver | AMD Radeon RX 9070 XT; Windows driver 32.0.31041.1004 |
| OS | Windows 11 Home, 10.0.26300 / build 26300 |
| Display | Fullscreen 2560×1440, desktop and observed fullscreen WMI mode 240 Hz; F3 confirms framebuffer resolution. Historical observer fullscreen was 144 Hz and is not used as a fresh baseline |
| Java | Microsoft 21.0.7+6-LTS, Microsoft-11369940; same managed Java executable for all seven JVMs |
| Game / loader / API | Minecraft 1.21.11 / Fabric Loader 0.19.2 / Fabric API 0.141.4+1.21.11, verified in runtime logs |
| Renderer / Sodium | OpenGL 3.3 core 26.8.1.260810 / Sodium 0.8.14+mc1.21.11 |
| Client | Aurora 3.0.0, unchanged source HEAD above, Loom development run; same compiled source and resources, no derived observer source set |
| Heap / JVM | `-Xms2G -Xmx2G`, `-Daurora.parkDiagnostics=true`, existing Loom development properties; no JFR, affinity, priority, power-plan or timer changes |
| Graphics | RD4 / SD12, CUSTOM preset, Fancy clouds, all particles, mipmaps4, FOV70, GUI Auto, no resource packs |
| Scheduling | PARK, global 240, native Unlimited (`maxFps:260`, F3 `T:inf`), VSyncOff, adaptive/Reflex sleepingOff, LowCPUOff, LowLatencyOn, configured HYBRID park threshold 2000; GUI cap30 applies to Aurora screens, not the world |
| Other inputs | Same Sodium, Cloth Config21.11.153 and Performance Overlay1.0.2 jars; same frozen source copy, spectator scene and options |

Java executable: `C:/Users/kalib/AppData/Local/com.aurora.launcher/runtimes/java-runtime-delta/windows-x64-cb4394a27089d19f65d5baa6cf0482c27c3c7865/bin/java.exe`. It was used read-only; no Launcher runtime or instance was modified.

The standalone retained jars have SHA-256:

- Sodium: `fd2619eff5da6b9ba6304b8b72ac3fe132c30e0bd5d675e4581eda417a272f5d`.
- Cloth Config: `8a40c84e5ecde525acfb6c4e13b8002dacfca3ef9534d7faf6e8049656f423c8`.
- Performance Overlay: `46c9500f67824996cd81b00a4d3da2dc09d5c6d56495cff98276c961d74804c2`.

The separate Launcher continuity fixture has a different scene and Loader/API stack; it was not substituted for this Client workload. Existing jar copies and read-only benchmark utilities were reused without editing their originals. Runtime warnings about PerfOS registry typing, the existing AMD workaround, development Realms authentication and sound resources were retained; no related changes were made.


Post-run compiled-class inventory: 415 classes, inventory SHA-256 `180ef5b803de37863d8c5af0c5d970b1f4627f8106154ef8d87ed1e8b8b6ed9f`. Full paths/hashes in `build-identity.json`; source hashes and unchanged Gradle compile state establish the build provenance, not a new release artifact.

## C. Declared experiment and measurement limits

The procedure was written before data in `run/cperf1b-benchmark/PROTOCOL.md`. Initial order: **500 / 250 / 250 / 500 / 500 / 250 µs**, fresh JVM each. The one permitted repeat is run 7 at 250 for the specifically resolved invalid run 2 configuration. Final valid pairs: **(1,7), (4,3), (5,6)**, each expressed baseline→candidate. Pair1/7 is widely separated in time and has weaker drift control than the other pairs. No 120 FPS block, extra rerun, heap sweep or other operating point was added.

Predeclared screens: approximately 1% effective-FPS difference; P99 regression `max(0.1ms, 2% × 4.166667ms)` = **0.1ms**. Interpret against baseline variation, histogram resolution and repeated direction. Require actual spin reduction without repeated worse deadline tails, late behavior or long-frame clusters. These are practical screens, not population confidence bounds.

Each JVM used a minimum 60s stationary diagnostic warmup, a diagnostic-Off Overlay 60s control, then a fresh primary diagnostic session enclosing an Overlay 60s capture. Warmup samples are excluded by declaration, not performance. Runs3–7 also exported the stopped warmup for configuration proof; runs1–2 confirmed through primary exports. Run7's failed first warmup activation is explicitly preserved below. No JSON export occurred inside an Overlay measurement window.

Source world: `run/cperf1av-validation/game/saves/Cperf1a`; initial-copy inventory identity SHA-256 `84b036cc3b191a066befc7968c88850c393520e3a69d68b369d19066365a352a`. The algorithm is SHA-256 over sorted compact JSON path/size/SHA-256 inventory, retained in `inputs-with-repeat.json`. All seven inputs started from this same snapshot. Seed 4138080715846940116. Exact source position `[-1402.0956924055163, 63, 1363.9175410163841]`, yaw/pitch `[136.97308349609375, 22.993738174438477]`, spectator 3.

The world was opened by keyboard, then frozen with existing `/tick freeze`. F3 camera checks at entry, before/after warmup and before/after primary matched rounded XYZ and137.0/23.0. No pointer action, teleport or camera reset occurred after world entry. Normal Save and Quit produced an exact NBT position/rotation match in all seven processes. The original source world was never opened or saved by this phase.

**Workload qualification:** initial world bytes and exact pose match; live worlds are not claimed bit-identical. Ticks elapsed between loading and the manual freeze differ, with saved Time/DayTime recorded below. Source Time was123992. Frozen scenes and settings remained comparable at the checkpoints, but different pre-freeze daylight/entity evolution and client animations/HUD clocks are residual variation. No measured-window drift was corrected. F3 endpoints and retained segment focus observations support continuity; they are not an independent continuous raw-input or physical-display certificate.

**Observers:** unchanged PARK collector via existing `/aurorapark start`, `stop`, `snapshot`; unchanged Performance Overlay1.0.2 F10 recorder with its existing60s auto-duration setting and HUD callback intervals. The hardcoded C-PERF.1A-V observer was not run or modified (it forces500, one JVM,60/240 blocks,10k buffer). No new instrumentation or CPU sampler was introduced. `manual.init.gradle` only selects disposable CWD, Java, heap, opt-in property and development username. `prepare.py`, `review.py` and `report.py` only copy/hash inputs or calculate offline results; they do not observe game frames/threads or instrument the application.

The existing Launcher `tools/benchmark/process_capture.py` retained a read-only handle to the explicit PID/creation identity,60s at0.5s intervals. Each capture completed121 samples. It measures whole-process CPU/memory and machine busy time. **Render-thread CPU and allocated bytes/callback are unavailable in this unchanged manual path.** Adding them would require prohibited instrumentation; they are reported unavailable, never inferred from process CPU or F3 text.

HUD statistics are computed from raw positive frame_ms values: type7 median/P95/P99; FPS = callback count / sum(intervals), not a smoothed HUD readout. T=4.166667ms; outliers exceed1.25T,2T,50ms. Cluster summary counts >2T intervals in fixed elapsed one-second bins. GC field is the existing time delta since previous poll, once per second; sum is contextual, not GC CPU or pause attribution. These callbacks do not establish display-presentation timing or input-to-photon latency.

PARK sessions bracket the 60s recorder with manual command gaps and thus have different counts/durations. Process capture starts after F10. Actual timing is listed below. No sample-identical frame/CPU/PARK correlation is claimed. Histogram ranks are approximate intervals `[lower, upper)` in µs; no bucket midpoint is promoted to an exact percentile. Spin/wait wall time includes possible preemption. Wake error is actual park duration minus requested park duration; deadline error is limiter exit minus anchored target. All exported deadline minima happened to be0ns here; no negative observations are invented.

## D. Attempt inventory and validity

Seven fresh JVMs attempted; **six valid primary runs (three per threshold), one invalid primary run retained, exactly one targeted repeat**. Every process produced two complete60s Overlay files, matching row/footer counts, monotonic endpoints and positive intervals, complete process captures, normal game shutdown and Gradle exit0/BUILD SUCCESSFUL, and exact normal-save camera match. Primary exports are stopped, segment1, FRAME_HEAD_GLOBAL/PARK240, focus true, no transitions/discards; every histogram count matches its corresponding invocation/park population.

Run2 requested 250 in copied aurora.json, but the copied active default profile restored 500 at startup. It is invalid as a candidate; raw Off/On data and `invalid.json` remain. Before further captures, the correction and final repeat slot were declared in PROTOCOL. Runs3/6/7 manually selected 250 in the existing UI before world entry and verified a stopped warmup snapshot. No original or production profile was edited. Natural saves affected disposable copies only.

Run6 setup accidentally toggled the Display Settings master before world entry, changing smooth camera, decoupled input and global cap. These were restored through the existing UI before loading the world. Full configuration comparison before measurement showed only spin 250 and playtime counters differing. Run7's first warmup start paste failed with logged clipboard access denied; its zero-invocation/session-none export is preserved as `premeasurement-empty-snapshot.json`. The complete command was then verified visibly, a full 80.043s diagnostic warmup completed in the same JVM, and a valid stopped warmup export was checked before either recorder window. This is a premeasurement correction, not another JVM or replacement of measured data. It increases run 7's premeasurement residence time and remains a limitation.

All post-run aurora.json documents match run 1 except spin allowance and playtime counters. All have native 260/VSyncfalse/fullscreentrue/RD4/SD12/resourcePacks[]/inactivity=minimized. Initial and final configuration hashes, raw hashes, runtime logs, boundary PNGs and timestamp events are retained.

| Run | Requested / actual µs | PID / creation identity | Primary session | FPS | Off / On callbacks | Validity |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 500 / 500 | 23316 / 134367334742602481 | 55d16cd3-c99c-4cdd-a587-799bf80dea66 | 239.894 | 14405 / 14404 | Valid |
| 2 | 250 / 500 | 22684 / 134367339994542519 | d338a532-794f-4d6f-a2af-a5f4e4e4af2d | 239.897 | 14403 / 14393 | INVALID: profile restored 500 |
| 3 | 250 / 250 | 28032 / 134367345700166712 | 3c87419e-7777-4cb6-b6dd-d32200178568 | 239.976 | 14403 / 14398 | Valid |
| 4 | 500 / 500 | 29656 / 134367352439780775 | 12c02e5c-31e0-4c01-b983-1ba74eb910b5 | 239.878 | 14403 / 14392 | Valid |
| 5 | 500 / 500 | 2328 / 134367357256381275 | 4c0a3607-48b0-4b51-9965-4454021d1abf | 239.870 | 14406 / 14403 | Valid |
| 6 | 250 / 250 | 7752 / 134367360633201213 | ddfae0f4-5ca4-46c3-9a19-362eff95c9d2 | 239.924 | 14400 / 14406 | Valid |
| 7 | 250 / 250 | 26740 / 134367365508635324 | 349755b7-00d4-40f4-a336-31c60643eb9f | 239.798 | 14404 / 14387 | Valid |

| Run | Warmup diagnostic s | Primary PARK s | Primary HUD first–last elapsed ms | CPU start after F10 Off / On s | Saved Time = DayTime |
| --- | --- | --- | --- | --- | --- |
| 1 | 97.142 | 94.382 | 3–60043 | 0.485 / 0.487 | 124877 |
| 2 | 91.811 | 87.773 | 3–59996 | 0.464 / 0.485 | 124944 |
| 3 | 87.441 | 89.573 | 3–59997 | 0.489 / 0.478 | 124573 |
| 4 | 63.275 | 66.296 | 3–59997 | 0.423 / 0.444 | 124333 |
| 5 | 69.860 | 67.696 | 3–60045 | 0.414 / 0.426 | 124219 |
| 6 | 70.860 | 72.083 | 3–60043 | 0.437 / 0.420 | 124270 |
| 7 | 80.043 | 77.685 | 3–59996 | 0.419 / 0.422 | 124208 |

Run7 warmup duration above is the successful activation only; failed setup history is retained. Run4 after-warmup F3 was taken at 57.6s, then collection stopped at 63.275s, satisfying the 60s minimum. All capture timing and manual gaps are retained in `events.jsonl` and logs. No primary window was paused or shortened.

## E. Primary timing comparison

Only valid runs enter the following comparisons. Histograms include zero spin/wait for invocation cases that did not enter the corresponding wait; requested/actual/wake populations are actual park calls.

| Run / µs | Invocations / active waits / parks | Late entry / too short / spin-only | Spin entered | Early / late park returns | Initial / late reanchors | Late entry / reanchor per10k |
| --- | --- | --- | --- | --- | --- | --- |
| 1 / 500 | 22641/22640/22637 | 1/3/3 | 22243 | 20206 / 2431 | 0 / 3 | 0.442 / 1.325 |
| 3 / 250 | 21495/21493/21493 | 2/0/0 | 19054 | 17093 / 4400 | 0 / 1 | 0.930 / 0.465 |
| 4 / 500 | 15897/15897/15897 | 0/0/0 | 14951 | 13039 / 2858 | 0 / 4 | 0.000 / 2.516 |
| 5 / 500 | 16235/16235/16235 | 0/0/0 | 15369 | 13274 / 2961 | 0 / 4 | 0.000 / 2.464 |
| 6 / 250 | 17289/17288/17288 | 1/0/0 | 15027 | 13576 / 3712 | 0 / 3 | 0.578 / 1.735 |
| 7 / 250 | 18628/18628/18627 | 0/1/1 | 16569 | 14880 / 3747 | 0 / 4 | 0.000 / 2.147 |


Initial-anchor count0 means these fresh collection sessions began while an existing anchored schedule was already running; it does not mean the scheduler never initialized. Reanchor counts are small and cover the wider PARK session.

### Spin wall time

| Run / µs | Mean µs | Approx P50 / P95 / P99 µs | Max µs | ≥1ms count / % | ≥2ms count / % |
| --- | --- | --- | --- | --- | --- |
| 1 / 500 | 1334.044 | [1350, 1375) / [2000, 4000) / [2000, 4000) | 29107.900 | 15128 / 66.817% | 3594 / 15.874% |
| 3 / 250 | 743.670 | [725, 750) / [1625, 1650) / [1975, 2000) | 2274.100 | 6993 / 32.533% | 198 / 0.921% |
| 4 / 500 | 1196.357 | [1175, 1200) / [2000, 4000) / [2000, 4000) | 2892.500 | 9692 / 60.967% | 2032 / 12.782% |
| 5 / 500 | 1148.768 | [1125, 1150) / [2000, 4000) / [2000, 4000) | 5988.900 | 9596 / 59.107% | 1677 / 10.330% |
| 6 / 250 | 835.608 | [850, 875) / [1875, 1900) / [2000, 4000) | 5032.600 | 7035 / 40.691% | 388 / 2.244% |
| 7 / 250 | 829.576 | [825, 850) / [1850, 1875) / [2000, 4000) | 24536.800 | 7193 / 38.614% | 441 / 2.367% |


Typical spin, P95 buckets and ≥1/2ms proportions improve in all pairs. P99 resolves lower only in run 3; runs6/7 and baselines share the broad [2000,4000)µs bucket, so a repeatable exact P99 reduction is not established. Maxima are noisy, including 24.537ms candidate and 29.108ms baseline spin. Actual mean spinning remains well above 250/500 because early park returns leave more time to spin. The measured reduction is not simply the configured 250µs difference.

### Parking, total wait and signed errors

All values below are µs. Each row reports mean, approximate P50/P95/P99 and observed min/max. The full buckets remain in raw exports.


**Requested park**

| Run / µs | Mean | Approx P50 | Approx P95 | Approx P99 | Min / max |
| --- | --- | --- | --- | --- | --- |
| 1 / 500 | 2889.793 | [2000, 4000) | [2000, 4000) | [2000, 4000) | 963.766 / 3167.533 |
| 3 / 250 | 3142.792 | [2000, 4000) | [2000, 4000) | [2000, 4000) | 3.199 / 3368.733 |
| 4 / 500 | 2919.692 | [2000, 4000) | [2000, 4000) | [2000, 4000) | 734.266 / 3152.233 |
| 5 / 500 | 2955.386 | [2000, 4000) | [2000, 4000) | [2000, 4000) | 493.266 / 3189.933 |
| 6 / 250 | 3064.990 | [2000, 4000) | [2000, 4000) | [2000, 4000) | 714.466 / 3385.699 |
| 7 / 250 | 3082.664 | [2000, 4000) | [2000, 4000) | [2000, 4000) | 697.266 / 3415.066 |


**Actual park**

| Run / µs | Mean | Approx P50 | Approx P95 | Approx P99 | Min / max |
| --- | --- | --- | --- | --- | --- |
| 1 / 500 | 2061.503 | [2000, 4000) | [2000, 4000) | [2000, 4000) | 40.699 / 7033.701 |
| 3 / 250 | 2681.855 | [2000, 4000) | [2000, 4000) | [2000, 4000) | 17.500 / 4503.900 |
| 4 / 500 | 2239.438 | [2000, 4000) | [2000, 4000) | [2000, 4000) | 24.000 / 4530.900 |
| 5 / 500 | 2320.143 | [2000, 4000) | [2000, 4000) | [2000, 4000) | 81.100 / 4508.700 |
| 6 / 250 | 2521.865 | [2000, 4000) | [2000, 4000) | [4000, 8000) | 37.300 / 4559.300 |
| 7 / 250 | 2539.199 | [2000, 4000) | [2000, 4000) | [4000, 8000) | 89.500 / 4794.100 |


**Total limiter wait**

| Run / µs | Mean | Approx P50 | Approx P95 | Approx P99 | Min / max |
| --- | --- | --- | --- | --- | --- |
| 1 / 500 | 3395.789 | [2000, 4000) | [2000, 4000) | [2000, 4000) | 0.000 / 30437.300 |
| 3 / 250 | 3426.007 | [2000, 4000) | [2000, 4000) | [2000, 4000) | 0.000 / 4504.100 |
| 4 / 500 | 3436.291 | [2000, 4000) | [2000, 4000) | [2000, 4000) | 1565.000 / 4531.300 |
| 5 / 500 | 3469.533 | [2000, 4000) | [2000, 4000) | [2000, 4000) | 993.300 / 7450.200 |
| 6 / 250 | 3357.872 | [2000, 4000) | [2000, 4000) | [4000, 8000) | 0.000 / 7177.100 |
| 7 / 250 | 3369.222 | [2000, 4000) | [2000, 4000) | [4000, 8000) | 68.900 / 25737.200 |


**Signed wake error**

| Run / µs | Mean | Approx P50 | Approx P95 | Approx P99 | Min / max |
| --- | --- | --- | --- | --- | --- |
| 1 / 500 | -828.290 | [-875, -850) | [200, 225) | [700, 725) | -1983.133 / 4361.668 |
| 3 / 250 | -460.937 | [-500, -475) | [525, 550) | [825, 850) | -1957.833 / 1349.634 |
| 4 / 500 | -680.254 | [-700, -675) | [550, 575) | [900, 925) | -2392.599 / 1485.834 |
| 5 / 500 | -635.243 | [-650, -625) | [525, 550) | [875, 900) | -2393.333 / 1491.801 |
| 6 / 250 | -543.125 | [-625, -600) | [600, 625) | [875, 900) | -1975.266 / 1472.634 |
| 7 / 250 | -543.465 | [-600, -575) | [575, 600) | [850, 875) | -1987.266 / 1684.367 |


**Signed deadline error**

| Run / µs | Mean | Approx P50 | Approx P95 | Approx P99 | Min / max |
| --- | --- | --- | --- | --- | --- |
| 1 / 500 | 6.603 | [0, 25) | [0, 25) | [200, 225) | 0.000 / 27045.933 |
| 3 / 250 | 33.587 | [0, 25) | [275, 300) | [575, 600) | 0.000 / 1100.033 |
| 4 / 500 | 16.598 | [0, 25) | [75, 100) | [400, 425) | 0.000 / 986.233 |
| 5 / 500 | 14.146 | [0, 25) | [50, 75) | [375, 400) | 0.000 / 4109.533 |
| 6 / 250 | 43.161 | [0, 25) | [350, 375) | [625, 650) | 0.000 / 4130.533 |
| 7 / 250 | 36.733 | [0, 25) | [325, 350) | [625, 650) | 0.000 / 22505.233 |


| Run | Late park returns % | Deadline ≥25µs count / % | Deadline ≥100µs count / % | Deadline ≥1ms count / % | Deadline ≥2ms count |
| --- | --- | --- | --- | --- | --- |
| 1 | 10.739 | 453 / 2.001 | 343 / 1.515 | 4 / 0.018 | 3 |
| 3 | 20.472 | 2332 / 10.849 | 1952 / 9.081 | 13 / 0.060 | 0 |
| 4 | 17.978 | 989 / 6.221 | 781 / 4.913 | 0 / 0.000 | 0 |
| 5 | 18.238 | 897 / 5.525 | 708 / 4.361 | 1 / 0.006 | 1 |
| 6 | 21.472 | 2382 / 13.778 | 1840 / 10.643 | 31 / 0.179 | 1 |
| 7 | 20.116 | 1992 / 10.694 | 1656 / 8.890 | 34 / 0.183 | 1 |


Wake-error P95/P99 is mixed across pairs and does not establish an independent uniform park-return regression. Longer requested parks and less negative mean wake error accompany lower spinning. **Final deadline-error P95/P99 and ≥100µs frequency worsen in every pair**, including disjoint P99 rank buckets. This is the central scheduling-quality tradeoff. Sub25µs deadline lateness cannot be separated from exact0 by the first nonnegative bucket; ≥25/100µs rates are reported instead of an invented exact positive-deadline count. Late-entry/reanchor rates do not show a consistent regression; rare worst-case maxima are not used alone to choose a winner.

### HUD callback intervals

| Run / µs | FPS | Median / P95 / P99 ms | Max ms | >1.25T / >2T / >50ms | Max >2T in one second | GC time deltas ms |
| --- | --- | --- | --- | --- | --- | --- |
| 1 / 500 | 239.894 | 4.166 / 4.404 / 4.601 | 32.308 | 7 / 3 / 0 | 1 | 40.0 |
| 3 / 250 | 239.976 | 4.168 / 4.496 / 4.861 | 14.039 | 41 / 1 / 0 | 1 | 18.0 |
| 4 / 500 | 239.878 | 4.165 / 4.479 / 4.682 | 21.411 | 11 / 3 / 0 | 2 | 41.0 |
| 5 / 500 | 239.870 | 4.165 / 4.455 / 4.733 | 17.750 | 7 / 4 / 0 | 1 | 48.0 |
| 6 / 250 | 239.924 | 4.164 / 4.597 / 4.852 | 16.795 | 15 / 3 / 0 | 2 | 30.0 |
| 7 / 250 | 239.798 | 4.166 / 4.581 / 4.873 | 27.554 | 15 / 4 / 0 | 2 | 63.0 |


All rates remain within 1%, with paired FPS differences between −0.040% and +0.041%. All three candidate P99 intervals exceed every baseline value. Candidate >1.25T counts increase in every pair (7→15,11→41,7→15). >2T counts and fixed-bin clusters are mixed; there is no demonstrated new repeatable >2T cluster problem, and no >50ms frame appears in any Off/On capture, including invalid run 2. This supports a moderate-tail regression rather than a throughput collapse or demonstrated stability failure.

### Per-condition medians and variation

These are medians/ranges of three run-level statistics, never pooled percentiles. Histogram rank ranges above remain the authority for P50/P95/P99 spin/error values. Relative differences below use the baseline median; rate differences are percentage points where marked.

| Statistic | 500 median [min,max] | 250 median [min,max] | 250−500 | Relative |
| --- | --- | --- | --- | --- |
| FPS | 239.878 [239.870, 239.894] | 239.924 [239.798, 239.976] | +0.046 | +0.02% |
| HUD median ms | 4.165 [4.165, 4.166] | 4.166 [4.164, 4.168] | +0.001 | +0.02% |
| HUD P95 ms | 4.455 [4.404, 4.479] | 4.581 [4.496, 4.597] | +0.126 | +2.83% |
| HUD P99 ms | 4.682 [4.601, 4.733] | 4.861 [4.852, 4.873] | +0.179 | +3.83% |
| Spin mean µs | 1196.357 [1148.768, 1334.044] | 829.576 [743.670, 835.608] | -366.781 | -30.66% |
| Requested park mean µs | 2919.692 [2889.793, 2955.386] | 3082.664 [3064.990, 3142.792] | +162.972 | +5.58% |
| Actual park mean µs | 2239.438 [2061.503, 2320.143] | 2539.199 [2521.865, 2681.855] | +299.761 | +13.39% |
| Total wait mean µs | 3436.291 [3395.789, 3469.533] | 3369.222 [3357.872, 3426.007] | -67.069 | -1.95% |
| Wake mean µs | -680.254 [-828.290, -635.243] | -543.125 [-543.465, -460.937] | +137.129 | +20.16% |
| Deadline mean µs | 14.146 [6.603, 16.598] | 36.733 [33.587, 43.161] | +22.587 | +159.67% |
| Spin ≥2ms % | 12.782 [10.330, 15.874] | 2.244 [0.921, 2.367] | -10.538 | -82.44% |
| Deadline ≥100µs % | 4.361 [1.515, 4.913] | 9.081 [8.890, 10.643] | +4.720 | +108.24% |
| Late entries /10k | 0.000 [0.000, 0.442] | 0.578 [0.000, 0.930] | +0.578 | n/a (zero baseline median) |
| Reanchors /10k | 2.464 [1.325, 2.516] | 1.735 [0.465, 2.147] | -0.729 | -29.57% |
| Process CPU one-core % | 72.630 [48.307, 82.213] | 63.802 [56.901, 65.625] | -8.828 | -12.15% |


For signed wake means, relative change uses the magnitude of the baseline; positive here means less early, not automatically better. Percentage-statistic absolute differences are percentage points.

Median-of-run percentile bounds (µs), derived from the ordered interval endpoints, are below. They are not pooled percentiles; overlapping bounds do not establish a change and no exact relative percentile difference is asserted.

| Median-of-run approximate rank | 500 µs | 250 µs |
| --- | --- | --- |
| SPIN_WALL P50 | [1175, 1200) | [825, 850) |
| SPIN_WALL P95 | [2000, 4000) | [1850, 1875) |
| SPIN_WALL P99 | [2000, 4000) | [2000, 4000) |
| REQUESTED_PARK P50 | [2000, 4000) | [2000, 4000) |
| REQUESTED_PARK P95 | [2000, 4000) | [2000, 4000) |
| REQUESTED_PARK P99 | [2000, 4000) | [2000, 4000) |
| ACTUAL_PARK P50 | [2000, 4000) | [2000, 4000) |
| ACTUAL_PARK P95 | [2000, 4000) | [2000, 4000) |
| ACTUAL_PARK P99 | [2000, 4000) | [4000, 8000) |
| TOTAL_WAIT_WALL P50 | [2000, 4000) | [2000, 4000) |
| TOTAL_WAIT_WALL P95 | [2000, 4000) | [2000, 4000) |
| TOTAL_WAIT_WALL P99 | [2000, 4000) | [4000, 8000) |
| WAKE_ERROR P50 | [-700, -675) | [-600, -575) |
| WAKE_ERROR P95 | [525, 550) | [575, 600) |
| WAKE_ERROR P99 | [875, 900) | [850, 875) |
| DEADLINE_ERROR P50 | [0, 25) | [0, 25) |
| DEADLINE_ERROR P95 | [50, 75) | [325, 350) |
| DEADLINE_ERROR P99 | [375, 400) | [625, 650) |


P99 median increases 0.17924ms (+3.83%), versus baseline across-run range 0.132ms and candidate range 0.021ms. Mean-spin median decreases 366.781µs (−30.66%), with nonoverlapping condition ranges. Deadline mean and ≥100µs frequency also have nonoverlapping ranges. These are descriptive repeated-run observations on this host, not a confidence interval or a universal causal estimate.

### Counterbalanced pairs

All differences are candidate−baseline. Pair1/7 inherits the repeat’s long temporal gap.

| Pair | Spin mean µs / relative | FPS / relative | HUD P95 ms | HUD P99 ms / relative | Deadline≥100µs rate | Reanchors /10k |
| --- | --- | --- | --- | --- | --- | --- |
| 1→7 | -504.468 (-37.81%) | -0.0958 (-0.040%) | +0.17700 | +0.27200 (+5.91%) | +7.375pp | +0.822 |
| 4→3 | -452.687 (-37.84%) | +0.0977 (+0.041%) | +0.01655 | +0.17924 (+3.83%) | +4.168pp | -2.051 |
| 5→6 | -313.160 (-27.26%) | +0.0546 (+0.023%) | +0.14200 | +0.11900 (+2.51%) | +6.282pp | -0.729 |


The 0.1ms P99 screen is exceeded by all three pairs (+0.272,+0.17924,+0.119ms). The final deadline P99 buckets move respectively [200,225)→[625,650),[400,425)→[575,600),[375,400)→[625,650)µs. Their conservative interval differences span approximately 400–450, 150–200, 225–275 µs; approximate ranks cannot support exact percentage changes. Spin P50 buckets move [1350,1375)→[825,850),[1175,1200)→[725,750),[1125,1150)→[850,875)µs, conservative differences spanning approximately −550 to −500, −475 to −425, −300 to −250 µs. Bucket endpoints make these bounds approximate; no midpoint was assumed.

## F. CPU, allocation and Off-control context

Render-thread CPU time / percentage and allocated bytes per observed frame: **unavailable** with the unchanged manual recorder. No estimate is substituted. Process CPU uses 100% for one logical core; divide by 28 for whole-machine percentage. CPU capture is shifted slightly after F10 as listed above and may include its small post-recorder remainder. GC sums below are polled GC-time deltas, not per-frame pause attribution.

| Run | Off / On process CPU % one core | On CPU seconds / machine % | Off / On machine busy % | On RSS min–max MiB | On available RAM minimum MiB | Off / On GC ms | Off FPS / P99ms |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 49.40 / 82.21 | 49.328 / 2.936 | 4.95 / 5.97 | 2925.5–2926.3 | 16355.0 | 37 / 40 | 239.923 / 4.616 |
| 2 | 47.92 / 64.17 | 38.500 / 2.292 | 4.67 / 5.21 | 2934.7–2935.6 | 16316.9 | 49 / 38 | 239.881 / 4.735 |
| 3 | 62.00 / 65.62 | 39.375 / 2.344 | 5.06 / 5.00 | 2893.7–2936.5 | 16501.9 | 47 / 18 | 239.883 / 4.836 |
| 4 | 75.65 / 48.31 | 28.984 / 1.725 | 5.46 / 4.68 | 2918.6–2919.4 | 16516.3 | 50 / 41 | 239.879 / 4.701 |
| 5 | 81.76 / 72.63 | 43.578 / 2.594 | 5.29 / 5.66 | 2929.0–2929.5 | 16284.7 | 26 / 48 | 239.937 / 4.779 |
| 6 | 61.07 / 56.90 | 34.141 / 2.032 | 5.15 / 4.65 | 2964.7–2965.0 | 16217.6 | 61 / 30 | 239.831 / 4.926 |
| 7 | 43.23 / 63.80 | 38.281 / 2.279 | 4.65 / 4.71 | 2908.0–2932.0 | 16661.8 | 31 / 63 | 239.915 / 4.870 |


**CPU evidence classification: Inconclusive.** Valid On baseline process CPU ranges 48.31–82.21% of one core (33.91pp), candidate 56.90–65.62% (8.72pp). Median changes 72.63→63.80%,−8.83pp (−12.15% relative), but pair changes are−18.41,+17.32,−15.73pp. This is inconsistent and within baseline variation. Off→On changes also reverse sharply, including +32.81pp in run 1 and −27.34pp in run 4. The sequential controls cannot disentangle diagnostics, process/JIT/GC state, host drift or thread migration. A lower group mean/median is not attributable CPU savings.

Off P99 remains higher in the candidate processes as well: baseline 4.616–4.779ms versus candidate 4.836–4.926ms. Thus the timing tradeoff is visible without active PARK diagnostics too; however, fixed Off→On order and one control per JVM do not isolate collector overhead or remove run-order confounding. On−Off P99 changes are −0.015,−0.019,−0.046ms in baselines and +0.025,−0.074,+0.003ms in candidates. These controls support rate/context checks, not a new negligible-overhead claim.

All primary HUD captures report 2048MiB maximum heap and 60 GC-counter polls; valid On GC-time sums range 18–63ms. Memory ranges and available RAM do not suggest a sustained host-memory shortage in these captures, but paging, GPU load, thermals, power, frequency and render-thread allocation/CPU were not observed. The historical +5.57pp 240FPS diagnostic-On render-CPU mean remains unresolved. No CPU, energy or power saving is demonstrated, and spin wall reductions are not converted into such claims.

## G. Interpretation

**2. Lower spin time but scheduling-quality regression.** This is the observed result within the declared host/workload/observer limits, not a verified universal PARK defect or proof of a particular OS cause.

- Repeatable benefit: lower typical and mean spin wall time, lower P95 spin rank buckets and substantially fewer ≥1/2ms spin intervals, at equivalent effective throughput.
- Repeated cost: worse final deadline-error P95/P99 and ≥100µs frequency, plus all three paired HUD P99 increases beyond0.1ms. Baseline variation does not erase the consistent direction or the separation in observed run ranges.
- Mixed or unresolved: exact spin P99 improvement, signed park wake tails, rare reanchors/late entries, >2T clusters, whole-process CPU, and all unmeasured render CPU/allocation/power effects.
- Stability: normal exit and complete evidence in every process, no measured focus/target/segment transition, no >50ms HUD interval. Run2 remains excluded for wrong threshold; no unfavorable valid result was replaced.

Comparable configuration, source snapshot, exact pose and FPS support this bounded comparison. Manual activation/bracketing, pre-freeze world-time differences, the repeat’s long temporal separation and unequal premeasurement residence, coarse large-value histogram buckets, small n=3 and uncontrolled boost/JIT/OS behavior limit causal generalization. The results fail the candidate acceptance screen; they do not justify an expanded experiment or an automatic policy.

## H. Recommendation

**Retain 500 µs; further PARK investigation justified by a specific measured issue.**

Keep the current default and manual PARK controls. The specific issue worth a separately approved investigation is the observed 250µs tradeoff: mean spin falls, but final deadline P99 shifts into 575–650µs buckets and HUD P99 repeatedly worsens by 0.119–0.272ms. A future scoped investigation could examine how the existing single park’s return behavior interacts with the shorter spin allowance on this Windows/Java configuration, accounting for manual timing and observer limitations. That is a hypothesis to investigate, not authorization to change the algorithm, collector, defaults or add experiments now.

No automatic-tuning design or implementation begins from this report. No default change, 120FPS confirmation, renderer/API migration or Launcher change is recommended by this phase. Await owner review before another phase.

**AURORA CLIENT HANDOFF: NOT YET REQUIRED** — this Client evidence phase proposes no technically established source fix. Any future implementation needs its own scoped Client task after investigation/review. Manual PARK, Sodium/OpenGL and current features remain intact; Vulkan compatibility remains separate.

## I. Preservation, verification and evidence

- Rehashed all 433 original tracked files and 742 pre-existing protected `run/` files after the last JVM: **zero changed or missing**. Original tracked-file set and HEAD match, all critical root docs/Gradle wrapper/Fabric and mixin metadata exist. No deletion/rename, unstaged tracked diff or staged diff.
- Modified pre-existing tracked files: none. Added report: `CPERF1B_PARK_BENCHMARK.md`. Local commits: none. Final status: `?? CPERF1B_PARK_BENCHMARK.md` only. No push, merge, tag, publication, release, reset, branch switch or cleanup.
- All original Client worlds/config/profiles, developer files, previous evidence, existing benchmark tools and Launcher fixtures remain unchanged. Disposable copies naturally saved game/config/profile state under this phase’s ignored root.
- New ignored evidence only under `run/cperf1b-preflight-20261017/` and `run/cperf1b-benchmark/`: input manifests/copies, run configuration, declared procedure, logs, screenshots,14 raw Overlay CSVs,14 process captures, primary/warmup exports, the retained empty setup export, invalid-run reason, offline calculations and preservation records. No original evidence was pruned.
- Launcher HEAD remains `4984010d5309e05ec923535bba066c4e7aa90a5b`, with the same pre-existing untracked items (`.zcode-icon-diag.py`, `.zcodeignore`, the Temp-evidence filename and performance-p0-2 `__pycache__`). No Launcher edit, build or application launch was performed.
- Existing offline Gradle development launches compiled/resolved the unchanged Client and all seven booted/loaded/saved/exited normally with exit0. This evidence-only phase does not claim a new production build, Rust checks, source test changes or a release test pass. Offline data checks and `git diff --check` passed; report whitespace checked separately because it is untracked.

Evidence root: `C:/Dev/Aurora-Client/run/cperf1b-benchmark/`.

| Record | Contents |
| --- | --- |
| `PROTOCOL.md` | Procedure/screens before data; declared run 2 correction and single repeat |
| `inputs.json`, `inputs-with-repeat.json` | Initial world/config/options/mod hashes and all seven input identities |
| `events.jsonl`, run boundary PNGs | Actual activation/stop/export and camera checkpoints, including setup corrections |
| `run-N/runtime.log`, `process.json`, `saved-camera.json` | Versions, explicit process identity, normal exit and exact pose |
| `run-N/game/debug/aurora-park/*.json` | Unchanged raw stopped exports; warmup and empty setup snapshots retained |
| `run-N/game/config/performanceoverlay/benchmarks/*.csv` | Raw Off then On frame data/footer; two files per attempted JVM |
| `run-N/off-resources/`, `on-resources/` | Existing sampler capture.json/process.csv with explicit PID and creation identity |
| `run-N/review.json`, `review-summary.json` | Offline arithmetic, population/CSV/pose/completion checks; invalid run retained |
| `final-configurations.json` | All final config hashes, non-playtime differences and relevant native/graphics options |
| `evidence-sha256.json`, `build-identity.json` | Original output/config hash manifest and compiled-class provenance |
| `final-preservation.json` | Original433+742 hash audit, unchanged HEAD/tracked set and Git diffs/status |

**C-PERF.1B COMPLETE — RETAIN BASELINE**
