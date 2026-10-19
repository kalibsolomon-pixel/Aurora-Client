# Aurora performance initiative — completed execution record

Status: COMPLETE, 2026-10-19. The owner's attached end-to-end initiative is
implemented and evaluated within its declared bounded experiments. See
[the unified final report](PERFORMANCE_FINAL_REPORT.md) for results and limits.

| Phase | Completed outcome |
| --- | --- |
| A | Existing work reviewed; both repositories preserved; protocols fixed before measurement. |
| B | Re-parking rejected on repeated deadline regressions; original PARK/500 us retained. |
| C | Profile-attributed stable world-key allocation removed; 548 tests passed. |
| D | Existing native runtime, arguments, memory and readiness authority audited. |
| E | Four valid G1 runs retain 2048/default G1/no Xms; ZGC has no eligible performance result. |
| F–G | Existing explicit advice/manual settings retained; native persistence and conflict recovery passed. |
| H | Two native/WebView idle captures and source audit; no material bottleneck established. |
| I | Fullscreen native gameplay, normal shutdown, induced exact-child exit and subsequent normal relaunch passed. |
| J | Unified evaluation, local commits, sanitized evidence and final preservation audit completed. |

Client began at 66db439bf18fc60a661e00a78f2ea2236ea05ee5 on
codex/unified-theme-pilot; Launcher at 4984010d5309e05ec923535bba066c4e7aa90a5b
on codex/client-3-production-authority. Both now use local
codex/performance-initiative. Original branches, remotes and other worktrees remain.
Client owns runtime pacing/features; Launcher owns Java, arguments, memory,
readiness and supervision. No duplicate implementation or speculative rewrite.

Private .git/performance-initiative/20261019T032535Z/ baselines retain original
status, history, file inventory/SHA-256 and recoverable source/untracked/log bytes.
Client: 2,597 files, 1,804,777,225 bytes. Launcher: 3,268 files, 740,990,316 bytes.
Generated build directories excluded. Final audit requires zero missing originals;
only Client WorldScope.java, AGENTS.md and ARCHITECTURE.md intentionally differ;
Launcher originals remain unchanged. Historical three-log loss predates this work.

Raw successful and invalid captures remain private: 789-file C-PERF.2B manifest,
1,340-file JVM capture manifest and 53 protected native integration files.
Original worlds/config/profiles/instances were protected through fresh copies;
Gradle logs stayed isolated. Existing untracked files remain outside commits.

Internal HUD timing is not display/input latency; spin wall is not active CPU;
JFR weights are not an allocation ledger or individual-frame cause. The cache
microdiagnostic establishes removed allocation, not whole-game FPS gain. ZGC,
Linux/WSL, Vulkan, low-memory hosts and demanding packs remain unaccepted.
Fullscreen helper failure and startup observation limits are explicit in the
final report. No further phase work is pending under the bounded protocol.

All commits are local. No push, merge, release, public jar distribution or
system-wide setting change. Official release remains owner-controlled.
