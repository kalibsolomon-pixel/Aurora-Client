---
status: accepted
date: 2026-11-12
supersedes: 0005 (scope paragraph and rule 2 only; the overhead budget stays)
---

# Aurora ships its own client optimizations, on by default, when measured

ADR 0005 limited Aurora to a budget on its own cost and allowed small optimizations of vanilla code only default-off. On 2026-11-12 the owner asked for a set of loading, CPU, texture, world-render and map optimizations, most of them on by default with no switch in the UI. This ADR replaces 0005's scope paragraph and its rule 2. Its overhead budget, its rules 1 and 3, and everything in [OVERHEAD_BUDGET.md](../OVERHEAD_BUDGET.md) stay in force.

An optimization may ship when all of these hold:

1. **Measured.** Its batch's before/after benchmark (the diagnostics frame benchmark, the feature switched off with `-Daurora.mixin.disable`) shows a gain or no regression on every supported line it applies to, and the overhead gate passes. An optimization with no measured gain is dropped, not shipped off.
2. **Stands down.** When a mod that owns the area is loaded, its vanilla mixin is not applied (`AuroraMixinPlugin`, `MixinHealth.Cause.STOOD_DOWN`) and diagnostics say which mod it yielded to. The exception is an explicit patch into that mod, built against the version pinned in `gradle/minecraft-versions.json` and re-verified on every pin update. Sodium is the only such mod today.
3. **Invisible.** It changes neither packet timing nor anything gameplay-visible. A visible trade-off (a hidden overlay, F3 lines updating less often, more memory) gets a switch on the Settings tab's Performance page. Everything else is a config-only gate.
4. **Gated.** Every mixin still has a boolean `perf*` gate (ADR 0004), so fail-soft and `-Daurora.mixin.disable` keep working.

The new defaults apply to existing installs on purpose, so no migrator is added. The `perf*` fields are machine settings: they are kept out of profiles and out of the portable release defaults (`defaults/3.2.0.json`), so their initializers are their defaults.

## Considered options

- **Keep 0005 (default-off until benchmarked).** Rejected by the owner. Default-off optimizations help almost no one, and the benchmark gate still applies, at the time of shipping rather than as a default.
- **Port the features from another mod.** Not available. Each is written from its description.
- **Patch third-party internals (LambDynamicLights, ScalableLux, Particle Core, Xaero's).** Rejected: those mods own their performance. The three Xaero's ideas were applied to Aurora's own minimap and world map instead.

## Consequences

- One Settings-tab feature, `performance`, owns every `perf*` mixin, so mixin health and FAIR_PLAY.md report them in one place.
- Native Vulkan exists only on 26.2 and 26.3. Vulkan-only optimizations do not apply on 26.1. ADR 0008 parity means the same optimization on every line where its backend exists.
- The riskiest items (occlusion culling, sky in the main render pass, early swapchain acquire, background vertex copy) start as measured prototypes and ship only on a benchmark gain. On 2026-11-12 the owner deferred them, with the other GPU-side items, until testers on GPU-bound hardware can supply evidence; the development machine is CPU-bound.
