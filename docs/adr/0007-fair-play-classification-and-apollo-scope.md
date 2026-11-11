---
status: accepted
date: 2026-11-10
---

# Features are classified for fair play; advantage features ship off

Competitive servers ban clients by reputation, and Compliance Mode was removed in September 2026 with nothing replacing it. Every feature is therefore classified as one of: cosmetic, information vanilla already shows, information advantage, or timing. Features in the advantage class ship default-off, so that nothing questionable is on unless the player chose it.

First default-off list:

- Minimap entity dots for players
- Entity health for players (its own switch since 2026-11-11; before that the whole feature was off)
- Other players' totem pop counts
- FreeLook

Everything else keeps its current default, including hitboxes (vanilla F3+B shows them), Full Bright, No Fog and toggle sprint.

Amended 2026-11-11 by the owner: Full Bright and No Fog now ship off too. They had shipped on (the release defaults set both true) and are the two shipped-on features a server owner was most likely to question. Entity Health gained a separate players switch, off by default.

Better Hitreg stays in the client at its current default by owner decision. It is recorded as local feedback only: it does not change what the game sends for movement or combat, or when. It is the feature a server owner is most likely to ask about, so that description must stay accurate.

Corrected 2026-11-11 after reading the code. The first wording said it "sends no packets", which was not exact: two optional keys, unbound by default, send something when pressed. Switch Hand changes the vanilla Main Hand option, and the practice scoreboard's Send key posts the score as a chat message. Its current default is off: the release defaults ship `hitregEnabled` false.

## Apollo

Support for Lunar Client's Apollo server software is limited to honouring server restrictions (a server turning Aurora features off) and showing server-sent information. Aurora never presents itself as Lunar Client.

Apollo work does not start until two things are checked: Apollo's license terms, and whether registering the `lunar:apollo` plugin channel is how servers decide a player is on Lunar. Neither was verified when this was written. "Lunar Client" and "Apollo" are Moonsworth trademarks.

## Considered options

- **Leave all defaults as they are.** Rejected: it leaves no honest answer for server owners.
- **Full Apollo client support.** Rejected: collecting Lunar-only treatment by looking like Lunar puts Aurora's reputation in another company's hands.

## Consequences

- The classification lives in `FeatureCatalog` and is published as `docs/FAIR_PLAY.md`. Tests keep the two identical, check that every switch on the default-off list is off in a fresh install, and scan the source for anything that sends to a server.

- Saved preferences still win over defaults, so existing players keep what they had enabled.
- No feature may send non-vanilla packets or alter client tick or packet timing. Tick Sync was removed in September 2026 for this reason.
