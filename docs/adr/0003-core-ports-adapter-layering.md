---
status: accepted
date: 2026-11-10
---

# Layer by dependency direction: core, ports, version adapter

The codebase is split by which way dependencies point, not by kind of code:

1. **Core**: a separate Gradle module with no Minecraft on its compile classpath. Theme, config, layout, policies and feature logic.
2. **Ports**: a small set of interfaces the core draws and queries through (draw surface, text measurement, input, world and entity snapshots).
3. **Version adapter**: one per Minecraft version. It implements the ports and owns every mixin, the glass renderer and all Fabric API calls.

"Features" is not a layer. Each feature is core logic plus adapter hooks.

The payoff is cheaper ports and a testable core, not multi-version builds (see ADR 0002).

## Considered options

- **Three layers by kind: rendering/UI/performance, features and options, version bindings.** This was the original idea. Rejected because rendering and UI are the most version-volatile code in the repo and cannot sit underneath the bindings, and because most features are mixins, which are version-bound by nature.
- **Convention only, no separate module.** Rejected: a convention erodes; a module that cannot compile a Minecraft import does not.

## Consequences

- The ports are extracted only after the first forward port, from what actually broke. Designing them earlier would be a guess, because this codebase has never been through a major port.
- Moving the packages that are already Minecraft-free (`theme`, `module`, most of `config`, `modrinth`, `launcher`) into the core module needs no interface design and happens before the port.
- At the time of writing, 82 files call `GuiGraphics` directly and 133 distinct Minecraft classes are imported outside the mixins, dominated by `Minecraft`, `GuiGraphics`, `Component` and `Font`. The draw surface is therefore the port that removes the most coupling.

## Extraction after the 26.3 port (2026-11-11)

`ports/` is a separate Java library with five native-free interfaces: physical-pixel
rectangle coverage, active-font text measurement, native frame-deadline time,
physical key state and world identity. Core depends on ports; the adapter depends
on both. Ports depend only on the JDK. Shape rasterization, pacing policies,
key edge detection and scope-key policy live in core and use these interfaces.

The root Loom project is the build facade for exactly one adapter, selected by
`minecraft_version`: `adapters/minecraft-26.3/` in this checkout. It owns all client
sources and resources, including every mixin, glass shader/renderer, native input
binding, HUD hook and Fabric registration. Keeping `runClient`, `test` and `build`
at the root preserves existing launcher, matrix and Gradle init-script contracts.
Older version branches remain independent checkpoints; they are not compiled
alongside the active adapter.

The draw port reuses the existing live-GUI/cache seam. It does not introduce a
renderer or implement step 6. Entity rendering still uses native entities inside
the adapter; a broad entity abstraction would be speculative while those hooks
remain native. Existing pure geometry and smoothing policies already accept
primitive values. Further extraction should follow a concrete consumer.
