# Aurora Design Language — Settings Screen Layout Specification
A synthesis of iOS Settings/Human Interface Guidelines conventions and
OxygenOS's settings design language, adapted to Aurora's existing token
and glass systems. This document specifies how detail screens should
organize headers, titles, subtitles, body text, example/preview areas,
spacing, and setting order — not a new visual style, but a layout
*discipline* applied consistently across every feature detail screen.

## Why these two references
- **iOS Settings** is the clearest existing example of a *grouped,
  legible, dense-but-breathable* settings hierarchy: tiny uppercase
  section labels, hairline-separated rows within a rounded group, and a
  footer line under each group explaining what it does — this solves
  "where does explanatory text go" cleanly, without cluttering every row.
- **OxygenOS** contributes the *generous whitespace and per-row subtitle*
  convention: each row shows its current value/state directly beneath the
  label (not just on tap), and section headers get more visual weight and
  breathing room than iOS's tiny caps — this suits Aurora's existing
  larger touch-friendly widget sizing better than iOS's denser rows would.

The result is not a copy of either — it's iOS's *information architecture*
(grouping, footers, hierarchy) rendered with OxygenOS's *generosity of
space and row-level context*.

## The hierarchy, top to bottom

### 1. Screen title
One per detail screen. **Correction (found during the three-screen
pilot):** this was believed to already exist on `FeatureDetailScreen` —
it didn't; the title constants were dead/unreserved since the initial
commit. The pilot implemented it: the feature name, full-strength
`ON_BACKGROUND`, left-aligned at the window's content column (matching
row-label indent, per §6's consistent-indent principle — not flush to the
screen edge).

**Resolved: no separate type scale.** "Large and bold" is not
implementable under Aurora's constraints as a literal instruction —
there is exactly one type size mod-wide and no large-text idiom, and
inventing one (pose-scaled glyphs) would be a new visual style, outside
this document's scope. The title's distinction from row labels (which
are also full-strength `ON_BACKGROUND`) is **position, not color alone**
— it's the sole *claimant of the title role* in the band above the window
content (button captions like Done/Reset also live in that general area,
but as control labels, not competing title text), not competing with any
row label for that role. Color weight still does real
work *beneath* the title: `ON_BACKGROUND_SECONDARY`/`ON_BACKGROUND_MUTED`
for everything under it. Likewise "subtitle, smaller" degrades to
"secondary color, same size" — where a screen needs a subtitle beneath
the title (e.g.
attribution, like Better Hitreg's "Original project by Jass"), it sits
directly under the title in `ON_BACKGROUND_SECONDARY`, no extra top
margin beyond the title's own line height — subtitles are *attached* to
the title, not a separate block.

### 2. Section headers
Every logically distinct group of settings gets a `SectionHeader` (this
widget already exists — this is about *disciplined, consistent use*, not
a new component). Rules:
- **More space above a section header than below it** — the header
  should read as "starting something new," with the group's rows
  following close behind. iOS gets this right; don't split the
  difference evenly.
- Text: `ON_BACKGROUND_MUTED` (de-emphasized relative to row labels, which
  stay full-strength `ON_BACKGROUND`). **Correction:** an earlier version
  of this document deferred to "whichever token is currently used" — the
  pilot found `SectionHeaderSetting` was actually using neither sanctioned
  token, but a separate legacy tertiary static, exactly the "third
  de-emphasis level" this rule exists to prevent. Fixed during the pilot
  to `ON_BACKGROUND_MUTED` explicitly (value-identical to the legacy
  static in both factory palettes, so no visible change) — that's now
  the canonical answer, not something to re-derive per screen.
- A section header is a label, not a button, not a divider line — no
  glass, no background fill, matching the existing "toggles/sliders/text
  entry always opaque, never glass" convention extended to headers as a
  category of non-interactive chrome.

### 3. Section footers (body text)
Where a group of settings needs explanation beyond what the row labels
themselves convey, add a short footer line *below the group's last row,
before the next section header* — not a paragraph inline with the rows,
not a tooltip-only explanation. This is the "body text" placement rule:
**body text belongs to a group, positioned immediately after it**, in
`ON_BACKGROUND_MUTED`, small, left-aligned matching the row indent. Don't
add a footer to every section reflexively — only where the group's
purpose genuinely isn't self-evident from its header + row labels alone.

### 4. Individual setting rows
- **Label** stays full-strength `ON_BACKGROUND`, left-aligned.
- **Row subtitle** (the OxygenOS contribution): a small
  `ON_BACKGROUND_SECONDARY` line beneath the label, used **only** where a
  setting's current value or state is not visible at a glance from the
  control itself — this covers state hidden behind a hover-dwell
  tooltip, a nested screen, or anywhere else it isn't immediately
  legible, not just literal interaction with the control. **Correction
  (found during the pilot):** an earlier
  version of this document named sliders, enums, and keybinds as subtitle
  candidates — this directly contradicted the exclusion clause below,
  since all three of Aurora's widgets for those types already display
  their current value/state on the control itself (a slider's numeric
  readout, an enum's selected-option label, a keybind's bound-key pill).
  OxygenOS's row-subtitle convention solves a problem (state hidden until
  tapped) that these Aurora widgets don't have. **The exclusion clause
  governs, not the old example list:** do not add a subtitle to any
  widget that already surfaces its state at a glance. In practice, this
  makes the subtitle mechanism relevant almost exclusively to
  `BooleanSetting`-family toggles whose effect isn't visible from the
  toggle position alone (e.g. a toggle gating some computed/live figure,
  as Better Hitreg's three alert toggles do) — not a general-purpose
  addition to every row type.
- **Trailing control** (toggle, slider, chevron for a nested
  popup/screen) stays exactly as the existing `FeatureSetting` widget
  vocabulary already renders it — no change to the control widgets
  themselves, only to what surrounds them.

### 5. Example/preview areas
Aurora already has precedent for this: `ThemePreviewSetting`'s live
preview card. The general rule: **any setting whose effect is hard to
picture from its label alone should get a live preview.** A preview
should generally *precede* the setting(s) it demonstrates (you see the
effect, then you adjust it) — but this is not absolute. A trailing preview is acceptable where it demonstrably fits an inverse
flow — a preview that summarizes the *cumulative result* of settings
above it (adjust, then verify the result), rather than a preview meant to
be understood before adjusting anything. `ThemePreviewSetting`'s trailing
placement is compliant under exactly this reading: it's a result card
reflecting everything configured above it, not an explainer for what's
about to follow. This is the sole basis for a trailing exception — "reads
better" alone is not a criterion. Absent a comparable result-summary
case, default to leading placement.
Candidates worth a preview by this rule: crosshair/hitbox shape settings,
any color-picker-driven setting whose *effect* — as opposed to its raw
value — isn't shown by the control (e.g. the Theme accent: its own
swatch shows the color, but only a preview shows the derived palette it
produces), glass style/roundness. Don't
force a preview onto settings where the effect is already obvious (a
numeric FPS cap doesn't need a live preview). **Ruling from the pilot:**
a list of individual color rows, each already showing its own swatch (as
on Better Hitreg's 19 color settings), does not need a separate preview
area — the swatch *is* the value, and a contextual mock-scene preview for
that case would be a new component, not layout discipline. This applies
specifically to swatch-bearing color rows in a list; it doesn't reopen
the case for settings whose effect genuinely isn't visible from the
control (shape/roundness, combined multi-value effects) — those still
warrant a preview under the general rule above.

### 6. Spacers and rhythm
- Consistent vertical spacing *within* a section (row-to-row) — tighter
  than the spacing *between* sections. The reader should feel groups as
  visually distinct clusters, not one undifferentiated list.
- Consistent left indent for every row, subtitle, and footer within a
  section — nothing should hang further left/right than its section's
  established margin.
- Minimum row height should stay generous/touch-target-friendly
  (OxygenOS's instinct), not iOS's denser mobile row height — Aurora's
  existing row heights across `FeatureSetting` widgets are the baseline;
  don't shrink them to fit more on screen.

### 7. Setting order within a section
When ordering settings inside a group (not the order of *sections*,
which follows whatever logical grouping the feature naturally has):
1. Most commonly adjusted / highest-impact setting first.
2. Related settings that modify or depend on an earlier one immediately
   follow it (e.g. "Enable X" directly followed by X's own sub-settings,
   not separated by unrelated settings).
3. Destructive or irreversible actions (Reset, Delete, Clear) go **last**,
   isolated in their own section if the screen has more than a couple of
   groups — never interleaved with adjustable settings, matching iOS's
   convention of isolating destructive actions from everything else.

## 8. Scroll-boundary fade (top edge only)

Scrollable content **fades into the surface it sits on** as it approaches
the TOP clip boundary — never a hard cut at a scissor edge, and never a
slide over the title band on unscissored lists. The bottom edge
deliberately has no counterpart: lists end in the screen's own
padding/footer, which never produced the collision the top edge does
(content leaving at the top passes under fixed chrome — title, toolbar,
search band — which is exactly where an abrupt edge reads worst).

- **Mechanism:** `util/ScrollFade` — a vertical gradient painted AFTER the
  scrollable content (never inside a `UiLayerCache` capture pass), starting
  at the boundary in the surface color **verbatim — alpha channel
  included** — and ramping to fully transparent over **16 GUI pixels**
  (`ScrollFade.FADE_PX`). Because the top of the gradient is the color the
  surface already has at that position, the fade reads as content
  dissolving into the surface over glass and flat/fallback containers
  alike, and never introduces a second Background Opacity application
  point.
- **The surface color is whatever the screen's container tint actually is
  at that position:** `WINDOW_FILL` for scissored viewports inside a window
  (glass or flat — same token either way); `OVERLAY_DIM` for screens whose
  rows float over the veiled world with no container (the manager
  screens), where the dim veil IS the surface.
- **Engagement scales with scroll:** the gradient's top alpha is
  `min(1, scrollPos / 16)` of the surface color's own alpha — a list at
  rest (nothing above the boundary) is pixel-untouched, and the fade is
  fully engaged once one fade-height of content has passed the boundary.
  Engagement takes the raw scroll position, not a scroll/overflow ratio.
- **Two shapes:** gradient-only (`drawTop`) for scissored viewports, where
  the scissor already hides anything above the boundary; capped
  (`drawTopCapped`) for unscissored lists (the detail screens), where a
  solid cap of the same color must also cover content that still paints
  above the boundary. Hit-testing follows the render truth either way:
  content hidden by the fade band is not clickable (the same rule
  `ManagerListScreen` applies to its scissored clip band).
- **Rim highlights** of raised glass rows fading through the gradient are
  an accepted nuance — no special handling (verified on the pilot: the
  waypoint rows' rims dim smoothly with their bodies).

Piloted on `FeatureDetailScreen` (all detail screens incl. Miscellaneous),
`ManagerListScreen` (both manager screens), and `AuroraScreen` (Mods grid
+ Settings tab); extend screen by screen to the remaining scrollable
surfaces from here.

## What this does NOT change
- No new visual style, colors, or glass behavior — this is layout
  discipline applied through existing tokens and existing widgets.
- No change to any control widget's own rendering (`ToggleSwitch`,
  `Slider`, etc.) — only to the header/subtitle/footer/spacing scaffolding
  around them.
- Not a mandate to add a preview, subtitle, or footer to every single
  setting — each rule above includes when *not* to apply it. Overuse of
  any of these (a subtitle on every row regardless of whether it adds
  information, a footer on every section regardless of whether it's
  needed) would work against the "clean, legible, breathable" goal this
  whole document exists to serve.

## Grid screens — exemptions, not a forked document
`AuroraScreen` (tile grid + sidebar + search) was one of the three pilot
screens specifically to test whether this document covers non-list
layouts. Verdict: the underlying principles (hierarchy of emphasis,
spacing rhythm, restraint) hold; several *rules* are list-specific and
simply don't apply to a grid. Rather than forking this document for grid
layouts, the resolution is a short list of exemptions:

- **§1 (title) does not apply** to a grid screen's brand/wordmark element
  (e.g. the sidebar's "AURORA" mark) — that's identity chrome, not a
  document title.
- **§2/§3 (section headers/footers) do not apply** where the screen has
  no sections — a tile grid is one flat collection, not grouped content.
- **§4's row-subtitle rule still applies, exempted correctly by its own
  clause** — grid tiles already show state via the enabled accent
  stain/wash, satisfying the "control already shows state" exclusion.
  **Distinct from this:** the Mods tab's separate list layout (not the
  80×80 icon-grid tiles, which carry icon + name only) already shows a
  static description line under each feature name — that's pre-existing
  explanatory copy, not the §4 mechanism, which is specifically a *live
  state/value* subtitle. The two are different text kinds this document
  keeps separate everywhere else; the list's description line is fine
  as-is and needs no change, but it isn't an instance of §4.
- **§7 (setting order) does not apply** to tile order — a grid's tile
  order is curation (what the maintainer decided belongs where), not
  "most-adjusted-first." Reordering tiles is a scope decision, not a
  layout-discipline one.
- **§6 (spacing rhythm) still applies wherever a real list is embedded
  inside an otherwise-grid screen** — confirmed on the pilot: the
  Settings tab's list (embedded within `AuroraScreen`) needed genuine
  inter-entry separation distinct from its own within-entry row gap,
  exactly per §6's "groups read as clusters" principle, even though nothing
  else on that screen needed §1-§5/§7's list-specific rules.

## Minor clarifications (resolved during the pilot)
- **Title alignment:** aligns to the window's content column (shares the
  row-label indent), not flush to the screen edge — per §6's
  consistent-indent principle.
- **Footer scope:** a footer explains the *group* it follows, not a
  sub-block within a group. Where a footer's explanation really only
  concerns some of a group's rows (as with Better Hitreg's Tracking
  section, where the footer mainly explains three of several rows), it
  still sits after the group's *last* row, not inline mid-group — group
  boundaries are the only footer anchor point this document defines.

## Rollout approach
This is a layout system, not a one-time patch — apply it where
`FeatureDetailScreen`'s shared chrome can absorb it centrally (title/
subtitle treatment, section header spacing rhythm), and apply the
per-section content rules (footers, subtitles, ordering, preview
placement) screen by screen, since those require actual judgment about
each feature's specific settings, not a mechanical find-and-replace.
The three-screen pilot (No Fog + shared chrome, Better Hitreg, AuroraScreen
— commits `28d8aca`, `764c891`, `385e704`) validated this split: shared
chrome absorbed cleanly mod-wide; footers/subtitles/ordering needed real
per-screen judgment; restraint clauses did genuine work rather than being
decorative caveats (No Fog stayed undecorated, Better Hitreg got exactly
one footer instead of seven, the grid got a spacing fix instead of a
redesign). Continue rolling out screen by screen from here rather than
applying content-level rules mod-wide in one pass.
