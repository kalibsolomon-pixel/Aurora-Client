# Aurora performance initiative — execution record

Owner authorization: attached end-to-end initiative, 2026-10-18 (America/New_York).
Status: ACTIVE. This is a working record, not a final performance acceptance.

## Preservation and ownership

Client starts at `66db439bf18fc60a661e00a78f2ea2236ea05ee5` on
`codex/unified-theme-pilot`; Launcher starts at
`4984010d5309e05ec923535bba066c4e7aa90a5b` on
`codex/client-3-production-authority`. Both now use local
`codex/performance-initiative` branches. Origins remain the owner's Aurora-Client
and Aurora-Launcher repositories. No remote mutation or release is authorized.

Each repository retains a private `.git/performance-initiative/20261019T032535Z/`
baseline: status, HEAD, remotes, history, worktrees, tracked paths, file sizes and
SHA-256, plus original source/untracked/log bytes. Client inventory covers 2,597
files (1,804,777,225 bytes), including all existing run evidence/worlds/configs;
Launcher covers 3,268 files (740,990,316 bytes), including prior private performance
evidence. Generated build directories are excluded. Existing untracked files stay
outside initiative commits. Test logging uses the already implemented isolated
Gradle configuration. The historical three-log loss remains an acknowledged
exception, not a newly successful preservation claim.

Client owns scheduling/rendering/feature overhead. Launcher owns runtime selection,
JVM arguments, memory policies, readiness and its own overhead. No duplicate pacer.

## Roadmap and measurable gates

| Phase | Work and decision | Status |
| --- | --- | --- |
| A | Review existing evidence, protect inputs, fix bounded experiment criteria | Complete |
| B | C-PERF.2B stopped after two pairs on repeated deadline regression; original PARK/500 retained | Complete |
| C | Retained JFR allocation review identifies repeated WorldScope sanitizing; targeted validation next | Running |
| D | Audit existing instance policies, runtime authority, JVM validation and deterministic assembly | Pending |
| E | Bounded, justified Java 21 heap/collector comparison; reuse existing pipeline and fixed worlds | Pending |
| F–G | Preserve or improve existing presets and manual controls according to evidence; validate migration/readiness/UI | Pending |
| H | Review prior Launcher profiles, measure current startup/idle; fix only material attributable overhead | Pending |
| I | Combined Windows Java 21/Fabric/Sodium/OpenGL validation; Linux only if available | Pending |
| J | Unified before/after report, local commits, preserved evidence and explicit limitations | Pending |

## Established evidence and measurement limits

Read C-PERF.3 correctness/validation, C-PERF.1A diagnostics/observer validation,
C-PERF.1B, C-PERF.2 architecture investigation and C-PERF.2A/recovery. Existing
native precedence and HYBRID Low CPU correctness fixes stand. 250 us was rejected:
lower spin wall accompanied repeated worse callback/deadline tails. Production
PARK/500 us and Launcher 2048 MiB remain references. Bounded re-parking is tested,
startup-only and default-off; performance is unestablished.

Launcher P3-B.1 remains inconclusive. P3-B.1a confirmed camera checkpoints but
failed performance alignment; those captures cannot become valid heap comparisons.
Existing Launcher presets/configuration already exist and must be audited before
adding controls. Prior phase-local restrictions are historical; this owner's new
request authorizes the present cross-repository initiative.

Overlay reports internal eligible HUD callback intervals, not displayed frames or
input-to-photon latency. PARK histograms report coarse rank buckets and wall time,
not active CPU. Whole-process CPU/RSS are contextual; the unresolved historical
240 FPS diagnostic-On CPU increase cannot be dismissed. JFR supplies sampled
attribution/aggregate GC context; it does not establish individual-frame GC cause.
No new observer is assumed free. Raw and invalid runs remain retained.

## Resume

The six inputs are prepared in fresh ignored `run/cperf2b-benchmark/`, matching
world identity `84b036cc3b191a066befc7968c88850c393520e3a69d68b369d19066365a352a`.
`CPERF2B_PROTOCOL.md` fixes criteria before data. Runs 1–4 passed all validity
checks and ended normally. Both candidate/reference pairs fail the deadline-tail
screen despite lower spin wall; see `CPERF2B_RESULTS.md`. Prepared 5/6 are unused,
not invalid or replacement runs. Test/build passed: 546 tests, no failures/errors/skips.
Logs, event times and camera PNGs remain in each ignored run directory, with a
789-file completed-run SHA-256 manifest. Production defaults/source are unchanged.

Next: Phase C, then the audited Launcher architecture and bounded JVM comparison.
Retained P3-B.1a JFR windows are useful allocation leads, not valid performance
comparisons: weighted statistical samples attribute 14.6/18.4 MB per 30-second
window to WorldScope.sanitize. Its current per-frame/tick callers repeatedly
compile a regex and construct identical keys. Full recording startup outliers
were excluded by the existing resource window, not by a value filter. The private
aggregation with source hashes is `run/performance-initiative/retained-allocation-review.json`.
Do not rerun preparation or reuse benchmark outputs. Continue without routine
owner approval; preserve all evidence and report practical limitations.
