# UI polish overhaul (2026-11-14)

Branch `feat/ui-polish-overhaul`, PR #37. Goal set by the owner: iterate until every reviewer
on a council of independent models rates a set of UI screenshots at least 9/10, and update the
design language and theme tokens so later work inherits the result.

**Outcome so far: not met.** Twenty rounds moved the council's mean overall score from 5.7
(R0) to a plateau of 6.7–7.4; no reviewer has given 9 overall. Individual qualities reached
8 (GLM-5.3 Flash: hierarchy, type, colour, consistency, polish, comfort in R18). The asks
every reviewer repeats are mostly ones the owner has ruled on (see *What holds the score*).

## Method

- Each round: build, boot a disposable 26.3 dev client at factory defaults
  (`build/ui-polish/capture.sh`), capture 12 screens at 1600×900, GUI scale 2 (feature grid,
  list, three detail pages, three Settings pages, empty Waypoints, HUD, title screen, grid
  hover), crop the window chrome, and send the same brief to every reviewer. Reviewers never
  see each other's ratings or the lead's.
- Council: GLM-5.3 Flash, Haiku 5.5, Inkling 2 Small, GPT-6.1 Terra, LongCat 2.5 (R0–R9,
  replaced for slowness), Particle-1 and MiniMax M3.1 Flash (R13 on). A reviewer slower than
  five minutes was cancelled; Opus 5.5 stood in for GPT/Terra in R0–R3.
- Claims were checked against the pixels before acting; several were wrong (accent on
  icons that are grey, numbers "not over their slots" that are, a "dimmer" heading that is not).

## Overall ratings per round

| Round | Mean | Lowest | GLM-5.3 Flash | GPT-6.1 Terra | Haiku 5.5 | Inkling 2 Small | LongCat 2.5 | MiniMax M3.1 Flash | Opus 5.5 (lead, stand-in for GPT) | Opus 5.5 (lead, stand-in for Terra) | Particle-1 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| R0 | 5.7 | 5 | 6 | - | 5 | 6 | 6.5 | - | - | 5 | - |
| R1 | 6.7 | 6 | 7 | - | 7 | 6 | 7 | - | - | 6.5 | - |
| R2 | 6.6 | 6 | 7 | - | 6 | 6 | 7 | - | - | 7 | - |
| R3 | 7.1 | 7 | 7 | - | 7 | 7 | 7 | - | 7.5 | - | - |
| R4 | 6.8 | 6 | 6 | 7 | 6 | 7 | 8 | - | - | - | - |
| R5 | 7.4 | 7 | 8 | 7 | 7 | 7 | 8 | - | - | - | - |
| R6 | 7.4 | 7 | 8 | 7 | 7 | 7 | 8 | - | - | - | - |
| R7 | 7.0 | 7 | 7 | 7 | 7 | 7 | 7 | - | - | - | - |
| R8 | 7.4 | 7 | 8 | 7 | 7 | 7 | 8 | - | - | - | - |
| R9 | 7.2 | 7 | 7 | 7 | 7 | 7 | 8 | - | - | - | - |
| R10 | 7.0 | 7 | 7 | 7 | 7 | 7 | - | - | - | - | - |
| R11 | 6.8 | 6 | 7 | 7 | 6 | 7 | - | - | - | - | - |
| R12 | 7.0 | 7 | 7 | 7 | 7 | 7 | - | - | - | - | - |
| R13 | 6.7 | 6 | 7 | 7 | 7 | 7 | - | 6 | - | - | 6 |
| R14 | 6.7 | 6 | 7 | 7 | 7 | 7 | - | 6 | - | - | 6 |
| R15 | 6.7 | 6 | 7 | 7 | 7 | 7 | - | 6 | - | - | 6 |
| R16 | 6.7 | 6 | 7 | 7 | 7 | 7 | - | 6 | - | - | 6 |
| R17 | 6.5 | 6 | 7 | 7 | 6 | 7 | - | 6 | - | - | 6 |
| R18 | 7.2 | 7 | 8 | 7 | 7 | 7 | - | 7 | - | - | 7 |
| R19 | 6.8 | 6 | 7 | 7 | 7 | 7 | - | 6 | - | - | 7 |
| R20 | 6.7 | 6 | 7 | 7 | 6 | 7 | - | 7 | - | - | 6 |

Per-quality ratings for every round are in `build/ui-polish/scores/` and were shown to the
owner as a chart after each round.

## What changed

The design-language record is `docs/DESIGN_LANGUAGE.md` (section "UI polish overhaul —
2026-11-14" and §3.2, §4.1, §11.2a, §11.4–11.11, §13). In short:

- **Palette and tokens:** graphite factory background (#16171D) and Indigo accent (#5C6BC0);
  flat accent fills; one opaque primary action tone; window edge one step stronger.
- **Type roles:** `AuroraFontRenderer.Role` TITLE (11 px raster) and HEADING (9 px); body 8 px.
- **Feature state:** the icon colour alone (accent on, neutral grey off), after four
  indicator designs were tried and retired (dot, mini switch, On/Off pill, bottom bar). Tiles
  and rows carry a faint resting hairline that strengthens on hover; a settings chevron on
  hover or focus.
- **Settings and detail pages:** content-fit windows resting on row boundaries; group
  summaries; sub-section captions; scrollbar tracks; hex values beside colour swatches;
  inline sliders with bounded tracks (dropdown width, half for ten or fewer positions, one
  length per page); keyboard glyph on keybind pills; a live crosshair preview.
- **Lists and managers:** 24 px list rows; empty-state pattern (icon, heading, hint, one
  action); empty Waypoints shows no toolbar; "Features" sidebar item (was "Mods").
- **HUD:** HUD text shadow defaults to Normal; durability bars on by default; the Keystrokes
  Space key no longer shows a stray line (rounded-shape rasterizer fix, with a regression test).

## Owner decisions (2026-11-14)

Graphite background; Indigo accent; detail windows fit content with the description under the
title; "Sprint & Sneak" name; Settings pages fit content; hover settings chevron; accent-filled
Done everywhere (opaque), kept beside the empty Waypoints call to action; white per-feature
colour defaults kept; flat accent fills; armor durability bars on; feature state is the icon
colour only, with no Enabled switch on detail pages; "Features" sidebar label; inline sliders
not spanning the window, one track length per page; 24 px list rows; HUD text shadow Normal by
default (new installs; saved configs keep their value, as with the durability-bar default).

## What holds the score

Across R13–R20 the top issue from almost every reviewer was an explicit on/off marker on
feature tiles, which the owner ruled out twice. The next most frequent asks also run against
owner decisions: a single navigation pattern instead of the sidebar plus Settings tab row,
fixed window heights instead of content-fit ones, and one accent button per screen. Asks not
yet taken: a back affordance on detail pages (Done already returns), one section-header
style for groups and sub-sections, labelled HUD durability numbers.

## Verification

- `./gradlew test -Paurora.minecraft=26.3`: all tests pass at every pushed round.
- Every round booted the dev client at factory defaults (OpenGL, 1600×900, GUI scale 2) and
  covered the twelve screens listed above, in-world and on the title backdrop. Theme settings
  other than the factory defaults were not swept for these rounds.
- `python tools/parity.py`: 26.1.2, 26.2 and 26.3 build and pass their tests (after a clean
  26.2 build; a stale compiled test class from an older branch had broken test discovery).
- `tools/overhead_check.py` on a 26.3 OpenGL boot at factory defaults (six diagnostics runs,
  two warm-up): CPU per frame 0.133–0.149 ms mean, 0.178–0.226 ms p95, within budget in all
  four judged runs.
