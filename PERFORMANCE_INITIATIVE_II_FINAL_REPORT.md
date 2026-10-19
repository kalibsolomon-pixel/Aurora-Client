# Aurora Performance Initiative II — Realistic Workloads, Startup, and Latency

2026-10-19 · Windows investigation · local development changes only

## A. Executive summary

Two changes are accepted for review:

1. **Client:** RegionCache uses Minecraft's bundled primitive-key linked map.
   Actual-class Java 21 measurements reduce a hot lookup from 19.88–19.98 ns and
   21 allocated bytes to 6.31–6.64 ns and zero bytes. Existing access-order
   eviction, pixels, stamps and cleanup remain intact. This is a component
   allocation improvement; six matched gameplay processes do **not** establish
   a whole-game FPS improvement.
2. **Launcher:** initialization waits for a superseding state reload before
   selecting the instance and checking its runtime. Three reference boots never
   request readiness; three candidate boots reach Ready automatically. Required
   integrity, runtime, account and final spawn validation remain authoritative.

The expanded worldgen/optimization workload, traversal, entity/block-entity
scene, GUI activity and a 10.5-minute native session completed. PARK/500 µs,
bounded automatic Reflex, G1 and 2048 MiB remain the tested reference. No preset,
renderer, input ordering or system setting change was justified. PresentMon and
actual swap calls improve the timing evidence, but physical input-to-photon
latency remains unmeasured.

Machine-readable results, per-run distributions, attribution and raw-artifact
hashes are in [results.json](docs/performance-initiative-ii/results.json).
Reproduction and validity rules are in
[INITIATIVE_II.md](tools/performance/INITIATIVE_II.md). Raw captures stay private.

## B. Realistic workload results

### Environment and population

Windows 11 Home; i7-14700F, 28 logical processors; approximately 32 GiB RAM;
Radeon RX 9070 XT, Windows driver 32.0.31041.1004. Native OpenGL identifies AMD
26.8.1.260810. Minecraft 1.21.11, Microsoft Java 21.0.7+6, Fabric API
0.141.4+1.21.11, Sodium 0.8.14. Development Loader is 0.19.2; installed native
Loader is 0.19.5. Normal classes compile for Java 21.

The lightweight fixture copies the previous Cperf1a world: render/simulation
distance 8/8. The expanded fixture uses 16/8, minimap/world map, entity-health
and hitbox features, existing HUD features, plus these seven pinned jars:

| Mod | Version | Exact Modrinth version |
| --- | --- | --- |
| Lithium | 0.21.4 | Ow7wA0kG |
| FerriteCore | 8.2.0 | Ii0gP3D8 |
| ImmediatelyFast | 1.14.3+1.21.11 | 4EwhsTu7 |
| Entity Culling | 1.11.3 | CTabrwB1 |
| Terralith | 2.6.1 | vVdNToqE |
| Tectonic | 3.0.19 | 7olSYFxL |
| Lithostitched, required dependency | 1.7.2 | pLbQKCOo |

Downloads use official [Modrinth API](https://docs.modrinth.com/api/) metadata
and its CDN, verified against expected SHA-512. SHA-256s, versions and URLs are
committed in the aggregate manifest; full Fabric dependency metadata stays in
each private fixture. Existing Sodium/API assets are reused. This is a practical
expanded worldgen/optimization configuration, **not** a claim of coverage for
hundreds of content mods. The host is not under OS memory exhaustion.

Each fresh copied world gets a 49×49 stone platform, all **256 hopper** positions,
192 persistent sheep and 64 item stacks. Setup generates the footprint before
fill commands. Saved Anvil data independently confirms hopper positions and
Terralith biome palettes, including the last two matrix runs. Entity counts at
the stationary checkpoint are 212–215 after ordinary entity processing; intended
spawn commands are not substituted for observed population.

### Matched protocol and measurements

Six fresh Java processes run **reference/candidate/candidate/reference/reference/
candidate** (`aii-s1` through `aii-s6`). Worlds, profiles, options, mods, workload
jar, heap, Java and launch configuration are frozen. Only RegionCache production
behavior changes. Recompiled RegionTile differs only in debug line metadata;
disassembled code is identical. No build or other benchmark runs concurrently.

The framebuffer is 1280×720, VSync off, gameplay cap 120, GUI cap 30,
PARK/500 µs, G1/2048 MiB. After setup and 30 seconds warmup:

- **Entity scene:** 60 seconds stationary, fixed camera at 0.5,95,0.5.
- **Traversal:** 60 seconds, 20 controlled teleports every three seconds,
  ending at x=1216.5. The first teleport is x=0: displacement is 1,216 blocks,
  not 1,280 traveled. Chunk generation/loading is deliberately active.
- **GUI:** 60 seconds of Aurora settings opening/closing every ten seconds.

Scheduled windows are 60 seconds; first-to-last observed coverage is about
59.9–60 seconds. All six have normal exit, complete lifecycle, successful setup,
valid active/unpaused states and stable stationary poses. JFR execution/allocation
sampling is identical across this cohort. Frame-loop heads are internal software
observations, not displayed FPS. The frozen `swap_ns` marker precedes
Window.updateDisplay and is labeled `preUpdateDisplayIntervals` in final results.

| Run | Variant | Entity FPS / P99 ms | Traversal FPS / P99 ms | GUI FPS / P99 ms | >50 ms intervals, phases 1/2/3 |
| --- | --- | --- | --- | --- | --- |
| 1 | Reference | 119.694 / 8.737 | 116.853 / 16.429 | 74.458 / 33.362 | 0 / 5 / 3 |
| 2 | Candidate | 119.636 / 8.690 | 116.953 / 17.298 | 74.622 / 33.373 | 0 / 3 / 0 |
| 3 | Candidate | 119.563 / 8.586 | 117.285 / 16.709 | 74.637 / 33.391 | 0 / 1 / 1 |
| 4 | Reference | 119.643 / 8.646 | 117.038 / 16.262 | 74.485 / 33.349 | 0 / 2 / 1 |
| 5 | Reference | 119.577 / 8.596 | 116.596 / 18.248 | 74.347 / 33.477 | 0 / 0 / 3 |
| 6 | Candidate | 119.726 / 8.690 | 116.684 / 17.243 | 74.450 / 33.422 | 0 / 5 / 1 |

Median frame interval is approximately 8.333 ms throughout. Reference/candidate
median P95 is 8.340/8.342 ms in the entity scene, 8.663/8.681 in traversal and
33.334/33.334 in GUI. The mixed GUI rate around 74 FPS reflects alternating
120/30 caps. Candidate traversal P99 median is about 4.95% higher, but lies
inside reference variation (16.262–18.248 ms); this does not meet the predeclared
repeated >5% harm rule. No result meets the >=5% whole-game gain rule beyond
reference spread. The small process count does not support a tight confidence
interval merely because it contains thousands of correlated frames.

Traversal has 0–5 long frames per run and 48.8–80.7 ms maximum frame-loop
intervals. Long-frame clustering and >100 ms counts remain in the JSON; none of
these six has a >100 ms frame-loop interval. JFR's leading execution samples are
Minecraft noise/biome/density generation and collections. This is a worldgen
load, not evidence that Aurora causes those stalls. Whole-process traversal CPU
is 658–693% of one core (about 23.5–24.7% of this 28-thread machine); stationary
CPU is 80–91%. Peak sampled process RSS is approximately 2.43–2.53 GiB.
These include JVM, server, rendering and observer work, not render-thread CPU.

The lightweight valid baseline (`aii-base-05`) reaches 119.985 internal FPS,
P99 8.760 ms, maximum 16.645 ms and zero >50 ms intervals. It is a workload
reference, not a matched Aurora-off control or proof of the candidate's benefit.

### Sustained native integration

`aurora-p0-2-i/game-1` runs the expanded scene through the real Launcher for
630 seconds after setup: warmup, active phases, then 420 seconds settled at the
traversal endpoint. It exits normally, saves/disconnects, and Launcher returns
Ready. All recorded states and stationary poses are valid. Native GC-only JFR,
Loader, release-remapped classes and saved window/GUI settings differ from the
matrix: actual framebuffer is **2560×1415**, and menus retain a 120 cap. Therefore
it is a separate sustained/integration diagnostic, not another comparison run.
The fixture helper now copies options explicitly; historical output is preserved.

Observed native stationary population is 196; late settled population is one
after leaving the crowded scene. Settled FPS is 119.994, P99 8.656 ms, maximum
16.624 ms, with zero >50 ms intervals. Per-minute P99 ranges 8.528–8.736 ms;
there is no large progressive degradation. Used heap cycles through 637–1161
MiB, committed heap stays at 1229 MiB, and settled sampled RSS spans
2149.8–2152.8 MiB. This is bounded evidence against growth during this seven-minute
settled window, not a multi-hour leak test or a long crowded-scene acceptance.
Native GUI transitions still have five >50 ms intervals, two >100 ms, maximum
147.3 ms; those remain visible despite stable late gameplay.

A separate 60-second read-only capture of the same native process family has
nine stable members, 30.89% one-core CPU / **1.103% machine CPU**, and summed
RSS 2618.9–2619.2 MiB. Summed RSS double-counts shared pages; it is not unique
physical memory. Maximum family sampling read time is 2.274 ms; its uncalibrated
observer effect is not subtracted.

## C. Mod-side optimizations

Allocation samples repeatedly identify boxed `Long` keys beneath RegionCache.peek
in the minimap's per-texel lookups. Reference weighted samples are 385.1, 568.1
and 282.1 MB across whole recordings; candidates have zero matching samples.
Sample weights are estimates and include startup/setup/save. They corroborate
the actual-class diagnostic rather than establish exact saved bytes in gameplay.

The accepted change uses the already bundled
`Long2ObjectLinkedOpenHashMap<RegionTile>`. `getAndMoveToLast(long)` preserves
the old access-ordered map's behavior for acquire **and peek**. The old peek
comment incorrectly promised no LRU update; the implementation did update it.
No persistent key format, color, tile image, snapshot/stamp, capacity or eviction
listener contract changes. No dependency is added and no world object is retained.

Three fresh processes per implementation, 500,000 warmup / 2,000,000 measured
calls, fixed 256 MiB heap and a volatile result sink use the actual RegionCache
class and NativeImage tiles. ThreadMXBean reports **21 → 0 bytes/call**;
timings are **19.901/19.881/19.982 → 6.641/6.311/6.470 ns/call**. This component
measurement is not extrapolated to FPS. Two new tests exercise eviction order,
misses, cleanup, zero/negative/extreme packed keys, image pixels and stamps.

JFR also points to minimap ring/entity primitives and downstream vanilla GUI
allocations; capture hillshading and world lines appear in Aurora execution
stacks. These are leads, not all verified defects. A renderer/cache redesign
would need independent visual and invalidation evidence. Better Hitreg's timing
core, glass renderer, Frame Pacer and Reflex remain untouched.

## D. JVM and memory findings

G1/2048 MiB handles this workload without OOM, persistent heap saturation or
failed normal shutdown. Matrix used-heap maxima reach 1553 MiB reference and
1487 MiB candidate; maximum committed values reach 1678 and 1603 MiB. The
differences are not accepted as a global memory win. Whole-recording GC pause
totals overlap at 4.06–4.20 seconds; maximum pauses are 26.36–31.56 ms.
Native GC-only recording has 2.442 seconds total pause and 29.406 ms maximum
over the longer, differently observed session. Collector names G1New/G1Old do
not by themselves identify a stop-the-world full collection.

JFR weighted allocation estimates are about 911–919 MiB/s across matrix whole
recordings, dominated by worldgen/setup and downstream allocations. These are
statistical weighted rates over the observed event span, not exact allocation
accounting or steady render allocation rates. No JFR DataLoss is present.

There is no credible need here to reduce the heap, change collector, or enlarge
every preset. Keep the previous G1/2048 reference for this tested configuration.
Efficient/Balanced/Performance are not changed. Large content packs, low-RAM hosts
and other hardware require their own sizing evidence. ZGC, rejected re-parking
and 250 µs experiments were not reopened without new evidence.

## E. Startup measurements

Instrumentation separates process creation, native setup, frontend initialization,
state/accounts restoration, initial Home paint, restored usable Home, runtime
validation, Ready, Play, child spawn, Minecraft constructor, Aurora initialization,
menu, world-open request and first interactive world frame. Native setup is
about 4–5 ms in the final native runs. JVM/Fabric work before Minecraft's
constructor is measured as one segment; it is not incorrectly assigned to Aurora.

Native `Instant` and child `nanoTime` have separate Windows QPC brackets. Native
origin bracket is 2 µs; child brackets are 44–53 µs. Frontend performance.now
is retained in its own domain; cross-process tables use native IPC receipt
timestamps and QPC, not direct subtraction of unrelated clocks. Child creation
is bounded by the authoritative command.spawn span. RAF markers are scheduling
checkpoints after state, not scanout or proof of a physical visible pixel.

All times below start after process creation returned. Add the recorded spawn
call bracket when measuring from creation-call entry. These are fresh processes
with warm OS caches/shared isolated WebView storage; there is no controlled cold
boot claim. Native captures use an unoptimized debug Rust executable embedding
production frontend assets in research mode, not a signed release binary.

| Launcher cohort | Initial Home paint | First automatic Ready | Interpretation |
| --- | --- | --- | --- |
| Reference b1/b2/b3 | 343.698 / 323.038 / 331.543 ms | absent in all three | no runtime/readiness request, observed beyond 30s |
| Candidate c1/c2/c3 | 323.752 / 330.552 / 326.884 ms | 29.777 / 11.386 / 10.915 s | all reach Ready without manual recheck |
| Final native n2/n3/n4 | 346.141 / 345.292 / 338.083 ms | 11.822 / 11.352 / 11.788 s | expanded saved instance; repeated automatic Ready |

The **restored usable Home** marker was added after c1–c3; it is absent there,
not inferred from paint. Final n2/n3/n4 mark it at **779.180 / 781.226 / 781.953
ms**, after native state and account discovery complete. First native-owned
visible top-level window is observed around 22–43 ms with 20 ms polling; it may
be a helper/blank window, so it is not a usable-Home timestamp. c1's spawn call
bracket is about 295 ms versus about 3 ms later, another reason not to invent
a precise startup percentage from these small populations.

Native n2 Play receipt to spawn is bounded at **10.331–10.354 s**, with a
23.065 ms spawn bracket. After child creation, Minecraft constructor start is
9.446–9.469 s, end 13.695–13.718 s, renderable menu **19.235–19.258 s**,
world-open request 22.268–22.291 s, and interactive world **27.787–27.811 s**.
Aurora initialization itself takes **208.862 ms**. Menu-to-world includes the
driver's deliberate 3-second dwell; world-open to interactive is **5.519 s**.
The first native child-window observer started late: its 41.2-second observation
is an upper bound only and is excluded as a window-creation result.

The successful warm relaunch n4 has a 5.436 ms spawn bracket, Play-to-spawn
11.042–11.048 s, first owned game window observed at about **9.038 s** after
spawn return (50 ms polling plus observer scheduling), and menu at
**15.813–15.818 s** after child creation. Aurora initialization is 174.549 ms.
This is descriptive restart evidence, not a controlled startup optimization in
Minecraft. Required validation remains the large pre-spawn cost.

## F. Startup optimizations

Update discovery can issue a newer launcher-state read while initialization's
first read is pending. Freshness correctly rejects the first result, but startup
previously looked up selection immediately, found none, and permanently omitted
runtime/readiness. The store now tracks the latest reload promise and waits for
the winning read before selection. Regression tests cover both completion orders;
the original fails the regression, and the fixed startup suite passes all 28 tests.

Reference boots have zero runtime/readiness requests, while candidate boots
perform the authoritative checks and reach Ready. The accepted outcome is
**restored automatic readiness**, not an invented speedup from a censored baseline.
First candidate validation is slower (runtime status 23.655 s, game integrity
20.094 s); later required integrity checks are around 3.7–3.8 s and readiness
around 5.3 s. Unsafe mtime-only trust caching is not introduced. No Minecraft
initialization change is justified by the measured Aurora segment.

## G. Presentation and latency

A separate `aii-input` diagnostic uses actual GLFW swap-call entry/return and QPC,
with PresentMon 2.3.1 targeting the exact Java PID/creation identity. Portable
binary SHA-256 is `364e5d98d4d134bd54dd25c22ed2ca2f4883f8bc3ed6502bee0c151e3436d30c`;
Intel Authenticode is valid. No elevation, driver installation, timer, affinity
or power setting change occurs. See the upstream
[PresentMon project](https://github.com/GameTechDev/PresentMon) for the tool.

The 55.989-second common interior trims exactly two seconds at each end and
contains **6717 actual swap calls and 6717 PresentMon rows**, one OpenGL/Other
stream, `Composed: Copy with GPU GDI`. Session-specific loss queries at 1, 23
and 79 seconds return zero loss; the last covers the interior end. They do not
certify shutdown-tail loss. Nearest association has only **6716 unique matches**,
maximum discrepancy **3.130 ms**, failing the predeclared unique-within-2-ms rule.
Therefore counts support consistent submission cadence but not certified
presentation completeness or physical delivery.

PresentMon event intervals: median **8.345 ms**, P95 **9.006**, P99 **9.303**,
maximum **22.932**, zero >50 ms. Actual swap-call intervals independently give
median 8.341, P95 8.958, P99 9.212 and maximum 22.783 ms. These channels remain
separate from the main workload's earlier frame-loop/updateDisplay markers.
6714 OS display estimates are available, three missing; estimated display delay
ranges **4.995–14.762 ms**. These describe the OS graphics path, not observed
scanout or photons. All input-to-photon fields are unavailable.

Three automated drags produce only **six callbacks / six paired frames**. The
last callback consumed per frame is paired through accumulated mouse movement
return, camera setup return and actual GLFW swap entry. Callback-to-swap values
are **8.314, 7.308, 8.648, 6.974, 8.898, 8.519 ms**. Callback-to-mouse-return is
6.22–7.80 ms; mouse-return to camera is about 4.6–10.7 µs; camera to swap is
0.72–1.35 ms. This is a small descriptive software sample with uncalibrated
automation/tracing overhead. It does not measure a hardware switch, continuous
motion, scanout or visible response. Do not add software stages to OS estimates
and call the sum input-to-photon. No attributable latency defect justifies
changing input, pacing or Reflex based on these observations.

## H. Rejected experiments

- A whole-game FPS gain and global GC/RSS improvement for RegionCache are
  rejected as unsupported: macro distributions and GC totals overlap.
- The first six micro captures accidentally selected the same implementation;
  they are retained as ineligible `micro-1`…`micro-6`. Corrected classpath-asserted
  captures are `micro-valid-1`…`micro-valid-6`.
- The exploratory `aii-pack-screen` completes but its fill commands fail on
  unloaded chunks. It is ineligible as a complex block-entity benchmark. Eager
  footprint generation and failed-command rejection fix the research driver.
- Calibration `aii-base-01`…`04` retain reserved Mixin helper-package failure,
  onboarding/profile assumptions, constructor injector static requirements and
  missing normal disconnect notification causing a save hang. The latter has
  a preserved stack/partial JFR. These are driver failures, not Aurora regressions.
- Expanding JFR stack JSON exceeds a bounded 60-second attempt; streaming the
  JDK recording avoids the huge intermediate output. Raw recording is preserved.
- PresentMon's full unique event association fails; no certified displayed-frame
  or physical-latency claim is promoted. More aggressive tracing is not attempted.
- G1/1536, ZGC sweeps, bounded re-parking and 250 µs are not rerun merely to
  revisit prior rejected/ineligible results. No new workload need justifies them.

## I. Workarounds and technical debt

| Problem | Reversible workaround and limitation | Preferred permanent solution |
| --- | --- | --- |
| Loom runDir rejects the initial absolute-path assumption | Resolve a repository-relative path into a new marked Temp fixture | Keep explicit path checks in future orchestration |
| Real native launch with window dimensions rejects `${resolution_width}` | Preserve failed n1; set disposable native window config to null, retain saved options; no validation bypass | Bind validated width/height placeholders and test real metadata's conditional argument group |
| Native fixture initially omitted copying options | Actual sustained resolution/GUI settings recorded; native diagnostic kept separate; helper now copies options | Assert actual framebuffer and GUI cap before any future matched native matrix |
| Runtime diagnostic is java.exe but actual launch is javaw.exe | Resolve `launchExecutable` from the installed runtime manifest; retain failed observer attempts; n2 window observation remains late | Start manifest-driven exact-child observer before Play, as in n3/n4 |
| Native UI accessibility click has no coordinate geometry | Freshly select/activate the window, observe screenshot and click one visible target | Maintain supported Computer Use fallback; never guess a stale index |
| A later n3 revalidation briefly shows Needs attention | Preserve observation; automatic focus refresh recovers, no manual trust bypass; trace proves initial Ready at 11.352 s | Preserve diagnostics and examine reproducibility before calling this a verified product defect |

Fixture fixes are research infrastructure, not silent changes to original worlds
or official manifests. Initial failed launch n1 has no game child; it is excluded
from gameplay acceptance. No hidden production workaround weakens readiness.

## J. Repository status

Both repositories start tracked-clean on `codex/performance-initiative`:
Client `071993b192d6c2e0389fee3c461b7ad51bc21731`, Launcher
`ecf5ea8f23ffd7e0c36ac79a6020a0a92dd46ce7`. Work is on
**`codex/performance-initiative-ii`** in both. Tested changes are committed
locally; exact resulting commit IDs are reported with delivery and available in
`git log -1`. No push, remote rewrite, release publication or manifest-authority
change is performed. Remotes remain unchanged.

Production scope is one Client cache class and Launcher state coordination;
other source edits are opt-in numeric probes, two cache tests, two startup tests,
research helpers and documentation. No unexpected deletion is staged. Baseline
SHA-256 inventories cover **5073 Client files / 3,934,123,918 bytes** and
**2629 Launcher files / 731,719,665 bytes**. Final audits show zero missing
originals and zero protected changes. Only intended tracked source/docs differ.

Existing untracked Client park-investigation notes and Launcher developer files,
diagnostic marker and pre-existing Python caches remain excluded and unchanged.
Private baselines, source/class snapshots, logs, binaries, micro results and
matrix manifests remain under each repository's `.git/performance-initiative-ii`.
Raw runs are in OS Temp `aii-base-*`, `aii-pack-screen`, `aii-s1`…`s6`,
`aii-input`, `aurora-p0-2-ii/{b*,c*}`, and `aurora-p0-2-i/{n*,game-*}`.
No raw account state, credentials, browser profile, arguments or game logs are
committed. Export only reviewed aggregates when sharing evidence.

## K. Release readiness

Ready for code review with bounded Windows acceptance:

- Client full test/build: **550 passing tests**, explicitly repeated with managed
  Java 21; four offline capture-quality tests pass. Normal jar contains no research
  classes and compiles to Java 21. Existing sources-jar policy is preserved.
- Launcher frontend: type/style check **zero errors/warnings**, **284 tests pass**.
  Rust format/check pass; **997 native tests pass**, 31 existing ignored.
  Production frontend and ordinary nonresearch native builds succeed; final
  research native build boots repeatedly. Existing build warnings are retained.
- Native installed expanded instance: required runtime selected, 2048 MiB saved
  configuration, G1 observed, pacing cap active, complex world loads, gameplay
  runs, world disconnects and game exits **0**. Launcher returns Ready.
- Restarted Launcher reaches Ready; exact child is terminated with **23 at the
  main menu**, before world-open, and the native UI reports unsuccessful exit
  while returning Ready. Another fresh Launcher launches successfully to menu
  and exits **0**. All parents exit normally. **171 saved-world files match
  hashes after both menu-only cycles.** No process-name termination is used.

This does not authorize a release. Signed/optimized shipping startup, other
hardware, Linux runtime acceptance and optical latency remain outside this
evidence. Windows-specific tooling/QPC code is platform-gated; Linux behavior is
preserved in source but not claimed tested. Native custom-resolution resolution
still needs its own fix and review.

## L. Remaining future investigations

Only unresolved work remains: larger gameplay/content packs and low-RAM machines;
multi-hour crowded-scene/cache accumulation; measured minimap primitive batching
with pixel/invalidation checks; exact native custom-resolution placeholder repair;
signed optimized cold/warm startup with controlled cache state; Linux acceptance;
and a hardware/optical input-to-visible-response setup or stronger independently
validated presentation association. None changes the accepted claims above.
