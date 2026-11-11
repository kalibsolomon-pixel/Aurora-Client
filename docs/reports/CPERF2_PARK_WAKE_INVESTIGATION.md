# C-PERF.2 — PARK wake behavior and scheduling architecture investigation

Date: 2026-10-17 (America/New_York). Investigation only; no implementation or new gameplay measurements.

**Recommendation B: one separately authorized, developer-only bounded re-parking prototype is justified. Keep production PARK unchanged at 500 µs.** Test whether one conservative retry after a substantially early return can reduce excess spin wall time without the precision loss seen when shortening the allowance. This is a falsifiable experiment, not an established optimization or CPU-saving claim. Wake compensation and automatic allowance tuning are not justified by these data.

## A. Repository baseline and preservation inventory

| Item | Verified state |
| --- | --- |
| Root | `C:/Dev/Aurora-Client` |
| Origin fetch and push | `https://github.com/kalibsolomon-pixel/Aurora-Client.git` |
| Branch | `codex/unified-theme-pilot` |
| Full HEAD | `ab86f03e419d0656e84bfc748e21313d22886436` |
| Initial tracked changes / index | None / empty |
| Initial untracked file | `CPERF1B_PARK_BENCHMARK.md` only; protected |
| Ignored roots reported by Git | `.gradle/`, `build/`, `logs/`, `run/` |
| Protection baseline | SHA-256 and size for 433 tracked files, the untracked C-PERF.1B report, 2,145 files under `run/`, and 14 under `logs/`: 2,593 files total |
| Other generated inventory | Size and modification-time inventory for 1,137 files under `.gradle/` and `build/`; separately verified 415 compiled-class hashes against C-PERF.1B |

The protection inventory was held in the investigation session, not written over prior evidence. Relevant ignored `run/` contents were:

| Location | Existing file count |
| --- | ---: |
| `cperf1b-benchmark/` | 1,399 |
| `cperf1b-preflight-20261017/` | 4 |
| `cperf1a-validation/` | 361 |
| `cperf1av-validation/` | 203 |
| Original `saves/` | 131 |
| Original `config/` | 23 |
| `logs/` inside `run/` | 19 |
| Remaining run-root files, `data/`, `downloads/` | 5 |

Read the project guidance in `AGENTS.md` and `ARCHITECTURE.md`, including the current correction/diagnostic records; inspected the scheduler, both hooks, diagnostics/control, configuration, related tests and mapped game bytecode. Read all five prior performance reports: `CPERF1A_DIAGNOSTICS_VALIDATION.md`, `CPERF1A_OBSERVER_VALIDATION.md`, `CPERF1B_PARK_BENCHMARK.md`, `CPERF3_NATIVE_THROTTLE.md`, `CPERF3_VALIDATION.md`. Historical independent-anchor descriptions are superseded by C-PERF.3 and current source. No separate original C-PERF.1 report exists in the tracked inventory; this is the previously documented provenance qualification, not newly missing C-PERF.1B evidence.

Verified `cd3f50d`, `93c3c07`, `4045692`, and `ab86f03` are ancestors of HEAD. Current source retains effective native-limit precedence, one shared deadline/owner, HYBRID-only Low CPU, optional session diagnostics, and factory PARK/500 µs/2000 µs settings. The 2000 µs setting controls normal HYBRID's spin window, not a PARK retry threshold. Manual custom values are normalized for dispatch without rewriting saved configuration.

Protected C-PERF.1B report SHA-256: `58ae80c05af77c942b7944a3303afcca37fcb97587a93c23af93c36066b2f1bf`.

## B. Current source architecture

### Target selection and ownership

Primary source locations, with one-based starting lines:

| Source | Relevant entry |
| --- | --- |
| `src/client/java/com/aurora/client/mixin/MinecraftClientRenderMixin.java:28` | `runTick` HEAD; glass frame boundary, pacing, then optional accumulated mouse-delta application |
| `src/client/java/com/aurora/client/mixin/RenderSystemMixin.java:18` | HEAD interception of native `limitDisplayFPS`, after the game's display update/event pumping |
| `src/client/java/com/aurora/client/util/FramePacing.java:26` | Pure limit resolution |
| `src/client/java/com/aurora/client/util/FramePacing.java:48` | Head ownership and deadline dispatch |
| `src/client/java/com/aurora/client/util/FramePacing.java:65` | Post-swap recheck/cancellation |
| `src/client/java/com/aurora/client/util/FramePacing.java:88` | Shared anchored schedule |
| `src/client/java/com/aurora/client/util/FramePacer.java:55` | Original PARK wait |
| `src/client/java/com/aurora/client/util/FramePacer.java:70` | Diagnostic PARK wait |
| `src/client/java/com/aurora/client/util/FramePacer.java:130` | Strategy dispatch |

`customPacer` means `smoothFramePacer` and strategy other than VANILLA; null retains the PARK fallback. At frame head, the native input is the actual `FramerateLimitTracker` result. The post-swap hook receives Minecraft's effective incoming FPS. Both resolve the same applicable limits:

1. Native is finite only for `0 < fps < 260`. Native 260+ is Unlimited; zero/unavailable is inactive. The mapped native policy still supplies iconified 10, long AFK 10, short AFK `min(configured,30)`, and outside-world menu 60. Loss of focus alone is not a native finite cap.
2. GUI classification remains the `com.aurora.client.screen` class prefix. A finite GUI cap participates only on such screens while the native limiter is applicable; 1000 is GUI Unlimited. The historical GUI-only skip at native Unlimited remains.
3. Any positive global cap selects FRAME_HEAD at the minimum applicable target. It works even at native Unlimited and when custom pacing is disabled/VANILLA.
4. Otherwise, outside Aurora screens, custom pacing plus `lowLatencyRender && adaptiveRenderSleeping` selects FRAME_HEAD for a finite native target.
5. Otherwise an applicable custom/GUI limiter selects POST_SWAP. Remaining finite cases select NATIVE; unrestricted cases select NONE.

FRAME_HEAD is labeled GLOBAL or ADAPTIVE in diagnostics. A head-owned wait suppresses the corresponding post-swap native wait only when Aurora actually dispatched a target and the head FPS is at least as restrictive as the current FPS. A newly tighter restriction after event pumping falls through to native this frame and is adopted next frame. Arming/reanchoring also leaves native fallback available. For POST_SWAP, cancellation occurs only when its own nonzero target is returned. There is no independent second Aurora deadline.

The mapped 1.21.11 `Minecraft.runTick` calls native `limitDisplayFPS` only below 260. `RenderSystem.flipFrame` pumps events before and after swap. Native `limitDisplayFPS` itself loops with `glfwWaitEventsTimeout`; Aurora's PARK does not pump events or execute client tasks inside its wait. VSync, GPU work, Reflex and normal frame processing retain their existing locations outside this collector.

### Anchoring and wait pseudocode

```text
decision = resolve(effectiveNative, global, applicableGUI, strategy, adaptive)
if (owner, fps) differs from scheduled:
    nextTarget = 0

at the hook owning the schedule:
    T = 1 / fps
    if nextTarget == 0 OR now > nextTarget + T/2:
        record initial anchor OR late reanchor, if collecting PARK
        nextTarget = now + T
        dispatch no Aurora wait on this frame
    else:
        D = nextTarget
        nextTarget += T          // anchored advance, not exitTime + T
        dispatch configured strategy (or fixed HYBRID fallback)

PARK(D, configuredSpin):
    S = max(50, configuredSpin) microseconds  // dispatcher normalization
    e = GLFW time
    remaining = D - e
    if remaining <= 0:
        return                 // measured path records late entry
    request = truncate((remaining - S) * 1e9)
    if request > 0:
        LockSupport.parkNanos(request)        // exactly one call
    while GLFW time < D:
        Thread.onSpinWait()
    return
```

Time in deadline calculations is GLFW seconds; the conversion above treats S in seconds. The code increments `nextTarget` before waiting. Slight lateness up to the half-period recovery limit still dispatches a past deadline, which PARK immediately returns from. Reanchoring requires **strictly greater** than `nextTarget + T/2`; equality does not reanchor. At 240 FPS, T is 4.166667 ms and the recovery tolerance is about 2.083333 ms.

Owner/FPS changes, including intervals of NONE/NATIVE, invalidate the prior schedule. Merely changing the spin setting, diagnostic state, or focus without changing the resolved owner/FPS does not independently reanchor `FramePacing`; diagnostics may nevertheless begin a new segment. Disabling adaptive does not necessarily remove a global or ordinary post-swap owner.

There is no PARK call when the scheduler arms/reanchors, chooses NATIVE/NONE, or dispatches another strategy/fixed HYBRID fallback. A dispatched PARK returns immediately if its deadline expired at entry; if the computed request is nonpositive, it skips parking and may spin. If time is consumed before the first spin check, that loop is skipped too. A late park return is followed by that same deadline check; no compensating sleep or corrective frame reschedule occurs inside PARK.

### Why the configured allowance is not a maximum

After `parkNanos` returns, `waitUntilPark` goes directly to its final loop at lines 64–66. It never rechecks whether it is still far outside the intended spin window. The measured counterpart does the same using `spinStart` at line 84 and its final condition read. An early return can therefore leave more than S before D, all of which becomes spin wall time. Preemption during that interval can extend measured spin wall time further. Neither method bounds active CPU consumption to S or caps spin wall duration at S.

In the measured path, two `System.nanoTime()` reads bracket only the park call. GLFW supplies entry, first spin check, and exit. Spin wall time runs from the first spin check to the final failed condition read if the body was entered; it is zero otherwise. Exit/deadline timestamps precede collector aggregation and method return. Diagnostic overhead after that timestamp can affect later frame work without appearing in that invocation's deadline error.

Existing tests inspected: `FramePacingTest` covers the ownership/limit/transition matrix and late recovery; `FramePacerLowCpuTest` pins the original whole PARK/SPIN method hashes and HYBRID policy; `FramePacingPersistenceTest` covers defaults and real config/profile persistence; `ParkDiagnosticsTest` covers timing arithmetic, early/late parks, skipped spin, segmentation, populations and source boundaries. This report does not claim a new execution of those tests or a fresh build.

## C. Evidence verification

### Independent checks performed on retained files

Offline calculations were run from stdin without invoking the retained preparation/review/report scripts, launching Minecraft, importing Launcher utilities, or writing analysis artifacts into evidence directories.

- All **130** entries in `evidence-sha256.json` match retained bytes and sizes. All **415** compiled classes match `build-identity.json`.
- All **153** source-world files match `inputs-with-repeat.json`; compact inventory JSON reproduces identity `84b036cc3b191a066befc7968c88850c393520e3a69d68b369d19066365a352a`. Initial-copy identity is a recorded premeasurement fact; final live world copies naturally differ after loading/saving.
- Each `run-N/review.json` equals its entry in `review-summary.json`. Independently parsed all **14** raw Overlay CSVs: positive finite intervals, monotonic elapsed fields, matching row/footer counts, AUTO_DURATION termination and approximately 60-second endpoints. Recomputed FPS, type-7 median/P95/P99, maxima and long-interval counts; results reproduce the reviews.
- Recomputed CPU from endpoint differences in all **14** raw `process.csv` files. Each capture is complete with 121 samples and matching explicit PID/creation identity. This verifies arithmetic, not CPU attribution.
- Checked all seven primary PARK JSON files: stopped, segment 1, FRAME_HEAD_GLOBAL/PARK/240/focused, 500 or 250 as actually used, zero transitions/discards. Recomputed histogram populations and percentile rank buckets; early plus late returns equals park calls, with no exact-zero wake errors in these primaries.
- Inspected all 13 exports, including five retained stopped warmups and run 7's zero-invocation setup export. Successful exported warmups exceed 60 seconds with correct spin settings. The report's event-based warmup durations and collector session durations differ by at most about 46 ms where both are available; this does not change validity or any primary statistic.
- Read procedure, invalid record, input/final configuration identities, event records and runtime logs. Final configs differ from run 1 only in spin and `playtimePerWorld`/`playtimeTotalMs`; native 260, VSync false, fullscreen, RD4/SD12 and resource packs agree. All copied mod hashes match their input manifests.
- Independently decoded saved `level.dat` NBT in all seven runs. Exact position/rotation match the source; saved Time/DayTime match the reviews. Visually inspected run 3 and run 4 before/after-primary F3 PNGs: same rounded XYZ, 137.0/23.0 aim, frozen server, native Unlimited and renderer. All retained screenshot files are covered by the evidence hash check; four were visually rechecked, not every screenshot.
- Logs establish normal game shutdown and BUILD SUCCESSFUL in all seven processes. Run 7's three ERROR lines belong to the documented premeasurement Win32 clipboard failure; they are not a newly discovered measured-window renderer failure. No adverse valid primary was discarded.

### Run inventory

| Run | Requested / actual allowance µs | Primary invocations / parks | Primary PARK seconds | Primary HUD rows | Status |
| --- | --- | ---: | ---: | ---: | --- |
| 1 | 500 / 500 | 22641 / 22637 | 94.382 | 14404 | Valid reference |
| 2 | 250 / 500 | 21053 / 21051 | 87.773 | 14393 | **Invalid candidate; excluded from comparisons** |
| 3 | 250 / 250 | 21495 / 21493 | 89.573 | 14398 | Valid candidate |
| 4 | 500 / 500 | 15897 / 15897 | 66.296 | 14392 | Valid reference |
| 5 | 500 / 500 | 16235 / 16235 | 67.696 | 14403 | Valid reference |
| 6 | 250 / 250 | 17289 / 17288 | 72.083 | 14406 | Valid candidate |
| 7 | 250 / 250 | 18628 / 18627 | 77.685 | 14387 | Valid replacement for run 2 |

Run 2's active profile restored 500 despite requested 250 in its copied config. `run-2/invalid.json` and the procedure amendment explicitly retain/exclude it and authorize the single replacement. Valid pairs remain **1→7, 4→3, 5→6**, reference→candidate; the first has weaker temporal adjacency. Run 6's setup toggle was restored before world entry/measurement. Run 7's failed warmup activation and longer residence time remain disclosed.

All comparisons below use only 1/4/5 versus 3/6/7. Statistics are medians of run statistics where stated, not pooled frame/sample percentiles. PARK sessions bracket the 60-second HUD windows and CPU captures start slightly later; these are not sample-identical streams.

### Reproduced central results

| Statistic | 500 µs run-statistic median | 250 µs run-statistic median | Interpretation |
| --- | ---: | ---: | --- |
| Effective HUD FPS | 239.878 | 239.924 | Comparable throughput |
| Mean requested park µs | 2919.692 | 3082.664 | +162.972; not the full configured 250 µs difference across different frames/runs |
| Mean actual park µs | 2239.438 | 2539.199 | +299.761 |
| Mean signed wake error µs | -680.254 | -543.125 | Less early on average; not uniformly better tails |
| Mean spin wall µs | 1196.357 | 829.576 | -366.781, or -30.66% |
| Mean total wait wall µs | 3436.291 | 3369.222 | Different entry/work distributions; not a faster target cadence |
| HUD P95 ms | 4.455 | 4.581 | +0.126 |
| HUD P99 ms | 4.682 | 4.861 | +0.179 |
| Mean deadline error µs | 14.146 | 36.733 | Worse |
| Deadline ≥100 µs late | 4.361% | 9.081% | +4.720 percentage points |
| Spin wall ≥2 ms | 12.782% | 2.244% | Lower; CPU remains unmeasured |
| Late reanchors /10k invocations | 2.464 | 1.735 | No consistent paired regression |

The median requested, actual and wake means come from independently ranked runs; subtracting group medians need not reproduce a median difference. Within a run the actual-minus-requested mean agrees with wake mean to rounding.

### Return proportions and error tails

All intervals below are approximate rank buckets in µs, `[lower,upper)`. Percentages of early/late parks use **park calls**; deadline percentages and reanchor rates use **invocations**.

| Run / allowance | Early / late park % | Wake P95 | Wake P99 | Deadline P95 | Deadline P99 | Deadline ≥100 µs % | Late reanchors /10k |
| --- | --- | --- | --- | --- | --- | ---: | ---: |
| 1 / 500 | 89.261 / 10.739 | [200,225) | [700,725) | [0,25) | [200,225) | 1.515 | 1.325 |
| 4 / 500 | 82.022 / 17.978 | [550,575) | [900,925) | [75,100) | [400,425) | 4.913 | 2.516 |
| 5 / 500 | 81.762 / 18.238 | [525,550) | [875,900) | [50,75) | [375,400) | 4.361 | 2.464 |
| 7 / 250 | 79.884 / 20.116 | [575,600) | [850,875) | [325,350) | [625,650) | 8.890 | 2.147 |
| 3 / 250 | 79.528 / 20.472 | [525,550) | [825,850) | [275,300) | [575,600) | 9.081 | 0.465 |
| 6 / 250 | 78.528 / 21.472 | [600,625) | [875,900) | [350,375) | [625,650) | 10.643 | 1.735 |

Wake P99 worsens for 1→7, improves for 4→3 and occupies the same bucket for 5→6. Wake P95 is similarly mixed. In contrast, final deadline P95/P99 and ≥100 µs frequency worsen in every pair. A hypothesis that all candidate park-error quantiles uniformly worsened is unsupported.

| Run / allowance | Spin mean µs | Spin P50 | Spin P95 | Spin P99 | Spin ≥2 ms % | HUD P95 / P99 ms |
| --- | ---: | --- | --- | --- | ---: | --- |
| 1 / 500 | 1334.044 | [1350,1375) | [2000,4000) | [2000,4000) | 15.874 | 4.404 / 4.601 |
| 4 / 500 | 1196.357 | [1175,1200) | [2000,4000) | [2000,4000) | 12.782 | 4.479 / 4.682 |
| 5 / 500 | 1148.768 | [1125,1150) | [2000,4000) | [2000,4000) | 10.330 | 4.455 / 4.733 |
| 7 / 250 | 829.576 | [825,850) | [1850,1875) | [2000,4000) | 2.367 | 4.581 / 4.873 |
| 3 / 250 | 743.670 | [725,750) | [1625,1650) | [1975,2000) | 0.921 | 4.496 / 4.861 |
| 6 / 250 | 835.608 | [850,875) | [1875,1900) | [2000,4000) | 2.244 | 4.597 / 4.852 |

Paired HUD P99 increases reproduce **+0.27200, +0.17924, +0.11900 ms**, all beyond the declared 0.1 ms screen. >1.25-period HUD counts increase 7→15, 11→41, 7→15. >2-period counts/clusters are mixed; no >50 ms HUD interval occurs in any Off/On file. Baseline/candidate maximum spin wall intervals reach 29.108/24.537 ms; maxima do not establish which mechanism caused a stall. Reanchor counts are only 1–4 per valid session; late-entry counts are 0–2. Initial-anchor counts of zero reflect collection starting after the schedule was already established.

### Resolution and observer limits

The six histograms have 178 buckets: 25 µs spacing centrally from -2 to +2 ms, progressively broader tails, including `[2,4)` and `[4,8)` ms, plus unbounded ends. Rank selection uses `ceil(N*p/100)`. Requested/actual park P95/P99 mostly occupy the broad `[2000,4000)` µs bucket; actual park P99 reaches `[4000,8000)` for runs 6/7. Exact percentile differences cannot be recovered. In particular, common spin P99 buckets do not establish equal P99 or an exact improvement. Deadline `[0,25)` combines exact zero with sub-25 µs lateness; ≥100 µs counts can be computed exactly at that bucket edge, but exact positive-deadline frequency cannot.

C-PERF.1A-V found no material callback/allocation effect within its measurement resolution, but its 240 FPS On-minus-Off mean render CPU difference was **+5.57 percentage points**, with paired differences -4.68/+18.47/+2.92 and Off range 11.28 points. This remains unresolved. C-PERF.1B provides no render-thread CPU/allocation counters; process CPU ranges and pair changes are inconsistent. The baseline process CPU range is 48.31–82.21% of one logical core, candidate 56.90–65.62%; the apparent lower median is not attributable savings. Spin wall time includes descheduling. No CPU, energy, power, presentation or input-to-photon conclusion follows.

Off controls also have higher candidate HUD P99 (4.836–4.926 versus 4.616–4.779 ms), supporting the timing observation beyond active PARK diagnostics. Fixed Off→On order, one control per process, manual bracketing, and common observer cost prevent a negligible-overhead claim. Other residuals include n=3 per condition, pre-freeze world-time/entity differences, run 7's temporal gap/residence, thread migration, JIT/GC, boost, background activity and unmeasured GPU/thermal behavior. Results apply to this Java 21/Windows/Sodium/OpenGL workload, not every system.

## D. Early-return investigation

### Observed facts

Negative wake error here is `System.nanoTime` elapsed around the call minus the exact long request. Early returns comprise 81.762–89.261% of baseline parks and 78.528–79.884% of candidate parks. This establishes calls returning before their requested elapsed duration, not why they returned. It is not an inference from mismatched GLFW/Java clock origins: those origins are never subtracted.

The current Aurora source checks neither `isInterrupted()` nor `Thread.interrupted()` around PARK, records no interrupt or permit state, does not classify return causes, retries no parks, applies no wake compensation and estimates no expected wake behavior. Diagnostics collect distributions but do not feed scheduling decisions. A search of `src/client` found no direct `LockSupport.unpark` or interrupt call targeting this path.

**There is nevertheless a concrete dependency unpark path.** Read-only disassembly of the locally mapped 1.21.11 jars establishes:

```text
Minecraft extends ReentrantBlockableEventLoop<Runnable>
ReentrantBlockableEventLoop extends BlockableEventLoop
Minecraft.getRunningThread() -> gameThread
BlockableEventLoop.execute(r), when scheduling is needed:
    wrap runnable -> schedule(r)
BlockableEventLoop.schedule(r):
    pendingRunnables.add(r)
    LockSupport.unpark(getRunningThread())
```

In `schedule`, bytecode offset 15 calls `LockSupport.unpark`; `Minecraft` initializes `gameThread` from the current game thread. Thus cross-thread client task submission can signal the same thread that PARK uses. A signal/permit can already exist before the call, or arrive during it. Aurora's async diagnostic export completion also calls `source.getClient().execute`, although exports were outside primary Overlay windows and this does not establish them as the main cause. Other task producers were not traced to individual waits. Do not replace the source finding with the false claim that there are no known render-thread unpark paths.

Local bytecode identity (no jar modification): mapped client-only jar SHA-256 `5d1c49e6c49cecef6b2a3636395d192fff431af5d1c4834d9e70e32d46abfcd9`; common jar `f860bd5fe92ddc445e74ff5b5df703496be3d6180e520ca398fa52f246abb2ca`. Both are under the existing Gradle Fabric Loom `minecraftMaven/net/minecraft/` cache, version `1.21.11-loom.mappings.1_21_11.layered+hash.2198-v2`.

### Supported hypotheses and unverified possibilities

| Mechanism | Evidence classification | What can and cannot be concluded |
| --- | --- | --- |
| Available permit / another thread unparks game thread | **Supported hypothesis**, with a source-established path | Client task scheduling can cause early return. These exports contain no task/unpark timestamps or permit state; its share is unknown. |
| Runtime timeout conversion and Windows timer phase | **Supported hypothesis**, implementation evidence below | Requests near integer-ms boundaries could change effective waits discontinuously. No retained OS timeout result or exact vendor-native execution trace identifies individual returns. |
| Interrupt already set or arriving while blocked | **Unverified possibility** for these runs | Permitted by the runtime contract; no interrupt-state samples. Substantial actual parked means make a continuously set interrupt throughout a whole run an inadequate sole explanation, but do not exclude intermittent interrupts. |
| Spurious return | **Unverified possibility** for these runs | Allowed; not a synonym for every negative wake error. Cannot count it by subtracting any known subset here. |
| Preemption, safepoints, dispatch delay | **Supported explanation for possible positive/tail contributions**, unattributed | Can lengthen park elapsed, final spin wall time and deadline error. Ordinary preemption alone does not make a requested timeout finish early. |
| Clock/read overhead and precision | **Known measurement limitation**, unquantified | Surrounding timestamp reads generally add elapsed cost; they do not alone explain a systematic large negative mean. Clock resolution/calibration and small deltas remain unmeasured. |
| JVM defect | **Unsupported** | No direct evidence; ordinary documented behavior and known paths suffice as alternatives. |

[Java 21 LockSupport documentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/locks/LockSupport.html#parkNanos(long)) specifies permit consumption, interruption, timeout and spurious returns, and provides no return-cause value. Code must recheck its condition. Interruption does not make the current Aurora final spin process work or exit early; it still checks the absolute deadline.

For platform threads, the matching upstream [OpenJDK 21.0.7+6 LockSupport source](https://github.com/openjdk/jdk21u/blob/jdk-21.0.7%2B6/src/java.base/share/classes/java/util/concurrent/locks/LockSupport.java#L392) dispatches relative parking through Unsafe. Its [Windows Parker implementation](https://github.com/openjdk/jdk21u/blob/jdk-21.0.7%2B6/src/hotspot/os/windows/os_windows.cpp#L5288) divides nanoseconds by 1,000,000, substitutes 1 ms if that becomes zero, checks interruption/event state and then uses `WaitForSingleObject`. For example, 2.92 ms maps to 2 ms and 3.08 ms to 3 ms in that implementation. This offers a specific nonlinear mechanism consistent with the request means straddling 3 ms. The means do not reveal how many requests crossed that boundary. The benchmark used Microsoft 21.0.7+6; its native binary was not proven byte-equivalent to this upstream source. This is an implementation-supported hypothesis, not proof of the benchmark's cause or a JVM bug.

[Microsoft's wait documentation](https://learn.microsoft.com/en-us/windows/win32/api/synchapi/nf-synchapi-waitforsingleobject) describes integer-millisecond timeout/timer-resolution effects, including waits shorter than a clock tick and variation between ticks. The machine's effective timer resolution and timer phase were not captured. Windows alone is not a causal diagnosis. FramePacer's historical 15.625 ms / near-zero-CPU prose is not a measured explanation of these PARK results.

[Java's nanoTime contract](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#nanoTime()) provides elapsed-time measurement with nanosecond units, without promising nanosecond resolution. The collector avoids mixed-origin subtraction, but does not calibrate clocks or observe native wake readiness separately from when Java resumes.

The render thread cannot service queued client work while in Aurora's park/spin body. Being unparked does not execute the queued Runnable inside that body. This matters to any retry design: preserve task/input/event order, and do not turn a pacing experiment into task processing or event pumping inside a wait. Existing records cannot distinguish timeout conversion, timer phase, actual unpark, interrupt, spurious return or combinations on a per-call basis.

## E. Why 250 µs reduced spin but worsened precision

Use a conceptual single elapsed-time domain to explain the arithmetic, without numerically mixing collector clock origins. Let entry be e, deadline D, planned allowance S, park start p, park return q, first spin check s, and exit x. For a positive park request Q, integer truncation residual ε is less than 1 ns:

```text
Q = D - e - S - ε
A = q - p
W = A - Q                              // signed wake error
δ = (p - e) + (s - q)                   // work outside the bracketed park
R = D - s = S + ε - W - δ              // signed remaining before final spin
E = x - D                              // final deadline error
spin wall = R + E, when spin entered   // otherwise recorded as zero
```

Negative W can increase R far above S. A positive W can be absorbed while it remains smaller than the available reserve; once the park consumes that reserve, a final spin cannot recover the missed deadline. Illustratively, an otherwise identical +600 µs wake error exceeds a 500 µs reserve by about 100 µs and a 250 µs reserve by about 350 µs, before overhead. This is arithmetic illustration, not a paired measured event or a percentile calculation.

At identical entry/deadline, lowering S by 250 µs requests 250 µs more parking. Across actual runs, entries/work differ, so mean request increases only about 163 µs by the group-median comparison. Actual park rises about 300 µs, wake means become less negative, and spin means fall about 367 µs. Early returns still leave typical spinning above both nominal allowances. Shorter reserve can worsen final deadline error even if a wake-error quantile improves slightly: the tolerated error threshold has also moved.

| Proposed explanation | Assessment from existing evidence |
| --- | --- |
| Longer requests expose more late-wake variability | Requests and late-return proportions rise in each pair; plausible. Signed wake P95/P99 are mixed, so no uniform worsening of the primitive is established. Longer duration alone is not proven causal. |
| Smaller allowance absorbs less variability | Directly supported by control flow and arithmetic; repeated worse deadline tails are consistent. This does not identify the OS/runtime mechanism producing W. |
| Early returns obscure the nominal spin threshold | Directly supported: most parks return early and measured means/P50 exceed S. The threshold is a planned reserve, not a time cap. |
| More of the remaining interval is parked, increasing deadline misses | Supported as the observed condition-level association plus the source mechanism. There is no per-frame causal decomposition. |
| Preemption or unrelated work contributes | Plausible and uncontrolled. Preemption during the wait can enter its error; work after measured exit affects HUD intervals outside it. GC totals cannot attribute individual spikes. |

Anchored deadlines help explain equivalent average FPS despite worse tails: modest misses do not replace the next deadline with `exit + T`. Later intervals can be shorter. The absence of a consistent reanchor increase also argues against treating this as throughput collapse. HUD callback positions additionally include game/render work after the head wait, so HUD P99 is not a direct park-error percentile.

**The existing instrumentation cannot recover frame-by-frame correlations.** JSON retains separate marginal histograms, extrema, means and counts, not joint tuples, frame IDs or the distribution of R. Raw HUD CSVs do not carry matching PARK identifiers. Even where the measured method temporarily knows `target - spinStart`, that quantity is not exported. Subtracting histogram quantiles, matching samples by rank, or combining different session windows would invent relationships. Thus exact wake→remaining→spin→deadline→HUD attribution cannot be obtained without future instrumentation. It is unnecessary to assert one cause to test a bounded retry against direct end results.

## F. Candidate comparison

The following are qualitative predictions and risks, not measured gains from unbuilt algorithms.

| Criterion | Existing PARK / 500 µs | Bounded re-park | Wake compensation | Adaptive allowance |
| --- | --- | --- | --- | --- |
| Potential spin reduction | No new reduction; measured reference | Plausible for substantially early returns; unknown magnitude | Plausible but may increase spinning to protect late tails | Plausible; no validated feedback policy |
| Deadline precision risk | Known host/workload behavior; better than tested 250 | Additional oversleep opportunity; fixed reserve cannot guarantee safety | Biased/stale estimate can under-reserve; conservative estimate costs spin | Feedback can trade away precision or chase noise |
| Implementation complexity | Lowest; unchanged | Low to moderate: one retry, guard, explicit measurements | Moderate: estimator, bounds, reset and ageing | Highest: estimator/controller, hysteresis and transition handling |
| Measurement requirements | Existing timing metrics suffice for reference | Calls per wait, remaining time, total parked time and final return error | Chosen margin, estimator state/age, changes and subsequent misses | Effective allowance, updates, dwell/reset state and misses |
| Compatibility risk | Known Java21/OpenGL/Sodium evidence, limited matrix | Extra use of shared park permit; interruptions; changed wait subdivision | Platform/workload-dependent timing; no universal offset | FPS/focus/owner/VSync/workload transitions and oscillation |
| Rollback simplicity | None needed | Remove startup opt-in; original path retained | Easy only with isolated original path | Requires clean reset/bypass and original path |
| Evidence strength | Direct six-run comparison supports retaining default | Strong evidence of excess wall spin; benefit and safety untested | Marginals support variability, not a calibrated predictor | Two fixed allowances do not establish a control law |

**Candidate 1, reference single park:** simple, no estimator or retry loop, absolute deadlines, current native correctness, manually selectable. It is stable in the retained runs and beats tested 250 on the declared precision screen. It still spins after early returns and cannot avoid park oversleep/preemption. Source comments do not establish low actual CPU cost.

**Candidate 2, bounded re-parking:** can give back a portion of an unexpectedly long residual interval while retaining the user's reserve. Recheck the same deadline, use a minimum useful duration, limit both retry count and request, and preserve interruption status. A second call can oversleep, return immediately again, consume a shared permit, or save too little to pay its overhead. A fixed one-retry design has no learned state or feedback oscillation, though eligibility can vary from frame to frame. It need not make the 250 µs tradeoff again.

**Candidate 3, wake compensation:** if explored later, positive lateness risk rather than the negative mean would have to inform a conservative safety margin. Cancelling early-return means by parking longer risks the observed positive tail. Existing unordered data cannot assess serial predictability, estimator lag, response time or a useful optimal quantile. A bounded, slowly updated estimator would need conservative startup, ageing and resets after workload/owner/FPS/focus changes. Abrupt changes can make it stale. No CPU-specific offsets or prototype are recommended.

**Candidate 4, adaptive allowance:** deadline error is an outcome, not an unambiguous park predictor; late entry, preemption and earlier work can influence it. A controller might keep 500-like precision while reducing some spinning, but these data do not demonstrate that possibility. Strict bounds, slow changes, hysteresis/dwell, fallback and resets would be necessary. Absolute time margins interact with FPS periods, and the evidence supplies only 240 FPS. Focus/owner changes and Unlimited transitions cannot share stale state. No automatic tuning is justified here.

**Candidate 5, no algorithmic change:** fully defensible for production and the disposition until owner review. Choosing it permanently would avoid experiment cost/risk, but would leave a concrete source-level opportunity unexplored. The reason to permit one experiment is observed excess spin after a single early return, not confidence in a root-cause theory or demonstrated CPU savings.

## G. Diagnostic sufficiency and smallest future additions

Nothing was instrumented or changed in this phase.

| Question | Current collector sufficiency |
| --- | --- |
| First requested/actual park and signed error | Yes: exactly one call, so all current park samples are first and final |
| Early/late return proportions | Yes, by elapsed/request comparison; no return-cause classification |
| Remaining time immediately after park / before spin | Known transiently at the first GLFW condition, not exported as a distribution |
| Final spin and total wait wall duration | Yes; no thread-CPU inference |
| Deadline error / late entry / reanchors | Yes, with documented timestamps and separate counters |
| Scheduling owner / effective target / focus | Yes, observed segment metadata; no continuous raw-input certificate |
| Multiple calls / total park time / final-call error | No separate semantics for multiple calls; changing code alone would invalidate current one-call assumptions |
| Adaptive margin / estimator / margin updates | Absent; would require explicit state/change measurements for such a different experiment |

For the selected **at-most-one-retry** experiment, minimum additions proposed for a separately reviewed schema are:

1. Explicit experimental-path identity, fixed retry limits and minimum/cap in the snapshot; condition segregation. Keep original schema-1 meanings available. Do not silently redefine its request/actual/wake histograms or mix strategies.
2. Primitive counts for invocations with 0/1/2 park calls (derivable from first-park/retry counts), total calls, eligible retries, retries taken, retries skipped for observed interruption, and residual eligibility after exhausting the single retry. The last is a retry-limit event, not authorization for a third call. Preserve existing first-call distributions under explicit first-call labels and distinguish first calls from total calls.
3. Four bounded additional distributions: total park elapsed per invocation (sum of call-bracket durations, not intervening code); final call's signed wake error per invocation that parks; signed remaining time after the first return; signed remaining at the final spin check. First/final remaining coincide when no retry occurs. A primitive retry-request min/max is sufficient to audit the fixed request bound; no separate retry request/actual histograms or raw trace are needed initially.

These identify whether a retry happened, whether useful residual time existed, whether parking increased and spinning fell, and whether the last return exceeded its request/reserve. They do not prove why a call woke or recover full per-frame joint correlations. Existing final deadline/HUD distributions remain the acceptance measures. Detailed task/unpark tracing, OS profiling, thread CPU instrumentation, estimator telemetry, frame IDs and a general profiler are not prerequisites for this narrowly defined outcome test.

Use preallocated storage and primitive updates. Reuse the first GLFW spin-condition timestamp to decide retry; a retry needs its own final GLFW check and two Java bracket reads while collecting, with no added clock reads inside the final spin loop. Both diagnostic-Off and On experimental paths must make the same scheduling decisions. Added diagnostic cost must be treated as unvalidated; comparable On collection and Off controls are needed, and CPU restrictions persist.

## H. Recommendation and future experiment, requiring separate authorization

### One candidate: single capped re-park with the existing reserve

The measured problem is frequent early returns followed by longer-than-planned spin: at 500 µs, **10.330–15.874%** of invocations spend at least 2 ms in measured spin wall time. This provides a narrower target than lowering every frame's reserve. It is not an estimate of eligible retries or available CPU savings; long measured spin can include preemption.

Proposed reviewable experiment:

- Keep the first park calculation/call identical and keep `S = max(50, configuredSpin)` and the absolute deadline D. Use 500 µs for the comparison; do not rewrite manual values/defaults.
- After the first park, use a fresh/reused GLFW condition read. Only consider a retry if the return precedes `D - S` and at least **S + 1 ms** remains before D. No retry after no first park or after a late/near-deadline return.
- At most **one** additional park; cap its request at **1 ms**, with a **1 ms minimum useful request**. These equal bounds deliberately define a fixed 1 ms retry for this experiment. They are conservative experimental limits, not a calibrated platform guarantee or new user preset. The native timeout hypothesis motivates avoiding sub-millisecond retries, without claiming 1 ms is universally optimal.
- If interruption was observed before/after the first park or before the retry, skip retry and retain the existing final deadline spin. Use non-clearing checks; do not clear/restore the interrupt bit, process tasks, or exit before D. An interrupt can still race the call; the one-retry limit bounds repeated immediate returns.
- After that call, enter the same absolute-deadline final spin. If still substantially early, do not retry again; record limit exhaustion and spin as the reference does. There is no adaptive estimator, allowance adjustment, target shift or catch-up policy change.

The reserve remains **planned**, not guaranteed: even a nominal 1 ms retry can return late enough to consume it. A retry may also be futile if another permit is available. Queued tasks still run at the existing game-loop positions. The experiment adds no new unbounded retry/busy loop; the final loop remains the existing deadline-controlled spin and retains its wall-time/preemption limitations.

**Why this candidate:** it targets a source-established consequence of early returns while leaving the reserve that outperformed 250 intact, has a hard call-count bound and no state feedback, and can be disabled with a restart. It requires no assumption that negative wake means predict future positive tails. Its effectiveness can fail clearly. Compensation/adaptation add unvalidated prediction/control problems; a further uniform allowance reduction already has adverse evidence.

### Future scope and disable path

Potential changes would be limited to `FramePacer.java` experimental dispatch/wait and `ParkDiagnostics.java`/`ParkDiagnosticsControl.java` explicit experimental measurements, their tests, and the applicable architecture records if implemented. `FramePacing.java`, both hook placements, limiter resolution/cancellation and configuration/profile schema need no scheduling changes. Diagnostic context could label experimental dispatch through the existing observation adapter without adding hooks. No Launcher change is needed.

Use a separate startup-only developer property, absent/false by default and independent of diagnostic activation. Preserve both original diagnostic-Off PARK and its measured counterpart as the reference. The experimental path must behave consistently with collection Off/On. Removing the property and restarting restores the original path; no saved setting migration or ordinary UI option. Deterministic tests must prove the two-call maximum, request bounds, absolute deadline, interrupt preservation, zero/expired/short waits and repeated immediate/late returns. Existing ownership, native throttle, global/GUI, Unlimited, HYBRID, persistence and diagnostic tests remain required.

Do not alter Java21/Minecraft1.21.11/Fabric/Sodium/OpenGL compatibility, VSync/Reflex policy, input/tick/event ordering or manual strategy selection. No per-frame allocation, render-thread I/O, new thread, excessive synchronization, timer-resolution change, priority/affinity adjustment, power-plan change or hardware-specific preset is part of this design.

### Small future benchmark plan

**Not executed in C-PERF.2.** After separately authorized implementation and deterministic validation, compare original **PARK/500** with experimental **PARK/500**, using the C-PERF.1B scene, exact pose, copied original source-world snapshot, Java21, heap, graphics, mods, VSyncOff, global240/nativeUnlimited, adaptive/Reflex sleepingOff and stable focus. Preserve originals and all failed attempts. Verify effective exported settings after profile application before accepting any capture.

Use six fresh processes in reference/experiment/experiment/reference/reference/experiment order, three valid runs per condition, with identical at-least-60-second warmup and the existing 60-second Off control plus On primary. Pair temporally adjacent runs as in the original planned design; predeclare any single validity replacement before further measurement and disclose reduced adjacency. Hold workload and boundary operations comparable; record unavoidable pre-freeze time differences rather than correcting camera/workload mid-window. Do not expand FPS, driver or threshold sweeps. No export during sampling. Retain raw CSVs, JSON, CPU captures, identities, logs and camera evidence.

Proposed acceptance/rejection rules, to be fixed before data collection:

| Requirement | Practical decision rule |
| --- | --- |
| Valid comparison | Correct owner/target/500 µs/path, stable camera/focus, homogeneous segment, no discarded samples, equivalent settings/workload, complete evidence and consistent populations. A validity failure is not a performance success or failure. |
| Equivalent FPS | Paired effective callback-rate differences within approximately 1%, as in C-PERF.1B. |
| Spin reduction | Lower mean spin wall in all three valid pairs and a lower median of run means; P50/P95 buckets and ≥1/2 ms rates must support, not contradict, the interpretation. No promised percentage gain. |
| HUD precision | Apply the existing `max(0.1 ms, 2% of T)` = 0.1 ms screen to paired P95 and P99. A repeated breach rejects; one breach requires owner review, not extra opportunistic runs. |
| Deadline precision | Reject repeated disjoint worse P95/P99 buckets or repeated ≥100 µs late-rate increases beyond the concurrent reference run-to-run spread. A repeatable upward tail shift fails even with lower spinning or comparable FPS. Overlapping coarse buckets are inconclusive, not proof of equivalence. |
| Late recovery / stability | Compare late-entry and reanchor rates per 10k, not unequal raw session counts; no repeated increase beyond concurrent reference variation, new stall clusters or instability. Examine >1.25T/>2T/>50 ms and fixed-bin clusters without selecting maxima alone. |
| Retry behavior | At most two calls per invocation, requested retry within fixed bounds, no retry despite observed interruption, no premature exit. Confirm eligible/taken/limit counts and final-return/remaining distributions. Futile retries without repeatable spin reduction do not pass. |
| CPU conclusions | Remain restricted. Process CPU is contextual; reduced spin wall does not establish render CPU, power or energy savings. |

These are bounded practical screens, not statistical equivalence tests. With three runs and broad histogram tails, small effects can remain unresolved; such an outcome is not acceptance. Existing C-PERF.1B is historical reference context, while concurrent original-500 runs are the actual control. The rejected 250 µs condition is not the acceptance baseline.

Immediate rollback/stop triggers: broken native precedence or ownership, an early return from the final deadline wait, more than two park calls, cleared interrupt status, a new crash/hang/renderer or Reflex failure, repeatable new stall/reanchor clusters, or a material precision regression. A focus/camera/config mismatch invalidates the capture and stops the bounded procedure for review. Restore the original startup path; do not tune constants against unfavorable runs or silently increase the allowance to rescue the candidate.

After a successful outcome, unknowns would still include exact wake causes, active CPU/energy cost, broad platform/FPS/workload generalization, dynamic/multiplayer behavior, display presentation/input latency and rare-tail reliability. A successful prototype would justify further review, not default replacement or automatic rollout.

## I. Repository preservation and closing checks

- Pre-existing tracked files modified: **none**. Source, tests, build scripts, configuration, profiles, architecture/project guidance and Launcher remain unchanged.
- New artifact: **`CPERF2_PARK_WAKE_INVESTIGATION.md` only**. The pre-existing untracked C-PERF.1B report is preserved. No analysis files or new evidence directories were created in the repository.
- Deleted/renamed files: **none**. No cleanup, reset, rebase, merge or branch switch.
- Final preservation audit: all **2,593** initially hashed files unchanged and present; no `.gradle/`/`build/` inventory size/time changes; protected raw evidence and compiled-class identities retained. The final tracked path set, branch and HEAD match baseline.
- `git diff --check`, tracked `git diff --name-status` and staged name-status are empty/successful. The untracked report was also checked directly for trailing whitespace/newline and its scope reviewed.
- No source implementation, instrumentation, configuration change, gameplay benchmark, game launch, build/test execution, Launcher modification, commit, push, merge, tag, publication or release occurred in this phase. Only read-only source/bytecode inspection, offline retained-data calculations, preservation checks and this report were performed.

Final Git status:

```text
?? CPERF1B_PARK_BENCHMARK.md
?? CPERF2_PARK_WAKE_INVESTIGATION.md
```

Owner review is the next gate. Neither the prototype nor its benchmark is authorized by this report.

**C-PERF.2 COMPLETE — PROTOTYPE JUSTIFIED**
