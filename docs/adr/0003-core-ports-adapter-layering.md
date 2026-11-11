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
