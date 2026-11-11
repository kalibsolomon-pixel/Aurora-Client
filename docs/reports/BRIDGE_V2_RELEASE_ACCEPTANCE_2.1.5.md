# Bridge v2 release acceptance — Aurora Client 2.1.5 candidate — 2026-09-28

**Verdict: READY FOR OWNER RELEASE APPROVAL.** This is the recovery candidate
after the 2.1.4 publication incident (`BRIDGE_V2_RELEASE_ACCEPTANCE.md`,
"Publication outcome addendum"). Nothing is published, tagged, uploaded, or
pushed by preparing this candidate, and the launcher production pin remains
2.1.3. The owner must approve the exact candidate JAR hash below before any
publication.

## Incident recovery status

- The approved 2.1.4 candidate (`bb99647e`, SHA-256
  `5c2da84e3c33492c2979740c33acb520834bb8429eabde647acd7f280f37a4a8`) passed
  its full technical review — including the real-Minecraft v2 smoke test — but
  was never a standing public release: its immutable release was deleted
  during metadata remediation and GitHub permanently reserved the `v2.1.4` tag
  name. Treat `v2.1.4` as permanently unavailable.
- Recovery = this 2.1.5 candidate: **the same reviewed runtime under a new,
  truthful version**. `RELEASE_PROCESS.md` now codifies the draft-first
  publication sequence, and the release verifier's draft check was fixed and
  hardened (drafts are located through the authenticated release listing, not
  `releases/tags`, which never resolves drafts) with deterministic tests.

## Candidate identity

- Version **2.1.5** (stable); tag will be `v2.1.5` (created only at
  owner-approved publication).
- Source commit: `a5497f680a38883a40afc2011fe418c59821029b`
  (`chore: prepare client version 2.1.5 for the bridge-v2 release candidate`).
- Runtime source lineage: the reviewed 2.1.4 runtime source `bb99647e`, plus
  documentation/tooling only, plus the one-line `mod_version` bump. No Java
  source, resource, build-script, dependency, mixin, or bridge-protocol
  change of any kind (verified by commit-tree diff and by the artifact
  comparison below).
- Build: Gradle 9.4.1 wrapper, Temurin 25.0.4.1 launcher JVM (identical
  toolchain to the approved 2.1.4 candidate), command
  `./gradlew.bat --no-daemon -I <audit>/tests.gradle clean compileClientJava
  check build` (init script affects only the test working directory).
- File `aurora-2.1.5.jar`, **2,468,545 bytes**, SHA-256
  `fdc344e28c95a93b84af4b4fb1e61f9d3cc5774339f753f45f603c901df324d6`.

## Artifact audit

`tools/verify_release_artifact.py inspect` + `verify` passed against the exact
source commit. ZIP integrity OK; **444 entries**; no duplicate entries; no
nested JARs; no test harness (`LauncherHistoryContractHarness` absent), no
Python tools, no Discord code, no acceptance receiver, no fixture data or
credentials. Embedded metadata: `fabric.mod.json` id `aurora`, version
**2.1.5**, environment `client`, Minecraft `~1.21.11`, Java `>=21`, Fabric
Loader `>=0.16.0`, Fabric API `*`. Build inputs: Minecraft 1.21.11, Loader
0.19.2, Fabric API 0.141.4+1.21.11 (all unchanged from 2.1.4).

## Comparison to the approved 2.1.4 candidate

Byte-level ZIP comparison against the preserved approved 2.1.4 bytes:

- size delta **0 bytes** (both 2,468,545);
- entry count 444 vs 444; **0 added, 0 removed**;
- **exactly 1 changed entry: `fabric.mod.json`**, and its complete content
  diff is `"version": "2.1.4"` → `"version": "2.1.5"`;
- all 443 other entries — every class and resource — are byte-identical.

**Therefore the bridge/runtime code cannot have changed**: 2.1.5 is the
already-reviewed bridge-v2 candidate under a new version string.

## Verification results

- Client suite (fresh `clean` build): **373 tests, 0 failures, 0 skipped** —
  identical count and result to the approved 2.1.4 review run — including
  `ActivityProtocolTest` (10), `ActivitySnapshotTest` (11),
  `LauncherActivityBridgeTest` (15), and the bridge bootstrap/failure-isolation
  tests (bridge v1 regression, v2 frames, world/server identity, clearing,
  malformed-input handling, privacy/redaction).
- Python tooling: **29 tests passed** (26 prior + 3 new deterministic tests for
  the hardened draft verification).
- `compileClientJava`, `check`, `build`: passed.

## Prior real-Minecraft v2 smoke test — applicability

**The 2.1.4 real-game smoke test remains applicable.** That test exercised the
exact runtime bytes that 2.1.5 carries (443/444 entries byte-identical; the
single differing entry is the mod version string, which the smoke test did not
depend on). Per the recovery task's rule, a second destructive real-world test
is not required; the runtime-delta proof above is the basis.

## Bridge status

- **Bridge v1:** unchanged — same code paths, same tests, still compatible
  with existing launcher versions.
- **Bridge v2:** implementation identical to the reviewed 2.1.4 candidate;
  negotiated only when the launcher bootstrap requests protocol 2 with a
  reviewed artifact.

## Future publication procedure

Per `RELEASE_PROCESS.md`: owner approval of the exact hash → `inspect` /
`verify` / `collision` → **draft** release with explicit `--target
a5497f680a38883a40afc2011fe418c59821029b` → upload the exact approved JAR →
`draft` verification → publish as a separate step → read-only `public`
verification. No automatic delete/recreate recovery after publication, ever.

## Release notes draft (for the eventual release)

> **Aurora Client 2.1.5**
>
> - Adds secure launcher activity bridge v2 support, negotiated by Aurora
>   Launcher when a reviewed client build is in use.
> - Enables launcher-side Recent Worlds and Recent Servers history (and
>   history-based Quick Launch) when used with a compatible Aurora Launcher;
>   world and server identity stays local to the launcher.
> - Preserves full bridge v1 compatibility with existing launcher versions.
> - Fixes the singleplayer save identity reported to the launcher.
> - No Discord integration, telemetry, or new dependencies are added.
> - Requires Minecraft ~1.21.11, Java 21+, Fabric Loader >=0.16.0.
>
> Note: version 2.1.4 was never publicly released due to a release-process
> issue; 2.1.5 is the first public release containing these changes.

## Current state

- PUBLICATION STATUS: **NOT PUBLISHED** (no `v2.1.5` tag, release, draft, or
  asset exists; verified against the GitHub API).
- LAUNCHER PIN: **2.1.3** (launcher repository untouched).
- Preparation commits are local on `master` and **not pushed**.

## Remaining manual gates

1. Owner approval of the exact candidate hash above.
2. Owner-authorized publication strictly per `RELEASE_PROCESS.md` (draft
   first, explicit target, verify, then publish).
3. Launcher pin update to 2.1.5 only after independent public-artifact
   verification, per the 2.1.4 acceptance document's activation plan.
