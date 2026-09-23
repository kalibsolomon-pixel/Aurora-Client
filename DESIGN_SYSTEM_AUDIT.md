# Aurora Design System Audit

Investigation date: 2026-09-14<br>
Code baseline: `master` at `f36b1178d7583f5e8df74ef06d8c819b34c08f83`<br>
Scope: read-only investigation of the shipped UI system. This document records current behavior; it does not amend `DESIGN_LANGUAGE.md` or adopt a v3 specification.

## Executive summary

Aurora already has a coherent visual vocabulary, but it is more centralized at the rendering and theme-token layers than at the interaction layer. The strongest invariants are semantic glass roles, a single-accent palette, one practical UI text size, compact spacing, raised controls over depressed containers, and stained material reserved for selection or primary emphasis. `GlassSurface`, `PaletteEngine`, `ThemeResolver`, `RenderUtil`, and the font/icon renderers form a meaningful shared foundation.

The largest sources of variation are hand-built screen interactions. Several controls that look equivalent are driven by different event paths: `ButtonWidget` inherits vanilla keyboard, narration, and click sound behavior, while direct `Button` users and manually hit-tested cards/tabs generally do not. Hover timing also has two distinct implementations, and the widely reused `HoverAnim` intentionally snaps hover-in from rest while easing hover-out, despite multiple call-site comments describing a symmetric fade.

The glass system is blur, tint, face lighting, and rim lighting. It does not implement distortion or refraction. Source inspection of the compositor shader found no position-dependent UV displacement; runtime comparisons on panorama, menu, and live-world backgrounds were consistent with that result. Nested surfaces sample the same underlying main render target rather than recursively sampling previously drawn glass.

The theme engine mechanically derives a broad token palette from one accent, mode, roundness, glass style, and opacity. It does not guarantee readable composited text over arbitrary world or panorama imagery. Its opaque token-pair contrast picker performs well for main text, but default red and mid-gray accents produce `ON_ACCENT` ratios below 4.5:1, and muted/faint roles are deliberately far below that threshold. At the current runtime setting of 10% window opacity, both text robustness and surface separation depend heavily on the background.

The highest-leverage future decisions are therefore behavioral rather than ornamental: choose one interaction contract for button-like controls; decide whether hover-in should animate; define disabled and focus states for every interactive primitive; decide whether text contrast is merely token-relative or must survive compositing; and resolve whether fixed local radii and unbounded manual hit testing are acceptable exceptions.

## Current architecture map

The current dependency flow is:

```text
AuroraConfig / ThemeDefinition
        |
        v
PaletteEngine -> ThemeResolver -> ResolvedTheme -> ThemeManager
                                      |
                                      +-> ThemeToken lookups
                                      +-> AuroraTheme legacy projection
                                                   |
FeatureRegistry -> FeatureSetting rows ------------+-> screens
                                                        |
                         components --------------------+
                         Button, ToggleSwitch, Slider,
                         SegmentedControl, ColorSwatch
                                                        |
RenderUtil / UiLayerCache / AuroraFontRenderer / MaterialIconRenderer
                                                        |
GlassSurface -> BlurPanelRenderer -> Minecraft render target / GL textures
```

Primary screen families:

- `AuroraScreen`: main modules/settings shell, grid/list layouts, tabs, search, and inline Theme settings.
- `FeatureDetailScreen`: reusable feature-setting list, reset/done controls, top scroll fade, and enum overlays.
- `ManagerListScreen`: the only substantial screen base class; `ProfileManagerScreen` and `WaypointManagerScreen` supply row content and actions.
- `ResourcePackBrowserScreen`: independent sidebar, grid, detail modal, card interaction, scrolling, and hover animation.
- `ColorPickerScreen`, `HudEditorScreen`, and `WorldMapScreen`: purpose-built editors with data-heavy surfaces and selective glass chrome.
- `AuroraTitleScreen` plus selection-screen mixins: custom title flow and themed vanilla screen controls.
- `BlurTestScreen`: developer-only material inspection surface.

Shared UI layers:

- Theme: `theme/*`, with roughly forty semantic `ThemeToken` values.
- Components: `ui/component/*`; `ButtonWidget` adapts Aurora's painter to vanilla widget routing.
- Material: `ui/render/blur/GlassSurface` is the semantic API; `BlurPanelRenderer` owns capture, blur, compositing, masks, tint, and rim finishing.
- Drawing/caching: `RenderUtil`, `UiLayerCache`, `CanvasTexture`, and Minecraft `DynamicTexture` objects.
- Type/icons: `AuroraFontRenderer` and `MaterialIconRenderer` are separate pipelines.
- Interaction/motion helpers: `AuroraAnim`, `HoverAnim`, `Animation`, `SmoothScroll`, and `ScrollFade`.
- Legacy bridge: approximately thirty files still read `AuroraTheme` statics. `ResolvedTheme.project()` is the sanctioned writer.

There is no universal screen layout or component-state framework. The shared manager base is an exception; most screens combine primitives directly and perform some hit testing themselves.

## Color system

`ThemeToken` provides these main families:

- Accent: base, hover, pressed, subtle, strong, and on-accent text.
- Structure: background, window fill, surface, elevated surface, inset surface, and border variants.
- Text: on-background, on-surface, secondary, muted, and faint.
- Controls: control fill/hover/pressed, tile fills, and selected fills.
- Semantics: success, warning, error, info, and fixed HUD status colors.
- Overlays/HUD: overlay dim, HUD panel/background, and HUD text policies.

`PaletteEngine` converts the configured accent to opaque RGB, then derives the rest through HSL-like lightness/saturation constraints and RGB blends. Accent hover is a 15% blend toward white; accent pressed is a 28% blend toward black. Neutral surfaces preserve only a small amount of accent saturation. Dark and light modes use separate lightness bands. `ON_BACKGROUND` and `ON_SURFACE` select the better of near-white and near-black against an opaque paired token. Secondary, muted, and faint reuse that RGB with alpha `0x99`, `0x4D`, and `0x2E`.

Color categories found outside theme-token lookups:

| Category | Examples | Assessment |
|---|---|---|
| Data/content colors | map terrain/biome colors, item/effect sprites, hue/saturation picker, checkerboard transparency, pixel canvas | Intentional; recoloring would corrupt represented data. |
| Fixed semantic colors | success/warning/error/info and HUD status palette | Intentional semantic exception. |
| Deliberate structural constants | map overlays, selection/data boundaries, shadow black, transparent masks | Usually implementation-level rather than user-facing theme choices. |
| Local visual constants | several `AuroraScreen` radii and immediate hover washes, ASCII status/remove glyph colors | Potential bypasses; they do not all track roundness or a component primitive. |
| Legacy reads | `AuroraTheme` static fields | Historical bridge; values are still projected from the resolved theme. |

No general contrast guarantee exists for text over blurred world imagery, panoramas, maps, or translucent nested surfaces. The contrast picker sees opaque token colors, not the eventual composited framebuffer. This distinction matters most in Wireframe mode and at low opacity.

## Theme/accent derivation

The user-facing theme model contains:

- One accent color.
- Dark or Light mode.
- Round, Slightly Round, or Square geometry.
- Frosted or Transparent glass style; Transparent is labeled "Wireframe" in the UI.
- Background opacity.

An accent with zero alpha falls back to the preset/default accent; otherwise derivation treats it as opaque. The resolver's baseline dark surfaces are near black (`#000000`, `#1A1A1A`, `#262626`, `#121212`, and `#0D0D0D`), while light surfaces occupy approximately 90-96.5% lightness. Roundness resolves standard/large/small radii to `10/14/6`, `6/8/3`, or `0/0/0` GUI pixels.

Opacity is not a universal alpha multiplier. It primarily sets `WINDOW_FILL` alpha. A stained material uses accent RGB with alpha at least 140, so selected/primary controls can remain much more opaque than a 10% window. Rim finish alpha scales approximately with opacity squared. Frost blur radius is requested radius multiplied by `max(1/6, sqrt(opacity))`; at 10% opacity, the default 24-device-pixel request becomes about 7.59 device pixels.

Extreme valid accents are not rejected or corrected. The engine chooses on-accent near-white/near-black, but a binary choice alone cannot make every mid-luminance accent satisfy a normal-text target. Very dark, very light, yellow, and cyan samples were robust in the measured opaque pairing; default red and mid-gray were not. Accent-derived borders and subtle fills can also become hard to distinguish over dynamic backdrops.

HUD color is partly separate by design. Status meanings use fixed hues; general HUD text and selected chrome may use a zero-value sentinel meaning "follow theme accent." This is a domain-specific policy, not a direct reuse of every screen token.

## Typography

Aurora intentionally has one practical UI type size. The UI renders at Minecraft's `Font.lineHeight`—normally 9 GUI pixels—and differentiates roles through color, position, casing, and surrounding spacing rather than a type scale.

Observed roles:

| Role | Current treatment |
|---|---|
| Screen title | Normal size, high-emphasis text, fixed top placement. |
| Attached credit subtitle | Normal size, secondary/muted color, immediately below the title. |
| Section header | Normal size, generally uppercase, with a larger gap above. |
| Section footer | Normal size, muted/secondary, wrapped at `lineHeight + 1`. |
| Setting label | Normal size; wraps when the row supports dynamic height. |
| Value line | Normal size, secondary color beneath the primary label. |
| Button/control label | Normal size, centered or left-aligned by component. |
| Tooltip | Normal size, wrapped to a maximum width around 220 GUI pixels. |

The only clear pose-scale exception in the inspected screen UI is the HUD editor instruction text at 0.75 scale. Material icon scaling is not typography; it is a separate exact-device-pixel raster path.

`AuroraFontRenderer` supports OFF, AURORA_ONLY, and ALL_TEXT scopes. Bundled choices include Inter, Roboto, and other TTFs; JSON providers use nominal size 11 with oversampling 8. Custom UI drawing deliberately avoids Minecraft's usual shadow. Italic styles can select an italic face. The current runtime was OFF/ROBOTO, so vanilla font rendering—not the selected Roboto client font—was active during captures.

Overflow behavior is local but mostly sensible: compact buttons, keybinds, and cards ellipsize; descriptions, Boolean labels, and footers wrap; dropdowns size around their content; tooltips wrap. A specific behavioral exception is `KeybindSetting`, which intentionally does not show the normal label-hover description tooltip.

## Geometry and spacing

The dominant rhythm is compact: 18-22-pixel controls, 4-10-pixel gaps, 28-40-pixel setting rows, and 1-pixel flat borders. Material rims are visually around 2 GUI pixels. Important measured geometry includes:

| Surface | Key dimensions |
|---|---|
| Main settings window | 360 x 240; 90-pixel sidebar; 254 x 180 content viewport. |
| Module grid/list | 80 x 80 tiles at 4 gap, three columns; list cards 250 x 48 at 6 gap. |
| Main tabs/search | 64 x 22 tabs at 28 pitch; 202 x 20 search. |
| Feature detail | 320-wide list; 60 top padding; 6 row gap; 10 list inset; 64 x 22 Done and 70 x 22 Reset. |
| Manager rows | Profiles 420 x 30 at 4 gap; waypoints 460 x 28 at 4 gap. |
| Resource pack browser | 130 sidebar; 44 top bar; 220 x 96 cards at 10 gap; detail modal capped near 460 x 360. |
| Title actions | 220 x 30 at 6 gap. |
| Common setting controls | Enum 100 x 18; keybind/button 90 x 18; slider row 36 with 4-pixel track and 14-pixel knob. |
| Color picker | Dynamic 60-240 padding; 18-pixel hue/alpha bars; 28 preview; 18 hex field; 100 x 20 buttons. |

Boolean rows have a 28-pixel minimum and grow to fit wrapped labels/value lines. Their rendered toggle is 28 x 15 in settings, despite older prose referring to a 36 x 20 switch. Slider tracks reserve about 12 pixels at each end. Text fields use approximately 8 pixels left and 4 pixels right padding.

Values come from a mix of sources: resolved theme radii; constants in shared components/settings; and screen-local layout constants. Some responsive values are deliberate, including Boolean row height, Color Picker padding, resource-pack columns/modal bounds, and device-scaled material blur. Several `AuroraScreen` tab/tile/layout radii remain fixed local values, so Square roundness does not make every visible shape square.

## Glass/material system

`GlassSurface` exposes semantic constructors rather than arbitrary shader parameters:

- `container`: depressed window material.
- surface-token container: depressed secondary/elevated material.
- `control`: raised neutral material.
- `stainedControl`: raised accent material for primary/selected state.
- above-dim variants: equivalent roles composited after a modal dim layer.

The rendering sequence on converted screens is structurally consistent: begin glass pass, submit material bodies, draw overlay dim where applicable, draw content, then finish deferred rims. Enum popups and the resource-pack detail modal are explicit above-dim exceptions.

Frosted material processing is:

1. Quantize the GUI rectangle to device pixels and pad for the effective blur radius.
2. Capture from Minecraft's main render target.
3. Downsample through two linear-filtered steps to quarter resolution.
4. Apply a separable nine-tap Gaussian blur using offsets -4 through +4.
5. Composite the screen-aligned sample through a rounded signed-distance mask.
6. Apply tint, a directional face gradient, and a cached rim finish.

The default requested radius is 24 device pixels. Effective radius is opacity-adjusted, has a 1.5-pixel practical minimum, and is reduced under chain/pool pressure. The renderer maintains an output pool of 64 and a rim-mask cache of 16. Panel priority is Window, Control, Row, then Detail. Screenshot suppression lasts about 400 ms. Failures, invalid backdrops, pool pressure, tiny/offscreen rectangles, Wireframe mode, and active screenshot suppression can all select a flat fallback.

Lighting uses a fixed direction close to `(0.243, -0.970)`. Raised and depressed roles invert the perceived edge relationship, while both use approximately 0.45 edge intensity and 0.10 face-gradient strength. The face gradient is normalized across each panel, and the rim is generated per panel. Adjacent surfaces can therefore show seams even though their blurred background samples remain screen-aligned.

Representative semantic-role usage:

| Context | Container | Controls/rows | Special case |
|---|---|---|---|
| Main settings | Depressed 360 x 240 window | Raised neutral/stained tiles, tabs, search, layout controls | Manual tile radii and hit testing. |
| Feature detail | Depressed setting list | Raised setting controls/top buttons | Enum popup is raised above dim. |
| Managers | Main list container | Profile rows depressed; waypoint rows raised | Deliberate domain distinction. |
| Pack browser | Depressed sidebar/content | Raised cards and controls | Active category has custom tint; modal is depressed above dim. |
| Title | No enclosing window | 220 x 30 raised neutral/stained buttons | Declares panorama as eager backdrop. |
| Color picker | Selective button/input glass | Color fields remain data-rendered | Edit-box mixin makes all themed text fields glass. |
| World map | Map is data surface | Raised chrome controls | Glass samples the underlying render target, not deferred map tiles. |

Wireframe means the blur renderer declines material rendering and consumers draw their flat/token fallbacks. It is not a clear-glass refractive mode.

## Distortion/refraction

Aurora currently implements neither distortion nor refraction.

This conclusion is source-level, not merely visual:

- `BlurPanelRenderer` explicitly lists refraction, distortion, normals, specular response, and dome geometry as out of scope.
- The compositor samples `texture(uInput, mix(uUvRect.xy, uUvRect.zw, vUv))` without time, noise, sine, normal, edge-normal, or content-dependent coordinate offsets.
- The rounded SDF controls coverage only. Face lighting and rim lighting alter color/alpha rather than sample position.
- `GlassSurface` exposes blur/tint/lighting roles but no displacement parameter.

Nested or overlapping panels do not compound deformation. They capture the same underlying main render target rather than previously submitted deferred GUI glass/content. A panel may be visually segmented by its own face gradient and rim, but background geometry remains aligned.

Deterministic runtime comparisons used the title panorama, selection screens, the main settings shell opened from title, and the same shell over a live world. Frosted and Wireframe captures showed blur/tint/rim changes but no displacement of panorama lines, textural edges, terrain contours, or neighboring UI geometry. This matches the shader audit.

## Component state matrix

`—` means no distinct treatment was found in the component itself.

| Component | Default | Hover | Press/drag | Selected/active | Focus | Disabled | Input/a11y path |
|---|---|---|---|---|---|---|---|
| `Button` painter | Neutral/accent/destructive surface | `HoverAnim` wash | 0.96 scale, then overshoot recovery | Primary uses accent/stained | — | Muted/inset | Painter alone has none. |
| `ButtonWidget` | Uses `Button` | Same | Same | Same | No dedicated visual | Vanilla active flag plus painter | Vanilla mouse/keyboard/narration/sound. |
| Selection-screen button mixin | Themed vanilla button | Hover or focus treated alike | Same 0.96/recovery timings | By vanilla type | Hover-equivalent | Themed disabled state | Vanilla routing/narration/sound. |
| `ToggleSwitch` | Track + circular thumb | — | — | Thumb slides/crossfades | — | No distinct render branch | Manual click; disabled blocks input. |
| `Slider` | 4-pixel track, 14-pixel knob | Knob halo | Immediate jump/drag | Value fill/position | Only setting-local keyboard focus | Distinct muted treatment | Manual mouse; arrow keys when focused. |
| `SegmentedControl` | Neutral segments | Immediate wash/text change | — | Accent/stained segment | — | Muted text; selected surface remains | Manual click. |
| `ColorSwatch` | Color chip | Halo | — | Selection outline | — | Alpha-reduced, no hover | Manual click. |
| `EnumSetting` | 100 x 18 field | Text/wash | — | Expanded state; selected option accent | — | Setting-level gating | Manual popup; no general keyboard route. |
| `KeybindSetting` | 90 x 18 field | Wash | — | Listening is stained | — | Does not consult disabled state in its own render/click path | Manual key capture; no description tooltip. |
| Module tile/card | Neutral raised card | Immediate wash | — | Stained when enabled/selected | — | Feature-specific | Manual left/right click; no narration. |
| Pack card/tab | Raised/custom surface | Symmetric 150 ms animation | — | Active tint/tab state | — | Context-specific | Manual click; direct buttons mostly silent. |
| Themed text field | Glass field | — | Caret/editing | Focus accent hairline | Accent line + caret | Vanilla field behavior | Vanilla EditBox routing. |

## Hover behavior

There are three main hover models:

1. `HoverAnim`: used by shared buttons, sliders, swatches, enum/keybind fields, and tooltips. Its constructor/backdating behavior intentionally makes the first hover from rest immediately reach 1.0; hover-out eases, and mid-flight reversal is continuous. This is asymmetric despite many 140 ms or 200 ms call-site labels.
2. Immediate state: module cards, main tabs/layout buttons, segmented choices, and some popup rows apply a hover wash/text color with no transition.
3. Resource-pack local animation: cards and tabs use a genuine approximately 150 ms ease-out transition in both directions.

Tooltip display adds a 1500 ms dwell. Once the threshold is crossed, its `HoverAnim` is already at full value from rest, so appearance is effectively a delayed snap followed by a 200 ms fade-out—not a 200 ms fade-in/out.

Hover generally affects fill, text, halo, or surface tint. It does not change glass sampling coordinates. No UI hover sound was found.

## Press/click behavior

Shared buttons activate on mouse-down. The painter animates from scale 1.0 to 0.96 over 90 ms with cubic easing, then back to 1.0 over 180 ms with an overshoot curve. While scaled, the button is ineligible for the glass path and renders a flat fallback so the material capture rectangle does not fight the pose transform.

The visual press contract is not universal:

- `ButtonWidget` and themed vanilla selection buttons use the 90/180 ms scale response.
- Direct `Button` users receive the painter response but not vanilla's event services.
- Toggles, segmented controls, enum options, swatches, cards, and tabs generally have no separate depressed frame.
- Sliders jump to the clicked value and begin dragging; hover halo is their only extra visual state.
- Pack modal interaction is gated until its open progress exceeds roughly 0.5.

Keyboard activation follows vanilla behavior only when the control is a vanilla widget. Manual controls do not share a consistent focus traversal or activation contract.

## Motion/animation

Shared curve vocabulary:

- `AuroraAnim`: ease-out cubic, ease-in-out cubic, spring overshoot, scalar/ARGB interpolation, and alpha scaling.
- `HoverAnim`: duration-based reversible progress, with snap-in-from-rest semantics.
- `Animation`: exponential approach using real delta time capped at 50 ms, speed factor 12 in toggles, and a 0.01 snap epsilon.
- `SmoothScroll`: exponential convergence using nanosecond time, first-frame 16 ms, delta capped at 80 ms.

Observed durations/parameters:

| Motion | Current behavior |
|---|---|
| Button press | 90 ms down, 180 ms overshoot release. |
| Shared hover call sites | Usually labeled 140 ms; actual resting hover-in snaps, exit eases. |
| Pack card/tab hover | Genuine 150 ms ease-out in both directions. |
| Tooltip | 1500 ms dwell, then snap-in / 200 ms fade-out. |
| Toggle | Exponential slide/crossfade, speed 12; no documented spring/halo in implementation. |
| Scroll | Feature detail/managers tau 60 ms; pack tau 80 ms; Aurora adaptive tau about 24-65 ms by GUI FPS. |
| Pack detail modal | 8 ticks, roughly 400 ms at 20 TPS; ease-out, 16-pixel vertical travel, dim to alpha 180. |
| Toast | Static hold; final 250 ms fade. Managers hold 1500 ms, pack browser 3000 ms. |
| Text caret | 530 ms on/off blink. |
| Theme live update | Accent/background changes throttled around 100 ms, committed on release. |

Wheel steps differ by context: approximately 30 in feature detail/managers, 40 for pack grid, and 28 for pack sidebar. These can be contextual choices because content density differs. No general screen transition system exists beyond the pack modal.

## UI sound

Aurora has no general UI sound layer, no UI-specific OGG assets or `sounds.json`, and no independent UI-sound preference.

Sound depends on routing:

- `ButtonWidget` and vanilla selection buttons inherit Minecraft's `UI_BUTTON_CLICK` behavior on mouse or keyboard activation. Inspection of the mapped 1.21.11 `AbstractWidget`/`AbstractButton` bytecode confirmed the standard UI click instance at pitch 1.0.
- Direct `Button` painters do not play a sound themselves. As a result, button-looking controls in setting rows, manager rows, and resource-pack card/modal paths can be silent.
- Toggles, sliders, enums, swatches, segmented controls, cards, and main tabs are manually handled and no general click sound was found.
- No hover sound exists. Repeated accepted `ButtonWidget` clicks can each trigger the vanilla sound.

The one UI-like click found in project code belongs to Better Hitreg practice behavior, not the general interface. Alert sounds are feature feedback and are likewise outside the UI control system.

A future unified sound policy would require both a semantic sound API and adoption by manual controls; it cannot be achieved only by adding an asset.

## Iconography

Feature icons are Material Symbols codepoints mapped centrally in `FeatureIcons`. The primary path rasterizes `material_symbols_rounded.ttf` with FreeType at the exact device-pixel em size and blits 1:1, avoiding Minecraft's nearest-sampled glyph-atlas degradation under pose scaling. Rasters are cached by codepoint/em/GUI scale; the implementation caps individual rasters at 128 em / 512 device pixels. A Minecraft font-glyph fallback exists.

Typical icon sizes are 28 em for grid tiles, 16 for list cards, and about 11 for dropdown chevrons. Minecraft's item/effect atlas is correctly used for represented game content. The saturation HUD's `textures/icons.png` and hotbar slot texture are functional content assets, not competing feature-icon systems.

Inconsistencies are mostly at the small-action level: remove actions use raw ASCII `x`, add actions use `+`, and status may use `✓`, while neighboring navigation/actions use Material Symbols. There is no reusable icon-button primitive that standardizes hit target, hover, focus, tooltip, disabled state, and narration for these cases.

## Scrolling/clipping

`SmoothScroll` is shared by all major scrolling screens, but each screen selects its own target step, convergence time, and thumb behavior. `ScrollFade` is a 16-GUI-pixel top gradient whose maximum alpha comes from the relevant surface token. It visually obscures content; scissoring provides actual clipping.

Confirmed adopters:

- Main settings: both modules and settings tabs.
- Feature detail.
- Profile and waypoint managers.
- Resource-pack grid and sidebar.

Feature detail, managers, and pack browser gate manual hit tests to their clipped viewport. Feature detail also adjusts the scissor boundary around its cached row layer so content does not leak into the fade band. The glass pass uses tracked scissor wrappers so deferred rim finishing respects clipping; ordinary content may use raw scissor calls.

`AuroraScreen` draws/scissors its content, but its manual tile and setting click loops do not apply the same explicit viewport bounds check found in the other scrolling screens. This can leave an off-viewport logical row clickable if its transformed bounds overlap another interactive region. That is a documentation/implementation disagreement with the stated scroll-boundary discipline and should be verified with a focused input test before changing it.

World-map pan/zoom is not list scrolling and has no top fade. Color Picker and Title do not scroll.

## Screen-level bypasses

| Screen/path | Shared system bypass | Classification |
|---|---|---|
| `AuroraScreen` cards/tabs/layout controls | Manual hit testing, immediate hover, no vanilla narration/sound; some fixed radii | Historical/custom composition plus unexplained behavior differences. |
| `ResourcePackBrowserScreen` | Local symmetric hover animation and direct-button routing | Contextual implementation with historical duplication. |
| `ColorPickerScreen` | Data surfaces are flat/literal; buttons/field selectively glass | Data exception is intentional. Text-field behavior conflicts with prose. |
| `WorldMapScreen` | Literal map/data rendering; glass chrome samples underlying render target | Intentional data/chrome split; sampling result is a pipeline constraint. |
| `HudEditorScreen` | HUD content stays in its own renderer; only editor chrome uses glass | Intentional domain split. |
| Selection screens | Local `AbstractButtonMixin` instead of shared component instance | Historical integration with vanilla widget lifecycle. |
| Setting subclasses | Local geometry and event logic around shared components | Expected architecture, but it permits state drift such as keybind disabled behavior. |

One concrete documentation conflict is the text-field material policy. Color Picker prose says its hex field remains opaque, and design notes describe search fields as the canonical glass text-field use. `EditBoxMixin`, however, themes every edit box whose screen implements `ThemedScreen`, including hex, numeric, and rename fields. Current code therefore applies glass more broadly than the written exception model.

## Runtime capture evidence

Two automated dev-client passes were executed at 854 x 480 framebuffer resolution, GUI scale 2, approximately 57-60 FPS. The user's persisted configuration was restored after the temporary harness wiring.

Runtime configuration recorded for the captures:

- Mode: Dark.
- Roundness: Slightly Round.
- Glass style before/after tests: Transparent/Wireframe.
- Background opacity: 0.10.
- Accent: `0xFFE9771B`.
- Text renderer: OFF; configured client font ROBOTO was therefore inactive.

Capture sets:

- `run/.devpilot-design-system-audit/`: main Modules and Settings views; No Fog detail; Better Hitreg empty, populated, alert-row, and bottom states; Theme; additional screen grabs.
- `run/.devpilot-design-system-audit/oow-current/`: Frosted and Wireframe title/menu/world comparisons, selection screens, profile flow, and screenshot-suppression/fallback evidence.

Observed runtime results:

- FreeType Material Symbols initialized and icons rendered on the native raster path.
- Better Hitreg populated 69 rows; its live value-line update measured about 44 ms in the harness, reset replaced the tracked queue identity, and row ordering matched source structure.
- Frosted at 10% opacity visibly suppressed high-frequency panorama/world detail while preserving alignment; Wireframe exposed busy background detail through low-alpha surfaces.
- The declared-panorama main screen rendered Frosted material from title. Screenshot suppression and Wireframe both exercised flat fallback outcomes. Live-world Frosted and Wireframe captures were also obtained.
- No apparent background displacement was observed across deterministic comparisons.
- The host lacked optional `libflite.so`, so Minecraft logged narrator initialization failure. This did not block UI rendering, but it prevented runtime narrator verification.

Temporary DevPilot installation calls were reverted. `AuroraClient.java`, `run/devpilot.properties`, and the pre-existing untracked `DevPilot.java` were restored byte-for-byte; captures remain only in the established ignored `run/` location.

## Contrast/robustness observations

Contrast probes used the exact `PaletteEngine` output and standard relative-luminance math. Alpha text was composited over its paired opaque background before measuring. These values are diagnostic, not a claim that WCAG directly governs every game-overlay context.

| Mode | Role | Measured range across default, black, white, yellow, cyan, magenta, gray accents |
|---|---|---|
| Dark | `ON_BACKGROUND` | 17.55-18.08:1 |
| Dark | Secondary | 6.77-6.84:1 |
| Dark | Muted | 2.56-2.61:1 |
| Dark | Faint | 1.63-1.68:1 |
| Light | `ON_BACKGROUND` | 17.03-17.21:1 |
| Light | Secondary | 4.67-4.75:1 |
| Light | Muted | 1.95-1.98:1 |
| Light | Faint | about 1.47:1 |

`ON_ACCENT` is mode-independent for the tested accents: default red 4.30:1, black 19.64:1, white 18.59:1, yellow 16.89:1, cyan 14.54:1, magenta 5.91:1, and mid-gray 4.19:1. Default and gray are below the common 4.5:1 normal-text threshold even before dynamic-background compositing.

Robustness risks with measurable causes:

- Low window opacity exposes arbitrary luminance and detail; the token-pair contrast calculation cannot account for it.
- Muted/faint text is intentionally low contrast even on opaque paired surfaces.
- Stained controls enforce a relatively high minimum alpha, producing a different opacity hierarchy from their enclosing window.
- Fixed local radii make Square mode geometrically incomplete.
- Disabled toggles remain visually indistinguishable from enabled interactive toggles; keybind rows would remain interactive if a disabled supplier were attached.
- Manual controls lack a uniform keyboard-focus and narration path.
- UI cache and material fallbacks preserve usability, but a scaled pressed button changes from glass to flat for the duration of its animation.

## Intentional differences

Category A—intentional semantic differences:

- Depressed containers and raised controls communicate depth roles; stained material is reserved for primary/selected state.
- Profile rows are depressed while waypoint rows are raised. This is explicitly documented rather than an accidental mismatch.
- Map, item, effect, hue, alpha, and canvas colors represent data and remain outside general theme recoloring.
- Fixed semantic status hues and the HUD's follow-accent sentinel serve domain meanings.
- Toggle thumbs and slider knobs remain circular in Square mode.
- Enum popups and the pack detail modal render above dim.
- Different scroll steps/tau values can reflect content density and control context.

Category B—responsive/contextual differences:

- Boolean rows wrap and increase height.
- Color Picker padding, resource-pack columns, and modal size adapt to available dimensions.
- Blur operates in device pixels and scales with opacity.
- Pack modal input waits for sufficient opening progress.
- World map/HUD editor expose data via their own rendering pipelines while using themed chrome.

## Historical/legacy differences

Category C—historical or integration-driven differences:

- `AuroraTheme` remains a widely read compatibility facade even though the new theme engine is authoritative.
- Vanilla selection screens use a local mixin painter because they must remain in Minecraft's widget lifecycle.
- `ResourcePackBrowserScreen` owns a separate hover animator instead of `HoverAnim`.
- Main-screen cards/tabs predate a universal accessible card/button primitive and remain manually routed.
- Manager-specific editor/toast composition sits in a shared base but is still ordered through subclass-tail hooks.
- Comments/Javadocs around Toggle animation, Color Picker field opacity, and several hover durations describe earlier or intended behavior rather than the current implementation.

## Unexplained divergences

Category D—differences without a clear current semantic justification:

- Shared `HoverAnim` snaps hover-in from rest, while comments and durations imply a visible fade. Tooltips inherit the same discrepancy after their dwell.
- `ToggleSwitch` has no hover, press, or focus visual and no visually distinct disabled branch.
- `ButtonWidget` has no dedicated focus visual, while themed selection-screen buttons treat focus as hover.
- Visually equivalent direct buttons and `ButtonWidget` controls differ in sound, keyboard activation, and narration.
- Manual cards/tabs lack the interaction services of vanilla widgets.
- Main-screen scrolling clips rendering but does not explicitly clip all corresponding manual hit tests.
- Several main-screen local radii ignore resolved Square roundness.
- `KeybindSetting` does not consult its inherited disabled state during render/click.
- Default red and mid-gray accents can yield `ON_ACCENT` below 4.5:1 with no warning or adjustment.
- The text-field mixin applies glass to all themed-screen fields despite documentation describing narrower exceptions.

## Candidate Design Language v3 invariants

These are questions for a future design decision, not adopted rules.

### Candidate invariant: one semantic interaction contract for button-like controls

Evidence: direct `Button`, `ButtonWidget`, selection-screen buttons, cards, and tabs can look equivalent while differing in click sound, focus, keyboard activation, and narration.<br>
Current exceptions: manual cards/tabs, setting-row direct buttons, manager row actions, and pack actions.<br>
Decision required: whether all button-like elements must use a common accessible router, or whether silent/pointer-only classes are an accepted semantic category.

### Candidate invariant: hover timing states must describe observed motion

Evidence: `HoverAnim(140)` snaps in from rest and eases out; pack hover genuinely animates both directions.<br>
Current exceptions: immediate cards/segments and pack-local animation.<br>
Decision required: choose asymmetric snap/ease, symmetric animation, or explicitly named per-context behaviors.

### Candidate invariant: every interactive primitive defines default, hover, pressed, focus, and disabled behavior

Evidence: buttons cover most visual states, while toggles, segments, swatches, enum options, cards, and keybind fields omit different subsets.<br>
Current exceptions: pointer-only controls and components whose setting is never currently disabled.<br>
Decision required: which states are mandatory, and whether focus-equivalent-to-hover is acceptable.

### Candidate invariant: rendered clipping and input clipping share the same bounds

Evidence: feature detail, managers, and pack browser gate both; the main settings screen does not explicitly gate all manual row/card clicks.<br>
Current exceptions: `AuroraScreen` manual loops.<br>
Decision required: whether a shared viewport/input helper should become mandatory.

### Candidate invariant: roundness tokens govern all chrome geometry

Evidence: shared glass/component paths resolve theme radii, but several main-screen radii are literal.<br>
Current exceptions: circular toggles/knobs are deliberate; data shapes may need fixed geometry.<br>
Decision required: identify which shapes are semantic exceptions and whether Square means fully square chrome.

### Candidate invariant: text contrast is evaluated after final compositing

Evidence: the current picker only compares opaque token pairs, while glass can expose a panorama or world; two valid accents fail 4.5:1 even in the simple pairing.<br>
Current exceptions: muted/faint roles and artistic game-overlay contexts.<br>
Decision required: whether Aurora promises a numeric threshold, a minimum material backing, adaptive text scrim, warnings, or no formal guarantee.

### Candidate invariant: text-field material role is explicit by field semantics

Evidence: a global `ThemedScreen` mixin makes search, hex, numeric, and rename fields glass, conflicting with written exceptions.<br>
Current exceptions: all non-search themed edit boxes.<br>
Decision required: whether glass is canonical for every edit box or only for search/navigation chrome.

### Candidate invariant: small actions use one icon-button vocabulary

Evidence: Material Symbols are centralized for features/navigation, while add/remove/status actions mix raw `+`, `x`, and `✓`.<br>
Current exceptions: content sprites and textual status indicators.<br>
Decision required: whether small glyph actions need standardized icon, hit target, tooltip, state, and narration rules.

## Highest-leverage areas for future standardization

1. Interaction routing: a shared accessible button/card/action layer would simultaneously address focus, keyboard, narration, click sound, disabled state, and press animation.
2. Composited contrast policy: decide what Aurora guarantees at minimum opacity over world/panorama content before adjusting individual colors.
3. State completeness: define state behavior once for Toggle, Segment, Enum, Keybind, Swatch, card, and icon-action primitives.
4. Hover semantics: reconcile `HoverAnim`, immediate states, and the pack-local animator with names and timings that match behavior.
5. Viewport coupling: share render scissor, fade boundary, scrollbar geometry, and input bounds.
6. Geometry ownership: separate theme-governed chrome radii from explicitly fixed data/circular shapes.
7. Documentation/source synchronization: correct stale behavioral comments only when a maintainer decides which behavior is authoritative.

## Questions requiring maintainer judgment

- Is snap-in/ease-out hover an intentional Aurora signature, or should the existing duration values produce visible hover-in motion?
- Should every button-looking control use vanilla-compatible focus, keyboard, narration, and click sound, or are silent pointer-only actions intentional?
- Does Square mean all chrome becomes square except circular mechanical parts, or may screen-local rounding remain?
- Is 10% opacity expected to remain usable over arbitrary live-world imagery without an adaptive scrim?
- Should the accent be constrained/adjusted for on-accent text, or should primary controls avoid text at failing colors?
- Are muted and faint text informationally expendable, or must they meet a readability target?
- Should all `ThemedScreen` edit boxes be glass, or only search/navigation fields?
- Are profile-row depressed and waypoint-row raised semantics meant to become documented reusable row roles?
- Should resource-pack hover remain deliberately smoother than the rest of Aurora?
- Is UI sound intended to follow Minecraft's master/UI behavior wherever available, and should manual controls opt into it?

## Investigation limitations

- Runtime testing covered representative rather than exhaustive screen/state combinations. It did not exercise every resolution, GUI scale, theme preset, language, or extreme accent in-client.
- Contrast probes are exact for sampled token pairs but cannot enumerate arbitrary dynamic framebuffer content.
- The narrator could not initialize on this host because optional `libflite.so` was missing; accessibility conclusions therefore combine source inspection with vanilla bytecode behavior rather than spoken-output capture.
- No automated keyboard-only or gamepad traversal suite exists. Manual-control focus conclusions are source-based.
- Screenshot suppression and material fallback were observed, but output-pool exhaustion and forced shader failure were not induced.
- Runtime evidence remains in ignored `run/` storage and is not part of the committed audit.
- Historical intent was inferred only where comments, architecture documentation, or clearly separate integration paths support it. Unclear cases remain categorized as unexplained rather than assigned intent.

---

## Closure addendum — Phase B disposition (2026-09-18)

This addendum is appended, not merged: everything above remains the untouched
2026-09-14 baseline snapshot. It records how this audit's Phase-B-relevant
observations were dispositioned by the Phase A/B work that landed through
`18f885e` ("Phase B — Motion and State Primitives — COMPLETE"; see
`ARCHITECTURE.md`'s Phase B history and closure record for the per-family
pilots and their evidence). A future reader should treat statements above
like "HoverAnim snaps hover-in from rest" or "Pack card/tab hover is the
browser's own animator" as historical, not current.

| Original audit observation (2026-09-14) | Disposition |
|---|---|
| `HoverAnim` snaps in from rest while comments claim symmetric fade | RESOLVED — `HoverAnim.symmetric(140)` is the canonical vocabulary; every custom interactive family uses it; the legacy constructor survives only as Slider's ThemePreview mock, ColorSwatch's zero-consumer public default, and the internal factory construction |
| Pack browser's local ~150 ms symmetric animator (a special dialect) | RESOLVED — retired; tabs and card bodies run the shared canonical animator (2026-09-18) |
| Selected/expanded pins hover (`hover || expanded`, pack `hover || active`) | RESOLVED — Enum (2026-09-16), pack tabs (2026-09-18), Accent peers (2026-09-18) all decoupled the channels; the aliasing pattern is source-banned in those files |
| Toggles/sliders/swatches/enums omit hover/press/focus/disabled subsets | RESOLVED — state-complete treatments per family (Phase B pilots 1–9), each pixel- and runtime-verified in both themes |
| Manual cards/tabs lack vanilla-equivalent keyboard/narration/sound | SPLIT — pack tabs, accent peers, and all settings-row families RESOLVED (semantic controls, focus, narration, exactly-one sound); AuroraScreen manual chrome and SegmentedControl DEFERRED TO C (host/geometry pass); `AbstractButtonMixin`'s themed vanilla buttons (hand-rolled 140 ms snap-in hover, hover/focus aliasing) DEFERRED TO C (painter migration to `ButtonWidget` is the documented path) |
| Routing-dependent sound split (custom controls silent) | RESOLVED at the ownership level — accepted actions play exactly one semantic click through the shared adapter on every family whose host permits; sound *identity* remains Phase F |
| Disabled treatments inconsistent (toggle indistinguishable, keybind ignores disabled, DONE install paints enabled pixels) | RESOLVED — disabled is authoritative per family; the DONE-phase button paints the canonical disabled look (`6134aae`) |
| Waypoint chips' spurious hover halo | RESOLVED — `ColorSwatch.dataOnly()` (`18f885e`); zero interaction response, literal interiors |
| Immediate-hover module cards/segments/main-screen chrome | DEFERRED TO C (AuroraScreen manual chrome + SegmentedControl migrate with their host/component pass) |
| Fixed local radii ignoring Square mode | DEFERRED TO C (geometry conformance) |
| Composited contrast / `ON_ACCENT` below 4.5:1 / low-opacity text robustness | DEFERRED TO D |
| No distortion/refraction in glass | INTENTIONAL — v3 §7.2 keeps the canonical material blur+tint+lighting+rim; refraction remains Phase G R&D only |
| Narration unverified at runtime (`libflite` missing) | UNCHANGED ENVIRONMENT LIMITATION — metadata-verified only |
| Stale factual counts (e.g. "9 category tabs") | SUPERSEDED — current topology: 23 sidebar rows = 21 selectable tabs + 2 headers; 26 enum rows; 29 color rows; 17 keybind rows; the counts in `ARCHITECTURE.md` and the pinned source tests are current |

---

## Closure addendum II — Phase C disposition (2026-09-21)

Appended the same way as the Phase B addendum: everything above remains the
untouched baseline. This records how the audit's Phase-C-relevant deferred
observations were dispositioned by the Phase C work through the closure
baseline (see `ARCHITECTURE.md`'s Phase C closure record for the full
harness/matrix evidence).

| Observation deferred at the Phase B addendum | Phase C disposition |
|---|---|
| AuroraScreen manual chrome (cards/tabs/layout) lacks keyboard/narration/sound | RESOLVED — C-2b/C-5: semantic controls, canonical hover, narration, exactly-one-click model on every chrome family |
| SegmentedControl immediate hover + selected-suppression | RESOLVED — C-5: pointer-only symmetric 140 ms composing with selection, semantic peers, one persistence path |
| `AbstractButtonMixin` hand-rolled hover, hover/focus aliasing | RESOLVED — C-6: shared painter/timeline, canonical hover, independent focus hairline, vanilla keeps interaction/sound |
| Fixed local radii ignoring Square mode | RESOLVED — C-7 Wave 1 + the closure harness's argument-aware literal inventory (fourteen classified sites, zero unclassified rectangular chrome) |
| `AuroraScreen` render-clip/input-bounds drift | RESOLVED — C-1/C-8 ClipBand coupling across every scrolling host |
| The §15.2 conformance harness ("future verification tool") | RESOLVED — built at closure: the tracked `conformance` test package (family manifest + manifest audits + behavioral matrix) and the untracked DevPilot `phasecclose` runtime matrix (31/31 on dark/light × ROUND/SQUARE) |
| Composited contrast / `ON_ACCENT` / low-opacity robustness | REMAINS PHASE D (unchanged boundary) |
| Glass seams/continuity, scrollbar sub-pixel polish | REMAINS PHASE E |
| Sound identity | REMAINS PHASE F |
| Refraction/distortion | REMAINS PHASE G (R&D-only per v3 §7.2) |
| Narration runtime verification | UNCHANGED ENVIRONMENT LIMITATION |

---

## Closure addendum III — Phase D-0 planning disposition (2026-09-22)

Appended the same way as the prior addenda: the baseline above is untouched.
The Phase D-0 planning audit (see `ARCHITECTURE.md`'s Phase D-0 record for
the full methodology, failure table, and implementation plan) re-derived
this audit's contrast-relevant observations with the real `PaletteEngine`
under an alpha-composited model and confirmed them:

| Observation deferred at the Phase C addendum | D-0 disposition |
|---|---|
| Composited contrast at low opacity | CONFIRMED + QUANTIFIED — primary text over a 0.10 window is 1.21:1 over a near-white world; 4.5:1 needs window opacity ≥ 0.58 (worst fixture); D-4 owns the minimum-effective-backing pilot |
| `ON_ACCENT` below 4.5:1 for default red (4.30) | CONFIRMED + EXTENDED — sat-blue 4.33 also fails; hover/gradient variants fall to 3.46; the binary picker's floor is ≈4.17; stained-translucent backings collapse the pick's reference to 1.95 (sat-red) — D-2/D-3 own the readable-stained-backing adaptation |
| Light-theme disabled readability | CONFIRMED — disabled labels 1.95-1.97 (light), disabled fill barely distinct from enabled (1.04); D-5 owns it |
| A unified disabled material | PLANNED (D-5) — the Enum inset+muted idiom is the designated base |
| Disabled/covered field material | PLANNED (D-5) — no disabled EditBox visual exists today; covered = pack-modal containment, ruled interaction-only (the veil is the signal) |
| Muted/faint below any readability floor | CLASSIFIED — muted 2.59 dark / 1.96 light vs a proposed 2.2 project floor; faint exempt (decorative per §3.3) |
| (New, not in this audit) tooltip text role | D-0 finding — light-mode tooltip text is near-white on a near-white box at every opacity (math ~1.09; runtime-measured 1.00, pixel-identical); fix scoped to D-1/D-6 |

All other Phase D-0 findings (selection separation 1.00, focus hairline
1.00 vs stained, white thumb/knob 1.00 on light accents, the bypass set)
live in the ARCHITECTURE.md failure table with their root-cause groups.

---

## Closure addendum IV — Phase D-1 foundation disposition (2026-09-23)

Appended without rewriting the baseline or D-0 findings. Phase D-1 implemented the
zero-visual-change prerequisite for later contrast pilots: DESIGN_LANGUAGE v3 §3.6 contains
the normative 4.5/3.0/2.2 contract; `PaletteEngine` owns the canonical sRGB, luminance,
contrast, and straight-alpha composition math; and immutable `ContrastDerivations` are computed
once by `ResolvedTheme` on both resolver paths. The new foundation covers readable foreground,
bounded stained backing (`|ΔL| <= 0.08`, insufficiency exposed), focus ring, selection
separation, and minimum readability backing. It does not migrate a painter or alter a token.

The preserved 64-row pre-D-1 token oracle is byte-identical after the implementation, all
representative Phase-C consumer sources are unchanged, the untracked runtime harness passed
7/7, and the full suite passed 390/390. Therefore this audit's contrast observations remain
historical/current failure evidence for D-2 through D-6, while the shared math and resolution
ownership they required are now RESOLVED at the foundation level. Consumer conformance remains
deliberately open; D-2 begins with the `ON_ACCENT` pilot.
