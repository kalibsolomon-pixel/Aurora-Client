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

Aurora uses one practical UI text size. Version 3 does not introduce a type scale. Hierarchy
comes from semantic color, position, casing, spacing, alignment, and grouping.

Equivalent roles must not receive arbitrary local size, shadow, weight, or color treatment.
Material Symbol sizing is iconography, not typography.

| Role | Normative treatment |
|---|---|
| Screen title | Primary, normal Aurora UI size, uniquely placed in the title band. |
| Attached subtitle/attribution | Secondary, same size, directly attached below the title. |
| Section header | Muted, generally uppercase, more space above than below. |
| Setting label | Primary, left-aligned to the section content column. |
| Value/subtitle line | Secondary, beneath the label only when it exposes otherwise hidden state. |
| Footer/body explanation | Muted, wrapped, left-aligned to the group. |
| Button/control label | Primary or `ON_ACCENT` according to its material. |
| Card/tile title | Primary; concise and stable. |
| Card description | Secondary or muted according to importance; never the only operational instruction if muted. |
| Tooltip | Readable supporting text; it supplements rather than replaces essential labels. |
| Metadata/status | Secondary by default; semantic color may reinforce status but cannot be its only carrier where practical. |

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

Square mode means square UI chrome. A screen-local radius must not survive only because that
screen predates resolved roundness.

Explicit exceptions are mechanical, circular, or data-driven geometry: toggle thumbs,
slider knobs, color wheels, circular handles, represented game shapes, map markers, and
similar controls whose function inherently requires the shape. A capsule track may remain a
capsule where the control's mechanics require it. The exception belongs to the semantic
part, not its surrounding rectangular chrome.

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

**Current implementation note:** the default frost request is 24 device pixels and resolves
as `requested × max(1/6, sqrt(backgroundOpacity))`. This makes the low end clearer while full
frost arrives toward the upper part of the opacity range. Stained tint currently keeps a
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

**Nested-material ownership rule (Phase E pilot):** an enclosing window or panel owns the
backdrop sample, blur, and outer rim. A child that is only an interaction subdivision of that
same region uses an embedded control treatment: a centrally derived local tint plus its
existing hover/focus/state overlays, with no second backdrop sample and no child rim. A child
still requests isolated glass when it is a genuinely separate raised object, crosses a parent
boundary, or floats independently. Stained state remains permitted on an embedded control;
the stain communicates selection/primary emphasis, not another structural layer. A selected
card or selected peer may instead remain isolated raised stained glass when frost,
directional lighting, and the glossy rim are the product's intended persistent highlight;
Aurora's enabled module cards and selected navigation/view peers are canonical exceptions.
Their unselected peers remain neutral raised glass when the whole control group is intended
to read as a set of physical frosted controls.

Floating tooltip material is stable token-backed chrome rather than live glass. It uses one
low-emphasis edge and shadow, respects theme roundness, and does not inherit the window's
opacity merely because it originated inside that window. This is a material-depth decision,
not adaptive contrast behavior.

### 7.4 Integration invariants

The following are both design and renderer-safety rules:

- Background Opacity has one application point: `WINDOW_FILL` alpha. Do not multiply a
  second opacity factor into the material. Centrally defined rim/frost derivations from that
  value are not additional user-facing opacity controls.
- Containers are depressed; controls are raised; stained surfaces remain selected/primary.
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

## 9. Motion

Aurora uses a small semantic vocabulary:

| Category | Purpose | Baseline |
|---|---|---|
| Micro interaction | Hover and compact state response | 120–150 ms; canonical hover is 140 ms. |
| Press | Immediate accepted-input response | Approximately 90 ms where compression fits. |
| Recovery/state settling | Return from press or settle a discrete state | Approximately 150–200 ms, restrained overshoot only where physical. |
| Structural transition | Popup/modal or context-preserving structural change | Longer than micro motion, but brief and non-blocking. |
| Continuous manipulation | Slider drag, scrolling, toggle interpolation | Direct manipulation or smooth convergence, not staged decoration. |

Preferred easing principles:

- Ease-out communicates immediate response and quick settling.
- Ease-in-out is appropriate for reversible structural movement.
- Overshoot is reserved for small physical recovery and must remain subtle.
- Interruption/reversal should continue from current progress rather than restart or snap.
- Frame-rate-independent time is required; fixed per-frame deltas are not acceptable.

Use existing `AuroraAnim` capabilities where they fit. The design language does not require a
new animation framework.

Aurora has no mandatory universal screen transition. Future transitions should preserve
spatial/contextual continuity, avoid delaying interaction, use the shared vocabulary, and
avoid gratuitous full-screen motion. Modal transitions may be more explicit because they
communicate depth.

Architecture should allow larger structural animation to be reduced or disabled later. Sound
and animation must not be the sole confirmation of an operation.

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
- **Material:** raised neutral control; stained for the single primary/selected action;
  semantic destructive treatment for destructive action.
- **Geometry:** theme-controlled rectangular chrome and a compact, usable hit target.
- **States:** symmetric hover; immediate physical press; persistent focus independent of
  hover; readable, non-interactive disabled state.
- **Input/accessibility:** pointer and keyboard activation, narrated accessible name, disabled
  state conveyed.
- **Sound:** activation/navigation/destructive policy according to semantics, not adapter.
- **Motion:** current restrained compression/recovery is the baseline where glass/fallback
  compositing can support it.

### 11.3 Icon action/button

- **Purpose:** a compact discrete action represented primarily by an icon.
- **Material:** same semantic choices as Button; it is not exempt because it lacks text.
- **Geometry:** consistent hit target with optically centered shared icon.
- **States/services:** full button-like contract.
- **Accessibility:** always has a non-glyph accessible label; tooltip when the visible icon is
  not sufficiently self-explanatory.
- **Pilot required:** exact focus, pressed, and tooltip treatment for very small actions.

### 11.4 ToggleSwitch

- **Purpose:** directly switch one persistent Boolean state.
- **Material:** opaque token control by current convention; on state uses accent/selected
  semantics. Thumb remains circular in all roundness modes.
- **States:** off/on must be unambiguous; hover, accepted press, focus, and disabled must each
  be defined. Disabled must not look like interactive rest.
- **Input/accessibility:** clicking its associated row may toggle only when that enlarged hit
  target is intentional and does not conflict with child actions. Keyboard activation and
  narration include the current on/off state.
- **Sound:** one state-change event per accepted toggle.
- **Pilot required:** exact hover/press/focus/disabled visuals.

### 11.5 Slider

- **Purpose:** choose a value from a continuous or stepped range.
- **Material:** opaque track and knob; accent fill communicates the chosen portion. Knob is a
  mechanical circular exception to Square mode.
- **States:** rest, hover, dragging, focus, and disabled. Dragging remains visibly active when
  the pointer leaves the original knob bounds.
- **Input/accessibility:** pointer drag; keyboard increments when focused; accessible label,
  range, and current value. Click-to-edit numeric fields follow Text field rules.
- **Sound:** no full click per value; optional restrained boundary/commit feedback may be
  piloted later.
- **Motion:** direct during manipulation, smooth only where it does not lag the pointer.

### 11.6 SegmentedControl

- **Purpose:** choose one item from a small mutually exclusive set.
- **Material:** containing/control treatment centrally derived; selected segment uses stained
  semantics, unselected segments remain neutral.
- **States:** each segment defines hover, press, selected, focus, and disabled. Selection must
  remain distinguishable without hover.
- **Input/accessibility:** arrow or equivalent keyboard navigation should be supported by the
  future adapter; narration identifies the group, option, and selection state.
- **Sound:** discrete selection, without replay when the current option is reselected unless
  that action has a separate semantic effect.

### 11.7 Enum/dropdown

- **Purpose:** select one value from a longer or context-dependent set.
- **Material:** raised control field; expanded popup is an explicit above-dim control/surface
  when the screen uses a dim layer.
- **States:** field hover/press/focus/disabled; expanded state; option hover/focus/selected/
  disabled where applicable.
- **Input/accessibility:** pointer open/select/outside-dismiss; keyboard open, navigate,
  choose, and dismiss; narration identifies current value and expanded state.
- **Sound:** activation for open/close and selection for a changed value, avoiding duplicate
  feedback for one gesture.

### 11.8 Keybind control

- **Purpose:** display and capture a keyboard/mouse binding.
- **Material:** raised control; listening/capture mode may use stained active treatment.
- **States:** rest, hover, pressed, listening, focus, and disabled. Listening is not merely
  hover and must remain obvious.
- **Input/accessibility:** keyboard-operable entry into capture, a documented cancellation
  path, current binding in visible and narrated output, and complete disabled gating.
- **Sound:** activation entering capture and a restrained state-change confirmation on
  accepted binding; no success sound on cancellation/rejection.
- **Pilot required:** exact focus/listening relationship and cancellation interaction.

### 11.9 ColorSwatch

- **Purpose:** display/select a color or open a color editor.
- **Material:** represented color is data; surrounding selection/action chrome follows theme.
- **Geometry:** swatch clipping follows theme roundness when rectangular; checkerboard is an
  implementation/data pattern.
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
  hierarchy. Selected/on treatment uses stain or a compact accent indicator as appropriate.
- **States:** actionable cards use the button-like contract. Hover, press, selection, focus,
  and disabled are distinct from child-action states.
- **Input/accessibility:** the card's principal action and each child action must have
  non-overlapping hit semantics and accessible names.
- **Sound:** based on navigation, selection, or state change—not simply “card clicked.”

### 11.11 Tab/sidebar item

- **Purpose:** select one peer context without implying a destructive action.
- **Material:** selected item receives stained/accent emphasis. An inactive item may remain
  flat inside a material sidebar when depth would be redundant.
- **States:** hover, press, selected, focus, and disabled. Focus and selection must not be
  visually confused.
- **Input/accessibility:** pointer and keyboard navigation with group position/state exposed.
- **Sound:** restrained selection/navigation event only when context changes.
- **Motion:** canonical symmetric hover; selection transition remains short and reversible.

### 11.12 Text field

- **Purpose:** enter or edit search, rename, hex, numeric, or ordinary text.
- **Material:** glass is the canonical Aurora input chrome for normal text-entry fields, not
  only search/navigation fields. Search, rename, hex, numeric, and ordinary entry all follow
  this semantic rule.
- **Data distinction:** input chrome does not convert represented data surfaces into glass. A
  color picker's hue/saturation area remains data even though its hex field uses glass.
- **States:** rest, focus/editing, invalid/rejected where applicable, and disabled. Caret and
  a subtle accent focus treatment communicate editing without changing material tint.
- **Input/accessibility:** preserve selection, cursor position, standard editing keys,
  narration, label/purpose, and value. Numeric commit/revert behavior must be explicit.
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

### 13.1 Top-edge fade

Scrollable content fades into the surface at the top clip boundary. The bottom edge has no
automatic counterpart because lists terminate into their own padding/footer; add another
edge only if a real collision is demonstrated.

The canonical implementation is `ScrollFade`:

- Paint it after scrollable content and outside static cache capture.
- Use a 16-GUI-pixel vertical gradient.
- Begin with the actual surface color at that position, alpha included, and end transparent.
- Scale engagement by `min(1, scrollPosition / 16)`. A resting list is pixel-untouched; the
  fade is fully engaged after one fade height has scrolled past.
- Use raw scroll position, not overflow ratio.

The surface color is semantic and contextual:

- `WINDOW_FILL` inside the main window;
- the actual overlay dim for rows floating over a veiled world;
- `SURFACE` for a secondary container such as a sidebar;
- a screen's actual custom dim strength when that is the surface already present.

Do not invent a stronger fade color. The fade must dissolve into the backdrop already there
and must not create a second Background Opacity application point.

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

- A visually hidden or clipped item is not clickable, focusable, draggable, or scroll-
  addressable outside the effective viewport.
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
Theme: dark / light; round / slightly round / square;
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
