---
status: accepted
date: 2026-11-10
---

# A mixin that cannot apply disables its feature instead of crashing

Aurora is run in mod sets nobody has tested. When a mixin cannot apply, the feature that depends on it turns itself off and says so in the UI and in the diagnostics report, and the game still starts. Before this decision the mixin config was `required: true` with `defaultRequire: 1`, so one failed injection in any of roughly 73 mixins stopped the game at startup.

This requires a mixin config plugin, a health flag per feature, and an audit of every mixin for whether its failure is survivable. It also requires knowing which mixins each feature depends on, so the three hand-maintained registries (`FeatureManager`, `FeatureRegistry`, `ModuleManager`) collapse into one source of truth that records that mapping.

## Considered options

- **Keep crash-on-failure.** Rejected: acceptable for a pinned launcher environment, not for a public mod.
- **Blanket `require = 0` with no reporting.** Rejected: features would silently do nothing and testers could not say what broke.

## Consequences

- Known-bad mods are declared in `fabric.mod.json` under `breaks`.
- First-tier tested matrix, boot-tested with Aurora before every release (not every commit): Sodium, Iris, VulkanMod, ImmediatelyFast, EntityCulling, Lithium, Mod Menu. Everything else relies on fail-soft.
- A "copy diagnostics" action produces a local report: mod list, which features disabled themselves and why, render backend, recent errors, and a short built-in benchmark. The player sends it by hand.
- This is built on 1.21.11 before the port, so that on each new version a broken mixin shows up as a disabled feature in a client that still boots.
