# Aurora launcher activity protocol v1

Phase E1 adds an explicitly negotiated v2 identity extension documented in [PHASE_E1_ACTIVITY_PROTOCOL.md](PHASE_E1_ACTIVITY_PROTOCOL.md). The v1 contract below remains unchanged.

Target: Aurora Client 2.1.2 / Minecraft 1.21.11 / Fabric / Java 21. The mod reports
authenticated local gameplay identity. **Aurora Launcher is the sole Discord owner**:
connection, application/assets, privacy, formatting and clearing. No Discord SDK,
preferences, final presence formatter, telemetry or remote-control commands in the mod.

## Existing launcher boundary

Read-only inspection of launcher HEAD `01a2be65fe3b3809ba873a15b9ba6ef9f9a0dea2`
confirmed `discord.rs::GameActivity` holds supervised process/instance facts only;
`discord.rs::activity` applies independent preferences. `launch/resolve.rs::LaunchSpec`
is native/non-serializable with secret redaction; `launch/process.rs` uses Tokio
Command and retains ordinary host env after removing Java injection variables.
No existing bridge listener/wire schema/bootstrap exists. This contract matches the
future capability boundary in launcher ARCHITECTURE.md, DISCORD_SETUP.md and
FINAL_RELEASE_POLISH_ACCEPTANCE.md. COMPATIBILITY_ACCEPTANCE.md was also inspected.
Launcher code remains untouched; implementing the receiver there is a separate task.

## Bootstrap

Launcher binds **exclusive IPv4 `127.0.0.1:0` TCP before spawning Minecraft** and
obtains its ephemeral port. Generate a fresh random UUID session ID and **32
cryptographically random bytes**, encoded as 64 lowercase hex characters. Supply
these variables only to that matching supervised child:

| Variable | Exact representation |
| --- | --- |
| `AURORA_ACTIVITY_ENDPOINT` | `127.0.0.1:<port>`; decimal 1–65535, no leading zero |
| `AURORA_ACTIVITY_SESSION_ID` | canonical lowercase hyphenated UUID, 36 ASCII characters |
| `AURORA_ACTIVITY_CAPABILITY` | 64 lowercase hex characters, 256 random bits |
| `AURORA_ACTIVITY_PROTOCOL` | literal `1` |

All absent: no callbacks/thread/network/warnings. Partial/malformed/unsupported:
integration disabled with generic DEBUG only. No config/disk/command-line bootstrap,
DNS, port scanning or fallback; client endpoint uses literal `127,0,0,1` bytes.
Client never listens or serves HTTP. Standard Java NIO and existing Rust Tokio net
need no new dependencies; Windows named pipes would require extra Java native support.

Keep bootstrap in native memory/child environment, never persist/log it, expose it
to frontend DTOs, serialize LaunchSpec or put it in arguments. Include capability
in launcher secret redactions. Remove inherited bridge env before injecting fresh
values; never forward them to unrelated children or reuse a capability. Local actors
able to inspect child memory/environment can steal it; this is not TLS or an OS sandbox.

## Connection and authentication

Client makes **one attempt**, no reconnect. Launcher listener must already exist.
Cap pending handshakes (recommended **1**) and total attempts (**8**); a rejected peer
must not poison the valid child. Accept exactly one authenticated connection for
the current supervised session, then close the listener. Never bind wildcard/LAN
interfaces or a default fixed port.

Frames are compact UTF-8 JSON followed by **one LF byte**; byte limits include LF.
No BOM, CRLF, raw interior LF, duplicate fields or non-finite numbers. JSON-escape
strings; reject invalid UTF-8/types, unexpected fields, unknown states and versions.
Bound reads before parsing and bound parsing. No Java object serialization.

First frame (angle-bracket strings are notation, not example credentials):

```json
{"type":"hello","schemaVersion":1,"sessionId":"<canonical UUID>","capability":"<64 lowercase hex characters>"}
```

Verify version/types, exact current child session and constant-time capability
equality. Reply with **these exact bytes**:

```text
{"type":"accepted","schemaVersion":1}\n
```

Here `\n` is a single LF, not literal backslash+n. Exact order/spelling/spacing is
required: mod compares bounded bytes rather than deserializing server JSON. Rejection
means close without acceptance; never echo input. No activity before acceptance.
After acceptance send **no more bytes**. Unsolicited input disables the bridge and
cannot execute game actions.

Connect, whole acceptance read and each whole write have independent **2s total
deadlines**, including partial progress. Receiver hello has a total 2s budget;
pre-hello slow startup allowance is separate. Limits including LF:

| Frame | Bytes |
| --- | --- |
| Hello | 1024 |
| Acceptance | 128 |
| Activity | 4096 |

## Activity schema

| Required field | Type/value |
| --- | --- |
| `type` | string `activity` |
| `schemaVersion` | integer `1` |
| `sessionId` | matching canonical UUID string |
| `sequence` | integer 1–9223372036854775807, strictly increasing |
| `state` | string `MAIN_MENU`, `SINGLEPLAYER`, `MULTIPLAYER` |

Optional strings are **omitted**, never null:

| Field | Allowed state | Code-point limit / exact Minecraft source |
| --- | --- | --- |
| `worldDisplayName` | SINGLEPLAYER | 128; `Minecraft.getSingleplayerServer().getWorldData().getLevelName()` |
| `serverDisplayName` | MULTIPLAYER | 128; `Minecraft.getCurrentServer().name` (`ServerData`) |
| `serverAddress` | MULTIPLAYER | 255; `Minecraft.getCurrentServer().ip` (`ServerData`) |

Client removes ISO controls C0/C1, Unicode FORMAT characters, unpaired surrogates,
U+2028/U+2029, trims whitespace, truncates by code point and omits empty values.
Input work is capped at 4096 UTF-16 units per string. Launcher independently validates
and sanitizes before projection. Level name is user-facing, never save directory/path.
Address/name stay separate; no DNS/MOTD/arbitrary metadata. Missing data is omitted.
Direct-connect/LAN guest/Realms may have generic Minecraft ServerData labels; no guesses.

Non-sensitive example (sequence 2 after initial menu):

```json
{"type":"activity","schemaVersion":1,"sessionId":"4b609826-a9a8-4fa6-a7d4-57e7f373900e","sequence":2,"state":"MULTIPLAYER","serverDisplayName":"Fixture SMP","serverAddress":"example.invalid"}
```

Future Realms/LAN-specific states/extra fields require a future explicit protocol
version; v1 rejects unknown states/fields. No speculative telemetry now.

## Lifecycle, threading and ordering

AuroraClient initialization calls `LauncherActivityIntegration.initialize()`. Valid
bootstrap registers Fabric CLIENT_STARTED, AFTER_CLIENT_WORLD_CHANGE, play JOIN/
DISCONNECT and CLIENT_STOPPING. No new mixins/tick/render hooks.

Initial queued snapshot: **sequence 1 MAIN_MENU**, including splash/no gameplay level.
Subsequent lifecycle callbacks capture immutable state on Minecraft's lifecycle thread:

- `client.level == null` → MAIN_MENU, no identities.
- Loaded level + `hasSingleplayerServer()` → SINGLEPLAYER, including LAN host.
- Loaded level without integrated server → MULTIPLAYER; missing ServerData leaves
  this state with omitted identities.
- Pause/settings/inventory Screen does not change gameplay state.

Fabric 0.141.4's MinecraftMixin fires AFTER_CLIENT_WORLD_CHANGE at
`updateLevelInEngines` tail **only for non-null worlds**. DISCONNECT explicitly emits
MAIN_MENU rather than projecting the still-loaded old level during teardown. JOIN/
dimension changes can repeat snapshots; equality suppresses duplicates. Normal order:
menu → world → menu → server → menu; server A → menu → server B.

On multiplayer join, world-change can precede the new player's connection and thus
ServerData availability. It may emit MULTIPLAYER with identities omitted; JOIN at
`ClientPacketListener.handleLogin` return then emits the identified MULTIPLAYER snapshot.
Receivers must apply the whole snapshot, clearing omitted identities, and allow a later
snapshot in the same gameplay state. Never retain the previous server's identity.

One daemon `Aurora-LauncherActivity` owns all socket I/O/JSON encoding/deadlines/channel
closure. Callbacks sanitize/capture and offer to a **32-entry FIFO**; a short monitor
assigns sequence. No client-thread I/O, capacity wait or join. Overflow terminates
integration instead of dropping a clearing transition. Selector wakeup handles updates/
stop, idle readiness detects EOF/RST; no heartbeat, per-tick serialization, filesystem
polling, log scraping, renderer dependency or high-frequency traffic.

One ordered connection + monotonic sequence prevents stale application. Receiver must
require first sequence 1/menu, then reject duplicate/stale/non-integer sequences and
wrong sessions. Increasing gaps may be tolerated; never attach delayed data to a
replacement supervised process.

## Failure, shutdown and privacy

Malformed bootstrap/API/worker/connect/auth/version/disconnect/write/timeout/launcher
exit failures disable optional integration for the rest of the session. Minecraft
startup/gameplay/quit stay independent. Logs are generic DEBUG only, with no exception
objects, endpoint, capabilities or private identities.

CLIENT_STOPPING signals termination without waiting. Worker closes channel, clears
queue and releases resources. Quit while playing does not send a false menu. Pending
states may be discarded at quit: **EOF/supervised child exit is authoritative clearing**.
Launcher clears identities on EOF, malformed/stale input, child exit, session replacement
and launcher shutdown; generic process fallback may remain. Destroy capabilities and
snapshots at session end. Abnormal exit is covered by TCP/process supervision; no disk
shutdown hook or reconnect. Launcher EOF/RST is detected even during quiet gameplay.

World/server names and raw addresses are potentially private; never persist/log them
merely because the bridge exists. Excluded: account/Microsoft/Minecraft/refresh tokens,
credentials, chat, coordinates, inventory, biome, dimension, paths, mod list, logs,
arguments, arbitrary server metadata/resource-pack URLs, duplicate instance/version/
process-time facts. Mod never knows whether anything is published to Discord.

## Next launcher task

1. Own per-supervised-child listener/session/capability; inject four env values via
   native spawn while preserving LaunchSpec's secret boundary.
2. Implement bounded auth/exact acceptance/typed v1 validation/session+sequence checks/
   clearing above. Keep private snapshots separate from existing Discord allowlist.
3. Add independently opt-in **Show World**, **Show Server Name**, and (recommended)
   **Show Server Address**, default off. World off excludes world identity; Server off
   excludes all server identity. Friendly name opt-in must not implicitly expose raw
   address; address needs clear separate consent. Re-sanitize before projection.
4. Apply master presence toggle and field privacy before building Discord activity.
   Connection/assets/formatting/clearing remain launcher-owned. Bridge failures never
   fail Play; preserve existing process-known instance/version/elapsed-time sources.
5. Test real supervised launches/transitions, bad auth/version/frames, stale updates,
   session/owner exit and all privacy projections. No launcher-side code or client
   Discord settings/publishing belongs in this task.

## Development receiver

`tools/activity_bridge_receiver.py` is standard-library Python test tooling excluded
from the mod JAR. It binds exclusive loopback/ephemeral TCP, generates in-memory
bootstrap, launches supplied command with child env, validates auth/activity, caps
attempts and accepts one connection. Output contains only sequence/state/field
availability, never values/credentials. No game controls. Test-tool budgets: 120s
startup, 600s idle, 2s frames, 8 attempts, 1 pending connection; these lifetime limits
do not imply a production heartbeat. Use disposable directories/non-sensitive fixtures.

```powershell
python tools/activity_bridge_receiver.py -- ./gradlew.bat --no-daemon runClient
python -m unittest discover -s tools -p test_activity_bridge_receiver.py
./gradlew.bat --no-daemon test --tests 'com.aurora.client.launcher.*'
```

This tool is acceptance evidence, not completion of the real launcher half.
Primary references: [Java 21 SocketChannel](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/SocketChannel.html),
[Java 21 Selector](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/Selector.html),
[Fabric events](https://docs.fabricmc.net/develop/events/). Exact event timing and mapped
Minecraft APIs were verified from local 1.21.11 jars/cached Fabric 0.141.4 sources.
