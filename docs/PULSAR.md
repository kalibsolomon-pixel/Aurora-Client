# Pulsar analytics

Pulsar brings [Phenomena](https://github.com/kalibsolomon-pixel/Phenomena) into Aurora as an optional feature. The imported source is pinned at `0f01577a6ab8e475b7d106c7a107137ee074eb8a`. The upstream analytics, event model, recording formats and offline report viewer retain the `dev.phenomena` namespace. Aurora owns feature enablement, configuration, profiles, key bindings, native adapters and dashboard styling.

Pulsar ships **off**, classified as an information advantage because its measurements describe other players. Enable its icon in Features, then open its feature details and choose Open dashboard, press K, or run `/pulsar`. Capture runs while enabled even when the dashboard is closed. Disabling Pulsar finalizes an active recording as Interrupted and continues accepted background saves. Opening the dashboard while disabled allows inspection of local history.

## Dashboard and recording

| Surface | Available workflows |
|---|---|
| Overview | Start/end manual recording, recording/export folders, latest result and evidence, recent aggregate metrics, interpretations, trend chart, recent fights |
| Offense | Accuracy, reach and spacing, aim, weapon timing, critical and sprint hits; scope/weapon filters |
| Defense | Incoming attribution, shield evidence, jump timing, damage and knockback |
| Fight Flow | Combos, encounter dominance, exchanges, older/newer loaded fight; participant scopes remain available in manual-session reports |
| History | Sort, outcome and hits-only filters; selection, details, offline report, export and confirmed delete |
| Settings | Recording, server capture and diagnostics, combat exchanges, attack classification, audio feedback, chart appearance, data/exports, reset, Aurora key settings |

Keys 1–6 select tabs. Tab navigates controls; arrow keys operate sliders, lists and tab peers; Enter activates; Page Up/Down and Home/End scroll. Escape and Done return to the invoking Aurora screen. Local command aliases `/phenomena` and `/kwm` remain available with the same recording and diagnostics subcommands. No additional network sends are introduced.

Automatic capture preserves the six upstream server adapters and their evidence rules: MCPvP, CatPvP, Minemen, PvPHQ, FishFFA and PvPLegacy. Unconfigured hosts use generic combat capture. A configured match requires its announcement; inactivity or a death never substitutes for it. Manual sessions continue across arena/world replacement and interruptions produce recoverable records. Fixture coverage does not establish fresh live-server acceptance on these Minecraft versions.

Accuracy remains confirmed hits divided by unique eligible attempts minus confirmed shield blocks. Uncertain attempts remain in the denominator. Reach and aim are client geometry. Reach is the distance from the eye to the point vanilla's tick attack pick hit, the same measurement as Reach Display, so in vanilla survival a hit never exceeds 3.0 blocks (servers and weapon attack-range components can change the range); frame-interpolated positions are never used, because each player's momentum over the leftover partial tick would add distance the game did not allow. Attacks made in creative (5.0-block range) are recorded with their true distance but kept out of every reach statistic. An empty swing sends no attack packet, so server evidence never confirms one, and a real attack in the same two-tick input replaces an empty swing as that input's measurement; an empty swing's miss distance therefore never becomes a hit's reach. Critical conditions are not a verified damage multiplier. Jump timing does not establish knockback cancellation. Results retain classification, evidence source and confidence independently. Historical missing data stays unavailable rather than becoming zero.

## Preferences, files and privacy

Aurora stores `pulsarEnabled`, `pulsarKey` (portable SDL key combo), and nested `pulsarSettings` in `config/aurora.json`. Profiles snapshot the nested settings independently. Reset restores the Pulsar prefix; controller references remain stable when a profile replaces the settings object. Unsupported nested schemas fall back safely. Invalid input in aurora.json is backed up before replacement; an imported profile's original fields remain recoverable.

Existing `phenomena/` recordings and exports are reused in place. Upstream `kwamster/` migration remains non-destructive. Standalone preferences are imported explicitly in Settings → Data & exports; import reads `config/phenomena.json`, falling back to `config/kwamster.json`, and preserves the input. Aurora's appearance, key and feature state remain under Aurora's controls.

| Data | Location relative to the game directory |
|---|---|
| Recordings | `phenomena/fights/` |
| Offline reports | `phenomena/exports/` |
| Manual recovery checkpoints | `phenomena/pending-sessions/` |
| Opt-in local diagnostics | `phenomena/diagnostics/` |
| Server compatibility inventory | `phenomena/server-compatibility.json` |

Nothing is uploaded. Export anonymization is on by default; local recordings retain their known opponent labels. Source-compatible recording and report formats allow standalone history to remain readable. Recovery is performed on the bounded storage worker, with the upstream 512 MiB individual-recording limit and a bounded startup pass. Additional preserved checkpoints can recover on a later startup.

The retired upstream HUD, summary popup and movement indicator remain absent. Aurora supplies the existing theme, font, roundness and window material; standalone palette/density/key settings do not create a second design system. The offline viewer remains the upstream self-contained report, with its payload format and privacy behavior unchanged; only its reach explanation was updated for the pick-based measurement.

## Engineering and verification

Pure analytics and storage reside in core; native capture, feedback and fail-soft mixins reside in each version adapter; dashboard/runtime glue is shared. The feature gate is checked fresh by every native hook. Tick callbacks use `Timed`. Disabled startup does not initialize storage. Shapes use Aurora's renderer; the dense dominance area is rasterized once into a native-resolution layer, and texture retirement waits for a frame boundary.

See the integration report in `docs/reports/2026-11-14-pulsar-integration.md` for actual tests, screenshots, performance measurements and independent council results. Upstream source provenance is also recorded in THIRD_PARTY_NOTICES.md; its pinned repository contains no LICENSE file, so no upstream license is asserted here.
