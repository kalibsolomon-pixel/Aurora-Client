---
status: accepted
date: 2026-11-10
---

# Features are classified for fair play; advantage features ship off

Competitive servers ban clients by reputation, and Compliance Mode was removed in September 2026 with nothing replacing it. Every feature is therefore classified as one of: cosmetic, information vanilla already shows, information advantage, or timing. Features in the advantage class ship default-off, so that nothing questionable is on unless the player chose it.

First default-off list:

- Minimap entity dots for players
- Entity health for players
- Other players' totem pop counts
- FreeLook

Everything else keeps its current default, including hitboxes (vanilla F3+B shows them), Full Bright, No Fog and toggle sprint.

Better Hitreg stays in the client at its current default by owner decision. It is recorded as local feedback only: it sends no packets and changes no timing on the wire. It is the feature a server owner is most likely to ask about, so that description must stay accurate.

## Apollo

Support for Lunar Client's Apollo server software is limited to honouring server restrictions (a server turning Aurora features off) and showing server-sent information. Aurora never presents itself as Lunar Client.

Apollo work does not start until two things are checked: Apollo's license terms, and whether registering the `lunar:apollo` plugin channel is how servers decide a player is on Lunar. Neither was verified when this was written. "Lunar Client" and "Apollo" are Moonsworth trademarks.

## Considered options

- **Leave all defaults as they are.** Rejected: it leaves no honest answer for server owners.
- **Full Apollo client support.** Rejected: collecting Lunar-only treatment by looking like Lunar puts Aurora's reputation in another company's hands.

## Consequences

- Saved preferences still win over defaults, so existing players keep what they had enabled.
- No feature may send non-vanilla packets or alter client tick or packet timing. Tick Sync was removed in September 2026 for this reason.
