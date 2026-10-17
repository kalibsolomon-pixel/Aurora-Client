# C-PERF.1A-V — Diagnostic observer validation (2026-10-17)

## Repository baseline

`C:\Dev\Aurora-Client`; origin
`https://github.com/kalibsolomon-pixel/Aurora-Client.git`; branch
`codex/unified-theme-pilot`; complete starting HEAD
`4045692dead7fb125b0b3f13380950ea54759e26`. Initial `git status --short`,
tracked diff and untracked inventory were empty. All three prior corrections/
instrumentation commits are present. AGENTS.md and ARCHITECTURE.md were read in
the preceding implementation; their current diagnostic/correction guidance and
the complete C-PERF.1A report and timing implementation were rechecked against
this unchanged checkout.

Relevant pre-existing ignored artifacts: `run/cperf1a-validation/` (source,
init script, runtime/build logs, two disposable games and diagnostic JSON).
New validation files live only under `run/cperf1av-validation/`. Before copying,
SHA-256 baselines covered all 432 tracked files and 539 existing ignored files
under `run/`, including original worlds/configs and the preceding harness.
The new disposable game copies the preceding disposable world/config/mod set;
the original instances, fixtures and prior artifacts are never opened or edited.

## Environment and predefined procedure

Verified hardware: Intel Core i7-14700F, 20 physical / 28 logical processors;
AMD RX 9070 XT, Windows driver 32.0.31041.1004; Windows 11 Home build 26300;
desktop 2560×1440 at 240 Hz. Java is the same Microsoft Java 21 runtime used in
C-PERF.1A. The run uses a fixed `-Xms2G -Xmx2G` heap and the same Minecraft
1.21.11 / Loader 0.19.2 / Fabric API 0.141.4+1.21.11 / Sodium
0.8.14+mc1.21.11 production mod stack, plus the disposable observer mod.

Runtime selected **2560×1440 fullscreen at 144 Hz**, rather than the desktop's
240 Hz. That mode is held throughout; VSync is off, so a 240 FPS callback study
is still possible, but it is not a 240 Hz display-presentation study. Java is
Microsoft **21.0.7+6-LTS**, heap maximum **2147483648 bytes**; OpenGL is **3.3.0 Core
Profile Context 26.8.1.260810**. Render distance is 4, simulation distance 12,
graphics preset CUSTOM, ALL particles, FANCY clouds, four mipmap levels, FOV 70,
GUI scale Auto. Remaining copied graphics settings are retained in
`results/effective-options.txt`; exact runtime/JVM metadata is in
`results/environment.json`. No GC/priority/affinity/power-profile tuning.
A spectator camera stays at the copied pose, with the disposable
integrated server frozen once before warmup to hold scene simulation constant.
This controlled scene does not represent every dynamic gameplay workload.
PARK is always 500us spin / 2000us configured HYBRID park threshold, Low CPU off,
global-cap ownership, adaptive sleeping off and VSync forced off through the
existing feature. No production default or saved profile is changed.

Predeclared primary sequence: **Off → On → Off → On → On → Off**, six 30-second
samples at global 60/native 120. Warmup is 60 seconds (last 20 with collection
on to warm the measured path); every sample has ten seconds settling with no
export pending. Fresh local `/aurorapark start` sessions begin enabled windows;
CPU/frame samples end before stop/snapshot/export. Off windows remain disabled
and do not request snapshots. Command-registration JVM opt-in stays enabled
throughout. Exports must finish before the next settling period begins.

Conditional single additional block, declared before data: if every primary
window is valid, repeat the same six-window sequence at global 240/native
Unlimited (260), after 30 additional seconds settling. This checks collection
frequency, with the same PARK thresholds. No FPS matrix or further repeat is
authorized by this procedure. Failure to sustain a target is reported, never
compensated by changing graphics, priority, affinity or thresholds.

The existing HUD callback / `System.nanoTime()` observer uses a bounded primitive
interval array. Timing is **internal HUD callbacks**, not swap/presentation.
`ThreadMXBean` provides cumulative render-thread CPU and allocated bytes;
`OperatingSystemMXBean` supplies cumulative process CPU and approximate contextual
system CPU load; standard GC MXBeans supply collection-count/time deltas. CPU
percentages for the render thread and process use **one logical core** as 100%;
process machine-normalized percentages divide by 28. Counter reads bracket the
same approximately 30-second windows; command feedback, formatting, sorting and
file writes are outside those windows. Observer checks run identically at client
tick rate in both states; native input-received notifications prevent AFK without
moving the player.

Predefined exclusions: focus loss; camera/player-pose, world, screen/overlay,
pause, target, strategy, settings, framebuffer/fullscreen or collection-state
changes; exports during sampling; invalid CPU counters; saturated callback
storage; Reflex failure; renderer errors; inability to sustain the requested
callback rate within 3%. GC and unfavorable outliers alone are **not** excluded.
Enabled snapshots must retain segment 1, zero context changes/discarded samples,
correct owner/PARK/target/focus and consistent histogram populations.

Run command:

```
.\gradlew.bat -I run/cperf1av-validation/observer.init.gradle runClient --console=plain
```

A premeasurement launch was closed normally before any sample to align observer
counter/log boundaries; its startup log is retained as `runtime.log`. Measurement
uses `runtime-study.log`. No collected window was discarded in that preparation.

## Results and analysis

Both blocks completed: **12/12 valid windows**, no excluded measurement windows,
no extra repeat, normal client/server shutdown (`BUILD SUCCESSFUL`, 9m58s including
startup). CPU/allocation counters were available and positive. Window lengths
were 30.000–30.050 seconds because completion is checked on client ticks. The
desktop returned to 2560×1440 at 240 Hz after the game closed.

Intervals below are median / P95 / P99, in milliseconds. CPU columns are percent
of **one logical core**, including the process column. Allocation is render-thread
bytes per HUD callback. GC shows aggregate MXBean collection count / reported
collection milliseconds, including concurrent collectors; it is **not** a sum of
stop-the-world pause durations. Every sampled window is retained, including GC
and long-tail outliers.

| Cap / order | Collection | Callbacks | FPS | Median / P95 / P99 ms | Max ms | Render CPU % | Process CPU % | Bytes/callback | GC count/ms | Validity |
|---|---|---:|---:|---|---:|---:|---:|---:|---:|---|
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

Counter totals and outliers (T is the requested cap period, 16.67ms or 4.17ms):

| Cap / order | Render CPU seconds | Process CPU seconds | Render allocated MiB | Intervals >1.25T / >2T |
|---|---:|---:|---:|---:|
| 60 / 1 | 1.922 | 6.047 | 310.22 | 0 / 0 |
| 60 / 2 | 1.531 | 5.016 | 306.45 | 0 / 0 |
| 60 / 3 | 1.625 | 4.891 | 305.81 | 2 / 0 |
| 60 / 4 | 1.609 | 4.531 | 305.63 | 1 / 0 |
| 60 / 5 | 1.453 | 4.141 | 305.74 | 0 / 0 |
| 60 / 6 | 1.594 | 3.672 | 305.91 | 0 / 0 |
| 240 / 1 | 10.281 | 20.906 | 1160.95 | 6 / 1 |
| 240 / 2 | 8.875 | 14.438 | 1158.66 | 4 / 1 |
| 240 / 3 | 6.891 | 13.609 | 1156.07 | 2 / 1 |
| 240 / 4 | 12.422 | 22.281 | 1158.92 | 5 / 1 |
| 240 / 5 | 10.672 | 15.281 | 1156.82 | 7 / 1 |
| 240 / 6 | 9.813 | 15.703 | 1156.14 | 7 / 1 |

No sampled interval exceeded 50ms. At 240 FPS the On/Off totals are 16/15
intervals above 1.25T and 3/3 above 2T. System CPU context averaged 3.27/3.43%
of the machine for On/Off at 60 and 5.26/5.04% at 240; process machine percentages
are the table's one-core process values divided by 28. These aggregate contextual
values do not isolate collector CPU. Exact counter totals, GC collectors, wall
durations and every recorded interval are retained in the per-window JSON files.

### Paired comparisons

At 60 FPS, Off/On mean render CPU is **5.71/5.10%**, an On-minus-Off difference
of **-0.61 percentage points (-10.7% relative)**. Adjacent comparisons pair On 2
with Off 1, On 4 with Off 3, and On 5 with Off 6: **-1.31, -0.05, -0.47 pp**.
Off-to-Off range is **1.10 pp**; On-to-On range **0.52 pp**. These negative
differences do not establish a causal CPU saving from instrumentation. Process
CPU falls over run order from 20.16 to 12.22% of a core, demonstrating time drift.

Mean callback-rate difference is **-0.01 FPS (-0.016%)**. The mean of per-window
P99 values increases **24us (+0.14%)**, while individual paired P99 differences
are **+83, -38, +26us**, smaller than the **159us** Off-to-Off P99 range.
Allocation changes **-866 bytes/callback (-0.48%)**; paired changes are -2397,
-101 and -100 bytes/callback, against a 2768-byte Off-to-Off range. The data
show no repeatable positive allocation or material frame-time penalty.

At **240 FPS**, Off/On mean render CPU is **29.93/35.50%**. The observed
On-minus-Off difference is **+5.57 pp (+18.6% relative)**, not a negligible
difference if it were causal. However, adjacent pairs are **-4.68, +18.47,
+2.92 pp**, while Off-to-Off variation is **11.28 pp** and On-to-On variation
**11.87 pp**. The study cannot attribute that mean increase to instrumentation,
nor rule out a material positive cost. It is explicitly **not** evidence of
zero overhead. Process CPU changes +2.05 pp (+3.7% relative), with paired
differences -21.52, +28.98 and -1.32 pp, also too inconsistent for attribution.

At 240, callback-rate difference is **-0.014 FPS (-0.006%)**; all windows sustain
the target within 0.04%. Median interval changes less than 1us, mean per-window
P95 by -9us (-0.20%) and P99 by **-22us (-0.47%)**. Paired P99 differences are
**+33, -51, -47us**, compared with the **58us** Off-to-Off range. Maxima and
outlier counts do not show consistent deterioration when enabled. Allocation
changes **+255 bytes/callback (+0.15%)**, with paired values **-310, +696,
+380 bytes**, within the **710-byte** Off-to-Off range. This does not establish
a collector allocation source or a repeatable material allocation penalty.

### Diagnostic integrity and timing distributions

All six On sessions report **FRAME_HEAD_GLOBAL / PARK**, the intended effective
target, the 500us spin allowance and 2000us configured HYBRID threshold, focus
true, **segment 1**, zero condition changes and zero discarded invocations.
Histogram counts equal their documented populations; buckets sum correctly,
nonempty min/rounded-mean/max are ordered, duration values are nonnegative,
and signed error values remain signed. Real asynchronous snapshot exports all
succeeded. No export ran within a sampled interval. Snapshot differences of a
callback at window boundaries are expected: frame-head waits and late HUD
callbacks bracket different points of the same frame.

Means below come from each individual session, never pooled across FPS targets.
Deadline P99 is the collector's **approximate bucket [lower, upper)**, not an
exact percentile. Wait/spin values are elapsed wall time, not CPU time.

| Cap / order | Invocations / parks | Requested park ms | Actual park ms | Wake error us | Spin us | Total wait ms | Deadline P99 bucket us | Late entries / late reanchors |
|---|---:|---:|---:|---:|---:|---:|---|---:|
| 60 / 2 | 1803 / 1803 | 15.104 | 14.650 | -453 | 962 | 15.613 | [250,275) | 0 / 0 |
| 60 / 4 | 1803 / 1803 | 15.125 | 14.697 | -428 | 940 | 15.637 | [350,375) | 0 / 0 |
| 60 / 5 | 1803 / 1803 | 15.190 | 14.778 | -412 | 920 | 15.699 | [300,325) | 0 / 0 |
| 240 / 2 | 7208 / 7208 | 3.018 | 2.554 | -464 | 980 | 3.534 | [400,425) | 0 / 1 |
| 240 / 4 | 7197 / 7196 | 3.020 | 2.549 | -471 | 984 | 3.533 | [375,400) | 1 / 1 |
| 240 / 5 | 7197 / 7197 | 3.020 | 2.541 | -479 | 991 | 3.533 | [375,400) | 0 / 1 |

Early returns explain how a spin wall interval can exceed the configured tail;
the table does not imply more active CPU time or a PARK defect. At 240, On
spin means span only **11us** and wake-error means **15us**, much steadier than
render-thread CPU. This is useful evidence for direct timing measurements,
not a proof that changing an allowance will save a corresponding amount of CPU.

## Assessment, sensitivity and limitations

| Question | Classification | Meaning |
|---|---|---|
| Render-thread/process CPU attribution | **Inconclusive because of noise or insufficient data** | No stable positive cost at 60; the large positive 240 mean cannot be attributed or dismissed. A safe small CPU-overhead bound was not established. |
| Effective callback rate and frame-time consistency | **No material effect detected within measurement resolution** | Comparable rates, mixed small percentile differences and similar tails; this is not proof of zero overhead. |
| Allocation | **No material effect detected within measurement resolution** | Small mixed differences within natural variation; total Minecraft allocation is not collector allocation. |
| Diagnostic validity and direct timing repeatability | **No material effect detected within measurement resolution** | Stable context, valid populations and repeatable 240 spin/wake means. On/Off comparisons of internal wait errors are unavailable because Off does not collect them. |

The theoretical illustration of a 250us reduction at 240 FPS is at most
**60ms/s, or 6% of one logical core**, if every frame spins the full reduction.
It is not a predicted saving. The observed **11.28 pp Off-to-Off** variation
exceeds that illustration; smaller real improvements could be completely hidden.
These few samples cannot support a precise minimum detectable CPU effect or
confidence bound. CPU deltas occurred in 15.625ms multiples (about 0.052 pp over
30 seconds); observed variation is much larger than that accounting granularity.
No further CPU-counter calibration or causal explanation is claimed.

Limitations: one machine/driver and frozen spectator scene; three windows per
condition/target; a single balanced order per target, not independent launches;
uncontrolled thread migration, CPU power/boost state and background activity;
JVM compilation, allocation and GC variability; tick-level validity checks may
miss sub-tick transients. The identical external callback/tick observers have
their own overhead in both conditions. Process/system counters include unrelated
work; internal collector wall time includes preemption and clock-read cost.
The prior synthetic collector-only result is **not** used as proof of complete
instrumentation cost.

There is no display-presentation or input-to-photon measurement. Fullscreen
144 Hz differs from the desktop's 240 Hz. No subjective input assessment,
multiplayer, broad VSync/driver matrix or sustained GPU-bound Reflex study was
performed. The Reflex failure latch stayed clear; the runtime log contains no
new renderer error or export failure. Existing Loom/Sodium AMD workaround and
development warnings remain; a startup PerfOS registry-type warning is retained
without repair. This study uses Java MXBean cumulative CPU counters, not a
Perfmon chart; no CPU counter was unavailable, negative or reset during a window.
No measured window was invalidated or selectively removed.

## Recommendation

**2. Proceed with C-PERF.1B, but restrict CPU conclusions.**

The unchanged collector is suitable for a **timing-focused**, controlled follow-up:
keep diagnostics On for every compared configuration, use the same owner/target,
start fresh sessions, and compare signed wake errors, spin wall time and deadline
distributions across balanced repeats. Keep an Off control to detect observer
regressions. Do not infer active CPU time from spin wall time, report a CPU-saving
percentage, or claim a small CPU improvement from this evidence. CPU metrics
remain contextual until separately stabilized/validated. The 240 positive mean
must remain visible in any interpretation; it has not been explained away.

There is no demonstrated diagnostic correctness or frame-scheduling defect that
justifies a source correction in this validation-only phase. The bounded allowance
has been exhausted; no more environment tuning or repeat is proposed here.
**C-PERF.1B has not begun and requires owner authorization.**

## Preservation and evidence

Only this report is an expected new tracked artifact. No existing source, test,
documentation, config, profile, dependency, world or fixture is changed. PARK
500/2000 defaults and algorithms, native/global/GUI ownership, HYBRID Low CPU,
manual strategies, VSync policy, Reflex and input/tick/event ordering remain
byte-identical to the starting commit. No additional guidance edit is required
because no feature or architecture changed. No code commit is created.

Local ignored evidence is retained in `run/cperf1av-validation/`: `baseline.json`,
the derived observer source/init/resources, disposable game, `runtime.log`
(premeasurement preparation), `runtime-study.log`, `results/environment.json`,
`results/effective-options.txt`, twelve `results/window-<cap>-<order>-<state>.json`
files with raw intervals/counters/snapshots, the real exports in
`game/debug/aurora-park/`, and `analysis-final.txt` / `summary.json`. The analysis
pairs (1,2), (3,4), (6,5) within each target and reports unweighted window means
and ranges; it performs no significance test or synthetic overhead substitution.

Final checks: all **432 original tracked hashes** and **539 protected ignored
hashes match**; all twelve result files and six real snapshot exports validated.
`git diff --check` passed, and `git diff --name-status` showed no existing-file
changes or deletions. Before staging, status contained only this new report.
The optional local documentation commit contains this file alone; its SHA and
final clean status are provided at delivery. No push, merge, tag, publication,
release, cleanup or branch change. No new production test/build claim is made:
the derived observer compiled and ran successfully, and the implementation is
byte-identical to the previously tested C-PERF.1A commit.
