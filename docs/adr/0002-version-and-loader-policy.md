---
status: accepted
date: 2026-11-10
---

# Forward-only versions, Fabric-only releases

The main branch tracks the newest Minecraft release and receives all feature work. One previous version lives on a frozen branch that gets only crash and security fixes. Nothing is ever built for two versions at once, so no source preprocessor (Stonecutter or similar) is used. Minecraft's code is unobfuscated from 26.1 onward, which makes forward ports cheap and backports expensive; the policy follows that asymmetry.

Ports step through every intermediate release in order as build-and-boot checkpoints (for the first port: 26.1, then 26.2, then 26.3, the release the owner set as the target on 2026-11-11), with a separate Minecraft-specific JAR published for 26.1, 26.2 and 26.3 (owner revision, 2026-11-11). Artifact availability is separate from maintenance: all feature work remains on the newest version; the additional checkpoint artifacts do not create parallel feature branches. A universal JAR was never proposed. This keeps each batch of breakages small and attributable.

Aurora ships for Fabric only. NeoForge is deferred until there is real demand; the door is kept open by confining Fabric API usage (about 16 classes at the time of writing) to the version adapter described in ADR 0003.

## Considered options

- **Simultaneous multi-version builds.** Rejected: every feature would be written and accepted once per version.
- **Jumping straight to the newest release.** Rejected by the owner in favour of stepping through each version. The known cost is extra build-and-boot passes; checkpoint JARs are now also published at the owner's request.
- **Shipping NeoForge now.** Rejected: its audience is mostly modpack players, and it doubles the acceptance matrix.

## Consequences

- Support for pre-1.21 versions such as 1.8.9 is a possible future second product that would reuse the core module. It places no constraint on today's design: no Java 8 language limit on the core and no interface shaped around an engine that is not targeted.
- Loose goal once the layering exists: release within two weeks of Sodium supporting a new Minecraft version.
