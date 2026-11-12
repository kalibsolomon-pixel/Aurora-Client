---
status: partially superseded by ADR 0009 (scope paragraph and rule 2); the overhead budget stands
date: 2026-11-10
---

# Performance means a budget on Aurora's own cost, not a competing engine

Sodium and its neighbours own rendering throughput. Aurora's performance work is (a) a numeric per-frame budget on its own in-world cost with default settings, (b) frame pacing and latency, and (c) shipping and configuring proven mods through the launcher. Aurora does not write its own chunk, entity or particle rendering optimizations. Two performance initiatives in 2026 ended with no end-to-end FPS improvement claimed, which is the evidence for how little is left to win alone.

Small optimizations of vanilla code are still allowed when all three hold:

1. It stands down automatically when a mod that owns that area is loaded.
2. It ships default-off until a measured before/after passes the existing benchmark protocol.
3. It never changes packet timing or gameplay-visible behaviour.

## Consequences

- The overhead budget blocks a release that exceeds it. Set 2026-11-11 from a measured baseline: Aurora CPU per frame at most 0.25 ms mean and 0.40 ms p95, no glass captures and at most 100 UI fills per frame, in a world with no screen open and factory defaults. Baseline, method and release check: [OVERHEAD_BUDGET.md](../OVERHEAD_BUDGET.md).
- The budget is expressed in terms that carry across machines: Aurora's CPU time per frame, glass captures per frame, fills per frame. The development machine (discrete GPU, no integrated graphics, 1440p at 240 Hz) cannot stand in for low-end hardware.
- Frame-time numbers for weak hardware come from testers through the benchmark in the diagnostics report (ADR 0004). Those numbers decide the default glass setting.
