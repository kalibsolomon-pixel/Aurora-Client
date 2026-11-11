# Aurora Client and Launcher — final performance evaluation

Completed 2026-10-19 on local `codex/performance-initiative` branches in both
repositories. Phases A–J are complete within their declared bounded experiments.
The accepted production change removes repeated allocation when querying an
unchanged world identity. Original PARK/500 us and default G1 with a 2048 MiB
maximum heap remain the supported references. No end-to-end FPS improvement is
claimed. Alternatives were rejected or left inconclusive when evidence did not
support them; no release was published.

## Completed work and decisions

| Phase | Outcome |
| --- | --- |
| A: baseline | Prior Client/Launcher reports reviewed; repositories inventoried and protected; protocols fixed before measurements. |
| B: pacing | Four valid controlled runs reject bounded re-parking on repeated deadline regressions. Existing native/HYBRID correctness and manual controls retained. |
| C: Client overhead | Retained JFR windows identify repeated world-key construction; bounded cache removes stable-call allocation while preserving persisted keys. |
| D: configuration | Existing Rust runtime/argument/memory/readiness authority audited; no duplicate architecture needed. |
| E: JVM | Four eligible G1 runs compare 2048/1536 MiB. ZGC has no eligible timed result; reference retained. |
| F–G: settings | Existing advice/manual presets retained; native dependency validation, saved memory, restart persistence and conflicting-argument recovery exercised. |
| H: Launcher | Two native/WebView family idle captures plus source audit find no material idle bottleneck requiring a rewrite. |
| I: integration | Native fullscreen capture, normal exit, induced exact-child failure supervision and successful subsequent relaunch validated. |
| J: evaluation | Results, failures, limits, preservation and release readiness recorded; changes committed locally. |

## Measured results

Game frame statistics describe eligible internal HUD callback intervals, not
display presentation or input-to-photon latency. Fresh processes and repeated
pairs are comparison units; thousands of callbacks are not independent trials.
Whole-process CPU includes unrelated game activity; spin wall is not active CPU.
Summed process working sets can double-count shared pages.

| Area | Reference / before | Alternative / final | Decision |
| --- | --- | --- | --- |
| PARK/500 us | Deadline error >=100 us: 2.21% / 2.98%; P95 buckets [0,25) / [25,50) us | Re-parking: 8.21% / 7.53%; P95 [250,275) / [200,225) us; spin wall 41–44% lower | Reject re-parking. Lower spin wall accompanies repeated timing regression; active CPU saving unestablished. |
| Stable world-key query | 241.618–245.095 ns/call; 1375.367–1376.000 allocated bytes/call | 5.765–5.856 ns/call; zero allocated bytes/call | Accept targeted cache. Actual-helper standalone diagnostic, not Minecraft FPS/GC savings. |
| Managed G1 heap | 2048 MiB: P99 5.046 / 5.123 ms; RSS 1624–1669 MiB; max GC pause 4.424 / 4.378 ms | 1536 MiB: P99 5.013 / 5.057 ms; RSS 1791–1891 MiB; max GC pause 16.819 / 15.254 ms | Retain 2048. Callback tails overlap variation; smaller maximum did not reduce RSS and had longer GC pause tails. |
| Launcher idle | Observation 1: 0.0781% of one logical core; summed RSS 502.20–504.15 MiB | Restart observation 2: 0.0260% of one core; 484.45–485.27 MiB | Observations of unchanged implementation, not a before/after improvement. |
| Native combined run | No matched before-change capture | 239.994 FPS; P95 4.782 ms; P99 5.147 ms; combined summed RSS 1885.96–1886.82 MiB | Compatibility/resource observation; no causal improvement claim. |

### Frame Pacer

[Protocol](CPERF2B_PROTOCOL.md) and [results](CPERF2B_RESULTS.md) retain fixed
ordering, stopping rules, full histograms, retries, stalls, reanchors, process
resources and input identities. Four valid runs form two failed pairs; prepared
runs 5/6 are unused, not discarded failures. The candidate made at most one extra
fixed 1 ms park against the original deadline. The default-off prototype remains
experimental and is not a performance recommendation. Prior 250 us spin also
worsened tails and remains rejected. Neither supports adaptive/CPU-model tuning.

Conditions: Windows, Minecraft 1.21.11, Loader 0.19.2, API 0.141.4+1.21.11,
Sodium 0.8.14, Microsoft Java 21.0.7+6, 2 GiB initial/max heap, fullscreen
2560x1440, RD4/SD12, fixed spectator camera in copied frozen world, 240 global
cap, native Unlimited, VSync off, Low CPU/adaptive sleeping off. Valid runs used
at least 60 seconds warmup and separate 60-second Overlay control/primary captures.
Existing observer-overhead limits remain; no new observer entered these windows.
One Windows cap does not establish all-cap or cross-platform acceptance.

### Client runtime overhead

[Client runtime report](CLIENT_RUNTIME_PERFORMANCE.md) records sampled attribution,
compatibility tests and diagnostic procedure. Two retained approximately 30-second
JFR windows attributed weighted 14.6/18.4 MB to WorldScope sanitization. Those older
captures failed heap-performance alignment and serve only as profiling leads.
Sample weights are not an exact allocation ledger.

`WorldScopeKey` compiles its regex once and retains one immutable entry keyed by
namespace, source string and default locale. It retains no world/client objects.
Changing server/name/namespace/locale recomputes immediately; legacy casing,
Unicode, punctuation, whitespace, null behavior and saved key format are preserved.
Two tests cover old-output equivalence and cache transition/reuse. Three fresh
processes per path measured 100,000 warmup and 500,000 stable calls using
ThreadMXBean allocation accounting and volatile output on managed Java 21.
This demonstrates removed redundant work, without predicting whole-game savings.
Already-cached font styles and 10 Hz HUD strings did not justify more rewrites.
Better Hitreg timing, glass rendering and Sodium's chunk pipeline were untouched.

### Java, heap and configuration policy

Launcher [JVM results](C:/Dev/aurora-launcher/JVM_PERFORMANCE_INITIATIVE_RESULTS.md)
contain per-run data and reproducible review tooling. Fixed A/B/C/C/B/A matrix:
A default G1/2048 MiB, B G1/1536 MiB, C Java 21 generational ZGC/2048 MiB.
No Xms; actual initial heap was 510 MiB. Runs 1/2/5/6 meet entry/camera/warmup/
capture/normal-exit gates. Same lightweight stationary, normally ticking world,
240 cap, managed runtime and final Client were used. Long external gaps between
pairs and ambient variation prevent a CPU-efficiency conclusion.

G1 2048 runs had 14 contained GC pauses each, totaling 30.868/32.337 ms in resource
windows; 1536 runs had 9/10, totaling 44.046/41.962 ms. The smaller maximum allowed
higher committed heap in these sessions (950–1020 versus 783–795 MiB). Individual
slow callbacks are not proven GC-caused. JFR enabled selected JDK events, but
Minecraft also registered custom events; this is explicit, with sanitized exports
and zero DataLoss. Collector-dependent observer overhead is uncalibrated. This
is not a demanding-pack or OOM stress test.

| Existing Launcher mode | Recommendation retained |
| --- | --- |
| Efficient | 1536 MiB advice target, with fresh host safeguards and explicit apply/save. A heap budget; no proven efficiency/FPS advantage. |
| Balanced | 2048 MiB target, Java default collector, Java-selected initial heap. Tested reference, not universal optimum. |
| Performance | Same policy as Balanced. No evidence justifies extra memory/flags or faster claim. |
| Custom | Explicit instance memory and supported properties/assertions; validate conflicts without losing draft/saved values. |
| Existing Configuration | Missing mode preserves legacy policy/parser behavior; no silent opt-in. |

The [policy decision](C:/Dev/aurora-launcher/PERFORMANCE_POLICY_DECISION.md) and
[architecture audit](C:/Dev/aurora-launcher/PERFORMANCE_CONFIGURATION_AUDIT.md)
document one authoritative Xmx, bounded parsing, native save/launch validation,
managed game-plan-required Java without PATH fallback, and fail-without-overwrite
handling of unknown/damaged settings. Advice reserves 4096 MiB of total and
2048 MiB of available RAM, observes architecture caps and refuses stale/missing/
inconsistent facts. These are conservative heuristics, not RAM reservations or
playability guarantees. No CPU-model/mod-count tuning or collector bundle added.
The tested Java vendor is not a vendor recommendation.

### Launcher and combined system

[Launcher resource report](C:/Dev/aurora-launcher/LAUNCHER_OVERHEAD_RESULTS.md)
records two 60-second idle windows, eight processes and 121 samples each, no
membership changes/errors. CPU totals were 0.046875/0.015625 seconds; the 28-thread
host was about 2.2–2.4% busy. This is an embedded unoptimized debug native build,
not release performance. Observed spawn-to-first-home upper bounds were 41.646/
20.957 seconds, including observation delay. Exact startup/first-Ready latency
and any startup improvement are unestablished. Event-driven previews, bounded
artwork caches, runtime checks, memory-fact caching, exact-child await and optional
Discord reconnect behavior were audited; required functionality was retained.
Discord-enabled, download-heavy and large-library costs were not profiled. No
measured idle bottleneck justified production Launcher source changes.

[Integrated results](C:/Dev/aurora-launcher/INTEGRATED_PERFORMANCE_RESULTS.md)
record native Play using a fresh isolated instance, current Client, managed
Java 21.0.7+6, Loader 0.19.5, API/Sodium, AMD OpenGL and 2048 MiB/G1. Primary
fullscreen capture followed 93.798 seconds warmup: 14,406 CSV rows, 60 seconds,
nine family processes with stable membership, exact camera/world checkpoints,
max callback 8.443 ms, two intervals above twice the 240 FPS budget, none above
50 ms. Game CPU was 74.062% of one core; Launcher counter delta was zero at
measurement resolution, not proof of zero cost. Combined CPU was 2.645% of total
host capacity. Game RSS was 1399.20–1399.95 MiB, Launcher/background family
486.76–486.87 MiB. Different Loader/classpath/session and absence of JFR prohibit
a before/after comparison with the JVM experiment.

Native UI saved 1536 MiB, retained it across restart, rejected conflicting Xmx
without overwriting saved state, then restored 2048. Normal gameplay shutdown
returned exit 0/Ready. A second exact disposable child was deliberately terminated;
native supervision reported -1 and recovered through recheck. Subsequent windowed
relaunch initialized Client/Sodium and closed normally with exit 0/Ready. Saved
world hashes remained unchanged through both recovery launches. All test game/
Launcher processes were closed. This is induced-failure recovery, not reproduction
of a naturally occurring Client crash.

## Failures, workarounds and remaining limits

- ZGC original run 3 expired its recording during an external pause before capture.
  Its sole predeclared replacement recorded only 53.189 seconds at the warmup-end
  gate due to operator error. Although capture started later, that fixed gate was
  not relaxed. Run 4 was interrupted by physical Escape before capture. All remain
  retained/ineligible; no extra replacements or ZGC performance claim.
- Parallel frontend tests hit Vite SSR loader timeouts. Failure log retained;
  serial execution passed all 282 tests. Preferred repair is independent test-loader
  isolation; serial execution is the reproducible workaround.
- Python copying into a long disposable path exceeded Windows limits. A fresh short
  Temp root succeeded; failed copies remain preserved. No registry change. Prefer
  path-aware tooling if longer layouts are required.
- Copied dev mods lacked API because development classpath had supplied it. Native
  validation correctly refused launch. A verified existing Fabric API jar was copied
  only into the disposable fixture; readiness/launch then passed.
- Restart initially showed Needs Attention; settings save/readiness refresh restored
  Ready. Transient cause and unattended first-Ready timing remain unestablished.
  Explicit recheck is tested; instrument readiness before promising startup latency.
- Computer-use helper crashed during the second fullscreen observation and returned
  non-target frames after reset. Earlier title checkpoint is explicitly invalidated.
  Logs plus unchanged world hashes established no world entry; termination used only
  verified exact-child identity/handle. Primary evidence was preserved before changing
  copied options for windowed recovery smoke. Preferred repair is reliable helper
  fullscreen capture, not a renderer change. No invalid screenshot supports results.
- Linux/WSL was unavailable; no privileged installation attempted. Vulkan, low-memory
  hosts, demanding packs, other Java vendors, long sessions, minimized behavior and
  display/input latency remain unaccepted by these measurements.

No ordinary implementation phase is blocked or pending. Optional future studies
need new protocols: eligible ZGC comparisons if useful, demanding packs/low-RAM
hosts, Linux compatibility/performance, precise startup/display timing and observer
calibration. Existing source support was preserved; Windows evidence does not
confer acceptance on unavailable platforms.

## Validation, repository state and release readiness

Client offline test/build: **548 passed, zero failures/errors/skips**, Java release
21. Launcher: **992 native tests passed**, 31 explicitly ignored; **282 frontend
tests passed** serially; frontend check/build and native embedded build passed.
Tool AST/JSON checks and real captures validated added benchmark tooling. No
production changes followed these full checks. Test logging stayed isolated.

Client production changes: WorldScope.java and new WorldScopeKey.java; added
compatibility/cache tests, helper benchmark/review/preparation tooling and sanitized
reports. AGENTS.md/ARCHITECTURE.md reflect decisions. Client commits: `ddd05ba`
baseline/protocol, `fb7658d` re-parking rejection, `9513af4` stable-key optimization,
followed by this final documentation commit.

Launcher production source is unchanged. Commits: `2829ea7` baseline, `6da2631`
JVM protocol, `152063f` architecture/preparation, `b2a361f` integration protocol,
`d35ee4d` JVM results/review, `ecf5ea8` overhead/integration/policy results. Both
original branches and owner remotes remain; no push, merge or other worktree change.

Private `.git/performance-initiative/20261019T032535Z/` baselines cover **2,597
Client originals (1,804,777,225 bytes)** and **3,268 Launcher originals (740,990,316
bytes)**, excluding build directories. Final SHA-256 audit found zero missing
originals; intended Client WorldScope.java/AGENTS.md/ARCHITECTURE.md differ,
Launcher originals do not. Historical loss of three archived logs predates this
initiative and remains an acknowledged exception. No new evidence loss.

Client `run/cperf2b-benchmark/` retains a 789-file completed capture manifest;
`run/jvm-initiative/` a 1,340-file manifest. Launcher private integration evidence
retains 53 files/3,198,792 bytes plus its manifest, with original Temp fixture
preserved. Source hashes, CSV/JFR/process samples, normal saves, timestamps,
camera checkpoints, invalid runs and failure logs remain private and recoverable.
Only sanitized results/tooling committed; no credentials or browser data.

Remaining uncommitted files are deliberately excluded: Client's pre-existing
CPERF1B_PARK_BENCHMARK.md and CPERF2_PARK_WAKE_INVESTIGATION.md; Launcher's
pre-existing .zcode-icon-diag.py, .zcodeignore,
UserskalibAppDataLocalTempaurora-phase-d-final-evidencesodium-exit-zero-ready.txt
and docs/performance-p0-2/__pycache__/; generated tools/benchmark/__pycache__/.
No unfinished initiative source/report is left uncommitted.

The targeted Client change is tested and ready for code/release review with these
limits. Launcher results support retaining its implementation; no new JVM tuning
or experimental re-parking is recommended for release. Local jars were used only
in disposable validation. Publication, release manifests and official distribution
remain under owner control.
