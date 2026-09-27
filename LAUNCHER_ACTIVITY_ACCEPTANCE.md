# Launcher activity bridge acceptance — 2026-09-27

Client-side implementation only. Aurora Launcher remains the sole Discord owner;
its repository was inspected read-only and has no implemented receiver yet.
See [LAUNCHER_ACTIVITY_PROTOCOL.md](LAUNCHER_ACTIVITY_PROTOCOL.md) for the complete
wire contract, privacy boundary and exact next launcher task.

## Baseline and scope

- Client starting HEAD: `8ff9cf45be8a8c3dfa6c3a428a841d0af837760c`, branch `master`.
- Client has no `origin/main`. Actual `origin/master` equals starting HEAD; starting
  relationship 0 ahead / 0 behind, merge-base equals starting HEAD.
- Clean initial client worktree: 361 tracked files, zero protected non-ignored
  untracked files. No existing source/manifests/assets removed.
- Launcher inspected at `01a2be65fe3b3809ba873a15b9ba6ef9f9a0dea2`; its initial
  worktree status, 181 tracked hashes and 607 protected untracked hashes preserved.
- No pushes, launcher modifications, Discord connection/SDK/settings, dependencies,
  feature cards, config persistence, mixins or renderer changes.

## Deterministic validation

| Check | Result |
| --- | --- |
| Java tests | 369 passed; zero failures/errors/skips; 337 existing + 32 bridge |
| Bridge breakdown | 9 snapshot, 9 bootstrap/protocol, 14 real-loopback worker tests |
| Python tooling | 26 passed: 10 bridge receiver + 16 existing release-tool tests |
| `compileClientJava check build` | Passed; Java release 21, tests executed with Microsoft OpenJDK 21.0.7 |
| Formatting | `git diff --check` passed; no project formatter task configured |
| Production runtime JAR | Remap/build and existing release-artifact metadata verifier passed |

Bridge cases cover authoritative projection, missing identities/LAN-host classification,
Unicode/control sanitization and bounded work/output, malformed/absent bootstrap,
literal loopback restriction, bad versions/authentication, exact bounded ACK,
deduplication/ordered clearing/server changes/later identity availability, refusal/no retry,
partial-handshake timeout, malformed/oversized/unsolicited input, idle EOF/launcher exit,
RST/write failure, nonreading peer's total write deadline, overflow, worker exceptions,
redacted diagnostics, shutdown during handshake and while a world is active.
Receiver tests reject bad capability/session/version, stale ordering, invalid UTF-8,
duplicate/unknown fields, invalid types, oversized frames and unsafe display strings.

The initial unadjusted Windows test run hit **17 existing source-contract failures**:
those tests compare source substrings containing literal LF against checkout CRLF.
Unrelated sources/tests were left intact. Final full tests used an external mirror
of repository inputs, replacing CRLF with LF **only in Java source files**, as the
working directory for source-text assertions. All compiled production/test inputs
still came from the actual repository. A temporary Gradle init script sets that
working directory / `user.dir` and the Java 21 test executable. This is an explicit
test-environment workaround, not an assertion that plain Windows `test` is clean.

Commands used (audit directory is outside this repo):

```powershell
./gradlew.bat --no-daemon -I "$audit/tests.gradle" compileClientJava check build
python -B -m unittest discover -s tools -p 'test_*.py'
git diff --check
```

## Live Fabric acceptance

Minecraft 1.21.11 / Fabric API 0.141.4 / Loader 0.19.2, Java 21.0.7. Disposable game
and server directories outside the repository; the existing `run/` was not used.
A temporary separate acceptance mod/source set drove vanilla APIs and checked exact
fixture identities. It is absent from committed source and from the production JAR.
The committed Python receiver generates credentials in memory and logs only states,
sequence numbers and identity-field availability.

| Scenario | Observed result |
| --- | --- |
| Standalone, all bridge env absent | Aurora initialized, Main Menu reached, normal `Minecraft.stop()` / `Stopping!`, Gradle exit 0 |
| Authentication | Exclusive ephemeral loopback receiver accepted matching per-launch session/capability |
| Initial menu | Sequence 1 MAIN_MENU, no identity fields |
| Singleplayer | Sequence 2 SINGLEPLAYER; exact WorldData display name verified against a non-sensitive fixture |
| Pause screen in singleplayer | Loaded world and Singleplayer retained; no false menu snapshot |
| World leave | Sequence 3 MAIN_MENU, identities cleared; vanilla `disconnectFromWorld` stopped/saved integrated server |
| Multiplayer join | Sequence 4 MULTIPLAYER without identity at world-change, sequence 5 with exact distinct ServerData name/address at JOIN |
| Pause screen in multiplayer | Multiplayer retained; no false menu snapshot |
| Multiplayer disconnect | Sequence 6 MAIN_MENU, identities cleared |
| Normal Quit | Bridge EOF observed and receiver cleared activity; Minecraft `Stopping!`, Gradle/receiver exit 0 |

Multiplayer used a disposable dedicated Minecraft server bound to **127.0.0.1 only**,
with offline authentication for the Fabric development player. No public server or
real account credentials were used. Minecraft emitted the expected development-player
Realms/profile-key authorization failures; they did not prevent joining or normal quit.
LAN guest/host and Realms were not live-tested; their projection/fallback is documented.
Server A→menu→server B and failure/shutdown scenarios beyond the table were socket/unit
tests, not separate live Minecraft runs.

Early driver attempts called a lower-level teardown API without first disconnecting
the world and were terminated after diagnosis; these are **not** counted as successful
quit tests. The final run used the same `disconnectFromWorld` entrypoint as PauseScreen.
A subsequent early-identity assertion was corrected to respect the documented optional
metadata contract; final acceptance requires the later exact multiplayer identity.
A diagnostic thread dump showed the bridge worker idle in its selector, independent
of the test driver's teardown wait. No production renderer/lifecycle workaround added.

## Artifact inspection

`build/libs/aurora-2.1.2.jar`: **2,467,058 bytes**.

SHA-256: `eb2b06bc3955881ee9ff0dc561c25ced617de822a276c61fa3a9f79343602777`.

- Exactly one `fabric.mod.json`: id `aurora`, version `2.1.2`, environment `client`,
  entrypoint `com.aurora.client.AuroraClient`, Minecraft requirement `~1.21.11`, Java `>=21`.
- Eight launcher-package classes including two nested classes, all class major 65.
- No duplicate ZIP entries, nested dependency JARs, Discord library, acceptance mod,
  test classes/tools, fixture identities/addresses or capability literals.
- Build/dependency declarations and mixin manifest unchanged. Production JAR was
  inspected; live runs used Fabric development classes, not a release-installed JAR.

## Repository safety and remaining boundary

Every commit was preceded by status/name-status/whitespace and inventory checks.
All 361 baseline client tracked files remain, with zero tracked deletions or missing
files. Client had no protected untracked files. Launcher HEAD/status plus all 181
tracked and 607 protected untracked file hashes remained identical. Generated Minecraft,
server, runtime, diagnostics and build outputs were not staged or committed.

Actual launcher receiver, supervised-session handoff, native secret redaction,
typed activity storage/clearing and default-off world/server/address privacy projection
remain a separate launcher task. No end-to-end Discord acceptance claimed.
