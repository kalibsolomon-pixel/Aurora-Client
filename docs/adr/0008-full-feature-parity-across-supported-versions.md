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
- No source preprocessor (Stonecutter or similar) is introduced. Version differences live in native adapters, never in runtime version branches. Common Minecraft-facing behavior lives in `adapters/shared`; native-free policy and interfaces belong in core or ports (ADR 0003).
- An overhead-gate failure on any supported line blocks the entire parity release; gates are never loosened to ship parity. The original 26.3 port failure was resolved by step 5 (see its acceptance evidence); step 5.5 reruns acceptance for every current adapter.
- Prerequisite engineering: the published 26.1 and 26.2 JARs were built from pre-refactor branches. Step 5.5 creates their adapters in the common tree before new feature work; [acceptance](../port/step-5.5.md) records the completed restructure.
- RELEASE_PROCESS and MOD_MATRIX now require per-line release gates, alongside the per-line builds.
