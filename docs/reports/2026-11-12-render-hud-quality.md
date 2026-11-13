# Rendering, HUD and loading follow-up

Work in progress on `feat/render-hud-quality`, based on `7b01a13` and the
existing ADR 0009 optimization series. This is test work, not a release.

## Rendering reproduction

The workstation has a 2560 x 1440 landscape primary display and a 1440 x 2560
portrait secondary display, both at 125% desktop scaling. A disposable 26.3
client uses the native parity probe and desktop window capture; Minecraft's
F2 capture is avoided because it deliberately suppresses glass temporarily.

`tools/render_stability.py` compares a fixed region of successive native
window captures. Its exact-pixel verdict is suitable for a stationary
synthetic backdrop. Moving-world captures need a separate interpretation:
moving scenery and particles are valid pixel changes, not proof of flicker.

- OpenGL, static striped backdrop, device blur radius 25: all 16 captures
  identical in the sampled panel body on each display.
- OpenGL, the same radius with 32 extra glass panels: all 16 body captures
  identical on the portrait display.
- Native Vulkan, static stripes, radius 25: all 16 body captures identical
  on the portrait display. Captures contain a rendered, blurred panel.
- Native Vulkan, Aurora grid at GUI scale 3: both theme radius 8 and radius 9
  showed at most one intensity level of change in the sampled sidebar over
  16 captures with a live world. This does not reproduce the reported flicker.

Evidence is retained locally below `build/render-hud-quality/`. The original
live developer configuration and launcher instance were not modified.
Neither a hardware fault nor the reported software flicker has been ruled out.

## Remove Reloading Screen compatibility

The launcher's `Aurora 26.3` instance contains Remove Reloading Screen
5.2.8+mc.26.3 (`rrls`). Its saved log repeatedly reports a null
`ActiveTextCollector` while Aurora's `LoadingOverlayMixin` draws the current
screen inside RRLS's miniature overlay render.

A disposable 26.3 OpenGL client with Aurora, Fabric and that RRLS jar reproduced:

1. Startup failure in `PlayerSkinWidget`: `No model for layer
   minecraft:player#main`. RRLS exposes Aurora's title before initial model
   resources are ready. Disabling the custom title allows the same fixture
   to reach the world.
2. Pause screen followed by a resource reload: 242 `Failed to draw overlay`
   errors in the recorded log. The check asserting zero such messages fails.

Aurora now waits for `isGameLoadFinished()` before creating its title player
preview and rebuilds the title when resources become ready. Its reload-overlay
optimization yields to `rrls` under ADR 0009, leaving one owner of that render
path. The real catalog stand-down rule has a regression test.

The initial 26.3 test/build and focused stand-down test pass. Runtime rechecks,
all-line parity, the remaining feature changes and the final performance
comparison are still outstanding.
