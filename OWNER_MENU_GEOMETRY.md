# Owner Menu Geometry Corrections

READY FOR REVIEW. This bounded correction follows the owner's latest steering:
keep the original logo size and reduce card width. No UX-4, Pack Tweaks behavior
work, broader rollout or theme redesign was started.

## Geometry

All measurements are logical GUI pixels.

| Element | Baseline | Final |
|---|---|---|
| Detail-header mark | 24×24 at frame +8, +4 | Removed from shared FeatureDetailScreen |
| Detail title/subtitle left edge | frame-left +40 | frame-left +12, same as ordinary labels |
| Header title vertical position | frame-top +12; +7 with attached credit | Unchanged |
| Menu logo preferred bounds | 48×48 | 48×48, unchanged per latest instruction |
| White runtime asset | 256×256 | Original bytes unchanged |
| Each of five visible/interaction cards | 220×30 | 180×22 |
| Card gap / pitch | 6 / 36 | 4 / 26 |
| Five-card stack height | 174 | 126 |
| Logo-to-card gap | 12 | 12 |
| Complete preferred composition height | 234 | 186 |

One `MenuComposition.Layout` drives both drawing and widget hit bounds. All five
cards share the same geometry and have disjoint, half-open interaction bounds;
there is no separate enlarged hit region. The complete logo/cards group is
centered together, with integer edges and at most one logical pixel of centering
rounding. The preferred mark remains 48px on desktop; height-constrained layout
reduces it as needed. Supported logical viewports (≥240×160) fit with 8px margins.
No text scaling, decorative replacement icon, tinted logo, backing card or new glow.
Reset/Done and attached credits retain their original band and semantics.

## Verification

`gradlew.bat test build`: BUILD SUCCESSFUL, **421 tests, 0 failures, 0 errors,
0 skipped**. The 415-test baseline is retained plus six focused geometry tests.
The focused geometry/correction/compact/material selection comprises 21 tests;
it passed before the owner's width steering and all final variants passed in
the final full suite. The final full run follows the steering change; no source
changes followed that run. Existing Java 25/LWJGL warnings remain.

`gradlew.bat runClient` completed desktop checks at GUI scales **3 and 4**:
main menu, Zoom, Pack Tweaks, right-click opening, pointer Done navigation,
visible Tab focus and Space activation on Done. Both detail titles align with
their row labels and have no brand mark. Normal main menu shows five narrower,
shorter cards beneath the original-size mark. A restored small window (854×480
framebuffer, effective GUI scale 2) also fits the full composition. Tests cover
integer centering/fitting across 42 constrained/desktop viewport combinations,
identical/disjoint paint and hit bounds, original mark size and GUI 3/4 texture
coverage. Existing ROUND/SQUARE conformance tests pass; live captures used ROUND.
Roundness, material recipes, hover/press/focus, font size and action routing are
still owned by the existing ButtonWidget/Button path.

Main/detail telemetry in both GUI-scale sessions: panels/frame **avg=1.0,
max=1**, window claims only, zero child-card claims, zero declines and zero pool
exhaustion. The main menu retains its existing five standalone-card captures;
the logo owns none. Representative GUI 4 observations: main ~2.29ms/frame,
detail ~3.06ms/frame. These are local observations, not an FPS claim. No renderer
or material code changed and no GL/render error appeared. Existing FabricMC
offline authentication/Realms diagnostics are unrelated.

After both clients exited, all nine baseline runtime config/profile files and
options.txt were restored byte-for-byte. Baseline tracked inventory (398 files)
remains present and all four protected untracked Python cache hashes match.
No clean/reset, unexpected deletion, unrelated normalization or push.

## Exact files changed

- [AGENTS.md](C:/Dev/Aurora-Client/AGENTS.md)
- [ARCHITECTURE.md](C:/Dev/Aurora-Client/ARCHITECTURE.md)
- [DESIGN_LANGUAGE.md](C:/Dev/Aurora-Client/DESIGN_LANGUAGE.md)
- [OWNER_MENU_GEOMETRY.md](C:/Dev/Aurora-Client/OWNER_MENU_GEOMETRY.md)
- [src/client/java/com/aurora/client/screen/AuroraTitleScreen.java](C:/Dev/Aurora-Client/src/client/java/com/aurora/client/screen/AuroraTitleScreen.java)
- [src/client/java/com/aurora/client/screen/FeatureDetailScreen.java](C:/Dev/Aurora-Client/src/client/java/com/aurora/client/screen/FeatureDetailScreen.java)
- [src/client/java/com/aurora/client/ui/util/MenuComposition.java](C:/Dev/Aurora-Client/src/client/java/com/aurora/client/ui/util/MenuComposition.java)
- [src/client/java/com/aurora/client/ui/util/UiDensity.java](C:/Dev/Aurora-Client/src/client/java/com/aurora/client/ui/util/UiDensity.java)
- [src/test/java/com/aurora/client/ui/util/OwnerCorrectionTest.java](C:/Dev/Aurora-Client/src/test/java/com/aurora/client/ui/util/OwnerCorrectionTest.java)
- [src/test/java/com/aurora/client/ui/util/OwnerMenuGeometryTest.java](C:/Dev/Aurora-Client/src/test/java/com/aurora/client/ui/util/OwnerMenuGeometryTest.java)

The logo texture and AuroraBrand renderer are unchanged from the starting HEAD.

## New runtime screenshots

- [Zoom — logo-free header, GUI 3](C:/Users/kalib/.codex/visualizations/2026/10/06/01a11030-ac6c-7070-922a-87cda5952ab9/menu-geometry/01-zoom-header-gui3.png)
- [Main menu — original logo and narrower cards, GUI 3](C:/Users/kalib/.codex/visualizations/2026/10/06/01a11030-ac6c-7070-922a-87cda5952ab9/menu-geometry/02-main-menu-gui3.png)
- [Constrained window](C:/Users/kalib/.codex/visualizations/2026/10/06/01a11030-ac6c-7070-922a-87cda5952ab9/menu-geometry/03-constrained-menu.png)
- [Pack Tweaks — logo-free header, GUI 3](C:/Users/kalib/.codex/visualizations/2026/10/06/01a11030-ac6c-7070-922a-87cda5952ab9/menu-geometry/04-pack-tweaks-gui3.png)
- [Main menu, GUI 4](C:/Users/kalib/.codex/visualizations/2026/10/06/01a11030-ac6c-7070-922a-87cda5952ab9/menu-geometry/05-main-menu-gui4.png)
- [Zoom, GUI 4](C:/Users/kalib/.codex/visualizations/2026/10/06/01a11030-ac6c-7070-922a-87cda5952ab9/menu-geometry/06-zoom-header-gui4.png)
- [Pack Tweaks, GUI 4](C:/Users/kalib/.codex/visualizations/2026/10/06/01a11030-ac6c-7070-922a-87cda5952ab9/menu-geometry/07-pack-tweaks-gui4.png)

## Git

Starting HEAD: 65d1e625280feabc45060e539928ae58df723638.
Branch: codex/unified-theme-pilot. One independently revertible local correction
commit; its final SHA/HEAD is reported in chat to avoid a self-referential hash.
Pushed = NO. Owner visual acceptance remains pending; stop before UX-4.
