# Unified Aurora Theme Pilot — Owner Correction Pass

## Verdict

READY FOR OWNER RE-REVIEW

Implementation, desktop interaction review, five new runtime captures, alternate
GUI-scale review and final build/tests are complete. Owner visual acceptance is
the remaining pilot gate; broader rollout and legacy taxonomy removal stay deferred.

## Main settings

Logo removed: yes. Text branding retained: AURORA. Divider corrected: removed.
Cards square: 80×80 with accepted 6px gaps. Icons centered: yes. Names centered:
yes. Chevrons/reserved space removed: yes. Whole-card toggle and right-click
detail opening remain. Semantic inventory: 62 controls, down from 99 after
removing 37 redundant Configure actions. Settings-tab ordinary captions and
status subtitles also retain their original left alignment.

## Pause menu

The supplied reference is AuroraTitleScreen, the main menu. The official white
Aurora logo is centered ABOVE its five text-only cards, following the owner's
latest instruction. Preferred size 48px, gap 12px, height-constrained sizing,
combined group centered; existing 220×30 buttons and action routing remain.
AuroraBrand blits the existing official 256px logo derivative through GUI_TEXTURED.
No new asset or logo-specific blur pass. There is no added vanilla PauseScreen entry.

## Feature detail layout

Pack Tweaks supplies the accepted bounded frame (≤344×300), original 12px left
label inset and measured right-side controls. All FeatureDetailScreen hosts use
that frame. Compatible Boolean, Slider, Enum, Color, Keybind, Button and Section
rows share the compact measurement: 24px ordinary rows, 28px eligible inline
sliders with ≥120px track, and ≥36px stacked/wrapped fallback. Boolean live-value
subtitles reserve space. Complex editors keep specialized geometry. Item Scale
and Alerts retain pinned searches; all seven expanded Item Scale sliders also
measure compactly and contribute their actual heights to scrolling. Measurements
clear on close. The unmeasured inline Settings fallback also paints labels and
subtitles from the left. Header identity/attached credits use metadata.

## "+" icon

Root cause: odd FreeType raster bounds were divided by GUI scale then rounded
to integer GUI dimensions, resampling the ink. NativeIconGeometry pads the
transparent canvas to multiples of twice GUI scale, preserving centered native
ink and exact 1:1 integer blits. MaterialIconRenderer still owns raster/cache/
fallback. Both search add-button fallbacks use AA primitives at unchanged 24×20
geometry. Live checked at GUI scales 3 and 4; deterministic geometry tests cover
scales 1–6 and ink bounds 1–192.

## Preserved behavior

Desktop checks exercised whole-card toggling, right-click detail opening,
Mods/Settings/Profiles navigation, slider drag/value/clamp, toggles, enum popup
selection and input priority, Reset and Done, item/effect search and plus-add,
expanded Item Scale rows, clipping, scrolling and pinned searches. Keyboard Tab
focus and Space activation remained functional; representative Zoom keybind
layout retained C. Shared targeted contracts cover slider modifiers, semantic
actions, tooltips and removal behavior. No schema, defaults, reset-prefix,
registry or gameplay change. Original runtime config/profiles/options were
restored byte-for-byte after closing the task-owned client.

## Material/performance

Runtime telemetry exposed independent child-card captures in the initial main
screen (15 panels/frame). AuroraScreen and FeatureDetailScreen now opt into
ThemedScreen.parentGlassOnly(); GlassSurface.card declines before capture in
these hosts, letting embedded controls use their existing cached surfaces.
Standalone-card contexts keep their existing behavior. Renderer, shaders,
palette and motion are unchanged. Final main/detail observations at GUI 3 and 4
show panels/frame avg=1.0, max=1, window claims only, zero child-card claims,
zero declines and zero pool exhaustion. GUI 3 Pack Tweaks with an open enum
also retained one parent capture. Representative GUI 4 telemetry was ~2.35ms
for main and ~3.08ms for Item Scale; these narrow observations are not a general
FPS claim. No GL/render exception appeared; existing offline auth/Realms errors
are unrelated to the UI.

## Tests

Initial targeted correction suites: 124 passing. Expanded material-focused
selection after the runtime capture correction: 105 passing. Final alignment/
material/row targeted checks passed. Final `gradlew.bat test build` after all
source changes: BUILD SUCCESSFUL; 415 tests, 0 failures, 0 errors, 0 skipped.
The earlier 414-test run preceded the runtime-discovered fix and added contract.
Existing development Java 25/LWJGL native-access/deprecation warnings remain.

## Screenshots

Five new, actual client screenshots after the capture correction:

- [Main Mods](C:/Users/kalib/.codex/visualizations/2026/10/06/01a11030-ac6c-7070-922a-87cda5952ab9/owner-corrections/01-main-mods.png)
- [Main menu — logo above cards](C:/Users/kalib/.codex/visualizations/2026/10/06/01a11030-ac6c-7070-922a-87cda5952ab9/owner-corrections/02-menu-branding.png)
- [Pack Tweaks](C:/Users/kalib/.codex/visualizations/2026/10/06/01a11030-ac6c-7070-922a-87cda5952ab9/owner-corrections/03-pack-tweaks.png)
- [Item Scale](C:/Users/kalib/.codex/visualizations/2026/10/06/01a11030-ac6c-7070-922a-87cda5952ab9/owner-corrections/04-item-scale.png)
- [Alerts](C:/Users/kalib/.codex/visualizations/2026/10/06/01a11030-ac6c-7070-922a-87cda5952ab9/owner-corrections/05-alerts.png)
- [Alternate GUI scale 4 plus](C:/Users/kalib/.codex/visualizations/2026/10/06/01a11030-ac6c-7070-922a-87cda5952ab9/owner-corrections/06-item-scale-gui4.png)
- [Final left-aligned Settings](C:/Users/kalib/.codex/visualizations/2026/10/06/01a11030-ac6c-7070-922a-87cda5952ab9/owner-corrections/07-settings-navigation.png)
- [Representative Zoom keybind](C:/Users/kalib/.codex/visualizations/2026/10/06/01a11030-ac6c-7070-922a-87cda5952ab9/owner-corrections/11-zoom-keybind.png)

## Git

Branch: codex/unified-theme-pilot. Starting HEAD:
04b382d9710dc2aec2558346cc49bf781b86ea85 (the brief's e59c239 was stale).
First correction: 0837a4e6bb3ce2a0e6a70d6cb8180b82d2e248f4.
A separate local follow-up fixes runtime capture ownership and inline fallback
alignment and records completed verification. Final commit/HEAD reported in
chat to avoid a self-referential document hash. All 395 original tracked files
remain present; four protected untracked Python cache hashes match; master and
origin/master refs are unchanged. No clean/reset, unrelated normalization or
feature changes. Pushed = NO.
