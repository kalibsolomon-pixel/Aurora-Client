# Pulsar integration evidence

Integration branch: `feat/pulsar-integration`. Source and workflow parity are
documented in [PULSAR](../PULSAR.md). This report records acceptance evidence as
it is collected; unperformed checks are not passes.

## Source and scope

Phenomena is pinned at `0f01577a6ab8e475b7d106c7a107137ee074eb8a`. Its 44 pure
production Java classes and the report viewer were imported unchanged; the
reach precision fix below later changed `CombatEngine`, `AttackObservations`,
`Statistics`, `PerfectReach`, `FightAnalytics`, `MetricDefs` and one viewer
explanation, without changing the recording or report format. The council
review then changed player-facing copy and labels in `RecordingController`
(`/pulsar` notices; manual sessions ended by the player record
`Resolution.Source.MANUAL`), `Resolution` (the new `MANUAL` source, "Older
recording"), `Interpretation`, `MetricDefs`, `Format`, `ExportNotifications`
and `UiState` (scope labels), and added `Layout.scrollForThumb`. The new
source value is additive; older recordings read unchanged. The
352 retained upstream tests cover analytics, recording, server fixtures,
recovery, exports, privacy and layout. Native input/capture and the six-tab
dashboard are adapted to Aurora's three supported versions. The dormant HUD
is omitted; appearance and key editing route through Aurora.

## Initial acceptance

- Integrated 26.3 compilation and Java tests pass. Full three-version build
  verification is in progress.
- A disposable 26.3 OpenGL client passed disabled-startup checks: no runtime
  initialization and no `phenomena/` storage created.
- Enabling the feature, starting a manual session, ending it and waiting for
  the worker produced one JSON recording and an offline HTML report.
- Native screenshot review covered empty Overview, Recording settings,
  Server capture, empty History and a completed manual recording.
- No fresh acceptance on the six multiplayer servers has been performed.

Initial screenshots and logs: `build/perf-boot/pulsar-first-20261114b/`.

## Review findings addressed

The independent native/runtime audit found stale settings after disabled
profile changes, propagation of unsupported nested schemas, and an undersized
recovery budget. Settings reload independently of enablement and before save;
invalid nested schemas fall back safely with recoverable input; recovery now
accepts every individual checkpoint within upstream's 512 MiB limit.

The UI pass removed the large diagnostic block from Overview and retained it
in Server capture. Empty History no longer exposes a bank of disabled actions.
Canonical buttons, switches and slider painting replace standalone controls.
Dense dominance fills use a cached raster and list column layout is cached by
width/font style. Whole-word paragraphs and bounded ellipsis replace vanilla
word splitting in help text.

## Reach precision

The owner reported recorded maximum reach above the 3.0-block survival
limit. Cause: capture measured from the frame-interpolated eye to the
frame-interpolated target box, but attacks run in `Minecraft.tick` right
after `pick(1.0F)`, which decides the hit from current positions. Both
players' momentum over the leftover partial tick inflated the distance
(sprinting closure adds about 0.3 blocks). Creative attacks (5.0-block
range) were also mixed into the statistics.

A second path was found in-game: empty swings aimed near the opponent
record their miss distance (up to about 4.1 blocks) and entered the same
confirmation queue as real attacks. A server hit report could confirm the
oldest unresolved one, and when an empty swing and a real attack fell in
one two-tick input the empty swing stayed that input's measurement. Either
way a confirmed hit carried an out-of-range distance.

Fix: `PulsarReach` takes the vanilla pick's contact on the attacked entity
(the Reach Display measurement), falls back to a current-box ray only for
empty swings, and tags creative attacks so per-fight statistics, perfect
spacing, histograms and History aggregates exclude them. Server evidence no
longer confirms empty swings, and a real attack replaces an empty-swing
sample in its input. Each of the four `ReachPrecisionTest` regressions goes
red without its fix. Recordings made before the fix keep their stored
values.

In-game check (26.3, OpenGL, disposable fixture `build/perf-boot/pulsar-reach-20261114`,
probe `pulsar-reach`): the player sprints at a husk clicking the real
attack key, so attacks pass through the tick pick. Survival, two runs: 27
and 33 picked attacks, each recorded exactly the vanilla pick distance
(0 mismatches, maximum 2.738 and 2.747); the pre-fix formula on the same
attacks reached 3.169 (before the engine fix) and 3.022. The saved
recordings' Maximum reach was 2.738 and 2.747; empty-swing miss distances
up to 4.15 stayed out of the hit statistics. Creative: all 27 attacks were
tagged, and the recording's 6 hits contributed no reach samples. 26.1.2 and
26.2 share the identical capture source and pass tests and builds; no
in-game reach run was made on them.

## Deferred: replacing "uncertain" attempts

The owner does not want uncertain categories (unresolved attacks, uncertain
shields). Deferred until the owner can test in person, because the fix
depends on live-server behaviour that singleplayer cannot establish.

Evidence: an attack is unresolved when an attack was sent but neither a
server damage event nor a shield sound arrived within the correlation
window. In the five probe sessions of `build/perf-boot/pulsar-reach-20261114`,
31 of 33 unresolved attacks came less than 10 client ticks after the
previous confirmed hit on the same target: vanilla damage immunity, during
which the server ignores the attack and broadcasts no damage event.

Proposed change:

1. Track every damage event on the tracked opponent, not only local melee,
   since a third party's hit also starts immunity.
2. On expiry, classify an unconfirmed attack inside the immunity window as
   "During hit cooldown": neither hit nor miss, outside the accuracy
   denominator, like folded duplicates.
3. Classify the rest as "Not registered by server": in the denominator,
   stated as a fact about server evidence rather than as uncertainty.
   Blocking-and-facing attacks without a shield sound follow the same two
   rules, because a vanilla block always plays the sound to nearby players.
4. A new statistics schema version; older recordings keep their categories.

Open questions for the in-person test:

- Latency: the server's gap since the last hit exceeds the client's by about
  one round trip, so attacks near the 10-tick boundary need the measured
  ping (player list latency) to classify.
- Configured PvP servers may change hurt time or hit delay; each of the six
  adapters needs its own immunity length, verified on the server.
- Whether a stronger hit during immunity (vanilla applies the difference)
  is visible to the client at all.

Test protocol: on one configured server and one vanilla-rules server, at
known ping, record spaced hits (one per second), spam clicking (every tick)
and shield blocks facing and not facing. Compare the proposed classes with
what happened in game.

## Council

Independent read-only reviews of native Vulkan captures (26.3, 1400x900 and
800x600, factory and square/0.19 themes) and the source, scored out of 10
against the owner's target of 9. Briefs, reviews and capture fixtures are in
the ignored `build/pulsar-council/` and `build/perf-boot/pulsar-council-r*`.

Membership changed during review on the owner's instruction:

- Kimi K3 (`kimi-code/k3`) failed twice without output; the owner chose Kimi
  K3.1 Pro for that seat. In round 9 it ran 15 minutes without a result and
  was replaced by GPT-6.1 Sol, which also produced none.
- GPT-6.1 Terra and LongCat 2.5 were replaced by Haiku 5.5 and Sonnet 5.5
  from round 2.

| Round | Inkling 2 Small | GLM-5.3-Flash | Haiku 5.5 | Sonnet 5.5 | Kimi K3.1 Pro |
|---|---|---|---|---|---|
| 1 (earlier council) | | | 7 | 7.5 | 8.5 (Kimi) |
| 2 | 8.5 | 8.5 | 8.5 | 8.5 | 8.5 |
| 3 | 9 | 9 | 8.5 | 8.5 | 8.5 |
| 4 | 9 | 9 | 8.5 | 9 | 8.5 |
| 5 | 9 | 9 | 8.5 | 8.5 | 9 |
| 6 | 9 | 9 | 8.5 | 8.5 | 8.5 |
| 7 | 9 | 9 | 8.5 | 9 | 8.5 |
| 8 | 9 | 9 | 8.5 | 9 | 9 |
| 9 | 9 | 9 | 9 | 9 | no result |

Round 1 also had GPT-6.1 Terra at 7.5. In round 9 the four reviewers that
responded scored 9 with no blocking findings, and the owner accepted that as
the unanimous result. The final iteration applied their remaining notes:
manual sessions with no declared outcome read "No result", Overview's
average reach uses the same eligible hits as Offense, and the first-run
Overview says "recording".

Rounds 2-9 changed, among others: reach and evidence copy, removal of
internal version numbers, one "recording"/"fight" terminology rule, an
"Ended by you" result source for player-ended manual sessions, the keyboard
readout moved to the footer band, Aurora's scroll fades, narration by visible
label for every setting control, wrapping weapon chips, bounded chart
legends, confirmed-only status ink, a corrected Fight Flow empty state, and
scrollbar dragging that keeps the grab point. Deferred: replacing the
uncertain categories (above).
