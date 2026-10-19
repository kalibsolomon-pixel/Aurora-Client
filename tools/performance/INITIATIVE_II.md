# Initiative II research protocol

These tools are opt-in research infrastructure, outside ordinary game source
sets. See `PERFORMANCE_INITIATIVE_II_FINAL_REPORT.md` for decisions and limitations.
Never point them at an original instance. Preserve all captures, including failures.
Several orchestration scripts intentionally name the original local experiment
roots; choose **new** roots and inspect those scripts before another campaign.

## Preparation and execution

1. Record Git state and hash protected evidence with `initiative2_preserve.py`.
   It refuses an existing baseline. `initiative2_audit.py` compares it later.
2. `initiative2_prepare.py NEW_ROOT [--pack]` copies the prior disposable Cperf1a
   fixture and sets both config and profile fields. It pins Modrinth versions,
   validates SHA-512 downloads, records SHA-256 plus Fabric dependency metadata,
   and rejects existing destinations. `--pack-source ROOT` reuses verified jars.
3. Build an explicit named research source set using Gradle's
   `-I tools/performance/stress.init.gradle -PresearchRoot=NEW_ROOT
   -PresearchJava=JAVA21 writeResearchLaunch`. Use `researchKind=stress`,
   `researchSeconds=210`, `researchProfile=true` for the matched matrix.
   `researchRemapJar` produces a separate research mod for native installed Fabric.
   Normal `build` never includes research classes. Do not place the named jar in
   a production instance. Use the remapped jar there.
4. Freeze a reference class/resource copy **before** editing production source.
   Freeze workload source, config, worlds, mods and launch files before collection.
   `initiative2_matrix.py` shows A/B/B/A/A/B substitution and actual-class micro
   execution. It verifies the changed class set, disassembled inner-class code
   and reference classpath selection. Do not rebuild during timed captures.
5. `initiative2_run.py` starts exactly one generated private launch specification,
   retains that child identity, samples read-only process counters and preserves
   exit status. `initiative2_execute_matrix.py` runs fixtures sequentially.
6. Review with `initiative2_review.py ROOT...`. Failed commands, incomplete
   lifecycle, abnormal exits, unordered frames and short phases fail review.
   Any focus/pause mismatch remains visible. Do not silently discard slow frames.
   Scheduled phases are 60 seconds; first-to-last observed interval coverage is
   slightly shorter. The reviewer requires at least 59 seconds.
7. `initiative2_jfr.py ROOT --jfr JAVA21/bin/jfr.exe` uses the bounded streaming
   `JfrAttribution.java`, rejects JFR DataLoss, and exports aggregate statistics.
   Do not equate allocation sample weights with exact allocated bytes or CPU
   sample counts with thread CPU time. Recordings include setup and shutdown.
8. `initiative2_scene.py ROOT/game/saves/Cperf1a` independently reads saved Anvil
   chunks, checks all 256 hopper positions and confirms Terralith biome palettes.

## Definitions and controls

- Java 21, G1, 2048 MiB maximum, 1280x720 framebuffer, VSync off, 120 gameplay
  cap, PARK/500 microseconds, automatic bounded Reflex. Existing GUI cap is 30.
- 30-second post-setup warmup; stationary entity scene 30–90s; 20 teleports every
  three seconds 90–150s; GUI toggles every ten seconds 150–210s. The first
  teleport is x=0, final x=1216: a 1,216-block displacement, not 1,280 traveled.
- Native sustained test extends to 630s after setup, with 420s settled after
  active phases. Native run uses GC-only JFR; matrix uses execution/allocation
  profiling. These cohorts are not pooled.
- Heap/RSS/process CPU are separate. One-core CPU percentages may exceed 100;
  divide by 28 for this host's machine-normalized value. Summed family RSS
  double-counts shared pages. No low-RAM or whole-pack optimum claim follows.
- Minimum repeatable whole-game gain was 5% beyond reference spread. No repeated
  >5% P99 harm. Targeted component gains require correct lifecycle and repeated
  actual-class results. Three processes per side, not thousands of independent
  observations inferred from individual frames.

## Startup and latency

Launcher helpers live in `C:/Dev/aurora-launcher/tools/benchmark/initiative2_*`.
Native instrumentation uses the existing `performance-p0-2` research feature and
frontend mode. Ordinary builds compile probes away. Numeric event buffers are
bounded and flush only on exit. Never commit raw account/session/browser data.
Use `initiative2_native_fixture.py` only after inspecting its explicit new-root
paths. `initiative2_native_cycle.py` preserves logs/world hashes between normal
native boots, a menu-only induced child failure and a successful menu relaunch.
Native child observer resolves **launchExecutable** from runtime-installed.json;
the diagnostic Java executable may differ (javaw.exe versus java.exe).

The main workload's `frames.csv:swap_ns` marker is **before Window.updateDisplay**,
not at the actual swap API. Derived JSON calls it `preUpdateDisplayIntervals`.
`kind=input` separately enables InputTiming + SwapProbe: last mouse callback
consumed per frame, accumulated-movement return, camera-setup return and actual
GLFW swap-call entry/return, with QPC ticks. This separate cohort uses bounded
arrays and never writes a file in a frame callback.

`initiative2_present.py` targets the exact PID/creation identity with the previously
verified portable PresentMon binary, bounded duration and session-specific loss
queries. No privileges or system settings are changed. `initiative2_latency_review.py`
trims exactly two seconds at both overlap boundaries, checks populations, clocks,
session loss and unique association within two milliseconds. Failure to associate
every event prevents a completeness claim. OS display estimates and software
stages are kept separate; neither is physical input-to-photon latency.

Raw captures remain in new local Temp roots, identified in the final report and
private `.git/performance-initiative-ii` manifests. Sanitized aggregate JSON is
committed; raw JFR, process arguments, logs, account identities and browser state
stay private. Original repository run directories are never execution targets.
