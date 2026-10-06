# Unified Aurora theme pilot — 2026-10-06

**UNIFIED THEME PILOT. User visual acceptance is pending.** This is a controlled
foundation and production pilot, not a completed migration. Frosted and Wireframe
remain persisted compatibility options. Do not roll this out or remove them before
the review gate is accepted.

## Boundary and source

The pilot comprises `AuroraScreen` (Mods and Settings tabs) and the Theme instance of
`FeatureDetailScreen`, including its shared live preview and description tooltip.
`ThemedScreen.unifiedThemePilot()` defaults to false; only those hosts opt in.
Other screens, HUDs, per-module accent overrides, gameplay features and legacy theme
tokens retain their existing paths. The color-picker screen remains a compatibility
screen, with the chosen color feeding the pilot when returning.

Source: the completed Launcher/Client analysis, Client baseline `d8eecd89`, and
Aurora Launcher `4575a0e3` (`src-tauri/src/appearance.rs` and appearance CSS).
Launcher Dark is the visual reference. The Launcher provides no reviewed Light
companion; Light uses the existing Client palette, with its existing neutral hover
wash. This is not a claim that Light has been visually accepted.

## Roles and customization

The additive `UnifiedPalette` resolves alongside `PaletteEngine` once per theme
generation. It does not project over the legacy `AuroraTheme` facade. The pilot's
`MaterialSurface.color` maps semantic reads to the new roles within its host boundary.

| Dark role | RGB |
|---|---|
| Canvas | `#101116` |
| Panel | `#16171d` |
| Raised | `#1d1f27` |
| Hover | `#242631` |
| Pressed | `#1a1c24` |
| Sunken | `#0d0e13` |
| Border / strong border | `#24262f` / `#343743` |
| Primary / secondary / muted text | `#edf1fb` / `#a7b0c5` / `#76819a` |
| Success / warning / error / working | `#7ee2b8` / `#ecc36b` / `#ff9a9a` / `#8fb7ff` |

Dark ordinary surfaces and status colors are independent of accent. The stored base
remains the user's identity color; OnePlus Red (`#eb0029`) remains the factory choice.
Strong action, action hover/pressed, on-action, soft selection, marker and outline
are separate roles. Primary actions are solid and stronger; selected modules and
navigation use a translucent base wash plus a soft accent halo, without left selection
bars or a bright full perimeter. Keyboard focus has a separate geometry-following outline. Its outside radius
stays exactly zero in Square, while circular/mechanical switch and slider geometry
retains the existing exception.

The Java custom-color policy ports Launcher's HSL lightness band `.34–.62` and
white-versus-ink (`#101116`) foreground choice. Each action state is checked against
its foreground at contrast ≥4.5 **after byte rounding**. White-label hover/pressed
use lightness offsets `-.03/-.07`; ink-label states use `+.05/+.02`. If the strong
would equal the stored base, a small `.02` step separates it before deriving states.
The per-state correction also addresses the near-threshold Launcher custom-color
case found in analysis. Client accepts already-stored dark accents rather than
rejecting them; marker lightness is adjusted to contrast ≥3 against the raised
surface. Soft selection keeps the base RGB with alpha `0x24`; outline uses the
readable marker with alpha `0x8c` (approximately 55%).

Client stores RGB rather than Launcher preset IDs. All accents use the same custom
derivation; the Violet RGB does **not** claim the Launcher's curated Violet recipe.
For example, OnePlus action colors are `#e10027 / #d20025 / #bd0021` with white text;
Violet `#8b80ff` resolves to `#4e3dff / #402eff / #2d1aff` with white text. A dark
`#080914` base remains stored while its action is `#32387c` and marker `#5962bd`.

Accent/custom RGB, Dark/Light, ROUND/SLIGHTLY_ROUND/SQUARE, opacity, live preview,
theme enable/reset and profile persistence are retained. There is no new selectable
theme or saved field. Theme-off keeps the existing factory behavior and remembers
the user's source definition. The preview uses the real shared controls, now including
its mock toggle in the live overlay; it remains non-interactive.

## Material ownership and fallback

The parent window alone captures and blurs the valid world or explicitly declared
menu panorama. It uses the shared renderer with zero decorative edge/gradient
lighting; its neutral face, density and thin boundary are painted once by the
existing cached overlay. This makes successful and declined capture use the same
tint placement, instead of dimming only the successful face. The pre-dim capture,
scissor tracking, overlay dim, screenshot interlock, pool priorities, capacity guards,
thread rules and GL restoration remain shared. The 2026-10-06 user review refinement
adopts Launcher's Borealis diffusion: **28 logical pixels of requested blur**, capped
at the existing 64-device-pixel renderer limit, **saturation 135% and brightness 92%**.
The existing opacity/frost curve still controls diffusion strength. The source's
distorted appearance comes from these backdrop filters; it has no lens/UV warp.
Compatibility draws explicitly reset filter uniforms to (1,1).

The pilot's one window fill application point maps the resolved saved alpha to
`round(alpha * .68)`. At 100% saved opacity the maximum tint is 173/255, exposing
the diffuse backdrop instead of hiding it under an opaque face. At 0% it remains 0.
This is a material-density adaptation, not a saved-value/default change or a second
GPU opacity multiplier. The settings description explains the pilot behavior;
comparison screens retain their exact original opacity mapping. Light retains
its existing neutral RGB companion under the same material-density mapping.

Embedded cards, buttons, segments, fields and preview panels never request another
capture or glossy rim. At rest and settled hover, selected/neutral surfaces use a
small size/role/theme-generation template. Primary buttons use the strong action
roles; secondary buttons use neutral rest/hover/pressed roles. Search has an opaque
sunken face and independent focus outline. Template lookup uses a primitive key,
without allocating a key object per control per frame. Templates are released at
the pilot hosts' existing screen-removal boundary, so dimensions do not accumulate
across visits and textures are not retired during the draw walk.

Floating description tooltips use an independently opaque raised face, stronger
neutral edge and a restrained shadow. Their face/edge/shadow rasterize once and fade
by texture alpha, including during the existing 200 ms fade. They do not sample the
window again or inherit its opacity. Button shadows are also cached and retained
through press/hover. Selected peers and enabled cards gain a restrained accent halo;
primary actions gain a slightly stronger halo. The falloff is rasterized once into
the existing size/role/theme cache, then blitted throughout hover/press. It follows
custom accents, has no animation/pulse, and adds no child capture or per-frame
shadow rasterization. Left selection bars were removed from both settled and live
transition paths at the user's request. No per-component framebuffer blur was
introduced for shadows.

Settled button face, edge, elevation and primary halo share one template/blit;
only an active hover/press transition separates the cached elevation from its
live face. Renderer texture binds/deletes use vanilla's `GlStateManager` cache,
including around the managed output upload. The saved state now snapshots unit
zero's texture/sampler independently and restores the original active texture
unit, rather than assuming unit zero was active on entry.

The pilot calls the same renderer through a narrow entry point that bypasses only
the retained legacy style-choice guard. All automatic decline paths remain valid.
Outside the pilot, Frosted and `TRANSPARENT`/Wireframe still have their original
meaning. The active compatibility sites are the persisted `ThemeDefinition` field
and null/default copy repair, `GlassStyle` enum/name parser, `ResolvedTheme` accessor,
`ThemeManager.sync` dirty check, `FeatureRegistry` selector/setter, and the renderer's
single TRANSPARENT decline guard. Profile/config JSON and migration are retained.
The separate `Button.GlassStyle` OFF/NEUTRAL/STAINED presentation branches/templates
remain for comparison hosts; they are not the persisted Frosted/Wireframe enum. The settings description names this boundary. Removing the selector, enum,
serialized preference and legacy treatment is a later migration after acceptance.

## Motion and interaction

`CubicBezier` correctly solves X to evaluate Y, using Newton iteration with a
bounded bisection fallback. The shared vocabulary defines CSS ease `(.25,.1,.25,1)`,
ease-out `(0,0,.58,1)`, and Launcher emphasized `(.2,.8,.2,1)`.

Pilot button, navigation/card, segment and slider hover use **120 ms CSS ease**.
Button press uses the same easing and a maximum **one logical pixel** displacement,
without scaling or spring overshoot. Pointer-backed buttons retain held feedback;
semantic/keyboard activations without a release channel use a bounded press pulse.
Interrupted transitions retarget from the visible value and shorten their remaining
leg. This finite retarget policy is a Client adaptation, not a browser CSS engine.

The switch deliberately adopts the **Launcher Pack** recipe: solid readable accent
track, white circular thumb, 120 ms ease slide, fixed capsule even in Square mode,
and no compression, glow or decorative pulse. An 8% neutral hover wash and existing
disabled value residue are explicit Client state adaptations. Slider value/thumb
coordinates remain direct; only its hover decoration eases. Existing click, sound,
focus traversal, narration, disabled gates, key handling, persistence and slider
commit throttles remain owned by their original semantic/native paths.

## Verification and review gate

Deterministic tests cover neutral/status independence, all three action contrasts,
dark/bright/saturated/neutral/red/violet/cyan/amber accents, a 4,096-color RGB lattice
in both modes, exact easing endpoints/samples/monotonicity/retargeting, profile/config
migration preservation and the scoped material/capture/safety boundary, including
pilot density versus unchanged saved/legacy opacity and explicit filter reset.
The final full suite reports **394 tests, 0 failures, 0 errors, 0 skipped**;
`gradlew.bat --no-daemon test build` succeeds. Existing
renderer reliability and semantic interaction tests remain in the full suite.

Local `runClient` probes reach the production hosts from a normal client startup and
capture Dark Round, Dark Square, Light, Violet, a dark accent at 45% opacity with
Wireframe stored, primary hover, independent focus and the actual description
tooltip. Vanilla screenshots exercise automatic suppression and material fallback.
Review refinement captures exposed intermittent missing glyphs in automated
screenshot/fallback frames, including with accent glow disabled. Texture-state
restoration was corrected, but that did not eliminate this capture defect. The
fallback visual check remains unresolved; this revision needs correction before
acceptance or broader rollout. Normal settled captures are available for reviewing
the requested blur, glow and selection treatment.
The probes are developer-local and excluded from the final artifact. No world was
joined; world-backdrop visual acceptance and physical-input feel remain user checks.

At 1600×1000, GUI scale 2, the settled main-window comparison measured 12→1 glass
panels/frame and 1,341→332 submitted AA fills/frame (162 sampled frames each).
The Theme detail comparison measured 14→1 panels and 1,304→965 AA fills (162 each).
The legacy Theme comparison explicitly declared the same panorama for an equal
valid-backdrop comparison; its ordinary old menu path previously declined glass.
Square main measured one panel and 130 AA fills/frame. These are scoped work counts,
not a portable FPS guarantee. Legacy swatch/text-field/scrollbar work remains and
hover/focus/fade costs must be distinguished from settled counts.

Open **Aurora Settings → Mods/Settings**, then click **Theme**. Review Dark Round and
Square first; compare OnePlus, Violet, dark/bright custom accents and low opacity.
Compare solid primary versus softly selected cards, pointer hover versus keyboard
focus, toggle ON/OFF and slider drag, search editing, tooltip readability/shadow and
F2 suppression. Confirm Light functions and saved Wireframe still affects comparison
screens. User acceptance is required before production rollout or taxonomy removal.
