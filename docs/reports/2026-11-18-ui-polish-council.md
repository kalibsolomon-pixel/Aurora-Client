# UI polish council (2026-11-18)

Branch `ui/polish-council`, PR #62, stacked on #61. It follows the 2026-11-14 overhaul
([report](2026-11-14-ui-polish-overhaul.md)) and the material lighting work (bevels and 3D
effects). Goal set by the owner: a council of independent vision models rates fresh
screenshots each round until every reviewer gives 9 overall, while every control keeps an
accurate click target.

**Outcome: not met; the owner ended the loop after R14, when the council had converged.**
R14 was the first unanimous 8 (mean 6.8 in R0). No reviewer gave 9 overall; consistency
and cohesion drew 9s. By R14, many remaining asks contradicted each other or other rounds:
- primary vs secondary value text;
- a disabled-slider fill vs none;
- a brighter vs dimmer scrollbar.

Others described pixels the captures do not contain, such as decimal armor numbers or
clipped headers.

## Method

- **Capture.** Each round boots a disposable 26.3 dev client at factory defaults
  (`build/ui-council/run.py` with the parity probe's `look`, `server`, `expect-*` and
  `press` commands). It captures 14 screens at 1600×900, GUI scale 2:
  - HUD;
  - the Features grid, the grid with hover, and the Features list;
  - three detail pages and three Settings pages;
  - Profiles and empty Waypoints;
  - the HUD editor and the title screen.

  The window chrome is cropped and every reviewer gets the same brief.
- **Brief.** It lists the owner's fixed decisions so they are not penalized. It also includes
  a vision check, two values readable only from the images. Reviewers never see their own
  earlier ratings or anyone else's.
- **Council.** It was GPT-6.2 Sol (low), GPT-6.1 Terra (medium), GLM-5.3 Flash, Inkling 2 Small
  and Hy4 Flash.
  - Particle-1 sat R0–R5 and was dropped at the owner's request because it re-read too much.
  - ERNIE 5.5 Flash has no image input and declined R0.
  - GLM ran at reasoning variant low from R6.
- **Interaction check.** Every round, before review:
  - **Layout boxes.** Every interactive face drawn that frame must sit on the layout box that
    input hit-tests at its centre (`ui-bounds`, ≥ 90 % coverage).
  - **Real clicks.** Probe clicks at the drawn centres of grid tiles, list rows, a detail
    toggle, Done, the Theme Enabled row, Separate controls and the HUD editor's
    Aurora Settings button must change the expected setting or screen.

  The final capture checked 51 faces in 9 reports with 0 problems, and every click
  landed.
- **Verification.** Claims were checked against the pixels before acting. Several were wrong:
  a "grey-accented" Sprint icon, header clipping, decimal durability numbers and a hint
  artifact.

## Overall ratings per round

| Round | Mean | GPT-6.2 Sol | GPT-6.1 Terra | GLM-5.3 Flash | Inkling 2 Small | Particle-1 | Hy4 Flash |
|---|---|---|---|---|---|---|---|
| R0 | 6.8 | 7 | 7 | 7 | 7 | 6 | 7 |
| R1 | 7.2 | 7 | 7 | 7 | 8 | 7 | 7 |
| R2 | 7.3 | 7 | 7 | 8 | 8 | 7 | 7 |
| R3 | 7.3 | 7 | 7 | 8 | 8 | 7 | 7 |
| R4 | 7.7 | 8 | 7 | 8 | 8 | 8 | 7 |
| R5 | 7.5 | 8 | 7 | 8 | 8 | 7 | 7 |
| R6 | 7.8 | 7 | 8 | 8 | 8 | - | 8 |
| R7 | 7.8 | 8 | 8 | 8 | 8 | - | 7 |
| R8 | 7.8 | 8 | 8 | 8 | 8 | - | 7 |
| R9 | 8.0 | 8 | 8 | 8 | 8 | - | 8 |
| R10 | 8.0 | 8 | 8 | 8 | 8 | - | 8 |
| R11 | 8.0 | 8 | 8 | 8 | 8 | - | 8 |
| R12 | 7.8 | 8 | 8 | 8 | 8 | - | 7 |
| R13 | 7.8 | 8 | 8 | 8 | 8 | - | 7 |
| R14 | 8.0 | 8 | 8 | 8 | 8 | - | 8 |

## Category means, first and last round

| Category | R0 | R14 |
|---|---|---|
| hierarchy | 7.2 | 8.0 |
| layout | 6.3 | 7.8 |
| typography | 7.2 | 7.6 |
| color | 7.2 | 8.0 |
| depth | 7.0 | 7.6 |
| consistency | 6.3 | 8.2 |
| affordance | 6.5 | 7.4 |
| iconography | 7.5 | 8.0 |
| polish | 6.0 | 7.4 |
| cohesion | 7.3 | 8.2 |

## What changed

### Material

- **Bevels.** The shade gain is blended by facet, so flat faces have quieter shadows and
  gem facets keep their contrast. Raised, hover and module light presets are softer.
- **Background opacity.** Owner permission: the factory default is now 42 %.
- **Blur.** The default radius is 12 px.
- **Hover.** Hover lifts by +5.

### Type

- **Font quality.** Blocky headings and titles were a font-quality race: providers loaded in
  parallel kept a 1× raster. `AuroraFontQuality` now tracks the raster scale per provider.
- **Text colours.** Secondary and muted text are brighter. Values, hotkey prompts, open
  captions and the HUD editor guide use primary text.

### Controls

- **Buttons.** An icon and its label centre as one group. Reset is sized to its label.
- **Switches.**
  - The OFF switch track is lifted so its edge reads on dark glass.
  - The Profiles "Separate controls" switch is the same shaded switch as every settings row.
    It used to be a flat preview mock.
- **Sliders.** The visible well spans the knob's reach; hit testing is unchanged. Disabled
  sliders show a faint accent fill and a muted knob.
- **Key lists.** An empty key list shows an inline "+ Add Key".
- **Section captions.**
  - Collapsed captions count as rows when a page fits its body.
  - An orphaned open caption moves to the next page.

### Screens

- **Header inset (owner, after R14).**
  - The header row (title, Done, Reset, search, toolbars) now sits 14 px below the top edge
    (`UiDensity.HEADER_TOP`), matching the title column and row controls. Done used to sit
    10 px under the lit top band, closer than anything else.
  - The window grew from 336×288 to 336×292, so every page keeps its content space. The grid
    still hides only the last 4 px of its third row, and the list shows nine rows.

- **Profiles.**
  - A title row and a tools row: search, Separate controls and Open folder.
  - "Set hotkey" sits on the card, and the gear has a hover face.
- **HUD editor.**
  - A three-line guide on a backing plate.
  - Reset Positions sits in the bottom-right corner.
  - Corner grips are clamped inside the screen and drawn with a dark keyline.
  - An empty module shows its name.
- **Armor HUD.** Durability numbers sit on readout plates with even spacing.
- **Crosshair preview.** It is taller and zooms further.
- **Colour swatches.** A light swatch gets a dark edge on dark windows.
- **Settings copy.**
  - The Theme group has an Enabled row.
  - Look Length is disabled while neither look line is on.
  - Clearer row names, and "Free Look" is now two words.

### HUD text

With Smooth Scaling (SDF) on, Keystrokes text skips the raster quantize. This is the separate
Codex fix in commit a7a2e30d.

## What holds the score

The R14 asks that recur across reviewers are:
- one slider value column on detail pages and Settings;
- detail-page section headers that differ more from rows;
- HUD editor grips near the bottom edge over busy scenery;
- lighter lower bevels on dropdowns;
- the dense Profiles tools row.

The rest conflict with owner decisions in the brief or with other reviewers.

## Evidence

The captures, briefs, raw results and the chart are under `build/ui-council/`, which is not
tracked. The per-round tables above are generated from those results.
