# C-PERF.2A — Experimental bounded re-parking implementation

Date: 2026-10-17 (America/New_York).

The isolated prototype, deterministic suite, full build and two disposable runtime
smokes passed. **Acceptance is blocked by a preservation failure:** running the
existing test suite rotated repository-root test logs. Three original archived-log
identities are no longer present. Implementation work stopped when the final hash
audit detected this; no commit was made. See H for exact scope and retained evidence.
No performance improvement is established, and C-PERF.2B has not begun.

## A. Repository baseline

| Item | Verified value |
| --- | --- |
| Root | `C:/Dev/Aurora-Client` |
| Origin fetch/push | `https://github.com/kalibsolomon-pixel/Aurora-Client.git` |
| Branch | `codex/unified-theme-pilot` |
| Starting and current HEAD | `ab86f03e419d0656e84bfc748e21313d22886436` |
| Initial tracked/index changes | None |
| Initial untracked files | `CPERF1B_PARK_BENCHMARK.md`, `CPERF2_PARK_WAKE_INVESTIGATION.md` |
| Ignored roots | `.gradle/`, `build/`, `logs/`, `run/` |

Reviewed project/architecture guidance, the two C-PERF.3 records, both C-PERF.1A
validation records, the C-PERF.1B report and the authoritative C-PERF.2 investigation;
inspected scheduler, diagnostics/control, both mixins and corresponding tests.
C-PERF.3 effective-native precedence, single anchored schedule, native fallback,
Unlimited reset and HYBRID-only Low CPU fixes remain present. No excluded scheduler,
configuration, mixin, renderer, Reflex, Launcher or dependency source was edited.

Before implementation, recorded SHA-256/size for **2,594 existing files**: 433
tracked files, both untracked reports, 2,145 files under `run/`, and 14 under root
`logs/`. Protected inventories include original `run/saves/` (131 files),
`run/config/` (23, including profiles), benchmark/preflight evidence, previous
diagnostic validation games and developer files. `.gradle/` and `build/` are
generated build outputs; compilation necessarily replaces their compiled products.

Audit and new disposable evidence directory:
`C:/Users/kalib/AppData/Local/Temp/aurora-cperf2a-c248c118510149778adb0c445e29d65a/`.
It contains `baseline-sha256.json`, initial Git status, preservation differences,
test XML copies, smoke source/resources/init script, two separate game copies,
runtime logs, actual JSON exports and `export-validation.json`.

Retained C-PERF.1B evidence verification before rebuilding: **130/130 manifest
files** and **415/415 baseline compiled classes** matched their recorded hashes.
Independently checked histogram populations in all **13 retained raw exports**.
No old evidence script or game instance was run.

## B. Implementation

Changed source files:

- `FramePacer.java`: startup flag, isolated experimental wait, deterministic timing/
  parking boundary, and PARK-only dispatch branch.
- `ParkDiagnostics.java`: experimental segment identity, four bounded histograms,
  retry counters and explicit schema-2 exports.
- `ParkDiagnosticsControl.java`: freeze the independent flag during startup, one
  startup message when opted in, and label experimental dispatch `PARK_REPARK`.

Added `ExperimentalParkReparkTest.java`; appended narrow architecture/project-guide
records. This report is the other added file. No existing tests were edited.

`-Daurora.experimentalParkRepark=true` is read exactly once by a static final
boolean, initialized during existing diagnostic-control startup even without
diagnostic command opt-in. Absent/false retains the original diagnostic-Off and
diagnostic-On PARK methods. True affects only custom PARK (including its existing
null-strategy fallback), independently of `-Daurora.parkDiagnostics=true` and
collection start/stop. There is no persisted field, user control or runtime toggle.

The existing dispatcher still normalizes `S = max(50, configuredSpinMicros)`.
The initial GLFW entry/deadline subtraction and positive long park request are
identical to reference PARK. After an actual first park, reuse the next GLFW
condition read: permit one retry only when `D - firstReturn >= S + 1ms` and no
interruption was observed at entry or before retry. This necessarily precedes
the planned spin window. Retry request is exactly **1,000,000 ns**. There are two
explicit park call sites and no retry loop. At most two calls occur per wait.

Entry/before-retry checks use non-clearing `isInterrupted()`. The final check can
race an interrupt; an immediate second return still proceeds to the final spin.
Nothing clears/restores interruption. After the second return, check the same
absolute GLFW deadline, then spin until its condition is satisfied. Already-expired
entry retains the reference immediate return. Java double-to-long request casts
retain reference saturation/truncation; no integer addition constructs a deadline.

Both original PARK methods are preserved in full. Tests pin their hashes:

- Diagnostic Off: `682728fccccd05cb3dd25e03b6aa5cb10672ba3e57e499953f497937ddbfa1a3`.
- Diagnostic On: `d9827dccdb59d1be90081af299b17482a346327a50b6766e32ce76a0afd02824`.

Existing original PARK/SPIN hash checks remain unchanged and pass. No preservation
check was removed, weakened or updated to a new expectation.

## C. Scheduler correctness and scope

`FramePacing.java` and both hooks are unchanged: effective native restrictions,
global/applicable GUI minimum, ownership precedence, late recovery, owner/FPS
switches and anchored `nextTarget += period` remain the C-PERF.3 implementation.
The experiment never reanchors, moves D, changes FPS or compensates future frames.
PARK/500/2000 configuration defaults and all saved custom values remain unchanged;
2000us remains a HYBRID setting. VANILLA, HYBRID, SPIN and fixed HYBRID fallback
retain their dispatch. VSync, Reflex and frame-head/post-swap placement are intact.

Locally mapped 1.21.11 `javap -p -c` reconfirmed
`BlockableEventLoop.schedule`: queue insertion followed by
`LockSupport.unpark(getRunningThread())` (offset 15). A permit arriving after the
first park can be consumed by the second. Neither wait services queued tasks;
the reference spins until D and the experiment retains that D. A retry that
oversleeps can delay the next existing task-servicing position. Even a bounded
request is not a bound on elapsed delay, and equivalent task responsiveness is
**not guaranteed**. No queue lock, task execution, event pumping, input reorder,
new thread or hook move is introduced. Source inspection found no need to change
task/event ordering to implement the authorized experiment.

## D. Diagnostic contract

Reference exports remain **schema 1** with their original six metric names,
counter meanings, bucket boundaries and populations. `parkPath` is additive
identity metadata and `reparkCounters` is null. Existing schema-1 readers that
ignore unknown fields retain their interpretation; strict whole-object validators
must allow the additive fields. The old diagnostic method itself is unchanged.

Experimental segments use **schema 2**, strategy `PARK_REPARK`, path
`EXPERIMENTAL_BOUNDED_REPARK`, startup flag state, maximum two calls, fixed retry
request and textual eligibility. Actual-strategy changes participate in existing
segment comparison/reset, including transitions between PARK and PARK_REPARK.
Only the latest homogeneous segment survives; discard/transition totals remain.
The process flag cannot change during a session.

The existing `counters.parkCalls`, early/late return counts and the first three
histograms retain **first-call** semantics. Schema 2 exports the latter explicitly
as `FIRST_REQUESTED_PARK`, `FIRST_ACTUAL_PARK`, `FIRST_WAKE_ERROR`. New counters:
total invocations, zero/one/two-call invocations, first calls, eligible retries,
retries taken, retries skipped for observed interruption, retry-limit exhaustion,
total calls, and retry-request min/max (zero with no retry).

| Histogram | Event population / definition |
| --- | --- |
| FIRST_REQUESTED_PARK / FIRST_ACTUAL_PARK / FIRST_WAKE_ERROR | First park calls; original exact request, Java call bracket and signed actual minus request |
| SPIN_WALL / TOTAL_WAIT_WALL / DEADLINE_ERROR | All invocations; original GLFW definitions, including zero spin and expired entry |
| TOTAL_PARK_ELAPSED | All invocations; sum of park-call bracket durations, zero without parking; excludes intervening code |
| FINAL_PARK_WAKE_ERROR | Invocations with a first park; last actual call elapsed minus its request |
| REMAINING_AFTER_FIRST_PARK | First park calls; signed `D - firstReturn` in GLFW time |
| REMAINING_AT_FINAL_SPIN_CHECK | All invocations; signed `D - spinStart`, including expired-entry timestamp |

Population invariants: `N=N0+N1+N2`; `first=N1+N2`; `taken=N2`;
`eligible=taken+skippedInterruption`; `totalCalls=first+taken`.
Exhaustion counts retries whose final remaining time still satisfies the time
eligibility rule; it never authorizes another call and does not classify wake cause.
Total-call and elapsed sums saturate instead of wrapping. Invocation counts freeze
at Long.MAX_VALUE as before; histogram buckets/min/max/approximate means remain bounded.

Ten preallocated histograms use the existing 178-bucket representation; export
aliases require no extra storage. Recording uses primitive locals/counters and
bounded histogram updates. No per-frame allocation, logging, filesystem work,
synchronization, raw trace, frame IDs, CPU sampler or worker thread was added.
Nullable collection changes only Java elapsed bracketing/aggregation, not deadline,
retry or interrupt decisions. Diagnostic Off executes no diagnostic clock reads or
record calls. Explicit snapshots still use the existing bounded immutable copy and
asynchronous common-pool export, with one export in flight. No extra loop clocks.

## E. Tests and build

- Existing suite after implementation: passed, 511 tests.
- New focused suite plus existing regressions: passed, **546 tests**, zero failures,
  errors or skips (35 new parameterized executions).
- Required `.\gradlew.bat test build --console=plain`: **BUILD SUCCESSFUL**, 546
  tests, release-21 compilation; remapped production jar built. No dependency or
  toolchain change. Build host remains installed JDK 25.
- Fresh-JVM probes cover absent/false/true flag values, property immutability after
  class initialization and independence from diagnostic opt-in. Scripted clocks/
  parks cover collection Off/On, expired/tiny/short/no-park waits, near/inside/early/
  late first returns, exact eligibility and neighboring-double boundaries, custom
  50/250/500/1500/max-int allowances, exact retry request, interruption at entry/
  first park/before retry/racing retry with preserved status, immediate/late/reserve-
  consuming retries, residual exhaustion, two-call limit and no premature exit.
- Large finite deadlines and long conversion boundaries, saturated parked elapsed,
  snapshot JSON identity, mixed 0/1/2 populations, every histogram population and
  reference/experiment segment discard/reset are covered. Script exhaustion fails
  immediately; no test depends on Windows timeout accuracy or a frozen fake clock.
- Dispatcher minimum-50 normalization and default strategy behavior remain covered
  by source contracts and existing tests. Existing native/menu/AFK/minimized/global/
  GUI/Unlimited/ownership/reanchor/strategy/VSync/Reflex/config/profile/segmentation/
  asynchronous-export regressions pass. Both original method-body hashes pass.

No introduced test/build failure occurred. The preservation failure in H was
detected after successful tests/smokes and is an acceptance blocker, not a test pass.
No further tests were run after detecting log rotation, to avoid further loss.

## F. Disposable functional runtime validation

Two distinct new copies under the audit directory, `reference/game/` and
`experimental/game/`. Copied prior source world/config/profiles and Sodium before
launch; the originals were never opened. A disposable helper mod/init script
configured PARK/500/global60 only inside these copies, exercised existing local
commands and checked state. No production helper or sampler was added.

Exact stack: Microsoft OpenJDK **21.0.7+6-LTS**, Minecraft **1.21.11**, Fabric
Loader **0.19.2**, Fabric API **0.141.4+1.21.11**, Sodium
**0.8.14+mc1.21.11**, AMD RX 9070 XT, OpenGL
**3.3.0 Core Profile Context 26.8.1.260810**; LWJGL 3.3.3-snapshot.
The pre-existing managed Java executable was used read-only; no Launcher change.

Commands: `.\gradlew.bat -I <audit>/smoke.init.gradle -Pcperf2aKind=reference
runClient --console=plain`, then the same with `experimental`.
Reference omitted the experimental property; experimental set it true. Both
enabled developer commands while beginning with collection Off, ran normally,
started/stopped collection, and exported through the actual asynchronous command.

| Functional check | Reference | Experimental |
| --- | --- | --- |
| Startup/title, copied world load, collection Off | Pass | Pass |
| Actual dispatched/exported path | PARK / schema 1 | PARK_REPARK / schema 2 |
| Export invocation / first-call population | 240 / 240 | 240 / 240 |
| Zero / one / two-call invocations | Reference single-call semantics | 0 / 203 / 37 |
| Eligible / taken / interrupted / exhausted | No retry | 37 / 37 / 0 / 0 |
| Total calls and retry min/max | 240; no retry | 277; 1,000,000 / 1,000,000 ns |
| All histogram populations, sums, metadata | Pass | Pass |
| Keyboard inventory, close, pause, resume | Pass | Pass |
| Native Unlimited gives NONE / no PARK samples | Pass | Pass |
| Minimized native 10, restored native 260 | Pass | Pass |
| Reflex failure latch remains clear | Pass | Pass |
| Normal client/server shutdown and Gradle exit 0 | Pass | Pass |

Both exported segments were focused FRAME_HEAD_GLOBAL/60/500/2000, with no
condition changes or discarded invocations. `export-validation.json` records
file identities and all counters. These are short functional populations only;
no frame-time/CPU metrics were compared or accepted as performance evidence.

Both logs contain no ERROR lines, crash, hang, pacing failure or renderer
exception. Existing development remap/refmap, native-access/deprecation, Sodium
AMD workaround, offline Realms and empty salmon sound warnings remain. No repair
of unrelated warnings was attempted. Actual prolonged AFK inactivity, multiplayer,
input latency, all VSync states and GPU-bound Reflex behavior were not newly
tested; existing deterministic regression coverage remains applicable.

## G. Risks and limits

The 1ms request can oversleep, consume a later task permit, be interrupted, or
return immediately. It is an experimental constant, not a calibrated platform
guarantee. Normal input success is not a task-responsiveness equivalence proof.
Diagnostic observer overhead is unmeasured for this expanded collector; prior
CPU restrictions persist. Spin wall time includes preemption and is not active
CPU usage. CPU, power, frame-time, display timing, latency and cross-platform
effects remain unmeasured. No automatic tuning or production rollout is justified.

## H. Preservation failure and final repository state

The final audit found **13 baseline path/hash differences**: five intended tracked
edits and eight root-log paths affected by the pre-existing test logger. All
**2,145 original `run/` files**, both prior reports and all unrelated tracked files
remain present with matching SHA-256. Protected benchmark evidence, original
worlds, configurations and profiles are intact. 2,581 baseline paths are unchanged.

Root log rotation changed `logs/2026-10-17-{1..7}.log.gz` and `logs/latest.log`:

- Baseline archives 4/5/6/7 still exist byte-identically at current archive paths
  1/2/3/4. The six older October 6 archives remain unchanged.
- Baseline `latest.log` was recovered byte-for-byte **into the audit directory**
  as `recovered-original-latest.log`, by decompressing current archive 5;
  SHA-256 `02b40782549696c7af681463f99d732b986e82b35a0fe199440d1d4c76a079cf`.
- Baseline October 17 archives 1/2/3 have no matching byte identity in current
  root logs. Their original SHA-256s are, respectively:
  `74d54d2e05223bc94982a17c7bb2d1109e546a44b613d8bdc463b09b0b8399e8`,
  `496cc2798b8d55c04d4cbb64828d1773fee420c120212f3e5b0d1677719c78d9`,
  `e4eaf9a11ac86d37689a9f69c08667523fb133c9f78922d0ad99721e03425346`.

This is a preservation failure even though these are generated test logs. Hashing
alone did not back up their bytes. Current root logs were copied to
`root-logs-after-tests/` in the audit directory; no attempt was made to overwrite
current logs, fabricate original archives, or hide the failure. No further source
work or build/test execution followed detection. The requested report records
the stop. Owner review is required before resuming or committing; future suite
runs need isolated test logging or complete log backups.

Modified tracked files: `AGENTS.md`, `ARCHITECTURE.md`, and the three source files
listed in B. Added files: this report and `ExperimentalParkReparkTest.java`.
No tracked deletion/rename. No intentional cleanup/delete command, `git clean`,
reset, rebase, merge, branch switch, dependency upgrade or Launcher edit occurred;
the test logger's archive retention/rotation is the unintended loss described above.

Prior report hashes still match:
`CPERF1B_PARK_BENCHMARK.md` =
`58ae80c05af77c942b7944a3303afcca37fcb97587a93c23af93c36066b2f1bf`;
`CPERF2_PARK_WAKE_INVESTIGATION.md` =
`f9e7b54f924a85bc67917beaae313d14e85244f6466794f057bfe9f944e71487`.

`git diff --check` passed; diff name-status contains only the five intended
existing tracked files. Index remains empty; no local implementation commit was
made because preservation validation failed. No push, merge, tag, publication or
release occurred. Final short status:

```text
 M AGENTS.md
 M ARCHITECTURE.md
 M src/client/java/com/aurora/client/util/FramePacer.java
 M src/client/java/com/aurora/client/util/ParkDiagnostics.java
 M src/client/java/com/aurora/client/util/ParkDiagnosticsControl.java
?? CPERF1B_PARK_BENCHMARK.md
?? CPERF2A_REPARK_IMPLEMENTATION.md
?? CPERF2_PARK_WAKE_INVESTIGATION.md
?? src/test/java/com/aurora/client/util/ExperimentalParkReparkTest.java
```

## I. C-PERF.2B readiness

Technical prototype tests and bounded functional smokes pass. **Not accepted for
the controlled benchmark until the owner resolves the preservation exception.**
No performance success is claimed; no six-run matrix, CPU comparison or C-PERF.2B
measurement was run. Await owner review before any further work.

**C-PERF.2A BLOCKED — OWNER DECISION REQUIRED**

---

## J. Owner review and preservation recovery addendum — 2026-10-18

### Authorization and historical status

The owner's C-PERF.2A-R request accepts the technical implementation based on the
reported validation, acknowledges the preservation exception without erasing or
waiving the original failure, and authorizes a bounded recovery search, test-only
logging isolation and one local implementation commit after preservation checks.
The original audit **failed**, and sections A–I and their original verdict above
are retained unchanged. The owner acknowledged the irreversible loss of the three
original archives unless authentic byte-identical copies can be found. C-PERF.2B,
push, merge, tag, publication and release remain unauthorized.

Recovery began at the same branch/remote and full HEAD recorded in A, with the
five intended tracked modifications, two protected untracked prior reports, this
untracked report and the experimental test. The index was empty. No unexpected
source change or additional protected-file loss was found. A new **3,031-file**
SHA-256/size baseline covers all existing tracked/untracked work, current root
logs, every original `run/` file and the entire existing C-PERF.2A audit directory.
Current root logs were also copied before any test execution.

New recovery evidence directory:
`C:/Users/kalib/AppData/Local/Temp/aurora-cperf2ar-20261018-120436/`.
It retains `baseline.json`, initial status/diffs, implementation source copies,
`root-logs-before/`, `archive-search.json`, the original dependency logging XML,
reference-method hashes, isolation results/logs and the final preservation/commit
records. The earlier C-PERF.2A audit remains unchanged at the path in A.

### Authentic recovery search

The read-only search was limited to the existing C-PERF.2A audit, repository root
logs, `run/cperf1a-validation`, `run/cperf1av-validation`,
`run/cperf1b-benchmark`, `run/cperf1b-preflight-20261017`, and the immediately
identified prior temporary C-PERF.3 audit/smoke directory and C-PERF temporary
files. No other project backup/snapshot location was identified in the reviewed
guidance. No unrestricted filesystem search, recovery utility, archive rewrite
or speculative reconstruction was used.

All **18 candidate files** with the originals' exact byte lengths (1,642 or
1,643 bytes), regardless of filename/extension, were hashed in full. **Zero of
the three missing archives were recovered; all three remain unrecovered/lost.**
Their original paths and full SHA-256 identities in H remain the authoritative
record. `archive-search.json` lists searched roots, candidate paths and missing
hashes; there is no fabricated replacement or recovered-archive claim.

The four surviving shifted archives and the previously recovered original
`latest.log` remain intact. The original 2,594-file audit still shows exactly
the same five authorized tracked edits and eight known rotated-log path changes,
with no additional loss. All **2,145 original `run/` files**, both protected prior
reports, worlds/configurations/profiles and benchmark evidence still match their
original hashes. Current repository logs were never overwritten during recovery.

### Confirmed cause and test-only prevention

The dependency `com.mojang:logging:1.6.11` contains a default `log4j2.xml` with a
`RollingRandomAccessFile` appender targeting relative `logs/latest.log` and
`logs/%d{yyyy-MM-dd}-%i.log.gz`, plus `OnStartupTriggeringPolicy` and time-based
rotation. The Gradle test worker's working directory is the project root. Tests
that initialize application logging therefore activate the dependency's file
appender against the original root logs. The existing Reflex regression emits an
intentional exception to test its failure latch, explaining the test-worker
messages in the old root `latest.log`. This is dependency logger initialization
inside the test JVM, not a new pacing log or Gradle daemon output destination.

The prevention change is limited to:

- `build.gradle`: only the named `test` task supplies an absolute
  `log4j.configurationFile` URI and, immediately before execution, creates a fresh
  OS temporary directory named `aurora-client-tests-*` and passes its absolute
  path as `aurora.testLogDirectory`. The build output prints that directory.
- `src/test/resources/aurora-test-log4j2.xml`: the dependency configuration with
  only its two file destination attributes redirected into that directory.

Console, file, queue and listener appenders, layouts, filters, log level and
rotation policies are retained. Static XML comparison confirmed that the two
destination attributes are the **only semantic differences** from Mojang's XML.
Logging is not disabled. The test working directory remains the root so existing
source-contract tests retain their relative paths. This configuration is selected
only for the Gradle test JVM; it does not change runClient, production logging,
the mod's packaged resources, scheduler source or dependencies.

Future Gradle test executions use a new directory each time they actually run;
an up-to-date skipped task starts no test JVM. Direct IDE/JUnit launches that
bypass the Gradle task are outside this guarantee and must use equivalent explicit
logging configuration/destination or disposable execution. Temporary logs are
retained; no automatic cleanup was added. This isolates the identified logger,
not arbitrary future test code that might explicitly write repository paths.

### Bounded verification and implementation integrity

Ran only:

```text
.\gradlew.bat test --tests 'com.aurora.client.util.reflex.ReflexSchedulerTest' --console=plain
```

**Two tests passed**, zero failures/errors; BUILD SUCCESSFUL. The intentional
Reflex exception and its useful stack trace were retained in:
`C:/Users/kalib/AppData/Local/Temp/aurora-client-tests-2496790746593318435/latest.log`.
That absolute path is outside the repository. Its SHA-256 is
`179a20390919823b0643373d82c207475cd0a9dc206badd8f1e7d2cd4ab4caeb`.
The log and XML results are also copied into the recovery evidence directory.
All 14 current root-log hashes and the entire existing C-PERF.2A audit stayed
unchanged. Before the addendum, the only changed baseline file in this recovery
phase was the intended `build.gradle` adjustment. No full-suite rerun, gameplay
smoke repeat or benchmark was performed.

Reviewed the scheduler/diagnostic diff against starting HEAD and reconfirmed both
reference method hashes from B. All three implementation source files and
`ExperimentalParkReparkTest.java` remain byte-identical to their recovery-entry
copies: startup-only default-false property, PARK-only dispatch, one fixed 1ms
retry maximum, normalized reserve, original deadline/final spin and non-clearing
interrupt checks are unchanged. Schema-1 reference populations and schema-2 path
identity/counters/histograms are unchanged. Original PARK/500 and all config
defaults, hook locations, scheduling ownership, Reflex and Launcher are preserved.

Retained original test XML independently totals **546 tests, zero failures/errors/
skips**. Both retained runtime logs contain their matrix PASS and BUILD SUCCESSFUL
with normal shutdown. Those existing validation results remain applicable; the
new two-test logging check does not replace or claim a new 546-test run.

### Preservation and local commit readiness

Preservation checks permit only `build.gradle` and this appended report to change
relative to the recovery-entry baseline; the new test-only XML is an authorized
addition. No original benchmark file, world, config, profile, prior report,
root log or old audit artifact changed during recovery. The historical three-log
loss remains explicitly recorded as an owner-acknowledged exception.

The authorized local commit includes exactly nine files:

```text
AGENTS.md
ARCHITECTURE.md
CPERF2A_REPARK_IMPLEMENTATION.md
build.gradle
src/client/java/com/aurora/client/util/FramePacer.java
src/client/java/com/aurora/client/util/ParkDiagnostics.java
src/client/java/com/aurora/client/util/ParkDiagnosticsControl.java
src/test/java/com/aurora/client/util/ExperimentalParkReparkTest.java
src/test/resources/aurora-test-log4j2.xml
```

Only these files are staged after diff/whitespace/deletion and protected-hash
checks. No scheduler source changes were made during recovery. The existing
untracked `CPERF1B_PARK_BENCHMARK.md` and
`CPERF2_PARK_WAKE_INVESTIGATION.md` remain excluded and protected. The full local
commit SHA and final status are recorded in the recovery evidence after creation
and reported at delivery; this avoids a self-referential commit hash in its own
contents. Nothing is pushed, merged, tagged, published or released.

The prototype is ready for **owner authorization of C-PERF.2B**, with its risks
and unmeasured performance unchanged. This recovery does not start or authorize
that benchmark and does not turn the original preservation failure into a clean
pass. Recovery/prevention checks support the owner's authorized local progression.

**C-PERF.2A-R COMPLETE — READY FOR OWNER REVIEW**
