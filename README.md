# Aurora Client

Aurora Client is a client-side Fabric mod with 38 quality-of-life, PvP and
performance features, a themed GUI and optional glass. It works in any
Fabric-compatible launcher and has no telemetry or paid cosmetics. See the
[feature and fair-play catalog](docs/FAIR_PLAY.md).

The current source supports Minecraft 26.1.2, 26.2 and 26.3 and requires Java 25. Build and test
with the checked-in Gradle wrapper:

```sh
python tools/parity.py
# Or one line (26.3 is the default):
./gradlew test build -Paurora.minecraft=26.2
```

The runtime mod is `build/minecraft-<minecraft>/libs/aurora-<version>-mc<minecraft>.jar`. Do not place a sources JAR
in a player's `mods/` directory. See `gradle.properties` and the built JAR's
`fabric.mod.json` for the exact version and dependency declarations. The
project is licensed under MIT; see `LICENSE`. Code, fonts and libraries from
other projects are listed in `THIRD_PARTY_NOTICES.md`.

Contributors start with [CONTRIBUTING.md](CONTRIBUTING.md) and `AGENTS.md`.
Report vulnerabilities using [SECURITY.md](SECURITY.md).
Decisions are in `docs/adr/`, the plan in
`docs/ROADMAP.md`, reference documents in `docs/` and dated reports in
`docs/reports/`.

## Installation and version policy

Download the JAR matching your Minecraft version from
[GitHub Releases](https://github.com/kalibsolomon-pixel/Aurora-Client/releases).
Install Fabric Loader and the matching Fabric API, then put the Aurora JAR in
that instance's `mods` folder. Install only one Aurora JAR; core and ports are
already bundled.

Distinct artifacts target **26.1.2**, **26.2** and **26.3**.
See [release availability and source evidence](docs/STATUS.md).
Every supported line receives the current feature set from one source tree,
with a version-specific adapter and JAR (ADR 0008). The parity window grows
with new ports. Published checkpoint JARs remain immutable; parity starts with
the next release. There is no universal JAR. Check release notes for availability.

Sodium is optional. Minecraft 26.2+ provides native Vulkan. Iris is an OpenGL
shader mod; Vulkan shader mods need their own compatibility evidence. See the
[pinned tested matrix](docs/MOD_MATRIX.md).

Information-advantage features ship off. Existing settings are migrated. Respect
server rules. Broken Aurora mixins disable affected features for the session and
report through Settings → General → Support → **Copy Diagnostics**; nothing is
uploaded automatically.

## Manual production releases

**Pushing code does not release Aurora.** The ordinary `build` workflow runs on
pushes and pull requests but only produces CI artifacts. The separate
**Publish Aurora Release** workflow runs only through `workflow_dispatch` on
the default `master` branch. Its publication job uses the existing
`aurora-production` environment, which requires reviewer approval.

1. Prepare and review the source commit. Set `mod_version` in
   `gradle.properties` to the intended release version, update the Minecraft
   and dependency declarations as needed, and verify the source locally.
   The release workflow validates these declarations; it does not edit them.
2. Ensure the intended source commit is available in the canonical GitHub
   repository. Manually run **Publish Aurora Release** from `master`, providing
   `version` without `v`, an explicit `channel`, and a `source_ref`. A full
   commit SHA is preferred. A branch or tag is resolved once to a recorded
   commit SHA before testing or building.
3. Review the read-only validation job. It runs the complete Gradle test
   suite for every supported line, builds with the wrapper and JDK 25, and requires
   current per-line GPU matrix/overhead evidence. It inspects the complete set
   of Minecraft-specific JARs. Each JAR's `aurora` mod ID, embedded version, Minecraft,
   Fabric Loader, Fabric API, and Java requirements must pass. Its size and
   SHA-256 are recorded and checked again after the job handoff.
4. Approve `aurora-production` only after inspecting the candidate. The
   publication job refuses an existing tag or release, creates a **draft**
   release at the exact built commit, uploads every verified JAR, and checks
   that the draft contains exactly that complete set before publishing. If upload
   or draft verification fails, the release stays a draft for manual review.
5. After publication, the workflow downloads every public release asset
   anonymously, checks its size and SHA-256 against the candidate, reopens
   the JAR, and confirms its embedded identity. A failure after publication
   requires manual investigation; immutable releases are never automatically
   rewritten or deleted.
6. Separately decide when to add the proven public asset to Aurora Launcher's
   production release manifest. A GitHub release by itself does not make an
   update visible to launcher users.

The tag is always `v<version>`, with one
`aurora-<version>-mc<minecraft>.jar` asset per supported line from the same source.
Set **make_latest** for the current complete stable release. `stable` requires `X.Y.Z`; `beta` requires
`X.Y.Z-beta.N`; `nightly` requires `X.Y.Z-nightly.YYYYMMDD`. Beta and nightly
are GitHub prereleases. The developer chooses the next version; this workflow
never chooses or increments one. Existing tags and releases, including the
immutable `v2.1.1` release with no runtime asset, are never repaired by this
workflow.
