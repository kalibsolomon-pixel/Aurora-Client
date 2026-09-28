# Bridge v2 release acceptance — Aurora Client 2.1.4 candidate — 2026-09-28

**Verdict: READY FOR OWNER RELEASE APPROVAL.** A reviewed production candidate was
built and audited. Nothing was published, tagged, uploaded, or pushed, and the
launcher production pin remains 2.1.3. The owner must approve the exact candidate
JAR hash below before any publication.

## Production baseline

- Public production client: Aurora Client 2.1.3, tag `v2.1.3`, source commit
  `e978d0b5545e4991137e0fc1387e9897e752dfc7`, artifact `aurora-2.1.3.jar`,
  2,467,058 bytes, SHA-256 `4bf78dc1ef8f18e124377575c508ca357327be1c230b203e9f8181bdcb9ebc81`.
  The downloaded release asset was re-hashed during this review and matches exactly.
- Production 2.1.3 speaks bridge v1 only; launcher world/server identity collection
  is therefore inactive in production today.

## Development commits reviewed

Development HEAD at review start: `687f24c72d1c47cff7c50d192e2771804d78c89d`,
exactly 2 commits ahead of the production source commit, 0 behind `origin/master`
(which equals the production source). Commit-tree comparison
`e978d0b..687f24c` touches 11 files, 150 insertions / 18 deletions:

1. `ad40760` feat: add negotiated gameplay identity to activity bridge — 5
   production files in `launcher/` only (`ActivityProtocol`, `ActivitySnapshot`,
   `BridgeBootstrap`, `LauncherActivityBridge`, `LauncherActivityIntegration`),
   3 test files, and the two protocol documents.
2. `687f24c` test: exercise compiled v2 bridge worker interoperability — adds the
   test-only `LauncherHistoryContractHarness` and documents it.

No committed change touches rendering, Sodium/OpenGL/Vulkan compatibility, GUI,
settings, config persistence, gameplay, mixins, dependencies, `fabric.mod.json`,
version metadata, assets, telemetry, or logging. All changes are bridge-v2 work.

## Preparation commits added by this review

3. `6ea2306` chore: prepare client version 2.1.4 for bridge-v2 release candidate —
   one-line `gradle.properties` `mod_version` bump.
4. `bb99647` fix: normalize the world save path so v2 save identity is real —
   genuine defect found by the real-game smoke run (below). `LevelResource.ROOT`
   has id `.`, so `getWorldPath(ROOT).getFileName()` returned the literal `"`
   .`"` component, which `safeWorldId` correctly rejects; the v2 `worldSaveId`
   was therefore always omitted in real Minecraft. `normalize()` resolves the
   trailing dot to the true save directory component.

## Bridge v1 compatibility

Unchanged by design and by tests. The v1 wire format, bootstrap validation,
hello/acceptance bytes, activity fields, sequence rules, one-attempt connection,
2s deadlines, frame limits, and failure disabling are identical to production
2.1.3 (the v1 code paths are the same code; v2 branches only on the
launcher-owned `AURORA_ACTIVITY_PROTOCOL` bootstrap value). The 5-argument
`ActivitySnapshot.project` overload is preserved; v1 frames never carry
`worldSaveId`/`serverTarget`, and the launcher receiver independently rejects
v2 fields in v1 frames. Launcher receiver v1 regression tests pass.

## Bridge v2 protocol review

Negotiation is explicit and launcher-owned: bootstrap accepts only protocol
`1` or `2`; the client never reconnects or downgrades after a failed v2
handshake. Hello/activity frames carry `schemaVersion:2`; acceptance is the
exact byte string `{"type":"accepted","schemaVersion":2}\n`. Transport remains
literal-IPv4-loopback-only TCP (byte literal `127,0,0,1`; endpoint regex
`127\.0\.0\.1:[1-9][0-9]{0,4}`, port ≤ 65535) — the client cannot connect to
arbitrary hosts through this feature. Session is a canonical UUID, capability
is 64 lowercase hex (256 random bits validated), frames are bounded JSON lines
(1024/128/4096 bytes) with 2s total I/O deadlines, sequences are strictly
increasing from 1, one connection attempt, no reconnect loop, all NIO on one
daemon worker thread. Malformed bootstrap disables the bridge for the session
with generic DEBUG logging only; gameplay is unaffected.

## Identity semantics

- Singleplayer `worldSaveId` is the save directory component from the integrated
  server's `LevelResource.ROOT` (post-normalize), not the display name, not an
  absolute path, not frontend-controlled. The real-game smoke run proves the
  display name ("Activity Bridge Fixture") and save identity
  ("activity-fixture") are emitted independently and correctly.
- Multiplayer `serverTarget` is Minecraft's current `ServerData.ip` connection
  field, not the server-list label. The launcher canonicalizes it
  (`host:port`, default 25565, bracketed IPv6, lowercased DNS host) before
  persistence; the client sends it bounded and sanitized only.
- Both fields are state-gated (singleplayer/multiplayer only), cleared on every
  MAIN_MENU snapshot, and rejected-to-omitted client-side when malformed
  (separators, traversal, drive prefix, reserved Windows names, trailing
  dot/space, controls/format characters, >128/>255).
- Launcher-side validation (`WorldSaveId::parse`, `ServerTarget::parse`) matches
  the client contract as defense in depth; invalid v2 identity is rejected
  rather than used.

## Privacy audit

The client emits world/server identity only over the authenticated loopback
bridge to the launcher. No Discord code, SDK, telemetry, or publishing exists in
the client; the launcher remains the sole Discord owner. Diagnostics are generic
DEBUG strings without exception detail, endpoints, capabilities, or identity
values; `BridgeBootstrap.toString()` and `ActivitySnapshot.toString()` are
redacted. The launcher keeps local history identity and Discord display fields
in separate consent domains: Discord projection reads only display fields behind
the existing independent Show World / Show Server / Show Server Address toggles
(default off), and the launcher regression suite proves validated history
targets do not serialize into Discord even when display toggles are enabled.

## Failure isolation

All bridge failures (absent/malformed bootstrap, refused connection, timeout,
rejected authentication, malformed/unsolicited input, EOF, receiver closure,
worker exception, queue overflow) disable the optional bridge for the session
and cannot crash Minecraft, block the game thread, stall world loading, break
multiplayer, reconnect repeatedly, or log uncontrolled detail. Callbacks only
capture bounded immutable snapshots; `publish` never waits; overflow terminates
the bridge rather than losing a clearing transition.

## Verification results

- Client suite: `compileClientJava check build` passed; **373 tests, 0 failures,
  0 skipped** (both on Microsoft OpenJDK 21.0.7 and on the Temurin 25.0.4.1
  launcher JVM), including the real-loopback v2 worker test (identity transport,
  separation, clearing). Python tooling: 26 tests passed. The historical
  CRLF/LF source-assertion workaround (external LF-normalized mirror as test
  working directory via Gradle init script; repository inputs compiled
  unchanged) was reproduced; plain in-repository `test` on Windows remains
  unqualified, unchanged from the previous acceptance.
- Launcher regression: **cargo test 562 passed, 0 failed, 21 ignored**
  (explicit opt-in fixtures) plus 6 icon tests; **npm test 130 passed**;
  svelte-check 0 errors/0 warnings; `cargo fmt --check` and
  `cargo check --all-targets` passed. **No launcher source change was needed**
  to accept bridge v2 — the E1 receiver already implements it.
- Java/Rust interoperability (explicit ignored test
  `compiled_java_v2_worker_interoperates_with_rust_receiver`): **passed** against
  the exact current client compiled classes and the exact current Rust receiver
  through the real supervised-spawn boundary under managed Java 21.
- Real Minecraft smoke test: **PASSED** (details below).

## Real Minecraft v2 smoke test

Disposable fixture only (surviving temp fixture from the prior phase: installed
Fabric 0.19.5 game, managed `java-runtime-delta` 21.0.7, loopback offline-mode
dedicated server bound to 127.0.0.1, temporary acceptance driver mod, offline
synthetic player identity). The **exact candidate JAR** was placed in the
fixture mods directory. A v2 receiver (audit-directory Python tooling outside
both repositories) generated the bootstrap with `AURORA_ACTIVITY_PROTOCOL=2`
and validated the negotiated frames. Observed authenticated snapshots:

| Seq | State | worldDisplayName | worldSaveId | serverDisplayName | serverTarget |
| --- | --- | --- | --- | --- | --- |
| 1 | MAIN_MENU | — | — | — | — |
| 2 | SINGLEPLAYER | Activity Bridge Fixture | **activity-fixture** | — | — |
| 3 | MAIN_MENU | — | — | — | — |
| 4 | MULTIPLAYER | — | — | — (pending ServerData) | — |
| 5 | MULTIPLAYER | — | — | Activity Fixture SMP | **127.0.0.1:54151** |
| 6 | MAIN_MENU | — | — | — | — |

The first run of this smoke test exposed the real `worldSaveId` defect fixed in
`bb99647`; after the fix the same run passes with exit code 0 and no receiver
errors. This is the only test in either repository that exercises the real
Minecraft identity-extraction APIs for v2; both harnesses bypass them by
construction.

## Candidate version and artifact

- Version **2.1.4** (stable). No `v2.1.4` tag, release, or asset exists
  (verified against the GitHub API); tags end at `v2.1.3`. Repository policy is
  patch bumps on the same 1.21.11 target.
- Source commit: `bb99647e043025f61ef5e6b2bdfab62504143680` (candidate =
  `6ea2306` version bump + `bb99647` fix on reviewed `687f24c`).
- Build: Gradle 9.4.1 wrapper, launcher JVM Temurin 25.0.4.1, `javac
  --release 21`, Fabric Loom remap 1.16-SNAPSHOT; command
  `./gradlew.bat --no-daemon -I <audit>/tests.gradle clean compileClientJava
  check build` (the init script affects only the test working directory).
  Building with the Microsoft OpenJDK 21 javac instead produces byte-identical
  intent but different lambda desugaring numbering in 38 unrelated classes;
  the published 2.1.3 toolchain (PATH-default javac) was chosen so the
  artifact delta stays minimal.
- File `aurora-2.1.4.jar`, **2,468,545 bytes**, SHA-256
  `5c2da84e3c33492c2979740c33acb520834bb8429eabde647acd7f280f37a4a8`.
- Embedded metadata: `fabric.mod.json` id `aurora`, version `2.1.4`,
  environment `client`, entrypoint `com.aurora.client.AuroraClient`,
  Minecraft `~1.21.11`, Java `>=21`, Fabric Loader `>=0.16.0`, Fabric API `*`.
  Build inputs: Minecraft 1.21.11, Loader 0.19.2, Fabric API 0.141.4+1.21.11.
- Repository release verifier (`tools/verify_release_artifact.py`):
  `inspect` + `verify` passed against the exact source commit.

## Artifact content audit

ZIP integrity OK; 444 entries; no duplicate entries; no nested JARs; 371
classes all major version 65; `aurora.mixins.json` identical to 2.1.3 (69
entries). The deterministic E1 Java/Rust test harness
(`LauncherHistoryContractHarness`), Python tools, Discord code, acceptance
driver, fixture data, credentials, 64-hex capability literals, production
endpoints, and local/build-machine paths are all absent. Embedded URLs are the
pre-existing Modrinth API client, font-license metadata, and `example.com`
placeholders only. `AURORA_ACTIVITY_*` names appear only in
`BridgeBootstrap.class`.

## Comparison to published 2.1.3

444 entries both; size delta **+1,487 bytes**; 0 added, 0 removed; **436
entries byte-identical**; the only 8 differing entries are the 7 changed
launcher classes (`ActivityProtocol`, `ActivitySnapshot`, `ActivitySnapshot$State`,
`BridgeBootstrap`, `LauncherActivityBridge`, `LauncherActivityBridge$Update`,
`LauncherActivityIntegration`) and `fabric.mod.json` (version string). Every
delta is attributable to the bridge-v2 implementation plus the candidate
version metadata. An earlier build compiled with the OpenJDK 21 javac showed
38 additional class deltas from lambda-name desugaring only (identical
structure and size; e.g. `lambda$save$0`→`lambda$save$1`); that is a
toolchain artifact, not a source change, and does not appear in this candidate.

## Launcher activation plan (NOT applied)

After the owner approves and the artifact is actually published, the launcher
changes are exactly:

1. `src-tauri/production/aurora-releases.json` — add the 2.1.4 stable release
   entry: `auroraVersion 2.1.4`, `minecraftVersion 1.21.11`,
   `fabricLoaderVersion 0.19.5`, Java 21, artifact URL
   `https://github.com/kalibsolomon-pixel/Aurora-Client/releases/download/v2.1.4/aurora-2.1.4.jar`,
   SHA-256 `5c2da84e3c33492c2979740c33acb520834bb8429eabde647acd7f280f37a4a8`,
   size 2,468,545, and the same pinned Fabric API coordinates as 2.1.3.
2. `src-tauri/src/launch/activity_bridge.rs` — update
   `BRIDGE_ARTIFACT_SHA256` (and its doc comment) to the candidate digest and
   switch the digest-gated session preparation from protocol 1 to protocol 2
   (`Session::prepare()` → `prepare_for_protocol(2)`, or equivalently thread the
   protocol from `supported`). Eligibility remains bootstrap-active +
   Minecraft 1.21.11 + exact reviewed digest.
3. Tests that reference the pin: `distribution.rs::
   production_release_is_exact_and_separate_from_the_development_fixture`
   (release count/resolution) and `activity_bridge.rs::
   artifact_support_uses_verified_identity_not_version_or_filename`.

Until then the launcher keeps using production 2.1.3 with protocol 1; an
unreviewed artifact hash receives no bridge at all.

## Release notes draft

> **Aurora Client 2.1.4**
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

## Repository safety

- Client: starting HEAD `687f24c`, 2 ahead / 0 behind `origin/master`
  (= production source `e978d0b`); final HEAD `bb99647`, 4 ahead / 0 behind;
  **not pushed**. The ~276/277 pre-existing line-ending-only modified entries
  and the protected untracked
  `tools/__pycache__/verify_release_artifact.cpython-312.pyc` were preserved
  untouched; no `git clean`, reset, restore, or normalization was run. All
  361+ tracked files remain; no deletions occurred at any commit.
- Launcher: HEAD `8196d07` = `origin/main`, 0/0, **not pushed**; no source
  change. `src-tauri/Cargo.toml` line-ending-only state and all protected
  diagnostic files preserved and not committed. Local-only housekeeping: origin
  URL updated from `kalibsolomon-pixel/aurora-launcher` to the canonical
  `kalibsolomon-pixel/Aurora-Launcher` confirmed by the GitHub API (case-only
  rename; no commit, no content change; remote HEAD unchanged).
- Smoke-test fixture and all audit tooling live under
  `%TEMP%\aurora-214-candidate-audit` and the pre-existing disposable fixture
  root, outside both repositories; the fixture mods directory was restored to
  its original contents afterward.

## Remaining manual gates

1. Owner approval of the exact candidate hash above.
2. Owner-executed publication (tag `v2.1.4`, GitHub release, JAR upload) — not
   created here.
3. Launcher pin update per the activation plan after publication, with its own
   regression run.
