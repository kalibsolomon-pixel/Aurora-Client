# Aurora Client

Aurora Client is a client-side Fabric mod for Minecraft. The current source
targets Minecraft 1.21.11. Build and test with the checked-in Gradle wrapper:

```sh
./gradlew test
./gradlew build
```

The runtime mod is `build/libs/aurora-<version>.jar`. Do not place a sources JAR
in a player's `mods/` directory. See `gradle.properties` and the built JAR's
`fabric.mod.json` for the exact version and dependency declarations. The
project is licensed under MIT; see `LICENSE`.

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
   suite, rebuilds with the project wrapper and JDK 25, and inspects exactly
   one runtime JAR. The JAR's `aurora` mod ID, embedded version, Minecraft,
   Fabric Loader, Fabric API, and Java requirements must pass. Its size and
   SHA-256 are recorded and checked again after the job handoff.
4. Approve `aurora-production` only after inspecting the candidate. The
   publication job refuses an existing tag or release, creates a **draft**
   release at the exact built commit, uploads the one verified JAR, and checks
   that the draft contains that complete asset before publishing. If upload
   or draft verification fails, the release stays a draft for manual review.
5. After publication, the workflow downloads the public release asset
   anonymously, checks its size and SHA-256 against the candidate, reopens
   the JAR, and confirms its embedded identity. A failure after publication
   requires manual investigation; immutable releases are never automatically
   rewritten or deleted.
6. Separately decide when to add the proven public asset to Aurora Launcher's
   production release manifest. A GitHub release by itself does not make an
   update visible to launcher users.

The tag is always `v<version>`, and the single release asset is named
`aurora-<version>.jar`. `stable` requires `X.Y.Z`; `beta` requires
`X.Y.Z-beta.N`; `nightly` requires `X.Y.Z-nightly.YYYYMMDD`. Beta and nightly
are GitHub prereleases. The developer chooses the next version; this workflow
never chooses or increments one. Existing tags and releases, including the
immutable `v2.1.1` release with no runtime asset, are never repaired by this
workflow.
