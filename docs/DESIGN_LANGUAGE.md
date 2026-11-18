# Aurora Design Language

Version 3 — normative interface specification, adopted 2026-09-14.

This document defines what Aurora interfaces are supposed to be. It governs visual
hierarchy, materials, interaction, motion, sound, components, screen composition,
accessibility, and verification. It applies to new UI and to future migration of existing
UI. It does not claim that every current screen already conforms.

`DESIGN_SYSTEM_AUDIT.md` is the factual snapshot of what existed at the 2026-09-14 audit
baseline. This document is the normative standard chosen after that audit. `AGENTS.md` and
`ARCHITECTURE.md` remain the authorities for repository process, implementation constraints,
and hard-won renderer history.

The terms **must**, **should**, and **may** are intentional:

- **Must** identifies a design-system invariant.
- **Should** identifies the strong default; a semantic exception may override it.
- **May** identifies permitted optional behavior.
- **Current implementation note** records useful facts, not automatic approval of current
  behavior.
- **Historical note** preserves why a rule exists and what must not be repeated.

Aurora Design Language v1/v2 was deliberately limited to settings-screen layout. Version 3
preserves that validated discipline and expands it into the complete interface standard.

## 1. Design principles

### 1.1 One system, not a collection of screens

Equivalent semantics must produce equivalent expectations. A button does not lose keyboard
access, narration, sound policy, or press feedback merely because one screen routed it
manually. Screens may compose differently, but implementation path must not become a hidden
source of user-facing behavior.

### 1.2 Hierarchy before decoration

Aurora communicates structure first through semantic depth, color role, spacing, position,
and restrained typography. Blur, borders, previews, animation, and sound reinforce that
hierarchy; they do not substitute for it.

### 1.3 Motion communicates state

Motion must explain accepted input, selection, manipulation, opening, closing, or spatial
continuity. Movement added only to make a screen feel busy is not Aurora motion.

### 1.4 Glass behaves like a material

Glass roles are semantic and centrally derived. Screens request a container, control,
stained control, or above-dim surface; they do not independently invent blur, rim, tint,
lighting, or opacity formulas.

### 1.5 Restraint is part of the identity

Do not add a subtitle, preview, footer, border, glass layer, animation, or sound without a
specific informational or interaction purpose. Repetition is not hierarchy, and more
effects do not make an interface more Aurora.

### 1.6 User customization is authoritative

The user's accent, light/dark mode, roundness, glass style, and opacity remain meaningful.
Implementation may adapt a rendered treatment for usability, but must not silently rewrite
the stored preference merely to simplify rendering.

### 1.7 Render truth and interaction truth agree

What the user can see and what the user can operate must describe the same state. A clipped,
hidden, disabled, covered, or unavailable control must not remain active through a separate
input path.

### 1.8 Minecraft context, Aurora coherence

Aurora remains compact enough for a game overlay and respectful of Minecraft content. It is
not a clone of a mobile or desktop operating system. Game sprites, maps, HUD data, and live
world imagery may demand domain-specific treatment, while the chrome surrounding them still
belongs to one Aurora system.

## 2. Semantic hierarchy

Every element should have one principal semantic role. A role determines its normal text,
color, depth, state, input, and sound expectations before a screen adds local content.

The main hierarchy is:

1. **Backdrop/content:** world, panorama, map, item/effect imagery, or represented data.
2. **Container:** large depressed structure that organizes a screen or modal.
3. **Secondary container:** a nested grouping used only when another structural level is
   genuinely necessary.
4. **Control:** raised interactive chrome.
5. **Stained control:** selected, on, or primary-emphasis interactive chrome.
6. **Above-dim surface:** foreground interaction intentionally composited above a modal dim.
7. **Text and icons:** content whose semantic emphasis is independent of material depth.

A component must not combine roles just to acquire a preferred appearance. In particular,
an entire container-like row should not become stained merely to mark selection when a
compact badge, indicator, or selected control can carry that state.

## 3. Color

### 3.1 Semantic sources

Aurora color belongs to one of five categories:

1. **Theme/chrome color:** derived through `ThemeToken`, `PaletteEngine`, and the resolved
   theme. Windows, controls, tabs, text fields, borders, and ordinary text use these roles.
2. **Semantic status color:** fixed or centrally defined success, warning, error, info, and
   HUD-status meanings. A status color communicates meaning, not brand accent.
3. **Represented data/content color:** Minecraft item/effect sprites, map terrain, biome
   color, a chosen color value, hue/saturation data, crosshair canvas pixels, and comparable
   content. Accuracy to the represented information takes priority over theme recoloring.
4. **Implementation-only color:** masks, transparent clears, shadow construction, signed-
   distance coverage, and other non-semantic rendering machinery.
5. **Exceptional local styling:** a narrowly scoped color required by a domain that cannot
   be expressed by the first four categories. It must be documented when non-obvious.

UI chrome must use semantic theme tokens rather than screen-local literal colors. This does
not outlaw literals for represented data or renderer internals. A literal is unacceptable
when it silently creates a second accent, text hierarchy, or status vocabulary.

### 3.2 Accent

**Factory palette (owner decisions, 2026-11-14):** new installs start with the Indigo accent
preset (#5C6BC0), the family of the ribbon logo, on the stock graphite Background Color
(#16171D). Earlier factory values (green #43A047 on purple #22163A) read as two palettes next
to the logo and tinted every neutral purple. Saved colours are never rewritten.

Accent is Aurora's identity and emphasis color. It is appropriate for selection, an on
state, a primary action, a compact indicator, and selected chrome. It is not a universal
foreground color and should not color every interactive element.

Stained material is reserved for selected/on or primary-emphasis controls. Content on a
stained surface uses `ON_ACCENT`, never the accent itself.

Aurora preserves the user's configured accent. It must not change the stored accent to make
a particular foreground easier to render.

`ON_ACCENT` must choose the better appropriate light or dark foreground. If a text-bearing
stained treatment still fails the project's chosen readability diagnostic, a future
implementation must correct the rendered treatment instead of the preference. Permitted
directions include changing the effective stained backing/lightness or increasing its
material backing through a deterministic rule. Dynamic frame-by-frame foreground changes
based on arbitrary world pixels are not the default solution.

Contrast ratios are diagnostic evidence, not a claim that WCAG mechanically governs every
translucent Minecraft surface. Tests must state the actual compositing context.

### 3.3 Text hierarchy

- **Primary:** essential information, principal labels, active values, and controls needed
  to understand or operate the UI. It must remain strongly readable.
- **Secondary:** supporting information that remains meaningfully readable, including
  attached title subtitles and useful live value lines.
- **Muted:** supplemental or de-emphasized information. It may have lower contrast, but it
  must not carry essential meaning alone.
- **Faint:** decorative, redundant, or extremely low-priority information only. Required
  information must never exist exclusively in faint treatment.

Local alpha changes must not invent unofficial levels between these roles without a
documented reason. Alpha that comes from a material or transition is separate from the
semantic text role.

### 3.4 Low-opacity robustness

Aurora does not promise mathematically guaranteed text contrast against every possible live
world framebuffer at 10% Background Opacity. Such a guarantee would require intrusive
adaptive rendering and could undermine the material design.

It does require practical usability:

- Essential text must remain operable and understandable.
- Navigation and interactive state must not depend on favorable world imagery.
- Important text-bearing surfaces should provide sufficient material backing when needed.
- Supplemental muted/faint content may degrade more than primary content.
- Per-frame text-color mutation against arbitrary backdrop pixels should not be used as a
  routine fix.

The exact minimum effective backing algorithm is unresolved and requires a controlled
pilot. The configured window opacity remains authoritative; any compensating treatment must
be explicit, deterministic, and limited to the surfaces that need it.

### 3.5 Current architecture note

`PaletteEngine` derives the accent family, surfaces, borders, text, controls, semantic
colors, and HUD tokens from one accent plus mode. `ResolvedTheme` is the immutable read path;
`AuroraTheme` remains a projection facade, not an independent palette. New code should use
semantic tokens and must not write legacy statics outside `ResolvedTheme.project()`.

## 4. Typography

Aurora uses one practical UI text size for body text. Hierarchy comes first from semantic
color, position, spacing, alignment, and grouping, and from the two text roles in §4.1.

Equivalent roles must not receive arbitrary local size, shadow, weight, or color treatment.
Material Symbol sizing is iconography, not typography.

Where the configured font applies is the Text Renderer setting (owner ruling 2026-11-17):
"Aurora UI Only" styles text drawn by an Aurora window's own render — the module window,
detail screens, pickers, dropdown overlays, tooltips and Pulsar's dashboard — and nothing
else in the frame: not the vanilla hotbar, chat or scoreboard behind an open window, not
vanilla screens that carry Aurora accents, and not the in-world HUD, whose editor previews
must look the way they will in game. "All Text" styles everything, HUD and vanilla included.
Measurement always follows drawing: `MixinFont` re-routes width through the same scope, and
`AuroraFontRenderer.styleKey()` folds the applicability into every memo key.

**Smooth Scaling (SDF)** is off by default. It changes how Aurora's custom glyphs are
drawn in the GUI, not where the custom font applies or how it is measured. The 8 px body,
9 px body, 11 px title and 20 px wordmark keep their raster metrics. Compare both at
native pose scale before judging stroke weight; the main gain is at larger pose scales.
World-space text and non-bundled fallback glyphs retain the bitmap path.

| Role | Normative treatment |
|---|---|
| Screen title | Primary, `TITLE` role (§4.1), uniquely placed in the title band. |
| Window identity | "AURORA", text only (owner ruling 2026-10-06), in the `TITLE` role at the top left of the main window. |
| Feature description | Secondary, body size, at most two whole-word lines under a detail screen's title; the catalog's one-sentence summary. |
| Attached subtitle/attribution | Muted (warning when it reports a broken mixin), body size, after the description. |
| Settings group heading | Primary, `HEADING` role (§4.1). |
| Settings group summary | Secondary, one line directly under the heading (`FeatureMetadata.pageSummary`), ellipsized to the column. It says what the group is for; row details stay in tooltips. |
| Section header | Muted, generally uppercase, more space above than below. |
| Setting label | Primary, left-aligned to the section content column. |
| Value/subtitle line | Secondary, beneath the label only when it exposes otherwise hidden state. |
| Footer/body explanation | Muted, wrapped, left-aligned to the group. |
| Button/control label | Primary or `ON_ACCENT` according to its material. |
| Card/tile title | Primary; concise and stable. |
| Card description | Secondary or muted according to importance; never the only operational instruction if muted. |
| Tooltip | Readable supporting text; it supplements rather than replaces essential labels. |
| Metadata/status | Secondary by default; semantic color may reinforce status but cannot be its only carrier where practical. |

### 4.1 Text roles

Two roles sit above body text (UI polish overhaul, 2026-11-14). Both are a heavier cut of the
user's chosen family, never a pose-scaled glyph, and both come from
`AuroraFontRenderer.role(text, Role)`:

| Role | Cut | Use |
|---|---|---|
| `TITLE` | one weight step heavier (Regular/Medium → SemiBold, SemiBold/Bold → Bold), its own 11 px raster (`<font>_<weight>_title`, `tools/build_fonts.py`) | screen titles, window identity |
| `HEADING` | the same heavier weight at the regular 9 px raster, one step above compact 8 px body text | settings group headings, section captions, empty-state headlines |

A role sequence names its own font, so `MixinFont`'s global swap leaves it alone; the role still
follows the configured family, italic and case. With the vanilla font it falls back to Minecraft
bold. No other sizes or weights are introduced locally.

Text should be ellipsized where a compact control has a fixed width and wrapped where
explanation is expected. Truncation must use rendered width rather than character count.
Controls should expose full content through an appropriate tooltip or accessible label when
visible text is truncated.

The bundled-font renderer may substitute a configured face according to its scope, but must
preserve per-character color and other meaningful style information. Aurora UI text does not
gain a shadow merely to imitate vanilla chrome.

## 5. Geometry and spacing

### 5.1 Roundness ownership

Theme roundness governs rectangular UI chrome, including:

- windows and ordinary containers;
- cards and rows;
- buttons and icon actions;
- tabs and sidebar items;
- text-field chrome;
- dropdowns, popups, and modals;
- previews when their boundary is chrome rather than represented data.

Roundness is one pixel value, the Corner Radius slider (0–20, factory 10; it replaced the
Round / Slightly Round / Square choice on 2026-11-12). Panels and buttons use it directly,
major sheets ×7/5, compact controls ×3/5. Radius 0 means square UI chrome. A screen-local
radius must not survive only because that screen predates resolved roundness.

Capsule shapes follow it too (owner ruling 2026-11-12): toggle tracks and thumbs, slider
tracks and knobs, scrollbar thumbs and the colour picker's pad and strips. A capsule shape is
a full capsule at the factory radius or above and flattens in proportion below it, square at 0
(`ThemeRoundness.capsule`). The remaining exceptions are represented data and game geometry
that is not theme chrome: colour wheels, represented game shapes and map markers.

In-game HUD shapes keep fixed corners by default (panel backgrounds 2 px, flat/translucent
keystroke keys 3 px, Cherry-profile keycaps 2 px, the square minimap frame 0 px). Each HUD
feature's detail screen has a **Theme Corner Radius** switch, off by default, that hands its
shapes the theme's compact radius (Corner Radius × 3/5) instead (owner ruling 2026-11-12).
Keystrokes' existing **Theme Corner Radius** row is shown for **3D Keycap**: it gives the
sculpted caps the theme's control radius (`radiusSmall`) instead, including exact square facets
at radius 0 (the window radius turned 20 px keys into circles), while
continuing to control the panel and the other key styles' compact corners.

### 5.2 Compact spacing vocabulary

Aurora keeps a compact Minecraft-appropriate density. Existing implementation provides the
baseline vocabulary:

- 3–4 GUI pixels: hairline/optical separation and compact internal spacing.
- 6 GUI pixels: common row/control adjacency.
- 8–10 GUI pixels: ordinary content inset and component breathing room.
- 16 GUI pixels: clear group separation and the established scroll-fade depth.
- 18–22 GUI pixels: common compact control height.
- 28–40 GUI pixels: common setting-row range, expanding when content wraps.

These are a vocabulary, not a demand that every screen use identical dimensions. Responsive
layout and domain density may select values within the established rhythm. New isolated
spacing values should have a content or geometry reason rather than being chosen by eye on
one screen.

### 5.3 Rhythm

- Spacing within a group must be tighter than spacing between groups.
- Row labels, value lines, footers, and previews should share the group's content indent.
- Section headers receive more space above than below.
- Existing setting rows are the minimum density baseline; do not shrink them simply to fit
  more content.
- Dynamic row height is correct when labels or value lines wrap. Render, scroll extent,
  clipping, and hit testing must use the same resulting geometry.

## 6. Iconography

Material Symbols Rounded is Aurora's principal chrome and action icon language. Chrome icons
should use the shared codepoint map and `MaterialIconRenderer`, including its exact-device-
pixel raster path. A screen should not create a parallel icon vocabulary through raw text or
one-off bitmaps.

Minecraft sprites and assets remain correct for represented items, effects, blocks, HUD
content, and other game data. Domain-specific visualization may use symbols that accurately
communicate the data.

Normal UI actions should prefer a semantic Material Symbol over raw `+`, `x`, `✓`, `v`, or
`>` characters when a proper action icon exists. A textual character may remain when it is
genuinely text, data, or the clearest accessible representation—not merely because it was
easy to draw.

A future shared icon-action primitive must define:

- a consistent hit target and optical icon size;
- rest, hover, press, focus, and disabled treatment;
- tooltip behavior where the icon alone is not self-explanatory;
- an accessible/narrated label independent of the visible glyph;
- semantic sound behavior;
- theme roundness for its rectangular chrome.

The exact focus treatment and final small-action icon replacements require a component pilot.

## 7. Materials and glass

### 7.1 Material roles

Screens request a semantic role, not arbitrary material parameters.

#### Container

A large enclosing or depressed structural surface. It separates a working area from its
backdrop and visually receives the controls it contains.

#### Secondary container/surface

A nested depressed grouping used only when a second structural level materially improves
comprehension. It is not a license to put a panel behind every group.

#### Control

A raised interactive surface. Neutral controls communicate availability without implying
selection or primary emphasis.

#### Stained control

A raised accent material for selected, on, active, or primary-emphasis interaction. It must
not stain an entire container-like row when a compact selected indicator is sufficient.

#### Above-dim surface

A foreground surface intentionally composited after the modal dim. It is reserved for the
active popup/modal interaction and its controls, not ordinary screen chrome.

Profile rows being depressed while waypoint rows are raised is an accepted, documented
domain exception. It does not establish two universal row variants until another real use
case justifies promoting that distinction.

### 7.2 Canonical glass

Aurora glass currently consists of:

- a captured backdrop;
- Gaussian blur in Frosted mode;
- semantic tint;
- rounded masking governed by theme geometry;
- directional face lighting;
- directional rim lighting;
- raised/depressed role inversion.

Frosted runs the capture/blur path. Transparent, presented to users as **Wireframe**, skips
blur work and uses each consumer's complete flat/translucent fallback. Wireframe is not a
refractive clear-glass mode.

**Current implementation note:** the blur radius is its own theme value, the Blur Radius
slider (0–32 GUI pixels, factory 9; 0 is no blur). Since 2026-11-12 it no longer follows
Background Opacity, which only sets the tint's alpha; themes saved earlier were seeded with the
blur they rendered (28 GUI pixels × opacity). Stained tint currently keeps a
centrally defined alpha floor so selected/primary controls remain identifiable at low window
opacity. These are central material behaviors, not parameters for screens to copy or tune.

Aurora v3 does **not** define refraction or distortion as part of the canonical glass
material. Blur is not distortion. The canonical compositor keeps backdrop sampling aligned;
lighting changes color and coverage, not background geometry.

If refraction or distortion is explored later, it requires a separate R&D investigation:

1. Build deterministic line, grid, and high-contrast material tests.
2. Establish whether displacement improves material legibility rather than merely adding
   movement.
3. Pilot on very few surfaces.
4. Measure performance and GUI/device-scale behavior.
5. Accept the treatment explicitly.
6. Amend this document before any broad use.

Future agents must not casually add UV displacement while “improving glass.”

### 7.3 Central derivation and continuity

Equivalent material roles must derive blur radius, face-light direction, rim strength, tint
formula, opacity hierarchy, and corner behavior centrally. A screen-local override requires
a documented semantic exception.

Per-panel gradients and rims can create visible seams even while backdrop sampling remains
screen-aligned. Clean continuity between equivalent adjacent surfaces is a conformance
target. This does not yet prescribe a shader rewrite; first evaluate role, geometry, tint,
opacity, blur, rim, lighting, and compositing consistently.

Do not over-layer glass. Nested material levels must explain structure, not showcase the
renderer. Small transient rows inside an already-material container may remain flat when a
second glass layer adds no semantic depth or when its opaque content would hide the material.

**Production ownership rule (updated 2026-10-06):** enclosing windows own a
stationary blurred boundary and neutral cached face. Visible standalone cards may
also sample the backdrop through `GlassSurface.card`; ordinary embedded controls
inherit their containing material. Active feature cards use an accent outline,
wash and halo, without left indicator bars. Search fields retain their neutral
edge while editing. Scrollable content is clipped inside the stationary frame;
search/add headers stay fixed above that body. Overflow text stays centered and
wraps only at whole-word boundaries. Floating tooltip/popup/modal faces remain
opaque cached material with restrained elevation. See `UNIFIED_THEME_PILOT.md`.

Floating tooltip material is stable token-backed chrome rather than live glass. It uses one
low-emphasis edge and shadow, respects theme roundness, and does not inherit the window's
opacity merely because it originated inside that window. This is a material-depth decision,
not adaptive contrast behavior.

`TooltipPresenter` shares the raised opaque overlay raster with dropdowns and the colour
editor. It caps text at 220 GUI px before padding (6 px horizontal, 4 px vertical), wraps
between words, and fits the result inside the window and screen with an 8 px margin. It
prefers below-right, then flips or clamps without painting the face over the source.
Vanilla item tooltips are unchanged.

### 7.4 Integration invariants

The following are both design and renderer-safety rules:

- Background Opacity has one application point: `WINDOW_FILL` alpha. Do not multiply a
  second opacity factor into the material. Centrally defined rim/frost derivations from that
  value are not additional user-facing opacity controls.
- Production parents use neutral lighting; children are flat. Raised/depressed lighting presets remain renderer diagnostic references.
- Every glass surface body is submitted before the screen dim. Content is drawn after the
  dim. Deferred rim finishing occurs at the established phase boundary.
- Popup/modal surfaces intentionally above the dim use the explicit above-dim path.
- Scissored glass uses tracked scissor helpers so deferred rims obey the same clip.
- Every material consumer has a complete flat fallback for Wireframe, invalid capture,
  screenshot suppression, budget decline, or renderer failure.
- Repeated surfaces declare the appropriate material priority so degradation is
  deterministic.
- Text, hover, caret, focus, and press feedback do not independently mutate the underlying
  glass tint.
- Scrollbar thumb geometry retains fractional logical positions and snaps both physical
  edges together to the device-pixel grid; painting, hit testing, and drag offsets share the
  same resolved bounds.

Toggle and slider mechanics remain opaque by convention. Tooltips should use a stable,
readable token surface rather than becoming another live-backdrop showcase. Text fields are
the explicit v3 change: their normal input chrome is glass, while represented editing/data
surfaces remain outside that rule.

The GL reliability rules, texture lifetime constraints, screenshot interlock, pool behavior,
and exact render pipeline remain implementation authority in `AGENTS.md` and renderer
Javadocs; they are not restated here as visual choices.

## 8. Interaction states

### 8.1 Canonical vocabulary

Every reusable interactive primitive must explicitly define the states that apply:

```text
rest
hover
pressed / active manipulation
selected / on
focused
disabled
```

Not every state must look identical across components. An omitted state must be an intentional
semantic decision, never an accidental result of manual event routing.

State precedence must be predictable. Disabled suppresses accepted hover/press/activation
feedback. Active manipulation remains visible while the pointer moves. Focus remains visible
without hover. Selected/on persists after transient hover and press end.

### 8.2 Button-like interaction service

Anything semantically button-like participates in one common service contract even when its
visual treatment differs. This includes ordinary buttons, icon actions, actionable cards,
tabs, sidebar items, and comparable controls.

Where semantically applicable, that contract includes:

- pointer activation;
- keyboard activation;
- visible keyboard focus;
- narration/accessibility name and state;
- disabled behavior;
- semantic UI sound;
- immediate press feedback.

Vanilla-backed controls and custom Aurora controls may use different adapters. They must not
expose different user capabilities solely because their technical routing differs.

### 8.3 Hover

The canonical hover transition is **140 ms on enter and 140 ms on exit**. Progress must be
smooth, symmetric, reversible, and continuous when interrupted. Hover must not snap from rest.

The existing resource-pack browser's genuine approximately 150 ms symmetric hover is evidence
for this direction, not a permanent special dialect. Future implementation should converge it
and the common hover utility on the same vocabulary.

Immediate hover may remain when interpolation is genuinely meaningless—for example, a
pixel-exact manipulation target whose direct state change is clearer—but the exception must
be explicit. No component should become immediate merely because it bypasses the shared
animator.

Hover sound is not part of the baseline.

### 8.4 Press and manipulation

Accepted physical interaction should receive immediate feedback suited to component geometry.

- Buttons and button-like cards may use subtle compression/depression. The current shared
  90 ms movement toward 0.96 and restrained approximately 180 ms recovery is the baseline
  reference, not a mandate for every component.
- Toggles should respond through their thumb/track relationship.
- Sliders should make knob manipulation unmistakable without playing a click on every value.
- Tabs should acknowledge press while preserving the persistent selected state.
- Swatches and compact icon actions may use a small halo, depression, or scale response.

Avoid excessive bounce. Response should feel physical and controlled rather than playful.
Exact treatments beyond the current button require component pilots.

### 8.5 Focus and keyboard

Focus is an intentional Aurora state:

- Every keyboard-operable control must expose visible focus.
- Focus must not depend on mouse hover.
- Focus may share color or surface properties with hover, but keyboard position must remain
  understandable when the pointer is elsewhere.
- Button-like controls support keyboard activation where their semantics permit it.
- Manual rendering is not an exemption from focus routing or narration metadata.

The exact focus visual should be subtle and Aurora-native. Version 3 does not prescribe a
heavy ring or exact alpha; that treatment requires a pilot across glass, flat, stained, and
data-adjacent controls.

### 8.6 Disabled

Disabled is behavioral and visual:

- Disabled controls reject mouse, keyboard, and indirect activation.
- Their appearance is distinguishable from enabled rest state.
- The label/icon remains readable enough to identify the unavailable control.
- Hover, press, selected sound, and other feedback must not imply successful interaction.
- A disabled child cannot remain active because its parent/screen forgot to consult the same
  state supplier.

The exact material/alpha treatment is unresolved. It must be piloted instead of copied from a
single current component.

### 8.7 Destructive actions

Destructive actions confirm with press-again (3 s), Escape disarms.

A control whose effect destroys user data or resets configuration beyond a single editable
value — deleting a profile, resetting a feature to defaults, clearing HUD layouts — must not
fire on one click. Its confirmation contract:

- **Press 1 arms.** The label changes to the imperative consequence ("Confirm delete",
  "Confirm reset"); the shared destructive styling applies. The button does not change size,
  and the state is carried by the label and that existing destructive styling — never by a new
  tint (see the tint rule in §6 and the glass rules).
- **Press 2 within 3 s fires.** The window is a `core` `ArmedAction` (injectable clock), so the
  timeout is testable and the armed state lapses lazily when polled each frame.
- **Escape disarms and consumes that press**, one layer at a time: a focused field or open
  popup consumes its own Escape first, an armed action disarms on the next, and only a further
  Escape closes the screen.
- Any other click — another control, empty space — disarms.

Small reversible edits (removing one key from a list, toggling a switch) and session-only
resets are not destructive and stay single-press. A waypoint list row's Delete also stays
single-press (owner decision 2026-11-17): it acts on one row in place, and its 48 px slot in
the shared row grid has no room for a confirm label. §12.7's placement rule (destructive
actions last) still applies.

## 9. Motion

Aurora uses a small semantic vocabulary:

| Category | Purpose | Baseline |
|---|---|---|
| Micro interaction | Hover and compact state response | 120–150 ms; canonical hover is 140 ms. |
| Press | Immediate accepted-input response | Approximately 90 ms where compression fits. |
| Recovery/state settling | Return from press or settle a discrete state | Approximately 150–200 ms, restrained overshoot only where physical. |
| Structural transition | Anchored dropdown or colour editor | 120 ms open, 90 ms close; ease-out, opacity and 96–100% scale. |
| Continuous manipulation | Slider drag, scrolling, toggle interpolation | Direct manipulation or smooth convergence, not staged decoration. |

Preferred easing principles:

- Ease-out communicates immediate response and quick settling.
- Ease-in-out is appropriate for reversible structural movement.
- Overshoot is reserved for small physical recovery and must remain subtle.
- Interruption/reversal should continue from current progress rather than restart or snap.
- Frame-rate-independent time is required; fixed per-frame deltas are not acceptable.

Use existing `AuroraAnim` capabilities where they fit. The design language does not require a
new animation framework.

Window entry and exit stay immediate. A window-wide pose or fade would need to preserve
stationary input, clip geometry and glass sampling across the capture and above-dim passes.
Only floating overlays animate their local pose; settled text draws without a transform.

Tooltips retain a 1500 ms initial dwell and 200 ms fade, with immediate hand-off while a
tooltip is still visible or fading. General → Interface → Reduced Motion (off by default)
keeps the dwell but removes the fade, snaps overlays to their endpoints and jumps to the
keyboard reveal target. Pulsar also honours its own motion preference. Sound and animation
must not be the sole confirmation of an operation.

## 10. Sound

Aurora should use a semantic UI-sound policy. Event-routing implementation must not decide
whether an otherwise equivalent action is silent.

Conceptual events are:

- **Activation:** an ordinary accepted action.
- **State change:** a toggle or persistent mode changes.
- **Navigation/back:** context changes or closes.
- **Selection:** a discrete item/tab becomes selected.
- **Rejection/error:** an attempted action is unavailable or fails.

Equivalent semantic actions should have equivalent sound policy. Disabled input must not play
successful activation feedback. High-frequency continuous controls such as sliders must not
emit a full click on every value change.

Hover is silent by default. If future testing supports hover sound, it may be used only for
significant discrete navigation/selection movement and must avoid sound spam.

UI sound should integrate with Minecraft's appropriate UI/master audio behavior rather than
act as an unrelated subsystem. It must respect user audio settings and remain reducible or
disableable if Aurora introduces custom sound assets. Version 3 selects no final sound files
and requires no OGG assets.

**Current implementation note:** vanilla-backed buttons inherit Minecraft's click sound;
many custom/manual controls are silent. That routing-dependent split is non-conforming and is
a future interaction-foundation task.

## 11. Components

### 11.1 Specification template

Every reusable interactive component must eventually document:

```text
Purpose
Material role
Geometry
Text/icon role
Rest
Hover
Pressed/manipulating
Selected/on
Focus
Disabled
Mouse behavior
Keyboard behavior
Narration/accessibility
Sound
Motion
Exceptions
```

The following sections establish v3 expectations without inventing unresolved pixel values.

### 11.2 Button

- **Purpose:** execute a discrete action.
- **Material (2026-11-17):** opaque raised neutral control with the depth recipe below;
  primary uses the `ACTION_STRONG` recipe, pressed reverses the light. Ghost and owner-themed
  actions retain their specified treatment; destructive text/hover colours remain semantic.
- **Geometry:** theme-controlled rectangular chrome and a compact, usable hit target.
- **States:** symmetric hover; immediate physical press; persistent focus independent of
  hover; readable, non-interactive disabled state.
- **Input/accessibility:** pointer and keyboard activation, narrated accessible name, disabled
  state conveyed.
- **Sound:** activation/navigation/destructive policy according to semantics, not adapter.
- **Motion:** current restrained compression/recovery is the baseline where glass/fallback
  compositing can support it.

### 11.2a Secondary actions beside a stronger sibling

`Button.Theming.GHOST` (2026-11-14) is text only at rest, in secondary text, and takes the
neutral hover face on hover or press. It is for a secondary action that shares a header with a
more important one: feature-detail Reset next to Done. Done is accent-filled on every window
(owner decision 2026-11-14, superseding the 2026-10-19 neutral-Done ruling), and a primary
button's `ACTION_STRONG` fill is opaque like an ON switch track: at the old half alpha over the
dark window it read as a dim, disabled-looking lilac; a manager's own
header action beside it (Drop Waypoint) is the outlined secondary, and an empty list carries
the accent-filled call to action under its hint.

### 11.3 Icon action/button

- **Purpose:** a compact discrete action represented primarily by an icon.
- **Material (2026-11-17):** on unified screens an icon action's face is the raised control
  recipe — rest/hover through the cached-state blend, the pressed recipe while pressed, muted
  flat when disabled — with its glyph centred through the shared box-centring helper so it
  shares the text axis. Bare glyph actions on a host surface stay the host's. The title
  screen's top bar keeps its existing treatment (depth-pass exclusion).
- **Geometry:** consistent hit target with optically centered shared icon.
- **States/services:** full button-like contract.
- **Accessibility:** always has a non-glyph accessible label; tooltip when the visible icon is
  not sufficiently self-explanatory.
- **Pilot required:** exact focus, pressed, and tooltip treatment for very small actions.

### 11.4 ToggleSwitch

- **Purpose:** directly switch one persistent Boolean state.
- **Material (2026-11-17):** opaque inset `MATERIAL_SUNKEN` OFF track. ON keeps the existing
  flat accent fill and interpolation; no accent outline. The same-size white thumb gets a
  curvature-lit dome and a down-right shadow (1 GUI px elevation, 2 GUI px softness, 18% black). Track/thumb follow the
  Corner Radius capsule rule, including exactly square geometry at zero. Focus still grows
  the thumb; the non-interactive represented preview retains its existing appearance.
- **States:** off/on must be unambiguous; hover, accepted press, focus, and disabled must each
  be defined. Disabled must not look like interactive rest.
- **Input/accessibility:** clicking its associated row may toggle only when that enlarged hit
  target is intentional and does not conflict with child actions. Keyboard activation and
  narration include the current on/off state.
- **Sound:** one state-change event per accepted toggle.
- **Pilot required:** exact hover/press/focus/disabled visuals.

### 11.5 Slider

- **Purpose:** choose a value from a continuous or stepped range.
- **Material (2026-11-17):** opaque dished `MATERIAL_SUNKEN` track (+3 → +7 HSL points) and same-size domed knob
  with a down-right shadow (1 GUI px elevation, 2 GUI px softness, 18% black). Accent fill behaviour is unchanged (flat accent since
  2026-11-14); the depth pass adds no AccentGradient changes. Track and knob follow Corner
  Radius. Value text never scales; numeric entry uses the inset field recipe.
- **Layout (owner direction, 2026-11-14):** one inline row everywhere: the label on the left,
  then the value right-aligned against a bounded track in the control column. The track is a
  dropdown's width (`SettingRowLayout.TRACK_W`, 84 px), or half that (`SHORT_TRACK_W`) for a
  slider with at most `SHORT_STEPS` (10) positions; it never spans the row. A page uses one
  length (`FeatureSetting.pageTrackWidth`, owner decision): the short track only when every
  slider on it has few positions, so values and track starts share one column. It ends
  `SettingRowLayout.KNOB_RESERVE` short of the control edge so a knob at either end lands on
  the edge every toggle, field and swatch ends on. A long label wraps between words and the row
  grows. History: a stacked row with a full-width track (earlier the same day) drew long lines
  with little meaning, most of all for sliders with a handful of positions.
- **States:** rest, hover, dragging, focus, and disabled. Dragging remains visibly active when
  the pointer leaves the original knob bounds. A disabled row mutes its label as well as its
  track and knob.
- **Input/accessibility:** pointer drag; keyboard increments when focused; accessible label,
  range, and current value. Click-to-edit numeric fields follow Text field rules.
- **Sound:** no full click per value; optional restrained boundary/commit feedback may be
  piloted later.
- **Motion:** direct during manipulation, smooth only where it does not lag the pointer.

### 11.6 SegmentedControl

- **Purpose:** choose one item from a small mutually exclusive set.
- **Material (2026-11-17):** one opaque dished well (+1 → +5 HSL points) behind the pick-one group. Unselected
  segments are text only (faint neutral hover lift); the selected cell is raised, with a 1 px
  inner margin, `SELECTION_SOFT` wash and existing thin accent edge. Independent toggle groups
  (Italic / Underline) instead use separate raised chips with gaps and no divider or shared
  well. ON chips add the same selected treatment and a leading `check` glyph. Labels, or the
  check-plus-label group, stay centred and fit the active font and actual segment width.
- **States:** each segment defines hover, press, selected, focus, and disabled. Selection must
  remain distinguishable without hover.
- **Input/accessibility:** arrow or equivalent keyboard navigation should be supported by the
  future adapter; narration identifies the group, option, and selection state.
- **Sound:** discrete selection, without replay when the current option is reselected unless
  that action has a separate semantic effect.

### 11.7 Enum/dropdown

- **Purpose:** select one value from a longer or context-dependent set.
- **Material (2026-11-17):** opaque curvature-lit raised `MaterialSurface.field` recipe (+5 → -3, 14% shadow); expanded popup is
  an explicit above-dim surface. Closed value text is centred in the face area left of the
  16 px chevron slot (centred stays centred, 2026-11-17), fitted to that area less equal side
  padding; the chevron centres on the same optical axis as the value. Expanded options retain their existing alignment.
- **States:** field hover/press/focus/disabled; expanded state; option hover/focus/selected/
  disabled where applicable.
- **Placement:** attached to the field, below when it fits and above near the bottom;
  use the same resolved bounds for rendering and pointer input.
- **Input/accessibility:** pointer open/select/outside-dismiss; keyboard open, navigate,
  choose, and dismiss; narration identifies current value and expanded state.
- **Sound:** activation for open/close and selection for a changed value, avoiding duplicate
  feedback for one gesture.

### 11.8 Keybind control

- **Purpose:** display and capture a keyboard/mouse binding.
- **Material (2026-11-17):** opaque raised `MaterialSurface.field` recipe; content stays
  centred. Listening/capture retains its selected wash and thin accent edge.
- **States:** rest, hover, pressed, listening, focus, and disabled. Listening is not merely
  hover and must remain obvious.
- **Input/accessibility:** keyboard-operable entry into capture, a documented cancellation
  path, current binding in visible and narrated output, and complete disabled gating.
- **Sound:** activation entering capture and a restrained state-change confirmation on
  accepted binding; no success sound on cancellation/rejection.
- **Pilot required:** exact focus/listening relationship and cancellation interaction.

### 11.9 ColorSwatch

- **Purpose:** display/select a color or open a color editor.
- **Shape (2026-11-14):** a rounded square at every Corner Radius (radius at most a quarter of
  its height), so a sample never reads as a switch knob.
- **Value (2026-11-14):** a colour setting row shows `#RRGGBB` (`#RRGGBBAA` when translucent)
  in secondary text just before the swatch (`SettingRowLayout.HEX_W` reserved); a
  swatch near the window tone (the factory graphite background) read as an empty box.
- **Material:** represented color is data; surrounding selection/action chrome follows theme.
- **Geometry:** swatch clipping follows theme roundness when rectangular; checkerboard is an
  implementation/data pattern.
- **Editor:** an opaque floating panel attached to the swatch; it does not reflow the list.
  It flips upward near the bottom and reduces the editing pad on short or narrow viewports.
- **States:** hover, press, selection, focus, and disabled, without changing the represented
  color itself.
- **Input/accessibility:** accessible label includes a useful color value/name when possible;
  color must not be the only indication of selection or error.
- **Sound:** selection/activation according to whether the action chooses or opens.

### 11.10 Card/tile

- **Purpose:** represent a feature, resource, profile, or other compound item; may navigate,
  select, toggle, or contain child actions.
- **Material:** role follows semantics. A raised tile is appropriate for an actionable item;
  a flat card inside an existing material container is valid when another layer adds no
  hierarchy. Selected peer items use the shared selection treatment; feature state follows
  the explicit elevation rule below.
- **Feature on/off (owner ruling, 2026-11-17):** elevation is the primary state cue, including
  in greyscale. ON is an opaque lit raised face with a shallow black soft shadow; OFF is an
  opaque flat `MATERIAL_RAISED` face at -3 HSL lightness points. The icon remains accent on /
  secondary grey off, and names stay primary in both states. Hover lifts either face by +2
  lightness points only; there is no resting/hover perimeter outline, accent wash/edge or
  separate indicator (dot, switch, pill, bar, badge or state text). The settings chevron stays
  hover/focus-only: top-right on tiles, trailing on rows. Layout, wrapping and hit behaviour
  are unchanged. This supersedes the 2026-11-14 icon-only state cue and tile hairlines.
- **List rows:** icon, then the name left-aligned on the icon's axis, then trailing state. A
  name is never centred in a row whose icon is pinned left.
- **Editor/list cards (owner report, 2026-11-17):** per-item editor cards (Item Scale's rows,
  the per-effect alert rows) and browser result cards are raised opaque cards — the raised
  control recipe, hover through the cached-state blend — with no resting outline: the bevel
  defines the edge. Their trailing glyph actions centre on the card face through the shared
  helper and the text block stays left-aligned. Non-unified screens keep the translucent band
  and hover outline.
- **States:** actionable cards use the button-like contract. Hover, press, selection, focus,
  and disabled are distinct from child-action states.
- **Input/accessibility:** the card's principal action and each child action must have
  non-overlapping hit semantics and accessible names.
- **Sound:** based on navigation, selection, or state change—not simply “card clicked.”

### 11.10a Empty state

An empty list shows one centred group: an optional 24 px muted icon, a headline in the
`HEADING` role, and one secondary line that names the next action (`ManagerListScreen`'s
`emptyIcon`/`emptyMessage`/`emptyHint`). A filtered list with no matches keeps a single
secondary line. A manager whose collection is empty (no waypoints in this world) shows no
search or filter row at all; it appears with the first item. A list setting inside a page (Keystrokes' Extra Keys) shows a muted "None"
in its label row's trailing value slot instead, above its Add control.

### 11.11 Tab/sidebar item

- **Purpose:** select one peer context without implying a destructive action.
- **Material:** selected item receives stained/accent emphasis. An inactive item may remain
  flat inside a material sidebar when depth would be redundant.
- **One accent selection per screen (2026-11-14).** The sidebar owns the accent selection. A
  second navigation level (the Settings sub-tabs) marks its selection by text alone: the
  body weight in primary text with a persistent neutral underline, no face (a heavier label
  shifted the tab labels as the selection moved); the list/grid layout pair marks the active layout
  with the opaque neutral field face and a primary icon.
- **States:** hover, press, selected, focus, and disabled. Focus and selection must not be
  visually confused.
- **Input/accessibility:** pointer and keyboard navigation with group position/state exposed.
- **Sound:** restrained selection/navigation event only when context changes.
- **Motion:** canonical symmetric hover; selection transition remains short and reversible.

### 11.12 Text field

Every text-entry field — search, rename, hex, numeric and ordinary entry — is an opaque
inset well on unified screens (search joined the recipe by the owner report, 2026-11-17: the
search bars had stayed flat translucent glass while every other field was depressed). The
inset bevel defines the edge, so no resting outline draws; focus remains the caret (plus the
selection fill below). Search fields keep their trailing magnifier, text window and hooks.
Non-unified screens keep the raised-glass / flat-fill contract.

- **Purpose:** enter or edit search, rename, hex, numeric, or ordinary text.
- **Material:** glass is the canonical Aurora input chrome for normal text-entry fields, not
  only search/navigation fields. Search, rename, hex, numeric, and ordinary entry all follow
  this semantic rule.
- **Data distinction:** input chrome does not convert represented data surfaces into glass. A
  color picker's hue/saturation area remains data even though its hex field uses glass.
- **States:** rest, focus/editing, invalid/rejected where applicable, and disabled. Caret and
  a subtle accent focus treatment communicate editing without changing material tint.
  A focused field paints its selection behind the text in `ACCENT` at 40% alpha, clipped to
  the visible window (`SELECTION_SOFT` was too faint on the factory field).
- **Invalid/rejected:** a rejected value never leaves silently. A short, specific message
  renders under the field it refuses, in the normal text size and `STATUS_ERROR` — "Name
  already used", "Name can't be empty", "Use 6 hex digits, like 3A7BFF". It appears live
  while typing where the format is knowable (hex, names) and clears the moment the input
  becomes valid again; an invalid Enter/Done keeps the field open with the user's text, and
  Escape keeps its screen's meaning while never committing an invalid value. A screen whose
  validation is save-time (the waypoint editor) names the first invalid field in the same
  voice instead. The message is measured with the active font in the field's real column:
  wrap between whole words, bounded ellipsis only when a single word cannot fit. Preventing
  invalid characters up front (the dashboard's filtered hex fields) is the complementary
  idiom — then there is nothing to reject, and no message is needed.
- **Input/accessibility:** preserve selection, cursor position, standard editing keys,
  narration, label/purpose, and value. Numeric commit/revert behavior must be explicit.
- **Editing history:** Aurora-owned `EditBox` fields, including Pulsar's `KTextField`
  subclass, support Ctrl+Z undo and Ctrl+Shift+Z / Ctrl+Y redo (the platform's editing
  modifier on macOS). This follows the themed-field takeover and `customTitleScreen` gate.
  Each field retains at most 100 edits. Consecutive typed word characters coalesce until
  whitespace/punctuation, a caret/selection/focus change, deletion, clipboard action, or
  a one-second pause. Deletions, selection replacements and pastes are separate steps.
  Undo restores text, caret and selection and updates the existing value responder.
  Programmatic `setValue` starts fresh history; responder sanitization during user input
  belongs to that input's edit. Vanilla continues to own word movement/deletion, selection,
  Home/End and clipboard handling; word movement uses spaces and ordinary movement uses
  code points, without adding grapheme-aware navigation.
- **Sound:** ordinary typing remains Minecraft/system behavior; commit/rejection may follow
  semantic sound policy without per-character custom sounds.

**Current implementation note:** the global themed `EditBox` mixin already applies glass
more broadly than the former search-only prose, but global targeting is not automatically the
ideal architecture. Future code should express the semantic field rule intentionally.

## 12. Screen composition

This section carries forward the validated v1/v2 settings hierarchy.

**Historical note:** that hierarchy borrowed the clarity of grouped iOS settings and the
breathing room/live row context associated with OxygenOS, then adapted both to Aurora's
single-size typography, compact game density, theme tokens, and material system. The result
is Aurora's own information architecture, not an instruction to imitate either product.

### 12.1 Screen title

Each detail screen has one screen title. It uses primary `ON_BACKGROUND`, the normal Aurora
text size, and left-aligns to the window's content column/row-label indent. Its distinction is
its unique position in the title band, not a locally invented size or color.

Control captions in the same broad area remain control labels and do not compete for the title
role.

### 12.2 Attached subtitle or attribution

A subtitle is optional. Use it for attribution or essential context that genuinely belongs
to the title, such as Better Hitreg's original-project credit. It uses secondary text at the
same size, sits directly beneath the title, and adds no independent top margin. It is attached
title metadata, not a second section.

Do not add subtitles reflexively.

### 12.3 Section headers

Every logically distinct settings group receives a `SectionHeader` where grouping is useful.
It has more space above than below, uses `ON_BACKGROUND_MUTED`, and acts only as a label. It
has no glass, fill, divider, or button behavior.

### 12.4 Section footers and body explanation

When a group needs explanation beyond its header and row labels, place a short footer after
the group's final row and before the next section header. It uses muted text and the row
indent. A footer explains the whole group; it does not interrupt a subgroup of rows.

Do not add a footer where the group is already self-explanatory. Essential operational
information should not be tooltip-only.

### 12.5 Setting rows and value lines

- Simple config-backed rows expose an individual factory reset only while modified. A small
  Material Symbols `restart_alt` icon follows the label inside its existing column; value and
  control columns never move. There is no empty icon slot at the default. Modified labels use
  cached, active-font ellipsis to make room for the 18 px target and 4 px gap, in compact and
  normal text. Row height remains stable. Compound/list/item editors and HUD layout scale do
  not acquire this action.
- Reset is an `IconAction` with the C-4 colour-only hover, focus hairline, Tab traversal,
  Enter/Space activation and one semantic click. Its tooltip and accessible name are both
  `Reset <label> to default (<displayed default>)`. Hidden resets leave narration and focus
  traversal. Disabled rows cannot reset. Reset immediately applies the cached effective
  release default through the row's existing setter/responder, then saves asynchronously.
  Nullable colour overrides return to inheritance. A single value is easy to change again,
  so this action is single-press; whole-feature Reset retains §8.7's press-again confirmation.
- Keybind labels show the same description tooltip as other setting labels at both densities.
- Labels are primary and left-aligned.
- A value/subtitle line is secondary and appears only when current state is not visible at a
  glance from the control, including state hidden behind a nested screen or a computed/live
  figure.
- Do not duplicate state already shown by a slider value, enum label, keybind pill, color
  swatch, or another obvious control representation.
- Boolean settings with otherwise hidden live/computed state are the main value-line use
  case. Collapsed compound rows may use one concise line when the expanded state would
  otherwise be invisible.
- The trailing control follows its component specification; row scaffolding does not invent a
  separate control style.

### 12.6 Example and preview areas

A setting whose effect is hard to picture from its label/control should receive a live
preview. By default the preview precedes the setting it demonstrates: see the effect, then
adjust it.

A trailing preview is valid only when it summarizes the cumulative result of settings above
it. Theme Preview is the established example. “Reads better” alone is not justification.

Previews should use the real rendering path where practical so they cannot drift from the
effect. Do not add a preview when the control already makes the result self-evident. A list of
swatch-bearing color rows needs no separate preview merely because it contains colors; each
swatch already exposes its value.

Shape and combined-treatment controls—such as crosshair/hitbox form, roundness, or derived
theme appearance—are strong preview candidates. The existing Crosshair preview demonstrates
the real-shape-path rule; Theme Preview demonstrates the valid trailing cumulative-result
case.

### 12.7 Group rhythm and ordering

Within a section:

1. Put the most commonly adjusted or highest-impact setting first.
2. Place dependent/modifying settings immediately after the setting they depend on.
3. Put destructive or irreversible actions last.
4. Isolate destructive actions in a trailing Maintenance-style section when the screen has
   more than a couple of groups. A small flat screen may use a clearly trailing action without
   inventing a one-row section.

The established Settings-tab inter-entry separation is 16 GUI pixels; it remains distinct
from tighter within-entry row spacing.

### 12.8 Grid screens

Grid screens are an exemption from list-specific layout, not a separate design language:

- A brand/wordmark such as the sidebar's “AURORA” mark is identity chrome, not the detail-
  screen title role.
- Section headers and footers do not apply to a genuinely flat tile collection.
- Grid tiles may show enabled/selected state through their persistent accent treatment, so a
  duplicated row-style value line is unnecessary.
- The Mods list layout's static feature description is explanatory copy, not a live state
  value line.
- Tile order is curated information architecture, not “most adjusted first.”
- Any real settings list embedded in a grid screen still follows group rhythm, row hierarchy,
  clipping, and input rules.

## 13. Scrolling, clipping, and input bounds

### 13.0 Rest on a row boundary

A scrolling body ends on a row boundary at rest (2026-11-14), so no row is cut through its
text before the user scrolls. A detail window's body band ends on the last whole row that
fits inside the fixed frame (`FeatureDetailScreen.fitFrame`); the main window's Settings viewport trims its band
to the last whole row (`AuroraScreen.contentViewport`), and the scissor, hit tests, scroll
extent and scrollbar all read that one band. Partial rows still appear while scrolling.

### 13.1 Top-edge fade

Scrollable content fades into the surface at the top clip boundary, and at the bottom
boundary while more content lies below (`ScrollFade.drawBottom`, engaged by the remaining
scroll distance).

The canonical implementation is `ScrollFade`:

- Paint it after scrollable content and outside static cache capture.
- Use a 16-GUI-pixel vertical gradient.
- Begin with the actual surface color at that position, alpha included, and end transparent.
- Scale engagement by `min(1, scrollPosition / 16)`. A resting list is pixel-untouched; the
  fade is fully engaged after one fade height has scrolled past. The bottom edge uses the
  shorter `ScrollFade.BOTTOM_FADE_PX` (8) because bodies rest on a row boundary (§13.0).
- Use raw scroll position, not overflow ratio.

The surface color is semantic and contextual:

- `WINDOW_FILL` inside the main window;
- the actual overlay dim for rows floating over a veiled world;
- `SURFACE` for a secondary container such as a sidebar;
- a screen's actual custom dim strength when that is the surface already present.

The fade's edge alpha has a floor, `ScrollFade.MIN_EDGE_ALPHA` (0xD8), above the surface's
own alpha (UI polish overhaul, 2026-11-14). At the factory 33% Background Opacity a fade to
the window tint verbatim was nearly invisible, and every council reviewer read rows cut at the
viewport edge as a rendering bug. The floor only darkens a 16 px gradient at an edge that has
content beyond it; it is not a cover, the scissor still clips, and the window's own tint keeps
Background Opacity as its single application point.

This rule applies to content that can cross a fixed viewport edge. A dropdown that moves only
by whole rows and never partially crosses its edge does not need the fade, and world-map wheel
zoom is not list scrolling.

### 13.2 Scissor is mandatory

The gradient softens an edge; it does not enforce it. Every scrollable viewport must use an
opacity-independent scissor for rows/content. When cached rows need clipping but surrounding
chrome does not, use separate cache layers.

**Historical note:** the original detail-screen pilot used a painted surface-color cover to
hide unscissored rows. At Background Opacity 0.1 the cover was 90% transparent, so Minimap and
Better Hitreg rows visibly crossed the title band. The fix was a real scissor plus separate
chrome/row caches. A painted cover must never again be treated as clipping.

Raised row rims may fade with their bodies; no special rim exception is required. Glass
submitted under a scissor must use the tracked glass scissor path so deferred rim finishing
reapplies the clip.

### 13.3 Input bounds

Rendered visibility and interactive availability must agree:

- A visually hidden or clipped item is not clickable or draggable outside the effective
  viewport. A present (not conditionally hidden) hosted control can receive traversal
  focus while clipped, but cannot activate until visible. Its list scrolls just far enough
  to reveal it, with an 8 px margin where the extent allows it.
- Manual hit testing uses the same dynamic row geometry and effective clip/fade boundary as
  rendering.
- Modal dim/above-dim ordering also applies to input: covered background controls cannot
  accept interaction.
- Scrollbar thumb, content target, and visible bounds derive from the same scroll extent.

The audit's `AuroraScreen` mismatch is a future correctness task, not a v3 exception.

## 14. Accessibility and robustness

Accessibility belongs to component semantics rather than a cleanup pass.

- Keyboard-operable controls expose visible focus and appropriate activation.
- Narration metadata conveys accessible name, role, current value/state, and disabled state
  where Minecraft's APIs permit it.
- Essential text remains practically readable and does not live only in muted/faint roles.
- Color should not be the sole carrier of critical state where a label, icon, position, or
  shape can reinforce it.
- Disabled controls remain identifiable while clearly unavailable.
- Motion is not the sole confirmation of state; architecture should permit future reduced-
  motion handling for structural animation.
- Sound is not the sole confirmation of success/failure; custom sound must have a clear user-
  control strategy.
- Pointer enlargement such as whole-row toggle activation must not create overlapping or
  surprising hit regions.
- Text truncation, wrapping, localization, GUI scale, and small-window behavior are part of
  robustness, not cosmetic edge cases.

Aurora should use Minecraft's accessibility facilities honestly without overstating their
coverage. Runtime narrator behavior was not fully verified during the 2026-09-14 audit
because the host lacked optional `libflite.so`; future narration work needs a capable runtime
environment.

## 15. Conformance and verification

### 15.1 Conformance review

A new or migrated component/screen should answer:

- Which semantic color, text, material, and interaction roles does each element have?
- Does theme roundness govern all rectangular chrome?
- Are rest, hover, pressed/manipulating, selected/on, focus, and disabled explicitly handled?
- Do button-like actions receive pointer, keyboard, narration, disabled, sound, and press
  services consistently?
- Do render clipping, input clipping, and dynamic geometry agree?
- Is essential text usable at difficult accents, both modes, and low opacity?
- Are represented data colors separated from chrome colors?
- Does glass have a complete flat/Wireframe/failure fallback and correct phase ordering?
- Is every exception semantic, narrow, and documented?

### 15.2 Future conformance harness

Aurora should gain a deterministic developer-only conformance harness showing representative
components and materials in controlled states:

```text
Buttons: rest / hover / pressed / focus / disabled
Toggles: off / hover / pressed / on / focus / disabled
Sliders: rest / hover / dragging / focus / disabled
Cards: rest / hover / pressed / selected / focus
Text: primary / secondary / muted / faint / accent
Glass: container / secondary / control / stained / above-dim
Theme: dark / light; corner radius 0 / 6 / 10 / 20;
       high / low opacity; representative difficult accents
```

This is a future verification tool, not a v3 documentation deliverable. `DevPilot.java`
remains the appropriate untracked development automation mechanism where applicable.

### 15.3 Evidence standard

- A clean compile is necessary but is not visual verification.
- Visible runtime changes require real runtime evidence.
- Prefer deterministic captures and source/log/state oracles.
- Use pixel/color measurement for measurable claims.
- Vision-model judgment must never be the sole proof of visual correctness.
- Theme-dependent claims must test at least the user's live configuration and the relevant
  factory/default axis; include dark/light, roundness, opacity, and difficult accents when
  they are causal.
- Risky visible changes should pilot on one to three representative components/screens before
  mod-wide rollout.
- Configuration schema changes must verify old-config/profile compatibility.
- Human input must not interfere with DevPilot-controlled sessions.
- Rollout commits should remain independently revertible.
- Any UI change must be checked for list-size complexity, per-frame allocation, shape-fill
  volume, and material panel budget as required by repository process.

These rules supplement rather than duplicate the operational safety detail in `AGENTS.md`.

## 16. Exceptions and extension policy

An Aurora screen may differ when its function genuinely requires it. Established exception
domains include:

- maps and data visualization;
- HUD content floating over the world;
- color-picking data surfaces;
- Minecraft item/effect/block content;
- explicitly documented domain-specific depth semantics;
- mechanical circular control parts.

An exception must be:

1. intentional;
2. narrowly scoped;
3. explainable in semantic or functional terms;
4. documented when it is not obvious.

“That screen already does it differently” is not a justification. “Reads better” alone is
not a justification when it contradicts an invariant. Repeated exceptions should trigger a
design-language review: either the shared component is incomplete or a real new role has
emerged.

New shared roles, motion categories, material parameters, sound events, or icon systems must
be introduced centrally and piloted. A local screen must not establish them by precedent
through repetition.

## 17. Current conformance gap

The 2026-09-14 audit records known implementation gaps against v3, including routing-dependent
button services, snap-in hover, incomplete focus/disabled states, manual card/tab behavior,
fixed main-screen radii, broad but implicit text-field targeting, `AuroraScreen` input-clip
drift, and unresolved on-accent/low-opacity robustness.

Those gaps are expected. Version 3 defines the destination; this documentation change does
not authorize opportunistic production fixes. Implementations should close one coherent gap
at a time through the roadmap below.

## 18. Implementation roadmap

**UNIFIED THEME — fixed-window correction (2026-10-06):** the latest user
feedback restores enclosing blur and outlines on the main Modules/Settings screen
and Profile/Waypoint managers. The frame is stationary; only its interior content
scrolls. Detail windows now have a viewport-sized stationary frame and separately
pinned search/add headers for Particles, Item Scale and Alerts. Title keeps its five
independent neutral blurred cards without an enclosing panel. Shared neutral action
cards (including HUD editor Aurora Settings/Reset Positions) have no painted outline;
primary/destructive actions and keyboard focus keep their established cues. Active
feature cards/selected peers use a bright accent outline over their wash and halo.
Search fields retain a neutral resting edge while typing, with the caret as focus cue.
The global `moduleGridView` preference saves immediately and survives reopening,
restart and profile switches (excluded from profile snapshots).
Ordinary embedded controls still inherit their card; floating popups remain opaque.
The renderer's bounded pool, priority degradation and screenshot/session/menu/resource
guards remain authoritative. Capture count now follows visible standalone cards.

Production appearance is Dark only. Legacy LIGHT configs/profiles migrate to DARK;
resolution and the dirty-check normalize it too. The Mode selector, Theme preview,
and Text & Fonts/Theme/Interface header popouts are retired. These settings remain
inline, with font/accent/corners/opacity customization intact. Repeated feature and
manager headings are removed; Better Hitreg's attached author credit remains.

Bundled text fonts rasterize at 9 px rather than 11 px; icon fonts remain unchanged.
Text reserves control/value space using native font measurements. Overflow layouts
are centered and wrap only between whole words, never split words across lines;
bounded ellipsis is the fallback for a word wider than its column. Full semantic
labels remain available. Cached wrapping invalidates on width/font/theme changes. UI scroll tracks use a finite
90 ms glide with one rounded geometry position for all layers, immediate thumb
dragging and exact rest; the pure legacy exponential API remains for diagnostics.
Typing resumes in a visible search after selection, without taking input from a
key-capture/editor or an open browser modal. Slider knobs paint above hover/focus.
Swatch color/checker pixels share a device-pixel rounded mask at rest and hover;
swatch cache eviction occurs only between frames, protecting deferred draws.

Blur remains 28 logical px (64 device px cap), saturation 1.35, brightness .92,
with a LINEAR frost (requested × opacity, 0 at the floor since 2026-10-30). Window density is the saved opacity ×255 (solid at max since 2026-10-30);
primary actions retain a legibility floor while remaining translucent. Data/status
colors and explicit player overrides retain meaning; HUD backing remains neutral.

The old per-element lighting/rim recipes below are historical/diagnostic paths,
superseded for production by this rollout. The diagnostic `BlurTestScreen` retains
its synthetic controls and lighting comparisons. Details and verification limits:
[UNIFIED_THEME_PILOT.md](reports/UNIFIED_THEME_PILOT.md).

Every phase begins with a narrow pilot and evidence review before broad rollout.

### Phase A — Interaction foundation

Create shared semantic interaction routing/services for focus, keyboard activation,
narration metadata, disabled behavior, and sound hooks. Adapt both custom and vanilla-backed
controls without changing all component visuals at once.

### Phase B — Motion and state primitives — **COMPLETE (2026-09-18)**

Correct shared hover to symmetric 140 ms behavior and establish state-complete primitives.
Pilot press/focus/disabled treatments before applying them mod-wide.

**Closure record (2026-09-18, `18f885e`):** every custom interactive
family runs the canonical state vocabulary — Button (all ordinary
actions), ToggleSwitch, Slider, EnumSetting (26 rows), ColorSetting /
interactive ColorSwatch (29 rows), KeybindSetting (17 rows), KeyList,
the tooltip fade (the one deliberate 200 ms supplemental exception),
PixelCanvas text actions, pack-browser navigation tabs + card hover,
AccentSetting peer selection, and the Waypoint data chips as explicit
data-only surfaces. Persistent value/selection state is independent of
hover wherever the semantics require it; disabled is authoritative on
every Phase B-complete component; semantic sound ownership is
exactly-once with silent no-ops and rejections; capture families hold
exclusive ownership with safe teardown. The finite immediate-hover
exception list and the Phase C+ deferral inventory live in
`ARCHITECTURE.md`'s Phase B closure record. Phase C begins from the
frozen component contracts recorded there.

### Phase C — Geometry and conformance — **COMPLETE (2026-09-21)**

Move rectangular chrome to resolved roundness, establish the icon-action primitive, and
couple render clipping with input bounds.

### Phase D — Color and contrast robustness — **REVERTED / DEFERRED (2026-09-24)**

Phase D's deterministic on-accent, low-opacity backing, and semantic contrast treatment was
implemented and manually evaluated, then intentionally reverted. The implementation was not
technically broken; manual review found that it changed Aurora's appearance more than its
benefit justified. Phase C color/contrast behavior is authoritative. Contrast may be revisited
later only as an explicitly requested, more conservative design effort.

### Phase E — Material refinement — **NEXT**

Evaluate glass continuity, panel seams, rims, lighting, and opacity hierarchy after semantic
roles are consistently applied. Do not begin with a shader rewrite or quietly reintroduce the
reverted Phase D contrast treatment; preserve Phase C color semantics unless a material effect
requires a strictly local adjustment.

### Phase F — Sound identity — **PLANNED**

Implement semantic sound routing and carefully designed Aurora UI assets only after the
interaction contract can deliver them consistently and user control is clear.

### Phase G — Optional R&D — **OPTIONAL**

Explore refraction/distortion only through the dedicated process in §7.2, after the existing
material system is coherent. It is not part of the v3 baseline.

## Compact pilot addendum — owner corrections, 2026-10-06

Keep the approved unified palette, material, native text size and motion. Main
settings use AURORA text only and separation by space/material without a sidebar
divider. Grid cards are square 80×80, retain 6px gaps, and center icon/name with
no chevron or reserved chevron space. Detail opening remains right-click.
The title menu displays the official white mark centered above the text-only
cards, with a preferred 48px mark and 12px gap; the combined group is centered.
*Superseded 2026-11-12 by the OneClient-style title screen below.*

Pack Tweaks is the compact detail reference. Compatible ordinary detail rows
share the original left-aligned 12px label inset and measured right controls;
simple rows are 24px, eligible inline sliders 28px, stacked/wrapped rows ≥36px.
Status subtitles reserve room. Specialized editors preserve their semantics;
Item Scale's nested ordinary sliders use the same layout rules. No text/pose
scaling, extra blur/glow, theme taxonomy removal or gameplay change. Search add
icons use native-pixel FreeType ink without resampling and unchanged button
geometry. Existing focus, switch, slider, roundness, opacity and tooltip policy
remain. Owner visual acceptance remains open; see OWNER_UI_CORRECTIONS.md.


### Completed desktop verification and capture ownership (2026-10-06)

AuroraScreen and FeatureDetailScreen opt into `ThemedScreen.parentGlassOnly()`.
`GlassSurface.card` declines before capture for embedded cards in these hosts;
their existing cached surfaces paint under the single parent window. Other
standalone-card hosts retain their existing behavior. Live GUI-scale 3/4 checks
confirmed one parent panel per frame, zero child-card captures/declines and no
pool exhaustion. Ordinary inline Settings captions/status subtitles also keep
left alignment. Final build/test: 415 passing; five new client screenshots plus
alternate-scale evidence are linked in OWNER_UI_CORRECTIONS.md. Owner visual
acceptance remains the gate for broader rollout and legacy taxonomy removal.

## Owner menu geometry correction — 2026-10-06

The owner's latest instruction retains the main-menu mark at its original 48px
preferred size and narrows all five menu cards to 180px. `MenuComposition` owns
the single centered logo/card composition: identical 180×22 paint/hit bounds,
4px gaps, 26px pitch, 126px card stack and 186px complete preferred height.
The mark reduces only for constrained height; supported logical viewports are
at least 240×160. The original white 256px asset and textured rendering remain.
FeatureDetailScreen headers are feature identity only: no Aurora mark, title
and attached subtitle at the same frame-left +12px inset as row labels, with
Reset/Done retained. Main settings branding/cards, compact rows, theme recipes,
roundness/motion, behavior and parent-only capture ownership remain unchanged.
GUI scales 3/4 and a constrained window were reviewed; the 421-test build passed.
See OWNER_MENU_GEOMETRY.md for exact geometry, files and fresh screenshots.
This supersedes the previous menu dimensions; UX-4 and broader rollout remain deferred.

## Settings group spacing and sidebar edges — 2026-10-06

Separate logical settings groups with left-aligned headings and spacing, rather
than framed section cards. AuroraScreen's Settings entries share a 20px gap
across painting, scroll measurement and pointer handling; the enclosing window
and functional controls retain their boundaries. Sidebar tabs and Profiles now
paint within the same 56×24 bounds used for hit testing and focus. A selected
unified tab owns its single accent edge; an unselected keyboard-focused tab
still receives a visible focus outline. Live GUI-scale 3 review confirmed these
changes and one parent glass capture per frame. The full 421-test build passed.

Feature detail screens also reserve an 18px blank band above each section
caption, including compact rows. This spacing is included in the shared row
height so painting, scrolling and pointer routing advance by the same amount.

Top-right Done buttons use the same neutral treatment as Reset, without the
primary button's accent fill or glow. This applies across feature details,
the profile/waypoint managers and the resource pack browser.

## Spxctra simplification pass — 2026-10-30

Applied under the owner's Spxctra Design Language brief (simplicity first; atmosphere
through restraint, not effects). Owner rulings recorded above still win where they are
specific (title cards, HUD editor actions, Reset/Done edges, search-field edge, blur
recipe, 18px section band between groups). These rules are now normative:

- **One emphasis per state.** Selected/on = soft wash + one 1px accent edge. Primary =
  strong fill. No accent glow or halo anywhere (`MaterialSurface` no longer rasterizes
  one). Glow carried no information the wash/edge/fill did not.
- **One face for neutral controls.** Dropdowns, keybinds and key-list add buttons share
  `MaterialSurface.field`, and every plain neutral (and resting destructive) `Button` wears
  the same face: opaque raised tone, no outline, no shadow, hover lifts the tone. It stays
  legible at any window opacity and a settings column never mixes treatments. A listening
  keybind keeps the selection treatment; themed buttons (title, HUD editor, Reset/Done)
  keep their owner-specified treatments.
- **One capture per window.** A screen with an enclosing window declares
  `ThemedScreen.parentGlassOnly()`; child buttons/rows paint cached faces. Per-button
  blur tinted each control by whatever sat behind it (sky-blue chips, brown buttons) and
  multiplied GPU work. Exceptions: the title screen's independent cards (owner ruling)
  and the window-less HUD editor.
- **Title screen (owner redesign, 2026-11-12, after OneClient's).** One centred
  column: the 56px mark, an "AURORA" wordmark (its own `aurora:wordmark` provider,
  never re-cased), three full 200×24 outlined cards with leading icons, then two half
  cards. A right column holds the player model and the account card, centred in the
  space right of the buttons; below ~560 GUI px wide it becomes a chip in the top bar.
  The top-right icon row is Aurora, Fullscreen and Quit. Geometry is
  `MenuComposition`. The backdrop is `MenuAtmosphere`: a cached theme gradient plus
  slow, faint light motes and haze bands — an owner-approved exception to the
  no-ambient-effects default; it freezes when Screen Effects is 0. No panorama and no
  glass on this screen.
- **Repeated rows rest quietly.** Manager rows separate by spacing and tone (no per-row
  border inside a bordered window). Destructive actions rest like their neutral siblings
  with a red label and commit to solid red on hover/press. A grid has no repeated primary
  action: pack cards' Install is neutral; the detail modal keeps the one primary Install.
- **Copy, not identifiers.** Section captions render as authored (no forced capitals).
  Dropdown values without explicit labels render in sentence case (`LEGACY_1_8` →
  "Legacy 1.8"). A caption that opens a list drops the band above it — the window's
  header band already separates it; later captions keep the group band.
- **Inactive sidebar items are flat at rest** (§11.11); hover shows a face, focus keeps
  its outline, the selected item keeps wash + edge.
- **Panels that host controls use the panel tone.** An opaque surface carrying buttons
  (map toolbar/status bar, waypoint editor, mapping data) uses `MaterialSurface.floatingPanel`
  so controls sit one tone above it; `floating` (raised tone) stays for tooltips/popups.
- **Focus never hides under selection.** A focused element that already wears the accent
  selection edge (selected tab, enabled tile, selected layout peer) draws its focus ring
  2 px outside that edge.
- **Previews follow roundness.** The crosshair preview is a rounded inset tone, not a
  square outlined box. It draws at a whole-number zoom (3x to 6x) chosen so small shapes fill
  about two thirds of the well; a 2 px dot at a fixed 3x read as a speck.

Evidence, measurements and limits: `SPXCTRA_UI_SIMPLIFICATION_REPORT.md`.

## Compact pass — uniform window, smaller UI, collapsible sections (2026-10-30)

Owner request: every Aurora window the same size, smaller text/controls, tighter spacing,
less scrolling, and expandable categories for long option lists. Owner follow-up: feature
cards keep their original 80×80 size; the window must not be oversized for the smaller UI.

- **One window.** `UiDensity.window` (336×288 GUI px after the same-day follow-up, shrunk to
  fit with a 12 px margin) is
  the frame for the main screen, every feature detail screen, the Profile and Waypoint
  managers, the waypoint editor, color picker, mapping data and the resource-pack browser
  (which runs its layout inside the frame as a translated viewport). The content well
  (254×252) shows exactly a 3×3 grid of the 80×80 cards — the owner's target; the first
  draft of the pass was 420×272 with 4 columns. The title screen,
  HUD editor and the full-screen world map are not windows and keep their layouts.
- **Compact text.** Inside Aurora windows (`ThemedScreen.compactText`, default = windowed
  hosts) text uses `<font>_ui`, the same bundled face rasterized at 8 instead of 9 px —
  crisp, no pose scaling; measurement uses the same style. Vanilla-font users
  (SansSerif / renderer Off) keep vanilla text.
- **Compact metrics** (`UiDensity`): 24 px header band with 16 px header buttons; setting rows
  18 px (sliders 20, wrapped 28) with 2 px gaps; 14 px field controls (dropdown 84 wide,
  keybind/action 72); 24×12 toggles; 14×14 square color swatches (accent presets the same
  squares, its Custom slot a wider labeled pill); 4.5 px slider knob (5 before 2026-11-14); section band 8 px.
  Feature cards stay 80×80 with 6 px gaps (3 columns, 3 rows visible); list rows are 24 px
  with 4 px gaps, nine visible (owner decision 2026-11-14; 32 px rows were half empty).
- **Per-pixel color gradients.** Both color pickers (`ColorPickerScreen` and
  `ColorSetting`'s floating editor) rasterize the saturation/lightness pad and the
  hue/alpha strips one sample per DEVICE pixel into small `UiLayerCache` buffers
  (`writeArgb`), blitted per frame and re-rasterized only for the value they display —
  the former GUI-cell grids (40×40 / 32×32) banded visibly at every GUI scale. Preview
  samples are squares, not wide bars.
- **Collapsible sections.** `SectionHeaderSetting` is a disclosure control (click, Enter or
  Space; chevron at the trailing edge; focus ring). Lists with three or more sections open
  with only the first expanded *when the fully expanded list would scroll*; otherwise all
  open. A player's choice is remembered for the session per feature and caption. A caption
  directly under a collapsed caption drops its band so collapsed runs read as one list.
- **Waypoint editor** shows Location and Details together (the "Show details" switch is gone).

## UI polish overhaul — 2026-11-14

A council of independent vision models rated screenshots of eleven screens after each round
(evidence and ratings: `docs/reports/2026-11-14-ui-polish-overhaul.md`). The rules below are
written into the sections they belong to; this list records what changed and which earlier
rulings it supersedes.

- **Factory palette** (§3.2): Indigo accent on graphite for new installs (owner decision).
- **Text roles** (§4.1): `TITLE` (11 px raster) and `HEADING`, a heavier cut of the user's family. Supersedes
  "Version 3 does not introduce a type scale" for those two roles only; body text keeps the
  one size.
- **One window size** (owner direction 2026-11-15): every Aurora window, the main window on
  either tab and every feature detail window, keeps the shared frame however little or much it
  holds; content scrolls inside it. Supersedes the 2026-11-14 content-fitted detail windows and
  Settings pages. Detail windows still carry the feature's one-sentence summary under the title.
- **Rest on a row boundary** (§13.0) and a shorter bottom fade with an alpha floor (§13.1).
- **Feature state is the icon colour, not a selection** (§11.10): neutral tiles and rows,
  an accent icon when on, a neutral grey icon when off. Supersedes the enabled-tile accent
  edge and wash. The owner confirmed icon colour as the only cue after five plateaued rounds,
  and ruled out an Enabled switch on feature detail pages; the tile or row stays the toggle.
- **The sidebar's first item is "Features"** (owner decision; was "Mods"), matching the search
  field's "Search features and settings". The title screen's Mods card (Mod Menu) keeps its name.
- **One accent selection per screen** (§11.11): the sidebar keeps it; sub-tabs mark selection
  by text weight, the layout pair by the neutral field face.
- **Controls** — filled OFF track shared by switches and slider tracks (§11.4); one inline
  slider row with a bounded track and the unit in the value (§11.5); rounded-square swatches with a light hairline
  on dark samples (§11.9); ghost Reset beside an accent-filled Done, one primary per header
  (§11.2a);
  disabled rows mute their label.
- **Settings pages** — group headings in `HEADING`, a one-line summary under each heading;
  collapsible captions in the heading weight, one step quieter.
- **Managers** — titled header with the action beside Done; structured empty state (§11.10a);
  the waypoint group chip only exists while a group is chosen.
- **Names** — "Toggle Sprint/Sneak" is "Sprint & Sneak" (owner decision), so every grid label
  fits one line.


## Depth pass — 2026-11-17

The owner explicitly approves purposeful vertical gradients and shallow bevel depth to make
state and hierarchy clearer. This supersedes the 2026-10-30 “one face for neutral controls:
no outline, no shadow” / flat-child restrictions where they conflict, the 2026-11-14 icon-only
feature state and tile hairlines, and the former filled OFF tracks. The title screen, HUD
editor, ghost Reset / Done positions, sidebar selection, 336×288 window, 80×80 tiles, gaps,
columns and row heights are untouched. Done keeps its primary role, with the primary depth
recipe. Settings chevrons remain hover/focus-only (2026-11-14 owner ruling).

Round 2 replaces the first pass's vertical-only bevel lines with a curvature-aware height
field. One light is shared by every lit face: **L = normalize(-0.35, -0.75, 0.55)** in
screen coordinates (y down). Each covered device pixel samples its face's rounded-box SDF
`d` (negative inside). Circular knobs use the circle case of that same SDF. SQUARE keeps an
exact box silhouette and switches facets at the 45-degree medial diagonal.

The rounded bevel band is `clamp(round(radiusDevice * .8), round(1.5 * scale), 4 * scale)`
(owner, 2026-11-17: keep the rim-and-ramp style, slightly more pronounced). **Gem facets**
(owner, 2026-11-17): as the corner radius approaches 0 the bevel becomes a 2.25 GUI px planar
chamfer whose four sides are flat 45-degree facets, each lit at its own angle (top brightest,
then left, right, bottom), with crisp creases on the corner diagonals. `facetBlend =
clamp(radius / (2 * scale), 0, 1)` blends band width and slope from the facet (0) to the
pillowed roll-off (1), so dragging Corner Radius never jumps. The pillow's slope is its own
`h'(t)` (the bevel is as tall as it is wide), capped at 3 at the silhouette. The band never exceeds 22% of the face's shorter side
(domes exempt), so small controls keep a flat plateau for their text: a 14 px field gets about
3 px of bevel. With `t = clamp(-d / band, 0, 1)`, height is
`sqrt(1 - (1-t)^2)`, negated for inset faces. Pressed scales height to .4 and disables
specular. Domes use a `.55 * radiusDevice` band (plateau radius .45 of the radius), while
SQUARE knobs use the gem chamfer. The physical outward normal is
`normalize(gradient(d) * slope * k, 1)` with `slope = lerp(1, min(3, h'(t)), facetBlend)` and **k = 1**. Equivalently it is the
negative gradient of inward distance `-d`; this sign makes the upper-left rim catch the
upper-left light. The draft spec's minus sign with an *outward* SDF gradient would invert
that lighting; this sign clarification preserves the required highlight direction.

**Disabled controls (2026-11-17).** A disabled control keeps the raised form in the
`MATERIAL_PANEL` tone with no shadow and a muted label, so it still reads as a control; a flat
panel-tone face disappeared into the window.

**Switches (2026-11-17).** The ON track is the same recessed well as OFF, filled with the accent
(`Light.INSET`), so the two states differ only in colour; the knob rests on whole pixels with a
2 px margin (`h - 4` knob), exactly centred on the track.

**Text and glyph centring (2026-11-17).** Text on faces and rows is placed with
`AuroraFontRenderer.textY`, which centres the active font's measured cap/x-height ink (from the
baked glyphs of that style, cached per style and resource generation; descenders, underline and
shadow excluded) on the box, instead of `(h - lineHeight) / 2` plus ad-hoc nudges. Icons
(chevrons, checks, keyboard glyphs) centre on the same axis, so a label and its glyph share one
centre line. Horizontal centring centres the fitted text's visible ink (the glyph quads'
left/right bounds, `AuroraFontRenderer.inkMid`, cached per style) rather than its advance
width, so side bearings never shift a centred label; the sub-GUI-pixel remainder is applied
through the pose snapped to the device-pixel grid, so centred text is accurate to half a device
pixel and stays crisp; faces keep `TextAlignment.FACE_PAD` (5 px)
equal padding, clear of the widest bevel. Descriptor lines use one pitch,
`lineHeight + 2`, and the same gap below the label they describe.

Final HSL lightness delta is `faceRamp(y) + 20 * (dot(n,L) - L.z) + 10 * spec`, with
`spec = pow(max(0, reflect(-L,n).z), 24)` only on raised rims. Inset/pressed have no specular;
the plateau has neither rim nor specular delta. Hue, saturation and alpha are preserved
through lighting; original captured AA coverage is unchanged. **No separate top/bottom
edge lines remain in any depth role.** All lighting is baked before upload, never through
the gradient shader's flat capture fallback. Cached state endpoints blend with texture alpha.

| Role | Face top → bottom (HSL points) | Height / rim | Shadow: elevation / softness / black alpha |
|---|---|---|---|
| Raised control | `MATERIAL_RAISED` +5 → -3 | raised + specular | 1 / 2 GUI px / 14% |
| Raised hover | `MATERIAL_HOVER` +5 → -3 | raised + specular | same |
| Pressed | `MATERIAL_PRESSED` -3 → +2 | raised height ×.4, no specular | none at full press; fades out during press |
| Inset: segmented / numeric / preview well, OFF switch track | `MATERIAL_SUNKEN` +1 → +5 | inset, no specular | none |
| Slider track | `MATERIAL_SUNKEN` +3 → +7 | inset, no specular | none |
| Primary: Done / primary action | `ACTION_STRONG` +6 → -4; top capped at +3 if `ON_ACTION` luminance >.5 | raised + specular | 1 / 2 GUI px / 14% |
| Feature tile / list row ON | opaque `MATERIAL_RAISED` +11 → +3 | raised + specular | 2 / 4 GUI px / 20% |
| Feature tile / list row OFF | opaque `MATERIAL_RAISED` -5 flat | flat | none |
| ON hover | +13 → +5 | raised + specular | same as ON |
| OFF hover | -3 flat | flat | none |
| Disabled | flat `MATERIAL_PANEL` (muted thumb colour retained) | flat | none |
| Selected segment / peer / ON chip | raised + `SELECTION_SOFT` wash + existing thin accent edge | raised + specular | same as raised |
| Slider / switch knob | existing face colour, no face ramp | shallow dome + specular | 1 / 2 GUI px / 18% |

Shadow offsets follow `elevationDevice * normalize(.35, .75)`; softness is twice elevation.
The round-2 distances are **GUI pixel elevations converted to device pixels at bake time**,
superseding the first pass's fixed device-pixel shadows. Inset, OFF and disabled faces cast
none. Hover's tile face ramp is exactly +2, weaker than the ON/OFF separation; pure math tests
require at least 8 mean greyscale points for rest ON/rest OFF and rest ON/hover OFF in both
owner themes. Geometry and icon colour remain the only two tile state cues.

Tiles and module rows have opaque faces. The window remains glass; Background Opacity is
still only the `WINDOW_FILL` alpha. Its body has no gradient. The `WINDOW_OUTLINE` draw uses
**55% of that outline token's alpha**, a fixed constant independent of window opacity.
Controls, toggles, sliders and fields — search included (owner report, 2026-11-17, extending
this pass's coverage) — remain opaque. Preview **wells** may use
inset lighting; the represented data painted inside them is unchanged. Per-item editor cards
and browser result cards are raised opaque cards (§11.10).

For a custom Background Color, only `MATERIAL_RAISED`, `MATERIAL_HOVER` and
`MATERIAL_PRESSED` cap HSL saturation at `min(backgroundSaturation * .35, .10)`, preserving
each lightness and alpha. Window/background tint and opacity, other tokens and stock graphite
are untouched. This is a **reversible owner-review tuning item** in `UnifiedPalette`, not a
persisted setting change or another opacity factor.

`MaterialLighting` in core owns SDF, height, normal, light and colour math.
`UiLayerCache.lightFace` loops captured device pixels; `softShadow` composites behind them.
Legacy RGB `shadeVertical` remains only for pre-existing owner-themed cards/popups. Module
and control shadows are separate cached rasters behind state endpoints, avoiding doubled
shadow opacity during blending. Knobs bake the shadow with their face. Template keys include
recipe/state, geometry and radius; version stamps include theme generation, scale, fill and
border. Templates survive screen changes and resizes in a primitive LRU capped at 256 entries
and 16 MiB of RGBA image backing (at most another 16 MiB of texture pixel backing; driver
overhead is not measured). Identity includes exact fractional geometry and colours; hash
collisions are checked against the full identity and treated as misses. Theme generation,
GUI scale and roundness changes invalidate the pool, and returning to gameplay (no screen
open) releases it: no HUD draws these faces, so nothing is retained while playing. Evictions and oversized, unretained
rasters wait until the next extraction boundary to be freed, so transient backing for the
current frame is additional to the retention cap. Reusing a template never mutates its pixels.
No new callbacks,
dependencies or frameworks are introduced. No new per-frame lighting math or static GUI
fills; the control shadow adds a cached blit. Text, icons, headings, sidebar, scrollbars,
focus rings, represented swatch/crosshair/checkerboard data and the title screen /
HUD editor retain their existing treatment; search fields moved to the inset recipe with the
2026-11-17 owner report (superseding this pass's earlier "search glass" exclusion).

The bake uses direct ABGR buffer access. A conservative plateau cross skips SDF/normal/
specular work; the remaining band and corner pixels evaluate the original equations once.
Straight rim samples are reused per row/column. Face ramps and uniform-source HSL conversions
are reused per row. Shadow SDF work is bounded
to its offset/softness rectangle, skips opaque pixels, and reuses separable axis terms. A cheap
outer scan preserves the old pass's normalization of hidden RGB in fully transparent pixels.
Frozen old-path differential tests require bit identity, including AA and shadow compositing.

Implementation verification is offline: colour and NativeImage raster tests, source contracts,
Python tooling tests and the three-line parity build. The parent owns the required live/factory
ROUND/SQUARE screenshots, overflow review at small/large viewports, greyscale ON/OFF review,
and `RenderUtil.fillsSubmitted()` / CPU probes. Runtime fills and CPU cost are unmeasured until
those probes run; a headless test only verifies the raster passes submit zero GUI fills.

### Keystrokes key styles

**Flat** (default; owner, 2026-11-17: "the key text on a flat coloured background, no outline,
3D design or anything", the way Lunar Client does it) is one face per key in the key colour with
its legend; pressing swaps the face to the accent. No shell, rim, outline or travel. The former
Translucent shell-and-rim style is retired; saved `TRANSLUCENT` values load as Flat.
**3D Keycap** is the opt-in represented-keyboard material: an outer rounded skirt,
8% back / 18% front / 12% side planar walls, and a shallow concave cylindrical top dish. The
single upper-left material light shades every facet; radius 0 keeps crisp gem-like corner facets.
Pressing sinks the top by 1 GUI px, shortens the front wall by the same amount, tightens the
contact shadow and blends in the existing 30% pressed accent.

**Legends (both styles).** One routine places every legend: the text's visible ink (glyph
left/right bounds and the active font's cap/x-height) is centred on the label area (the whole
face for Flat, the top surface for 3D Keycap, the upper part of a mouse key above its CPS
line), with the origin snapped to the device grid. Each row shares one size, taken from its
widest legend (3 px side clearance on a flat face, 1.5 px on a keycap's top) and rounded down to
whole device pixels per font pixel when that costs at most 15%, so W/A/S/D, the mouse
buttons, Shift/Ctrl and the extra keys never differ in size within a row; nothing is scaled up.

`KeycapProfile` owns pure core geometry/lighting; `KeycapRenderer` owns a HUD-lifetime primitive
LRU of 48 entries and 4 MiB of retained RGBA pixels, keyed by exact geometry, colour, pressed
state, radius, device density and theme generation. A settled cap is one blit and a transition
two endpoint blits. Evicted or oversized rasters retire at the next extraction boundary. No new
callback or `Timed` registration is needed: drawing remains inside the module's measured render.
