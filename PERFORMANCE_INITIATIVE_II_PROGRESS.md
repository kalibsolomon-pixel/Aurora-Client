# Aurora Performance Initiative II — execution record

Started 2026-10-19. User authorizes both repositories, disposable instances,
profiling and local commits; no push/publication/system settings changes.

## Preservation and rollback

Both repositories started on `codex/performance-initiative`, tracked trees clean.
Client HEAD `071993b192d6c2e0389fee3c461b7ad51bc21731`; Launcher HEAD
`ecf5ea8f23ffd7e0c36ac79a6020a0a92dd46ce7`. New branches: `codex/performance-initiative-ii`.
Private `.git/performance-initiative-ii/baseline.json` files record remotes,
history, tracked/untracked sets and SHA-256s: Client 5,073 files/3,934,123,918
bytes; Launcher 2,629 files/731,719,665 bytes. Existing untracked work stays
excluded from commits. Original `run/` content stays immutable.

## Execution plan

1. Reuse prior tooling and verified local assets. Freeze workloads, validity and
   stopping rules before timed collection; use new short disposable roots.
2. Run lightweight, complex entity/block-entity, traversal/high-distance,
   populated compatible Fabric configuration, memory-pressure and sustained
   workloads. Profile costly stages with JFR and independent process counters.
3. Audit Launcher initialization/readiness and Minecraft startup/input/render
   ordering. Reuse opt-in probes; add missing phase boundaries without moving
   required validation. Investigate available presentation diagnostics.
4. Implement only attributable improvements; compare matched fresh processes.
   Keep G1/2048, PARK/500 and automatic Reflex unless new evidence warrants change.
5. Run integrated launch/restart/failure/relaunch checks and proportional tests;
   document raw evidence, failed captures and limitations; audit preservation and
   commit tested changes locally.

## Predeclared measurement rules

- Comparisons use fresh processes and identical copied worlds/configuration.
  Three runs per side in A/B/B/A/A/B order for any accepted performance claim;
  stop after two repeated material regressions. Exploratory screening may use
  one run and cannot establish an improvement.
- Startup: three fresh-process repetitions, warm filesystem/cache classification;
  no claim of cold-cache behavior without controlled independent cache state.
  Process clocks remain separate; cross-process intervals require Windows QPC
  correlation or an external monotonic observer with explicit uncertainty.
- Gameplay: >=30s warmup after setup; >=60s per measured phase; fixed 1280x720
  window, VSync off, cap 120 for controlled runs; separate high-distance/traversal
  phase. Record active/focus/pause and actual scene populations. No camera changes
  inside stationary windows. Traversal is explicitly dynamic.
- Missing shutdown, exceptions, incomplete buffers, wrong mods/settings, absent
  scene setup, insufficient duration or active/pause mismatch invalidate captures.
  Preserve every invalid/failed run. No silently repeated retries.
- A candidate must improve its attributable cost repeatedly; whole-game gains
  additionally require >=5% repeated improvement beyond reference spread, no
  >5% P99 regression, and correct lifecycle/appearance. Microbenchmarks never
  substitute for whole-game or physical-latency results.
- JFR weights estimate sampled allocation; process RSS is not heap/leak proof.
  Frame-loop/swap-call intervals are software metrics. PresentMon WGL event
  loss/display missingness remain explicit. No input-to-photon claim without
  independently observed visible response. No privileged tracing workaround.
- Sustained validation targets 10 minutes, bounded by observed risks. Test only
  one credible alternative heap if pressure warrants it. No collector sweep.

## Execution history (superseded by completion below)

Preservation complete. Research-only Fabric workload mod and QPC-correlated
startup probes implemented. All disposable roots are under the OS Temp directory;
native startup roots use the existing research probe's required `aurora-p0-2-`
prefix. Scripts and private baselines identify exact roots and artifacts.

Calibration failures retained: Loom absolute runDir assumption (relative path
workaround), reserved Mixin helper package, non-static constructor HEAD injector,
accessibility onboarding flag, copied profile wrapper, frozen fixture entry,
and missing normal disconnect notification in the research driver. No production
defect inferred from these driver failures. A completed exploratory pack run
failed both fill commands because destination chunks were unloaded; it is
ineligible for the declared complex block-entity scene. Setup now generates that
footprint before placement and the reviewer rejects failed commands.

Lightweight baseline `aii-base-05`: normal exit, valid states, 119.985 internal
FPS, P99 8.760ms, no >50ms interval, 1280x720 framebuffer. Aurora initialization
179ms within ~19.7s process-to-menu observation; development launch, not native
release startup. Full phase/JFR/process records are retained privately.

Launcher startup: references b1/b2/b3 issue no readiness request and remain Needs
attention beyond 30s. A newer initial state read supersedes startup's first read
before selection exists; startup skips runtime/readiness. The candidate waits
for the winning in-flight reload without relaxing native checks. Regression
fails original; candidate passes all 28 startup tests. Candidate c1/c2/c3 reaches
Ready without recheck at ~29.777/11.386/10.915s after process creation returned.
QPC/Popen brackets remain explicit. Home RAF checkpoint ~0.32–0.33s; physical
visibility and Ready differ. Candidate c1's early UI observation was Needs
attention; its completed trace proves later automatic recovery, not a failed boot.

Client candidate: RegionCache uses Minecraft's existing primitive-key linked map,
preserving access-driven eviction (the former peek comment was incorrect).
Full test/build: 550 tests pass. Tests cover eviction order, misses, negative
keys, pixel/stamp retention and cleanup. Actual-class six-process diagnostic:
reference 19.901/19.881/19.982 ns, 21 allocated bytes/call; candidate
6.641/6.311/6.470 ns, zero bytes/call. Earlier misidentified same-class diagnostic
is ineligible and retained as micro-1..6; valid captures are micro-valid-1..6.

Six corrected matched workload fixtures frozen in A/B/B/A/A/B order (`aii-s1`
through `aii-s6`). Only RegionCache changes production behavior. Reference class
snapshot retained privately; RegionTile's recompiled debug-line changes were
checked against identical disassembly. Baseline worlds/config/mod manifests and
per-run launch files are frozen. No gameplay gain is accepted yet.

JFR stack JSON export exceeded its 60s bound; raw recording remains intact.
Streaming JfrAttribution reads it in bounded memory without a huge intermediate
export. Statistical allocation/CPU attribution stays distinct from exact counters.
Remaining: matched runs, sustained validation, presentation/software input capture,
integrated native stress/restart/failure/relaunch, full Launcher checks, preservation
audit and final report/commits. No push or release changes.

## Completion — 2026-10-19

All six corrected A/B/B/A/A/B gameplay runs complete normally and pass state,
scene, camera and lifecycle review. Frame-time distributions overlap; no whole
FPS/GC/RSS gain is accepted. Actual RegionCache microbenchmarks establish the
narrow lookup improvement and all three candidate JFRs remove matching boxed-key
samples. G1/2048, PARK/500 and bounded Reflex remain unchanged.

Native sustained game-1 completes 630 seconds after setup and exits 0; seven
settled minutes have zero >50ms frame-loop intervals and bounded RSS/heap cycles.
Actual native resolution 2560x1415 and GUI cap 120 differ from the matrix; this
is a separate integration diagnostic. The fixture omitted options copying;
the helper is corrected and historical output stays intact. Explicit native
resolution settings also reveal an existing unsupported resolution placeholder;
the disposable null-window/saved-options workaround preserves validation.

Final native n2/n3/n4 boots mark restored usable Home at 779/781/782ms and first
Ready at 11.822/11.352/11.788s. Normal world load/gameplay/save/exit succeeds.
Menu-only induced child failure exits 23 and is reported by Launcher; another
fresh Launcher successfully relaunches to menu and exits 0. All parents exit 0.
All 171 saved-world files remain hash-identical after both menu-only cycles.
The observer now uses manifest launchExecutable (javaw), not diagnostic java.
First game's late window observation is excluded as a creation timestamp.

PresentMon diagnostic interior has 6717 actual swaps and 6717 rows, but only
6716 unique nearest associations and 3.130ms worst discrepancy: completeness
criterion fails. Six software callback-to-swap observations span 6.974–8.898ms;
no physical input-to-photon result or latency optimization is claimed.

Client full test/build passes 550 tests explicitly on Java21; four quality tests
pass. Launcher frontend 284, native 997 pass (31 existing ignored), type/style,
format/check and ordinary/research builds pass. Raw failures and private logs
remain retained. Baseline audits: zero missing originals, zero protected changes.
The final A–L report, reproduction protocol and sanitized aggregate/hash manifest
are complete. Local commits contain the accepted changes, tests and research
documentation; original untracked work remains excluded. No push/publication.
