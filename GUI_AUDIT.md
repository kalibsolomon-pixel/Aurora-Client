# Aurora GUI Audit — Findings & Prioritized Plan (2026-09-04)

Full audit of the GUI and GUI system, code-verified against the branch
`fix/glass-session-latch-and-glass-style` (commit `395b406` — the current GUI code; see
`ARCHITECTURE.md` §0 for why master is stale). **No code was changed in this audit.** Every
item below is documentation only; nothing gets executed without explicit item-by-item approval
in a future session.

Method note: everything marked **[verified]** was read directly in the code (file:line refs
are relative to `src/client/java/com/aurora/client/`); everything marked **[inferred]** is
reasoning from code + docs, not a measurement; everything marked **[measured-prior]** cites
in-code instrumentation or prior in-repo measurements (not re-run this session).

---

## Findings

### Category 0 — Repo/process state (the meta-finding)

**F0.1 [verified] The entire current GUI lives on an unmerged branch; master is a stale
snapshot.** Working tree = master = initial commit only (still has `GLASS_PILOT_IDS`, no rim
system, no RP-browser glass, no GlassStyle, no Reset fix). Branch `395b406` carries all of it
plus the only copy of `AGENTS.md`. Any future agent that starts from master will read and
patch year-old conventions. Nothing else in this plan should start until this is landed
(Recommendation P0).

**F0.2 [verified] The four "rumored" work items resolve as follows, from code:**
- ResourcePack browser glass rollout — **done, on the branch** (sidebar/modal/cards/tab glass,
  tokenized radii), with two defects found by this audit (B1, B2).
- Frosted/Transparent style toggle — **done, on the branch** (`theme/GlassStyle.java`,
  early-return guard in `BlurPanelRenderer.renderPanel`, Theme-screen SegmentedSetting; the
  dev config is actively using TRANSPARENT @ 0% opacity).
- Reset-button ("Reset kills glass") bug — **fixed, on the branch** (cold-only rim-mask
  eviction + deferred destruction in `beginFrame()` + GL error-queue drain;
  `permanentlyDisabled` latch deliberately retained). Open judgment call remains: the latch
  still has no recovery short of client restart (AGENTS.md §9) — untouched, as it should be.
- UI performance investigation — **instrumentation exists and is wired** (`GlassStats` behind
  `-Daurora.glassStats=true`, `RenderUtil.fillsSubmitted()`, `[ui-perf]` debug logs,
  `[canvas-cost]` benchmark), but **no GlassStats capture appears in the current
  `run/logs/latest.log`** — the numbers have not been taken for the current glass-everywhere
  state. The one hard datum in the live log is the pool-exhaustion warning (B1).

---

### Category B — Bugs (verified in code unless noted)

**B1. Glass output-pool exhaustion is happening live, and two screens can cause it.**
[verified code + observed log] `run/logs/latest.log` shows
`[BlurPanel] output pool exhausted (24 concurrent panels)` five times at 10 s intervals
(the rate limit) starting 22:40:20 on 2026-09-04 — a sustained condition, ~36 s after world
join. The pool (`BlurPanelRenderer.OUTPUT_POOL = 24`) exists because 1.21.11's deferred
`GuiRenderState` gives each concurrent panel its own output texture. Two screens can exceed it:
- `ResourcePackBrowserScreen`: every visible card calls `renderPanel`
  (`ResourcePackBrowserScreen.java:627`) **and** every card's install button is a glass
  `Button` (another `renderPanel` inside `Button.renderOverlay`), plus sidebar + active tab +
  Done + glass search field → 2 panels × visible cards + ~5. At 6 columns × 4+ rows this is
  50+ panels.
- `ProfileManagerScreen`: the row glass pre-pass draws glass for **all rows with no visibility
  cull** (`ProfileManagerScreen.java:133-143`; the flat pass culls at :176) — a long profile
  list plus toolbar buttons blows the cap.
Consequence per the renderer's own design: extra panels decline → glass/flat flicker on the
overflow elements. Note the pool cap is also functioning as an accidental FPS brake — without
it, these screens would pay full pipeline cost for every panel (see P-note 1).

**B2. The pack browser's per-card glass is invisible — cards pay full glass cost for
nothing.** [verified] Card tint =
`lerpArgb(AuroraTheme.IOS_SECONDARY_BG, AuroraTheme.IOS_TERTIARY_BG, hover)`
(`ResourcePackBrowserScreen.java:626,630`). Those statics project from `SURFACE`/
`SURFACE_VARIANT`, which `PaletteEngine` resolves **opaque** (its `hsl()` always returns
alpha 0xFF; factory palette likewise `0xFF1A1A1A`). The opaque fill is drawn over the entire
glass quad, so the captured blur (and the in-glass lighting/rim) is fully occluded; only
`drawRimFinish` (drawn above the fill) is visible. Compare the sidebar/modal, which correctly
use `ThemeManager.surfaceColor(SURFACE)` (token RGB + `WINDOW_FILL` alpha). So each card runs
capture→blur→composite→readback for a result the user cannot see — while also being the main
pool-exhaustion driver (B1). Verify in-client to confirm the visual, but the alpha math is
unambiguous. (Fix direction — make the tint translucent like the sidebar, or drop per-card
glass — is R9, a user-visible look decision, not a silent cleanup.)

**B3. `ProfileManagerScreen` is the only glass screen without a `renderBackground`
world-gate override.** [verified] All sibling screens override `renderBackground` to skip
vanilla's blur+dim sandwich when a world is live; Profiles doesn't
(agent-verified absence; siblings at `AuroraScreen.java:126`, `FeatureDetailScreen.java:142`,
`WaypointManagerScreen.java:127`, `ColorPickerScreen.java:155`, `HudEditorScreen.java:131`,
`ResourcePackBrowserScreen.java:861`). With a world loaded, vanilla's sandwich renders first,
so the "pre-dim" glass pass samples the already-darkened, already-blurred backdrop —
contradicting the documented layering contract and making Profile rows read differently from
every other glass surface.

**B4. `WaypointManagerScreen` draws row glass AFTER the overlay dim.** [verified] The
`OVERLAY_DIM` fill is at `WaypointManagerScreen.java:136` but row glass renders inside
`renderRow` at `:223-225` — the opposite order from every other screen (contract: glass
pre-dim). Waypoint rows sample the already-dimmed target and read darker than their Profile
equivalents. Same root cause as B3: the layering contract lives only in convention/comments,
enforced nowhere (see R1).

**B5. The `reflex` feature is unreachable in the UI.** [verified] `FeatureRegistry` registers
`reflex` in the MODULES bucket (`FeatureRegistry.java:983`) but `ModuleManager`'s hardcoded 33
cards don't include it, and AuroraScreen's grid is driven exclusively by ModuleManager. No
tile, no settings row — the feature may still run via config, but users cannot see or
configure it. (Also the only functional instance of the general drift hazard D8.)

**B6. `item_scale`'s Reset button doesn't reset the per-hand fields.**
[verified] `FeatureMetadata.java:26-28` javadoc cites `offHandScale*`/`mainHandScale*` as the
reason `resetPrefixes` exists — yet `item_scale` is registered WITHOUT prefixes
(`FeatureRegistry.java:882-895`), so `reset()` matches only `itemScale…` fields and never the
per-hand defaults. One-line class of fix; behavior change is "Reset now resets more", which is
its documented intent.

**B7. `BooleanSetting` ignores its own disabled state.** [verified]
`BooleanSetting.java:141-149` — `mouseClicked` has no `isDisabled()` guard: a disabled row
still toggles and saves. It also consumes clicks anywhere in the whole row (label included),
unlike siblings. (Related: `DoubleSliderSetting` has no disabled guard anywhere while
`IntSliderSetting` guards render/click/keys — same defect class, split across B7/B8.)

**B8. `DoubleSliderSetting` missing `isDisabled()` guards** (render colors, click, keys) —
[verified] `DoubleSliderSetting.java:61-101` vs the guarded `IntSliderSetting.java:53,83,110`.

**B9. `SegmentedSetting.glassSegments(false)` is dead — glass is forced on every frame.**
[verified] `SegmentedSetting.java:106` unconditionally calls `control.glassEnabled(true)` in
`renderShapes`, overriding any opt-out. The registry's `.glassSegments(true)` calls
(`FeatureRegistry.java:87,97,107`) and `.glassButton(true)` calls (:191, :303) are all no-ops
now that defaults flipped on — harmless, but the API lies.

**B10. Focus/lifecycle leaks across screen close in several settings widgets.** [verified]
`KeybindSetting` has no `onDetailScreenClose`: closing the detail screen while listening
leaves `listening = true` and the **static** `FeatureSetting.activeFocused` pointing at a
stale row into the next screen. `EnumSetting` keeps `expanded`/`scrollOffset`/focus (popup
reopens next visit; and collapsing on outside-click returns `false` at
`EnumSetting.java:305-308`, letting the click also act on the screen underneath).
`ItemScaleSetting` and `StringListSetting` keep search text/focus (ParticleConfig and
KeyList reset correctly — the inconsistency is the bug pattern).

**B11. Missing config saves on add/delete paths.** [verified] `ItemScaleSetting` add
(:353-357) and delete (:381-385), and `StringListSetting.addEntry` (:185-193) never call
`AuroraConfig.save()` — changes persist only if something else saves later (a crash loses
them). Sibling widgets save at commit.

**B12. Click-through/margin bugs in the two list screens' hit-testing.** [verified]
Both `ProfileManagerScreen` (:405-433) and `WaypointManagerScreen` (:323-345) hit-test rows
without accounting for the scissor clip — a row scrolled above `LIST_TOP` remains clickable in
the margin zone. Worse, the ProfileManager switch-profile body click has **no X bounds**: any
click at the row's height, however far outside the list horizontally, switches profiles.

**B13. `WaypointManagerScreen` row caches keyed by index, invalidated only on count change.**
[verified] `WaypointManagerScreen.java:78-82,150-157` — a remove+add between frames keeps the
count equal and leaves captured `Waypoint` objects acting on deleted entries (rename/copy/
delete targeting the wrong waypoint for a frame or longer).

**B14. `PixelCanvasSetting` first-frame misplacement + benchmark thread lifecycle.**
[verified] The status line draws using `canvasY` before it is assigned that frame
(:291-298 — stale/zero coordinate on first render); the benchmark's raw daemon thread can
write `benchResult` after the screen closes/disposes (:460-470 vs :762) — consumed next open.

**B15. `EditBoxMixin` caret always renders at the end of visible text** (mid-string cursor
misplaced during blink) because `cursorPos` isn't shadowed — [verified]
`EditBoxMixin.java:142-148`. Cosmetic but visible in every search field.

**B16. `AbstractButtonMixin` caches label width forever** (`aurora$cachedLabelW` never
invalidated) — a vanilla button whose message changes after first render stays mis-centered.
[verified] `AbstractButtonMixin.java:33,97`.

**B17. `WorldMapScreen` prompt loses typed text on window resize** — `init()` re-creates
`promptName` and resets its value while `promptOpen` survives. [verified]
`WorldMapScreen.java:142-147`.

**B18. `ToggleSwitch` animates at frame-rate-dependent speed** — `advance()` always calls
`update(0.016f)` (hard-coded 60 fps delta; `ToggleSwitch.java:113`), so at 240 Hz the toggle
springs ~4× faster than at 60 Hz. Everything else in the codebase moved to wall-clock dt.

**B19. Smaller verified defects** (each real, each small):
`ThemePreviewSetting.java:168` chip radius clamp uses `BTN_H/2` instead of `CHIP_H/2`, and
:199-202 likely double-draws the mock-button labels; `EnumSetting.java:135` cache check is
reference-equality on a per-frame-fresh string (cache never hits when truncated — perf nit);
`StringListSetting.java:90` truncates by char count, not width (overflow with wide glyphs);
`ItemScaleSetting` "+" click returns true on null item (:347-361) and its hover band (28 px)
≠ click area (26 px) (:181 vs :182,378); `HudEditorScreen.findAt` (:441-459) returns the last
overlapping module, not the topmost, and `disableViaRegistry` fallback (:483-485) desyncs
editor-local enabled vs registry state; `ColorPickerScreen.java:73` `padSize` can go ≤ 0 on
tiny windows (inverted fill loops); `FeatureSetting.java:367` tooltip text hardcodes
`0xFFE6E9F0` instead of a token.

**B20. Two fully-written mixins are dead at runtime.** [verified]
`MultiplayerServerListWidgetMixin` (numeric ping on server rows — complete, font-flag-wired,
clearly intended to run) and `MultiplayerScreenMixin` (refresh-all; bodies gutted, but its
class comment *wrongly claims* the injects still apply) are absent from
`aurora.mixins.json`. Registering them is a behavior change (the numeric-ping one looks like
an accidental omission; the refresh one looks deliberate) — decide, then either register or
delete; today the state is undocumented and misleading.

---

### Category P — Performance

**P1. Per-panel `glReadPixels` readback is the structural cost; the pack browser multiplies
it.** [inferred from code + docs; instrumentation exists, not yet run] The class javadoc and
`GlassStats` design note both identify the synchronous per-panel readback (~1-2 ms/panel,
serializing CPU and GPU once per panel) as the scaling cost. With the glass-everywhere
rollout, a screen like the pack browser issues dozens of panel pipelines per frame (B1/B2);
at 24 panels that is plausibly 24-48 ms/frame of readback alone — matching the pool's
accidental role as an FPS brake. **Before any optimization: run the game once with
`-Daurora.glassStats=true` on the pack browser, Profiles, main screen, and a detail screen,
and record the log lines.** The fix directions (flat cards / budget policy / eventual
PBO-or-GPU-resident composite) are decisions, not cleanups (R9/R10).

**P2. Three glass screens bypass the `UiLayerCache` static-chrome discipline.** [verified]
`AuroraScreen` and `FeatureDetailScreen` rasterize static chrome once (version-keyed) and blit;
`ProfileManagerScreen`, `WaypointManagerScreen`, and `ResourcePackBrowserScreen` re-submit all
shape fills every frame (the pack browser re-renders ~40 cards' fills/outlines; the color
picker re-renders ~2000 fills/frame for its pad/strips, `ColorPickerScreen.java:206-243`).
`RenderUtil.fillsSubmitted()` + `[ui-perf]` exist precisely to quantify this. Medium effort,
no look change if done to the same AA math (the capture sink guarantees identical output).

**P3. `MixinFont`'s swapped measurement paths allocate per call.** [verified]
`width(String)` builds a `Component.literal(...).withStyle(...)` per measurement
(`MixinFont.java:168`); `plainSubstrByWidthImpl` is a per-codepoint loop each iteration
allocating and re-measuring (effectively O(n²) with garbage; :245-280). Only matters when
font scope ≠ OFF, worst in ALL mode — vanilla truncation uses it everywhere.

**P4. Minor verified perf nits.** `EnumSetting` reference-equality cache miss (B19);
`ItemScaleSetting` re-measures chevron glyphs per row per frame where `EnumSetting` caches
(:215); pack-browser `mouseClicked` iterates all results without visible-row culling (:954);
`BlurTestScreen` does file I/O every frame (`pollStateFile` :133-145 — harness only);
`WorldMapScreen` software-rasterizes the player arrow edge pixels with `g.fill` (:511-551).
Prior measured wins already landed (worth knowing as precedent): the crosshair canvas fill
loop (12.8 ms → ~0.006 ms via `CanvasTexture`), `SavedGlState` pooling (~2000 direct-buffer
allocs/s eliminated on the pack browser), text-fit caching (~120 font-width calls/frame
removed).

---

### Category D — Duplication / inconsistency (grouped by root cause)

**D1. The glass-surface idiom is copy-pasted ~10× with two divergent layering placements.**
[verified] The same ~10-line block (renderPanel → tint → drawRimFinish / flat fallback) exists
at `AuroraScreen.java:267,292,351,435`, `ResourcePackBrowserScreen.java:383,572,627,741`,
`ProfileManagerScreen.java:264-276,332-340`, `WaypointManagerScreen.java:223-233`, plus
inside `Button`, `SegmentedControl`, `EditBoxMixin`, and 4 setting widgets. Two sites place it
wrong (B3, B4) — the classic signature of a convention that exists only in comments. Root
cause of the two worst layering bugs; consolidation is R1.

**D2. `liveWorldBackdrop()` — 7 copies** (4 named private methods + 3 inline variants), all
`minecraft != null && minecraft.level != null`. Trivially consolidatable (a `ThemedScreen`
default method or a static helper).

**D3. Smooth scrolling — 4+ independent implementations, 6 scrollbar variants.** [verified]
`AuroraScreen` (fps-adaptive τ table 24-65 ms, draggable thumb), `FeatureDetailScreen`
(fixed τ=60, no thumb), `ResourcePackBrowserScreen` (fixed τ=80, two draggable tracks),
`ProfileManagerScreen`/`WaypointManagerScreen` (wheel clamp, no easing, no visual scrollbar).
Also `AuroraScreen.drawScrollbar` vs `ResourcePackBrowserScreen.renderScrollbar` are
independent thumb implementations with literal radius 2.

**D4. Color/lerp/trim/checkerboard helpers duplicated.** [verified] `lerpColor(int,int,float)`
defined identically 4× in `screen/setting/` alone (Keybind:177, KeyList:223, Enum:327,
PixelCanvas:822) while `AuroraAnim.lerpArgb` exists; inline hover-rect math at 15+ sites
(only `ColorSetting` has an `inBounds`); ellipsis-trim loops in 4 variants (one char-count
based — B19); transparency checkerboard in 3 files; HSL↔RGB converters in 2 files; the
`(alpha<<24)|(token&0xFFFFFF)` idiom re-inlined in ~10 files where
`ThemeManager.withAlpha`/`surfaceColor` exist (`AuroraScreen.alpha()`/`surfaceFill()` are
private local re-implementations of both).

**D5. Animation/hover helpers — 4 classes + 3 hand-rolled idioms.** [verified]
`AnimationCurves`, `AuroraAnim`, `HoverAnim`, `ui/util/Animation` coexist (AGENTS.md already
flags this); `Button.advanceHover` hand-rolls what `HoverAnim` does; the pack browser has a
third Map-keyed `updateHover`; toast fade is hand-rolled twice (Profile/Waypoint, identical
math and constants); shadow/glow ring loops exist in 4 files with 3 different tunings.

**D6. Screen-level twins.** [verified] `ProfileManagerScreen` and `WaypointManagerScreen`
share near line-for-line: toast system, `fitName` truncation, inline-rename EditBox lifecycle,
header/footer button row, wheel-scroll clamp, tooltip block (the whole render block
:183-216 ≈ :211-253). Consolidation candidate R3.

**D7. Settings-widget skeleton clones.** [verified] `IntSliderSetting` ≈ `DoubleSliderSetting`
≈ `ThemeOpacitySetting` slider-row skeleton (~120 lines); the live-reload throttle is a
near-copy in `AccentSetting` vs `ThemeOpacitySetting` (~30 lines); the glass pill is cloned
across Keybind/KeyList/Enum/ItemScale; remove-row loops, search-container forwarding
(ItemScale ≈ ParticleConfig, ~80 lines), and PixelCanvas's three near-identical button
drawers.

**D8. Registry drift — the structural duplication.** [verified] Three registries
(FeatureManager / FeatureRegistry / ModuleManager) with three id-resolution paths
(`Module.meta()`, `AuroraScreen.findMeta()`, silent-null `setEnabled`). Already produced B5
(reflex) and text divergence (grid descriptions differ from detail-screen descriptions for
`theme`, `animations`, `item_physics`). Dead: `Module.Category.SETTINGS` never instantiated,
`getModulesByCategory` uncalled, `FeatureRegistry.all()` misleadingly returns only modules.

**D9. Dead code inventory.** [verified] `FeatureTile` (406 lines; zero constructors — the
grid renders tiles inline in AuroraScreen; only javadoc references remain in
ModuleAccentColors/ModuleIconRegistry), `SliderFocus` (zero references — the real mechanism is
`FeatureSetting.activeFocused`), `AuroraModMenuApi` (Mods button retired),
`MultiplayerScreenMixin`/`MultiplayerServerListWidgetMixin` (B20), `MixinGuiGraphics`
(intentional stub), empty-but-registered `WindowMixin`/`RenderTargetMixin` (intentional,
config-compat), `FeatureSetting`'s write-only wrap-cache fields + always-0
`descriptionHeight`, `ButtonSetting`'s unused geometry fields, dead `TRACK_H/KNOB_R` in the
slider settings, `HudEditorScreen.drawGrid/grid` (never drawn), `Feature.enabledByDefault()`
(never read), config orphans (`timeChangerEnabled`, compliance `hitboxFeatureEnabled`).

**D10. Inconsistent conventions inside the settings package.** [verified] Label token split:
`AuroraTheme.TEXT_PRIMARY` (8 widgets) vs `AuroraTheme.IOS_LABEL` (8 widgets) for the same
visual role. Disabled handling: Int/ThemeOpacity guard, Double/Boolean don't (B7/B8), others
have no concept. Icons: Material chevrons (Enum/ItemScale) vs ASCII `"v"/">"` (ParticleRow) vs
ASCII `"x"` trash (ItemScale) vs `"−"/"+"` glyphs. Lifecycle: 4 widgets reset on close, 4
don't (B10). Glass: pills glass in 4 widgets, flat in PixelCanvas/StringList siblings.

---

### Category C — Design / consistency gaps

**C1. Radius tokens not honored on older surfaces.** [verified] The pack browser is the model
(every radius a token; only scrollbar-thumb capsule literals remain — per its own note). But:
`AuroraScreen` uses literals 5 (chips :272), 6 (tiles :356), 4 (layout buttons :440);
`ColorPickerScreen` uses 4/6 exclusively; toasts hardcode 4 in two screens. Corner Style =
SQUARE therefore squares the window but leaves these rounded. (Whether small controls should
follow `RADIUS_SMALL` vs stay pill-capsule is a design ruling — the toggle/slider knob
capsule exemption is precedent — but today it's accidental, not ruled.)

**C2. The HUD layer and worldmap screens are entirely outside the theme system.** [verified]
`hud/` and `worldmap/` have **zero** `ThemeManager`/`AuroraTheme` references. `HUD_BACKDROP_*`
tokens exist in the palette for exactly this purpose and nothing reads them. Worst single
item: `KeystrokesModule.java:70-73` hardcodes its own accent `0xFF30A5FF`. Ping tiers,
toggle-on greens, potion text, minimap entity dots all hardcode red/green/amber while
`SEMANTIC_*` tokens sit unused (12 refs mod-wide). Possibly partly deliberate (HUD floats over
the world) — but the tokens' existence says intent outran wiring. Re-theming HUD is
user-visible; see R6 (needs sign-off).

**C3. Waypoint default color disagrees with itself.** [verified] `0xFFFFAA00` amber in
WaypointManager/FeatureRegistry/MinimapModule vs `0xFFFF3344` red in WorldMapScreen (:578) for
the same "default waypoint" concept.

**C4. `customTitleScreen` gates more than the title screen.** [verified] It also silently
controls the multiplayer/world-select search bars, button theming, and starfield backgrounds
(EditBoxMixin/AbstractButtonMixin/SelectionScreenBackgroundMixin). Disabling the custom title
unexpectedly un-themes three other screens.

**C5. Three different "is this an Aurora screen?" predicates** (ThemedScreen marker /
`com.aurora.client.` prefix / `com.aurora.client.screen` prefix) — see ARCHITECTURE.md §8.

**C6. Two enums named `GlassStyle`** (`theme.GlassStyle` vs `Button.GlassStyle`) — naming
hazard at every import site.

**C7. RP browser active-tab tint deviates from the stained-glass convention** — hand-rolled
accent lerp at 0.30-0.40 alpha (`ResourcePackBrowserScreen.java:551-556`) where the convention
(AGENTS.md §6.3) reserves `stainedTint()` (floor 0.55) for exactly this "selected control"
role. Also the only place the success state can't be expressed (Install/Installed mapped to
stained/neutral because `Button` has no success variant — already flagged in AGENTS.md §9).

**C8. Vanilla-screen theming is title-texture-based, not token-based**
(`SelectionScreenBackgroundMixin` blits a fixed PNG) while the buttons on the same screens are
token-driven — mixed provenance on one screen.

---

### Category R — Feature reorganization opportunities

> Each of these is its own explicit item. None removes any user-facing option or capability;
> each changes where/how a feature is implemented or presented. **None may be executed without
> explicit sign-off on that specific item.**

**R1. Extract the glass-surface idiom into one shared helper** (e.g.
`ui/component/GlassSurface` with `depressedWindow(...)` / `raisedControl(...)` /
`stainedControl(...)` returning boolean). What changes: ~10 copy-pasted blocks across 7 files
become one implementation; the layering placement (pre-dim) and fallback contract get enforced
in one place instead of convention. User-facing result: **none on screens that already do it
right** (AuroraScreen, FeatureDetail, RP sidebar/modal, ProfileManager rows once B3 is fixed);
WaypointManager rows would visibly change only insofar as B4's fix corrects their brightness.
Why: D1 is the root cause of the two layering bugs (B3/B4); a third bug of the same class is
otherwise a matter of time. Scope: small-medium. **Touches protected glass conventions in
spirit (it centralizes them, doesn't change them) — present the exact helper signature for
approval; do it AFTER B3/B4 are fixed so the pilot can prove pixel-parity.**

**R2. One scroll/easing component** (smooth target-lerp + optional draggable thumb, τ
configurable) replacing the 4 scroll implementations and 6 scrollbar variants. User-facing
result: identical per-screen behavior if τ is preserved per screen; enables adding a visible
scrollbar to Profiles/Waypoints later (improvement, opt-in per screen). Scope: medium; adopt
screen-by-screen with pilots (R-staging below).

**R3. Merge the ProfileManager/WaypointManager shared machinery** (toast, fitName, rename
lifecycle, toolbar row, row-container pattern) into a shared list-screen foundation.
User-facing result: none intended (pixel-parity pilot first); the two screens' remaining
differences (row content, actions) stay. Scope: medium.

**R4. Collapse Int/Double/ThemeOpacity slider settings into one parameterized slider setting**
(presentation identical; ThemeOpacity keeps its throttled live-reload as a hook).
User-facing result: none. Also dedupes `lerpColor` ×4, the throttle copy, and the value-string
cache triple. Scope: small-medium, very mechanical.

**R5. Make `HoverAnim` + `AuroraAnim` the canonical motion utilities** (Button adopts
HoverAnim; the pack browser's hover map adopts it; toast fades share one helper; fix B18's
fixed-dt while there). User-facing result: none visually (curves match within a few ms); the
toggle speed becomes frame-rate correct. Scope: small.

**R6. Wire HUD modules + worldmap chrome to the theme system** (`HUD_BACKDROP_*` tokens
exist unused; `SEMANTIC_*` for status colors; kill the hardcoded `0xFF30A5FF` accent).
User-facing result: **HUD colors begin following the user's accent/mode — a visible change**
(hence sign-off; arguably a feature). Suggest a design pass first (which elements theme, which
stay structural neutrals). Scope: large. Do last.

**R7. Derive `ModuleManager`'s grid from `FeatureRegistry`** (order override list where the
grid's curation matters). Fixes the B5 class permanently and ends the dual-maintained
name/description text. User-facing result: grid gains the missing `reflex` tile (that's B5's
fix — if R7 is deferred, fix B5 by adding the entry by hand). Scope: small-medium.

**R8. Cache the color picker's editing surfaces** (`CanvasTexture`-style raster for pad/strips,
re-raster only on value change). User-facing result: none (same pixels). Perf: removes ~2000
fills/frame. Scope: small-medium.

**R9. Decide the pack-browser card glass policy** — either (a) make card tints translucent
(`surfaceColor`-style) so the existing per-card glass becomes visible and then confront the
panel budget, or (b) drop per-card glass (flat cards inside an already-glass screen — matching
the sidebar-tabs precedent: "small transient rows inside an already-glass container stay
flat"), keeping glass for sidebar/modal/tab/chrome. User-facing result differs per option:
(a) frosted cards (new look, heavier), (b) flat cards (current look, since the blur is
currently invisible anyway per B2 — this option is close to a no-op visually and a large perf
win). **Either way it's a look decision + protected-system adjacent — needs GlassStats numbers
and explicit sign-off.**

**R10. Glass budget policy** (system-level): instead of pool-exhaustion flicker (B1), define a
per-screen priority order (window > primary buttons > rows > cards) so overflow degrades
deterministically, and/or raise the pool. Touches the renderer's pool machinery — protected
territory; propose only with measurements from P1's GlassStats run.

---

## Prioritized recommendations

Ordered by impact-vs-risk. "Safe & mechanical" = no user-visible change intended, no
protected architecture touched; still verify in-client per the project's pilot style.

| # | Item | What/Why | Size | Risk class |
|---|---|---|---|---|
| **P0** | **Land the branch** (`fix/glass-session-latch-and-glass-style` → master), bring AGENTS.md + this audit's ARCHITECTURE.md with it | F0.1 — every other item depends on auditing/patching the real code | trivial (git) | none |
| **P1** | **Take the measurements**: run with `-Daurora.glassStats=true` on pack browser / Profiles / main / detail screens; save the log | P1/B1/R9/R10 are guesses until then | trivial | none |
| **P2** | **Bug batch A — behavior bugs, safe & mechanical** (each independently verifiable): B5 reflex tile (or R7), B6 item_scale prefixes, B7 BooleanSetting disabled guard, B8 DoubleSlider guards, B9 dead opt-out flag, B10 lifecycle resets, B11 missing saves, B12 scissor/X-bounds hit-testing, B13 index-keyed caches, B14 PixelCanvas first-frame + thread, B19 small defects | Each is a contained correctness fix; no look change | small ×many | safe, mechanical (B12 slightly fiddly — pilot it first) |
| **P3** | **Bug batch B — layering contract fixes**: B3 ProfileManager world-gate, B4 WaypointManager pre-dim placement | Fixes two violations of a protected convention (the convention itself unchanged) | small | touches glass integration pattern — verify visually pre/post |
| **P4** | **R9 card-glass decision + R10 budget policy** (after P1 numbers) | The biggest live perf/visual question in the GUI today | medium | **needs explicit sign-off** (look change + renderer policy) |
| **P5** | **R1 glass helper consolidation** (pilot: WaypointManager + ProfileManager — smallest glass screens; prove pixel parity, then extend) | Kills the bug class behind P3 | medium | protected-adjacent; helper signature needs approval |
| **P6** | **R4 slider consolidation + R5 animation consolidation + D4 helper dedupe** | Pure code structure, zero intended visual change | medium | safe, mechanical — pilot on one pair first |
| **P7** | **R2 scroll component** (pilot: FeatureDetailScreen; second: pack browser) | Unifies 4 implementations; enables scrollbar parity | medium | behavior-sensitive (feel) — keep per-screen τ initially |
| **P8** | **P2 perf**: R8 color-picker caching; adopt `UiLayerCache` on Profiles/Waypoints/RP-browser static chrome; MixinFont measurement de-allocation | No look change; measurable via `fillsSubmitted()` | medium | safe; verify pixel parity on RP browser |
| **P9** | **Token hygiene**: C1 radius tokens (design ruling on small-control radii first), label-token unification, C3 waypoint default, C7 tab tint → `stainedTint()`, B19's tooltip color | Consistency; some visible change in SQUARE corner mode | medium | mixed — the radius ruling needs a decision |
| **P10** | **R7 registry derivation + B20 dead-mixin decisions + D9 dead-code removal** | Ends the drift class; shrinks surface area | medium | behavior edge (reflex tile appears); deletions need a once-over each |
| **P11** | **R3 list-screen foundation merge** | Structural | medium-large | pixel-parity pilot required |
| **P12** | **R6 HUD/worldmap theming** | Largest visible change; design doc first | large | **needs explicit sign-off** |

Protected-architecture scorecard: **no recommendation above changes any protected rule.**
P3/P5 enforce the existing layering contract rather than alter it; R9/R10 change *policy on
top of* the pipeline, not the pipeline's invariants; the `permanentlyDisabled` latch-recovery
question from AGENTS.md §9 remains deliberately open and untouched by this plan.

---

## Suggested staging (pilots before waves)

The project's established style: 1-2 verified pilots, then extend. Proposed order:

1. **Stage 0 — land + baseline.** Merge the branch (P0); take GlassStats + `[ui-perf]`
   baseline numbers (P1); update AGENTS.md §6/§8 and ARCHITECTURE.md if anything shifted.
2. **Stage 1 — correctness pilot.** B7+B8 (two sibling widgets, one defect class) as the
   pilot for "bug batch A" mechanics (fix → in-client verify → extend to the rest of P2).
   Then P3's two layering fixes as their own small verify (screenshots vs pre-fix at
   7/25/93% opacity, dark+light).
3. **Stage 2 — the glass decision.** Bring P1's numbers + B2's analysis to the user; get an
   explicit R9 ruling (flat cards recommended as the near-zero-visual-change, large-win
   option); only then discuss R10's budget/pool policy. Pilot any policy on the pack browser
   alone before touching the renderer defaults.
4. **Stage 3 — consolidation wave 1 (structure, no look change).** R1 pilot on
   WaypointManager (smallest glass surface, already stabilized by B4's fix) — pixel-parity
   screenshots gate the extension to the other ~8 call sites. R4+R5+D4 as a separate
   mechanical PR (pilot: one slider setting pair).
5. **Stage 4 — consolidation wave 2.** R2 scroll pilot on FeatureDetailScreen (no thumb →
   no visual regression possible), then pack browser. P8 perf items alongside, each gated on
   `fillsSubmitted()`/GlassStats deltas.
6. **Stage 5 — registry + hygiene.** R7, B20 rulings, D9 deletions (one commit per deleted
   subsystem so revert is trivial).
7. **Stage 6 — visible-change items, each individually approved.** P9 token rulings, R3,
   and finally R6 (design doc first).

Dependencies worth respecting: P5 depends on P3 (fix the placement before freezing it into a
helper); R10 depends on P1 (measure first); R6 should not start before C2's design ruling;
everything depends on P0.

---

## How to replicate this approach

*(Kept as its own top-level section here rather than a separate file — it documents method,
not architecture.)*

### Patterns — what to reuse and where the teeth are

- **Reuse, never reimplement**: `RenderUtil` (all AA primitives + `beginCapture`),
  `UiLayerCache` (static chrome), `ui/component/*` (the only button/toggle/slider/segmented
  implementations), `AuroraAnim`/`HoverAnim` (motion), `ThemeManager.color/surfaceColor/
  stainedTint/withAlpha` (never re-inline the alpha-mask idiom), `MaterialIconRenderer`
  (icons — the font atlas is NEAREST and degrades when scaled), `FeatureSetting`'s
  shapes/overlay/glassPass split + focus registry + tooltip dwell, `EditBoxMixin` (the only
  search bar), `BlurPanelRenderer.renderPanel + drawRimFinish` (the only glass entry points).
- **Conventions are enforced nowhere** — they live in AGENTS.md §6 and javadocs. Before
  touching glass, re-read §6's ten rules and the `BlurPanelRenderer` class javadoc; the bugs
  in B3/B4 exist because convention-only enforcement fails silently.
- **Non-obvious gotchas**: deferred `GuiRenderState` means never destroy textures mid-frame;
  `glGetError` reports the oldest queued error (drain first); PaletteEngine surfaces are
  opaque (compose translucency explicitly — B2 is what happens when you forget); `IOS_*`
  statics hold OnePlus Red; two different `GlassStyle` enums; `defaultRequire:1` makes
  descriptor drift a launch crash; the output pool declines (not queues) beyond 24 panels.
- **Instrumentation already exists** — use it before optimizing anything:
  `-Daurora.glassStats=true`, `RenderUtil.fillsSubmitted()`, `[ui-perf]` debug logs,
  `[canvas-cost]` benchmark, `BlurPanelRenderer.lastOutcome()` decline reasons. Evidence
  lands in `run/logs/latest.log`.
- **Where the bodies are buried**: `AGENTS.md` §9 (known work/reverts/hazards) is accurate as
  of 2026-09-04 — trust it, and update it in the same change when you touch anything it
  describes. The git-history layer to know: master has one commit; all narrative history
  lives in javadocs and AGENTS.md.

### Process — how this audit was run (reusable method)

1. **State before substance.** First actions were: check for AGENTS.md/ARCHITECTURE.md,
   `git log`/branches, and reconcile the working tree against the branch. This surfaced the
   decisive fact (master is stale; the branch is the real GUI) that would otherwise have
   invalidated every subsequent finding. **Always establish which commit is the truth before
   reading a single file.**
2. **Mine prior context.** The branch's AGENTS.md (611 lines, self-dated and self-verified)
   served as a claimed map; this audit then spot-verified its load-bearing claims (glass
   rollout table, Reset fix, GlassStyle) against code rather than trusting them — which
   changed several "known" answers (e.g., the RP-browser glass is landed but defective).
   Live logs (`run/logs/latest.log`) were read for runtime evidence (pool exhaustion) that
   code alone can't prove.
3. **Read the core yourself, fan out the rest.** The load-bearing subsystems (theme engine,
   BlurPanelRenderer, main screens, shared components) were read in full by the lead — these
   produce the judgment calls. Broad, pattern-shaped sweeps (all 20 setting widgets; 8
   secondary screens; 16 mixins; hardcoded-color grep; registry diff) were delegated as
   parallel read-only Explore agents with tightly-scoped prompts demanding file:line
   evidence. Every agent claim that became a headline finding was either read directly or
   spot-verified afterward (e.g., B2's alpha math was personally re-derived from
   PaletteEngine before being called a bug).
4. **Group by root cause, not by symptom.** Ten near-identical glass blocks aren't ten
   findings; they're one finding (D1) with ten locations — and the two real bugs (B3/B4)
   were found *because* the grouping asked "where does this idiom diverge?" Same for the
   scroll/hover/literal-radius families.
5. **Label evidence class.** Every perf claim is tagged [measured-prior]/[inferred]/
   [verified]; nothing is presented as measured that wasn't. The audit explicitly recommends
   taking the missing measurement (P1) before any perf work.
6. **Prioritize by impact-vs-risk and stage by pilot-ability.** Items were sorted into
   safe-mechanical vs needs-sign-off, then sequenced so each risky step has a 1-2 screen
   pilot and each dependency (measure → decide → consolidate) is explicit.
7. **For a re-audit after changes land**: re-run steps 1-2 (branch state may have changed
   again), re-take the GlassStats baseline, and diff this document's findings against the
   then-current code — most sections are written to be checkable item-by-item.
