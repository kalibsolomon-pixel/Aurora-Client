# C-PERF.1A — PARK diagnostics (2026-10-17)

## Baseline

Root `C:\Dev\Aurora-Client`; origin
`https://github.com/kalibsolomon-pixel/Aurora-Client.git`; branch
`codex/unified-theme-pilot`; starting HEAD
`93c3c07aef11389a607f3c2c1c055c657ea8342a`. Initial status, tracked
diff and untracked inventory were empty. C-PERF.3 native ownership and
HYBRID-only Low CPU corrections are present. Existing development games,
profiles, worlds, ignored developer files and benchmark fixtures are protected.

## Design recorded before implementation

- Instrument only the PARK dispatch, with a separate measured implementation;
  retain the original disabled PARK method. Observe context in the two existing
  hooks and arming/late reanchors at the existing `FramePacing.advance` decision.
  All calls run on the client render thread; no new scheduling owner or hook.
- GLFW seconds remain authoritative for entry, spin and final deadline error.
  Two self-contained `System.nanoTime()` reads bracket `parkNanos` only;
  those values are never subtracted from GLFW timestamps. Reuse the spin
  condition's exit timestamp; no extra clock read inside each iteration.
- Six preallocated signed histograms: requested/actual park, wake error,
  spin wall time, total wait wall time and deadline error. Primitive counters
  distinguish invocations, active waits, late entry, too-short-to-park,
  spin-only, early/late park returns, arming and late reanchors.
- Keep only the current homogeneous segment. Reset measurements on observed
  owner, strategy, effective FPS, configured caps/thresholds, Low CPU or focus
  changes. Preserve session transition totals and discarded invocation count;
  explicitly expose this retention rule. No unbounded segment history.
- Developer JVM opt-in `-Daurora.parkDiagnostics=true` registers local
  `/aurorapark start|stop|reset|status|snapshot` commands. Collection itself
  defaults off. No config/profile fields or ordinary settings UI. Explicit
  snapshots copy bounded data on the writer thread outside the wait, then
  serialize/write JSON on the existing common executor, with one export in
  flight. No polling thread or render-thread disk I/O.
- Disabled cost: predictable enable checks only; no extra clocks, histogram
  writes, synchronization or per-frame allocations. The original wait and
  deadline decision mathematics remain intact.
- Validate with synthetic arithmetic/lifecycle/aggregation tests, existing
  C-PERF.3 regressions, full Windows `test build`, and (if safely available)
  a disposable Java 21 game. Compare diagnostics off/on at identical PARK
  defaults using existing frame callbacks and JVM thread CPU/allocation
  counters. No 250-microsecond experiment. Report observer-effect evidence
  separately from unit/static correctness and leave gaps explicitly pending.

## Architecture and operation

Added `ParkDiagnostics.java` (collector) and `ParkDiagnosticsControl.java`
(developer activation/export); changed `AuroraClient.java` (startup registration),
`FramePacer.java` (enabled dispatch), both existing pacing mixins (context), and
`FramePacing.java` (anchor observation). Added `ParkDiagnosticsTest.java`;
adjusted the prior Low CPU test's dispatch assertion while retaining its original
PARK/SPIN whole-method hash checks. AGENTS.md and ARCHITECTURE.md link this record.

Launch a disposable development client with JVM property
`-Daurora.parkDiagnostics=true`. This registers **local client commands** only;
it does not activate collection or change pacing. Commands require an integrated
or multiplayer connection, as the Fabric client dispatcher does:

```
/aurorapark start       # fresh active session; replaces previous data
/aurorapark status      # explicitly reports ACTIVE or OFF
/aurorapark snapshot    # bounded copy; asynchronous JSON export
/aurorapark stop        # freeze measurements/duration, retain data
/aurorapark reset       # same fresh-session behavior as start (also activates)
```

Snapshots go under the disposable game's `debug/aurora-park/`, with random session
and export identifiers. There are no automatic exports, snapshot timers, shutdown
writes or polling workers. Only one export may be in flight. Status/start/stop
feedback is local; no network command, telemetry, player/server/world identity,
credential, chat content or filesystem path enters the JSON. Export success/failure
is reported locally. No new dependencies or settings/profile schema.

Schema 1 contains version, random session id, collection state/durations, segment
number, scheduling owner, actual dispatched strategy, effective FPS target,
configured global/GUI caps, configured/effective spin allowance, configured HYBRID
park threshold, Low CPU flag, focus, counters, transition totals, shared bucket
boundaries and six distributions. Fixed HYBRID fallback is explicitly labeled
`HYBRID_FALLBACK`; native/inactive owners use strategy `NONE` and produce no PARK
samples. The configurable 2000us park threshold remains HYBRID-only; PARK has no
minimum-remaining threshold beyond its existing positive `parkNanos` test.

Only the newest homogeneous segment survives. Any **observed** owner, actual
strategy, effective FPS, configured global/GUI cap, spin/park threshold, Low CPU
or focus change clears all segment distributions/counters and advances its number.
Session totals retain condition/FPS/owner/focus transitions and the number of
discarded invocations. Export each condition before switching if its data matters.
Start/reset creates a new id and clears everything; stop retains the segment and
freezes duration. No silent aggregation across conditions or unbounded history.

## Exact measurements

GLFW seconds are converted to integer nanoseconds with `round(delta * 1e9)`:

| Quantity | Definition / population |
|---|---|
| Requested park | Exact positive long passed to `LockSupport.parkNanos`; one sample per actual park call |
| Actual park | `afterNanoTime - beforeNanoTime`, immediately bracketing that call; elapsed wall time |
| Wake error | `actualParkNanos - requestedParkNanos`, signed; early/late counters use `<0` / `>0` |
| Spin wall time | Final failed GLFW condition read minus the first spin condition read, if entered; zero otherwise |
| Total wait wall time | Final failed GLFW condition read minus entry GLFW read; zero on already-expired entry |
| Deadline error | Exit GLFW read minus target deadline, signed; includes late-entry invocations |
| Initial anchors | Existing `advance` branch with `nextTarget == 0`, while dispatched strategy is PARK |
| Late reanchors | Same branch with a previous target and `now > nextTarget + period/2`; distinct from invocation late entry |

There is one original entry read and one read per original spin condition. The
enabled path adds only two Java timestamp reads when parking; it reuses first/final
spin-condition reads for spin/exit. No extra per-iteration clocks. Aggregation is
after exit capture, so total wait/deadline error exclude collector processing and
method-return instructions. An expired entry uses that entry timestamp as exit.
Late entry means `target <= entry`; too-short-to-park means positive remaining but
the original computed request is nonpositive (including integer truncation).
Active waits actually park or enter the spin body. Spin-only requires entering
the body without parking; a tiny positive interval consumed before the first spin
test is not counted as active. All invocations have spin/total/deadline samples;
only park calls have requested/actual/wake samples.

No Java/GLFW timestamps are subtracted from each other. Positive park error does
not imply a late deadline. Spin/wait wall time is not CPU consumption. No unique
frame id is invented; counts identify wait invocations at the existing mutually
exclusive owner, not frame-rate samples. Native waits, Reflex, VSync, GPU stalls,
swap, event processing and ordinary frame work are outside the measured wait.

## Aggregation, bounds and disabled cost

Six histograms each contain 178 preallocated long buckets (8544 bytes of bucket
payload total). Shared upper-exclusive boundaries are -1s, -256/-128/-64/-32/-16/
-8/-4ms, then -2ms through +2ms in 25us steps, then +4/+8/+16/+32/+64/+128/+256ms
and +1s. Buckets are `[lower, upper)`; first/last have unbounded endpoints represented
as JSON null. Exact edges enter the bucket above that edge. Binary search is bounded;
no boxing in recording. P50/P95/P99 are **approximate rank buckets**, using
`ceil(count * percentile / 100)`, never fabricated exact percentiles or midpoint
values for an unbounded tail. Count/min/max and an integer-nanosecond rounded mean
are included (double accumulation avoids long sum overflow). Empty statistics are
null. Counters saturate at `Long.MAX_VALUE`; wait recording freezes at that invocation
count; histogram sample counts likewise freeze rather than wrap. No raw sample queue.

Startup initializes bounded storage before frames, without reading a clock or
activating collection. Disabled frames add one plain boolean enable check at each
existing hook, one in PARK dispatch, and one only when `advance` takes an anchor
branch. No added disabled timestamps, sample updates, locks/atomics, I/O or per-frame
allocations. The original wait implementations and scheduler mathematics remain.
Enabled frames add scalar context comparisons, counter writes and up to six bounded
histogram updates. Explicit snapshots allocate/copy about 21KB of JSON-equivalent
data on the writer thread outside the wait; formatting and filesystem work use
the existing common executor. Snapshot lists/records are immutable and may safely
be inspected by that worker. Export controls use an atomic only on explicit commands.

## Verification results

- Baseline `.\gradlew.bat test --console=plain`: existing 489 tests passed
  (up to date). Final `.\gradlew.bat test build --console=plain`: **511 tests,
  zero failures/errors/skips**, production build passed. 22 new executions cover
  signed arithmetic, zero/expired/tiny entries, actual-spin classification,
  early/late parks, every histogram edge and tails, approximate ranks, count/mean/
  reset, lifecycle/segmentation, immutable asynchronous snapshot inspection,
  JSON, saturating arithmetic, real scheduling anchors and owner/fallback labels.
  Existing native/global/GUI, HYBRID Low CPU and real profile/config persistence
  suites pass; original PARK/SPIN method hashes still match.
- Runtime command:
  `.\gradlew.bat -I run/cperf1a-validation/smoke.init.gradle runClient --console=plain`.
  Final harness/log: `run/cperf1a-validation/runtime-final.log`. Microsoft Java
  **21.0.7**, Minecraft **1.21.11**, Loader **0.19.2**, Fabric API
  **0.141.4+1.21.11**, Sodium **0.8.14+mc1.21.11**, RX 9070 XT OpenGL 3.3 core.
  Disposable game copy only; all harness sources/resources/exports remain ignored
  under `run/cperf1a-validation/`. No production harness or dependency change.
- All 12 final phases completed and the game/server shut down normally. Real
  local commands exercised start/stop/reset/status/snapshot; JSON exported
  asynchronously, with valid bucket counts/populations and immutable session
  metadata. Global head, adaptive head and post-swap owners were distinct.
  Native 60 and native Unlimited generated **zero** PARK invocations. Iconifying
  retained native 10 FPS restriction (observed 9.67 FPS over a short sample);
  restoring recovered global 60. GUI 30 remained effective. Focus/FPS/owner
  transitions reset the segment and reported discarded data. Stopping froze the
  counters while rendering continued. Reflex's session-disabled latch stayed false;
  no new render/mixin exceptions. Existing Loom/deprecation/native-access and
  unauthenticated-development/empty-sound warnings are not new diagnostics failures.
- Normal launch (developer flag absent):
  `.\gradlew.bat -I run/cperf1a-validation/smoke.init.gradle -Pcperf1aNoCommands runClient --console=plain`.
  Separate disposable game, same Java/Sodium/OpenGL stack; real dispatcher had
  no `aurorapark` command, collection stayed OFF while PARK/global 60 ran, and
  the game shut down normally (`runtime-default.log`: `CPERF1A DEFAULT PASS`).

### Observer-effect check and remaining gap

One warm in-game repeat used **off/on/on/off**, unchanged PARK 500/2000, global 60,
native 120, VSync forced off, same stationary scene, Sodium, focused window, eight
seconds of settling then fifteen seconds sampled per phase. A fixed-size existing
HUD callback sample array measured internal frame intervals; JVM thread counters
measured render-thread CPU/allocation. Chat feedback was cleared before sampling;
snapshot/export happened outside each sampling interval. Harness calls to the
native input-received method held AFK state constant without moving the player.

| Phase | Collection | Frames | FPS | Mean interval ms | P95 ms | P99 ms | Max ms | Render-thread CPU % of one core | Allocated bytes/frame |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 0 | Off | 902 | 59.936 | 16.664 | 17.954 | 18.440 | 21.075 | 15.055 | 336488 |
| 1 | On | 899 | 59.933 | 16.665 | 17.947 | 18.265 | 19.600 | 17.812 | 320631 |
| 2 | On | 902 | 59.934 | 16.666 | 17.780 | 18.137 | 23.012 | 13.601 | 308674 |
| 3 | Off | 902 | 59.934 | 16.666 | 17.765 | 18.182 | 21.229 | 13.081 | 293921 |

Cadence and interval tails show no material scheduling regression in this bounded
check. CPU/allocation vary with time and ordinary game work: CPU spans **13.60–17.81%**
with collection and **13.08–15.06%** without it. This does **not** isolate a precise
end-to-end observer CPU cost. An additional warmed, synthetic collector-only check
on the render thread recorded 100000 samples in 6.5163ms with **zero allocated bytes**;
it excludes clocks, waiting, owner observation and game work and is not a claim
about full instrumentation overhead. Static inspection also found no per-wait
diagnostic objects. No new stability failure or frame-time deterioration was found.

The first exploratory pass (`runtime.log`) encountered native AFK 30 during its
fourth observer phase; that phase is excluded from the comparison. It usefully
verified native precedence and segment resets, but is not equivalent-condition
performance evidence. The final repeat corrected the harness condition, not the
scheduler. The measured PARK data also includes early park returns and spin wall
times longer than the configured tail; the allowance is not a guaranteed maximum.
These observations motivate measurement, not an algorithm change.

**Runtime observer-effect validation remains incomplete at the CPU attribution
boundary.** A longer stabilized/repeated off/on measurement using existing JVM
profiling tools should bound that cost before using the collector for comparative
CPU conclusions. Do not claim negligible overhead from the source or this short
run alone. Disabled internal deadline errors are intentionally unavailable; external
frame intervals provide the comparison. No clock-resolution calibration, subjective
input/visual acceptance, multiplayer, sustained GPU-bound Reflex, fullscreen/driver
matrix or alternative renderer validation was performed. Spin/wait wall time remains
subject to preemption and clock-read overhead. This is not a 500us-versus-250us test.

## Compatibility and disposition

PARK defaults **500us/2000us**, one-park/one-spin decisions, deadline advancement/
recovery, native ownership/Unlimited behavior, GUI/global caps, manual strategy
selection, HYBRID Low CPU, VSync policy, input/tick/event ordering, Reflex and all
configuration/profile keys are unchanged. Sodium/OpenGL ran successfully on the
specified machine; broader compatibility is not inferred. The measured method
duplicates only the small PARK wait, intentionally leaving the original disabled
body intact; future PARK changes must keep both paths aligned.

**Recommended next step: 3 — Additional observer-effect validation required.**
Implementation, deterministic validation, output and runtime compatibility checks
are complete. The CPU variance prevents a confident full observer-cost bound for
the intended subsequent study. No tuning or C-PERF.1B work has begun. Owner review
is next.

## Repository preservation and closing checks

Added four tracked artifacts: this record, the two diagnostic classes and
`ParkDiagnosticsTest.java`. Modified eight existing files: AGENTS.md,
ARCHITECTURE.md, AuroraClient, both pacing mixins, FramePacer, FramePacing and
FramePacerLowCpuTest. **Deleted: none.** No Launcher, production profile/config,
world, benchmark fixture or dependency edits. The 154 original development
configuration/world files were SHA-256 checked against the baseline with no changes.
Ignored disposable harness/game/log/JSON files are retained for review.

Final `test build` and `git diff --check` passed. Reviewed `git diff --name-status`
and the explicit staged inventory; no deletion entries or unrelated files. The
scoped local commit contains this report, so its SHA and final clean status are
provided at delivery (also available with `git log -1 --format=%H --
CPERF1A_DIAGNOSTICS_VALIDATION.md`). No push, merge, tag, publication or release.
