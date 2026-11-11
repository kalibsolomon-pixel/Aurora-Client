---
status: accepted
date: 2026-11-11
---

# Full feature parity across every supported Minecraft line

ADR 0002 froze older Minecraft lines to crash and security fixes and rejected simultaneous multi-version builds, because every feature would be accepted once per version. The owner revisited that on 2026-11-11 and chose the opposite trade: every Minecraft line from 26.1 onward receives all feature work, forever. One source tree on master holds one adapter per supported line (`adapters/minecraft-26.x`; adapters for 26.1 and 26.2 are created by re-applying the PR #13 layout), while core and ports stay shared and compile once. Each release builds one version-pinned JAR per line from that tree. A universal JAR remains rejected.

The window only grows: when a new Minecraft version is ported, its adapter is added and the older lines stay. 1.21.11 (Aurora 3.2.0) predates 26.1 and stays immutable, outside the parity window. 1.8.9 remains a distant-future target: when that work starts, its core is a translated fork of the day's core, so today's core keeps no Java 8 language constraint. This reverses ADR 0002's second-product framing and is recorded here.

## Considered options

- **Frozen previous line (ADR 0002).** Rejected by the owner: supported versions must be fully up to date, not merely stable.
- **Main plus scheduled backport trains.** Rejected: older lines lag by a train, and the lag grows with the window.
- **A universal JAR binding to any modern instance.** Still rejected: ~73 mixins and the adapter APIs differ per line; fail-soft (ADR 0004) would silently disable features instead of adapting, and per-line acceptance would lose attribution of breakage.

## Consequences

- Every feature is written once but accepted once per line: mod-matrix boots and the overhead gate run per line, per release. Release effort grows linearly with the window, and the Sodium two-week goal becomes harder to hold; cadence is judged per release.
- No source preprocessor (Stonecutter or similar) is introduced. Version differences live in adapter modules, never in conditional code; anything shared enough to duplicate in two adapters belongs in core or ports (ADR 0003).
- The standing 26.3 OpenGL overhead-gate failure blocks every line's release until it is resolved; gates are never loosened to ship parity.
- Prerequisite engineering: the published 26.1 and 26.2 JARs were built from pre-refactor branches, so their adapters do not exist in the current layout. Creating them is the first roadmap task, before new feature work.
- RELEASE_PROCESS and MOD_MATRIX gain per-line release gates when the restructure lands, in the same change that adds the builds.
